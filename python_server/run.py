from flask import Flask
from app.routes import api
from config.config import HOST, PORT, DEBUG


def create_app():
    app = Flask(__name__, template_folder="templates")
    app.register_blueprint(api)
    return app


if __name__ == "__main__":
    app = create_app()
    app.run(host=HOST, port=PORT, debug=DEBUG)
