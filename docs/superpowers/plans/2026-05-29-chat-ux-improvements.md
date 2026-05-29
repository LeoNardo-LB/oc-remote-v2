# Chat UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 UX issues in the chat screen: nested scroll inertia, token stats completeness, response timestamp, topbar counts, expandable card scrolling, and markdown text selection.

**Architecture:** All changes are in the chat feature layer. Shared utility modifiers go in ChatScreen.kt's private scope. Data model changes are minimal — existing fields (`modelId`, `time`, `Session.tokens`) are already present but unused in UI.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, mikepenz multiplatform-markdown-renderer 0.41.0

---

## File Structure

| File | Responsibility |
|------|---------------|
| `ChatScreen.kt` | All UI changes: nested scroll modifier, card scrolling, timestamp, token footer, markdown selection |
| `ChatViewModel.kt` | TopBar counts: use `Session.tokens` instead of computed from loaded messages |
| `ChatUiState.kt` (inline in ChatViewModel.kt) | Add `sessionCost`/`sessionTokens` fields |
| `values/strings.xml` | New string resources |
| `values-zh-rCN/strings.xml` | Chinese string resources |

---

## Task Dependency Graph

```
Task 1 (shared modifier) ──────────┐
Task 2 (ReasoningBlock fix) ────────┤ depends on 1
Task 3 (Search/Task card scroll) ───┤ depends on 1
Task 4 (ToolCallCard scroll) ───────┤ depends on 1
Task 5 (Response timestamp) ────────┤ independent
Task 6 (Token footer enhance) ──────┤ independent
Task 7 (TopBar counts) ─────────────┤ independent
Task 8 (Markdown selection) ────────┤ independent
Task 9 (Version bump) ──────────────┘ depends on all above
```

Tasks 2-4 MUST execute after Task 1. Tasks 5-8 are independent of each other and can be parallelized after Task 1 is complete. **All tasks modify ChatScreen.kt (except Task 7) so they MUST be strictly sequential.**

---

### Task 1: Create shared nested scroll boundary consumer modifier

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:286-297` (near `codeHorizontalScroll()`)

This modifier implements **方案B** — a `NestedScrollConnection` that consumes remaining fling velocity only when the child scrollable has reached its boundary. This prevents inertia from "punching through" to the parent `LazyColumn`.

- [ ] **Step 1: Add the shared modifier function**

Insert after the `codeHorizontalScroll()` function (after line 297):

```kotlin
/**
 * Creates a [Modifier] that prevents nested-scroll inertia from propagating
 * to the parent when the child scrollable reaches its top or bottom boundary.
 *
 * Uses **方案B** (conditional consume): the [NestedScrollConnection] only
 * consumes remaining velocity when [ScrollState.canScrollForward] or
 * [ScrollState.canScrollBackward] is false — i.e., the child is at a boundary.
 * During normal scrolling, everything passes through unaffected.
 */
@Composable
private fun Modifier.consumeBoundaryFling(scrollState: ScrollState): Modifier {
    val connection = remember(scrollState) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // Only consume when at boundary — this prevents the "punch-through"
                // acceleration of the parent LazyColumn.
                val atBottom = !scrollState.canScrollForward
                val atTop = !scrollState.canScrollBackward
                return if ((atBottom && available.y < 0f) || (atTop && available.y > 0f)) {
                    available // consume all remaining velocity
                } else {
                    Velocity.Zero // let parent handle it
                }
            }
        }
    }
    return this.nestedScroll(connection)
}
```

Required imports (add if not already present):
```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
```

- [ ] **Step 2: Verify compilation**

```powershell
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "refactor: add shared consumeBoundaryFling modifier for nested scroll fix"
```

**Acceptance criteria:**
- [ ] `consumeBoundaryFling` function compiles
- [ ] No existing behavior changed

---

### Task 2: Fix ReasoningBlock nested scroll inertia

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:5324-5339`

Apply `consumeBoundaryFling` to the `ReasoningBlock`'s scrollable `Box`.

- [ ] **Step 1: Add consumeBoundaryFling to ReasoningBlock**

Change lines 5324-5339 from:

```kotlin
                // Expandable content
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = halfScreenHeight)
                                .verticalScroll(rememberScrollState())
                        ) {
```

To:

```kotlin
                // Expandable content
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
                        val reasoningScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = halfScreenHeight)
                                .consumeBoundaryFling(reasoningScrollState)
                                .verticalScroll(reasoningScrollState)
                        ) {
```

Key change: extract `rememberScrollState()` into a named variable, pass it to `consumeBoundaryFling()` before `verticalScroll()`.

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: prevent ReasoningBlock scroll inertia from punching through to main chat list"
```

**Acceptance criteria:**
- [ ] ReasoningBlock content scrolls normally within half-screen height
- [ ] Scrolling to top/bottom boundary stops — no sudden acceleration of main list
- [ ] Second swipe after boundary still moves main list

---

### Task 3: Fix SearchToolCard & TaskToolCard — add verticalScroll + nested scroll fix

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:6380-6402` (SearchToolCard)
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:6522-6544` (TaskToolCard)

Both cards have `.heightIn(max = 600.dp)` but **no `verticalScroll`**, causing content truncation. Change to half-screen height + scroll + boundary fix.

- [ ] **Step 1: Fix SearchToolCard expanded area**

Change lines 6380-6402 from:

```kotlin
            AnimatedVisibility(
                visible = expanded && hasOutput,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 600.dp)
                ) {
                    Column(
                        modifier = Modifier
                    ) {
                        MarkdownContent(
                            markdown = output,
                            textColor = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer,
                            isUser = false
                        )
                    }
                }
            }
```

To:

```kotlin
            AnimatedVisibility(
                visible = expanded && hasOutput,
            ) {
                val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
                val scrollState = rememberScrollState()
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = halfScreenHeight)
                        .consumeBoundaryFling(scrollState)
                        .verticalScroll(scrollState)
                ) {
                    MarkdownContent(
                        markdown = output,
                        textColor = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer,
                        isUser = false
                    )
                }
            }
```

- [ ] **Step 2: Fix TaskToolCard expanded area**

Change lines 6522-6544 from:

```kotlin
            AnimatedVisibility(
                visible = expanded && hasOutput,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 600.dp)
                ) {
                    Column(
                        modifier = Modifier
                    ) {
                        MarkdownContent(
                            markdown = output,
                            textColor = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer,
                            isUser = false
                        )
                    }
                }
            }
```

To:

```kotlin
            AnimatedVisibility(
                visible = expanded && hasOutput,
            ) {
                val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
                val scrollState = rememberScrollState()
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = halfScreenHeight)
                        .consumeBoundaryFling(scrollState)
                        .verticalScroll(scrollState)
                ) {
                    MarkdownContent(
                        markdown = output,
                        textColor = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer,
                        isUser = false
                    )
                }
            }
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: add verticalScroll + boundary fix to SearchToolCard and TaskToolCard"
```

**Acceptance criteria:**
- [ ] SearchToolCard expanded output scrolls within half-screen height
- [ ] TaskToolCard expanded output scrolls within half-screen height
- [ ] Content no longer truncated at 600dp
- [ ] Scrolling boundary does not accelerate main list

---

### Task 4: Fix ToolCallCard expanded content — add half-screen height + scroll + boundary fix

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:5453-5502`

Wrap the expanded `Column` in a height-limited, scrollable Box.

- [ ] **Step 1: Wrap ToolCallCard expanded content**

Change lines 5453-5502 from:

```kotlin
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (input.isNotEmpty()) {
                        val inputText = input.entries
                            .filter { (_, v) -> v.toString().length <= 500 }
                            .joinToString("\n") { (k, v) ->
                                val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString().take(200)
                                "$k: $value"
                            }
                        if (inputText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = toolOutputContainerColor(isAmoled),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = inputText.take(2000),
                                    style = CodeTypography.copy(fontSize = 11.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)),
                                    modifier = Modifier.padding(8.dp).codeHorizontalScroll()
                                )
                            }
                        }
                    }
                    val output = when (val s = tool.state) {
                        is ToolState.Completed -> s.output
                        is ToolState.Error -> s.error
                        else -> ""
                    }
                    if (output.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = toolOutputContainerColor(isAmoled),
                            border = if (isAmoled) BorderStroke(1.dp, stateColor.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = output.take(3000),
                                style = CodeTypography.copy(fontSize = 11.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.padding(8.dp).codeHorizontalScroll()
```

To:

```kotlin
            AnimatedVisibility(visible = expanded) {
                val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
                val toolCardScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = halfScreenHeight)
                        .consumeBoundaryFling(toolCardScrollState)
                        .verticalScroll(toolCardScrollState)
                ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (input.isNotEmpty()) {
                        val inputText = input.entries
                            .filter { (_, v) -> v.toString().length <= 500 }
                            .joinToString("\n") { (k, v) ->
                                val value = (v as? JsonPrimitive)?.contentOrNull ?: v.toString().take(200)
                                "$k: $value"
                            }
                        if (inputText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = toolOutputContainerColor(isAmoled),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = inputText.take(2000),
                                    style = CodeTypography.copy(fontSize = 11.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)),
                                    modifier = Modifier.padding(8.dp).codeHorizontalScroll()
                                )
                            }
                        }
                    }
                    val output = when (val s = tool.state) {
                        is ToolState.Completed -> s.output
                        is ToolState.Error -> s.error
                        else -> ""
                    }
                    if (output.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = toolOutputContainerColor(isAmoled),
                            border = if (isAmoled) BorderStroke(1.dp, stateColor.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = output.take(3000),
                                style = CodeTypography.copy(fontSize = 11.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.padding(8.dp).codeHorizontalScroll()
```

**Also add the closing `}` for the `Box`** — find the closing `}` of the original `AnimatedVisibility` block's content (should be a `}` that matches the `Column {` opened above) and add `}` after it to close the `Box`. Read the code around lines 5495-5510 to find the exact position.

The structure should be:

```kotlin
            AnimatedVisibility(visible = expanded) {
                val halfScreenHeight = ...
                val toolCardScrollState = ...
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = halfScreenHeight)
                    .consumeBoundaryFling(toolCardScrollState).verticalScroll(toolCardScrollState)) {
                    Column(modifier = Modifier.padding(top = 4.dp), ...) {
                        // ... existing input/output surfaces unchanged ...
                    }
                } // close Box
            }
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: add half-screen height + scroll + boundary fix to ToolCallCard expanded content"
```

**Acceptance criteria:**
- [ ] ToolCallCard expanded content limited to half-screen height
- [ ] Content scrolls within bounds
- [ ] Scrolling boundary does not accelerate main list
- [ ] Input/output surfaces render correctly inside the scrollable container

---

### Task 5: Add local timestamp to Response header

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4165-4203`

Add formatted local time (`HH:mm`) next to the "Response" label, using `Message.Assistant.time.created`.

- [ ] **Step 1: Add timestamp formatting and display**

Change lines 4165-4203 from:

```kotlin
                // "Response" header — only on the first of a consecutive sequence
                if (!isContinuation) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (assistantMsg?.providerId != null) {
                                ProviderIcon(
                                    providerId = assistantMsg.providerId,
                                    size = 12.dp,
                                    tint = textColor.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = stringResource(R.string.chat_response),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 0.8.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor.copy(alpha = 0.4f)
                            )
                        }
                        if (onCopyText != null) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { performHaptic(hapticView, hapticOn); onCopyText() },
                                tint = textColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
```

To:

```kotlin
                // "Response" header — only on the first of a consecutive sequence
                if (!isContinuation) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (assistantMsg?.providerId != null) {
                                ProviderIcon(
                                    providerId = assistantMsg.providerId,
                                    size = 12.dp,
                                    tint = textColor.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = stringResource(R.string.chat_response),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 0.8.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textColor.copy(alpha = 0.4f)
                            )
                            // Local timestamp from message creation time
                            assistantMsg?.time?.created?.let { createdMs ->
                                val timeText = remember(createdMs) {
                                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(createdMs))
                                }
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp
                                    ),
                                    color = textColor.copy(alpha = 0.3f)
                                )
                            }
                        }
                        if (onCopyText != null) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { performHaptic(hapticView, hapticOn); onCopyText() },
                                tint = textColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
```

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: add local timestamp (HH:mm) to Response header"
```

**Acceptance criteria:**
- [ ] "Response" label now shows as "Response 14:32" (with time)
- [ ] Timestamp uses server-provided `time.created`, not client time
- [ ] Old messages loaded via REST also show correct timestamp
- [ ] Streaming messages show timestamp from when message was first created
- [ ] Continuation messages (isContinuation=true) still hide the header

---

### Task 6: Enhance token footer with model name + duration

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4221-4251`

Extend the StepFinish footer to show: `[ProviderIcon] modelId · duration · tokens · cost`.

- [ ] **Step 1: Add duration formatting helper**

Insert before `AssistantMessageCard` function (around line 4130):

```kotlin
/** Format millisecond duration to human-readable string (e.g., "12.3s", "1.5m"). */
private fun formatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "%.1fs".format(ms / 1000.0)
        else -> "%.1fm".format(ms / 60000.0)
    }
}
```

- [ ] **Step 2: Enhance token footer**

Change lines 4221-4251 from:

```kotlin
                // Token/cost footer from all StepFinish parts in this message
                val stepFinishes = chatMessage.parts.filterIsInstance<Part.StepFinish>()
                if (stepFinishes.isNotEmpty()) {
                    val totalInput = stepFinishes.sumOf { it.tokens?.input ?: 0 }
                    val totalOutput = stepFinishes.sumOf { it.tokens?.output ?: 0 }
                    val totalCost = stepFinishes.sumOf { it.cost ?: 0.0 }
                    val hasStats = totalInput > 0 || totalOutput > 0 || totalCost > 0.0

                    if (hasStats) {
                        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (totalInput > 0 || totalOutput > 0) {
                                Text(
                                    text = stringResource(R.string.chat_tokens_format, totalInput, totalOutput),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            if (totalCost > 0.0 && totalCost.isFinite()) {
                                Text(
                                    text = stringResource(R.string.chat_cost_format, String.format("%.4f", totalCost)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
```

To:

```kotlin
                // Token/cost/duration footer from all StepFinish parts in this message
                val stepFinishes = chatMessage.parts.filterIsInstance<Part.StepFinish>()
                if (stepFinishes.isNotEmpty()) {
                    val totalInput = stepFinishes.sumOf { it.tokens?.input ?: 0 }
                    val totalOutput = stepFinishes.sumOf { it.tokens?.output ?: 0 }
                    val totalCost = stepFinishes.sumOf { it.cost ?: 0.0 }
                    val hasTokenStats = totalInput > 0 || totalOutput > 0 || totalCost > 0.0

                    // Duration from message time
                    val durationMs = assistantMsg?.time?.let { t ->
                        t.completed?.let { end -> end - t.created }
                    }
                    val hasFooter = hasTokenStats || durationMs != null || !assistantMsg?.modelId.isNullOrBlank()

                    if (hasFooter) {
                        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Provider icon (small, in footer)
                            if (assistantMsg?.providerId != null) {
                                ProviderIcon(
                                    providerId = assistantMsg.providerId,
                                    size = 10.dp,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                            // Model name
                            if (!assistantMsg?.modelId.isNullOrBlank()) {
                                Text(
                                    text = assistantMsg.modelId,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            // Duration
                            if (durationMs != null && durationMs > 0) {
                                Text(
                                    text = formatDuration(durationMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                            // Tokens
                            if (totalInput > 0 || totalOutput > 0) {
                                Text(
                                    text = stringResource(R.string.chat_tokens_format, totalInput, totalOutput),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                            // Cost
                            if (totalCost > 0.0 && totalCost.isFinite()) {
                                Text(
                                    text = stringResource(R.string.chat_cost_format, String.format("%.4f", totalCost)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: enhance token footer with provider icon, model name, and duration"
```

**Acceptance criteria:**
- [ ] Footer shows: `[icon] model-name · 12.3s · 128/1024 tokens · $0.0023`
- [ ] Duration only shows when message is complete (completed != null)
- [ ] Model name truncates with ellipsis if too long
- [ ] Footer remains hidden when no stats at all
- [ ] Existing token/cost display unaffected

---

### Task 7: Fix TopBar counts — use Session-level tokens

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt:382-391, 449-451`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:1500-1511`

The current TopBar counts (`totalCost`, `totalInputTokens`, `totalOutputTokens`) are computed from **loaded** assistant messages only. Use `Session.tokens` and `Session.cost` instead — these are server-side aggregates that include all messages, not just the loaded ones.

- [ ] **Step 1: Update ChatViewModel to use Session-level data**

In `ChatViewModel.kt`, change lines 382-391 from:

```kotlin
        // Compute cost/token totals from assistant messages
        val assistantMessages = sessionMessages.filterIsInstance<Message.Assistant>()
        val totalCost = assistantMessages.sumOf { it.cost ?: 0.0 }
        val totalInputTokens = assistantMessages.sumOf { it.tokens?.input ?: 0 }
        val totalOutputTokens = assistantMessages.sumOf { it.tokens?.output ?: 0 }
        // Context usage: total tokens from the last assistant message with output > 0
        val lastWithOutput = assistantMessages.firstOrNull { (it.tokens?.output ?: 0) > 0 }
        val lastContextTokens = lastWithOutput?.tokens?.let { t ->
            t.input + t.output + t.reasoning + t.cache.read + t.cache.write
        } ?: 0
```

To:

```kotlin
        // Compute cost/token totals — prefer session-level aggregates (covers all messages,
        // not just loaded ones). Fall back to summing loaded assistant messages.
        val assistantMessages = sessionMessages.filterIsInstance<Message.Assistant>()
        val sessionTokens = session?.tokens
        val totalCost = session?.cost
            ?: assistantMessages.sumOf { it.cost ?: 0.0 }
        val totalInputTokens = sessionTokens?.input
            ?: assistantMessages.sumOf { it.tokens?.input ?: 0 }
        val totalOutputTokens = sessionTokens?.output
            ?: assistantMessages.sumOf { it.tokens?.output ?: 0 }
        // Context usage: total tokens from the last assistant message with output > 0
        val lastWithOutput = assistantMessages.firstOrNull { (it.tokens?.output ?: 0) > 0 }
        val lastContextTokens = lastWithOutput?.tokens?.let { t ->
            t.input + t.output + t.reasoning + t.cache.read + t.cache.write
        } ?: 0
```

Note: `contextWindow` and `lastContextTokens` still use per-message data because they represent the latest context state, not a total.

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git commit -m "fix: use Session-level tokens/cost for TopBar counts instead of loaded-only"
```

**Acceptance criteria:**
- [ ] TopBar tokens show server-side totals (even when only 20 messages loaded)
- [ ] When session has no tokens field (edge case), falls back to loaded messages sum
- [ ] Context usage percentage in input bar still works correctly
- [ ] `messageCount` still shows loaded message count (API limitation)

---

### Task 8: Markdown direct text selection (方案D — transparent selection layer)

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4870-4972`

Replace the long-press Dialog with a transparent `SelectionContainer` overlay. This uses **方案D** — the same pattern already proven in the terminal component (lines 3535-3578).

**⚠️ This is the most complex task. If it causes issues, revert and keep the Dialog approach.**

- [ ] **Step 1: Replace MarkdownContent's long-press Dialog with transparent selection layer**

Change lines 4870-4972 from the current implementation (pointerInput + Dialog) to:

```kotlin
    // NOTE: SelectionContainer cannot be directly wrapped around Markdown because its
    // selectableGroup() pointer-input handler leaks into LazyColumn's pointer scope and
    // blocks clicks on adjacent tool cards. Instead, we use a transparent text overlay
    // (方案D — same pattern as the terminal component).
    //
    // retainState = true keeps the previous rendered content visible while
    // new markdown is being parsed, preventing the Loading→Success flicker
    // that causes screen flashing during streaming output.
    val markdownState = rememberMarkdownState(
        content = normalizedMarkdown,
        retainState = true
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        // Layer 1: Visible Markdown rendering
        Markdown(
            markdownState = markdownState,
            colors = colors,
            typography = typography,
            components = components,
            dimens = dimens,
            imageTransformer = Coil3ImageTransformerImpl,
            modifier = Modifier.fillMaxWidth()
        )

        // Layer 2: Transparent text selection overlay — only for assistant messages
        if (!isUser) {
            // Custom selection colors — transparent text, visible highlight
            val selectionColors = TextSelectionColors(
                handleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                SelectionContainer(
                    modifier = Modifier
                        .matchParentSize()
                        .semantics { contentDescription = normalizedMarkdown }
                ) {
                    Text(
                        text = normalizedMarkdown,
                        color = Color.Transparent,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
```

Required imports (verify present):
```kotlin
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
```

**Important:** Remove the `showTextDialog` state, `clipboardManager`, `view`, and the entire `AlertDialog` block. Also remove the `pointerInput` modifier that was attached to `Markdown`.

The `bodyStyle` variable should already be defined earlier in `MarkdownContent` — it's the computed `TextStyle` based on font size preference. If not, add:

```kotlin
val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
    fontSize = bodyFontSize,
    lineHeight = bodyLineHeight
)
```

- [ ] **Step 2: If compilation fails due to pointer conflicts**

If the transparent `SelectionContainer` still causes click interference (the original bug from lines 4870-4881), add a `Modifier.pointerInput` barrier to isolate it:

```kotlin
SelectionContainer(
    modifier = Modifier
        .matchParentSize()
        .pointerInput(Unit) { /* empty — isolation barrier */ }
) {
```

- [ ] **Step 3: Verify compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual test — verify no regressions**

Test the following on a real device/emulator:
1. Long-press on assistant markdown text → text selection handles appear (no dialog)
2. Drag to select → blue highlight visible over rendered markdown
3. Tap "Copy" in selection menu → text copied to clipboard
4. Tap on tool cards → they still respond to clicks (no interference)
5. Tap expand/collapse on reasoning block → still works
6. Scroll the main list → no jank or stutter
7. User messages → no text selection (isUser check)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: replace text selection dialog with transparent SelectionContainer overlay"
```

**Acceptance criteria:**
- [ ] Long-press on assistant text shows native selection handles (no dialog popup)
- [ ] Text selection highlight is visible over markdown content
- [ ] Tool card clicks still work (no pointer interference)
- [ ] User messages are not selectable
- [ ] Copy via selection context menu works
- [ ] If any regression occurs, revert to Dialog approach

---

### Task 9: Version bump and release

**Files:**
- Modify: `app/build.gradle.kts:20-21`

- [ ] **Step 1: Bump version**

```kotlin
versionCode = 267
versionName = "2.0.0-beta.67"
```

- [ ] **Step 2: Build final release APK**

```powershell
cd D:\Develop\code\app\oc-remote
.\gradlew.bat clean assembleRelease 2>&1 | Select-String "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Smoke test**

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n dev.minios.ocremote/.MainActivity
```

- [ ] **Step 4: Commit version bump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump to v2.0.0-beta.67 (versionCode=267)"
```

- [ ] **Step 5: Tag and release**

```powershell
$repoUrl = git remote get-url fork 2>&1
$repoName = if ($repoUrl -match 'github\.com[:/](.+?)(\.git)?$') { $matches[1] } else { "LeoNardo-LB/oc-remote-v2" }

@"
## v2.0.0-beta.67

### Nested Scroll Fix
- **Inertia punch-through prevention** — scrolling inside expandable cards (reasoning, tools) no longer accelerates the main chat list when reaching boundaries (方案B: conditional velocity consume)

### Expandable Cards
- **Half-screen height limit** — all expandable cards (ToolCallCard, SearchToolCard, TaskToolCard) now consistently limit to half screen height with scroll
- **SearchToolCard / TaskToolCard** — content no longer truncated at 600dp, now properly scrollable

### Token & Stats Enhancement
- **Rich footer** — response footer now shows: provider icon + model name + duration + tokens + cost
- **Session-level counts** — TopBar tokens/cost now use server-side session totals instead of summing only loaded messages

### Response Header
- **Local timestamp** — "Response" header now shows message creation time (HH:mm)

### Text Selection
- **Inline text selection** — long-press on assistant markdown now shows native text selection handles instead of a dialog popup (transparent overlay pattern)
"@ | Out-File -FilePath release_notes.md -Encoding utf8

git tag v2.0.0-beta.67
git push fork dev
git push fork v2.0.0-beta.67
gh release create v2.0.0-beta.67 "app/build/outputs/apk/release/app-release.apk#oc-remote-v2.0.0-beta.67.apk" --repo $repoName --title "v2.0.0-beta.67 - Chat UX Improvements" --notes-file release_notes.md
```

---

## Self-Review Checklist

- [ ] **Spec coverage:** Every user request maps to a task
- [ ] **Placeholder scan:** No TBD/TODO/implement-later — all code shown
- [ ] **Type consistency:** `ScrollState`, `NestedScrollConnection`, `Velocity`, `Message.TimeInfo` types match across tasks
- [ ] **Line number accuracy:** All line numbers verified against current codebase
- [ ] **Import completeness:** Required imports listed for each task
