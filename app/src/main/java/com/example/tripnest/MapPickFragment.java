package com.example.tripnest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MapPickFragment extends Fragment {

    private MapView mapView;
    private TextView coordView;
    private GeoPoint selectedPoint = new GeoPoint(37.5665, 126.9780);
    private boolean pickMode;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    moveToCurrentLocation();
                } else if (getView() != null) {
                    Snackbar.make(getView(), R.string.map_location_unavailable, Snackbar.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map_pick, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pickMode = getArguments() == null || getArguments().getBoolean("pickMode", true);
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        coordView = view.findViewById(R.id.tv_map_pick_coord);
        TextView titleView = view.findViewById(R.id.tv_map_title);
        View topBar = view.findViewById(R.id.map_top_bar);
        View pickPanel = view.findViewById(R.id.map_pick_panel);
        View centerPin = view.findViewById(R.id.tv_center_pin);
        View currentLocationButton = view.findViewById(R.id.btn_current_location);
        View floatingLocationButton = view.findViewById(R.id.btn_map_current_floating);
        View confirmButton = view.findViewById(R.id.btn_map_confirm);

        mapView = view.findViewById(R.id.osm_map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);
        mapView.getController().setCenter(selectedPoint);
        updateSelectedPointFromCenter();

        if (pickMode) {
            titleView.setText(R.string.map_pick_title);
            topBar.setVisibility(View.VISIBLE);
            pickPanel.setVisibility(View.VISIBLE);
            centerPin.setVisibility(View.VISIBLE);
            floatingLocationButton.setVisibility(View.GONE);
        } else {
            topBar.setVisibility(View.GONE);
            pickPanel.setVisibility(View.GONE);
            centerPin.setVisibility(View.GONE);
            floatingLocationButton.setVisibility(View.VISIBLE);
        }

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updateSelectedPointFromCenter();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updateSelectedPointFromCenter();
                return false;
            }
        });

        view.findViewById(R.id.btn_map_back).setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
        currentLocationButton.setOnClickListener(v -> requestCurrentLocation());
        floatingLocationButton.setOnClickListener(v -> requestCurrentLocation());
        confirmButton.setOnClickListener(v -> {
            updateSelectedPointFromCenter();
            Bundle result = new Bundle();
            result.putDouble("latitude", selectedPoint.getLatitude());
            result.putDouble("longitude", selectedPoint.getLongitude());
            getParentFragmentManager().setFragmentResult("map_pick_result", result);
            Navigation.findNavController(view).navigateUp();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    private void requestCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
            return;
        }
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void moveToCurrentLocation() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            showLocationUnavailable();
            return;
        }

        Location location = null;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (location == null) {
            showLocationUnavailable();
            return;
        }

        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        selectedPoint = point;
        mapView.getController().animateTo(point);
        updateCoordText();
    }

    private void showLocationUnavailable() {
        if (getView() != null) {
            Snackbar.make(getView(), R.string.map_location_unavailable, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateSelectedPointFromCenter() {
        if (mapView == null) {
            return;
        }
        IGeoPoint center = mapView.getMapCenter();
        selectedPoint = new GeoPoint(center.getLatitude(), center.getLongitude());
        updateCoordText();
    }

    private void updateCoordText() {
        if (coordView == null || !pickMode) {
            return;
        }
        String text = String.format(
                java.util.Locale.KOREA,
                getString(R.string.selected_coord_format),
                selectedPoint.getLatitude(),
                selectedPoint.getLongitude()
        );
        coordView.setText(text);
    }
}
