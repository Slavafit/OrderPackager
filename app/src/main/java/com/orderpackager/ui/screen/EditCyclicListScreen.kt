package com.orderpackager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.data.db.entity.CyclicItem
import com.orderpackager.repository.AppRepository
import com.orderpackager.utils.CsvHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditCyclicListViewModel(private val repo: AppRepository) : ViewModel() {
    val items: StateFlow<List<CyclicItem>> = repo.getAllCyclicItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun add(name: String) = repo.insertCyclicItem(name)
    suspend fun delete(item: CyclicItem) = repo.deleteCyclicItem(item)
    suspend fun clearAll() = repo.clearCyclicItems()
    suspend fun moveUp(items: List<CyclicItem>, index: Int) {
        if (index <= 0) return
        val m = items.toMutableList()
        val tmp = m[index]; m[index] = m[index - 1]; m[index - 1] = tmp
        repo.reorderCyclicItems(m)
    }
    suspend fun moveDown(items: List<CyclicItem>, index: Int) {
        if (index >= items.size - 1) return
        val m = items.toMutableList()
        val tmp = m[index]; m[index] = m[index + 1]; m[index + 1] = tmp
        repo.reorderCyclicItems(m)
    }
    suspend fun importAndReplace(names: List<String>) = repo.importCyclicItems(names)
    fun exportCsv(items: List<CyclicItem>) = CsvHelper.exportCyclicItems(items)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditCyclicListScreen(repo: AppRepository, onBack: () -> Unit) {
    val vm: EditCyclicListViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(c: Class<T>) = EditCyclicListViewModel(repo) as T
    })

    val items by vm.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet       by remember { mutableStateOf(false) }
    var newName            by remember { mutableStateOf("") }
    var deleteTarget       by remember { mutableStateOf<CyclicItem?>(null) }
    var showClearDialog    by remember { mutableStateOf(false) }
    // Предпросмотр импорта перед применением
    var pendingImport      by remember { mutableStateOf<List<String>?>(null) }
    var showImportPreview  by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val names = CsvHelper.importCyclicItems(context, it)
                if (names.isNotEmpty()) {
                    pendingImport = names
                    showImportPreview = true
                } else {
                    snackbarHostState.showSnackbar("Файл пуст или не распознан")
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                CsvHelper.writeToUri(context, it, vm.exportCsv(items))
                snackbarHostState.showSnackbar("Экспорт выполнен")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Циклический список", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("${items.size} позиций • удержите для удаления", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf(
                            "text/csv", "text/plain",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel"
                        ))
                    }) {
                        Icon(Icons.Default.FileUpload, "Импорт", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { exportLauncher.launch("cyclic_list.csv") }) {
                        Icon(Icons.Default.FileDownload, "Экспорт", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    // Очистить всё
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Очистить",
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newName = ""; showAddSheet = true }) {
                Icon(Icons.Default.Add, "Добавить")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.FormatListNumbered, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("Список пуст", color = MaterialTheme.colorScheme.outline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddSheet = true }) { Text("Добавить") }
                        OutlinedButton(onClick = {
                            importLauncher.launch(arrayOf(
                                "text/csv", "text/plain",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        }) {
                            Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Импорт")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = { deleteTarget = item }
                        ),
                        headlineContent = { Text(item.name, fontSize = 17.sp) },
                        leadingContent = {
                            Text("${index + 1}", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(28.dp))
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { scope.launch { vm.moveUp(items, index) } },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) { Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(20.dp)) }
                                IconButton(
                                    onClick = { scope.launch { vm.moveDown(items, index) } },
                                    enabled = index < items.size - 1,
                                    modifier = Modifier.size(36.dp)
                                ) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp)) }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                }
            }
        }
    }

    // Bottom sheet — добавить
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Новая позиция", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("Имя Фамилия") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch { vm.add(newName.trim()); showAddSheet = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = newName.isNotBlank()
                ) { Text("Добавить", fontSize = 16.sp) }
            }
        }
    }

    // Диалог удаления одной позиции (long press)
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить позицию?") },
            text  = { Text("«${target.name}» будет удалена из списка.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { vm.delete(target); deleteTarget = null } },
                    colors  = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }

    // Диалог очистки всего списка
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon  = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Очистить список?") },
            text  = { Text("Все ${items.size} позиций будут удалены. Это нельзя отменить.\n\nПосле этого вы сможете загрузить новый список через импорт.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            vm.clearAll()
                            showClearDialog = false
                            snackbarHostState.showSnackbar("Список очищен")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text("Очистить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Предпросмотр импорта — показываем сколько строк и первые несколько
    if (showImportPreview && pendingImport != null) {
        val names = pendingImport!!
        AlertDialog(
            onDismissRequest = { showImportPreview = false; pendingImport = null },
            icon  = { Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Подтвердить импорт") },
            text  = {
                Column {
                    Text("Найдено позиций: ${names.size}")
                    Spacer(Modifier.height(4.dp))
                    Text("Первые строки:", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline)
                    names.take(5).forEach {
                        Text("• $it", fontSize = 13.sp)
                    }
                    if (names.size > 5) {
                        Text("…ещё ${names.size - 5}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (items.isEmpty())
                            "Список будет заполнен."
                        else
                            "⚠️ Текущий список (${items.size} поз.) будет заменён.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (items.isEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        vm.importAndReplace(names)
                        showImportPreview = false
                        pendingImport = null
                        snackbarHostState.showSnackbar("Импортировано: ${names.size} позиций")
                    }
                }) { Text("Загрузить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportPreview = false; pendingImport = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}