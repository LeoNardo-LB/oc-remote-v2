# SSE 视窗锚定调研报告

> 日期：2026-06-23  
> 分支：`feat/termlib-migration`  
> 文件：`ChatMessageList.kt` / `ChatScreen.kt`

## 问题描述

LazyColumn(reverseLayout=true) 渲染聊天消息。SSE 流式输出时最新消息高度持续增长。用户上滑到非底部位置后，视窗被"拖着往底部方向走"。

**关键对比（用户提供）：**
- 纯文字 SSE 输出 + 上滑 → **不往下拽** ✅
- 多消息回复（工具卡片、代码块、表格等）+ 上滑 → **被往下拽** ❌

---

## 架构概览

### LazyColumn 配置
```
ChatScreen.kt
  └─ rememberLazyListState() → listState
  └─ isAtBottom = derivedStateOf { firstVisibleItemIndex == 0 && offset < 100 }
  └─ autoScrollEnabled（LaunchedEffect 管理）
  └─ LaunchedEffect(messageCount) → scrollToItem(0) if autoScrollEnabled

ChatMessageList.kt
  └─ LazyColumn(reverseLayout = true)
       ├─ compaction_banner
       ├─ retry_banner
       ├─ tool_progress（带 layout 修饰器 + 反射补偿）
       ├─ step_progress（无补偿）
       ├─ question / permission
       └─ itemsIndexed(displayItems)
            └─ streamingMsgId 匹配的消息 → Modifier.layout { 反射补偿 }
            └─ 其他消息 → Modifier.fillMaxWidth()（无补偿）
```

### reverseLayout 语义（Compose 作者 Andrey Kulikov 确认）
- `firstVisibleItemScrollOffset` 是相对**底部锚点**的偏移
- off=0 = 在底部（看最新消息）
- off 增大 = 上滑（看更旧的消息）
- item 高度增长时 LazyColumn **保持 offset 不变**（底部锚定）
- 但此锚定仅对 **firstVisibleItem** 可靠；当增长的 item 不可见时锚定可能失效

### 反射机制 `LazyListReflection`
```kotlin
// 绕过 requestScrollToItem 的 scroll{} mutex（不取消 fling）
// 直接设置 LazyListState 内部的 scrollPosition.pendingPosition
// + 触发 measurementScopeInvalidator 导致重新 measure
private object LazyListReflection {
    fun requestScrollToItemNoCancel(state, index, scrollOffset)
}
```

---

## 实验记录

### 实验 1：原始代码 `offset + delta`（master 原始）

**代码：**
```kotlin
// 流式消息的 layout 修饰器内
val delta = realHeight - compensateState.lastHeight
if (shouldCompensate && delta > 0) {
    requestScrollToItemNoCancel(
        listState,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset + delta  // ← 跟随型
    )
}
```

**结果：**
- 流式消息可见时：**有效** ✅ — off 随 delta 增长，视窗内容不动
- 流式消息不可见时：**无补偿** ❌ — layout 修饰器不执行

**日志证据（纯文字 SSE，流式消息可见）：**
```
用户上滑到 off=79
d=69 → REFLECT: off=79+69=148 → off 变成 148
d=58 → REFLECT: off=204+58=262 → off 变成 262
d=116 → REFLECT: off=262+116=378 → off 变成 378
...（off 持续增长，但用户感知不到移动）
```
用户确认："不往下拽"

**日志证据（目录查看，流式消息不可见）：**
```
stream layout 执行: 0 次
REFLECT 执行: 0 次
off 从 9230 → 11851 → 回 0
```
用户确认："被往下拽"

**结论：** `+ delta` 方向正确（跟随型），但只在流式消息可见时有效。

---

### 实验 2：`offset - delta`

**假设：** reverseLayout 下 offset 应该减小 delta

**结果：** 用户反馈"SSE 输出 1，向下走估计有 2" — 过度补偿，方向错误。

**结论：** `- delta` 方向错误。`+ delta` 是正确的。

---

### 实验 3：`offset`（不加 delta）

**假设：** 设置 pending position = 当前 offset，钉住视窗

**结果：** off 仍然缓慢增长（61 → 198） — 补偿不足。

**结论：** 不加 delta 等于不补偿，LazyColumn 的自然调整仍然生效。

---

### 实验 4：完全移除反射

**结果：** 用户反馈"不行" — reverseLayout 自然锚定在非底部时不可靠。

---

### 实验 5：条件改为 `isAtBottom`

**假设：** 只在底部时执行反射

**结果：** `isAtBottom` 的 `derivedStateOf` 有**帧延迟** — off 远超 100 时仍返回 true。反射在非底部时仍执行。

**日志证据：**
```
off=224   atBot=true    ← 应该 false！
off=751   atBot=true
off=6590  atBot=true
```

**结论：** `derivedStateOf` 帧延迟导致条件不可靠。

---

### 实验 6：全局 snapshotFlow 钉定（composition 阶段）

**方案：** 用 `snapshotFlow` 监听 position 变化，非滚动时通过反射拉回到锚点。

**结果：** **振荡** — off 在 0 和锚点之间快速跳变。

**根因：** composition 阶段设置的 pending position 被**下一帧** LazyColumn 的 `applyMeasureResult` 覆盖。反射和 LazyColumn 在打架。

**日志证据：**
```
off=694 → PIN: 拉回 694
off=0   → PIN: 拉回 694
off=694 → PIN: 拉回 694
off=0   → PIN: 拉回 694
（循环...）
```

**关键发现：** 反射必须在 **layout 修饰器内部（measure 阶段）** 调用，才能在当前帧的 `applyMeasureResult` 中生效。在 composition 阶段调用会滞后一帧，被覆盖。

---

### 实验 7：PIN 模式（measure 阶段，所有 item）

**方案：** 给所有消息 item 加 layout 修饰器，pin 模式检查 offset 是否被 LazyColumn 重算。

**结果：**
- PIN 在用户主动滚动时也执行 → 干扰用户滚动（"拉不出最后一条消息"）
- 加 `!isScrollInProgress` 守卫后 → PIN 完全不执行（LazyColumn 重算时 `isScrollInProgress` 似乎为 true）
- 移除守卫后 → **振荡**（off 在锚点和 LazyColumn 调整值之间跳变）

**根因：** PIN 是**固定型**补偿（固定 offset），和内容增长天然矛盾。LazyColumn 每帧根据内容增长重算 offset，PIN 每帧拉回锚点，两者打架。

**日志证据：**
```
PIN: 0/1815 -> 0/1333    ← PIN 拉回 1333
PIN: 0/1815 -> 0/1333    ← LazyColumn 又调到 1815
PIN: 0/1815 -> 0/1333    ← 又拉回
（循环数百条...）
```

**结论：** 补偿必须是**跟随型**（`offset + delta`），不能是**固定型**（pin）。跟随型和 LazyColumn 的内容增长方向一致，不打架。

---

### 实验 8：生产者-消费者 delta 共享

**方案：**
- 流式消息（生产者）：计算 delta，存入 `sharedDelta`
- 其他 assistant 消息（消费者）：流式消息不可见时，读取 `sharedDelta`，代为执行 `offset + sharedDelta`
- `SideEffect` 重置帧级标志

**Bug 1：** `SideEffect` 只在 recomposition 时执行，relayout 时不执行 → 帧级标志永远 true → 消费者被永久阻塞。

**修复：** 改用 `listState.layoutInfo.visibleItemsInfo` 检查流式消息可见性。

**Bug 2：** `remember(streamingMsgId)` 导致 `sharedDelta` 在多消息回复中频繁重置为 0。

**修复：** `remember` 不带 key + `LaunchedEffect(streamingMsgId)` 只重置 `lastHeight`。

**Bug 3：** `sharedDelta` 在 delta=0 时被覆盖为 0（layout 修饰器每帧执行两次，第二次 delta=0）。

**修复：** `if (delta > 0) sharedDelta = delta`。

**最终结果：** 消费者执行了（PROXY 日志出现），但 **振荡**：

```
PROXY: off=224+16    ← sharedDelta=16（固定不变）
PROXY: off=240+16    ← off 在 224/240 间振荡
PROXY: off=224+16
（循环数百条...）
```

**根因：** `sharedDelta` 是流式消息最后可见时的 delta（16），但实际内容增长量远大于 16（58、69、116 等）。固定的 sharedDelta 和实际 delta 不匹配 → 补偿量错误 → 振荡。

**结论：** delta 必须来自**实时的 measure**（高度增长量），不能来自缓存值。流式消息不可见时无法获取实时 delta。

---

## 核心结论

### 什么有效
- **`offset + delta`（跟随型补偿）** 在 layout 修饰器内（measure 阶段）调用
- 仅在流式消息**可见时**有效
- delta 必须是实时计算的 `realHeight - lastHeight`

### 什么无效
| 方案 | 失败原因 |
|------|---------|
| `offset - delta` | 方向错误 |
| `offset`（不加 delta） | 补偿不足 |
| 无反射 | reverseLayout 自然锚定不可靠 |
| `isAtBottom` 条件 | derivedStateOf 帧延迟 |
| 全局 snapshotFlow（composition 阶段） | pending position 被下一帧 applyMeasureResult 覆盖 → 振荡 |
| PIN 模式（固定 offset） | 固定型与内容增长矛盾 → 振荡 |
| sharedDelta（缓存 delta） | 过时值与实际 delta 不匹配 → 振荡 |

### 根本矛盾
1. **delta 来源限制**：delta = `realHeight - lastHeight`，只在流式消息的 layout 修饰器中可知。流式消息不可见时 layout 修饰器不执行 → delta 不可知。
2. **measure 阶段限制**：反射必须在 layout 修饰器内（measure 阶段）调用才能在当前帧生效。在 composition 阶段调用会滞后一帧被覆盖。
3. **跟随型 vs 固定型**：补偿必须是跟随型（`offset + delta`），不能是固定型（pin）。跟随型和 LazyColumn 内容增长方向一致，不打架。但跟随型需要实时 delta。

### `isScrollInProgress` 不可靠
- 无法区分"用户主动滚动"和"LazyColumn 内容增长触发的内部调整"
- 两者都可能设置 `isScrollInProgress = true`
- 在 measure 阶段读取时值不确定

### `derivedStateOf` 帧延迟
- `isAtBottom` 的 `derivedStateOf` 在 `firstVisibleItemScrollOffset` 变化后有一帧延迟
- 在快速变化期间返回过时的值（off > 100 时仍返回 true）
- 不能用作补偿条件的可靠来源

---

## 时序分析（deep-explore 子代理调研结论）

### LazyListState 的 measure 流程
1. `requestPositionAndForgetLastKnownKey(index, scrollOffset)` — 直接写入 `scrollPosition` 字段（非 observable）
2. `measurementScopeInvalidator.value = Unit` — 触发 invalidation（**schedule 下一帧**，非同步）
3. 下一帧 measure 时读取 `scrollPosition` → 使用 pending position
4. measure 完成后 `applyMeasureResult` → `scrollPosition.updateFromMeasureResult(result)` → 可能重算 offset

### layout 修饰器中的反射时序
- layout 修饰器在 **measure 阶段**执行
- 此时 `firstVisibleItemScrollOffset` 是**上一帧**的值（当前帧 layout 未完成）
- 反射设置的 pending position 在**当前帧**的 `applyMeasureResult` 中使用 → 有效
- 这是 delta 模式有效的根本原因

### composition 阶段的反射时序
- snapshotFlow/LaunchedEffect 在 **composition 阶段**执行
- 反射设置的 pending position 在**下一帧**的 measure 中使用
- 但下一帧的 `applyMeasureResult` 可能又重算 offset → 覆盖 pending position → 振荡
- 这是全局 snapshotFlow 方案失败的根本原因

---

## 当前状态

代码已还原到 master 原始状态（delta-only）：
- 流式消息可见时：delta 补偿有效 ✅
- 流式消息不可见时：无补偿（已知局限）❌

---

## 后续方向建议

### 方向 A：从 ViewModel 层估算 delta
- ChatViewModel 管理 SSE 数据，每次 token 到达时消息文本变长
- 追踪流式消息的**内容长度变化**（字符数 delta）
- 用简单的线性近似估算高度 delta：`heightDelta ≈ charDelta * lineHeight / charsPerLine`
- 将估算的 heightDelta 通过 CompensateState 传递给消费者
- **优点**：不依赖 item 可见性
- **缺点**：估算不精确（Markdown/代码块/表格的高度非线性）

### 方向 B：LazyColumn prefetch 机制
- 研究 Compose 的 `beyondBoundsItemCount` 或 prefetch
- 如果能让流式消息在不可见时也被 measure，delta 就能实时获取
- **优点**：精确
- **缺点**：可能需要修改 LazyColumn 配置，性能影响未知

### 方向 C：Sentinel item
- 在 item 0（流式消息）之前插入一个不可见的 sentinel item
- sentinel 高度固定（不随内容变化），成为 firstVisibleItem 的稳定锚点
- LazyColumn 对 sentinel 的锚定可靠（因为高度不变）
- **优点**：从根本上解决锚定不稳定问题
- **缺点**：需要修改 LazyColumn item 结构

### 方向 D：替换 reverseLayout
- 用 `Arrangement.Bottom` + 程序性滚动替代 reverseLayout
- 避免 reverseLayout 的特殊 offset 语义
- **优点**：回到标准 LazyColumn 行为
- **缺点**：改动大，可能引入新问题

### 推荐优先级
1. **方向 C**（Sentinel item）— 从根本上解决锚定问题，改动可控
2. **方向 A**（ViewModel 估算）— 如果 C 不可行，估算比不补偿好
3. **方向 B**（prefetch）— 需要深入研究 Compose 内部

---

## 附录：CompensateState 演进历史

```
v1 (master 原始): lastHeight + shouldCompensate
v2: + sharedDelta + compensatedThisFrame (SideEffect)
v3: - compensatedThisFrame + streamingVisible 检查
v4: remember 不带 key + LaunchedEffect 重置 lastHeight
v5: if (delta > 0) sharedDelta = delta
→ 最终还原到 v1
```

## 附录：反射方法 API

```kotlin
LazyListReflection.requestScrollToItemNoCancel(
    state: LazyListState,
    index: Int,           // firstVisibleItemIndex
    scrollOffset: Int     // firstVisibleItemScrollOffset ± delta
)
// 内部实现：
// 1. scrollPosition.requestPositionAndForgetLastKnownKey(index, scrollOffset)
// 2. measurementScopeInvalidator.value = Unit
```

---

## 实验 9：统一 Banners Item（2026-06-24）

### 新假设：漂移根因是 banners 的 item 插入/移除

重新审视用户反馈：
- 纯文字 SSE + 上滑 → **不往下拽** ✅
- 复杂内容 SSE（工具卡片/代码块/表格）+ 上滑 → **被往下拽** ❌

**关键洞察**：之前的 8 个实验都聚焦于"流式消息不可见时的 delta 补偿"。但如果漂移的真正原因是 **条件性 banners 的频繁插入/移除导致 LazyColumn item index 跳变**？

原始代码有 **7 个独立的条件性 LazyColumn items**：
1. `revert_banner` — 无 delta 补偿
2. `compaction_banner` — 无 delta 补偿
3. `retry_banner` — 无 delta 补偿
4. `tool_progress` — 有 delta 补偿（仅 delta > 0）
5. `step_progress` — 无 delta 补偿
6. `question_*` — 无 delta 补偿
7. `perm_*` — 无 delta 补偿

在复杂内容回复中：
- 工具调用开始 → `tool_progress` item 插入
- 步骤变化 → `step_progress` item 插入/移除
- 工具调用结束 → `tool_progress` item 移除
- 这些频繁的 item 插入/移除导致 LazyColumn item 数量变化 → **item index 跳变** → offset 漂移

只有 `tool_progress` 有 delta 补偿，其他 6 个 banners 没有。而且 delta 只在 `> 0` 时补偿（banner 消失时 delta < 0 不补偿）。

### 修复方案

将所有 7 个条件性 banners **合并为一个统一的 `item(key = "streaming_banners")`**：

```kotlin
item(key = "streaming_banners") {
    Column(modifier = Modifier.fillMaxWidth().layout { ... }) {
        // 所有条件性 banners 作为 Column 的子 composable
        if (revert != null) RevertBanner(...)
        if (compaction != null) CompactionBanner(...)
        if (retry != null) RetryBanner(...)
        activeTools.forEach { ToolProgressCard(...) }
        if (step != null) StepProgressIndicator(...)
        question?.let { QuestionCard(...) }
        permission?.let { PermissionCard(...) }
    }
}
```

**优点**：
1. **Item 数量恒定** — banners 的显示/隐藏不再改变 LazyColumn 的 item 数量，避免 index 跳变
2. **统一 delta 补偿** — 一个 layout 修饰器跟踪 Column 总高度变化
3. **双向补偿** — `delta != 0` 时都补偿（包括 banner 消失时的高度减小）
4. **避免双重补偿** — 只在 `!streamVisible` 时执行（流式消息可见时由其自己的 layout 修饰器处理）

### 诊断日志验证

添加了 `ScrollFix` 标签的诊断日志，确认：
- ✅ `shouldCompensate` 状态切换正常（用户滚动时 true，回到底部时 false）
- ✅ Banners layout 修饰器正确执行（`BANNER` 日志出现）
- ✅ `streamVisible` 检查正常工作

### 模拟器测试限制

通过 adb `input swipe` 无法触发 LazyColumn 滚动：
- `off=0` 持续不变（即使用了 5 次 swipe + 3 秒慢速 swipe）
- `isScrollInProgress=true` 被触发（`shouldCompensate=true`），但滚动量为 0
- **原因**：`PullToRefreshBox` 的 `nestedScrollConnection` 可能消费了 adb 模拟的手势事件
- `KEYCODE_DPAD_UP` 完全不触发 `isScrollInProgress`

**结论**：需要在真实设备上手动测试，或使用 Maestro UI 自动化（使用真实手势而非 adb input）。
```
