package com.orderpackager.data.catalog

import com.google.gson.Gson
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.network.CloudPosition
import org.junit.Assert.*
import org.junit.Test

class CompositionTest {
    private fun product(id: String, category: String = "clothing", unit: String = "pc") = CatalogProduct(
        id, category, mapOf("en" to id, "ru" to "Товар", "es" to "Producto"),
        id, unit, if (unit == "pair") "pairs" else "pcs", 10, false, "built_in"
    )

    @Test fun `count categories not products or quantities`() {
        val items = listOf(CompositionItem(product("coat"), 4), CompositionItem(product("shirt"), 7),
            CompositionItem(product("shoe", "footwear", "pair"), 2))
        assertEquals(2, Composition.categoryCount(items))
        assertEquals(items, Composition.decode(Composition.encode(items)))
    }

    @Test fun `sum same products across positions using stable ids`() {
        val coat = product("coat")
        val shirt = product("shirt") // same Russian display name, different ID
        val rows = CompositionReport.totals(listOf(
            listOf(CompositionItem(coat, 2), CompositionItem(shirt, 1)),
            listOf(CompositionItem(coat.copy(names = mapOf("ru" to "Куртка")), 5))
        ))
        assertEquals(7L, rows.single { it.product.id == "coat" }.quantity)
        assertEquals(1L, rows.single { it.product.id == "shirt" }.quantity)
        assertTrue(CompositionReport.text(rows).contains("coat — 7 pcs"))
        assertTrue(CompositionReport.text(rows).contains("shirt — 1 pc"))
    }

    @Test fun `different units are not mixed and large totals do not overflow int`() {
        val rows = CompositionReport.totals(listOf(listOf(
            CompositionItem(product("shoe", "footwear", "pair"), 3),
            CompositionItem(product("shoe", "footwear", "pc"), 1),
            CompositionItem(product("coat"), Int.MAX_VALUE),
            CompositionItem(product("coat"), 1)
        )))
        assertEquals(3, rows.size)
        assertEquals(2147483648L, rows.single { it.product.id == "coat" }.quantity)
    }

    @Test fun `cloud round trip retains quantities and product snapshot`() {
        val items = listOf(CompositionItem(product("coat"), 7))
        val entity = OrderPosition(orderId = 1, cyclicItemId = 2, cyclicItemName = "Test",
            compositionJson = Composition.encode(items))
        val json = Gson().toJson(CloudPosition.fromEntity(entity))
        val restored = Gson().fromJson(json, CloudPosition::class.java).toEntity(9)
        assertEquals(items, Composition.decode(restored.compositionJson))
        assertEquals(9L, restored.orderId)
    }

    @Test fun `old cloud payload without composition remains readable`() {
        val json = """{"cyclic_item_id":2,"cyclic_item_name":"Test","has_clothes":true,"other_text":"","weight_kg":1.5}"""
        val restored = Gson().fromJson(json, CloudPosition::class.java).toEntity(1)
        assertTrue(Composition.decode(restored.compositionJson).isEmpty())
        assertTrue(restored.hasClothes)
    }

    @Test fun `empty and removed items do not contribute to totals`() {
        assertEquals(0, Composition.categoryCount(emptyList()))
        assertTrue(CompositionReport.totals(listOf(emptyList())).isEmpty())
        assertTrue(Composition.decode(Composition.encode(listOf(CompositionItem(product("coat"), 0)))).isEmpty())
    }
}
