package com.orderpackager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.R
import com.orderpackager.repository.AppRepository
import com.orderpackager.ui.viewmodel.WorkingViewModel
import com.orderpackager.utils.PrintHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkingScreen(
    repo: AppRepository,
    orderId: Long,
    clientName: String,
    onFinishOrder: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: WorkingViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            WorkingViewModel(repo, orderId, clientName, context) as T
    })

    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFinishDialog by remember { mutableStateOf(false) }
    var isFinishRequested by remember { mutableStateOf(false) }
    var printError by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.client_label, clientName),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Text(
                            stringResource(R.string.position_counter, state.currentIndex + 1, state.totalItems),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showFinishDialog = true },
                        colors  = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.finish_order))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.cyclicItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.cyclic_list_empty))
                    Text(stringResource(R.string.cyclic_list_empty_hint))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ─── Имя позиции ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isCurrentSaved)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isCurrentSaved) {
                        AssistChip(
                            onClick     = {},
                            label       = { Text(stringResource(R.string.saved_check)) },
                            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text       = state.currentItem?.name ?: "—",
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = MaterialTheme.colorScheme.primary,
                        textAlign  = TextAlign.Center,
                        lineHeight = 42.sp
                    )
                }
            }

            // ─── Навигация ← → ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { vm.goPrev() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.prev))
                }
                OutlinedButton(onClick = { vm.goNext() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.next))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                }
            }

            // ─── Состав ───────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.composition),
                        style    = MaterialTheme.typography.labelLarge,
                        color    = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            CompactCheck(stringResource(R.string.clothes),    state.hasClothes,     vm::setClothes)
                            CompactCheck(stringResource(R.string.shoes),      state.hasShoes,       vm::setShoes)
                            CompactCheck(stringResource(R.string.cosmetics),  state.hasCosmetics,   vm::setCosmetics)
                        }
                        Column(Modifier.weight(1f)) {
                            CompactCheck(stringResource(R.string.accessories), state.hasAccessories, vm::setAccessories)
                            CompactCheck(stringResource(R.string.bags), state.hasBags, vm::setBags)
                            CompactCheck(stringResource(R.string.other),       state.hasOther,       vm::setOther)
                        }
                    }
                    if (state.hasOther) {
                        OutlinedTextField(
                            value         = state.otherText,
                            onValueChange = vm::setOtherText,
                            modifier      = Modifier.fillMaxWidth().padding(top = 4.dp),
                            placeholder   = { Text(stringResource(R.string.other_hint)) },
                            singleLine    = true
                        )
                    }
                }
            }

            // ─── Вес ──────────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp)) {
                    // Крупное отображение
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLoadingWeight && !state.autoWeight) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        } else {
                            Text(
                                text = state.weightKg
                                    ?.let { "${"%.2f".format(it)} ${context.getString(R.string.kg_unit)}" }
                                    ?: stringResource(R.string.weight_empty),
                                fontSize   = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (state.weightKg != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    state.weightError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    // Переключатель авто / вручную
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(if (state.autoWeight) R.string.weight_auto else R.string.weight_manual),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.autoWeight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                        Switch(
                            checked         = state.autoWeight,
                            onCheckedChange = { vm.toggleAutoWeight() }
                        )
                    }

                    // Ручная кнопка — только когда авто выключен
                    if (!state.autoWeight) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick  = { vm.fetchWeight() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !state.isLoadingWeight
                        ) {
                            Icon(Icons.Default.Scale, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.get_weight), fontSize = 16.sp)
                        }
                    }
                }
            }

            // ─── Ошибка принтера ──────────────────────────────────────────────
            printError?.let { err ->
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { printError = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }

            // ─── Кнопка Печать ────────────────────────────────────────────────
            Button(
                onClick = {
                    isPrinting = true
                    printError = null
                    scope.launch {
                        vm.saveCurrentPosition()
                        val item = state.currentItem
                        if (item != null) {
                            PrintHelper.printLabel(
                                context,
                                personName  = item.name,
                                weightKg    = state.weightKg ?: 0f,
                                composition = vm.buildCompositionText()
                            ).onFailure { e -> printError = e.message }
                        }
                        vm.goNext()
                        isPrinting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled  = !isPrinting
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Print, null)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (isPrinting) R.string.printing else R.string.print_label),
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    // Диалог завершения заказа
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            icon  = { Icon(Icons.Default.CheckCircle, null) },
            title = { Text(stringResource(R.string.finish_order_dialog_title)) },
            text  = {
                Text(stringResource(R.string.finish_order_dialog_body,
                    state.savedPositions.size, clientName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isFinishRequested) return@Button
                        isFinishRequested = true
                        showFinishDialog = false
                        onFinishOrder()
                    },
                    enabled = !isFinishRequested
                ) { Text(stringResource(R.string.finish_order)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CompactCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, modifier = Modifier.size(36.dp))
        Text(label, fontSize = 15.sp)
    }
}
