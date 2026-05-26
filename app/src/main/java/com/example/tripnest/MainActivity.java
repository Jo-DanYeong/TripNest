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

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.homeFragment,
                R.id.mapPickFragment,
                R.id.myTripFragment
        ).build();

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isLogin = destination.getId() == R.id.loginFragment;
            binding.bottomNav.setVisibility(isLogin ? View.GONE : View.VISIBLE);

            if (destination.getId() == R.id.homeFragment || destination.getId() == R.id.resultFragment) {
                selectBottomTab(binding, R.id.nav_home);
            } else if (destination.getId() == R.id.mapPickFragment) {
                selectBottomTab(binding, R.id.nav_map);
            } else if (destination.getId() == R.id.myTripFragment) {
                selectBottomTab(binding, R.id.nav_my);
            }
        });

        binding.navHome.setOnClickListener(v -> {
            if (navController.getCurrentDestination() == null
                    || navController.getCurrentDestination().getId() == R.id.homeFragment
                    || navController.getCurrentDestination().getId() == R.id.resultFragment) {
                return;
            }

            if (!navController.popBackStack(R.id.resultFragment, false)) {
                navController.popBackStack(R.id.homeFragment, false);
            }
        });

        binding.navMap.setOnClickListener(v -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != R.id.mapPickFragment) {
                Bundle args = new Bundle();
                args.putBoolean("pickMode", false);
                navController.navigate(R.id.mapPickFragment, args);
            }
        });

        binding.navMy.setOnClickListener(v -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != R.id.myTripFragment) {
                navController.navigate(R.id.myTripFragment);
            }
        });
    }

    private void selectBottomTab(ActivityMainBinding binding, int selectedId) {
        styleTab(binding.navHome, binding.navHomeIcon, binding.navHomeLabel, selectedId == R.id.nav_home);
        styleTab(binding.navMap, binding.navMapIcon, binding.navMapLabel, selectedId == R.id.nav_map);
        styleTab(binding.navMy, binding.navMyIcon, binding.navMyLabel, selectedId == R.id.nav_my);
    }

    private void styleTab(View tab, ImageView icon, TextView label, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_nav_selected : R.drawable.bg_nav_unselected);
        int color = getColor(selected ? R.color.primary : R.color.text_secondary);
        icon.setColorFilter(color);
        label.setTextColor(color);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
