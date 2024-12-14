import pandas as pd
import numpy as np
from sklearn.cluster import DBSCAN
from sklearn.preprocessing import StandardScaler
import os
from config.config import VEHICLE_SPEED_THRESHOLD
import logging
from scipy.spatial import ConvexHull


class DataProcessor:
    def __init__(self):
        self.scaler = StandardScaler()

    def load_csv_files(self, directory):
        """Load and combine all CSV files from the directory structure"""
        all_data = []
        for root, dirs, files in os.walk(directory):
            for file in files:
                if file.endswith(".csv"):
                    file_path = os.path.join(root, file)
                    try:
                        df = pd.read_csv(file_path)
                        all_data.append(df)
                    except Exception as e:
                        print(f"Error reading {file_path}: {e}")

        return pd.concat(all_data) if all_data else pd.DataFrame()

    def preprocess_data(self, df):
        """Preprocess the data and separate vehicle/non-vehicle points"""
        logging.debug(f"DataFrame columns: {df.columns}")
        # Ensure the DataFrame has the expected columns
        expected_columns = [
            "timestamp(ms)",
            "latitude(deg)",
            "longitude(deg)",
            "speed(m/s)",
            "gravity_x(m/s^2)",
            "gravity_y(m/s^2)",
            "gravity_z(m/s^2)",
            "linear_accel_x(m/s^2)",
            "linear_accel_y(m/s^2)",
            "linear_accel_z(m/s^2)",
            "altitude(m)",
            "wifi_enabled",
            "bluetooth_enabled",
            "silent_mode",
            "mobile_data_enabled",
        ]

        if not all(col in df.columns for col in expected_columns):
            raise ValueError("DataFrame does not contain the expected columns")

        vehicle_mask = df["speed(m/s)"] > VEHICLE_SPEED_THRESHOLD
        logging.debug(f"Vehicle mask: {vehicle_mask}")
        vehicle_points = df[vehicle_mask]
        non_vehicle_points = df[~vehicle_mask]
        return vehicle_points, non_vehicle_points

    def cluster_points(self, points, eps, min_samples):
        """Perform DBSCAN clustering on points"""
        if len(points) == 0:
            return pd.DataFrame()

        # Include settings as features for clustering
        features = points[
            [
                "latitude(deg)",
                "longitude(deg)",
                "altitude(m)",
                "wifi_enabled",
                "bluetooth_enabled",
                "silent_mode",
                "mobile_data_enabled",
            ]
        ].copy()

        # Convert boolean settings to numerical values
        features["wifi_enabled"] = features["wifi_enabled"].astype(int)
        features["bluetooth_enabled"] = features["bluetooth_enabled"].astype(int)
        features["silent_mode"] = features["silent_mode"].astype(int)
        features["mobile_data_enabled"] = features["mobile_data_enabled"].astype(int)

        scaled_features = self.scaler.fit_transform(features)
        dbscan = DBSCAN(eps=eps, min_samples=min_samples)
        clusters = dbscan.fit_predict(scaled_features)

        points_with_clusters = points.copy()
        points_with_clusters["cluster"] = clusters
        return points_with_clusters

    def create_zones(self, clustered_points, is_vehicle=False):
        """Create zones from clustered points"""
        if clustered_points.empty:
            logging.warning("No clustered points to process.")
            return []

        zones = []
        for cluster_id in clustered_points["cluster"].unique():
            if cluster_id == -1:  # Skip noise points
                continue

            cluster_points = clustered_points[clustered_points["cluster"] == cluster_id]
            if cluster_points.empty:
                logging.warning(f"No points found for cluster {cluster_id}.")
                continue

            if not is_vehicle:
                # Remove duplicate points
                points = np.unique(
                    cluster_points[["latitude(deg)", "longitude(deg)"]].values, axis=0
                )

                # Check if there are enough points to form a convex hull
                if len(points) < 3:
                    logging.warning(
                        f"Not enough points to form a convex hull for cluster {cluster_id}."
                    )
                    continue

                try:
                    # Calculate the convex hull
                    hull = ConvexHull(points)
                    hull_points = points[hull.vertices]
                except Exception as e:
                    logging.error(
                        f"Error computing convex hull for cluster {cluster_id}: {e}"
                    )
                    continue

                zone = {
                    "hull": hull_points.tolist(),
                    "wifiEnabled": bool(cluster_points["wifi_enabled"].mode()[0]),
                    "bluetoothEnabled": bool(
                        cluster_points["bluetooth_enabled"].mode()[0]
                    ),
                    "silentMode": bool(cluster_points["silent_mode"].mode()[0]),
                    "mobileDataEnabled": bool(
                        cluster_points["mobile_data_enabled"].mode()[0]
                    ),
                    "isVehicleZone": is_vehicle,
                    "pointCount": len(cluster_points),
                }
            else:
                # Extract major settings for vehicle points
                zone = {
                    "wifiEnabled": bool(cluster_points["wifi_enabled"].mode()[0]),
                    "bluetoothEnabled": bool(
                        cluster_points["bluetooth_enabled"].mode()[0]
                    ),
                    "silentMode": bool(cluster_points["silent_mode"].mode()[0]),
                    "mobileDataEnabled": bool(
                        cluster_points["mobile_data_enabled"].mode()[0]
                    ),
                    "isVehicleZone": is_vehicle,
                    "pointCount": len(cluster_points),
                }

            zones.append(zone)

        return zones

    def distance(self, p1, p2):
        # Geographic distance including altitude
        lat_diff = p1["latitude(deg)"] - p2["latitude(deg)"]
        lon_diff = p1["longitude(deg)"] - p2["longitude(deg)"]
        alt_diff = p1["altitude(m)"] - p2["altitude(m)"]  # Use meters for altitude
        geo_distance = np.sqrt(lat_diff**2 + lon_diff**2 + alt_diff**2)

        # Settings distance
        settings_distance = 0
        if p1["wifi_enabled"] != p2["wifi_enabled"]:
            settings_distance += 10
        if p1["bluetooth_enabled"] != p2["bluetooth_enabled"]:
            settings_distance += 10
        if p1["silent_mode"] != p2["silent_mode"]:
            settings_distance += 10
        if p1["mobile_data_enabled"] != p2["mobile_data_enabled"]:
            settings_distance += 10

        # Combine distances with weights
        weight_geo = 0.3
        weight_settings = 0.7

        return weight_geo * geo_distance + weight_settings * settings_distance

    def predict_settings(
        self, longitude, latitude, altitude, speed, non_vehicle_zones, vehicle_settings
    ):
        """Predict settings based on location and speed"""
        if speed > VEHICLE_SPEED_THRESHOLD:
            # Return settings for vehicle
            return {
                **vehicle_settings,
                "isVehicleZone": True,
            }

        for zone in non_vehicle_zones:
            if self.is_within_zone(longitude, latitude, altitude, zone):
                return {
                    "wifiEnabled": zone["wifiEnabled"],
                    "bluetoothEnabled": zone["bluetoothEnabled"],
                    "silentMode": zone["silentMode"],
                    "mobileDataEnabled": zone["mobileDataEnabled"],
                    "isVehicleZone": False,
                }
        return None

    def is_within_zone(self, longitude, latitude, altitude, zone):
        """Check if a point is within a zone"""
        # Define a threshold for being "within" a zone
        threshold = 0.01  # Adjust as needed for lat/lon
        altitude_threshold = 3.0  # Meters, adjust for floor detection
        return (
            abs(zone["latitude"] - latitude) < threshold
            and abs(zone["longitude"] - longitude) < threshold
            and abs(zone["altitude"] - altitude) < altitude_threshold
        )

    def extract_vehicle_settings(self, vehicle_points):
        """Extract major settings from vehicle points"""
        if vehicle_points.empty:
            logging.warning("No vehicle points to process.")
            return []

        settings = {
            "wifiEnabled": bool(vehicle_points["wifi_enabled"].mode()[0]),
            "bluetoothEnabled": bool(vehicle_points["bluetooth_enabled"].mode()[0]),
            "silentMode": bool(vehicle_points["silent_mode"].mode()[0]),
            "mobileDataEnabled": bool(vehicle_points["mobile_data_enabled"].mode()[0]),
        }
        return settings
