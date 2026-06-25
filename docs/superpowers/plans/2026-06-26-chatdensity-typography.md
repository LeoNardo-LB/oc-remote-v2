# ChatDensity Typography & Spacing Token System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace scattered hardcoded typography/spacing values with a unified ChatDensity token system, swap dev.snipme:highlights for compose-highlight (Highlight.js via WebView), and fix the h4>h3 heading inversion bug.

**Architecture:** A single `ChatDensity` enum (Normal/Compact) drives three immutable token data classes (Typography/Spacing/Bubble) via `LocalChatDensity` CompositionLocal. compose-highlight's `SyntaxHighlightedCode` replaces the hand-rolled OcCodeBlock, with `CodeBlockStyle` reading from the same tokens. Heading sizes use a +N rule (h1=body+4 … h6=body+0) computed dynamically from the body font size.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose, Material 3, Mikepenz markdown-renderer 0.43.0, compose-highlight 0.31.0 (Highlight.js), Hilt/KSP, JDK 21

## Global Constraints

- **JDK 21** required (`jvmToolchain(21)` in build.gradle.kts)
- **compileSdk 37**, minSdk 26, targetSdk 35
- **Gradle proxy** `127.0.0.1:7897` hardcoded in gradle.properties — fails if proxy unreachable
- **Product flavors**: always specify `DevDebug` or `DevRelease` in gradle tasks
- **compileDevDebugKotlin timeout**: 120s; **assembleDevRelease**: 300s; **unit tests**: 180s
- **ChatScreen.kt editing**: Read before Edit, compile after each edit, commit after each compilation. On failure: `git checkout -- <file>`, re-read, retry
- **Material 3 First**: Use `MaterialTheme.colorScheme`, `AlphaTokens`, `SpacingTokens`, `ShapeTokens`
- **Path handling**: Always use `PathUtils` for cross-platform remote paths
- **PowerShell encoding**: Never use `Out-File`/`Set-Content` (BOM issue); use Write/Edit tools

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `ui/theme/ChatDensity.kt` | ChatDensity enum, token data classes, Normal/Compact presets, LocalChatDensity |
| `src/main/assets/highlightjs/github-dark.css` | Highlight.js GitHub Dark theme CSS |
| `src/main/assets/highlightjs/github-light.css` | Highlight.js GitHub Light theme CSS |

### Modified Files
| File | Change |
|------|--------|
| `app/build.gradle.kts` | +compose-highlight:0.31.0, -dev.snipme:highlights |
| `ui/theme/Theme.kt` | Wrap AppTheme content in HighlightThemeProvider |
| `markdown/MarkdownContent.kt` | Headings from +N rule, table=code size, spacing from token, h1 divider, remove wordWrap/highlights imports |
| `markdown/OcCodeBlock.kt` | Rewrite: SyntaxHighlightedCode + CodeBlockStyle from token, remove Highlights import |
| `markdown/MarkdownTable.kt` | Cell padding from ChatSpacingTokens |
| `components/MessageCard.kt` | Bubble padding from ChatBubbleTokens, compact→density |
| `components/AssistantTurnBubble.kt` | compact→density |
| `chat/util/ChatCompositionLocals.kt` | Remove 3 old Locals, add LocalChatDensity |
| `chat/ChatScreen.kt` | Provides LocalChatDensity, messageSpacing from density |
| `chat/util/ChatModifiers.kt` | Remove wordWrap modifier code |
| `screens/settings/SettingsScreen.kt` | Merge font size + compact → single "对话字体" radio |
| `screens/settings/ChatFontSizePickerDialog.kt` | Replace with ChatDensityPickerDialog |

---

## Task 1: ChatDensity Token System

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensity.kt`
- Test: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensityTest.kt`

**Interfaces:**
- Produces: `ChatDensity` enum, `ChatTypographyTokens`, `ChatSpacingTokens`, `ChatBubbleTokens`, `LocalChatDensity`, `ChatDensity.Normal.typography`, `ChatDensity.Compact.typography`, etc.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensityTest.kt
package dev.leonardo.ocremotev2.ui.theme

import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals

class ChatDensityTest {

    @Test
    fun `Normal body font size is 14sp`() {
        assertEquals(14.sp, ChatDensity.Normal.typography.bodyFontSize)
    }

    @Test
    fun `Normal body line height is 22sp`() {
        assertEquals(22.sp, ChatDensity.Normal.typography.bodyLineHeight)
    }

    @Test
    fun `Compact body font size is 13sp`() {
        assertEquals(13.sp, ChatDensity.Compact.typography.bodyFontSize)
    }

    @Test
    fun `Normal h1 is body plus 4`() {
        assertEquals(18.sp, ChatDensity.Normal.typography.h1.fontSize)
    }

    @Test
    fun `Normal h6 equals body size`() {
        assertEquals(ChatDensity.Normal.typography.bodyFontSize,
            ChatDensity.Normal.typography.h6.fontSize)
    }

    @Test
    fun `Compact h1 is body plus 4`() {
        assertEquals(17.sp, ChatDensity.Compact.typography.h1.fontSize)
    }

    @Test
    fun `Normal code equals table font size`() {
        assertEquals(
            ChatDensity.Normal.typography.codeFontSize,
            ChatDensity.Normal.typography.tableFontSize
        )
    }

    @Test
    fun `Normal table cell equals code block spacing`() {
        assertEquals(
            ChatDensity.Normal.spacing.tableCell,
            ChatDensity.Normal.spacing.codeBlock
        )
    }

    @Test
    fun `Headings are strictly descending in Normal`() {
        val t = ChatDensity.Normal.typography
        assert(t.h1.fontSize > t.h2.fontSize)
        assert(t.h2.fontSize > t.h3.fontSize)
        assert(t.h3.fontSize > t.h4.fontSize)
        assert(t.h4.fontSize > t.h5.fontSize)
        assert(t.h5.fontSize >= t.h6.fontSize)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*.ChatDensityTest" --rerun`
Expected: FAIL — `ChatDensity` not found

- [ ] **Step 3: Create ChatDensity.kt**

```kotlin
// app/src/main/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensity.kt
package dev.leonardo.ocremotev2.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════════════
//  Enums & Data Classes
// ════════════════════════════════════════════════════════════════

enum class ChatDensity {
    Normal,
    Compact;
}

@Immutable
data class HeadingStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val alpha: Float = 1f,
)

@Immutable
data class ChatTypographyTokens(
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
    val codeFontSize: TextUnit,
    val codeLineHeight: TextUnit,
    val tableFontSize: TextUnit,
    val h1: HeadingStyle,
    val h2: HeadingStyle,
    val h3: HeadingStyle,
    val h4: HeadingStyle,
    val h5: HeadingStyle,
    val h6: HeadingStyle,
)

@Immutable
data class ChatSpacingTokens(
    val block: Dp,
    val listItemBottom: Dp,
    val listIndent: Dp,
    val tableCell: Dp,
    val codeBlock: Dp,
    val blockQuoteHorizontal: Dp,
)

@Immutable
data class ChatBubbleTokens(
    val paddingH: Dp,
    val paddingV: Dp,
    val itemSpacing: Dp,
)

// ════════════════════════════════════════════════════════════════
//  Preset Factory
// ════════════════════════════════════════════════════════════════

private fun typography(
    bodySize: Float,
    bodyLH: Float,
    codeSize: Float,
    codeLH: Float,
): ChatTypographyTokens {
    val body = bodySize.sp
    val bodyLine = bodyLH.sp
    return ChatTypographyTokens(
        bodyFontSize = body,
        bodyLineHeight = bodyLine,
        codeFontSize = codeSize.sp,
        codeLineHeight = codeLH.sp,
        tableFontSize = codeSize.sp,  // table = code
        h1 = HeadingStyle((bodySize + 4).sp, (bodyLH + 4).sp, FontWeight.Black),
        h2 = HeadingStyle((bodySize + 3).sp, (bodyLH + 3).sp, FontWeight.Bold),
        h3 = HeadingStyle((bodySize + 2).sp, (bodyLH + 2).sp, FontWeight.SemiBold),
        h4 = HeadingStyle((bodySize + 1).sp, (bodyLH + 1).sp, FontWeight.SemiBold),
        h5 = HeadingStyle(body, bodyLine, FontWeight.SemiBold, alpha = 1f),
        h6 = HeadingStyle(body, bodyLine, FontWeight.Medium, alpha = AlphaTokens.HIGH),
    )
}

private fun spacing(
    block: Float,
    indent: Float,
    cell: Float,
    quoteH: Float,
): ChatSpacingTokens = ChatSpacingTokens(
    block = block.dp,
    listItemBottom = 2.dp,
    listIndent = indent.dp,
    tableCell = cell.dp,
    codeBlock = cell.dp,    // codeBlock = tableCell (unified)
    blockQuoteHorizontal = quoteH.dp,
)

// ════════════════════════════════════════════════════════════════
//  Presets
// ════════════════════════════════════════════════════════════════

val ChatDensity.typography: ChatTypographyTokens
    get() = when (this) {
        ChatDensity.Normal  -> typography(14f, 22f, 13f, 20f)
        ChatDensity.Compact -> typography(13f, 18f, 12f, 18f)
    }

val ChatDensity.spacing: ChatSpacingTokens
    get() = when (this) {
        ChatDensity.Normal  -> spacing(block = 4f, indent = 16f, cell = 6f, quoteH = 16f)
        ChatDensity.Compact -> spacing(block = 2f, indent = 12f, cell = 4f, quoteH = 12f)
    }

val ChatDensity.bubble: ChatBubbleTokens
    get() = when (this) {
        ChatDensity.Normal  -> ChatBubbleTokens(16.dp, 14.dp, 10.dp)
        ChatDensity.Compact -> ChatBubbleTokens(10.dp, 8.dp, 4.dp)
    }

// ════════════════════════════════════════════════════════════════
//  CompositionLocal
// ════════════════════════════════════════════════════════════════

val LocalChatDensity = compositionLocalOf { ChatDensity.Normal }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*.ChatDensityTest" --rerun`
Expected: PASS (9 tests)

- [ ] **Step 5: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensity.kt \
        app/src/test/kotlin/dev/leonardo/ocremotev2/ui/theme/ChatDensityTest.kt
git commit -m "feat: add ChatDensity token system with Normal/Compact presets"
```

---

## Task 2: Dependency Swap — compose-highlight in, highlights out

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `dev.hossain:compose-highlight:0.31.0` on classpath; `dev.snipme:highlights` removed

- [ ] **Step 1: Read current build.gradle.kts dependencies**

Run: Read `app/build.gradle.kts`, find the `dependencies` block. Locate the `dev.snipme` line and the markdown renderer lines.

- [ ] **Step 2: Add compose-highlight, remove highlights**

In `app/build.gradle.kts`, within the `dependencies` block:

Replace:
```kotlin
implementation("dev.snipme:highlights:<version>")
```

With:
```kotlin
implementation("dev.hossain:compose-highlight:0.31.0")
```

If `dev.snipme:highlights` appears as a transitive dependency of mikepenz (check for `dev.snipme` in the codebase), keep the line but change it to compose-highlight. If no direct reference to `Highlights` or `Highlights.Builder` remains after Task 4, this is safe.

**Note:** If `dev.snipme:highlights` is pulled transitively by mikepenz-markdown-renderer-code, add an exclusion:
```kotlin
implementation("com.mikepenz:multiplatform-markdown-renderer-code:<version>") {
    exclude(group = "dev.snipme")
}
```

- [ ] **Step 3: Verify Gradle can resolve the new dependency**

Run: `.\gradlew :app:dependencies --configuration devReleaseRuntimeClasspath | findstr "compose-highlight"`
Expected: Shows `dev.hossain:compose-highlight:0.31.0`

- [ ] **Step 4: Compile check (will fail on OcCodeBlock — expected)**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: FAIL on OcCodeBlock.kt (still references `Highlights.Builder`). This is expected — we fix it in Task 4.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: swap dev.snipme:highlights for compose-highlight:0.31.0"
```

---

## Task 2.5: compose-highlight Compatibility Smoke Test

**Files:**
- Create (temporary): `app/src/main/kotlin/dev/leonardo/ocremotev2/SmokeTest.kt` (delete after passing)

**Rationale:** compose-highlight 0.31.0 was released 2026-04. Kotlin 2.3.21 + Compose BOM 2026.05.01 compatibility has never been verified. This 5-minute smoke test prevents discovering incompatibility after deep integration.

- [ ] **Step 1: Create a minimal SyntaxHighlightedCode usage**

Create a temporary composable in any existing file (e.g., in Theme.kt or a new file):
```kotlin
@Composable
fun SmokeTest() {
    dev.hossain.highlight.ui.SyntaxHighlightedCode(
        code = "fun hello() = \"world\"",
        language = "kotlin",
    )
}
```

- [ ] **Step 2: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL. If FAIL — compose-highlight is incompatible with Kotlin 2.3.21 / Compose BOM 2026.05.01. STOP and report to user before proceeding.

- [ ] **Step 3: Delete smoke test**

Remove the temporary SmokeTest code.

- [ ] **Step 4: Commit (if any changes remain)**

No commit needed if smoke test was cleanly removed.

---

## Task 3: GitHub Highlight.js Theme Assets

**Files:**
- Create: `app/src/main/assets/highlightjs/github-dark.css`
- Create: `app/src/main/assets/highlightjs/github-light.css`

**Interfaces:**
- Produces: CSS files loadable via `HighlightTheme.fromAsset(context, "highlightjs/github-dark.css", "github-dark")`

- [ ] **Step 1: Download GitHub Dark CSS**

Download from Highlight.js repository:
```bash
curl -o app/src/main/assets/highlightjs/github-dark.css \
  "https://raw.githubusercontent.com/highlightjs/highlight.js/main/src/styles/github-dark.css"
```

If curl fails (proxy), manually create the file. The CSS content is the standard Highlight.js github-dark theme (~2KB). It starts with `pre code.hljs { ... }` and defines `.hljs-keyword`, `.hljs-string`, etc.

- [ ] **Step 2: Download GitHub Light CSS**

```bash
curl -o app/src/main/assets/highlightjs/github-light.css \
  "https://raw.githubusercontent.com/highlightjs/highlight.js/main/src/styles/github-light.css"
```

- [ ] **Step 3: Strip background from both CSS files**

compose-highlight's internal `Surface` reads `.hljs { background: ... }` from the CSS. To let the outer Material `Surface` control the background color, we must remove this rule.

In **both** `github-dark.css` and `github-light.css`, find the `.hljs` block and set `background: transparent;`:

```css
/* BEFORE (github-dark.css): */
.hljs { color: #c9d1d9; background: #0d1117; }

/* AFTER: */
.hljs { color: #c9d1d9; background: transparent; }
```

```css
/* BEFORE (github-light.css): */
.hljs { color: #24292e; background: #fff; }

/* AFTER: */
.hljs { color: #24292e; background: transparent; }
```

This preserves all syntax highlighting colors (keyword, string, etc.) while letting Material `colorScheme` control the block background.

- [ ] **Step 4: Verify both files exist and have content**

Run: `dir app\src\main\assets\highlightjs\`
Expected: Two files, each > 500 bytes

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/highlightjs/
git commit -m "assets: add Highlight.js GitHub Dark/Light theme CSS"
```

---

## Task 4: Delete OcCodeBlock.kt — Inline to MarkdownContent

**Files:**
- Delete: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/OcCodeBlock.kt`

**Rationale:** compose-highlight's `SyntaxHighlightedCode` replaces OcCodeBlock entirely. AST extraction logic moves into MarkdownContent.kt as a private utility function. No need for a separate wrapper component.

- [ ] **Step 1: Extract AST helper before deleting**

Read `OcCodeBlock.kt` lines 63-77 (the codeText extraction logic) and lines with language extraction. Save this logic — it will be moved to MarkdownContent.kt in Task 6.

The key extraction function pattern:
```kotlin
private fun extractCodeAndLanguage(
    content: String,
    node: ASTNode,
    isFence: Boolean,
): Pair<String, String> {
    val codeText = if (isFence && node.children.size >= 3) {
        val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
        val start = node.children[2].startOffset
        val minCount = if (language != null && node.children.size > 3) 3 else 2
        val end = node.children[(node.children.size - 2).coerceAtLeast(minCount)].endOffset
        content.subSequence(start, end).toString().replaceIndent()
    } else if (!isFence && node.children.isNotEmpty()) {
        val start = node.children[0].startOffset
        val end = node.children[node.children.size - 1].endOffset
        content.subSequence(start, end).toString().replaceIndent()
    } else {
        content
    }
    val lang = if (isFence) {
        node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getText(content)?.trim()?.lowercase() ?: ""
    } else ""
    return codeText to lang
}
```

- [ ] **Step 2: Delete OcCodeBlock.kt**

```bash
rm app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/OcCodeBlock.kt
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: delete OcCodeBlock.kt — logic inlined to MarkdownContent (Task 6)"
```

**Note:** Compilation will fail until Task 6 wires up the inline call. This is expected.

---

## Task 5: HighlightThemeProvider in AppTheme

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/theme/Theme.kt`

**Interfaces:**
- Produces: `HighlightThemeProvider` wrapping all app content; `LocalHighlightTheme` available app-wide

- [ ] **Step 1: Read Theme.kt — find the AppTheme composable**

Read the file, locate the `AppTheme` composable function and its `content: @Composable () -> Unit` parameter.

- [ ] **Step 2: Add HighlightThemeProvider import**

Add at the top of Theme.kt:
```kotlin
import dev.hossain.highlight.engine.HighlightTheme
import dev.hossain.highlight.ui.HighlightThemeProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
```

- [ ] **Step 3: Wrap content in HighlightThemeProvider**

Inside `AppTheme`, wrap the `content()` call:

```kotlin
// Inside AppTheme, after MaterialTheme { ... }:
val context = LocalContext.current
val lightTheme = remember { HighlightTheme.fromAsset(context, "highlightjs/github-light.css", "github-light") }
val darkTheme = remember { HighlightTheme.fromAsset(context, "highlightjs/github-dark.css", "github-dark") }

HighlightThemeProvider(
    lightHighlightTheme = lightTheme,
    darkHighlightTheme = darkTheme,
) {
    content()
}
```

**Placement:** This should be *inside* the `MaterialTheme` block but *outside* the `content()` call, so that Material's `isSystemInDarkTheme()` context is available.

- [ ] **Step 4: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL (or errors from Task 4's incomplete wiring — acceptable if only in MarkdownContent.kt)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/theme/Theme.kt
git commit -m "feat: add HighlightThemeProvider with GitHub themes to AppTheme"
```

---

## Task 6: Rewrite MarkdownContent.kt — Headings, Tokens, h1 Divider

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt`

**Interfaces:**
- Consumes: `LocalChatDensity` (Task 1), `OcCodeBlock` (Task 4 new signature)
- Produces: `MarkdownContent(markdown, textColor, isUser, customFontSize)` — but `customFontSize` parameter deprecated

- [ ] **Step 1: Read the full current MarkdownContent.kt**

Read the entire file to understand current structure before editing. Pay attention to:
- `normalizeMarkdown` function
- `MarkdownContent` composable signature
- Typography block (h1-h6)
- Components block (codeBlock, codeFence, table)
- Dimens block
- Padding block
- Final `Markdown(...)` call

- [ ] **Step 2: Replace imports**

Remove:
```kotlin
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalChatFontSize
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalCompactMessages
import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalCodeWordWrap
import dev.snipme.highlights.Highlights
```

Add:
```kotlin
import dev.leonardo.ocremotev2.ui.theme.ChatDensity
import dev.leonardo.ocremotev2.ui.theme.LocalChatDensity
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
```

- [ ] **Step 3: Replace typography block**

Replace the entire `val typography = markdownTypography(...)` block with:

```kotlin
val density = LocalChatDensity.current
val tokens = density.typography
val spacing = density.spacing

val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
    color = textColor,
    fontSize = tokens.bodyFontSize,
    lineHeight = tokens.bodyLineHeight,
)

val typography = markdownTypography(
    h1 = MaterialTheme.typography.titleLarge.copy(
        color = textColor,
        fontSize = tokens.h1.fontSize,
        lineHeight = tokens.h1.lineHeight,
        fontWeight = tokens.h1.fontWeight,
    ),
    h2 = MaterialTheme.typography.titleLarge.copy(
        color = textColor,
        fontSize = tokens.h2.fontSize,
        lineHeight = tokens.h2.lineHeight,
        fontWeight = tokens.h2.fontWeight,
    ),
    h3 = MaterialTheme.typography.titleMedium.copy(
        color = textColor,
        fontSize = tokens.h3.fontSize,
        lineHeight = tokens.h3.lineHeight,
        fontWeight = tokens.h3.fontWeight,
    ),
    h4 = MaterialTheme.typography.titleSmall.copy(
        color = textColor,
        fontSize = tokens.h4.fontSize,
        lineHeight = tokens.h4.lineHeight,
        fontWeight = tokens.h4.fontWeight,
    ),
    h5 = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontSize = tokens.h5.fontSize,
        lineHeight = tokens.h5.lineHeight,
        fontWeight = tokens.h5.fontWeight,
    ),
    h6 = MaterialTheme.typography.bodyMedium.copy(
        color = textColor.copy(alpha = tokens.h6.alpha),
        fontSize = tokens.h6.fontSize,
        lineHeight = tokens.h6.lineHeight,
        fontWeight = tokens.h6.fontWeight,
    ),
    text = bodyStyle,
    code = CodeTypography.copy(
        color = codeBlockFg,
        fontSize = tokens.codeFontSize,
        lineHeight = tokens.codeLineHeight,
    ),
    inlineCode = CodeTypography.copy(
        color = inlineCodeFg,
        fontSize = tokens.codeFontSize,
        fontWeight = FontWeight.Medium,
    ),
    quote = bodyStyle.copy(
        color = textColor.copy(alpha = AlphaTokens.MEDIUM),
        fontStyle = FontStyle.Italic,
    ),
    paragraph = bodyStyle,
    ordered = bodyStyle,
    bullet = bodyStyle,
    list = bodyStyle,
    table = bodyStyle.copy(fontSize = tokens.tableFontSize, lineHeight = tokens.codeLineHeight),
    textLink = TextLinkStyles(
        style = bodyStyle.copy(color = linkColor, fontWeight = FontWeight.Medium).toSpanStyle()
    ),
)
```

- [ ] **Step 4: Replace components block — inline SyntaxHighlightedCode**

Replace the `markdownComponents(...)` block with:

```kotlin
val context = LocalContext.current
val components = remember(density, isUser) {
    markdownComponents(
        codeBlock = { model ->
            val (code, lang) = extractCodeAndLanguage(model.content, model.node, isFence = false)
            CodeBlockRenderer(
                code = code,
                language = lang,
                isUser = isUser,
                isAmoled = isAmoled,
                density = density,
                backgroundColor = codeBlockBg,
                codeBlockFg = codeBlockFg,
                context = context,
            )
        },
        codeFence = { model ->
            val (code, lang) = extractCodeAndLanguage(model.content, model.node, isFence = true)
            CodeBlockRenderer(
                code = code,
                language = lang,
                isUser = isUser,
                isAmoled = isAmoled,
                density = density,
                backgroundColor = codeBlockBg,
                codeBlockFg = codeBlockFg,
                context = context,
            )
        },
        heading1 = { model ->
            Column {
                MarkdownHeader(model.content, model.node, style = typography.h1)
                HorizontalDivider(
                    modifier = Modifier.padding(top = spacing.block),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.FAINT),
                )
            }
        },
        table = { model ->
            SimpleMarkdownTable(model.content, model.node, model.typography.table)
        },
    )
}
```

Then add the `CodeBlockRenderer` and `extractCodeAndLanguage` private composables/functions after the MarkdownContent function:

```kotlin
/** Extracts pure code text and language from a Mikepenz AST node. */
private fun extractCodeAndLanguage(
    content: String,
    node: ASTNode,
    isFence: Boolean,
): Pair<String, String> {
    val codeText = if (isFence && node.children.size >= 3) {
        val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
        val start = node.children[2].startOffset
        val minCount = if (language != null && node.children.size > 3) 3 else 2
        val end = node.children[(node.children.size - 2).coerceAtLeast(minCount)].endOffset
        content.subSequence(start, end).toString().replaceIndent()
    } else if (!isFence && node.children.isNotEmpty()) {
        val start = node.children[0].startOffset
        val end = node.children[node.children.size - 1].endOffset
        content.subSequence(start, end).toString().replaceIndent()
    } else {
        content
    }
    val lang = if (isFence) {
        node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getText(content)?.trim()?.lowercase() ?: ""
    } else ""
    return codeText to lang
}

/**
 * Renders a code block.
 *
 * - User message bubbles: plain monospace text (no syntax highlighting).
 *   GitHub theme colors clash with primary background — plain text ensures readability.
 * - Assistant messages: compose-highlight (Highlight.js via WebView).
 *   Outer Surface controls Material background; CSS has .hljs{background:transparent}.
 * - WebView unavailable: compose-highlight shows its own plaintext placeholder via onError.
 */
@Composable
private fun CodeBlockRenderer(
    code: String,
    language: String,
    isUser: Boolean,
    isAmoled: Boolean,
    density: ChatDensity,
    backgroundColor: Color,
    codeBlockFg: Color,
    context: android.content.Context,
) {
    val spacing = density.spacing
    val tokens = density.typography

    Surface(
        shape = ShapeTokens.extraSmall,
        color = backgroundColor,
        tonalElevation = 0.dp,
    ) {
        if (isUser) {
            // User messages: plain text, no WebView/highlighting
            Text(
                text = code,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = tokens.codeFontSize,
                    lineHeight = tokens.codeLineHeight,
                    color = codeBlockFg,
                ),
                modifier = Modifier.padding(all = spacing.codeBlock),
            )
        } else {
            // Assistant messages: compose-highlight with GitHub syntax colors
            SyntaxHighlightedCode(
                code = code,
                language = language.ifBlank { "plaintext" },
                style = CodeBlockStyle(
                    shape = RectangleShape,  // outer Surface controls shape
                    padding = PaddingValues(spacing.codeBlock),
                    textStyle = TextStyle(
                        fontSize = tokens.codeFontSize,
                        lineHeight = tokens.codeLineHeight,
                        fontFamily = FontFamily.Monospace,
                    ),
                    copyButtonSize = 24.dp,
                ),
                onCopyClick = {
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}
```

Also add these imports to MarkdownContent.kt:
```kotlin
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import dev.hossain.highlight.ui.CodeBlockStyle
import dev.hossain.highlight.ui.SyntaxHighlightedCode
```

And remove the old import:
```kotlin
import dev.snipme.highlights.Highlights  // DELETE
```

**MarkdownHeader fallback note:** If `MarkdownHeader(content, node, style=...)` fails to compile (the `style` parameter may not exist in Mikepenz 0.43.0's public API), replace the heading1 component with:
```kotlin
heading1 = { model ->
    Column {
        Text(
            text = model.node.getUnescapedTextInNode(model.content),
            style = typography.h1,
        )
        HorizontalDivider(...)
    }
}
```
Verify with: `.\gradlew :app:compileDevDebugKotlin` — if MarkdownHeader import fails, use the Text fallback.

- [ ] **Step 5: Replace dimens + padding + Markdown call**

Remove the `dimens` and `padding` blocks entirely (dimens are now in tokens, padding is handled by OcCodeBlock's CodeBlockStyle).

Replace the final `Markdown(markdownState=..., dimens=..., padding=...)` call with:

```kotlin
val padding = markdownPadding(
    block = spacing.block,
    list = 0.dp,
    listItemBottom = spacing.listItemBottom,
)

Markdown(
    content = normalizedMarkdown,
    colors = colors,
    typography = typography,
    components = components,
    padding = padding,
    imageTransformer = Coil3ImageTransformerImpl,
    modifier = Modifier.fillMaxWidth(),
)
```

- [ ] **Step 6: Remove dead code**

Remove these now-unused blocks:
- `val fontSizeSetting = customFontSize ?: LocalChatFontSize.current` and the entire `when` block for fontSize
- `val wordWrap = LocalCodeWordWrap.current`
- `val highlightsBuilder = remember { Highlights.Builder() }`
- `val compact = LocalCompactMessages.current`
- `val screenWidthDp = ...` (no longer needed for dimens)
- The `customFontSize` parameter from the function signature (or deprecate it with `@Suppress("UNUSED_PARAMETER")`)
- `val markdownState = rememberMarkdownState(...)` — remove, use `content=` form
- The `DebugLog` line (`android.util.Log.e("TableDebug", ...)`)

- [ ] **Step 7: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

If FAIL on ChatScreen.kt (still provides old CompositionLocals), proceed — we fix that in Task 8.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownContent.kt
git commit -m "feat: MarkdownContent uses ChatDensity tokens, +N headings, h1 divider"
```

---

## Task 7: MarkdownTable Cell Padding from Token

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownTable.kt`

- [ ] **Step 1: Read MarkdownTable.kt, find the hardcoded `pad = 6.dp` value**

Search for `pad = ` or `padding` in the file.

- [ ] **Step 2: Replace with token**

Add import:
```kotlin
import dev.leonardo.ocremotev2.ui.theme.LocalChatDensity
```

Replace the hardcoded padding value (likely `val pad = 6.dp`) with:
```kotlin
val pad = LocalChatDensity.current.spacing.tableCell
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/markdown/MarkdownTable.kt
git commit -m "refactor: MarkdownTable cell padding from ChatDensity token"
```

---

## Task 8: Replace CompositionLocals — ChatCompositionLocals + ChatScreen + ChatModifiers

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ChatCompositionLocals.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ChatModifiers.kt`

**Interfaces:**
- Removes: `LocalChatFontSize`, `LocalCodeWordWrap`, `LocalCompactMessages`
- Adds: `LocalChatDensity` (already defined in ChatDensity.kt Task 1, now wired up)

- [ ] **Step 1: Read ChatCompositionLocals.kt**

Read the full file to see all three locals and their definitions.

- [ ] **Step 2: Remove only 3 target locals from ChatCompositionLocals.kt**

**WARNING:** This file contains 13 CompositionLocals. Only remove the 3 target ones. Do NOT replace the entire file — that would delete 8 unrelated locals (LocalCollapseTools, LocalExpandReasoning, LocalShowTurnDividers, LocalHapticFeedbackEnabled, LocalImageSaveRequest, LocalToolExpandedStates, LocalOnToggleToolExpanded, LocalToolCardResolver).

Read the file first, then delete ONLY these 3 lines:
```kotlin
val LocalChatFontSize = compositionLocalOf { "medium" }
val LocalCodeWordWrap = compositionLocalOf { true }
val LocalCompactMessages = compositionLocalOf { false }
```

Keep all other locals intact. Do not delete the file.

- [ ] **Step 3: Read ChatScreen.kt — find the CompositionLocalProvider**

Search for `LocalChatFontSize provides` and `LocalCompactMessages provides`.

- [ ] **Step 4: Replace provides in ChatScreen.kt**

Find the block:
```kotlin
LocalChatFontSize provides chatFontSize,
LocalCodeWordWrap provides codeWordWrap,
LocalCompactMessages provides compactMessages,
```

Replace with:
```kotlin
LocalChatDensity provides chatDensity,
```

Add the density derivation above the provider:
```kotlin
// Derive ChatDensity from legacy settings (will be simplified in Task 9)
val chatDensity = when {
    compactMessages -> ChatDensity.Compact
    chatFontSize == "small" -> ChatDensity.Compact
    else -> ChatDensity.Normal
}
```

Update the `messageSpacing` line (line ~989):
```kotlin
// Old: val messageSpacing = if (LocalCompactMessages.current) 2.dp else 8.dp
val messageSpacing = if (LocalChatDensity.current == ChatDensity.Compact) 2.dp else 8.dp
```

Remove imports for `LocalChatFontSize`, `LocalCodeWordWrap`, `LocalCompactMessages`. Add:
```kotlin
import dev.leonardo.ocremotev2.ui.theme.LocalChatDensity
import dev.leonardo.ocremotev2.ui.theme.ChatDensity
```

- [ ] **Step 5: Read ChatModifiers.kt — remove wordWrap code**

Read the file. Find the `LocalCodeWordWrap` reference.

- [ ] **Step 6: Remove wordWrap from ChatModifiers.kt**

If the modifier wraps code in horizontal scroll based on wordWrap, replace with always-horizontal-scroll:

```kotlin
// Old: return if (!LocalCodeWordWrap.current) { Modifier.horizontalScroll(...) } else Modifier
// New: always horizontal scroll (wordWrap setting removed)
return Modifier.horizontalScroll(rememberScrollState())
```

Remove the `LocalCodeWordWrap` import. If the entire file becomes trivial, consider inlining the modifier at call sites and deleting the file.

- [ ] **Step 7: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ChatCompositionLocals.kt \
        app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt \
        app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ChatModifiers.kt
git commit -m "refactor: replace 3 CompositionLocals with LocalChatDensity, remove wordWrap"
```

---

## Task 9: MessageCard + AssistantTurnBubble — Bubble Padding from Token

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/MessageCard.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/AssistantTurnBubble.kt`

- [ ] **Step 1: Read MessageCard.kt — find all `compact` references**

Search for `LocalCompactMessages` and `compact` in the file. There are 2 usage sites (user bubble + assistant bubble padding).

- [ ] **Step 2: Replace compact with density in MessageCard.kt**

Add import:
```kotlin
import dev.leonardo.ocremotev2.ui.theme.LocalChatDensity
import dev.leonardo.ocremotev2.ui.theme.ChatDensity
```

Replace each occurrence of:
```kotlin
val compact = LocalCompactMessages.current
```
With:
```kotlin
val compact = LocalChatDensity.current == ChatDensity.Compact
```

This is the minimal change — `compact` boolean variable still works everywhere it's referenced.

Remove the `LocalCompactMessages` import.

- [ ] **Step 3: Read AssistantTurnBubble.kt — find compact reference**

- [ ] **Step 4: Replace in AssistantTurnBubble.kt**

Same replacement:
```kotlin
// Old: val compact = LocalCompactMessages.current
val compact = LocalChatDensity.current == ChatDensity.Compact
```

Update imports accordingly.

- [ ] **Step 5: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/MessageCard.kt \
        app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/AssistantTurnBubble.kt
git commit -m "refactor: MessageCard and AssistantTurnBubble use LocalChatDensity"
```

---

## Task 10: Settings Page — Merge to Single "对话字体" Radio

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/settings/ChatFontSizePickerDialog.kt` (rename/replace)
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/data/local/DataStoreExtensions.kt` (or equivalent settings store)

- [ ] **Step 1: Read the current settings screen and picker dialog**

Read SettingsScreen.kt to find the font size and compact messages entries.
Read ChatFontSizePickerDialog.kt to understand its structure.

- [ ] **Step 2: Read the DataStore settings keys**

Find where `chatFontSize` and `compactMessages` are stored (likely DataStore<Preferences>).

- [ ] **Step 3: Add a new setting key for ChatDensity**

In the settings store, add:
```kotlin
val CHAT_DENSITY_KEY = stringPreferencesKey("chat_density")
// Values: "normal" (default), "compact"

// Migration: on first read, if chat_density not set, derive from old keys:
//   if (compactMessages || chatFontSize == "small") → "compact"
//   else → "normal"
```

- [ ] **Step 4: Replace SettingsScreen entries**

Remove the "字体大小" row and "紧凑消息" row. Replace with:

```kotlin
// Single "对话字体" entry
SettingsItem(
    icon = Icons.Default.FormatSize,
    title = stringResource(R.string.settings_chat_font),
    subtitle = when (chatDensity) {
        ChatDensity.Normal -> stringResource(R.string.settings_chat_font_normal)
        ChatDensity.Compact -> stringResource(R.string.settings_chat_font_compact)
    },
    onClick = { showChatDensityPicker = true },
)
```

- [ ] **Step 5: Replace picker dialog**

Replace `ChatFontSizePickerDialog` with `ChatDensityPickerDialog`:

```kotlin
@Composable
fun ChatDensityPickerDialog(
    current: ChatDensity,
    onSelect: (ChatDensity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_chat_font)) },
        text = {
            Column {
                ChatDensity.values().forEach { density ->
                    val label = when (density) {
                        ChatDensity.Normal -> stringResource(R.string.settings_chat_font_normal)
                        ChatDensity.Compact -> stringResource(R.string.settings_chat_font_compact)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(density) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = density == current,
                            onClick = { onSelect(density) },
                        )
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}
```

- [ ] **Step 6: Add string resources**

In `values/strings.xml` and `values-zh-rCN/strings.xml`:
```xml
<string name="settings_chat_font">对话字体</string>
<string name="settings_chat_font_normal">正常</string>
<string name="settings_chat_font_compact">小</string>
```

- [ ] **Step 7: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/settings/ \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: merge font size + compact into single 对话字体 setting"
```

- [ ] **Step 9: Update ChatViewModel / SettingsViewModel**

Read `ChatScreen.kt` around line 1159-1170 where it reads `viewModel.chatFontSize` and `viewModel.compactMessages`. These flows come from a ViewModel. 

Add a new flow `chatDensity` to the ViewModel that reads from the new `CHAT_DENSITY_KEY`. Remove (or deprecate) the old `chatFontSize` and `compactMessages` flows.

The provides block in ChatScreen should change from:
```kotlin
LocalChatFontSize provides chatFontSize,
LocalCodeWordWrap provides codeWordWrap,
LocalCompactMessages provides compactMessages,
```
to:
```kotlin
LocalChatDensity provides chatDensity,
```

- [ ] **Step 10: Write migration test**

Create `app/src/test/kotlin/dev/leonardo/ocremotev2/data/repository/SettingsMigrationTest.kt`:

```kotlin
package dev.leonardo.ocremotev2.data.repository

import dev.leonardo.ocremotev2.ui.theme.ChatDensity
import org.junit.Test
import kotlin.test.assertEquals

class SettingsMigrationTest {

    /** Migration rule: compact on → Compact, regardless of font size */
    @Test
    fun `compact on with medium font migrates to Compact`() {
        val result = migrateDensity(fontSize = "medium", compact = true)
        assertEquals(ChatDensity.Compact, result)
    }

    @Test
    fun `compact on with large font migrates to Compact`() {
        val result = migrateDensity(fontSize = "large", compact = true)
        assertEquals(ChatDensity.Compact, result)
    }

    /** small font without compact → Compact */
    @Test
    fun `small font without compact migrates to Compact`() {
        val result = migrateDensity(fontSize = "small", compact = false)
        assertEquals(ChatDensity.Compact, result)
    }

    /** medium/large without compact → Normal */
    @Test
    fun `medium font without compact migrates to Normal`() {
        val result = migrateDensity(fontSize = "medium", compact = false)
        assertEquals(ChatDensity.Normal, result)
    }

    @Test
    fun `large font without compact migrates to Normal`() {
        val result = migrateDensity(fontSize = "large", compact = false)
        assertEquals(ChatDensity.Normal, result)
    }

    /** First-time users (no setting) → Normal */
    @Test
    fun `null settings default to Normal`() {
        val result = migrateDensity(fontSize = null, compact = null)
        assertEquals(ChatDensity.Normal, result)
    }

    private fun migrateDensity(fontSize: String?, compact: Boolean?): ChatDensity {
        if (compact == true) return ChatDensity.Compact
        if (fontSize == "small") return ChatDensity.Compact
        return ChatDensity.Normal
    }
}
```

- [ ] **Step 11: Run migration tests**

Run: `.\gradlew :app:testDevDebugUnitTest --tests "*.SettingsMigrationTest" --rerun`
Expected: PASS (6 tests)

- [ ] **Step 12: Commit**

```bash
git add app/src/test/kotlin/dev/leonardo/ocremotev2/data/repository/SettingsMigrationTest.kt \
        app/src/main/kotlin/dev/leonardo/ocremotev2/  # ViewModel changes
git commit -m "feat: add density migration tests + ViewModel chatDensity flow"
```

---

## Task 11: Full Build + Install + Visual Verification

- [ ] **Step 1: Stop gradle daemons**

Run: `.\gradlew --stop`

- [ ] **Step 2: Full release build**

Run: `.\gradlew :app:assembleDevRelease`
Expected: BUILD SUCCESSFUL (timeout 300s)

- [ ] **Step 3: Install to emulator**

Run: `adb install -r app/build/outputs/apk/dev/release/app-dev-release.apk`
Expected: Success

- [ ] **Step 4: Launch and verify visually**

Run: `adb shell am force-stop dev.leonardo.ocremotev2.dev; adb shell am start -n dev.leonardo.ocremotev2.dev/dev.leonardo.ocremotev2.MainActivity`

Verify:
1. Open a chat with Markdown content
2. Check h1-h6 headings are strictly descending in size
3. Check h1 has a bottom divider line
4. Check code blocks have syntax highlighting (GitHub colors)
5. Check code blocks have language label (top-left) and copy button (top-right)
6. Check tables render with correct cell padding
7. Go to Settings → verify single "对话字体" option with 正常/小
8. Toggle to 小 → verify tighter spacing and smaller fonts
9. Toggle back to 正常 → verify comfortable spacing

- [ ] **Step 5: Run unit tests**

Run: `.\gradlew :app:testDevDebugUnitTest --rerun`
Expected: All tests pass (including new ChatDensityTest)

- [ ] **Step 6: Commit version + tag (optional)**

If ready for preview release:
```bash
# Bump version in version.properties
git add version.properties
git commit -m "chore: bump version for ChatDensity + compose-highlight"
```

---

## Self-Review Notes

**Spec coverage:**
- ✅ ChatDensity token system → Task 1
- ✅ compose-highlight integration (deps + Theme + OcCodeBlock) → Tasks 2-5
- ✅ Heading +N rule + h1 divider → Task 6
- ✅ Table = code font size → Task 6 (typography.table = tokens.tableFontSize)
- ✅ Spacing from tokens → Tasks 6-7
- ✅ Bubble padding from tokens → Task 9
- ✅ CompositionLocal cleanup → Task 8
- ✅ Settings consolidation → Task 10
- ✅ wordWrap removed → Task 8 (ChatModifiers) + Task 6 (MarkdownContent)

**Risk mitigations:**
- Task 4 changes OcCodeBlock signature → Task 6 updates the call site. If compiled independently, Task 4-5 will fail. Execute sequentially.
- Task 8 deletes CompositionLocals that Tasks 9-10 still reference. Execute sequentially.
- compose-highlight WebView lifecycle: HighlightThemeProvider handles disposal. No manual cleanup needed.
