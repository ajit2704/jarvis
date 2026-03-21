package com.jarvis.voiceassistant.ui.blocks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.voiceassistant.data.Block

/**
 * Renders a single block (Text or Todo). Dispatches to [TextBlock] or [TodoBlock].
 * Optional card styling for clearer separation between blocks.
 */
@Composable
fun BlockRenderer(
    block: Block,
    onTodoToggle: (itemIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = CardDefaults.shape
    ) {
        when (block) {
            is Block.Text -> TextBlock(block = block, modifier = Modifier.padding(12.dp))
            is Block.Todo -> TodoBlock(
                block = block,
                onTodoToggle = onTodoToggle,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
