package com.jarvis.voiceassistant.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.data.Message

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val initState by viewModel.initState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()

    LaunchedEffect(Unit) {
        if (!recordPermission.status.isGranted && !recordPermission.status.shouldShowRationale) {
            recordPermission.launchPermissionRequest()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = initState) {
            is ChatInitState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading models…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                return
            }
            is ChatInitState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                return
            }
            is ChatInitState.Ready -> { /* continue to chat UI */ }
        }

        Text(
            text = "Jarvis",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = statusText(assistantState),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(messages, key = { it.timestamp }) { msg ->
                MessageBubble(message = msg)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            val disabled = viewModel.isListening() || viewModel.isProcessing()
            FloatingActionButton(
                onClick = { if (!disabled) viewModel.startVoiceInput(5) },
                modifier = Modifier.padding(8.dp),
                containerColor = if (disabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(if (viewModel.isListening()) "⏹" else "🎤")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

private fun statusText(state: AssistantState): String = when (state) {
    AssistantState.Idle -> "Ready"
    AssistantState.Listening -> "Listening…"
    AssistantState.ProcessingSTT -> "Transcribing…"
    AssistantState.ProcessingLLM -> "Thinking…"
    is AssistantState.Error -> "Error: ${state.message}"
}
