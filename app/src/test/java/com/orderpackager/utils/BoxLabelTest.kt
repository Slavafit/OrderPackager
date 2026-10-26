package com.orderpackager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BoxLabelTest {

    @Test
    fun `box number contains NBX date time four random digits and B suffix`() {
        val date = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 23, 15, 42, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val boxNumber = BoxNumberGenerator.generate(date)

        assertTrue(boxNumber.matches(Regex("NBX10231542\\d{4}B")))
        assertEquals(16, boxNumber.length)
    }

    @Test
    fun `summary label matches requested content and excludes client data`() {
        val boxNumber = "NBX102315421234B"
        val zpl = PrintHelper.buildOrderSummaryZpl(
            boxNumber = boxNumber,
            batchNumber = "LOTE-77",
            totalWeightKg = 18.86f,
            boxLengthCm = 70f,
            boxWidthCm = 45f,
            boxHeightCm = 50f,
            date = "23.10.2026 15:42",
            appName = "Order Packer",
            version = "v1.10",
            developer = "by Slavafit"
        )

        assertTrue(zpl.contains("^PW800"))
        assertTrue(zpl.contains("^LL1200"))
        assertTrue(zpl.contains("Onza España"))
        assertTrue(zpl.contains("^BCN,120,N,N,N^FD$boxNumber^FS"))
        assertTrue(zpl.contains("^FD1234B^FS"))
        assertTrue(zpl.contains("Packed in Malaga: 23.10.2026 15:42"))
        assertTrue(zpl.contains("Batch: LOTE-77"))
        assertTrue(zpl.contains("Weight total: 18.86 kg"))
        assertTrue(zpl.contains("Sizes: 70x45x50 cm"))
        assertTrue(zpl.contains("^BQN,2,6^FDLA,$boxNumber^FS"))
        assertTrue(zpl.contains("Order Packer"))
        assertTrue(zpl.contains("Version v1.10"))
        assertTrue(zpl.contains("by Slavafit"))
        assertTrue(zpl.contains("^PQ2"))
        assertFalse(zpl.contains("^PQ1"))

        assertFalse(zpl.contains("Иванов"))
        assertFalse(zpl.contains("Фамилия"))
        assertFalse(zpl.contains("Позиций"))
        assertFalse(zpl.contains("Orders"))
    }

    @Test
    fun `all printed elements fit inside 1200 dot label`() {
        val zpl = PrintHelper.buildOrderSummaryZpl(
            boxNumber = "NBX102315421234B",
            batchNumber = "LOTE-77",
            totalWeightKg = 18.86f,
            boxLengthCm = 70f,
            boxWidthCm = 45f,
            boxHeightCm = 50f,
            date = "23.10.2026 15:42",
            appName = "Order Packer",
            version = "v1.10",
            developer = "by Slavafit"
        )

        val yCoordinates = Regex("\\^FO\\d+,(\\d+)")
            .findAll(zpl)
            .map { it.groupValues[1].toInt() }
            .toList()

        assertTrue(yCoordinates.isNotEmpty())
        assertTrue(yCoordinates.all { it in 0 until 1200 })
        assertEquals(1120, yCoordinates.maxOrNull())
    }
}
