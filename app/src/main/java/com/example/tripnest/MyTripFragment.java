package com.example.tripnest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.example.tripnest.data.AuthSession;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class MyTripFragment extends Fragment {

    static final String PREFS_NAME = "tripnest_state";
    static final String KEY_LAST_QUERY = "last_query";
    static final String KEY_LAST_LATITUDE = "last_latitude";
    static final String KEY_LAST_LONGITUDE = "last_longitude";

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
        MaterialButton logoutButton = view.findViewById(R.id.btn_logout);
        AuthSession session = new AuthSession(requireContext());

        String displayName = session.getName().isEmpty() ? session.getEmail() : session.getName();
        accountView.setText(getString(R.string.auth_signed_in_as, displayName));
        logoutButton.setOnClickListener(v -> {
            session.clear();
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build();
            Navigation.findNavController(view).navigate(R.id.loginFragment, null, options);
        });

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastQuery = prefs.getString(KEY_LAST_QUERY, "");
        float latitude = prefs.getFloat(KEY_LAST_LATITUDE, Float.NaN);
        float longitude = prefs.getFloat(KEY_LAST_LONGITUDE, Float.NaN);

        if (lastQuery == null || lastQuery.trim().isEmpty()) {
            titleView.setText(R.string.my_trip_empty_title);
            bodyView.setText(R.string.my_trip_empty_body);
            locationView.setVisibility(View.GONE);
            return;
        }

        titleView.setText(R.string.my_trip_recent);
        bodyView.setText(getString(R.string.my_trip_destination_format, lastQuery));

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
}
