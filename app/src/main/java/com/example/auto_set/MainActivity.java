package com.example.auto_set;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
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
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private Sensor barometerSensor;
    private Sensor linearAccelerationSensor;

    private float[] gravityValues = new float[3]; // Gravity sensor values (X, Y, Z)
    private float currentPressure = Float.NaN; // Barometer value
    private float[] linearAccelerationValues = new float[3]; // Linear acceleration values (X, Y, Z)

    private double currentAltitude = 0.0; // Altitude starting at 0 meters
    private double verticalVelocity = 0.0; // Vertical velocity (initially zero)
    private long lastUpdateTime = -1; // Last update timestamp for integration

    private Handler handler = new Handler();
    private Runnable locationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        barometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        if (gravitySensor != null) {
            sensorManager.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e("Gravity", "No gravity sensor found!");
        }

        if (barometerSensor != null) {
            sensorManager.registerListener(sensorListener, barometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e("Barometer", "No barometer sensor found!");
        }

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(sensorListener, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e("LinearAcceleration", "No linear acceleration sensor found!");
        }

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

    private SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            long currentTime = System.currentTimeMillis();

            switch (event.sensor.getType()) {
                case Sensor.TYPE_GRAVITY:
                    System.arraycopy(event.values, 0, gravityValues, 0, event.values.length);
                    break;

                case Sensor.TYPE_PRESSURE:
                    currentPressure = event.values[0];
                    break;

                case Sensor.TYPE_LINEAR_ACCELERATION:
                    System.arraycopy(event.values, 0, linearAccelerationValues, 0, event.values.length);

                    // Calculate magnitude of gravity vector
                    double gravityMagnitude = Math.sqrt(
                            gravityValues[0] * gravityValues[0] +
                                    gravityValues[1] * gravityValues[1] +
                                    gravityValues[2] * gravityValues[2]
                    );

                    // Ensure gravity magnitude is not zero to avoid division by zero
                    if (gravityMagnitude != 0) {
                        // Calculate dot product of linear acceleration and gravity vectors
                        double dotProduct = linearAccelerationValues[0] * gravityValues[0] +
                                linearAccelerationValues[1] * gravityValues[1] +
                                linearAccelerationValues[2] * gravityValues[2];

                        // Calculate the vertical component of linear acceleration
                        double verticalAcceleration = dotProduct / gravityMagnitude;

                        // Reverse the sign to match the direction of gravity:
                        // Negative when going up, positive when going down
                        verticalAcceleration *= -1;

                        // Update altitude calculation based on vertical component of acceleration
                        if (lastUpdateTime != -1) {
                            long deltaTime = currentTime - lastUpdateTime; // Time difference in milliseconds
                            double deltaTimeSeconds = deltaTime / 1000.0; // Convert to seconds

                            // Integrate to find velocity (v = v0 + a * t)
                            verticalVelocity += verticalAcceleration * deltaTimeSeconds;

                            // Integrate to find altitude (h = h0 + v * t)
                            currentAltitude += verticalVelocity * deltaTimeSeconds;
                        }
                    }

                    lastUpdateTime = currentTime;
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
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

        boolean isNewFile = !file.exists(); // Check if the file is new

        try (FileWriter writer = new FileWriter(file, true)) {
            // Add header if it's a new file
            if (isNewFile) {
                String header = "timestamp(ms),latitude(deg),longitude(deg),speed(m/s),gravity_x(m/s^2),gravity_y(m/s^2),gravity_z(m/s^2),pressure(hPa),linear_accel_x(m/s^2),linear_accel_y(m/s^2),linear_accel_z(m/s^2),altitude(m)\n";
                writer.append(header);
            }

            String data = String.format(Locale.getDefault(), "%d,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f\n",
                    System.currentTimeMillis(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getSpeed(),
                    gravityValues[0], // Gravity X
                    gravityValues[1], // Gravity Y
                    gravityValues[2], // Gravity Z
                    currentPressure,  // Air pressure
                    linearAccelerationValues[0], // Linear acceleration X
                    linearAccelerationValues[1], // Linear acceleration Y
                    linearAccelerationValues[2], // Linear acceleration Z
                    currentAltitude // Calculated Altitude
            );
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
        sensorManager.unregisterListener(sensorListener);
    }
}
