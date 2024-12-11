package com.example.auto_set;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class ClusterView extends View {
    private List<Zone> zones;
    private Paint paint;

    public ClusterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setZones(List<Zone> zones) {
        this.zones = zones;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (zones != null) {
            for (Zone zone : zones) {
                // Convert latitude and longitude to x, y coordinates
                float x = (float) (zone.getLatitude() * getWidth());
                float y = (float) (zone.getLongitude() * getHeight());
                canvas.drawCircle(x, y, 10, paint); // Draw a circle for each zone
            }
        }
    }
} 