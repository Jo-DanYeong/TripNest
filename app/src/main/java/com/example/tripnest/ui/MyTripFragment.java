package com.example.tripnest.ui;

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

import com.example.tripnest.R;
import com.example.tripnest.data.AuthSession;
import com.example.tripnest.data.BackendClient;
import com.example.tripnest.data.ServerConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

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

        // 내 여행 화면은 최근 여행 정보와 개발용 서버 설정을 한곳에서 관리한다.
        TextView titleView = view.findViewById(R.id.tv_my_trip_title);
        TextView bodyView = view.findViewById(R.id.tv_my_trip_body);
        TextView locationView = view.findViewById(R.id.tv_my_trip_location);
        TextView accountView = view.findViewById(R.id.tv_account);
        TextInputEditText serverUrlInput = view.findViewById(R.id.et_server_url);
        TextView serverStatusView = view.findViewById(R.id.tv_server_status);
        MaterialButton saveServerButton = view.findViewById(R.id.btn_save_server);
        MaterialButton testServerButton = view.findViewById(R.id.btn_test_server);
        MaterialButton logoutButton = view.findViewById(R.id.btn_logout);
        AuthSession session = new AuthSession(requireContext());
        ServerConfig serverConfig = new ServerConfig(requireContext());
        BackendClient backendClient = new BackendClient(requireContext());

        // 저장된 서버 주소를 먼저 보여줘야 사용자가 현재 연결 대상을 바로 확인할 수 있다.
        serverUrlInput.setText(serverConfig.getBaseUrl());
        serverStatusView.setText(getString(R.string.server_current_format, serverConfig.getBaseUrl()));
        saveServerButton.setOnClickListener(v -> {
            String value = textOf(serverUrlInput);
            // Android cleartext/HTTPS 정책과 무관하게 명시적인 URL 형식만 받는다.
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                showMessage(view, getString(R.string.server_url_invalid));
                return;
            }
            serverConfig.saveBaseUrl(value);
            serverStatusView.setText(getString(R.string.server_saved_format, serverConfig.getBaseUrl()));
            showMessage(view, getString(R.string.server_saved));
        });
        testServerButton.setOnClickListener(v -> {
            String value = textOf(serverUrlInput);
            // 테스트 전에 저장까지 해두면 BackendClient가 같은 주소로 health check를 보낸다.
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                showMessage(view, getString(R.string.server_url_invalid));
                return;
            }
            serverConfig.saveBaseUrl(value);
            serverStatusView.setText(R.string.server_testing);
            testServerButton.setEnabled(false);
            backendClient.checkHealth(new BackendClient.HealthCallback() {
                @Override
                public void onSuccess(String serviceName) {
                    testServerButton.setEnabled(true);
                    serverStatusView.setText(getString(R.string.server_connected_format, serviceName));
                }

                @Override
                public void onError(Exception error) {
                    testServerButton.setEnabled(true);
                    serverStatusView.setText(getString(R.string.server_failed_format, error.getMessage()));
                }
            });
        });

        // 계정 표시는 이름이 없을 때 이메일로 대체한다.
        String displayName = session.getName().isEmpty() ? session.getEmail() : session.getName();
        accountView.setText(getString(R.string.auth_signed_in_as, displayName));
        logoutButton.setOnClickListener(v -> {
            // 로그아웃 후에는 내비게이션 백스택을 비워 인증 화면으로 확실히 돌아간다.
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

        // 아직 검색 기록이 없으면 빈 상태 문구를 유지한다.
        if (lastQuery == null || lastQuery.trim().isEmpty()) {
            titleView.setText(R.string.my_trip_empty_title);
            bodyView.setText(R.string.my_trip_empty_body);
            locationView.setVisibility(View.GONE);
            return;
        }

        titleView.setText(R.string.my_trip_recent);
        bodyView.setText(getString(R.string.my_trip_destination_format, lastQuery));

        // 지도에서 위치를 선택한 적이 있을 때만 좌표 영역을 보여준다.
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

    private String textOf(TextInputEditText input) {
        // 설정 입력값은 앞뒤 공백을 제거한 뒤 저장/검증한다.
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showMessage(View view, String message) {
        // 짧은 설정 피드백은 Snackbar로 화면 흐름을 끊지 않게 보여준다.
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }
}
