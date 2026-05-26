package com.example.tripnest.model;

import java.util.List;

public class TripRecommendation {
    public final String summary;
    public final String relatedSummary;
    public final int filteredAdCount;
    public final int lodgingNightlyCostWon;
    public final List<Place> places;
    public final List<Source> sources;

    public TripRecommendation(String summary,
                              String relatedSummary,
                              int filteredAdCount,
                              int lodgingNightlyCostWon,
                              List<Place> places,
                              List<Source> sources) {
        this.summary = summary;
        this.relatedSummary = relatedSummary;
        this.filteredAdCount = filteredAdCount;
        this.lodgingNightlyCostWon = lodgingNightlyCostWon;
        this.places = places;
        this.sources = sources;
    }
}
