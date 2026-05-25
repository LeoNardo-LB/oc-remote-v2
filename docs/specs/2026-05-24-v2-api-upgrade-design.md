# V2 API 升级设计

> 日期: 2026-05-24
> 状态: Draft
> 范围: 将 oc-remote 的 API 层从 V1 升级到 V2

## 1. 背景与动机

oc-remote 最后发版于 2026-03-13，基于 OpenCode V1 API。OpenCode 在 2026-05 左右更新了 V2 API，新增了技能端点、实例级事件流、会话子管理等功能。用户报告了以下问题：

- 技能无法在输入框中选择使用
- 权限请求时客户端完全无响应
- 消息队列(queue)标记缺失
- 子 Agent 查看体验差

**本 PR 覆盖范围**：

| 用户问题 | 是否覆盖 | 说明 |
|---------|---------|------|
| 技能无法选择 | ✅ 覆盖 | 新增 `listSkills` API + 修改 ChatScreen 斜杠命令过滤 |
| 权限请求无响应 | ⚠️ DTO 对齐 | `PermissionAsked` 事件已存在（SseEvent.kt:86），本次仅更新 DTO 字段以匹配 V2 格式（`always: Boolean`、`metadata: Map<String, String>`）。实际的 UI 响应流程需排查客户端处理逻辑，不在本 PR 范围 |
| 消息队列标记缺失 | ❌ 不纳入 | 记录在第 7 节 |
| 子 Agent 查看体验差 | ❌ 不纳入 | 记录在第 7 节 |

## 2. 核心发现

### V1 与 V2 的关系

V2 SDK 中包含**两套并行的 API**：

| 层级 | 路径风格 | 说明 |
|------|---------|------|
| V1 兼容层 | `/session`, `/command`, `/agent` | oc-remote 当前使用的全部端点**均保留可用** |
| V2 新协议 | `/api/session`, `/api/model` 等 8 个 | 全新设计的端点（游标分页、prompt 投递等） |

**结论**：现有 52 个 V1 方法的 URL、HTTP 方法在 V2 中**全部不变**。升级工作集中在：

1. DTO 扩展（新增字段，`ignoreUnknownKeys=true` 保证兼容）
2. 新增约 10 个 V2 端点方法
3. SSE 事件扩展（~24 -> ~45 种）
4. 新增实例级 SSE 连接 `/event`

### HTTP 方法分布

V2 仍然是 RESTful 风格，不存在"全部改 POST"的情况：
- GET: 44 个 | POST: 40 个 | DELETE: 9 个 | PATCH: 6 个 | PUT: 3 个

## 3. 升级策略：原地升级

直接在现有文件上扩展，不新建 V2 文件。

理由：
- 现有端点全部兼容，无需重写
- DTO 用 `@Serializable` + `ignoreUnknownKeys=true` 天然向后兼容
- 避免两套代码并存的维护负担

## 4. 修改清单

### 4.1 数据模型层

#### `Session.kt` — 新增字段

```kotlin
data class Session(
    // ... 现有字段不变 ...
    val id: String,
    val slug: String = "",
    @SerialName("projectID") val projectId: String = "",
    val directory: String = "",
    @SerialName("parentID") val parentId: String? = null,
    val title: String? = null,
    val version: String = "",
    val time: Time,
    val summary: Summary? = null,
    val share: Share? = null,
    val permission: List<PermissionRule>? = null,
    val revert: Revert? = null,

    // --- V2 新增字段 ---
    @SerialName("workspaceID") val workspaceId: String? = null,
    val path: String? = null,
    val cost: Double? = null,
    val tokens: SessionTokens? = null,
    val agent: String? = null,
    val model: SessionModel? = null,
) {
    // 新增内部类
    @Serializable
    data class SessionTokens(
        val input: Int = 0,
        val output: Int = 0,
        val reasoning: Int = 0,
        val cache: Cache = Cache()
    ) {
        @Serializable
        data class Cache(val read: Int = 0, val write: Int = 0)
    }

    @Serializable
    data class SessionModel(
        val id: String,
        @SerialName("providerID") val providerId: String,
        val variant: String? = null
    )
}
```

#### `Message.kt` — 无需改动

现有 `Message.User` 和 `Message.Assistant` 字段已覆盖 V2 需求。V2 SDK 中 `UserMessage.agent` 和 `model` 变为必填，但 oc-remote 已声明为可选且有默认值，兼容。

#### `Part.kt` — 无需改动

现有 Part 类型（Text/Reasoning/Tool/StepStart/StepFinish/File/Snapshot/Patch/Subtask/Compaction/Retry/Agent）已覆盖 V2 需求。V2 移除了 Permission/Question/Abort/SessionTurn，但这些在 oc-remote 中也未活跃使用。

### 4.2 API 客户端层

#### `OpenCodeApi.kt` — 新增方法

在现有文件中新增以下方法：

```kotlin
// ===== V2 新增端点 =====

/** 获取技能列表 — GET /skill */
suspend fun listSkills(conn: ServerConnection): List<SkillInfo>

/** 获取会话子列表 — GET /session/{sessionId}/children */
suspend fun listSessionChildren(conn: ServerConnection, sessionId: String): List<Session>

/** 获取会话待办 — GET /session/{sessionId}/todo */
suspend fun getSessionTodos(conn: ServerConnection, sessionId: String): List<Todo>

/** 批量获取会话状态 — GET /session/status */
suspend fun listSessionStatus(conn: ServerConnection): Map<String, SessionStatus>

/** 获取可用 shell — GET /pty/shells */
suspend fun listPtyShells(conn: ServerConnection): List<ShellInfo>

/** 符号搜索 — GET /find/symbol */
suspend fun findSymbols(conn: ServerConnection, query: String): List<SymbolInfo>

/** 文件 Git 状态 — GET /file/status */
suspend fun getFileStatus(conn: ServerConnection): List<FileStatusInfo>

/** 实例释放 — POST /instance/dispose */
suspend fun disposeInstance(conn: ServerConnection): Boolean
```

#### 新增 DTO

```kotlin
// 技能信息
@Serializable
data class SkillInfo(
    val name: String,
    val description: String? = null,
    val location: String,
    val content: String
)

// 待办项（对应 V2 SDK 的 Todo 类型）
@Serializable
data class Todo(
    val id: String,
    val content: String,
    val status: String = "pending",
    val priority: String = "medium"
)

// Shell 信息
@Serializable
data class ShellInfo(
    val path: String,
    val name: String,
    val acceptable: Boolean = true
)

// 符号搜索结果
@Serializable
data class SymbolInfo(
    val name: String,
    val kind: String,
    val path: String,
    val line: Int? = null,
    val language: String? = null
)

// 文件 Git 状态
@Serializable
data class FileStatusInfo(
    val path: String,
    val status: String,
    val staged: Boolean = false
)
```

#### 现有方法微调

以下方法的请求体需要扩展字段（向后兼容，老字段不变）：

| 方法 | 新增 body 字段 |
|------|---------------|
| `executeCommand` | `agent`, `model`, `variant`, `parts` |
| `promptAsync` | `tools`, `format`, `system` |
| `summarizeSession` | `auto` |
| `revertSession` | `partID` |
| `createSession` | `agent`, `model`, `permission`, `workspaceID` |
| `updateSession` | `permission`, `time.archived` |

实现方式：在对应的请求 Body data class 中添加可选字段。

### 4.3 SSE 事件层

#### `SseClient.kt` — 新增连接

```kotlin
/** V2 实例级事件流 — GET /event */
fun connectToInstanceEvents(
    conn: ServerConnection,
    directory: String?
): Flow<SseEvent>
```

现有 `connectToGlobalEvents()` 保留不变，两者并行使用。

#### `SseEvent.kt` — 新增事件类型

V2 新增的事件类型（约 20 种），按优先级分批：

**P0 — 直接影响用户体验：**

```kotlin
// 权限相关（DTO 字段更新以匹配 V2 格式，已有事件类型在 SseEvent.kt:86）
data class PermissionAsked(
    val id: String,
    val sessionId: String,
    val permission: String,
    val patterns: List<String>,
    val metadata: Map<String, String>? = null,
    val always: Boolean = false,
    val tool: ToolRef? = null
) : SseEvent()

// 会话压缩
data class SessionCompacted(val sessionId: String) : SseEvent()

// PTY 事件
data class PtyCreated(val info: PtyInfo) : SseEvent()
data class PtyUpdated(val info: PtyInfo) : SseEvent()
data class PtyExited(val id: String) : SseEvent()
data class PtyDeleted(val id: String) : SseEvent()

// 工作区
data class WorkspaceReady(val workspaceId: String) : SseEvent()
data class WorkspaceFailed(val workspaceId: String, val error: String?) : SseEvent()

// 文件编辑
data class FileEdited(val path: String) : SseEvent()

// MCP
data class McpToolsChanged(val server: String) : SseEvent()

// 命令执行完成
data class CommandExecuted(
    val name: String,
    val sessionId: String,
    val arguments: String,
    val messageId: String
) : SseEvent()
```

**P1 — 后续功能支持：**

```kotlin
// 文件监听
data class FileWatcherUpdated(val path: String) : SseEvent()

// TUI 事件（远程控制用）
data class TuiPromptAppend(val text: String) : SseEvent()
data class TuiCommandExecute(val command: String) : SseEvent()
data class TuiToastShow(val title: String?, val message: String, val variant: String) : SseEvent()
data class TuiSessionSelect(val sessionId: String) : SseEvent()

// 安装更新
data class InstallationUpdated(val version: String) : SseEvent()
data class InstallationUpdateAvailable(val version: String) : SseEvent()

// 工作树
data class WorktreeReady(val path: String) : SseEvent()
data class WorktreeFailed(val path: String, val error: String?) : SseEvent()
```

#### `EventReducer.kt` — 新增事件处理

为每个新事件类型添加处理逻辑，更新对应的 StateFlow。

### 4.4 UI 层

#### `ChatScreen.kt` — 技能选择

修改斜杠命令建议面板，将 `source == "skill"` 的命令纳入显示：

```kotlin
// 当前代码（ChatScreen.kt:6019）：
.filter { it.source != "skill" && it.name !in clientNames }

// 修改为：不再排除 skill，而是分组显示
val allCommands = remember(commands, clientCmds) {
    val clientNames = clientCmds.map { it.name }.toSet()
    val serverSlash = commands
        .filter { it.name !in clientNames }
        .map { SlashCommand(it.name, it.description, it.source ?: "server") }
    clientCmds + serverSlash
}
```

在建议面板 UI 中区分显示：
- 普通命令：`/` 前缀，默认样式
- 技能命令：`/` 前缀 + 技能图标/标签

## 5. 文件修改清单

| 文件 | 修改类型 | 工作量 |
|------|---------|--------|
| `domain/model/Session.kt` | 新增 6 个字段 + 2 个内部类 | 低 |
| `domain/model/SseEvent.kt` | 新增 ~20 种事件类型 | 中 |
| `data/api/OpenCodeApi.kt` | 新增 8 个方法 + 5 个 DTO + 6 个方法扩展 body | 中 |
| `data/api/SseClient.kt` | 新增 `connectToInstanceEvents` 方法 | 低 |
| `data/repository/EventReducer.kt` | 新增 ~20 个事件处理分支 | 中 |
| `ui/screens/chat/ChatScreen.kt` | 修改斜杠建议过滤逻辑 | 低 |
| `ui/screens/chat/ChatViewModel.kt` | 新增 `loadSkills` 方法 | 低 |

**总计约 7 个文件，预估 3-5 天。**

## 6. 风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 新增字段破坏现有反序列化 | 低 | `ignoreUnknownKeys=true` + 所有新字段为可选 |
| SSE 事件解析遗漏 | 低 | 未知事件类型 fallback 到日志记录 |
| V2 服务端尚未部署某些端点 | 中 | 所有新方法加 try-catch，失败时优雅降级 |
| 权限事件格式与预期不符 | 中 | 需实际连服务端验证 |

## 7. 暂不纳入的功能

以下功能记录但不在本次 PR 范围内：

- 子 Agent 深入查看体验优化
- 消息队列(queue)标记
- V2 新协议端点 (`/api/session/*`)
- MCP 管理 UI
- Workspace 管理 UI
- TUI 远程控制
