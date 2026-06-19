import asyncio
import json
import logging
import signal

import paho.mqtt.client as mqtt
import yaml

from models import TibotConfig, TibotEnvelope, TelegramMessage, AutoReplyRule
from bot import start_bot, stop_bot

logger = logging.getLogger(__name__)
MQTT_TOPIC_PREFIX = "tibot"

# In-memory stores
_chats: dict[int, dict] = {}
_autoreply_rules: list[AutoReplyRule] = []
_bot_app = None
_mqtt_client = None
_config: TibotConfig = None
_lock = asyncio.Lock()


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


def _publish(topic: str, payload: dict) -> None:
    env = TibotEnvelope(type=topic.split("/")[-1], payload=payload)
    _mqtt_client.publish(topic, json.dumps(env.to_dict()), qos=1)


# ---- MQTT callbacks ----


def _on_connect(client, _userdata, _flags, rc) -> None:
    logger.info(f"MQTT connected (rc={rc})")
    # Subscribe to all control topics
    client.subscribe(_make_topic("msg/out/#"))
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
    global _autoreply_rules

    # Extract the sub-topic after "tibot/"
    sub = topic.replace(f"{MQTT_TOPIC_PREFIX}/", "", 1)

    if sub.startswith("msg/out/"):
        # Android wants to send a message
        chat_id = int(sub.split("/")[-1])
        text = env.payload.get("text", "")
        reply_to = env.payload.get("reply_to")
        if _bot_app:
            await _bot_app.bot.send_message(
                chat_id=chat_id, text=text,
                reply_to_message_id=reply_to,
            )

    elif sub.startswith("msg/file/"):
        chat_id = int(sub.split("/")[-1])
        file_path = env.payload.get("path", "")
        caption = env.payload.get("caption", "")
        if _bot_app and file_path:
            with open(file_path, "rb") as f:
                await _bot_app.bot.send_document(
                    chat_id=chat_id, document=f, caption=caption,
                )

    elif sub == "cmd/restart":
        logger.info("Restart command received")
        _publish(_make_topic("status", "offline"), {"reason": "restarting"})
        # Actual restart handled by proot supervisor

    elif sub == "cmd/exec":
        # Terminal command execution
        import subprocess
        cmd = env.payload.get("command", "")
        try:
            result = subprocess.run(
                cmd, shell=True, capture_output=True, text=True, timeout=30,
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
        async with _lock:
            action = sub.split("/")[-1]
            if action == "get":
                _publish(_make_topic("autoreply", "list"), {
                    "rules": [r.__dict__ for r in _autoreply_rules],
                })
            elif action == "set":
                rule = AutoReplyRule(**env.payload)
                # Replace existing or append
                for i, r in enumerate(_autoreply_rules):
                    if r.rule_id == rule.rule_id:
                        _autoreply_rules[i] = rule
                        break
                else:
                    _autoreply_rules.append(rule)
                _publish(_make_topic("autoreply", "list"), {
                    "rules": [r.__dict__ for r in _autoreply_rules],
                })
            elif action == "delete":
                rule_id = env.payload.get("rule_id", "")
                _autoreply_rules = [r for r in _autoreply_rules if r.rule_id != rule_id]
                _publish(_make_topic("autoreply", "list"), {
                    "rules": [r.__dict__ for r in _autoreply_rules],
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
    async with _lock:
        for rule in _autoreply_rules:
            if rule.matches(msg.text or ""):
                if _bot_app:
                    await _bot_app.bot.send_message(
                        chat_id=msg.chat_id, text=rule.reply,
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


async def run_bridge(config: TibotConfig) -> None:
    global _mqtt_client, _bot_app, _config
    _config = config

    # Setup MQTT
    _mqtt_client = mqtt.Client(client_id="tibot-bridge")
    _mqtt_client.on_connect = _on_connect
    _mqtt_client.on_message = _on_message
    _mqtt_client.connect_async(config.mqtt_host, config.mqtt_port, 60)
    _mqtt_client.loop_start()

    # Start PTB bot
    _bot_app = await start_bot(config, _on_telegram_message)

    # Wait for shutdown signal
    stop_event = asyncio.Event()
    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, stop_event.set)
        except NotImplementedError:
            pass  # Windows doesn't support add_signal_handler

    await stop_event.wait()

    # Cleanup
    await stop_bot(_bot_app)
    _mqtt_client.loop_stop()
    _mqtt_client.disconnect()


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    config = load_config()
    asyncio.run(run_bridge(config))


if __name__ == "__main__":
    main()
