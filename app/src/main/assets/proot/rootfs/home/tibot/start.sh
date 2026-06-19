#!/bin/bash
# TiBot proot startup script
# Called by the Android app when proot container starts

cd /home/tibot

# Start mosquitto if not running
mosquitto -d -c /etc/mosquitto/mosquitto.conf

# Wait for mosquitto
sleep 1

# Start the Python bridge + PTB bot
exec python3 main.py
