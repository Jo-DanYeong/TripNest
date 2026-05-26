package com.example.tripnest;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.tripnest.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ViewBinding으로 화면 요소를 잡아두면 하단 탭처럼 자주 쓰는 뷰를 안전하게 다룰 수 있다.
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 앱 전체의 화면 이동은 Navigation graph에 맡기고, Activity는 공통 UI만 관리한다.
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.mapPickFragment,
                R.id.myTripFragment
        ).build();

        // 로그인 화면에서는 하단 탭을 숨기고, 나머지 화면에서는 현재 위치에 맞게 선택 상태를 갱신한다.
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isLogin = destination.getId() == R.id.loginFragment;
            binding.bottomNav.setVisibility(isLogin ? View.GONE : View.VISIBLE);
            if (destination.getId() == R.id.homeFragment) {
                selectBottomTab(binding, R.id.nav_home);
            } else if (destination.getId() == R.id.mapPickFragment) {
                selectBottomTab(binding, R.id.nav_map);
            } else if (destination.getId() == R.id.myTripFragment) {
                selectBottomTab(binding, R.id.nav_my);
            }
        });

        // 홈은 루트 화면이라 새로 navigate하지 않고 백스택을 홈까지 되돌린다.
        binding.navHome.setOnClickListener(v -> {
            if (navController.getCurrentDestination() == null
                    || navController.getCurrentDestination().getId() != R.id.homeFragment) {
                navController.popBackStack(R.id.homeFragment, false);
            }
        });

        // 지도 탭은 위치 선택이 아닌 보기 모드로 연다.
        binding.navMap.setOnClickListener(v -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != R.id.mapPickFragment) {
                Bundle args = new Bundle();
                args.putBoolean("pickMode", false);
                navController.navigate(R.id.mapPickFragment, args);
            }
        });

        // 내 여행 화면은 저장된 검색/위치와 설정을 확인하는 개인 영역이다.
        binding.navMy.setOnClickListener(v -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != R.id.myTripFragment) {
                navController.navigate(R.id.myTripFragment);
            }
        });

    }

    private void selectBottomTab(ActivityMainBinding binding, int selectedId) {
        // 선택된 탭만 강조하고 나머지는 같은 규칙으로 차분하게 낮춘다.
        styleTab(binding.navHome, binding.navHomeIcon, binding.navHomeLabel, selectedId == R.id.nav_home);
        styleTab(binding.navMap, binding.navMapIcon, binding.navMapLabel, selectedId == R.id.nav_map);
        styleTab(binding.navMy, binding.navMyIcon, binding.navMyLabel, selectedId == R.id.nav_my);
    }

    private void styleTab(View tab, ImageView icon, TextView label, boolean selected) {
        // 배경과 아이콘 색을 한곳에서 바꿔야 탭 스타일이 서로 어긋나지 않는다.
        tab.setBackgroundResource(selected ? R.drawable.bg_nav_selected : R.drawable.bg_nav_unselected);
        int color = getColor(selected ? R.color.primary : R.color.text_secondary);
        icon.setColorFilter(color);
        label.setTextColor(color);
    }

    @Override
    public boolean onSupportNavigateUp() {
        // 시스템 뒤로가기와 상단 navigateUp 동작을 Navigation component 흐름에 맞춘다.
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
