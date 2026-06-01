# 复制交互重新设计

**日期**: 2026-06-01
**状态**: 设计已确认，待实现
**项目**: OC Remote (D:\Develop\code\app\oc-remote)

## 背景

当前复制功能存在以下问题：
- 统计栏的复制按钮点击后弹出 MarkdownPreviewDialog 全屏弹窗，用户需在弹窗中长按选择文本才能复制，不够直觉
- 工具卡片（Edit/Write/Read/Search/Task/通用 ToolCall）没有复制能力
- BashToolCard 是唯一有复制按钮的工具卡片，但复制后无反馈

## 目标

1. 统计栏复制按钮改为直接复制，取消弹窗
2. 工具标题行长按可静默复制工具标识文本
3. 工具输出内容长按可触发系统原生文本选择手柄

## 设计

### 第一部分：统计栏复制按钮 — 直接复制

**改动文件**: `ChatMessageList.kt` (Line 185-192)

**当前行为**: 点击复制按钮 → 提取所有 Part.Text → 调用 `onMarkdownPreviewText(text)` → 弹出 MarkdownPreviewDialog

**改为**: 点击复制按钮 → 提取所有 Part.Text → `clipboardManager.setText()` + Snackbar "已复制到剪贴板"

```kotlin
// ChatMessageList.kt — onCopyText for assistant turn last message
onCopyText = if (isTurnLast) {
    {
        val messages = turnMessagesForMsg
        val text = messages.flatMap { m ->
            m.parts.filterIsInstance<Part.Text>().map { it.text }
        }.joinToString("\n\n")
        if (text.isNotBlank()) {
            clipboardManager.setText(AnnotatedString(text))
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.chat_copied_clipboard)
                )
            }
        }
    }
} else null
```

**MarkdownPreviewDialog 处理**: 检查是否还有其他调用方。如果没有，可保留文件备用但不影响功能。

### 第二部分：工具标题行长按复制

**通用策略**: 将标题行 Row 的 `.clickable` 改为 `.combinedClickable(onClick = { ... }, onLongClick = { 复制+反馈 })`。需要 `@OptIn(ExperimentalFoundationApi::class)`。

**各卡片改动详情**:

| 卡片 | 文件 | 标题行行号 | 长按复制内容 | 当前点击行为 |
|------|------|-----------|------------|------------|
| BashToolCard | `cards/BashToolCard.kt` | L104-160 | `$ command` (仅命令部分) | 展开/折叠 |
| EditToolCard | `cards/EditToolCard.kt` | L107-156 | `Edit: filePath` | 展开/折叠 |
| WriteToolCard | `cards/WriteToolCard.kt` | L83-124 | `Write: filePath` | 展开/折叠 |
| ReadToolCard | `cards/ReadToolCard.kt` | L91-132 | `Read: filePath` | 展开/折叠 |
| SearchToolCard | `cards/SearchToolCard.kt` | L97-133 | `title` (含搜索模式) | 展开/折叠 |
| TaskToolCard | `cards/TaskToolCard.kt` | L104-165 | `description` 或 `agentType + Agent` | 跳转子会话 或 展开/折叠 |
| ToolCallCard | `ToolCardRenderer.kt` | L89-155 | `title · subtitle` | 展开/折叠 |

**注意**: TaskToolCard 的标题行有两种点击行为（有子会话时跳转，否则展开折叠），长按复制不应影响这两种行为。

**反馈方式**: 使用 Toast（因为工具卡片内部没有 SnackbarHostState），可通过 Context 获取：
```kotlin
val context = LocalContext.current
// 长按时:
clipboardManager.setText(AnnotatedString(copyText))
Toast.makeText(context, context.getString(R.string.chat_copied_clipboard), Toast.LENGTH_SHORT).show()
```

### 第三部分：工具输出内容长按选择

在工具卡片的输出文本区域包裹 `SelectionContainer`，使长按可触发系统原生文本选择手柄。

**各卡片改动详情**:

| 卡片 | 文件 | 输出区域行号 | 已有 SelectionContainer? | 改动 |
|------|------|------------|:----------------------:|------|
| BashToolCard | `cards/BashToolCard.kt` | L162-189 | ✅ 已有 | 无需改动 |
| EditToolCard | `cards/EditToolCard.kt` | L158-196 | ❌ | DiffView 输出较复杂，用 `SelectionContainer` 包裹 DiffView |
| WriteToolCard | `cards/WriteToolCard.kt` | L126-151 | ❌ | 用 `SelectionContainer` 包裹 Text |
| ReadToolCard | `cards/ReadToolCard.kt` | L134-189 | ❌ | 用 `SelectionContainer` 包裹 Column |
| SearchToolCard | `cards/SearchToolCard.kt` | L135-158 | ✅ MarkdownContent 内部已有 | 无需改动 |
| TaskToolCard | `cards/TaskToolCard.kt` | L167-190 | ✅ MarkdownContent 内部已有 | 无需改动 |
| ToolCallCard | `ToolCardRenderer.kt` | L157-216 | ❌ | 用 `SelectionContainer` 包裹输出区域 Column |

**嵌套冲突注意**: `SelectionContainer` 不能嵌套。如果 MarkdownContent 内部已有 SelectionContainer，外部不要再包裹。SearchToolCard 和 TaskToolCard 的 MarkdownContent 已内部处理。

**BashToolCard 额外修复**: 复制按钮（L138-150）点击后无反馈，需补充 Toast：
```kotlin
IconButton(
    onClick = {
        clipboardManager.setText(AnnotatedString(displayText))
        Toast.makeText(context, context.getString(R.string.chat_copied_clipboard), Toast.LENGTH_SHORT).show()
    },
)
```

## 涉及文件

| 文件 | 改动类型 |
|------|---------|
| `ui/screens/chat/components/ChatMessageList.kt` | 修改 onCopyText 回调 |
| `ui/screens/chat/tools/cards/BashToolCard.kt` | 标题行长按 + 复制按钮反馈 |
| `ui/screens/chat/tools/cards/EditToolCard.kt` | 标题行长按 + 输出 SelectionContainer |
| `ui/screens/chat/tools/cards/WriteToolCard.kt` | 标题行长按 + 输出 SelectionContainer |
| `ui/screens/chat/tools/cards/ReadToolCard.kt` | 标题行长按 + 输出 SelectionContainer |
| `ui/screens/chat/tools/cards/SearchToolCard.kt` | 标题行长按 |
| `ui/screens/chat/tools/cards/TaskToolCard.kt` | 标题行长按 |
| `ui/screens/chat/tools/ToolCardRenderer.kt` | 标题行长按 + 输出 SelectionContainer |
| `ui/screens/chat/dialog/MarkdownPreviewDialog.kt` | 检查是否可移除 |

## 触摸到复制全链路竞态分析

基于当前代码的组件层级结构，分析每个触摸事件流经的路径及潜在的竞态条件。

### 触摸事件流路径

**路径 A：标题行短按（展开/折叠）**
```
手指按下 → combinedClickable 检测
  ├─ < 400ms 抬起 → onClick → onToggleExpand()
  └─ > 400ms 未抬起 → onLongClick → 复制+Toast（见路径 C）
```

**路径 B：输出区域长按（文本选择）**
```
手指按下 → verticalScroll + SelectionContainer 手势竞争
  ├─ 垂直拖动 → verticalScroll 消费（滚动）或 SelectionContainer 消费（选择）
  └─ 长按不动 → SelectionContainer 触发文本选择手柄
```

**路径 C：标题行长按（静默复制）**
```
手指按下 → combinedClickable 检测
  └─ > 400ms → onLongClick → clipboardManager.setText() + Toast
```

**路径 D：统计栏复制按钮短按**
```
手指按下 → Icon.clickable → onCopyText()
  → clipboardManager.setText()（同步）
  → coroutineScope.launch { snackbarHostState.showSnackbar() }（异步）
```

### 竞态条件清单

#### RC-1 [P2]: BashToolCard 复制按钮区域的长按误触发

**场景**: 用户在 BashToolCard 标题行右侧的复制 IconButton 上执行长按（而非短按）。

**组件层级**:
```
Row(.combinedClickable(onClick=展开, onLongClick=复制$command))
  └─ IconButton(.clickable(onClick=复制完整输出))  ← 子组件
```

**问题**: IconButton 没有注册 onLongClick。用户长按 IconButton 时，事件传播到父 Row 的 `onLongClick`，触发的是复制 `$ command`（仅命令）而非完整输出。与短按按钮复制完整输出的行为不一致。

**影响**: 低。用户不太会在 22dp 的小图标上长按，且两种复制都有反馈。

**处理方案**: 可接受现状。如需修复，给 IconButton 添加 `Modifier.pointerInput` 消费长按事件：
```kotlin
Modifier.pointerInput(Unit) {
    detectTapGestures(onLongPress = { /* 消费但不处理 */ })
}
```

#### RC-2 [P2]: SelectionContainer + verticalScroll + codeHorizontalScroll 三层手势竞争

**场景**: 所有新增 SelectionContainer 的工具卡片（Write/Read/Edit/ToolCall）。

**组件层级**（以 WriteToolCard 为例）:
```
AnimatedVisibility
  └─ Surface(.verticalScroll)          ← L1: 垂直滚动
       └─ SelectionContainer            ← L2: 文本选择（新增）
            └─ Text(.codeHorizontalScroll) ← L3: 水平滚动
```

**问题**: 三层手势竞争——长按选择文本时垂直拖动会被 verticalScroll 消费（滚动），导致无法通过拖动扩展选择范围。这是 Compose 已知问题（b/217469105）。

**当前状态**: BashToolCard 已有相同的 SelectionContainer + verticalScroll 组合，说明项目已接受此限制。

**影响**: 中。用户可以长按开始选择，但拖动扩展选择可能不流畅。仍然可以通过多次长按+系统菜单"全选"来复制。

**处理方案**: 这是 Compose 框架的已知限制，不在本设计范围内。未来 Compose 修复后自动改善。

#### RC-3 [已验证安全]: EditToolCard DiffView 内部无手势冲突

**验证结果**: DiffView（DiffHelpers.kt L58-110）内部仅使用纯展示组件（Column > Row > Text），没有任何 clickable/scrollable 手势修饰符。唯一的交互修饰符是 `codeHorizontalScroll`。

**结论**: SelectionContainer 包裹 DiffView 不会与内部手势冲突。与 RC-2 相同的三层竞争存在，但无额外的 DiffView 特有问题。

#### RC-4 [P2]: 快速连续长按 Toast 堆叠

**场景**: 用户在 1 秒内对同一个工具标题行执行多次长按。

**问题**: 每次 onLongClick 都会 `Toast.makeText().show()`，Android Toast 队列会排队显示，导致多个 Toast 依次弹出（每个持续 ~2s）。

**影响**: 低。不影响复制功能，仅影响视觉体验。

**处理方案**: 可选。添加防抖——记录上次复制内容+时间戳，1 秒内相同内容不重复弹 Toast。

#### RC-5 [需验证]: TaskToolCard 双重点击行为 + 长按

**场景**: TaskToolCard 标题行有两种 onClick 行为：
- 有 `subSessionId` → 点击跳转子会话
- 无 `subSessionId` → 点击展开/折叠

**当前代码**（L107-113）:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth().let { mod ->
        when {
            subSessionId != null && onViewSubSession != null ->
                mod.clickable { onViewSubSession(subSessionId) }
            else ->
                mod.clickable { onToggleExpand() }
        }
    }
)
```

**改为 combinedClickable 时**: 需保持条件分支逻辑，两种情况都添加相同的 onLongClick（复制 description）。

**验证点**: `combinedClickable` 的 `onClick` 参数与当前 `clickable` 的 `onClick` 参数签名一致，可以安全替换。条件分支只需将 `.clickable { ... }` 改为 `.combinedClickable(onClick = { ... }, onLongClick = { ... })`。

**处理方案**: 实现时注意保持条件分支结构，在 `when` 的两个分支中都传入 `onLongClick`。

#### RC-6 [已验证安全]: SnackbarHostState.showSnackbar 的连续调用

**场景**: 用户快速连续点击统计栏复制按钮。

**分析**: `SnackbarHostState.showSnackbar()` 是挂起函数，内部使用 `Mutex` 保证同时只显示一个 Snackbar。新调用会取消前一个正在显示的 Snackbar。所以连续点击不会导致多个 Snackbar 堆叠。

**结论**: 安全，无需特殊处理。

#### RC-7 [已验证安全]: SSE 流更新与复制的竞态

**场景**: 用户点击复制按钮时，SSE 流正在向消息追加新的 Part.Text。

**分析**: Compose 的状态管理运行在主线程的单线程调度器上。`onCopyText` lambda 捕获的 `text` 值是 lambda 创建时的快照。`clipboardManager.setText()` 是同步调用。不存在数据竞争。

**结论**: 安全。复制的是用户点击时刻的文本快照。

### 组件层级与手势竞争全景图

```
工具卡片通用结构
├─ Surface（外层容器）
│   └─ Column
│       ├─ Row(.combinedClickable)          ← Part 2: 标题行长按复制
│       │   ├─ Row(图标 + 标题文字)          ← 无手势
│       │   └─ Row(复制按钮 + 展开箭头)      ← 复制按钮有独立 clickable
│       │       ⚠️ RC-1: BashToolCard 长按传播
│       └─ AnimatedVisibility
│           └─ [Box|Surface](.verticalScroll) ← L1: 垂直滚动
│               └─ SelectionContainer         ← L2: 文本选择（Part 3 新增）
│                   └─ Text(.codeHorizontalScroll) ← L3: 水平滚动
│                       ⚠️ RC-2: 三层手势竞争

统计栏结构（MessageCardAssistant）
├─ Surface
│   └─ Column
│       ├─ PartContent 循环                  ← 正文
│       └─ Row(统计栏)
│           ├─ 时间 + Provider + Model + Tokens + Cost + Duration
│           └─ Icon(.clickable → onCopyText)  ← Part 1: 直接复制
│               ✅ RC-6: Snackbar 安全
│               ✅ RC-7: SSE 竞态安全
```

## 不做的事

- 不改变统计栏本身的布局或样式
- 不新增浮动按钮
- 不改变用户消息气泡的复制行为（已支持直接复制+Snackbar）
- 不修改 MarkdownContent 内部的 SelectionContainer 逻辑
- 不解决 Compose 框架的 SelectionContainer + scrollable 已知手势竞争问题
