package com.example.tripnest.data;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;

import com.example.tripnest.BuildConfig;

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

    public void register(String name, String email, String password, AuthCallback callback) {
        requestAuth("/api/auth/register", name, email, password, callback);
    }

    public void login(String email, String password, AuthCallback callback) {
        requestAuth("/api/auth/login", null, email, password, callback);
    }

    private void requestAuth(String path, String name, String email, String password, AuthCallback callback) {
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

    public void requestRecommendations(String destination, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("destination", destination == null || destination.trim().isEmpty() ? "Travel" : destination.trim());
                requestBody.put("durationDays", 3);
                requestBody.put("styles", new JSONArray().put("nature").put("food").put("route"));

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
                                placeJson.optString("category")
                        ));
                    }
                }

                TripRecommendation recommendation = new TripRecommendation(
                        response.optString("summary"),
                        response.optInt("filteredAdCount"),
                        places
                );
                mainHandler.post(() -> callback.onSuccess(recommendation));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    public void requestNearby(double latitude, double longitude, int radiusMeters, NearbyCallback callback) {
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
        LinkedHashSet<String> baseUrls = new LinkedHashSet<>();
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
                    validationErrors.put(baseUrl, "response does not include expected data");
                    continue;
                }
                return response;
            } catch (Exception error) {
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

    private String parseErrorMessage(String errorBody) {
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

    public interface Callback {
        void onSuccess(TripRecommendation recommendation);
        void onError(Exception error);
    }

    public interface NearbyCallback {
        void onSuccess(NearbyResult result);
        void onError(Exception error);
    }

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(Exception error);
    }

    public static class AuthResult {
        public final String token;
        public final AuthUser user;

        public AuthResult(String token, AuthUser user) {
            this.token = token;
            this.user = user;
        }
    }

    public static class AuthUser {
        public final String id;
        public final String email;
        public final String name;

        public AuthUser(String id, String email, String name) {
            this.id = id;
            this.email = email;
            this.name = name;
        }
    }

    public static class TripRecommendation {
        public final String summary;
        public final int filteredAdCount;
        public final List<Place> places;

        public TripRecommendation(String summary, int filteredAdCount, List<Place> places) {
            this.summary = summary;
            this.filteredAdCount = filteredAdCount;
            this.places = places;
        }
    }

    public static class Place {
        public final String name;
        public final String description;
        public final String category;

        public Place(String name, String description, String category) {
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }

    public static class NearbyResult {
        public final List<String> stays;
        public final List<String> attractions;
        public final List<String> restaurants;

        public NearbyResult(List<String> stays, List<String> attractions, List<String> restaurants) {
            this.stays = stays;
            this.attractions = attractions;
            this.restaurants = restaurants;
        }
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
