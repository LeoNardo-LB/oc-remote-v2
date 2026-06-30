package dev.leonardo.ocremotev2.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Message
import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.domain.model.AgentInfo
import dev.leonardo.ocremotev2.ui.components.AmoledDefaultBorder
import dev.leonardo.ocremotev2.ui.components.ConfirmDialog
import dev.leonardo.ocremotev2.ui.components.ProviderIcon
import dev.leonardo.ocremotev2.ui.screens.chat.ChatMessage
import dev.leonardo.ocremotev2.ui.screens.chat.tools.RenderableTurn
import dev.leonardo.ocremotev2.ui.screens.chat.isBubbleRenderablePart
import dev.leonardo.ocremotev2.ui.screens.chat.dialog.ImageThumbnailRow
import dev.leonardo.ocremotev2.ui.theme.ChatDensity
import dev.leonardo.ocremotev2.ui.theme.LocalChatDensity
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalHapticFeedbackEnabled
import dev.leonardo.ocremotev2.ui.theme.QueuedBadgeColor
import dev.leonardo.ocremotev2.ui.theme.QueuedBadgeTextColor
import dev.leonardo.ocremotev2.ui.screens.chat.util.agentColor
import dev.leonardo.ocremotev2.ui.screens.chat.tools.RenderItem
import dev.leonardo.ocremotev2.ui.screens.chat.util.formatDuration
import dev.leonardo.ocremotev2.ui.screens.chat.util.formatTokenCount
import dev.leonardo.ocremotev2.ui.screens.chat.util.isAmoledTheme
import dev.leonardo.ocremotev2.ui.screens.chat.util.performHaptic
import dev.leonardo.ocremotev2.ui.screens.chat.util.resolveUserCommandLabel
import dev.leonardo.ocremotev2.ui.screens.chat.tools.ContextToolGroupCard
import dev.leonardo.ocremotev2.ui.screens.chat.tools.PartGroup
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalShowTurnDividers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.leonardo.ocremotev2.ui.theme.ShapeTokens
import dev.leonardo.ocremotev2.ui.theme.AlphaTokens
import dev.leonardo.ocremotev2.ui.theme.SpacingTokens

enum class MessageCardRole { USER, ASSISTANT }

@Composable
internal fun MessageCard(
    role: MessageCardRole,
    currentMessage: ChatMessage,
    isQueued: Boolean = false,
    renderableTurn: RenderableTurn? = null,
    onViewSubSession: ((String) -> Unit)? = null,
    onOpenFile: ((String) -> Unit)? = null,
    onRevert: (() -> Unit)? = null,
    onCopyText: (() -> Unit)? = null,
    isAmoled: Boolean = false,
    isTurnLast: Boolean = false,
    agents: List<AgentInfo> = emptyList(),
) {
    when (role) {
        MessageCardRole.USER -> MessageCardUser(
            currentMessage = currentMessage,
            isQueued = isQueued,
            onRevert = onRevert,
            onCopyText = onCopyText,
            isAmoled = isAmoled,
        )
        MessageCardRole.ASSISTANT -> MessageCardAssistant(
            renderableTurn = renderableTurn ?: error("renderableTurn is required for ASSISTANT role"),
            currentMessage = currentMessage,
            onViewSubSession = onViewSubSession,
            onOpenFile = onOpenFile,
            isAmoled = isAmoled,
            isTurnLast = isTurnLast,
            agents = agents,
        )
    }
}

@Composable
private fun MessageCardUser(
    currentMessage: ChatMessage,
    isQueued: Boolean,
    onRevert: (() -> Unit)?,
    onCopyText: (() -> Unit)?,
    isAmoled: Boolean,
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isAmoled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val bubbleBorder = if (isAmoled) {
        BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.MUTED)
        )
    } else {
        null
    }
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    // Filter visible parts for user messages
    val visibleParts = currentMessage.parts.filter { part ->
        when (part) {
            is Part.Text -> part.synthetic != true && part.ignored != true && part.text.isNotBlank()
            else -> true
        }
    }

    val userMessage = currentMessage.message as? Message.User
    val userFallbackText = userMessage?.summary?.body?.takeIf { it.isNotBlank() }
        ?: userMessage?.summary?.title?.takeIf { it.isNotBlank() }
    val userCommandLabel = resolveUserCommandLabel(currentMessage.parts)

    val contentParts = visibleParts

    val hasRenderableUserPart = contentParts.any(::isBubbleRenderablePart)
    if (!hasRenderableUserPart && userFallbackText == null && userCommandLabel == null) {
        return
    }

    var showRevertConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 4.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = backgroundColor,
            border = bubbleBorder,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            val compact = LocalChatDensity.current == ChatDensity.Compact
            Column(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 10.dp else SpacingTokens.LG.dp,
                        vertical = if (compact) SpacingTokens.SM.dp else 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) SpacingTokens.XS.dp else 10.dp)
                ) {
                    // Content parts (text, reasoning, patches, etc.)
                    // Group image file parts into a compact thumbnail row
                    val (imageFiles, renderableOtherParts) = remember(contentParts) {
                        val images = contentParts.filterIsInstance<Part.File>()
                            .filter { it.mime.startsWith("image/") && !it.url.isNullOrBlank() }
                        val others = contentParts.filter { part ->
                            !(part is Part.File && part.mime.startsWith("image/") && !part.url.isNullOrBlank())
                        }.filter(::isBubbleRenderablePart)
                        images to others
                    }

                    // Render image thumbnails as a horizontal row
                    if (imageFiles.isNotEmpty()) {
                        ImageThumbnailRow(imageFiles = imageFiles)
                    }

                    // Render remaining parts
                    for (part in renderableOtherParts) {
                        key(part.id) {
                            PartContent(
                                part = part,
                                textColor = textColor,
                                isUser = true,
                                onViewSubSession = null
                            )
                        }
                    }

                    if (imageFiles.isEmpty() && renderableOtherParts.isEmpty() && userCommandLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = stringResource(R.string.a11y_icon_rate_review),
                                modifier = Modifier.size(16.dp),
                                tint = textColor.copy(alpha = AlphaTokens.MEDIUM)
                            )
                            Text(
                                text = userCommandLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = AlphaTokens.AMOLED)
                            )
                        }
                    }

                    // If text parts are absent but server provided a summary, render it.
                    if (visibleParts.isEmpty() && userFallbackText != null) {
                        Text(
                            text = userFallbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = AlphaTokens.MUTED)
                        )
                    }

                    // 统计栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compact) SpacingTokens.XS.dp else SpacingTokens.SM.dp),
                        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.SM.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：时间
                        val timeText = remember(currentMessage.message.time.created) {
                            SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(Date(currentMessage.message.time.created))
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                        )

                        // QUEUED badge
                        if (isQueued) {
                            Surface(
                                shape = ShapeTokens.extraSmall,
                                color = QueuedBadgeColor,
                                modifier = Modifier.padding(end = SpacingTokens.XS.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_queued),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp,
                                        color = QueuedBadgeTextColor
                                    ),
                                    modifier = Modifier.padding(horizontal = SpacingTokens.XS.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // 弹性空白
                        Spacer(modifier = Modifier.weight(1f))

                        // Undo 按钮（仅主会话，onRevert != null 时显示）
                        if (onRevert != null) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.chat_revert),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        performHaptic(hapticView, hapticOn)
                                        showRevertConfirmation = true
                                    },
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                            )
                        }

                        // Copy 按钮（最右侧）
                        if (onCopyText != null) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        performHaptic(hapticView, hapticOn)
                                        onCopyText()
                                    },
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                            )
                        }
                    }
                }
        }

        // 撤回确认对话框
        if (showRevertConfirmation && onRevert != null) {
            ConfirmDialog(
                title = stringResource(R.string.chat_revert),
                message = stringResource(R.string.chat_revert_message),
                confirmLabel = stringResource(R.string.chat_revert),
                onDismiss = { showRevertConfirmation = false },
                onConfirm = {
                    showRevertConfirmation = false
                    onRevert()
                },
            )
        }
    }
}

@Composable
private fun MessageCardAssistant(
    renderableTurn: RenderableTurn,
    currentMessage: ChatMessage,
    onViewSubSession: ((String) -> Unit)?,
    onOpenFile: ((String) -> Unit)?,
    isAmoled: Boolean,
    isTurnLast: Boolean,
    agents: List<AgentInfo> = emptyList(),
) {
    val textColor = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface

    if (renderableTurn.isEmpty) return

    val compact = LocalChatDensity.current == ChatDensity.Compact
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val showTurnDividers = LocalShowTurnDividers.current

    // Keep for footer display (time, provider icon)
    val assistantMsg = currentMessage.message as? Message.Assistant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = ShapeTokens.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = if (isAmoled) AmoledDefaultBorder else null,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compact) 10.dp else SpacingTokens.LG.dp,
                    vertical = if (compact) SpacingTokens.SM.dp else 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else SpacingTokens.XS.dp)
            ) {
                // Render pre-computed items — zero filtering/grouping during composition.
                for (item in renderableTurn.renderItems) {
                    when (item) {
                        is RenderItem.TurnDivider -> {
                            if (showTurnDividers) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = if (compact) 3.dp else 6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is RenderItem.GroupedParts -> {
                            when (item.group) {
                                is PartGroup.Context -> key(item.group.parts.first().id) {
                                    ContextToolGroupCard(
                                        parts = item.group.parts,
                                        onOpenFile = onOpenFile ?: {},
                                    )
                                }
                                is PartGroup.Single -> key(item.group.part.id) {
                                    PartContent(
                                        part = item.group.part,
                                        textColor = textColor,
                                        isUser = false,
                                        onViewSubSession = onViewSubSession,
                                        onOpenFile = onOpenFile,
                                        turnAgentName = if (item.group.part is Part.Tool && item.group.part.tool == "task") {
                                            renderableTurn.taskAgentName
                                        } else null,
                                    )
                                }
                            }
                        }
                    }
                }

                // Pre-computed metadata
                val agentName = renderableTurn.agentName
                val stepFinishes = renderableTurn.stepFinishes
                val copyText = renderableTurn.copyText

                // Token/cost/duration footer — only on the last message of a turn
                if (stepFinishes.isNotEmpty()) {
                    val lastTokens = stepFinishes.lastOrNull()?.tokens
                    val totalInput = lastTokens?.input ?: 0
                    val totalOutput = lastTokens?.output ?: 0
                    val hasTokenStats = totalInput > 0 || totalOutput > 0

                    val durationMs = renderableTurn.durationMs
                    val modelId = renderableTurn.modelId

                    val hasFooter = hasTokenStats || (durationMs ?: 0) > 0 || !modelId.isNullOrBlank() || !agentName.isNullOrBlank()

                    if (hasFooter) {
                        Spacer(modifier = Modifier.height(if (compact) SpacingTokens.XS.dp else SpacingTokens.SM.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 时间
                            assistantMsg?.time?.created?.let { createdMs ->
                                val timeText = remember(createdMs) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(Date(createdMs))
                                }
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                                )
                            }
                            // Agent name tag (styled like QUEUED badge with agent color)
                            if (!agentName.isNullOrBlank()) {
                                val tagColor = agentColor(agentName, agents)
                                Surface(
                                    shape = ShapeTokens.smallMedium,
                                    color = tagColor.copy(alpha = AlphaTokens.FAINT)
                                ) {
                                    Text(
                                        text = agentName.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = tagColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            // Provider icon + model name (tight 3dp spacing, matching input bar)
                            val hasProviderOrModel = assistantMsg?.providerId != null || !modelId.isNullOrBlank()
                            if (hasProviderOrModel) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (assistantMsg?.providerId != null) {
                                        ProviderIcon(
                                            providerId = assistantMsg.providerId,
                                            size = 10.dp,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                                        )
                                    }
                                    if (!modelId.isNullOrBlank()) {
                                        Text(
                                            text = modelId,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (totalInput > 0 || totalOutput > 0) {
                                Text(
                                    text = "↑${formatTokenCount(totalInput)} ↓${formatTokenCount(totalOutput)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                                )
                            }
                            if (durationMs != null && durationMs > 0) {
                                Text(
                                    text = formatDuration(durationMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (copyText != null) {
                                CopyButton(
                                    text = copyText,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    // Fallback: no stats but copy button needed
                    if (!hasFooter && isTurnLast && copyText != null) {
                        Spacer(modifier = Modifier.height(if (compact) SpacingTokens.XS.dp else SpacingTokens.SM.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 时间
                            assistantMsg?.time?.created?.let { createdMs ->
                                val timeText = remember(createdMs) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(Date(createdMs))
                                }
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                                )
                            }
                            // Agent name tag
                            if (!agentName.isNullOrBlank()) {
                                val tagColor = agentColor(agentName, agents)
                                Surface(
                                    shape = ShapeTokens.smallMedium,
                                    color = tagColor.copy(alpha = AlphaTokens.FAINT)
                                ) {
                                    Text(
                                        text = agentName.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = tagColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            CopyButton(
                                text = copyText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }


                }

                // Fallback footer: no StepFinish but still show Agent Tag + time
                if (stepFinishes.isEmpty() && isTurnLast && !agentName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(if (compact) SpacingTokens.XS.dp else SpacingTokens.SM.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 时间
                        assistantMsg?.time?.created?.let { createdMs ->
                            val timeText = remember(createdMs) {
                                SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(createdMs))
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                            )
                        }
                        // Agent name tag
                        val tagColor = agentColor(agentName, agents)
                        Surface(
                            shape = ShapeTokens.smallMedium,
                            color = tagColor.copy(alpha = AlphaTokens.FAINT)
                        ) {
                            Text(
                                text = agentName.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = tagColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (copyText != null) {
                            CopyButton(
                                text = copyText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Error display
                if (renderableTurn.errorText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = AlphaTokens.FAINT),
                        shape = ShapeTokens.mediumSmall,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isAmoled) AlphaTokens.HIGH else AlphaTokens.FAINT)),
                        tonalElevation = 0.dp,
                    ) {
                        ErrorPayloadContent(
                            text = renderableTurn.errorText,
                            textStyle = MaterialTheme.typography.bodySmall,
                            textColor = textColor,
                            modifier = Modifier.padding(horizontal = SpacingTokens.MD.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
