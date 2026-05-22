package com.orderpackager.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import com.orderpackager.repository.AppRepository
import com.orderpackager.utils.ShareHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.orderpackager.R

class OrderHistoryViewModel(private val repo: AppRepository) : ViewModel() {
    val todayOrders: StateFlow<List<PackingOrder>> = repo.getTodayOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getPositions(orderId: Long) = repo.getPositionsForOrderOnce(orderId)
    suspend fun deleteOrder(order: PackingOrder) = repo.deleteOrder(order)
    suspend fun updateOrder(order: PackingOrder) = repo.updateOrder(order)
    suspend fun updatePosition(pos: OrderPosition) = repo.upsertPosition(pos)
    suspend fun deletePosition(pos: OrderPosition) = repo.deletePosition(pos)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(repo: AppRepository, onBack: () -> Unit) {
    val vm: OrderHistoryViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(c: Class<T>) = OrderHistoryViewModel(repo) as T
    })

    val orders by vm.todayOrders.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val today   = remember { dateFmt.format(Date()) }

    var expandedId       by remember { mutableStateOf<Long?>(null) }
    var positionsCache   by remember { mutableStateOf<Map<Long, List<OrderPosition>>>(emptyMap()) }
    var deleteTarget     by remember { mutableStateOf<PackingOrder?>(null) }
    // Редактирование позиции
    var editingPos       by remember { mutableStateOf<OrderPosition?>(null) }
    var editWeight       by remember { mutableStateOf("") }
    var deletePositionTarget by remember { mutableStateOf<OrderPosition?>(null) }
    // Редактирование заказа
    var editingOrder  by remember { mutableStateOf<PackingOrder?>(null) }
    var editBoxL      by remember { mutableStateOf("") }
    var editBoxW      by remember { mutableStateOf("") }
    var editBoxH      by remember { mutableStateOf("") }
    var editCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text(stringResource(R.string.history_subtitle, today, orders.size), fontSize = 12.sp,
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
                        scope.launch {
                            val sb = StringBuilder()
                            sb.appendLine(stringResource(R.string.daily_report_for, today))
                            sb.appendLine("═══════════════════")
                            orders.forEach { order ->
                                val pos = positionsCache[order.id] ?: vm.getPositions(order.id)
                                val w = pos.sumOf { it.weightKg.toDouble() }
                                sb.appendLine("• ${order.clientLastName} — ${pos.size} поз. / ${"%.2f".format(w)} кг")
                            }
                            sb.appendLine("═══════════════════")
                            val grand = orders.flatMap { positionsCache[it.id] ?: vm.getPositions(it.id) }
                                .sumOf { it.weightKg.toDouble() }
                            sb.appendLine("Всего: ${orders.size} зак. / ${"%.2f".format(grand)} кг")
                            ShareHelper.share(context, sb.toString())
                        }
                    }) {
                        Icon(Icons.Default.Share, "Отчёт", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->

        if (orders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.no_orders_today), color = MaterialTheme.colorScheme.outline)
                }
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding)) {
            // Плашка итогов
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatChip(stringResource(R.string.orders_today),    "${orders.size}")
                    StatChip(stringResource(R.string.orders_done), "${orders.count { it.isCompleted }}")
                    StatChip(stringResource(R.string.orders_in_progress), "${orders.count { !it.isCompleted }}")
                }
            }

            LazyColumn {
                items(orders, key = { it.id }) { order ->
                    val isExpanded = expandedId == order.id
                    val positions  = positionsCache[order.id]

                    LaunchedEffect(isExpanded) {
                        if (isExpanded && positions == null) {
                            positionsCache = positionsCache + (order.id to vm.getPositions(order.id))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(if (isExpanded) 4.dp else 1.dp)
                    ) {
                        Column {
                            // Заголовок
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(order.clientLastName,
                                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (order.isCompleted) {
                                            Icon(Icons.Default.CheckCircle, null,
                                                Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text(
                                        "${timeFmt.format(Date(order.createdAt))}  •  " +
                                                stringResource(R.string.order_positions_count, positionsCache[order.id]?.size ?: "…"),
                                        fontSize = 13.sp, color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val pos = positionsCache[order.id] ?: vm.getPositions(order.id)
                                        ShareHelper.share(context, ShareHelper.buildOrderText(order, pos))
                                    }
                                }) { Icon(Icons.Default.Share, null) }
                                IconButton(onClick = {
                                    editingOrder  = order
                                    editBoxL      = if (order.boxLength > 0) order.boxLength.toInt().toString() else ""
                                    editBoxW      = if (order.boxWidth  > 0) order.boxWidth.toInt().toString()  else ""
                                    editBoxH      = if (order.boxHeight > 0) order.boxHeight.toInt().toString() else ""
                                    editCompleted = order.isCompleted
                                }) { Icon(Icons.Default.Edit, null) }
                                IconButton(onClick = { deleteTarget = order }) {
                                    Icon(Icons.Default.Delete, null,
                                        tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = {
                                    expandedId = if (isExpanded) null else order.id
                                }) {
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null
                                    )
                                }
                            }

                            // Раскрытый список позиций с редактированием
                            AnimatedVisibility(visible = isExpanded) {
                                Column(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 12.dp)) {
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))

                                    if (positions == null) {
                                        Box(Modifier.fillMaxWidth().padding(8.dp),
                                            contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(Modifier.size(24.dp))
                                        }
                                    } else if (positions.isEmpty()) {
                                        Text(stringResource(R.string.no_positions_in_order),
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 14.sp)
                                    } else {
                                        positions.forEach { pos ->
                                            val isEditing = editingPos?.id == pos.id
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(pos.cyclicItemName,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium)
                                                    Text(ShareHelper.buildComposition(pos),
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.outline)
                                                }
                                                if (isEditing) {
                                                    // Поле редактирования веса
                                                    OutlinedTextField(
                                                        value = editWeight,
                                                        onValueChange = { editWeight = it },
                                                        modifier = Modifier.width(90.dp),
                                                        singleLine = true,
                                                        suffix = { Text(stringResource(R.string.weight_empty)) },
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Decimal
                                                        )
                                                    )
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            val w = editWeight.replace(",", ".").toFloatOrNull()
                                                                ?: pos.weightKg
                                                            val updated = pos.copy(weightKg = w)
                                                            vm.updatePosition(updated)
                                                            // Обновить кэш
                                                            positionsCache = positionsCache + (order.id to
                                                                    vm.getPositions(order.id))
                                                            editingPos = null
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Check, null,
                                                            tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                    IconButton(onClick = { editingPos = null }) {
                                                        Icon(Icons.Default.Close, null)
                                                    }
                                                } else {
                                                    Text(
                                                        stringResource(R.string.weight_kg, pos.weightKg),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            editingPos = pos
                                                            editWeight = "%.f".format(pos.weightKg)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, null,
                                                            Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { deletePositionTarget = pos },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, null,
                                                            Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        }

                                        // Итог
                                        Spacer(Modifier.height(6.dp))
                                        Row(Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(R.string.order_total), fontWeight = FontWeight.Bold)
                                            Text(
                                                "${"%.2f".format(positions.sumOf { it.weightKg.toDouble() })} кг",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (order.boxLength > 0) {
                                            Text(stringResource(R.string.box_label,
                                                order.boxLength.toInt(),
                                                order.boxWidth.toInt(),
                                                order.boxHeight.toInt()),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Диалог удаления заказа
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить заказ?") },
            text  = { Text("Заказ клиента «${target.clientLastName}» будет удалён.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { vm.deleteOrder(target); deleteTarget = null } },
                    colors  = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }

    // Диалог удаления позиции
    deletePositionTarget?.let { target ->
        val orderId = target.orderId
        AlertDialog(
            onDismissRequest = { deletePositionTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Удалить позицию?") },
            text  = { Text("«${target.cyclicItemName}» будет удалена из заказа.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            vm.deletePosition(target)
                            positionsCache = positionsCache + (orderId to vm.getPositions(orderId))
                            deletePositionTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletePositionTarget = null }) { Text("Отмена") }
            }
        )
    }
    // Bottom sheet — редактирование заказа
    editingOrder?.let { order ->
        ModalBottomSheet(onDismissRequest = { editingOrder = null }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Редактировать заказ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(order.clientLastName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline)

                HorizontalDivider()

                // Статус завершения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Заказ завершён", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = editCompleted, onCheckedChange = { editCompleted = it })
                }

                // Размер коробки
                Text("Размер коробки (см)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editBoxL, onValueChange = { editBoxL = it },
                        label = { Text("Д") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editBoxW, onValueChange = { editBoxW = it },
                        label = { Text("Ш") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editBoxH, onValueChange = { editBoxH = it },
                        label = { Text("В") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            vm.updateOrder(order.copy(
                                boxLength   = editBoxL.toFloatOrNull() ?: order.boxLength,
                                boxWidth    = editBoxW.toFloatOrNull() ?: order.boxWidth,
                                boxHeight   = editBoxH.toFloatOrNull() ?: order.boxHeight,
                                isCompleted = editCompleted
                            ))
                            editingOrder = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}