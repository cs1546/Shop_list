package com.joon.shoplist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val dao: ShoppingDao,
    private val historyDao: HistoryDao
) {

    val allItems: Flow<List<ShoppingItem>> = dao.getAllItems()

    suspend fun add(name: String) {
        dao.insert(ShoppingItem(name = name, sortOrder = dao.getMaxSortOrder() + 1))
    }

    suspend fun importItem(name: String, isChecked: Boolean, imagePath: String): Boolean {
        val existing = dao.findByName(name)
        if(existing != null) {
            if(imagePath.isEmpty())
                return false
            dao.update(existing.copy(isChecked = isChecked, imagePath = imagePath))
            return true
        }
        dao.insert(ShoppingItem(
            name = name,
            isChecked = isChecked,
            imagePath = imagePath,
            sortOrder = dao.getMaxSortOrder() + 1
        ))
        return true
    }

    suspend fun addIfNotExists(name: String): Boolean {
        if(dao.findByName(name) != null)
            return false
        dao.insert(ShoppingItem(name = name, sortOrder = dao.getMaxSortOrder() + 1))
        return true
    }

    suspend fun reorderItems(items: List<ShoppingItem>) {
        dao.reorder(items)
    }

    suspend fun toggleCheck(item: ShoppingItem) {
        dao.update(item.copy(isChecked = !item.isChecked))
    }

    suspend fun rename(item: ShoppingItem, newName: String) {
        dao.update(item.copy(name = newName))
    }

    suspend fun setImagePath(item: ShoppingItem, path: String) {
        dao.update(item.copy(imagePath = path))
    }

    suspend fun delete(item: ShoppingItem) {
        dao.delete(item)
    }

    suspend fun restore(item: ShoppingItem) {
        dao.insert(item.copy(id = 0))
    }

    suspend fun restoreAll(items: List<ShoppingItem>) {
        items.forEach { dao.insert(it.copy(id = 0)) }
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
