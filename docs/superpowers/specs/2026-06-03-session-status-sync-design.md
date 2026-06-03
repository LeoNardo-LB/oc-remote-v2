# 会话状态同步 — 彻底修复设计规范

> 日期: 2026-06-03
> 状态: Draft
> 范围: P0-3（运行状态未同步）+ P0-4（SSE 重连后消息丢失）

---

## 1. 问题陈述

用户核心痛点：**会话在服务器端正在处理（busy），但客户端 UI 不反映真实状态**。

具体表现：
- 进入一个正在处理的会话 → 显示 Idle，停止按钮不出现
- App 被杀后重新打开 → 所有会话显示 Idle
- SSE 断线重连 → 正在处理的会话被强制标为 Idle
- 会话列表页 → 无任何状态校正机制，完全依赖 SSE 推送

---

## 2. 根因分析

### 2.1 根因一览

| # | 根因 | 严重度 | 影响 |
|---|------|--------|------|
| R1 | `recoverMessages()` 盲目将所有会话标为 Idle | 高 | SSE 重连后 busy 会话错误显示 Idle |
| R2 | `preLoadSessions()` 不初始化 `_sessionStatuses` | 高 | 冷启动后所有会话默认 Idle |
| R3 | 会话列表页（SessionListViewModel）完全没有状态校正 | 中 | 列表页状态永远依赖 SSE 推送 |
| R4 | `syncSessionStatus()` 只用当前 sessionId，丢弃全量数据 | 中 | 浪费 REST 调用，无法校正其他会话 |
| R5 | `syncSessionStatus()` 不恰当地设置 `_isLoading = false` | 中 | 与 loadMessages 职责冲突 |
| R6 | 消息恢复 `mergeMessages()` 保留本地旧数据 | 高 | 重连后消息内容残缺 |

### 2.2 数据流关系

```
SessionEventHandler._sessionStatuses (Singleton, @Singleton)
│
├── 写入点（11个）:
│   [SSE]  handleSessionCreated     → Idle（硬编码）
│   [SSE]  handleSessionStatus      → 事件中的值（Idle/Busy/Retry）
│   [SSE]  handleSessionIdle         → Idle
│   [SSE]  CommandExecuted           → Idle（跨Handler）
│   [REST] syncSessionStatus("idle")  → markSessionIdle → Idle
│   [REST] syncSessionStatus("busy")  → updateSessionStatus → Busy
│   [REST] syncSessionStatus("retry") → updateSessionStatus → Retry
│   [重连] recoverMessages            → markSessionIdle → Idle ⚠️ 盲目
│   [用户] abortSession               → Idle
│   [清理] clearForServer             → 移除
│   [清理] clearAll                   → 清空
│
├── 读取点（3个）:
│   ChatViewModel.combine()          → uiState.sessionStatus → 停止按钮
│   SessionListViewModel.combine()    → treeNodes.status → 列表行状态图标
│   TreeNode                          → 目录活跃数计算
```

### 2.3 场景-根因映射

| 场景 | 触发的根因 | 当前表现 |
|------|-----------|---------|
| A. 首次进入 busy 会话 | R2 | 短暂 Idle → REST 到达后修正 |
| B. App 被杀后重新打开 | R2 | 冷启动 preLoadSessions 不设状态 |
| C. Home 键后台再切回 | — | 基本正常（ON_RESUME 有 sync） |
| D. SSE 断线重连 | R1, R6 | **busy 被强制 Idle + 消息残缺** |
| E. 会话列表页 | R3 | **无任何校正，冷启动/后台恢复错误** |
| F. 冷启动加载 | R2 | 所有会话默认 Idle |
| G. 会话间切换 | — | 正常（Singleton 保留状态） |

---

## 3. 设计原则

### P1: Source of Truth 明确化

- **SSE 实时事件** = 实时更新的主数据源（推模式）
- **REST API** = 启动/恢复/重连时的初始化与校正（拉模式）
- **本地缓存** = 不存在，`_sessionStatuses` 是纯内存 Singleton

### P2: REST 全量利用

`GET /session/status` 返回所有会话状态。每次调用后应将**全量结果**写入 `_sessionStatuses`，而非只取一条。一次 REST 调用同时校正当前会话 + 列表页所有会话。

### P3: 恢复时 REST 为权威源

SSE 重连后的消息恢复，REST 返回的数据比断线前的本地数据更新、更完整，应直接覆盖本地。不保留断线前的增量。

### P4: 分离关注点

- `_isLoading` = 消息加载状态，由 `loadMessages()` 独立管理
- `_sessionStatuses` = 运行状态，由 `syncSessionStatus()` / SSE 独立管理
- 两者不应交叉操作

---

## 4. 改动清单

### 4.1 MessageEventHandler.kt — 新增 `replaceMessages()`

**问题**: `mergeMessages()` 保留本地旧数据（`existingById[newMsg.id] ?: newMsg`），用于 SSE 重连恢复时逻辑完全错误。

**方案**: 新增 `replaceMessages(sessionId, newMessages)` 方法，REST 数据直接覆盖本地消息和 parts。

```kotlin
fun replaceMessages(sessionId: String, newMessages: List<MessageWithParts>) {
    _messages.update {
        it + (sessionId to newMessages.map { m -> m.info }.sortedBy { m -> m.time.created })
    }
    val partsMap = newMessages.associate { it.info.id to it.parts }
    _parts.update { it + partsMap }
}
```

**保留**: `mergeMessages()` 不改，仍用于 `ChatViewModel.loadMessages()`（OOM 降级）和 `loadOlderMessages()`（加载更多历史）场景。

### 4.2 EventDispatcher.kt — 新增代理方法

**方案**: 新增两个代理方法。

```kotlin
fun replaceMessages(sessionId: String, messages: List<MessageWithParts>) =
    messageHandler.replaceMessages(sessionId, messages)

fun syncAllSessionStatuses(statuses: Map<String, SessionStatus>) =
    sessionHandler.updateAllSessionStatuses(statuses)
```

`syncAllSessionStatuses` 批量写入所有会话状态，用于 `syncSessionStatus()` 和 `recoverMessages()` 中。

### 4.3 SessionEventHandler.kt — 新增 `updateAllSessionStatuses()`

**问题**: `setSessions()` 不初始化 `_sessionStatuses`（根因 R2）。

**方案**: 新增批量写入方法。

```kotlin
fun updateAllSessionStatuses(statuses: Map<String, SessionStatus>) {
    _sessionStatuses.update { current ->
        current + statuses
    }
}
```

**不改动 `setSessions()`**: `setSessions` 的职责是管理 session 列表，不应混入状态逻辑。状态初始化由调用方（`preLoadSessions` / `recoverMessages`）通过 `fetchSessionStatus` + `updateAllSessionStatuses` 显式完成。

### 4.4 SseConnectionManager.kt — 重写 `recoverMessages()`

**问题**: 
- R1: 盲目 `markSessionIdle()` 将 busy 会话错误标为 Idle
- R6: `mergeMessages()` 保留旧数据，丢弃 REST 新数据

**方案**: 分两阶段恢复。

```
阶段 1: 消息恢复
  对每个 session:
    messages = api.listMessages(conn, sessionId)  // REST 获取完整消息
    eventDispatcher.replaceMessages(sessionId, messages)  // REST 覆盖本地

阶段 2: 状态同步
  statuses = api.fetchSessionStatus(conn)  // REST 获取真实状态
  for (sessionId, statusInfo) in statuses:
    status = when (statusInfo.type) {
      "busy" → Busy
      "retry" → Retry(...)
      else → Idle
    }
    eventDispatcher.updateSessionStatus(sessionId, status)
    if (status == Idle) {
      eventDispatcher.markSessionIdle(sessionId)  // 只对真正 idle 的才标记
    }
```

**关键变化**:
- `mergeMessages` → `replaceMessages`（REST 为权威源）
- 盲目 `markSessionIdle` → 先查真实状态再决定
- 新增 `fetchSessionStatus` 调用获取真实状态

**注意**: `recoverMessages()` 只在 `attempt > 1`（重连）时调用。首次连接（`attempt == 1`）走 `preLoadSessions()`，后者也需要追加 `fetchSessionStatus` 调用来初始化状态（见 4.7）。

### 4.7 SseConnectionManager.kt — `preLoadSessions()` 追加状态初始化

**问题**: R2 — `preLoadSessions()` 只加载 session 列表，不初始化 `_sessionStatuses`。

**方案**: 在 `preLoadSessions()` 末尾追加 `fetchSessionStatus` + `syncAllSessionStatuses` 调用。

```
preLoadSessions() 末尾追加:
  statuses = api.fetchSessionStatus(conn)
  eventDispatcher.syncAllSessionStatuses(statusMap)
```

**效果**: 首次 SSE 连接（attempt == 1）时，session 列表 + 状态同时初始化。冷启动后所有会话在 `_sessionStatuses` 中都有正确值。

### 4.5 ChatViewModel.kt — 改进 `syncSessionStatus()`

**问题**:
- R4: 只用 `statuses[sessionId]`，丢弃全量数据
- R5: 不恰当地设置 `_isLoading.value = false`

**方案**: 全量写入 + 移除 `_isLoading` 副作用。

```kotlin
fun syncSessionStatus() {
    viewModelScope.launch {
        // ... 获取 conn ...
        val result = api.fetchSessionStatus(conn)
        result.onSuccess { statuses ->
            // 全量写入所有会话状态
            val statusMap = statuses.mapValues { (_, info) ->
                when (info.type) {
                    "busy" -> SessionStatus.Busy
                    "retry" -> SessionStatus.Retry(info.attempt, info.message, info.next)
                    else -> SessionStatus.Idle
                }
            }
            eventDispatcher.syncAllSessionStatuses(statusMap)

            // 对当前会话，如果 idle 则修正消息完成状态
            val currentStatus = statusMap[sessionId]
            if (currentStatus == null || currentStatus is SessionStatus.Idle) {
                eventDispatcher.markSessionIdle(sessionId)
            }
        }
        // 移除 _isLoading.value = false
    }
}
```

**变化**:
- 全量 `syncAllSessionStatuses(statusMap)` 替代单条 `updateSessionStatus`
- 保留对当前会话的 `markSessionIdle` 调用（idle 时修正未完成消息）
- 移除 `_isLoading.value = false`（不归此方法管）

### 4.6 SessionListViewModel.kt — 新增状态校正

**问题**: R3 — 会话列表页完全没有状态校正。

**方案**: 在 `refreshSessions()` 中增加状态同步。

在 `refreshSessions()` 的现有逻辑（加载 session 列表）之后，追加 `fetchSessionStatus` 调用，将全量状态写入 `eventDispatcher`。

```kotlin
// refreshSessions() 末尾追加:
val conn = ... // 获取当前服务器连接
api.fetchSessionStatus(conn).onSuccess { statuses ->
    val statusMap = statuses.mapValues { (_, info) ->
        when (info.type) {
            "busy" -> SessionStatus.Busy
            "retry" -> SessionStatus.Retry(info.attempt, info.message, info.next)
            else -> SessionStatus.Idle
        }
    }
    eventDispatcher.syncAllSessionStatuses(statusMap)
}
```

**触发时机**:
- SessionListScreen 已有下拉刷新 → 触发 `refreshSessions()`
- `init` 块中的 `loadSessions()` 调用 → 首次加载时同步

**不添加 ON_RESUME**: SessionListScreen 没有 ON_RESUME 回调，且添加它需要改 UI 层。下拉刷新 + init 加载已覆盖主要场景。如果后续需要，可在 SessionListScreen 中添加 DisposableEffect。

---

## 5. 改动文件总览

| # | 文件 | 改动类型 | 说明 |
|---|------|---------|------|
| 1 | `MessageEventHandler.kt` | 新增方法 | `replaceMessages()` |
| 2 | `EventDispatcher.kt` | 新增方法 | `replaceMessages()` + `syncAllSessionStatuses()` 代理 |
| 3 | `SessionEventHandler.kt` | 新增方法 | `updateAllSessionStatuses()` |
| 4 | `SseConnectionManager.kt` | 重写方法 | `recoverMessages()` — replaceMessages + fetchSessionStatus |
| 5 | `SseConnectionManager.kt` | 修改方法 | `preLoadSessions()` — 追加 fetchSessionStatus 初始化状态 |
| 6 | `ChatViewModel.kt` | 修改方法 | `syncSessionStatus()` — 全量写入 + 移除 _isLoading |
| 7 | `SessionListViewModel.kt` | 修改方法 | `refreshSessions()` — 追加状态同步 |

**不需要改动的文件**:
- UI 层（ChatScreen, ChatInputBar, SessionRow, TreeNode）— 纯消费者，数据源修正后自动正确
- SessionListScreen, HomeScreen — 不涉及状态逻辑
- SseClient.kt — 事件解析已完整覆盖
- OpenCodeApi.kt — API 接口不变
- SessionStatus.kt — 枚举值不变

---

## 6. 场景验证

改动后每个场景的预期行为：

| 场景 | 修复后表现 |
|------|-----------|
| A. 首次进入 busy 会话 | `syncSessionStatus` 全量写入 → 所有会话状态正确 |
| B. App 被杀后重新打开 | `recoverMessages` 中 `fetchSessionStatus` 初始化状态 → 所有会话正确 |
| C. Home 键后台再切回 | ON_RESUME `syncSessionStatus` 全量写入 → 正确 |
| D. SSE 断线重连 | `recoverMessages` 改用 `replaceMessages` + `fetchSessionStatus` → busy 保持 Busy + 消息完整 |
| E. 会话列表页 | `refreshSessions` 追加状态同步 → 列表状态正确 |
| F. 冷启动加载 | `preLoadSessions()` 追加 `fetchSessionStatus` 初始化状态 → 所有会话正确 |
| G. 会话间切换 | Singleton 保留 + `syncSessionStatus` 全量写入 → 正确 |

---

## 7. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| `replaceMessages` 覆盖本地增量 | REST `listMessages` 无 limit 参数，返回完整快照。服务器是权威源，覆盖安全 |
| `fetchSessionStatus` 网络失败 | 已在 try-catch 中，失败时不更新（保持 SSE 推送的状态） |
| `syncAllSessionStatuses` 覆盖 SSE 推送的更新值 | SSE 事件持续推送，即使 REST 返回过期数据也会被后续 SSE 事件覆盖 |
| `SessionListViewModel.refreshSessions` 增加一次 REST 调用 | `fetchSessionStatus` 是轻量 API，延迟可忽略 |
