# 5项回归Bug修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 beta.115 引入的 5 个回归 bug：列表卡顿、全路径标题、子agent无用图标、搜索入参重叠、代码块复制无提示。

**Architecture:** 新建 `OcCodeBlock` 组件替代 mikepenz 的 showHeader，其余 4 个修复均为单文件小改动（路径截取、展开图标开关、布局 Column 包裹）。

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 + mikepenz v0.41.0

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| **创建** | `.../chat/markdown/OcCodeBlock.kt` | 自绘代码块组件 — showHeader=false + 浮动复制按钮 + Toast |
| **修改** | `.../chat/markdown/MarkdownContent.kt` | 替换 codeBlock/codeFence 为 OcCodeBlock，`remember` 缓存 components |
| **修改** | `.../chat/tools/cards/EditToolCard.kt` | 路径截取改为 `java.io.File().name` |
| **修改** | `.../chat/tools/cards/ReadToolCard.kt` | 同上 |
| **修改** | `.../chat/tools/cards/ToolCardScaffold.kt` | 新增 `showExpandIcon` 参数 |
| **修改** | `.../chat/tools/cards/TaskToolCard.kt` | 导航卡片传入 `showExpandIcon = false` |
| **修改** | `.../chat/tools/cards/SearchToolCard.kt` | expandedContent 套 `Column` 防重叠 |

---

### Task 1: 创建 OcCodeBlock 组件

**Files:**
- Create: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/markdown/OcCodeBlock.kt`

- [ ] **Step 1: 创建 OcCodeBlock.kt**

```kotlin
package dev.minios.ocremote.ui.screens.chat.markdown

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import dev.snipme.highlights.Highlights
import org.intellij.markdown.ast.ASTNode

/**
 * Custom code block component replacing mikepenz's built-in showHeader.
 *
 * Renders highlighted code with a floating copy button (top-right corner).
 * Copy action triggers a system Toast ("已复制").
 *
 * @param content  Raw code text
 * @param node     Markdown AST node (used by mikepenz for language detection)
 * @param style    TextStyle for code (from markdownTypography)
 * @param highlightsBuilder  Reusable Highlights.Builder for syntax highlighting
 * @param wordWrap Whether to wrap long lines (true) or use horizontal scroll (false)
 * @param isFence  true for fenced code blocks (```), false for indented (4 spaces)
 */
@Composable
internal fun OcCodeBlock(
    content: String,
    node: ASTNode,
    style: TextStyle,
    highlightsBuilder: Highlights.Builder,
    wordWrap: Boolean,
    isFence: Boolean = false
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .fillMaxWidth()
    ) {
        // Syntax-highlighted code (mikepenz engine, no built-in header)
        if (wordWrap) {
            if (isFence) {
                MarkdownHighlightedCodeFence(
                    content, node, style, highlightsBuilder,
                    false,  // showHeader
                    true    // wordWrap
                )
            } else {
                MarkdownHighlightedCodeBlock(
                    content, node, style, highlightsBuilder,
                    false,
                    true
                )
            }
        } else {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                if (isFence) {
                    MarkdownHighlightedCodeFence(
                        content, node, style, highlightsBuilder,
                        false,
                        false
                    )
                } else {
                    MarkdownHighlightedCodeBlock(
                        content, node, style, highlightsBuilder,
                        false,
                        false
                    )
                }
            }
        }

        // Floating copy button (top-right, always visible, fixed position)
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(content))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制代码",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/markdown/OcCodeBlock.kt
git commit -m "feat: 新增 OcCodeBlock 代码块组件 (showHeader=false + 浮动复制按钮 + Toast)"
```

---

### Task 2: 替换 MarkdownContent 中的代码块渲染

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/markdown/MarkdownContent.kt`

- [ ] **Step 1: 删除不再需要的 import**

删除第 23-24 行：
```kotlin
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
```

- [ ] **Step 2: 替换 codeBlock/codeFence 定义（第 227-281 行）**

找到从 `val components = if (wordWrap) {` 到第 281 行 `}` 的整段代码，替换为：

```kotlin
    val components = remember(wordWrap) {
        markdownComponents(
            codeBlock = { model ->
                OcCodeBlock(
                    content = model.content,
                    node = model.node,
                    style = typography.code,
                    highlightsBuilder = highlightsBuilder,
                    wordWrap = wordWrap,
                    isFence = false
                )
            },
            codeFence = { model ->
                OcCodeBlock(
                    content = model.content,
                    node = model.node,
                    style = typography.code,
                    highlightsBuilder = highlightsBuilder,
                    wordWrap = wordWrap,
                    isFence = true
                )
            },
            table = { model ->
                SimpleMarkdownTable(model.content, model.node, model.typography.table)
            }
        )
    }
```

**关键改动：**
1. 删除 `if (wordWrap) ... else ...` 双分支 → 统一为一个 `markdownComponents` 调用（OcCodeBlock 内部处理 wordWrap）
2. 包裹 `remember(wordWrap)` → 缓存 components 对象，消除每帧重建
3. `codeBlock` 和 `codeFence` 都改用 `OcCodeBlock`

- [ ] **Step 3: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/markdown/MarkdownContent.kt
git commit -m "fix: MarkdownContent 用 OcCodeBlock 替换 showHeader 代码块 (修复卡顿+复制提示)"
```

---

### Task 3: 修复 EditToolCard 全路径标题

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/EditToolCard.kt:58`

- [ ] **Step 1: 修改路径截取**

```kotlin
// 改前
val shortPath = filePath.substringAfterLast('/')

// 改后
val shortPath = java.io.File(filePath).name
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/EditToolCard.kt
git commit -m "fix: EditToolCard 标题显示短文件名 (java.io.File.name)"
```

---

### Task 4: 修复 ReadToolCard 全路径标题

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ReadToolCard.kt:54`

- [ ] **Step 1: 修改路径截取**

```kotlin
// 改前
val shortPath = filePath.substringAfterLast('/')

// 改后
val shortPath = java.io.File(filePath).name
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ReadToolCard.kt
git commit -m "fix: ReadToolCard 标题显示短文件名 (java.io.File.name)"
```

---

### Task 5: ToolCardScaffold 添加 showExpandIcon 参数

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ToolCardScaffold.kt`

- [ ] **Step 1: 在函数签名添加参数（第 76 行后插入）**

在第 76 行 `expandedContent: @Composable () -> Unit` 之后插入：
```kotlin
    showExpandIcon: Boolean = true,
```

同时更新 KDoc 注释（第 58 行后插入）：
```kotlin
 * @param showExpandIcon Whether to show the expand/collapse chevron icon. Default true.
```

- [ ] **Step 2: 包裹展开图标渲染（第 161-166 行）**

找到：
```kotlin
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
```

改为：
```kotlin
                        if (showExpandIcon) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
```

- [ ] **Step 3: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ToolCardScaffold.kt
git commit -m "feat: ToolCardScaffold 添加 showExpandIcon 参数控制展开图标显示"
```

---

### Task 6: TaskToolCard 导航模式隐藏展开图标

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/TaskToolCard.kt`

- [ ] **Step 1: ToolCardScaffold 调用处新增 showExpandIcon（第 104 行后插入）**

在第 104 行 `onToggleExpand = onToggleExpand,` 之后插入：
```kotlin
        showExpandIcon = !showNavArrow,
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/TaskToolCard.kt
git commit -m "fix: TaskToolCard 导航模式隐藏无意义的展开图标"
```

---

### Task 7: 修复 SearchToolCard 入参与输出重叠

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/SearchToolCard.kt`

- [ ] **Step 1: expandedContent 包裹 Column**

找到第 77 行的 `) {` 和第 132 行的 `}`（expandedContent lambda 边界），在 `) {` 之后插入 `Column {`，在结尾 `}` 之前插入 `}`。

精确改动：

第 77-78 行：
```kotlin
// 改前
    ) {
        // 入参信息块

// 改后
    ) {
        Column {
            // 入参信息块
```

第 131-132 行（`MarkdownContent` 调用之后）：
```kotlin
// 改前
            }
        }
    }
}

// 改后
            }
        }
        } // close Column
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
.\gradlew.bat compileBetaDebugKotlin 2>&1 | Select-Object -Last 5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/SearchToolCard.kt
git commit -m "fix: SearchToolCard expandedContent 套 Column 防入参与输出重叠"
```

---

### Task 8: 构建 Release APK + 发版

**Files:** 无新增，构建产物

- [ ] **Step 1: 全量编译 Release**

```bash
.\gradlew.bat assembleBetaRelease 2>&1 | Select-Object -Last 3
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 更新版本号**

修改 `app/build.gradle.kts`:
```kotlin
// 改前
        versionCode = 315
        versionName = "2.0.0-beta.115"
// 改后
        versionCode = 316
        versionName = "2.0.0-beta.116"
```

- [ ] **Step 3: 提交 + 推送**

```bash
git add app/build.gradle.kts
git commit -m "chore: 更新版本号到 2.0.0-beta.116"
git push origin master
```

- [ ] **Step 4: 创建 GitHub Release 并上传 APK**

```bash
gh release create v2.0.0-beta.116 --title "v2.0.0-beta.116" --notes "## Bug 修复 (beta.115 回归)

### 修复
- **列表卡顿**: 移除 mikepenz showHeader，自绘 OcCodeBlock 代码块组件，消除 SubcomposeLayout 每帧重建
- **全路径标题**: Read/Edit 卡片标题改用 java.io.File.name 截取短文件名
- **子agent无用图标**: 导航模式下的 Task 卡片隐藏展开/折叠图标
- **搜索入参重叠**: SearchToolCard expandedContent 套 Column 修复 AnimatedVisibility 堆叠
- **复制无提示**: 代码块复制按钮点击后系统 Toast '已复制'" 2>&1

gh release upload v2.0.0-beta.116 app\build\outputs\apk\beta\release\app-beta-release.apk 2>&1
```
Expected: 输出 Release URL

---

## 验证清单

| # | 验证项 | 方法 |
|---|--------|------|
| 1 | 列表滚动流畅 | 聊天页面快速滑动含多个代码块的 AI 回复 |
| 2 | 读取编辑文件名 | Read/Edit 卡片标题显示文件名而非全路径（含 Windows 反斜杠路径） |
| 3 | 子 agent 无箭头 | 有 subSessionId 的 Task 卡片不显示 ExpandLess/ExpandMore 图标 |
| 4 | 搜索入参分离 | 展开 glob/grep 卡片，入参块在输出上方独立显示，不重叠 |
| 5 | 复制有提示 | 点击代码块复制按钮，系统弹出 Toast "已复制" |
