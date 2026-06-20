package com.faster.tibot.ui.chats.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val EMOJIS = listOf(
    "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE05", "\uD83D\uDE06", "\uD83D\uDE09", "\uD83D\uDE0A",
    "\uD83D\uDE0B", "\uD83D\uDE0E", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83E\uDD70", "\uD83D\uDE17", "\uD83D\uDE19", "\uD83D\uDE1A", "\uD83D\uDE42", "\uD83E\uDD17",
    "\uD83E\uDD29", "\uD83E\uDD14", "\uD83E\uDD28", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE36", "\uD83D\uDE44", "\uD83D\uDE0F", "\uD83D\uDE23", "\uD83D\uDE25",
    "\uD83D\uDE2E", "\uD83E\uDD10", "\uD83D\uDE2F", "\uD83D\uDE2A", "\uD83D\uDE2B", "\uD83E\uDD71", "\uD83D\uDE34", "\uD83D\uDE0C", "\uD83D\uDE1B", "\uD83D\uDE1C",
    "\uD83D\uDE1D", "\uD83E\uDD24", "\uD83D\uDE12", "\uD83D\uDE13", "\uD83D\uDE14", "\uD83D\uDE15", "\uD83D\uDE43", "\uD83E\uDD11", "\uD83D\uDE32", "☹️",
    "\uD83D\uDE41", "\uD83D\uDE16", "\uD83D\uDE1E", "\uD83D\uDE1F", "\uD83D\uDE24", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE2C", "\uD83D\uDE26", "\uD83D\uDE27",
    "\uD83D\uDE29", "\uD83E\uDD2F", "\uD83D\uDE2C", "\uD83D\uDE30", "\uD83D\uDE31", "\uD83E\uDD75", "\uD83E\uDD76", "\uD83D\uDE33", "\uD83E\uDD2A", "\uD83D\uDE35",
    "\uD83E\uDD74", "\uD83D\uDE20", "\uD83D\uDE21", "\uD83E\uDD2C", "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD15", "\uD83E\uDD22", "\uD83E\uDD2E", "\uD83E\uDD27",
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4C", "\uD83D\uDC4C", "✌️", "\uD83E\uDD1E", "\uD83E\uDD1F", "\uD83E\uDD18", "\uD83E\uDD19",
    "\uD83D\uDC4B", "\uD83D\uDC4B", "\uD83D\uDCAA", "\uD83D\uDE4F", "❤️", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C",
    "\uD83D\uDC94", "\uD83E\uDD0D", "\uD83E\uDD0E", "\uD83D\uDC94", "❣️", "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96",
    "\uD83D\uDC98", "\uD83D\uDC9D", "\uD83D\uDC9F", "\uD83D\uDC8C", "\uD83D\uDCA2", "\uD83D\uDCA3", "\uD83D\uDCA5", "\uD83D\uDCA6", "\uD83D\uDCA8", "\uD83D\uDCAB",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "表情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismiss()
                        }
                    },
                ) {
                    Text("关闭")
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(8.dp),
            ) {
                items(EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                onEmojiSelected(emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                        )
                    }
                }
            }
        }
    }
}
