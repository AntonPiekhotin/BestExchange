package com.tony

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import kotlinx.coroutines.*

class MyBot : TelegramLongPollingBot() {
    private val chatId = "770129748"

    override fun getBotToken(): String = "8128343370:AAEtN2E6fAkWQmnBywFrHF_Xrj1oRQanA28"
    override fun getBotUsername(): String = "BestExchange"

    override fun onUpdateReceived(update: Update?) {
        if (update?.message?.text == "/start") {
            execute(SendMessage(chatId, "Бот запущений!"))
        }
    }

    fun startSendingRates() {
        GlobalScope.launch {
            while (isActive) {
                val rate = getUsdRate()
                execute(SendMessage(chatId, "Поточний курс USD: $rate"))
                delay(3 * 60 * 60 * 1000L) // кожні 3 години
            }
        }
    }

    private fun getUsdRate(): String {
        // тут ти інтегруєш API НБУ чи іншого банку
        return "39.45 UAH"
    }
}

fun main() {
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    val bot = MyBot()
    botsApi.registerBot(bot)
    bot.startSendingRates()
}

