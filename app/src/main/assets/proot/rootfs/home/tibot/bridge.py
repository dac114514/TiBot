import asyncio
import json
import logging
import signal
from dataclasses import asdict

import paho.mqtt.client as mqtt
import yaml

from autoreply import AutoReplyEngine
from models import TibotConfig, TibotEnvelope, TelegramMessage, AutoReplyRule
from bot import start_bot, stop_bot
from typing import Optional

logger = logging.getLogger(__name__)
MQTT_TOPIC_PREFIX = "tibot"

# In-memory stores
_chats: dict[int, dict] = {}
_autoreply_engine = AutoReplyEngine()
_bot_app = None
_mqtt_client = None
_config: Optional[TibotConfig] = None


def load_config(path: str = "config.yaml") -> TibotConfig:
    try:
        with open(path) as f:
            data = yaml.safe_load(f)
    except FileNotFoundError:
        logger.error(f"Config file not found: {path}")
        raise
    except yaml.YAMLError as e:
        logger.error(f"Invalid YAML in config file {path}: {e}")
        raise
    if not data or "bot_token" not in data:
        raise ValueError(f"Config file {path} is missing required 'bot_token' field")
    return TibotConfig(
        bot_token=data["bot_token"],
        admin_ids=data.get("admin_ids", []),
        mqtt_host=data.get("mqtt", {}).get("host", "127.0.0.1"),
        mqtt_port=data.get("mqtt", {}).get("port", 1883),
    )


def _make_topic(*parts: str) -> str:
    return "/".join([MQTT_TOPIC_PREFIX, *parts])


def _publish(topic: str, payload: dict, msg_type: str = None) -> None:
    if msg_type is None:
        parts = topic.split("/")
        msg_type = parts[-2] if len(parts) >= 2 else parts[-1]
    env = TibotEnvelope(type=msg_type, payload=payload)
    _mqtt_client.publish(topic, json.dumps(env.to_dict()), qos=1)


# ---- MQTT callbacks ----


def _on_connect(client, _userdata, _flags, rc) -> None:
    logger.info(f"MQTT connected (rc={rc})")
    # Subscribe to all control topics
    client.subscribe(_make_topic("msg/out/#"))
    client.subscribe(_make_topic("msg/file/#"))
    client.subscribe(_make_topic("cmd/#"))
    client.subscribe(_make_topic("autoreply/#"))
    client.subscribe(_make_topic("chat/#"))
    client.subscribe(_make_topic("llm/#"))
    # Announce online
    _publish(_make_topic("status", "online"), {
        "running": True, "username": "tibot",
    })


async def _safe_handle(topic: str, env: TibotEnvelope) -> None:
    try:
        await _handle_mqtt_command(topic, env)
    except Exception as e:
        logger.error(f"Error handling MQTT command on {topic}: {e}", exc_info=True)


def _on_message(_client, _userdata, msg: mqtt.MQTTMessage) -> None:
    topic = msg.topic
    try:
        env = TibotEnvelope.from_dict(json.loads(msg.payload))
    except Exception as e:
        logger.warning(f"Invalid MQTT payload on {topic}: {e}")
        return
    asyncio.ensure_future(_safe_handle(topic, env))


async def _handle_mqtt_command(topic: str, env: TibotEnvelope) -> None:

    # Extract the sub-topic after "tibot/"
    sub = topic.replace(f"{MQTT_TOPIC_PREFIX}/", "", 1)

    if sub.startswith("msg/out/"):
        # Android wants to send a message
        chat_id = int(sub.split("/")[-1])
        text = env.payload.get("text", "")
        reply_to = env.payload.get("reply_to")
        if not text:
            _publish(_make_topic("msg", "error"), {
                "chat_id": chat_id, "error": "empty message",
            })
            return
        if _bot_app:
            await _bot_app.bot.send_message(
                chat_id=chat_id, text=text,
                reply_to_message_id=reply_to,
            )

    elif sub.startswith("msg/file/"):
        chat_id = int(sub.split("/")[-1])
        raw_path = env.payload.get("path", "")
        caption = env.payload.get("caption", "")
        if _bot_app and raw_path:
            safe_dir = "/home/tibot/files"
            import os
            resolved = os.path.realpath(raw_path)
            if not resolved.startswith(os.path.realpath(safe_dir)):
                logger.warning(f"Blocked path traversal attempt: {raw_path}")
                _publish(_make_topic("msg", "error"), {
                    "chat_id": chat_id, "error": "path not allowed",
                })
                return
            with open(resolved, "rb") as f:
                await _bot_app.bot.send_document(
                    chat_id=chat_id, document=f, caption=caption,
                )

    elif sub == "cmd/ping":
        _publish("tibot/status", {
            "bot_running": _bot_app is not None,
            "reason": "pong",
        })

    elif sub == "cmd/restart":
        logger.info("Restart command received")
        _publish(_make_topic("status", "offline"), {"reason": "restarting"})
        # Actual restart handled by proot supervisor

    elif sub == "cmd/exec":
        # Terminal command execution
        import subprocess
        import shlex
        cmd = env.payload.get("command", "")
        logger.warning(f"Executing shell command: {cmd}")
        try:
            result = subprocess.run(
                shlex.split(cmd), capture_output=True, text=True, timeout=30,
            )
            _publish(_make_topic("cmd", "result"), {
                "command": cmd,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "returncode": result.returncode,
            })
        except Exception as e:
            _publish(_make_topic("cmd", "result"), {
                "command": cmd, "error": str(e),
            })

    elif sub.startswith("autoreply/"):
        action = sub.split("/")[-1]
        if action == "get":
            _publish(_make_topic("autoreply", "list"), {
                "rules": [asdict(r) for r in _autoreply_engine.rules],
            })
        elif action == "set":
            rule = AutoReplyRule(**env.payload)
            if not _autoreply_engine.update_rule(rule):
                _autoreply_engine.add_rule(rule)
            _publish(_make_topic("autoreply", "list"), {
                "rules": [asdict(r) for r in _autoreply_engine.rules],
            })
        elif action == "delete":
            rule_id = env.payload.get("rule_id", "")
            _autoreply_engine.delete_rule(rule_id)
            _publish(_make_topic("autoreply", "list"), {
                "rules": [asdict(r) for r in _autoreply_engine.rules],
            })

    elif sub.startswith("chat/"):
        rest = sub[len("chat/"):]
        if rest == "list":
            _publish(_make_topic("chat", "list"), {
                "chats": list(_chats.values()),
            })
        elif rest.startswith("history/"):
            chat_id = int(rest.split("/")[-1])
            # history would come from persistent storage; for MVP return last 50
            _publish(_make_topic("chat", "history", str(chat_id)), {
                "messages": [],
            })

    elif sub.startswith("llm/"):
        # Reserved for future LLM integration
        pass


# ---- PTB message callback ----


async def _on_telegram_message(msg: TelegramMessage) -> None:
    # Store chat in memory
    _chats[msg.chat_id] = {
        "chat_id": msg.chat_id,
        "title": msg.chat_title,
        "last_message": msg.text or "[file]",
        "last_message_time": msg.date,
        "type": "group" if msg.chat_id < 0 else "private",
    }

    # Check auto-reply first
    reply = _autoreply_engine.check(msg)
    if reply:
        if _bot_app:
            await _bot_app.bot.send_message(
                chat_id=msg.chat_id, text=reply,
                reply_to_message_id=msg.message_id,
            )
        return  # Auto-reply matched, don't forward to Android

    # Forward to Android
    _publish(_make_topic("msg", "in", str(msg.chat_id)), msg.to_dict())

    # Update chat list
    _publish(_make_topic("chat", "list"), {
        "chats": list(_chats.values()),
    })


# ---- Main entry point ----


def _on_bot_ready() -> None:
    """Callback invoked when the PTB bot has initialized and started polling."""
    _publish("tibot/status", {
        "bot_running": True,
        "reason": "bot started",
    })


async def run_bridge(config: TibotConfig) -> None:
    global _mqtt_client, _bot_app, _config
    _config = config

    # Setup MQTT
    _mqtt_client = mqtt.Client(client_id="tibot-bridge")
    _mqtt_client.on_connect = _on_connect
    _mqtt_client.on_message = _on_message
    _mqtt_client.connect_async(config.mqtt_host, config.mqtt_port, 60)
    _mqtt_client.loop_start()

    # Start PTB bot (on_ready publishes bot_running:true for Android gate)
    _bot_app = await start_bot(config, _on_telegram_message, on_ready=_on_bot_ready)

    # Wait for shutdown signal
    stop_event = asyncio.Event()
    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, stop_event.set)
        except NotImplementedError:
            pass  # Windows doesn't support add_signal_handler

    await stop_event.wait()

    # LWT: announce bot going offline before cleanup
    _publish("tibot/status", {
        "bot_running": False,
        "reason": "bot shutting down",
    })
    await stop_bot(_bot_app)
    _mqtt_client.loop_stop()
    _mqtt_client.disconnect()
