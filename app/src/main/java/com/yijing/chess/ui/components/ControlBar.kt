package com.yijing.chess.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yijing.chess.ui.theme.PrimaryGold

@Composable
fun GameControlBar(
    canUndo: Boolean,
    isEditorMode: Boolean,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onFlip: () -> Unit,
    onToggleEditor: () -> Unit,
    onRestart: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ControlItem(
            icon = Icons.Default.Undo,
            label = "悔棋",
            enabled = canUndo,
            onClick = onUndo
        )
        ControlItem(
            icon = Icons.Default.AutoAwesome,
            label = "提示",
            enabled = !isEditorMode,
            onClick = onHint
        )
        ControlItem(
            icon = Icons.Default.Cached,
            label = "翻转",
            enabled = true,
            onClick = onFlip
        )
        ControlItem(
            icon = Icons.Default.Build,
            label = if (isEditorMode) "退出摆谱" else "摆谱排局",
            enabled = true,
            isHighlight = isEditorMode,
            onClick = onToggleEditor
        )
        ControlItem(
            icon = Icons.Default.Share,
            label = "导出",
            enabled = true,
            onClick = onExport
        )
        ControlItem(
            icon = Icons.Default.Refresh,
            label = "重开",
            enabled = true,
            onClick = onRestart
        )
    }
}

@Composable
private fun ControlItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (isHighlight) PrimaryGold else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isHighlight) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
        Text(text = label, fontSize = 13.sp)
    }
}
