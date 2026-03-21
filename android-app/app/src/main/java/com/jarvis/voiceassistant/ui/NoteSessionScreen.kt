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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NoteSessionScreen(viewModel: NoteSessionViewModel) {
    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val initState by viewModel.initState.collectAsState()
    val raw by viewModel.rawTranscript.collectAsState()
    val committed by viewModel.committedStable.collectAsState()
    val pending by viewModel.pendingTranscript.collectAsState()
    val processed by viewModel.processedPlaceholder.collectAsState()
    val captureState by viewModel.captureState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (!recordPermission.status.isGranted && !recordPermission.status.shouldShowRationale) {
            recordPermission.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        when (val state = initState) {
            is NoteSessionInitState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading STT…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                return
            }
            is NoteSessionInitState.Error -> {
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
            is NoteSessionInitState.Ready -> { }
        }

        Text(
            text = "Note",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = captureStatusText(captureState),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Top: processed (Phase 2) — placeholder for now
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = processed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Capture (stabilized)",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Bottom: stable + pending + raw STT log (Phase 1b)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    text = "Stable",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = committed.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = pending.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Raw STT (segments)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = raw.ifBlank { "… speak to dictate; mic stays on until you stop or 2 min silence." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.15f)
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { data ->
                Snackbar(snackbarData = data, modifier = Modifier.padding(8.dp))
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
            val listening = captureState == NoteCaptureState.Listening ||
                captureState == NoteCaptureState.Transcribing
            FloatingActionButton(
                onClick = {
                    if (listening) viewModel.stopNoteSession()
                    else viewModel.startNoteSession()
                },
                modifier = Modifier.padding(8.dp),
                containerColor = if (listening) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (listening) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(if (listening) "⏹" else "🎤")
            }
        }
    }
}

private fun captureStatusText(state: NoteCaptureState): String = when (state) {
    NoteCaptureState.Idle -> "Ready — tap mic to dictate"
    NoteCaptureState.Listening -> "Listening… (chunk)"
    NoteCaptureState.Transcribing -> "Transcribing…"
    NoteCaptureState.EndedByUser -> "Stopped"
    NoteCaptureState.EndedBySilence -> "Stopped (2 min silence)"
}
