package com.example.tripnest.data;

import com.example.tripnest.BuildConfig;

public class ServerConfig {

    public ServerConfig(android.content.Context context) {
        // 서버 주소는 앱 실행 중 입력받지 않고 Gradle 빌드 설정에서만 가져온다.
    }

    public String getBaseUrl() {
        return normalizeBaseUrl(BuildConfig.BACKEND_BASE_URL);
    }

    public String getFallbackUrl() {
        return normalizeBaseUrl(BuildConfig.BACKEND_FALLBACK_URL);
    }

    public static String normalizeBaseUrl(String value) {
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
