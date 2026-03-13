package com.jarvis.voiceassistant.ui

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Simple UI to test the STT model: load model, record a few seconds, show transcription.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SttTestScreen(viewModel: SttTestViewModel) {
    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val modelStatus by viewModel.modelStatus.collectAsState()
    val recordState by viewModel.recordState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        if (!recordPermission.status.isGranted && !recordPermission.status.shouldShowRationale) {
            recordPermission.launchPermissionRequest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "STT Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Record 5 seconds, then see transcription",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (val status = modelStatus) {
            is SttTestModelStatus.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading model…", style = MaterialTheme.typography.bodyMedium)
            }
            is SttTestModelStatus.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = status.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is SttTestModelStatus.Ready -> {
                if (!recordPermission.status.isGranted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Microphone permission is required to record.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { recordPermission.launchPermissionRequest() }) {
                                Text("Grant permission")
                            }
                        }
                    }
                } else {
                    Text("Model ready", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (recordState) {
                        SttTestRecordState.Recording -> Text("Recording… (5 s)", style = MaterialTheme.typography.titleMedium)
                        SttTestRecordState.Transcribing -> Text("Transcribing…", style = MaterialTheme.typography.titleMedium)
                        SttTestRecordState.Idle -> { }
                    }
                    if (recordState == SttTestRecordState.Recording || recordState == SttTestRecordState.Transcribing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    FloatingActionButton(
                        onClick = { viewModel.recordAndTranscribe(5) },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(if (recordState == SttTestRecordState.Idle) "🎤 Record" else "⏳")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap to record 5 seconds", style = MaterialTheme.typography.bodySmall)

                    if (transcript != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = "Transcript:",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Text(
                                text = transcript ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Start
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.clearTranscript() }) {
                            Text("Clear")
                        }
                    }
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
