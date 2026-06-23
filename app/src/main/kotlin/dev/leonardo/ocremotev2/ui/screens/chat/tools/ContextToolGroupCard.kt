package dev.leonardo.ocremotev2.ui.screens.chat.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.domain.model.ToolState
import dev.leonardo.ocremotev2.ui.components.AmoledCard
import dev.leonardo.ocremotev2.ui.screens.chat.util.isAmoledTheme
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ContextToolGroupCard(
    parts: List<Part.Tool>,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pending = remember(parts) {
        parts.any { it.state is ToolState.Running || it.state is ToolState.Pending }
    }
    val summary = remember(parts) { contextToolSummary(parts) }
    val isAmoled = isAmoledTheme()

    AmoledCard(
        isAmoledDark = isAmoled,
        modifier = modifier.fillMaxWidth(),
        normalContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // Header
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(
                        if (pending) R.string.context_exploring
                        else R.string.context_explored
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            supportingContent = {
                val summaryText = buildSummaryText(summary.read, summary.search)
                if (summaryText.isNotEmpty()) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = if (pending) Icons.Filled.Search
                                  else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (pending) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                                  else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable { expanded = !expanded },
        )

        // Expanded children
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                parts.forEachIndexed { idx, part ->
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                text = toolLabel(part.tool),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        supportingContent = {
                            val subtitle = toolSubtitle(part)
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = toolIcon(part.tool),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { handleToolClick(part, onOpenFile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun buildSummaryText(readCount: Int, searchCount: Int): String {
    val parts = mutableListOf<String>()
    if (readCount > 0) {
        parts.add(stringResource(R.string.context_read_count, readCount))
    }
    if (searchCount > 0) {
        parts.add(stringResource(R.string.context_search_count, searchCount))
    }
    return parts.joinToString(" · ")
}

private fun handleToolClick(part: Part.Tool, onOpenFile: (String) -> Unit) {
    if (part.tool.lowercase() != "read") return
    val input = extractToolInput(part)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull
    if (filePath != null) onOpenFile(filePath)
}

private fun toolIcon(toolName: String) = when (toolName.lowercase()) {
    "read" -> Icons.Filled.Description
    "glob" -> Icons.Filled.FindInPage
    "grep" -> Icons.Filled.Search
    else -> Icons.Filled.Description
}

@Composable
private fun toolLabel(toolName: String): String = when (toolName.lowercase()) {
    "read" -> stringResource(R.string.tool_read)
    "glob" -> stringResource(R.string.tool_glob)
    "grep" -> stringResource(R.string.tool_grep)
    else -> toolName
}

private fun toolSubtitle(part: Part.Tool): String {
    val input = extractToolInput(part)
    return when (part.tool.lowercase()) {
        "read" -> input["filePath"]?.jsonPrimitive?.contentOrNull
            ?: input["path"]?.jsonPrimitive?.contentOrNull ?: ""
        "glob", "grep" -> input["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        else -> ""
    }
}
