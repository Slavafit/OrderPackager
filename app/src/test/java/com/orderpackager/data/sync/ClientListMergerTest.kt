package com.orderpackager.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ClientListMergerTest {
    @Test
    fun `first sync uploads local clients when cloud is empty`() {
        val result = ClientListMerger.firstSync(
            cloudLastNames = emptyList(),
            localLastNames = listOf("Garcia", "Lopez")
        )

        assertEquals(listOf("Garcia", "Lopez"), result)
    }

    @Test
    fun `first sync keeps cloud clients and appends unique local clients`() {
        val result = ClientListMerger.firstSync(
            cloudLastNames = listOf("Garcia", "Lopez"),
            localLastNames = listOf(" lopez ", "Martin", "GARCIA", "Sanchez")
        )

        assertEquals(listOf("Garcia", "Lopez", "Martin", "Sanchez"), result)
    }

    @Test
    fun `normalize removes blanks and case insensitive duplicates`() {
        val result = ClientListMerger.normalize(
            listOf("  ", "Garcia", " garcia ", "Иванов", "ИВАНОВ")
        )

        assertEquals(listOf("Garcia", "Иванов"), result)
    }
}
