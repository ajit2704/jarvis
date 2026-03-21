package com.jarvis.voiceassistant.ui.blocks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.voiceassistant.data.Block

@Composable
fun TextBlock(
    block: Block.Text,
    modifier: Modifier = Modifier
) {
    Text(
        text = block.content,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}
