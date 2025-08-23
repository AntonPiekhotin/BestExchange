package com.tony

data class MonoCurrencyResponse(
    val currencyCodeA: Int? = null,
    val currencyCodeB: Int? = null,
    val date: Int? = null,
    val rateSell: Double? = null,
    val rateBuy: Double? = null,
    val rateCross: Double? = null
)