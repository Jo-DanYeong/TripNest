package com.example.tripnest.model;

public class AuthUser {
    public final String id;
    public final String email;
    public final String name;

    public AuthUser(String id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }
}
