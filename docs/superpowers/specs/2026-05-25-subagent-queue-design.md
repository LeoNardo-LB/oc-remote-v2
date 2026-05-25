# 子 Agent 查看体验 + QUEUED 排队标记 设计文档

> **日期**: 2026-05-25
> **状态**: Draft
> **范围**: 需求 #2（子 Agent 查看体验）+ 需求 #3（Queue 排队标记）

## 背景

### 需求来源

| # | 需求 | 当前状态 |
|---|------|----------|
| 2 | 子 Agent 查看体验 — 只看返回值，无法深入，缺 Markdown 渲染 | ❌ 未实现 |
| 3 | Queue 排队标记 — 消息未完成时发新消息缺少排队标记 | ❌ 未实现 |

### 服务端行为调研结论

通过深扒 OpenCode TS 服务端源码（`anomalyco/opencode`），确认了以下行为：

**`prompt_async` 的完整处理流程：**

1. HTTP handler 立即返回 204（fire-and-forget）
2. 用户消息写入 SQLite DB
3. 服务端 `loop()` 以 `while(true)` 循环运行，每轮迭代从 DB 读取所有消息
4. 当会话忙碌时收到新 prompt → 不启动新循环，消息自然积累在 DB 中
5. 当前循环完成后，下一轮迭代自动发现所有未处理用户消息
6. 用 `<system-reminder>` 标签包裹这些消息，**合并为一次 LLM 调用**
7. 最终产生一个统一回复，回应所有消息内容

**结论：** 用户可以放心连续发送多条消息，服务端会自动合并处理。客户端只需要视觉标记"还没被处理到"的消息即可。

### OpenCode TUI 参考实现

| 功能 | OpenCode TUI 做法 |
|------|-------------------|
| 排队 | 不做本地队列。忙碌时仍可发送，后续 user 消息显示 `QUEUED` 徽章 |
| 防重复提交 | 简单 `submitting` boolean 标志 |
| 子 Agent 展示 | 每个 subagent 创建独立 Session（`parentID` 关联）。父会话中用一行摘要显示，点击跳转子 Session |
| 子 Agent 导航 | `SubagentFooter` 底部导航栏 + `ctrl+x p/[/,/.` 快捷键 |
| 子 Agent 输出 | TUI 中用 `InlineTool` 行内渲染（不展开）；Web 视图用 `ContentMarkdown` 渲染 |

---

## 设计

### 功能 1：QUEUED 徽章

**目标：** 在用户消息气泡上显示"排队中"视觉标记，告知用户这条消息还没被 AI 处理到。

**判断逻辑（纯客户端）：**

```
条件：当前 session 有未完成的 assistant 消息
  → 在它之后发送的所有 user 消息显示 QUEUED 徽章
触发清除：收到 SSE session.idle 或 assistant 消息 completed
```

**数据流：**

```
EventReducer.sessionStatus == Busy
  → ChatUiState.pendingAssistantMessageId = 最新未完成 assistant 消息的 id
  → ChatScreen 渲染 user 消息时：
     if (message.role == "user" && message.id > pendingAssistantMessageId)
       → 显示金色 QUEUED 徽章
```

**改动范围：**

| 文件 | 改动 |
|------|------|
| `ChatViewModel.kt` | `ChatUiState` 新增 `pendingAssistantMessageId: String?` 字段；在状态聚合时计算该值 |
| `ChatScreen.kt` | `UserMessage` composable 中条件渲染 QUEUED 徽章（金色 Badge） |

**不改动的部分：**
- 不改服务端
- 不改 EventReducer
- 不加本地消息队列
- 不改 `sendParts()` 逻辑

**UI 规格：**
- 徽章样式：金色背景 + 黑色文字 `QUEUED`，圆角，小号字体
- 位置：user 消息气泡头部，用户名右侧
- 动画：无（静态徽章即可）

---

### 功能 2a：子 Agent Markdown 渲染

**目标：** 将 `TaskToolCard` 中子 Agent 的纯文本输出替换为 Markdown 渲染，去掉 5000 字符截断。

**当前问题：**
- `ChatScreen.kt:5387-5394` 使用 `Text(output.take(5000))` 纯文本显示
- 用等宽字体（`CodeTypography`），无格式化
- 超过 5000 字符的输出被截断

**改动：**

| 文件 | 改动 |
|------|------|
| `ChatScreen.kt` | `TaskToolCard` 中将 `Text(output.take(5000))` 替换为 `MarkdownContent(markdown = output, textColor = contentColor, isUser = false)` |

复用已有的 `MarkdownContent` composable（第 4046-4169 行），它基于 `multiplatform-markdown-renderer`，支持标题、代码块（语法高亮）、列表、链接、图片。

---

### 功能 2b：Part.Agent 渲染

**目标：** 将 `Part.Agent` 从"完全跳过"改为渲染为轻量级 Agent 标识卡片。

**当前问题：**
- `ChatScreen.kt:4038` — `is Part.Agent -> { /* skip */ }`
- 子 Agent 的 `name` 和 `source` 信息完全不显示

**改动：**

| 文件 | 改动 |
|------|------|
| `ChatScreen.kt` | 在 `PartContent` 的 `when` 分支中，将 `is Part.Agent -> { /* skip */ }` 改为渲染一个 Agent 标识卡片 |

**Agent 标识卡片 UI 规格：**
- 高度紧凑的单行卡片
- 左侧：Agent 图标（🤖）+ Agent 名称（紫色高亮）
- 右侧：来源标识（灰色小字 `source: subagent`）
- 作为消息流中的视觉分隔符，不占用过多空间

**Part.Agent 数据模型：**
```kotlin
data class Agent(
    val name: String,    // Agent 名称，如 "Coder"
    val source: String   // 来源，如 "subagent"
) : Part()
```

---

### 功能 2c：子 Agent 跳转导航

**目标：** 在 `TaskToolCard` 中添加"查看详情"按钮，点击后跳转到子 Session 的 ChatScreen 页面。

**已有基础设施：**

| 组件 | 状态 | 位置 |
|------|------|------|
| `Session.parentId` 字段 | ✅ 已存在 | `Session.kt:16` |
| `listSessionChildren` API | ✅ 已存在 | `OpenCodeApi.kt:838` |
| `createSession(parentId)` API | ✅ 已存在 | `OpenCodeApi.kt:137` |
| SSE 多会话支持 | ✅ 已存在 | `OpenCodeConnectionService.kt:111` — 按 sessionId 分发事件 |
| ChatScreen 路由含 sessionId 参数 | ✅ 已存在 | `Screen.kt:45-63` |
| NavGraph `onNavigateToSession` 回调 | ✅ 已存在 | `NavGraph.kt:495-509` |

**需要新增：**

| 组件 | 改动 |
|------|------|
| `ChatViewModel` | 新增 `childSessions: StateFlow<List<Session>>` + `loadChildSessions()` 方法 |
| `ChatViewModel` | `ChatUiState` 新增 `childSessions: List<Session>` 字段 |
| `ChatScreen.kt` | `TaskToolCard` 添加"查看详情 →"按钮 |
| `ChatScreen.kt` | 子会话 ChatScreen 顶部显示"← 返回父会话"导航栏 |
| `NavGraph.kt` | 修改导航策略为 push（而非 replace），支持从子会话返回父会话 |

**导航策略：**

当前 `onNavigateToSession` 使用 `popUpTo` replace 策略（跳转后不能返回）。子 Agent 跳转需要改为 **push** 策略：
- 点击"查看详情" → `navController.navigate(Screen.Chat.createRoute(childSessionId, ...))`
- 不使用 `popUpTo`，自然压栈
- 子会话 ChatScreen 顶部显示"← 返回父会话"按钮，点击 `navController.popBackStack()`

**子会话标识 UI：**
- 当 `session.parentId != null` 时，ChatScreen 顶部显示一个紧凑导航栏
- 左侧："← 返回父会话" 按钮
- 右侧：Agent 名称 + 子会话编号

**TaskToolCard 的子 Agent 关联 — 子会话 ID 获取路径：**

OpenCode 服务端（`packages/opencode/src/tool/task.ts`）在 task 工具完成时，将子会话 ID 写入 `ToolState.Completed.metadata`：

```json
{
  "status": "completed",
  "metadata": {
    "sessionId": "sub-session-id-here",
    "parentSessionId": "parent-session-id",
    "model": { "providerID": "...", "modelID": "..." }
  }
}
```

客户端获取子会话 ID 的代码路径：

```kotlin
val subSessionId = (tool.state as? ToolState.Completed)
    ?.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
```

- 如果 `subSessionId` 存在 → 显示"查看详情 →"按钮，点击跳转
- 如果 `subSessionId` 不存在（如工具仍在运行或 metadata 缺失）→ 隐藏按钮
- 备选方案：如果 metadata 中没有 sessionId，改用 `api.listSessionChildren(conn, currentSessionId)` 获取子会话列表

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 修改 | `ui/screens/chat/ChatViewModel.kt` | 新增 `pendingAssistantMessageId` 状态、`childSessions` 状态、`loadChildSessions()` 方法 |
| 修改 | `ui/screens/chat/ChatScreen.kt` | QUEUED 徽章、Markdown 渲染、Part.Agent 卡片、子 Agent 跳转按钮、子会话导航栏 |
| 修改 | `ui/navigation/NavGraph.kt` | 子会话导航 push 策略 |
| 不动 | `data/api/OpenCodeApi.kt` | API 已存在 |
| 不动 | `domain/model/Session.kt` | `parentId` 字段已存在 |
| 不动 | `data/repository/EventReducer.kt` | 不改动 |

---

## 不纳入本次范围

| 功能 | 原因 |
|------|------|
| 本地消息队列 | 服务端已支持合并，不需要客户端队列 |
| 子会话导航快捷键 | 移动端无键盘，暂不需要 |
| Session 列表展示子会话 | 当前 `SessionListViewModel` 过滤了 `parentId != null`，可后续迭代 |
| 子会话内容预览（不跳转直接展开） | OpenCode 也不这么做，保持一致的跳转体验 |
| 消息发送失败重试 | 独立问题，可在后续 PR 中处理 |

---

## 技术约束

- **Kotlin 2.0.21** + **Compose BOM 2024.12.01**
- **Markdown 渲染**：复用已有的 `MarkdownContent` composable（基于 `multiplatform-markdown-renderer`）
- **导航**：使用 Jetpack Navigation Compose 的 `navController.navigate()` + `popBackStack()`
- **子会话数据**：通过 `api.listSessionChildren()` 获取，不需要修改 SSE 流
- **线程安全**：`childSessions` 使用 `MutableStateFlow`，`loadChildSessions()` 在 `viewModelScope.launch` 中执行

---

## 验收标准

1. **QUEUED 徽章**：会话忙碌时发送的新消息显示金色 QUEUED 徽章；AI 回复完成后徽章消失
2. **Markdown 渲染**：子 Agent 输出正确渲染标题、代码块、列表、链接；无截断
3. **Part.Agent 渲染**：Agent 标识卡片显示名称和来源
4. **子 Agent 跳转**：点击"查看详情"跳转到子会话 ChatScreen；可返回父会话
5. **编译**：`assembleDebug` BUILD SUCCESSFUL
6. **无回归**：现有 27 个单元测试全部通过
