package com.orderpackager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orderpackager.data.db.dao.*
import com.orderpackager.data.db.entity.*

@Database(
    entities = [Client::class, CyclicItem::class, PackingOrder::class, OrderPosition::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun cyclicItemDao(): CyclicItemDao
    abstract fun packingOrderDao(): PackingOrderDao
    abstract fun orderPositionDao(): OrderPositionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // ─── Вставь сюда свои имена ───────────────────────────────────────────
        private val INITIAL_CYCLIC_ITEMS = listOf(
            "Иван Иванов",
            "Мария Петрова",
            "Алексей Сидоров",
            "Елена Козлова",
            "Дмитрий Новиков",
            "Ольга Морозова",
            "Сергей Волков",
            "Наталья Лебедева",
            "Андрей Соколов",
            "Татьяна Попова"
        )

        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Заполняется только при первом создании БД
                INITIAL_CYCLIC_ITEMS.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO cyclic_items (name, position) VALUES (?, ?)",
                        arrayOf(name, index)
                    )
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE packing_orders ADD COLUMN boxNumber TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    UPDATE packing_orders
                    SET boxNumber = 'NBX' ||
                        strftime('%m%d%H%M', createdAt / 1000, 'unixepoch', 'localtime') ||
                        printf('%04d', abs(random()) % 10000)
                    WHERE boxNumber = ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE packing_orders ADD COLUMN batchNumber TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "packager_db"
                )
                .addCallback(seedCallback)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
