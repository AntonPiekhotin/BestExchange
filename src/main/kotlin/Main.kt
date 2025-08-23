package com.tony

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

class MyBot : TelegramLongPollingBot() {

    private val objectMapper = ObjectMapper()

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
                val rate = getEurUahRate()
                execute(SendMessage(MY_CHAT_ID, "Поточний курс USD: $rate"))
                delay(3 * 60 * 60 * 1000L) // кожні 3 години
            }
        }
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
            )

            val responseBody = objectMapper.readValue(body, MonoCurrencyResponse::class.java)
            val eurToUah = responseBody.currencies.firstOrNull {
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
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val bot = MyBot()
    botsApi.registerBot(bot)
    bot.startSendingRates()
}
