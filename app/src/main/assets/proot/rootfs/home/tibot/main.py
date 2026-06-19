#!/usr/bin/env python3
"""TiBot — Python Backend Entry Point

Starts Mosquitto, MQTT bridge, and PTB bot.
Expected to be run inside the proot container.
"""

import asyncio
import logging
import socket
import subprocess
import time

from bridge import load_config, run_bridge
from models import TibotConfig

logger = logging.getLogger(__name__)

MOSQUITTO_RETRY_DELAY = 0.5
MOSQUITTO_MAX_RETRIES = 10


def ensure_mosquitto(config: TibotConfig) -> subprocess.Popen:
    """Start mosquitto if not already running."""
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

    for attempt in range(1, MOSQUITTO_MAX_RETRIES + 1):
        time.sleep(MOSQUITTO_RETRY_DELAY)
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        ready = s.connect_ex((config.mqtt_host, config.mqtt_port)) == 0
        s.close()
        if ready:
            logger.info(f"Mosquitto ready (pid={proc.pid}, attempt={attempt})")
            return proc
        logger.debug(f"Waiting for mosquitto (attempt {attempt})")

    logger.error("Mosquitto failed to start within timeout")
    proc.terminate()
    return None


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    config = load_config()
    proc = ensure_mosquitto(config)
    if proc is None and config.mqtt_host == "127.0.0.1":
        return

    try:
        asyncio.run(run_bridge(config))
    except KeyboardInterrupt:
        logger.info("Shutting down...")
    finally:
        if proc:
            proc.terminate()


if __name__ == "__main__":
    main()
