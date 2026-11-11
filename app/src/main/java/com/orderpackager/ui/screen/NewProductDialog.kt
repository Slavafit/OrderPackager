package com.orderpackager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orderpackager.R
import com.orderpackager.data.catalog.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NewProductDialog(
    category: CatalogCategory,
    language: String,
    onDismiss: () -> Unit,
    onCreate: suspend (CatalogProduct) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var report by rememberSaveable { mutableStateOf("") }
    var unitIndex by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val reportName = if (language == "en") name else report
    val valid = name.trim().length in 1..120 && reportName.trim().length in 1..120
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.catalog_new_product)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category.names.localized(language))
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 120) name = it },
                    label = { Text(stringResource(R.string.catalog_product_name)) },
                    singleLine = true, enabled = !saving
                )
                if (language != "en") OutlinedTextField(
                    value = report, onValueChange = { if (it.length <= 120) report = it },
                    label = { Text(stringResource(R.string.catalog_report_name)) },
                    singleLine = true, enabled = !saving
                )
                Text(stringResource(R.string.catalog_product_unit))
                val unitLabels = listOf(R.string.catalog_unit_pc, R.string.catalog_unit_pair, R.string.catalog_unit_package, R.string.catalog_unit_bottle)
                unitLabels.forEachIndexed { index, label ->
                    OutlinedButton(
                        onClick = { unitIndex = index }, enabled = !saving,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
                    ) { Text((if (unitIndex == index) "✓ " else "") + stringResource(label)) }
                }
                Text(stringResource(R.string.catalog_sync_hint), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        onCreate(CustomProducts.create(category.id, language, name, reportName, unitIndex))
                    } catch (e: CancellationException) { throw e }
                    catch (_: DuplicateProductException) { error = R.string.catalog_product_duplicate }
                    catch (_: Exception) { error = R.string.catalog_save_error }
                    finally { saving = false }
                }
            }, enabled = valid && !saving, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.catalog_create_product))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
