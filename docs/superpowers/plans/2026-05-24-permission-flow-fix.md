# Permission 流完整修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复权限请求（PermissionAsked）在客户端无法被用户看到和响应的 P0 bug，并对齐 Question 流的防御性机制（乐观清除 + REST 恢复）。

**Architecture:** 在 EventReducer 层补充 `removePermission()` 和 `setPermissions()` 方法；在 ChatViewModel 层补充 `loadPendingPermissions()` 并在 init 块调用；在 `replyToPermission()` 中添加乐观清除；在 UI 层将 PermissionCard 从内联列表卡片增强为带视觉区分的样式，并确保自动滚动逻辑能正确将用户带到权限卡。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow, kotlinx.serialization

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 修改 | `data/repository/EventReducer.kt` | 添加 `removePermission()` 和 `setPermissions()` |
| 修改 | `ui/screens/chat/ChatViewModel.kt` | 添加 `loadPendingPermissions()`，修改 `replyToPermission()`，修改 init 块 |
| 修改 | `ui/screens/chat/ChatScreen.kt` | 增强 PermissionCard 样式，添加防重复提交状态 |
| 不动 | `data/api/OpenCodeApi.kt` | `listPendingPermissions` API 已存在，无需改动 |
| 不动 | `domain/model/SseEvent.kt` | `PermissionAsked` 模型已对齐 V2，无需改动 |
| 不动 | `data/api/SseClient.kt` | SSE 解析逻辑已完善，无需改动 |

---

## Task 1: EventReducer 补充 removePermission 和 setPermissions

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/data/repository/EventReducer.kt:294-296`

在 `handlePermissionReplied()` 方法之后、`// ============ Question Events ============` 注释之前，添加两个公共方法。

- [ ] **Step 1: 在 EventReducer.kt 第 294 行之后添加 `removePermission()` 和 `setPermissions()`**

在 `handlePermissionReplied()` 的闭合大括号（第 294 行）之后、第 296 行 `// ============ Question Events ============` 之前，插入：

```kotlin

    /**
     * Optimistically remove a permission from the pending list.
     * Called after a successful API reply, in case the SSE event doesn't arrive.
     */
    fun removePermission(permissionId: String) {
        _permissions.update { current ->
            current.mapValues { (_, permissions) ->
                permissions.filter { it.id != permissionId }
            }
        }
    }

    /**
     * Set pending permissions for a session (loaded from REST API on session open).
     */
    fun setPermissions(sessionId: String, permissions: List<SseEvent.PermissionAsked>) {
        _permissions.update { current ->
            if (permissions.isEmpty()) {
                current - sessionId
            } else {
                current + (sessionId to permissions)
            }
        }
    }
```

- [ ] **Step 2: 验证编译通过**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/data/repository/EventReducer.kt
git commit -m "feat: add removePermission() and setPermissions() to EventReducer"
```

---

## Task 2: ChatViewModel 添加 loadPendingPermissions 并修改 init 块

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt:415-419` (init 块)
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt:540` (loadPendingQuestions 之后)

在 `loadPendingQuestions()` 方法之后（第 540 行），添加 `loadPendingPermissions()` 方法。

**DTO → SseEvent 映射注意**：`PermissionRequest`（REST DTO）的 `always` 字段类型为 `List<String>`，而 `SseEvent.PermissionAsked` 的 `always` 字段类型为 `Boolean`。`metadata` 字段也存在 `Map<String, JsonElement>` vs `Map<String, String>` 的差异。需要在映射时做类型转换。

- [ ] **Step 1: 在 ChatViewModel.kt 的 `loadPendingQuestions()` 方法之后添加 `loadPendingPermissions()`**

在第 540 行（`loadPendingQuestions()` 的闭合大括号）之后、第 542 行（注释 `// Removed initModelFromMessages`）之前，插入：

```kotlin

    private suspend fun loadPendingPermissions() {
        try {
            val allPermissions = api.listPendingPermissions(conn, directory = sessionDirectory)
            if (BuildConfig.DEBUG) Log.d(TAG, "loadPendingPermissions: ${allPermissions.size} total pending (directory=$sessionDirectory), filtering for session $sessionId")
            val sessionPermissions = allPermissions
                .filter { it.sessionId == sessionId }
                .map { req ->
                    SseEvent.PermissionAsked(
                        id = req.id,
                        sessionId = req.sessionId,
                        permission = req.permission,
                        patterns = req.patterns,
                        metadata = req.metadata?.mapValues { (_, v) ->
                            // JsonElement → String: primitives use content, others use toString
                            v.jsonPrimitive.contentOrNull ?: v.toString()
                        },
                        always = req.always.isNotEmpty(), // List<String> → Boolean: non-empty means "always"
                        tool = req.tool
                    )
                }
            if (sessionPermissions.isNotEmpty()) {
                eventReducer.setPermissions(sessionId, sessionPermissions)
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${sessionPermissions.size} pending permissions for session $sessionId")
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "No pending permissions for session $sessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load pending permissions: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
```

- [ ] **Step 2: 在 init 块中调用 `loadPendingPermissions()`**

将第 415-420 行的 init 协程块：

```kotlin
        viewModelScope.launch {
            currentMessageLimit = settingsRepository.initialMessageCount.first()
            loadSession()
            loadMessages()
            loadPendingQuestions()
        }
```

改为：

```kotlin
        viewModelScope.launch {
            currentMessageLimit = settingsRepository.initialMessageCount.first()
            loadSession()
            loadMessages()
            loadPendingQuestions()
            loadPendingPermissions()
        }
```

- [ ] **Step 3: 验证编译通过**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git commit -m "feat: add loadPendingPermissions() with REST recovery on session open"
```

---

## Task 3: ChatViewModel.replyToPermission 添加乐观清除

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt:796-810`

参照 `replyToQuestion()` 的模式（第 830-847 行），在 API 调用成功后乐观移除权限卡。

- [ ] **Step 1: 修改 `replyToPermission()` 方法**

将第 796-810 行：

```kotlin
    fun replyToPermission(requestId: String, reply: String) {
        viewModelScope.launch {
            try {
                api.replyToPermission(
                    conn = conn,
                    requestId = requestId,
                    reply = reply,
                    directory = sessionDirectory
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Replied to permission $requestId with $reply")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reply to permission", e)
            }
        }
    }
```

改为：

```kotlin
    fun replyToPermission(requestId: String, reply: String) {
        viewModelScope.launch {
            try {
                val success = api.replyToPermission(
                    conn = conn,
                    requestId = requestId,
                    reply = reply,
                    directory = sessionDirectory
                )
                if (success) {
                    // Optimistically remove the permission card — SSE event may arrive late or not at all
                    eventReducer.removePermission(requestId)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Replied to permission $requestId with $reply (success=$success)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reply to permission $requestId: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }
```

**关键变更**：
- `api.replyToPermission()` 返回值赋给 `success`（该方法已返回 `Boolean`）
- 成功时调用 `eventReducer.removePermission(requestId)` 乐观清除
- 日志增强，包含 `success` 状态
- 错误日志格式对齐 `replyToQuestion` 的风格

- [ ] **Step 2: 验证编译通过**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModel.kt
git commit -m "fix: add optimistic removal in replyToPermission to prevent stuck cards"
```

---

## Task 4: 增强 PermissionCard 样式和防重复提交

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:5868-5950`

PermissionCard 当前使用 `tertiaryContainer` 颜色。与 QuestionCard（同为内联卡片）保持内联风格一致，但通过以下方式区分权限特点：
1. 添加 `submitted` 防重复提交状态（参照 QuestionCard 的 `var submitted` 模式）
2. 提交后显示加载指示器和禁用按钮
3. 使用更醒目的 `errorContainer` / `error` 色调突出安全敏感性（区别于 Question 的中性色调）

- [ ] **Step 1: 重写 PermissionCard Composable**

将第 5868-5950 行的完整 `PermissionCard` 函数替换为：

```kotlin
@Composable
private fun PermissionCard(
    permission: SseEvent.PermissionAsked,
    onOnce: () -> Unit,
    onAlways: () -> Unit,
    onReject: () -> Unit
) {
    val isAmoled = isAmoledTheme()
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var submitted by remember { mutableStateOf(false) }

    // Use error-container colors to signal security sensitivity (distinct from Question's tertiary)
    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isAmoled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer
    val buttonTint = if (isAmoled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) else null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: security icon + "Permission Request" title
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = buttonTint
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                if (submitted) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(start = 4.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                }
            }
            // Permission description
            Text(
                text = permission.permission,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            // File patterns (if any)
            if (permission.patterns.isNotEmpty()) {
                Text(
                    text = permission.patterns.joinToString(", "),
                    style = CodeTypography.copy(
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (submitted) return@OutlinedButton
                        performHaptic(hapticView, hapticOn)
                        submitted = true
                        onReject()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !submitted,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.permission_deny), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        if (submitted) return@OutlinedButton
                        performHaptic(hapticView, hapticOn)
                        submitted = true
                        onOnce()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !submitted,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.permission_allow_once), maxLines = 1)
                }
                Button(
                    onClick = {
                        if (submitted) return@Button
                        performHaptic(hapticView, hapticOn)
                        submitted = true
                        onAlways()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !submitted,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.permission_allow_always), maxLines = 1)
                }
            }
        }
    }
}
```

**关键变更**：
- 颜色从 `tertiaryContainer` 改为 `errorContainer` — 突出安全敏感性，与 Question 的中性色调区分
- 添加 `submitted` 状态防止重复点击（参照 QuestionCard 模式）
- 提交后按钮 `enabled = false` + 显示小型 `CircularProgressIndicator`
- AMOLED 模式边框从 `tertiary` 改为 `error` 色调

- [ ] **Step 2: 确认 ChatScreen.kt 中 PermissionCard 的调用方式无需修改**

PermissionCard 的函数签名（参数列表）未变，只是内部实现增强。LazyColumn 中的调用代码（第 2431-2442 行）无需修改：

```kotlin
items(
    uiState.pendingPermissions,
    key = { "perm_${it.id}" }
) { permission ->
    PermissionCard(
        permission = permission,
        onOnce = { viewModel.replyToPermission(permission.id, "once") },
        onAlways = { viewModel.replyToPermission(permission.id, "always") },
        onReject = { viewModel.replyToPermission(permission.id, "reject") }
    )
}
```

- [ ] **Step 3: 验证编译通过**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: enhance PermissionCard with error colors and anti-duplicate-submit guard"
```

---

## Task 5: 确保自动滚动在权限出现时生效

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:1387-1405`

当前自动滚动逻辑使用 `pendingCount`（permissions + questions 的总数）作为 `LaunchedEffect` 的 key 之一。当 `pendingCount` 增加时，如果 `autoScrollEnabled` 为 true，会自动滚到底部。

**问题**：如果用户之前手动滚动过（`autoScrollEnabled = false`），权限卡出现时不会触发自动滚动，用户看不到。

**修复**：当 pending permissions 数量变化时（从 0 变为非 0），强制启用一次自动滚动。

- [ ] **Step 1: 在 ChatScreen.kt 中修改自动滚动逻辑**

找到第 1387 行附近的自动滚动 `LaunchedEffect` 块。当前代码大致为：

```kotlin
val pendingCount = uiState.pendingPermissions.size + uiState.pendingQuestions.size
```

在这行之前，添加一个强制自动滚动的 `LaunchedEffect`：

```kotlin
// Force auto-scroll when new permissions or questions appear (user may have scrolled away)
val pendingCount = uiState.pendingPermissions.size + uiState.pendingQuestions.size
LaunchedEffect(pendingCount) {
    if (pendingCount > 0) {
        autoScrollEnabled = true
    }
}
```

**注意**：需要确认 `autoScrollEnabled` 变量在此作用域内是否可写。如果不是 `mutableStateOf`，需要找到其定义位置并确保可修改。如果 `autoScrollEnabled` 是 `remember { mutableStateOf(true) }` 类型的局部变量，则可以直接赋值。

- [ ] **Step 2: 确认 `autoScrollEnabled` 的定义方式和位置**

搜索 ChatScreen.kt 中 `autoScrollEnabled` 的定义。它应该是一个 `var autoScrollEnabled by remember { mutableStateOf(true) }` 之类的局部变量。确认它在第 1387 行之前已定义，且在同一 Composable 作用域内。

如果 `autoScrollEnabled` 不是一个可写的 state（例如是 val），则调整方案：改为在原有的 LaunchedEffect 中添加条件分支，当 `pendingCount > 0` 时无条件滚动（忽略 autoScrollEnabled 标志）。

替代方案（如果 autoScrollEnabled 不可直接赋值）— 修改原有 LaunchedEffect 的条件逻辑：

```kotlin
val pendingCount = uiState.pendingPermissions.size + uiState.pendingQuestions.size
val isBusy = uiState.sessionStatus is SessionStatus.Busy
LaunchedEffect(messageCount, lastPartCount, lastContentLength, pendingCount, isBusy) {
    if (messageCount > 0 && (autoScrollEnabled || pendingCount > 0)) {
        val lastIndex = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
        listState.scrollToItem(lastIndex)
    }
}
```

这样当有 pending 项时，无论 `autoScrollEnabled` 状态如何都会滚动。

- [ ] **Step 3: 验证编译通过**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: force auto-scroll when permission/question cards appear"
```

---

## Task 6: 端到端验证

**Files:** 无代码修改，仅验证

- [ ] **Step 1: 编译并安装到模拟器**

Run: `$env:JAVA_HOME="D:\Develop\Scoop\apps\temurin17-jdk\current"; .\gradlew.bat assembleDebug 2>&1 | Select-Object -Last 5`

Expected: `BUILD SUCCESSFUL`

安装 APK 到模拟器，连接测试服务端 `http://10.0.2.2:8765`。

- [ ] **Step 2: 验证场景**

1. **SSE 实时权限**：在服务端触发一个需要权限的操作（如执行 shell 命令），确认客户端出现 PermissionCard
2. **自动滚动**：先手动滚动到列表中间，触发权限请求，确认页面自动滚到底部显示 PermissionCard
3. **回复后清除**：点击 "Allow Once"，确认卡片立即消失（乐观清除）
4. **防重复提交**：点击按钮后确认按钮变为禁用状态
5. **REST 恢复**：在权限未回复时关闭应用并重新打开同一会话，确认权限卡仍然显示
6. **视觉区分**：确认 PermissionCard 使用红色调（errorContainer），与 QuestionCard 的中性色调明显区分

- [ ] **Step 3: Final commit if any hotfixes needed**

如果验证过程中发现需要修复的问题，提交修复后在此记录。
