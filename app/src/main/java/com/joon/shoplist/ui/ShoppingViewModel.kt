package com.joon.shoplist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.joon.shoplist.data.SavedShoppingItem
import com.joon.shoplist.data.SavedShoppingList
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.data.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class Screen {
    data object ShoppingList : Screen()
    data object HistoryList : Screen()
    data class HistoryDetail(val listId: Long, val date: String) : Screen()
}

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    val items: StateFlow<List<ShoppingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedLists: StateFlow<List<SavedShoppingList>> = repository.allSavedLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.ShoppingList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedListItems = MutableStateFlow<List<SavedShoppingItem>>(emptyList())
    val selectedListItems: StateFlow<List<SavedShoppingItem>> = _selectedListItems.asStateFlow()

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

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun saveAndClear() {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val currentItems = items.value
            if(currentItems.isNotEmpty()) {
                repository.saveAndClear(today, currentItems)
            }
            _currentScreen.value = Screen.HistoryList
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if(screen is Screen.HistoryDetail) {
            viewModelScope.launch {
                repository.getSavedItems(screen.listId).collect {
                    _selectedListItems.value = it
                }
            }
        }
    }

    fun goBack() {
        _currentScreen.value = when(_currentScreen.value) {
            is Screen.HistoryDetail -> Screen.HistoryList
            is Screen.HistoryList -> Screen.ShoppingList
            else -> Screen.ShoppingList
        }
    }

    fun deleteSavedList(listId: Long) {
        viewModelScope.launch { repository.deleteSavedList(listId) }
    }

    class Factory(private val repository: ShoppingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShoppingViewModel(repository) as T
    }
}
