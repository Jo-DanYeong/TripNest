package com.example.tripnest.ui;

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

import com.example.tripnest.R;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapPickFragment extends Fragment {

    private MapView mapView;
    private TextView coordView;
    private GeoPoint selectedPoint = new GeoPoint(37.5665, 126.9780);
    private boolean pickMode;

    // 권한 요청 결과는 화면이 살아 있는 동안만 처리하고, 허용되면 즉시 현재 위치로 이동한다.
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
        // pickMode=true는 위치 선택용, false는 특정 장소를 보여주는 보기 전용 모드다.
        pickMode = getArguments() == null || getArguments().getBoolean("pickMode", true);
        double initialLatitude = getArguments() == null ? Double.NaN : getArguments().getDouble("latitude", Double.NaN);
        double initialLongitude = getArguments() == null ? Double.NaN : getArguments().getDouble("longitude", Double.NaN);
        String placeName = getArguments() == null ? "" : getArguments().getString("placeName", "");
        if (Double.isFinite(initialLatitude) && Double.isFinite(initialLongitude)) {
            selectedPoint = new GeoPoint(initialLatitude, initialLongitude);
        }
        // osmdroid는 앱별 User-Agent가 필요해서 패키지명을 사용한다.
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        coordView = view.findViewById(R.id.tv_map_pick_coord);
        TextView titleView = view.findViewById(R.id.tv_map_title);
        View topBar = view.findViewById(R.id.map_top_bar);
        View pickPanel = view.findViewById(R.id.map_pick_panel);
        View centerPin = view.findViewById(R.id.tv_center_pin);
        View currentLocationButton = view.findViewById(R.id.btn_current_location);
        View floatingLocationButton = view.findViewById(R.id.btn_map_current_floating);
        View confirmButton = view.findViewById(R.id.btn_map_confirm);

        // 지도는 서울 시청 좌표를 기본값으로 두고, 인자로 좌표가 오면 그 위치에서 시작한다.
        mapView = view.findViewById(R.id.osm_map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);
        mapView.getController().setCenter(selectedPoint);
        if (!placeName.isEmpty()) {
            addPlaceMarker(placeName);
        }
        updateSelectedPointFromCenter();

        // 같은 지도 화면을 선택용/보기용으로 함께 쓰기 위해 필요한 패널만 보여준다.
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

        // 선택 완료 시 ResultFragment가 받을 수 있도록 FragmentResult API로 좌표를 되돌려준다.
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

    private void addPlaceMarker(String placeName) {
        // 보기 모드에서는 선택 중심점 대신 실제 장소명을 가진 마커를 올린다.
        Marker marker = new Marker(mapView);
        marker.setPosition(selectedPoint);
        marker.setTitle(placeName);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(marker);
        marker.showInfoWindow();
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
        // 권한이 이미 있으면 바로 이동하고, 없으면 Android 권한 플로우를 탄다.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
            return;
        }
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void moveToCurrentLocation() {
        // GPS가 비어 있으면 네트워크 위치를 한 번 더 확인해 사용자가 막히는 일을 줄인다.
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
        // 위치 실패는 치명적 오류가 아니라 사용자가 다시 시도할 수 있는 안내로 처리한다.
        if (getView() != null) {
            Snackbar.make(getView(), R.string.map_location_unavailable, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateSelectedPointFromCenter() {
        // 선택 모드는 지도 중앙을 곧 선택 좌표로 보며, 스크롤/줌마다 최신화한다.
        if (mapView == null) {
            return;
        }
        IGeoPoint center = mapView.getMapCenter();
        selectedPoint = new GeoPoint(center.getLatitude(), center.getLongitude());
        updateCoordText();
    }

    private void updateCoordText() {
        // 보기 전용 모드에서는 좌표 라벨이 숨겨져 있으므로 불필요한 갱신을 하지 않는다.
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
