package com.example.tripnest.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tripnest.BuildConfig;

public class ServerConfig {
    private static final String PREFS_NAME = "tripnest_server";
    private static final String KEY_BASE_URL = "base_url";

    private final SharedPreferences prefs;

    public ServerConfig(Context context) {
        // 사용자가 입력한 서버 주소는 앱을 다시 켜도 유지한다.
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getBaseUrl() {
        // 직접 저장한 주소가 있으면 우선 사용하고, 없으면 빌드 설정의 기본 주소로 돌아간다.
        String savedUrl = prefs.getString(KEY_BASE_URL, "");
        if (savedUrl != null && !savedUrl.trim().isEmpty()) {
            return normalizeBaseUrl(savedUrl);
        }
        return normalizeBaseUrl(BuildConfig.BACKEND_BASE_URL);
    }

    public void saveBaseUrl(String value) {
        // 같은 서버라도 끝의 슬래시 개수 때문에 다른 주소처럼 보이지 않도록 저장 전에 정리한다.
        prefs.edit()
                .putString(KEY_BASE_URL, normalizeBaseUrl(value))
                .apply();
    }

    public void clear() {
        // 서버 설정만 초기화하고 로그인 세션 등 다른 저장값은 건드리지 않는다.
        prefs.edit().remove(KEY_BASE_URL).apply();
    }

    public static String normalizeBaseUrl(String value) {
        // API path를 붙일 때 //api 처럼 중복 슬래시가 생기지 않게 만든다.
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
