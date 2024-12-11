package com.example.auto_set;

public class Zone {
    private double latitude;
    private double longitude;
    private boolean wifiEnabled;
    private boolean bluetoothEnabled;
    private boolean silentMode;
    private boolean mobileDataEnabled;

    public Zone(double latitude, double longitude, boolean wifiEnabled, boolean bluetoothEnabled, boolean silentMode, boolean mobileDataEnabled) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.wifiEnabled = wifiEnabled;
        this.bluetoothEnabled = bluetoothEnabled;
        this.silentMode = silentMode;
        this.mobileDataEnabled = mobileDataEnabled;
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