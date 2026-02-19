package com.joon.shoplist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val dao: ShoppingDao,
    private val historyDao: HistoryDao
) {

    val allItems: Flow<List<ShoppingItem>> = dao.getAllItems()

    suspend fun add(name: String) {
        dao.insert(ShoppingItem(name = name))
    }

    suspend fun toggleCheck(item: ShoppingItem) {
        dao.update(item.copy(isChecked = !item.isChecked))
    }

    suspend fun rename(item: ShoppingItem, newName: String) {
        dao.update(item.copy(name = newName))
    }

    suspend fun delete(item: ShoppingItem) {
        dao.delete(item)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun saveAndClear(date: String, items: List<ShoppingItem>): Long {
        val names = items.map { it.name }
        val listId = historyDao.saveList(date, names)
        dao.deleteAll()
        return listId
    }

    val allSavedLists: Flow<List<SavedShoppingList>> = historyDao.getAllLists()

    fun getSavedItems(listId: Long): Flow<List<SavedShoppingItem>> =
        historyDao.getItemsByListId(listId)

    suspend fun deleteSavedList(listId: Long) {
        historyDao.deleteList(listId)
    }
}
