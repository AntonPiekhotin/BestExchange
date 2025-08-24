package com.tony

import it.sauronsoftware.cron4j.Scheduler
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

internal const val MY_CHAT_ID = "770129748"

private const val RATE_CHANGE_THRESHOLD = 0.2

class BestExchangeBot : TelegramLongPollingBot() {

    private val webClient = WebClient
    private val lastRates = ArrayDeque<Double>(15)
    private val scheduler = Scheduler()

    override fun getBotToken(): String =
        System.getenv("TELEGRAM_BOT_TOKEN") ?: throw Exception("No TELEGRAM_BOT_TOKEN env set")

    override fun getBotUsername(): String = "BestExchange"

    override fun onUpdateReceived(update: Update?) {
        if (update?.message?.text == "/start") {
            execute(SendMessage(MY_CHAT_ID, "Бот запущений!"))
        }
    }

    fun startSendingRates() {
        // Run once at startup to initialize lastRates quietly
        try {
            significantChanges()
        } catch (t: Throwable) {
            logError("Startup significantChanges error: ${t.message}")
        }

        // Every 3 hours at minute 0
        scheduler.schedule("0 */3 * * *") {
            try {
                significantChanges()
            } catch (t: Throwable) {
                logError("Scheduled significantChanges error: ${t.message}")
            }
        }
        // Every day at 16:00 local time (19:00 Kyiv time)
        scheduler.schedule("0 16 * * *") {
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
        logInfo("Sending daily rate at noon: $msg")
        execute(SendMessage(MY_CHAT_ID, "Daily EUR/UAH\n$msg"))
    }

    private fun significantChanges() {
        println("========================================================================")
        logInfo("Checking for rate update...")
        val uahEur = webClient.getEurUahRateValue()
        if (uahEur.rateBuy != null) {
            logInfo("Current EUR/UAH rate: ${uahEur.rateBuy}")
            logInfo("current last rates: $lastRates")
            if (shouldSendRate(uahEur.rateBuy)) {
                val msg = TelegramMessage(
                    "EUR/UAH",
                    uahEur.rateSell.toString(),
                    uahEur.rateBuy.toString()
                )
                logInfo("Significant rate change detected. Sending update: $msg")
                execute(SendMessage(MY_CHAT_ID, "Significant rate change: \n$msg"))
            } else {
                logInfo("No significant rate change. Not sending update.")
            }
            addRate(uahEur.rateBuy)
        }
    }

    private fun addRate(rate: Double) {
        if (lastRates.size == 15) lastRates.removeFirst()
        lastRates.addLast(rate)
    }

    private fun shouldSendRate(newRate: Double): Boolean {
        val last = lastRates.lastOrNull() ?: return true
        return kotlin.math.abs(newRate - last) > RATE_CHANGE_THRESHOLD
    }

    private fun millisUntilNextNoon(): Long {
        val now = java.time.LocalDateTime.now()
        val nextNoon = if (now.hour < 12) {
            now.withHour(12).withMinute(0).withSecond(0).withNano(0)
        } else {
            now.plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0)
        }
        val duration = java.time.Duration.between(now, nextNoon)
        return duration.toMillis()
    }

    fun getEurUahRateFromMonobankApi(): TelegramMessage {
        val apiResponse = webClient.getEurUahRateValue()
        return TelegramMessage(
            "EUR/UAH",
            apiResponse.rateSell.toString(),
            apiResponse.rateBuy.toString()
        )
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
