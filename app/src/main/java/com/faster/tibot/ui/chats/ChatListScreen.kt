package com.faster.tibot.ui.chats

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faster.tibot.ui.chats.components.ChatListSearchBar
import com.faster.tibot.ui.chats.components.ChatListTopBar
import com.faster.tibot.ui.chats.components.ChatRow

@Composable
fun ChatListScreen(
    onChatClick: (Long) -> Unit = {},
) {
    val vm: ChatsViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val chats by vm.chats.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }

    val filteredChats = if (searchQuery.isBlank()) {
        chats
    } else {
        val q = searchQuery.trim().lowercase()
        chats.filter {
            it.chatTitle.lowercase().contains(q) ||
                it.lastMessage.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChatListTopBar(
            title = "消息",
            onSearchClick = { searchVisible = !searchVisible },
        )

        if (searchVisible) {
            ChatListSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
        }

        if (filteredChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无消息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有匹配的聊天" else "您的聊天记录将显示在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredChats, key = { it.chatId }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = {
                            vm.selectChat(chat.chatId)
                            onChatClick(chat.chatId)
                        },
                    )
                }
            }
        }
    }
}
