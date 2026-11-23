import os
import logging
from pathlib import Path
from dotenv import load_dotenv
from telegram import Update
from telegram.ext import Application, CommandHandler, ContextTypes

load_dotenv(Path(__file__).with_name(".env"))

TOKEN = os.getenv("BOT_TOKEN")
ALLOWED_USER_ID = os.getenv("ALLOWED_USER_ID")

logging.basicConfig(level=logging.INFO)

def authorized(update):
    user = update.effective_user
    return user is not None and str(user.id) == ALLOWED_USER_ID

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not authorized(update):
        await update.message.reply_text("Доступ запрещён.")
        return

    await update.message.reply_text(
        "KOCMOMATICS подключён.\n"
        "/status — состояние бота"
    )

async def status(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not authorized(update):
        await update.message.reply_text("Доступ запрещён.")
        return

    await update.message.reply_text("Бот работает.")

def main():
    if not TOKEN or not ALLOWED_USER_ID:
        raise RuntimeError("Проверь BOT_TOKEN и ALLOWED_USER_ID в bot/.env")

    app = Application.builder().token(TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("status", status))
    app.run_polling()

if __name__ == "__main__":
    main()
