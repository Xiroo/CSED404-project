package com.example.auto_set;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

public class MainActivity extends AppCompatActivity {
    private ExpandableListView fileListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity", "onCreate called");

        // Start the DataCollectionService as a foreground service
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.i("MainActivity", "Attempting to startForegroundService");
                startForegroundService(serviceIntent);
            } else {
                Log.i("MainActivity", "Attempting to startService");
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to start service", e);
        }

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
                                if (hourFile.isFile()) {
                                    String fileInfo = hourFile.getName() + " (" + (hourFile.length() / 1024) + " KB)";
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
                    child.put("HOUR", hour); // Include file size information
                    childItemList.add(child);
                }
            }
            childList.add(childItemList);
        }
        return childList;
    }
}
