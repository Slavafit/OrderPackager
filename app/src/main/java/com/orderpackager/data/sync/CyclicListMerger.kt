package com.orderpackager.data.sync

import java.util.Locale

object CyclicListMerger {
    fun normalize(names: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return names.map(String::trim)
            .filter(String::isNotEmpty)
            .filter { seen.add(it.lowercase(Locale.ROOT)) }
    }

    fun firstSync(
        cloudNames: List<String>,
        localNames: List<String>,
        localIsDefaultSeed: Boolean = false
    ): List<String> = when {
        cloudNames.isNotEmpty() && localIsDefaultSeed -> normalize(cloudNames)
        else -> normalize(cloudNames + localNames)
    }
}
