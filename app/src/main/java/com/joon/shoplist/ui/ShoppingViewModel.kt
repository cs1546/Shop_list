package com.joon.shoplist.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.joon.shoplist.data.SavedShoppingItem
import com.joon.shoplist.data.SavedShoppingList
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.data.ShoppingRepository
import com.joon.shoplist.util.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    private val _snackMessage = MutableSharedFlow<String>()
    val snackMessage = _snackMessage.asSharedFlow()

    private val _defaultStore = MutableStateFlow(loadDefaultStore())
    val defaultStore: StateFlow<Store> = _defaultStore.asStateFlow()

    private val _sharedFolderUri = MutableStateFlow(prefs.getString("shared_folder_uri", null))
    val sharedFolderUri: StateFlow<String?> = _sharedFolderUri.asStateFlow()

    private fun loadDefaultStore(): Store {
        val name = prefs.getString("default_store", Store.COUPANG.name) ?: Store.COUPANG.name
        return Store.entries.find { it.name == name } ?: Store.COUPANG
    }

    fun setDefaultStore(store: Store) {
        prefs.edit().putString("default_store", store.name).apply()
        _defaultStore.value = store
    }

    fun setSharedFolderUri(uri: String?) {
        prefs.edit().putString("shared_folder_uri", uri).apply()
        _sharedFolderUri.value = uri
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
            if(item.imagePath.isNotEmpty())
                File(item.imagePath).delete()
            repository.setImagePath(item, "")
        }
    }

    fun exportToSharedFolder() {
        viewModelScope.launch {
            val folderUriStr = _sharedFolderUri.value
            if(folderUriStr == null) {
                _snackMessage.emit("공유 폴더를 먼저 설정하세요")
                return@launch
            }
            withContext(Dispatchers.IO) {
                try {
                    val folder = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUriStr))
                        ?: throw Exception("폴더 접근 실패")

                    val zipBytes = createZipBytes(items.value)

                    folder.listFiles()
                        .filter { it.name?.endsWith(".zip") == true }
                        .forEach { it.delete() }

                    val newFile = folder.createFile("application/zip", "쇼핑목록")
                        ?: throw Exception("파일 생성 실패")

                    appContext.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        out.write(zipBytes)
                    }
                    _snackMessage.emit("공유 완료")
                } catch(e: Exception) {
                    _snackMessage.emit("공유 실패: ${e.message}")
                }
            }
        }
    }

    fun importFromSharedFolder() {
        viewModelScope.launch {
            val folderUriStr = _sharedFolderUri.value
            if(folderUriStr == null) {
                _snackMessage.emit("공유 폴더를 먼저 설정하세요")
                return@launch
            }
            withContext(Dispatchers.IO) {
                try {
                    val folder = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUriStr))
                        ?: throw Exception("폴더 접근 실패")

                    val zipDoc = folder.listFiles()
                        .filter { it.name?.endsWith(".zip") == true }
                        .maxByOrNull { it.lastModified() }

                    if(zipDoc == null) {
                        _snackMessage.emit("공유된 리스트가 없습니다")
                        return@withContext
                    }

                    val count = appContext.contentResolver.openInputStream(zipDoc.uri)?.use { input ->
                        importFromZipStream(input)
                    } ?: 0

                    _snackMessage.emit("${count}개 항목 추가됨")
                } catch(e: Exception) {
                    _snackMessage.emit("가져오기 실패: ${e.message}")
                }
            }
        }
    }

    private fun createZipBytes(items: List<ShoppingItem>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            val jsonItems = JSONArray()
            items.forEachIndexed { index, item ->
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("checked", item.isChecked)
                val imgName = if(item.imagePath.isNotEmpty() && File(item.imagePath).exists())
                    "img_$index.jpg" else ""
                obj.put("image", imgName)
                jsonItems.put(obj)
            }
            zip.putNextEntry(ZipEntry("list.json"))
            zip.write(JSONObject().put("items", jsonItems).toString().toByteArray())
            zip.closeEntry()

            items.forEachIndexed { index, item ->
                if(item.imagePath.isNotEmpty() && File(item.imagePath).exists()) {
                    zip.putNextEntry(ZipEntry("img_$index.jpg"))
                    File(item.imagePath).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    fun importFromZipStream(input: java.io.InputStream): Int {
        val imagesDir = File(appContext.filesDir, "images")
        imagesDir.mkdirs()

        val imageMap = mutableMapOf<String, String>()
        var jsonContent = ""

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while(entry != null) {
                if(entry.name == "list.json") {
                    jsonContent = zip.bufferedReader().readText()
                } else if(entry.name.startsWith("img_")) {
                    val destFile = File(imagesDir, "${System.currentTimeMillis()}_${entry.name}")
                    destFile.outputStream().use { out -> zip.copyTo(out) }
                    imageMap[entry.name] = destFile.absolutePath
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if(jsonContent.isEmpty()) return 0

        val items = JSONObject(jsonContent).getJSONArray("items")
        var count = 0
        val importList = mutableListOf<Triple<String, Boolean, String>>()
        for(i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val name = obj.getString("name")
            val checked = obj.getBoolean("checked")
            val imgFile = obj.optString("image", "")
            val localPath = if(imgFile.isNotEmpty()) imageMap[imgFile] ?: "" else ""
            importList.add(Triple(name, checked, localPath))
            count++
        }

        kotlinx.coroutines.runBlocking {
            importList.forEach { (name, checked, imagePath) ->
                if(name.isNotBlank())
                    repository.importItem(name.trim(), checked, imagePath)
            }
        }
        return count
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

    fun importFullItems(items: List<Triple<String, Boolean, String>>) {
        viewModelScope.launch {
            items.forEach { (name, checked, imagePath) ->
                if(name.isNotBlank())
                    repository.importItem(name.trim(), checked, imagePath)
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
