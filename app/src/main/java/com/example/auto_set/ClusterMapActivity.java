package com.example.auto_set;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class ClusterMapActivity extends AppCompatActivity {
    private ClusterView clusterView;
    private List<Zone> zones;
    private double currentLatitude;
    private double currentLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cluster_map);

        clusterView = findViewById(R.id.clusterView);
        Button refreshButton = findViewById(R.id.refreshButton);
        Button backButton = findViewById(R.id.backButton);

        zones = getIntent().getParcelableArrayListExtra("zones");
        currentLatitude = getIntent().getDoubleExtra("currentLatitude", 0);
        currentLongitude = getIntent().getDoubleExtra("currentLongitude", 0);

        if (zones != null) {
            Log.i("ClusterMapActivity", "Zones received: " + zones.size());
            clusterView.setZones(zones);
        }

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshView();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Close the activity and return to the previous screen
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Handle back button press
        super.onBackPressed();
    }

    private void refreshView() {
        if (zones != null) {
            Log.i("ClusterMapActivity", "Current location: " + currentLatitude + ", " + currentLongitude);
            for (Zone zone : zones) {
                Log.i("ClusterMapActivity", "Zone coordinates: " + zone.getLatitude() + ", " + zone.getLongitude());
                double distance = calculateDistance(currentLatitude, currentLongitude, zone.getLatitude(), zone.getLongitude());
                Log.i("ClusterMapActivity", "Distance to zone: " + distance + " km");
            }
            clusterView.invalidate(); // Redraw the view
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the Earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in km
    }
} 