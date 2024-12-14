package com.example.auto_set;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.bluetooth.BluetoothAdapter;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREDICT_URL = "http://15.165.115.206:5000/predict_settings";
    private static final String UPLOAD_URL = "http://15.165.115.206:5000/upload_csv";
    private static final String PROCESS_URL = "http://15.165.115.206:5000/process_uploaded_data";
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
        Button makeClusterButton = findViewById(R.id.makeClusterButton);

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

        makeClusterButton.setOnClickListener(v -> {
            Log.d(TAG, "Make Cluster button clicked");
            uploadCsvFiles();
        });
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
        try {
            // Create JSON object for the request body
            String json = new JSONObject()
                    .put("longitude", longitude)
                    .put("latitude", latitude)
                    .put("altitude", altitude)
                    .put("speed", speed)
                    .toString();

            RequestBody body = RequestBody.create(
                    json, MediaType.get("application/json; charset=utf-8"));

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
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation failed", e);
            runOnUiThread(() -> settingsOutput.setText("Error creating JSON request"));
        }
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

    private void uploadCsvFiles() {
        Log.d(TAG, "Starting to upload CSV files");
        File baseDir = getExternalFilesDir(null);
        if (baseDir == null) {
            Log.e(TAG, "Failed to access base directory.");
            return;
        }

        String appDir = baseDir.getAbsolutePath() + "/gps_data/";
        File csvDirectory = new File(appDir);

        if (csvDirectory.exists() && csvDirectory.isDirectory()) {
            List<File> csvFiles = new ArrayList<>();
            findCsvFiles(csvDirectory, csvFiles);

            if (!csvFiles.isEmpty()) {
                Log.d(TAG, "Found " + csvFiles.size() + " CSV files to upload.");
                AtomicInteger filesUploaded = new AtomicInteger(0);
                for (File csvFile : csvFiles) {
                    Log.d(TAG, "Preparing to upload file: " + csvFile.getName());
                    uploadCsvFile(csvFile, csvFiles.size(), filesUploaded);
                }
            } else {
                Log.e(TAG, "No CSV files found in the directory");
                runOnUiThread(() -> Toast.makeText(this, "No CSV files found", Toast.LENGTH_SHORT).show());
            }
        } else {
            Log.e(TAG, "CSV directory does not exist");
            runOnUiThread(() -> Toast.makeText(this, "CSV directory does not exist", Toast.LENGTH_SHORT).show());
        }
    }

    private void findCsvFiles(File directory, List<File> csvFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findCsvFiles(file, csvFiles);
                } else if (file.isFile() && file.getName().endsWith(".csv")) {
                    csvFiles.add(file);
                }
            }
        }
    }

    private void uploadCsvFile(File csvFile, int totalFiles, AtomicInteger filesUploaded) {
        Log.d(TAG, "Uploading file: " + csvFile.getName());
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", csvFile.getName(),
                        RequestBody.create(csvFile, MediaType.parse("text/csv")))
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "File upload failed: " + csvFile.getName(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error uploading " + csvFile.getName(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "File uploaded successfully: " + csvFile.getName());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Uploaded " + csvFile.getName(), Toast.LENGTH_SHORT).show());

                    if (filesUploaded.incrementAndGet() == totalFiles) {
                        Log.d(TAG, "All files uploaded. Starting to process uploaded data.");
                        processUploadedData();
                    }
                } else {
                    Log.e(TAG, "File upload error: " + response.code() + " - " + response.message());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + response.message(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void processUploadedData() {
        Request request = new Request.Builder()
                .url(PROCESS_URL)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Processing uploaded data failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error processing uploaded data", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Processing response received: " + responseData);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data processed successfully", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e(TAG, "Processing error: " + response.code() + " - " + response.message());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + response.message(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
