package com.tony

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

internal const val MY_CHAT_ID = "770129748"
internal const val MONOBANK_API_URL = "https://api.monobank.ua/bank/currency"
internal const val UAH_CURRENCY_CODE = 980
internal const val EUR_CURRENCY_CODE = 978
internal const val USD_CURRENCY_CODE = 840

private const val ONE_HOUR = 60 * 60 * 1000L
private const val HALF_HOUR = 30 * 60 * 1000L
private const val THREE_HOUR = 3 * 60 * 60 * 1000L
private const val FIVE_MIN = 5 * 60 * 1000L

private const val RATE_CHANGE_THRESHOLD = 0.2

class BestExchangeBot : TelegramLongPollingBot() {

    private val objectMapper = ObjectMapper()
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
                println("========================================================================")
                logInfo("Checking for rate update...")
                val rate = getEurUahRateValue()
                if (rate != null) {
                    logInfo("Current EUR/UAH rate: $rate")
                    logInfo("current last rates: $lastRates")
                    if (shouldSendRate(rate)) {
                        val msg = getEurUahRate()
                        logInfo("Significant rate change detected. Sending update: $msg")
                        execute(SendMessage(MY_CHAT_ID, "Significant rate change: \n$msg"))
                    } else {
                        logInfo("No significant rate change. Not sending update.")
                    }
                    addRate(rate)
                }
                delay(THREE_HOUR)
            }
        }
        GlobalScope.launch {
            while (isActive) {
                val millisToNoon = millisUntilNextNoon()
                delay(millisToNoon)
                val msg = getEurUahRate()
                logInfo("Sending daily rate at noon: $msg")
                execute(SendMessage(MY_CHAT_ID, "Щоденний курс EUR/UAH\n$msg"))
                delay(ONE_HOUR)
            }
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

    fun getEurUahRateValue(): Double? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(MONOBANK_API_URL)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return null
            val responseBody = objectMapper.readValue(body, object : TypeReference<List<MonoCurrencyResponse>>() {})
            val eurToUah = responseBody.firstOrNull {
                it.currencyCodeA == EUR_CURRENCY_CODE && it.currencyCodeB == UAH_CURRENCY_CODE
            }
            return eurToUah?.rateBuy
        }
    }

    // Повертає мілісекунди до наступної 12:00
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

    fun getEurUahRate(): TelegramMessage {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(MONOBANK_API_URL)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return TelegramMessage(
                "bad resonse from api :(",
                "N/A",
                "N/A"
            ).also { logError("Bad response from Monobank API, $response") }

            val responseBody = objectMapper.readValue(body, object : TypeReference<List<MonoCurrencyResponse>>() {})
            logDebug("Monobank API response: $responseBody")
            val eurToUah = responseBody.firstOrNull {
                it.currencyCodeA == EUR_CURRENCY_CODE && it.currencyCodeB == UAH_CURRENCY_CODE
            }
            return if (eurToUah != null) {
                TelegramMessage(
                    "Курс EUR/UAH",
                    eurToUah.rateSell?.toString() ?: "N/A",
                    eurToUah.rateBuy?.toString() ?: "N/A"
                )
            } else {
                TelegramMessage("No EUR/UAH rate found", "N/A", "N/A")
            }
        }
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
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val bot = BestExchangeBot()
    botsApi.registerBot(bot)
    bot.startSendingRates()
}
