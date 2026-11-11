package com.orderpackager.data.catalog

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CustomProductsTest {
    @Test fun `new product has stable UUID and English fallback`() {
        val product = CustomProducts.create("clothing", "ru", "  Плащ  ", " Raincoat ", 0)
        assertEquals(product.id, UUID.fromString(product.id).toString())
        assertEquals("Плащ", product.names.localized("ru"))
        assertEquals("Raincoat", product.names.localized("es"))
        assertEquals("Raincoat", product.reportNameEn)
        assertTrue(product.custom)
        assertEquals("user_defined", product.source)
        assertEquals("pcs", product.unitPlural)
    }

    @Test fun `duplicates ignore case but separate categories and units`() {
        val product = CustomProducts.create("clothing", "en", "Coat", "Coat", 0)
        val duplicate = CustomProducts.create("clothing", "en", "COAT", "COAT", 0)
        assertTrue(CustomProducts.isDuplicate(duplicate, listOf(product)))
        assertFalse(CustomProducts.isDuplicate(duplicate.copy(categoryId = "misc"), listOf(product)))
        assertFalse(CustomProducts.isDuplicate(duplicate.copy(unitSingular = "pair", unitPlural = "pairs"), listOf(product)))
    }

    @Test fun `separate creations get distinct ids for idempotent independent uploads`() {
        val first = CustomProducts.create("misc", "en", "Book", "Book", 0)
        val second = CustomProducts.create("misc", "en", "Notebook", "Notebook", 0)
        assertNotEquals(first.id, second.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank report name is rejected`() {
        CustomProducts.create("misc", "ru", "Книга", "  ", 0)
    }

    @Test fun `custom product snapshot survives composition serialization`() {
        val product = CustomProducts.create("misc", "es", "Libro", "Book", 0)
        val items = listOf(CompositionItem(product, 3))
        assertEquals(items, Composition.decode(Composition.encode(items)))
        assertEquals("Book — 3 pcs", CompositionReport.text(CompositionReport.totals(listOf(items))))
    }
}
