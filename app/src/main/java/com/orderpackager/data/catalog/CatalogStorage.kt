package com.orderpackager.data.catalog

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.orderpackager.data.auth.SupabaseAuthManager
import com.orderpackager.data.db.AppDatabase
import com.orderpackager.data.network.SupabaseApi
import com.orderpackager.data.sync.CloudSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@Entity(tableName = "catalog_cache")
data class CatalogCache(@PrimaryKey val id: Int = 1, val payload: String)

@Entity(tableName = "catalog_custom_products", primaryKeys = ["ownerId", "id"])
data class CustomProductRow(
    val ownerId: String,
    val id: String,
    val payload: String,
    val pending: Boolean
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_cache WHERE id = 1")
    fun observeCatalog(): Flow<CatalogCache?>
    @Query("SELECT * FROM catalog_cache WHERE id = 1")
    suspend fun getCatalog(): CatalogCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalog(row: CatalogCache)
    @Query("SELECT * FROM catalog_custom_products WHERE ownerId = :ownerId ORDER BY id")
    fun observeProducts(ownerId: String): Flow<List<CustomProductRow>>
    @Query("SELECT * FROM catalog_custom_products WHERE ownerId = :ownerId ORDER BY id")
    suspend fun getProducts(ownerId: String): List<CustomProductRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProduct(row: CustomProductRow)
    @Query("UPDATE catalog_custom_products SET pending = 0 WHERE ownerId = :ownerId AND id = :id AND payload = :payload")
    suspend fun acknowledge(ownerId: String, id: String, payload: String)
}

data class CloudCatalog(val catalog: ProductCatalog, val products: List<CatalogProduct>)
data class CatalogUpsertRequest(val p_products: List<CatalogProduct>)

object CustomProducts {
    val units = listOf("pc" to "pcs", "pair" to "pairs", "package" to "packages", "bottle" to "bottles")

    fun create(categoryId: String, language: String, name: String, reportName: String, unitIndex: Int): CatalogProduct {
        val local = name.trim()
        val report = reportName.trim()
        require(local.length in 1..120 && report.length in 1..120)
        require(language in listOf("ru", "en", "es"))
        val unit = units[unitIndex]
        return CatalogProduct(
            id = UUID.randomUUID().toString(), categoryId = categoryId,
            names = if (language == "en") mapOf("en" to report) else mapOf("en" to report, language to local),
            reportNameEn = report, unitSingular = unit.first, unitPlural = unit.second,
            sortOrder = 10000, custom = true, source = "user_defined"
        )
    }

    fun isDuplicate(candidate: CatalogProduct, existing: List<CatalogProduct>): Boolean = existing.any {
        it.categoryId == candidate.categoryId && it.unitSingular == candidate.unitSingular &&
            (it.reportNameEn.equals(candidate.reportNameEn, ignoreCase = true) ||
                candidate.names.any { (lang, name) -> it.names[lang]?.equals(name, ignoreCase = true) == true })
    }
}

class DuplicateProductException : IllegalArgumentException()

class CatalogRepository private constructor(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.catalogDao()
    private val gson = Gson()
    private val auth = SupabaseAuthManager.getInstance(context)
    private val mutex = Mutex()
    private val bundled by lazy { ProductCatalog.load(context) }

    private fun catalog(cache: CatalogCache?): ProductCatalog =
        cache?.let { gson.fromJson(it.payload, ProductCatalog::class.java) } ?: bundled

    fun observe(ownerId: String): Flow<ProductCatalog> = combine(dao.observeCatalog(), dao.observeProducts(ownerId)) { cache, rows ->
        val base = catalog(cache)
        base.copy(products = base.products + rows.map { gson.fromJson(it.payload, CatalogProduct::class.java) })
    }

    suspend fun add(ownerId: String, product: CatalogProduct) = mutex.withLock {
        check(ownerId.isNotBlank() && auth.currentUserId == ownerId) { "Sign in required" }
        val base = catalog(dao.getCatalog())
        require(base.categories.any { it.id == product.categoryId })
        val existing = base.products + dao.getProducts(ownerId).map { gson.fromJson(it.payload, CatalogProduct::class.java) }
        if (CustomProducts.isDuplicate(product, existing)) throw DuplicateProductException()
        dao.saveProduct(CustomProductRow(ownerId, product.id, gson.toJson(product), pending = true))
        CloudSyncManager.getInstance(context).enqueue()
    }

    suspend fun synchronize(api: SupabaseApi, ownerId: String) = mutex.withLock {
        check(auth.currentUserId == ownerId)
        val pending = dao.getProducts(ownerId).filter { it.pending }
        // Per-row acknowledgement and idempotent server upsert retain offline additions on failures.
        pending.chunked(100).forEach { batch ->
            val response = api.upsertCatalogProducts(CatalogUpsertRequest(batch.map { gson.fromJson(it.payload, CatalogProduct::class.java) }))
            check(response.isSuccessful) { "Catalog upload failed: HTTP ${response.code()}" }
            check(auth.currentUserId == ownerId)
            db.withTransaction { batch.forEach { dao.acknowledge(ownerId, it.id, it.payload) } }
        }
        val response = api.pullCatalog()
        check(response.isSuccessful) { "Catalog download failed: HTTP ${response.code()}" }
        val cloud = checkNotNull(response.body())
        require(cloud.catalog.schemaVersion == 1 && cloud.catalog.categories.isNotEmpty())
        val categories = cloud.catalog.categories.map { it.id }
        require(categories.size == categories.distinct().size)
        require(cloud.catalog.categories.all { it.id.isNotBlank() && it.names["en"].orEmpty().isNotBlank() })
        val products = cloud.catalog.products + cloud.products
        require(products.map { it.id }.distinct().size == products.size)
        require(products.all { product ->
            product.id.isNotBlank() && product.categoryId in categories &&
                product.names["en"].orEmpty().isNotBlank() && product.reportNameEn.isNotBlank() &&
                (product.unitSingular to product.unitPlural) in CustomProducts.units
        })
        check(auth.currentUserId == ownerId)
        db.withTransaction {
            dao.saveCatalog(CatalogCache(payload = gson.toJson(cloud.catalog)))
            cloud.products.forEach { dao.saveProduct(CustomProductRow(ownerId, it.id, gson.toJson(it), pending = false)) }
        }
    }

    companion object {
        @Volatile private var instance: CatalogRepository? = null
        fun getInstance(context: Context): CatalogRepository = instance ?: synchronized(this) {
            instance ?: CatalogRepository(context.applicationContext).also { instance = it }
        }
    }
}
