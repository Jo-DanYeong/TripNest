package com.example.tripnest;

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

        EditText searchInput = view.findViewById(R.id.et_search);
        View.OnClickListener openResults = v -> openResults(view, searchInput);

        view.findViewById(R.id.btn_search).setOnClickListener(openResults);
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

    private void openResults(@NonNull View view, @NonNull EditText searchInput) {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Snackbar.make(view, R.string.search_empty_message, Snackbar.LENGTH_SHORT).show();
            return;
        }

        Bundle args = new Bundle();
        args.putString("query", query);
        Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_resultFragment, args);
    }
}
