#!/bin/bash

LOG_DIR="/storage/emulated/0/ES-DE Companion/logs"
mkdir -p "$LOG_DIR"

printf "%s" "$1" > "$LOG_DIR/esde_system_name.txt" &
