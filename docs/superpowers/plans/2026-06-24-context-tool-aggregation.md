# Context 工具聚合卡片 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将连续的 read/glob/grep 工具调用聚合为 SegmentedListItem 可折叠卡片，折叠时显示"已探索 N 个文件 · M 次搜索"。

**Architecture:** 在 MessageCardAssistant 的 parts 遍历前插入分组预处理（groupContextParts），将连续 context 工具合并为 PartGroup.Context，渲染为 ContextToolGroupCard（SegmentedListItem + AnimatedVisibility）。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 Expressive (SegmentedListItem), JUnit4

## Global Constraints

- Compose BOM `2026.05.01`，Material 3 含 SegmentedListItem（`@ExperimentalMaterial3ExpressiveApi`）
- Icons 包：`androidx.compose.material.icons.Icons` + `androidx.compose.material.icons.filled.XXX`
- JsonPrimitive：`kotlinx.serialization.json.JsonPrimitive`，取值用 `kotlinx.serialization.json.jsonPrimitive` + `contentOrNull`
- Theme tokens：`dev.leonardo.ocremotev2.ui.theme.{ShapeTokens, AlphaTokens}`
- 字符串资源：`dev.leonardo.ocremotev2.R.string.xxx`
- 编译检查命令：`.\gradlew :app:compileDevDebugKotlin`（超时 120s）
- 单元测试命令：`.\gradlew :app:testDevDebugUnitTest --tests "xxx" --rerun`（超时 180s）
- 完整构建命令：`.\gradlew :app:assembleDevRelease`（超时 300s）

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `tools/PartGrouper.kt` | 新增 | 分组算法 + ContextSummary + contextToolSummary |
| `tools/ContextToolGroupCard.kt` | 新增 | SegmentedListItem 聚合卡片 UI |
| `components/MessageCard.kt` | 修改 | parts 遍历前插入 groupContextParts |
| `res/values/strings.xml` | 修改 | 新增 4 个字符串 |

> 所有 `tools/` 路径前缀为 `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/`
> 测试路径前缀为 `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/`

---

### Task 1: PartGrouper 分组算法 + 单元测试

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouper.kt`
- Test: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouperTest.kt`

**Interfaces:**
- Produces: `PartGroup` sealed class, `groupContextParts(parts: List<Part>): List<PartGroup>`, `ContextSummary`, `contextToolSummary(parts: List<Part.Tool>): ContextSummary`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouperTest.kt`:

```kotlin
package dev.leonardo.ocremotev2.ui.screens.chat.tools

import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.domain.model.ToolState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartGrouperTest {

    private fun tool(id: String, toolName: String, input: Map<String, JsonElement> = emptyMap()): Part.Tool {
        return Part.Tool(
            id = id,
            sessionId = "sess-1",
            messageId = "msg-1",
            callId = "call-$id",
            tool = toolName,
            state = ToolState.Completed(input = input, output = "ok"),
        )
    }

    private fun text(id: String): Part.Text {
        return Part.Text(id = id, sessionId = "sess-1", messageId = "msg-1", text = "hello")
    }

    @Test
    fun `two consecutive reads are grouped`() {
        val parts = listOf(
            tool("p1", "read", mapOf("filePath" to JsonPrimitive("/a.kt"))),
            tool("p2", "read", mapOf("filePath" to JsonPrimitive("/b.kt"))),
        )
        val groups = groupContextParts(parts)
        assertEquals(1, groups.size)
        assertTrue(groups[0] is PartGroup.Context)
        assertEquals(2, (groups[0] as PartGroup.Context).parts.size)
    }

    @Test
    fun `single read is not grouped`() {
        val parts = listOf(tool("p1", "read"))
        val groups = groupContextParts(parts)
        assertEquals(1, groups.size)
        assertTrue(groups[0] is PartGroup.Single)
    }

    @Test
    fun `read glob grep are grouped together`() {
        val parts = listOf(
            tool("p1", "read"),
            tool("p2", "glob"),
            tool("p3", "grep"),
        )
        val groups = groupContextParts(parts)
        assertEquals(1, groups.size)
        assertTrue(groups[0] is PartGroup.Context)
        assertEquals(3, (groups[0] as PartGroup.Context).parts.size)
    }

    @Test
    fun `bash splits context groups`() {
        val parts = listOf(
            tool("p1", "read"),
            tool("p2", "read"),
            tool("p3", "bash"),
            tool("p4", "read"),
        )
        val groups = groupContextParts(parts)
        assertEquals(3, groups.size)
        assertTrue(groups[0] is PartGroup.Context)   // p1+p2
        assertTrue(groups[1] is PartGroup.Single)     // bash
        assertTrue(groups[2] is PartGroup.Single)     // p4 (single, not grouped)
    }

    @Test
    fun `text part splits context groups`() {
        val parts = listOf(
            tool("p1", "read"),
            text("p2"),
            tool("p3", "read"),
            tool("p4", "read"),
        )
        val groups = groupContextParts(parts)
        assertEquals(3, groups.size)
        assertTrue(groups[0] is PartGroup.Single)     // p1 (single)
        assertTrue(groups[1] is PartGroup.Single)     // text
        assertTrue(groups[2] is PartGroup.Context)    // p3+p4
    }

    @Test
    fun `summary counts read glob grep correctly`() {
        val parts = listOf(
            tool("p1", "read"),
            tool("p2", "read"),
            tool("p3", "glob"),
            tool("p4", "grep"),
            tool("p5", "list"),
        )
        val summary = contextToolSummary(parts)
        assertEquals(2, summary.read)
        assertEquals(2, summary.search)  // glob + grep
    }

    @Test
    fun `tool names are case insensitive`() {
        val parts = listOf(
            tool("p1", "READ"),
            tool("p2", "Glob"),
        )
        val groups = groupContextParts(parts)
        assertEquals(1, groups.size)
        assertTrue(groups[0] is PartGroup.Context)
    }

    @Test
    fun `empty list returns empty`() {
        val groups = groupContextParts(emptyList())
        assertTrue(groups.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "dev.leonardo.ocremotev2.ui.screens.chat.tools.PartGrouperTest" --rerun`
Expected: FAIL — `groupContextParts`, `PartGroup`, `contextToolSummary` unresolved

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouper.kt`:

```kotlin
package dev.leonardo.ocremotev2.ui.screens.chat.tools

import dev.leonardo.ocremotev2.domain.model.Part

private val CONTEXT_TOOLS = setOf("read", "glob", "grep")

sealed class PartGroup {
    data class Context(val parts: List<Part.Tool>) : PartGroup()
    data class Single(val part: Part) : PartGroup()
}

data class ContextSummary(val read: Int, val search: Int)

fun groupContextParts(parts: List<Part>): List<PartGroup> {
    val result = mutableListOf<PartGroup>()
    val buffer = mutableListOf<Part.Tool>()

    fun flush() {
        if (buffer.size >= 2) {
            result.add(PartGroup.Context(buffer.toList()))
        } else {
            buffer.forEach { result.add(PartGroup.Single(it)) }
        }
        buffer.clear()
    }

    for (part in parts) {
        if (part is Part.Tool && part.tool.lowercase() in CONTEXT_TOOLS) {
            buffer.add(part)
        } else {
            flush()
            result.add(PartGroup.Single(part))
        }
    }
    flush()
    return result
}

fun contextToolSummary(parts: List<Part.Tool>): ContextSummary {
    val read = parts.count { it.tool.lowercase() == "read" }
    val search = parts.count { it.tool.lowercase() in setOf("glob", "grep") }
    return ContextSummary(read, search)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "dev.leonardo.ocremotev2.ui.screens.chat.tools.PartGrouperTest" --rerun`
Expected: PASS — all 8 tests

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouper.kt app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/PartGrouperTest.kt
git commit -m "feat: add PartGrouper for context tool aggregation"
```

---

### Task 2: i18n 字符串

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: 14 个 locale 的 `strings.xml`

**Interfaces:**
- Produces: `R.string.context_exploring`, `R.string.context_explored`, `R.string.context_read_count`, `R.string.context_search_count`

- [ ] **Step 1: Add strings to default values/strings.xml**

在 `app/src/main/res/values/strings.xml` 中找到工具相关的字符串（如 `tool_read`），在其附近添加：

```xml
<string name="context_exploring">Exploring</string>
<string name="context_explored">Explored</string>
<string name="context_read_count">%1$d files</string>
<string name="context_search_count">%1$d searches</string>
```

- [ ] **Step 2: Add Chinese translations to values-zh/strings.xml**

```xml
<string name="context_exploring">探索中</string>
<string name="context_explored">已探索</string>
<string name="context_read_count">%1$d 个文件</string>
<string name="context_search_count">%1$d 次搜索</string>
```

- [ ] **Step 3: Add translations to values-zh-rTW/strings.xml**

```xml
<string name="context_exploring">探索中</string>
<string name="context_explored">已探索</string>
<string name="context_read_count">%1$d 個檔案</string>
<string name="context_search_count">%1$d 次搜尋</string>
```

- [ ] **Step 4: Run lokit to sync remaining locales**

Run: `lokit`
Expected: 其他 12 个 locale 自动填充翻译

- [ ] **Step 5: Verify build**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/
git commit -m "feat: add i18n strings for context tool aggregation"
```

---

### Task 3: ContextToolGroupCard 组件

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/ContextToolGroupCard.kt`

**Interfaces:**
- Consumes: `PartGroup.Context` from Task 1, `contextToolSummary` from Task 1, `extractToolInput` from `ToolCardRenderer.kt`, `R.string.*` from Task 2
- Produces: `ContextToolGroupCard(parts: List<Part.Tool>, onOpenFile: (String) -> Unit, modifier: Modifier)`

- [ ] **Step 1: Create ContextToolGroupCard.kt**

Create `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/ContextToolGroupCard.kt`:

```kotlin
package dev.leonardo.ocremotev2.ui.screens.chat.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Part
import dev.leonardo.ocremotev2.domain.model.ToolState
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContextToolGroupCard(
    parts: List<Part.Tool>,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val pending = remember(parts) {
        parts.any { it.state is ToolState.Running || it.state is ToolState.Pending }
    }
    val summary = remember(parts) { contextToolSummary(parts) }
    val itemCount = 1 + if (expanded) parts.size else 0
    val colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        // Header
        SegmentedListItem(
            onClick = { expanded = !expanded },
            colors = colors,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = itemCount),
            leadingContent = {
                Icon(
                    imageVector = if (pending) Icons.AutoMirrored.Filled.Search
                                  else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (pending) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                                  else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            },
            content = {
                Text(
                    text = stringResource(
                        if (pending) R.string.context_exploring
                        else R.string.context_explored
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            supportingContent = {
                val summaryText = buildSummaryText(summary.read, summary.search)
                if (summaryText.isNotEmpty()) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        // Expanded children
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                parts.forEachIndexed { idx, part ->
                    SegmentedListItem(
                        onClick = { handleToolClick(part, onOpenFile) },
                        colors = colors,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = idx + 1,
                            count = itemCount,
                        ),
                        leadingContent = {
                            Icon(
                                imageVector = toolIcon(part.tool),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        content = {
                            Text(
                                text = toolLabel(part.tool),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        supportingContent = {
                            val subtitle = toolSubtitle(part)
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun buildSummaryText(readCount: Int, searchCount: Int): String {
    val parts = mutableListOf<String>()
    if (readCount > 0) {
        parts.add(stringResource(R.string.context_read_count, readCount))
    }
    if (searchCount > 0) {
        parts.add(stringResource(R.string.context_search_count, searchCount))
    }
    return parts.joinToString(" · ")
}

private fun handleToolClick(part: Part.Tool, onOpenFile: (String) -> Unit) {
    if (part.tool.lowercase() != "read") return
    val input = extractToolInput(part)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull
    if (filePath != null) onOpenFile(filePath)
}

private fun toolIcon(toolName: String) = when (toolName.lowercase()) {
    "read" -> Icons.Filled.Description
    "glob" -> Icons.Filled.FindInPage
    "grep" -> Icons.AutoMirrored.Filled.Search
    else -> Icons.Filled.Description
}

@Composable
private fun toolLabel(toolName: String): String = when (toolName.lowercase()) {
    "read" -> stringResource(R.string.tool_read)
    "glob" -> stringResource(R.string.tool_glob)
    "grep" -> stringResource(R.string.tool_grep)
    else -> toolName
}

private fun toolSubtitle(part: Part.Tool): String {
    val input = extractToolInput(part)
    return when (part.tool.lowercase()) {
        "read" -> input["filePath"]?.jsonPrimitive?.contentOrNull
            ?: input["path"]?.jsonPrimitive?.contentOrNull ?: ""
        "glob", "grep" -> input["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        else -> ""
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

If `Icons.Filled.FindInPage` is unresolved, replace with `Icons.Filled.Search`.
If `Icons.AutoMirrored.Filled.Search` is unresolved, replace with `Icons.Filled.Search`.
If `R.string.tool_glob` or `R.string.tool_grep` don't exist, use hardcoded labels: `"查找文件"` / `"搜索代码"`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/tools/ContextToolGroupCard.kt
git commit -m "feat: add ContextToolGroupCard with SegmentedListItem"
```

---

### Task 4: MessageCard 集成

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/MessageCard.kt` (parts 遍历区域，约 386-412 行)

**Interfaces:**
- Consumes: `groupContextParts`, `PartGroup`, `ContextToolGroupCard` from Tasks 1+3
- Produces: Modified MessageCardAssistant that groups consecutive context tools

- [ ] **Step 1: Read current MessageCardAssistant parts loop**

Read `MessageCard.kt` lines 380-420 to find the exact double-loop code:
```kotlin
for ((msgIndex, msg) in turnMsgs.withIndex()) {
    val msgParts = filterRenderableParts(msg.parts)
    for (part in msgParts) {
        key(part.id) {
            PartContent(part = part, ...)
        }
    }
    if (msgIndex < turnMsgs.lastIndex && msgParts.isNotEmpty()) {
        HorizontalDivider(...)
    }
}
```

- [ ] **Step 2: Replace inner part loop with grouped loop**

Replace the `for (part in msgParts)` inner loop with:

```kotlin
val groups = groupContextParts(msgParts)
for (group in groups) {
    when (group) {
        is PartGroup.Context -> key(group.parts.first().id) {
            ContextToolGroupCard(
                parts = group.parts,
                onOpenFile = onOpenFile,
            )
        }
        is PartGroup.Single -> key(group.part.id) {
            PartContent(
                part = group.part,
                textColor = textColor,
                isUser = false,
                onViewSubSession = onViewSubSession,
                onOpenFile = onOpenFile,
                turnAgentName = if (group.part is Part.Tool && group.part.tool == "task") {
                    // 保留原有 task agent 名提取逻辑
                } else null,
            )
        }
    }
}
```

注意：`PartContent` 的参数列表需要与当前代码完全一致。Step 1 中读取的代码会揭示确切参数。将 `group.part` 替换原来的 `part` 变量即可，其余参数（textColor、isUser、onViewSubSession、onOpenFile、turnAgentName）保持不变。

同时添加 import:
```kotlin
import dev.leonardo.ocremotev2.ui.screens.chat.tools.{ContextToolGroupCard, PartGroup, groupContextParts}
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/MessageCard.kt
git commit -m "feat: integrate context tool aggregation in MessageCard"
```

---

### Task 5: 构建验证 + 发包测试

**Files:**
- 无新增文件

- [ ] **Step 1: Full build**

Run: `.\gradlew :app:assembleDevRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Install on emulator**

Run: `adb install -r app/build/outputs/apk/dev/release/app-dev-release.apk`
Expected: Success

- [ ] **Step 3: Manual test — trigger context exploration**

在 app 中发送一个需要 agent 探索代码库的 prompt（如"这个项目有哪些文件？"），验证：
- 连续的 read/glob/grep 调用被聚合为一个卡片
- 卡片折叠时显示"已探索 N 个文件 · M 次搜索"
- 点击卡片展开，显示每个工具的摘要行
- read 行点击可打开文件查看器
- bash/edit 等非 context 工具仍为独立卡片
- 单个 read/glob/grep 不被聚合（保持独立卡片）

- [ ] **Step 4: Bump version and publish**

```bash
# 修改 version.properties
# VERSION_CODE += 1
# VERSION_NAME = "2.0.0-beta.XX"

.\gradlew --stop
.\gradlew :app:assembleDevRelease

gh release create "v2.0.0-beta.XX-dev" \
  "app/build/outputs/apk/dev/release/app-dev-release.apk" \
  --title "v2.0.0-beta.XX-dev — Context 工具聚合卡片" \
  --prerelease \
  --notes "将连续 read/glob/grep 工具调用聚合为 SegmentedListItem 可折叠卡片"
```

- [ ] **Step 5: Commit version bump**

```bash
git add version.properties
git commit -m "chore: bump version to v2.0.0-beta.XX"
```
