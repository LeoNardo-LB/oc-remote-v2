# Design H: Token 统计位置修正 + 嵌套滑动传递修复

## 日期
2026-05-30

## 前提条件
- **Kotlin**: 2.3.21 · **Compose BOM**: 2026.05.01 · **Compose Compiler Plugin**: 2.3.21
- 上述版本中 `verticalScroll` + `LazyColumn` 的嵌套滚动机制已完全成熟，支持 `reverseLayout` + fling 边界传递
- 所有文件位于同一模块 `:app`，`internal` 可见性在模块内共享

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

这是开箱即用的行为，不需要自定义 `NestedScrollConnection`。删除 `consumeBoundaryScroll` 后，默认的 `NestedScrollConnection` 同时正确处理 drag（拖拽）和 fling（惯性滑动）的边界传递。

> **前提**：tool card 内容区域有固定高度约束（由 `heightIn(max = halfScreenHeight)` 提供）。没有固定高度约束时，同方向嵌套滚动会抛出 `IllegalStateException`。

改动：
1. 删除 `ChatModifiers.kt` 中 `consumeBoundaryScroll` 函数（含 `onPostFling` 覆写）
2. 所有调用点移除 `.consumeBoundaryScroll(scrollState)` — 共 8 处（BashToolCard, WriteToolCard, ReadToolCard, EditToolCard, TaskToolCard, SearchToolCard, ReasoningBlock, ToolCardRenderer）
3. 删除后检查每条 modifier 链语法完整性（无尾随点号或双点号）

**回退方案**：若删除后发现滚动行为异常（如边界不传递、fling 卡顿），可恢复 `consumeBoundaryScroll` 但将 `onPostScroll` 返回值改为 `Offset.Zero`（不消费余量），同时保留 `onPreScroll` 不拦截。

### H3: 统一提取 halfScreenHeight
在 `ChatModifiers.kt` 中新增（`internal` 可见性，同模块 8 个文件可直接访问，无需额外 import）：

```kotlin
@Composable
internal fun halfScreenHeight(): Dp {
    return maxOf(LocalConfiguration.current.screenHeightDp.dp / 2, 200.dp)
}
```

> **注意**：函数命名为 `halfScreenHeight()`（无 `remember` 前缀），因为内部不含 `remember { }` 委托。`LocalConfiguration.current` 在配置变化时自动触发重组，无需手动缓存。

8 个文件统一替换（两种模式归一）：
- 模式 A（有 200dp 兜底）：`val halfScreenHeight = maxOf(LocalConfiguration.current.screenHeightDp.dp / 2, 200.dp)` → `val halfScreenHeight = halfScreenHeight()`
- 模式 B（无兜底）：`val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2` → `val halfScreenHeight = halfScreenHeight()`

> **行为变更**：TaskToolCard、SearchToolCard、ReasoningBlock、ToolCardRenderer 4 个文件此前无 200dp 下限。统一后在屏幕高度 < 400dp 的设备上，tool card 最小高度将从不足 200dp 提升至 200dp——这是预期行为，确保多终端一致性。

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

### 功能验收
- 多条 assistant 消息组成的 turn，footer 只出现在最后一条（视觉最底部）
- tool card 内容 > 半屏：内部优先滚动，到边界后过渡到聊天列表
- tool card 内容 = 半屏（恰好等于 halfScreenHeight）：行为等同于 < 半屏（`verticalScroll` 无可滚动空间，delta 直接传给 parent）
- tool card 内容 < 半屏：滑动直接驱动聊天列表
- 删除 consumeBoundaryScroll 后，fling（快速滑动）从 tool card 到 chat list 的过渡流畅无卡顿
- 子会话（BottomSheet 内嵌的聊天）中 H1/H2/H3 行为与主会话一致（子会话复用相同的组件）

### 回归验证
```bash
.\gradlew :app:compileDevDebugKotlin          # 编译检查
.\gradlew :app:testDevDebugUnitTest            # 全量单元测试
```
- 重点关注 `TurnGroupCalculatorTest`（H1 回归）
- 手动回归：单条消息（无 turn 分组）、空消息列表、纯文本 tool card（无滚动）

### 人工测试
- 在真机/模拟器上验证 tool card 展开 → 内部滚动 → 到边界 → 聊天列表继续滚动的流畅性
- 验证快速 fling 从 tool card 区域到 chat list 过渡无跳跃
