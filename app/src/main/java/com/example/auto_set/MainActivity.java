package com.example.auto_set;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private LocationManager locationManager;
    private Handler handler = new Handler();
    private Runnable locationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null);
                    handler.postDelayed(this, 5000); // Re-run every 5 seconds
                }
            }
        };
        handler.post(locationRunnable);
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            saveLocationToFile(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    private void saveLocationToFile(Location location) {
        File baseDir = getExternalFilesDir(null); // App-specific directory
        if (baseDir == null) {
            Log.e("GPSDataCollection", "Failed to access base directory.");
            return;
        }

        String appDir = baseDir.getAbsolutePath() + "/gps_data/";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd/HH", Locale.getDefault());
        String subDir = dateFormat.format(new Date());
        String filePath = appDir + subDir + ".csv";

        File file = new File(filePath);
        File parentDir = file.getParentFile();

        // Check if the parent directory exists; if not, create it
        if (parentDir != null && !parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (!dirsCreated) {
                Log.e("GPSDataCollection", "Failed to create directory: " + parentDir.getAbsolutePath());
                return; // Exit if directories couldn't be created
            }
        }

        try (FileWriter writer = new FileWriter(file, true)) {
            String data = String.format(Locale.getDefault(), "%d,%f,%f\n", System.currentTimeMillis(), location.getLatitude(), location.getLongitude());
            writer.append(data);
            Log.i("GPSDataCollection", String.format("File write in %s", filePath));
        } catch (IOException e) {
            Log.e("GPSDataCollection", "File write failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(locationRunnable);
        locationManager.removeUpdates(locationListener);
    }
}
