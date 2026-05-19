package com.robote.joe.mobile

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueDate: LocalDate,
    val notes: String = "",
    val isDone: Boolean = false
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amount: Double,
    val currency: String,
    val dueDate: LocalDate,
    val notes: String = "",
    val isPaid: Boolean = false
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorName: String,
    val amount: Double,
    val currency: String,
    val billDate: LocalDate,
    val category: String,
    val isPaid: Boolean = false
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val addedBy: String = "",
    val isDone: Boolean = false
)

class JoeConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

@Dao
interface JoeDao {
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC, id DESC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM debts ORDER BY dueDate ASC, id DESC")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM bills ORDER BY billDate DESC, id DESC")
    fun observeBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM shopping_items WHERE isDone = 0 ORDER BY id DESC")
    fun observeShoppingItems(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(item: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(item: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(item: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItemEntity)

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun reminderCount(): Int

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun debtCount(): Int

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun billCount(): Int

    @Query("SELECT COUNT(*) FROM shopping_items")
    suspend fun shoppingCount(): Int
}

@Database(
    entities = [ReminderEntity::class, DebtEntity::class, BillEntity::class, ShoppingItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(JoeConverters::class)
abstract class JoeDatabase : RoomDatabase() {
    abstract fun dao(): JoeDao

    companion object {
        @Volatile
        private var INSTANCE: JoeDatabase? = null

        fun get(context: Context): JoeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JoeDatabase::class.java,
                    "joe_mobile.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
