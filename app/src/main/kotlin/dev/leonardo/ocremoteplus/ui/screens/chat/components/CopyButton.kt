package dev.leonardo.ocremoteplus.ui.screens.chat.components

import android.content.ClipData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import dev.leonardo.ocremoteplus.ui.theme.AlphaTokens
import kotlinx.coroutines.launch

/**
 * A small copy button that copies [text] to clipboard.
 * Used in assistant message bubbles and tool cards.
 */
@Composable
fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "Copy"
) {
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    IconButton(
        onClick = {
            clipScope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("copy", text)))
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.MUTED),
            modifier = Modifier
        )
    }
}
