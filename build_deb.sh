#!/bin/bash
# This script builds a .deb package for the Folia Phantom GUI.
# It uses variables to make it easier to update for new versions.

# --- Configuration ---
APP_NAME="folia-phantom-gui"
APP_VERSION="1.0.0"
MAIN_CLASS="com.patch.foliaphantom.gui.Launcher"
# The script expects the JAR file to be named in the format: <APP_NAME>-<APP_VERSION>.jar
JAR_FILE="${APP_NAME}-${APP_VERSION}.jar"
# --- End of Configuration ---

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please make sure the JAR file is in the same directory as this script and the name matches the configuration."
    exit 1
fi

# Create a temporary input directory for jpackage
INPUT_DIR="input_temp"
mkdir -p "$INPUT_DIR"

# Copy the GUI JAR into the input directory
cp "$JAR_FILE" "$INPUT_DIR/"

echo "Starting jpackage to create the .deb package..."

# Run jpackage to create the .deb package
jpackage --type deb \
         --dest . \
         -i "$INPUT_DIR" \
         -n "$APP_NAME" \
         --main-jar "$JAR_FILE" \
         --main-class "$MAIN_CLASS" \
         --app-version "$APP_VERSION"

echo "Cleaning up temporary directory..."
# Clean up the temporary input directory
rm -rf "$INPUT_DIR"
# jpackage may also leave a temporary config directory named after the app
rm -rf "$APP_NAME"

echo "Build complete. The .deb package should be in the current directory."
