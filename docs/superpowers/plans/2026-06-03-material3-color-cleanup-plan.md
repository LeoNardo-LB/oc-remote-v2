# Material 3 Color Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all hardcoded color values and AMOLED `Color.Black` branches, replacing them with Material 3 semantic tokens.

**Architecture:** Token replacement — simplify `if (isAmoled) Color.Black else X` to just `X`. Only `surface`/`surfaceContainerLowest`/`background` resolve to `Color.Black` in AMOLED; other tokens (secondaryContainer, primaryContainer, etc.) inherit Material 3 dark defaults. This produces a subtle shift from pure black to Material 3's intended dark tints — acceptable per user decision.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3

---

## Task Dependencies

```
Task 1 (ButtonTokens) → Task 2 (DialogButtons)   [sequential, compile fail between them]
Task 3 (AlphaTokens)                             [independent]
Task 4 (MarkdownContent)                         [independent]
Task 5 (RevertBanner/TerminalKeyboard)           [independent]
Task 6 (Chat Color.Black)                        [independent]
Task 7 (Terminal Color.Black)                    [independent]
Task 8 (Screens Color.Black)                     [independent]
Task 9 (Build & Release)                         [depends on all above]
```

Tasks 3-8 have no inter-dependencies and can execute in parallel. Tasks 1-2 must execute sequentially.

---

### Task 1: ButtonTokens Refactor

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/theme/ButtonTokens.kt`

- [ ] **Step 1: Refactor ButtonTokens.kt**

Replace entire file content:

```kotlin
package dev.minios.ocremote.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Centralized button style tokens.
 *
 * Usage:
 * ```kotlin
 * // Primary (Filled Button)
 * Button(colors = ButtonTokens.filledColors(), border = ButtonTokens.amoledBorder())
 *
 * // Secondary (FilledTonalButton — no custom colors needed)
 * FilledTonalButton() // use Material 3 defaults
 *
 * // Danger (FilledTonalButton with error tonal)
 * FilledTonalButton(colors = ButtonTokens.dangerColors(), border = ButtonTokens.amoledBorder())
 * ```
 */
object ButtonTokens {

    // ── Content Padding ──────────────────────────────────────────────

    /** Compact vertical padding for full-width stacked buttons (3+ in a Column). */
    val CompactPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

    /** Spacing between stacked full-width buttons in a Column. */
    const val StackSpacing = 4

    /** Spacing between inline buttons in a Row. */
    const val RowSpacing = 8

    // ── Filled Button Colors (Primary) ────────────────────────────────

    /**
     * Colors for [Button] (Filled) for Primary actions.
     *
     * - **Light / Dark**: Material 3 default (`primary`/`onPrimary`).
     * - **AMOLED**: Black container + primary content.
     */
    @Composable
    fun filledColors(): ButtonColors {
        val isAmoled = LocalAmoledMode.current
        return if (isAmoled) {
            ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    }

    // ── Danger FilledTonalButton Colors ───────────────────────────────

    /**
     * Colors for danger [FilledTonalButton] (delete / destructive).
     *
     * - **Light / Dark**: `errorContainer` / `onErrorContainer` (Material 3 tonal error).
     * - **AMOLED**: Black container + error content.
     */
    @Composable
    fun dangerColors(): ButtonColors {
        val isAmoled = LocalAmoledMode.current
        return if (isAmoled) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.Black,
                contentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    // ── AMOLED Border ────────────────────────────────────────────────

    /**
     * Border for buttons adapted to the current theme.
     *
     * - **AMOLED**: 1dp primary border with [AlphaTokens.HIGH] alpha.
     * - **Light / Dark**: `null` (no border).
     */
    @Composable
    fun amoledBorder(): BorderStroke? {
        val isAmoled = LocalAmoledMode.current
        return if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.HIGH))
        } else {
            null
        }
    }
}
```

Key changes:
- `tonalColors()` → `filledColors()`, uses `ButtonDefaults.buttonColors()` (Material 3 Filled Button default)
- `secondaryColors()` → **deleted** (Secondary uses FilledTonalButton defaults)
- `dangerColors()` → uses `errorContainer`/`onErrorContainer` instead of `error`/`onError`
- `tonalBorder()` → `amoledBorder()` (renamed for clarity)

- [ ] **Step 2: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: FAIL (DialogButtons.kt still references old method names)

---

### Task 2: DialogButtons Update

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/components/DialogButtons.kt`

- [ ] **Step 1: Update DialogButtons.kt**

Replace entire file content:

```kotlin
package dev.minios.ocremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.minios.ocremote.ui.theme.ButtonTokens

/**
 * Role of a button inside a dialog.
 *
 * - [Primary]:   Main action (confirm, save, create). Filled Button with primary color.
 * - [Secondary]: Cancel / dismiss. FilledTonalButton with Material 3 default colors.
 * - [Danger]:    Destructive action (delete, revert). FilledTonalButton with errorContainer color.
 */
enum class DialogButtonRole {
    Primary,
    Secondary,
    Danger,
}

/**
 * Unified dialog button row.
 *
 * Layout rules:
 * - 1 button: single Row, right-aligned
 * - 2 buttons: Row, horizontal, right-aligned
 * - 3+ buttons: Column, vertical, full-width
 *
 * @param buttons List of (label, role, onClick) triples.
 */
@Composable
fun DialogButtons(
    buttons: List<Triple<String, DialogButtonRole, () -> Unit>>,
) {
    if (buttons.size <= 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonTokens.RowSpacing.dp, Alignment.End),
        ) {
            buttons.forEach { (text, role, onClick) ->
                DialogActionButton(
                    text = text,
                    role = role,
                    onClick = onClick,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ButtonTokens.StackSpacing.dp),
        ) {
            buttons.forEach { (text, role, onClick) ->
                DialogActionButton(
                    text = text,
                    role = role,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    role: DialogButtonRole,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val contentPadding = if (compact) ButtonTokens.CompactPadding else ButtonDefaults.ContentPadding
    when (role) {
        DialogButtonRole.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier,
                colors = ButtonTokens.filledColors(),
                border = ButtonTokens.amoledBorder(),
                contentPadding = contentPadding,
            ) {
                Text(text)
            }
        }
        DialogButtonRole.Secondary -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                contentPadding = contentPadding,
            ) {
                Text(text)
            }
        }
        DialogButtonRole.Danger -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                colors = ButtonTokens.dangerColors(),
                border = ButtonTokens.amoledBorder(),
                contentPadding = contentPadding,
            ) {
                Text(text)
            }
        }
    }
}
```

Key changes:
- Primary: `FilledTonalButton` → `Button` (Filled), uses `filledColors()` + `amoledBorder()`
- Secondary: `FilledTonalButton`, no custom colors (Material 3 default)
- Danger: `FilledTonalButton`, uses `dangerColors()` + `amoledBorder()`
- Removed `import androidx.compose.ui.unit.dp` (unused after cleanup)

- [ ] **Step 2: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: buttons use Material 3 defaults — Primary=Filled, Secondary=Tonal, Danger=errorContainer"
```

---

### Task 3: AlphaTokens — Add DIFF_BG

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/theme/Alpha.kt`

- [ ] **Step 1: Add DIFF_BG token**

In `Alpha.kt`, add after `SELECTED`:

```kotlin
const val DIFF_BG = 0.1f
```

- [ ] **Step 2: Update DiffHelpers.kt**

**File:** `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/DiffHelpers.kt`

Change line 64-65 from:
```kotlin
DiffAdded.copy(alpha = 0.1f)
DiffRemoved.copy(alpha = 0.1f)
```
to:
```kotlin
DiffAdded.copy(alpha = AlphaTokens.DIFF_BG)
DiffRemoved.copy(alpha = AlphaTokens.DIFF_BG)
```

Ensure `import dev.minios.ocremote.ui.theme.AlphaTokens` is present.

- [ ] **Step 3: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: replace hardcoded 0.1f alpha with AlphaTokens.DIFF_BG"
```

---

### Task 4: MarkdownContent — Replace Hardcoded AMOLED Colors

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/markdown/MarkdownContent.kt`

- [ ] **Step 1: Replace line 119**

Change:
```kotlin
Color(0xFF2A2A36)  // AMOLED code block background
```
to:
```kotlin
MaterialTheme.colorScheme.surfaceContainerHighest
```

- [ ] **Step 2: Replace line 156**

Change:
```kotlin
Color(0xFF353540)  // AMOLED inline code background
```
to:
```kotlin
MaterialTheme.colorScheme.surfaceContainerHighest
```

Both are inside `if (isAmoled)` blocks — since AMOLED theme defines `surfaceContainerHighest = Color(0xFF2A2A36)`, the visual result is identical for code blocks and nearly identical for inline code.

- [ ] **Step 3: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: MarkdownContent AMOLED colors use surfaceContainerHighest token"
```

---

### Task 5: RevertBanner & TerminalKeyboardOverlay — Replace Hardcoded Colors

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/components/RevertBanner.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/terminal/TerminalKeyboardOverlay.kt`

- [ ] **Step 1: RevertBanner.kt line 45**

Change:
```kotlin
Color(0xFF2A3E44)
```
to:
```kotlin
MaterialTheme.colorScheme.tertiaryContainer
```

- [ ] **Step 2: TerminalKeyboardOverlay.kt line 138**

Change:
```kotlin
Color(0xFF80CBC4)
```
to:
```kotlin
MaterialTheme.colorScheme.tertiary
```

- [ ] **Step 3: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: RevertBanner and TerminalKeyboardOverlay use Material 3 semantic colors"
```

---

### Task 6: AMOLED Color.Black Cleanup — Chat Components

**Files:**
- Modify: `ChatColors.kt`
- Modify: `ReasoningBlock.kt`
- Modify: `FileCard.kt`
- Modify: `AssistantTurnBubble.kt`
- Modify: `MessageCard.kt`
- Modify: `ChatMessageList.kt`
- Modify: `ChatTopBar.kt`
- Modify: `ErrorPayloadContent.kt`
- Modify: `QuestionCard.kt`
- Modify: `PermissionRequestCard.kt`
- Modify: `ImagePreviewDialog.kt`
- Modify: `ChatInputBar.kt`
- Modify: `EditToolCard.kt`
- Modify: `TodoListCard.kt`
- Modify: `PatchCard.kt`
- Modify: `ToolCardRenderer.kt`
- Modify: `WriteToolCard.kt`
- Modify: `ReadToolCard.kt`
- Modify: `TaskToolCard.kt`
- Modify: `SearchToolCard.kt`
- Modify: `BashToolCard.kt`

All under: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/`

- [ ] **Step 1: Simplify each `if (isAmoled) Color.Black else X` to just `X`**

For each file, read the exact code first, then replace the ternary with just the else-branch value. The AMOLED theme already defines semantic tokens to resolve to `Color.Black` where appropriate.

| File | Line | Current pattern | Simplified to |
|------|------|----------------|---------------|
| `ChatColors.kt` | 17 | `if (isAmoled) Color.Black else secondaryContainer...` | `MaterialTheme.colorScheme.secondaryContainer` (remove isAmoled param from function) |
| `ReasoningBlock.kt` | 61 | `if (isAmoled) Color.Black else surfaceContainer.copy(alpha=MEDIUM)` | `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.MEDIUM)` |
| `FileCard.kt` | 37 | `if (isAmoled) Color.Black else surfaceContainerLow` | `MaterialTheme.colorScheme.surfaceContainerLow` |
| `AssistantTurnBubble.kt` | 51 | `if (isAmoled) Color.Black else surfaceContainerHigh` | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `AssistantTurnBubble.kt` | 192 | `if (isAmoled) Color.Black else errorContainer.copy(alpha=FAINT)` | `MaterialTheme.colorScheme.errorContainer.copy(alpha = AlphaTokens.FAINT)` |
| `MessageCard.kt` | 104 | `if (isAmoled) Color.Black else primaryContainer` | `MaterialTheme.colorScheme.primaryContainer` |
| `MessageCard.kt` | 356 | `if (isAmoled) Color.Black else surfaceVariant` | `MaterialTheme.colorScheme.surfaceVariant` |
| `MessageCard.kt` | 527 | `if (isAmoled) Color.Black else errorContainer.copy(alpha=FAINT)` | `MaterialTheme.colorScheme.errorContainer.copy(alpha = AlphaTokens.FAINT)` |
| `ChatMessageList.kt` | 390 | `if (isAmoled) Color.Black else secondaryContainer` | `MaterialTheme.colorScheme.secondaryContainer` |
| `ChatMessageList.kt` | 429 | `if (isAmoled) Color.Black else tertiaryContainer` | `MaterialTheme.colorScheme.tertiaryContainer` |
| `ChatTopBar.kt` | 256 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| `ErrorPayloadContent.kt` | 80 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| `QuestionCard.kt` | 85 | `if (isAmoled) Color.Black else surfaceVariant` | `MaterialTheme.colorScheme.surfaceVariant` |
| `QuestionCard.kt` | 216 | `if (isAmoled) Color.Black else surface.copy(alpha=MEDIUM)` | `MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.MEDIUM)` |
| `PermissionRequestCard.kt` | 54 | `if (isAmoled) Color.Black else errorContainer` | `MaterialTheme.colorScheme.errorContainer` |
| `ImagePreviewDialog.kt` | 167 | `if (isAmoled) Color.Black else surface.copy(alpha=AMOLED)` | `MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.AMOLED)` |
| `ChatInputBar.kt` | 381 | `if (isAmoled) Color.Black else surfaceContainerHigh` | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `ChatInputBar.kt` | 444 | `if (isAmoled) Color.Black else surfaceContainerHigh` | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `ChatInputBar.kt` | 710 | `if (isAmoled) Color.Black else surfaceContainerHigh` | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `ChatInputBar.kt` | 766 | `if (isAmoled) Color.Black else surfaceVariant.copy(alpha=MUTED)` | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.MUTED)` |
| `ChatInputBar.kt` | 829 | `if (isAmoled) Color.Black else primary.copy(alpha=FAINT)` | `MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.FAINT)` |
| `ChatInputBar.kt` | 834 | `if (isAmoled) Color.Black else surfaceVariant.copy(alpha=FAINT)` | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.FAINT)` |
| `EditToolCard.kt` | 121 | `if (isAmoled) Color.Black else errorContainer` | `MaterialTheme.colorScheme.errorContainer` |
| `TodoListCard.kt` | 96 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| `PatchCard.kt` | 51 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |

**Special case — `ChatColors.kt`:** The `toolOutputContainerColor(isAmoled: Boolean)` function takes `isAmoled` as parameter. After simplification, remove the parameter entirely:
```kotlin
// Before
internal fun toolOutputContainerColor(isAmoled: Boolean): Color {
    return when {
        isAmoled -> Color.Black
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = AlphaTokens.AMOLED)
    }
}

// After
internal fun toolOutputContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = AlphaTokens.AMOLED)
    }
}
```
Also update all callers of `toolOutputContainerColor()` to remove the `isAmoled` argument.

Also clean up: where `val isAmoled = ...` was only used for these color expressions and is now unused, remove the variable declaration and any now-unused imports (`LocalAmoledMode`, etc.).

- [ ] **Step 2: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: chat components — remove Color.Black AMOLED branches, use semantic tokens"
```

---

### Task 7: AMOLED Color.Black Cleanup — Terminal Components

**Files:**
- Modify: `ChatTerminalView.kt`
- Modify: `SessionTerminalInline.kt`

Under: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/terminal/`

- [ ] **Step 1: ChatTerminalView.kt**

| Line | Current | Simplified to |
|------|---------|---------------|
| 337 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| 396 | `if (isAmoled) Color.Black else surfaceVariant.copy(alpha=MUTED)` | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AlphaTokens.MUTED)` |
| 460 | `if (isAmoled) Color.Black else secondaryContainer.copy(alpha=MUTED)` | `MaterialTheme.colorScheme.secondaryContainer.copy(alpha = AlphaTokens.MUTED)` |
| 464 | `if (isAmoled) Color.Black else Color.Transparent` | `Color.Transparent` (AMOLED theme doesn't make Transparent different) |

Note: Line 464 `unselectedContainerColor` — `Color.Transparent` in AMOLED is the same as default. Simplify to just `Color.Transparent`.

- [ ] **Step 2: SessionTerminalInline.kt**

Lines 143 and 415 are **unconditional `Color.Black`** (terminal rendering background). These are NOT AMOLED-specific — they are terminal-specific (terminal always has black background). **Do NOT change these.**

- [ ] **Step 3: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: terminal components — remove Color.Black AMOLED branches"
```

---

### Task 8: AMOLED Color.Black Cleanup — Screens & Shared Components

**Files:**
- Modify: `SessionListScreen.kt` (in `ui/screens/sessions/`)
- Modify: `HomeScreen.kt` (in `ui/screens/home/`)
- Modify: `LocalLaunchOptionsDialog.kt` (in `ui/screens/home/components/`)
- Modify: `ServerCard.kt` (in `ui/screens/home/components/`)
- Modify: `ServerDialog.kt` (in `ui/screens/home/components/`)
- Modify: `SettingsScreen.kt` (in `ui/screens/settings/`)
- Modify: `LocalServerLaunchOptionsDialog.kt` (in `ui/screens/settings/components/`)
- Modify: `ServerModelFilterScreen.kt` (in `ui/screens/server/`)
- Modify: `ProviderRow.kt` (in `ui/screens/server/components/`)
- Modify: `AppPickerList.kt` (in `ui/components/`)
- Modify: `AmoledCard.kt` (in `ui/components/`)
- Modify: `AmoledDialogParams.kt` (in `ui/components/`)

- [ ] **Step 1: Simple container color replacements**

| File | Line | Current | Simplified to |
|------|------|---------|---------------|
| `SessionListScreen.kt` | 150 | `if (isAmoled) Color.Black else primaryContainer` | `MaterialTheme.colorScheme.primaryContainer` |
| `HomeScreen.kt` | 135 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| `ServerCard.kt` | 99 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |
| `ProviderRow.kt` | 39 | `if (isAmoled) Color.Black else surfaceContainerHigh` | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| `LocalLaunchOptionsDialog.kt` | 94 | `if (isAmoled) Color.Black else surface` | `MaterialTheme.colorScheme.surface` |

- [ ] **Step 2: Switch track colors (5 files, same pattern)**

All Switch track colors in AMOLED branches set `checkedTrackColor = Color.Black` and `uncheckedTrackColor = Color.Black`. In AMOLED mode, the track becomes invisible against the black background. Removing these overrides lets the track use Material 3 default colors, which are slightly visible dark tones — an improvement.

Files:
- `LocalLaunchOptionsDialog.kt` lines 85, 88
- `ServerDialog.kt` lines 109, 112
- `SettingsScreen.kt` lines 132, 135
- `LocalServerLaunchOptionsDialog.kt` lines 98, 101
- `ServerModelFilterScreen.kt` lines 157, 160

For each file, replace the entire `if (isAmoled) { ... } else { ... }` SwitchDefaults.colors() block with:

```kotlin
SwitchDefaults.colors()
```

This works because `checkedThumbColor = primary` is already the Material 3 default. Read each file first to verify the exact context.

- [ ] **Step 3: AppPickerList.kt line 63**

Change:
```kotlin
when {
    isSelected && isAmoled -> Color.Black
    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaTokens.MUTED)
    else -> Color.Transparent
}
```
to:
```kotlin
when {
    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = AlphaTokens.MUTED)
    else -> Color.Transparent
}
```

- [ ] **Step 4: AmoledCard.kt — simplify but keep component**

`AmoledCard` is a reusable component with `normalContainerColor` parameter defaults. The `if (isAmoledDark)` checks can be simplified by removing the AMOLED branch since the AMOLED theme already sets the right colors.

However, `AmoledCard`'s purpose is specifically to provide AMOLED-aware card containers. The `normalContainerColor` defaults are `surfaceContainerHighest`, `surfaceContainerLow`, `surface`. In AMOLED mode, these all resolve to values defined in the theme — but they won't resolve to `Color.Black` unless the user explicitly set them to Black in the theme.

Checking the AMOLED theme: `surfaceContainerHighest = Color(0xFF2A2A36)`, NOT `Color.Black`. So we **cannot** remove the AMOLED branch from AmoledCard because `surfaceContainerHighest` in AMOLED is dark grey, not pure black.

**Decision: Keep AmoledCard.kt as-is.** The component exists specifically because AMOLED needs `Color.Black` where the default theme tokens provide slightly different dark values.

- [ ] **Step 5: AmoledDialogParams.kt line 45**

Change:
```kotlin
containerColor = Color.Black,
```
to:
```kotlin
containerColor = MaterialTheme.colorScheme.surface,
```

AMOLED theme: `surface = Color.Black`. Same result.

The `amoledOutlinedTextFieldColors()` function (lines 70-72) uses `Color.Black` for text field containers. This function is only called in AMOLED branches. Change to `MaterialTheme.colorScheme.surface`:

```kotlin
focusedContainerColor = MaterialTheme.colorScheme.surface,
unfocusedContainerColor = MaterialTheme.colorScheme.surface,
disabledContainerColor = MaterialTheme.colorScheme.surface,
```

- [ ] **Step 6: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: screens and components — remove Color.Black AMOLED branches, use semantic tokens"
```

---

### Task 9: Final Build, Bump & Release

**Files:**
- Modify: `app/build.gradle.kts` (version bump)

- [ ] **Step 1: Full compile check**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Build release APK**

Run: `.\gradlew :app:assembleBetaRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Bump version**

In `app/build.gradle.kts`, bump `versionCode` by 1 and `versionName` to next beta number.

- [ ] **Step 4: Commit, tag, push, release**

```bash
git add -A
git commit -m "release: v2.0.0-beta.XXX"
git tag v2.0.0-beta.XXX
git push origin master v2.0.0-beta.XXX
gh release create v2.0.0-beta.XXX "app\build\outputs\apk\beta\release\app-beta-release.apk" --title "v2.0.0-beta.XXX" --notes "..."
```

---

## Notes for Implementer

1. **Always read before edit** — each file may have been modified since this plan was written.
2. **AmoledCard.kt is intentionally NOT changed** — the component needs pure `Color.Black` because AMOLED theme's `surfaceContainerHighest` is `0xFF2A2A36`, not `Color.Black`.
3. **TerminalEmulator.kt and SessionTerminalInline.kt unconditional `Color.Black`** — these are terminal-specific (terminal always has black background), not AMOLED-specific. Do NOT change.
4. **Switch track colors** — after removing `Color.Black` override, verify visually that switches remain usable in AMOLED mode.
5. **ChatColors.kt `toolOutputContainerColor()`** — removing the `isAmoled` parameter means updating all callers. Search for `toolOutputContainerColor(` to find them.
6. **Clean up unused imports** — after removing `if (isAmoled)` checks, `LocalAmoledMode`, `isAmoledTheme()`, and `Color` imports may become unused in some files. Remove them.
