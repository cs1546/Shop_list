package com.joon.shoplist.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.joon.shoplist.data.SavedShoppingItem
import com.joon.shoplist.data.SavedShoppingList
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.data.ShoppingRepository
import com.joon.shoplist.util.Store
import com.joon.shoplist.util.UpdateChecker
import com.joon.shoplist.util.UpdateInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class Screen
{
    data object ShoppingList : Screen()
    data object HistoryList : Screen()
    data class HistoryDetail(val listId: Long, val date: String) : Screen()
    data object Settings : Screen()
}

sealed class UndoEvent
{
    data class Single(val item: ShoppingItem) : UndoEvent()
    data class ClearAll(val items: List<ShoppingItem>) : UndoEvent()
}

class ShoppingViewModel(
    private val repository: ShoppingRepository,
    private val appContext: Context
) : ViewModel()
{
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

    private val _roomCode = MutableStateFlow(prefs.getString("room_code", null))
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val deviceId: String = prefs.getString("device_id", null) ?: run {
        val id = java.util.UUID.randomUUID().toString().take(8)
        prefs.edit().putString("device_id", id).apply()
        id
    }

    private var firebaseListener: ValueEventListener? = null
    private var listenerRef: DatabaseReference? = null

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init
    {
        if(_roomCode.value != null)
            startFirebaseListener()
    }

    private fun loadDefaultStore(): Store
    {
        val name = prefs.getString("default_store", Store.COUPANG.name) ?: Store.COUPANG.name
        return Store.entries.find { it.name == name } ?: Store.COUPANG
    }

    fun setDefaultStore(store: Store)
    {
        prefs.edit().putString("default_store", store.name).apply()
        _defaultStore.value = store
    }

    fun setRoomCode(code: String?)
    {
        val trimmed = code?.trim()?.ifEmpty { null }
        prefs.edit().putString("room_code", trimmed).apply()
        _roomCode.value = trimmed
        stopFirebaseListener()
        if(trimmed != null)
            startFirebaseListener()
    }

    fun addItem(name: String)
    {
        if(name.isBlank()) return
        viewModelScope.launch { repository.add(name.trim()) }
    }

    fun toggleCheck(item: ShoppingItem)
    {
        viewModelScope.launch { repository.toggleCheck(item) }
    }

    fun renameItem(item: ShoppingItem, newName: String)
    {
        if(newName.isBlank()) return
        viewModelScope.launch { repository.rename(item, newName.trim()) }
    }

    fun deleteItem(item: ShoppingItem)
    {
        viewModelScope.launch {
            repository.delete(item)
            _undoEvent.emit(UndoEvent.Single(item))
        }
    }

    fun undoDelete(event: UndoEvent)
    {
        viewModelScope.launch {
            when(event)
            {
                is UndoEvent.Single -> repository.restore(event.item)
                is UndoEvent.ClearAll -> repository.restoreAll(event.items)
            }
        }
    }

    fun setImagePath(item: ShoppingItem, path: String)
    {
        viewModelScope.launch { repository.setImagePath(item, path) }
    }

    fun removeImage(item: ShoppingItem)
    {
        viewModelScope.launch {
            if(item.imagePath.isNotEmpty())
                File(item.imagePath).delete()
            repository.setImagePath(item, "")
        }
    }

    fun reorderItems(newOrder: List<ShoppingItem>)
    {
        viewModelScope.launch { repository.reorderItems(newOrder) }
    }

    fun checkUpdate(currentVersion: String, silent: Boolean = false)
    {
        viewModelScope.launch {
            val info = UpdateChecker.check(currentVersion)
            if(info != null)
                _updateInfo.value = info
            else if(!silent)
                _snackMessage.emit("최신 버전입니다")
        }
    }

    fun dismissUpdate()
    {
        _updateInfo.value = null
    }

    fun downloadAndInstall()
    {
        val info = _updateInfo.value ?: return
        viewModelScope.launch {
            _snackMessage.emit("다운로드 중...")
            val file = UpdateChecker.download(appContext, info.downloadUrl)
            if(file == null)
            {
                _snackMessage.emit("다운로드 실패")
                return@launch
            }
            _updateInfo.value = null
            UpdateChecker.install(appContext, file)
        }
    }

    fun sendToFirebase()
    {
        val code = _roomCode.value
        if(code == null)
        {
            viewModelScope.launch {
                _snackMessage.emit("설정에서 방 코드를 먼저 입력하세요")
                _currentScreen.value = Screen.Settings
            }
            return
        }
        viewModelScope.launch {
            try
            {
                val db = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
                val ref = db.getReference("rooms/$code/shared")

                val itemsList = items.value.map { item ->
                    val map = mutableMapOf<String, Any>(
                        "name" to item.name,
                        "checked" to item.isChecked
                    )
                    if(item.imagePath.isNotEmpty() && File(item.imagePath).exists())
                    {
                        val b64 = compressImageToBase64(item.imagePath)
                        if(b64.isNotEmpty())
                            map["image"] = b64
                    }
                    map
                }
                val data = mapOf(
                    "items" to itemsList,
                    "sender" to deviceId,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                ref.setValue(data)
                    .addOnSuccessListener {
                        viewModelScope.launch { _snackMessage.emit("보내기 완료") }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch { _snackMessage.emit("보내기 실패: ${e.message}") }
                    }
            }
            catch(e: Exception)
            {
                _snackMessage.emit("보내기 실패: ${e.message}")
            }
        }
    }

    fun receiveFromFirebase()
    {
        val code = _roomCode.value
        if(code == null)
        {
            viewModelScope.launch {
                _snackMessage.emit("설정에서 방 코드를 먼저 입력하세요")
                _currentScreen.value = Screen.Settings
            }
            return
        }
        val db = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
        val ref = db.getReference("rooms/$code/shared")
        ref.get().addOnSuccessListener { snapshot ->
            viewModelScope.launch { importFromSnapshot(snapshot, true) }
        }.addOnFailureListener { e ->
            viewModelScope.launch { _snackMessage.emit("받아오기 실패: ${e.message}") }
        }
    }

    private suspend fun importFromSnapshot(snapshot: DataSnapshot, showEmpty: Boolean = false)
    {
        val itemsSnap = snapshot.child("items").children.toList()
        if(itemsSnap.isEmpty())
        {
            if(showEmpty)
                _snackMessage.emit("공유된 리스트가 없습니다")
            return
        }
        var added = 0
        for(itemSnap in itemsSnap)
        {
            val name = itemSnap.child("name").getValue(String::class.java) ?: continue
            val checked = itemSnap.child("checked").getValue(Boolean::class.java) ?: false
            val imageB64 = itemSnap.child("image").getValue(String::class.java) ?: ""

            var localPath = ""
            if(imageB64.isNotEmpty())
                localPath = saveBase64Image(imageB64)

            if(name.isNotBlank() && repository.importItem(name.trim(), checked, localPath))
                added++
        }
        if(0 < added)
            _snackMessage.emit("${added}개 항목 추가됨")
        else if(showEmpty)
            _snackMessage.emit("새로운 항목 없음")
    }

    private fun startFirebaseListener()
    {
        val code = _roomCode.value ?: return
        val db = FirebaseDatabase.getInstance(FIREBASE_DB_URL)
        val ref = db.getReference("rooms/$code/shared")
        listenerRef = ref

        firebaseListener = object : ValueEventListener
        {
            override fun onDataChange(snapshot: DataSnapshot)
            {
                val sender = snapshot.child("sender").getValue(String::class.java) ?: return
                if(sender == deviceId) return

                viewModelScope.launch { importFromSnapshot(snapshot) }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(firebaseListener!!)
    }

    private fun stopFirebaseListener()
    {
        firebaseListener?.let { listenerRef?.removeEventListener(it) }
        firebaseListener = null
        listenerRef = null
    }

    private fun compressImageToBase64(path: String): String
    {
        return try
        {
            val bitmap = BitmapFactory.decodeFile(path) ?: return ""
            val max_dim = 300
            val scale = minOf(max_dim.toFloat() / bitmap.width, max_dim.toFloat() / bitmap.height, 1f)
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            if(bitmap !== scaled) scaled.recycle()
            bitmap.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
        catch(e: Exception) { "" }
    }

    private fun saveBase64Image(base64: String): String
    {
        return try
        {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val imagesDir = File(appContext.filesDir, "images")
            imagesDir.mkdirs()
            // 여러 항목을 같은 밀리초에 저장하므로 시각만으로 이름을 지으면 서로 덮어씀
            val file = File(imagesDir, "${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_firebase.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }
        catch(e: Exception) { "" }
    }

    fun clearAll()
    {
        viewModelScope.launch {
            val snapshot = items.value
            repository.clearAll()
            if(snapshot.isNotEmpty())
                _undoEvent.emit(UndoEvent.ClearAll(snapshot))
        }
    }

    fun saveAndClear()
    {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val currentItems = items.value
            if(currentItems.isNotEmpty())
            {
                val snapshot = currentItems.toList()
                repository.saveAndClear(today, currentItems)
                _undoEvent.emit(UndoEvent.ClearAll(snapshot))
            }
            _currentScreen.value = Screen.HistoryList
        }
    }

    fun navigateTo(screen: Screen)
    {
        _currentScreen.value = screen
        if(screen is Screen.HistoryDetail)
        {
            viewModelScope.launch {
                repository.getSavedItems(screen.listId).collect {
                    _selectedListItems.value = it
                }
            }
        }
    }

    fun goBack()
    {
        _currentScreen.value = when(_currentScreen.value)
        {
            is Screen.HistoryDetail -> Screen.HistoryList
            is Screen.HistoryList -> Screen.ShoppingList
            is Screen.Settings -> Screen.ShoppingList
            else -> Screen.ShoppingList
        }
    }

    fun importItems(names: List<String>)
    {
        viewModelScope.launch {
            var added = 0
            names.forEach { name ->
                if(name.isNotBlank() && repository.addIfNotExists(name.trim()))
                    added++
            }
            val skipped = names.count { it.isNotBlank() } - added
            val msg = buildString {
                if(0 < added) append("${added}개 추가")
                if(0 < skipped)
                {
                    if(isNotEmpty()) append(", ")
                    append("${skipped}개 중복 제외")
                }
            }
            if(msg.isNotEmpty())
                _snackMessage.emit(msg)
        }
    }

    fun deleteSavedList(listId: Long)
    {
        viewModelScope.launch { repository.deleteSavedList(listId) }
    }

    override fun onCleared()
    {
        super.onCleared()
        stopFirebaseListener()
    }

    class Factory(
        private val repository: ShoppingRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory
    {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShoppingViewModel(repository, appContext) as T
    }

    companion object
    {
        private const val FIREBASE_DB_URL =
            "https://shoplist-d6e26-default-rtdb.asia-southeast1.firebasedatabase.app"
    }
}
