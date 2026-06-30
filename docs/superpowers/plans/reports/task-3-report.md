# Task 3 Report: FileViewerOverlay + Migrate All Callers + Delete Obsolete Files

## Status: DONE

## Commit
- SHA: `d653df8f`
- Subject: `feat(viewer): FileViewerOverlay + migrate all callers`

## Compile Result
`compileDevDebugKotlin` → **BUILD SUCCESSFUL** (21s). Only pre-existing deprecation warnings.

---

## Changes Per File

### Files Created (2)

#### 1. `ui/screens/viewer/FileViewerEntryPoint.kt`
Hilt `@EntryPoint` interface exposing `FileViewerViewModel.Factory` for non-Hilt ViewModel instantiation via `EntryPointAccessors`.

#### 2. `ui/screens/viewer/FileViewerOverlay.kt`
Dialog-based overlay composable replacing the old route navigation. Key design:
- **`OverlayViewModelStoreOwner`** — private `ViewModelStoreOwner` with its own `ViewModelStore`, cleared on dispose. Gives the overlay VM an isolated lifecycle scope (does not attach to the host screen's back stack entry).
- **`SimpleViewModelFactory`** — bridges `AssistedInject.Factory.create(params)` to the `ViewModelProvider.Factory` interface.
- **`FileViewerDialogContent`** — wraps the existing `FileViewerScreen` in a `Dialog` with `usePlatformDefaultWidth = false`, `dismissOnClickOutside = false`. All callbacks (copy/share/annotate/submit) wired identically to the old `FileViewerRoute`.

**Fix applied during implementation:** The task spec had `import androidx.compose.ui.LocalContext` — incorrect package. Fixed to `androidx.compose.ui.platform.LocalContext`.

### Files Modified (6)

#### 3. `ui/screens/chat/components/PartContent.kt`
- Import: `FileViewerNav` → `FileViewerSource`
- Source constants: `FileViewerNav.Source.TOOL_SNAPSHOT_DIFF` → `FileViewerSource.TOOL_SNAPSHOT_DIFF` (and non-diff variant)

#### 4. `ui/screens/chat/ChatViewModel.kt`
- Deleted `pendingScrollKey: String?` and `pendingScrollOffset: Int` fields
- Simplified scroll-state comment (removed "survives navigation" rationale — no longer needed since FileViewer is an overlay, not a navigation destination)

#### 5. `ui/screens/chat/ChatScreen.kt` (the big one — 8 edits)
1. **Imports:** Added `FileViewerOverlay`, `FileViewerParams`, `FileViewerSource`, `LocalOnViewTool`
2. **Signature:** Added `serverId: String,` and `sessionId: String,` as first two params
3. **Overlay state:** Added `fileViewerRequest` state + `handleOpenFile` lambda (sets `FileViewerParams` with `LIVE` source)
4. **Deleted `DisposableEffect`** that saved scroll position on dispose (no longer needed — overlay doesn't navigate away)
5. **Simplified `LaunchedEffect(messageCount)`** — removed `savedKey` restore logic, kept only auto-scroll-to-bottom
6. **Replaced** all 3 occurrences of `onOpenFile = onOpenFile` → `onOpenFile = handleOpenFile` (linkUriHandler + 2× ChatMessageList)
7. **Added `LocalOnViewTool` provider** to existing `CompositionLocalProvider` block — caches tool part and sets `fileViewerRequest` with appropriate source/toolPartIds
8. **Added overlay rendering** after `ChatSettingsProvider` close, before function end

#### 6. `ui/screens/workspace/WorkspaceScreen.kt`
- `WorkspaceRoute`: removed `onOpenFile`/`onOpenGitDiff` params, added `serverId`/`sessionId`
- Added `fileViewerRequest` state + internal lambdas for LIVE and GIT_DIFF sources
- Renders `FileViewerOverlay` when request is non-null
- Added imports for `mutableStateOf`/`remember`/`setValue`/`FileViewerOverlay`/`FileViewerParams`/`FileViewerSource`

#### 7. `ui/screens/chat/ChatRoute.kt` (bonus fix — not in plan)
- `ChatRoute.kt` defines a `NavGraphBuilder.chatScreen()` extension that is **dead code** (never called — NavGraph registers ChatScreen inline). It also calls `ChatScreen(...)`, so the new required params `serverId`/`sessionId` had to be passed for compilation. Added `serverId = args.server.serverId, sessionId = args.sessionId`.

#### 8. `ui/navigation/NavGraph.kt`
- **Removed imports:** `CompositionLocalProvider`, `LocalOnViewTool`, `FileViewerRoute`
- **ChatScreen destination:** Removed `CompositionLocalProvider(LocalOnViewTool provides {...})` wrapper (logic moved into ChatScreen). Added `serverId`/`sessionId` params. Deleted `onOpenFile` lambda (navigated to `FileViewerNav`). Removed orphaned closing brace.
- **Workspace destination:** Replaced `onOpenFile`/`onOpenGitDiff` navigation lambdas with `serverId`/`sessionId` params
- **Deleted FileViewer destination** entirely (`composable(route = FileViewerNav.routePattern, ...)`)

### Files Deleted (2)
- `ui/navigation/routes/FileViewerNav.kt` — route definition, params, URL encoding helpers
- `ui/screens/viewer/FileViewerRoute.kt` — old Hilt-VM-based composable

### Not Deleted (1)
- `ui/screens/chat/ScrollPositionDelegate.kt` — task specified `util/ScrollPositionDelegate.kt` (doesn't exist). The actual file at `chat/ScrollPositionDelegate.kt` is pre-existing dead code (never referenced) and was not in scope for this task's deletion list. Left as-is.

---

## Issues Encountered

1. **`LocalContext` import path** — Task spec provided `androidx.compose.ui.LocalContext`; correct package is `androidx.compose.ui.platform.LocalContext`. Fixed.
2. **`ChatRoute.kt` missed by plan** — Dead-code extension function `NavGraphBuilder.chatScreen()` also calls `ChatScreen`. Required adding `serverId`/`sessionId` args for compilation. This file was not in the original task's file list.

## Warnings (non-blocking)
- `LocalClipboardManager` deprecation in `FileViewerOverlay.kt` — consistent with the previous `FileViewerRoute.kt` which used the same API. Can be migrated to `LocalClipboard` in a follow-up.
- `Icons.Filled.HelpOutline` deprecation in `PartContent.kt` — pre-existing, unrelated to this change.
