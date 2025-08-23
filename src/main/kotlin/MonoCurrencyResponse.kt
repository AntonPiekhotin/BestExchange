package com.tony

data class MonoCurrencyResponse(
    val currencies: List<CurrencyRate>
)

data class CurrencyRate(
    val currencyCodeA: Int? = null,
    val currencyCodeB: Int? = null,
    val date: Int? = null,
    val rateSell: Int? = null,
    val rateBuy: Double? = null,
    val rateCross: Double? = null
)