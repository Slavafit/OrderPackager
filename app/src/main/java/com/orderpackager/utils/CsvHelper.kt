package com.orderpackager.utils

import android.content.Context
import android.net.Uri
import com.orderpackager.data.db.entity.Client
import com.orderpackager.data.db.entity.CyclicItem
import org.apache.poi.ss.usermodel.WorkbookFactory

object CsvHelper {

    // ─── Экспорт ──────────────────────────────────────────────────────────────

    fun exportClients(clients: List<Client>): String = buildString {
        appendLine("id,lastName")
        clients.forEach { appendLine("${it.id},${it.lastName}") }
    }

    fun exportCyclicItems(items: List<CyclicItem>): String = buildString {
        appendLine("position,name")
        items.sortedBy { it.position }.forEach { appendLine("${it.position},${it.name}") }
    }

    fun writeToUri(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)
            ?.bufferedWriter()?.use { it.write(content) }
    }

    // ─── Чтение файла ─────────────────────────────────────────────────────────

    fun readFromUri(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""

    /**
     * Универсальный импорт циклического списка.
     * Поддерживает:
     *  - .txt  — одно имя на строку
     *  - .csv  — колонка "name" или просто первая колонка
     *  - .xlsx / .xls — первая колонка первого листа (заголовок пропускается если не имя)
     */
    fun importCyclicItems(context: Context, uri: Uri): List<String> {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(context, uri) ?: ""

        return when {
            isExcel(mimeType, fileName) -> parseExcel(context, uri)
            isCsv(mimeType, fileName)   -> parseCyclicItemsCsv(readFromUri(context, uri))
            else                         -> parsePlainText(readFromUri(context, uri))
        }
    }

    fun importClients(context: Context, uri: Uri): List<String> {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(context, uri) ?: ""

        return when {
            isExcel(mimeType, fileName) -> parseExcel(context, uri)
            isCsv(mimeType, fileName)   -> parseClientsCsv(readFromUri(context, uri))
            else                         -> parsePlainText(readFromUri(context, uri))
        }
    }

    // ─── Парсеры ──────────────────────────────────────────────────────────────

    /**
     * Простой текст — одна запись на строку, пустые строки пропускаются.
     */
    private fun parsePlainText(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    /**
     * CSV с заголовком — ищем колонку "name" или "lastname",
     * если не находим — берём первую колонку.
     */
    fun parseCyclicItemsCsv(csv: String): List<String> {
        val lines = csv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val col = header.indexOf("name").takeIf { it >= 0 } ?: 0
        return lines.drop(1).mapNotNull { line ->
            line.split(",").getOrNull(col)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun parseClientsCsv(csv: String): List<String> {
        val lines = csv.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val col = header.indexOf("lastname").takeIf { it >= 0 } ?: 0
        return lines.drop(1).mapNotNull { line ->
            line.split(",").getOrNull(col)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Excel (.xlsx / .xls) — берём первую непустую колонку каждой строки.
     * Если первая строка выглядит как заголовок (не содержит пробела — т.е. одно слово
     * вроде "name", "имя") — пропускаем её.
     */
    private fun parseExcel(context: Context, uri: Uri): List<String> {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val workbook = WorkbookFactory.create(stream)
            val sheet    = workbook.getSheetAt(0)
            val rows     = mutableListOf<String>()

            for (row in sheet) {
                val cell = row.getCell(0) ?: continue
                val value = when (cell.cellType) {
                    org.apache.poi.ss.usermodel.CellType.STRING  -> cell.stringCellValue.trim()
                    org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                        cell.numericCellValue.toLong().toString()
                    else -> continue
                }
                if (value.isNotBlank()) rows += value
            }
            workbook.close()

            // Если первая строка — заголовок (одно слово, нет пробела)
            if (rows.isNotEmpty() && !rows.first().contains(" ")) {
                rows.drop(1)
            } else {
                rows
            }
        } ?: emptyList()
    }

    // ─── Вспомогательные ──────────────────────────────────────────────────────

    private fun isExcel(mimeType: String, fileName: String): Boolean =
        mimeType.contains("spreadsheet") ||
        mimeType.contains("excel") ||
        fileName.endsWith(".xlsx", ignoreCase = true) ||
        fileName.endsWith(".xls", ignoreCase = true)

    private fun isCsv(mimeType: String, fileName: String): Boolean =
        mimeType.contains("csv") ||
        fileName.endsWith(".csv", ignoreCase = true)

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        }
    }
}
