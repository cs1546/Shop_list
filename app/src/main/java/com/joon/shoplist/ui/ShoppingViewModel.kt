package com.joon.shoplist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.data.ShoppingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(name: String) {
        if(name.isBlank()) return
        viewModelScope.launch { repository.add(name.trim()) }
    }

    fun toggleCheck(item: ShoppingItem) {
        viewModelScope.launch { repository.toggleCheck(item) }
    }

    fun renameItem(item: ShoppingItem, newName: String) {
        if(newName.isBlank()) return
        viewModelScope.launch { repository.rename(item, newName.trim()) }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch { repository.delete(item) }
    }

    class Factory(private val repository: ShoppingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShoppingViewModel(repository) as T
    }
}
