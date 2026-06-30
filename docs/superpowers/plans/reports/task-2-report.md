# Task 2 Report — Refactor FileViewerViewModel to @AssistedInject + Rewrite Tests

**Commit:** `1d8f6496` — `refactor(viewer): FileViewerViewModel @AssistedInject + remove SavedStateHandle`
**Status:** DONE

## What Changed

### 1. `FileViewerViewModel.kt` (main refactor)

- **Constructor:** `@HiltViewModel` + `@Inject constructor(savedStateHandle: SavedStateHandle, ...)` →
  `@AssistedInject constructor(@Assisted params: FileViewerParams, ...)`.
- **Param extraction:** All fields (`serverId`, `directory`, `filePath`, `source`, `sessionId`, `toolPartIds`)
  now read directly from `params` (a typed `FileViewerParams`) instead of `SavedStateHandle` with
  `URLDecoder.decode(...)` + string key lookups.
- **`@AssistedFactory interface Factory`:** Added — `fun create(params: FileViewerParams): FileViewerViewModel`.
  Dagger/KSP generates the implementation at build time.
- **Source constants:** `FileViewerNav.Source.LIVE`/`GIT_DIFF`/`TOOL_SNAPSHOT`/`TOOL_SNAPSHOT_DIFF` →
  `FileViewerSource.*` in the `init` `when` block.
- **SavedStateHandle annotation persistence removed:**
  - `loadLive()`: deleted `restoreAnnotationsFromHandle()` call + the rotation-survival restore.
  - `addAnnotation` / `deleteAnnotation` / `updateAnnotation`: deleted `saveAnnotationsToHandle(all)`.
  - `submitAnnotations()`: deleted `savedStateHandle.remove<Any>("annotations_flat")`.
  - Deleted the two methods `saveAnnotationsToHandle()` and `restoreAnnotationsFromHandle()` entirely.
- **Removed imports:** `SavedStateHandle`, `dagger.hilt.android.lifecycle.HiltViewModel`,
  `FileViewerNav`, `ServerRouteParams`, `java.net.URLDecoder`.
- **Added imports:** `dagger.assisted.Assisted`, `AssistedFactory`, `AssistedInject`.

### 2. `FileViewerViewModelTest.kt` (rewrite)

- **Removed imports:** `SavedStateHandle`, `FileViewerNav`, `ServerRouteParams`, `java.net.URLEncoder`.
- **Removed fields:** `encodedDirectory`, `encodedFilePath`, `encodedMdFilePath` (URL encoding no longer needed).
- **New helper** `fileViewerParams(source, path, dir, toolPartIds: List<String>)` replaces `savedStateHandle(...)`,
  constructing a `FileViewerParams` directly. `sessionId` defaults to `"session-123"`.
- **All call sites updated:**
  - `FileViewerNav.Source.X` → `FileViewerSource.X`.
  - `savedStateHandle(...)` → `fileViewerParams(...)` (~20 constructor call sites).
  - `path = encodedMdFilePath` → `path = mdFilePath`; `path = URLEncoder.encode("X","UTF-8")` → `path = "X"`.
  - `toolPartIds = "part-1"` → `toolPartIds = listOf("part-1")`; `toolPartIds = "p1,p2"` → `listOf("p1","p2")`.
- **Deleted test:** `annotations survive ViewModel recreation via SavedStateHandle`. This tested the
  SavedStateHandle annotation-persistence feature, which was removed in this refactor. The feature no
  longer exists, so the test is obsolete. (Test count: 35 → 34.)

### 3. Deleted `FileViewerNavTest.kt`

`git rm` — this file tested `FileViewerNav.createRoute`/`fromEntry`, which is being phased out with the
navigation → dialog overlay migration.

## Test Results

**Command:**
```
.\gradlew :app:testDevDebugUnitTest --rerun --tests "*FileViewerViewModelTest*"
```

**Result:** `BUILD SUCCESSFUL in 1m 1s`

JUnit XML summary (`TEST-...FileViewerViewModelTest.xml`):
```
tests   = 34
skipped = 0
failures = 0
errors  = 0
No failures/errors detected.
```

### Note on compilation (deviates from task prediction)

The task brief predicted the full project would NOT compile because `FileViewerRoute.kt` still calls
`hiltViewModel<FileViewerViewModel>()`. In practice, the **test-variant compilation succeeded**:
`hiltViewModel<T>()` only requires `T : ViewModel` at *compile* time — the missing `@HiltViewModel`
annotation is a *runtime* DI-graph issue, not a compile-time one. KSP processed the new
`@AssistedInject`/`@AssistedFactory` without error for the unit-test variant.

This is favorable: the refactored VM + tests are verified green, not just "committed-blind". The runtime
DI breakage (`hiltViewModel<FileViewerViewModel>()` will fail to inject at runtime since the Hilt factory
binding no longer exists) remains and is expected to be resolved by Task 3.

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/.../viewer/FileViewerViewModel.kt` | Refactored to `@AssistedInject`; removed SavedStateHandle |
| `app/src/test/.../viewer/FileViewerViewModelTest.kt` | Rewritten for `FileViewerParams` (34 tests pass) |
| `app/src/test/.../navigation/routes/FileViewerNavTest.kt` | Deleted |

**Diff stat:** 3 files changed, 73 insertions(+), 236 deletions(-).

## Self-Review Findings

1. **Unused imports kept (per task template):** The task template explicitly included
   `import javax.inject.Inject` and `import ...domain.model.Annotation`. After this refactor both are
   technically unused (`@AssistedInject` replaces `@Inject`; the two `Annotation`-typed methods were
   deleted). They were retained verbatim to match the provided template and avoid divergence from the
   orchestrator's plan. These produce harmless compiler warnings only. Task 3 / final cleanup may drop them.

2. **Deleted a test not explicitly listed in instructions:** The rotation-survival test
   (`annotations survive ViewModel recreation via SavedStateHandle`) was not enumerated in the task's
   change list, but it necessarily had to be removed — it asserts behavior (SavedStateHandle persistence)
   that no longer exists, so it would fail. This is covered by the "rewrite tests" directive (removing
   obsolete tests). Flagged for transparency.

3. **Scoped commit:** Only the 3 task files were committed. Pre-existing working-tree changes
   (`docs/superpowers/specs/2026-06-30-fileviewer-overlay-design.md`, untracked `reports/`) were left
   unstaged to keep this commit focused.
