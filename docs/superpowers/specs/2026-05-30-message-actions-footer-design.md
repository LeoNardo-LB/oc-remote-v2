# 消息操作栏与统计栏统一设计

## 背景

当前 oc-remote 的用户消息撤回使用 SwipeToDismissBox（左滑/右滑），不适合聊天场景。Assistant 回复卡片头部有一个多余的复制按钮。需要参考 opencode webui 的交互模式进行统一。

参考项目：[OpenCodeUI](https://github.com/lehhair/OpenCodeUI)（React + TypeScript），其用户消息操作栏采用 hover/触屏始终显示的方式。

## 需求

1. **删除 AssistantMessageCard 头部多余复制按钮** — 标题行右端的 ContentCopy 图标删除，只保留统计栏底部的复制按钮
2. **去除用户消息滑动撤回** — 删除 SwipeToDismissBox 包裹
3. **用户消息新增统计栏** — 左侧显示时间（HH:mm），右侧始终显示操作按钮（Undo + Copy）
4. **风格统一** — 用户消息统计栏与 Assistant 统计栏使用相同的视觉风格

## 设计

### 用户消息统计栏

```
┌──────────────────────────────────────────┐
│  用户消息气泡内容                          │
│                                          │
│  HH:mm                [Undo] [Copy]      │
└──────────────────────────────────────────┘
```

- 左侧：`HH:mm` 格式时间，灰色小字（10sp, alpha=0.35f）
- 右侧：Undo 图标（Undo2/RestoreFromTrash）+ Copy 图标（ContentCopy）
- 操作按钮始终可见（纯触屏设备）
- 点击 Undo → 弹出确认对话框（AlertDialog）→ 确认后执行撤回
- 点击 Copy → 复制消息文本到剪贴板 + Snackbar 提示

### Assistant 回复统计栏（不变）

```
┌──────────────────────────────────────────┐
│  [Provider] "Response" HH:mm  [Copy]     │  ← 头部（删除 Copy）
│                                          │
│  Assistant 回复内容                       │
│                                          │
│  [Provider] model  duration ↑in ↓out $   │  ← 统计栏（保留 Copy）
│  cost                    [Copy]          │
└──────────────────────────────────────────┘
```

- 头部：删除右侧的 ContentCopy 图标
- 统计栏底部：保持现有的复制按钮不变

### 视觉规格

| 属性 | 用户消息统计栏 | Assistant 统计栏 |
|------|---------------|-----------------|
| 字号 | 10sp | 10sp |
| 颜色 | onSurface.copy(alpha=0.35f) | onSurface.copy(alpha=0.35f) |
| 图标大小 | 14dp | 14dp |
| 图标颜色 | onSurface.copy(alpha=0.35f) | onSurface.copy(alpha=0.35f) |
| 间距 | Arrangement.spacedBy(8.dp) | Arrangement.spacedBy(8.dp) |
| 对齐 | 左：时间，右：操作按钮 | 左：provider/model/stats，右：Copy |

## 改动范围

### 文件清单

| 文件 | 改动 |
|------|------|
| `ChatMessageBubble.kt` | 删除 SwipeToDismissBox（L215-305），新增统计栏 Row |
| `AssistantMessageCard.kt` | 删除头部复制按钮（L125-134） |

### ChatMessageBubble.kt 改动细节

1. **删除**：`SwipeToDismissBox` 包裹及相关代码（confirmValueChange、dismissState、背景渲染等）
2. **新增**：气泡内容底部添加统计栏 Row

```kotlin
// 统计栏（在气泡内容 Column 的末尾）
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // 左侧：时间
    Text(
        text = formattedTime, // HH:mm
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        modifier = Modifier.weight(1f, fill = false)
    )
    // 右侧：Undo 按钮
    if (onRevert != null) {
        Icon(
            Icons.AutoMirrored.Outlined.Undo, // 或 Icons.Default.RestoreFromTrash
            contentDescription = "撤销",
            modifier = Modifier
                .size(14.dp)
                .clickable { showRevertDialog = true },
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
    // 右侧：Copy 按钮
    if (onCopyText != null) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "复制",
            modifier = Modifier
                .size(14.dp)
                .clickable {
                    performHaptic(hapticView, hapticOn)
                    onCopyText()
                },
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}
```

3. **保留**：`showRevertDialog` state 和 `AlertDialog`（用于 Undo 按钮的确认弹窗）
4. **注意**：消息气泡内容不再被 SwipeToDismissBox 包裹，直接放在 Column 中

### AssistantMessageCard.kt 改动细节

删除头部标题行的复制按钮（约 L125-134）：

```kotlin
// 删除这段：
if (onCopyText != null) {
    Icon(
        Icons.Default.ContentCopy,
        contentDescription = stringResource(R.string.chat_copy),
        modifier = Modifier
            .size(15.dp)
            .clickable {
                performHaptic(hapticView, hapticOn)
                onCopyText()
            },
        tint = textColor.copy(alpha = 0.3f)
    )
}
```

统计栏底部的复制按钮保持不变。

## 不涉及的改动

- 子会话（onRevert=null）的用户消息：不显示 Undo 按钮，只显示时间和 Copy
- Assistant 统计栏的其他元素（provider、model、duration、tokens、cost）：保持不变
- 撤回的实际逻辑（API 调用、store 更新）：保持不变，只改 UI 触发方式
