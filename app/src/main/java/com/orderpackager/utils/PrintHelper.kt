package com.orderpackager.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import com.orderpackager.R
import com.orderpackager.BuildConfig

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
                Exception(context.getString(R.string.printer_unknow))
            )
        }

        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val zpl  = buildZpl(
            name = personName,
            weightKg = weightKg,
            composition = composition,
            date = date,
            appInfo = buildAppInfo(context)
        )

        runCatching {
            Socket(ip, PRINT_PORT).use { socket ->
                socket.soTimeout = 5_000
                val out: OutputStream = socket.getOutputStream()
                out.write(zpl.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    suspend fun printOrderSummaryLabel(
        context: Context,
        boxNumber: String,
        batchNumber: String,
        totalWeightKg: Float,
        boxLengthCm: Float,
        boxWidthCm: Float,
        boxHeightCm: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = getPrinterIp(context)
        if (ip.isBlank()) {
            return@withContext Result.failure(
                Exception(context.getString(R.string.printer_unknow))
            )
        }

        val now = Date()
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(now)
        val zpl = buildOrderSummaryZpl(
            boxNumber = boxNumber,
            batchNumber = batchNumber,
            totalWeightKg = totalWeightKg,
            boxLengthCm = boxLengthCm,
            boxWidthCm = boxWidthCm,
            boxHeightCm = boxHeightCm,
            date = date,
            appName = context.getString(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
            developer = context.getString(R.string.developer_signature)
        )

        runCatching {
            Socket(ip, PRINT_PORT).use { socket ->
                socket.soTimeout = 5_000
                val out: OutputStream = socket.getOutputStream()
                out.write(zpl.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    internal fun buildOrderSummaryZpl(
        boxNumber: String,
        batchNumber: String,
        totalWeightKg: Float,
        boxLengthCm: Float,
        boxWidthCm: Float,
        boxHeightCm: Float,
        date: String,
        appName: String,
        version: String,
        developer: String
    ): String {
        val shortNumber = boxNumber.takeLast(5)
        val sizes = listOf(boxLengthCm, boxWidthCm, boxHeightCm)
            .joinToString("x") { formatDimension(it) }
        return """
^XA
^PW800
^LL1200
^CI28

^FO60,45^A0N,58,58^FDOnza España^FS

^FO60,130^BY3,2,120^BCN,120,N,N,N^FD$boxNumber^FS
^FO40,260^A0N,28,28^FB720,1,0,C^FD$boxNumber^FS

^FO60,325^A0N,82,82^FD$shortNumber^FS

^FO60,425^GB680,3,3^FS
^FO60,445^A0N,30,30^FDPacked in Malaga: $date^FS
^FO60,490^GB680,3,3^FS

^FO60,520^A0N,52,52^FDBatch: $batchNumber^FS

^FO60,590^A0N,54,54^FDWeight total: ${"%.2f".format(Locale.US, totalWeightKg)} kg^FS

^FO60,715^A0N,44,44^FDSizes: $sizes cm^FS

^FO70,825^BQN,2,8^FDLA,$boxNumber^FS
^FO315,875^A0N,34,34^FD$appName^FS
^FO315,925^A0N,27,27^FDVersion $version^FS
^FO315,965^A0N,27,27^FD$developer^FS

^PQ2
^XZ
        """.trimIndent()
    }

    private fun formatDimension(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else "%.1f".format(Locale.US, value)

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
        date:        String,
        appInfo:     String
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

^FO40,165^A0N,50,50^FDВес: ${"%.2f".format(weightKg)} кг^FS

^FO40,240^A0N,40,40^FDСостав:^FS
^FO40,290^A0N,35,35^FD$compShort^FS

^FO40,360^GB720,3,3^FS

^FO40,380^A0N,35,35^FD$date^FS

^FO40,1100^GB720,2,2^FS
^FO40,1120^A0N,28,28^FB720,1,0,C^FD$appInfo^FS

^PQ1
^XZ
        """.trimIndent()
    }

    private fun buildAppInfo(context: Context): String =
        "${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} - " +
            context.getString(R.string.developer_signature)
}
