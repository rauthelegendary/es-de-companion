#!/bin/bash

LOG_DIR="/storage/emulated/0/ES-DE Companion/logs"
mkdir -p "$LOG_DIR"

echo -n "$1" > "$LOG_DIR/esde_screensaver_end.txt"

