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
        // Implement logic to create zones from collected data
        // This could involve clustering algorithms or other logic
        List<Zone> zones = new ArrayList<>();
        
        // Example: Create a dummy zone for demonstration
        zones.add(new Zone(37.7749, -122.4194, true, false, true, false));
        
        return zones;
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
}
