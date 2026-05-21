package com.example.tripnest.model;

public class Place {
    public final String name;
    public final String description;
    public final String category;
    public final String address;
    public final String placeUrl;
    public final double latitude;
    public final double longitude;

    public Place(String name,
                 String description,
                 String category,
                 String address,
                 String placeUrl,
                 double latitude,
                 double longitude) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.address = address;
        this.placeUrl = placeUrl;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
