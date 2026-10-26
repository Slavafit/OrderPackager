package com.orderpackager.data.network

import com.google.gson.Gson
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOrderTest {
    @Test
    fun `cloud payload keeps order and position data`() {
        val order = PackingOrder(
            id = 7,
            clientId = 3,
            clientLastName = "Garcia",
            boxNumber = "NBX102615301234B",
            batchNumber = "LOT-42",
            createdAt = 1_793_028_600_000,
            boxLength = 70f,
            boxWidth = 45f,
            boxHeight = 50f,
            isCompleted = true
        )
        val position = OrderPosition(
            orderId = 7,
            cyclicItemId = 5,
            cyclicItemName = "Position 5",
            hasClothes = true,
            hasOther = true,
            otherText = "fragile",
            weightKg = 3.75f
        )

        val cloud = CloudOrder.fromEntities(order, listOf(position))
        val json = Gson().toJson(cloud)

        assertEquals(order.boxNumber, cloud.boxNumber)
        assertEquals(1, cloud.positions.size)
        assertEquals(3.75f, cloud.positions.single().weightKg)
        assertTrue(json.contains("\"box_number\":\"NBX102615301234B\""))
        assertTrue(json.contains("\"has_clothes\":true"))
    }

    @Test
    fun `cloud position restores as a new local entity`() {
        val cloud = CloudPosition(
            cyclicItemId = 9,
            cyclicItemName = "Shoes",
            hasClothes = false,
            hasShoes = true,
            hasCosmetics = false,
            hasAccessories = false,
            hasBags = false,
            hasOther = false,
            otherText = "",
            weightKg = 2.5f
        )

        val restored = cloud.toEntity(orderId = 22)

        assertEquals(0, restored.id)
        assertEquals(22, restored.orderId)
        assertTrue(restored.hasShoes)
        assertEquals(2.5f, restored.weightKg)
    }
}
