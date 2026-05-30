# Design H: Token 统计位置修正 + 嵌套滑动传递修复

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 token 统计显示位置错误和嵌套滑动传递被阻断两个 bug，并统一 halfScreenHeight 公共函数。

**Architecture:** 三处改动相互独立：H1 修正 ChatScreen.kt 中 isTurnLast 的 index 方向；H2 删除 ChatModifiers.kt 中的 consumeBoundaryScroll 函数及其 8 处调用；H3 在 ChatModifiers.kt 新增 halfScreenHeight() 公共函数并替换 8 处内联计算。改动后依赖 Compose 内置嵌套滚动机制。

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose BOM 2026.05.01

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `ChatModifiers.kt` | 修改 | 删除 `consumeBoundaryScroll` 函数 (L54-88)，新增 `halfScreenHeight()` 函数 |
| `ChatScreen.kt` | 修改 | 修正两处 `isTurnLast` 方向 (L1847-1848, L2100-2101) |
| `BashToolCard.kt` | 修改 | 移除 `.consumeBoundaryScroll(scrollState)` (L174)，替换 halfScreenHeight (L164) |
| `WriteToolCard.kt` | 修改 | 同上模式 |
| `ReadToolCard.kt` | 修改 | 同上模式 |
| `EditToolCard.kt` | 修改 | 同上模式 |
| `TaskToolCard.kt` | 修改 | 同上模式（无 200dp 兜底版） |
| `SearchToolCard.kt` | 修改 | 同上模式 |
| `ReasoningBlock.kt` | 修改 | 同上模式 |
| `ToolCardRenderer.kt` | 修改 | 同上模式（scrollState 变量名不同） |

---

### Task 1: H1 — 修正 isTurnLast 方向（ChatScreen.kt）

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt`

- [ ] **Step 1: 修正主会话 isTurnLast（第一处，L1847-1848）**

将：
```kotlin
                                        val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                                                         uiState.messages.getOrNull(index + 1)?.isAssistant != true
```
改为：
```kotlin
                                        val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                                                         (index == 0 || uiState.messages.getOrNull(index - 1)?.isAssistant != true)
```

- [ ] **Step 2: 修正子会话 isTurnLast（第二处，L2100-2101）**

将：
```kotlin
                                                val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                                                                 uiState.messages.getOrNull(index + 1)?.isAssistant != true
```
改为：
```kotlin
                                                val isTurnLast = uiState.messages.getOrNull(index)?.isAssistant == true &&
                                                                 (index == 0 || uiState.messages.getOrNull(index - 1)?.isAssistant != true)
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/ChatScreen.kt
git commit -m "fix: correct isTurnLast direction in newest-first message list (H1)"
```

---

### Task 2: H2 — 删除 consumeBoundaryScroll（ChatModifiers.kt）

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/util/ChatModifiers.kt`

- [ ] **Step 1: 删除 consumeBoundaryScroll 函数**

删除 ChatModifiers.kt 中 L54-L88 的整个 `consumeBoundaryScroll` 函数定义：

```kotlin
// 删除以下全部代码：
internal fun Modifier.consumeBoundaryScroll(scrollState: ScrollState): Modifier {
    val connection = remember(scrollState) {
        object : NestedScrollConnection {
            /** Finger drag reached boundary — consume remaining delta, block propagation to parent */
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val atTop = !scrollState.canScrollBackward
                val atBottom = !scrollState.canScrollForward
                return when {
                    atTop && available.y < 0f -> available
                    atBottom && available.y > 0f -> available
                    else -> Offset.Zero
                }
            }

            /** Inertia fling reached boundary — consume remaining velocity, block propagation to parent */
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                val atTop = !scrollState.canScrollBackward
                val atBottom = !scrollState.canScrollForward
                return when {
                    atTop && available.y < 0f -> available
                    atBottom && available.y > 0f -> available
                    else -> Velocity.Zero
                }
            }
        }
    }
    return this.nestedScroll(connection)
}
```

同时删除不再需要的 import（如果有未使用的 `NestedScrollConnection`、`NestedScrollSource`、`Offset`、`Velocity` 相关 import，需确认其他函数是否仍在使用。注意 `Modifier.codeHorizontalScroll()` 也可能使用部分 import，逐个检查后再删除）。

- [ ] **Step 2: 编译验证**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: 编译错误 — `consumeBoundaryScroll` 在 8 个文件中未解析的引用（预期，下一步修复）

---

### Task 3: H2+H3 — 移除 consumeBoundaryScroll 调用 + 统一 halfScreenHeight（8 个 ToolCard 文件）

**Files:**
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/util/ChatModifiers.kt` (新增 halfScreenHeight 函数)
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/BashToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/WriteToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ReadToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/EditToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/TaskToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/SearchToolCard.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/components/ReasoningBlock.kt`
- Modify: `app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/ToolCardRenderer.kt`

- [ ] **Step 1: 在 ChatModifiers.kt 中新增 halfScreenHeight 函数**

在 ChatModifiers.kt 文件末尾添加：

```kotlin
@Composable
internal fun halfScreenHeight(): Dp {
    return maxOf(LocalConfiguration.current.screenHeightDp.dp / 2, 200.dp)
}
```

确保文件顶部有必要的 import：
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
```

- [ ] **Step 2: 修改 BashToolCard.kt（有 200dp 兜底的模板）**

在 AnimatedVisibility 块内：

将：
```kotlin
                val halfScreenHeight = maxOf(LocalConfiguration.current.screenHeightDp.dp / 2, 200.dp)
```
改为：
```kotlin
                val halfScreenHeight = halfScreenHeight()
```

将 modifier 链中的：
```kotlin
                        .consumeBoundaryScroll(scrollState)
                        .verticalScroll(scrollState)
```
改为：
```kotlin
                        .verticalScroll(scrollState)
```

- [ ] **Step 3: 修改 WriteToolCard.kt**

同 Step 2 模式：替换 `halfScreenHeight` 定义 + 移除 `.consumeBoundaryScroll(scrollState)`。

- [ ] **Step 4: 修改 ReadToolCard.kt**

同 Step 2 模式。

- [ ] **Step 5: 修改 EditToolCard.kt**

同 Step 2 模式。

- [ ] **Step 6: 修改 TaskToolCard.kt（无 200dp 兜底版）**

将：
```kotlin
                val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
```
改为：
```kotlin
                val halfScreenHeight = halfScreenHeight()
```

将 modifier 链中的：
```kotlin
                        .consumeBoundaryScroll(scrollState)
                        .verticalScroll(scrollState)
```
改为：
```kotlin
                        .verticalScroll(scrollState)
```

- [ ] **Step 7: 修改 SearchToolCard.kt**

同 Step 6 模式（无 200dp 兜底）。

- [ ] **Step 8: 修改 ReasoningBlock.kt**

同 Step 6 模式（无 200dp 兜底）。

- [ ] **Step 9: 修改 ToolCardRenderer.kt（注意 scrollState 变量名不同）**

将：
```kotlin
                val halfScreenHeight = LocalConfiguration.current.screenHeightDp.dp / 2
```
改为：
```kotlin
                val halfScreenHeight = halfScreenHeight()
```

将 modifier 链中的：
```kotlin
                        .consumeBoundaryScroll(toolCardScrollState)
                        .verticalScroll(toolCardScrollState)
```
改为：
```kotlin
                        .verticalScroll(toolCardScrollState)
```

- [ ] **Step 10: 编译验证**

Run: `.\gradlew :app:compileDevDebugKotlin`
Expected: BUILD SUCCESSFUL（所有 `consumeBoundaryScroll` 引用已移除，所有 `halfScreenHeight()` 已解析）

- [ ] **Step 11: 运行单元测试**

Run: `.\gradlew :app:testDevDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 12: Commit**

```bash
git add app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/util/ChatModifiers.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/BashToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/WriteToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/ReadToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/EditToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/TaskToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/cards/SearchToolCard.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/components/ReasoningBlock.kt app/src/main/kotlin/dev/minios/ocremote/ui/screens/chat/tools/ToolCardRenderer.kt
git commit -m "fix: remove consumeBoundaryScroll + unify halfScreenHeight (H2+H3)"
```

---

### Task 4: 清理与最终验证

- [ ] **Step 1: 清理不再需要的 import**

检查 ChatModifiers.kt 是否有未使用的 import（`NestedScrollConnection`、`NestedScrollSource` 等），如有则删除。注意 `Modifier.codeHorizontalScroll()` 可能仍需部分 import。

检查 8 个 ToolCard 文件是否有未使用的 import（`consumeBoundaryScroll` 相关），如有则删除。

- [ ] **Step 2: 最终编译 + 测试**

Run: `.\gradlew :app:compileDevDebugKotlin`
Run: `.\gradlew :app:testDevDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 3: Commit cleanup (如有改动)**

```bash
git add -A
git commit -m "chore: cleanup unused imports after H2+H3 refactor"
```

如果无 import 变化，跳过此步。
