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
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isSignedIn() {
        return getToken() != null && !getToken().trim().isEmpty();
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
        prefs.edit().clear().apply();
    }
}
