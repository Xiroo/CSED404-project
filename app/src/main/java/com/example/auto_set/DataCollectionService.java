package com.example.auto_set;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
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
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationRequest;

import android.bluetooth.BluetoothAdapter;
import android.media.AudioManager;
import android.os.HandlerThread;

public class DataCollectionService extends Service {

    private static final String CHANNEL_ID = "DataCollectionChannel";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private Sensor linearAccelerationSensor;

    private float[] gravityValues = new float[3];
    private float[] linearAccelerationValues = new float[3];
    private double verticalVelocity = 0.0;
    private long lastUpdateTime = -1;

    private Handler handler = new Handler();
    private Runnable locationRunnable;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private HandlerThread handlerThread;
    private Handler backgroundHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        // Start background thread
        handlerThread = new HandlerThread("LocationThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        registerSensors();
        startLocationUpdates();

        createNotificationChannel();
        startForeground(1, getNotification());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        startFusedLocationUpdates();
    }

    private void registerSensors() {
        if (gravitySensor != null) {
            sensorManager.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (linearAccelerationSensor != null) {
            sensorManager.registerListener(sensorListener, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            backgroundHandler.post(() -> {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500,
                    0,
                    locationListener,
                    handlerThread.getLooper()
                );
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    500,
                    0,
                    locationListener,
                    handlerThread.getLooper()
                );
            });
        }
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            // Remove accuracy check to get more frequent updates
            saveLocationToFile(location);
            broadcastLocationUpdate(location);
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

                case Sensor.TYPE_LINEAR_ACCELERATION:
                    System.arraycopy(event.values, 0, linearAccelerationValues, 0, event.values.length);
                    calculateVerticalVelocity(currentTime);
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private void calculateVerticalVelocity(long currentTime) {
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
            }
        }

        lastUpdateTime = currentTime;
    }

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
                String header = "timestamp(ms),latitude(deg),longitude(deg),speed(m/s),gravity_x(m/s^2),gravity_y(m/s^2),gravity_z(m/s^2),linear_accel_x(m/s^2),linear_accel_y(m/s^2),linear_accel_z(m/s^2),altitude(m),wifi_enabled,bluetooth_enabled,silent_mode,mobile_data_enabled\n";
                writer.append(header);
            }

            String data = String.format(Locale.getDefault(), "%d,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f,%b,%b,%b,%b\n",
                    System.currentTimeMillis(),
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getSpeed(),
                    gravityValues[0],
                    gravityValues[1],
                    gravityValues[2],
                    linearAccelerationValues[0],
                    linearAccelerationValues[1],
                    linearAccelerationValues[2],
                    location.getAltitude(),
                    isWifiEnabled(),
                    isBluetoothEnabled(),
                    isSilentMode(),
                    isMobileDataEnabled());
            writer.append(data);
            Log.i("GPSDataCollection", String.format("File write in %s", filePath));
        } catch (IOException e) {
            Log.e("GPSDataCollection", "File write failed", e);
        }

        // Move broadcast to separate method
        broadcastLocationUpdate(location);
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Data Collection in Progress")
                .setContentText("Collecting location and sensor data...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists and is correct
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Set priority to high to ensure visibility
                .build();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Data Collection Service Channel",
                    NotificationManager.IMPORTANCE_HIGH // Use IMPORTANCE_HIGH for foreground services
            );
            channel.setDescription("Channel for data collection service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(500);  // Update every 0.5 seconds
        locationRequest.setFastestInterval(500);
        locationRequest.setSmallestDisplacement(0);  // Update as soon as new location is available
    }

    private void startFusedLocationUpdates() {
        createLocationRequest();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                backgroundHandler.post(() -> {
                    for (Location location : locationResult.getLocations()) {
                        saveLocationToFile(location);
                        broadcastLocationUpdate(location);
                    }
                });
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                handlerThread.getLooper()
            );
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start data collection
        startFusedLocationUpdates();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up background thread
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            locationManager.removeUpdates(locationListener);
            sensorManager.unregisterListener(sensorListener);
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (InterruptedException e) {
            Log.e("DataCollectionService", "Error shutting down thread", e);
        }
    }

    private boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private boolean isSilentMode() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
    }

    private boolean isMobileDataEnabled() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            Method method = cm.getClass().getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(cm);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void notifySettingsChanged() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Settings Changed")
                .setContentText("Your device settings have been adjusted based on your location.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(2, notification);
    }

    private void broadcastLocationUpdate(Location location) {
        Intent updateIntent = new Intent("com.example.auto_set.LOCATION_UPDATE");
        updateIntent.putExtra("latitude", location.getLatitude());
        updateIntent.putExtra("longitude", location.getLongitude());
        updateIntent.putExtra("speed", location.getSpeed());
        updateIntent.putExtra("altitude", location.getAltitude());
        
        Log.d("DataCollectionService", String.format(
            "Broadcasting - Lat: %.6f, Lon: %.6f, Speed: %.1f, Alt: %.1f",
            location.getLatitude(),
            location.getLongitude(),
            location.getSpeed(),
            location.getAltitude()
        ));
        
        sendBroadcast(updateIntent);
    }
}
