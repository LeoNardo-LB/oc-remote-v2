# Unified Directory Tree + Session List Design

**Date:** 2026-06-02
**Status:** Approved

## Problem

Current SessionListScreen uses a dual-mode pattern (`ListMode.PROJECTS` / `ListMode.SESSIONS`) with no transition animation. The project directory list is flat (one card per path), and session list requires a mode switch. Swipe-to-dismiss for delete/rename is non-discoverable and conflicts with long-press selection.

## Goals

1. Replace flat project cards with a **path-level tree view** (collapsible by depth)
2. **Merge directory and session views** into a single `LazyColumn` (no mode switching)
3. Add **expand/collapse animations** for tree nodes
4. Replace swipe gestures with a **three-dot overflow menu**
5. Menu actions: Rename, Delete, Copy info, View details
6. Maintain M3 + AlphaTokens + ShapeTokens + AMOLED consistency

## Data Model

```kotlin
sealed interface TreeNode {
    val id: String

    data class Directory(
        override val id: String,       // path string
        val path: String,              // raw file path
        val displayName: String,       // leaf segment for display
        val depth: Int,                // indentation level (0 = root)
        val childDirectoryCount: Int,  // direct child directories
        val sessionCount: Int,         // sessions in this exact directory
        val totalSessionCount: Int,    // sessions including subdirectories
        val isExpanded: Boolean,
    ) : TreeNode

    data class Session(
        override val id: String,       // sessionId
        val session: SessionItem,      // existing session wrapper
        val depth: Int,                // indentation level (parent depth + 1)
    ) : TreeNode
}
```

## Tree Construction Algorithm

Input: `List<Session>` from API, each with `.directory` path.

```
1. Collect all unique directory paths from sessions
2. For each path, split by '/' and generate all ancestor segments
3. Deduplicate segments → build a tree of Directory nodes
4. Assign depth to each node (root = 0)
5. Count sessions per directory (exact match, not recursive)
6. Flatten tree to a list respecting visit order:
   - For each directory, emit Directory node
   - If directory.isExpanded, emit its child Directories (recursive) then its Sessions
```

### Path Display

- Replace `/home/user/` prefix with `~/`
- Each Directory node shows only its **leaf segment** (e.g., `oc-remote`, not the full path)
- Full path shown in tooltip/details dialog

## Visual Structure

### Directory Node (`DirectoryTreeNode` composable)

```
[16dp * depth padding]
[▶/▼ arrow] [📁 Folder icon] [Directory Name] [session count badge] [⋮ menu]
```

- Arrow: `Icons.Default.KeyboardArrowRight`, rotates 90° when expanded
- Folder: `Icons.Default.Folder` (collapsed) / `Icons.Default.FolderOpen` (expanded)
- Name: `bodyLarge`, primary color, max 1 line ellipsis
- Session count: `bodySmall`, `onSurfaceVariant`, e.g., "3 sessions"
- Menu: `Icons.Default.MoreVert`, opens `DropdownMenu`

### Session Node (`SessionRow` composable)

```
[16dp * depth padding]
[status dot] [Session Title           ] [diff stats] [⋮ menu]
             [Updated time  ·  status ]
```

- Status dot: Busy = pulsing tertiary dot, Retry = error dot, Idle = none
- Title: `bodyLarge`, max 1 line ellipsis, "Untitled" fallback
- Time: `bodySmall`, `onSurfaceVariant.copy(alpha = MUTED)`
- Diff stats: `+N` (DiffAdded) / `-N` (DiffRemoved), `fontFamily = FontFamily.Monospace`
- Menu: `Icons.Default.MoreVert`, opens `DropdownMenu`
- Selection: long-press enters selection mode, checkbox slides in from left

### Indentation

- Per depth level: `16.dp` horizontal padding (via `padding(start = depth * 16.dp)`)
- No connecting lines (cleaner, matches M3 style)
- Depth 0 = no extra padding (aligned with standard list padding)

## Animations

| Event | Animation | Duration | Token |
|-------|-----------|----------|-------|
| Expand directory | `expandVertically` + `fadeIn` | 200ms | AppMotion.SHORT (if defined, else 200ms) |
| Collapse directory | `shrinkVertically` + `fadeOut` | 150ms | |
| Arrow rotation | `animateFloatAsState` 0°→90° | 200ms | `easeInOutCubic` |
| Session appear on expand | `fadeIn` + `slideInVertically(initialOffsetY = { it / 4 })` | 200ms | |
| Selection checkbox | `expandHorizontally` + `fadeIn` (existing) | | |

## Three-Dot Menu

### Directory Menu

| Action | Icon | Behavior |
|--------|------|----------|
| Copy path | `Icons.Default.ContentCopy` | Copies full path to clipboard, shows Snackbar |
| View details | `Icons.Default.Info` | Opens dialog showing full path, session count, last updated |

### Session Menu

| Action | Icon | Behavior |
|--------|------|----------|
| Rename | `Icons.Default.Edit` | Opens rename dialog (existing) |
| Delete | `Icons.Default.Delete` | Opens delete confirmation dialog (existing) |
| Copy session ID | `Icons.Default.ContentCopy` | Copies session ID to clipboard, shows Snackbar |
| View details | `Icons.Default.Info` | Opens dialog showing ID, status, created/updated times, diff summary |

### Menu Implementation

- Use M3 `DropdownMenu` with `DropdownMenuItem` items
- Menu expanded state: `var expanded by remember { mutableStateOf(false) }`
- Triggered by `IconButton` with `Icons.Default.MoreVert`
- Menu dismisses on outside click or item selection

## AMOLED Adaptation

| Element | Normal | AMOLED |
|---------|--------|--------|
| Directory row background | transparent | transparent |
| Session row background | transparent | transparent |
| Session row pressed | `surfaceVariant.copy(alpha = FAINT)` | `primary.copy(alpha = FAINT)` |
| Arrow + Folder icon | `primary` | `primary` |
| Divider between groups | `outlineVariant.copy(alpha = 0.2f)` | `outlineVariant.copy(alpha = FAINT)` |
| DropdownMenu | `surfaceContainer` | `Color.Black` + `AmoledDefaultBorder` |
| Dialogs | M3 defaults | `AmoledSurface` with `Color.Black` background |

## Expand State Persistence

- `ViewModel` holds `expandedPaths: Set<String>` in `StateFlow`
- Toggle on directory click: `expandedPaths = expandedPaths.toggle(path)`
- Default: only root-level directories expanded (`depth == 0`)

## ViewModel Changes

### Remove

- `ListMode` enum
- `selectProject()` / `navigateBack()` mode switching
- `BackHandler` for mode switching (keep selection mode BackHandler)

### Add

- `expandedPaths: StateFlow<Set<String>>` — which directory paths are expanded
- `toggleDirectory(path: String)` — toggle expand/collapse
- `expandAll()` / `collapseAll()` — optional future actions
- `treeNodes: StateFlow<List<TreeNode>>` — flattened tree for LazyColumn
- Tree construction logic (pure function: `sessions + expandedPaths → List<TreeNode>`)

### Keep

- Selection mode logic (`selectedIds`, `isSelectionMode`, etc.)
- Rename/delete dialog state
- Session creation (FAB)
- `navigateToSession` flow

## Screen Changes (SessionListScreen.kt)

### Before

- TopAppBar: conditional based on `ListMode.PROJECTS` vs `ListMode.SESSIONS`
- Content: `when(mode) { PROJECTS -> ProjectList, SESSIONS -> SessionList }`
- Two `BackHandler`s (mode + selection)

### After

- TopAppBar: single mode, shows "Sessions" title + selection mode variant
- Content: single `LazyColumn` with `items(treeNodes) { node -> ... }`
- One `BackHandler` (selection mode only)
- No mode switching

## File Changes

| File | Action | Lines |
|------|--------|-------|
| `sessions/SessionListScreen.kt` | Rewrite | ~454 → ~350 |
| `sessions/SessionListViewModel.kt` | Major refactor | ~449 → ~500 |
| `sessions/components/SessionRow.kt` | Rewrite | ~332 → ~250 |
| `sessions/components/ProjectGroupRow.kt` | Rewrite → `DirectoryTreeNode.kt` | ~112 → ~120 |
| `sessions/components/ProjectHeader.kt` | Delete | ~53 → 0 |
| `sessions/components/DirectoryRow.kt` | Keep (OpenProjectDialog internal) | unchanged |
| New: `sessions/components/TreeNode.kt` | Create | ~50 |

**Net change:** approximately -80 lines total (simpler architecture, removed dual-mode complexity)

## Out of Scope

- OpenProjectDialog changes (directory browser for adding projects)
- ServerCard changes on HomeScreen
- Navigation animation changes (already fixed in previous commit)
- ChatScreen changes
