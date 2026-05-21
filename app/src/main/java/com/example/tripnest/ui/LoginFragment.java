package com.example.tripnest.ui;

import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.tripnest.model.AuthResult;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginFragment extends Fragment {

    private final BackendClient backendClient = new BackendClient();
    private boolean registerMode = false;

    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton submitButton;
    private TextView titleView;
    private TextView subtitleView;
    private TextView toggleView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AuthSession session = new AuthSession(requireContext());
        if (session.isSignedIn()) {
            openHome(view);
            return;
        }

        nameLayout = view.findViewById(R.id.layout_name);
        nameInput = view.findViewById(R.id.et_name);
        emailInput = view.findViewById(R.id.et_email);
        passwordInput = view.findViewById(R.id.et_password);
        submitButton = view.findViewById(R.id.btn_auth_submit);
        titleView = view.findViewById(R.id.tv_auth_title);
        subtitleView = view.findViewById(R.id.tv_auth_subtitle);
        toggleView = view.findViewById(R.id.tv_auth_toggle);

        submitButton.setOnClickListener(v -> submit(view, session));
        toggleView.setOnClickListener(v -> {
            registerMode = !registerMode;
            renderMode();
        });
        renderMode();
    }

    private void renderMode() {
        nameLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        titleView.setText(registerMode ? R.string.auth_register_title : R.string.auth_login_title);
        subtitleView.setText(registerMode ? R.string.auth_register_subtitle : R.string.auth_login_subtitle);
        submitButton.setText(registerMode ? R.string.auth_register_button : R.string.auth_login_button);
        toggleView.setText(registerMode ? R.string.auth_toggle_login : R.string.auth_toggle_register);
    }

    private void submit(View view, AuthSession session) {
        String name = textOf(nameInput);
        String email = textOf(emailInput);
        String password = textOf(passwordInput);

        if (registerMode && name.isEmpty()) {
            showMessage(view, getString(R.string.auth_name_required));
            return;
        }
        if (email.isEmpty() || !email.contains("@")) {
            showMessage(view, getString(R.string.auth_email_required));
            return;
        }
        if (password.length() < 8) {
            showMessage(view, getString(R.string.auth_password_required));
            return;
        }

        setLoading(true);
        BackendClient.AuthCallback callback = new BackendClient.AuthCallback() {
            @Override
            public void onSuccess(AuthResult result) {
                setLoading(false);
                session.save(result);
                openHome(view);
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                showMessage(view, error.getMessage());
            }
        };

        if (registerMode) {
            backendClient.register(name, email, password, callback);
        } else {
            backendClient.login(email, password, callback);
        }
    }

    private void openHome(View view) {
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build();
        Navigation.findNavController(view).navigate(R.id.homeFragment, null, options);
    }

    private void setLoading(boolean loading) {
        submitButton.setEnabled(!loading);
        toggleView.setEnabled(!loading);
        submitButton.setText(loading
                ? R.string.auth_loading
                : registerMode ? R.string.auth_register_button : R.string.auth_login_button);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showMessage(View view, String message) {
        Snackbar.make(view, TextUtils.isEmpty(message) ? getString(R.string.auth_error_default) : message, Snackbar.LENGTH_SHORT).show();
    }
}
