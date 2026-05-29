# Full Architecture Refactoring Design

## 1. 概述

### 1.1 动机
项目现有 42 个 Kotlin 文件（26,164 行），存在严重的架构问题：
- ChatScreen.kt 8,237 行（占项目 32%），80+ 组件/函数零组织
- Domain 层只有裸数据类，无接口层、无用例层
- ViewModel 直接依赖 data 层具体类，违反 DIP
- 共享组件仅 ProviderIcon.kt 一个，大量重复代码
- 27 个 MockK 单元测试全部失败（预先存在）
- 无统一错误处理策略

### 1.2 目标
- 全面重构为分层架构：domain → data → service → ui
- 落实 SOLID 原则：SRP、SoC、DIP、OCP、ISP、DRY
- 引入设计模式：工厂模式（工具卡片注册）、策略模式（事件处理器）、Repository 模式、UseCase 模式、Mapper 模式
- 采用 TDD 流程：先写 Characterization Tests 锁定行为，再在安全网下重构
- 功能行为不变：重构后用户可见行为与现在一致或更优

### 1.3 策略
混合策略（方案 C），分 6 个 Phase：
- Phase 0: 测试基础设施修复 + Characterization Tests
- Phase 1: Domain 层重构（接口定义 + UseCase 抽象）
- Phase 2: Chat 模块重构（最大巨石 ChatScreen.kt）
- Phase 3: 基础设施层（API 拆分 + EventReducer + Service）
- Phase 4: 其余 Screen 模块（Home/Session/Settings/Server）
- Phase 5: 共享组件库 + Navigation 重构 + 清理

### 1.4 约束
- 不设硬性行数上限，核心标准是"一个文件一个明确的职责"
- 功能行为不变，重构后能力一致或更优
- 每个 Phase 独立可验证，不能跳步
- 编码前必须查询 context7 确认 API，禁止臆测
- 遵循项目现有代码风格

## 2. 现有架构诊断

### 2.1 文件规模统计
| 文件 | 行数 | 大小(KB) | 问题等级 |
|------|------|----------|----------|
| ChatScreen.kt | 8,237 | 386.2 | 🚨 巨石 |
| HomeScreen.kt | 1,464 | 69.3 | ⚠️ |
| SessionListScreen.kt | 1,450 | 64.4 | ⚠️ |
| ChatViewModel.kt | 1,436 | 60.5 | ⚠️ |
| SettingsScreen.kt | 1,297 | 55.3 | ⚠️ |
| OpenCodeApi.kt | 1,290 | 43.3 | ⚠️ |
| TerminalEmulator.kt | 1,227 | 45.4 | ⚠️ |
| OpenCodeConnectionService.kt | 907 | 37.0 | ⚠️ |
| HomeViewModel.kt | 798 | 31.0 | |
| NavGraph.kt | 768 | 36.0 | |
| SseClient.kt | 585 | 24.0 | |
| ServerProvidersScreen.kt | 544 | 26.3 | |
| EventReducer.kt | 541 | 22.4 | ⚠️ |
| SettingsRepository.kt | 526 | 19.0 | |

### 2.2 核心问题清单
| 层 | 问题 | 违反原则 |
|---|---|---|
| UI | ChatScreen.kt 8,237行，80+组件零分区 | SRP |
| UI | 共享组件仅 ProviderIcon.kt 一个 | DRY |
| UI | PulsingDotsIndicator 在 ChatScreen 和 HomeScreen 各一份 | DRY |
| UI | NavGraph.kt 768行路由+深链接+导航逻辑混在一起 | SRP |
| Data | OpenCodeApi.kt 1,290行 API接口+DTO+序列化器混在一起 | SRP |
| Data | EventReducer.kt 541行 状态管理+事件分发+6种事件处理 | SRP, SoC |
| Service | OpenCodeConnectionService.kt 907行 SSE连接+通知+WakeLock+生命周期 | SRP |
| Domain | domain/model 只有数据类，缺少用例/接口层 | DIP |
| 全局 | 无统一错误处理策略 | — |
| 全局 | 27个MockK测试全部失败(预先存在) | 测试基础设施缺失 |

## 3. 目标架构

### 3.1 目录结构

```
dev.minios.ocremote/
│
├── di/                           ← 依赖注入 (Hilt Module)
│   ├── NetworkModule.kt          (保留)
│   ├── RepositoryModule.kt       (新增: Repository 绑定)
│   └── UseCaseModule.kt          (新增: UseCase 绑定)
│
├── domain/                       ← 领域层 (纯 Kotlin, 无 Android 依赖)
│   ├── model/                    (保留: 数据模型)
│   │   ├── Session.kt
│   │   ├── Message.kt
│   │   ├── Part.kt
│   │   ├── SseEvent.kt
│   │   ├── ToolState.kt
│   │   ├── SessionStatus.kt
│   │   └── ServerConfig.kt
│   │
│   ├── repository/               (新增: Repository 接口)
│   │   ├── ChatRepository.kt
│   │   ├── SessionRepository.kt
│   │   ├── ServerRepository.kt
│   │   └── SettingsRepository.kt
│   │
│   └── usecase/                  (新增: 用例层)
│       ├── chat/
│       │   ├── SendMessageUseCase.kt
│       │   ├── LoadMessagesUseCase.kt
│       │   ├── ManagePermissionsUseCase.kt
│       │   └── ManageQuestionsUseCase.kt
│       ├── session/
│       │   ├── CreateSessionUseCase.kt
│       │   └── SwitchSessionUseCase.kt
│       └── server/
│           ├── ConnectServerUseCase.kt
│           └── ManageLocalServerUseCase.kt
│
├── data/                         ← 数据层 (实现 domain 接口)
│   ├── api/
│   │   ├── OpenCodeApi.kt        (瘦身: 仅 API 接口)
│   │   ├── SseClient.kt          (保留)
│   │   └── dto/                  (新增: 从 OpenCodeApi 拆出)
│   │       ├── request/
│   │       │   ├── CreateSessionRequest.kt
│   │       │   ├── SendMessageRequest.kt
│   │       │   └── PermissionReplyRequest.kt
│   │       ├── response/
│   │       │   ├── ProvidersResponse.kt
│   │       │   ├── SessionResponse.kt
│   │       │   └── MessagesResponse.kt
│   │       └── serializer/
│   │           └── Base64UrlSerializer.kt
│   │
│   ├── repository/
│   │   ├── impl/                 (新增: domain 接口的实现)
│   │   │   ├── ChatRepositoryImpl.kt
│   │   │   ├── SessionRepositoryImpl.kt
│   │   │   └── ServerRepositoryImpl.kt
│   │   ├── EventReducer.kt       (瘦身: 仅状态容器)
│   │   ├── EventDispatcher.kt    (新增: 从 EventReducer 拆出)
│   │   ├── handler/              (新增: 事件处理器)
│   │   │   ├── SseEventHandler.kt (接口)
│   │   │   ├── SessionEventHandler.kt
│   │   │   ├── MessageEventHandler.kt
│   │   │   ├── PermissionEventHandler.kt
│   │   │   ├── QuestionEventHandler.kt
│   │   │   └── MiscEventHandler.kt
│   │   ├── LocalServerManager.kt
│   │   └── DraftRepository.kt
│   │
│   └── mapper/                   (新增: DTO ↔ Domain Model 映射)
│       ├── SessionMapper.kt
│       ├── MessageMapper.kt
│       └── ServerConfigMapper.kt
│
├── service/                      ← 服务层
│   ├── OpenCodeConnectionService.kt  (瘦身: 仅生命周期编排)
│   ├── connection/               (新增: 从 Service 拆出)
│   │   ├── SseConnectionManager.kt
│   │   └── SseEventParser.kt
│   └── notification/             (新增: 从 Service 拆出)
│       ├── AppNotificationManager.kt
│       ├── NotificationBuilder.kt
│       └── NotificationChannelHelper.kt
│
└── ui/                           ← 表现层
    ├── theme/
    ├── components/               (大幅扩充: 共享组件库)
    │   ├── ProviderIcon.kt
    │   ├── indicators/
    │   │   ├── PulsingDotsIndicator.kt
    │   │   └── BreathingCircleIndicator.kt
    │   ├── cards/
    │   │   ├── ToolCallCard.kt
    │   │   ├── ReasoningBlock.kt
    │   │   ├── PermissionCard.kt
    │   │   └── ExpandableCard.kt
    │   ├── input/
    │   │   └── ChatInputBar.kt
    │   ├── markdown/
    │   │   ├── MarkdownContent.kt
    │   │   └── SimpleMarkdownTable.kt
    │   └── diff/
    │       ├── DiffView.kt
    │       └── DiffChangesInline.kt
    │
    ├── navigation/
    │   ├── NavGraph.kt           (瘦身: 仅 composable 注册)
    │   ├── Screen.kt             (保留)
    │   ├── deeplink/
    │   │   └── DeepLinkHandler.kt
    │   └── routes/
    │       ├── ChatRoute.kt
    │       ├── HomeRoute.kt
    │       ├── SessionListRoute.kt
    │       ├── SettingsRoute.kt
    │       ├── ServerSettingsRoute.kt
    │       ├── AboutRoute.kt
    │       └── WebViewRoute.kt
    │
    └── screens/
        ├── chat/
        │   ├── ChatScreen.kt     (8237→~300行: 仅布局骨架)
        │   ├── ChatViewModel.kt  (瘦身: 委托 UseCase)
        │   ├── ChatNavigation.kt
        │   ├── components/
        │   │   ├── ChatMessageBubble.kt
        │   │   ├── AssistantMessageCard.kt
        │   │   ├── AssistantTurnBubble.kt
        │   │   ├── UserMessageBubble.kt
        │   │   ├── RevertBanner.kt
        │   │   ├── TokenFooter.kt
        │   │   └── MessageGroupHeader.kt
        │   ├── tools/
        │   │   ├── ToolCardRegistry.kt
        │   │   ├── ToolCallCard.kt
        │   │   ├── EditToolCard.kt
        │   │   ├── WriteToolCard.kt
        │   │   ├── BashToolCard.kt
        │   │   ├── ReadToolCard.kt
        │   │   ├── SearchToolCard.kt
        │   │   ├── TaskToolCard.kt
        │   │   ├── TodoListCard.kt
        │   │   └── PatchCard.kt
        │   ├── input/
        │   │   ├── ChatInputBar.kt
        │   │   ├── SlashCommandMenu.kt
        │   │   └── AttachmentPreview.kt
        │   ├── dialog/
        │   │   ├── ModelPickerDialog.kt
        │   │   ├── ImagePreviewDialog.kt
        │   │   └── QuestionCard.kt
        │   ├── terminal/
        │   │   ├── TerminalEmulator.kt
        │   │   ├── ServerTerminalWorkspace.kt
        │   │   ├── SessionTerminalInline.kt
        │   │   ├── TerminalKeyboardOverlay.kt
        │   │   └── TerminalKeys.kt
        │   ├── markdown/
        │   │   ├── MarkdownContent.kt
        │   │   └── SimpleMarkdownTable.kt
        │   └── util/
        │       ├── ChatFormatters.kt
        │       ├── ChatModifiers.kt
        │       ├── ChatColors.kt
        │       └── MediaUtils.kt
        ├── home/
        │   ├── HomeScreen.kt
        │   ├── HomeViewModel.kt
        │   ├── ServerDialog.kt
        │   └── components/
        │       ├── ServerCard.kt
        │       ├── LocalRuntimeCard.kt
        │       ├── LocalLaunchOptionsDialog.kt
        │       ├── EmptyServersView.kt
        │       └── BatteryOptimizationBanner.kt
        ├── sessions/
        │   ├── SessionListScreen.kt
        │   ├── SessionListViewModel.kt
        │   └── components/
        │       └── SessionListItem.kt
        ├── settings/
        │   ├── SettingsScreen.kt
        │   ├── SettingsViewModel.kt
        │   └── components/
        │       └── SettingsSection.kt
        ├── server/
        │   ├── ServerSettingsScreen.kt
        │   ├── ServerSettingsViewModel.kt
        │   ├── ServerProvidersScreen.kt
        │   └── ServerModelFilterScreen.kt
        ├── webview/
        │   └── WebViewScreen.kt
        └── about/
            └── AboutScreen.kt
```

### 3.2 设计原则映射

| 原则 | 体现位置 |
|------|----------|
| **SRP** | 每个文件单一明确职责。ChatScreen 8237行→多个职责单一的小文件 |
| **SoC** | domain (纯Kotlin) → data (实现) → service (系统) → ui (展示) 严格分层 |
| **DIP** | ViewModel 依赖 domain/repository 接口，不依赖 data 层具体实现 |
| **OCP** | ToolCardRegistry 工厂模式 — 新增工具卡片只需注册，不改已有代码 |
| **ISP** | 小接口分离：ChatRepository, SessionRepository, ServerRepository 独立 |
| **DRY** | 共享组件库 (indicators, markdown, diff, cards) 消除重复 |
| **LSP** | Repository 实现可替换（测试用 Fake，生产用 Real） |

### 3.3 设计模式应用

| 模式 | 应用场景 |
|------|----------|
| **Repository** | domain/repository 接口 + data/repository/impl 实现 |
| **UseCase / Command** | domain/usecase/ — 每个 UseCase 封装一个业务操作 |
| **Factory / Registry** | ToolCardRegistry — 工具卡片注册与分发 |
| **Strategy** | SseEventHandler 接口 + 多种 Handler 实现 — 事件处理策略 |
| **Observer** | StateFlow / SharedFlow — 响应式数据流 |
| **Mapper** | data/mapper/ — DTO ↔ Domain Model 转换 |
| **Facade** | EventDispatcher — 对外统一事件分发入口 |
| **Template Method** | ExpandableCard — 通用展开/折叠壳，具体内容由子类提供 |

## 4. 各层详细设计

### 4.1 Domain 层

#### 4.1.1 Repository 接口

**ChatRepository** — 聊天核心操作
```kotlin
interface ChatRepository {
    fun getMessagesFlow(sessionId: String): Flow<List<MessageWithParts>>
    fun getPermissionsFlow(sessionId: String): Flow<List<PermissionState>>
    fun getQuestionsFlow(sessionId: String): Flow<List<QuestionState>>
    suspend fun sendMessage(sessionId: String, parts: List<PromptPart>): Result<Message>
    suspend fun replyPermission(permissionId: String, reply: String): Result<Boolean>
    suspend fun replyQuestion(questionId: String, answer: String): Result<Boolean>
    fun getToolExpandedStates(): MutableMap<String, Boolean>
}
```

**SessionRepository** — 会话管理
```kotlin
interface SessionRepository {
    fun getSessionsFlow(serverId: String): Flow<List<Session>>
    suspend fun createSession(serverId: String, opts: CreateSessionOpts): Result<Session>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun switchSession(sessionId: String): Result<Unit>
}
```

**ServerRepository** — 服务器连接
```kotlin
interface ServerRepository {
    fun getServersFlow(): Flow<List<ServerConfig>>
    suspend fun connect(server: ServerConfig): Result<Unit>
    suspend fun disconnect(serverId: String): Result<Unit>
    suspend fun testConnection(server: ServerConfig): Result<Boolean>
}
```

**SettingsRepository** — 应用设置
```kotlin
interface SettingsRepository {
    fun getSettingsFlow(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings): Result<Unit>
}
```

#### 4.1.2 UseCase 层

每个 UseCase 遵循：
- 单一公共 `operator fun invoke`
- 参数为领域模型，不暴露 DTO
- 返回 `Result<T>` 统一错误处理
- 纯 Kotlin，可单元测试

示例：
```kotlin
class SendMessageUseCase @Inject constructor(
    private val chatRepo: ChatRepository,
    private val sessionRepo: SessionRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        attachments: List<Attachment>
    ): Result<Message> {
        val parts = buildPromptParts(text, attachments)
        return chatRepo.sendMessage(sessionId, parts)
    }
}
```

### 4.2 Data 层

#### 4.2.1 API 拆分

OpenCodeApi.kt (1290行) 拆为：
- `api/OpenCodeApi.kt` — 仅 Ktor HTTP 接口定义
- `api/dto/request/` — 请求体 DTO
- `api/dto/response/` — 响应体 DTO
- `api/dto/serializer/` — 自定义序列化器
- `mapper/` — DTO ↔ Domain Model 转换

#### 4.2.2 EventReducer 拆分

现状 EventReducer.kt 541行混合职责，拆为：

**EventReducer** (瘦身) — 仅状态容器
- 持有 StateFlow：sessions, messages, permissions, questions, todos
- 提供类型安全的读写方法
- 由 Handler 调用，不自行处理事件

**EventDispatcher** — 事件分发入口
- 持有 `Map<KClass<out SseEvent>, SseEventHandler>`
- `dispatch(event, serverId)` 路由到对应 Handler

**SseEventHandler 接口** — 策略模式
```kotlin
interface SseEventHandler {
    val eventType: KClass<out SseEvent>
    fun handle(event: SseEvent, serverId: String, reducer: EventReducer)
}
```

Handler 实现：
- SessionEventHandler — 会话创建/更新/删除/状态
- MessageEventHandler — 消息/Part 更新/增量/删除
- PermissionEventHandler — 权限请求/回复
- QuestionEventHandler — 问题请求/回复/拒绝
- MiscEventHandler — Todo, Vcs, Project, Compacted

#### 4.2.3 Mapper 层

```kotlin
object SessionMapper {
    fun toDomain(dto: SessionResponse): Session = Session(...)
    fun toDto(domain: CreateSessionOpts): CreateSessionRequest = ...
}
```

好处：API 层改 DTO 结构不改 Domain Model；Domain Model 改字段不影响 API 层；Mapper 可单独测试。

### 4.3 Service 层

OpenCodeConnectionService.kt (907行) 拆为：

**OpenCodeConnectionService** (瘦身) — 仅 Service 生命周期编排
- onCreate / onStartCommand / onDestroy
- 编排连接、通知、WakeLock

**SseConnectionManager** — SSE 连接管理
- connect / disconnect / reconnect
- 心跳 / 超时处理
- 回调事件给 Service

**AppNotificationManager** — 通知系统统一入口
- 前台服务通知
- 事件通知（permission / question / error）
- 通知渠道管理

### 4.4 UI 层

#### 4.4.1 ChatScreen 拆分（核心）

8237行拆解为以下模块：

**ChatScreen.kt** (~300行) — 仅 Scaffold 骨架
- TopBar + LazyColumn + ChatInputBar
- ViewModel 状态收集
- 无具体渲染逻辑

**ChatViewModel.kt** (瘦身)
- 不再直接调用 OpenCodeApi / EventReducer
- 委托给 UseCase
- 仅保留 UI 状态管理

**components/** — 消息级组件
- ChatMessageBubble — 消息分发
- AssistantMessageCard — 助理消息渲染
- AssistantTurnBubble — 助理轮次
- UserMessageBubble — 用户消息
- RevertBanner, TokenFooter, MessageGroupHeader

**tools/** — 工具卡片 (OCP: 工厂模式)
- ToolCardRegistry — 注册表
- 各工具卡片独立文件

**input/** — 输入区
- ChatInputBar, SlashCommandMenu, AttachmentPreview

**dialog/** — 对话框
- ModelPickerDialog, ImagePreviewDialog, QuestionCard

**terminal/** — 终端子系统
- TerminalEmulator, ServerTerminalWorkspace, SessionTerminalInline, TerminalKeyboardOverlay, TerminalKeys

**markdown/** — Markdown 渲染
- MarkdownContent, SimpleMarkdownTable

**util/** — 辅助
- ChatFormatters (formatTokenCount, formatFileSize, formatDuration)
- ChatModifiers (consumeBoundaryFling, codeHorizontalScroll)
- ChatColors (agentColor, isAmoledTheme, toolOutputContainerColor)
- MediaUtils (extensionForMime, imageThumbnailModel, estimateVisionTokens)

#### 4.4.2 工具卡片注册表

```kotlin
object ToolCardRegistry {
    private val builders = mutableMapOf<String, @Composable (Part.Tool) -> Unit>()
    
    fun register(toolName: String, builder: @Composable (Part.Tool) -> Unit) {
        builders[toolName] = builder
    }
    
    fun resolve(toolName: String): (@Composable (Part.Tool) -> Unit)? = builders[toolName]
    
    fun registerDefaults() {
        register("edit") { EditToolCard(it) }
        register("write") { WriteToolCard(it) }
        register("bash") { BashToolCard(it) }
        register("read") { ReadToolCard(it) }
        register("search") { SearchToolCard(it) }
        register("task") { TaskToolCard(it) }
        register("todolist") { TodoListCard(it) }
        register("patch") { PatchCard(it) }
    }
}
```

新增工具卡片只需：创建新卡片文件 + 在 registerDefaults 中注册一行。无需修改 PartContent 或任何已有代码。

#### 4.4.3 共享组件库

`ui/components/` 存放跨 Screen 复用的组件：
- indicators/ — PulsingDotsIndicator (从 Chat+Home 去重), BreathingCircleIndicator
- cards/ — ToolCallCard (通用壳), ReasoningBlock, PermissionCard, ExpandableCard
- input/ — ChatInputBar
- markdown/ — MarkdownContent, SimpleMarkdownTable
- diff/ — DiffView, DiffChangesInline

#### 4.4.4 Navigation 重构

NavGraph.kt (768行) 拆为：
- NavGraph.kt — 仅 composable 注册
- deeplink/DeepLinkHandler.kt — 深链接解析 + 路由匹配
- routes/ — 每个路由的参数提取 + ViewModel 绑定

#### 4.4.5 其他 Screen

遵循相同拆分模式（程度较轻）：
- HomeScreen → components/ (ServerCard, LocalRuntimeCard, EmptyServersView, ...)
- SessionListScreen → components/ (SessionListItem)
- SettingsScreen → components/ (SettingsSection)
- Server 系列 Screen 视实际复杂度决定是否拆分

## 5. 统一错误处理

### 5.1 策略
- Domain 层：所有操作返回 `Result<T>`
- ViewModel：collect 时统一处理 Error 状态，映射为 UI State
- UI 层：统一的 ErrorBanner / Snackbar 组件

### 5.2 Result 模式
```kotlin
// Domain 层统一返回类型
suspend fun sendMessage(...): Result<Message>
suspend fun connect(...): Result<Unit>

// ViewModel 处理
viewModelScope.launch {
    sendMessageUseCase(sessionId, text, attachments)
        .onSuccess { /* 状态更新 */ }
        .onFailure { _errorState.value = it.toUiError() }
}
```

## 6. TDD 策略

### 6.1 总体流程

```
Phase 0: 测试基础设施修复 + Characterization Tests
  ├── 修复 27 个 MockK 失败测试
  ├── 分析根因 (构造函数签名变更 / mock 不完整 / 缺少依赖)
  └── 确保现有测试全部 PASS

Phase 0 续: Characterization Tests (行为捕获)
  ├── EventReducer: 每个 handler 方法的输入→状态输出
  ├── ChatViewModel: 每个公共方法的状态变化
  ├── HomeViewModel: 连接/断开/服务器管理
  ├── SseClient: 事件解析
  └── OpenCodeApi: API 调用 (mock HTTP)

后续每个 Phase:
  ├── 重构前: Characterization Test 全绿
  ├── 重构中: 保持测试绿
  ├── 新增抽象 (接口/UseCase/Handler): 标准 TDD (RED → GREEN → REFACTOR)
  └── 完成后: 全量回归
```

### 6.2 Characterization Tests 覆盖范围

| 目标 | 测试内容 | 优先级 |
|------|----------|--------|
| EventReducer | 每个 handle 方法的输入→状态输出 | P0 |
| ChatViewModel | sendMessage, replyPermission, selectModel, loadMessages | P0 |
| HomeViewModel | connect, disconnect, server CRUD | P0 |
| SseClient | SSE 事件解析 | P1 |
| OpenCodeApi | API 调用 (mock HTTP) | P1 |
| SettingsRepository | 读写设置 | P2 |
| DraftRepository | 草稿管理 | P2 |

### 6.3 TDD 规则
- 重构场景：先写 Characterization Test（全部 PASS），锁定现有行为
- 新增抽象（接口、UseCase、Handler）：标准 RED → GREEN → REFACTOR
- 每个 UseCase 必须有单元测试
- 每个 Repository 实现必须有集成测试
- 每个 Handler 必须有单元测试
- Mapper 必须有单元测试（双向转换验证）
- 每个 Phase 完成后全量回归

## 7. 执行阶段

### Phase 0: 测试基础设施
- 修复 27 个 MockK 失败测试
- 编写 Characterization Tests 覆盖核心行为
- 验证全部 PASS

### Phase 1: Domain 层
- 定义 domain/repository/ 接口
- 实现 domain/usecase/ 用例
- 编写 UseCase 单元测试 (TDD)
- 创建 DI Module 绑定
- 验证编译通过

### Phase 2: Chat 模块重构
- 拆分 ChatScreen.kt 为组件树
- 实现 ToolCardRegistry
- 拆分 ChatViewModel (委托 UseCase)
- 提取 terminal 子系统
- 提取 util 辅助函数
- 验证 UI 行为不变 + 测试全绿

### Phase 3: 基础设施层
- 拆分 OpenCodeApi (DTO/Serializer/Mapper)
- 拆分 EventReducer (状态/分发/Handler)
- 拆分 OpenCodeConnectionService (连接/通知)
- 实现 Repository Impl
- 验证编译通过 + 测试全绿

### Phase 4: 其余 Screen 模块
- HomeScreen 拆分
- SessionListScreen 拆分
- SettingsScreen 拆分
- Server 系列 Screen 拆分
- 去重共享组件 (PulsingDotsIndicator 等)
- 验证 UI 行为不变

### Phase 5: 共享组件库 + Navigation + 清理
- 建立 ui/components/ 共享组件库
- 拆分 NavGraph (deeplink / routes)
- 清理未使用的代码
- 全量回归测试
- Release build 验证

## 8. 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 重构引入回归 bug | Characterization Tests 锁定行为，每个 Phase 全量回归 |
| Hilt DI 循环依赖 | Domain 层纯接口，Data 层依赖 Domain，方向单一 |
| 性能回退 | 每个 Phase 后做 release build + 手动测试 |
| 测试维护成本高 | 测试只验证公共 API 行为，不测实现细节 |
| 重构范围蔓延 | 严格分 Phase，每个 Phase 有明确的完成标准 |

## 9. 完成标准

每个 Phase 的完成标准：
1. 所有 Characterization Tests 通过
2. 所有新增 TDD 测试通过
3. Release build 成功
4. 手动验证核心功能正常
5. 代码符合目标目录结构

整体完成标准：
1. 所有 6 个 Phase 完成
2. ChatScreen.kt 不超过 400 行
3. 每个文件单一职责
4. ViewModel 不直接依赖 data 层具体类
5. 新增工具卡片只需注册一行
6. 所有测试通过（原有 + 新增）
7. APK 大小不显著增长
8. 功能行为与重构前一致
