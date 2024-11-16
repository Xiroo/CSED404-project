package com.example.auto_set;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
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

        // No need to stop the service in onDestroy
        // The service should run independently of the activity lifecycle
    }
}
