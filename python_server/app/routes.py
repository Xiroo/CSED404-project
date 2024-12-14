from flask import Blueprint, request, jsonify, render_template
from .processor import DataProcessor
from config.config import (
    VEHICLE_SPEED_THRESHOLD,
    VEHICLE_EPS,
    NON_VEHICLE_EPS,
    VEHICLE_MIN_SAMPLES,
    NON_VEHICLE_MIN_SAMPLES,
)
import os
import logging
import json

api = Blueprint("api", __name__)


@api.route("/health", methods=["GET"])
def health_check():
    return jsonify({"status": "healthy"})


@api.route("/process_data", methods=["POST"])
def process_data():
    try:
        data_dir = request.json.get("data_directory", "app/src/main/assets/gps_data")
        processor = DataProcessor()

        df = processor.load_csv_files(data_dir)
        if df.empty:
            return jsonify({"error": "No data found"}), 404

        vehicle_points, non_vehicle_points = processor.preprocess_data(df)

        # Process non-vehicle points and create hulls
        non_vehicle_clusters = processor.cluster_points(
            non_vehicle_points, eps=NON_VEHICLE_EPS, min_samples=NON_VEHICLE_MIN_SAMPLES
        )
        non_vehicle_zones = processor.create_zones(
            non_vehicle_clusters, is_vehicle=False
        )

        # Extract major settings for vehicle points
        vehicle_settings = processor.extract_vehicle_settings(vehicle_points)

        return jsonify(
            {
                "nonVehicleZones": non_vehicle_zones,
                "vehicleSettings": vehicle_settings,
            }
        )

    except Exception as e:
        logging.error(f"Error processing data: {e}")
        return jsonify({"error": str(e)}), 500


@api.route("/map", methods=["GET"])
def map_view():
    return render_template("map.html")


@api.route("/predict_settings", methods=["POST"])
def predict_settings():
    try:
        # Get data from the request
        data = request.json
        longitude = data.get("longitude")
        latitude = data.get("latitude")
        altitude = data.get("altitude")
        speed = data.get("speed")

        # Define the path to the result.json file
        json_file_path = os.path.join(os.path.dirname(__file__), "result.json")

        # Open and read the JSON file
        with open(json_file_path, "r") as json_file:
            zones_data = json.load(json_file)

        # Check for a matching zone
        matching_zone = None
        for zone in zones_data.get("zones", []):
            if is_matching_zone(zone, longitude, latitude, altitude, speed):
                matching_zone = zone
                break

        if matching_zone:
            return jsonify(matching_zone)
        else:
            return jsonify({"error": "No matching zone found"}), 404

    except FileNotFoundError:
        return jsonify({"error": "result.json file not found"}), 404
    except json.JSONDecodeError:
        return jsonify({"error": "Error decoding JSON"}), 500
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def is_matching_zone(zone, longitude, latitude, altitude, speed):
    # Implement your logic to determine if the zone matches the given parameters
    # This is a placeholder function; adjust the logic as needed
    return (
        zone.get("longitude") == longitude
        and zone.get("latitude") == latitude
        and zone.get("altitude") == altitude
        and zone.get("speed") == speed
    )


@api.route("/upload_csv", methods=["POST"])
def upload_csv():
    try:
        if "file" not in request.files:
            return jsonify({"error": "No file part"}), 400

        file = request.files["file"]
        if file.filename == "":
            return jsonify({"error": "No selected file"}), 400

        if file and file.filename.endswith(".csv"):
            directory = "app/src/main/assets/gps_data"
            os.makedirs(directory, exist_ok=True)
            save_path = os.path.join(directory, file.filename)
            logging.info(f"Saving file to: {save_path}")
            file.save(save_path)
            return (
                jsonify({"success": f"File {file.filename} uploaded successfully"}),
                200,
            )
        else:
            return jsonify({"error": "Invalid file type"}), 400

    except Exception as e:
        logging.error(f"Error saving file: {e}")
        return jsonify({"error": str(e)}), 500


@api.route("/upload", methods=["GET"])
def upload_page():
    return render_template("upload.html")


@api.route("/process_uploaded_data", methods=["GET"])
def process_uploaded_data():
    try:
        directory = "app/src/main/assets/gps_data"
        processor = DataProcessor()

        # Load data from the directory
        df = processor.load_csv_files(directory)
        if df.empty:
            return jsonify({"error": "No data found"}), 404

        # Process vehicle and non-vehicle points separately
        vehicle_points, non_vehicle_points = processor.preprocess_data(df)
        print("preprocess_data done")
        # Cluster vehicle points
        vehicle_clusters = processor.cluster_points(
            vehicle_points, eps=VEHICLE_EPS, min_samples=VEHICLE_MIN_SAMPLES
        )
        print(vehicle_clusters)
        vehicle_zones = processor.create_zones(vehicle_clusters, is_vehicle=True)
        print("create_zones done")

        # Cluster non-vehicle points
        non_vehicle_clusters = processor.cluster_points(
            non_vehicle_points, eps=NON_VEHICLE_EPS, min_samples=NON_VEHICLE_MIN_SAMPLES
        )
        non_vehicle_zones = processor.create_zones(
            non_vehicle_clusters, is_vehicle=False
        )

        # Combine zones
        all_zones = vehicle_zones + non_vehicle_zones

        # Save results to a JSON file
        results = {
            "zones": all_zones,
            "vehicleZonesCount": len(vehicle_zones),
            "nonVehicleZonesCount": len(non_vehicle_zones),
        }
        results_path = "app/src/main/assets/results.json"
        with open(results_path, "w") as f:
            json.dump(results, f, indent=4)

        return jsonify(results)

    except Exception as e:
        logging.error(f"Error processing uploaded data: {e}")
        return jsonify({"error": str(e)}), 500
