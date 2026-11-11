package com.orderpackager.data.catalog

import android.content.Context
import com.google.gson.Gson

// IDs from the export are stable catalog IDs, not Firestore document UUIDs.
data class CatalogCategory(val id: String, val names: Map<String, String>, val sortOrder: Int)
data class CatalogProduct(
    val id: String,
    val categoryId: String,
    val names: Map<String, String>,
    val reportNameEn: String,
    val unitSingular: String,
    val unitPlural: String,
    val sortOrder: Int,
    val custom: Boolean,
    val source: String
)
data class ProductCatalog(
    val schemaVersion: Int,
    val categories: List<CatalogCategory>,
    val products: List<CatalogProduct>
) {
    companion object {
        fun load(context: Context): ProductCatalog =
            context.assets.open("product_catalog.json").bufferedReader().use {
                Gson().fromJson(it, ProductCatalog::class.java)
            }
    }
}

fun Map<String, String>.localized(language: String): String =
    this[language] ?: this["en"] ?: values.firstOrNull().orEmpty()

// Store a snapshot so historical orders remain readable after catalog updates.
data class CompositionItem(val product: CatalogProduct, val quantity: Int)

object Composition {
    private val gson = Gson()
    const val MAX_QUANTITY = 99999

    fun encode(items: List<CompositionItem>): String = gson.toJson(items.filter { it.quantity > 0 })
    fun decode(json: String?): List<CompositionItem> =
        if (json.isNullOrBlank()) emptyList() else
            gson.fromJson(json, Array<CompositionItem>::class.java)?.toList().orEmpty()
    fun categoryCount(items: List<CompositionItem>): Int =
        items.filter { it.quantity > 0 }.map { it.product.categoryId }.distinct().size
    fun text(items: List<CompositionItem>, language: String): String = items.joinToString(", ") {
        val name = if (language == "en") it.product.reportNameEn else it.product.names.localized(language)
        val unit = if (it.quantity == 1) it.product.unitSingular else it.product.unitPlural
        "$name — ${it.quantity} $unit"
    }
}


data class ProductTotal(val product: CatalogProduct, val quantity: Long)

object CompositionReport {
    // IDs, not translated labels or surnames, identify a product. Keep units
    // separate if a future catalog revision changes how a product is measured.
    fun totals(positions: List<List<CompositionItem>>): List<ProductTotal> = positions
        .flatten()
        .filter { it.quantity > 0 }
        .groupBy { Triple(it.product.id, it.product.unitSingular, it.product.unitPlural) }
        .values
        .map { entries -> ProductTotal(entries.first().product, entries.sumOf { it.quantity.toLong() }) }
        .sortedWith(compareBy({ it.product.categoryId }, { it.product.sortOrder }, { it.product.id }))

    fun text(totals: List<ProductTotal>): String = totals.joinToString("\n") {
        val unit = if (it.quantity == 1L) it.product.unitSingular else it.product.unitPlural
        "${it.product.reportNameEn} — ${it.quantity} $unit"
    }
}
