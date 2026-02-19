package com.joon.shoplist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClearDialog(
    onSaveAndClear: () -> Unit,
    onClearOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("리스트 클리어") },
        text = { Text("오늘 날짜로 현재 항목을 저장하시겠습니까?") },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
                TextButton(onClick = onClearOnly) {
                    Text("클리어만")
                }
                TextButton(onClick = onSaveAndClear) {
                    Text("저장 후 클리어")
                }
            }
        }
    )
}
