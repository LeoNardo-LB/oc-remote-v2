# 统一 MessageCard 组件设计

## 背景

当前 oc-remote 的用户消息和 Agent 回复使用两个完全独立的组件：
- `ChatMessageBubble.kt` — 用户消息卡片
- `AssistantMessageCard.kt` — Agent 回复卡片

两者有大量重复逻辑（统计栏、气泡样式、Parts 渲染），且视觉风格不统一（用户有气泡而 Agent 无气泡）。需要合并为一个统一的 MessageCard 组件。

## 需求

1. **统一组件** — 合并 ChatMessageBubble + AssistantMessageCard 为一个 MessageCard 组件
2. **Agent 气泡** — Agent 回复用 surfaceVariant 灰色气泡包裹全部内容
3. **用户气泡** — 保持现有 provider 着色方案
4. **同轮合并** — 同一 turn 内多条 Agent 消息合并为一个气泡
5. **统计栏统一** — 气泡底部统计栏共享组件，内容按 role 区分
6. **子卡片交互统一** — 所有子卡片（Reasoning、工具卡片）点击标题栏展开/折叠，点击跳转按钮导航

## 设计

### 整体架构

```
ChatScreen (LazyColumn)
├── item: turn_group (user)  → MessageCard(role=User, ...)
├── item: turn_group (agent) → MessageCard(role=Assistant, turnMessages, ...)
├── item: turn_group (user)  → MessageCard(role=User, ...)
└── ...

MessageCard
├── Surface (气泡：颜色和形状按 role 切换)
│   └── Column
│       ├── Parts 渲染（文字、ReasoningBlock、工具卡片等）
│       └── FooterStatsRow (统计栏)
```

ChatScreen 的 LazyColumn items 从逐条渲染消息改为按 `turnGroups` 分组渲染。

### 气泡规格

```
用户气泡 (右对齐，provider 着色)          Agent 气泡 (左对齐，surfaceVariant 灰色)
┌──────────────────────────┐         ┌─────────────────────────────────┐
│ 用户输入文字               │         │ Agent 回复文字                    │
│                          │         │                                 │
│                          │         │ ▎● Thinking              ▼      │
│                          │         │ ▎ [思考内容]                     │
│                          │         │                                 │
│                          │         │ ┌─ Bash ──────────────────────┐ │
│                          │         │ │ $ npm install               │ │
│                          │         │ └─────────────────────────────┘ │
│                          │         │                                 │
│ 12:31 [QUEUED] [Undo][C]│         │ 12:30 [P] model ↑↓ $1 12.3s [C]│
└──────────────────────────┘         └─────────────────────────────────┘
```

| 属性 | 用户消息 | Agent 回复 |
|------|---------|-----------|
| 气泡颜色 | provider 着色 (primaryContainer / AMOLED: black) | surfaceVariant |
| 对齐 | 右对齐 | 左对齐 |
| 圆角 | 现有不对称圆角 | 统一 12dp 圆角 |
| 边框 | AMOLED: 1dp primary border | 无 |
| 内边距 | compact: 10/8dp, normal: 16/14dp | 同左 |

### 统计栏（FooterStatsRow）

共享组件，按 `role` 参数控制显示内容：

| 元素 | 用户消息 | Agent 回复 |
|------|---------|-----------|
| 时间 HH:mm | ✅ | ✅ |
| Provider 图标 | ❌ | ✅ |
| Model 名称 | ❌ | ✅ |
| Token ↑↓ | ❌ | ✅ |
| Cost $ | ❌ | ✅ |
| Duration | ❌ | ✅ |
| QUEUED badge | ✅ 条件 | ❌ |
| Undo 按钮 | ✅ 主会话 | ❌ |
| Copy 按钮 | ✅ 最右侧 | ✅ 最右侧 |

视觉规格：10sp、`onSurface.copy(alpha=0.35f)`、14dp 图标、`Arrangement.spacedBy(8.dp)`。

### 子卡片交互统一

所有 Agent 气泡内的子卡片（ReasoningBlock、BashToolCard、WriteToolCard 等）：

| 操作 | 行为 |
|------|------|
| **点击标题栏** | 展开/折叠（切换 AnimatedVisibility） |
| **点击跳转按钮** | 导航到子会话 |

与当前 ReasoningBlock 的交互完全一致。

### Turn 分组渲染

`TurnGroupCalculator.computeTurnGroups()` 已返回 `Map<Int, List<ChatMessage>>`（每轮 assistant 消息的索引 → 该轮全部消息列表）。

ChatScreen itemsIndexed 改为：

```kotlin
itemsIndexed(rawMessages, key = { msg -> msg.message.id }) { index, msg ->
    when {
        msg.isAssistant -> {
            val isFirstInTurn = /* index 是 assist 且前一条不是 assist */
            if (!isFirstInTurn) return@itemsIndexed // 非首条跳过，在首条渲染整轮
            val turnMessages = turnGroups[index] ?: listOf(msg)
            MessageCard(
                role = Role.Assistant,
                turnMessages = turnMessages,
                ...
            )
        }
        msg.isUser -> {
            MessageCard(
                role = Role.User,
                message = msg,
                ...
            )
        }
    }
}
```

`isCompactionTrigger` 的特殊处理：compact 分割线维持现有逻辑。

## 改动范围

| 操作 | 文件 |
|------|------|
| **新建** | `MessageCard.kt` — 统一消息卡片 |
| **删除** | `ChatMessageBubble.kt` |
| **删除** | `AssistantMessageCard.kt` |
| **修改** | `ChatScreen.kt` — items 改为 turn 分组渲染，import 更新 |
| **检查** | 搜索所有 import 这两个旧组件的位置 |

## 验收标准

### 需求 1：统一组件
- [ ] `ChatMessageBubble.kt` 和 `AssistantMessageCard.kt` 已删除
- [ ] 所有引用更新为 `MessageCard`
- [ ] 编译成功，测试通过

### 需求 2：Agent 气泡
- [ ] Agent 回复有 surfaceVariant 灰色背景气泡
- [ ] 气泡包裹全部内容（文字、工具卡片、统计栏）

### 需求 3：用户气泡
- [ ] 用户消息保持现有 provider 着色
- [ ] AMOLED 模式下的黑色背景 + 边框正常

### 需求 4：同轮合并
- [ ] 同一 turn 的多条 Agent 消息在一个气泡内
- [ ] 非首条消息不单独渲染
- [ ] 统计栏仅显示一条（在气泡底部）

### 需求 5：统计栏
- [ ] 用户统计栏：`HH:mm [QUEUED] [Undo] [Copy]`
- [ ] Agent 统计栏：`HH:mm [P] model ↑↓ $cost duration [Copy]`
- [ ] 样式统一（10sp, alpha=0.35f, 14dp, spacedBy=8dp）
- [ ] 子会话用户消息无 Undo 按钮
- [ ] Copy 在所有统计栏的最右侧

### 需求 6：子卡片交互
- [ ] 所有子卡片点击标题栏展开/折叠
- [ ] 跳转按钮点击导航到子会话

## 不涉及的改动

- 工具卡片的具体内容和样式（保持现有）
- 撤回逻辑（保持现有 onRevert 回调）
- 复制逻辑（保持现有 onCopyText 回调）
- ChatScreen 的其他非消息相关逻辑
