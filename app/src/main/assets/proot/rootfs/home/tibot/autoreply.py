import json
import logging
from dataclasses import asdict
from pathlib import Path
from typing import Optional

from models import AutoReplyRule, TelegramMessage

logger = logging.getLogger(__name__)
RULES_FILE = "autoreply_rules.json"


class AutoReplyEngine:
    def __init__(self):
        self._rules: list[AutoReplyRule] = []
        self._load()

    def _load(self) -> None:
        path = Path(RULES_FILE)
        if path.exists():
            try:
                data = json.loads(path.read_text())
                self._rules = [AutoReplyRule(**r) for r in data]
                logger.info(f"Loaded {len(self._rules)} auto-reply rules")
            except Exception as e:
                logger.warning(f"Failed to load rules: {e}")

    def _save(self) -> None:
        Path(RULES_FILE).write_text(
            json.dumps([asdict(r) for r in self._rules], indent=2)
        )

    @property
    def rules(self) -> list[AutoReplyRule]:
        return self._rules

    def add_rule(self, rule: AutoReplyRule) -> None:
        self._rules.append(rule)
        self._save()

    def update_rule(self, rule: AutoReplyRule) -> bool:
        for i, r in enumerate(self._rules):
            if r.rule_id == rule.rule_id:
                self._rules[i] = rule
                self._save()
                return True
        return False

    def delete_rule(self, rule_id: str) -> bool:
        old_len = len(self._rules)
        self._rules = [r for r in self._rules if r.rule_id != rule_id]
        if len(self._rules) != old_len:
            self._save()
            return True
        return False

    def check(self, message: TelegramMessage) -> Optional[str]:
        """Check if a message matches any rule. Returns reply text or None."""
        text = message.text or ""
        for rule in self._rules:
            if rule.matches(text):
                logger.info(f"Auto-reply matched: '{rule.keyword}' -> '{rule.reply}'")
                return rule.reply
        return None
