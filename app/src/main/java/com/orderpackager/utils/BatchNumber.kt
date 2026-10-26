package com.orderpackager.utils

object BatchNumber {
    const val PREFIX = "KG"

    fun digits(value: String): String = value.filter(Char::isDigit)

    fun format(value: String): String {
        val digits = digits(value)
        return if (digits.isEmpty()) "" else "$PREFIX$digits"
    }
}
