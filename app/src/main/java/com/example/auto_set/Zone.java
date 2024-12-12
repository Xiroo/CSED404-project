package com.example.auto_set;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

import com.example.auto_set.DataPoint;

public class Zone implements Parcelable {
    private double latitude;
    private double longitude;
    private boolean wifiEnabled;
    private boolean bluetoothEnabled;
    private boolean silentMode;
    private boolean mobileDataEnabled;
    private List<DataPoint> dataPoints;

    public Zone(double latitude, double longitude, boolean wifiEnabled, boolean bluetoothEnabled, boolean silentMode, boolean mobileDataEnabled) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.wifiEnabled = wifiEnabled;
        this.bluetoothEnabled = bluetoothEnabled;
        this.silentMode = silentMode;
        this.mobileDataEnabled = mobileDataEnabled;
        this.dataPoints = new ArrayList<>();
    }

    protected Zone(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        wifiEnabled = in.readByte() != 0;
        bluetoothEnabled = in.readByte() != 0;
        silentMode = in.readByte() != 0;
        mobileDataEnabled = in.readByte() != 0;
        dataPoints = in.createTypedArrayList(DataPoint.CREATOR);
    }

    public static final Creator<Zone> CREATOR = new Creator<Zone>() {
        @Override
        public Zone createFromParcel(Parcel in) {
            return new Zone(in);
        }

        @Override
        public Zone[] newArray(int size) {
            return new Zone[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeByte((byte) (wifiEnabled ? 1 : 0));
        dest.writeByte((byte) (bluetoothEnabled ? 1 : 0));
        dest.writeByte((byte) (silentMode ? 1 : 0));
        dest.writeByte((byte) (mobileDataEnabled ? 1 : 0));
        dest.writeTypedList(dataPoints);
    }

    public void addPoint(DataPoint point) {
        dataPoints.add(point);
    }

    public boolean contains(DataPoint point) {
        return dataPoints.contains(point);
    }

    // Getters and setters
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean isWifiEnabled() {
        return wifiEnabled;
    }

    public void setWifiEnabled(boolean wifiEnabled) {
        this.wifiEnabled = wifiEnabled;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothEnabled;
    }

    public void setBluetoothEnabled(boolean bluetoothEnabled) {
        this.bluetoothEnabled = bluetoothEnabled;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public boolean isMobileDataEnabled() {
        return mobileDataEnabled;
    }

    public void setMobileDataEnabled(boolean mobileDataEnabled) {
        this.mobileDataEnabled = mobileDataEnabled;
    }
} 