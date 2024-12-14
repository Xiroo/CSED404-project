package com.example.auto_set;

import android.os.Parcel;
import android.os.Parcelable;

public class DataPoint implements Parcelable {
    double latitude;
    double longitude;
    double altitude;
    float speed;
    boolean wifiEnabled;
    boolean bluetoothEnabled;
    boolean silentMode;
    boolean mobileDataEnabled;
    boolean isVehicle;

    public DataPoint(double latitude, double longitude, double altitude, float speed, boolean wifiEnabled, boolean bluetoothEnabled, boolean silentMode, boolean mobileDataEnabled) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.wifiEnabled = wifiEnabled;
        this.bluetoothEnabled = bluetoothEnabled;
        this.silentMode = silentMode;
        this.mobileDataEnabled = mobileDataEnabled;
        this.isVehicle = speed > 12.0f;
    }

    protected DataPoint(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        altitude = in.readDouble();
        speed = in.readFloat();
        wifiEnabled = in.readByte() != 0;
        bluetoothEnabled = in.readByte() != 0;
        silentMode = in.readByte() != 0;
        mobileDataEnabled = in.readByte() != 0;
        isVehicle = in.readByte() != 0;
    }

    public static final Creator<DataPoint> CREATOR = new Creator<DataPoint>() {
        @Override
        public DataPoint createFromParcel(Parcel in) {
            return new DataPoint(in);
        }

        @Override
        public DataPoint[] newArray(int size) {
            return new DataPoint[size];
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
        dest.writeDouble(altitude);
        dest.writeFloat(speed);
        dest.writeByte((byte) (wifiEnabled ? 1 : 0));
        dest.writeByte((byte) (bluetoothEnabled ? 1 : 0));
        dest.writeByte((byte) (silentMode ? 1 : 0));
        dest.writeByte((byte) (mobileDataEnabled ? 1 : 0));
        dest.writeByte((byte) (isVehicle ? 1 : 0));
    }
} 