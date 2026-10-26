package com.orderpackager.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.orderpackager.data.db.AppDatabase
import com.orderpackager.data.db.entity.Client
import com.orderpackager.data.db.entity.PackingOrder
import com.orderpackager.data.network.CloudOrder
import com.orderpackager.data.network.CloudPullRequest
import com.orderpackager.data.network.CloudUpsertRequest
import com.orderpackager.data.network.SupabaseApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CloudSyncStatus {
    data object NotConfigured : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val completedAt: Long) : CloudSyncStatus
    data class Offline(val lastSuccessAt: Long?) : CloudSyncStatus
}

class CloudSyncManager private constructor(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _status = MutableStateFlow<CloudSyncStatus>(
        if (SupabaseApiProvider.isConfigured) CloudSyncStatus.Syncing
        else CloudSyncStatus.NotConfigured
    )
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()
    private var lastSuccessAt: Long? = null

    fun enqueue() {
        if (!SupabaseApiProvider.isConfigured) {
            _status.value = CloudSyncStatus.NotConfigured
            return
        }
        scope.launch {
            _status.value = CloudSyncStatus.Syncing
            runCatching { synchronize() }
                .onSuccess {
                    val completedAt = System.currentTimeMillis()
                    lastSuccessAt = completedAt
                    _status.value = CloudSyncStatus.Synced(completedAt)
                }
                .onFailure {
                    _status.value = CloudSyncStatus.Offline(lastSuccessAt)
                }
        }
    }

    suspend fun synchronize(): Int = mutex.withLock {
        if (!SupabaseApiProvider.isConfigured) return@withLock 0
        restoreMissingOrders()

        var synced = 0
        db.packingOrderDao().getPendingCloudSync().forEach { order ->
            val cloudOrder = CloudOrder.fromEntities(
                order,
                db.orderPositionDao().getForOrderOnce(order.id)
            )
            val response = SupabaseApiProvider.api.upsert(
                CloudUpsertRequest(BuildConfigToken.value, cloudOrder)
            )
            if (!response.isSuccessful) {
                error("Supabase sync failed: HTTP ${response.code()}")
            }
            db.packingOrderDao().markCloudSynced(order.id)
            synced++
        }
        synced
    }

    private suspend fun restoreMissingOrders() {
        val response = SupabaseApiProvider.api.pull(CloudPullRequest(BuildConfigToken.value))
        if (!response.isSuccessful) error("Supabase restore failed: HTTP ${response.code()}")

        response.body().orEmpty().forEach { cloud ->
            if (db.packingOrderDao().getByBoxNumber(cloud.boxNumber) != null) return@forEach
            db.withTransaction {
                val client = db.clientDao().getByLastName(cloud.clientLastName)
                val clientId = client?.id ?: db.clientDao().insert(Client(lastName = cloud.clientLastName))
                val orderId = db.packingOrderDao().insert(cloud.toEntity(clientId))
                cloud.positions.forEach { db.orderPositionDao().insert(it.toEntity(orderId)) }
            }
        }
    }

    private fun CloudOrder.toEntity(clientId: Long) = PackingOrder(
        clientId = clientId,
        clientLastName = clientLastName,
        boxNumber = boxNumber,
        batchNumber = batchNumber,
        createdAt = createdAtMs,
        boxLength = boxLength,
        boxWidth = boxWidth,
        boxHeight = boxHeight,
        isCompleted = isCompleted,
        cloudSynced = true
    )

    private object BuildConfigToken {
        val value: String get() = com.orderpackager.BuildConfig.SUPABASE_SYNC_TOKEN
    }

    companion object {
        @Volatile private var INSTANCE: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager =
            INSTANCE ?: synchronized(this) {
                CloudSyncManager(AppDatabase.getInstance(context.applicationContext))
                    .also { INSTANCE = it }
            }
    }
}
