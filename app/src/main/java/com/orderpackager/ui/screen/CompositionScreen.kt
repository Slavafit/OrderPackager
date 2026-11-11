package com.orderpackager.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orderpackager.R
import com.orderpackager.data.catalog.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompositionScreen(
    initial: List<CompositionItem>,
    clientName: String,
    onCancel: () -> Unit,
    onSave: suspend (List<CompositionItem>) -> Unit
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language.let {
        if (it in listOf("ru", "en", "es")) it else "en"
    }
    val auth = remember { com.orderpackager.data.auth.SupabaseAuthManager.getInstance(context) }
    val authState by auth.state.collectAsState()
    val ownerId = remember(authState) { auth.currentUserId.orEmpty() }
    val repository = remember { CatalogRepository.getInstance(context) }
    val bundled = remember { ProductCatalog.load(context) }
    val catalog by remember(ownerId) { repository.observe(ownerId) }.collectAsState(initial = bundled)
    var showNewProduct by rememberSaveable { mutableStateOf(false) }
    var createdProductJson by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) com.orderpackager.data.sync.CloudSyncManager.getInstance(context).enqueue()
    }
    var draftJson by rememberSaveable { mutableStateOf(Composition.encode(initial)) }
    val items = remember(draftJson) { Composition.decode(draftJson) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var showList by rememberSaveable { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val createdProduct = remember(createdProductJson) {
        createdProductJson?.let { com.google.gson.Gson().fromJson(it, CatalogProduct::class.java) }
    }
    val product = catalog.products.firstOrNull { it.id == productId }
        ?: items.firstOrNull { it.product.id == productId }?.product
        ?: createdProduct?.takeIf { it.id == productId }
    val number = quantity.toIntOrNull()?.takeIf { it in 0..Composition.MAX_QUANTITY }

    fun pendingItems(selectedQuantity: Int? = number): List<CompositionItem> {
        if (product == null || selectedQuantity == null) return items
        return items.filterNot { it.product.id == product.id } +
            if (selectedQuantity > 0) listOf(CompositionItem(product, selectedQuantity)) else emptyList()
    }
    fun back() {
        when {
            saving -> Unit
            productId != null -> productId = null
            showList -> showList = false
            categoryId != null -> categoryId = null
            else -> discardDialog = true
        }
    }
    BackHandler { back() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.composition_client, clientName)) }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { back() }, enabled = !saving) { Text(stringResource(R.string.catalog_back)) }
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = {
                    draftJson = Composition.encode(pendingItems())
                    productId = null
                    showList = true
                }, enabled = !saving && (product == null || number != null)) { Text(stringResource(R.string.catalog_list)) }
                Button(shape = MaterialTheme.shapes.medium, onClick = {
                    val result = pendingItems()
                    saving = true
                    saveError = false
                    scope.launch {
                        try { onSave(result) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { saveError = true }
                        finally { saving = false }
                    }
                }, enabled = !saving && (product == null || number != null), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.catalog_finish))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (saveError) Text(stringResource(R.string.catalog_save_error), color = MaterialTheme.colorScheme.error)
            when {
                product != null -> {
                    Text(product.names.localized(language), style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { quantity = ((number ?: 1) - 1).coerceAtLeast(0).toString() }) { Text("−") }
                        Text(quantity, fontSize = 32.sp)
                        Button(shape = MaterialTheme.shapes.medium, onClick = { quantity = ((number ?: 0) + 1).coerceAtMost(Composition.MAX_QUANTITY).toString() }) { Text("+") }
                    }
                    listOf(listOf(1, 2, 3, 4), listOf(5, 6, 8, 10)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { value ->
                                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = {
                                    draftJson = Composition.encode(pendingItems(value))
                                    productId = null
                                    categoryId = null
                                    showList = false
                                }, modifier = Modifier.weight(1f)) { Text("$value") }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) quantity = it },
                        label = { Text(stringResource(R.string.catalog_quantity)) },
                        supportingText = { Text(stringResource(R.string.catalog_quantity_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = number == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(shape = MaterialTheme.shapes.medium, onClick = {
                        draftJson = Composition.encode(pendingItems())
                        productId = null
                        categoryId = null
                        showList = false
                    }, enabled = number != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.catalog_add_another)) }
                }
                showList -> {
                    if (items.isEmpty()) Text(stringResource(R.string.catalog_empty))
                    items.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(Composition.text(listOf(item), language))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                                        categoryId = item.product.categoryId
                                        productId = item.product.id
                                        quantity = item.quantity.toString()
                                    }) { Text(stringResource(R.string.catalog_edit)) }
                                    TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                                        draftJson = Composition.encode(items.filterNot { it.product.id == item.product.id })
                                    }) { Text(stringResource(R.string.catalog_remove)) }
                                }
                            }
                        }
                    }
                }
                categoryId == null -> {
                    Text(stringResource(R.string.composition_stats, Composition.categoryCount(items), items.sumOf { it.quantity.toLong() }))
                    catalog.categories.sortedBy { it.sortOrder }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { category ->
                                Button(shape = MaterialTheme.shapes.medium, onClick = { categoryId = category.id }, modifier = Modifier.weight(1f).heightIn(min = 88.dp)) {
                                    Text(category.names.localized(language))
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text(catalog.categories.firstOrNull { it.id == categoryId }?.names?.localized(language).orEmpty(), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = { showNewProduct = true },
                        enabled = ownerId.isNotBlank() && catalog.categories.any { it.id == categoryId },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                    ) { Text(stringResource(R.string.catalog_new_product)) }
                    catalog.products.filter { it.categoryId == categoryId }.sortedBy { it.sortOrder }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { entry ->
                                Button(shape = MaterialTheme.shapes.medium, onClick = {
                                    productId = entry.id
                                    quantity = (items.firstOrNull { it.product.id == entry.id }?.quantity ?: 1).toString()
                                }, modifier = Modifier.weight(1f).heightIn(min = 88.dp)) { Text(entry.names.localized(language)) }
                            }
                        }
                    }
                }
            }
        }
    }
    val selectedCategory = catalog.categories.firstOrNull { it.id == categoryId }
    if (showNewProduct && selectedCategory != null) NewProductDialog(
        category = selectedCategory,
        language = language,
        onDismiss = { showNewProduct = false },
        onCreate = { entry ->
            repository.add(ownerId, entry)
            createdProductJson = com.google.gson.Gson().toJson(entry)
            productId = entry.id
            quantity = "1"
            showNewProduct = false
        }
    )
    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false },
        title = { Text(stringResource(R.string.catalog_discard)) },
        confirmButton = { TextButton(shape = MaterialTheme.shapes.medium, onClick = onCancel) { Text(stringResource(R.string.catalog_leave)) } },
        dismissButton = { TextButton(shape = MaterialTheme.shapes.medium, onClick = { discardDialog = false }) { Text(stringResource(R.string.catalog_continue)) } }
    )
}
