# File Viewer 多格式渲染框架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 FileViewer 扩展统一的"源码 ↔ 渲染"切换框架，支持图片(PNG/JPEG/GIF/WebP/BMP)、SVG、CSV/TSV、JSON 的渲染预览。

**Architecture:** 引入 FileType 枚举替代 isMarkdown 标记，所有新格式通过 WebView 渲染（复用 CodeWebView 配置模式）。RenderHtmlBuilder 为纯 Kotlin 函数，在 Kotlin 端解析 CSV/JSON 并生成 HTML（不用 WebView JS，更安全易测）。Markdown 保持现有 MarkdownPreview 不变。

**Tech Stack:** Kotlin + Jetpack Compose + WebView + kotlinx.serialization

## Global Constraints

- JDK 21, Compose BOM 2026.05.01
- Gradle daemon disabled; 编译超时 120s, 测试超时 180s
- 编译命令: `.\gradlew :app:compileDevDebugKotlin --console=plain`
- 测试命令: `.\gradlew :app:testDevDebugUnitTest --tests "*TestName*" --rerun --console=plain`
- ChatScreen.kt editing protocol 不涉及本计划
- 遵循 Material 3 原生组件优先原则
- JSON 美化用 kotlinx.serialization（`Json.parseToJsonElement` + `Json { prettyPrint = true }`），不用 org.json（单元测试不可用）
- CSV 解析手写简单 parser（不引入第三方库）
- 每个任务结束后 commit

---

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `ui/screens/viewer/FileType.kt` | 格式枚举 + 后缀判定 | 新建 |
| `ui/screens/viewer/RenderHtmlBuilder.kt` | CSV/JSON/SVG → HTML 生成（纯 Kotlin） | 新建 |
| `ui/screens/viewer/ImageViewer.kt` | 图片查看器 Composable（WebView `<img>`） | 新建 |
| `ui/screens/viewer/FormatWebView.kt` | SVG/CSV/JSON 统一 WebView 渲染器 | 新建 |
| `ui/screens/viewer/FileViewerUiState.kt` | UI 状态（isMarkdown → fileType） | 修改 |
| `ui/screens/viewer/FileViewerViewModel.kt` | toggleRenderMode + loadLive 二进制处理 | 修改 |
| `ui/screens/viewer/FileViewerScreen.kt` | when 块 + TopBar 切换按钮 | 修改 |
| `res/values/strings.xml` | 新增渲染切换文案 | 修改 |
| `test/.../FileTypeTest.kt` | FileType 单元测试 | 新建 |
| `test/.../RenderHtmlBuilderTest.kt` | HTML 生成单元测试 | 新建 |
| `test/.../FileViewerViewModelTest.kt` | 更新现有测试 + 新增格式测试 | 修改 |

---

## Task 1: FileType 枚举 + 后缀判定

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileType.kt`
- Test: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileTypeTest.kt`

**Interfaces:**
- Produces: `FileType` enum, `FileType.fromExtension(path: String): FileType`, `FileType.supportsRender: Boolean`

- [ ] **Step 1: 写失败测试**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class FileTypeTest {

    @Test
    fun `md extension maps to MARKDOWN`() {
        assertEquals(FileType.MARKDOWN, FileType.fromExtension("readme.md"))
    }

    @Test
    fun `markdown extension maps to MARKDOWN`() {
        assertEquals(FileType.MARKDOWN, FileType.fromExtension("doc.markdown"))
    }

    @Test
    fun `uppercase PNG maps to IMAGE`() {
        assertEquals(FileType.IMAGE, FileType.fromExtension("photo.PNG"))
    }

    @Test
    fun `all image extensions map to IMAGE`() {
        listOf("png", "jpg", "jpeg", "gif", "webp", "bmp").forEach { ext ->
            assertEquals(".$ext should be IMAGE", FileType.IMAGE, FileType.fromExtension("file.$ext"))
        }
    }

    @Test
    fun `svg extension maps to SVG`() {
        assertEquals(FileType.SVG, FileType.fromExtension("icon.svg"))
    }

    @Test
    fun `csv and tsv map to CSV`() {
        assertEquals(FileType.CSV, FileType.fromExtension("data.csv"))
        assertEquals(FileType.CSV, FileType.fromExtension("data.tsv"))
    }

    @Test
    fun `json extension maps to JSON`() {
        assertEquals(FileType.JSON, FileType.fromExtension("config.json"))
    }

    @Test
    fun `kt extension maps to TEXT`() {
        assertEquals(FileType.TEXT, FileType.fromExtension("main.kt"))
    }

    @Test
    fun `unknown extension maps to TEXT`() {
        assertEquals(FileType.TEXT, FileType.fromExtension("file.xyz"))
    }

    @Test
    fun `no extension maps to TEXT`() {
        assertEquals(FileType.TEXT, FileType.fromExtension("Makefile"))
    }

    @Test
    fun `supportsRender is false only for TEXT`() {
        assertFalse(FileType.TEXT.supportsRender)
        assertTrue(FileType.MARKDOWN.supportsRender)
        assertTrue(FileType.IMAGE.supportsRender)
        assertTrue(FileType.SVG.supportsRender)
        assertTrue(FileType.CSV.supportsRender)
        assertTrue(FileType.JSON.supportsRender)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*FileTypeTest*" --rerun --console=plain`
Expected: FAIL（FileType 未定义）

- [ ] **Step 3: 实现 FileType**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

/**
 * File types supported by FileViewer for render-mode switching.
 * TEXT files only have source view; all others support [FileViewerRenderMode.RENDER_PREVIEW].
 */
enum class FileType {
    TEXT,
    MARKDOWN,
    IMAGE,
    SVG,
    CSV,
    JSON;

    /** Whether this file type supports a rendered preview mode (vs source-only). */
    val supportsRender: Boolean get() = this != TEXT

    companion object {
        private val EXT_MAP: Map<String, FileType> = mapOf(
            "md" to MARKDOWN, "markdown" to MARKDOWN, "mdx" to MARKDOWN,
            "png" to IMAGE, "jpg" to IMAGE, "jpeg" to IMAGE,
            "gif" to IMAGE, "webp" to IMAGE, "bmp" to IMAGE,
            "svg" to SVG,
            "csv" to CSV, "tsv" to CSV,
            "json" to JSON
        )

        /** Determine file type from path extension (case-insensitive). Returns [TEXT] for unknown/no extension. */
        fun fromExtension(filePath: String): FileType =
            EXT_MAP[filePath.substringAfterLast('.', "").lowercase()] ?: TEXT
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*FileTypeTest*" --rerun --console=plain`
Expected: PASS（11 tests）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileType.kt app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileTypeTest.kt
git commit -m "feat: add FileType enum for multi-format render support"
```

---

## Task 2: RenderHtmlBuilder — HTML 生成

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/RenderHtmlBuilder.kt`
- Test: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/RenderHtmlBuilderTest.kt`

**Interfaces:**
- Consumes: `FileType` (from Task 1)
- Produces: `RenderHtmlBuilder.build(fileType: FileType, content: String, isDark: Boolean): String`

**注意:** build() 接受 `isDark: Boolean` 参数生成暗/亮主题 CSS。SVG/CSV/JSON 三种格式。JSON 用 kotlinx.serialization 美化（不用 JS）。CSV 手写 parser。

- [ ] **Step 1: 写失败测试**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals

class RenderHtmlBuilderTest {

    // ===== CSV =====

    @Test
    fun `CSV build produces table with header row`() {
        val html = RenderHtmlBuilder.build(FileType.CSV, "name,age\nAlice,30\nBob,25", isDark = false)
        assertTrue("should contain <table>", html.contains("<table"))
        assertTrue("should contain <th>name</th>", html.contains("<th>name</th>"))
        assertTrue("should contain <th>age</th>", html.contains("<th>age</th>"))
        assertTrue("should contain <td>Alice</td>", html.contains("<td>Alice</td>"))
        assertTrue("should contain <td>30</td>", html.contains("<td>30</td>"))
    }

    @Test
    fun `CSV with TSV uses tab delimiter`() {
        val html = RenderHtmlBuilder.build(FileType.CSV, "a\tb\n1\t2", isDark = false)
        assertTrue(html.contains("<th>a</th>"))
        assertTrue(html.contains("<th>b</th>"))
        assertTrue(html.contains("<td>1</td>"))
    }

    @Test
    fun `CSV handles quoted fields with commas`() {
        val html = RenderHtmlBuilder.build(FileType.CSV, "\"na,me\",val\n\"Ali,ce\",30", isDark = false)
        assertTrue(html.contains("<th>na,me</th>"))
        assertTrue(html.contains("<td>Ali,ce</td>"))
    }

    @Test
    fun `CSV empty content produces empty table`() {
        val html = RenderHtmlBuilder.build(FileType.CSV, "", isDark = false)
        assertTrue("should still contain <table>", html.contains("<table"))
    }

    // ===== JSON =====

    @Test
    fun `JSON build produces pretty-printed pre`() {
        val html = RenderHtmlBuilder.build(FileType.JSON, "{\"b\":2,\"a\":1}", isDark = false)
        assertTrue("should contain <pre>", html.contains("<pre"))
        assertTrue("should contain key a", html.contains("\"a\""))
        assertTrue("should contain key b", html.contains("\"b\""))
        assertTrue("should be indented", html.contains("  "))
    }

    @Test
    fun `JSON array produces pretty-printed pre`() {
        val html = RenderHtmlBuilder.build(FileType.JSON, "[1,2,3]", isDark = false)
        assertTrue(html.contains("<pre"))
        assertTrue(html.contains("1"))
    }

    @Test
    fun `JSON invalid produces error message`() {
        val html = RenderHtmlBuilder.build(FileType.JSON, "{invalid json}", isDark = false)
        assertTrue("should contain error text", html.contains("Invalid JSON") || html.contains("error") || html.contains("Error"))
    }

    // ===== SVG =====

    @Test
    fun `SVG build embeds svg content directly`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"><circle r=\"50\"/></svg>"
        val html = RenderHtmlBuilder.build(FileType.SVG, svg, isDark = false)
        assertTrue("should embed svg tag", html.contains("<svg"))
        assertTrue("should contain circle", html.contains("circle"))
    }

    // ===== Common =====

    @Test
    fun `dark theme produces dark background CSS`() {
        val html = RenderHtmlBuilder.build(FileType.JSON, "{}", isDark = true)
        assertTrue("should contain dark bg color", html.contains("#1") || html.contains("#0") || html.contains("dark"))
    }

    @Test
    fun `light theme produces light background CSS`() {
        val html = RenderHtmlBuilder.build(FileType.JSON, "{}", isDark = false)
        assertTrue("should contain light bg color", html.contains("#f") || html.contains("#e") || html.contains("white") || html.contains("light"))
    }

    @Test
    fun `all HTML contains viewport meta tag`() {
        listOf(FileType.CSV, FileType.JSON, FileType.SVG).forEach { ft ->
            val html = RenderHtmlBuilder.build(ft, if (ft == FileType.CSV) "a\n1" else if (ft == FileType.JSON) "{}" else "<svg/>", isDark = false)
            assertTrue("$ft should have viewport meta", html.contains("viewport"))
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*RenderHtmlBuilderTest*" --rerun --console=plain`
Expected: FAIL（RenderHtmlBuilder 未定义）

- [ ] **Step 3: 实现 RenderHtmlBuilder**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Generates complete HTML documents for rendering CSV, JSON, and SVG file previews
 * inside a WebView. All parsing is done in Kotlin (no JS) for testability and security.
 */
object RenderHtmlBuilder {

    private val jsonPretty = Json { prettyPrint = true; indent = "  " }

    fun build(fileType: FileType, content: String, isDark: Boolean): String {
        val bg = if (isDark) "#1a1a1a" else "#ffffff"
        val fg = if (isDark) "#e0e0e0" else "#1a1a1a"
        val headerBg = if (isDark) "#2a2a2a" else "#f0f0f0"
        val borderColor = if (isDark) "#444" else "#ccc"

        val bodyHtml = when (fileType) {
            FileType.CSV -> buildCsvTable(content, borderColor, headerBg)
            FileType.JSON -> buildJsonPre(content)
            FileType.SVG -> content.ifBlank { "<p style=\"color:#888;text-align:center\">Empty SVG</p>" }
            else -> "<pre>${escapeHtml(content)}</pre>"
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
        <style>
            body { margin:0; padding:12px; background:$bg; color:$fg; font-family:monospace; font-size:14px; line-height:1.5; }
            table { border-collapse:collapse; width:100%; }
            th, td { border:1px solid $borderColor; padding:6px 10px; text-align:left; }
            th { background:$headerBg; font-weight:bold; }
            pre { white-space:pre-wrap; word-wrap:break-word; margin:0; }
        </style>
        </head>
        <body>
        $bodyHtml
        </body>
        </html>
        """.trimIndent()
    }

    /** Parse CSV (comma or tab delimited, with quoted fields) and produce an HTML table. */
    private fun buildCsvTable(content: String, borderColor: String, headerBg: String): String {
        if (content.isBlank()) return "<table><tbody></tbody></table>"
        val delimiter = if (content.contains('\t')) '\t' else ','
        val rows = parseCsvLines(content, delimiter)
        if (rows.isEmpty()) return "<table><tbody></tbody></table>"

        val sb = StringBuilder()
        sb.append("<table>")
        rows.forEachIndexed { index, row ->
            val tag = if (index == 0) "th" else "td"
            sb.append("<tr>")
            row.forEach { cell ->
                sb.append("<$tag>").append(escapeHtml(cell)).append("</$tag>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
        return sb.toString()
    }

    /**
     * Simple CSV line parser supporting:
     * - delimiter (comma or tab)
     * - quoted fields (double quotes wrapping, embedded delimiters and newlines)
     * - escaped quotes ("") inside quoted fields
     */
    private fun parseCsvLines(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < content.length && content[i + 1] == '"') {
                            currentField.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        currentField.append(c)
                    }
                }
                c == '"' -> { inQuotes = true }
                c == delimiter -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                c == '\n' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                c == '\r' -> { /* skip CR */ }
                else -> currentField.append(c)
            }
            i++
        }
        // Last field/row
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    /** Pretty-print JSON using kotlinx.serialization. Returns error HTML if invalid. */
    private fun buildJsonPre(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return "<pre>{}</pre>"
        return try {
            val element: JsonElement = Json.parseToJsonElement(trimmed)
            val pretty = jsonPretty.encodeToString(JsonElement.serializer(), element)
            // Syntax-highlight keys and strings with basic coloring
            val highlighted = pretty
                .replace(Regex("""(".*?")(\s*:)""")) { match ->
                    "<span style=\"color:#7ec699\">${match.groupValues[1]}</span>${match.groupValues[2]}"
                }
                .replace(Regex(""":\s*(".*?")""")) { match ->
                    ": <span style=\"color:#f9c859\">${match.groupValues[1]}</span>"
                }
            "<pre>$highlighted</pre>"
        } catch (e: Exception) {
            "<p style=\"color:#e57373\">Invalid JSON: ${escapeHtml(e.message ?: "parse error")}</p><pre>${escapeHtml(trimmed)}</pre>"
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*RenderHtmlBuilderTest*" --rerun --console=plain`
Expected: PASS（13 tests）

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/RenderHtmlBuilder.kt app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/RenderHtmlBuilderTest.kt
git commit -m "feat: add RenderHtmlBuilder for CSV/JSON/SVG HTML generation"
```

---

## Task 3: FileViewerUiState + ViewModel 变更

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerUiState.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt`
- Modify: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt`

**Interfaces:**
- Consumes: `FileType` (from Task 1)
- Produces: `FileViewerUiState.fileType: FileType`, `FileViewerUiState.isMarkdown` (computed property for backward compat)

**关键变更点:**
1. UiState: `isMarkdown: Boolean` → `fileType: FileType` + `isMarkdown` computed getter
2. ViewModel: `toggleRenderMode()` 条件 `isMarkdown` → `fileType.supportsRender`
3. ViewModel: `loadLive()` 二进制处理——IMAGE 类型保留 base64 content
4. ViewModel: `isMarkdownFile()` 删除（被 `FileType.fromExtension` 替代）
5. 测试: 所有 `isMarkdown` 断言改用 `fileType == MARKDOWN`，新增 IMAGE/JSON/CSV/SVG 测试

- [ ] **Step 1a: 修改 FileViewerUiState.kt — 替换 isMarkdown 字段**

Read `FileViewerUiState.kt`，Edit 替换字段（约 L37-39）：

oldString:
```
    // Phase 2: Markdown render toggle
    val renderMode: FileViewerRenderMode = FileViewerRenderMode.SOURCE,
    val isMarkdown: Boolean = false,
```

newString:
```
    // Phase 2: Markdown render toggle (now multi-format via FileType)
    val renderMode: FileViewerRenderMode = FileViewerRenderMode.SOURCE,
    val fileType: FileType = FileType.TEXT,
```

- [ ] **Step 1b: 修改 FileViewerUiState.kt — 添加 computed property 类体**

Read `FileViewerUiState.kt`，Edit 文件末尾添加类体（约 L47-49）：

oldString:
```
    // Scroll to this line on initial load (-1 = no scroll, for Edit tool jump)
    val initialScrollLine: Int = -1
)
```

newString:
```
    // Scroll to this line on initial load (-1 = no scroll, for Edit tool jump)
    val initialScrollLine: Int = -1
) {
    /** Backward-compatible accessor for markdown check. */
    val isMarkdown: Boolean get() = fileType == FileType.MARKDOWN
}
```

- [ ] **Step 2: 修改 FileViewerViewModel.kt — toggleRenderMode**

oldString (L230-239):
```kotlin
    fun toggleRenderMode() {
        val current = _uiState.value
        if (!current.isMarkdown || current.mode == FileViewerMode.DIFF) return
        _uiState.update {
            it.copy(
                renderMode = if (it.renderMode == FileViewerRenderMode.SOURCE) FileViewerRenderMode.RENDER_PREVIEW
                else FileViewerRenderMode.SOURCE
            )
        }
    }
```

newString:
```kotlin
    fun toggleRenderMode() {
        val current = _uiState.value
        if (!current.fileType.supportsRender || current.mode == FileViewerMode.DIFF) return
        _uiState.update {
            it.copy(
                renderMode = if (it.renderMode == FileViewerRenderMode.SOURCE) FileViewerRenderMode.RENDER_PREVIEW
                else FileViewerRenderMode.SOURCE
            )
        }
    }
```

- [ ] **Step 3: 修改 FileViewerViewModel.kt — loadLive 文本处理**

在 `loadLive()` 的文本成功分支（约 L89-90），替换 `isMarkdown` 赋值：

oldString:
```kotlin
                                isMarkdown = isMarkdownFile(filePath),
```

newString:
```kotlin
                                fileType = FileType.fromExtension(filePath),
```

- [ ] **Step 4: 修改 FileViewerViewModel.kt — loadLive 二进制处理**

在 `loadLive()` 的 BINARY 分支（约 L69），增加 IMAGE 类型判断：

oldString:
```kotlin
                    if (c.type == ContentType.BINARY) _uiState.update { it.copy(isLoading = false, isBinary = true, mimeType = c.mimeType) }
```

newString:
```kotlin
                    if (c.type == ContentType.BINARY) {
                        val ft = FileType.fromExtension(filePath)
                        if (ft == FileType.IMAGE) {
                            // Image: retain base64 content for render preview
                            _uiState.update { it.copy(isLoading = false, isBinary = false, fileType = ft, content = c.content, mimeType = c.mimeType) }
                        } else {
                            // Other binary: not supported
                            _uiState.update { it.copy(isLoading = false, isBinary = true, mimeType = c.mimeType) }
                        }
                    }
```

- [ ] **Step 5: 修改 FileViewerViewModel.kt — setupToolSnapshotSource**

在 `setupToolSnapshotSource()`（约 L336），同样替换：

oldString:
```kotlin
                isMarkdown = isMarkdownFile(filePath),
```

newString:
```kotlin
                fileType = FileType.fromExtension(filePath),
```

- [ ] **Step 6: 删除 isMarkdownFile() 方法**

删除 FileViewerViewModel.kt 中的 `isMarkdownFile` 私有方法（约 L257-260）：

oldString:
```kotlin
    private fun isMarkdownFile(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "md" || ext == "markdown" || ext == "mdx"
    }

```

newString: (empty - remove entirely)

- [ ] **Step 7: 编译确认**

Run: `.\gradlew :app:compileDevDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 更新现有测试 — isMarkdown 断言**

在 `FileViewerViewModelTest.kt` 中，所有断言 `vm.uiState.value.isMarkdown` 改为 `vm.uiState.value.fileType == FileType.MARKDOWN`。

需要更新的测试（搜索 `isMarkdown`）:
- Test 10: `assert(vm.uiState.value.isMarkdown)` → `assert(vm.uiState.value.fileType == FileType.MARKDOWN)`
- Test 11: `assert(!vm.uiState.value.isMarkdown)` → `assert(vm.uiState.value.fileType == FileType.TEXT)`
- Test 13: toggleRenderMode no-op 测试 — 现在 .kt 文件 fileType=TEXT，supportsRender=false，toggleRenderMode 仍然 no-op，逻辑正确

注意：toggleRenderMode 的测试名 `toggleRenderMode is no-op for non-markdown files` 改为 `toggleRenderMode is no-op for TEXT files`。

oldString:
```kotlin
    // 13. toggleRenderMode no-op for non-markdown
    @Test
    fun `toggleRenderMode is no-op for non-markdown files`() = runTest {
```

newString:
```kotlin
    // 13. toggleRenderMode no-op for TEXT (non-renderable)
    @Test
    fun `toggleRenderMode is no-op for TEXT files`() = runTest {
```

- [ ] **Step 9: 新增测试 — JSON/IMAGE 格式 + toggleRenderMode**

在 FileViewerViewModelTest.kt 末尾（class 闭合 `}` 之前）新增：

```kotlin
    // ===== Multi-format render tests =====

    @Test
    fun `init with json file sets fileType JSON`() = runTest {
        coEvery { getFileContent(any(), any(), any()) } returns Result.success(
            dev.leonardo.ocremotev2.domain.model.FileContent(
                path = "config.json",
                type = ContentType.TEXT,
                content = "{\"key\":\"value\"}"
            )
        )
        val vm = FileViewerViewModel(
            savedStateHandle(path = java.net.URLEncoder.encode("config.json", "UTF-8")),
            getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
        )
        assert(vm.uiState.value.fileType == FileType.JSON) { "fileType should be JSON" }
    }

    @Test
    fun `toggleRenderMode switches SOURCE to RENDER_PREVIEW for JSON`() = runTest {
        coEvery { getFileContent(any(), any(), any()) } returns Result.success(
            dev.leonardo.ocremotev2.domain.model.FileContent(
                path = "config.json",
                type = ContentType.TEXT,
                content = "{\"key\":\"value\"}"
            )
        )
        val vm = FileViewerViewModel(
            savedStateHandle(path = java.net.URLEncoder.encode("config.json", "UTF-8")),
            getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
        )
        vm.toggleRenderMode()
        assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW) {
            "JSON should toggle to RENDER_PREVIEW"
        }
    }

    @Test
    fun `init with png binary sets fileType IMAGE and retains base64 content`() = runTest {
        coEvery { getFileContent(any(), any(), any()) } returns Result.success(
            dev.leonardo.ocremotev2.domain.model.FileContent(
                path = "photo.png",
                type = ContentType.BINARY,
                content = "iVBORw0KGgo=",
                mimeType = "image/png"
            )
        )
        val vm = FileViewerViewModel(
            savedStateHandle(path = java.net.URLEncoder.encode("photo.png", "UTF-8")),
            getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
        )
        assert(vm.uiState.value.fileType == FileType.IMAGE) { "fileType should be IMAGE" }
        assert(!vm.uiState.value.isBinary) { "IMAGE should not be marked isBinary" }
        assert(vm.uiState.value.content == "iVBORw0KGgo=") { "base64 content should be retained" }
        assert(vm.uiState.value.mimeType == "image/png") { "mimeType should be preserved" }
    }

    @Test
    fun `toggleRenderMode works for IMAGE files`() = runTest {
        coEvery { getFileContent(any(), any(), any()) } returns Result.success(
            dev.leonardo.ocremotev2.domain.model.FileContent(
                path = "photo.png",
                type = ContentType.BINARY,
                content = "iVBORw0KGgo=",
                mimeType = "image/png"
            )
        )
        val vm = FileViewerViewModel(
            savedStateHandle(path = java.net.URLEncoder.encode("photo.png", "UTF-8")),
            getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
        )
        vm.toggleRenderMode()
        assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW)
    }

    @Test
    fun `toggleRenderMode works for CSV files`() = runTest {
        coEvery { getFileContent(any(), any(), any()) } returns Result.success(
            dev.leonardo.ocremotev2.domain.model.FileContent(
                path = "data.csv",
                type = ContentType.TEXT,
                content = "a,b\n1,2"
            )
        )
        val vm = FileViewerViewModel(
            savedStateHandle(path = java.net.URLEncoder.encode("data.csv", "UTF-8")),
            getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
        )
        vm.toggleRenderMode()
        assert(vm.uiState.value.fileType == FileType.CSV)
        assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW)
    }
```

- [ ] **Step 10: 运行测试确认通过**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*FileViewerViewModelTest*" --rerun --console=plain`
Expected: PASS（现有测试 + 5 新测试）

- [ ] **Step 11: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerUiState.kt app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt
git commit -m "feat: replace isMarkdown with FileType enum in FileViewerViewModel"
```

---

## Task 4: strings.xml 新增渲染文案

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `R.string.viewer_show_render`, `R.string.viewer_show_source`, `R.string.viewer_image_render_error`

- [ ] **Step 1: 新增字符串**

在 `strings.xml` 中找到 `viewer_md_show_render` 和 `viewer_md_show_source`（约 L754-755），在其后新增通用文案：

```xml
    <string name="viewer_md_show_render">Show rendered preview</string>
    <string name="viewer_md_show_source">Show source code</string>
    <!-- Multi-format render toggle -->
    <string name="viewer_show_render">Show rendered preview</string>
    <string name="viewer_show_source">Show source code</string>
    <string name="viewer_image_render_error">Failed to load image</string>
```

注意：保留 `viewer_md_show_render` / `viewer_md_show_source`（现有代码可能引用），新增通用版本供 TopBar 使用。

- [ ] **Step 2: 编译确认**

Run: `.\gradlew :app:compileDevDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add multi-format render toggle strings"
```

---

## Task 5: ImageViewer Composable

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/ImageViewer.kt`

**Interfaces:**
- Consumes: `base64Data: String`, `mimeType: String`
- Produces: `ImageViewer` composable

**注意:** 复用 CodeWebView 的 WebView 配置模式（DisposableEffect 清理、暗色背景）。图片用 `<img src="data:mime;base64,...">` data URI。内置缩放手势。

- [ ] **Step 1: 实现 ImageViewer**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Image viewer using WebView with base64 data URI.
 * Supports pinch-to-zoom via WebView built-in zoom controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImageViewer(
    base64Data: String,
    mimeType: String,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface.toArgb()

    DisposableEffect(Unit) {
        onDispose { /* WebView cleanup happens via AndroidView factory lifecycle */ }
    }

    val html = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
    <style>
        body { margin:0; padding:0; background:${argbToHex(bgColor)}; display:flex; justify-content:center; align-items:center; min-height:100vh; }
        img { max-width:100%; height:auto; object-fit:contain; }
    </style>
    </head>
    <body>
    <img src="data:$mimeType;base64,$base64Data" alt="preview" />
    </body>
    </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(bgColor)
                webViewClient = android.webkit.WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun argbToHex(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}
```

- [ ] **Step 2: 编译确认**

Run: `.\gradlew :app:compileDevDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/ImageViewer.kt
git commit -m "feat: add ImageViewer composable for image render preview"
```

---

## Task 6: FormatWebView Composable

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FormatWebView.kt`

**Interfaces:**
- Consumes: `FileType` (from Task 1), `RenderHtmlBuilder` (from Task 2), `content: String`
- Produces: `FormatWebView` composable

**注意:** 委托 RenderHtmlBuilder 生成 HTML，用 WebView 渲染。复用 CodeWebView 的 WebView 配置模式。

- [ ] **Step 1: 实现 FormatWebView**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * WebView-based renderer for SVG, CSV, and JSON file previews.
 * Delegates HTML generation to [RenderHtmlBuilder].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FormatWebView(
    content: String,
    fileType: FileType,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.red * 0.299f + surfaceColor.green * 0.587f + surfaceColor.blue * 0.114f < 0.5f
    val bgColorArgb = surfaceColor.toArgb()

    val html = RenderHtmlBuilder.build(fileType, content, isDark)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(bgColorArgb)
                webViewClient = android.webkit.WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}
```

- [ ] **Step 2: 编译确认**

Run: `.\gradlew :app:compileDevDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FormatWebView.kt
git commit -m "feat: add FormatWebView composable for SVG/CSV/JSON render preview"
```

---

## Task 7: FileViewerScreen 集成

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerScreen.kt`

**Interfaces:**
- Consumes: `FileType`, `ImageViewer`, `FormatWebView` (from Tasks 1, 5, 6)

**关键变更点:**
1. `when` 块新增 RENDER_PREVIEW 分支（在现有 markdown 检查之后/替换）
2. TopBar 切换按钮条件 `isMarkdown` → `fileType.supportsRender`
3. TopBar 切换按钮图标/文案根据 fileType 动态选择

- [ ] **Step 1: Read FileViewerScreen.kt**

Read `FileViewerScreen.kt` 完整文件（已读过，确认 L141-191 的 when 块和 L302-327 的 TopBar actions）。

- [ ] **Step 2: 修改 when 块 — 替换 RENDER_PREVIEW 分支**

找到现有的 markdown RENDER_PREVIEW 分支（约 L154-160）：

oldString:
```kotlin
                // Phase 2: markdown render preview (before truncation check — preview shows full)
                uiState.isMarkdown && uiState.renderMode == FileViewerRenderMode.RENDER_PREVIEW -> {
                    MarkdownPreviewWithScrollAnchor(
                        markdown = uiState.content,
                        sourceScrollFraction = lastSourceFraction
                    )
                }
```

newString:
```kotlin
                // Multi-format render preview (before truncation check — preview shows full)
                uiState.fileType.supportsRender && uiState.renderMode == FileViewerRenderMode.RENDER_PREVIEW -> {
                    when (uiState.fileType) {
                        FileType.MARKDOWN -> MarkdownPreviewWithScrollAnchor(
                            markdown = uiState.content,
                            sourceScrollFraction = lastSourceFraction
                        )
                        FileType.IMAGE -> ImageViewer(
                            base64Data = uiState.content,
                            mimeType = uiState.mimeType ?: "image/*"
                        )
                        FileType.SVG, FileType.CSV, FileType.JSON -> FormatWebView(
                            content = uiState.content,
                            fileType = uiState.fileType
                        )
                        FileType.TEXT -> CodeWebView(
                            content = uiState.content,
                            filePath = uiState.filePath,
                            onLoadMore = if (!uiState.isFullyLoaded) onLoadMoreLines else null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
```

- [ ] **Step 3: 修改 TopBar 切换按钮条件**

找到 TopBar 中的 markdown 切换按钮（约 L313-327）：

oldString:
```kotlin
            // Phase 2: md render toggle (hidden when annotations exist)
            if (annotationCount == 0 && uiState.isMarkdown && uiState.mode != FileViewerMode.DIFF) {
                val isRender = uiState.renderMode == FileViewerRenderMode.RENDER_PREVIEW
                IconButton(
                    onClick = onToggleRenderMode,
                    modifier = Modifier.testTag("viewer_md_render_button")
                ) {
                    Icon(
                        imageVector = if (isRender) Icons.Default.Description else Icons.Default.RemoveRedEye,
                        contentDescription = if (isRender) stringResource(R.string.viewer_md_show_source)
                        else stringResource(R.string.viewer_md_show_render),
                        tint = if (isRender) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
```

newString:
```kotlin
            // Multi-format render toggle (hidden when annotations exist)
            if (annotationCount == 0 && uiState.fileType.supportsRender && uiState.mode != FileViewerMode.DIFF) {
                val isRender = uiState.renderMode == FileViewerRenderMode.RENDER_PREVIEW
                IconButton(
                    onClick = onToggleRenderMode,
                    modifier = Modifier.testTag("viewer_render_button")
                ) {
                    Icon(
                        imageVector = if (isRender) Icons.Default.Description else Icons.Default.RemoveRedEye,
                        contentDescription = if (isRender) stringResource(R.string.viewer_show_source)
                        else stringResource(R.string.viewer_show_render),
                        tint = if (isRender) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
```

- [ ] **Step 4: 编译确认**

Run: `.\gradlew :app:compileDevDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 运行全部 viewer 测试**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*viewer*" --rerun --console=plain`
Expected: PASS（FileTypeTest + RenderHtmlBuilderTest + FileViewerViewModelTest 全部通过）

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerScreen.kt
git commit -m "feat: integrate multi-format render preview in FileViewerScreen"
```

---

## Task 8: 最终验证 + 构建发版

**Files:** None (verification only)

- [ ] **Step 1: 全量单元测试**

Run: `.\gradlew :app:testDevDebugUnitTest --rerun --console=plain`
Expected: PASS（1004+ 测试全部通过，含新增的 FileType + RenderHtmlBuilder + ViewModel 测试）

- [ ] **Step 2: 构建 Debug APK**

Run: `.\gradlew :app:assembleDevDebug --console=plain`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 安装到模拟器手动验证**

Install: `.\gradlew :app:installDevDebug`
手动测试：
1. 打开 .md 文件 → 右上角眼睛图标 → 点击切换渲染/源码 ✅
2. 打开 .json 文件 → 右上角眼睛图标 → 点击显示美化视图 ✅
3. 打开 .csv 文件 → 右上角眼睛图标 → 点击显示表格 ✅
4. 打开 .svg 文件 → 右上角眼睛图标 → 点击显示矢量图 ✅
5. 打开 .png 文件 → 右上角眼睛图标 → 点击显示图片（支持缩放）✅
6. 打开 .kt 文件 → 无切换按钮（TEXT 不支持渲染）✅

- [ ] **Step 4: Commit 最终状态（如有遗漏修改）**

```bash
git add -A
git commit -m "chore: multi-format file viewer render framework complete"
```

---

## Summary

| Task | 文件 | 测试数 | 依赖 |
|------|------|--------|------|
| 1. FileType | FileType.kt (new) | 11 | — |
| 2. RenderHtmlBuilder | RenderHtmlBuilder.kt (new) | 13 | Task 1 |
| 3. ViewModel 变更 | UiState, ViewModel (modify) | 5 new + update existing | Task 1 |
| 4. strings.xml | strings.xml (modify) | — | — |
| 5. ImageViewer | ImageViewer.kt (new) | — (manual) | — |
| 6. FormatWebView | FormatWebView.kt (new) | — (manual) | Tasks 1, 2 |
| 7. Screen 集成 | FileViewerScreen.kt (modify) | — (manual) | Tasks 1, 5, 6 |
| 8. 最终验证 | — | full suite | All |
