package com.example.auto_set;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataCollectionService extends Service {

    private static final String CHANNEL_ID = "DataCollectionChannel";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private Sensor barometerSensor;
    private Sensor linearAccelerationSensor;

    private float[] gravityValues = new float[3];
    private float currentPressure = Float.NaN;
    private float[] linearAccelerationValues = new float[3];
    private double currentAltitude = 0.0;
    private double verticalVelocity = 0.0;
    private long lastUpdateTime = -1;

    private Handler handler = new Handler();
    private Runnable locationRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        barometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        registerSensors();
        startLocationUpdates();

        createNotificationChannel();
        startForeground(1, getNotification());
    }

    private void registerSensors() {
        if (gravitySensor != null) {
            sensorManager.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (barometerSensor != null) {
            sensorManager.registerListener(sensorListener, barometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(sensorListener, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void startLocationUpdates() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(DataCollectionService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null);
                    handler.postDelayed(this, 5000);
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

                    double gravityMagnitude = Math.sqrt(gravityValues[0] * gravityValues[0] +
                            gravityValues[1] * gravityValues[1] +
                            gravityValues[2] * gravityValues[2]);

                    if (gravityMagnitude != 0) {
                        double dotProduct = linearAccelerationValues[0] * gravityValues[0] +
                                linearAccelerationValues[1] * gravityValues[1] +
                                linearAccelerationValues[2] * gravityValues[2];

                        double verticalAcceleration = dotProduct / gravityMagnitude;
                        verticalAcceleration *= -1;

                        if (lastUpdateTime != -1) {
                            long deltaTime = currentTime - lastUpdateTime;
                            double deltaTimeSeconds = deltaTime / 1000.0;

                            verticalVelocity += verticalAcceleration * deltaTimeSeconds;
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
        File baseDir = getExternalFilesDir(null);
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

        if (parentDir != null && !parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (!dirsCreated) {
                Log.e("GPSDataCollection", "Failed to create directory: " + parentDir.getAbsolutePath());
                return;
            }
        }

        boolean isNewFile = !file.exists();

        try (FileWriter writer = new FileWriter(file, true)) {
            if (isNewFile) {
                String header = "timestamp(ms),latitude(deg),longitude(deg),speed(m/s),gravity_x(m/s^2),gravity_y(m/s^2),gravity_z(m/s^2),pressure(hPa),linear_accel_x(m/s^2),linear_accel_y(m/s^2),linear_accel_z(m/s^2),altitude(m)\n";
                writer.append(header);
            }

            String data = String.format(Locale.getDefault(), "%d,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f\n",
                    System.currentTimeMillis(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getSpeed(),
                    gravityValues[0],
                    gravityValues[1],
                    gravityValues[2],
                    currentPressure,
                    linearAccelerationValues[0],
                    linearAccelerationValues[1],
                    linearAccelerationValues[2],
                    currentAltitude);
            writer.append(data);
            Log.i("GPSDataCollection", String.format("File write in %s", filePath));
        } catch (IOException e) {
            Log.e("GPSDataCollection", "File write failed", e);
        }
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Data Collection in Progress")
                .setContentText("Collecting location and sensor data...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Data Collection Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(locationRunnable);
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(sensorListener);
    }
}
