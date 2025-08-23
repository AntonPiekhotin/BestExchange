package com.tony

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

internal const val MY_CHAT_ID = "770129748"

private const val ONE_HOUR = 60 * 60 * 1000L
private const val HALF_HOUR = 30 * 60 * 1000L
private const val THREE_HOUR = 3 * 60 * 60 * 1000L
private const val FIVE_MIN = 5 * 60 * 1000L

private const val RATE_CHANGE_THRESHOLD = 0.2

class BestExchangeBot : TelegramLongPollingBot() {

    private val webClient = WebClient.instance
    private val lastRates = ArrayDeque<Double>(15)

    override fun getBotToken(): String = "8128343370:AAEtN2E6fAkWQmnBywFrHF_Xrj1oRQanA28"
    override fun getBotUsername(): String = "BestExchange"

    override fun onUpdateReceived(update: Update?) {
        if (update?.message?.text == "/start") {
            execute(SendMessage(MY_CHAT_ID, "Бот запущений!"))
        }
    }

    fun startSendingRates() {
        GlobalScope.launch {
            while (isActive) {
                significantChanges()
                delay(THREE_HOUR)
            }
        }
        GlobalScope.launch {
            while (isActive) {
                dailyUpdate()
                delay(ONE_HOUR)
            }
        }
    }

    private suspend fun dailyUpdate() {
        val millisToNoon = millisUntilNextNoon()
        delay(millisToNoon)
        val msg = getEurUahRateFromMonobankApi()
        logInfo("Sending daily rate at noon: $msg")
        execute(SendMessage(MY_CHAT_ID, "Щоденний курс EUR/UAH\n$msg"))
    }

    private fun significantChanges() {
        println("========================================================================")
        logInfo("Checking for rate update...")
        val rate = webClient.getEurUahRateValue().rateBuy
        if (rate != null) {
            logInfo("Current EUR/UAH rate: $rate")
            logInfo("current last rates: $lastRates")
            if (shouldSendRate(rate)) {
                val msg = getEurUahRateFromMonobankApi()
                logInfo("Significant rate change detected. Sending update: $msg")
                execute(SendMessage(MY_CHAT_ID, "Significant rate change: \n$msg"))
            } else {
                logInfo("No significant rate change. Not sending update.")
            }
            addRate(rate)
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
            String.format("Sell:", apiResponse.rateSell ?: 0.0),
            String.format("Buy:", apiResponse.rateBuy ?: 0.0)
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
