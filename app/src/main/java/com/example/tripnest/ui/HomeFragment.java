package com.example.tripnest.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tripnest.R;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText searchInput = view.findViewById(R.id.et_search);
        EditText startDateInput = view.findViewById(R.id.et_start_date);
        EditText endDateInput = view.findViewById(R.id.et_end_date);
        EditText budgetInput = view.findViewById(R.id.et_budget);
        Spinner transportSpinner = view.findViewById(R.id.spinner_transport);
        Spinner adultSpinner = view.findViewById(R.id.spinner_adult);
        Spinner youthSpinner = view.findViewById(R.id.spinner_youth);
        Spinner seniorSpinner = view.findViewById(R.id.spinner_senior);
        Spinner childSpinner = view.findViewById(R.id.spinner_child);

        setupDateInput(startDateInput);
        setupDateInput(endDateInput);
        setupTransportSpinner(transportSpinner,"transport");
        setupTransportSpinner(adultSpinner,"adult");
        setupTransportSpinner(youthSpinner,"youth");
        setupTransportSpinner(seniorSpinner,"senior");
        setupTransportSpinner(childSpinner,"child");

        View.OnClickListener openResults = v ->
                openResults(
                        view,
                        searchInput,
                        startDateInput,
                        endDateInput,
                        budgetInput,
                        transportSpinner,
                        adultSpinner,
                        youthSpinner,
                        seniorSpinner,
                        childSpinner
                );

        view.findViewById(R.id.btn_search).setOnClickListener(openResults);
        view.findViewById(R.id.card_feature_recommendation).setOnClickListener(openResults);
        view.findViewById(R.id.card_feature_map).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("pickMode", true);
            Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_mapPickFragment, args);
        });
        view.findViewById(R.id.card_feature_summary).setOnClickListener(v -> openSelectedTripSummary(view));
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (isSearchAction || isEnter) {
                openResults.onClick(textView);
                return true;
            }
            return false;
        });
    }

    private void setupTransportSpinner(Spinner spinner,String option) {
        int temp = 0;
        switch (option){
            case "transport":
                temp = R.array.transport_options;
                break;
            case "adult":
                temp = R.array.trip_adult;
                break;
            case "youth":
                temp = R.array.trip_youth;
                break;
            case "senior":
                temp = R.array.trip_senior;
                break;
            case "child":
                temp = R.array.trip_child;
                break;
        }
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                temp,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupDateInput(EditText input) {
        input.setFocusable(false);
        input.setCursorVisible(false);
        input.setOnClickListener(v -> showDatePicker(input));
    }

    private void showDatePicker(EditText target) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) ->
                        target.setText(String.format(Locale.KOREA, "%04d-%02d-%02d", year, month + 1, dayOfMonth)),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void openResults(@NonNull View view,
                             @NonNull EditText searchInput,
                             @NonNull EditText startDateInput,
                             @NonNull EditText endDateInput,
                             @NonNull EditText budgetInput,
                             @NonNull Spinner transportSpinner,
                             @NonNull Spinner adultSpinner,
                             @NonNull Spinner youthSpinner,
                             @NonNull Spinner seniorSpinner,
                             @NonNull Spinner childSpinner) {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Snackbar.make(view, R.string.search_empty_message, Snackbar.LENGTH_SHORT).show();
            return;
        }

        String startDate = startDateInput.getText().toString().trim();
        String endDate = endDateInput.getText().toString().trim();
        if (startDate.isEmpty() && endDate.isEmpty()) {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().getTime());
            startDate = today;
            endDate = today;
        } else if (startDate.isEmpty()) {
            startDate = endDate;
        } else if (endDate.isEmpty()) {
            endDate = startDate;
        }

        Bundle args = new Bundle();
        String adultCnt = normalizeCount(adultSpinner,0),
                youthCnt = normalizeCount(youthSpinner,0),
                seniorCnt = normalizeCount(seniorSpinner,0),
                childCnt = normalizeCount(childSpinner,0);

        if (adultCnt.equals("0") && youthCnt.equals("0") && seniorCnt.equals("0") && childCnt.equals("0")){
            Snackbar.make(view, R.string.input_trip_headcount, Snackbar.LENGTH_SHORT).show();
            return;
        }
        args.putString("query", query);
        args.putString("startDate", startDate);
        args.putString("endDate", endDate);
        args.putString("budgetWon", budgetInput.getText().toString().trim());
        args.putString("routePlan", String.valueOf(transportSpinner.getSelectedItem()));
        args.putString("adultCount", normalizeCount(adultSpinner,0));
        args.putString("youthCount", normalizeCount(youthSpinner,0));
        args.putString("seniorCount", normalizeCount(seniorSpinner,0));
        args.putString("childCount", normalizeCount(childSpinner,0));
        Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_resultFragment, args);
    }

    private void openSelectedTripSummary(@NonNull View view) {
        SharedPreferences prefs = requireContext().getSharedPreferences(MyTripFragment.PREFS_NAME, Context.MODE_PRIVATE);
        String selectedRoute = prefs.getString(MyTripFragment.KEY_LAST_ROUTE_PLAN, "");
        if (selectedRoute == null || selectedRoute.trim().isEmpty()) {
            Snackbar.make(view, R.string.selected_summary_empty_message, Snackbar.LENGTH_SHORT).show();
            return;
        }
        Navigation.findNavController(view).navigate(R.id.myTripFragment);
    }

    private String normalizeCount(Spinner input, int fallback) {
        // 1. 스피너에서 텍스트를 가져오고 null이면 fallback 처리
        String strCnt = input.getSelectedItem() == null ? "" : input.getSelectedItem().toString().trim();
        // 2. 숫자가 아닌 모든 문자 제거 (예: "성인 3명" -> "3")
        strCnt = strCnt.replaceAll("[^0-9]", "");
        // 3. 비어있으면 fallback 반환, 숫자가 있으면 그대로 반환
        return strCnt.isEmpty() ? String.valueOf(fallback) : strCnt;
    }
}
