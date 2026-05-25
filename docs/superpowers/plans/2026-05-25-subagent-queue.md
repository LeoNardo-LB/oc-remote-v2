# 子 Agent 查看体验 + QUEUED 排队标记 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 oc-remote 客户端实现 4 项体验增强：QUEUED 排队徽章、子 Agent Markdown 渲染、Part.Agent 标识卡片、子 Agent 跳转导航。

**Architecture:** 全客户端改动，不改服务端。QUEUED 徽章通过检测未完成 assistant 消息判定（列表位置判断 → Set ID membership）；Markdown 渲染复用已有 `MarkdownContent` composable；子 Agent 跳转从 `ToolState.Completed.metadata["sessionId"]` 获取子会话 ID，通过 `Screen.Chat` 路由 push（备选：`listSessionChildren` API 反查）。

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose BOM 2024.12.01, multiplatform-markdown-renderer, Jetpack Navigation Compose

### Prerequisites

| 要求 | 验证 |
|------|------|
| **JDK** | JDK 17+（Temurin），命令中使用 `D:\Develop\Scoop\apps\temurin17-jdk\current` |
| **Gradle** | 项目自带 Gradle Wrapper，`.\gradlew.bat --version` 确认 |
| **Android SDK** | compileSdk 34, minSdk 26, targetSdk 34 |
| **网络** | 代理：`127.0.0.1:7897`（无代理环境可移除 GRADLE_OPTS） |
| **前置知识** | Compose 状态提升模式、Navigation Compose back stack 语义、MockK/Turbine 测试 |
| **数据前提** | `Session.parentId: String?` 已存在（Session.kt:16）；`ToolState.Completed.metadata: Map<String, JsonElement>?` 已存在（ToolState.kt:54） |

### Task 依赖关系

| Task | 依赖 | 注意 |
|------|------|------|
| Task 1 | 无 | 修改 ChatUiState（行号 39-63），后续 Task 的 ChatUiState 行号基于 Task 1 完成后 |
| Task 2 | 无 | 独立修改 ChatScreen.kt |
| Task 3 | 无 | 独立修改 ChatScreen.kt |
| Task 4 | **Task 1** | 依赖 Task 1 新增的 `ChatUiState.queuedMessageIds` 字段；Step 1 修改 `ChatMessageBubble` 签名与 Task 1 Step 3 合并 |

> **行号偏移警告**：Task 1 在 ChatViewModel.kt 中新增代码后，后续 Task 引用的行号可能偏移。因 ChatUiState 字段新增在末尾（不影响前面代码），实际偏移极小。Task 4 Step 3 的行号（`349-369`）不受 Task 1 行号变化影响（因为 combine 中的修改在 `chatMessages` 计算之后）。

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 修改 | `app/.../ui/screens/chat/ChatViewModel.kt` | `ChatUiState` 新增 `queuedMessageIds`、`sessionParentId` 字段 |
| 修改 | `app/.../ui/screens/chat/ChatScreen.kt` | QUEUED 徽章、Markdown 渲染、Part.Agent 卡片、子 Agent 跳转按钮、子会话导航栏 |
| 修改 | `app/.../ui/navigation/NavGraph.kt` | 子会话跳转改用 push 策略 |

---

## Table of Contents

- [Task 1: QUEUED 徽章](#task-1-queued-徽章)
- [Task 2: TaskToolCard Markdown 渲染](#task-2-tasktoolcard-markdown-渲染)
- [Task 3: Part.Agent 渲染](#task-3-partagent-渲染)
- [Task 4: 子 Agent 跳转导航](#task-4-子-agent-跳转导航)
- [Acceptance Criteria](#acceptance-criteria)
- [Troubleshooting](#troubleshooting)

---

## Task 1: QUEUED 徽章

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt:39-63` (ChatUiState) + `:349-369` (聚合)
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:3470-3500` (ChatMessageBubble 头部)

### 背景

`Message.Assistant` 的 `time.completed: Long?` 字段：为 `null` 时表示消息仍在流式输出中（未完成）。OpenCode TUI 的做法：找到最后一条未完成的 assistant 消息，之后的所有 user 消息标记 QUEUED。

### 判断逻辑

```
chatMessages 中：
  找到最后一条 time.completed == null 的 assistant 消息
  → 如果找到，它之后的所有 user 消息加入 queuedMessageIds
  → 如果没找到（全部已完成），queuedMessageIds 为空
```

> 注意：Message.id 是 `String`，不能直接比大小。改用 **列表位置** 判断：`index > pendingAssistantIndex`。

- [ ] **Step 1: ChatUiState 新增 `queuedMessageIds` 字段**

在 `ChatViewModel.kt` 第 39-63 行 `ChatUiState` 末尾（`selectedAgent` 之后）添加：

```kotlin
    val queuedMessageIds: Set<String> = emptySet()
```

- [ ] **Step 2: 在 combine 聚合中计算 queuedMessageIds**

在 `ChatViewModel.kt` 第 259-283 行，`chatMessages` 计算完成后、构建 `ChatUiState` 之前，插入：

```kotlin
// Compute queued message IDs: messages sent while assistant is still generating
val pendingAssistantIndex = chatMessages.indexOfLast {
    it.message is Message.Assistant && it.message.time.completed == null
}
val queuedMessageIds = if (pendingAssistantIndex >= 0) {
    chatMessages.drop(pendingAssistantIndex + 1)
        .filter { it.isUser }
        .map { it.message.id }
        .toSet()
} else {
    emptySet<String>()
}
```

在第 349-369 行构建 `ChatUiState` 时，将 `queuedMessageIds` 传入：

```kotlin
ChatUiState(
    // ... 其他字段不变 ...
    queuedMessageIds = queuedMessageIds,
)
```

需要新增 import（文件顶部已有 `Message` import，无需新增）。

- [ ] **Step 3: ChatMessageBubble 中显示 QUEUED 徽章**

在 `ChatScreen.kt` 找到 `ChatMessageBubble` 函数（第 3470 行）。在助理消息头（第 3586 行 `if (!isUser)` 块）下方，用户消息气泡区域之前（约第 3625 行附近），添加用户消息的条件渲染：

```kotlin
// 用户消息头部（含 QUEUED 徽章）
if (isUser) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chatMessage.message.id in uiState.queuedMessageIds) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFFFD700),  // 金色
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "QUEUED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
```

注意：`ChatMessageBubble` 需要访问 `uiState`。确认 uiState 在此 Composable 作用域内可访问（通常作为参数传入或通过 ViewModel 暴露）。如果 `ChatMessageBubble` 内部没有直接引用 `uiState`，改为通过参数传入 `isQueued: Boolean`：

```kotlin
// ChatMessageBubble 签名修改
private fun ChatMessageBubble(
    chatMessage: ChatMessage,
    isQueued: Boolean = false,  // ← 新增
    onRevert: (() -> Unit)? = null,
    onCopyText: (() -> Unit)? = null
)
```

然后在 LazyColumn 中调用时传入（约第 2430 行附近）：

```kotlin
items(uiState.messages, key = { "msg_${it.message.id}" }) { chatMessage ->
    ChatMessageBubble(
        chatMessage = chatMessage,
        isQueued = chatMessage.message.id in uiState.queuedMessageIds,
        // ... 其他参数不变
    )
}
```

- [ ] **Step 4: 编译验证**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 运行已有测试确认无回归**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat test --no-daemon 2>&1 | Select-Object -Last 10`

Expected: `BUILD SUCCESSFUL`, 27 tests (EventReducerTest:10 + ChatViewModelPermissionTest:17), 0 failures

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: add QUEUED badge for user messages sent while assistant is busy"
```

---

## Task 2: TaskToolCard Markdown 渲染

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:5387-5394` (Text → MarkdownContent)

- [ ] **Step 1: 替换纯文本为 Markdown 渲染**

当前代码（第 5387-5394 行）：

```kotlin
Text(
    text = output.take(5000),
    style = CodeTypography.copy(fontSize = 12.sp, color = ...),
    ...
)
```

改为：

```kotlin
MarkdownContent(
    markdown = output,
    textColor = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer,
    isUser = false
)
```

同时移除 `heightIn(max = 300.dp)` 限制（约第 5382 行），改为合理的最大高度或直接移除，让 Markdown 内容自然展开：

```kotlin
.heightIn(max = 600.dp)  // 从 300.dp 增大
```

> `MarkdownContent` 已在 `PartContent` 中用于 `Part.Text` 渲染（第 3966 行），无需新增 import。它接受 `markdown: String, textColor: Color, isUser: Boolean` 三个参数，内部自动处理代码块语法高亮、链接、列表等。

- [ ] **Step 2: 编译验证**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: render sub-agent output with MarkdownContent instead of plain Text"
```

---

## Task 3: Part.Agent 渲染

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:4036-4038` (skip → Agent 卡片)

### 数据模型

`Part.Agent`（`Part.kt:187-194`）：

```kotlin
data class Agent(
    val id: String,
    val sessionId: String,
    val messageId: String,
    val name: String = "",
    val source: JsonElement? = null
) : Part()
```

- [ ] **Step 1: 将 Part.Agent 从 skip 列表中移出，渲染 Agent 标识卡片**

当前代码（第 4036-4038 行）：

```kotlin
// Ignore less relevant parts
is Part.Snapshot, is Part.Subtask, is Part.Compaction,
is Part.Agent, is Part.SessionTurn, is Part.Unknown -> { /* skip */ }
```

改为（将 `is Part.Agent` 从 skip 列表中移出，添加独立分支）：

```kotlin
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
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
```

> 注意：`Icons.Default.Android` 在 `material-icons-core` 中，项目已使用。不需要额外依赖。

- [ ] **Step 2: 编译验证**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "feat: render Part.Agent as a compact identifier card instead of skipping"
```

---

## Task 4: 子 Agent 跳转导航

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt` — TaskToolCard 添加跳转按钮 + 子会话顶部导航栏
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt` — ChatUiState 新增 `sessionParentId` 字段
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/navigation/NavGraph.kt:500-509` — 改为 push 策略

### 子会话 ID 获取路径

OpenCode 服务端在 task 工具 `Completed` 状态的 `metadata` 中写入 `sessionId`：

```kotlin
val subSessionId = (tool.state as? ToolState.Completed)
    ?.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
```

### 导航策略

当前 `onNavigateToSession` 使用 `popUpTo` replace 策略（跳转后不能返回）。子 Agent 跳转改为 **push** 策略：不 pop，自然压栈，用户可按返回键退回父会话。

- [ ] **Step 1: 在 PartContent 和 TaskToolCard 的调用链中添加 `onNavigateToSession` 回调**

**1a.** 在 `PartContent` 函数签名添加参数 `onViewSubSession`：

```kotlin
@Composable
private fun PartContent(
    part: Part,
    textColor: Color,
    isUser: Boolean = false,
    onViewSubSession: ((String) -> Unit)? = null  // ← 新增
)
```

在 `TaskToolCard` 签名添加参数：

```kotlin
@Composable
private fun TaskToolCard(
    tool: Part.Tool,
    onViewSubSession: ((String) -> Unit)? = null  // ← 新增
)
```

在 `PartContent` 中调用 `TaskToolCard` 处（约第 3993 行）传递回调：

```kotlin
"task" -> TaskToolCard(tool = part, onViewSubSession = onViewSubSession)
```

在 `ChatMessageBubble` 中调用 `PartContent` 处传递上层回调。在 `ChatScreen` 顶层（第 2430 行附近的 LazyColumn），将 `onNavigateToChildSession` 传入 `ChatMessageBubble`，最终到达 `PartContent`。

**注意：与 Task 1 Step 3 合并 `ChatMessageBubble` 签名。** 合并后的完整签名为：

```kotlin
@Composable
private fun ChatMessageBubble(
    chatMessage: ChatMessage,
    isQueued: Boolean = false,           // ← Task 1 新增
    onViewSubSession: ((String) -> Unit)? = null,  // ← Task 4 新增
    onRevert: (() -> Unit)? = null,
    onCopyText: (() -> Unit)? = null
)
```

调用处（LazyColumn items 中）同时传入两个新增参数：

```kotlin
ChatMessageBubble(
    chatMessage = chatMessage,
    isQueued = chatMessage.message.id in uiState.queuedMessageIds,
    onViewSubSession = onNavigateToChildSession,
    // ... 其他参数不变
)
```

- [ ] **Step 2: TaskToolCard 中添加"查看详情"按钮**

在 `TaskToolCard` 内，`extractToolOutput` 获取输出后（第 5314 行附近），提取子会话 ID：

```kotlin
val subSessionId = (tool.state as? ToolState.Completed)
    ?.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
```

在 `Surface` 底部（第 5399 行 `}` 之前），`AnimatedVisibility` 块之后、`Column` 闭合之前，添加"查看详情"按钮：

```kotlin
// "查看详情" 按钮 — 仅当有子会话 ID 且有回调时显示
if (subSessionId != null && onViewSubSession != null && hasOutput) {
    TextButton(
        onClick = { onViewSubSession(subSessionId) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = "查看详情",
            style = MaterialTheme.typography.labelMedium
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
    }
}
```

- [ ] **Step 3: ChatUiState 新增 `sessionParentId` 字段**

在 `ChatViewModel.kt` 第 39-63 行 `ChatUiState` 末尾添加：

```kotlin
    val sessionParentId: String? = null
```

在 combine 聚合中（第 349-369 行）传入：

```kotlin
ChatUiState(
    // ... 其他字段不变 ...
    sessionParentId = session?.parentId,
)
```

- [ ] **Step 4: 子会话 ChatScreen 顶部显示子会话标识行**

当前 `TopAppBar` 的 `title` 是 Column（第 1441 行）。当 `uiState.sessionParentId != null` 时，在 title Column 上方添加一个紧凑的子会话标识行：

```kotlin
title = {
    Column {
        // 子会话标识行（仅在 parentId 非空时显示）
        if (uiState.sessionParentId != null) {
            Row(
                modifier = Modifier.padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SubdirectoryArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "子会话",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
        // 原有标题
        Text(
            text = uiState.sessionTitle,
            // ... 不变 ...
        )
        // 原有副标题（token/成本）
        // ... 不变 ...
    }
}
```

- [ ] **Step 5: NavGraph 添加子 Agent 跳转回调（push 策略）**

当前 `onNavigateToSession`（第 495-509 行）使用 `popUpTo` replace 策略——适合新建/分支（跳转后 back 键回会话列表）。子 Agent 跳转需要 push 策略——back 键回父会话。

在 NavGraph 中添加**第二个回调** `onNavigateToChildSession`（push 策略，无 popUpTo）：

在 `NavGraph.kt` 第 509 行（`onNavigateToSession` 闭包闭合之后），`ChatScreen` 的调用处（约第 490 行附近），添加新参数：

```kotlin
composable(Screen.Chat.route) { backStackEntry ->
    // ... 现有参数提取（serverUrl, username 等）...
    ChatScreen(
        // ... 现有参数不变 ...
        onNavigateToSession = { newSessionId ->
            val route = Screen.Chat.createRoute(
                serverUrl = serverUrl, username = username, password = password,
                serverName = serverName, serverId = serverId, sessionId = newSessionId
            )
            navController.navigate(route) {
                popUpTo("sessions?serverUrl=$encodedUrl&...") { inclusive = false }
            }
        },
        // 新增 — 子 Agent 跳转（push，无 popUpTo，防重复压栈）
        onNavigateToChildSession = { childSessionId ->
            val route = Screen.Chat.createRoute(
                serverUrl = serverUrl, username = username, password = password,
                serverName = serverName, serverId = serverId, sessionId = childSessionId
            )
            navController.navigate(route) {
                launchSingleTop = true  // 防止快速重复点击导致多次压栈
            }
        },
    )
}
```

在 `ChatScreen.kt` 函数签名（约第 810 行附近）添加参数：

```kotlin
@Composable
fun ChatScreen(
    // ... 现有参数 ...
    onNavigateToSession: (String) -> Unit,
    onNavigateToChildSession: (String) -> Unit,  // ← 新增
    // ...
)
```

在 `TaskToolCard` 的"查看详情"按钮中调用 `onNavigateToChildSession(subSessionId)` 而非 `onNavigateToSession(subSessionId)`。

- [ ] **Step 6: 编译验证**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 运行已有测试确认无回归**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; $env:GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"; .\gradlew.bat test --no-daemon 2>&1 | Select-Object -Last 10`

Expected: `BUILD SUCCESSFUL`, 27 tests, 0 failures

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git add app/src/main/kotlin/dev/minios/ocremote/ui/navigation/NavGraph.kt
git commit -m "feat: add sub-agent jump navigation with push strategy and child session indicator"
```

---

## Acceptance Criteria

| # | 标准 | 验证方式 |
|---|------|----------|
| 1 | QUEUED 徽章：会话忙碌时发送的新消息显示金色 QUEUED 徽章 | 发送消息后，在 AI 回复过程中，观察后续用户消息气泡头部 |
| 2 | QUEUED 徽章消失：AI 回复完成后徽章消失 | AI 回复完成（finish ≠ null）后确认徽章不可见 |
| 3 | Markdown 渲染：子 Agent 输出正确渲染标题、代码块、列表 | 查看已有 subagent 工具输出的渲染效果 |
| 4 | 无截断：子 Agent 输出不限制 5000 字符 | 长输出完整可见 |
| 5 | Part.Agent 卡片：显示 agent 名称和来源 | 消息流中出现 agent 切换时可见 |
| 6 | 子 Agent 跳转：点击"查看详情"跳转到子会话 ChatScreen | 点击按钮后导航到新页面 |
| 7 | 子 Agent 返回：按 back 键可返回父会话 | 子会话页面按 back 回到父会话 |
| 8 | 子会话不存在时：无"查看详情"按钮（subSessionId 为 null 的 task 工具卡） | 检查无 metadata.sessionId 的已完成 task 工具卡 |
| 9 | 快速双击"查看详情"不重复压栈 | 连续两次点击，back 一次即返回父会话 |
| 10 | 编译通过：`assembleDebug` BUILD SUCCESSFUL | |
| 11 | 无回归：27 个现有测试全部通过 | |

---

## Troubleshooting

| 症状 | 可能原因 | 解决方案 |
|------|----------|----------|
| QUEUED 徽章不显示 | `pendingAssistantIndex` 未正确匹配 | 检查 `time.completed == null` 判断 — 确认服务端流式消息中 completed 确实为 null |
| QUEUED 徽章不消失 | 服务端未更新 `time.completed` | 确认 SSE `message.updated` 事件中 completed 时间戳是否正确更新 |
| Markdown 渲染样式错乱 | `MarkdownContent` 参数不匹配 | 参考 `PartContent` 中 `Part.Text` 的调用方式 |
| 子会话跳转后页面空白 | 子会话数据未加载 | 检查 `sessionId` 是否正确传入 ChatViewModel；确认 API 可以获取子会话消息 |
| 导航栈错误（back 键行为异常） | popUpTo 未正确移除 | 确认 NavGraph 中使用了 `navigate(route)` 而非 `navigate(route) { popUpTo(...) }` |
| 子会话跳转后无实时更新 | 子会话 SSE 事件未到达 | EventReducer 全局共享，确认按 sessionId 分区正确分发 |
| QUEUED 徽章永久不消失 | 网络断开导致 SSE 事件丢失 | 连接恢复后 `loadSession()` 重新拉取数据；当前依赖 SSE 恢复 |
| 子会话页面空/报错 | 子 session 已删除或网络错误 | 依赖 ChatScreen 已有错误 UI；按 back 返回父会话 |
| "查看详情"重复压栈 | 快速双击 | 已使用 `launchSingleTop = true` 防护 |
