## Task 2 Report: Python Bridge Bug Fixes (history storage + autoreply/get routing)

**Status:** Complete
**Commit:** `837d469` - `fix(python): store message history and return on history request`

---

### Changes Made to `bridge.py`

1. **Added message history storage (line 20-21):**
   - `_message_history: dict[int, list[dict]] = {}` -- per-chat message lists keyed by chat_id
   - `MAX_HISTORY_PER_CHAT = 200` -- cap at 200 most recent messages per chat

2. **Appending messages to history in `_on_telegram_message` (lines 217-222):**
   - After updating `_chats`, each incoming message is appended to `_message_history[chat_id]`
   - Uses `TelegramMessage.to_dict()` for serialization (confirmed present at models.py:33)
   - Trims to last 200 entries when the list exceeds `MAX_HISTORY_PER_CHAT`

3. **Fixed history handler (lines 193-196):**
   - Changed `"messages": []` to `"messages": _message_history.get(chat_id, [])`
   - When Android requests `tibot/chat/history/<chat_id>`, the bridge now returns the actual stored messages instead of an empty array

4. **Verified autoreply/get routing (lines 167-170) -- no changes needed:**
   - Topic `tibot/autoreply/get` is already handled correctly:
     - `sub = "autoreply/get"` (after stripping prefix)
     - `sub.startswith("autoreply/")` matches at line 167
     - `action = sub.split("/")[-1]` yields `"get"`
     - Publishes rule list to `tibot/autoreply/list` with `[asdict(r) for r in _autoreply_engine.rules]`

### Verification

- Python syntax check passed: `py_compile.compile('bridge.py', doraise=True)` returned OK
- `TelegramMessage.to_dict()` confirmed at `models.py:33`
- `asdict` import from `dataclasses` is present and used correctly for autoreply rules
- Diff shows only the intended 3 hunks (+10 lines, -2 lines)
- No regressions: all existing MQTT command handlers remain unchanged
