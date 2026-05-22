package com.orderpackager.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.data.db.entity.Client
import com.orderpackager.repository.AppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.orderpackager.R

// ─── ViewModel ────────────────────────────────────────────────────────────────
class ClientSelectionViewModel(private val repo: AppRepository) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val clients: StateFlow<List<Client>> = _search
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) repo.getAllClients() else repo.searchClients(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { _search.value = q }

    suspend fun createOrder(clientId: Long, clientLastName: String): Long =
        repo.createOrder(clientId, clientLastName)
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSelectionScreen(
    repo:          AppRepository,
    onStartWork:   (orderId: Long, clientName: String) -> Unit,
    onEditClients: () -> Unit,
    onEditCyclic:  () -> Unit,
    onHistory:     () -> Unit,
    onSettings:    () -> Unit
) {
    val vm: ClientSelectionViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            ClientSelectionViewModel(repo) as T
    })

    val clients        by vm.clients.collectAsStateWithLifecycle()
    val search         by vm.search.collectAsStateWithLifecycle()
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    var showSearch     by remember { mutableStateOf(false) }
    val scope          = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Открыли поиск — фокус на поле
    LaunchedEffect(showSearch) {
        if (showSearch) runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                // Режим поиска — поле на весь топбар
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value         = search,
                            onValueChange = vm::setSearch,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder   = { Text(stringResource(R.string.search_client)) },
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor      = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                focusedBorderColor        = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor        = MaterialTheme.colorScheme.onPrimary,
                                focusedTextColor          = MaterialTheme.colorScheme.onPrimary,
                                cursorColor               = MaterialTheme.colorScheme.onPrimary,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                focusedPlaceholderColor   = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            vm.setSearch("")
                        }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
                )
            } else {
                // Обычный топбар
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, stringResource(R.string.search),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = onHistory) {
                            Icon(Icons.Default.History, stringResource(R.string.history),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        // Меню
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.menu),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            DropdownMenu(
                                expanded         = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text         = { Text(stringResource(R.string.clients_list)) },
                                    leadingIcon  = { Icon(Icons.Default.People, null) },
                                    onClick      = { menuExpanded = false; onEditClients() }
                                )
                                DropdownMenuItem(
                                    text         = { Text(stringResource(R.string.cyclic_list)) },
                                    leadingIcon  = { Icon(Icons.Default.FormatListNumbered, null) },
                                    onClick      = { menuExpanded = false; onEditCyclic() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text         = { Text(stringResource(R.string.settings)) },
                                    leadingIcon  = { Icon(Icons.Default.Settings, null) },
                                    onClick      = { menuExpanded = false; onSettings() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
                )
            }
        },
        // Нижняя кнопка "Начать" — фиксирована снизу
        bottomBar = {
            selectedClient?.let { client ->
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            scope.launch {
                                val orderId = vm.createOrder(client.id, client.lastName)
                                onStartWork(orderId, client.lastName)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.start_work, client.lastName),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── Заголовок раздела ─────────────────────────────────────────────
            if (!showSearch || search.isBlank()) {
                Text(
                    text  = if (clients.isEmpty()) stringResource(R.string.start_work)
                    else stringResource(R.string.select_client),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                Text(
                    text  = stringResource(R.string.results, clients.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // ─── Список клиентов ───────────────────────────────────────────────
            if (clients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOff, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (search.isNotBlank()) stringResource(R.string.no_search_results, search)
                            else stringResource(R.string.no_clients),
                            textAlign  = TextAlign.Center,
                            color      = MaterialTheme.colorScheme.outline,
                            modifier   = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(clients, key = { it.id }) { client ->
                        val isSelected = selectedClient?.id == client.id
                        ClientRow(
                            client     = client,
                            isSelected = isSelected,
                            onClick    = {
                                selectedClient = if (isSelected) null else client
                            }
                        )
                    }
                    // Отступ снизу если выбран клиент (под кнопкой)
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ─── Строка клиента ────────────────────────────────────────────────────────────
@Composable
private fun ClientRow(
    client:     Client,
    isSelected: Boolean,
    onClick:    () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Surface(color = bgColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар-буква
            Surface(
                shape  = RoundedCornerShape(50),
                color  = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text  = client.lastName.take(1).uppercase(),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text     = client.lastName,
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 74.dp),
            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}