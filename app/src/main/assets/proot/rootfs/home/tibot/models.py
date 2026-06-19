import uuid
import time
from dataclasses import dataclass, field, asdict
from typing import Optional


@dataclass
class TibotEnvelope:
    type: str
    payload: dict
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: int = field(default_factory=lambda: int(time.time()))

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, d: dict) -> "TibotEnvelope":
        return cls(**d)


@dataclass
class TelegramMessage:
    message_id: int
    chat_id: int
    chat_title: str
    text: Optional[str] = None
    sender_name: str = "Unknown"
    date: int = 0
    file_id: Optional[str] = None
    file_name: Optional[str] = None

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_ptb_message(cls, msg) -> "TelegramMessage":
        return cls(
            message_id=msg.message_id,
            chat_id=msg.chat.id,
            chat_title=msg.chat.title or msg.chat.first_name or str(msg.chat.id),
            text=msg.text or msg.caption,
            sender_name=msg.from_user.full_name if msg.from_user else "Unknown",
            date=int(msg.date.timestamp()),
            file_id=(msg.document or msg.photo[-1] if msg.photo else None).file_id
            if msg.document or msg.photo else None,
            file_name=msg.document.file_name if msg.document else None,
        )


@dataclass
class ChatSummary:
    chat_id: int
    title: str
    last_message: Optional[str] = None
    last_message_time: int = 0
    unread_count: int = 0
    type: str = "private"  # private, group, supergroup, channel


@dataclass
class AutoReplyRule:
    keyword: str
    reply: str
    match_type: str = "exact"  # exact, contains, regex, command
    enabled: bool = True
    rule_id: str = field(default_factory=lambda: str(uuid.uuid4()))

    def matches(self, text: str) -> bool:
        if not text or not self.enabled:
            return False
        if self.match_type == "exact":
            return text.strip() == self.keyword
        elif self.match_type == "contains":
            return self.keyword.lower() in text.lower()
        elif self.match_type == "regex":
            import re
            return bool(re.search(self.keyword, text))
        elif self.match_type == "command":
            return text.strip().startswith(self.keyword)
        return False


@dataclass
class TibotConfig:
    bot_token: str
    admin_ids: list[int] = field(default_factory=list)
    mqtt_host: str = "127.0.0.1"
    mqtt_port: int = 1883
    mqtt_topic_prefix: str = "tibot"
