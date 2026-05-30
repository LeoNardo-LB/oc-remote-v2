# Design H: Token 统计位置修正 + 嵌套滑动传递修复

## 日期
2026-05-30

## 问题

### H1: Token 统计位置错误
- `uiState.messages` 是 newest-first 顺序，但 `isTurnLast` 使用 `getOrNull(index + 1)` 检查更旧的下一条消息
- 导致 footer 出现在 turn 中最旧的消息上（视觉靠顶部），而非最新的消息上（视觉靠底部）

### H2: 嵌套滑动传递被阻断
- `consumeBoundaryScroll` 在 `onPostScroll` 中消费了所有边界余量
- 阻止了 tool card 内部滑动到达边界后向 parent LazyColumn 的传递
- 内容 < 半屏时也完全拦截，导致在 tool card 区域滑动时聊天列表完全不动

### H3: halfScreenHeight 重复且不一致
- 8 个文件各自内联计算 `halfScreenHeight`
- 部分有 200dp 兜底（BashToolCard, WriteToolCard, ReadToolCard, EditToolCard），部分没有（TaskToolCard, SearchToolCard, ReasoningBlock, ToolCardRenderer）

## 方案

### H1: 修正 isTurnLast 方向
文件：`ChatScreen.kt`（两处：主会话 + 子会话）

```kotlin
// Before:
val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                 uiState.messages.getOrNull(index + 1)?.isAssistant != true

// After:
val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                 (index == 0 || uiState.messages.getOrNull(index - 1)?.isAssistant != true)
```

在 newest-first 列表中，`index - 1` 指向更新的消息。当 `index == 0` 或上一条不是 assistant 时，当前消息就是 turn 的最后一条（chronologically latest）。

### H2: 删除 consumeBoundaryScroll
Compose 的 `verticalScroll` + `LazyColumn` 天然支持嵌套滚动传递：
- `verticalScroll` 到达边界后自动将剩余 delta 传给 parent
- 内容不够高时完全不拦截，所有 delta 直接传给 parent

这是开箱即用的行为，不需要自定义 `NestedScrollConnection`。

改动：
1. 删除 `ChatModifiers.kt` 中 `consumeBoundaryScroll` 函数
2. 所有调用点移除 `.consumeBoundaryScroll(scrollState)`

### H3: 统一提取 halfScreenHeight
在 `ChatModifiers.kt` 中新增：

```kotlin
@Composable
internal fun rememberHalfScreenHeight(): Dp {
    return maxOf(LocalConfiguration.current.screenHeightDp.dp / 2, 200.dp)
}
```

8 个文件统一替换。

## 影响范围
| 文件 | 改动 |
|------|------|
| `ChatScreen.kt` | 2 行 isTurnLast 修正（主会话 + 子会话） |
| `ChatModifiers.kt` | 删除 consumeBoundaryScroll + 新增 rememberHalfScreenHeight |
| BashToolCard.kt | 移除 consumeBoundaryScroll + 统一 halfScreenHeight |
| WriteToolCard.kt | 同上 |
| ReadToolCard.kt | 同上 |
| EditToolCard.kt | 同上 |
| TaskToolCard.kt | 同上 |
| SearchToolCard.kt | 同上 |
| ReasoningBlock.kt | 同上 |
| ToolCardRenderer.kt | 同上 |

## 验证
- 多条 assistant 消息组成的 turn，footer 只出现在最后一条（视觉最底部）
- tool card 内容 > 半屏：内部优先滚动，到边界后过渡到聊天列表
- tool card 内容 < 半屏：滑动直接驱动聊天列表
- 编译通过 + 单元测试通过
