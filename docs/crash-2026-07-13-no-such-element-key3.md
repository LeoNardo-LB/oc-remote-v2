# Runtime Crash: NoSuchElementException key=3

**Status**: ✅ FIXED (2026-07-13)
**Source**: Main workspace production build v1.0.0

## Crash Details

- **App**: dev.leonardo.ocremoteplus (1.0.0)
- **Android**: 16 (SDK 36)
- **Device**: OnePlus PLK110
- **Time**: 2026-07-13 10:22:23.468
- **Thread**: main
- **Exception**: `java.util.NoSuchElementException: Cannot find value for key 3`

## Root Cause

**`CodeSourceView.kt:258`** used `key = { it }` — a **bare Int** as LazyColumn item key.

Compose LazyColumn internally maintains a `Map<Key, Int>` (key-to-index map) for item
positioning and diffing. When item keys are bare Ints, the key space collides with
Compose's internal index tracking. During pagination (`onLoadMore` changes `visLines`),
the key-to-index map is rebuilt. A touch event during this rebuild triggers a Comparator
lookup on a stale map snapshot → `Map.getValue(3)` → `NoSuchElementException`.

### Trigger Chain

```
User scrolls file viewer (dispatchTouchEvent)
  → snapshotFlow detects near-bottom → onLoadMore()
  → visLines changes → LazyColumn reconfiguration
  → Compose rebuilds internal keyToIndex Map
  → Comparator compares items using stale Map
  → Map.getValue(3) → key 3 not in new Map → CRASH
```

### Evidence

- Project code has **zero** `Map.getValue()` calls and **zero** `Comparator<>` declarations
- The crash originates in Compose runtime internals (R8-obfuscated `r8-map-id.r/c`)
- `CodeSourceView.kt` was the **only** file using bare Int as LazyColumn key
- Chat screen uses String keys (`"u_xxx"`/`"t_xxx"`) — not affected

## Fix

1. **Primary**: `key = { it }` → `key = { index -> "line_$index" }`
   String keys avoid collision with Compose's internal Int key space.

2. **Secondary**: `lineAnnotations[index]!!` → `lineAnnotations[index]?.let { ... } ?: baseLine`
   Eliminates TOCTOU race between `containsKey` check and `!!` force-unwrap (2 locations).

## Verification

- `compileDevDebugKotlin` ✅
- `ui.screens.viewer.*` tests ✅ (all pass)
