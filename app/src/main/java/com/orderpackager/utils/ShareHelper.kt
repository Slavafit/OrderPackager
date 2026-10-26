package com.orderpackager.utils

import android.content.Context
import android.content.Intent
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import java.text.SimpleDateFormat
import java.util.*
import com.orderpackager.R

object ShareHelper {

    fun buildOrderText(
        context: Context,
        order: PackingOrder,
        positions: List<OrderPosition>
    ): String {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(order.createdAt))
        val totalWeight = positions.sumOf { it.weightKg.toDouble() }

        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.report_order_title, order.clientLastName))
        sb.appendLine(context.getString(R.string.report_box_number, order.boxNumber.ifBlank { "—" }))
        sb.appendLine(context.getString(R.string.report_batch_number, order.batchNumber.ifBlank { "—" }))
        sb.appendLine(context.getString(R.string.report_date, date))
        sb.appendLine("──────────────────")

        positions.forEachIndexed { i, pos ->
            sb.appendLine("${i + 1}. ${pos.cyclicItemName}")
            sb.appendLine("   ${buildComposition(context, pos)}")
            sb.appendLine("   ${context.getString(R.string.weight_kg, "%.2f".format(pos.weightKg))}")
        }

        sb.appendLine("─────────────────")
        sb.appendLine(context.getString(R.string.report_positions_total, positions.size))
        sb.appendLine(context.getString(R.string.report_total_weight, "%.2f".format(totalWeight)))

        if (order.boxLength > 0 || order.boxWidth > 0 || order.boxHeight > 0) {
            sb.appendLine(context.getString(R.string.report_box_size,
                order.boxLength.toInt(), order.boxWidth.toInt(), order.boxHeight.toInt()))
        }

        return sb.toString().trimEnd()
    }

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_order)))
    }

    fun buildComposition(context: Context, pos: OrderPosition): String {
        val parts = mutableListOf<String>()
        if (pos.hasClothes) parts += context.getString(R.string.content_clothes)
        if (pos.hasShoes) parts += context.getString(R.string.content_shoes)
        if (pos.hasCosmetics) parts += context.getString(R.string.content_cosmetics)
        if (pos.hasAccessories) parts += context.getString(R.string.content_accessories)
        if (pos.hasBags) parts += context.getString(R.string.content_bags)
        if (pos.hasOther && pos.otherText.isNotBlank()) parts += context.getString(R.string.content_other_value, pos.otherText)
        else if (pos.hasOther) parts += context.getString(R.string.content_other)
        return if (parts.isEmpty()) "—" else parts.joinToString(", ")
    }
}
