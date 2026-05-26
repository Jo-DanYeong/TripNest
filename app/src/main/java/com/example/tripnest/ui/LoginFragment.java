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

    private BackendClient backendClient;
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

        // 이미 로그인된 사용자는 인증 화면을 건너뛰고 바로 홈으로 보낸다.
        AuthSession session = new AuthSession(requireContext());
        backendClient = new BackendClient(requireContext());
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
        // 로그인/회원가입 전환은 같은 화면에서 필요한 필드와 문구만 바꿔준다.
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

        // 서버에 보내기 전, 사용자가 바로 고칠 수 있는 입력 오류는 클라이언트에서 먼저 잡는다.
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
        // 로그인과 회원가입이 성공 이후에는 같은 흐름이라 콜백을 공유한다.
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
        // 로그인 화면을 백스택에서 제거해야 뒤로가기로 인증 화면에 다시 돌아가지 않는다.
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build();
        Navigation.findNavController(view).navigate(R.id.homeFragment, null, options);
    }

    private void setLoading(boolean loading) {
        // 네트워크 요청 중에는 중복 제출과 모드 전환을 잠깐 막는다.
        submitButton.setEnabled(!loading);
        toggleView.setEnabled(!loading);
        submitButton.setText(loading
                ? R.string.auth_loading
                : registerMode ? R.string.auth_register_button : R.string.auth_login_button);
    }

    private String textOf(TextInputEditText input) {
        // TextInputEditText는 null을 돌려줄 수 있어서 호출부가 매번 방어하지 않게 한다.
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showMessage(View view, String message) {
        // 서버 오류 메시지가 비어 있으면 앱에서 준비한 기본 문구로 보여준다.
        Snackbar.make(view, TextUtils.isEmpty(message) ? getString(R.string.auth_error_default) : message, Snackbar.LENGTH_SHORT).show();
    }
}
