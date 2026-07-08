package com.orderpackager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orderpackager.data.db.dao.*
import com.orderpackager.data.db.entity.*

@Database(
    entities = [Client::class, CyclicItem::class, PackingOrder::class, OrderPosition::class],
    version = 2,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "packager_db"
                )
                .addCallback(seedCallback)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
