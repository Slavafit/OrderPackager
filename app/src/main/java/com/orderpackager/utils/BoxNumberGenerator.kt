package com.orderpackager.utils

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BoxNumberGenerator {
    private val random = SecureRandom()

    fun generate(date: Date = Date()): String {
        val dateTime = SimpleDateFormat("MMddHHmm", Locale.US).format(date)
        val randomDigits = random.nextInt(10_000).toString().padStart(4, '0')
        return "NBX${dateTime}${randomDigits}B"
    }
}
