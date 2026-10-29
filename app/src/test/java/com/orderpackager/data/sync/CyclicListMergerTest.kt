package com.orderpackager.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CyclicListMergerTest {
    @Test
    fun `first sync uploads local list when cloud is empty`() {
        val result = CyclicListMerger.firstSync(
            cloudNames = emptyList(),
            localNames = listOf("Ana", "Luis")
        )

        assertEquals(listOf("Ana", "Luis"), result)
    }

    @Test
    fun `first sync keeps cloud order and appends unique local items`() {
        val result = CyclicListMerger.firstSync(
            cloudNames = listOf("Ana", "Luis"),
            localNames = listOf(" luis ", "Marta", "ANA", "Pedro")
        )

        assertEquals(listOf("Ana", "Luis", "Marta", "Pedro"), result)
    }

    @Test
    fun `existing cloud replaces default seed on a fresh installation`() {
        val result = CyclicListMerger.firstSync(
            cloudNames = listOf("Cloud position"),
            localNames = listOf("Seed one", "Seed two"),
            localIsDefaultSeed = true
        )

        assertEquals(listOf("Cloud position"), result)
    }

    @Test
    fun `normalize removes blanks and case insensitive duplicates`() {
        val result = CyclicListMerger.normalize(
            listOf("  ", "Position", " position ", "Другая", "ДРУГАЯ")
        )

        assertEquals(listOf("Position", "Другая"), result)
    }
}
