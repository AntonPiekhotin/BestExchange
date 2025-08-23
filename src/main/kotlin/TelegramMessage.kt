package com.tony

data class TelegramMessage(
    val message: String,
    val sellRate: String,
    val buyRate: String
) {
    override fun toString(): String {
        return "$message\nКурс продажу: $sellRate\nКурс купівлі: $buyRate"
    }
}