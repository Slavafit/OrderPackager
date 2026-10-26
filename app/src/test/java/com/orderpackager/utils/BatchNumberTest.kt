package com.orderpackager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchNumberTest {
    @Test
    fun `formats digits with permanent KG prefix`() {
        assertEquals("KG153", BatchNumber.format("153"))
        assertEquals("KG153", BatchNumber.format("KG153"))
    }

    @Test
    fun `input keeps digits only`() {
        assertEquals("12345", BatchNumber.digits("KG 12-3A45"))
    }

    @Test
    fun `empty batch remains empty`() {
        assertEquals("", BatchNumber.format(""))
    }
}
