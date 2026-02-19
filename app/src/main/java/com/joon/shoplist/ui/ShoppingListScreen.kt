package com.joon.shoplist.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.util.SearchUrlHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel) {
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ShoppingItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("쇼핑 체크리스트") },
                actions = {
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    ) { padding ->
        if(items.isEmpty()) {
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.id }) { item ->
                    ShoppingItemRow(
                        item = item,
                        onToggle = { viewModel.toggleCheck(item) },
                        onEdit = { editTarget = item },
                        onDelete = { viewModel.deleteItem(item) },
                        onSearchCoupang = {
                            openUrl(context, SearchUrlHelper.coupang(item.name))
                        },
                        onSearchSSG = {
                            openUrl(context, SearchUrlHelper.ssg(item.name))
                        }
                    )
                }
            }
        }
    }

    if(showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addItem(name)
                showAddDialog = false
            }
        )
    }

    if(showClearDialog) {
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
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
