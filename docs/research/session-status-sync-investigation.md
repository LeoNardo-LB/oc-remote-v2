# 会话状态同步机制调研报告（v2 — 深挖修正版）

> 调查两个用户反馈的 bug：① 重启后目录树看不到活跃会话 ② 流式结束后进度条卡死。
> 方法：oc-remote 代码级根因追踪（含 SessionStatusManager FSM 深挖）+ OpenCode 服务端源码 + 官方 TuI 对比。
> **v2 修正**：深挖发现 Bug 2 的进度条由会话级 FSM 状态驱动（非 part.time.end），且与 Bug 1 同源。

---

## 执行摘要

两个 bug **本是同一个根因**：所有 REST 状态查询都用 `directory = null`，而 OpenCode 服务端是 **per-directory 内存隔离**，导致非默认 directory（多 project worktree）的会话状态永远拿不到。

更深层的架构问题是**三层状态源各自独立、同步机制割裂**：

| 状态层 | 持有者 | 更新来源 | 消费者 |
|--------|--------|---------|--------|
| ① `eventDispatcher.sessionStatuses` | SessionEventHandler | SSE SessionStatus + REST `syncAllSessionStatuses` | 目录树（SessionListViewModel） |
| ② `SessionStatusManager.statusFlow` | 独立 FSM | SSE 事件（经 FSM）+ REST `triggerRestValidation` | **会话内进度条（ChatViewModel）** |
| ③ `message.time.completed` / `part.time.end` | MessageEventHandler | SSE message 事件 + REST replaceMessages + `markSessionIdle` | 消息流式动画、Reasoning 计时器 |

- **Bug 1** 发生在第 ① 层（目录树 directory=null）
- **Bug 2** 发生在第 ② 层（SessionStatusManager.triggerRestValidation directory=null）
- `markSessionIdle` 漏 Part.Text 是第 ③ 层的**次要 bug**（影响 Reasoning 计时器，非用户报告的进度条主因）

---

## 一、状态管理架构全景（深挖发现）

### 1.1 进度条的真实状态源（关键修正）

用户报告的"进度条一直转"是 **ChatScreen 顶部的 `LinearProgressIndicator`**（`ChatScreen.kt:627-635`）：
```kotlin
val isBusy = sessionMeta.sessionStatus is SessionStatus.Busy || sessionMeta.sessionStatus is SessionStatus.Retry
AnimatedVisibility(visible = isBusy, ...) { LinearProgressIndicator(...) }
```

`sessionMeta.sessionStatus` 来自 `ChatViewModel.sessionMetaState`（L539-562），其 `statuses` 来自 **`SessionStatusManager.statusFlow`**（注释明确 "FSM-driven"），**不是** `eventDispatcher.sessionStatuses`，**也不是** `part.time.end`。

（注：`PartContent.kt:117` 的 `part.time?.end == null` 只驱动 `ReasoningBlock` 的计时器，Text part 无 streaming UI；`ChatMessageList.kt:141` 的 `message.time.completed == null` 驱动消息流式动画。两者都不是顶部进度条。）

### 1.2 SessionStatusManager —— 独立的 FSM 状态机

`data/repository/SessionStatusManager.kt`：每个 session 一个 `SessionFSMState`，经 `SessionStatusFSM.transition()`（纯函数）驱动。事件入口：
- `onSendParts` / `onAbort`（客户端动作）
- `onSseEvent`（SSE：SessionStatus/SessionIdle/SessionError/StepStarted/TextStarted/ToolInputStarted/...）
- `onRestValidation`（REST 校验结果）

**设计完善的多层自愈**：
- **L2 staleness guard**：每 5s 检查，Busy 超 15s 无事件 → 触发 REST 校验
- **L3 REST validation**：`fetchSessionStatuses` → `onRestValidation` → FSM 转 Idle
- **L5 cross-validation**：Idle 但有 incomplete assistant → 触发 REST 校验
- FSM 的 `RestValidation` 处理（SessionStatusFSM.kt:83-92）**本身正确**，能把 Busy 转 Idle

### 1.3 StreamingStateTracker 是死代码

`domain/tracker/StreamingStateTracker.kt`（在 SessionNextEventHandler 里实例化）**只写不读**——只有 `onStarted/onDelta/onEnded/cleanup`，无任何 `.getState()` 外部读取点。不影响 UI，是遗留跟踪器。

---

## 二、统一根因：directory 传播缺失

### 2.1 服务端机制（OpenCode 源码证实）

`sst/opencode` 服务端 **per-instance（per-directory）内存隔离**：
- `GET /session/status` handler 直接返回 `statusSvc.list()` = 当前 instance 的全部 session 状态
- instance 由 `x-opencode-directory` header 路由（`WorkspaceRoutingMiddleware`）
- **不传 directory → 默认 instance → 只返回该 directory 的会话**
- 状态 Map 中缺失的 sessionID 兜底返回 idle：`data.get(id) ?? { type: "idle" }`
- SSE 事件同样按 `event.location.directory === instance.directory` 过滤

### 2.2 客户端的 directoryHeader（确凿）

`data/api/ApiClient.kt:31-33`：
```kotlin
internal fun HttpRequestBuilder.directoryHeader(directory: String?) {
    directory?.let { header("x-opencode-directory", URLEncoder.encode(it, "UTF-8")) }  // null → 不发 header
}
```

`directory = null` 时**不发 header** → 服务端默认 instance → 拿不到非默认 directory 的会话状态。

### 2.3 所有出错的位置（统一根因）

| # | 位置 | 调用 | 影响 |
|---|------|------|------|
| 1 | `SseConnectionManager.syncSessionStatuses` (L322) | `fetchSessionStatus(conn)` 无 directory | Bug 1（preLoadSessions 后状态缺失） |
| 2 | `SessionListViewModel.syncSessionStatuses` (L343) | `fetchSessionStatus(conn, directory=null)` | Bug 1（loadSessions 后状态缺失） |
| 3 | **`SessionStatusManager.triggerRestValidation` (L189)** | `fetchSessionStatuses(sid, directory=null)` | **Bug 2（FSM 自愈失效）** |

对比**正确做法**——`SessionActionsDelegate.syncSessionStatus`（L110）/`refreshAndSync`（L134）：用 `directory = sessionDirectoryProvider()`，所以进入会话能查到状态。

### 2.4 Bug 2 的完整因果链

1. 用户在会话中（**非默认 directory**，如某 project worktree）流式输出 → FSM 为 Busy → 顶部进度条显示
2. 切走，会话在服务端结束 → 服务端发 `session.status(idle)`/`session.idle`
3. 应用后台，SSE 事件丢失（系统网络限制）
4. `SessionStatusManager` FSM 卡 Busy
5. **L2 staleness guard**（每 5s）检测到 Busy>15s → 触发 `triggerRestValidation`
6. `fetchSessionStatuses(sid, directory=null)` → 该会话不在默认 directory → `statuses[sessionId] = null`
7. L192 `if (serverStatus != null)` → **跳过 `onRestValidation`**
8. FSM 永远卡 Busy → 进度条永远转

同理，ON_RESUME 的 `refreshIfNeeded` → `refreshAndSync` 虽用 `sessionDirectoryProvider()`（正确），但它更新的是 `eventDispatcher`（第①层），**不更新 SessionStatusManager**（第②层）。两层割裂导致即使 ON_RESUME 刷新了 eventDispatcher，进度条读的 FSM 仍卡 Busy。

### 2.5 Bug 1 的完整因果链

1. 重启 App，`SessionListViewModel.init` → `loadSessions` → 按 `project.worktree` 查 sessions（正确），但 `syncSessionStatusesFromServer()` 用 `directory=null` 查 status
2. 非默认 directory 的活跃会话状态缺失 → `sessionStatuses` 里没有 → `buildTreeNodes` 的 `activeSessionCount=0` → 目录树不显示活跃
3. 进入会话 → `SessionActionsDelegate.syncSessionStatus` 用 `sessionDirectoryProvider()` → 查到真实状态 → 回到目录树显示活跃（因为 `syncAllSessionStatuses` 更新了 eventDispatcher）

---

## 三、次要 bug：markSessionIdle 漏 Part.Text（第③层）

`MessageEventHandler.markSessionIdle`（488-525）的 force-complete 只处理 `Part.Reasoning`，漏 `Part.Text`。两者 `time.end` 结构相同（Part.kt:58/71）。影响：后台丢 `ReasoningEnded`/`TextEnded` 后，Reasoning 计时器/Text 相关流式 UI 可能卡住。

**但这不是用户报告的"顶部进度条"主因**（进度条读 FSM，不读 part.time.end）。仍应修复，属 P1。

---

## 四、官方 TuI 对比（为何 oc-remote 必须更强）

TuI（`packages/tui/src/context/`）：
- 纯推导式状态：`last.time.completed ? idle : working`，**无 FSM、无 force-complete、无 staleness guard**
- 本地进程模式，靠 `server.instance.disposed` → 全量 bootstrap 自愈
- 无 sequence/gap 检测，按 id 幂等更新

**oc-remote 不能照搬**：远程移动端网络断线频繁、server 不 dispose。oc-remote 已有 SessionStatusManager FSM + 多层自愈（比 TuI 先进），但**自愈的 directory 传播缺失导致对多 worktree 会话失效**。

服务端 `cleanup()`（`processor.ts`）经 `Effect.ensuring` 强制兜底关闭 text/reasoning/tool part（补 time.end），但前提是客户端收到对应事件；客户端必须自己有 directory 正确的 REST 兜底。

---

## 五、解决方案（修正版，按根因统一）

### 🔴 P0 — 统一根因修复：REST 状态查询正确传 directory

这是修复 Bug 1 **和** Bug 2 的核心。三处 `directory=null` 都需修正：

**修复点 1 & 2（Bug 1）**——`SseConnectionManager.syncSessionStatuses` + `SessionListViewModel.syncSessionStatuses`：
遍历所有 project worktree 逐 directory 查询，聚合后一次性同步：
```kotlin
val aggregated = mutableMapOf<String, SessionStatus>()
for (project in projects) {
    fetchSessionStatus(conn, directory = project.worktree).onSuccess { aggregated += it }
}
eventDispatcher.syncAllSessionStatuses(aggregated)
```

**修复点 3（Bug 2，关键）**——`SessionStatusManager.triggerRestValidation`：
当前签名 `triggerRestValidation(sessionId)` 不知道目标会话的 directory。两种方案：
- **方案 A（推荐）**：从 `eventDispatcher.sessions` 查该 sessionId 的 directory，用它查询
- **方案 B**：ChatViewModel 像 `setServerId` 一样 `setCurrentDirectory(directory)`，SessionStatusManager 用它

```kotlin
// 方案 A 伪代码
private fun triggerRestValidation(sessionId: String) {
    val sessionDir = eventDispatcher.sessions.value.find { it.id == sessionId }?.directory
    appScope.launch {
        val result = sessionRepositoryProvider.get().fetchSessionStatuses(sid, directory = sessionDir)
        result.onSuccess { statuses ->
            val serverStatus = statuses[sessionId]
            if (serverStatus != null) onRestValidation(sessionId, serverStatus)
        }
    }
}
```

### 🔴 P0 — 补充：SessionActionsDelegate 的更新需同步到 SessionStatusManager

`refreshAndSync`（L128-148）当前只更新 `eventDispatcher`。若 Bug 2 场景下 FSM 已卡 Busy，需让 `syncAllSessionStatuses` 或 `markSessionIdleProtected` 也通知 SessionStatusManager（调 `onRestValidation`）。否则两层割裂仍存在。

### 🟡 P1 — 次要 bug：markSessionIdle 补 Part.Text

`MessageEventHandler.markSessionIdle`（488-525）增加 Part.Text 处理，对齐服务端 cleanup 范围：
```kotlin
if (part is Part.Text && part.time?.end == null) {
    part.copy(time = Part.Text.Time(start = part.time?.start ?: now, end = now))
}
```

### 🟡 P1 — 清理死代码

`StreamingStateTracker`（只写不读）可移除或明确用途，避免误导。

### 🟢 P2 — 架构优化

- 统一三层状态源的同步入口（一次 REST 校验同时更新 ①②③）
- 评估用服务端 `sync/history`（按 seq 增量）替代部分全量 replaceMessages
- SessionStatusManager 的 staleness guard 可作为所有状态层的统一兜底

---

## 六、验证计划

- **Bug 1**：多 project worktree，某 directory 有 busy 会话，重启 App 验证目录树显示活跃
- **Bug 2**：在非默认 directory 的会话中流式输出，切后台等结束，切回验证进度条停止
- **回归**：`SessionStatusFSMTest`、`SessionEventHandlerTest`、`EventDispatcherIntegrationTest`、`MessageEventHandlerTest`、`ChatViewModelStreamingTest` 应全绿
- 新增测试：`SessionStatusManager` 的 `triggerRestValidation` 在非默认 directory 下的行为

---

## 七、关键代码位置索引

| 文件 | 关键点 | 行号 |
|------|--------|------|
| `SessionStatusManager.kt` | FSM + L2/L3/L5 自愈 + **triggerRestValidation(directory=null)** | 185-203 |
| `SessionStatusFSM.kt` | 纯函数 FSM（RestValidation 正确） | 83-92 |
| `ApiClient.kt` | `directoryHeader(null)` 不发 header | 31-33 |
| `SseConnectionManager.kt` | preLoadSessions + syncSessionStatuses（无 directory） | 261-350 |
| `SessionListViewModel.kt` | syncSessionStatuses（无 directory） | 341-362 |
| `SessionActionsDelegate.kt` | syncSessionStatus（有 directory，对比）+ REFRESH_COOLDOWN_MS=5s | 105-148, 600 |
| `ChatScreen.kt` | 顶部进度条 isBusy + ON_RESUME refreshIfNeeded | 474-487, 627-635 |
| `ChatViewModel.kt` | sessionMetaState 读 SessionStatusManager.statusFlow | 536-562 |
| `EventDispatcher.kt` | syncAllSessionStatuses + markSessionIdle/Protected | 311-345, 403-415 |
| `MessageEventHandler.kt` | markSessionIdle（漏 Text，次要 bug） | 488-525 |
| `PartContent.kt` | Reasoning isStreaming（非顶部进度条） | 117 |
| `StreamingStateTracker.kt` | 死代码（只写不读） | 24-96 |

---

*调研日期：2026-06-30（v2）。基于 oc-remote 当前代码 + sst/opencode dev 分支源码 + 官方 TuI 实现。*
*v1→v2 修正：Bug 2 主因从"markSessionIdle 漏 Text"修正为"SessionStatusManager.triggerRestValidation 的 directory=null 导致 FSM 自愈失效"；确认 Bug1/Bug2 同源。*
