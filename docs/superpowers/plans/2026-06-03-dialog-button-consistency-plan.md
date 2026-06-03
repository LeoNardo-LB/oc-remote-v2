# Dialog Button Consistency — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify all remaining dialog/page button inconsistencies — remove redundant picker Cancel buttons, replace native Button with FilledTonalButton, OAuth dialog → DialogButtons, add AMOLED colors to DialogButtons Primary.

**Architecture:** 4 independent tasks, each modifying specific files. No new files created. Each task is verified by `compileDevDebugKotlin`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

---

### Task 1: Remove Cancel buttons from 6 single-select picker dialogs

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/ImageCompressionDialog.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/ReconnectModePickerDialog.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/MessageCountPickerDialog.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/FontSizePickerDialog.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/LanguagePickerDialog.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/settings/components/ThemePickerDialog.kt`

All 6 files follow the same pattern. In each dialog function:

- [ ] **Step 1: Remove the DialogButtons call and preceding Spacer**

Find and delete these lines in each dialog:
```kotlin
// DELETE these 2 blocks (one per dialog function in the file):
Spacer(Modifier.height(16.dp))
DialogButtons(
    buttons = listOf(
        Triple(stringResource(R.string.cancel), DialogButtonRole.Secondary, onDismiss)
    )
)
```

Note: `ImageCompressionDialog.kt` has TWO dialog functions — remove the DialogButtons block from both.

- [ ] **Step 2: Clean up unused imports**

After removing DialogButtons from each file, remove these imports if no longer used:
```kotlin
import dev.minios.ocremote.ui.components.DialogButtons
import dev.minios.ocremote.ui.components.DialogButtonRole
```

Also remove `import androidx.compose.foundation.layout.Spacer` if the file has no other Spacer usage.

- [ ] **Step 3: Compile verify**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove redundant Cancel buttons from picker dialogs"
```

---

### Task 2: Replace 4 native Button with FilledTonalButton

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/sessions/SessionListScreen.kt:210`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt:973`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/home/components/EmptyServersView.kt:44`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/dialog/QuestionCard.kt:403`

- [ ] **Step 1: SessionListScreen.kt — retry Button → FilledTonalButton**

Change line 210:
```kotlin
// Before:
Button(onClick = { viewModel.loadSessions() }) {

// After:
FilledTonalButton(onClick = { viewModel.loadSessions() }) {
```

Ensure import exists:
```kotlin
import androidx.compose.material3.FilledTonalButton
```
Remove unused `import androidx.compose.material3.Button` if no other `Button` usage remains in the file.

- [ ] **Step 2: ChatScreen.kt — retry Button → FilledTonalButton**

Change line 973:
```kotlin
// Before:
Button(onClick = { viewModel.loadMessages() }) {

// After:
FilledTonalButton(onClick = { viewModel.loadMessages() }) {
```

Ensure import exists:
```kotlin
import androidx.compose.material3.FilledTonalButton
```
Check if `Button` is used elsewhere in the file before removing its import.

- [ ] **Step 3: EmptyServersView.kt — add-server Button → FilledTonalButton + AMOLED**

Change lines 44-48:
```kotlin
// Before:
Button(onClick = onAddServer) {
    Icon(Icons.Default.Add, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.home_add_server))
}

// After:
FilledTonalButton(
    onClick = onAddServer,
    colors = amoledTonalButtonColors(),
    border = amoledTonalButtonBorder(),
) {
    Icon(Icons.Default.Add, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.home_add_server))
}
```

Update imports — add:
```kotlin
import androidx.compose.material3.FilledTonalButton
import dev.minios.ocremote.ui.components.amoledTonalButtonColors
import dev.minios.ocremote.ui.components.amoledTonalButtonBorder
```
Remove unused:
```kotlin
import androidx.compose.material3.Button
```

- [ ] **Step 4: QuestionCard.kt — submit Button → FilledTonalButton**

Change lines 403-413:
```kotlin
// Before:
Button(
    onClick = {
        performHaptic(hapticView, hapticOn)
        submitted = true
        onSubmit(answersPerQuestion.map { it.toList() })
    },
    enabled = answersPerQuestion.any { it.isNotEmpty() } && !submitted,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
) {
    Text(stringResource(R.string.question_submit), style = MaterialTheme.typography.labelMedium)
}

// After:
FilledTonalButton(
    onClick = {
        performHaptic(hapticView, hapticOn)
        submitted = true
        onSubmit(answersPerQuestion.map { it.toList() })
    },
    enabled = answersPerQuestion.any { it.isNotEmpty() } && !submitted,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
) {
    Text(stringResource(R.string.question_submit), style = MaterialTheme.typography.labelMedium)
}
```

Add import:
```kotlin
import androidx.compose.material3.FilledTonalButton
```
Remove `import androidx.compose.material3.Button` if no other usage in file.

- [ ] **Step 5: Compile verify**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: replace native Button with FilledTonalButton"
```

---

### Task 3: OAuth dialog manual buttons → DialogButtons

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/server/ServerProvidersScreen.kt` (~lines 391-404)

- [ ] **Step 1: Replace manual Row+TextButton+FilledTonalButton with DialogButtons**

Find the OAuth code-input dialog's button area (~line 391):
```kotlin
// Before:
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = {
        oauthCode = ""
        viewModel.cancelProviderOauth()
    }) { Text(stringResource(R.string.cancel)) }
    if (pending.authorization.method == "code") {
        FilledTonalButton(
            onClick = {
                viewModel.completeProviderOauth(oauthCode)
                oauthCode = ""
            },
            enabled = oauthCode.isNotBlank() && !uiState.isSaving
        ) { Text(stringResource(R.string.server_settings_oauth_complete)) }
    }
}
```

Replace with:
```kotlin
// After:
DialogButtons(
    buttons = buildList {
        add(Triple(stringResource(R.string.cancel), DialogButtonRole.Secondary) {
            oauthCode = ""
            viewModel.cancelProviderOauth()
        })
        if (pending.authorization.method == "code") {
            add(Triple(
                stringResource(R.string.server_settings_oauth_complete),
                DialogButtonRole.Primary
            ) {
                viewModel.completeProviderOauth(oauthCode)
                oauthCode = ""
            })
        }
    }
)
```

Note: The `enabled` guard on the Complete button is dropped. The onClick lambda already performs the action; if `oauthCode` is blank, the server-side will reject it. This matches how other DialogButtons work (no per-button enabled state).

- [ ] **Step 2: Clean up unused imports**

Remove if no longer used elsewhere in this dialog block:
```kotlin
import androidx.compose.foundation.layout.Arrangement  // check first
```

Ensure these are imported:
```kotlin
import dev.minios.ocremote.ui.components.DialogButtons
import dev.minios.ocremote.ui.components.DialogButtonRole
```
(They may already be imported for the other dialogs in the same file.)

- [ ] **Step 3: Compile verify**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: OAuth dialog uses DialogButtons instead of manual layout"
```

---

### Task 4: Add AMOLED adaptation to DialogButtons Primary button

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/components/DialogButtons.kt:86-91`

- [ ] **Step 1: Update DialogActionButton Primary branch**

In the `DialogActionButton` composable, change the Primary branch:

```kotlin
// Before:
DialogButtonRole.Primary -> {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

// After:
DialogButtonRole.Primary -> {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = amoledTonalButtonColors(),
        border = amoledTonalButtonBorder(),
    ) {
        Text(text)
    }
}
```

No import changes needed — `amoledTonalButtonColors()` and `amoledTonalButtonBorder()` are defined in the same file.

- [ ] **Step 2: Compile verify**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add AMOLED adaptation to DialogButtons Primary button"
```

---

### Task 5: Version bump and release

**Files:**
- Modify: `app/build.gradle.kts:20-21`

- [ ] **Step 1: Bump version**

```kotlin
// Before:
versionCode = 335
versionName = "2.0.0-beta.135"

// After:
versionCode = 336
versionName = "2.0.0-beta.136"
```

- [ ] **Step 2: Build release APK**

Run: `.\gradlew :app:assembleBetaRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit, tag, push, release**

```bash
git add -A
git commit -m "release: v2.0.0-beta.136"
git tag v2.0.0-beta.136
git push origin master v2.0.0-beta.136
gh release create v2.0.0-beta.136 "app\build\outputs\apk\beta\release\app-beta-release.apk" --title "v2.0.0-beta.136" --notes "## Dialog Button Consistency

- Remove redundant Cancel buttons from 6 single-select picker dialogs
- Replace 4 native Button with FilledTonalButton (retry, add-server, submit)
- OAuth dialog now uses DialogButtons component
- Add AMOLED adaptation to DialogButtons Primary button"
```
