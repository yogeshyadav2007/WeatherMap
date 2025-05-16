package com.example.weatherusingmap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private RequestQueue requestQueue;
    private MyLocationNewOverlay locationOverlay;
    private static final String API_KEY = "685d0dddd6a45d67fdf185eaa0dbcc00";
    private EditText searchInput;
    private ImageButton btnLocateMe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        searchInput = findViewById(R.id.searchInput);
        btnLocateMe = findViewById(R.id.btnLocateMe);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        GeoPoint defaultLocation = new GeoPoint(20.5937, 78.9629);
        mapView.getController().setCenter(defaultLocation);
        mapView.getController().setZoom(5.0);

        requestQueue = Volley.newRequestQueue(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint geoPoint) {
                handleMapTap(geoPoint);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint geoPoint) {
                return false;
            }
        });

        mapView.getOverlays().add(eventsOverlay);

        btnLocateMe.setOnClickListener(view -> {
            if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
                GeoPoint userLoc = locationOverlay.getMyLocation();
                mapView.getController().animateTo(userLoc);
                mapView.getController().setZoom(15.0);
                handleMapTap(userLoc);
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String city = searchInput.getText().toString();
                if (!city.isEmpty()) {
                    searchCityAndFetchWeather(city);
                }
                return true;
            }
            return false;
        });
    }

    private void enableUserLocation() {
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        locationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint userLocation = locationOverlay.getMyLocation();
            if (userLocation != null) {
                mapView.getController().setCenter(userLocation);
                mapView.getController().setZoom(15.0);
                handleMapTap(userLocation);
            } else {
                Toast.makeText(MainActivity.this, "Could not get current location!", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void handleMapTap(GeoPoint point) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
            String placeName = addresses.isEmpty() ? "Unknown Location" : addresses.get(0).getLocality();
            fetchWeatherAndShowDialog(point.getLatitude(), point.getLongitude(), placeName);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Geocoding failed!", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchWeatherAndShowDialog(double lat, double lon, String placeName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&units=metric&appid=" + API_KEY;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONObject main = response.getJSONObject("main");
                        double temp = main.getDouble("temp");
                        showWeatherDialog(placeName, temp);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Error parsing weather data!", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> Toast.makeText(MainActivity.this, "Failed to fetch weather data!", Toast.LENGTH_SHORT).show());

        requestQueue.add(request);
    }

    private void showWeatherDialog(String placeName, double temperature) {
        new AlertDialog.Builder(this)
                .setTitle("Weather Info")
                .setMessage("Location: " + placeName + "\nTemperature: " + temperature + "\u00B0C")
                .setPositiveButton("OK", null)
                .show();
    }

    private void searchCityAndFetchWeather(String cityName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addressList = geocoder.getFromLocationName(cityName, 1);
            if (!addressList.isEmpty()) {
                Address address = addressList.get(0);
                GeoPoint cityLocation = new GeoPoint(address.getLatitude(), address.getLongitude());
                mapView.getController().animateTo(cityLocation);
                mapView.getController().setZoom(10.0);
                handleMapTap(cityLocation);
            } else {
                Toast.makeText(this, "City not found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error finding city", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        } else {
            Toast.makeText(this, "Location permission is required to get weather updates!", Toast.LENGTH_SHORT).show();
        }
    }
}
