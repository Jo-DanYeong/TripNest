package com.example.tripnest.model;

public class AuthResult {
    public final String token;
    public final AuthUser user;

    public AuthResult(String token, AuthUser user) {
        this.token = token;
        this.user = user;
    }
}
