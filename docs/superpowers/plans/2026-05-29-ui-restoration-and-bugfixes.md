# UI Restoration and Bugfixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore 12 UI features and fix regressions in oc-remote Android chat interface, including reasoning animations, token/cost statistics, tool card consistency, keyboard behavior, Markdown newline rendering, and prevent future code loss.

**Architecture:** All changes are in `ChatScreen.kt` (8000+ lines) which contains the entire chat UI. Changes are isolated to specific composable functions: `ReasoningBlock`, `ReadToolCard`, `WriteToolCard`, `SearchToolCard`, `ToolCallCard`, `AssistantMessageCard`, `MarkdownContent`, `ChatInputBar`, and dead code removal (`StepFinishInfo`). Data for reasoning duration comes from `Part.Reasoning.time: Time(start, end)`. Token/cost data comes from `Part.StepFinish.tokens` and `Part.StepFinish.cost` (per-step basis, aggregated in `AssistantMessageCard`). No new files or architectural changes.

**Tech Stack:** JDK 17, Kotlin 2.3.21, AGP 9.2.1, Jetpack Compose (BOM 2026.05.01), Material 3, Ktor HTTP client, kotlinx.serialization, Gradle wrapper (9.5.1), compileSdk=36, minSdk=26, targetSdk=35

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt` (all changes)
- Modify: `app/src/main/res/values/strings.xml` (English strings — default)
- Modify: `app/src/main/res/values-zh-rCN/strings.xml` (Chinese strings)
- Modify: `app/build.gradle.kts` (version bump)
- Do NOT modify: `Part.kt`, `OpenCodeApi.kt`, `ChatViewModel.kt` (data models already support the features)

**String resource i18n note:** New string `chat_thinking_complete` is added to `values/strings.xml` (English default) and `values-zh-rCN/strings.xml` (Chinese). The remaining 13 language files (`values-ar/de/es/fr/id/it/ja/ko/pl/pt-rBR/ru/tr/uk`) will fall back to the English default automatically — this is expected behavior per Android resource resolution.

## Table of Contents

1. [Prerequisites & Editing Strategy](#prerequisites--editing-strategy)
2. [Task 1: Send button disabled state circle](#task-1-send-button-disabled-state-circle-already-implemented-commit-only)
3. [Task 2: ReasoningBlock — restore pulse animation](#task-2-reasoningblock--restore-pulse-animation--gradient-accent-line--surface-container)
4. [Task 3: Add token/cost statistics](#task-3-add-tokencost-statistics-to-assistant-messages)
5. [Task 4: Clean up StepFinishInfo dead code](#task-4-clean-up-stepfinishinfo-dead-code)
6. [Task 5: Read tool card — show file name](#task-5-read-tool-card--show-file-name)
7. [Task 6: Write tool card — show file name](#task-6-write-tool-card--show-file-name)
8. [Task 7: Fix keyboard auto-popup on scroll to bottom](#task-7-fix-keyboard-auto-popup-on-scroll-to-bottom)
9. [Task 8: Search tool card — fix title format](#task-8-search-tool-card--fix-title-format)
10. [Task 9: Add Surface wrapper to bare tool cards](#task-9-add-surface-wrapper-to-bare-tool-cards-toolcallcard--todolistcard--patchcard)
11. [Task 10: Fix Markdown single newline rendering](#task-10-fix-markdown-single-newline-rendering-for-user-messages)
12. [Task 11: Add text selection/copy for assistant messages](#task-11-add-text-selectioncopy-for-assistant-message-content)
13. [Task 12: Version bump and release](#task-12-version-bump-and-release)
14. [Task 13: Prevent future code loss](#task-13-prevent-future-code-loss--process-improvement)
15. [Task Dependency Graph](#task-dependency-graph)

---

## Prerequisites & Editing Strategy

### Pre-flight checks (run before starting)

```bash
# 1. Verify build tools
java -version 2>&1 | Select-String "17"
.\gradlew.bat --version 2>&1 | Select-String "Gradle 9"
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD"

# 2. Verify git state
git remote -v | Select-String "fork"
git status --porcelain  # Should be clean except for Task 1's working-tree change

# 3. Verify signing (required for release APK)
Test-Path "app/keystore/signing.properties"
# If NOT found: release APK will be unsigned. Ask user whether to create signing or skip release.

# 4. Verify adb device
adb devices  # Should show at least one device/emulator

# 5. Verify GitHub CLI
gh auth status 2>&1 | Select-String "Logged in"
```

### ⚠️ Editing Strategy: MANDATORY SEQUENTIAL

All 12 tasks modify `ChatScreen.kt`. **Do NOT parallelize.** Execute tasks in order (1→12) to avoid line-number shift conflicts. Use Edit tool with `oldString` text matching (not line numbers) for reliability.

**Before each edit:** Read the target section to confirm current state.
**After each task:** Run `.\gradlew.bat :app:compileDebugKotlin` for fast validation (≈30s), then commit.
**If compilation fails:** `git checkout -- ChatScreen.kt` to revert, re-read the file, adjust the edit, retry.

### Rollback protocol
```bash
# Revert to last clean state
git checkout -- ChatScreen.kt
git checkout -- app/src/main/res/values/strings.xml
git checkout -- app/src/main/res/values-zh-rCN/strings.xml
git checkout -- app/build.gradle.kts
```

---

### Task 1: Send button disabled state circle (already implemented, commit only)

**Files:**
- Modify: `ChatScreen.kt:7560-7607` (already changed in working tree)

**Current working tree fix:**
- Before: `Color.Transparent` background, no border when `!showStop && !(isShellMode && !isSending)` — button invisible in disabled state
- After: semi-transparent background (`surfaceVariant.copy(alpha = 0.3f)` on light, `Color.Black` on AMOLED) and thin border (`outlineVariant.copy(alpha = 0.35f)` on light, `outlineVariant.copy(alpha = 0.5f)` on AMOLED)

**Acceptance criteria:** 
- When input field is empty (canSend=false), the send button should show a visible circle with faint border
- AMOLED theme: black fill + white outline
- Light theme: light grey fill + subtle outline
- When text is entered (canSend=true), button returns to normal colored state

- [ ] **Step 1: Verify the fix compiles**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit the send button fix**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: restore send button circle in disabled state

Add semi-transparent background (surfaceVariant 0.3f alpha) and thin
outlineVariant border to send button when input is empty (disabled state).
Previously was transparent with no border, making the button invisible."
```

---

### Task 2: ReasoningBlock — restore pulse animation + gradient accent line + Surface container

**Files:**
- Modify: `ChatScreen.kt:5079-5157` (current ReasoningBlock)

**Current state (lines 5079-5157):** Plain `Box` with flat left accent line, no pulse animation, no Surface container, always shows "Thinking" text statically.

**Target:** Restore the pre-4e714c5 design with:
- `rememberInfiniteTransition` pulse animation on a dot
- `Surface` container with rounded corners and background
- Gradient vertical left accent bar
- Keep existing expand/collapse behavior

- [ ] **Step 1: Replace the ReasoningBlock implementation**

Replace lines 5079-5157 with:

```kotlin
private fun ReasoningBlock(text: String, defaultExpanded: Boolean = false, durationMs: Long? = null) {
    val isAmoled = isAmoledTheme()
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember { mutableStateOf(defaultExpanded) }

    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val containerColor = when {
        isAmoled -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)
    }
    val textColor = MaterialTheme.colorScheme.onSurface

    // Pulse animation for the thinking dot (runs only while durationMs == null = still thinking)
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 1200; 0.7f at 400; 0.4f at 800 },
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isComplete = durationMs != null
    val headerText = if (isComplete && durationMs != null) {
        val dur = if (durationMs < 1000) "${durationMs}ms"
            else if (durationMs < 60000) "${"%.1f".format(durationMs / 1000.0)}s"
            else "${"%.1f".format(durationMs / 60000.0)}m"
        stringResource(R.string.chat_thinking_complete, dur)
    } else {
        stringResource(R.string.chat_status_thinking)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Gradient left accent bar
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.15f)
                                    )
                                )
                            )
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); expanded = !expanded }
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Animated pulse dot (shows only while thinking)
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = accentColor.copy(
                                            alpha = if (isComplete) 0.4f else pulseAlpha
                                        )
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            ),
                            color = textColor.copy(alpha = 0.45f)
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded)
                            stringResource(R.string.chat_collapse)
                        else
                            stringResource(R.string.chat_expand),
                        modifier = Modifier.size(18.dp),
                        tint = textColor.copy(alpha = 0.3f)
                    )
                }

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
                            MarkdownContent(
                                markdown = text,
                                textColor = textColor.copy(alpha = 0.55f),
                                isUser = false
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Update the Part.Reasoning rendering call**

At line 4513, change from:
```kotlin
ReasoningBlock(text = part.text, defaultExpanded = LocalExpandReasoning.current)
```
to:
```kotlin
val reasoningDuration = part.time?.let { t ->
    t.end?.let { end -> end - t.start }
}
ReasoningBlock(
    text = part.text,
    defaultExpanded = LocalExpandReasoning.current,
    durationMs = reasoningDuration
)
```

- [ ] **Step 3: Add new string resource**

In `app/src/main/res/values/strings.xml` (English default), add:
```xml
<string name="chat_thinking_complete">Thought for %1$s</string>
```

In `app/src/main/res/values-zh-rCN/strings.xml` (Chinese), add:
```xml
<string name="chat_thinking_complete">思考完毕 · %1$s</string>
```

Note: The remaining 13 language files (`values-ar/de/es/fr/id/it/ja/ko/pl/pt-rBR/ru/tr/uk`) will automatically fall back to the English default via Android resource resolution. This is expected behavior — no changes needed to those files.

- [ ] **Step 4: Build, verify animation, and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: restore ReasoningBlock pulse animation and thinking duration

- Add rememberInfiniteTransition pulse animation on colored dot
- Restore Surface container with rounded corners and gradient left accent bar
- Show 'Thought for X.Xs' when reasoning is complete (using Part.Reasoning.time)
- Keep existing expand/collapse and MarkdownContent rendering"
```

**Acceptance criteria (verify on emulator):**
- [ ] Start a chat → send a message that triggers AI thinking
- [ ] Reasoning block appears with pulse dot animation (opacity oscillates ~1.2s cycle)
- [ ] Left gradient accent line visible (primary color → fading)
- [ ] "Thinking" label shown while reasoning is in progress
- [ ] When reasoning completes, label changes to "Thought for X.Xs"
- [ ] Pulse dot stops animating (stays at fixed 0.4f opacity)
- [ ] Tapping the block expands/collapses the reasoning content

---

### Task 3: Add token/cost statistics to assistant messages

**Files:**
- Modify: `ChatScreen.kt:4141-4226` (AssistantMessageCard)
- Modify: `ChatScreen.kt:4575-4577` (Part.StepFinish rendering)

**Current state:** `Part.StepFinish` carries `tokens` (input/output/reasoning/cache) and `cost` fields but the PartContent switch block skips it entirely (line 4575: `// Token/cost info hidden from message bubbles (WebUI convention)`).

**Target:** Collect token/cost from all `Part.StepFinish` parts across all assistant messages and display at bottom of each `AssistantMessageCard`.

- [ ] **Step 1: Add token/cost footer to AssistantMessageCard**

After the last `PartContent` loop (before line 4209, the error Surface), add a token/cost footer:

In `AssistantMessageCard`, after the `for (part in renderableParts)` loop and before the error Surface, add:

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

- [ ] **Step 2: Remove the dead Part.StepFinish skip comment**

At line 4575-4577, change:
```kotlin
is Part.StepFinish -> {
    // Token/cost info hidden from message bubbles (WebUI convention)
}
```
to:
```kotlin
is Part.StepFinish -> {
    // Token/cost info is aggregated at the bottom of AssistantMessageCard
}
```

- [ ] **Step 3: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: restore token/cost statistics below assistant messages

Aggregate input/output tokens and cost from all Part.StepFinish parts
within an assistant message and display as a footer row (e.g., '↑123 ↓456 · $0.0023').
Previously this data was received but UI skipped it entirely."
```

**Acceptance criteria:**
- [ ] Assistant message shows `↑N ↓N` token footer when tokens > 0
- [ ] Shows `$X.XXXX` cost footer when cost > 0
- [ ] No footer shown when both tokens and cost are 0/null
- [ ] `cost.isFinite()` guard prevents NaN/Infinity display
- [ ] All `StepFinish` parts in a message are aggregated correctly

---

### Task 4: Clean up StepFinishInfo dead code

**Files:**
- Modify: `ChatScreen.kt:6514-6536` (remove StepFinishInfo function)

- [ ] **Step 1: Remove the unused StepFinishInfo function**

Delete lines 6514-6536 (the entire `StepFinishInfo` function, which is never called):

```kotlin
// DELETE these lines:
private fun StepFinishInfo(step: Part.StepFinish) {
    if (step.tokens != null || step.cost != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            step.tokens?.let { tokens ->
                Text(
                    text = stringResource(R.string.chat_tokens_format, tokens.input, tokens.output),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            step.cost?.let { cost ->
                Text(
                    text = stringResource(R.string.chat_cost_format, String.format("%.4f", cost)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "chore: remove unused StepFinishInfo dead code

This function was never called since token/cost info was hidden from
message bubbles. Cleaned up in favor of the new aggregated footer
in AssistantMessageCard."
```

**Acceptance criteria:**
- [ ] `Select-String "fun StepFinishInfo" ChatScreen.kt` returns no match
- [ ] Build passes

---

### Task 5: Read tool card — show file name

**Files:**
- Modify: `ChatScreen.kt:6040-6046`

**Current:** `text = stringResource(R.string.tool_read)` — only shows "读取"/"Read"

**Target:** Show "读取 · filename" using the already-extracted `shortPath` variable (line 5996)

- [ ] **Step 1: Replace the title text**

At line 6040-6046, change from:
```kotlin
Text(
    text = stringResource(R.string.tool_read),
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f)
)
```
to:
```kotlin
val displayText = if (shortPath.isNotBlank()) {
    "${stringResource(R.string.tool_read)} · $shortPath"
} else {
    stringResource(R.string.tool_read)
}
Text(
    text = displayText,
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f)
)
```

- [ ] **Step 2: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: show file name in ReadToolCard title
Display '读取 · filename' instead of just '读取', using already-extracted shortPath."
```

**Acceptance criteria:**
- [ ] Read tool card shows "Read · filename" when file exists
- [ ] Shows "Read" only when filePath is empty
- [ ] Long filenames truncated with ellipsis
- [ ] Build passes

---

### Task 6: Write tool card — show file name

**Files:**
- Modify: `ChatScreen.kt:5804-5810`

**Current:** `text = stringResource(R.string.chat_write_label)` — only shows "写入"/"Write"

**Target:** Show "写入 · filename" using the already-extracted `shortPath` variable (line 5768)

- [ ] **Step 1: Replace the title text**

At line 5804-5810, change from:
```kotlin
Text(
    text = stringResource(R.string.chat_write_label),
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f)
)
```
to:
```kotlin
val displayText = if (shortPath.isNotBlank()) {
    "${stringResource(R.string.chat_write_label)} · $shortPath"
} else {
    stringResource(R.string.chat_write_label)
}
Text(
    text = displayText,
    style = MaterialTheme.typography.labelMedium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f)
)
```

- [ ] **Step 2: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: show file name in WriteToolCard title
Display '写入 · filename' instead of just '写入', using already-extracted shortPath."
```

**Acceptance criteria:**
- [ ] Write tool card shows "Write · filename" when file exists
- [ ] Shows "Write" only when filePath is empty
- [ ] Long filenames truncated with ellipsis
- [ ] Build passes

---

### Task 7: Fix keyboard auto-popup on scroll to bottom

**Files:**
- Modify: `ChatScreen.kt:2381, 2635` (remove `imeNestedScroll()`)

**Root cause:** `Modifier.imeNestedScroll()` on the LazyColumn is bidirectional — it hides the keyboard when scrolling **up**, but also **shows** the keyboard when overscrolling at the **bottom**. This causes unwanted keyboard popup when user scrolls to the bottom of messages.

**Fix strategy:**
1. Remove `imeNestedScroll()` from both LazyColumn modifiers
2. Replace with `snapshotFlow` watching `firstVisibleItemIndex` changes to detect scroll
3. Only call `keyboardController?.hide()`, never `.show()` — prevents the reverse problem

**Why not `isScrollInProgress`:** `isScrollInProgress` only fires during fling and programmatic scroll, not during user touch drag (confirmed in beta.64 analysis). `snapshotFlow` fires on EVERY scroll state change including touch drag.

- [ ] **Step 1: Remove imeNestedScroll from both LazyColumns**

At line 2381 (main session LazyColumn modifier chain), remove the line:
```kotlin
.imeNestedScroll()
```

At line 2635 (sub-session LazyColumn modifier chain), remove the line:
```kotlin
.imeNestedScroll()
```

- [ ] **Step 2: Add snapshotFlow-based keyboard dismissal**

First, verify the `mutableIntStateOf` import exists in ChatScreen.kt:
```bash
Select-String "import androidx.compose.runtime.mutableIntStateOf" app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
```

If not found, add the imports near other `androidx.compose.runtime` and `kotlinx.coroutines.flow` imports:
```kotlin
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.drop
```

Then, in both locations (main session at ~line 2380 and sub-session at ~line 2634), after LazyColumn setup and `listState` declaration, add:

```kotlin
// Dismiss keyboard on scroll (hide-only, never show)
val keyboardController = LocalSoftwareKeyboardController.current
var lastScrollIndex by remember { mutableIntStateOf(listState.firstVisibleItemIndex) }
LaunchedEffect(Unit) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .drop(1)  // skip initial state to avoid false trigger on composition
        .collect { index ->
            if (index != lastScrollIndex) {
                keyboardController?.hide()
                lastScrollIndex = index
            }
        }
}
```

**How it works:**
- `snapshotFlow` emits on every index change (fling, drag, programmatic — all types)
- `.drop(1)` skips the initial composition emission, preventing false `hide()` on first render
- `lastScrollIndex` initialized to actual current index, not hardcoded 0
- Only triggers `hide()` when index actually changes
- `keyboardController?.hide()` is one-directional — NEVER shows keyboard

- [ ] **Step 3: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: use snapshotFlow instead of imeNestedScroll for keyboard dismissal

Remove bidirectional imeNestedScroll() (which auto-shows keyboard on
bottom overscroll) and replace with snapshotFlow watching scroll index
changes — only calls keyboardController?.hide(), never show.

snapshotFlow fires on ALL scroll types (fling, touch drag, programmatic)
unlike isScrollInProgress which only fires on fling. This provides
reliable keyboard dismissal without the unwanted auto-popup side effect."
```

**Acceptance criteria (must test on emulator/device):**
- [ ] Focus input field → keyboard appears
- [ ] Scroll message list with touch drag → keyboard hides
- [ ] Scroll message list with fling → keyboard hides
- [ ] Scroll to bottom of messages → keyboard does NOT auto-popup
- [ ] Tap input field → keyboard reappears normally
- [ ] Build passes (compileDebugKotlin)
- [ ] `Select-String "imeNestedScroll" ChatScreen.kt` returns no matches

---

### Task 8: Search tool card — fix title format

**Files:**
- Modify: `ChatScreen.kt:6132-6142` (SearchToolCard title and args construction)

**Current:** Uses `serverTitle` as-is, which shows raw server text like "Searching for TODO in *.kt files". Should show structured format: "搜索 · pattern/TODO" or "搜索代码" + args.

**Target:** Show icon-appropriate title ("搜索代码" for grep, "查找文件" for glob) with the search pattern as inline args display.

- [ ] **Step 1: Fix the title construction**

At lines 6132-6142, change from:
```kotlin
val title = when (tool.tool) {
    "glob" -> serverTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tool_find_files)
    "grep" -> serverTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tool_search_code)
    else -> serverTitle?.takeIf { it.isNotBlank() } ?: tool.tool
}

// Build args display
val argsText = buildList {
    pattern?.let { add("pattern=$it") }
    include?.let { add("include=$it") }
}.takeIf { it.isNotEmpty() }?.joinToString(", ", "[", "]")
```

to:
```kotlin
val baseTitle = when (tool.tool) {
    "glob" -> stringResource(R.string.tool_find_files)
    "grep" -> stringResource(R.string.tool_search_code)
    else -> tool.tool.replace("_", " ").replaceFirstChar { it.uppercase() }
}
val patternShort = pattern?.let {
    if (it.length > 40) it.take(37) + "..." else it
}
val title = if (patternShort != null) "$baseTitle · $patternShort" else baseTitle
```

- [ ] **Step 2: Remove unused argsText variable**

The `argsText` variable (originally at line 6139-6142) is only defined and never referenced elsewhere in SearchToolCard. Since pattern is now shown directly in the title, this variable is dead code. The Step 1 code replacement already removes it — no extra action needed. Verify with:

```bash
Select-String "argsText" app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
# Expected: no matches (variable removed)
```

- [ ] **Step 3: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: improve SearchToolCard title format
Show '搜索代码 · pattern' or '查找文件 · glob' instead of raw server title.
Use Magnifier icon consistently for search tools."
```

**Acceptance criteria:**
- [ ] Grep search shows "Search code · pattern" (not raw server title)
- [ ] Glob search shows "Find files · pattern"
- [ ] Patterns >40 chars truncated with "..."
- [ ] `argsText` variable no longer exists (`Select-String "argsText" ChatScreen.kt` returns no match)
- [ ] Magnifier icon visible (already present in existing code)
- [ ] Build passes

---

### Task 9: Add Surface wrapper to bare tool cards (ToolCallCard + TodoListCard + PatchCard)

**Files:**
- Modify: `ChatScreen.kt:5160-5326` (ToolCallCard — MCP tools via `else` branch)
- Modify: `ChatScreen.kt:6420-6521` (TodoListCard — task list card)
- Modify: `ChatScreen.kt:6594-6693` (PatchCard — diff/file change card)

**Audit results:** Of 13 card composables, only 3 are missing Surface/Card wrappers:
| Card | Has Surface? | Status |
|------|-------------|--------|
| ToolCallCard (MCP) | ❌ bare Row | → Fix in Step 1 |
| TodoListCard (tasks) | ❌ bare Row | → Fix in Step 2 |
| PatchCard (diffs) | ❌ bare Row | → Fix in Step 3 |
| EditToolCard | ✅ | — |
| WriteToolCard | ✅ | — |
| BashToolCard | ✅ | — |
| ReadToolCard | ✅ | — |
| SearchToolCard | ✅ | — |
| TaskToolCard | ✅ | — |
| FileCardFallback | ✅ | — |
| PermissionCard | ✅ (uses Card) | — |
| QuestionCard | ✅ (uses Card) | — |

**Target:** Wrap all 3 bare cards in the same Surface container pattern (`shape=8.dp, color, border, tonalElevation`) for visual consistency with other tool cards.

- [ ] **Step 1: Wrap ToolCallCard in Surface**

Replace the current `ToolCallCard` function body (lines 5187-5326, the content after `val expanded = isExpanded`) with the same Surface wrapper pattern used by ReadToolCard:

```kotlin
    val expanded = isExpanded

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row — always clickable to allow expand/collapse in any state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (tool.state) {
                            is ToolState.Running -> Icons.Default.Sync
                            is ToolState.Completed -> toolDisplay.icon
                            is ToolState.Error -> Icons.Default.Error
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (tool.state is ToolState.Error) stateColor else toolDisplay.iconTint ?: stateColor
                    )
                    if (tool.tool == "task") {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = toolDisplay.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (toolDisplay.subtitle != null) {
                                Text(
                                    text = toolDisplay.subtitle,
                                    style = CodeTypography.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        val displayText = if (toolDisplay.subtitle != null && toolDisplay.subtitle != toolDisplay.title) {
                            "${toolDisplay.title} · ${toolDisplay.subtitle}"
                        } else {
                            toolDisplay.title
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (tool.state is ToolState.Running) {
                    PulsingDotsIndicator(dotSize = 5.dp, dotSpacing = 3.dp, color = stateColor)
                } else {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

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
                            )
                        }
                    }
                }
            }
        }
    }
```

Note: The key change is wrapping everything **after** `val expanded = isExpanded` in `Surface(shape=8.dp, color, border, elevation) { Column(padding=8.dp) { ... } }` instead of the current bare Row/AnimatedVisibility.

**Edit target:** Find the line `val expanded = isExpanded` (currently ~line 5186). Delete everything from the NEXT line through the closing `}` of the function (~line 5326). Replace with the Surface+Column wrapper above, preserving `val expanded = isExpanded` as-is.

Also change the MCP title separator from space to `·` for uniformity:
```kotlin
"${toolDisplay.title} · ${toolDisplay.subtitle}"  // single dot separator like all other tool cards
```

- [ ] **Step 2: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
- [ ] **Step 2: Wrap TodoListCard in Surface**

Find the `TodoListCard` function (~line 6420). After the `val hapticOn = LocalHapticFeedbackEnabled.current` line (~line 6461) and `val expanded = isExpanded`, the body currently starts directly with a bare `Row` (header row). Wrap it:

Change from (the body after `val expanded = isExpanded`):
```kotlin
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ... onToggleExpand() },
                ...
```

To:
```kotlin
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); onToggleExpand() },
                ...
```

And add the closing `}` for both `Column` and `Surface` at the end of the function (before the final `}`).

- [ ] **Step 3: Wrap PatchCard in Surface**

Same pattern as TodoListCard. Find `PatchCard` (~line 6594). After `val expanded = isExpanded` (~line 6602), the body starts with a bare `Row`. Wrap with:

```kotlin
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // existing Row + AnimatedVisibility content
        }
    }
```

- [ ] **Step 4: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: wrap ToolCallCard + TodoListCard + PatchCard in Surface

All three bare tool cards now have the same Surface container
(8dp rounded corners, background, border) as other tool cards,
ensuring uniform tool card appearance throughout the chat."
```

**Acceptance criteria:**
- [ ] MCP tool cards (e.g., `search_graph`, `context7_query-docs`) have visible 8dp rounded rectangle background
- [ ] TodoListCard (task list) has visible 8dp rounded rectangle background
- [ ] PatchCard (diff/file changes) has visible 8dp rounded rectangle background
- [ ] Light theme: surface color + 1dp tonalElevation
- [ ] AMOLED theme: black fill + 1dp outlineVariant border
- [ ] Click-to-expand still works on all 3 cards
- [ ] Input/output parameters display correctly in expanded state
- [ ] Task tool cards still show 2-line layout (title + subtitle)
- [ ] Build passes

---

### Task 10: Fix Markdown single newline rendering for user messages

**Files:**
- Modify: `ChatScreen.kt:4667` (MarkdownContent normalizedMarkdown)

**Root cause:** Markdown renderer treats single `\n` as a soft break (no visible line break) per CommonMark spec. User-typed single newlines in the input field need `\n\n` (double newline) to produce a paragraph break in Markdown. Assistant messages use actual Markdown so they're fine — only user messages are affected because the user types plain text that gets rendered as Markdown.

**Fix:** In `MarkdownContent`, when `isUser == true`, convert single `\n` to `\n\n` before passing to the Markdown renderer. This makes user-typed line breaks visible without affecting assistant messages.

**Known limitation:** This conversion does NOT protect code blocks (```...```) inside user messages. If a user pastes code with backtick fences, line breaks inside the fence may be doubled. This is acceptable because: (a) user messages in a coding assistant rarely contain formatted Markdown code blocks, and (b) the simpler approach avoids complex state-machine parsing.

- [ ] **Step 1: Add newline doubling for user messages**

At line 4667, change from:
```kotlin
val normalizedMarkdown = remember(markdown) { preserveRawHtmlPayload(markdown) }
```
to:
```kotlin
val normalizedMarkdown = remember(markdown, isUser) {
    val base = preserveRawHtmlPayload(markdown)
    if (isUser) {
        // User messages: single \n doesn't break in Markdown (soft break).
        // Convert standalone \n to \n\n for paragraph breaks.
        // Note: this does not protect code blocks — see Known limitation above.
        base.replace(Regex("(?<!\n)\n(?!\n)"), "\n\n")
    } else {
        base
    }
}
```

- [ ] **Step 2: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: preserve newlines in user messages by doubling \n for Markdown
User-typed single newlines (\n) are invisible in Markdown (soft breaks).
Convert to \n\n (paragraph break) only for user messages so that
line breaks typed in the input field are visible in the rendered output."
```

**Acceptance criteria:**
- [ ] Type "line1\nline2" in input, send → both lines visible in message bubble
- [ ] Type "line1\n\nline2" (double newline) → still renders as two paragraphs (no quadruple)
- [ ] Assistant messages remain unchanged (no double-doubling)
- [ ] Build passes

---

### Task 11: Add text selection/copy for assistant message content

**Files:**
- Modify: `ChatScreen.kt:4662-4840` (MarkdownContent)

**Current state:** `MarkdownContent` intentionally removed `SelectionContainer` because its `selectableGroup()` pointer-input handler leaks into the LazyColumn's pointer scope and blocks clicks on tool cards in adjacent messages. Text can only be copied via the header "Copy" button (copies entire message). Users cannot select partial text.

**Target:** Add long-press gesture on assistant Markdown content → show a Dialog with selectable plain text. The Dialog's `SelectionContainer` operates in a separate window layer, so it doesn't interfere with the LazyColumn.

**Why not just wrap Markdown in SelectionContainer?**
Markdown renderer produces multiple composables (paragraphs, code blocks, lists). `SelectionContainer`'s internal `selectableGroup()` captures pointer events at the LazyColumn scope, blocking clicks on sibling items (tool cards, expand/collapse). This was confirmed and documented in lines 4809-4822.

- [ ] **Step 1: Add long-press state and Dialog to MarkdownContent**

At the end of `MarkdownContent` (before the closing `}`), wrap the `Markdown(...)` call and add a Dialog:

Change from:
```kotlin
    val markdownState = rememberMarkdownState(
        content = normalizedMarkdown,
        retainState = true
    )
    Markdown(
        markdownState = markdownState,
        colors = colors,
        typography = typography,
        components = components,
        dimens = dimens,
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = Modifier.fillMaxWidth()
    )
}
```

To:
```kotlin
    // Long-press to select/copy text (Dialog-based to avoid SelectionContainer
    // pointer-input interference with LazyColumn's tool card clicks)
    var showTextDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current

    val markdownState = rememberMarkdownState(
        content = normalizedMarkdown,
        retainState = true
    )
    Markdown(
        markdownState = markdownState,
        colors = colors,
        typography = typography,
        components = components,
        dimens = dimens,
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isUser) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                performHaptic(view, true)
                                showTextDialog = true
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    )

    // Selectable text dialog — SelectionContainer is safe here because
    // Dialog operates in a separate window, isolated from LazyColumn
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.chat_select_text_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(markdown))
                        showTextDialog = false
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                val dialogScrollState = rememberScrollState()
                SelectionContainer {
                    Text(
                        text = markdown,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollState)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
```

- [ ] **Step 2: Add string resource**

In `app/src/main/res/values/strings.xml` (English default), add:
```xml
<string name="chat_select_text_title">Select Text</string>
```

In `app/src/main/res/values-zh-rCN/strings.xml` (Chinese), add:
```xml
<string name="chat_select_text_title">选择文本</string>
```

- [ ] **Step 3: Verify imports**

Ensure these imports exist near other `androidx.compose.foundation` imports:
```kotlin
import androidx.compose.foundation.text.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalClipboardManager
```

- [ ] **Step 4: Build and commit**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL

git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: add long-press text selection for assistant messages

Long-press on assistant Markdown content opens a Dialog with selectable
plain text. Uses Dialog-based SelectionContainer (isolated window) to
avoid pointer-input interference with LazyColumn tool card clicks."
```

**Acceptance criteria:**
- [ ] Long-press on assistant message text → Dialog appears with plain text
- [ ] Text in Dialog is selectable (drag to select, copy via context menu)
- [ ] "Copy all" button in Dialog header copies entire text to clipboard
- [ ] User messages do NOT show long-press dialog (isUser check)
- [ ] Short tap/click on assistant message still works normally
- [ ] Tool cards, expand/collapse still respond to clicks
- [ ] Dialog has OK button to dismiss
- [ ] Build passes

---

### Task 12: Version bump and release

**Files:**
- Modify: `app/build.gradle.kts:20-21`

- [ ] **Step 1: Bump version**

```kotlin
versionCode = 266
versionName = "2.0.0-beta.66"
```

- [ ] **Step 2: Build final release APK**

```bash
cd D:\Develop\code\app\oc-remote
.\gradlew.bat clean assembleRelease 2>&1 | Select-String "BUILD|error:"
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 3: Install and smoke test on emulator**

```bash
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n dev.minios.ocremote/.MainActivity
adb logcat -d -s "AndroidRuntime" --format=brief 2>&1 | Select-String "FATAL|ocremote"
# Expected: no output (no crashes)
```

- [ ] **Step 4: Commit version bump**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump to v2.0.0-beta.66 (versionCode=266)"
```

- [ ] **Step 5: Tag and release**

First, create the release notes file:
```bash
@"
## v2.0.0-beta.66

### Restored Features
- **Reasoning animation** — pulse dot + gradient accent line + Surface container
- **Thinking duration** — shows "Thought for X.Xs" when reasoning completes
- **Token/cost statistics** — restored below assistant messages (↑123 ↓456 · $0.0023)
- **Send button circle** — visible in disabled state with semi-transparent background
- **Text selection** — long-press assistant text to open selectable text dialog

### Tool Card Improvements
- **Read/Write tools** — show file name (e.g., "Read · main.kt")
- **Search tools** — show pattern in title (e.g., "Search code · TODO")
- **MCP/TodoList/Patch cards** — unified Surface container with all other tool cards

### Bug Fixes
- **Keyboard popup** — scrolling to bottom no longer auto-opens keyboard
- **Newline rendering** — line breaks typed in input are now visible in messages

### Process
- Added pre-flight validation and sequential editing strategy to prevent code loss
"@ | Out-File -FilePath release_notes.md -Encoding utf8
```

Verify pre-flight:
```bash
git remote -v | Select-String "fork"     # Confirm remote exists
gh auth status 2>&1 | Select-String "Logged in"   # Confirm GitHub CLI authed
```

Then create the release:
```bash
# Extract repo name from git remote (avoids hardcoding fork name)
$repoUrl = git remote get-url fork 2>&1
$repoName = if ($repoUrl -match 'github\.com[:/](.+?)(\.git)?$') { $matches[1] } else { "LeoNardo-LB/oc-remote-v2" }

git tag v2.0.0-beta.66
git push fork dev
git push fork v2.0.0-beta.66
gh release create v2.0.0-beta.66 "app/build/outputs/apk/release/app-release.apk#oc-remote-v2.0.0-beta.66.apk" --repo $repoName --title "v2.0.0-beta.66 - UI Restoration & Bugfixes" --notes-file release_notes.md
```

---

### Task 13: Prevent future code loss — process improvement

**Files:**
- Modify: none (process change)

**Root cause:** Earlier beta cycles (beta.62→63→64) showed that changes in ChatScreen.kt were repeatedly lost during multi-commit sequences, likely due to git workflow issues (force pushes, branch mismatches, or merge conflicts overwriting previous work).

**Prevention measures (implemented in this plan):**
1. **Sequential editing** — all tasks modify ChatScreen.kt in order (1→12), never parallel
2. **Per-task commits** — each task has its own `git add` + `git commit` step
3. **Pre-edit reads** — always Read the target section before editing to confirm current state
4. **Fast validation** — each task runs `compileDebugKotlin` (≈30s) before committing
5. **Rollback protocol** — documented `git checkout` commands to revert any failed edit
6. **Single commit per task** — no force pushes, no squashing of unrelated changes

- [ ] **Step 1: Document the process in AGENTS.md or CLAUDE.md**

Ensure the project's agent instruction file includes the rule:

```markdown
## ChatScreen.kt Editing Protocol
- NEVER edit ChatScreen.kt in parallel across multiple agents
- ALWAYS Read before Edit to confirm current content
- After each Edit, run `.\gradlew.bat :app:compileDebugKotlin`
- Commit after each successful compilation
- If compilation fails: `git checkout -- ChatScreen.kt`, re-read, retry
- One commit = one logical change (no bundling)
```

- [ ] **Step 2: Verify no stray changes before starting**

```bash
git status --porcelain
# Expected: only Task 1's send-button changes in working tree
# All other files should be clean
```

- [ ] **Step 3: Commit the process improvement**

```bash
git add AGENTS.md  # (if modified)
git commit -m "docs: add ChatScreen.kt editing protocol to prevent code loss"
```

---

## Task Dependency Graph

```
Task 1 (send btn circle) ──┐
Task 2 (reasoning block) ──┤
Task 3 (token/cost stats) ─┤
Task 4 (dead code cleanup) ┤  All independent,
Task 5 (read file name) ───┤  execute SEQUENTIALLY
Task 6 (write file name) ──┤  in order 1→12
Task 7 (keyboard fix) ─────┤
Task 8 (search format) ────┤
Task 9 (Surface wrapper x3) ──┤
Task 10 (newline fix) ─────┤
Task 11 (text selection) ──┤
Task 12 (version bump) ────┤  depends on all above
Task 13 (process doc) ─────┘  independent (no code changes)
```

**⚠️ Execution order: STRICTLY SEQUENTIAL (Task 1→13).** All tasks modify the same file (`ChatScreen.kt`). Parallel execution WILL cause line-number conflicts and edit failures. Execute one task at a time, complete its build+commit steps, then move to the next.

**Fast compile validation:** Replace `assembleRelease` in each task's build step with:
```bash
.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-String "BUILD|error:"
```
This runs in ≈30s (vs ≈2min for assembleRelease). Use `assembleRelease` only in Task 12 for the final APK.

**Line ranges per task (no overlap, base commit 0b19511):**
| Task | Line Range | Function |
|------|-----------|----------|
| 1 | 7560-7607 | ChatInputBar send button |
| 2 | 4511-4514 + 5079-5157 | Part.Reasoning call + ReasoningBlock |
| 3 | 4141-4226 + 4575-4577 | AssistantMessageCard + StepFinish skip |
| 4 | 6514-6536 | StepFinishInfo (delete) |
| 5 | 6040-6046 | ReadToolCard title |
| 6 | 5804-5810 | WriteToolCard title |
| 7 | 2381, 2635 | imeNestedScroll removal |
| 8 | 6132-6142 | SearchToolCard title |
| 9 | 5187-5326, 6462-6521, 6603-6693 | ToolCallCard + TodoListCard + PatchCard Surface wrappers |
| 10 | 4667 | MarkdownContent normalize |
| 11 | 4662-4840 | MarkdownContent text selection dialog |
| 12 | 20-21 | build.gradle.kts version |
| 13 | — | No code changes |
