package com.example.auto_set;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import okhttp3.*;

import java.io.IOException;
import java.util.Locale;

import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.bluetooth.BluetoothAdapter;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREDICT_URL = "http://15.165.115.206:5000/predict_settings";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude;
    private double currentLongitude;
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView speedText;
    private TextView altitudeText;
    private TextView settingsOutput;
    private TextView adjustSettingsState;
    private OkHttpClient client = new OkHttpClient();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler autoAdjustHandler = new Handler();
    private boolean isAutoAdjustActive = false;
    private boolean isDataCollectionActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize UI elements
        latitudeText = findViewById(R.id.latitudeText);
        longitudeText = findViewById(R.id.longitudeText);
        speedText = findViewById(R.id.speedText);
        altitudeText = findViewById(R.id.altitudeText);
        settingsOutput = findViewById(R.id.settingsOutput);
        adjustSettingsState = findViewById(R.id.adjustSettingsState);

        Button toggleDataCollectionButton = findViewById(R.id.toggleDataCollectionButton);
        Button predictButton = findViewById(R.id.predictButton);
        Button adjustSettingsButton = findViewById(R.id.adjustSettingsButton);

        // Request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            updateInitialLocation();
        }

        toggleDataCollectionButton.setOnClickListener(v -> toggleDataCollection(toggleDataCollectionButton));

        predictButton.setOnClickListener(v -> {
            // Example metrics, replace with actual data collection
            double longitude = 126.9780;
            double latitude = 37.5665;
            double altitude = 50; // Example altitude
            double speed = 15; // Example speed in m/s

            makePredictRequest(longitude, latitude, altitude, speed);
        });

        adjustSettingsButton.setOnClickListener(v -> toggleAutoAdjust(adjustSettingsButton));
    }

    private void updateInitialLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        updateStatusPanel(location.getLatitude(), location.getLongitude(), location.getSpeed(), location.getAltitude());
                    } else {
                        updateStatusPanel(0, 0, 0, 0);
                    }
                })
                .addOnFailureListener(e -> updateStatusPanel(0, 0, 0, 0));
        }
    }

    private void updateStatusPanel(double latitude, double longitude, float speed, double altitude) {
        mainHandler.post(() -> {
            latitudeText.setText(String.format(Locale.getDefault(), "Latitude: %.6f°", latitude));
            longitudeText.setText(String.format(Locale.getDefault(), "Longitude: %.6f°", longitude));
            speedText.setText(String.format(Locale.getDefault(), "Speed: %.1f m/s", speed));
            altitudeText.setText(String.format(Locale.getDefault(), "Altitude: %.1f m", altitude));
        });
    }

    private void toggleDataCollection(Button toggleButton) {
        isDataCollectionActive = !isDataCollectionActive;
        if (isDataCollectionActive) {
            startService(new Intent(this, DataCollectionService.class));
            toggleButton.setText("Stop Data Collection");
            Toast.makeText(this, "Data collection started", Toast.LENGTH_SHORT).show();
        } else {
            stopService(new Intent(this, DataCollectionService.class));
            toggleButton.setText("Start Data Collection");
            Toast.makeText(this, "Data collection stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAutoAdjust(Button adjustButton) {
        isAutoAdjustActive = !isAutoAdjustActive;
        adjustSettingsState.setText("Adjust Settings: " + (isAutoAdjustActive ? "ON" : "OFF"));
        if (isAutoAdjustActive) {
            startAutoAdjust();
            adjustButton.setText("Stop Adjust Settings");
        } else {
            stopAutoAdjust();
            adjustButton.setText("Start Adjust Settings");
        }
    }

    private void startAutoAdjust() {
        autoAdjustHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAutoAdjustActive) {
                    updateInitialLocation();
                    makePredictRequest(currentLongitude, currentLatitude, 0, 0); // Use actual altitude and speed
                    autoAdjustHandler.postDelayed(this, 5000); // Repeat every 5 seconds
                }
            }
        }, 0);
    }

    private void stopAutoAdjust() {
        autoAdjustHandler.removeCallbacksAndMessages(null);
    }

    private void makePredictRequest(double longitude, double latitude, double altitude, double speed) {
        RequestBody body = new FormBody.Builder()
                .add("longitude", String.valueOf(longitude))
                .add("latitude", String.valueOf(latitude))
                .add("altitude", String.valueOf(altitude))
                .add("speed", String.valueOf(speed))
                .build();

        Request request = new Request.Builder()
                .url(PREDICT_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "HTTP request failed", e);
                runOnUiThread(() -> settingsOutput.setText("Error predicting settings"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Response received: " + responseData);
                    runOnUiThread(() -> {
                        settingsOutput.setText(responseData);
                        applySettings(responseData);
                    });
                } else {
                    Log.e(TAG, "HTTP error: " + response.code() + " - " + response.message());
                    runOnUiThread(() -> settingsOutput.setText("Error: " + response.message()));
                }
            }
        });
    }

    private void applySettings(String responseData) {
        // Parse the responseData to extract settings
        // This is a placeholder; adjust based on your server's response format
        boolean wifiEnabled = responseData.contains("wifiEnabled: true");
        boolean bluetoothEnabled = responseData.contains("bluetoothEnabled: true");
        boolean silentMode = responseData.contains("silentMode: true");

        // Change WiFi settings
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.setWifiEnabled(wifiEnabled);
            Toast.makeText(this, "WiFi " + (wifiEnabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        }

        // Change Bluetooth settings
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothEnabled) {
                bluetoothAdapter.enable();
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
            } else {
                bluetoothAdapter.disable();
                Toast.makeText(this, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
            }
        }

        // Change sound mode
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            if (silentMode) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                Toast.makeText(this, "Silent mode activated", Toast.LENGTH_SHORT).show();
            } else {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                Toast.makeText(this, "Normal mode activated", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
