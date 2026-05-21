package com.example.tripnest;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tripnest.data.BackendClient;

import java.util.List;
import java.util.Locale;

public class ResultFragment extends Fragment {

    private final BackendClient backendClient = new BackendClient();
    private double selectedLatitude = 37.5665;
    private double selectedLongitude = 126.9780;

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

        String query = getArguments() != null ? getArguments().getString("query", "") : "";
        TextView titleView = view.findViewById(R.id.tv_destination);
        TextView queryView = view.findViewById(R.id.et_search);
        TextView summaryView = view.findViewById(R.id.tv_ai_summary);
        TextView filterMessageView = view.findViewById(R.id.tv_ad_filter_message);
        TextView coordView = view.findViewById(R.id.tv_selected_coord);
        TextView recommendationEmptyView = view.findViewById(R.id.tv_recommendation_empty);
        nearbyResultView = view.findViewById(R.id.tv_nearby_result);

        View nearbySearchButton = view.findViewById(R.id.btn_search_nearby);
        nearbySearchButton.setVisibility(View.GONE);

        View[] placeCards = {
                view.findViewById(R.id.card_place_1),
                view.findViewById(R.id.card_place_2),
                view.findViewById(R.id.card_place_3)
        };
        TextView[] placeNames = {
                view.findViewById(R.id.tv_place_1_name),
                view.findViewById(R.id.tv_place_2_name),
                view.findViewById(R.id.tv_place_3_name)
        };
        TextView[] placeDescriptions = {
                view.findViewById(R.id.tv_place_1_description),
                view.findViewById(R.id.tv_place_2_description),
                view.findViewById(R.id.tv_place_3_description)
        };
        TextView[] placeCategories = {
                view.findViewById(R.id.tv_place_1_category),
                view.findViewById(R.id.tv_place_2_category),
                view.findViewById(R.id.tv_place_3_category)
        };

        if (!query.isEmpty()) {
            titleView.setText(getString(R.string.result_title_format, query));
            queryView.setText(query);
            saveLastQuery(query);
        }
        hideAllCards(placeCards);
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

        backendClient.requestRecommendations(query, new BackendClient.Callback() {
            @Override
            public void onSuccess(BackendClient.TripRecommendation recommendation) {
                summaryView.setText(
                        recommendation.summary.isEmpty()
                                ? getString(R.string.gemini_summary_fallback)
                                : recommendation.summary
                );
                filterMessageView.setText(
                        getString(R.string.ad_filter_message_format, recommendation.filteredAdCount)
                );

                if (recommendation.places == null || recommendation.places.isEmpty()) {
                    hideAllCards(placeCards);
                    recommendationEmptyView.setText(R.string.no_recommendations);
                    recommendationEmptyView.setVisibility(View.VISIBLE);
                    return;
                }
                recommendationEmptyView.setVisibility(View.GONE);
                renderPlaces(recommendation.places, placeCards, placeNames, placeDescriptions, placeCategories);
            }

            @Override
            public void onError(Exception error) {
                summaryView.setText(getString(R.string.recommendations_unavailable));
                filterMessageView.setText(getString(R.string.ad_filter_message));
                hideAllCards(placeCards);
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

    private String joinTop(List<String> items) {
        if (items == null || items.isEmpty()) {
            return getString(R.string.no_data);
        }
        int count = Math.min(items.size(), 3);
        return TextUtils.join(", ", items.subList(0, count));
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

    private void saveLastQuery(String query) {
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(MyTripFragment.KEY_LAST_QUERY, query)
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

    private void hideAllCards(View[] cards) {
        for (View card : cards) {
            card.setVisibility(View.GONE);
        }
    }

    private void runNearbySearch() {
        nearbyResultView.setVisibility(View.VISIBLE);
        nearbyResultView.setText(getString(R.string.map_nearby_loading));
        backendClient.requestNearby(selectedLatitude, selectedLongitude, 2000, new BackendClient.NearbyCallback() {
            @Override
            public void onSuccess(BackendClient.NearbyResult result) {
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

    private void renderPlaces(List<BackendClient.Place> places,
                              View[] cards,
                              TextView[] names,
                              TextView[] descriptions,
                              TextView[] categories) {
        hideAllCards(cards);
        int count = Math.min(places.size(), cards.length);
        for (int i = 0; i < count; i++) {
            BackendClient.Place place = places.get(i);
            names[i].setText(place.name);
            descriptions[i].setText(place.description);
            categories[i].setText(place.category);
            cards[i].setVisibility(View.VISIBLE);
        }
    }
}
