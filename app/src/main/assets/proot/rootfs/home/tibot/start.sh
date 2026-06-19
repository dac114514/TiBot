#!/bin/bash
# TiBot proot startup script
# Called by the Android app when proot container starts
set -e

cd /home/tibot

# --- Bootstrap: install required packages on first boot ---
BOOTSTRAP_DONE="/home/tibot/.tibot_bootstrap_done"
if [ ! -f "$BOOTSTRAP_DONE" ]; then
    echo "[bootstrap] first boot, installing dependencies..."

    # Ensure DNS works (proot may not have /etc/resolv.conf from host)
    if [ ! -s /etc/resolv.conf ]; then
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
    fi

    # Install packages — capture stderr for diagnostics
    echo "[bootstrap] apt-get update..."
    if ! apt-get update -qq 2>&1; then
        echo "[ERROR] apt-get update failed (check network/DNS)"
        exit 1
    fi

    echo "[bootstrap] apt-get install python3 python3-pip mosquitto..."
    if ! apt-get install -y -qq python3 python3-pip mosquitto 2>&1; then
        echo "[ERROR] apt-get install failed"
        exit 1
    fi

    # Install Python dependencies
    if [ -f /home/tibot/requirements.txt ]; then
        echo "[bootstrap] pip3 install -r requirements.txt..."
        if ! pip3 install -r /home/tibot/requirements.txt 2>&1; then
            echo "[ERROR] pip3 install failed"
            exit 1
        fi
    fi

    touch "$BOOTSTRAP_DONE"
    echo "[bootstrap] done"
fi

# Start mosquitto if not running
echo "[start] launching mosquitto..."
mosquitto -d -c /etc/mosquitto/mosquitto.conf

# Wait for mosquitto
sleep 1

# Start the Python bridge + PTB bot
echo "[start] launching python3 main.py..."
exec python3 main.py
