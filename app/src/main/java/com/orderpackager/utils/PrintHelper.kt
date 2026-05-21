package com.orderpackager.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

object PrintHelper {

    private const val PRINT_PORT = 9100
    const val PREF_PRINTER_IP   = "printer_ip"
    private const val PREFS_NAME = "packager_prefs"

    // ─── Настройки ────────────────────────────────────────────────────────────
    fun getPrinterIp(context: Context): String =
        prefs(context).getString(PREF_PRINTER_IP, "") ?: ""

    fun savePrinterIp(context: Context, ip: String) =
        prefs(context).edit().putString(PREF_PRINTER_IP, ip.trim()).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Печать ───────────────────────────────────────────────────────────────
    /**
     * Отправка ZPL на Zebra по TCP:9100.
     * Размер этикетки: 100×150мм (800×1200 dot при 203dpi).
     */
    suspend fun printLabel(
        context:     Context,
        personName:  String,
        weightKg:    Float,
        composition: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = getPrinterIp(context)
        if (ip.isBlank()) {
            return@withContext Result.failure(
                Exception("IP принтера не задан. Укажите его в Настройках.")
            )
        }

        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val zpl  = buildZpl(personName, weightKg, composition, date)

        runCatching {
            Socket(ip, PRINT_PORT).use { socket ->
                socket.soTimeout = 5_000
                val out: OutputStream = socket.getOutputStream()
                out.write(zpl.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    /**
     * ZPL для Zebra, 100×150мм при 203 dpi:
     *   ширина  = 100мм × 8 dot/мм = 800 dot  → ^PW800
     *   высота  = 150мм × 8 dot/мм = 1200 dot → ^LL1200
     *
     * Одна этикетка: ^PQ1
     */
    private fun buildZpl(
        name:        String,
        weightKg:    Float,
        composition: String,
        date:        String
    ): String {
        // Обрезаем строки — Zebra не переносит автоматически
        val nameShort  = name.take(30)
        val compShort  = composition.take(50)

        return """
^XA
^PW800
^LL1200
^CI28

^FO40,40^A0N,70,70^FD$nameShort^FS

^FO40,140^GB720,3,3^FS

^FO40,165^A0N,50,50^FDВес: ${"%.3f".format(weightKg)} кг^FS

^FO40,240^A0N,40,40^FDСостав:^FS
^FO40,290^A0N,35,35^FD$compShort^FS

^FO40,360^GB720,3,3^FS

^FO40,380^A0N,35,35^FD$date^FS

^PQ1
^XZ
        """.trimIndent()
    }
}
