import logging

from telegram import Update
from telegram.ext import (
    Application,
    CommandHandler,
    MessageHandler,
    filters,
    CallbackContext,
)
from models import TibotConfig, TelegramMessage

logger = logging.getLogger(__name__)

# Callback type: async (TelegramMessage) -> None
_on_message = None


async def _handle_message(update: Update, _context: CallbackContext) -> None:
    if not update.message or not _on_message:
        return
    msg = TelegramMessage.from_ptb_message(update.message)
    await _on_message(msg)


async def _start_cmd(update: Update, _context: CallbackContext) -> None:
    await update.message.reply_text(
        "🤖 TiBot is online!\n\n"
        "Commands:\n"
        "/start - Show this message\n"
        "/help - Get help\n"
        "/ping - Check bot is alive"
    )


async def _help_cmd(update: Update, _context: CallbackContext) -> None:
    await update.message.reply_text(
        "TiBot — Telegram Bot Gateway for Android\n"
        "Manage your bot from the TiBot Android app."
    )


async def _ping_cmd(update: Update, _context: CallbackContext) -> None:
    await update.message.reply_text("pong! 🏓")


async def start_bot(
    config: TibotConfig,
    message_callback,
) -> Application:
    """Start PTB bot with polling. Returns the Application instance."""
    global _on_message
    _on_message = message_callback

    app = Application.builder().token(config.bot_token).build()

    app.add_handler(CommandHandler("start", _start_cmd))
    app.add_handler(CommandHandler("help", _help_cmd))
    app.add_handler(CommandHandler("ping", _ping_cmd))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, _handle_message))
    app.add_handler(MessageHandler(filters.Document.ALL, _handle_message))
    app.add_handler(MessageHandler(filters.PHOTO, _handle_message))

    await app.initialize()
    await app.start()
    await app.updater.start_polling()
    logger.info("PTB bot started, polling...")
    return app


async def stop_bot(app: Application) -> None:
    """Gracefully stop the bot."""
    try:
        await app.updater.stop()
    finally:
        try:
            await app.stop()
        finally:
            await app.shutdown()
