package com.example.tripnest.model;

import java.util.List;

public class PlaceInsight {
    public final String placeName;
    public final String summary;
    public final int filteredAdCount;
    public final List<Source> sources;

    public PlaceInsight(String placeName, String summary, int filteredAdCount, List<Source> sources) {
        this.placeName = placeName;
        this.summary = summary;
        this.filteredAdCount = filteredAdCount;
        this.sources = sources;
    }
}
