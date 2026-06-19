#!/usr/bin/env python3
"""TiBot — Python Backend Entry Point

Starts Mosquitto, MQTT bridge, and PTB bot.
Expected to be run inside the proot container.
"""

import logging
import subprocess
import sys
import time

from bridge import load_config, run_bridge
from models import TibotConfig

logger = logging.getLogger(__name__)


def ensure_mosquitto(config: TibotConfig) -> subprocess.Popen:
    """Start mosquitto if not already running."""
    import socket
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    result = s.connect_ex((config.mqtt_host, config.mqtt_port))
    s.close()
    if result == 0:
        logger.info("Mosquitto already running")
        return None

    proc = subprocess.Popen(
        ["mosquitto", "-c", "/etc/mosquitto/mosquitto.conf"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    time.sleep(1)
    logger.info(f"Mosquitto started (pid={proc.pid})")
    return proc


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    config = load_config()
    ensure_mosquitto(config)

    try:
        import asyncio
        asyncio.run(run_bridge(config))
    except KeyboardInterrupt:
        logger.info("Shutting down...")


if __name__ == "__main__":
    main()
