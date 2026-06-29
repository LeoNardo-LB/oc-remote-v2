# Inline Code Path Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make path-like inline code spans (`` `src/Main.kt` ``) in AI replies clickable — styled with underline+link color, clicking opens FileViewer/Workspace/Snackbar via existing link infrastructure.

**Architecture:** Extend the existing paragraph component's AST extraction to also detect `CODE` (inline code) nodes. Post-process the `AnnotatedString` to add underline+link color for path-like code spans. Extend the tap handler to route code path clicks through the existing `UriHandler` → `LinkClassifier` pipeline. No changes to raw markdown data, `LinkClassifier`, `LinkUriHandler`, or `NavGraph`.

**Tech Stack:** Kotlin, Jetpack Compose, mikepenz markdown 0.43.0, IntelliJ Markdown AST parser, JUnit4 + MockK

## Global Constraints

- JDK 21, Compose BOM 2026.05.01
- mikepenz markdown 0.43.0 — `MarkdownElementTypes.CODE` is inline code, `CODE_FENCE` is code block
- IntelliJ markdown `getUnescapedTextInNode` on CODE nodes returns text INCLUDING surrounding backticks — must `.trim('`')`
- `ChatScreen.kt` editing protocol: Read before Edit, compile after each edit, commit after each successful compilation
- PowerShell: use `;` not `&&`; Gradle `--no-daemon`; compile timeout 120s, test 180s
- `LinkClassifier.classify()` already handles bare relative paths (`src/Foo.kt` → `RelativePath`) and absolute paths (`/etc/passwd` → `AbsolutePath`)
- `uriHandler.openUri(text)` → `handleLinkClick` → `LinkClassifier.classify` → file/dir/Snackbar — already working for markdown links

---

### Task 1: `isLikelyFilePath` Pure Function + Unit Tests

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/domain/model/LinkClassifier.kt`
- Test: `app/src/test/kotlin/dev/leonardo/ocremotev2/domain/model/LinkClassifierTest.kt`

**Interfaces:**
- Produces: `LinkClassifier.isLikelyFilePath(text: String): Boolean` — top-level function in `LinkClassifier` object

- [ ] **Step 1: Write failing tests**

Add these tests to `LinkClassifierTest.kt`, before the closing `}`:

```kotlin
    @Test
    fun `path with slash is likely file path`() {
        assertTrue(LinkClassifier.isLikelyFilePath("src/Main.kt"))
    }

    @Test
    fun `path with backslash is likely file path`() {
        assertTrue(LinkClassifier.isLikelyFilePath("app\\build.gradle"))
    }

    @Test
    fun `directory path ending with slash is likely file path`() {
        assertTrue(LinkClassifier.isLikelyFilePath("docs/specs/"))
    }

    @Test
    fun `bare filename with extension is likely file path`() {
        assertTrue(LinkClassifier.isLikelyFilePath("Main.kt"))
    }

    @Test
    fun `gradle filename is likely file path`() {
        assertTrue(LinkClassifier.isLikelyFilePath("build.gradle"))
    }

    @Test
    fun `code snippet is not likely file path`() {
        assertFalse(LinkClassifier.isLikelyFilePath("val x = 1"))
    }

    @Test
    fun `import statement is not likely file path`() {
        assertFalse(LinkClassifier.isLikelyFilePath("import foo"))
    }

    @Test
    fun `single word is not likely file path`() {
        assertFalse(LinkClassifier.isLikelyFilePath("TODO"))
    }

    @Test
    fun `boolean literal is not likely file path`() {
        assertFalse(LinkClassifier.isLikelyFilePath("true"))
    }
```

Add import at top of file (after existing imports):
```kotlin
import org.junit.Assert.assertFalse
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```powershell
.\gradlew :app:testDevDebugUnitTest --tests "dev.leonardo.ocremotev2.domain.model.LinkClassifierTest.isLikelyFilePath*" --no-daemon 2>&1 | Select-String "FAIL|error:|BUILD"
```
Expected: FAIL — `isLikelyFilePath` not defined (unresolved reference)

- [ ] **Step 3: Implement `isLikelyFilePath`**

Add to `LinkClassifier` object in `LinkClassifier.kt`, after the `classify` function (before the closing `}`):

```kotlin
    private val fileExtensionRegex = Regex("\\.\\w{1,10}$")

    /**
     * Heuristic: does this inline code content look like a file path or filename?
     * Returns true if it contains a path separator (/ or \) or has a file extension.
     */
    fun isLikelyFilePath(text: String): Boolean {
        if (text.contains('/') || text.contains('\\')) return true
        return fileExtensionRegex.containsMatchIn(text)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```powershell
.\gradlew :app:testDevDebugUnitTest --tests "dev.leonardo.ocremotev2.domain.model.LinkClassifierTest" --no-daemon 2>&1 | Select-String "FAIL|PASS|BUILD|Tests"
```
Expected: All tests PASS, BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/dev/leonardo/ocremotev2/domain/model/LinkClassifier.kt app/src/test/kotlin/dev/leonardo/ocremotev2/domain/model/LinkClassifierTest.kt; git commit -m "feat: add isLikelyFilePath heuristic for inline code path detection"
```

---

### Task 2: Refactor Extraction — `ClickableItem` Sealed Interface + CODE Node Support

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt`

**Interfaces:**
- Consumes: `LinkClassifier.isLikelyFilePath(text: String): Boolean` from Task 1
- Produces: `extractClickableItems(content: String, node: ASTNode): List<ClickableItem>` replacing `extractMarkdownLinks`
- Produces: `ClickableItem` sealed interface with `Link(text, url)` and `CodePath(text)` subtypes

- [ ] **Step 1: Replace `MarkdownLink` data class with `ClickableItem` sealed interface**

In `MarkdownContent.kt`, find the `MarkdownLink` data class and `extractMarkdownLinks` function (bottom of file, lines ~344-369).

Replace this block:

```kotlin
private data class MarkdownLink(val text: String, val url: String)

/**
 * Extract [text](url) links from a markdown AST paragraph node.
 * Uses raw AST offsets to find link text and destination.
 */
private fun extractMarkdownLinks(content: String, node: org.intellij.markdown.ast.ASTNode): List<MarkdownLink> {
    val links = mutableListOf<MarkdownLink>()
    fun walk(n: org.intellij.markdown.ast.ASTNode) {
        if (n.type == MarkdownElementTypes.INLINE_LINK) {
            val dest = n.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
            val textNode = n.findChildOfType(MarkdownElementTypes.LINK_TEXT)
            if (dest != null && textNode != null) {
                val url = dest.getUnescapedTextInNode(content).toString()
                val rawText = textNode.getUnescapedTextInNode(content).toString()
                val linkText = rawText.removeSurrounding("[", "]")
                if (linkText.isNotEmpty() && url.isNotEmpty()) {
                    links.add(MarkdownLink(linkText, url))
                }
            }
        }
        n.children.forEach { walk(it) }
    }
    walk(node)
    return links
}
```

With:

```kotlin
private sealed interface ClickableItem {
    val text: String
    data class Link(override val text: String, val url: String) : ClickableItem
    data class CodePath(override val text: String) : ClickableItem
}

/**
 * Extract clickable items from a markdown AST paragraph node:
 * - [text](url) markdown links → ClickableItem.Link
 * - `code` inline code that looks like a path → ClickableItem.CodePath
 */
private fun extractClickableItems(content: String, node: org.intellij.markdown.ast.ASTNode): List<ClickableItem> {
    val items = mutableListOf<ClickableItem>()
    fun walk(n: org.intellij.markdown.ast.ASTNode) {
        if (n.type == MarkdownElementTypes.INLINE_LINK) {
            val dest = n.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
            val textNode = n.findChildOfType(MarkdownElementTypes.LINK_TEXT)
            if (dest != null && textNode != null) {
                val url = dest.getUnescapedTextInNode(content).toString()
                val rawText = textNode.getUnescapedTextInNode(content).toString()
                val linkText = rawText.removeSurrounding("[", "]")
                if (linkText.isNotEmpty() && url.isNotEmpty()) {
                    items.add(ClickableItem.Link(linkText, url))
                }
            }
        } else if (n.type == MarkdownElementTypes.CODE) {
            val raw = n.getUnescapedTextInNode(content).toString()
            val codeText = raw.trim('`').trim()
            if (codeText.isNotEmpty() && LinkClassifier.isLikelyFilePath(codeText)) {
                items.add(ClickableItem.CodePath(codeText))
            }
        }
        n.children.forEach { walk(it) }
    }
    walk(node)
    return items
}
```

Add import at top of file:
```kotlin
import dev.leonardo.ocremotev2.domain.model.LinkClassifier
```

- [ ] **Step 2: Update paragraph component to use `extractClickableItems`**

In the same file, find the paragraph component (inside `markdownComponents(`). Replace:

```kotlin
                val links = remember(model.content, model.node) {
                    extractMarkdownLinks(model.content, model.node)
                }
```

With:

```kotlin
                val items = remember(model.content, model.node) {
                    extractClickableItems(model.content, model.node)
                }
```

- [ ] **Step 3: Update tap handler to iterate `items`**

In the same paragraph component, find the `detectTapGestures` block. Replace:

```kotlin
                            detectTapGestures { pos ->
                                val layout = layoutResult ?: return@detectTapGestures
                                val offset = layout.getOffsetForPosition(pos)
                                val text = annotated.text
                                var searchFrom = 0
                                for (link in links) {
                                    val idx = text.indexOf(link.text, searchFrom)
                                    if (idx >= 0 && offset >= idx && offset < idx + link.text.length) {
                                        uriHandler.openUri(link.url)
                                        return@detectTapGestures
                                    }
                                    if (idx >= 0) searchFrom = idx + link.text.length
                                }
                            }
```

With:

```kotlin
                            detectTapGestures { pos ->
                                val layout = layoutResult ?: return@detectTapGestures
                                val offset = layout.getOffsetForPosition(pos)
                                val text = annotated.text
                                var searchFrom = 0
                                for (item in items) {
                                    val idx = text.indexOf(item.text, searchFrom)
                                    if (idx >= 0 && offset >= idx && offset < idx + item.text.length) {
                                        when (item) {
                                            is ClickableItem.Link -> uriHandler.openUri(item.url)
                                            is ClickableItem.CodePath -> uriHandler.openUri(item.text)
                                        }
                                        return@detectTapGestures
                                    }
                                    if (idx >= 0) searchFrom = idx + item.text.length
                                }
                            }
```

Note: This task adds code path items to the list but they have NO visual styling yet (Task 3) and clicks on them go through `uriHandler.openUri(codeText)` which routes to the existing `handleLinkClick` — this already works because `LinkClassifier` handles bare relative/absolute paths.

- [ ] **Step 4: Compile check**

Run:
```powershell
.\gradlew :app:compileDevDebugKotlin --no-daemon 2>&1 | Select-String "BUILD|error:|^e:"
```
Expected: BUILD SUCCESSFUL, no errors

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt; git commit -m "refactor: extractClickableItems with inline code path detection"
```

---

### Task 3: AnnotatedString Post-Processing — Underline + Link Color for Code Paths

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt`

**Interfaces:**
- Consumes: `ClickableItem.CodePath` from Task 2
- Consumes: `linkColor` val already defined in `MarkdownContent` composable (line ~157-161)

- [ ] **Step 1: Add imports for `SpanStyle` and `TextDecoration`**

Add to the import block at top of `MarkdownContent.kt`:

```kotlin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
```

- [ ] **Step 2: Add post-processing after `buildMarkdownAnnotatedString`**

In the paragraph component, find this block:

```kotlin
                val annotated = model.content.buildMarkdownAnnotatedString(
                    textNode = model.node,
                    style = model.typography.text,
                    annotatorSettings = settings,
                )
                val items = remember(model.content, model.node) {
                    extractClickableItems(model.content, model.node)
                }
                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
```

Replace with:

```kotlin
                val rawAnnotated = model.content.buildMarkdownAnnotatedString(
                    textNode = model.node,
                    style = model.typography.text,
                    annotatorSettings = settings,
                )
                val items = remember(model.content, model.node) {
                    extractClickableItems(model.content, model.node)
                }
                val codePaths = items.filterIsInstance<ClickableItem.CodePath>()
                val annotated = if (codePaths.isEmpty()) rawAnnotated else {
                    buildAnnotatedString {
                        append(rawAnnotated.text)
                        rawAnnotated.spanStyles.forEach { range ->
                            addStyle(range.item, range.start, range.end)
                        }
                        var searchFrom = 0
                        for (cp in codePaths) {
                            val idx = rawAnnotated.text.indexOf(cp.text, searchFrom)
                            if (idx >= 0) {
                                addStyle(
                                    SpanStyle(
                                        textDecoration = TextDecoration.Underline,
                                        color = linkColor,
                                    ),
                                    idx, idx + cp.text.length,
                                )
                                searchFrom = idx + cp.text.length
                            }
                        }
                    }
                }
                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
```

- [ ] **Step 3: Compile check**

Run:
```powershell
.\gradlew :app:compileDevDebugKotlin --no-daemon 2>&1 | Select-String "BUILD|error:|^e:"
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Build APK + install to emulator**

Run:
```powershell
.\gradlew :app:assembleDevDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL"
```
Expected: BUILD SUCCESSFUL

Install:
```powershell
adb install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
```

- [ ] **Step 5: Visual verification**

On emulator: open a chat, trigger an AI reply containing `` `src/Main.kt` `` or similar path in inline code. Verify:
- Path-like inline code has underline + link color (in addition to existing code background)
- Non-path inline code (`` `val x = 1` ``) has NO underline, looks unchanged

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt; git commit -m "feat: underline + link color for path-like inline code spans"
```

---

### Task 4: Build, Test, Verify End-to-End Click Behavior

**Files:**
- No new file changes — this task verifies Tasks 1-3 work together

**Note:** Tap handler click routing for `ClickableItem.CodePath` was already added in Task 2 Step 3 (`uriHandler.openUri(item.text)`). This task verifies it works end-to-end.

- [ ] **Step 1: Run all unit tests**

Run:
```powershell
.\gradlew :app:testDevDebugUnitTest --no-daemon 2>&1 | Select-String "FAIL|PASS|BUILD|Tests"
```
Expected: All tests PASS, BUILD SUCCESSFUL

- [ ] **Step 2: Build + install debug APK**

Run:
```powershell
.\gradlew :app:assembleDevDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL"
```
Then install:
```powershell
adb install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
```

- [ ] **Step 3: End-to-end verification on emulator**

Test scenarios (trigger AI replies containing these patterns or find existing messages):

| # | Inline code | Expected behavior |
|---|-------------|-------------------|
| 1 | `` `src/Main.kt` `` (existing file) | Tap → opens FileViewer |
| 2 | `` `nonexistent.kt` `` (missing file) | Tap → Snackbar "File not found" |
| 3 | `` `docs/specs/` `` (directory) | Tap → opens Workspace tree |
| 4 | `` `val x = 1` `` (non-path code) | No underline, not clickable |
| 5 | `` `[text](url)` `` (markdown link, existing) | Still works as before |
| 6 | `` `build.gradle` `` (filename, no path separator) | Underline visible, tap → FileViewer or Snackbar |

- [ ] **Step 4: Commit if any fixes were needed**

If fixes were made during verification, commit them. Otherwise skip.

---

## Self-Review Notes

- **Spec coverage**: All 4 design components covered (isLikelyFilePath ✓, AST extraction ✓, AnnotatedString post-processing ✓, tap handler ✓)
- **Type consistency**: `ClickableItem` sealed interface used consistently across tasks; `isLikelyFilePath` defined in Task 1, consumed in Task 2
- **No placeholders**: All code blocks are complete implementations
