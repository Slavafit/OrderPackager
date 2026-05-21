package com.orderpackager.utils

import android.content.Context
import android.content.Intent
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import java.text.SimpleDateFormat
import java.util.*

object ShareHelper {

    fun buildOrderText(
        order: PackingOrder,
        positions: List<OrderPosition>
    ): String {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(order.createdAt))
        val totalWeight = positions.sumOf { it.weightKg.toDouble() }

        val sb = StringBuilder()
        sb.appendLine("📦 ЗАКАЗ — ${order.clientLastName}")
        sb.appendLine("Дата: $date")
        sb.appendLine("──────────────────")

        positions.forEachIndexed { i, pos ->
            sb.appendLine("${i + 1}. ${pos.cyclicItemName}")
            sb.appendLine("   ${buildComposition(pos)}")
            sb.appendLine("   ${"%.2f".format(pos.weightKg)} кг")
        }

        sb.appendLine("──────────────────")
        sb.appendLine("Итого позиций: ${positions.size}")
        sb.appendLine("Общий вес: ${"%.2f".format(totalWeight)} кг")

        if (order.boxLength > 0 || order.boxWidth > 0 || order.boxHeight > 0) {
            sb.appendLine("Коробка: ${order.boxLength.toInt()} × ${order.boxWidth.toInt()} × ${order.boxHeight.toInt()} см")
        }

        return sb.toString().trimEnd()
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться заказом"))
    }

    fun buildComposition(pos: OrderPosition): String {
        val parts = mutableListOf<String>()
        if (pos.hasClothes) parts += "Одежда"
        if (pos.hasShoes) parts += "Обувь"
        if (pos.hasCosmetics) parts += "Косметика"
        if (pos.hasAccessories) parts += "Аксессуары"
        if (pos.hasOther && pos.otherText.isNotBlank()) parts += "Другое: ${pos.otherText}"
        else if (pos.hasOther) parts += "Другое"
        return if (parts.isEmpty()) "—" else parts.joinToString(", ")
    }
}
