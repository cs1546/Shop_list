package com.joon.shoplist.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.joon.shoplist.data.SavedShoppingItem
import com.joon.shoplist.data.SavedShoppingList
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.data.ShoppingRepository
import com.joon.shoplist.util.Store
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class Screen {
    data object ShoppingList : Screen()
    data object HistoryList : Screen()
    data class HistoryDetail(val listId: Long, val date: String) : Screen()
    data object Settings : Screen()
}

sealed class UndoEvent {
    data class Single(val item: ShoppingItem) : UndoEvent()
    data class ClearAll(val items: List<ShoppingItem>) : UndoEvent()
}

class ShoppingViewModel(
    private val repository: ShoppingRepository,
    private val appContext: Context
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val items: StateFlow<List<ShoppingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedLists: StateFlow<List<SavedShoppingList>> = repository.allSavedLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.ShoppingList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedListItems = MutableStateFlow<List<SavedShoppingItem>>(emptyList())
    val selectedListItems: StateFlow<List<SavedShoppingItem>> = _selectedListItems.asStateFlow()

    private val _undoEvent = MutableSharedFlow<UndoEvent>()
    val undoEvent = _undoEvent.asSharedFlow()

    private val _defaultStore = MutableStateFlow(loadDefaultStore())
    val defaultStore: StateFlow<Store> = _defaultStore.asStateFlow()

    private fun loadDefaultStore(): Store {
        val name = prefs.getString("default_store", Store.COUPANG.name) ?: Store.COUPANG.name
        return Store.entries.find { it.name == name } ?: Store.COUPANG
    }

    fun setDefaultStore(store: Store) {
        prefs.edit().putString("default_store", store.name).apply()
        _defaultStore.value = store
    }

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
        viewModelScope.launch {
            repository.delete(item)
            _undoEvent.emit(UndoEvent.Single(item))
        }
    }

    fun undoDelete(event: UndoEvent) {
        viewModelScope.launch {
            when(event) {
                is UndoEvent.Single -> repository.restore(event.item)
                is UndoEvent.ClearAll -> repository.restoreAll(event.items)
            }
        }
    }

    fun setImagePath(item: ShoppingItem, path: String) {
        viewModelScope.launch { repository.setImagePath(item, path) }
    }

    fun removeImage(item: ShoppingItem) {
        viewModelScope.launch {
            if(item.imagePath.isNotEmpty()) {
                File(item.imagePath).delete()
            }
            repository.setImagePath(item, "")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val snapshot = items.value
            repository.clearAll()
            if(snapshot.isNotEmpty())
                _undoEvent.emit(UndoEvent.ClearAll(snapshot))
        }
    }

    fun saveAndClear() {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val currentItems = items.value
            if(currentItems.isNotEmpty()) {
                val snapshot = currentItems.toList()
                repository.saveAndClear(today, currentItems)
                _undoEvent.emit(UndoEvent.ClearAll(snapshot))
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
            is Screen.Settings -> Screen.ShoppingList
            else -> Screen.ShoppingList
        }
    }

    fun importItems(names: List<String>) {
        viewModelScope.launch {
            names.forEach { name ->
                if(name.isNotBlank())
                    repository.add(name.trim())
            }
        }
    }

    fun deleteSavedList(listId: Long) {
        viewModelScope.launch { repository.deleteSavedList(listId) }
    }

    class Factory(
        private val repository: ShoppingRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShoppingViewModel(repository, appContext) as T
    }
}
