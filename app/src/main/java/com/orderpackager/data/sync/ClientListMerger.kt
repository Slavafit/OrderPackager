package com.orderpackager.data.sync

import java.util.Locale

object ClientListMerger {
    fun normalize(lastNames: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return lastNames.map(String::trim)
            .filter(String::isNotEmpty)
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
    }

    fun firstSync(cloudLastNames: List<String>, localLastNames: List<String>): List<String> =
        normalize(cloudLastNames + localLastNames)
}
