package com.example.auto_set;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

public class MainActivity extends AppCompatActivity {
    private ExpandableListView fileListView;
    private boolean isCollectingData = false;
    private Button toggleModeButton;
    private Button adjustSettingsButton;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private ClusterView clusterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity", "onCreate called");

        // Request location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        // Initialize buttons
        toggleModeButton = findViewById(R.id.toggleModeButton);
        adjustSettingsButton = findViewById(R.id.adjustSettingsButton);

        toggleModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });

        adjustSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustSettingsBasedOnZones();
            }
        });

        // Set up the ExpandableListView to view collected files
        fileListView = findViewById(R.id.file_list_view);
        loadFileData(fileListView);

        // Set up Refresh Button
        Button refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("MainActivity", "Refresh button clicked");
                loadFileData(fileListView);
            }
        });

        Button checkClustersButton = findViewById(R.id.checkClustersButton);
        checkClustersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkClustersAroundMe();
            }
        });

        clusterView = findViewById(R.id.clusterView);

        // Load and display clusters
        List<Zone> zones = createZonesFromData();
        clusterView.setZones(zones);

        // No need to stop the service in onDestroy
        // The service should run independently of the activity lifecycle
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with location updates
            } else {
                // Permission denied, handle accordingly
            }
        }
    }

    private void loadFileData(ExpandableListView fileListView) {
        File baseDir = getExternalFilesDir(null);
        if (baseDir == null) {
            Log.e("MainActivity", "Failed to access base directory.");
            return;
        }

        File gpsDataDir = new File(baseDir, "gps_data");
        List<String> dateList = new ArrayList<>();
        Map<String, List<String>> hourMap = new HashMap<>();

        if (gpsDataDir.exists() && gpsDataDir.isDirectory()) {
            File[] dateDirs = gpsDataDir.listFiles();
            if (dateDirs != null) {
                for (File dateDir : dateDirs) {
                    if (dateDir.isDirectory()) {
                        String date = dateDir.getName();
                        dateList.add(date);
                        File[] hourFiles = dateDir.listFiles();
                        List<String> hours = new ArrayList<>();
                        if (hourFiles != null) {
                            for (File hourFile : hourFiles) {
                                if (hourFile.isFile() && hourFile.getName().endsWith(".csv")) {
                                    String fileInfo = hourFile.getName() + " (" + (hourFile.length() / 1024) + " KB)";
                                    int rowCount = countCsvRows(hourFile);
                                    fileInfo += ", Rows: " + rowCount;
                                    hours.add(fileInfo);
                                }
                            }
                        }
                        hourMap.put(date, hours);
                    }
                }
            }
        }

        ExpandableListAdapter adapter = new SimpleExpandableListAdapter(
                this,
                createGroupList(dateList),
                android.R.layout.simple_expandable_list_item_1,
                new String[] { "DATE" },
                new int[] { android.R.id.text1 },
                createChildList(dateList, hourMap),
                android.R.layout.simple_expandable_list_item_2,
                new String[] { "HOUR" },
                new int[] { android.R.id.text1 }
        );
        fileListView.setAdapter(adapter);
    }

    private int countCsvRows(File csvFile) {
        int rowCount = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while (br.readLine() != null) {
                rowCount++;
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error reading file: " + csvFile.getName(), e);
        }
        return rowCount;
    }

    private List<Map<String, String>> createGroupList(List<String> dateList) {
        List<Map<String, String>> groupList = new ArrayList<>();
        for (String date : dateList) {
            Map<String, String> group = new HashMap<>();
            group.put("DATE", date);
            groupList.add(group);
        }
        return groupList;
    }

    private List<List<Map<String, String>>> createChildList(List<String> dateList, Map<String, List<String>> hourMap) {
        List<List<Map<String, String>>> childList = new ArrayList<>();
        for (String date : dateList) {
            List<Map<String, String>> childItemList = new ArrayList<>();
            List<String> hours = hourMap.get(date);
            if (hours != null) {
                for (String hour : hours) {
                    Map<String, String> child = new HashMap<>();
                    child.put("HOUR", hour); // Include file size and row count information
                    childItemList.add(child);
                }
            }
            childList.add(childItemList);
        }
        return childList;
    }

    private void toggleMode() {
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        if (isCollectingData) {
            stopService(serviceIntent);
            toggleModeButton.setText("Start Data Collection");
        } else {
            startService(serviceIntent);
            toggleModeButton.setText("Stop Data Collection");
        }
        isCollectingData = !isCollectingData;
    }

    private void adjustSettingsBasedOnZones() {
        // Load collected data
        List<Zone> zones = createZonesFromData();
        
        // Adjust settings based on zones
        for (Zone zone : zones) {
            applySettingsForZone(zone);
        }
    }

    private List<Zone> createZonesFromData() {
        List<DataPoint> dataPoints = loadDataPoints();
        List<Zone> zones = clusterDataPoints(dataPoints);
        return zones;
    }

    private List<DataPoint> loadDataPoints() {
        List<DataPoint> dataPoints = new ArrayList<>();
        File baseDir = getExternalFilesDir(null);
        if (baseDir == null) {
            Log.e("MainActivity", "Failed to access base directory.");
            return dataPoints;
        }

        File gpsDataDir = new File(baseDir, "gps_data");
        if (gpsDataDir.exists() && gpsDataDir.isDirectory()) {
            File[] dateDirs = gpsDataDir.listFiles();
            if (dateDirs != null) {
                for (File dateDir : dateDirs) {
                    if (dateDir.isDirectory()) {
                        File[] hourFiles = dateDir.listFiles();
                        if (hourFiles != null) {
                            for (File hourFile : hourFiles) {
                                if (hourFile.isFile() && hourFile.getName().endsWith(".csv")) {
                                    dataPoints.addAll(parseCsvFile(hourFile));
                                }
                            }
                        }
                    }
                }
            }
        }
        return dataPoints;
    }

    private List<DataPoint> parseCsvFile(File csvFile) {
        List<DataPoint> dataPoints = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length >= 16) {
                    double latitude = Double.parseDouble(values[1]);
                    double longitude = Double.parseDouble(values[2]);
                    boolean wifiEnabled = Boolean.parseBoolean(values[12]);
                    boolean bluetoothEnabled = Boolean.parseBoolean(values[13]);
                    boolean silentMode = Boolean.parseBoolean(values[14]);
                    boolean mobileDataEnabled = Boolean.parseBoolean(values[15]);
                    dataPoints.add(new DataPoint(latitude, longitude, wifiEnabled, bluetoothEnabled, silentMode, mobileDataEnabled));
                }
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error reading file: " + csvFile.getName(), e);
        }
        return dataPoints;
    }

    private List<Zone> clusterDataPoints(List<DataPoint> dataPoints) {
        double eps = 0.001; // Define a suitable epsilon value
        int minPts = 5; // Define a suitable minimum number of points

        List<Zone> zones = new ArrayList<>();
        List<DataPoint> visited = new ArrayList<>();
        List<DataPoint> noise = new ArrayList<>();

        for (DataPoint point : dataPoints) {
            if (visited.contains(point)) continue;
            visited.add(point);

            List<DataPoint> neighbors = getNeighbors(point, dataPoints, eps);
            if (neighbors.size() < minPts) {
                noise.add(point);
            } else {
                Zone zone = new Zone();
                expandCluster(point, neighbors, zone, dataPoints, visited, eps, minPts);
                zones.add(zone);
            }
        }
        return zones;
    }

    private void expandCluster(DataPoint point, List<DataPoint> neighbors, Zone zone, List<DataPoint> dataPoints, List<DataPoint> visited, double eps, int minPts) {
        zone.addPoint(point);
        for (int i = 0; i < neighbors.size(); i++) {
            DataPoint neighbor = neighbors.get(i);
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                List<DataPoint> neighborNeighbors = getNeighbors(neighbor, dataPoints, eps);
                if (neighborNeighbors.size() >= minPts) {
                    neighbors.addAll(neighborNeighbors);
                }
            }
            if (!zone.contains(neighbor)) {
                zone.addPoint(neighbor);
            }
        }
    }

    private List<DataPoint> getNeighbors(DataPoint point, List<DataPoint> dataPoints, double eps) {
        List<DataPoint> neighbors = new ArrayList<>();
        for (DataPoint other : dataPoints) {
            if (distance(point, other) <= eps) {
                neighbors.add(other);
            }
        }
        return neighbors;
    }

    private double distance(DataPoint p1, DataPoint p2) {
        double latDiff = p1.latitude - p2.latitude;
        double lonDiff = p1.longitude - p2.longitude;
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    private void applySettingsForZone(Zone zone) {
        // Implement logic to apply settings for a given zone
        // This could involve enabling/disabling WiFi, Bluetooth, etc.
        if (zone.isWifiEnabled()) {
            // Enable WiFi
        } else {
            // Disable WiFi
        }

        if (zone.isBluetoothEnabled()) {
            // Enable Bluetooth
        } else {
            // Disable Bluetooth
        }

        if (zone.isSilentMode()) {
            // Set to silent mode
        } else {
            // Set to normal mode
        }

        if (zone.isMobileDataEnabled()) {
            // Enable mobile data
        } else {
            // Disable mobile data
        }
    }

    private void checkClustersAroundMe() {
        // Implement logic to check clusters around the user's current location
        // This could involve filtering the zones based on proximity to the current location
        // For demonstration, let's log the clusters
        List<Zone> zones = createZonesFromData();
        for (Zone zone : zones) {
            Log.i("MainActivity", "Cluster at: " + zone.getLatitude() + ", " + zone.getLongitude());
        }
    }
}

class DataPoint {
    double latitude;
    double longitude;
    boolean wifiEnabled;
    boolean bluetoothEnabled;
    boolean silentMode;
    boolean mobileDataEnabled;

    public DataPoint(double latitude, double longitude, boolean wifiEnabled, boolean bluetoothEnabled, boolean silentMode, boolean mobileDataEnabled) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.wifiEnabled = wifiEnabled;
        this.bluetoothEnabled = bluetoothEnabled;
        this.silentMode = silentMode;
        this.mobileDataEnabled = mobileDataEnabled;
    }
}
