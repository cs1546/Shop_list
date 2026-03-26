package com.joon.shoplist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joon.shoplist.data.ShoppingItem
import com.joon.shoplist.util.Store
import java.io.File

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    defaultStore: Store,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSearch: (Store) -> Unit,
    onAttachImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onViewImage: () -> Unit,
    onPasteImage: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var menuExpanded by remember { mutableStateOf(false) }
    val hasImage = item.imagePath.isNotEmpty() && File(item.imagePath).exists()

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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if(item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if(item.isChecked)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                )

                if(hasImage) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = "첨부 이미지",
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onViewImage() },
                        contentScale = ContentScale.Crop
                    )
                }
            }

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
                        text = { Text(if(hasImage) "사진 변경" else "사진 첨부") },
                        onClick = {
                            menuExpanded = false
                            onAttachImage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("클립보드에서 붙여넣기") },
                        onClick = {
                            menuExpanded = false
                            onPasteImage()
                        }
                    )
                    if(hasImage) {
                        DropdownMenuItem(
                            text = { Text("사진 삭제") },
                            onClick = {
                                menuExpanded = false
                                onRemoveImage()
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
