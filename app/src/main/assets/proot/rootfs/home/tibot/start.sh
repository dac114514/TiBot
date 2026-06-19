#!/bin/bash
# TiBot proot startup script
# Called by the Android app when proot container starts

cd /home/tibot

# --- Bootstrap: install required packages on first boot ---
BOOTSTRAP_DONE="/home/tibot/.tibot_bootstrap_done"
if [ ! -f "$BOOTSTRAP_DONE" ]; then
    echo "[bootstrap] first boot, installing dependencies..."

    # Ensure DNS works (proot may not have /etc/resolv.conf from host)
    if [ ! -s /etc/resolv.conf ]; then
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
    fi

    # Install packages
    apt-get update -qq
    apt-get install -y -qq python3 python3-pip mosquitto 2>&1

    # Install Python dependencies
    if [ -f /home/tibot/requirements.txt ]; then
        pip3 install -r /home/tibot/requirements.txt 2>&1
    fi

    touch "$BOOTSTRAP_DONE"
    echo "[bootstrap] done"
fi

# Start mosquitto if not running
mosquitto -d -c /etc/mosquitto/mosquitto.conf

# Wait for mosquitto
sleep 1

# Start the Python bridge + PTB bot
exec python3 main.py
