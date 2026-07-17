package com.orderpackager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orderpackager.ui.ThemeMode
import com.orderpackager.ui.saveThemeMode
import com.orderpackager.utils.PrintHelper
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.orderpackager.R

private const val PREF_SCALE_IP   = "scale_ip"
private const val DEFAULT_SCALE_IP = "192.168.1.50"
private const val DEFAULT_PRINTER_IP = "192.168.1.168"

fun getScaleIp(context: android.content.Context): String =
    context.getSharedPreferences("packager_prefs", android.content.Context.MODE_PRIVATE)
        .getString(PREF_SCALE_IP, DEFAULT_SCALE_IP) ?: DEFAULT_SCALE_IP

fun saveScaleIp(context: android.content.Context, ip: String) =
    context.getSharedPreferences("packager_prefs", android.content.Context.MODE_PRIVATE)
        .edit().putString(PREF_SCALE_IP, ip.trim()).apply()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme:  ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack:        () -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Если IP ещё не сохранён — подставляем дефолт
    var printerIp by remember {
        mutableStateOf(
            PrintHelper.getPrinterIp(context).ifBlank { DEFAULT_PRINTER_IP }
        )
    }
    var scaleIp by remember {
        mutableStateOf(getScaleIp(context))
    }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting  by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // imePadding поднимает контент над клавиатурой
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── Тема ─────────────────────────────────────────────────────────
            SettingsCard(icon = Icons.Default.Palette, title = stringResource(R.string.theme_section)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val (label, icon) = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system) to Icons.Default.AutoMode
                            ThemeMode.LIGHT  -> stringResource(R.string.theme_light) to Icons.Default.LightMode
                            ThemeMode.DARK   -> stringResource(R.string.theme_dark) to Icons.Default.DarkMode
                        }
                        FilterChip(
                            selected    = currentTheme == mode,
                            onClick     = { onThemeChange(mode); saveThemeMode(context, mode) },
                            label       = { Text(label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                            modifier    = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─── Весы ─────────────────────────────────────────────────────────
            SettingsCard(icon = Icons.Default.Scale, title = stringResource(R.string.scale_section)) {
                Text(
                    stringResource(R.string.scale_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value           = scaleIp,
                    onValueChange   = { scaleIp = it },
                    label           = { Text(stringResource(R.string.scale_ip_label)) },
                    placeholder     = { Text(DEFAULT_SCALE_IP) },
                    leadingIcon     = { Icon(Icons.Default.Wifi, null) },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        saveScaleIp(context, scaleIp)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.printer_ip_saved)) }
                    })
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        saveScaleIp(context, scaleIp)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.scale_ip_saved)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = scaleIp.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            }

            // ─── Принтер Zebra ────────────────────────────────────────────────
            SettingsCard(icon = Icons.Default.Print, title = stringResource(R.string.printer_section)) {
                Text(
                    stringResource(R.string.printer_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value           = printerIp,
                    onValueChange   = { printerIp = it },
                    label           = { Text(stringResource(R.string.printer_ip_label)) },
                    placeholder     = { Text(DEFAULT_PRINTER_IP) },
                    leadingIcon     = { Icon(Icons.Default.Wifi, null) },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        PrintHelper.savePrinterIp(context, printerIp)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.printer_ip_saved)) }
                    })
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            PrintHelper.savePrinterIp(context, printerIp)
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.printer_ip_saved)) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled  = printerIp.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save))
                    }
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            PrintHelper.savePrinterIp(context, printerIp)
                            isTesting  = true
                            testResult = null
                            scope.launch {
                                val result = PrintHelper.printLabel(
                                    context,
                                    personName  = context.getString(R.string.printer_test),
                                    weightKg    = 1.234f,
                                    composition = context.getString(R.string.printer_test)
                                )
                                testResult = result.fold(
                                    onSuccess = { context.getString(R.string.printer_test_ok) },
                                    onFailure = { "❌ ${it.message}" }
                                )
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled  = printerIp.isNotBlank() && !isTesting
                    ) {
                        if (isTesting)
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Print, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.printer_test))
                    }
                }
                testResult?.let { res ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        res,
                        color = if (res.startsWith("✅"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize   = 14.sp
                    )
                }
            }

            SettingsCard(icon = Icons.Default.Info, title = stringResource(R.string.developer_section)) {
                Text(
                    stringResource(R.string.developer_name),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.developer_telegram),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Отступ снизу — чтобы последняя карточка не прилипала к клавиатуре
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    title:   String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
