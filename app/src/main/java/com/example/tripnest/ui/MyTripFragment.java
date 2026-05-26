package com.example.tripnest.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.tripnest.R;
import com.example.tripnest.data.AuthSession;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public class MyTripFragment extends Fragment {

    static final String PREFS_NAME = "tripnest_state";
    static final String KEY_LAST_QUERY = "last_query";
    static final String KEY_LAST_LATITUDE = "last_latitude";
    static final String KEY_LAST_LONGITUDE = "last_longitude";
    static final String KEY_LAST_ROUTE_PLAN = "last_route_plan";
    static final String KEY_LAST_ESTIMATED_ROUTE_BUDGET = "last_estimated_route_budget";
    static final String KEY_TOTAL_BUDGET = "total_budget";
    static final String KEY_TRAVELERS = "travelers";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_trip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView titleView = view.findViewById(R.id.tv_my_trip_title);
        TextView bodyView = view.findViewById(R.id.tv_my_trip_body);
        TextView locationView = view.findViewById(R.id.tv_my_trip_location);
        TextView accountView = view.findViewById(R.id.tv_account);
        TextView routeEstimateView = view.findViewById(R.id.tv_budget_route_estimate);
        TextView totalView = view.findViewById(R.id.tv_budget_total);
        TextView leftView = view.findViewById(R.id.tv_budget_left);
        TextView splitView = view.findViewById(R.id.tv_budget_split);
        TextView summaryView = view.findViewById(R.id.tv_budget_summary);
        EditText totalInput = view.findViewById(R.id.et_budget_total);
        MaterialButton saveBudgetButton = view.findViewById(R.id.btn_save_budget);
        MaterialButton logoutButton = view.findViewById(R.id.btn_logout);

        AuthSession session = new AuthSession(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String displayName = session.getName().isEmpty() ? session.getEmail() : session.getName();
        accountView.setText(getString(R.string.auth_signed_in_as, displayName));
        logoutButton.setOnClickListener(v -> {
            session.clear();
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build();
            Navigation.findNavController(view).navigate(R.id.loginFragment, null, options);
        });

        renderLastTrip(prefs, titleView, bodyView, locationView);
        renderBudget(prefs, routeEstimateView, totalView, leftView, splitView, summaryView, totalInput);

        saveBudgetButton.setOnClickListener(v -> {
            int totalBudget = parseMoney(totalInput);
            prefs.edit()
                    .putInt(KEY_TOTAL_BUDGET, totalBudget)
                    .apply();
            renderBudget(prefs, routeEstimateView, totalView, leftView, splitView, summaryView, totalInput);
            Snackbar.make(view, R.string.budget_saved, Snackbar.LENGTH_SHORT).show();
        });
    }

    private void renderLastTrip(SharedPreferences prefs, TextView titleView, TextView bodyView, TextView locationView) {
        String lastQuery = prefs.getString(KEY_LAST_QUERY, "");
        float latitude = prefs.getFloat(KEY_LAST_LATITUDE, Float.NaN);
        float longitude = prefs.getFloat(KEY_LAST_LONGITUDE, Float.NaN);
        String routePlan = prefs.getString(KEY_LAST_ROUTE_PLAN, "");

        if (lastQuery == null || lastQuery.trim().isEmpty()) {
            titleView.setText(R.string.my_trip_empty_title);
            bodyView.setText(R.string.my_trip_empty_body);
            locationView.setVisibility(View.GONE);
            return;
        }

        titleView.setText(R.string.my_trip_recent);
        String body = getString(R.string.my_trip_destination_format, lastQuery);
        if (routePlan != null && !routePlan.trim().isEmpty()) {
            body += "\n" + getString(R.string.my_trip_route_format, routePlan);
        }
        bodyView.setText(body);

        if (!Float.isNaN(latitude) && !Float.isNaN(longitude)) {
            locationView.setText(String.format(
                    Locale.KOREA,
                    getString(R.string.my_trip_location_format),
                    latitude,
                    longitude
            ));
            locationView.setVisibility(View.VISIBLE);
        } else {
            locationView.setVisibility(View.GONE);
        }
    }

    private void renderBudget(SharedPreferences prefs,
                              TextView routeEstimateView,
                              TextView totalView,
                              TextView leftView,
                              TextView splitView,
                              TextView summaryView,
                              EditText totalInput) {
        int totalBudget = prefs.getInt(KEY_TOTAL_BUDGET, 0);
        int estimatedRouteBudget = prefs.getInt(KEY_LAST_ESTIMATED_ROUTE_BUDGET, 0);
        int travelers = Math.max(1, prefs.getInt(KEY_TRAVELERS, 1));

        totalInput.setText(totalBudget > 0 ? String.valueOf(totalBudget) : "");

        routeEstimateView.setText(estimatedRouteBudget > 0
                ? getString(R.string.budget_route_estimate_format, formatWon(estimatedRouteBudget))
                : getString(R.string.budget_unlimited));

        if (totalBudget <= 0) {
            totalView.setText("미설정");
            leftView.setText("제한 없음");
            splitView.setText(formatWon(estimatedRouteBudget / travelers) + "원");
            summaryView.setText(R.string.budget_unlimited);
            return;
        }

        int leftBudget = Math.max(0, totalBudget - estimatedRouteBudget);
        totalView.setText(formatWon(totalBudget) + "원");
        leftView.setText(formatWon(leftBudget) + "원");
        splitView.setText(formatWon(estimatedRouteBudget / travelers) + "원");
        summaryView.setText(getString(
                R.string.budget_summary_format,
                formatWon(totalBudget),
                formatWon(estimatedRouteBudget),
                formatWon(leftBudget)
        ) + "\n" + getString(R.string.budget_split_format, formatWon(estimatedRouteBudget / travelers)));
    }

    private int parseMoney(EditText input) {
        String value = input.getText() == null ? "" : input.getText().toString();
        try {
            return Math.max(0, Integer.parseInt(value.replaceAll("[^0-9]", "")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatWon(int value) {
        return String.format(Locale.KOREA, "%,d", value);
    }
}
