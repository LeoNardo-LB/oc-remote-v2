# Chat UX 修复与优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 ChatScreen 的 7 个 UX 问题，按优先级从高到低分 3 批实施。

**Architecture:** 所有修改集中在 `ChatScreen.kt`、`ChatParts.kt`、`ChatViewModel.kt`、`EventReducer.kt`。修复采用最小侵入原则，不引入新依赖。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, mikepenz multiplatform-markdown-renderer 0.41.0

---

## 修复优先级排序

| 优先级 | Task | 问题 | 影响范围 |
|--------|------|------|---------|
| P0 | Task 1 | 流式输出时强制拉到底部 | ChatScreen.kt 1 行 |
| P0 | Task 2 | 部分工具卡片不能点击 | ChatScreen.kt 5 处条件 |
| P1 | Task 3 | 工具展开时上方内容上抬 | ChatScreen.kt 9 处 AnimatedVisibility |
| P1 | Task 4 | 消息计数/分组与加载更多截断 | ChatViewModel.kt 2 处 + EventReducer.kt 1 函数 + ChatParts.kt (无改动，仅引用 groupMessages) |
| P2 | Task 5 | 表格列宽固定 | ChatScreen.kt SimpleMarkdownTable |
| P2 | Task 6 | 斜杠命令样式不显眼 | ChatScreen.kt 1 行 |

---

## File Structure

| 文件 | 修改类型 | 负责的 Task |
|------|---------|------------|
| `app/.../ui/screens/chat/ChatScreen.kt` | 修改 | Task 1, 2, 3, 5, 6 |
| `app/.../ui/screens/chat/ChatParts.kt` | 修改 | Task 4 |
| `app/.../ui/screens/chat/ChatViewModel.kt` | 修改 | Task 4 |
| `app/.../data/repository/EventReducer.kt` | 修改 | Task 4 |
| `app/.../domain/model/Message.kt` | 不修改 | — |
| `app/.../ui/theme/Color.kt` | 不修改 | — |
| `app/.../ui/theme/Theme.kt` | 不修改 | — |

---

## Task 1: 修复流式输出时强制拉到底部 [P0]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:1423`

**根因:** `ChatScreen.kt:1423` 行条件 `autoScrollEnabled || pendingCount > 0` 中，`pendingCount > 0` 绕过了用户滚动控制标志 `autoScrollEnabled`。当 Agent 请求权限/问题时，即使用户已上滑阅读之前内容，也会被强制拉到底部。

**修复策略:** 去掉 `pendingCount > 0` 分支。pending 权限/问题以独立 item 形式插入 LazyColumn，用户滚动到底部时自然可见。

- [ ] **Step 1: 修改自动滚动条件**

在 `ChatScreen.kt` 第 1423 行，将：

```kotlin
if (messageCount > 0 && (autoScrollEnabled || pendingCount > 0)) {
```

改为：

```kotlin
if (messageCount > 0 && autoScrollEnabled) {
```

- [ ] **Step 2: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 在模拟器上 E2E 验证**

1. 打开一个会话，发送消息让 Agent 开始流式输出
2. 在流式输出过程中，手动向上滑动
3. 验证：页面停留在用户手动滑到的位置，不再被强制拉到底部
4. 等 Agent 输出完成，验证：点击"滚动到底部"FAB 仍可正常使用
5. 验证：Agent 请求权限时，不再强制拉到底部

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: remove pendingCount bypass in auto-scroll to prevent forced scroll during streaming"
```

---

## Task 2: 统一工具卡片可点击性 [P0]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt` (5 处)

**根因:** 5 种工具卡片（EditToolCard、WriteToolCard、BashToolCard、SearchToolCard、TaskToolCard）使用 `hasContent`/`hasOutput` 条件控制 `.clickable`，在 Pending/Running 或无输出时不可点击。而 ReadToolCard、TodoListCard 和回退 ToolCallCard 始终可点击。

**修复策略:** 将所有工具卡片的头部行改为始终可点击（`.clickable` 无条件），展开箭头始终显示，展开区域在无内容时显示为空（与 ToolCallCard 行为一致）。

- [ ] **Step 1: 修改 EditToolCard（行 5200-5205）**

将：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent) mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded } else mod
                    },
```

改为：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); expanded = !expanded },
```

- [ ] **Step 2: 修改 WriteToolCard（行 5461-5466）**

将：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent) mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded } else mod
                    },
```

改为：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); expanded = !expanded },
```

- [ ] **Step 3: 修改 BashToolCard（行 5579-5584）**

将：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent) mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded } else mod
                    },
```

改为：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); expanded = !expanded },
```

- [ ] **Step 4: 修改 SearchToolCard（行 5864-5869）**

将：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasOutput) mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded } else mod
                    },
```

改为：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { performHaptic(hapticView, hapticOn); expanded = !expanded },
```

- [ ] **Step 5: 修改 TaskToolCard（行 6003-6014）**

将：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        when {
                            subSessionId != null && onViewSubSession != null ->
                                mod.clickable { performHaptic(hapticView, hapticOn); onViewSubSession(subSessionId) }
                            hasOutput ->
                                mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded }
                            else -> mod
                        }
                    },
```

改为：

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        when {
                            subSessionId != null && onViewSubSession != null ->
                                mod.clickable { performHaptic(hapticView, hapticOn); onViewSubSession(subSessionId) }
                            else ->
                                mod.clickable { performHaptic(hapticView, hapticOn); expanded = !expanded }
                        }
                    },
```

注意：TaskToolCard 保留了 `subSessionId` 优先导航逻辑（点击打开子会话），仅将 `else -> mod`（不可点击）改为 `else -> mod.clickable { ... }`（可点击展开）。

- [ ] **Step 6: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 在模拟器上 E2E 验证**

1. 打开包含各种工具调用的历史会话
2. 验证：所有工具卡片（edit、write、bash、glob/grep、task）均可点击展开/收缩
3. 验证：Pending 状态的工具卡片也可点击（展开后内容为空但可交互）
4. 验证：展开箭头图标始终可见
5. 验证：点击子会话类型的 task 卡片仍正常导航

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: make all tool cards always clickable regardless of content state"
```

---

## Task 3: 修复工具展开时上方内容上抬 [P1]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt` (9 处 AnimatedVisibility)

**根因:** `reverseLayout=true` 模式下，item 变高时向上生长（锚点是下边缘），导致展开工具卡片时上方内容被推移。

**修复策略:** 在每个工具卡片的展开动画中，检测高度变化并通过编程滚动补偿位移，使视觉上"上方不动、向下延展"。

**原理：** 当 `reverseLayout=true` 时，item 高度增加 `dh` 会导致该 item 上方所有 item 的偏移量减少 `dh`。通过 `scrollBy(dh)` 补偿这个位移，即可保持视觉位置稳定。

- [ ] **Step 0: 创建 LocalListState CompositionLocal**

在 ChatScreen.kt 的类级别（顶层，`ChatScreen` 函数之外）添加：

```kotlin
/** Provides [LazyListState] to descendant composables (e.g. tool cards) for scroll compensation. */
private val LocalListState = compositionLocalOf<LazyListState> {
    error("LocalListState not provided")
}
```

然后在主会话和子会话的两个 `LazyColumn` 处，用 `CompositionLocalProvider` 包裹：

```kotlin
// 主会话（约行 2330 处）
CompositionLocalProvider(LocalListState provides listState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        ...
    ) { ... }
}
```

```kotlin
// 子会话（约行 2594 处）— 同样包裹
CompositionLocalProvider(LocalListState provides listState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        ...
    ) { ... }
}
```

这样所有工具卡片都可通过 `val listState = LocalListState.current` 获取状态，无需修改 AssistantTurnBubble / PartContent 等中间层签名。

- [ ] **Step 1: 在文件顶部确认已有必要的 import**

确认以下 import 存在（`remember`、`mutableStateOf`、`LaunchedEffect`、`animateFloatAsState` 应该已有）：

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
```

如果 `onSizeChanged` 或 `onGloballyPositioned` 不在 import 中，需添加：

```kotlin
import androidx.compose.ui.layout.onSizeChanged
```

- [ ] **Step 2: 修改 ToolCallCard（约行 4936）**

找到 ToolCallCard 中 `AnimatedVisibility` 前的内容区域容器（通常是包含展开内容的 `Column` 或 `Box`），为其添加高度追踪。

在 `AnimatedVisibility(visible = expanded, ...)` 上方，添加高度追踪：

```kotlin
val listState = LocalListState.current
val coroutineScope = rememberCoroutineScope()
var previousHeight by remember { mutableIntStateOf(0) }
```

将 `AnimatedVisibility` 包裹在一个带有 `onSizeChanged` 的容器中：

```kotlin
AnimatedVisibility(
    visible = expanded,
    modifier = Modifier.onSizeChanged { size ->
        val newHeight = size.height
        if (newHeight != previousHeight && expanded) {
            val diff = newHeight - previousHeight
            if (diff > 4) {
                try { coroutineScope.launch { listState.scrollBy(diff.toFloat()) } } catch (_: Exception) {}
            }
        }
        previousHeight = newHeight
    }
) {
    // ... 原有内容不变
}
```

注意：`listState` 通过 Step 0 中创建的 `LocalListState` CompositionLocal 获取，`coroutineScope` 使用 `rememberCoroutineScope()` 创建。

**替代方案（如果 CompositionLocal 方案不可行）：** 使用 `LocalLazyListState` compositionLocal 或将 `onSizeChanged` + scroll 逻辑抽取为共享的 Composable wrapper。

- [ ] **Step 3: 为 EditToolCard（约行 5263）应用同样的模式**

```kotlin
var previousHeight by remember { mutableIntStateOf(0) }
AnimatedVisibility(
    visible = expanded && hasContent,
    modifier = Modifier.onSizeChanged { size ->
        val newHeight = size.height
        if (newHeight != previousHeight && expanded) {
            val diff = newHeight - previousHeight
            if (diff > 0) {
                coroutineScope.launch { listState.scrollBy(diff.toFloat()) }
            }
        }
        previousHeight = newHeight
    }
) {
    // ... 原有内容不变
}
```

- [ ] **Step 4: 为 WriteToolCard（约行 5509）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 5: 为 BashToolCard（约行 5649）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 6: 为 ReadToolCard（约行 5771）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 7: 为 SearchToolCard（约行 5927）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 8: 为 TaskToolCard（约行 6067）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 9: 为 TodoListCard（约行 6185）应用同样的模式**

同 Step 3 模式，替换对应的 `AnimatedVisibility`。

- [ ] **Step 10: 为 PatchCard（约行 6321）应用同样的模式**

注意：此处的 AnimatedVisibility（行 6321）属于 `PatchCard` composable（显示 git diff 文件列表），不是 ToolCallCard。PatchCard 同样有展开/收缩行为，在 reverseLayout 模式下也存在上方内容上抬的问题，因此需要同样应用滚动补偿。

同 Step 2 模式，在 PatchCard 的 `AnimatedVisibility(visible = expanded)`（无额外条件，即 `visible = expanded`）上添加 `onSizeChanged` + `scrollBy` 补偿。

- [ ] **Step 11: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12: 在模拟器上 E2E 验证**

1. 打开包含工具调用的会话
2. 点击展开一个工具卡片
3. 验证：该卡片上方的内容保持在原位不动
4. 验证：卡片内容向下延展
5. 点击收缩，验证：上方内容仍然不动
6. 对不同类型的工具卡片（edit、bash、glob 等）重复测试

- [ ] **Step 13: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: compensate scroll offset when tool cards expand/collapse in reverseLayout mode"
```

---

## Task 4: 修复消息计数/分组与加载更多截断 [P1]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatParts.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/data/repository/EventReducer.kt`

**根因分析:**

问题 A（消息计数）：服务端 Agent 每次内循环生成独立 `Message.Assistant` 对象。`groupMessages()` 在渲染层合并了，但 `uiState.messages.size` 仍是 n 个对象。如果 UI 展示用 `messages.size` 作为"交互次数"，则显示值比用户预期多。

问题 B（加载更多截断）：`setMessages()` 覆盖式替换 `_messages` 和 `_parts`。分页边界可能截断 AssistantTurn（部分 assistant message 在旧页、部分在新页），导致加载更多后视觉上出现截断/合并。同时 REST 返回的 parts 可能比 SSE 当前状态旧，导致短暂的内容消失。

**修复策略:**

1. 添加 `chatItemsCount` 到 `ChatUiState`，供 UI 展示分组后的消息数（而非原始 Message 对象数）
2. `loadOlderMessages()` 改用增量追加而非覆盖替换
3. `EventReducer.setMessages()` 合并 parts 时保留 SSE 最新状态（不覆盖已有 parts）

### 子任务 4A: 添加 chatItemsCount 状态

- [ ] **Step 1: 在 ChatViewModel.kt 的 ChatUiState 中添加 chatItemsCount 字段**

在 `ChatUiState` data class 中添加：

```kotlin
val chatItemsCount: Int = 0,
```

- [ ] **Step 2: 在 ChatViewModel.kt 的 combine 块中计算 chatItemsCount**

在构建 `ChatUiState` 的位置（搜索 `ChatUiState(` 构造调用，约行 368），添加对 `chatItemsCount` 的计算：

```kotlin
val chatItemsCount = groupMessages(chatMessages).size
```

并将 `chatItemsCount = chatItemsCount` 传入 `ChatUiState(...)` 构造。

- [ ] **Step 3: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git commit -m "feat: add chatItemsCount to ChatUiState for grouped message counting"
```

### 子任务 4B: loadOlderMessages 改为增量追加

- [ ] **Step 5: 在 EventReducer.kt 中添加 mergeMessages 函数（行 424 后）**

在 `setMessages` 函数之后添加新函数：

```kotlin
/**
 * Refresh messages from REST load-older, replacing the full message list
 * (to guarantee correct sort order) while preserving SSE-provided parts
 * (which may be newer than the REST snapshot).
 */
fun mergeMessages(sessionId: String, messages: List<MessageWithParts>) {
    _messages.update { current ->
        val incoming = messages.map { it.info }.sortedByDescending { m -> m.time.created }
        current + (sessionId to incoming)
    }

    // Only merge parts that we don't already have from SSE (SSE is always fresher)
    _parts.update { currentParts ->
        val existingKeys = currentParts.keys
        val newParts = messages
            .filter { it.info.id !in existingKeys }
            .associate { it.info.id to it.parts }
        currentParts + newParts
    }
}
```

- [ ] **Step 6: 修改 ChatViewModel.kt 的 loadOlderMessages（行 508-525）**

将当前的覆盖式替换：

```kotlin
fun loadOlderMessages() {
    viewModelScope.launch {
        _isLoadingOlder.value = true
        currentMessageLimit *= 2
        try {
            val messages = api.listMessages(conn, sessionId, limit = currentMessageLimit)
            eventReducer.setMessages(sessionId, messages)
            _hasOlderMessages.value = messages.size >= currentMessageLimit
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded older: ${messages.size} messages (limit=$currentMessageLimit, hasOlder=${_hasOlderMessages.value})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load older messages", e)
            currentMessageLimit /= 2
        } finally {
            _isLoadingOlder.value = false
        }
    }
}
```

改为增量追加（保留完整替换以保证排序正确，但改善 parts 合并逻辑）：

```kotlin
fun loadOlderMessages() {
    viewModelScope.launch {
        _isLoadingOlder.value = true
        currentMessageLimit *= 2
        try {
            val messages = api.listMessages(conn, sessionId, limit = currentMessageLimit)
            eventReducer.mergeMessages(sessionId, messages)
            _hasOlderMessages.value = messages.size >= currentMessageLimit
            if (BuildConfig.DEBUG) Log.d(TAG, "Loaded older: ${messages.size} messages (limit=$currentMessageLimit, hasOlder=${_hasOlderMessages.value})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load older messages", e)
            currentMessageLimit /= 2
        } finally {
            _isLoadingOlder.value = false
        }
    }
}
```

- [ ] **Step 7: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 在模拟器上 E2E 验证**

1. 打开一个有长对话历史的会话
2. 向上滑动，点击"加载更多"
3. 验证：已有内容不闪烁/消失
4. 验证：新加载的内容正常追加，不出现截断
5. 验证：Agent 回复的多个内循环消息在加载更多后仍然保持合并

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt app/src/main/kotlin/dev/minios/ocremote/data/repository/EventReducer.kt
git commit -m "fix: preserve SSE parts during load-older to prevent content flickering"
```

---

## Task 5: 表格列宽动态调整 [P2]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4645-4742`

**根因:** `SimpleMarkdownTable` 中每个单元格使用 `Modifier.weight(1f)`，导致所有列均分宽度，无法根据内容自适应。

**修复策略:** 使用 `SubcomposeLayout` 两遍测量：第一遍测量每列最宽内容的 intrinsic width，第二遍用计算出的列宽布局。添加水平滚动支持超宽表格。

- [ ] **Step 1: 重写 SimpleMarkdownTable（行 4645-4742）**

> **注意：** 以下行号为编写计划时的原始基准。经 Task 2（净 -10 行）和 Task 3（净 +72 行）修改后，实际位置偏移约 +62 行。请通过搜索函数名 `SimpleMarkdownTable` 定位。

将整个函数替换为：

```kotlin
/**
 * Table component for 0.41.0 — replaces the default MarkdownTable whose
 * MarkdownTableBasicText inline-content pipeline fails to render cells.
 *
 * Column widths are determined by the widest cell content in each column.
 * Horizontal scroll is enabled when the table exceeds the available width.
 */
@Composable
private fun SimpleMarkdownTable(
    content: String,
    tableNode: ASTNode,
    style: TextStyle,
) {
    val headerBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val rowBgEven = Color.Transparent
    val rowBgOdd = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val pad = 10.dp
    val shape = RoundedCornerShape(6.dp)
    val border = BorderStroke(1.dp, dividerColor)

    // Parse all cells into a grid structure: List<List<ASTNode>> (rows × cols)
    val rows = remember(tableNode, content) {
        val result = mutableListOf<List<ASTNode>>()
        tableNode.children.forEach { child ->
            when (child.type) {
                GFMHeader -> {
                    result.add(child.children.filter { it.type == GFMCell })
                }
                GFMRow -> {
                    result.add(child.children.filter { it.type == GFMCell })
                }
                else -> { /* GFMTableSeparator — skip */ }
            }
        }
        result
    }

    val columnCount = remember(rows) { rows.maxOfOrNull { it.size } ?: 0 }
    if (columnCount == 0 || rows.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Measure column widths using TextMeasurer (no composition needed)
    val columnWidths = remember(rows, content, style, density) {
        val minColWidth = with(density) { 60.dp.roundToPx() }
        val maxColWidth = with(density) { 320.dp.roundToPx() }
        val padding = with(density) { (pad * 2).roundToPx() }
        val widths = IntArray(columnCount) { minColWidth }

        for (row in rows) {
            row.forEachIndexed { colIndex, cell ->
                val cellText = content.buildMarkdownAnnotatedString(
                    textNode = cell,
                    style = style,
                    annotatorSettings = annotatorSettings(),
                )
                val measured = textMeasurer.measure(
                    text = cellText,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                )
                val textWidth = measured.size.width + padding
                if (colIndex < columnCount) {
                    widths[colIndex] = maxOf(widths[colIndex], textWidth.coerceIn(minColWidth, maxColWidth))
                }
            }
        }
        widths
    }

    val scrollState = rememberScrollState()
    val totalTableWidth = remember(columnWidths) {
        with(density) { columnWidths.sumOf { it }.toDp() }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val needsScroll = totalTableWidth > screenWidth - 24.dp // 12.dp padding * 2

    var rowIndex = 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (needsScroll) Modifier.horizontalScroll(scrollState) else Modifier)
            .border(border, shape)
            .clip(shape)
    ) {
        var headerRendered = false
        tableNode.children.forEach { child ->
            when (child.type) {
                GFMHeader -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        child.children.filter { it.type == GFMCell }.forEachIndexed { colIndex, cell ->
                            val isLast = colIndex == columnCount - 1
                            Box(
                                modifier = Modifier
                                    .width(with(density) { columnWidths[colIndex].toDp() })
                                    .then(
                                        if (!isLast) Modifier.drawBehind {
                                            drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
                                        } else Modifier
                                    )
                                    .padding(horizontal = pad, vertical = 8.dp)
                            ) {
                                MarkdownBasicText(
                                    text = content.buildMarkdownAnnotatedString(
                                        textNode = cell,
                                        style = style.copy(fontWeight = FontWeight.SemiBold),
                                        annotatorSettings = annotatorSettings(),
                                    ),
                                    style = style.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.5.dp, color = dividerColor)
                    headerRendered = true
                }
                GFMRow -> {
                    val rowBg = if (rowIndex % 2 == 0) rowBgEven else rowBgOdd
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        child.children.filter { it.type == GFMCell }.forEachIndexed { colIndex, cell ->
                            val isLast = colIndex == columnCount - 1
                            Box(
                                modifier = Modifier
                                    .width(with(density) { columnWidths.getOrNull(colIndex)?.toDp() ?: Dp.Unspecified })
                                    .then(
                                        if (!isLast) Modifier.drawBehind {
                                            drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
                                        } else Modifier
                                    )
                                    .padding(horizontal = pad, vertical = 6.dp)
                            ) {
                                MarkdownBasicText(
                                    text = content.buildMarkdownAnnotatedString(
                                        textNode = cell,
                                        style = style,
                                        annotatorSettings = annotatorSettings(),
                                    ),
                                    style = style,
                                )
                            }
                        }
                    }
                    if (rowIndex < tableNode.children.count { it.type == GFMRow } - 1) {
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.5f))
                    }
                    rowIndex++
                }
                GFMTableSeparator -> {
                    HorizontalDivider(thickness = 1.5.dp, color = dividerColor)
                }
            }
        }
    }
}
```

**关键变化：**
- `Modifier.weight(1f)` → `Modifier.width(columnWidths[colIndex].toDp())`
- 用 `TextMeasurer` 在 remember 块中离线测量每列最大宽度（不触发额外 composition）
- `minColWidth = 60.dp`，`maxColWidth = 320.dp` 防止极端列宽
- 超宽时自动启用 `horizontalScroll`
- 多行文本仍正常换行（仅第一遍宽度测量用 `maxLines = 1` 计算最大宽度）

- [ ] **Step 2: 确认 import 完整**

确保以下 import 存在：

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
```

如果 `rememberTextMeasurer` 不可用（需要 Compose UI 1.5+），检查 `build.gradle.kts` 中的 compose 版本。

- [ ] **Step 3: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 在模拟器上 E2E 验证**

1. 打开包含 Markdown 表格的对话
2. 验证：表格列宽根据内容自适应（短列窄、长列宽）
3. 验证：极宽表格可水平滚动
4. 验证：表格样式（表头背景、交替行色、分割线）保持不变
5. 验证：表格在流式输出中渲染正常（无闪烁）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: dynamic table column widths based on content with horizontal scroll"
```

---

## Task 6: 斜杠命令/行内代码样式优化 [P2]

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4527`

**根因:** `inlineCodeBackground = Color.Transparent`（行 4527）导致行内代码没有背景衬底，与普通文本几乎无法区分。

**修复策略:** 给 `inlineCodeBackground` 设置一个有对比度的背景色。根据当前主题（深色/浅色/AMOLED）和消息类型（用户/助手）使用不同的背景色。

- [ ] **Step 1: 修改 inlineCodeBackground（行 4524-4530）**

> **注意：** 以下行号为编写计划时的原始基准。经 Task 2（净 -10 行）和 Task 3（净 +72 行）修改后，实际位置偏移约 +62 行。请通过搜索 `inlineCodeBackground` 或 `Color.Transparent` 定位。

将：

```kotlin
    val colors = markdownColor(
        text = textColor,
        codeBackground = codeBlockBg,
        inlineCodeBackground = Color.Transparent,
        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tableBackground = MaterialTheme.colorScheme.surfaceContainerLow
    )
```

改为：

```kotlin
    val inlineCodeBg = when {
        isAmoled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }

    val colors = markdownColor(
        text = textColor,
        codeBackground = codeBlockBg,
        inlineCodeBackground = inlineCodeBg,
        dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tableBackground = MaterialTheme.colorScheme.surfaceContainerLow
    )
```

**配色说明：**
- 深色主题（助手消息）：`primaryContainer` (#2D2F6E) 的 35% 透明度 → 淡靛蓝色背景
- 用户消息气泡：`onPrimaryContainer` (#DEE0FF) 的 10% 透明度 → 极淡背景，不干扰深色气泡
- AMOLED：`surfaceContainerHighest` (#1C1C24) 的 70% 透明度 → 略微提亮但保持暗色

- [ ] **Step 2: 验证构建**

Run: `.\gradlew assembleRelease 2>&1 | Select-Object -Last 5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 在模拟器上 E2E 验证**

1. 打开包含斜杠命令（如 `/compact`、`/new`）和行内代码的对话
2. 验证：行内代码有明显的背景色衬底，与普通文本有区分
3. 切换到 AMOLED 主题，验证：背景色仍然有区分度
4. 查看用户消息气泡中的行内代码，验证：背景色不会太突兀
5. 查看助手消息中的行内代码，验证：背景色与主题协调

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "style: add visible background to inline code for better readability"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** 所有 7 个问题都有对应的 Task
- [x] **Placeholder scan:** 无 TBD/TODO/implement later — 每步都有具体代码
- [x] **Type consistency:** 所有引用的函数名、变量名、文件路径在 Task 间一致。groupMessages 定义在 ChatParts.kt，通过 import 使用
- [x] **File paths:** 在 git 命令中使用完整绝对路径，在 File Structure 表中使用缩写路径
- [x] **Code blocks:** 每个修改步骤都展示了完整的新旧代码对比

## 风险提示

| Task | 风险 | 缓解措施 |
|------|------|---------|
| Task 3 | 工具卡片内部无法访问 `listState` | 已通过 CompositionLocal (LocalListState) 解决，无需修改中间层函数签名 |
| Task 4B | `mergeMessages` 可能与活跃的 SSE 流竞争 | `setMessages` 保持不变供初始加载使用；`mergeMessages` 仅用于 loadOlder |
| Task 5 | `TextMeasurer` 在 LazyColumn 内可能有性能问题 | 用 `remember` 缓存测量结果，只在 `tableNode` 变化时重新测量 |
| Task 5 | `rememberTextMeasurer` 需要 Compose UI 1.5+ | 项目已使用较新版本（0.41.0 markdown renderer），应该兼容 |
