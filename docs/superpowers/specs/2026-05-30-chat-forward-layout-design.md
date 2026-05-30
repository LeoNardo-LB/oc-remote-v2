# 聊天列表正序布局改造设计

## 背景

当前聊天列表使用 `LazyColumn(reverseLayout = true)`，数据源 newest-first（最新消息在 index 0）。

**核心问题**：`reverseLayout=true` 下，index 0（最新回复）在视觉底部。流式输出时 item 高度增加，向上扩展，推动视窗内所有上方 items 向上移动。只要最新回复有像素在视窗中，用户就会感到"跟着走"，无法固定视窗。

**根因**：`reverseLayout` 是为传统 IM（消息完整到达）设计的。所有主流 AI 聊天应用（ChatGPT、Claude、Gemini、Cursor）均使用正序布局，因为正序布局下最新消息在列表尾部，item 增长向下扩展，不影响上方 items。

## 目标

1. 消除流式输出时的非预期视窗跟随
2. 实现"严格底部跟随"——只有视窗在绝对底部时才跟随流式输出
3. FAB 显隐与流式跟随共用同一个"是否在底部"判定
4. 将 load_older 按钮替换为 PullToRefresh 下拉刷新
5. 保持所有现有功能不变（子会话、滚动恢复、消息合并等）

## 设计

### 改动总览

| # | 模块 | 改动点 | 文件 | 类型 |
|---|---|---|---|---|
| 1 | 数据源排序 | 4处 sortedByDescending→sortBy | MessageEventHandler, ChatViewModel | 机械替换 |
| 2 | 数据源排序 | 2处 firstOrNull→lastOrNull | ChatViewModel | 机械替换 |
| 3 | 渲染方向 | 3处删除 reversed() | MessageCard, ChatScreen | 删除代码 |
| 4 | 过滤方向 | 3处 index 方向修正 | ChatScreen | 逻辑调整 |
| 5 | 布局 | 2处 reverseLayout=false | ChatScreen | 改参数 |
| 6 | 布局 | 2处 item 声明顺序反转 | ChatScreen | 代码重组 |
| 7 | 布局 | PullToRefresh 替代 load_older | ChatScreen | 新增组件 |
| 8 | 滚动 | isAtBottom 替代 autoScrollEnabled | ChatScreen | 状态重构 |
| 9 | 滚动 | 4处 scrollToItem(0)→scrollToItem(last) | ChatScreen | 机械替换 |
| 10 | 滚动 | 滚动保存改用 message ID | ChatViewModel, ChatScreen | 逻辑重写 |

**实施顺序**（标注依赖关系）：

```
① 数据源排序（#1, #2）← 必须最先完成，后续所有改动依赖数据方向
  ↓
② 渲染方向 + 过滤方向（#3, #4）← 依赖①的排序结果
  ↓
③ 布局（#5, #6, #7）← 依赖②的渲染方向确定
  ↓
④ 滚动逻辑（#8, #9, #10）← 依赖③的布局配置
```

①② 可以在同一次编译中完成。③④ 也可以一起完成。但 ①② 必须在 ③④ 之前。

### 1. 数据流方向：newest-first → oldest-first

消息数据源从降序改为升序，最老消息在 index 0，最新消息在 index last。

**改动清单**：

| 文件 | 行号 | 改动 |
|---|---|---|
| `MessageEventHandler.kt` | 48 | `sortByDescending` → `sortBy` |
| `MessageEventHandler.kt` | 98 | `sortedByDescending` → `sortedBy` |
| `MessageEventHandler.kt` | 104 | `sortedByDescending` → `sortedBy` |
| `ChatViewModel.kt` | 335 | `sortedByDescending` → `sortedBy` |
| `ChatViewModel.kt` | 356 | `firstOrNull { model != null }` → `lastOrNull` |
| `ChatViewModel.kt` | 374 | `firstOrNull { agent != null }` → `lastOrNull` |

### 2. 渲染方向：去除 reversed() 修正

数据源改为 oldest-first 后，turn group 内的消息顺序自动正确，不再需要 `reversed()`。

| 文件 | 行号 | 改动 |
|---|---|---|
| `MessageCard.kt` | 332 | 删除 `turnMessages?.reversed()` |
| `ChatScreen.kt` | 1864 | 删除 `.reversed()` |
| `ChatScreen.kt` | 2119 | 删除 `.reversed()` |

### 3. displayItems 过滤方向修正

oldest-first 时，turn group 的"第一条 assistant"改为向后查找。

**推理**：当前 newest-first 排列中，连续 assistant messages 按 created 降序排列。index-1 对应"比当前消息更新"的消息。代码检查 `rawMessages.getOrNull(index - 1)?.isAssistant` 判断当前 assistant 是否是一个新 turn 的开始——如果前一条（更新的）不是 assistant，说明当前是 turn 的第一条（最新的那条）。

改为 oldest-first 后，index+1 对应"比当前消息更新"的消息。所以改为检查 `rawMessages.getOrNull(index + 1)?.isAssistant`，逻辑等价。同理 `rawIndex == 0`（newest-first 中表示最新消息）改为 `rawIndex == rawMessages.lastIndex`（oldest-first 中表示最新消息）。

| 文件 | 行号 | 改动 |
|---|---|---|
| `ChatScreen.kt` | 1770 | `getOrNull(index - 1)` → `getOrNull(index + 1)` |
| `ChatScreen.kt` | 1853 | `rawIndex == 0` → `rawIndex == rawMessages.lastIndex`，`rawIndex - 1` → `rawIndex + 1` |
| `ChatScreen.kt` | 2108 | 同上（子会话） |

### 4. LazyColumn 布局

#### 4.1 reverseLayout 关闭

| 文件 | 行号 | 改动 |
|---|---|---|
| `ChatScreen.kt` | 1792 | `reverseLayout = true` → `false`（主会话）|
| `ChatScreen.kt` | 2048 | `reverseLayout = true` → `false`（子会话）|

#### 4.2 Item 声明顺序反转

正序布局下，声明顺序即视觉顺序（从上到下）。

**之前**（reverseLayout=true，声明从底到顶）：
```
① pendingQuestions
② pendingPermissions
③ revertBanner
④ displayItems (newest-first)
⑤ load_older
```

**之后**（正序，声明从顶到底）：
```
① PullToRefresh indicator
② displayItems (oldest-first)
③ revertBanner
④ pendingPermissions
⑤ pendingQuestions
```

load_older 按钮替换为 PullToRefresh，不再作为独立 item。用 `Modifier.pullToRefresh` 包裹整个 LazyColumn，在顶部触发时调用 `viewModel.loadOlderMessages()`。

主会话和子会话两处都需要同步修改。

#### 4.3 PullToRefresh 实现

添加 material3 的 `Modifier.pullToRefresh` 依赖（项目使用 material3 1.3.x+，应已内置）。

实现方式：
- LazyColumn 外层用 `Box` + `pullToRefreshState` 包裹
- 顶部显示刷新指示器
- 触发时调用 `viewModel.loadOlderMessages()`
- 状态绑定：`isLoadingOlder` 控制刷新动画，`hasOlderMessages` 控制是否启用下拉

### 5. 滚动逻辑

#### 5.1 "是否在底部"判定（核心）

替换当前的 `firstVisibleItemIndex == 0 && offset <= 50`，改为检测最后一个消息 item 是否可见：

```kotlin
val isAtBottom by remember {
    derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
        lastVisible != null && lastVisible.index >= layoutInfo.totalItemsCount - 1
    }
}
```

**语义定义**：最后一个 item（pendingQuestions/pendingPermissions 或最新消息）完全可见或部分可见时视为"在底部"。由于 pending 在列表最底部（声明顺序最后），当 pending 可见时 isAtBottom = true，此时流式跟随和 FAB 隐藏都是正确行为。

**注意**：pendingQuestions/pendingPermissions 是声明在 displayItems 之后的 item。isAtBottom 检测的是 LazyColumn 的最后一个 item，不是最后一个消息 item。这是有意为之——pending 出现时用户仍然在"底部"。

**空列表处理**：当 totalItemsCount == 0 时（无消息无 pending），`lastOrNull()` 为 null → isAtBottom = false → FAB 不显示，scrollToItem 不会执行（5.2 有 messageCount > 0 守卫）。

#### 5.2 scrollToItem 改为滚到最后

| 文件 | 行号 | 场景 |
|---|---|---|
| `ChatScreen.kt` | 843 | 新消息自动滚动 |
| `ChatScreen.kt` | 1226 | 发送消息后 |
| `ChatScreen.kt` | 2018 | 主会话 FAB 点击 |
| `ChatScreen.kt` | 2264 | 子会话 FAB 点击 |

所有 `scrollToItem(0)` 改为 `scrollToItem(listState.layoutInfo.totalItemsCount - 1)`。

#### 5.3 状态管理方案：删除 autoScrollEnabled，统一用 isAtBottom

**删除的内容**：
- `var autoScrollEnabled by remember { mutableStateOf(true) }` 变量声明
- `snapshotFlow { firstVisibleItemIndex to offset }.collect { ... }` 整个 LaunchedEffect

**替代方案**：所有原来读写 `autoScrollEnabled` 的地方，改为使用 5.1 的 `isAtBottom`（derivedStateOf，只读）。

**逐点映射**：

| 原使用方式 | 新方案 |
|---|---|
| `if (!autoScrollEnabled) { FAB }` | `if (!isAtBottom) { FAB }` |
| `if (autoScrollEnabled) scrollToItem(0)` | `if (isAtBottom) scrollToItem(last)` |
| `autoScrollEnabled = true`（发送后/FAB点击后） | **删除**——derivedStateOf 自动响应滚动位置变化，用户点击 FAB 后 `scrollToItem(last)` 执行，LazyColumn 滚到底部，`isAtBottom` 自动变为 true |
| `autoScrollEnabled = false`（恢复滚动后） | **删除**——恢复到非底部位置时 `isAtBottom` 自动为 false |
| `autoScrollEnabled = false`（子会话恢复后） | **删除**——`scrollToItem` 到非底部位置后 `isAtBottom` 自动为 false |

**交互闭环**（由 derivedStateOf 自动保证）：
1. 用户在底部 → `isAtBottom = true` → 流式跟随 + FAB 隐藏
2. 用户向上滚 → `isAtBottom = false` → 停止跟随 + FAB 显示
3. 用户手动滚回底部 → `isAtBottom = true` → 恢复跟随 + FAB 隐藏
4. 用户点击 FAB → `scrollToItem(last)` → LazyColumn 滚到底部 → `isAtBottom = true`

#### 5.4 滚动位置保存/恢复改用 message ID

排序方向改变后，index 不稳定（数据量变化时 index 会偏移）。改为保存 message ID：

**ViewModel 签名变更**：
```kotlin
// 之前
fun saveScrollPosition(index: Int, offset: Int)

// 之后
fun saveScrollPosition(messageId: String?, offset: Int)
```

**保存时**（在 `navigateToChildSessionWithSave` 中）：
- 当前 LazyColumn 的 key 设置方式：
  - 消息 item：`key = { _, item -> item.second.message.id }`（String 类型）
  - pending questions：`key = { "question_${it.id}" }`（String，以 "question_" 前缀）
  - pending permissions：`key = { "perm_${it.id}" }`（String，以 "perm_" 前缀）
  - revert banner：`key = "revert_banner"`
  - load_older：`key = "load_older"`（改为 PullToRefresh 后不再存在）
- 遍历 `listState.layoutInfo.visibleItemsInfo`，取第一个 key 不是 "revert_banner" / "load_older" 的 item
- 如果 key 以 "question_" 或 "perm_" 开头，继续找下一个
- 找到的 item 的 key 即为 message ID（String）
- 保存 messageId + offset

**恢复时**（在 `LaunchedEffect(scrollRestoreVersion)` 中）：
- 遍历 LazyColumn 当前所有 items（通过 layoutInfo 或 displayItems），找到对应 messageId 的 index
- `scrollToItem(foundIndex, savedOffset)`
- 恢复后用 5.1 的 `isAtBottom` 重新判定

**边界情况**：
- 如果 messageId 在恢复时已不存在（被删除），降级为 `scrollToItem(last)`（滚到底部）
- 如果 messageId 存在但位置变化（加载了更多历史消息），index 会偏移，但消息 ID 对应的 item 仍在，滚动位置接近原位置

#### 5.5 初始加载滚动定位

正序布局下首次进入会话时，需要滚到最后一个 item（最新消息）。当前 `LaunchedEffect(messageCount)` 中 `scrollToItem(0)` 改为 `scrollToItem(last)` 即可。由于 `isAtBottom`（derivedStateOf）初始为 true（列表空时 totalItemsCount=0，lastVisible 条件满足），首次加载后自动跟随。

#### 5.6 子会话恢复后的 isAtBottom 判定

恢复滚动位置后，用 5.1 的 `isAtBottom` 逻辑重新判定，替代当前的 `firstVisibleItemIndex == 0 && offset <= 50`。

### 6. 不需要改动的部分

| 组件 | 原因 |
|---|---|
| `computeTurnGroups` | 只按 index 顺序遍历找连续 assistant，不依赖排序方向 |
| `contentPadding` | 正序后语义恢复自然，top=top，bottom=bottom |
| 键盘弹出滚动检测 | 只检测 index 变化来收起键盘，不依赖具体方向 |
| `revert` 过滤逻辑 | ID 比较不依赖排序 |
| SSE 消息解析 | 只负责解析和分发事件 |

## 涉及文件汇总

| 文件 | 改动项数 | 改动类型 |
|---|---|---|
| `ChatScreen.kt` | ~12 | 布局重构、滚动逻辑、FAB、PullToRefresh |
| `ChatViewModel.kt` | ~5 | 排序、firstOrNull→lastOrNull、滚动保存改用 ID |
| `MessageEventHandler.kt` | 3 | 排序方向 |
| `MessageCard.kt` | 1 | 删除 reversed() |
| `TurnGroupCalculator.kt` | 0 | 无需改动 |
| `app/build.gradle.kts` | 0-1 | 可能需要确认 pullToRefresh 依赖 |

## 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| PullToRefresh 依赖版本不匹配 | 先确认 material3 版本是否内置 pullToRefresh；如不匹配则降级为顶部 item 按钮方案 |
| 滚动恢复 index 映射不准 | 改用 message ID 方案，彻底避免 index 映射问题 |
| 子会话和主会话同步遗漏 | 逐项对照修改，确保两处完全镜像 |
| isAtBottom 阈值过严导致体验不跟手 | 正序布局下最后一个 item 完全可见 = 在底部，语义明确，无需额外阈值 |
