package com.example.tripnest.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.tripnest.R;
import com.google.android.material.snackbar.Snackbar;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 홈에서 받은 검색 조건은 결과 화면으로 그대로 넘긴다.
        EditText searchInput = view.findViewById(R.id.et_search);
        EditText startDateInput = view.findViewById(R.id.et_start_date);
        EditText endDateInput = view.findViewById(R.id.et_end_date);
        EditText budgetInput = view.findViewById(R.id.et_budget);
        View.OnClickListener openResults = v -> openResults(view, searchInput, startDateInput, endDateInput, budgetInput);

        view.findViewById(R.id.btn_search).setOnClickListener(openResults);
        searchInput.setOnEditorActionListener((textView, actionId, event) -> {
            // 키보드의 검색 버튼과 물리 Enter 키를 같은 검색 동작으로 처리한다.
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

    private void openResults(@NonNull View view,
                             @NonNull EditText searchInput,
                             @NonNull EditText startDateInput,
                             @NonNull EditText endDateInput,
                             @NonNull EditText budgetInput) {
        String query = searchInput.getText().toString().trim();
        // 목적지가 없으면 추천 요청 자체가 의미 없어서 화면 이동 전에 막는다.
        if (query.isEmpty()) {
            Snackbar.make(view, R.string.search_empty_message, Snackbar.LENGTH_SHORT).show();
            return;
        }

        // ResultFragment가 API 요청과 화면 표시를 모두 시작할 수 있도록 최소 입력값을 묶어 전달한다.
        Bundle args = new Bundle();
        args.putString("query", query);
        args.putString("startDate", startDateInput.getText().toString().trim());
        args.putString("endDate", endDateInput.getText().toString().trim());
        args.putString("budgetWon", budgetInput.getText().toString().trim());
        args.putString("routePlan", String.valueOf(transportSpinner.getSelectedItem()));
        args.putString("adultCount", normalizeCount(adultSpinner,0));
        args.putString("youthCount", normalizeCount(youthSpinner,0));
        args.putString("seniorCount", normalizeCount(seniorSpinner,0));
        args.putString("childCount", normalizeCount(childSpinner,0));
        Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_resultFragment, args);
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
