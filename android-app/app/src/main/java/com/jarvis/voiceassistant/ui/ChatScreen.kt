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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.ui.blocks.BlockRenderer

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val initState by viewModel.initState.collectAsState()
    val document by viewModel.document.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (document.blocks.isEmpty()) {
                    Text(
                        text = "Empty document — tap mic to add notes or todos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    document.blocks.forEachIndexed { blockIndex, block ->
                        BlockRenderer(
                            block = block,
                            onTodoToggle = { itemIndex -> viewModel.toggleTodo(blockIndex, itemIndex) }
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        LaunchedEffect(snackbarMessage) {
            snackbarMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                viewModel.clearSnackbar()
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

private fun statusText(state: AssistantState): String = when (state) {
    AssistantState.Idle -> "Ready"
    AssistantState.Listening -> "Listening…"
    AssistantState.ProcessingSTT -> "Transcribing…"
    AssistantState.ProcessingLLM -> "Thinking…"
    is AssistantState.Error -> "Error: ${state.message}"
}
