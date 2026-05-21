package com.orderpackager.data.db.dao

import androidx.room.*
import com.orderpackager.data.db.entity.*
import kotlinx.coroutines.flow.Flow

// ─── ClientDao ────────────────────────────────────────────────────────────────
@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY lastName ASC")
    fun getAll(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE lastName LIKE '%' || :q || '%' ORDER BY lastName ASC")
    fun search(q: String): Flow<List<Client>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Delete
    suspend fun delete(client: Client)

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getById(id: Long): Client?
}

// ─── CyclicItemDao ────────────────────────────────────────────────────────────
@Dao
interface CyclicItemDao {
    @Query("SELECT * FROM cyclic_items ORDER BY position ASC")
    fun getAll(): Flow<List<CyclicItem>>

    @Query("SELECT * FROM cyclic_items ORDER BY position ASC")
    suspend fun getAllOnce(): List<CyclicItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CyclicItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CyclicItem>)

    @Update
    suspend fun update(item: CyclicItem)

    @Delete
    suspend fun delete(item: CyclicItem)

    @Query("DELETE FROM cyclic_items")
    suspend fun deleteAll()
}

// ─── PackingOrderDao ──────────────────────────────────────────────────────────
@Dao
interface PackingOrderDao {
    @Query("SELECT * FROM packing_orders ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PackingOrder>>

    @Query("SELECT * FROM packing_orders WHERE createdAt >= :startOfDay ORDER BY createdAt DESC")
    fun getToday(startOfDay: Long): Flow<List<PackingOrder>>

    @Query("SELECT * FROM packing_orders WHERE id = :id")
    suspend fun getById(id: Long): PackingOrder?

    @Insert
    suspend fun insert(order: PackingOrder): Long

    @Update
    suspend fun update(order: PackingOrder)

    @Delete
    suspend fun delete(order: PackingOrder)
}

// ─── OrderPositionDao ─────────────────────────────────────────────────────────
@Dao
interface OrderPositionDao {
    @Query("SELECT * FROM order_positions WHERE orderId = :orderId ORDER BY id ASC")
    fun getForOrder(orderId: Long): Flow<List<OrderPosition>>

    @Query("SELECT * FROM order_positions WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getForOrderOnce(orderId: Long): List<OrderPosition>

    @Query("SELECT * FROM order_positions WHERE orderId = :orderId AND cyclicItemId = :cyclicItemId LIMIT 1")
    suspend fun getByOrderAndCyclic(orderId: Long, cyclicItemId: Long): OrderPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: OrderPosition): Long

    @Update
    suspend fun update(position: OrderPosition)

    @Delete
    suspend fun delete(position: OrderPosition)

    @Query("DELETE FROM order_positions WHERE orderId = :orderId")
    suspend fun deleteAllForOrder(orderId: Long)
}
