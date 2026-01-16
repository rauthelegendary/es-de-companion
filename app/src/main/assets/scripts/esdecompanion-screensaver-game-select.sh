#!/bin/bash

LOG_DIR="/storage/emulated/0/ES-DE Companion/logs"
mkdir -p "$LOG_DIR"

echo -n "$1" > "$LOG_DIR/esde_screensavergameselect_filename.txt"
echo -n "$2" > "$LOG_DIR/esde_screensavergameselect_name.txt"
echo -n "$3" > "$LOG_DIR/esde_screensavergameselect_system.txt"
