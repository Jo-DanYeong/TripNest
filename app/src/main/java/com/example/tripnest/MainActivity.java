package com.example.tripnest;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        EdgeToEdge.enable(this);

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
            if (destination.getId() == R.id.homeFragment) {
                binding.bottomNav.setSelectedItemId(R.id.nav_home);
            } else if (destination.getId() == R.id.mapPickFragment) {
                binding.bottomNav.setSelectedItemId(R.id.nav_map);
            } else if (destination.getId() == R.id.myTripFragment) {
                binding.bottomNav.setSelectedItemId(R.id.nav_my);
            }
        });

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                if (navController.getCurrentDestination() == null
                        || navController.getCurrentDestination().getId() != R.id.homeFragment) {
                    navController.popBackStack(R.id.homeFragment, false);
                }
                return true;
            }

            if (itemId == R.id.nav_map) {
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != R.id.mapPickFragment) {
                    Bundle args = new Bundle();
                    args.putBoolean("pickMode", false);
                    navController.navigate(R.id.mapPickFragment, args);
                }
                return true;
            }

            if (itemId == R.id.nav_my) {
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != R.id.myTripFragment) {
                    navController.navigate(R.id.myTripFragment);
                }
                return true;
            }

            return false;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
