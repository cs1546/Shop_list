package com.joon.shoplist.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class ShoppingRepository(
    private val dao: ShoppingDao,
    private val historyDao: HistoryDao
) {

    val allItems: Flow<List<ShoppingItem>> = dao.getAllItems()

    suspend fun currentItems(): List<ShoppingItem> = dao.getAllItemsOnce()

    suspend fun add(name: String) {
        dao.insert(ShoppingItem(name = name, sortOrder = dao.getMaxSortOrder() + 1))
    }

    suspend fun importItem(name: String, isChecked: Boolean, checkedAt: Long, imagePath: String): Boolean {
        val existing = dao.findByName(name)
        if(existing != null) {
            // 체크/해제는 더 나중에 바뀐 쪽을 따름
            // 양쪽 다 시각 기록이 없으면(구버전에서 보낸 데이터) 체크된 쪽을 따라 받는 쪽 체크가 풀리지 않게 함
            val remoteWins = existing.checkedAt < checkedAt ||
                    (checkedAt == 0L && existing.checkedAt == 0L && isChecked)
            val mergedChecked = if(remoteWins) isChecked else existing.isChecked
            val mergedCheckedAt = if(remoteWins) checkedAt else existing.checkedAt
            val hasNewImage = imagePath.isNotEmpty()
            if(!hasNewImage && mergedChecked == existing.isChecked && mergedCheckedAt == existing.checkedAt)
                return false
            if(hasNewImage && existing.imagePath.isNotEmpty() && existing.imagePath != imagePath)
                File(existing.imagePath).delete()
            dao.update(existing.copy(
                isChecked = mergedChecked,
                checkedAt = mergedCheckedAt,
                imagePath = if(hasNewImage) imagePath else existing.imagePath
            ))
            return true
        }
        dao.insert(ShoppingItem(
            name = name,
            isChecked = isChecked,
            checkedAt = checkedAt,
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
        dao.update(item.copy(isChecked = !item.isChecked, checkedAt = System.currentTimeMillis()))
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
