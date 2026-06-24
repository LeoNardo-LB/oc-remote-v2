package dev.leonardo.ocremotev2.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.domain.model.SseEvent
import dev.leonardo.ocremotev2.domain.model.ToolState
import dev.leonardo.ocremotev2.ui.theme.AlphaTokens
import dev.leonardo.ocremotev2.ui.theme.SpacingTokens
import dev.leonardo.ocremotev2.ui.screens.chat.dialog.QuestionCard
import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.ToolCardScaffold
import dev.leonardo.ocremotev2.ui.screens.chat.util.isAmoledTheme
import dev.leonardo.ocremotev2.ui.theme.ShapeTokens
import androidx.compose.foundation.text.selection.SelectionContainer
import dev.leonardo.ocremotev2.ui.screens.chat.markdown.MarkdownContent
import dev.leonardo.ocremotev2.ui.screens.chat.tools.ToolCallCard
import dev.leonardo.ocremotev2.ui.screens.chat.tools.ViewToolRequest
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnViewTool
import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav
import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.PatchCard
import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.TodoListCard
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalCollapseTools
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalToolCardResolver
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalExpandReasoning
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalHapticFeedbackEnabled
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnToggleToolExpanded
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalToolExpandedStates
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun PartContent(
    part: Part,
    textColor: Color,
    isUser: Boolean = false,
    onViewSubSession: ((String) -> Unit)? = null,
    turnAgentName: String? = null,
    onOpenFile: ((filePath: String) -> Unit)? = null,
    onViewTool: ((ViewToolRequest) -> Unit)? = null
) {
    when (part) {
        is Part.Text -> {
            // Hide synthetic/ignored text parts (internal system content)
            if (part.text.isNotBlank() && part.synthetic != true && part.ignored != true) {
                // opencode may send question+answer as a text part (not structured Part.Question)
                // Detect this format and render as collapsible card
                if (part.text.contains("questions:") && part.text.contains("User has answered")) {
                    CollapsibleQuestionPart(question = part.text)
                } else {
                    SelectionContainer {
                        MarkdownContent(
                            markdown = part.text,
                            textColor = textColor,
                            isUser = isUser,
                            immediate = !isUser  // assistant messages: synchronous to avoid height jump during streaming
                        )
                    }
                }
            }
        }
        is Part.Reasoning -> {
            if (part.text.isNotBlank()) {
                val isStreaming = part.time?.end == null
                val startTimeMs = part.time?.start
                val reasoningDuration = part.time?.let { t ->
                    t.end?.let { end -> end - t.start }
                }
                val toolExpandedStates = LocalToolExpandedStates.current
                val onToggleToolExpanded = LocalOnToggleToolExpanded.current
                val expandReasoningDefault = LocalExpandReasoning.current
                ReasoningBlock(
                    text = part.text,
                    isExpanded = toolExpandedStates[part.id] ?: expandReasoningDefault,
                    onToggleExpand = { onToggleToolExpanded(part.id, expandReasoningDefault) },
                    durationMs = reasoningDuration,
                    isStreaming = isStreaming,
                    startTimeMs = startTimeMs
                )
            }
        }
        is Part.Tool -> {
            // todoread parts are filtered out entirely (WebUI convention)
            val toolExpandedStates = LocalToolExpandedStates.current
            val onToggleToolExpanded = LocalOnToggleToolExpanded.current
            if (part.tool == "todoread") {
                // skip
            } else if (part.tool == "todowrite") {
                TodoListCard(
                    tool = part,
                    isExpanded = toolExpandedStates[part.id] ?: true,
                    onToggleExpand = { onToggleToolExpanded(part.id, true) }
                )
            } else {
                // Intercept question-summary tools — keep ToolCardScaffold appearance,
                // but expanded content shows ALL options with user's selection marked.
                val completedState = part.state as? ToolState.Completed
                val toolInput = completedState?.input ?: emptyMap()
                val toolOutput = completedState?.output ?: ""
                android.util.Log.e("PartContent", "TOOL ELSE: tool=${part.tool} state=${part.state::class.simpleName} outputLen=${toolOutput.length} outputHead=${toolOutput.take(200)}")
                val isQuestionTool = toolOutput.contains("questions:")
                    || toolInput.any { it.key.contains("question", ignoreCase = true) }
                android.util.Log.e("PartContent", "isQuestionTool=$isQuestionTool inputKeys=${toolInput.keys}")
                if (isQuestionTool) {
                    // Debug: log full tool data to find where answers live
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "=== tool data ===")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "input keys: ${toolInput.keys}")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "input: $toolInput")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "output: $toolOutput")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "metadata: ${completedState?.metadata}")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "title: ${completedState?.title}")
                    dev.leonardo.ocremotev2.util.DebugLogger.log("QuestionTool", "tool name: ${part.tool}")
                    val parsed = remember(part.id) {
                        parseQuestionFromToolData(part.id, toolInput, toolOutput)
                    }
                    val autoExpand = LocalCollapseTools.current
                    ToolCardScaffold(
                        icon = Icons.Default.HelpOutline,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = completedState?.title ?: "Asked",
                        copyText = toolOutput,
                        isExpanded = toolExpandedStates[part.id] ?: autoExpand,
                        isRunning = false,
                        hasContent = parsed.any { it.options.isNotEmpty() },
                        isAmoled = isAmoledTheme(),
                        onToggleExpand = { onToggleToolExpanded(part.id, autoExpand) },
                    ) {
                        QuestionExpandedOptions(parsed)
                    }
                } else {
                // Use the resolver registry
                val autoExpand = LocalCollapseTools.current
                val expanded = toolExpandedStates[part.id] ?: autoExpand
                val toggleExpand = { onToggleToolExpanded(part.id, autoExpand) }

                // Phase 2: intercept onOpenFile for Read/Write/Edit → TOOL_SNAPSHOT
                val viewTool = onViewTool ?: LocalOnViewTool.current
                val toolName = part.tool.lowercase()
                val isFileTool = toolName in setOf("read", "write", "edit", "multiedit")
                val isDiffTool = toolName in setOf("edit", "multiedit")
                val effectiveOnOpenFile: ((String) -> Unit)? = if (viewTool != null && isFileTool) {
                    { filePath ->
                        val source = if (isDiffTool) FileViewerNav.Source.TOOL_SNAPSHOT_DIFF
                        else FileViewerNav.Source.TOOL_SNAPSHOT
                        viewTool(ViewToolRequest(filePath, source, part))
                    }
                } else onOpenFile

                val resolved = LocalToolCardResolver.current.resolve(
                    tool = part,
                    isExpanded = expanded,
                    onToggleExpand = toggleExpand,
                    onViewSubSession = onViewSubSession,
                    turnAgentName = turnAgentName,
                    onOpenFile = effectiveOnOpenFile
                )

                if (resolved != null) {
                    resolved()
                } else {
                    // Fallback to generic ToolCallCard
                    ToolCallCard(
                        tool = part,
                        isExpanded = expanded,
                        onToggleExpand = toggleExpand
                    )
                }
                } // close question-summary else
            }
        }
        is Part.StepStart -> {
            // Visual separator between steps (hidden - WebUI doesn't show these)
        }
        is Part.StepFinish -> {
            // Token/cost info is aggregated at the bottom of the assistant message
        }
        is Part.Patch -> {
            val autoExpand = LocalCollapseTools.current
            val toolExpandedStates = LocalToolExpandedStates.current
            val onToggleToolExpanded = LocalOnToggleToolExpanded.current
            PatchCard(
                patch = part,
                isExpanded = toolExpandedStates[part.id] ?: autoExpand,
                onToggleExpand = { onToggleToolExpanded(part.id, autoExpand) },
                onOpenFile = onOpenFile
            )
        }
        is Part.File -> {
            FileCard(file = part)
        }
        is Part.Permission -> {
            Text(
                text = stringResource(R.string.chat_permission_label, part.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        is Part.Question -> {
            CollapsibleQuestionPart(question = part.question)
        }
        is Part.Abort -> {
            Text(
                text = stringResource(R.string.chat_aborted, part.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        is Part.Retry -> {
            Text(
                text = stringResource(R.string.chat_retry, part.attempt, part.errorMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        // Ignore less relevant parts
        is Part.Snapshot, is Part.Subtask, is Part.Compaction,
        is Part.SessionTurn, is Part.Unknown -> { /* skip */ }
        is Part.Agent -> {
            val displayName = part.name.ifBlank { "Agent" }
            val displaySource = part.source?.jsonPrimitive?.contentOrNull ?: ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = stringResource(R.string.a11y_icon_select_provider),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.MEDIUM)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (displaySource.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displaySource,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.FAINT)
                    )
                }
            }
        }
    }
}

/**
 * Collapsible card for historical Part.Question.
 * Header: [?] "提问" + question summary.
 * Expanded: full question text + user's selected answer (if available).
 *
 * The question field from opencode may be plain text or contain structured
 * JSON with question + answers. This composable handles both cases.
 */
@Composable
private fun CollapsibleQuestionPart(question: String) {
    var expanded by remember { mutableStateOf(false) }
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    // Parse question: plain text or embedded JSON with answer info
    val parsed = remember(question) {
        parseQuestionContent(question)
    }

    androidx.compose.material3.Surface(
        shape = dev.leonardo.ocremotev2.ui.theme.ShapeTokens.smallMedium,
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
                Text(
                    text = "提问",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = parsed.displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor.copy(alpha = AlphaTokens.FAINT)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = parsed.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                    // Show user's answer if available
                    parsed.answers.forEach { answer ->
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = accentColor.copy(alpha = AlphaTokens.SELECTED),
                            shape = androidx.compose.material3.MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = accentColor
                                )
                                Text(
                                    text = answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentColor
                                )
                            }
                        }
                    }
                    // Show raw content if JSON parse found extra fields
                    if (parsed.rawExtra.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = parsed.rawExtra,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = AlphaTokens.MUTED)
                        )
                    }
                }
            }
        }
    }
}

/** Result of parsing a Part.Question question field. */
private data class ParsedQuestion(
    val displayText: String,
    val answers: List<String>,
    val rawExtra: String
)

/** Parse question field — handles plain text, JSON, and opencode text format. */
private fun parseQuestionContent(raw: String): ParsedQuestion {
    val trimmed = raw.trim()

    // Format 1: opencode text ("questions: [...]\nUser has answered: ...")
    if (trimmed.contains("questions:") || trimmed.contains("User has answered")) {
        val questionText = Regex("\"question\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)?.groupValues?.getOrNull(1)
            ?: trimmed.lines().firstOrNull { it.isNotBlank() && !it.startsWith("Asked") }
            ?: trimmed
        val answers = mutableListOf<String>()
        val answerSection = trimmed.substringAfter("User has answered", "")
        if (answerSection.isNotBlank()) {
            val quoted = Regex("\"([^\"]+)\"").findAll(answerSection).map { it.groupValues[1] }.toList()
            if (quoted.isNotEmpty()) answers.addAll(quoted)
            else {
                val plain = answerSection.removePrefix(":").removePrefix(" your questions:").trim()
                if (plain.isNotBlank()) answers.add(plain)
            }
        }
        return ParsedQuestion(displayText = questionText, answers = answers, rawExtra = "")
    }

    // Format 2: pure JSON
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return try {
            val json = org.json.JSONObject(trimmed)
            val q = json.optString("question", raw)
            val answers = mutableListOf<String>()
            json.optString("answer", "").takeIf { it.isNotBlank() }?.let { answers.add(it) }
            json.optJSONArray("answers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.get(i)
                    if (item is String) answers.add(item)
                    else if (item is org.json.JSONArray) {
                        for (j in 0 until item.length()) answers.add(item.getString(j))
                    }
                }
            }
            ParsedQuestion(displayText = q, answers = answers, rawExtra = "")
        } catch (e: Exception) {
            ParsedQuestion(displayText = raw, answers = emptyList(), rawExtra = "")
        }
    }

    // Format 3: plain text
    return ParsedQuestion(displayText = raw, answers = emptyList(), rawExtra = "")
}

// ============ Question History (from Tool input + output) ============

private data class QHistOption(val label: String, val description: String = "")
private data class QHistItem(val text: String, val options: List<QHistOption>, val answers: List<String>, val isMultiple: Boolean = false)

/**
 * Parse question data from tool input (has full options) and output (has user answers).
 * Input format: {"questions": [{"question": "...", "options": [{"label":"A",...}]}]}
 * Output format: "questions: [...]\nUser has answered: \"answer\""
 */
private fun parseQuestionFromToolData(
    id: String,
    input: Map<String, JsonElement>,
    output: String
): List<QHistItem> {
    val items = mutableListOf<QHistItem>()

    // 1. Extract ALL options from tool input (structured JSON with options array)
    val questionsElement = input.entries
        .firstOrNull { it.key.contains("question", ignoreCase = true) }
        ?.value
    if (questionsElement is kotlinx.serialization.json.JsonArray) {
        questionsElement.forEach { qEl ->
            val qObj = qEl.jsonObject
            val qText = qObj["question"]?.jsonPrimitive?.content ?: ""
            val optsArr = qObj["options"]?.jsonArray
            val opts = optsArr?.map { optEl ->
                val optObj = optEl.jsonObject
                QHistOption(
                    label = optObj["label"]?.jsonPrimitive?.content ?: optObj["value"]?.jsonPrimitive?.content ?: "",
                    description = optObj["description"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()
            items.add(QHistItem(qText, opts, emptyList()))
        }
    }

    // Fallback: parse from output if input has no questions
    if (items.isEmpty()) {
        val qSection = output.substringAfter("questions:", "").trim()
        val jsonPart = qSection.substringBefore("\nUser has answered").substringBefore("\nAsked").trim()
        try {
            val arr = org.json.JSONArray(jsonPart)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val qText = obj.optString("question", "")
                val optsArr = obj.optJSONArray("options")
                val opts = mutableListOf<QHistOption>()
                if (optsArr != null) {
                    for (j in 0 until optsArr.length()) {
                        val opt = optsArr.optJSONObject(j)
                        if (opt != null) opts.add(QHistOption(
                            opt.optString("label", opt.optString("value", "")),
                            opt.optString("description", "")
                        ))
                    }
                }
                items.add(QHistItem(qText, opts, emptyList()))
            }
        } catch (e: Exception) {
            items.add(QHistItem(output.lines().firstOrNull { it.isNotBlank() } ?: "", emptyList(), emptyList()))
        }
    }

    // 2. Extract user answers from output format: "question text"="answer1, answer2"
    val answerSection = output.substringAfter("User has answered", "")
        .substringBefore(". You can")
    val answerPairs = Regex("\"([^\"]+)\"=\"([^\"]+)\"").findAll(answerSection).toList()
    answerPairs.forEachIndexed { idx, match ->
        val answers = match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (idx < items.size) {
            items[idx] = items[idx].copy(answers = answers)
        }
    }
    // Fallback: if no "q"="a" pairs, try plain answers after last = sign
    if (answerPairs.isEmpty() && items.isNotEmpty()) {
        val afterEquals = answerSection.substringAfter("=", "").trim().trim('"')
        val fallbackAnswers = afterEquals.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (fallbackAnswers.isNotEmpty()) items[0] = items[0].copy(answers = fallbackAnswers)
    }

    return items
}

/** Renders question history via shared QuestionPagerView (read-only). */
@Composable
private fun QuestionExpandedOptions(items: List<QHistItem>) {
    val questions = items.map { item ->
        SseEvent.QuestionAsked.Question(
            header = "",
            question = item.text,
            multiple = item.isMultiple,
            options = item.options.map { SseEvent.QuestionAsked.Option(it.label, it.description) }
        )
    }
    val selected = items.map { it.answers.toSet() }
    QuestionPagerView(
        questions = questions,
        selectedAnswers = selected,
        readOnly = true
    )
}

/**
 * Unified question display: TabRow + HorizontalPager + Checkbox/RadioButton.
 * Shared by QuestionCard (interactive) and question history (read-only).
 */
@Composable
internal fun QuestionPagerView(
    questions: List<SseEvent.QuestionAsked.Question>,
    selectedAnswers: List<Set<String>>,
    readOnly: Boolean = false,
    onOptionClick: ((pageIndex: Int, label: String) -> Unit)? = null
) {
    if (questions.size <= 1) {
        questions.firstOrNull()?.let { q ->
            QuestionOptionRows(q, selectedAnswers.firstOrNull() ?: emptySet(), readOnly) { onOptionClick?.invoke(0, it) }
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { questions.size })
        val scope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        var maxPageHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        Column {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent) {
                questions.indices.forEach { i ->
                    Tab(selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        text = { Text("Q${i + 1}", style = MaterialTheme.typography.labelSmall) })
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().then(
                    if (maxPageHeight > 0) Modifier.height(with(density) { maxPageHeight.toDp() }) else Modifier
                ),
                beyondViewportPageCount = 1,
                pageSpacing = 8.dp,
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                Box(modifier = Modifier
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height
                        if (h > maxPageHeight) maxPageHeight = h
                    }
                    .graphicsLayer {
                        alpha = (1f - pageOffset * 0.3f).coerceIn(0.7f, 1f)
                        scaleX = 1f - pageOffset * 0.04f
                        scaleY = 1f - pageOffset * 0.04f
                    }
                ) {
                    QuestionOptionRows(questions[page], selectedAnswers.getOrNull(page) ?: emptySet(), readOnly)
                    { onOptionClick?.invoke(page, it) }
                }
            }
        }
    }
}

@Composable
private fun QuestionOptionRows(
    question: SseEvent.QuestionAsked.Question,
    selected: Set<String>,
    readOnly: Boolean,
    onOptionClick: (String) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val isMultiple = question.multiple
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.SM.dp)) {
        if (question.question.isNotBlank()) {
            Text(question.question, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
        question.options.forEach { option ->
            val isSelected = option.label in selected
            Surface(onClick = { onOptionClick(option.label) }, enabled = !readOnly,
                shape = ShapeTokens.small,
                color = if (isSelected) accentColor.copy(alpha = AlphaTokens.SELECTED) else MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.MEDIUM),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isMultiple) (if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank) else (if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked),
                        contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (isSelected) accentColor else accentColor.copy(alpha = AlphaTokens.MEDIUM))
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) accentColor else contentColor)
                        if (option.description.isNotBlank()) {
                            Text(option.description, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = AlphaTokens.MEDIUM))
                        }
                    }
                }
            }
        }
        // Custom answer support
        if (question.custom != false) {
            val optionLabels = question.options.map { it.label }.toSet()
            val customAnswer = selected.firstOrNull { it !in optionLabels }
            if (customAnswer != null) {
                Surface(onClick = {}, enabled = false, shape = ShapeTokens.small,
                    color = accentColor.copy(alpha = AlphaTokens.SELECTED), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isMultiple) Icons.Default.CheckBox else Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Text(customAnswer, style = MaterialTheme.typography.bodyMedium, color = accentColor, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!readOnly && customAnswer == null) {
                var isEditing by remember { mutableStateOf(false) }
                var customText by remember { mutableStateOf("") }
                if (!isEditing) {
                    Surface(onClick = { isEditing = true }, shape = ShapeTokens.small, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentColor.copy(alpha = AlphaTokens.MEDIUM))
                            Text("自定义输入", style = MaterialTheme.typography.bodySmall, color = accentColor.copy(alpha = AlphaTokens.MEDIUM))
                        }
                    }
                } else {
                    androidx.compose.material3.OutlinedTextField(
                        value = customText, onValueChange = { customText = it },
                        placeholder = { Text("输入答案", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall, shape = ShapeTokens.small,
                        trailingIcon = {
                            Row {
                                androidx.compose.material3.IconButton(onClick = {
                                    val t = customText.trim()
                                    if (t.isNotBlank()) { onOptionClick(t); isEditing = false; customText = "" }
                                }, enabled = customText.isNotBlank()) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                androidx.compose.material3.IconButton(onClick = { isEditing = false; customText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
