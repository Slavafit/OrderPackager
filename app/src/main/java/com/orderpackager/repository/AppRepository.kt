package com.orderpackager.repository

import android.content.Context
import com.orderpackager.data.db.AppDatabase
import com.orderpackager.data.db.entity.*
import com.orderpackager.data.network.ScaleApiProvider
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class AppRepository private constructor(private val db: AppDatabase) {

    // ─── Клиенты ──────────────────────────────────────────────────────────────
    fun getAllClients(): Flow<List<Client>> = db.clientDao().getAll()
    fun searchClients(q: String): Flow<List<Client>> = db.clientDao().search(q)
    suspend fun insertClient(lastName: String) = db.clientDao().insert(Client(lastName = lastName.trim()))
    suspend fun updateClient(client: Client) = db.clientDao().update(client)
    suspend fun deleteClient(client: Client) = db.clientDao().delete(client)
    suspend fun getClientById(id: Long) = db.clientDao().getById(id)

    // ─── Циклический список ───────────────────────────────────────────────────
    fun getAllCyclicItems(): Flow<List<CyclicItem>> = db.cyclicItemDao().getAll()
    suspend fun getAllCyclicItemsOnce(): List<CyclicItem> = db.cyclicItemDao().getAllOnce()

    suspend fun insertCyclicItem(name: String) {
        val items = db.cyclicItemDao().getAllOnce()
        db.cyclicItemDao().insert(CyclicItem(name = name.trim(), position = items.size))
    }

    suspend fun deleteCyclicItem(item: CyclicItem) {
        db.cyclicItemDao().delete(item)
        // Перенумеровать оставшиеся
        val remaining = db.cyclicItemDao().getAllOnce().sortedBy { it.position }
        db.cyclicItemDao().insertAll(remaining.mapIndexed { i, it -> it.copy(position = i) })
    }

    suspend fun reorderCyclicItems(items: List<CyclicItem>) {
        db.cyclicItemDao().deleteAll()
        db.cyclicItemDao().insertAll(items.mapIndexed { i, item -> item.copy(position = i) })
    }

    suspend fun clearCyclicItems() = db.cyclicItemDao().deleteAll()

    suspend fun importCyclicItems(names: List<String>) {
        db.cyclicItemDao().deleteAll()
        db.cyclicItemDao().insertAll(names.mapIndexed { i, name ->
            CyclicItem(name = name.trim(), position = i)
        })
    }

    fun getSavedCyclicIndex(context: Context, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val saved = getPrefs(context).getInt(KEY_NEXT_CYCLIC_INDEX, 0)
        return ((saved % itemCount) + itemCount) % itemCount
    }

    fun saveNextCyclicIndex(context: Context, currentIndex: Int, itemCount: Int) {
        if (itemCount <= 0) return
        getPrefs(context)
            .edit()
            .putInt(KEY_NEXT_CYCLIC_INDEX, (currentIndex + 1) % itemCount)
            .apply()
    }

    // ─── Заказы ───────────────────────────────────────────────────────────────
    fun getAllOrders(): Flow<List<PackingOrder>> = db.packingOrderDao().getAll()

    fun getTodayOrders(): Flow<List<PackingOrder>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return db.packingOrderDao().getToday(cal.timeInMillis)
    }

    suspend fun getOrderById(id: Long): PackingOrder? = db.packingOrderDao().getById(id)

    suspend fun createOrder(clientId: Long, clientLastName: String): Long =
        db.packingOrderDao().insert(PackingOrder(clientId = clientId, clientLastName = clientLastName))

    suspend fun updateOrder(order: PackingOrder) = db.packingOrderDao().update(order)
    suspend fun deleteOrder(order: PackingOrder) = db.packingOrderDao().delete(order)

    // ─── Позиции заказа ───────────────────────────────────────────────────────
    fun getPositionsForOrder(orderId: Long): Flow<List<OrderPosition>> =
        db.orderPositionDao().getForOrder(orderId)

    suspend fun getPositionsForOrderOnce(orderId: Long): List<OrderPosition> =
        db.orderPositionDao().getForOrderOnce(orderId)

    suspend fun getPositionByCyclic(orderId: Long, cyclicItemId: Long): OrderPosition? =
        db.orderPositionDao().getByOrderAndCyclic(orderId, cyclicItemId)

    suspend fun upsertPosition(position: OrderPosition): Long {
        val existing = db.orderPositionDao().getByOrderAndCyclic(position.orderId, position.cyclicItemId)
        return if (existing != null) {
            db.orderPositionDao().update(position.copy(id = existing.id))
            existing.id
        } else {
            db.orderPositionDao().insert(position)
        }
    }

    suspend fun deletePosition(position: OrderPosition) = db.orderPositionDao().delete(position)

    // ─── Весы ─────────────────────────────────────────────────────────────────
    suspend fun getWeightFromScale(context: Context): Result<Float> = runCatching {
        val ip = context.getSharedPreferences("packager_prefs", Context.MODE_PRIVATE)
            .getString("scale_ip", "192.168.1.50") ?: "192.168.1.50"
        ScaleApiProvider.getApi(ip).getWeight().weight
    }

    companion object {
        private const val PREFS_NAME = "packager_prefs"
        private const val KEY_NEXT_CYCLIC_INDEX = "next_cyclic_index"

        @Volatile private var INSTANCE: AppRepository? = null

        private fun getPrefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun getInstance(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                AppRepository(AppDatabase.getInstance(context)).also { INSTANCE = it }
            }
    }
}
