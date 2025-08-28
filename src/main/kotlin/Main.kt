package com.tony

import it.sauronsoftware.cron4j.Scheduler
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Properties

internal const val MY_CHAT_ID = "770129748"

private const val RATE_CHANGE_THRESHOLD = 0.1
private const val SETTINGS_FILE = "bot_settings.properties"

class BestExchangeBot : TelegramLongPollingBot() {

    private val webClient = WebClient
    private val scheduler = Scheduler()
    private var lastSentRate: Double = 0.0
        set(value) {
            logDebug("Updating lastSentRate: $value (was $field)")
            field = value
        }
    private var dailyTaskId: String? = null
    private var dailyUpdateHour: Int = 17
    private var dailyUpdateMinute: Int = 0

    init {
        loadSettings()
    }

    override fun getBotToken(): String =
        System.getenv("TELEGRAM_BOT_TOKEN") ?: throw Exception("No TELEGRAM_BOT_TOKEN env set")

    override fun getBotUsername(): String = "BestExchange"

    override fun onUpdateReceived(update: Update?) {
        val message = update?.message
        val text = message?.text
        val chatId = message?.chatId?.toString()
        if (chatId != MY_CHAT_ID) return // Only respond to authorized user
        when {
            text == "/start" -> {
                execute(SendMessage(MY_CHAT_ID, "Бот запущений!"))
            }
            text == "/help" -> {
                val helpText = """
                    Доступні команди:
                    /start - Запустити бота
                    /help - Показати це повідомлення
                    /status - Показати поточні налаштування
                    /settime HH:MM - Встановити час щоденного оновлення (24-годинний формат)
                    
                    Приклад: /settime 09:30
                """.trimIndent()
                execute(SendMessage(MY_CHAT_ID, helpText))
            }
            text == "/status" -> {
                val statusText = """
                    Поточні налаштування:
                    Час щоденного оновлення: ${String.format("%02d:%02d", dailyUpdateHour, dailyUpdateMinute)}
                    Наступне оновлення: ${getNextUpdateTime()}
                """.trimIndent()
                execute(SendMessage(MY_CHAT_ID, statusText))
            }
            text?.startsWith("/settime ") == true -> {
                handleSetTimeCommand(text.substring(9))
            }
        }
    }

    fun startSendingRates() {
        // Run once at startup to initialize lastRates quietly
        try {
            significantChanges()
        } catch (t: Throwable) {
            logError("Startup significantChanges error: ${t.message}")
        }
        // Every hour at minute 0
        scheduler.schedule("0 */1 * * *") {
            try {
                significantChanges()
            } catch (t: Throwable) {
                logError("Scheduled significantChanges error: ${t.message}")
            }
        }
        // Every day at configured time
        dailyTaskId = scheduler.schedule("$dailyUpdateMinute $dailyUpdateHour * * *") {
            try {
                sendDailyRate()
            } catch (t: Throwable) {
                logError("Scheduled daily update error: ${t.message}")
            }
        }
        scheduler.start()
        logInfo("Schedulers started: 3-hourly checks and daily noon update")
    }

    private fun sendDailyRate() {
        val msg = getEurUahRateFromMonobankApi()
        logInfo("Sending daily rate: $msg")
        execute(SendMessage(MY_CHAT_ID, msg.toString()))
        lastSentRate = msg.buyRate.toDouble()
    }

    private fun significantChanges() {
        println("========================================================================")
        logInfo("Checking for rate update...")
        val uahEur = webClient.getEurUahRateValue()
        if (uahEur.rateBuy != null) {
            logInfo("Current EUR/UAH rate: ${uahEur.rateBuy}")
            if (shouldSendRate(uahEur.rateBuy)) {
                val msg = TelegramMessage(
                    "EUR/UAH",
                    uahEur.rateSell.toString(),
                    uahEur.rateBuy.toString()
                )
                logInfo("Significant rate change detected. Sending update: $msg")
                val arrows = if (uahEur.rateBuy > lastSentRate) "⬆️⬆️⬆️" else "⬇️⬇️⬇️"
                execute(SendMessage(MY_CHAT_ID, "Significant rate change $arrows: \n$msg"))
                lastSentRate = uahEur.rateBuy
            } else {
                logInfo("No significant rate change. Not sending update.")
            }
        }
    }

    private fun shouldSendRate(newRate: Double): Boolean {
        return kotlin.math.abs(newRate - lastSentRate) > RATE_CHANGE_THRESHOLD
    }

    fun getEurUahRateFromMonobankApi(): TelegramMessage {
        val apiResponse = webClient.getEurUahRateValue()
        return TelegramMessage(
            "EUR/UAH",
            apiResponse.rateSell.toString(),
            apiResponse.rateBuy.toString()
        )
    }

    private fun loadSettings() {
        try {
            val file = File(SETTINGS_FILE)
            if (file.exists()) {
                val reader = FileReader(file)
                val properties = Properties()
                properties.load(reader)
                dailyUpdateHour = properties.getProperty("dailyUpdateHour", "16").toInt()
                dailyUpdateMinute = properties.getProperty("dailyUpdateMinute", "0").toInt()
                reader.close()
                logInfo("Settings loaded: update time set to $dailyUpdateHour:$dailyUpdateMinute")
            } else {
                logInfo("Settings file not found, using default values")
            }
        } catch (e: Exception) {
            logError("Error loading settings: ${e.message}")
        }
    }

    private fun saveSettings() {
        try {
            val file = File(SETTINGS_FILE)
            val writer = FileWriter(file)
            val properties = Properties()
            properties.setProperty("dailyUpdateHour", dailyUpdateHour.toString())
            properties.setProperty("dailyUpdateMinute", dailyUpdateMinute.toString())
            properties.store(writer, "BestExchangeBot settings")
            writer.close()
            logInfo("Settings saved: update time set to $dailyUpdateHour:$dailyUpdateMinute")
        } catch (e: Exception) {
            logError("Error saving settings: ${e.message}")
        }
    }

    private fun handleSetTimeCommand(time: String) {
        try {
            val (hourStr, minuteStr) = time.split(":").map { it.trim() }
            val hour = hourStr.toIntOrNull()
            val minute = minuteStr.toIntOrNull()

            if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                execute(
                    SendMessage(
                        MY_CHAT_ID,
                        "Невірний формат часу. Використовуйте формат HH:MM (24-годинний формат)."
                    )
                )
                return
            }

            dailyUpdateHour = hour
            dailyUpdateMinute = minute
            saveSettings()

            // Reschedule the daily task
            dailyTaskId?.let { scheduler.deschedule(it) }
            dailyTaskId = scheduler.schedule("$dailyUpdateMinute $dailyUpdateHour * * *") {
                try {
                    sendDailyRate()
                } catch (t: Throwable) {
                    logError("Scheduled daily update error: ${t.message}")
                }
            }

            execute(
                SendMessage(
                    MY_CHAT_ID,
                    "Час щоденного оновлення змінено на ${String.format("%02d:%02d", hour, minute)}."
                )
            )
        } catch (e: Exception) {
            execute(SendMessage(MY_CHAT_ID, "Помилка при обробці команди: ${e.message}"))
        }
    }

    private fun getNextUpdateTime(): String {
        val now = java.time.LocalDateTime.now()
        var nextUpdate = now.withHour(dailyUpdateHour).withMinute(dailyUpdateMinute).withSecond(0).withNano(0)

        if (nextUpdate.isBefore(now) || nextUpdate.isEqual(now)) {
            nextUpdate = nextUpdate.plusDays(1)
        }

        val duration = java.time.Duration.between(now, nextUpdate)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return "через $hours год $minutes хв"
    }
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val bot = BestExchangeBot()
    botsApi.registerBot(bot)
    bot.startSendingRates()
}

fun logInfo(message: String) {
    println("${java.time.LocalDateTime.now()}: [INFO] $message")
}

fun logDebug(message: String) {
    println("${java.time.LocalDateTime.now()}: [DEBUG] $message")
}

fun logError(message: String) {
    println("${java.time.LocalDateTime.now()}: [ERROR] $message")
}
