package com.example.tripnest.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tripnest.model.AuthResult;

public class AuthSession {

    private static final String PREFS_NAME = "tripnest_auth";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";

    private final SharedPreferences prefs;

    public AuthSession(Context context) {
        // 앱 컨텍스트 기준으로 저장해 Fragment/Activity 생명주기에 영향을 받지 않게 한다.
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isSignedIn() {
        String token = getToken() == null ? "" : getToken().trim();
        if (token.startsWith("local-login-") || token.startsWith("local-register-")) {
            clear();
            return false;
        }
        return !token.isEmpty();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public void save(AuthResult result) {
        // 서버 응답이 비정상이면 기존 세션을 덮어쓰지 않는다.
        if (result == null || result.user == null) {
            return;
        }
        prefs.edit()
                .putString(KEY_TOKEN, result.token)
                .putString(KEY_USER_ID, result.user.id)
                .putString(KEY_EMAIL, result.user.email)
                .putString(KEY_NAME, result.user.name)
                .apply();
    }

    public void clear() {
        // 로그아웃 때는 토큰과 사용자 정보를 한 번에 지운다.
        prefs.edit().clear().apply();
    }
}
