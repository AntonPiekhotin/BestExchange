package com.tony

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val UAH_CURRENCY_CODE = 980
internal const val EUR_CURRENCY_CODE = 978
private const val MONOBANK_API_URL = "https://api.monobank.ua/bank/currency"

object WebClient {
    private val objectMapper = ObjectMapper()

    fun getEurUahRateValue(): MonoCurrencyResponse {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(MONOBANK_API_URL)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw RuntimeException("Empty response from Monobank API")

            val responseBody = objectMapper.readValue(body, object : TypeReference<List<MonoCurrencyResponse>>() {})
            logDebug("Monobank API response: $responseBody")
            val eurToUah = responseBody.firstOrNull {
                it.currencyCodeA == EUR_CURRENCY_CODE && it.currencyCodeB == UAH_CURRENCY_CODE
            }
            if (eurToUah != null) return eurToUah
            else throw RuntimeException("EUR to UAH rate not found in Monobank API response")
        }
    }
}