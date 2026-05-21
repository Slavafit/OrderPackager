package com.orderpackager.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Клиент ───────────────────────────────────────────────────────────────────
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lastName: String
)

// ─── Позиция в циклическом списке ─────────────────────────────────────────────
@Entity(tableName = "cyclic_items")
data class CyclicItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int
)

// ─── Заказ клиента ────────────────────────────────────────────────────────────
@Entity(tableName = "packing_orders")
data class PackingOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val clientLastName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val boxLength: Float = 0f,
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
    val isCompleted: Boolean = false
)

// ─── Обработанная позиция ─────────────────────────────────────────────────────
@Entity(tableName = "order_positions")
data class OrderPosition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val cyclicItemId: Long,
    val cyclicItemName: String,
    val hasClothes: Boolean = false,
    val hasShoes: Boolean = false,
    val hasCosmetics: Boolean = false,
    val hasAccessories: Boolean = false,
    val hasOther: Boolean = false,
    val otherText: String = "",
    val weightKg: Float = 0f
)
