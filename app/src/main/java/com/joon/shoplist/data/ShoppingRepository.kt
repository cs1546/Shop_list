package com.joon.shoplist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val dao: ShoppingDao) {

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
}
