package com.example.tripnest.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tripnest.R;
import com.example.tripnest.data.BackendClient;
import com.example.tripnest.model.NearbyResult;
import com.example.tripnest.model.Place;
import com.example.tripnest.model.PlaceInsight;
import com.example.tripnest.model.Source;
import com.example.tripnest.model.TripRecommendation;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResultFragment extends Fragment {

    private static final String CATEGORY_ATTRACTION = "관광";
    private static final String CATEGORY_FOOD = "음식";
    private static final String CATEGORY_STAY = "숙소";

    private BackendClient backendClient;
    private final List<Place> allPlaces = new ArrayList<>();
    private final List<Place> selectedPlaces = new ArrayList<>();
    private final Map<String, Integer> selectedLodgingNightlyCosts = new HashMap<>();
    private final Map<String, Integer> selectedMealCosts = new HashMap<>();
    private final Map<String, AdmissionCost> selectedAdmissionCosts = new HashMap<>();

    private double selectedLatitude = 37.5665;
    private double selectedLongitude = 126.9780;
    private String query = "";
    private String selectedCategory = CATEGORY_ATTRACTION;
    private String routePlan = "";
    private String startDate = "";
    private String endDate = "";
    private int budgetWon = 0;
    private int lodgingNightlyCostWon = 0;
    private int adultCount = 1;
    private int youthCount = 0;
    private int seniorCount = 0;
    private int childCount = 0;

    private LinearLayout placeListContainer;
    private TextView attractionTab;
    private TextView foodTab;
    private TextView stayTab;
    private TextView nearbyResultView;
    private TextView selectedRouteSummaryView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        backendClient = new BackendClient(requireContext());

        query = getArguments() != null ? getArguments().getString("query", "") : "";
        startDate = getArguments() != null ? getArguments().getString("startDate", "") : "";
        endDate = getArguments() != null ? getArguments().getString("endDate", "") : "";
        budgetWon = parseBudget(getArguments() != null ? getArguments().getString("budgetWon", "") : "");
        routePlan = getArguments() != null ? getArguments().getString("routePlan", "") : "";
        adultCount = parseCount(getArguments() != null ? getArguments().getString("adultCount", "1") : "1");
        youthCount = parseCount(getArguments() != null ? getArguments().getString("youthCount", "0") : "0");
        seniorCount = parseCount(getArguments() != null ? getArguments().getString("seniorCount", "0") : "0");
        childCount = parseCount(getArguments() != null ? getArguments().getString("childCount", "0") : "0");
        if (getTravelerCount() <= 0) {
            adultCount = 1;
        }

        TextView titleView = view.findViewById(R.id.tv_destination);
        TextView queryView = view.findViewById(R.id.et_search);
        TextView summaryView = view.findViewById(R.id.tv_ai_summary);
        TextView filterMessageView = view.findViewById(R.id.tv_ad_filter_message);
        TextView tripMetaView = view.findViewById(R.id.tv_trip_meta);
        TextView coordView = view.findViewById(R.id.tv_selected_coord);
        TextView recommendationEmptyView = view.findViewById(R.id.tv_recommendation_empty);

        placeListContainer = view.findViewById(R.id.place_list_container);
        attractionTab = view.findViewById(R.id.btn_category_attraction);
        foodTab = view.findViewById(R.id.btn_category_food);
        stayTab = view.findViewById(R.id.btn_category_stay);
        nearbyResultView = view.findViewById(R.id.tv_nearby_result);
        selectedRouteSummaryView = view.findViewById(R.id.tv_selected_route_summary);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        view.findViewById(R.id.btn_search_nearby).setVisibility(View.GONE);
        attractionTab.setOnClickListener(v -> selectCategory(CATEGORY_ATTRACTION));
        foodTab.setOnClickListener(v -> selectCategory(CATEGORY_FOOD));
        stayTab.setOnClickListener(v -> selectCategory(CATEGORY_STAY));

        if (!query.isEmpty()) {
            titleView.setText(getString(R.string.result_title_format, query));
            queryView.setText(query);
            saveLastQuery(query);
        }
        saveTripPlanningState();
        renderTripMeta(tripMetaView);
        renderPlaceList();
        updateSelectedRouteSummary();
        recommendationEmptyView.setVisibility(View.VISIBLE);
        nearbyResultView.setVisibility(View.GONE);

        getParentFragmentManager().setFragmentResultListener(
                "map_pick_result",
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    selectedLatitude = result.getDouble("latitude", selectedLatitude);
                    selectedLongitude = result.getDouble("longitude", selectedLongitude);
                    updateSelectedCoordText(coordView);
                    saveLastLocation();
                    updateSelectedRouteSummary();
                    runNearbySearch();
                });

        backendClient.requestRecommendations(query, startDate, endDate, budgetWon, routePlan, adultCount, youthCount, seniorCount, childCount, new BackendClient.Callback() {
            @Override
            public void onSuccess(TripRecommendation recommendation) {
                summaryView.setText(recommendation.summary.isEmpty()
                        ? getString(R.string.gemini_summary_fallback)
                        : recommendation.summary);
                filterMessageView.setText(getString(R.string.ad_filter_message_format, recommendation.filteredAdCount));
                if (recommendation.places == null || recommendation.places.isEmpty()) {
                    allPlaces.clear();
                    selectedPlaces.clear();
                    renderPlaceList();
                    updateSelectedRouteSummary();
                    recommendationEmptyView.setText(R.string.no_recommendations);
                    recommendationEmptyView.setVisibility(View.VISIBLE);
                    return;
                }

                allPlaces.clear();
                allPlaces.addAll(recommendation.places);
                lodgingNightlyCostWon = Math.max(0, recommendation.lodgingNightlyCostWon);
                recommendationEmptyView.setVisibility(View.GONE);
                selectCategory(CATEGORY_ATTRACTION);
                updateSelectedRouteSummary();
            }

            @Override
            public void onError(Exception error) {
                summaryView.setText(getString(R.string.recommendations_unavailable));
                filterMessageView.setText(getString(R.string.ad_filter_message));
                allPlaces.clear();
                selectedPlaces.clear();
                renderPlaceList();
                updateSelectedRouteSummary();
                recommendationEmptyView.setText(R.string.recommendations_unavailable);
                recommendationEmptyView.setVisibility(View.VISIBLE);
            }
        });

        view.findViewById(R.id.btn_pick_on_map).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("pickMode", true);
            Navigation.findNavController(view).navigate(R.id.action_resultFragment_to_mapPickFragment, args);
        });
    }

    private void selectCategory(String category) {
        selectedCategory = category;
        updateCategoryTabs();
        renderPlaceList();
    }

    private void updateCategoryTabs() {
        styleCategoryTab(attractionTab, CATEGORY_ATTRACTION.equals(selectedCategory));
        styleCategoryTab(foodTab, CATEGORY_FOOD.equals(selectedCategory));
        styleCategoryTab(stayTab, CATEGORY_STAY.equals(selectedCategory));
    }

    private void styleCategoryTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_badge_green : R.drawable.bg_result_pill);
        tab.setTextColor(getResources().getColor(selected ? R.color.white : R.color.primary_dark, requireContext().getTheme()));
    }

    private void renderPlaceList() {
        if (placeListContainer == null) {
            return;
        }

        placeListContainer.removeAllViews();
        int index = 1;
        for (Place place : allPlaces) {
            if (!isPlaceInSelectedCategory(place)) {
                continue;
            }
            placeListContainer.addView(createPlaceRow(place, index));
            index++;
        }

        if (index == 1) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(R.string.no_recommendations);
            emptyView.setTextColor(getResources().getColor(R.color.ink_muted, requireContext().getTheme()));
            emptyView.setTextSize(13);
            emptyView.setPadding(dp(12), dp(12), dp(12), dp(12));
            placeListContainer.addView(emptyView);
        }
    }

    private boolean isPlaceInSelectedCategory(Place place) {
        if (CATEGORY_ATTRACTION.equals(selectedCategory)) {
            return isAttractionPlace(place);
        }
        if (CATEGORY_FOOD.equals(selectedCategory)) {
            return isFoodPlace(place);
        }
        return isStayPlace(place);
    }

    private View createPlaceRow(Place place, int index) {
        boolean selected = isPlaceSelected(place);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(selected ? R.drawable.bg_soft_mint : R.drawable.bg_ai_box);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        TextView number = new TextView(requireContext());
        number.setText(String.valueOf(index));
        number.setGravity(android.view.Gravity.CENTER);
        number.setBackgroundResource(R.drawable.bg_place_num);
        number.setTextColor(getResources().getColor(R.color.white, requireContext().getTheme()));
        number.setTextSize(12);
        number.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        numberParams.setMargins(0, 0, dp(10), 0);
        row.addView(number, numberParams);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(requireContext());
        title.setText(place.name);
        title.setTextColor(getResources().getColor(R.color.text_primary, requireContext().getTheme()));
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);

        TextView body = new TextView(requireContext());
        body.setText(place.description == null || place.description.isEmpty() ? place.address : place.description);
        body.setTextColor(getResources().getColor(R.color.text_secondary, requireContext().getTheme()));
        body.setTextSize(12);
        body.setPadding(0, dp(4), 0, 0);
        content.addView(body);

        TextView meta = new TextView(requireContext());
        meta.setText(place.category == null || place.category.isEmpty() ? "상세 보기" : place.category);
        meta.setTextColor(getResources().getColor(R.color.primary_dark, requireContext().getTheme()));
        meta.setTextSize(11);
        meta.setPadding(0, dp(6), 0, 0);
        content.addView(meta);

        TextView selectButton = new TextView(requireContext());
        selectButton.setText(selected ? R.string.place_selected : R.string.place_select);
        selectButton.setGravity(android.view.Gravity.CENTER);
        selectButton.setTextColor(getResources().getColor(selected ? R.color.white : R.color.primary_dark, requireContext().getTheme()));
        selectButton.setTextSize(12);
        selectButton.setTypeface(null, Typeface.BOLD);
        selectButton.setBackgroundResource(selected ? R.drawable.bg_badge_green : R.drawable.bg_result_pill);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(54), dp(34));
        buttonParams.setMargins(dp(8), 0, 0, 0);
        row.addView(selectButton, buttonParams);

        selectButton.setOnClickListener(v -> toggleSelectedPlace(place));
        content.setOnClickListener(v -> showPlaceDialog(place));
        row.setOnClickListener(v -> showPlaceDialog(place));
        return row;
    }

    private void toggleSelectedPlace(Place place) {
        int index = findSelectedPlaceIndex(place);
        if (index >= 0) {
            selectedPlaces.remove(index);
        } else {
            selectedPlaces.add(place);
            requestPlaceCostEstimateIfNeeded(place);
        }
        updateSelectedRouteSummary();
        renderPlaceList();
    }

    private void requestPlaceCostEstimateIfNeeded(Place place) {
        boolean needsLodging = isStayPlace(place) && !selectedLodgingNightlyCosts.containsKey(placeKey(place));
        boolean needsMeal = isFoodPlace(place) && !selectedMealCosts.containsKey(placeKey(place));
        boolean needsAdmission = isAdmissionPlace(place) && !selectedAdmissionCosts.containsKey(placeKey(place));
        if (!needsLodging && !needsMeal && !needsAdmission) {
            return;
        }

        backendClient.requestPlaceCostEstimate(query, place, startDate, endDate, budgetWon, adultCount, youthCount, seniorCount, childCount, new BackendClient.PlaceCostEstimateCallback() {
            @Override
            public void onSuccess(int nightlyCostWon,
                                  int mealCostWon,
                                  int admissionAdultWon,
                                  int admissionYouthWon,
                                  int admissionSeniorWon,
                                  int admissionChildWon) {
                if (needsLodging && nightlyCostWon > 0) {
                    selectedLodgingNightlyCosts.put(placeKey(place), nightlyCostWon);
                }
                if (needsMeal) {
                    selectedMealCosts.put(placeKey(place), mealCostWon > 0 ? mealCostWon : fallbackMealCost(place));
                }
                if (needsAdmission) {
                    selectedAdmissionCosts.put(placeKey(place), normalizeAdmissionCost(place, admissionAdultWon, admissionYouthWon, admissionSeniorWon, admissionChildWon));
                }
                updateSelectedRouteSummary();
            }

            @Override
            public void onError(Exception error) {
                if (needsLodging) {
                    selectedLodgingNightlyCosts.put(placeKey(place), 0);
                }
                if (needsMeal) {
                    selectedMealCosts.put(placeKey(place), fallbackMealCost(place));
                    updateSelectedRouteSummary();
                }
                if (needsAdmission) {
                    selectedAdmissionCosts.put(placeKey(place), fallbackAdmissionCost(place));
                    updateSelectedRouteSummary();
                }
            }
        });
    }

    private boolean isPlaceSelected(Place place) {
        return findSelectedPlaceIndex(place) >= 0;
    }

    private int findSelectedPlaceIndex(Place place) {
        String key = placeKey(place);
        for (int i = 0; i < selectedPlaces.size(); i++) {
            if (key.equals(placeKey(selectedPlaces.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private String placeKey(Place place) {
        return (place.name == null ? "" : place.name) + "|" + (place.address == null ? "" : place.address);
    }

    private void updateSelectedRouteSummary() {
        if (selectedRouteSummaryView == null) {
            return;
        }
        if (selectedPlaces.isEmpty()) {
            selectedRouteSummaryView.setText(R.string.selected_route_empty);
            saveSelectedRouteState(routePlan == null ? "" : routePlan, estimateRouteBudget(routePlan, startDate, endDate));
            return;
        }

        List<Place> orderedPlaces = buildOptimizedRoute();
        String routeText = buildSelectedRouteText(orderedPlaces);
        String detailText = buildCostBreakdownText(orderedPlaces);
        String displayText = routeText + "\n\n" + detailText;
        int estimatedBudget = estimateSelectedRouteBudget(orderedPlaces);
        if (orderedPlaces.size() == 1 && estimatedBudget == 0) {
            selectedRouteSummaryView.setText(getString(R.string.selected_route_single_format, displayText));
        } else {
            selectedRouteSummaryView.setText(getString(R.string.selected_route_format, displayText, formatWon(estimatedBudget)));
        }
        saveSelectedRouteState(displayText, estimatedBudget);
    }

    private List<Place> buildOptimizedRoute() {
        List<Place> remaining = new ArrayList<>(selectedPlaces);
        List<Place> ordered = new ArrayList<>();
        double currentLat = selectedLatitude;
        double currentLng = selectedLongitude;

        while (!remaining.isEmpty()) {
            int nextIndex = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Place candidate = remaining.get(i);
                double distance = hasCoordinates(candidate)
                        ? distanceKm(currentLat, currentLng, candidate.latitude, candidate.longitude)
                        : Double.MAX_VALUE - i;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nextIndex = i;
                }
            }
            Place next = remaining.remove(nextIndex);
            ordered.add(next);
            if (hasCoordinates(next)) {
                currentLat = next.latitude;
                currentLng = next.longitude;
            }
        }
        return ordered;
    }

    private String buildSelectedRouteText(List<Place> orderedPlaces) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < orderedPlaces.size(); i++) {
            names.add((i + 1) + ". " + orderedPlaces.get(i).name);
        }
        return TextUtils.join("\n", names);
    }

    private int estimateSelectedRouteBudget(List<Place> orderedPlaces) {
        if (orderedPlaces.isEmpty()) {
            return estimateRouteBudget(routePlan, startDate, endDate);
        }

        int total = estimateTransportationCost(orderedPlaces);
        total += estimateLodgingCost(orderedPlaces);
        total += estimateMealCost(orderedPlaces);
        total += estimateAdmissionCost(orderedPlaces);
        return Math.max(total, 0);
    }

    private String buildCostBreakdownText(List<Place> orderedPlaces) {
        List<String> lines = new ArrayList<>();
        int lodgingTotal = estimateLodgingCost(orderedPlaces);
        int mealTotal = estimateMealCost(orderedPlaces);
        int admissionTotal = estimateAdmissionCost(orderedPlaces);
        int transportationTotal = estimateTransportationCost(orderedPlaces);

        lines.add("\uBE44\uC6A9 \uC0C1\uC138");
        lines.add("[\uC2DD\uBE44]");
        appendMealBreakdown(lines, orderedPlaces);
        lines.add("[\uC785\uC7A5\uAD8C]");
        appendAdmissionBreakdown(lines, orderedPlaces);
        lines.add("[\uC219\uBC15\uBE44]");
        appendLodgingBreakdown(lines, orderedPlaces);
        lines.add("[\uAD50\uD1B5\uBE44]");
        appendTransportationBreakdown(lines, orderedPlaces);
        lines.add("[\uD569\uACC4]");
        lines.add("\uCD1D \uC2DD\uBE44: " + formatWon(mealTotal) + "\uC6D0");
        lines.add("\uCD1D \uC785\uC7A5\uAD8C: " + formatWon(admissionTotal) + "\uC6D0");
        lines.add("\uCD1D \uC219\uBC15\uBE44: " + formatWon(lodgingTotal) + "\uC6D0");
        lines.add("\uCD1D \uAD50\uD1B5\uBE44: " + formatWon(transportationTotal) + "\uC6D0");
        return TextUtils.join("\n", lines);
    }

    private void appendMealBreakdown(List<String> lines, List<Place> orderedPlaces) {
        boolean hasFood = false;
        for (Place place : orderedPlaces) {
            if (!isFoodPlace(place)) {
                continue;
            }
            hasFood = true;
            int mealCost = getMealCost(place);
            lines.add("- \uC2DD\uBE44 \u00B7 " + place.name + ": " + formatWon(mealCost) + "\uC6D0 x " + getTravelerCount() + "\uBA85 = " + formatWon(mealCost * getTravelerCount()) + "\uC6D0");
        }
        if (!hasFood) {
            lines.add("- \uC2DD\uBE44: 0\uC6D0");
        }
    }

    private void appendAdmissionBreakdown(List<String> lines, List<Place> orderedPlaces) {
        boolean hasAdmission = false;
        for (Place place : orderedPlaces) {
            if (!isAdmissionPlace(place)) {
                continue;
            }
            hasAdmission = true;
            AdmissionCost cost = getAdmissionCost(place);
            lines.add("- \uC785\uC7A5\uAD8C \u00B7 " + place.name + ": "
                    + "\uC131\uC778 " + formatWon(cost.adultWon) + "\uC6D0 x " + adultCount
                    + ", \uCCAD\uC18C\uB144 " + formatWon(cost.youthWon) + "\uC6D0 x " + youthCount
                    + ", \uB178\uC778 " + formatWon(cost.seniorWon) + "\uC6D0 x " + seniorCount
                    + ", \uC5B4\uB9B0\uC774 " + formatWon(cost.childWon) + "\uC6D0 x " + childCount
                    + " = " + formatWon(cost.total(adultCount, youthCount, seniorCount, childCount)) + "\uC6D0");
        }
        if (!hasAdmission) {
            lines.add("- \uC785\uC7A5\uAD8C: 0\uC6D0");
        }
    }

    private void appendLodgingBreakdown(List<String> lines, List<Place> orderedPlaces) {
        int totalNights = Math.max(1, estimateDurationDays(startDate, endDate) - 1);
        int stayCount = countStayPlaces(orderedPlaces);
        if (stayCount == 0) {
            lines.add("- \uC219\uBC15\uBE44: 0\uC6D0");
            return;
        }

        int stayIndex = 0;
        for (Place place : orderedPlaces) {
            if (!isStayPlace(place)) {
                continue;
            }
            int nights = allocatedNightsForStay(totalNights, stayCount, stayIndex);
            int nightlyCost = getLodgingNightlyCost(place);
            int subtotal = nightlyCost * nights;
            lines.add("- \uC219\uBC15 \u00B7 " + place.name + ": "
                    + formatWon(nightlyCost) + "\uC6D0 x " + nights + "\uBC15 = "
                    + formatWon(subtotal) + "\uC6D0");
            stayIndex++;
        }
    }

    private void appendTransportationBreakdown(List<String> lines, List<Place> orderedPlaces) {
        if (orderedPlaces.size() < 2) {
            lines.add("- \uAD50\uD1B5\uBE44: \uC774\uB3D9 \uAD6C\uAC04 \uC5C6\uC74C");
            return;
        }

        for (int i = 1; i < orderedPlaces.size(); i++) {
            Place from = orderedPlaces.get(i - 1);
            Place to = orderedPlaces.get(i);
            int moveCost = estimateMoveCost(from, to);
            lines.add("- \uAD50\uD1B5\uBE44 \u00B7 " + from.name + " -> " + to.name + ": "
                    + formatWon(moveCost) + "\uC6D0");
        }
    }

    private int estimateTransportationCost(List<Place> orderedPlaces) {
        int total = 0;
        for (int i = 1; i < orderedPlaces.size(); i++) {
            total += estimateMoveCost(orderedPlaces.get(i - 1), orderedPlaces.get(i));
        }
        return total;
    }

    private int estimateMoveCost(Place from, Place to) {
        if (!hasCoordinates(from) || !hasCoordinates(to)) {
            return 8000;
        }

        double distanceKm = distanceKm(from.latitude, from.longitude, to.latitude, to.longitude);
        String route = routePlan == null ? "" : routePlan.toLowerCase(Locale.KOREA);
        if (route.contains("도보") || route.contains("걷")) {
            return (int) Math.round(distanceKm * 300);
        }
        if (route.contains("렌터") || route.contains("렌트") || route.contains("자동차") || route.contains("차")) {
            int vehicles = Math.max(1, (int) Math.ceil(getTravelerCount() / 5.0));
            return (int) Math.round((12000 + distanceKm * 900) * vehicles);
        }
        if (route.contains("택시")) {
            int taxis = Math.max(1, (int) Math.ceil(getTravelerCount() / 4.0));
            return (int) Math.round((4800 + distanceKm * 1200) * taxis);
        }
        if (route.contains("자전거") || route.contains("따릉")) {
            return (int) Math.round(distanceKm * 500 * getTravelerCount());
        }
        return (int) Math.round((1500 + distanceKm * 350) * getTravelerCount());
    }

    private boolean hasCoordinates(Place place) {
        return place != null
                && !Double.isNaN(place.latitude)
                && !Double.isNaN(place.longitude)
                && Math.abs(place.latitude) > 0.000001
                && Math.abs(place.longitude) > 0.000001;
    }

    private int estimateLodgingCost(List<Place> orderedPlaces) {
        int stayCount = countStayPlaces(orderedPlaces);
        if (stayCount == 0) {
            return 0;
        }

        int total = 0;
        int totalNights = Math.max(1, estimateDurationDays(startDate, endDate) - 1);
        int stayIndex = 0;
        for (Place place : orderedPlaces) {
            if (!isStayPlace(place)) {
                continue;
            }
            total += getLodgingNightlyCost(place) * allocatedNightsForStay(totalNights, stayCount, stayIndex);
            stayIndex++;
        }
        return total;
    }

    private int countStayPlaces(List<Place> orderedPlaces) {
        int count = 0;
        for (Place place : orderedPlaces) {
            if (isStayPlace(place)) {
                count++;
            }
        }
        return count;
    }

    private int allocatedNightsForStay(int totalNights, int stayCount, int stayIndex) {
        int base = totalNights / stayCount;
        int remainder = totalNights % stayCount;
        return base + (stayIndex < remainder ? 1 : 0);
    }

    private int getLodgingNightlyCost(Place place) {
        Integer selectedCost = selectedLodgingNightlyCosts.get(placeKey(place));
        if (selectedCost != null && selectedCost > 0) {
            return selectedCost;
        }

        int nightlyCost = lodgingNightlyCostWon > 0 ? lodgingNightlyCostWon : 90000;
        if (budgetWon > 0) {
            int nights = Math.max(1, estimateDurationDays(startDate, endDate) - 1);
            int budgetBasedCost = Math.round((budgetWon * 0.35f) / nights);
            nightlyCost = Math.min(nightlyCost, Math.max(50000, budgetBasedCost));
        }
        return nightlyCost;
    }

    private int estimateMealCost(List<Place> orderedPlaces) {
        int total = 0;
        for (Place place : orderedPlaces) {
            if (!isFoodPlace(place)) {
                continue;
            }
            total += getMealCost(place) * getTravelerCount();
        }
        return total;
    }

    private int getMealCost(Place place) {
        Integer mealCost = selectedMealCosts.get(placeKey(place));
        return mealCost != null && mealCost > 0 ? mealCost : fallbackMealCost(place);
    }

    private int estimateAdmissionCost(List<Place> orderedPlaces) {
        int total = 0;
        for (Place place : orderedPlaces) {
            if (isAdmissionPlace(place)) {
                total += getAdmissionCost(place).total(adultCount, youthCount, seniorCount, childCount);
            }
        }
        return total;
    }

    private AdmissionCost getAdmissionCost(Place place) {
        AdmissionCost cost = selectedAdmissionCosts.get(placeKey(place));
        return cost == null || cost.isEmpty() ? fallbackAdmissionCost(place) : cost;
    }

    private AdmissionCost normalizeAdmissionCost(Place place, int adultWon, int youthWon, int seniorWon, int childWon) {
        AdmissionCost fallback = fallbackAdmissionCost(place);
        return new AdmissionCost(
                adultWon > 0 ? adultWon : fallback.adultWon,
                youthWon > 0 ? youthWon : fallback.youthWon,
                seniorWon > 0 ? seniorWon : fallback.seniorWon,
                childWon > 0 ? childWon : fallback.childWon
        );
    }

    private AdmissionCost fallbackAdmissionCost(Place place) {
        String text = ((place.name == null ? "" : place.name) + " "
                + (place.category == null ? "" : place.category) + " "
                + (place.description == null ? "" : place.description)).toLowerCase(Locale.KOREA);
        if (text.contains("\uB86F\uB370\uC6D4\uB4DC") || text.contains("\uC5D0\uBC84\uB79C\uB4DC") || text.contains("\uB180\uC774") || text.contains("\uD14C\uB9C8\uD30C\uD06C") || text.contains("\uC6D4\uB4DC")) {
            return new AdmissionCost(59000, 52000, 46000, 47000);
        }
        if (text.contains("\uC544\uCFE0\uC544") || text.contains("\uC804\uB9DD\uB300") || text.contains("\uBC15\uBB3C\uAD00") || text.contains("\uBBF8\uC220\uAD00")) {
            return new AdmissionCost(25000, 20000, 15000, 15000);
        }
        return new AdmissionCost(0, 0, 0, 0);
    }

    private boolean isAttractionPlace(Place place) {
        String category = place == null || place.category == null ? "" : place.category;
        return category.contains(CATEGORY_ATTRACTION) || category.contains("명소") || category.contains("문화");
    }

    private boolean isStayPlace(Place place) {
        String category = place == null || place.category == null ? "" : place.category;
        return category.contains(CATEGORY_STAY) || category.contains("숙박") || category.contains("호텔");
    }

    private boolean isFoodPlace(Place place) {
        String category = place == null || place.category == null ? "" : place.category;
        return category.contains(CATEGORY_FOOD) || category.contains("맛집") || category.contains("음식") || category.contains("식당");
    }

    private boolean isAdmissionPlace(Place place) {
        String text = ((place == null || place.name == null ? "" : place.name) + " "
                + (place == null || place.category == null ? "" : place.category) + " "
                + (place == null || place.description == null ? "" : place.description)).toLowerCase(Locale.KOREA);
        return text.contains("\uB86F\uB370\uC6D4\uB4DC")
                || text.contains("\uC5D0\uBC84\uB79C\uB4DC")
                || text.contains("\uB180\uC774")
                || text.contains("\uD14C\uB9C8\uD30C\uD06C")
                || text.contains("\uC6D4\uB4DC")
                || text.contains("\uC544\uCFE0\uC544")
                || text.contains("\uC804\uB9DD\uB300")
                || text.contains("\uBC15\uBB3C\uAD00")
                || text.contains("\uBBF8\uC220\uAD00");
    }

    private int fallbackMealCost(Place place) {
        String text = ((place.name == null ? "" : place.name) + " "
                + (place.category == null ? "" : place.category) + " "
                + (place.description == null ? "" : place.description)).toLowerCase(Locale.KOREA);
        if (text.contains("숯불") || text.contains("갈비") || text.contains("구이") || text.contains("고기")) {
            return 25000;
        }
        if (text.contains("회") || text.contains("스시") || text.contains("초밥")) {
            return 35000;
        }
        if (text.contains("카페") || text.contains("커피") || text.contains("디저트")) {
            return 9000;
        }
        return 16000;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private void showPlaceDialog(Place place) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_place_detail, null);

        TextView titleView = dialogView.findViewById(R.id.tv_dialog_place_title);
        TextView addressView = dialogView.findViewById(R.id.tv_dialog_place_address);
        TextView summaryView = dialogView.findViewById(R.id.tv_dialog_summary);
        TextView mapButton = dialogView.findViewById(R.id.btn_dialog_map);
        LinearLayout dialogSourcesContainer = dialogView.findViewById(R.id.dialog_sources_container);

        titleView.setText(place.name);
        addressView.setText(place.description == null || place.description.isEmpty() ? place.address : place.description);
        renderSources(dialogSourcesContainer, null);
        mapButton.setVisibility(hasCoordinates(place) ? View.VISIBLE : View.GONE);
        mapButton.setOnClickListener(v -> {
            dialog.dismiss();
            openPlaceOnMap(place);
        });
        dialogView.findViewById(R.id.btn_dialog_close).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(dialogView);
        dialog.show();

        backendClient.requestPlaceInsight(query, place, new BackendClient.PlaceInsightCallback() {
            @Override
            public void onSuccess(PlaceInsight result) {
                summaryView.setText(result.summary.isEmpty()
                        ? getString(R.string.related_articles_empty)
                        : result.summary);
                renderSources(dialogSourcesContainer, result.sources);
            }

            @Override
            public void onError(Exception error) {
                summaryView.setText(getString(R.string.related_articles_empty));
                renderSources(dialogSourcesContainer, null);
            }
        });
    }

    private void openPlaceOnMap(Place place) {
        if (!hasCoordinates(place) || getView() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("pickMode", false);
        args.putDouble("latitude", place.latitude);
        args.putDouble("longitude", place.longitude);
        args.putString("placeName", place.name);
        Navigation.findNavController(getView()).navigate(R.id.action_resultFragment_to_mapPickFragment, args);
    }

    private void renderSources(LinearLayout container, List<Source> sources) {
        if (container == null) {
            return;
        }
        container.removeAllViews();

        if (sources == null || sources.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.sources_empty);
            empty.setTextColor(getResources().getColor(R.color.ink_muted, requireContext().getTheme()));
            empty.setTextSize(12);
            empty.setPadding(dp(12), dp(10), dp(12), dp(10));
            empty.setBackgroundResource(R.drawable.bg_ai_box);
            container.addView(empty);
            return;
        }

        for (Source source : sources) {
            container.addView(createSourceCard(source));
        }
    }

    private View createSourceCard(Source source) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_ai_box);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);

        TextView title = new TextView(requireContext());
        title.setText(source.title);
        title.setTextColor(getResources().getColor(R.color.text_primary, requireContext().getTheme()));
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView meta = new TextView(requireContext());
        String sourceName = source.source == null || source.source.isEmpty() ? "출처 없음" : source.source;
        meta.setText(sourceName);
        meta.setTextColor(getResources().getColor(R.color.primary_dark, requireContext().getTheme()));
        meta.setTextSize(11);
        meta.setPadding(0, dp(4), 0, 0);
        card.addView(meta);

        if (source.summary != null && !source.summary.isEmpty()) {
            TextView body = new TextView(requireContext());
            body.setText(source.summary);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setTextColor(getResources().getColor(R.color.text_secondary, requireContext().getTheme()));
            body.setTextSize(11);
            body.setPadding(0, dp(4), 0, 0);
            card.addView(body);
        }

        if (source.url != null && !source.url.isEmpty()) {
            card.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source.url))));
        }
        return card;
    }

    private String joinTop(List<String> items) {
        if (items == null || items.isEmpty()) {
            return getString(R.string.no_data);
        }
        int count = Math.min(items.size(), 3);
        return TextUtils.join(", ", items.subList(0, count));
    }

    private int parseBudget(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.replaceAll("[^0-9]", "")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int parseCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.replaceAll("[^0-9]", "")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getTravelerCount() {
        return Math.max(1, adultCount + youthCount + seniorCount + childCount);
    }

    private void renderTripMeta(TextView tripMetaView) {
        boolean hasDates = startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty();
        boolean hasBudget = budgetWon > 0;
        boolean hasRoute = routePlan != null && !routePlan.isEmpty();
        if (!hasDates && !hasBudget && !hasRoute) {
            tripMetaView.setVisibility(View.GONE);
            return;
        }

        List<String> parts = new ArrayList<>();
        if (hasDates) {
            parts.add(startDate + " - " + endDate);
        }
        parts.add(hasBudget ? "예산 " + formatWon(budgetWon) + "원" : "예산 제한 없음");
        if (hasRoute) {
            parts.add("동선 " + routePlan);
        }
        tripMetaView.setText(getString(R.string.trip_meta_partial_format, TextUtils.join(" · ", parts)));
        tripMetaView.setVisibility(View.VISIBLE);
    }

    private void updateSelectedCoordText(TextView coordView) {
        String text = String.format(
                Locale.KOREA,
                getString(R.string.selected_coord_format),
                selectedLatitude,
                selectedLongitude
        );
        coordView.setText(text);
    }

    private void saveLastQuery(String value) {
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(MyTripFragment.KEY_LAST_QUERY, value)
                .apply();
    }

    private void saveTripPlanningState() {
        saveSelectedRouteState(routePlan == null ? "" : routePlan, estimateRouteBudget(routePlan, startDate, endDate));
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt(MyTripFragment.KEY_TOTAL_BUDGET, budgetWon)
                .putInt(MyTripFragment.KEY_TRAVELERS, getTravelerCount())
                .apply();
    }

    private void saveSelectedRouteState(String routeText, int estimatedRouteBudget) {
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(MyTripFragment.KEY_LAST_ROUTE_PLAN, routeText == null ? "" : routeText)
                .putInt(MyTripFragment.KEY_LAST_ESTIMATED_ROUTE_BUDGET, estimatedRouteBudget)
                .apply();
    }

    private int estimateRouteBudget(String routePlan, String startDate, String endDate) {
        String route = routePlan == null ? "" : routePlan.toLowerCase(Locale.KOREA);
        int days = estimateDurationDays(startDate, endDate);
        int dailyMoveCost = 12000;
        if (route.contains("도보") || route.contains("걷")) {
            dailyMoveCost = 5000;
        } else if (route.contains("렌터") || route.contains("렌트") || route.contains("자동차") || route.contains("차")) {
            dailyMoveCost = 55000;
        } else if (route.contains("택시")) {
            dailyMoveCost = 42000;
        } else if (route.contains("대중교통") || route.contains("지하철") || route.contains("버스")) {
            dailyMoveCost = 12000;
        } else if (route.contains("자전거") || route.contains("따릉")) {
            dailyMoveCost = 9000;
        }
        return dailyMoveCost * days;
    }

    private int estimateDurationDays(String startDate, String endDate) {
        if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
            return 3;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            long start = format.parse(startDate).getTime();
            long end = format.parse(endDate).getTime();
            long days = ((end - start) / 86400000L) + 1;
            return (int) Math.max(1, Math.min(30, days));
        } catch (Exception ignored) {
            return 3;
        }
    }

    private void saveLastLocation() {
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putFloat(MyTripFragment.KEY_LAST_LATITUDE, (float) selectedLatitude)
                .putFloat(MyTripFragment.KEY_LAST_LONGITUDE, (float) selectedLongitude)
                .apply();
    }

    private void runNearbySearch() {
        nearbyResultView.setVisibility(View.VISIBLE);
        nearbyResultView.setText(getString(R.string.map_nearby_loading));
        backendClient.requestNearby(selectedLatitude, selectedLongitude, 2000, new BackendClient.NearbyCallback() {
            @Override
            public void onSuccess(NearbyResult result) {
                nearbyResultView.setText(
                        getString(R.string.nearby_label_stay, joinTop(result.stays)) + "\n"
                                + getString(R.string.nearby_label_attraction, joinTop(result.attractions)) + "\n"
                                + getString(R.string.nearby_label_food, joinTop(result.restaurants))
                );
            }

            @Override
            public void onError(Exception error) {
                nearbyResultView.setText(getString(R.string.nearby_connection_help));
            }
        });
    }

    private String formatWon(int value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private static class AdmissionCost {
        final int adultWon;
        final int youthWon;
        final int seniorWon;
        final int childWon;

        AdmissionCost(int adultWon, int youthWon, int seniorWon, int childWon) {
            this.adultWon = adultWon;
            this.youthWon = youthWon;
            this.seniorWon = seniorWon;
            this.childWon = childWon;
        }

        int total(int adultCount, int youthCount, int seniorCount, int childCount) {
            return adultWon * adultCount
                    + youthWon * youthCount
                    + seniorWon * seniorCount
                    + childWon * childCount;
        }

        boolean isEmpty() {
            return adultWon <= 0 && youthWon <= 0 && seniorWon <= 0 && childWon <= 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
