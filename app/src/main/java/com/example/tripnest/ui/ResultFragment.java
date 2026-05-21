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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResultFragment extends Fragment {

    private static final String CATEGORY_ATTRACTION = "\uAD00\uAD11";
    private static final String CATEGORY_FOOD = "\uC74C\uC2DD";
    private static final String CATEGORY_STAY = "\uC219\uC18C";

    private final BackendClient backendClient = new BackendClient();
    private final List<Place> allPlaces = new ArrayList<>();

    private double selectedLatitude = 37.5665;
    private double selectedLongitude = 126.9780;
    private String query = "";
    private String selectedCategory = CATEGORY_ATTRACTION;

    private LinearLayout placeListContainer;
    private TextView attractionTab;
    private TextView foodTab;
    private TextView stayTab;
    private TextView nearbyResultView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        query = getArguments() != null ? getArguments().getString("query", "") : "";
        String startDate = getArguments() != null ? getArguments().getString("startDate", "") : "";
        String endDate = getArguments() != null ? getArguments().getString("endDate", "") : "";
        int budgetWon = parseBudget(getArguments() != null ? getArguments().getString("budgetWon", "") : "");

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

        view.findViewById(R.id.btn_search_nearby).setVisibility(View.GONE);

        attractionTab.setOnClickListener(v -> selectCategory(CATEGORY_ATTRACTION));
        foodTab.setOnClickListener(v -> selectCategory(CATEGORY_FOOD));
        stayTab.setOnClickListener(v -> selectCategory(CATEGORY_STAY));

        if (!query.isEmpty()) {
            titleView.setText(getString(R.string.result_title_format, query));
            queryView.setText(query);
            saveLastQuery(query);
        }

        renderTripMeta(tripMetaView, startDate, endDate, budgetWon);
        renderPlaceList();
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
                    runNearbySearch();
                });

        backendClient.requestRecommendations(query, startDate, endDate, budgetWon, new BackendClient.Callback() {
            @Override
            public void onSuccess(TripRecommendation recommendation) {
                summaryView.setText(recommendation.summary.isEmpty()
                        ? getString(R.string.gemini_summary_fallback)
                        : recommendation.summary);
                filterMessageView.setText(getString(R.string.ad_filter_message_format, recommendation.filteredAdCount));
                if (recommendation.places == null || recommendation.places.isEmpty()) {
                    allPlaces.clear();
                    renderPlaceList();
                    recommendationEmptyView.setText(R.string.no_recommendations);
                    recommendationEmptyView.setVisibility(View.VISIBLE);
                    return;
                }

                allPlaces.clear();
                allPlaces.addAll(recommendation.places);
                recommendationEmptyView.setVisibility(View.GONE);
                selectCategory(CATEGORY_ATTRACTION);
            }

            @Override
            public void onError(Exception error) {
                summaryView.setText(getString(R.string.recommendations_unavailable));
                filterMessageView.setText(getString(R.string.ad_filter_message));
                allPlaces.clear();
                renderPlaceList();
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

    // Keeps the three category buttons visually in sync with the selected list.
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

    // Uses the backend-normalized category first, so each tab shows only its own place type.
    private boolean isPlaceInSelectedCategory(Place place) {
        String category = place.category == null ? "" : place.category;
        if (CATEGORY_ATTRACTION.equals(selectedCategory)) {
            return category.contains(CATEGORY_ATTRACTION);
        }
        if (CATEGORY_FOOD.equals(selectedCategory)) {
            return category.contains(CATEGORY_FOOD) || category.contains("\uB9DB\uC9D1");
        }
        return category.contains(CATEGORY_STAY) || category.contains("\uC219\uBC15");
    }

    // Builds a compact clickable row. The full article summary opens in a BottomSheet.
    private View createPlaceRow(Place place, int index) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_ai_box);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        TextView title = new TextView(requireContext());
        title.setText(index + ". " + place.name);
        title.setTextColor(getResources().getColor(R.color.text_primary, requireContext().getTheme()));
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        row.addView(title);

        TextView body = new TextView(requireContext());
        body.setText(place.description == null || place.description.isEmpty() ? place.address : place.description);
        body.setTextColor(getResources().getColor(R.color.text_secondary, requireContext().getTheme()));
        body.setTextSize(12);
        body.setPadding(0, dp(4), 0, 0);
        row.addView(body);

        row.setOnClickListener(v -> showPlaceDialog(place));
        return row;
    }

    // Place detail lives in a modal so the main list does not jump or grow awkwardly.
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
        mapButton.setVisibility(Double.isNaN(place.latitude) || Double.isNaN(place.longitude) ? View.GONE : View.VISIBLE);
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

    // Opens the shared map screen in view-only mode with a marker at the selected place.
    private void openPlaceOnMap(Place place) {
        if (place == null || Double.isNaN(place.latitude) || Double.isNaN(place.longitude) || getView() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("pickMode", false);
        args.putDouble("latitude", place.latitude);
        args.putDouble("longitude", place.longitude);
        args.putString("placeName", place.name);
        Navigation.findNavController(getView()).navigate(R.id.action_resultFragment_to_mapPickFragment, args);
    }

    // Source cards behave like embedded previews and open the original page in the browser.
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
        String sourceName = source.source == null || source.source.isEmpty() ? "\uC6F9 \uCD9C\uCC98" : source.source;
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

    private void renderTripMeta(TextView tripMetaView, String startDate, String endDate, int budgetWon) {
        boolean hasDates = startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty();
        boolean hasBudget = budgetWon > 0;
        if (!hasDates && !hasBudget) {
            tripMetaView.setVisibility(View.GONE);
            return;
        }

        String budgetText = String.format(Locale.KOREA, "%,d", budgetWon);
        if (hasDates && hasBudget) {
            tripMetaView.setText(getString(R.string.trip_meta_format, startDate, endDate, budgetText));
        } else if (hasDates) {
            tripMetaView.setText(getString(R.string.trip_meta_partial_format, startDate + " - " + endDate));
        } else {
            tripMetaView.setText(getString(R.string.trip_meta_partial_format, "\uC608\uC0B0 " + budgetText + "\uC6D0"));
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
