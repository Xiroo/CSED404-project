package com.example.auto_set;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

public class ClusterView extends View {
    private List<Zone> zones;
    private Paint paint;
    private float currentLatitude;
    private float currentLongitude;

    public ClusterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
    }

    public void setZones(List<Zone> zones) {
        this.zones = zones;
        invalidate(); // Redraw the view
    }

    public void setCurrentLocation(double latitude, double longitude) {
        this.currentLatitude = (float) latitude;
        this.currentLongitude = (float) longitude;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (zones != null) {
            paint.setColor(Color.BLUE);
            for (Zone zone : zones) {
                float x = (float) ((zone.getLongitude() + 180) / 360 * getWidth());
                float y = (float) ((90 - zone.getLatitude()) / 180 * getHeight());
                Log.i("ClusterView", "Drawing zone at: " + x + ", " + y);
                canvas.drawCircle(x, y, 10, paint); // Draw a circle for each zone
            }
        }
        // Draw current location
        paint.setColor(Color.RED);
        float x = (float) ((currentLongitude + 180) / 360 * getWidth());
        float y = (float) ((90 - currentLatitude) / 180 * getHeight());
        Log.i("ClusterView", "Drawing current location at: " + x + ", " + y);
        canvas.drawCircle(x, y, 5, paint); // Draw a smaller circle for the current location
    }
} 