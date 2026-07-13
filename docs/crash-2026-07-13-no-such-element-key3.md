# Runtime Crash: NoSuchElementException key=3

**Status**: Pending analysis (deferred until all optimization phases complete)
**Reported**: 2026-07-13
**Source**: Main workspace production build

## Crash Details

- **App**: dev.leonardo.ocremoteplus (1.0.0)
- **Android**: 16 (SDK 36)
- **Device**: OnePlus PLK110
- **Time**: 2026-07-13 10:22:23.468
- **Thread**: main
- **Exception**: `java.util.NoSuchElementException: Cannot find value for key 3`

## Stack Trace (deobfuscated hints)

```
NoSuchElementException: Cannot find value for key 3
  at r8-map-id.r()                          ← Map lookup
  at r8-map-id.c()                          ← Wrapper
  at ic2.compare()                          ← Comparator (LazyColumn sort?)
  at cx.r()                                 ← Comparison dispatch
  at oj3.n()                                ← Snapshot list mutation
  at nj3.e()                                ← Snapshot state update
  at t91.invoke()                           ← Composable lambda (line ~1572 in some file)
  at mq0.j() / lq0.invokeSuspend()         ← Coroutine continuation
  at ... DispatchedTaskKt.resume/dispatch   ← Coroutine dispatch
  at ... dispatchTouchEvent                 ← Touch event triggered
```

## Preliminary Hypotheses

1. **LazyColumn key lookup**: A `key(index)` or `key(item.id)` in a LazyColumn refers to an index/id that was removed from the backing list during scroll/recomposition. The comparator (`ic2.compare`) suggests a sorted list operation.

2. **TurnGroupCalculator**: The `t91.invoke` at line ~1572 could be in `TurnGroupCalculator` which groups messages by turn — a concurrent list mutation while grouping could cause a missing-key lookup.

3. **Concurrent list modification**: The `.compare()` frame suggests a sorting operation on a list that was concurrently modified — an item (key=3) was present when sort started but removed before its comparator ran.

## TODO (after all phases complete)

- [ ] Deobfuscate R8 map IDs against the release mapping file
- [ ] Identify the exact Composable (t91.invoke) — likely ChatMessageList or TurnGroupCalculator
- [ ] Check if this is related to the RS-005 fix (connections guard in collect) or RS-009 (assistantMessageIds)
- [ ] Verify if the race conditions we fixed in phases 1-4 could have caused this
