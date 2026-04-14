package com.joon.shoplist.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao
{
    @Query("SELECT * FROM shopping_items ORDER BY is_checked ASC, sort_order ASC, created_at ASC")
    fun getAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ShoppingItem?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM shopping_items")
    suspend fun getMaxSortOrder(): Long

    @Insert
    suspend fun insert(item: ShoppingItem)

    @Update
    suspend fun update(item: ShoppingItem)

    @Update
    suspend fun updateAll(items: List<ShoppingItem>)

    @Delete
    suspend fun delete(item: ShoppingItem)

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun reorder(items: List<ShoppingItem>)
    {
        val reordered = items.mapIndexed { index, item ->
            item.copy(sortOrder = (index + 1).toLong())
        }
        updateAll(reordered)
    }
}
