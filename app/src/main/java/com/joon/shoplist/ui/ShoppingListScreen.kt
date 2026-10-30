package com.joon.shoplist.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.util.SearchUrlHelper
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel)
{
    val items by viewModel.items.collectAsState()
    val defaultStore by viewModel.defaultStore.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ShoppingItem?>(null) }
    var imagePickTarget by remember { mutableStateOf<ShoppingItem?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val target = imagePickTarget ?: return@rememberLauncherForActivityResult
        imagePickTarget = null
        if(uri == null) return@rememberLauncherForActivityResult

        val savedPath = copyImageToInternal(context, uri, target.id)
        if(savedPath != null)
            viewModel.setImagePath(target, savedPath)
    }

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { event ->
            val message = when(event)
            {
                is UndoEvent.Single -> "'${event.item.name}' 삭제됨"
                is UndoEvent.ClearAll -> "${event.items.size}개 항목 삭제됨"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "되돌리기",
                duration = SnackbarDuration.Short
            )
            if(result == SnackbarResult.ActionPerformed)
                viewModel.undoDelete(event)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackMessage.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("쇼핑 체크리스트") },
                actions = {
                    IconButton(
                        onClick = { viewModel.sendToFirebase() },
                        enabled = items.isNotEmpty()
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "보내기")
                    }
                    IconButton(onClick = { viewModel.receiveFromFirebase() }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "받아오기")
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.HistoryList) }) {
                        Icon(Icons.Default.DateRange, contentDescription = "저장 기록")
                    }
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = items.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "클리어")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    ) { padding ->
        if(items.isEmpty())
        {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "항목이 없습니다.\n+를 눌러 추가하세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else
        {
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val reordered = items.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                viewModel.reorderItems(reordered)
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                // FAB(+)가 마지막 행의 메뉴 버튼을 가리지 않도록 하단 여백 확보
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ReorderableItem(reorderState, key = item.id) {
                        ShoppingItemRow(
                            item = item,
                            defaultStore = defaultStore,
                            onToggle = { viewModel.toggleCheck(item) },
                            onEdit = { editTarget = item },
                            onDelete = { viewModel.deleteItem(item) },
                            onSearch = { store ->
                                openUrl(context, SearchUrlHelper.buildUrl(store, item.name))
                            },
                            onAttachImage = {
                                imagePickTarget = item
                                imagePickerLauncher.launch("image/*")
                            },
                            onRemoveImage = { viewModel.removeImage(item) },
                            onViewImage = { viewImage(context, item.imagePath) },
                            onPasteImage = {
                                val path = pasteImageFromClipboard(context, item.id)
                                if(path != null)
                                    viewModel.setImagePath(item, path)
                            },
                            dragHandle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle()
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "순서 변경")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if(showAddDialog)
    {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addItem(name)
                showAddDialog = false
            }
        )
    }

    if(showClearDialog)
    {
        ClearDialog(
            onSaveAndClear = {
                showClearDialog = false
                viewModel.saveAndClear()
            },
            onClearOnly = {
                showClearDialog = false
                viewModel.clearAll()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    editTarget?.let { item ->
        AddItemDialog(
            initialName = item.name,
            isEdit = true,
            onDismiss = { editTarget = null },
            onConfirm = { newName ->
                viewModel.renameItem(item, newName)
                editTarget = null
            }
        )
    }

    UpdateDialog(
        info = updateInfo,
        onUpdate = { viewModel.downloadAndInstall() },
        onDismiss = { viewModel.dismissUpdate() }
    )
}

private fun copyImageToInternal(context: Context, uri: Uri, itemId: Long): String?
{
    return try
    {
        val imagesDir = File(context.filesDir, "images")
        if(!imagesDir.exists()) imagesDir.mkdirs()
        val destFile = File(imagesDir, "${itemId}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    }
    catch(e: Exception) { null }
}

private fun pasteImageFromClipboard(context: Context, itemId: Long): String?
{
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if(clip.itemCount < 1) return null

    val item = clip.getItemAt(0)
    val uri = item.uri ?: return null
    return copyImageToInternal(context, uri, itemId)
}

private fun viewImage(context: Context, imagePath: String)
{
    val file = File(imagePath)
    if(!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

private fun openUrl(context: Context, url: String)
{
    // 쇼핑앱을 별도 태스크로 띄워야 최근 앱(멀티키)에서 이 앱과 따로 전환 가능
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try
    {
        context.startActivity(intent)
    }
    catch(e: ActivityNotFoundException)
    {
        Toast.makeText(context, "열 수 있는 앱이 없습니다", Toast.LENGTH_SHORT).show()
    }
}
