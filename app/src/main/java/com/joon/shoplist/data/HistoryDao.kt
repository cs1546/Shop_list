package com.joon.shoplist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert
    suspend fun insertList(list: SavedShoppingList): Long

    @Insert
    suspend fun insertItems(items: List<SavedShoppingItem>)

    @Transaction
    suspend fun saveList(date: String, itemNames: List<String>): Long {
        val listId = insertList(SavedShoppingList(date = date))
        val items = itemNames.map { SavedShoppingItem(listId = listId, name = it) }
        insertItems(items)
        return listId
    }

    @Query("SELECT * FROM saved_shopping_lists ORDER BY created_at DESC")
    fun getAllLists(): Flow<List<SavedShoppingList>>

    @Query("SELECT * FROM saved_shopping_items WHERE list_id = :listId ORDER BY id ASC")
    fun getItemsByListId(listId: Long): Flow<List<SavedShoppingItem>>

    @Query("DELETE FROM saved_shopping_lists WHERE id = :listId")
    suspend fun deleteList(listId: Long)
}
