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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                        Text("Клиент: $clientName",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        Text("${state.currentIndex + 1} / ${state.totalItems}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
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
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Завершить заказ")
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
                    Text("Циклический список пуст!")
                    Text("Добавьте позиции в настройках")
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
                            onClick = {},
                            label = { Text("Сохранено ✓") },
                            leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = state.currentItem?.name ?: "—",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        lineHeight = 42.sp
                    )
                }
            }

            // ─── Навигация ← → ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.goPrev() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Предыдущий")
                }
                OutlinedButton(
                    onClick = { vm.goNext() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Следующий")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
                }
            }

            // ─── Состав — компактные чекбоксы ─────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Состав", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp))

                    // 2 колонки чекбоксов
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            CompactCheck("👔 Одежда",    state.hasClothes,     vm::setClothes)
                            CompactCheck("👟 Обувь",     state.hasShoes,       vm::setShoes)
                            CompactCheck("💄 Косметика", state.hasCosmetics,   vm::setCosmetics)
                        }
                        Column(Modifier.weight(1f)) {
                            CompactCheck("💍 Аксессуары", state.hasAccessories, vm::setAccessories)
                            CompactCheck("👜 Сумка",     state.hasOther,       vm::setOther)
                        }
                    }

                    if (state.hasOther) {
                        OutlinedTextField(
                            value = state.otherText,
                            onValueChange = vm::setOtherText,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            placeholder = { Text("Что именно?") },
                            singleLine = true
                        )
                    }
                }
            }

            // ─── Вес ──────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Крупное отображение веса
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.medium
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLoadingWeight) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        } else {
                            Text(
                                text = state.weightKg?.let { "${"%.3f".format(it)} кг" } ?: "— кг",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (state.weightKg != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    state.weightError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.fetchWeight() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoadingWeight
                    ) {
                        Icon(Icons.Default.Scale, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Получить вес", fontSize = 16.sp)
                    }
                }
            }

            // ─── Ошибка принтера ──────────────────────────────────────────────
            printError?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { printError = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }

            // ─── Кнопка Печать (= сохранить + следующий) ──────────────────────
            Button(
                onClick = {
                    isPrinting = true
                    printError = null
                    scope.launch {
                        // 1. Сохраняем позицию
                        vm.saveCurrentPosition()
                        // 2. Печатаем
                        val item = state.currentItem
                        if (item != null) {
                            val result = PrintHelper.printLabel(
                                context,
                                personName    = item.name,
                                weightKg      = state.weightKg ?: 0f,
                                composition   = vm.buildCompositionText()
                            )
                            result.onFailure { e -> printError = e.message }
                        }
                        // 3. Переходим к следующему
                        vm.goNext()
                        isPrinting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = !isPrinting
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Print, null)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isPrinting) "Печать…" else "🖨 Печать этикетки → далее",
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    // Диалог завершения
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, null) },
            title = { Text("Завершить заказ?") },
            text = {
                Text("Сохранено позиций: ${state.savedPositions.size}.\nПерейти к оформлению заказа для $clientName?")
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        vm.saveCurrentPosition()
                        showFinishDialog = false
                        onFinishOrder()
                    }
                }) { Text("Завершить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFinishDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun CompactCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.size(36.dp)
        )
        Text(label, fontSize = 15.sp)
    }
}
