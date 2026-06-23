package dev.leonardo.ocremotev2.ui.screens.chat.tools.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.ui.screens.chat.util.isAmoledTheme
import dev.leonardo.ocremotev2.ui.theme.CodeTypography

/**
 * Shows a summary of files changed at the end of an agent turn.
 * Uses [ToolCardScaffold] for consistent styling with other tool cards.
 */
@Composable
internal fun PatchCard(
    patch: Part.Patch,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isAmoled = isAmoledTheme()
    val title = if (patch.files.size == 1)
        stringResource(R.string.chat_files_changed, patch.files.size)
    else
        stringResource(R.string.chat_files_changed_plural, patch.files.size)

    ToolCardScaffold(
        icon = Icons.Default.Code,
        iconTint = MaterialTheme.colorScheme.primary,
        title = title,
        copyText = title,
        isExpanded = isExpanded,
        isRunning = false,
        hasContent = patch.files.isNotEmpty(),
        isAmoled = isAmoled,
        onToggleExpand = onToggleExpand,
    ) {
        Column(
            modifier = Modifier.padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (filePath in patch.files) {
                Text(
                    text = filePath.substringAfterLast('/'),
                    style = CodeTypography.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
