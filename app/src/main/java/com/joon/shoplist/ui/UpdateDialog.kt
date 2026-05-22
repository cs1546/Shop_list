package com.joon.shoplist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.joon.shoplist.util.UpdateInfo

@Composable
fun UpdateDialog(info: UpdateInfo?,
                 onUpdate: () -> Unit,
                 onDismiss: () -> Unit)
{
    info ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 버전 있음: ${info.latestVersion}") },
        text = {
            Text(
                if(info.notes.isNotBlank()) info.notes
                else "새 버전으로 업데이트하시겠습니까?"
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("업데이트")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}
