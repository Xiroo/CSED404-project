package com.example.auto_set;

import android.os.Parcel;
import android.os.Parcelable;

public class DataPoint implements Parcelable {
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

    protected DataPoint(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        wifiEnabled = in.readByte() != 0;
        bluetoothEnabled = in.readByte() != 0;
        silentMode = in.readByte() != 0;
        mobileDataEnabled = in.readByte() != 0;
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
        dest.writeByte((byte) (wifiEnabled ? 1 : 0));
        dest.writeByte((byte) (bluetoothEnabled ? 1 : 0));
        dest.writeByte((byte) (silentMode ? 1 : 0));
        dest.writeByte((byte) (mobileDataEnabled ? 1 : 0));
    }
} 