package com.orderpackager.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.orderpackager.data.db.AppDatabase
import com.orderpackager.data.auth.SupabaseAuthManager
import com.orderpackager.data.db.entity.Client
import com.orderpackager.data.db.entity.PackingOrder
import com.orderpackager.data.network.CloudClient
import com.orderpackager.data.network.CloudClientsReplaceRequest
import com.orderpackager.data.network.CloudOrder
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
    data object AuthRequired : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val completedAt: Long) : CloudSyncStatus
    data class Offline(val lastSuccessAt: Long?) : CloudSyncStatus
}

private class AuthenticationRequiredException : Exception()

class CloudSyncManager private constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val auth: SupabaseAuthManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val clientListMutex = Mutex()
    private val _status = MutableStateFlow<CloudSyncStatus>(
        if (SupabaseApiProvider.isConfigured) CloudSyncStatus.Syncing
        else CloudSyncStatus.NotConfigured
    )
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()
    private var lastSuccessAt: Long? = null
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun mutateClientList(mutation: suspend () -> Unit) {
        clientListMutex.withLock {
            mutation()
            prefs.edit().putBoolean(KEY_CLIENT_LIST_DIRTY, true).apply()
        }
        enqueue()
    }

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
                .onFailure { error ->
                    _status.value = if (error is AuthenticationRequiredException) {
                        CloudSyncStatus.AuthRequired
                    } else {
                        CloudSyncStatus.Offline(lastSuccessAt)
                    }
                }
        }
    }

    suspend fun synchronize(): Int = syncMutex.withLock {
        if (!SupabaseApiProvider.isConfigured) return@withLock 0
        val accessToken = auth.getValidAccessToken() ?: run {
            throw AuthenticationRequiredException()
        }
        val api = SupabaseApiProvider.api(accessToken)
        restoreMissingOrders(api)

        var synced = 0
        db.packingOrderDao().getPendingCloudSync().forEach { order ->
            val cloudOrder = CloudOrder.fromEntities(
                order,
                db.orderPositionDao().getForOrderOnce(order.id)
            )
            val response = api.upsert(CloudUpsertRequest(cloudOrder))
            if (!response.isSuccessful) {
                error("Supabase sync failed: HTTP ${response.code()}")
            }
            db.packingOrderDao().markCloudSynced(order.id)
            synced++
        }
        synchronizeClientList(api)
        synced
    }

    private suspend fun synchronizeClientList(api: com.orderpackager.data.network.SupabaseApi) =
        clientListMutex.withLock {
        val response = api.pullClients()
        if (!response.isSuccessful) {
            error("Supabase client list pull failed: HTTP ${response.code()}")
        }

        val cloudLastNames = response.body().orEmpty().map(CloudClient::lastName)
        val localClients = db.clientDao().getAllOnce()
        val localLastNames = localClients.map(Client::lastName)
        val initialized = prefs.getBoolean(KEY_CLIENT_LIST_INITIALIZED, false)
        val dirty = prefs.getBoolean(KEY_CLIENT_LIST_DIRTY, false)

        val lastNames = when {
            !initialized -> ClientListMerger.firstSync(cloudLastNames, localLastNames)
            dirty -> ClientListMerger.normalize(localLastNames)
            else -> ClientListMerger.normalize(cloudLastNames)
        }

        if (!initialized || dirty) {
            val replaceResponse = api.replaceClients(
                CloudClientsReplaceRequest(lastNames.map(::CloudClient))
            )
            if (!replaceResponse.isSuccessful) {
                error("Supabase client list update failed: HTTP ${replaceResponse.code()}")
            }
        }

        replaceLocalClientList(lastNames, localClients)
        prefs.edit()
            .putBoolean(KEY_CLIENT_LIST_INITIALIZED, true)
            .putBoolean(KEY_CLIENT_LIST_DIRTY, false)
            .apply()
        }

    private suspend fun replaceLocalClientList(lastNames: List<String>, existing: List<Client>) {
        val existingByLastName = existing.associateBy { it.lastName.trim().lowercase() }
        val replacement = lastNames.map { lastName ->
            existingByLastName[lastName.lowercase()]?.copy(lastName = lastName)
                ?: Client(lastName = lastName)
        }
        db.withTransaction {
            db.clientDao().deleteAll()
            db.clientDao().insertAll(replacement)
        }
    }

    private suspend fun restoreMissingOrders(api: com.orderpackager.data.network.SupabaseApi) {
        val response = api.pull()
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

    companion object {
        @Volatile private var INSTANCE: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager =
            INSTANCE ?: synchronized(this) {
                CloudSyncManager(
                    context.applicationContext,
                    AppDatabase.getInstance(context.applicationContext),
                    SupabaseAuthManager.getInstance(context.applicationContext)
                )
                    .also { INSTANCE = it }
            }

        private const val PREFS_NAME = "cloud_sync"
        private const val KEY_CLIENT_LIST_INITIALIZED = "client_list_initialized"
        private const val KEY_CLIENT_LIST_DIRTY = "client_list_dirty"
    }
}
