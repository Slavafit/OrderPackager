package com.orderpackager.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.data.db.entity.Client
import com.orderpackager.repository.AppRepository
import com.orderpackager.utils.CsvHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditClientsViewModel(private val repo: AppRepository) : ViewModel() {
    val clients: StateFlow<List<Client>> = repo.getAllClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun add(lastName: String) = repo.insertClient(lastName)
    suspend fun delete(client: Client) = repo.deleteClient(client)
    suspend fun update(client: Client) = repo.updateClient(client)
    suspend fun importCsv(names: List<String>) = names.forEach { repo.insertClient(it) }
    fun exportCsv(clients: List<Client>) = CsvHelper.exportClients(clients)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditClientsScreen(repo: AppRepository, onBack: () -> Unit) {
    val vm: EditClientsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(c: Class<T>) = EditClientsViewModel(repo) as T
    })

    val clients by vm.clients.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Добавление
    var showAddSheet by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    // Редактирование
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var editText by remember { mutableStateOf("") }

    // Удаление
    var deleteTarget by remember { mutableStateOf<Client?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val names = CsvHelper.importClients(context, it)
                vm.importCsv(names)
                snackbarHostState.showSnackbar("Импортировано: ${names.size} клиентов")
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                CsvHelper.writeToUri(context, it, vm.exportCsv(clients))
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
                        Text("Клиенты", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("${clients.size} в списке", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf(
                        "text/csv", "text/plain",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel"
                    )) }) {
                        Icon(Icons.Default.FileUpload, "Импорт", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { exportLauncher.launch("clients.csv") }) {
                        Icon(Icons.Default.FileDownload, "Экспорт", tint = MaterialTheme.colorScheme.onPrimary)
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
        if (clients.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PersonOff, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Список пуст", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddSheet = true }) { Text("Добавить клиента") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(clients, key = { it.id }) { client ->
                    ListItem(
                        headlineContent = {
                            if (editingClient?.id == client.id) {
                                val fr = remember { FocusRequester() }
                                LaunchedEffect(Unit) { fr.requestFocus() }
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                                )
                            } else {
                                Text(client.lastName, fontSize = 18.sp)
                            }
                        },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(client.lastName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        },
                        trailingContent = {
                            if (editingClient?.id == client.id) {
                                Row {
                                    IconButton(onClick = {
                                        scope.launch {
                                            vm.update(client.copy(lastName = editText.trim()))
                                            editingClient = null
                                        }
                                    }) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { editingClient = null }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            } else {
                                Row {
                                    IconButton(onClick = { editingClient = client; editText = client.lastName }) {
                                        Icon(Icons.Default.Edit, null)
                                    }
                                    IconButton(onClick = { deleteTarget = client }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }

    // Bottom sheet — добавить
    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Новый клиент", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { fr.requestFocus() }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("Фамилия") },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
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

    // Диалог удаления
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить клиента?") },
            text = { Text("«${target.lastName}» будет удалён из списка.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { vm.delete(target); deleteTarget = null } },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }
}