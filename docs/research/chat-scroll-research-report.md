# 主流聊天应用消息渲染方案调研报告

> **调研日期**: 2026-05-27  
> **方法**: 开源代码分析 + 产品行为观察 + 技术文档收集  
> **注**: 标注 `[待验证]` 的内容为基于产品行为的合理推断，未经源码确认

---

## 1. 各产品行为对比矩阵

### 1.1 流式输出 & 自动滚动

| 维度 | QQ | 微信 | Telegram | ChatGPT | 豆包 | DeepSeek | 通义千问 | **oc-remote** |
|------|----|------|----------|---------|------|----------|----------|---------------|
| **流式跟随** | ✅ | ✅ | ✅ (IM) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **滚动方式** | 段落跳动 | 段落跳动 | 段落跳动 | 逐字平滑 | 逐字平滑 | 逐字平滑 | 逐字平滑 | 段落跳动 (按 part 触发) |
| **打字机效果** | ❌ | ❌ | ❌ | ✅ 逐字渲染 | ✅ 逐字渲染 | ✅ 逐字渲染 | ✅ 逐字渲染 | ❌ 逐 token 但按 part 批量滚动 |
| **检测方式** | [待验证] | [待验证] | OnScrollListener | MutationObserver+ResizeObserver | [待验证] | [待验证] | [待验证] | LaunchedEffect(messageCount, partCount) |

### 1.2 用户上滑行为

| 维度 | QQ | 微信 | Telegram | ChatGPT | 豆包 | DeepSeek | 通义千问 | **oc-remote** |
|------|----|------|----------|---------|------|----------|----------|---------------|
| **流式期间上滑** | 不强制拉回 | 不强制拉回 | 不强制拉回 | 不强制拉回 | 不强制拉回 | 不强制拉回 | 不强制拉回 | ⚠️ pendingCount>0 会绕过锁定 |
| **位置稳定性** | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 (锁定生效时) |
| **检测粒度** | [待验证] | [待验证] | scroll state | scroll event + threshold | [待验证] | [待验证] | [待验证] | firstVisibleItemIndex==0 |

### 1.3 "回到底部"交互

| 维度 | QQ | 微信 | Telegram | ChatGPT | 豆包 | DeepSeek | 通义千问 | **oc-remote** |
|------|----|------|----------|---------|------|----------|----------|---------------|
| **FAB 按钮** | ✅ 向下箭头 | ❌ 点击输入框回底 | ✅ 箭头+计数 | ✅ 箭头 | ✅ 箭头 | ✅ 箭头 | ✅ 箭头 | ✅ KeyboardArrowDown |
| **新消息计数** | ❌ | ❌ | ✅ "↓N" | ❌ | ❌ | ❌ | ❌ | ❌ |
| **自动恢复跟随** | ✅ 滚到底部 | ✅ 点击输入框 | ✅ 滚到底部 | ✅ 滚到底部 | ✅ 滚到底部 | ✅ 滚到底部 | ✅ 滚到底部 | ✅ 滚到底部 |

### 1.4 工具调用 / 折叠展开

| 维度 | ChatGPT | 豆包 | DeepSeek | 通义千问 | **oc-remote** |
|------|---------|------|----------|----------|---------------|
| **工具调用展示** | 折叠卡片+展开 | 折叠卡片 | 折叠卡片 | 折叠卡片 | ToolCallCard 可折叠 |
| **展开效果** | 向下推 | 向下推 | 向下推 | 向下推 | 向下推 |
| **展开时滚动** | 不调整 | 不调整 | 不调整 | 不调整 | 不调整 (可能遮挡) |
| **思考过程** | 折叠 "Reasoning" | 折叠块 | 折叠块 | 折叠块 | 无 |

### 1.5 富文本渲染

| 维度 | ChatGPT | 豆包 | DeepSeek | 通义千问 | **oc-remote** |
|------|---------|------|----------|----------|---------------|
| **窄屏表格** | 水平滚动 | 水平滚动 | 水平滚动 | 水平滚动 | 水平滚动 |
| **长代码块** | 水平滚动+复制按钮 | 水平滚动+复制 | 水平滚动+复制 | 水平滚动+复制 | 水平滚动+复制 |
| **图片预览** | 点击放大 | 点击放大 | 点击放大 | 点击放大 | 长按保存 |

---

## 2. 传统 IM 应用详细分析

### 2.1 QQ

**数据来源**: 产品行为观察 [待验证]

#### 流式输出行为
QQ 作为传统 IM，不涉及 AI 流式输出。新消息到达时：
- **自动跟随**: 用户在底部时，新消息自动将视窗向下推
- **滚动方式**: 段落级跳动（每条新消息触发一次 scrollTo）
- **实现推测**: 基于 ListView/RecyclerView 的 `stackFromBottom=true`，新 item 插入后自动跟随

#### 用户上滑行为
- **不强制拉回**: 用户上滑查看历史时，新消息到达不会打断阅读
- **FAB 按钮**: 右下角出现向下箭头按钮，点击回到底部
- **无计数器**: 不显示未读消息数

#### 技术特点
- 使用自定义 ListView（Android 端基于 QMUINativeRecyclerView）
- `stackFromBottom` + `transcriptMode=normal` 实现底部锚定
- 新消息到达时只在底部才触发跟随

### 2.2 微信

**数据来源**: 产品行为观察 [待验证]

#### 流式输出行为
微信与 QQ 类似：
- **自动跟随**: 底部时新消息自动推下
- **无 FAB 按钮**: 没有专门的"回到底部"浮动按钮
- **点击输入框回底**: 点击底部输入框区域自动滚回最新消息

#### 技术特点
- Android 端使用自研 ListView（非标准 RecyclerView）
- 通过 `ListView.transcriptMode` 控制跟随行为
- 特殊的"点击输入框回底"交互——充分利用了 IM 场景中用户意图"输入=回到最新"

### 2.3 Telegram

**数据来源**: GitHub 源码分析 (DrKLO/Telegram)

#### 核心架构
Telegram Android 使用自定义 `RecyclerListView`（基于 RecyclerView），配合多个辅助类：

```
ChatActivity.java (46000+ lines)
├── ChatActivityBlurredRoundPageDownButton  — "回到底部"FAB + 未读计数
├── ChatActivityBottomViewsVisibilityController — 底部区域可见性控制
├── ChatScrollHelper                         — 滚动辅助逻辑
├── scrollingChatListView (boolean)           — 标记是否正在程序化滚动
└── OnScrollListener                         — 滚动状态监听
    ├── SCROLL_STATE_DRAGGING   → 标记用户正在拖拽
    ├── SCROLL_STATE_SETTLING   → 惯性滑动
    └── SCROLL_STATE_IDLE       → 停止 → 检查是否在底部
```

#### 流式输出行为
- **自动跟随**: 使用 `transcriptMode` + 自定义检测逻辑
- **滚动方式**: 消息级别跳动
- **向下箭头 FAB**: `ChatActivityBlurredRoundPageDownButton` 带模糊背景效果
- **未读计数器**: `CounterView` 显示未读消息数量（如 "↓3"）
- **reverseCounter**: 支持计数器位置翻转

#### 关键代码模式

```java
// 滚动状态追踪
private boolean scrollingChatListView;  // 程序化滚动标志

// OnScrollListener
@Override
public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
    if (newState == SCROLL_STATE_DRAGGING) {
        // 用户开始拖拽 → 标记为手动滚动
        scrollingFloatingDate = false;
    }
    if (newState == SCROLL_STATE_IDLE) {
        // 停止滚动 → 检查底部位置
        // 显示/隐藏 PageDownButton
    }
}
```

#### 特殊功能
- **模糊背景 FAB**: `BlurredBackgroundDrawableViewFactory` 为按钮提供实时模糊效果
- **浮游日期**: 滚动时顶部显示浮动时间戳 (`scrollingFloatingDate`)
- **强制滚动**: `nextScrollForce` 标志用于跳转到特定消息时绕过常规滚动控制

---

## 3. AI Agent 产品详细分析

### 3.1 ChatGPT (Web)

**数据来源**: 开源实现分析 (NextChat, Vercel AI Chatbot)

#### 核心滚动架构

ChatGPT Web 版使用 React + DOM 容器滚动方案（非虚拟列表）：

```
<div ref={containerRef} onScroll={handleScroll}>
  {messages.map(msg => <Message key={msg.id} />)}
  <div ref={endRef} />  <!-- 底部锚点 -->
</div>
```

#### Vercel AI Chatbot 的 `useScrollToBottom` Hook（ChatGPT 官方推荐的实现模式）

```typescript
// 核心机制:
// 1. MutationObserver 监听 DOM 变化（流式输出导致内容增长）
// 2. ResizeObserver 监听容器/子元素尺寸变化
// 3. isAtBottom 阈值 = scrollHeight - scrollTop - clientHeight < 100px
// 4. isUserScrolling 通过 150ms 超时防抖检测

export function useScrollToBottom() {
  const [isAtBottom, setIsAtBottom] = useState(true);
  const isUserScrollingRef = useRef(false);

  // MutationObserver: 监听所有内容变化
  useEffect(() => {
    const scrollIfNeeded = () => {
      if (isAtBottomRef.current && !isUserScrollingRef.current) {
        requestAnimationFrame(() => {
          container.scrollTo({
            top: container.scrollHeight,
            behavior: "instant",  // 自动滚动用 instant
          });
        });
      }
    };

    const mutationObserver = new MutationObserver(scrollIfNeeded);
    mutationObserver.observe(container, {
      childList: true, subtree: true, characterData: true
    });

    const resizeObserver = new ResizeObserver(scrollIfNeeded);
    resizeObserver.observe(container);
  }, []);
}
```

#### 行为特点

| 特性 | 实现 |
|------|------|
| **流式跟随** | MutationObserver 触发，`behavior: "instant"` 逐帧跟随 |
| **打字机效果** | 每个 token 更新 DOM → MutationObserver → requestAnimationFrame → scrollTo |
| **上滑锁定** | isUserScrollingRef + 150ms 防抖 |
| **回到底部** | FAB 按钮 + `scrollTo({ behavior: "smooth" })` |
| **恢复跟随** | `onViewportEnter` / `onViewportLeave` + `checkIfAtBottom()` |

### 3.2 豆包 (Doubao)

**数据来源**: 产品行为观察 [待验证]

#### 流式输出行为
- **逐字平滑滚动**: 流式输出时内容逐字出现，视窗平滑跟随
- **打字机效果**: 明显的逐 token 渲染效果
- **上滑不锁定**: 用户上滑后，流式输出内容在下方继续但不强制拉回
- **回到底部 FAB**: 右下角箭头按钮

#### 技术推测
基于 React/Vue 的 Web 应用，大概率使用类似 MutationObserver + scrollTo 的方案。豆包的特殊之处在于其流畅的逐字渲染动画。

### 3.3 DeepSeek

**数据来源**: 产品行为观察 [待验证]

#### 流式输出行为
- **逐字跟随**: 与 ChatGPT 类似的逐字渲染
- **思考过程展示**: DeepSeek R1 的 `<think/>` 内容单独折叠展示
- **代码高亮**: 实时语法高亮，代码块支持语言切换和复制
- **上滑锁定**: 用户上滑后保持位置稳定

#### 特殊功能
- **思考过程折叠**: "已思考 X 秒" 折叠块，展开可看推理过程
- **搜索结果展示**: 联网搜索时展示搜索来源卡片
- **内容类型标识**: 代码/数学/表格有明确标识

### 3.4 通义千问 (Qwen)

**数据来源**: 产品行为观察 [待验证]

#### 流式输出行为
- **逐字跟随**: 标准的流式渲染
- **多模态支持**: 图片识别、代码执行结果展示
- **工具调用**: 折叠式工具调用卡片

#### 特殊功能
- **代码执行**: 代码执行结果直接在聊天中展示
- **长文本优化**: 长回答时有"展开全文"选项
- **Markdown 渲染**: 表格、代码块、数学公式的完整支持

---

## 4. 通用最佳实践

### 4.1 自动滚动控制模式

通过分析所有产品，**自动滚动控制**有一个通用的状态机模型：

```
┌──────────────────────────────────────────────────┐
│                   Auto-Scroll State Machine       │
│                                                    │
│  ┌─────────┐   user at bottom   ┌──────────┐     │
│  │ ENABLED │◄──────────────────│ CHECKING  │     │
│  │ (auto   │                    │ (near     │     │
│  │ follow) │   user scrolls up  │ bottom?) │     │
│  └────┬────┘◄──────────────────└────┬─────┘     │
│       │                            │              │
│       │ content arrives            │ not at bottom│
│       │ → scrollTo bottom          │              │
│       │                            ▼              │
│       │                      ┌──────────┐        │
│       │                      │ DISABLED │        │
│       │                      │ (locked) │        │
│       │                      └────┬─────┘        │
│       │                            │              │
│       │                            │ user clicks  │
│       │                            │ FAB / scrolls │
│       │                            │ to bottom    │
│       │                            │              │
│       └────────────────────────────┘              │
└──────────────────────────────────────────────────┘
```

### 4.2 "到底部"检测方法对比

| 方法 | 产品 | 精度 | 性能 | 适用场景 |
|------|------|------|------|----------|
| `scrollTop + clientHeight >= scrollHeight - threshold` | ChatGPT/Vercel | 高 (100px阈值) | 好 | Web DOM |
| `firstVisibleItemIndex == 0` (reverseLayout) | oc-remote | 中 (item级) | 好 | LazyColumn |
| `OnScrollListener.SCROLL_STATE_IDLE` + offset check | Telegram | 高 | 好 | RecyclerView |
| `MutationObserver + ResizeObserver` | ChatGPT | 高 | 中 | Web DOM 流式 |
| `snapshotFlow { isScrollInProgress }` | oc-remote | 中 | 好 | Compose |

### 4.3 流式输出滚动频率控制

| 策略 | 产品 | 特点 |
|------|------|------|
| **MutationObserver (每帧)** | ChatGPT/Vercel | 最平滑，但可能过度触发 |
| **requestAnimationFrame 节流** | Open WebUI | 每帧最多一次 scrollTo |
| **内容变化节流** | Open WebUI | `pendingRebuild` 标志，结构变化立即响应，内容变化等一帧 |
| **messageCount + partCount 触发** | oc-remote | 按消息/分块粒度，避免高频 scrollTo |
| **MutationObserver + characterData** | Vercel | 监听文本内容变化，逐 token 触发 |

### 4.4 关键设计决策

#### 决策 1: 滚动粒度 — "逐字" vs "逐段"

| 方案 | 优点 | 缺点 | 采用者 |
|------|------|------|--------|
| 逐字平滑 | 视觉体验最佳 | 高频 scrollTo 可能有性能问题 | ChatGPT, 豆包, DeepSeek, 千问 |
| 逐段跳动 | 性能好，实现简单 | 视觉不够平滑 | QQ, 微信, Telegram, oc-remote |

**建议**: 对于 AI Agent 产品，逐字平滑是标配。实现关键是使用 `requestAnimationFrame` 节流 + `behavior: "instant"` 避免动画堆叠。

#### 决策 2: 虚拟列表 vs 全量渲染

| 方案 | 优点 | 缺点 | 采用者 |
|------|------|------|--------|
| 全量 DOM 渲染 | 实现简单，MutationObserver 天然支持 | 长对话性能差 | ChatGPT Web, 豆包 |
| 虚拟列表 | 长对话性能好 | 滚动位置管理复杂 | Telegram, Lobe Chat |
| LazyColumn (Compose) | 性能+简单 | 流式滚动粒度受限 | oc-remote |

#### 决策 3: reverseLayout vs stackFromBottom

| 方案 | 优点 | 缺点 | 采用者 |
|------|------|------|--------|
| `reverseLayout=true` | 新 item 在 index 0，自动推到底部 | "到底部"检测是 index==0，直觉反 | oc-remote |
| `stackFromBottom=true` | 语义清晰 | 需要 notifyDataSetChanged | Telegram Android |
| DOM scrollTo | 灵活控制滚动位置 | 需要手动管理 | Web 产品 |

### 4.5 "回到底部"FAB 设计模式

| 特性 | 最佳实践 | oc-remote 现状 |
|------|----------|----------------|
| **出现条件** | `!isAtBottom && !autoScrollEnabled` | ✅ 已实现 |
| **图标** | 向下箭头 (ChevronDown/ArrowDown) | ✅ KeyboardArrowDown |
| **动画** | 淡入淡出 + 缩放 | ⚠️ AnimatedVisibility |
| **未读计数** | 部分产品显示 (Telegram) | ❌ 未实现 |
| **模糊背景** | Telegram: 毛玻璃效果 | ❌ 未实现 |
| **点击行为** | `scrollTo(0)` + `autoScrollEnabled = true` | ✅ 已实现 |

---

## 5. 与当前实现的对比

### 5.1 oc-remote 当前架构

```kotlin
// ChatScreen.kt 核心滚动逻辑 (简化)
var autoScrollEnabled by remember { mutableStateOf(true) }
var isAutoScrolling by remember { mutableStateOf(false) }

// 底部检测 (reverseLayout: index==0)
val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

// 用户滚动检测
LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
    if (isAutoScrolling) return@LaunchedEffect
    if (listState.isScrollInProgress) autoScrollEnabled = false
    else if (isAtBottom) autoScrollEnabled = true
}

// 自动滚动触发
val pendingCount = uiState.pendingPermissions.size + uiState.pendingQuestions.size
LaunchedEffect(messageCount, lastPartCount, pendingCount, isBusy) {
    if (messageCount > 0 && autoScrollEnabled) {
        isAutoScrolling = true
        listState.scrollToItem(0)
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        isAutoScrolling = false
    }
}
```

### 5.2 差距分析

| 维度 | 行业标准 | oc-remote 现状 | 差距 |
|------|----------|----------------|------|
| **滚动粒度** | 逐字/逐 token 平滑 | 按 messageCount/partCount 跳动 | 🔴 大 |
| **pendingCount 绕过** | 不存在 | `pendingCount > 0` 绕过 autoScrollEnabled | 🔴 Bug |
| **到底部检测精度** | 100px 阈值 (Web) / offset (Android) | `firstVisibleItemIndex == 0` | 🟡 中 |
| **流式触发方式** | MutationObserver/ResizeObserver | LaunchedEffect deps | 🟡 可接受 |
| **FAB 未读计数** | Telegram 有 | 无 | 🟡 Nice-to-have |
| **思考过程折叠** | ChatGPT/DeepSeek/千问 都有 | 无 | 🟡 无需(非推理模型) |
| **工具调用展开** | 向下推+自动调整滚动 | 向下推但不调整滚动 | 🟡 小 |
| **加载更多消息** | 触顶加载+位置保持 | 已实现 | ✅ OK |
| **用户锁定** | 上滑锁定+回底恢复 | 已实现(有 pendingCount bug) | ⚠️ 有Bug |
| **程序化滚动保护** | scrollingChatListView/isAutoScrolling | isAutoScrolling 标志 | ✅ OK |

---

## 6. 改进建议

### 6.1 优先级 P0: 修复 pendingCount 绕过 (已识别的 Bug)

**问题**: `ChatScreen.kt:1415` 行条件 `autoScrollEnabled || pendingCount > 0` 中，`pendingCount > 0` 绕过了用户滚动控制标志。

**修复**: 移除 `pendingCount` 条件，改为：
```kotlin
LaunchedEffect(messageCount, lastPartCount, isBusy) {
    if (!autoScrollEnabled) return@LaunchedEffect  // 移除 pendingCount 绕过
    if (messageCount > 0) {
        isAutoScrolling = true
        listState.scrollToItem(0)
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        isAutoScrolling = false
    }
}
```

### 6.2 优先级 P1: 改善流式输出滚动粒度

**当前**: 按 `messageCount` 和 `partCount` 触发 `scrollToItem`，导致段落级跳动。

**目标**: 逐 token / 逐字符的平滑跟随。

**方案**: 参考 Open WebUI 的 `requestAnimationFrame` 节流 + 内容长度监听：

```kotlin
// 方案 A: 监听最后一条消息的内容长度变化
val lastMessageContentLength = uiState.messages.firstOrNull()
    ?.parts?.sumOf { it.text?.length ?: 0 } ?: 0

LaunchedEffect(lastMessageContentLength) {
    if (autoScrollEnabled && messageCount > 0) {
        // 使用 animateScrollToItem 实现平滑滚动
        listState.animateScrollToItem(0)
    }
}
```

```kotlin
// 方案 B: 使用 snapshotFlow + 频率控制 (推荐)
// 监听最后一条消息的内容变化，用 frame 节流
LaunchedEffect(Unit) {
    var lastLength = 0
    snapshotFlow { 
        uiState.messages.firstOrNull()?.parts?.sumOf { it.text?.length ?: 0 } ?: 0 
    }
        .filter { it != lastLength && autoScrollEnabled }
        .collect { newLength ->
            lastLength = newLength
            // 每帧最多一次 scrollTo
            listState.scrollToItem(0)
        }
}
```

**注意**: `animateScrollToItem` 会产生平滑滚动动画，但在流式输出高频场景下可能导致动画堆叠。建议使用 `scrollToItem`（instant）配合 `requestAnimationFrame` 式的节流。

### 6.3 优先级 P1: 改善"到底部"检测精度

**当前**: `firstVisibleItemIndex == 0` 精度到 item 级别，当最后一个 item 很高（长代码块）时，用户可能实际上已经接近底部但 index 仍为 1。

**方案**: 参考行业标准，增加 offset 阈值：

```kotlin
val isAtBottom by remember {
    derivedStateOf {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf true
        val firstItem = layoutInfo.visibleItemsInfo.first()
        firstItem.index == 0 && firstItem.offset > -100  // 100px 阈值
    }
}
```

### 6.4 优先级 P2: FAB 未读消息计数

**参考**: Telegram 的 `CounterView` + `ChatActivityBlurredRoundPageDownButton`

**方案**: 在现有的 FAB 上增加 badge 计数：

```kotlin
if (!isAtBottom && !autoScrollEnabled) {
    BadgeBox(
        badgeContent = { Text("${unreadCount}") },
        content = {
            FAB(onClick = { /* scroll to bottom */ }) {
                Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom")
            }
        }
    )
}
```

### 6.5 优先级 P2: 工具调用展开时自动调整滚动

**当前**: 工具调用卡片展开时内容向下推，但滚动位置不调整，可能导致新内容被遮挡。

**方案**: 展开/折叠时检查当前视窗是否需要调整：

```kotlin
// 工具卡片展开回调
fun onToolCardExpanded(messageId: String, partIndex: Int) {
    if (autoScrollEnabled) {
        // 如果在底部，保持在底部
        scope.launch { listState.scrollToItem(0) }
    } else {
        // 如果在中间位置，保持当前消息可见
        // 需要记录展开前的 scrollOffset 并补偿
    }
}
```

### 6.6 优先级 P3: 流式输出打字机效果增强

**参考**: ChatGPT/豆包的逐字渲染体验

**方案**: 如果希望实现真正的"打字机"效果，需要在 Markdown 渲染层面支持增量渲染：

```kotlin
// 概念方案：逐 token 渲染 + 光标闪烁
@Composable
fun StreamingMarkdownContent(content: String, isStreaming: Boolean) {
    val visibleLength by animateIntAsState(
        targetValue = content.length,
        animationSpec = tween(durationMillis = 16)  // ~60fps
    )
    val displayContent = content.take(visibleLength)
    
    MarkdownContent(displayContent)
    
    if (isStreaming && visibleLength < content.length) {
        BlinkingCursor()  // 闪烁光标
    }
}
```

### 6.7 实施路线图

| 阶段 | 改进项 | 预计工作量 | 影响 |
|------|--------|-----------|------|
| **Phase 1** | P0: 修复 pendingCount 绕过 | 0.5h | 修复核心 Bug |
| **Phase 2** | P1: 内容长度监听 + 平滑滚动 | 2h | 大幅改善流式体验 |
| **Phase 2** | P1: 改善到底部检测精度 | 1h | 减少误判 |
| **Phase 3** | P2: FAB 未读计数 | 1h | 功能对齐 |
| **Phase 3** | P2: 展开折叠滚动补偿 | 2h | 改善交互体验 |
| **Phase 4** | P3: 打字机效果 | 4h+ | 视觉增强 |

---

## 附录 A: 开源项目源码参考

### A.1 Lobe Chat — `useAutoScroll` Hook

```typescript
// src/hooks/useAutoScroll.ts
export function useAutoScroll<T extends HTMLElement = HTMLDivElement>(
  options: UseAutoScrollOptions = {},
): UseAutoScrollReturn<T> {
  const { deps = [], enabled = true, threshold = 20 } = options;
  const [userHasScrolled, setUserHasScrolled] = useState(false);
  const isAutoScrollingRef = useRef(false);

  const handleScroll = useCallback(() => {
    if (isAutoScrollingRef.current) return;  // 忽略程序化滚动
    const container = ref.current;
    const distanceToBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    if (distanceToBottom > threshold) setUserHasScrolled(true);  // 用户上滑
  }, [threshold]);

  // 流式结束时保持滚动位置
  useEffect(() => {
    if (prevEnabledRef.current && !enabled) {
      isAutoScrollingRef.current = true;
      requestAnimationFrame(() => {
        container.scrollTop = currentScrollTop;  // 恢复位置
      });
    }
  }, [enabled]);

  // 内容变化时自动滚动
  useEffect(() => {
    if (!enabled || userHasScrolled) return;
    isAutoScrollingRef.current = true;
    requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight;
    });
  }, [enabled, userHasScrolled, ...deps]);
}
```

**关键设计点**:
- `threshold = 20px` 底部阈值
- `isAutoScrollingRef` 防止程序化滚动被误判为用户滚动
- `prevEnabledRef` 检测 `enabled` 从 true→false 的转换（流式结束），此时保持滚动位置
- `resetScrollLock` 供外部重置锁定状态

### A.2 Open WebUI — 流式输出节流

```javascript
// src/lib/components/chat/Messages.svelte
// 流式期间每帧最多重建一次消息列表
const handleHistoryChange = (currentId, _messages) => {
  const currentIdChanged = currentId !== lastCurrentId;
  if (currentIdChanged) {
    // 结构变化: 新消息 → 立即重建
    cancelAnimationFrame(pendingRebuild);
    buildMessages();
  } else if (_messages) {
    // 内容变化 (流式): 节流到每帧一次
    if (!pendingRebuild) {
      pendingRebuild = requestAnimationFrame(() => {
        pendingRebuild = null;
        buildMessages();
      });
    }
  }
};

// scrollToBottom 双重调用补偿 content-visibility: auto
const scrollToBottom = () => {
  const element = document.getElementById('messages-container');
  element.scrollTop = element.scrollHeight;
  requestAnimationFrame(() => {
    element.scrollTop = element.scrollHeight;  // 补偿重排
  });
};
```

### A.3 Vercel AI Chatbot — `useScrollToBottom`

```typescript
// hooks/use-scroll-to-bottom.tsx
// 核心思路: MutationObserver + ResizeObserver 双保险
useEffect(() => {
  const scrollIfNeeded = () => {
    if (isAtBottomRef.current && !isUserScrollingRef.current) {
      requestAnimationFrame(() => {
        container.scrollTo({
          top: container.scrollHeight,
          behavior: "instant",  // 自动滚动用 instant
        });
      });
    }
  };

  const mutationObserver = new MutationObserver(scrollIfNeeded);
  mutationObserver.observe(container, {
    childList: true, subtree: true, characterData: true
  });

  const resizeObserver = new ResizeObserver(scrollIfNeeded);
  resizeObserver.observe(container);
  for (const child of container.children) {
    resizeObserver.observe(child);
  }
}, []);

// 底部检测阈值: 100px
const checkIfAtBottom = () => {
  const { scrollTop, scrollHeight, clientHeight } = container;
  return scrollTop + clientHeight >= scrollHeight - 100;
};

// 用户滚动检测: 150ms 防抖
const handleScroll = () => {
  isUserScrollingRef.current = true;
  clearTimeout(scrollTimeout);
  scrollTimeout = setTimeout(() => {
    isUserScrollingRef.current = false;
  }, 150);
};
```

### A.4 Telegram — PageDownButton

```java
// ChatActivityBlurredRoundPageDownButton.java
// 模糊背景 + 计数器 的"回到底部"按钮
public class ChatActivityBlurredRoundPageDownButton extends FrameLayout {
    private ChatActivityBlurredRoundButton buttonView;
    private CounterView counterView;

    public void setCount(int count, boolean animated) {
        if (counterView == null) {
            counterView = new CounterView(getContext(), resourcesProvider);
            counterView.setReverse(reversedCounter);
            addView(counterView, ...);
        }
        counterView.setCount(count, animated);
    }
}
```

---

## 附录 B: oc-remote 当前实现代码片段

```kotlin
// ChatScreen.kt:1369-1425 (核心滚动逻辑)
var autoScrollEnabled by remember { mutableStateOf(true) }
var isAutoScrolling by remember { mutableStateOf(false) }

val isAtBottom by remember {
    derivedStateOf { listState.firstVisibleItemIndex == 0 }
}

// 用户滚动检测
LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
    if (isAutoScrolling) return@LaunchedEffect
    if (listState.isScrollInProgress) autoScrollEnabled = false
    else if (isAtBottom) autoScrollEnabled = true
}

// 自动滚动触发 (按 message/part 粒度)
val messageCount = uiState.messages.size
val lastPartCount = uiState.messages.firstOrNull()?.parts?.size ?: 0
val pendingCount = uiState.pendingPermissions.size + uiState.pendingQuestions.size
LaunchedEffect(messageCount, lastPartCount, pendingCount, isBusy) {
    if (messageCount > 0 && autoScrollEnabled) {
        isAutoScrolling = true
        listState.scrollToItem(0)
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        isAutoScrolling = false
    }
}

// FAB (lines 2569-2588)
if (!isAtBottom && !autoScrollEnabled) {
    IconButton(onClick = {
        scope.launch { listState.scrollToItem(0) }
        autoScrollEnabled = true
    }) {
        Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom")
    }
}
```

---

## 附录 C: 技术方案对照表

| 技术栈 | 自动滚动 | 用户锁定 | 回到底部 | 流式节流 |
|--------|----------|----------|----------|----------|
| **Web (React)** | `scrollTo({top: scrollHeight})` | scroll event + threshold | FAB + smooth scroll | MutationObserver + rAF |
| **Web (Svelte)** | `element.scrollTop = scrollHeight` | on:scroll binding | FAB | pendingRebuild flag |
| **Android (RecyclerView)** | `scrollToPosition(0)` | OnScrollListener | FAB + CounterView | Handler.postDelayed |
| **Compose (LazyColumn)** | `scrollToItem(0)` / `animateScrollToItem(0)` | isScrollInProgress + isAtBottom | FAB | LaunchedEffect deps |
| **iOS (UICollectionView)** | `scrollToItem(at:scrollPosition:)` | scrollViewDidScroll delegate | FAB | DispatchQueue.main.async |
