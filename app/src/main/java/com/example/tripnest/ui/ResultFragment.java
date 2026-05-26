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

    private BackendClient backendClient;
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
        // 결과 화면은 추천 요청, 카테고리 탭, 지도 선택 결과를 한 번에 이어주는 중심 화면이다.
        backendClient = new BackendClient(requireContext());

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

        // 지도 화면에서 선택한 좌표가 돌아오면 화면 표시와 주변 검색을 바로 갱신한다.
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

        // 처음 진입하면 홈에서 받은 조건으로 추천 목록과 요약을 요청한다.
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
        // 카테고리를 바꾸면 탭 색과 장소 목록을 함께 다시 그린다.
        selectedCategory = category;
        updateCategoryTabs();
        renderPlaceList();
    }

    // 세 개의 카테고리 버튼이 현재 목록 상태와 어긋나지 않게 맞춘다.
    private void updateCategoryTabs() {
        styleCategoryTab(attractionTab, CATEGORY_ATTRACTION.equals(selectedCategory));
        styleCategoryTab(foodTab, CATEGORY_FOOD.equals(selectedCategory));
        styleCategoryTab(stayTab, CATEGORY_STAY.equals(selectedCategory));
    }

    private void styleCategoryTab(TextView tab, boolean selected) {
        // 선택된 탭만 진한 배경을 쓰고, 나머지는 같은 pill 스타일로 낮춘다.
        tab.setBackgroundResource(selected ? R.drawable.bg_badge_green : R.drawable.bg_result_pill);
        tab.setTextColor(getResources().getColor(selected ? R.color.white : R.color.primary_dark, requireContext().getTheme()));
    }

    private void renderPlaceList() {
        // 기존 목록을 지우고 현재 카테고리에 맞는 장소만 다시 쌓는다.
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

    // 백엔드가 정리한 카테고리를 우선 사용해서 탭마다 자기 종류의 장소만 보이게 한다.
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

    // 목록에서는 핵심만 작게 보여주고, 자세한 글 요약은 바텀시트에서 연다.
    private View createPlaceRow(Place place, int index) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_ai_box);
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

        row.setOnClickListener(v -> showPlaceDialog(place));
        return row;
    }

    // 장소 상세는 모달로 띄워서 목록 화면의 스크롤 위치가 갑자기 흔들리지 않게 한다.
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

    // 선택한 장소는 같은 지도 화면을 보기 모드로 열어 마커만 표시한다.
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

    // 출처 카드는 미리보기처럼 보이게 하고, 누르면 원문 페이지로 이동한다.
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
        // 출처 제목, 매체명, 짧은 요약을 한 카드에 묶어 신뢰 근거를 남긴다.
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
        // 주변 검색 결과는 너무 길어지지 않도록 최대 세 개까지만 한 줄로 보여준다.
        if (items == null || items.isEmpty()) {
            return getString(R.string.no_data);
        }
        int count = Math.min(items.size(), 3);
        return TextUtils.join(", ", items.subList(0, count));
    }

    private int parseBudget(String value) {
        // 사용자가 쉼표나 원 단위를 붙여도 숫자만 뽑아 예산으로 사용한다.
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
        // 날짜나 예산 중 하나라도 있으면 결과 상단에 입력 조건을 짧게 남긴다.
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
        // 지도에서 돌아온 좌표를 사용자가 확인할 수 있는 형식으로 표시한다.
        String text = String.format(
                Locale.KOREA,
                getString(R.string.selected_coord_format),
                selectedLatitude,
                selectedLongitude
        );
        coordView.setText(text);
    }

    private void saveLastQuery(String value) {
        // 내 여행 화면에서 최근 검색지를 보여주기 위해 마지막 검색어를 저장한다.
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(MyTripFragment.KEY_LAST_QUERY, value)
                .apply();
    }

    private void saveLastLocation() {
        // 마지막 지도 좌표도 함께 저장해 내 여행 화면에서 이어 볼 수 있게 한다.
        requireContext()
                .getSharedPreferences(MyTripFragment.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putFloat(MyTripFragment.KEY_LAST_LATITUDE, (float) selectedLatitude)
                .putFloat(MyTripFragment.KEY_LAST_LONGITUDE, (float) selectedLongitude)
                .apply();
    }

    private void runNearbySearch() {
        // 지도 중심을 바꾼 뒤에는 주변 장소를 새로 불러와 현재 위치 기준으로 보정한다.
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
