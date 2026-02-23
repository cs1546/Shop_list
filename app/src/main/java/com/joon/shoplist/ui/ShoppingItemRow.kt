package com.joon.shoplist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.util.Store

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    defaultStore: Store,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSearch: (Store) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(dismissState.currentValue) {
        if(dismissState.currentValue != SwipeToDismissBoxValue.Settled)
            onDelete()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if(dismissState.targetValue != SwipeToDismissBoxValue.Settled)
                    MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "swipe-bg"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })

            Text(
                text = item.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if(item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if(item.isChecked)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )

            TextButton(onClick = { onSearch(defaultStore) }) {
                Text(defaultStore.label, style = MaterialTheme.typography.labelSmall)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    Store.entries
                        .filter { it != defaultStore }
                        .forEach { store ->
                            DropdownMenuItem(
                                text = { Text(store.label) },
                                onClick = {
                                    menuExpanded = false
                                    onSearch(store)
                                }
                            )
                        }
                    DropdownMenuItem(
                        text = { Text("수정") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                }
            }
        }
    }
}
