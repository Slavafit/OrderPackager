package com.orderpackager.ui.screen

import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orderpackager.R
import com.orderpackager.data.db.entity.OrderPosition
import com.orderpackager.data.db.entity.PackingOrder
import com.orderpackager.repository.AppRepository
import com.orderpackager.utils.ShareHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
data class CompletionState(
    val order: PackingOrder? = null,
    val positions: List<OrderPosition> = emptyList(),
    val boxL: String = "",
    val boxW: String = "",
    val boxH: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
) {
    val totalWeight: Float get() = positions.sumOf { it.weightKg.toDouble() }.toFloat()
}

class OrderCompletionViewModel(
    private val repo: AppRepository,
    private val orderId: Long,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(CompletionState())
    val state: StateFlow<CompletionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val order = repo.getOrderById(orderId)
            val defL  = prefs.getString("box_l", "70") ?: "70"
            val defW  = prefs.getString("box_w", "50") ?: "50"
            val defH  = prefs.getString("box_h", "45") ?: "45"
            _state.update { it.copy(
                order     = order,
                boxL      = if (order != null && order.boxLength > 0) order.boxLength.toInt().toString() else defL,
                boxW      = if (order != null && order.boxWidth  > 0) order.boxWidth.toInt().toString()  else defW,
                boxH      = if (order != null && order.boxHeight > 0) order.boxHeight.toInt().toString() else defH,
                isLoading = false
            )}
            repo.getPositionsForOrder(orderId).collect { positions ->
                _state.update { it.copy(positions = positions) }
            }
        }
    }

    fun setBoxL(v: String) = _state.update { it.copy(boxL = v) }
    fun setBoxW(v: String) = _state.update { it.copy(boxW = v) }
    fun setBoxH(v: String) = _state.update { it.copy(boxH = v) }

    fun applyDefault() = _state.update { it.copy(
        boxL = prefs.getString("box_l", "70") ?: "70",
        boxW = prefs.getString("box_w", "50") ?: "50",
        boxH = prefs.getString("box_h", "45") ?: "45"
    )}

    fun saveDefault() {
        val s = _state.value
        prefs.edit().putString("box_l", s.boxL).putString("box_w", s.boxW)
            .putString("box_h", s.boxH).apply()
    }

    suspend fun deletePosition(pos: OrderPosition) = repo.deletePosition(pos)

    suspend fun saveOrder() {
        val s = _state.value
        val order = s.order ?: return
        repo.updateOrder(order.copy(
            boxLength   = s.boxL.toFloatOrNull() ?: 0f,
            boxWidth    = s.boxW.toFloatOrNull()  ?: 0f,
            boxHeight   = s.boxH.toFloatOrNull()  ?: 0f,
            isCompleted = true
        ))
        _state.update { it.copy(isSaved = true) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderCompletionScreen(repo: AppRepository, orderId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("packager_prefs", Context.MODE_PRIVATE) }
    val vm: OrderCompletionViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(c: Class<T>) =
            OrderCompletionViewModel(repo, orderId, prefs) as T
    })

    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<OrderPosition?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.completion_title), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text(state.order?.clientLastName ?: "", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary)
            )
        },
        bottomBar = {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val order = state.order ?: return@Button
                        ShareHelper.share(context, ShareHelper.buildOrderText(order, state.positions))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_order), fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            vm.saveOrder()
                            snackbarHostState.showSnackbar(context.getString(R.string.order_saved))
                            delay(600)
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_and_finish), fontSize = 16.sp)
                }
            }
        }
    ) { padding ->

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            // ─── Общий вес ────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.total_weight),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                //"${"%.2f".format(state.totalWeight)} кг",
                                stringResource(R.string.weight_kg, state.totalWeight ),
                                fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.positions_count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${state.positions.size}",
                                fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ─── Размер коробки ───────────────────────────────────────────────
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.box_size), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DimField(stringResource(R.string.box_l), state.boxL, vm::setBoxL, Modifier.weight(1f))
                            DimField(stringResource(R.string.box_w), state.boxW, vm::setBoxW, Modifier.weight(1f))
                            DimField(stringResource(R.string.box_h), state.boxH, vm::setBoxH, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.applyDefault() }, Modifier.weight(1f)) {
                                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.apply_default), fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    vm.saveDefault()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.default_saved)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.save_as_default), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ─── Заголовок списка позиций ─────────────────────────────────────
            item {
                Text(
                    stringResource(R.string.processed_positions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            // ─── Список позиций ───────────────────────────────────────────────
            if (state.positions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_positions),
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(state.positions, key = { it.id }) { pos ->
                    ListItem(
                        headlineContent = {
                            Text(pos.cyclicItemName, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(ShareHelper.buildComposition(pos), fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${"%.2f".format(pos.weightKg)} кг",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp
                                )
                                IconButton(
                                    onClick = { deleteTarget = pos },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete, null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    // Диалог удаления позиции
    deleteTarget?.let { pos ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_position_from_order_title)) },
            text  = { Text(stringResource(R.string.delete_position_from_order_body, pos.cyclicItemName)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            vm.deletePosition(pos)
                            deleteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DimField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}