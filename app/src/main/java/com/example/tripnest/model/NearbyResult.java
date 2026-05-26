package com.example.tripnest.model;

import java.util.List;

public class NearbyResult {
    public final List<String> stays;
    public final List<String> attractions;
    public final List<String> restaurants;

    public NearbyResult(List<String> stays, List<String> attractions, List<String> restaurants) {
        this.stays = stays;
        this.attractions = attractions;
        this.restaurants = restaurants;
    }
}
