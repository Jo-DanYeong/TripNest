package com.example.tripnest.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;

import com.example.tripnest.BuildConfig;
import com.example.tripnest.model.AuthResult;
import com.example.tripnest.model.AuthUser;
import com.example.tripnest.model.NearbyResult;
import com.example.tripnest.model.Place;
import com.example.tripnest.model.PlaceInsight;
import com.example.tripnest.model.Source;
import com.example.tripnest.model.TripRecommendation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackendClient {

    private static final String BASE_URL = BuildConfig.BACKEND_BASE_URL;
    private static final String BACKEND_FALLBACK_URL = BuildConfig.BACKEND_FALLBACK_URL;
    private static final String EMULATOR_FALLBACK_URL = "http://10.0.2.2:8080";
    private static final String ADB_REVERSE_FALLBACK_URL = "http://127.0.0.1:8080";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ServerConfig serverConfig;

    public BackendClient() {
        // 테스트나 단순 호출에서는 빌드 기본 URL만 사용한다.
        this.serverConfig = null;
    }

    public BackendClient(Context context) {
        // 실제 앱 화면에서는 사용자가 저장한 서버 주소를 우선 사용할 수 있게 한다.
        this.serverConfig = new ServerConfig(context);
    }

    public String getActiveBaseUrl() {
        // ServerConfig가 없으면 BuildConfig의 기본 서버로 폴백한다.
        return serverConfig == null ? BASE_URL : serverConfig.getBaseUrl();
    }

    public void checkHealth(HealthCallback callback) {
        // 서버 설정 화면에서 빠르게 연결 상태를 확인할 때 쓰는 가벼운 요청이다.
        executor.execute(() -> {
            try {
                JSONObject response = getJson(getActiveBaseUrl() + "/api/health");
                mainHandler.post(() -> callback.onSuccess(response.optString("service", "TripNest Backend")));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void register(String name, String email, String password, AuthCallback callback) {
        requestAuth("/api/auth/register", name, email, password, callback);
    }

    public void login(String email, String password, AuthCallback callback) {
        requestAuth("/api/auth/login", null, email, password, callback);
    }

    private void requestAuth(String path, String name, String email, String password, AuthCallback callback) {
        // 인증 요청은 로그인/회원가입이 거의 같아서 path와 name 유무만 다르게 받는다.
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                if (name != null) {
                    requestBody.put("name", name.trim());
                }
                requestBody.put("email", email == null ? "" : email.trim());
                requestBody.put("password", password == null ? "" : password);

                JSONObject response = postJsonWithFallback(path, requestBody, null);
                AuthResult result = new AuthResult(
                        response.optString("token"),
                        parseUser(response.optJSONObject("user"))
                );
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void requestRecommendations(String destination,
                                       String startDate,
                                       String endDate,
                                       int budgetWon,
                                       Callback callback) {
        // 추천 API는 사용자가 입력한 조건을 한 번에 넘기고, 응답은 화면 모델로 바꿔 돌려준다.
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("destination", destination == null || destination.trim().isEmpty() ? "Travel" : destination.trim());
                requestBody.put("startDate", startDate == null ? "" : startDate.trim());
                requestBody.put("endDate", endDate == null ? "" : endDate.trim());
                requestBody.put("budgetWon", Math.max(0, budgetWon));
                requestBody.put("durationDays", 3);
                requestBody.put("styles", new JSONArray().put("자연").put("맛집").put("동선"));

                JSONObject response = postJsonWithFallback(
                        "/api/trips/recommendations",
                        requestBody,
                        (res) -> {
                            JSONArray places = res.optJSONArray("places");
                            return places != null && places.length() > 0;
                        }
                );
                JSONArray placesJson = response.optJSONArray("places");
                List<Place> places = new ArrayList<>();
                if (placesJson != null) {
                    for (int i = 0; i < placesJson.length(); i++) {
                        JSONObject placeJson = placesJson.optJSONObject(i);
                        if (placeJson == null) {
                            continue;
                        }
                        places.add(new Place(
                                placeJson.optString("name"),
                                placeJson.optString("description"),
                                placeJson.optString("category"),
                                placeJson.optString("address"),
                                placeJson.optString("kakaoPlaceUrl"),
                                parseDouble(placeJson.optString("latitude")),
                                parseDouble(placeJson.optString("longitude"))
                        ));
                    }
                }

                TripRecommendation recommendation = new TripRecommendation(
                        response.optString("summary"),
                        response.optString("relatedSummary"),
                        response.optInt("filteredAdCount"),
                        places,
                        parseSources(response.optJSONArray("sources"))
                );
                mainHandler.post(() -> callback.onSuccess(recommendation));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void requestPlaceInsight(String destination, Place place, PlaceInsightCallback callback) {
        // 장소 상세 바텀시트에서 선택한 장소의 관련 글 요약을 따로 가져온다.
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("destination", destination == null ? "" : destination.trim());
                requestBody.put("placeName", place == null ? "" : place.name);
                requestBody.put("category", place == null ? "" : place.category);

                JSONObject response = postJsonWithFallback("/api/places/insights", requestBody, null);
                PlaceInsight result = new PlaceInsight(
                        response.optString("placeName"),
                        response.optString("summary"),
                        response.optInt("filteredAdCount"),
                        parseSources(response.optJSONArray("sources"))
                );
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void requestNearby(double latitude, double longitude, int radiusMeters, NearbyCallback callback) {
        // 지도에서 고른 중심 좌표를 기준으로 주변 숙소/관광/음식 데이터를 요청한다.
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("latitude", latitude);
                requestBody.put("longitude", longitude);
                requestBody.put("radiusMeters", radiusMeters);

                JSONObject response = postJsonWithFallback("/api/maps/nearby", requestBody, null);
                NearbyResult result = new NearbyResult(
                        parseNearbyNames(response.optJSONArray("stays")),
                        parseNearbyNames(response.optJSONArray("attractions")),
                        parseNearbyNames(response.optJSONArray("restaurants"))
                );
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private JSONObject postJsonWithFallback(String path, JSONObject body, ResponseValidator validator) throws Exception {
        // 개발 환경마다 접근 가능한 주소가 달라서, 가능성 높은 URL을 순서대로 시도한다.
        LinkedHashSet<String> baseUrls = new LinkedHashSet<>();
        baseUrls.add(getActiveBaseUrl());
        baseUrls.add(BACKEND_FALLBACK_URL);
        baseUrls.add(BASE_URL);
        baseUrls.add(ADB_REVERSE_FALLBACK_URL);
        if (isProbablyEmulator()) {
            baseUrls.add(EMULATOR_FALLBACK_URL);
        }

        Exception lastError = null;
        Map<String, String> validationErrors = new LinkedHashMap<>();
        Map<String, String> connectionErrors = new LinkedHashMap<>();
        for (String baseUrl : baseUrls) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                continue;
            }
            try {
                JSONObject response = postJson(baseUrl.trim() + path, body);
                if (validator != null && !validator.isValid(response)) {
                    // 연결은 됐지만 필요한 데이터가 비어 있으면 다음 후보 서버를 시도한다.
                    validationErrors.put(baseUrl, "response does not include expected data");
                    continue;
                }
                return response;
            } catch (Exception error) {
                // 4xx는 사용자 입력이나 인증 문제일 가능성이 커서 다른 서버로 숨기지 않는다.
                if (error instanceof BackendHttpException
                        && ((BackendHttpException) error).statusCode < 500) {
                    throw error;
                }
                lastError = error;
                connectionErrors.put(baseUrl, error.getMessage());
            }
        }
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("All backends returned empty recommendation data: " + validationErrors.keySet());
        }
        if (!connectionErrors.isEmpty()) {
            throw new IllegalStateException("백엔드 연결 실패: " + connectionErrors);
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("No backend URL candidates available.");
    }

    // Real phones use adb reverse with 127.0.0.1, while emulators need 10.0.2.2.
    private boolean isProbablyEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || "google_sdk".equals(Build.PRODUCT);
    }

    // Centralizes JSON POST handling so each API method only describes its payload/response.
    private JSONObject postJson(String urlString, JSONObject body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            String errorBody = "";
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                    StringBuilder errorBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorBuilder.append(line);
                    }
                    errorBody = errorBuilder.toString();
                }
            }
            String message = parseErrorMessage(errorBody);
            throw new BackendHttpException(
                    statusCode,
                    message.isEmpty() ? "Backend returned " + statusCode + " for " + urlString : message
            );
        }

        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return new JSONObject(responseBuilder.toString());
    }

    private JSONObject getJson(String urlString) throws Exception {
        // health check처럼 본문이 필요 없는 요청은 GET 전용 헬퍼로 처리한다.
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new BackendHttpException(statusCode, "Backend returned " + statusCode + " for " + urlString);
        }

        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return new JSONObject(responseBuilder.toString());
    }

    private String parseErrorMessage(String errorBody) {
        // 백엔드가 내려준 message/error 필드를 사용자에게 보여줄 수 있는 문구로 꺼낸다.
        try {
            JSONObject errorJson = new JSONObject(errorBody == null ? "" : errorBody);
            String message = errorJson.optString("message");
            if (!message.isEmpty()) {
                return message;
            }
            return errorJson.optString("error");
        } catch (Exception ignored) {
            return "";
        }
    }

    private AuthUser parseUser(JSONObject userJson) {
        // user가 빠진 응답도 앱이 터지지 않도록 빈 사용자 객체로 맞춘다.
        if (userJson == null) {
            return new AuthUser("", "", "");
        }
        return new AuthUser(
                userJson.optString("id"),
                userJson.optString("email"),
                userJson.optString("name")
        );
    }

    private List<String> parseNearbyNames(JSONArray array) {
        // 주변 장소 응답에서는 화면에 보여줄 이름만 추려낸다.
        List<String> names = new ArrayList<>();
        if (array == null) {
            return names;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("name");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private double parseDouble(String value) {
        // 좌표가 비어 있거나 깨졌을 때는 NaN으로 표시해 지도 버튼 노출을 막는다.
        try {
            return Double.parseDouble(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    // Sources are rendered as clickable cards in the result/detail UI.
    private List<Source> parseSources(JSONArray array) {
        List<Source> sources = new ArrayList<>();
        if (array == null) {
            return sources;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = item.optString("title");
            String source = item.optString("source");
            String url = item.optString("url");
            String summary = item.optString("summary");
            if (!title.trim().isEmpty()) {
                sources.add(new Source(title, source, url, summary));
            }
        }
        return sources;
    }

    public interface Callback {
        void onSuccess(TripRecommendation recommendation);
        void onError(Exception error);
    }

    public interface NearbyCallback {
        void onSuccess(NearbyResult result);
        void onError(Exception error);
    }

    public interface PlaceInsightCallback {
        void onSuccess(PlaceInsight result);
        void onError(Exception error);
    }

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(Exception error);
    }

    public interface HealthCallback {
        void onSuccess(String serviceName);
        void onError(Exception error);
    }

    private interface ResponseValidator {
        boolean isValid(JSONObject response);
    }

    private static class BackendHttpException extends Exception {
        final int statusCode;

        BackendHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
