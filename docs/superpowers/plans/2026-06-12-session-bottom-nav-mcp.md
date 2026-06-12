# Session Bottom Navigation + MCP Control Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 SessionListScreen 内新增底部导航栏（NavigationBar），Tab 1 为会话目录、Tab 2 为服务器级设置页（含 MCP 服务器启停面板）。

**Architecture:** 使用 HorizontalPager + NavigationBar 实现 2 Tab 切换。MCP API 集成遵循 Clean Architecture（Domain → Data → UI）。不新建 ViewModel，扩展 SessionListViewModel。

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Ktor, kotlinx.serialization

**Spec:** `docs/specs/2026-06-12-session-bottom-nav-mcp-design.md`

---

## File Map

### Create
| File | Responsibility | Depends on Task |
|------|---------------|-----------------|
| `domain/model/McpServerStatus.kt` | MCP 服务器状态领域模型 | — |
| `domain/repository/McpRepository.kt` | MCP 仓库接口 | Task 2 |
| `data/dto/response/McpResponses.kt` | MCP API 响应 DTO | — |
| `data/repository/McpRepositoryImpl.kt` | MCP 仓库实现（合并 /mcp + /config） | Task 2, 3, 4 |
| `ui/screens/sessions/ServerSettingsContent.kt` | Page 1 设置页内容 | Task 6 |
| `ui/screens/sessions/components/McpServerRow.kt` | MCP 服务器单行组件 | Task 2 |

### Modify
| File | Change | Depends on Task |
|------|--------|-----------------|
| `app/build.gradle.kts` | 添加 compose-foundation 依赖 | — |
| `data/dto/response/ConfigResponses.kt` | 扩展 mcp 字段 + McpServerConfig | Task 3 |
| `data/api/OpenCodeApi.kt` | 添加 3 个 MCP API 方法 | Task 3 |
| `di/DomainModule.kt` | 添加 McpRepository @Binds | Task 4 |
| `ui/screens/sessions/SessionListViewModel.kt` | 添加 MCP 状态和操作方法 | Task 4 |
| `ui/screens/sessions/SessionListScreen.kt` | 添加 NavigationBar + HorizontalPager + 重构 | Task 5, 7 |

### Dependency Graph

```
Task 1 (build.gradle.kts) ──────────────────────┐
Task 2 (Domain: model + interface) ──────────────┤
Task 3 (Data: DTO + Config) ── depends Task 2 ──┤
Task 4 (Data: API + Repo + DI) ── depends Task 3 ├→ Task 8 (UI: Screen refactor) → Task 9 (verify)
Task 5 (ViewModel) ── depends Task 4 ────────────┤
Task 6 (UI: McpServerRow) ── depends Task 2 ─────┤
Task 7 (UI: ServerSettingsContent) ── depends Task 6 ┘
```

> Task 1/2/3 可并行；Task 6 可与 Task 3/4 并行（仅依赖 Task 2 的模型）。

---

## Task 1: 添加 compose-foundation 依赖

**Files:**
- Modify: `app/build.gradle.kts`（compose 依赖块，约 line 116-126）

- [ ] **Step 1: 添加 foundation 依赖**

在 `app/build.gradle.kts` 的 compose 依赖块中，`material3` 行之后，添加：

```kotlin
    implementation("androidx.compose.foundation:foundation")
```

插入位置：在 `implementation("androidx.compose.material3:material3")` 行之后、`implementation("androidx.compose.material3:material3-window-size-class")` 行之前。

> 注：项目通过 Compose BOM `2026.05.01` 管理版本，无需指定具体版本号。

- [ ] **Step 2: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add compose-foundation dependency for HorizontalPager"
```

---

## Task 2: Domain 层 — McpServerStatus 模型 + McpRepository 接口

**Depends on:** 无（可与 Task 1 并行）

**Files:**
- Create: `app/src/main/kotlin/dev/minios/ocremote/domain/model/McpServerStatus.kt`
- Create: `app/src/main/kotlin/dev/minios/ocremote/domain/repository/McpRepository.kt`

- [ ] **Step 1: 创建 McpServerStatus 数据类**

```kotlin
package dev.minios.ocremote.domain.model

data class McpServerStatus(
    val name: String,
    val type: String,         // "local" | "remote"
    val status: String,       // connected | disabled | failed | needs_auth | needs_client_registration
    val command: List<String>? = null,  // local type
    val url: String? = null,            // remote type
)
```

- [ ] **Step 2: 创建 McpRepository 接口**

```kotlin
package dev.minios.ocremote.domain.repository

import dev.minios.ocremote.domain.model.McpServerStatus

interface McpRepository {
    suspend fun getMcpServers(): Result<List<McpServerStatus>>
    suspend fun toggleMcpServer(name: String, connect: Boolean): Result<Boolean>
    fun setConnection(conn: dev.minios.ocremote.data.api.ServerConnection)
}
```

> 注：`setConnection` 暴露在接口上是为了让 ViewModel 在 init 时传入当前 ServerConnection。McpRepositoryImpl 内部持有该连接用于 API 调用。

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/domain/model/McpServerStatus.kt app/src/main/kotlin/dev/minios/ocremote/domain/repository/McpRepository.kt
git commit -m "feat: add McpServerStatus model and McpRepository interface"
```

---

## Task 3: Data 层 — DTO + Config 扩展

**Depends on:** Task 2（McpServerConfig 引用）

**Files:**
- Create: `app/src/main/kotlin/dev/minios/ocremote/data/dto/response/McpResponses.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/data/dto/response/ConfigResponses.kt`

- [ ] **Step 1: 创建 MCP 响应 DTO**

```kotlin
package dev.minios.ocremote.data.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class McpStatusEntry(
    val status: String  // connected | disabled | failed | needs_auth | needs_client_registration
)

@Serializable
data class McpServerConfig(
    val type: String? = null,
    val command: List<String>? = null,
    val enabled: Boolean = true,
    val url: String? = null,
    val environment: Map<String, String>? = null,
    val headers: Map<String, String>? = null
)
```

- [ ] **Step 2: 扩展 ServerConfigResponse 添加 mcp 字段**

在 `ConfigResponses.kt` 的 `ServerConfigResponse` data class 中添加 mcp 字段。修改后的完整文件：

```kotlin
package dev.minios.ocremote.data.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfigResponse(
    @SerialName("disabled_providers") val disabledProviders: List<String> = emptyList(),
    @SerialName("enabled_providers") val enabledProviders: List<String>? = null,
    val model: String? = null,
    @SerialName("small_model") val smallModel: String? = null,
    @SerialName("default_agent") val defaultAgent: String? = null,
    val mcp: Map<String, McpServerConfig>? = null
)
```

> 注意：`McpServerConfig` 已在 `McpResponses.kt` 中定义，此处引用。由于 `mcp` 字段默认为 null，现有反序列化不受影响。

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/data/dto/response/McpResponses.kt app/src/main/kotlin/dev/minios/ocremote/data/dto/response/ConfigResponses.kt
git commit -m "feat: add MCP DTOs and extend ServerConfigResponse with mcp field"
```

---

## Task 4: Data 层 — OpenCodeApi MCP 方法 + McpRepositoryImpl

**Depends on:** Task 3

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/data/api/OpenCodeApi.kt`
- Create: `app/src/main/kotlin/dev/minios/ocremote/data/repository/McpRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/di/DomainModule.kt`

- [ ] **Step 1: 在 OpenCodeApi 中添加 3 个 MCP API 方法**

在 OpenCodeApi class 的最后一个方法之后（`RestSessionStatusInfo` data class 之前）添加：

```kotlin
    // ============ MCP ============

    suspend fun getMcpStatus(conn: ServerConnection): Map<String, McpStatusEntry> {
        return httpClient.get("${conn.baseUrl}/mcp") {
            conn.authHeader?.let { header("Authorization", it) }
        }.body()
    }

    suspend fun connectMcpServer(conn: ServerConnection, name: String): Boolean {
        return httpClient.post("${conn.baseUrl}/mcp/$name/connect") {
            conn.authHeader?.let { header("Authorization", it) }
        }.body()
    }

    suspend fun disconnectMcpServer(conn: ServerConnection, name: String): Boolean {
        return httpClient.post("${conn.baseUrl}/mcp/$name/disconnect") {
            conn.authHeader?.let { header("Authorization", it) }
        }.body()
    }
```

> 认证模式：`conn.authHeader?.let { header("Authorization", it) }`，与 OpenCodeApi 所有现有方法（`getHealth`、`fetchSessionStatus` 等）一致。OpenCodeApi 已有 `import dev.minios.ocremote.data.dto.response.*`（通配符），无需额外 import。

- [ ] **Step 2: 创建 McpRepositoryImpl**

```kotlin
package dev.minios.ocremote.data.repository

import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.api.ServerConnection
import dev.minios.ocremote.domain.model.McpServerStatus
import dev.minios.ocremote.domain.repository.McpRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpRepositoryImpl @Inject constructor(
    private val api: OpenCodeApi
) : McpRepository {

    @Volatile
    private var connection: ServerConnection? = null

    override fun setConnection(conn: ServerConnection) {
        connection = conn
    }

    private fun requireConnection(): ServerConnection =
        connection ?: throw IllegalStateException("McpRepository: ServerConnection not set. Call setConnection() first.")

    override suspend fun getMcpServers(): Result<List<McpServerStatus>> = runCatching {
        val conn = requireConnection()
        val statusMap = api.getMcpStatus(conn)
        val configMap = api.getConfig(conn).mcp ?: emptyMap()

        statusMap.map { (name, entry) ->
            val config = configMap[name]
            McpServerStatus(
                name = name,
                type = config?.type ?: "local",
                status = entry.status,
                command = config?.command,
                url = config?.url,
            )
        }
    }

    override suspend fun toggleMcpServer(name: String, connect: Boolean): Result<Boolean> = runCatching {
        val conn = requireConnection()
        if (connect) {
            api.connectMcpServer(conn, name)
        } else {
            api.disconnectMcpServer(conn, name)
        }
    }
}
```

> **ServerConnection 获取方案**：ViewModel 在 init 块中调用 `mcpRepository.setConnection(conn)` 传入当前连接（见 Task 5）。使用 `@Volatile` 保证线程可见性。
>
> **api.getConfig(conn)**：OpenCodeApi 已有此方法（约 line 800），返回 `ServerConfigResponse`。Task 3 已为其扩展了 `mcp` 字段。

- [ ] **Step 3: 在 DomainModule.kt 添加 @Binds**

在 `DomainModule.kt` 末尾（`bindSettingsRepository` 之后）添加：

```kotlin
    @Binds
    abstract fun bindMcpRepository(impl: McpRepositoryImpl): McpRepository
```

在文件顶部添加 import：
```kotlin
import dev.minios.ocremote.data.repository.McpRepositoryImpl
import dev.minios.ocremote.domain.repository.McpRepository
```

- [ ] **Step 4: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/data/api/OpenCodeApi.kt app/src/main/kotlin/dev/minios/ocremote/data/repository/McpRepositoryImpl.kt app/src/main/kotlin/dev/minios/ocremote/di/DomainModule.kt
git commit -m "feat: add MCP API methods, McpRepositoryImpl, and DI binding"
```

---

## Task 5: ViewModel 层 — 扩展 SessionListViewModel

**Depends on:** Task 4

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/SessionListViewModel.kt`

- [ ] **Step 1: 添加 MCP 相关字段和方法**

在 SessionListViewModel 的构造函数中添加 `McpRepository` 依赖：

```kotlin
@HiltViewModel
class SessionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventDispatcher: EventDispatcher,
    private val api: OpenCodeApi,
    private val manageSessionUseCase: ManageSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val draftRepository: DraftRepository,
    private val mcpRepository: McpRepository  // 新增
) : ViewModel()
```

在 ViewModel 的 `init` 块中（或 conn 创建之后）添加：

```kotlin
    mcpRepository.setConnection(conn)
```

> **关键**：SessionListViewModel 已有 `val conn = ServerConnection.from(serverUrl, username, ...)`（约 line 98）。必须在 init 中将 conn 传递给 mcpRepository，否则 McpRepositoryImpl.requireConnection() 会抛出 IllegalStateException。

在 StateFlow 声明区域（约 line 112 之后）添加：

```kotlin
    private val _mcpServers = MutableStateFlow<List<McpServerStatus>>(emptyList())
    val mcpServers: StateFlow<List<McpServerStatus>> = _mcpServers.asStateFlow()

    private val _mcpLoading = MutableStateFlow<String?>(null)
    val mcpLoading: StateFlow<String?> = _mcpLoading.asStateFlow()

    private val _mcpInitialLoading = MutableStateFlow(false)
    val mcpInitialLoading: StateFlow<Boolean> = _mcpInitialLoading.asStateFlow()

    private val _mcpError = MutableSharedFlow<String>()
    val mcpError: SharedFlow<String> = _mcpError.asSharedFlow()
```

在 ViewModel 末尾添加方法：

```kotlin
    fun loadMcpServers() {
        viewModelScope.launch {
            _mcpInitialLoading.value = true
            mcpRepository.getMcpServers()
                .onSuccess { _mcpServers.value = it }
                .onFailure {
                    // 保持上次状态或空列表
                    // 404 等错误通过 mcpError SharedFlow 通知 UI
                    _mcpError.emit(it.message ?: "Failed to load MCP servers")
                }
            _mcpInitialLoading.value = false
        }
    }

    fun toggleMcpServer(name: String) {
        if (_mcpLoading.value == name) return  // 防止重复操作
        val server = _mcpServers.value.find { it.name == name } ?: return
        val connect = server.status != "connected"
        _mcpLoading.value = name

        viewModelScope.launch {
            mcpRepository.toggleMcpServer(name, connect)
                .onSuccess {
                    // 刷新全部状态
                    mcpRepository.getMcpServers()
                        .onSuccess { _mcpServers.value = it }
                }
                .onFailure {
                    // Switch 自然回弹（因为未更新 _mcpServers），通过 Snackbar 通知用户
                    _mcpError.emit("Failed to ${if (connect) "connect" else "disconnect"} $name")
                }
            _mcpLoading.value = null
        }
    }
```

需要在文件顶部添加 import：
```kotlin
import dev.minios.ocremote.domain.model.McpServerStatus
import dev.minios.ocremote.domain.repository.McpRepository
```

> **错误处理机制**：`_mcpError: SharedFlow<String>` 用于 emit 错误消息，UI 层（Task 8）收集并显示 Snackbar。Toggle 失败时 Switch 自然回弹（因为 _mcpServers 未更新），无需额外回弹逻辑。

- [ ] **Step 2: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/SessionListViewModel.kt
git commit -m "feat: add MCP state, error handling, and operations to SessionListViewModel"
```

---

## Task 6: UI 层 — McpServerRow 组件

**Depends on:** Task 2（McpServerStatus 模型）。可与 Task 3/4 并行。

**Files:**
- Create: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/components/McpServerRow.kt`

- [ ] **Step 1: 创建 McpServerRow Composable**

```kotlin
package dev.minios.ocremote.ui.screens.sessions.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.minios.ocremote.domain.model.McpServerStatus

@Composable
fun McpServerRow(
    server: McpServerStatus,
    isLoading: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = buildString {
                    append(server.type)
                    append(" · ")
                    append(statusDot(server.status))
                    append(" ")
                    append(server.status)
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(server.status)
            )
        }
        Switch(
            checked = server.status == "connected",
            onCheckedChange = { onToggle() },
            enabled = !isLoading
                    && server.status != "needs_auth"
                    && server.status != "needs_client_registration"
        )
    }
}

private fun statusDot(status: String): String = when (status) {
    "connected" -> "●"
    "disabled" -> "○"
    "failed" -> "●"
    "needs_auth" -> "●"
    "needs_client_registration" -> "●"
    else -> "○"
}

private fun statusColor(status: String): Color = when (status) {
    "connected" -> Color(0xFF4CAF50)     // Green
    "disabled" -> Color.Gray
    "failed" -> Color(0xFFF44336)         // Red
    "needs_auth", "needs_client_registration" -> Color(0xFFFFC107) // Yellow/Amber
    else -> Color.Gray
}
```

- [ ] **Step 2: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/components/McpServerRow.kt
git commit -m "feat: add McpServerRow composable with status indicator and switch"
```

---

## Task 7: UI 层 — ServerSettingsContent 页面

**Depends on:** Task 6（McpServerRow）

**Files:**
- Create: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/ServerSettingsContent.kt`

- [ ] **Step 1: 创建 ServerSettingsContent Composable**

```kotlin
package dev.minios.ocremote.ui.screens.sessions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.minios.ocremote.domain.model.McpServerStatus
import dev.minios.ocremote.ui.screens.sessions.components.McpServerRow

@Composable
fun ServerSettingsContent(
    mcpServers: List<McpServerStatus>,
    mcpLoading: String?,
    mcpInitialLoading: Boolean,
    modifier: Modifier = Modifier,
    onToggleMcp: (name: String) -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = "MCP Servers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        when {
            mcpInitialLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
            mcpServers.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No MCP servers configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                items(count = mcpServers.size, key = { mcpServers[it].name }) { index ->
                    val server = mcpServers[index]
                    McpServerRow(
                        server = server,
                        isLoading = mcpLoading == server.name,
                        onToggle = { onToggleMcp(server.name) }
                    )
                }
            }
        }
    }
}
```

> **状态优先级**：Loading（CircularProgressIndicator）> 空列表（提示文本）> 正常列表。这避免了首次加载时闪现空状态文本。

- [ ] **Step 2: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/ServerSettingsContent.kt
git commit -m "feat: add ServerSettingsContent with loading/empty/list states"
```

---

## Task 8: UI 层 — 重构 SessionListScreen 添加 NavigationBar + HorizontalPager

**Depends on:** Task 5（ViewModel）, Task 7（ServerSettingsContent）

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/SessionListScreen.kt`

> ⚠️ 这是最大的改动。SessionListScreen 约 523 行。遵循 ChatScreen Editing Protocol：Read → Edit → Compile → Commit。

- [ ] **Step 1: 读取 SessionListScreen.kt 完整内容**

先用 Read 工具读取完整文件，理解当前结构。重点关注：
- Scaffold 的 snackbarHostState 声明位置
- content lambda 内的结构（PullToRefreshBox → Column → 搜索栏 + LazyColumn）
- FAB 的位置

- [ ] **Step 2: 添加 import**

在文件顶部 import 区域添加：

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

> 注：`Icons.AutoMirrored.Filled.List` 是推荐的 RTL 安全版本。

- [ ] **Step 3: 修改 Scaffold 结构**

**3a** — 在 Scaffold 调用之前，添加 pagerState 和 coroutineScope：

```kotlin
val pagerState = rememberPagerState(pageCount = { 2 })
val coroutineScope = rememberCoroutineScope()
```

**3b** — 添加页面切换监听（滑动切换时也触发 MCP 加载）：

```kotlin
// 监听页面变化（支持滑动切换）
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
        .collect { page ->
            if (page == 1) {
                viewModel.loadMcpServers()
            }
        }
}
```

> ⚠️ 注意：需要 `import androidx.compose.runtime.snapshotFlow`。此 LaunchedEffect 确保无论是 Tab 点击还是滑动切换到 Page 1，都会触发 MCP 加载。NavigationBar 的 onClick 中不再需要调用 `viewModel.loadMcpServers()`。

**3c** — 添加 Snackbar 错误收集：

```kotlin
// 收集 MCP 错误并显示 Snackbar
LaunchedEffect(Unit) {
    viewModel.mcpError.collect { errorMessage ->
        snackbarHostState.showSnackbar(errorMessage)
    }
}
```

> 使用已有的 `snackbarHostState`（SessionListScreen 已有此变量）。

**3d** — 修改 Scaffold 参数：

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },  // 保持不变
    topBar = { ... },        // 保持不变
    floatingActionButton = {
        AnimatedVisibility(visible = pagerState.currentPage == 0) {
            // 现有 FAB 代码整体移入此处，不改内容
        }
    },
    bottomBar = {
        NavigationBar {
            NavigationBarItem(
                selected = pagerState.currentPage == 0,
                onClick = {
                    if (!pagerState.isScrollInProgress) {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    }
                },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                label = { Text("Sessions") }
            )
            NavigationBarItem(
                selected = pagerState.currentPage == 1,
                onClick = {
                    if (!pagerState.isScrollInProgress) {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    }
                },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") }
            )
        }
    }
) { padding ->
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.padding(padding)
    ) { page ->
        when (page) {
            0 -> {
                // 将现有 Scaffold content 整体移入此处：
                // 搜索栏用 AnimatedVisibility(visible = pagerState.currentPage == 0) 包裹
                // PullToRefreshBox + Column + LazyColumn 保持原样
            }
            1 -> {
                ServerSettingsContent(
                    mcpServers = viewModel.mcpServers.collectAsStateWithLifecycle().value,
                    mcpLoading = viewModel.mcpLoading.collectAsStateWithLifecycle().value,
                    mcpInitialLoading = viewModel.mcpInitialLoading.collectAsStateWithLifecycle().value,
                    onToggleMcp = viewModel::toggleMcpServer
                )
            }
        }
    }
}
```

> **关键重构要点**：
> 1. 现有 Scaffold content（PullToRefreshBox 及其内部所有内容）整体移入 `page 0` 分支，不需要抽取为独立 Composable
> 2. 搜索栏（OutlinedTextField）用 `AnimatedVisibility(visible = pagerState.currentPage == 0)` 包裹
> 3. `isScrollInProgress` 防护避免快速连续点击时的并行动画
> 4. 使用 `collectAsStateWithLifecycle` 而非 `collectAsState`（生命周期感知）

- [ ] **Step 4: 验证编译**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/SessionListScreen.kt
git commit -m "feat: add NavigationBar + HorizontalPager to SessionListScreen"
```

---

## Task 9: 最终验证

- [ ] **Step 1: 完整编译检查**

Run: `.\gradlew :app:compileDevDebugKotlin` (timeout 120s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 构建 debug APK**

Run: `.\gradlew :app:assembleDevDebug` (timeout 300s)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验收清单自查**

对照 spec 的 Acceptance Criteria，逐项验证：

**导航栏**
- [ ] SessionListScreen 底部显示 NavigationBar，包含 2 个 Tab（Sessions、Settings）
- [ ] 点击 Tab 或左右滑动可切换页面，NavigationBar 选中状态同步
- [ ] 切换到 Settings Tab 时搜索栏和 FAB 平滑消失（AnimatedVisibility）；切回 Sessions 时恢复
- [ ] 进入 Chat 等子页面后 NavigationBar 消失

**MCP 列表**
- [ ] 进入 Settings Tab 时加载 MCP 服务器列表（LaunchedEffect 触发）
- [ ] 首次加载时显示 CircularProgressIndicator，加载完成后切换为列表或空状态
- [ ] 每个 MCP 服务器显示名称、类型、状态指示灯、Switch
- [ ] 状态指示灯正确反映 5 种颜色（绿=connected、灰=disabled、红=failed、黄=needs_auth/needs_client_registration）
- [ ] needs_auth 和 needs_client_registration 状态下 Switch disabled
- [ ] 无 MCP 服务器时显示 "No MCP servers configured" 空状态提示

**MCP Toggle**
- [ ] 点击 Switch 切换 connected → disabled（POST disconnect）或 disabled/failed → connected（POST connect）
- [ ] Toggle 过程中该服务器 Switch disabled，其他 MCP 服务器不受影响
- [ ] Toggle 成功后列表刷新为最新状态
- [ ] Toggle 失败时 Switch 回弹 + Snackbar 提示错误信息

- [ ] **Step 4: 最终 commit（如有修复）**

---

## SSE 重连刷新（后续优化）

Spec 要求"SSE 连接断开时保持上次已知 MCP 状态；重新连接后自动刷新 MCP 列表"。当前 Plan 通过 `loadMcpServers()` 按需加载，保持上次状态由 StateFlow 天然满足。SSE 重连后自动刷新可作为后续优化点：在 EventDispatcher 的连接状态回调中添加 `loadMcpServers()` 调用。实现时需参考现有 EventDispatcher 的连接事件监听模式。

---

## 404 不支持提示（后续优化）

Spec 要求"GET /mcp 返回 404 时 Page 1 显示 'This server does not support MCP management'"。当前 Plan 通过 `mcpError` SharedFlow 统一处理错误（包括 404），显示 Snackbar。如需专门的 404 特化 UI（替代 Snackbar），需：
1. McpRepositoryImpl 中捕获 `ClientRequestException`（status 404）并返回特定错误标记
2. ViewModel 区分 `mcpUnsupported` vs `mcpError` 状态
3. ServerSettingsContent 渲染 404 特化文案

此项可作为后续优化，当前错误通过 Snackbar 通知已满足基本需求。
