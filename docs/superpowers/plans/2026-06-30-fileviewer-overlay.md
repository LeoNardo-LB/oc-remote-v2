# FileViewer Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace FileViewer route navigation with a full-screen Dialog overlay so ChatScreen's composition (and LazyListState) is never destroyed, eliminating scroll position drift.

**Architecture:** FileViewerViewModel refactored from `@HiltViewModel` + `SavedStateHandle` to `@AssistedInject` + `FileViewerParams`. A new `FileViewerOverlay` composable wraps the existing `FileViewerScreen` in a `Dialog` with an independent `ViewModelStore`. ChatScreen and WorkspaceScreen intercept file-open callbacks to show the overlay instead of navigating.

**Tech Stack:** Jetpack Compose, Hilt, Dagger AssistedInject, Material 3 Dialog

## Global Constraints

- **ChatScreen.kt editing protocol**: Read → Edit → Compile → Commit after each edit
- **Gradle commands**: `--no-daemon`, timeout 120s for `compileDevDebugKotlin`, 300s for `assembleDevRelease`
- **PowerShell**: use `;` not `&&`, use `if ($?) {}` for dependent commands
- **Hilt**: uses KSP (not kapt) for annotation processing
- **Material 3 first**: prefer native components
- **No auto-commit/push** unless explicitly requested

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `ui/screens/viewer/FileViewerParams.kt` | Parameter data class + source constants |
| `ui/screens/viewer/FileViewerEntryPoint.kt` | Hilt EntryPoint exposing AssistedFactory |
| `ui/screens/viewer/FileViewerOverlay.kt` | Dialog wrapper + independent ViewModelStore + ViewModel creation + callbacks (from FileViewerRoute) |

### Modified Files

| File | Changes |
|------|---------|
| `ui/screens/viewer/FileViewerViewModel.kt` | `@HiltViewModel`→`@AssistedInject`, remove SavedStateHandle, remove annotation persistence |
| `ui/screens/chat/ChatScreen.kt` | Overlay state + intercept onOpenFile/LocalOnViewTool + delete scroll restore |
| `ui/screens/chat/ChatViewModel.kt` | Delete pendingScrollKey/Offset |
| `ui/screens/chat/components/PartContent.kt` | `FileViewerNav.Source.*`→`FileViewerSource.*` |
| `ui/screens/workspace/WorkspaceScreen.kt` | Overlay state + remove navigate callbacks |
| `ui/navigation/NavGraph.kt` | Delete FileViewer destination + update Chat/Workspace params |
| `app/src/test/.../FileViewerViewModelTest.kt` | Rewrite: SavedStateHandle→FileViewerParams |

### Deleted Files

| File | Reason |
|------|--------|
| `ui/navigation/routes/FileViewerNav.kt` | No longer needed |
| `ui/screens/viewer/FileViewerRoute.kt` | Replaced by FileViewerOverlay |
| `ui/screens/chat/util/ScrollPositionDelegate.kt` | Scroll restore no longer needed |
| `app/src/test/.../FileViewerNavTest.kt` | FileViewerNav deleted |

---

## Task 1: Create FileViewerParams + FileViewerSource

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerParams.kt`

**Interfaces:**
- Produces: `FileViewerParams` data class, `FileViewerSource` constants object

- [ ] **Step 1: Create FileViewerParams.kt**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

/**
 * Parameters for [FileViewerViewModel], replacing the old SavedStateHandle + NavBackStackEntry
 * approach. Passed directly via @AssistedInject, decoupling the ViewModel from the navigation system.
 */
data class FileViewerParams(
    val serverId: String,
    val sessionId: String,
    val filePath: String,
    val directory: String,
    val source: String,
    val toolPartIds: List<String> = emptyList()
)

object FileViewerSource {
    const val LIVE = "live"
    const val GIT_DIFF = "git_diff"
    const val TOOL_SNAPSHOT = "tool_snapshot"
    const val TOOL_SNAPSHOT_DIFF = "tool_snapshot_diff"
}
```

- [ ] **Step 2: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL (standalone file, no dependencies)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerParams.kt
git commit -m "feat(viewer): add FileViewerParams + FileViewerSource constants"
```

---

## Task 2: Refactor FileViewerViewModel to @AssistedInject + Rewrite Tests

**Files:**
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt`
- Modify: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt`
- Delete: `app/src/test/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNavTest.kt`

**Interfaces:**
- Consumes: `FileViewerParams` from Task 1
- Produces: `FileViewerViewModel` with `@AssistedInject` constructor + `Factory` interface

**⚠️ This task leaves the project in a non-compiling state** — `FileViewerRoute.kt` still uses `hiltViewModel<FileViewerViewModel>()` which will fail. Task 3 fixes this.

- [ ] **Step 1: Rewrite FileViewerViewModelTest with FileViewerParams**

Replace the `savedStateHandle` helper and all `FileViewerNav.Source.*` references with `FileViewerParams`:

```kotlin
// Top of file — replace imports
// DELETE: import androidx.lifecycle.SavedStateHandle
// DELETE: import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav
// DELETE: import dev.leonardo.ocremotev2.ui.navigation.routes.ServerRouteParams
// DELETE: import java.net.URLEncoder
// KEEP all other imports

// Replace the savedStateHandle() helper (lines 48-62) with:
    private fun fileViewerParams(
        source: String = FileViewerSource.LIVE,
        path: String = filePath,
        dir: String = directory,
        toolPartIds: List<String> = emptyList()
    ) = FileViewerParams(
        serverId = serverId,
        sessionId = "session-123",
        filePath = path,
        directory = dir,
        source = source,
        toolPartIds = toolPartIds
    )

// Delete encodedDirectory, encodedFilePath, encodedMdFilePath fields (lines 43-46)
// Replace mdFilePath reference — no URL encoding needed:
    private val mdFilePath = "docs/README.md"
```

Then replace every `FileViewerNav.Source.X` with `FileViewerSource.X` throughout the file.

Replace every `savedStateHandle(...)` call with `fileViewerParams(...)`. The signatures differ — `savedStateHandle` took encoded strings, `fileViewerParams` takes plain strings + List<String> for toolPartIds. For each test:

- `savedStateHandle()` → `fileViewerParams()`
- `savedStateHandle(source = FileViewerNav.Source.GIT_DIFF)` → `fileViewerParams(source = FileViewerSource.GIT_DIFF)`
- `savedStateHandle(source = FileViewerNav.Source.TOOL_SNAPSHOT)` → `fileViewerParams(source = FileViewerSource.TOOL_SNAPSHOT)`
- `savedStateHandle(source = FileViewerNav.Source.GIT_DIFF)` → `fileViewerParams(source = FileViewerSource.GIT_DIFF)`
- `savedStateHandle(path = encodedMdFilePath, source = FileViewerNav.Source.GIT_DIFF)` → `fileViewerParams(path = mdFilePath, source = FileViewerSource.GIT_DIFF)`
- `savedStateHandle(source = FileViewerNav.Source.TOOL_SNAPSHOT, toolPartIds = "part1")` → `fileViewerParams(source = FileViewerSource.TOOL_SNAPSHOT, toolPartIds = listOf("part1"))`

Replace every ViewModel construction:
- `FileViewerViewModel(savedStateHandle(...), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)`
  → `FileViewerViewModel(fileViewerParams(...), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)`

- [ ] **Step 2: Delete FileViewerNavTest.kt**

```bash
git rm app/src/test/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNavTest.kt
```

- [ ] **Step 3: Modify FileViewerViewModel — header + constructor**

Replace the class header (lines 1-42):

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.leonardo.ocremotev2.R
import dev.leonardo.ocremotev2.domain.model.Annotation
import dev.leonardo.ocremotev2.domain.model.ContentType
import dev.leonardo.ocremotev2.domain.model.VcsDiffMode
import dev.leonardo.ocremotev2.domain.repository.ToolSnapshotCache
import dev.leonardo.ocremotev2.domain.usecase.GetFileContentUseCase
import dev.leonardo.ocremotev2.domain.usecase.GetFileDiffUseCase
import dev.leonardo.ocremotev2.domain.usecase.SubmitAnnotationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class FileViewerViewModel @AssistedInject constructor(
    @Assisted private val params: FileViewerParams,
    private val getFileContent: GetFileContentUseCase,
    private val getFileDiff: GetFileDiffUseCase,
    private val toolSnapshotCache: ToolSnapshotCache,
    private val submitAnnotationsUseCase: SubmitAnnotationsUseCase
) : ViewModel() {
    private val serverId = params.serverId
    private val directory = params.directory
    private val filePath = params.filePath
    private val source = params.source
    private val sessionId = params.sessionId
    private val toolPartIds = params.toolPartIds
    private val _uiState = MutableStateFlow(FileViewerUiState(filePath = filePath, directory = directory))
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()
    private val diffParser = DiffParser()
    private var annotationManager: AnnotationManager? = null

    // Phase 4: pagination — cache full content for loadMore slicing
    private var fullContentCache: String = ""

    @AssistedFactory
    interface Factory {
        fun create(params: FileViewerParams): FileViewerViewModel
    }
```

**Removed imports**: `SavedStateHandle`, `HiltViewModel`, `URLDecoder`, `FileViewerNav`, `ServerRouteParams`.
**Removed**: `@HiltViewModel` annotation, `savedStateHandle: SavedStateHandle` parameter.

- [ ] **Step 4: Modify FileViewerViewModel — init block**

Replace the `when (source)` block (was lines 56-62):

```kotlin
    init {
        when (source) {
            FileViewerSource.LIVE -> loadLive()
            FileViewerSource.GIT_DIFF -> loadGitDiff()
            FileViewerSource.TOOL_SNAPSHOT -> loadToolSnapshot()
            FileViewerSource.TOOL_SNAPSHOT_DIFF -> loadToolSnapshotDiff()
        }
    }
```

- [ ] **Step 5: Modify FileViewerViewModel — remove annotation SavedStateHandle persistence**

In `loadLive()` (around line 87-89), delete the `restoreAnnotationsFromHandle()` call:
```kotlin
                    // DELETE these two lines:
                    // val restored = restoreAnnotationsFromHandle()
                    // if (restored.isNotEmpty()) annotationManager?.restore(restored)
```

In `addAnnotation()`, `deleteAnnotation()`, `updateAnnotation()` — delete `saveAnnotationsToHandle(all)` calls.

In `submitAnnotations()` — delete `savedStateHandle.remove<Any>("annotations_flat")`.

Delete the `saveAnnotationsToHandle()` and `restoreAnnotationsFromHandle()` methods entirely.

- [ ] **Step 6: Commit (non-compiling state acknowledged)**

```bash
git add -A
git commit -m "refactor(viewer): FileViewerViewModel @AssistedInject + remove SavedStateHandle

BREAKING: project does not compile until Task 3 (FileViewerOverlay).
- @HiltViewModel -> @AssistedInject + @AssistedFactory
- SavedStateHandle params -> FileViewerParams constructor
- Remove annotation SavedStateHandle persistence (overlay VM is short-lived)
- Rewrite FileViewerViewModelTest for FileViewerParams
- Delete FileViewerNavTest"
```

---

## Task 3: Create FileViewerOverlay + Migrate All Callers + Delete Obsolete Files

**Files:**
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerEntryPoint.kt`
- Create: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerOverlay.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt` (lines 196-197)
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt` (lines 509-510)
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt`
- Modify: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt`
- Delete: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNav.kt`
- Delete: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerRoute.kt`
- Delete: `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ScrollPositionDelegate.kt`

**Interfaces:**
- Consumes: `FileViewerViewModel.Factory` from Task 2, `FileViewerParams` from Task 1
- Produces: `FileViewerOverlay` composable, fully compilable project

- [ ] **Step 1: Create FileViewerEntryPoint.kt**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FileViewerEntryPoint {
    fun fileViewerViewModelFactory(): FileViewerViewModel.Factory
}
```

- [ ] **Step 2: Create FileViewerOverlay.kt**

```kotlin
package dev.leonardo.ocremotev2.ui.screens.viewer

import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.composeEntryPoint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.EntryPointAccessors
import dev.leonardo.ocremotev2.R
import kotlinx.coroutines.launch

@Composable
fun FileViewerOverlay(
    params: FileViewerParams,
    onDismiss: () -> Unit
) {
    val overlayOwner = remember { OverlayViewModelStoreOwner() }
    DisposableEffect(overlayOwner) {
        onDispose { overlayOwner.viewModelStore.clear() }
    }

    val context = LocalContext.current
    val assistedFactory = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            FileViewerEntryPoint::class.java
        ).fileViewerViewModelFactory()
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides overlayOwner) {
        val fileViewerViewModel: FileViewerViewModel = viewModel(
            factory = SimpleViewModelFactory { assistedFactory.create(params) }
        )

        FileViewerDialogContent(
            viewModel = fileViewerViewModel,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun FileViewerDialogContent(
    viewModel: FileViewerViewModel,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isSubmitting by remember { mutableStateOf(false) }

        FileViewerScreen(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onBack = onDismiss,
            onNextHunk = viewModel::nextHunk,
            onPrevHunk = viewModel::prevHunk,
            onCopyPath = {
                clipboard.setText(AnnotatedString(uiState.filePath))
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
            },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, uiState.content.ifBlank { uiState.filePath })
                }
                runCatching {
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            },
            onCopyAllContent = {
                clipboard.setText(AnnotatedString(uiState.content))
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
            },
            onToggleRenderMode = viewModel::toggleRenderMode,
            onSwitchToSource = viewModel::switchToSource,
            onAddAnnotation = { selectedText, startChar, endChar, note ->
                if (startChar >= 0) {
                    viewModel.addAnnotation(selectedText, startChar, endChar, note)
                }
            },
            onDeleteAnnotation = viewModel::deleteAnnotation,
            onUpdateAnnotation = viewModel::updateAnnotation,
            onLoadMoreLines = viewModel::loadMoreLines,
            onSubmitAnnotations = { overallNote, editedNotes ->
                if (!isSubmitting) {
                    isSubmitting = true
                    scope.launch {
                        val result = viewModel.submitAnnotations(overallNote, editedNotes)
                        isSubmitting = false
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.annotation_submitted_toast), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            snackbarHostState.showSnackbar(context.getString(R.string.annotation_submit_failed))
                        }
                    }
                }
            }
        )
    }
}

private class OverlayViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private class SimpleViewModelFactory(
    private val create: () -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
```

- [ ] **Step 3: Update PartContent.kt — Source constants**

Read `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt`.

Replace lines 196-197:
```kotlin
// OLD:
                        val source = if (isDiffTool) FileViewerNav.Source.TOOL_SNAPSHOT_DIFF
                        else FileViewerNav.Source.TOOL_SNAPSHOT

// NEW:
                        val source = if (isDiffTool) FileViewerSource.TOOL_SNAPSHOT_DIFF
                        else FileViewerSource.TOOL_SNAPSHOT
```

Add import: `import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource`

Remove import: `import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav`

- [ ] **Step 4: Update ChatViewModel.kt — delete pendingScrollKey/Offset**

Read `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt` around lines 498-510.

Delete lines 509-510:
```kotlin
// DELETE:
    var pendingScrollKey: String? = null
    var pendingScrollOffset: Int = 0
```

Keep `val listState` (line 503) — ChatMessageList still needs it. Update the comment on lines 498-502:
```kotlin
    // ============ Scroll State ============
    // LazyListState lives in the ViewModel for stable composition reference.
    val listState = androidx.compose.foundation.lazy.LazyListState()
```

- [ ] **Step 5: Update ChatScreen.kt — intercept onOpenFile + delete scroll restore + add overlay**

Read `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt`.

**5a. Add new parameters to ChatScreen function signature** (around line 261):

Add after existing params:
```kotlin
    serverId: String,
    sessionId: String,
```

The `onOpenFile` parameter stays (it's used internally by components), but its value changes — ChatScreen sets it internally instead of receiving from NavGraph.

**5b. Add overlay state + intercept callbacks** (after line 304):

```kotlin
    // ── FileViewer overlay state ──
    var fileViewerRequest by remember { mutableStateOf<FileViewerParams?>(null) }

    val handleOpenFile: (String) -> Unit = { filePath ->
        fileViewerRequest = FileViewerParams(
            serverId = serverId,
            sessionId = sessionId,
            filePath = filePath,
            directory = directory,
            source = FileViewerSource.LIVE
        )
    }
```

**5c. Delete scroll restore code** — delete lines 313-321 (DisposableEffect for saving scroll position) and lines 331-346 (the savedKey restore logic in LaunchedEffect(messageCount)):

```kotlin
    // DELETE this entire block:
    // DisposableEffect(Unit) {
    //     onDispose { ... pendingScrollKey ... pendingScrollOffset ... }
    // }

    // In LaunchedEffect(messageCount), DELETE:
    // val savedKey = viewModel.pendingScrollKey
    // if (savedKey != null && !autoScrollEnabled) { ... requestScrollToItem ... }
```

The LaunchedEffect(messageCount) should be simplified to:
```kotlin
    LaunchedEffect(messageCount) {
        if (messageCount > 0 && autoScrollEnabled && !listState.isScrollInProgress) {
            listState.scrollToItem(0)
        }
    }
```

**5d. Replace `onOpenFile` usage** — wherever `onOpenFile` is passed to child components, replace with `handleOpenFile`:

Find all references to `onOpenFile` parameter (lines 375, 1013, 1038) and replace with `handleOpenFile`.

**5e. Wrap content in LocalOnViewTool provider** — add before the main content:

```kotlin
    CompositionLocalProvider(
        LocalOnViewTool provides { request ->
            viewModel.cacheToolPart(request.part)
            fileViewerRequest = FileViewerParams(
                serverId = serverId,
                sessionId = sessionId,
                filePath = request.filePath,
                directory = directory,
                source = request.source,
                toolPartIds = listOf(request.part.id)
            )
        }
    ) {
        // ... existing content ...
    }
```

**5f. Add overlay rendering** — at the end of ChatScreen, before the closing `}`:

```kotlin
    fileViewerRequest?.let { params ->
        FileViewerOverlay(
            params = params,
            onDismiss = { fileViewerRequest = null }
        )
    }
```

Add imports:
```kotlin
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerParams
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerOverlay
```

- [ ] **Step 6: Update WorkspaceScreen.kt — overlay integration**

Read `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt` (line 39-62).

Modify `WorkspaceRoute`:
```kotlin
@Composable
fun WorkspaceRoute(
    viewModel: WorkspaceViewModel = hiltViewModel(),
    onBack: () -> Unit,
    serverId: String,
    sessionId: String,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var fileViewerRequest by remember { mutableStateOf<FileViewerParams?>(null) }

    WorkspaceScreen(
        uiState = uiState,
        onBack = onBack,
        onSwitchPanel = viewModel::switchPanel,
        onRefreshRoot = viewModel::refreshRoot,
        onToggleShowIgnored = viewModel::toggleShowIgnored,
        onToggleExpand = viewModel::toggleExpand,
        onRefreshGit = viewModel::loadGitChanges,
        onOpenFile = { filePath ->
            fileViewerRequest = FileViewerParams(
                serverId = serverId,
                sessionId = sessionId,
                filePath = filePath,
                directory = "",
                source = FileViewerSource.LIVE
            )
        },
        onOpenGitDiff = { filePath ->
            fileViewerRequest = FileViewerParams(
                serverId = serverId,
                sessionId = sessionId,
                filePath = filePath,
                directory = "",
                source = FileViewerSource.GIT_DIFF
            )
        },
        onEnterSearch = viewModel::enterSearch,
        onExitSearch = viewModel::exitSearch,
        onSearchQueryChange = viewModel::updateSearchQuery
    )

    fileViewerRequest?.let { params ->
        FileViewerOverlay(params = params, onDismiss = { fileViewerRequest = null })
    }
}
```

Add imports:
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerParams
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerOverlay
```

- [ ] **Step 7: Update NavGraph.kt — delete FileViewer destination + update Chat/Workspace**

Read `app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt`.

**7a. Delete imports** (lines 41):
```kotlin
// DELETE:
import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerRoute
```

**7b. Update ChatScreen destination** — delete `LocalOnViewTool provides { ... }` block (lines 420-440) that navigates to FileViewerNav. Replace with just the ChatScreen call. Add `serverId` and `sessionId` params:

```kotlin
            val chatViewModel = hiltViewModel<ChatViewModel>()
            ChatScreen(
                viewModel = chatViewModel,
                serverId = params.server.serverId,
                sessionId = params.sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { newSessionId -> /* existing logic */ },
                // ... other existing params EXCEPT onOpenFile ...
                onOpenDirectory = { directoryPath ->
                    navController.navigate(
                        WorkspaceNav.createRoute(
                            serverUrl = params.server.serverUrl,
                            username = params.server.username,
                            password = params.server.password,
                            serverName = params.server.serverName,
                            serverId = params.server.serverId,
                            sessionId = params.sessionId,
                            directory = directoryPath
                        )
                    ) { launchSingleTop = true }
                },
                checkFileExists = { filePath -> /* existing logic */ },
                initialSharedImages = imagesForThisSession,
                onSharedImagesConsumed = { /* existing */ },
                startInTerminalMode = params.openTerminal
            )
```

Remove the `CompositionLocalProvider(LocalOnViewTool provides ...)` wrapper — ChatScreen now handles it internally. Remove the `scope.launch { sessionRepository.getSession(...) }` call in the old onOpenFile.

**7c. Update Workspace destination** (lines 560-598):

```kotlin
        composable(
            route = WorkspaceNav.routePattern,
            arguments = WorkspaceNav.navArguments
        ) { entry ->
            val p = WorkspaceNav.fromEntry(entry)
            WorkspaceRoute(
                onBack = { navController.popBackStack() },
                serverId = p.server.serverId,
                sessionId = p.sessionId
            )
        }
```

**7d. Delete FileViewer destination** (lines 600-608):
```kotlin
        // DELETE this entire block:
        // composable(
        //     route = FileViewerNav.routePattern,
        //     arguments = FileViewerNav.navArguments
        // ) {
        //     FileViewerRoute(...)
        // }
```

- [ ] **Step 8: Delete obsolete files**

```bash
git rm app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNav.kt
git rm app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerRoute.kt
git rm app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/util/ScrollPositionDelegate.kt
```

- [ ] **Step 9: Compile check**

Run: `.\gradlew :app:compileDevDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

If there are import errors for `FileViewerNav`, search for remaining references:
```bash
rg "FileViewerNav" app/src/main/kotlin/
```
Fix any remaining references (replace with `FileViewerSource` or remove).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(viewer): FileViewerOverlay + migrate all callers

- Create FileViewerOverlay (Dialog + independent ViewModelStore)
- Create FileViewerEntryPoint (Hilt AssistedFactory access)
- ChatScreen: intercept onOpenFile/LocalOnViewTool → overlay state
- ChatScreen: delete scroll restore (pendingScrollKey/Offset)
- WorkspaceRoute: overlay state for onOpenFile/onOpenGitDiff
- NavGraph: delete FileViewer destination + LocalOnViewTool navigate
- PartContent: FileViewerNav.Source → FileViewerSource
- Delete FileViewerNav.kt, FileViewerRoute.kt, ScrollPositionDelegate.kt
- Project compiles cleanly"
```

---

## Task 4: Compile Verification + Manual Testing

**Files:** None (verification only)

- [ ] **Step 1: Full debug build**

Run: `.\gradlew :app:assembleDevDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Unit tests**

Run: `.\gradlew :app:testDevDebugUnitTest --no-daemon --rerun`
Expected: All tests pass

- [ ] **Step 3: Install on device**

```powershell
adb -s 192.168.110.238:42139 install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
adb -s 192.168.110.238:42139 shell am force-stop dev.leonardo.ocremotev2.dev
Start-Sleep -Seconds 2
adb -s 192.168.110.238:42139 shell am start -n dev.leonardo.ocremotev2.dev/dev.leonardo.ocremotev2.MainActivity
```

- [ ] **Step 4: Manual verification — scroll position preservation**

1. Open a chat session
2. Scroll down to the middle of the conversation
3. Click a file link in a message
4. Verify: FileViewer opens as full-screen overlay
5. Press back
6. **Verify: Scroll position is EXACTLY where it was** — no drift

- [ ] **Step 5: Manual verification — tool card open file**

1. Scroll to a Read/Write/Edit tool card
2. Tap the open-file button
3. Verify: FileViewer opens with tool snapshot
4. Press back
5. **Verify: Scroll position unchanged**

- [ ] **Step 6: Manual verification — FileViewer features**

1. Open a Markdown file → verify render preview toggle works
2. Open a diff file → verify diff view + hunk navigation
3. Open a large file → verify pagination (load more)
4. Open a file → add annotation → submit → verify success

- [ ] **Step 7: Manual verification — Workspace**

1. Navigate to Workspace
2. Open a file → verify FileViewer overlay
3. Press back → verify Workspace state preserved

- [ ] **Step 8: Commit final state**

```bash
git add -A
git commit -m "test(viewer): manual verification passed

- Scroll position preserved across FileViewer open/close
- Tool card file open works
- FileViewer features (markdown, diff, pagination, annotations) intact
- Workspace file open works"
```

---

## Self-Review Notes

### Spec coverage
- ✅ §3.1 FileViewerParams → Task 1
- ✅ §3.2 FileViewerViewModel @AssistedInject → Task 2
- ✅ §3.3 FileViewerEntryPoint → Task 3 Step 1
- ✅ §3.4 FileViewerOverlay → Task 3 Step 2
- ✅ §3.5 ChatScreen integration → Task 3 Step 5
- ✅ §3.6 WorkspaceScreen integration → Task 3 Step 6
- ✅ §3.7 NavGraph changes → Task 3 Step 7
- ✅ §3.8 ChatViewModel cleanup → Task 3 Step 4
- ✅ PartContent.kt Source constants → Task 3 Step 3
- ✅ File deletion → Task 3 Step 8
- ✅ Test rewrite → Task 2 Step 1
- ✅ FileViewerNavTest deletion → Task 2 Step 2

### Design adjustment from spec
- `directory` is always provided by the caller (not lazily resolved by VM). ChatScreen has it from `viewModel.directoryState`; WorkspaceScreen passes `""` (the existing behavior for Workspace).
- Annotation SavedStateHandle persistence removed entirely — overlay VM is short-lived, no process-death survival needed.
