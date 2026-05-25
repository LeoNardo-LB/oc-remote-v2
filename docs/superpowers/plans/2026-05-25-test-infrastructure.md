# 测试基础设施建立 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 oc-remote 项目建立单元测试基础设施，覆盖 EventReducer 和 ChatViewModel 中与 Permission 流相关的核心逻辑。

**Architecture:** 纯 JVM 单元测试（`app/src/test/`），不依赖 Android 框架、不需要模拟器。EventReducer 直接构造测试（仅需 `mockkStatic(Log::class)` 抑制 Android Log 桩方法，被测方法本身不调用 Log）；ChatViewModel 通过 MockK mock 依赖注入（api/draftRepository/settingsRepository），使用 `UnconfinedTestDispatcher` 替代 `Dispatchers.Main`，通过 `SavedStateHandle(mapOf(...))` 传入导航参数。使用 Turbine 断言 StateFlow 发射值。

**Tech Stack:** JUnit 4.13.2, MockK 1.14.9, Turbine 1.2.1, kotlinx-coroutines-test 1.10.2

### Prerequisites

| 要求 | 说明 |
|------|------|
| **JDK** | JDK 17+（Temurin），命令中使用 `D:\Develop\Scoop\apps\temurin17-jdk\current` |
| **Gradle** | 项目自带 Gradle Wrapper，运行 `.\gradlew.bat --version` 确认版本 |
| **OS** | Windows（命令使用 PowerShell 5.1 语法）。macOS/Linux 需调整 JAVA_HOME 和目录创建命令 |
| **网络** | 需访问 Maven Central/Google Maven。代理配置见下方 Environment Setup；无代理环境可移除 `GRADLE_OPTS` |
| **知识** | 熟悉 MockK（mock/coEvery/coVerify/mockkStatic）、Turbine Flow 测试、kotlinx-coroutines-test（runTest/TestDispatcher）、StateFlow/MutableStateFlow |

### Environment Setup

在执行任何 Task 之前，先设置环境变量（PowerShell）：

```powershell
$env:JAVA_HOME = "D:\Develop\Scoop\apps\temurin17-jdk\current"
# 如果需要代理访问 Maven 仓库（如公司内网/防火墙环境）：
$env:GRADLE_OPTS = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"
# 如果可直接访问外网，跳过 GRADLE_OPTS 设置
```

后续所有 `gradlew` 命令假定以上环境变量已设置，不再重复。

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 修改 | `app/build.gradle.kts` | 添加测试依赖 |
| 创建 | `app/src/test/kotlin/dev/minios/ocremote/data/repository/EventReducerTest.kt` | EventReducer 单元测试 |
| 创建 | `app/src/test/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModelPermissionTest.kt` | ChatViewModel 权限相关测试 |

---

## Task 1: 补充 build.gradle.kts 测试依赖

**Files:**
- Modify: `app/build.gradle.kts:144-146`

在现有的 `testImplementation("junit:junit:4.13.2")` 和 `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")` 之后，添加新依赖。

- [ ] **Step 1: 修改 build.gradle.kts**

将第 144-146 行：

```kotlin
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```

改为：

```kotlin
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("app.cash.turbine:turbine:1.2.1")
```

> **兼容性说明：** kotlinx-coroutines-test 从 1.8.1 升级到 1.10.2，需确认与项目 Kotlin 版本兼容。如果 Gradle sync 失败：① 检查 `build.gradle.kts` 中的 `kotlinVersion` 是否 ≥ 1.9.0；② 如果 1.10.2 不兼容，回退到 1.8.1 并在测试中使用 `TestCoroutineDispatcher` 替代 `runTest`；③ 如果 MockK 1.14.9 不兼容，尝试 1.13.12。

- [ ] **Step 2: 验证 Gradle sync**

Run: `.\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add MockK, Turbine test dependencies and upgrade coroutines-test"
```

---

## Task 2: EventReducer 权限逻辑单元测试

> **注意：** EventReducer 导入了 `android.util.Log` 和 `BuildConfig`，但在 `src/test/` 路径下 Android 桩方法（stub）自动可用。被测试的 `setPermissions`/`removePermission`/`clearAll` 方法内部不调用 Log，因此无需额外 mock。如果未来测试触发 Log 调用的方法，需添加 `mockkStatic(Log::class)`。

**Files:**
- Create: `app/src/test/kotlin/dev/minios/ocremote/data/repository/EventReducerTest.kt`

EventReducer 是 `@Inject constructor()` 无参构造，内部状态全部是 `MutableStateFlow`。**零 mock 需求**——直接实例化、调用方法、读取 `permissions` flow 值。

- [ ] **Step 1: 创建测试目录和文件**

```bash
New-Item -ItemType Directory -Path "app\src\test\kotlin\dev\minios\ocremote\data\repository" -Force
```

- [ ] **Step 2: 编写 EventReducerTest.kt**

```kotlin
package dev.minios.ocremote.data.repository

import app.cash.turbine.test
import dev.minios.ocremote.domain.model.SessionStatus
import dev.minios.ocremote.domain.model.SseEvent
import dev.minios.ocremote.domain.model.ToolRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventReducerTest {

    private lateinit var reducer: EventReducer

    @Before
    fun setup() {
        reducer = EventReducer()
    }

    // ============ setPermissions ============

    @Test
    fun `setPermissions adds permissions for session`() = runTest {
        val permission = createTestPermission("p1", "session1")
        reducer.setPermissions("session1", listOf(permission))

        val result = reducer.permissions.value
        assertEquals(1, result["session1"]?.size)
        assertEquals("p1", result["session1"]?.firstOrNull()?.id)
    }

    @Test
    fun `setPermissions with empty list removes session entry`() = runTest {
        // First add, then remove with empty list
        reducer.setPermissions("session1", listOf(createTestPermission("p1", "session1")))
        reducer.setPermissions("session1", emptyList())

        val result = reducer.permissions.value
        assertTrue(result.containsKey("session1").not())
    }

    @Test
    fun `setPermissions replaces existing permissions`() = runTest {
        reducer.setPermissions("session1", listOf(createTestPermission("p1", "session1")))
        reducer.setPermissions("session1", listOf(createTestPermission("p2", "session1")))

        val result = reducer.permissions.value
        assertEquals(1, result["session1"]?.size)
        assertEquals("p2", result["session1"]?.firstOrNull()?.id)
    }

    @Test
    fun `setPermissions does not affect other sessions`() = runTest {
        reducer.setPermissions("session1", listOf(createTestPermission("p1", "session1")))
        reducer.setPermissions("session2", listOf(createTestPermission("p2", "session2")))

        val result = reducer.permissions.value
        assertEquals(1, result["session1"]?.size)
        assertEquals(1, result["session2"]?.size)
    }

    // ============ removePermission ============

    @Test
    fun `removePermission removes specific permission from all sessions`() = runTest {
        val perm1 = createTestPermission("p1", "session1")
        val perm2 = createTestPermission("p2", "session1")
        reducer.setPermissions("session1", listOf(perm1, perm2))

        reducer.removePermission("p1")

        val result = reducer.permissions.value
        assertEquals(1, result["session1"]?.size)
        assertEquals("p2", result["session1"]?.firstOrNull()?.id)
    }

    @Test
    fun `removePermission with non-existent id does nothing`() = runTest {
        reducer.setPermissions("session1", listOf(createTestPermission("p1", "session1")))
        reducer.removePermission("nonexistent")

        val result = reducer.permissions.value
        assertEquals(1, result["session1"]?.size)
    }

    @Test
    fun `removePermission only affects target id across multiple sessions`() = runTest {
        // p1 exists in both sessions
        reducer.setPermissions("session1", listOf(createTestPermission("p1", "session1")))
        reducer.setPermissions("session2", listOf(createTestPermission("p1", "session2")))

        reducer.removePermission("p1")

        val result = reducer.permissions.value
        assertTrue(result["session1"].isNullOrEmpty())
        assertTrue(result["session2"].isNullOrEmpty())
    }

    // ============ Flow emission via Turbine ============

    @Test
    fun `permissions flow emits updated value after setPermissions`() = runTest {
        reducer.permissions.test {
            // Initial value
            assertEquals(emptyMap<String, List<SseEvent.PermissionAsked>>(), awaitItem())

            reducer.setPermissions("s1", listOf(createTestPermission("p1", "s1")))
            val updated = awaitItem()
            assertEquals(1, updated["s1"]?.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permissions flow emits updated value after removePermission`() = runTest {
        reducer.setPermissions("s1", listOf(
            createTestPermission("p1", "s1"),
            createTestPermission("p2", "s1")
        ))

        reducer.permissions.test {
            // Skip initial emission
            awaitItem()

            reducer.removePermission("p1")
            val updated = awaitItem()
            assertEquals(1, updated["s1"]?.size)
            assertEquals("p2", updated["s1"]?.firstOrNull()?.id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ============ clearAll / clearForServer ============

    @Test
    fun `clearAll resets permissions to empty`() = runTest {
        reducer.setPermissions("s1", listOf(createTestPermission("p1", "s1")))
        reducer.clearAll()

        assertEquals(emptyMap<String, List<SseEvent.PermissionAsked>>(), reducer.permissions.value)
    }

    // ============ Helper ============

    private fun createTestPermission(
        id: String,
        sessionId: String,
        permission: String = "bash",
        patterns: List<String> = listOf("/home/user/project"),
        always: Boolean = false
    ): SseEvent.PermissionAsked {
        return SseEvent.PermissionAsked(
            id = id,
            sessionId = sessionId,
            permission = permission,
            patterns = patterns,
            metadata = null,
            always = always,
            tool = null
        )
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `.\gradlew.bat test --tests "dev.minios.ocremote.data.repository.EventReducerTest" --no-daemon 2>&1 | Select-Object -Last 20`

Expected: `BUILD SUCCESSFUL`, 10 tests completed, 0 failures

- [ ] **Step 4: Commit**

```bash
git add app/src/test/
git commit -m "test: add EventReducer permission logic unit tests (10 tests)"
```

---

## Task 3: ChatViewModel 权限逻辑单元测试

**Files:**
- Create: `app/src/test/kotlin/dev/minios/ocremote/ui/screens/chat/ChatViewModelPermissionTest.kt`

ChatViewModel 是 `@HiltViewModel`，构造函数需要 `SavedStateHandle` 和多个依赖。测试方案：
- **EventReducer**：使用真实实例（无参构造）
- **API/DraftRepo/SettingsRepo**：通过 MockK mock
- **SavedStateHandle**：通过 `SavedStateHandle(mapOf(...))` 传入导航参数（serverUrl/username/password/serverName/serverId/sessionId）
- **viewModelScope**：通过 `Dispatchers.setMain(UnconfinedTestDispatcher())` 替代 Main dispatcher
- **Android Log**：通过 `mockkStatic(Log::class)` 抑制桩方法
- **Init 块**：ViewModel 构造时触发多个 API 调用，需在构造前设置好所有 mock stub

测试重点：
1. `loadPendingPermissions()` — PermissionRequest DTO 到 PermissionAsked 的映射（metadata/always/tool）
2. `replyToPermission()` — 乐观清除、API 返回 false 时保留、异常时保留
3. 多 Session 隔离 — 只加载当前 session 的权限

- [ ] **Step 1: 创建测试文件**

确保目录存在：
```bash
New-Item -ItemType Directory -Path "app\src\test\kotlin\dev\minios\ocremote\ui\screens\chat" -Force
```

- [ ] **Step 2: 编写 ChatViewModelPermissionTest.kt**

```kotlin
package dev.minios.ocremote.ui.screens.chat

import android.util.Log
import app.cash.turbine.test
import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.api.PermissionRequest
import dev.minios.ocremote.data.api.ProvidersResponse
import dev.minios.ocremote.data.repository.DraftRepository
import dev.minios.ocremote.data.repository.EventReducer
import dev.minios.ocremote.data.repository.SettingsRepository
import dev.minios.ocremote.domain.model.Session
import dev.minios.ocremote.domain.model.SseEvent
import dev.minios.ocremote.domain.model.ToolRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.SavedStateHandle

/**
 * Pure-JVM unit tests for ChatViewModel's permission-related logic.
 *
 * Uses [UnconfinedTestDispatcher] so viewModelScope coroutines execute eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPermissionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var eventReducer: EventReducer
    private lateinit var api: OpenCodeApi
    private lateinit var draftRepository: DraftRepository
    private lateinit var settingsRepository: SettingsRepository

    private val testSessionId = "session-123"
    private val testServerId = "server-1"
    private val testDirectory = "/home/user/project"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        eventReducer = EventReducer()

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        // Create fresh mocks per test to avoid stub ordering issues
        api = mockk(relaxed = true)
        draftRepository = mockk(relaxed = true)
        settingsRepository = mockk()

        every { draftRepository.getDraft(any()) } returns null

        every { settingsRepository.hiddenModels(any()) } returns flowOf(emptySet())
        every { settingsRepository.terminalFontSize } returns flowOf(13f)
        every { settingsRepository.initialMessageCount } returns flowOf(50)
        every { settingsRepository.chatFontSize } returns flowOf("medium")
        every { settingsRepository.codeWordWrap } returns flowOf(false)
        every { settingsRepository.confirmBeforeSend } returns flowOf(false)
        every { settingsRepository.compactMessages } returns flowOf(false)
        every { settingsRepository.collapseTools } returns flowOf(false)
        every { settingsRepository.hapticFeedback } returns flowOf(true)
        every { settingsRepository.keepScreenOn } returns flowOf(false)
        every { settingsRepository.compressImageAttachments } returns flowOf(true)
        every { settingsRepository.imageAttachmentMaxLongSide } returns flowOf(1440)
        every { settingsRepository.imageAttachmentWebpQuality } returns flowOf(60)

        // Init block API stubs — defaults that tests can override
        coEvery { api.getSession(any(), any()) } returns createTestSession()
        coEvery { api.listMessages(any(), any(), any()) } returns emptyList()
        coEvery { api.listPendingQuestions(any(), any()) } returns emptyList()
        coEvery { api.getProviders(any()) } returns ProvidersResponse(emptyList())
        coEvery { api.listAgents(any()) } returns emptyList()
        coEvery { api.listCommands(any()) } returns emptyList()
        // NOTE: listPendingPermissions is NOT set here — each test sets its own stub
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        sessionId: String = testSessionId,
        serverId: String = testServerId
    ): ChatViewModel {
        val savedState = SavedStateHandle(mapOf(
            "serverUrl"  to "http://localhost:8080",
            "username"   to "testuser",
            "password"   to "testpass",
            "serverName" to "TestServer",
            "serverId"   to serverId,
            "sessionId"  to sessionId
        ))
        return ChatViewModel(
            savedStateHandle = savedState,
            eventReducer = eventReducer,
            api = api,
            draftRepository = draftRepository,
            settingsRepository = settingsRepository
        )
    }

    private fun createTestSession(
        id: String = testSessionId,
        directory: String = testDirectory
    ): Session = Session(
        id = id,
        title = "Test Session",
        directory = directory,
        time = Session.Time(created = 1000L, updated = 2000L)
    )

    private fun createTestPermissionRequest(
        id: String = "perm-1",
        sessionId: String = testSessionId,
        permission: String = "bash",
        patterns: List<String> = listOf("/home/user/project"),
        metadata: Map<String, JsonPrimitive>? = null,
        always: List<String> = emptyList(),
        tool: ToolRef? = null
    ): PermissionRequest = PermissionRequest(
        id = id,
        sessionId = sessionId,
        permission = permission,
        patterns = patterns,
        metadata = metadata,
        always = always,
        tool = tool
    )

    // ============================================================
    // Sanity checks: verify init block coroutines execute
    // ============================================================

    @Test
    fun `init block executes — getSession API is called`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns emptyList()
        createViewModel()
        coVerify { api.getSession(any(), testSessionId) }
    }

    @Test
    fun `init block executes — permissions API is called`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns emptyList()
        createViewModel()
        coVerify { api.listPendingPermissions(any(), any()) }
    }

    @Test
    fun `EventReducer setPermissions works directly`() = runTest {
        val perm = SseEvent.PermissionAsked(id = "p1", sessionId = testSessionId, permission = "bash")
        eventReducer.setPermissions(testSessionId, listOf(perm))
        assertEquals(1, eventReducer.permissions.value[testSessionId]?.size)
        assertEquals("p1", eventReducer.permissions.value[testSessionId]?.firstOrNull()?.id)
    }

    // ============================================================
    // Tests: loadPendingPermissions
    // ============================================================

    @Test
    fun `loadPendingPermissions maps and stores permission`() = runTest {
        val permRequest = createTestPermissionRequest(
            id = "perm-1",
            sessionId = testSessionId,
            permission = "bash",
            patterns = listOf("/home/user"),
            metadata = mapOf("key" to JsonPrimitive("value")),
            always = listOf("pattern1")
        )
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(permRequest)

        val vm = createViewModel()

        // Check EventReducer directly (source of truth)
        val reducerPerms = eventReducer.permissions.value
        assertEquals("EventReducer should have 1 permission for session, got: $reducerPerms",
            1, reducerPerms[testSessionId]?.size)
        assertEquals("perm-1", reducerPerms[testSessionId]?.firstOrNull()?.id)
        assertEquals("bash", reducerPerms[testSessionId]?.firstOrNull()?.permission)
        assertEquals(true, reducerPerms[testSessionId]?.firstOrNull()?.always)
        assertEquals(mapOf("key" to "value"), reducerPerms[testSessionId]?.firstOrNull()?.metadata)
    }

    @Test
    fun `loadPendingPermissions filters by session ID`() = runTest {
        val perm1 = createTestPermissionRequest(id = "p1", sessionId = testSessionId)
        val perm2 = createTestPermissionRequest(id = "p2", sessionId = "other-session")
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(perm1, perm2)

        val vm = createViewModel()

        val reducerPerms = eventReducer.permissions.value
        assertEquals(1, reducerPerms[testSessionId]?.size)
        assertEquals("p1", reducerPerms[testSessionId]?.firstOrNull()?.id)
        assertTrue(reducerPerms["other-session"].isNullOrEmpty())
    }

    @Test
    fun `loadPendingPermissions empty result — no permissions stored`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns emptyList()

        val vm = createViewModel()

        assertTrue(eventReducer.permissions.value.isEmpty())
    }

    @Test
    fun `loadPendingPermissions maps metadata`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(
                id = "pm",
                metadata = mapOf(
                    "str" to JsonPrimitive("hello"),
                    "num" to JsonPrimitive(42),
                    "bool" to JsonPrimitive(true)
                )
            )
        )

        createViewModel()

        val perm = eventReducer.permissions.value[testSessionId]?.firstOrNull()
        assertNotNull(perm)
        assertEquals("hello", perm?.metadata?.get("str"))
        assertEquals("42", perm?.metadata?.get("num"))
        assertEquals("true", perm?.metadata?.get("bool"))
    }

    @Test
    fun `loadPendingPermissions maps always field`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "p-no", always = emptyList()),
            createTestPermissionRequest(id = "p-yes", always = listOf("pattern"))
        )

        createViewModel()

        val perms = eventReducer.permissions.value[testSessionId]
        assertEquals(2, perms?.size)
        assertFalse(perms?.first { it.id == "p-no" }?.always ?: true)
        assertTrue(perms?.first { it.id == "p-yes" }?.always ?: false)
    }

    @Test
    fun `loadPendingPermissions API exception does not crash`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } throws RuntimeException("err")

        createViewModel() // Should not throw

        assertTrue(eventReducer.permissions.value.isEmpty())
    }

    @Test
    fun `loadPendingPermissions maps tool ref`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "pt", tool = ToolRef(messageId = "m1", callId = "c1"))
        )

        createViewModel()

        val perm = eventReducer.permissions.value[testSessionId]?.firstOrNull()
        assertNotNull(perm)
        assertEquals("m1", perm?.tool?.messageId)
        assertEquals("c1", perm?.tool?.callId)
    }

    // ============================================================
    // Tests: replyToPermission
    // ============================================================

    @Test
    fun `replyToPermission calls API and removes permission`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "perm-reply")
        )
        coEvery { api.replyToPermission(any(), any(), any(), any(), any()) } returns true

        val vm = createViewModel()
        assertEquals("Precondition: 1 permission loaded",
            1, eventReducer.permissions.value[testSessionId]?.size)

        vm.replyToPermission("perm-reply", "once")

        coVerify { api.replyToPermission(any(), "perm-reply", "once", any(), any()) }
        assertTrue(eventReducer.permissions.value[testSessionId].isNullOrEmpty())
    }

    @Test
    fun `replyToPermission with reply=always`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "pa")
        )
        coEvery { api.replyToPermission(any(), any(), any(), any(), any()) } returns true

        val vm = createViewModel()

        vm.replyToPermission("pa", "always")

        coVerify { api.replyToPermission(any(), "pa", "always", any(), any()) }
        assertTrue(eventReducer.permissions.value[testSessionId].isNullOrEmpty())
    }

    @Test
    fun `replyToPermission with reply=reject`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "pr")
        )
        coEvery { api.replyToPermission(any(), any(), any(), any(), any()) } returns true

        val vm = createViewModel()

        vm.replyToPermission("pr", "reject")

        coVerify { api.replyToPermission(any(), "pr", "reject", any(), any()) }
        assertTrue(eventReducer.permissions.value[testSessionId].isNullOrEmpty())
    }

    @Test
    fun `replyToPermission does NOT remove when API returns false`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "pf")
        )
        coEvery { api.replyToPermission(any(), any(), any(), any(), any()) } returns false

        val vm = createViewModel()

        vm.replyToPermission("pf", "once")

        assertEquals(1, eventReducer.permissions.value[testSessionId]?.size)
    }

    @Test
    fun `replyToPermission spares other permissions`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "p1", permission = "bash"),
            createTestPermissionRequest(id = "p2", permission = "write")
        )
        coEvery { api.replyToPermission(any(), "p1", any(), any(), any()) } returns true

        val vm = createViewModel()

        vm.replyToPermission("p1", "once")

        val perms = eventReducer.permissions.value[testSessionId]
        assertEquals(1, perms?.size)
        assertEquals("p2", perms?.firstOrNull()?.id)
        assertEquals("write", perms?.firstOrNull()?.permission)
    }

    @Test
    fun `replyToPermission API exception keeps permission`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "pe")
        )
        coEvery { api.replyToPermission(any(), any(), any(), any(), any()) } throws RuntimeException("err")

        val vm = createViewModel()

        vm.replyToPermission("pe", "once")

        assertEquals(1, eventReducer.permissions.value[testSessionId]?.size)
    }

    // ============================================================
    // Tests: multi-session
    // ============================================================

    @Test
    fun `multi-session — only current session permissions loaded into EventReducer`() = runTest {
        coEvery { api.listPendingPermissions(any(), any()) } returns listOf(
            createTestPermissionRequest(id = "p1", sessionId = testSessionId),
            createTestPermissionRequest(id = "p2", sessionId = "session-456")
        )

        createViewModel()

        // Only current session's permissions are stored (filter is by sessionId)
        assertEquals(1, eventReducer.permissions.value[testSessionId]?.size)
        assertEquals("p1", eventReducer.permissions.value[testSessionId]?.firstOrNull()?.id)
        // session-456 is NOT loaded because loadPendingPermissions only stores
        // permissions matching the ViewModel's own sessionId
        assertTrue(eventReducer.permissions.value["session-456"].isNullOrEmpty())
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `.\gradlew.bat test --tests "dev.minios.ocremote.ui.screens.chat.ChatViewModelPermissionTest" --no-daemon 2>&1 | Select-Object -Last 20`

Expected: `BUILD SUCCESSFUL`, 17 tests completed, 0 failures

- [ ] **Step 4: Commit**

```bash
git add app/src/test/
git commit -m "test: add ChatViewModel permission logic unit tests (17 tests)"
```

---

## Task 4: 运行全部测试 + 编译验证

**Files:** 无新文件

- [ ] **Step 1: 运行全部单元测试**

Run: `.\gradlew.bat test --no-daemon 2>&1 | Select-Object -Last 30`

Expected: `BUILD SUCCESSFUL`, 27 tests completed (10 EventReducer + 17 ChatViewModel), 0 failures. 确认输出中包含 `EventReducerTest` 和 `ChatViewModelPermissionTest` 两个测试类。

- [ ] **Step 2: 运行完整编译确认无回归**

Run: `.\gradlew.bat assembleDebug --no-daemon 2>&1 | Select-String "BUILD|FAIL|error:"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Final commit if any adjustments needed**

---

## Troubleshooting

| 症状 | 可能原因 | 解决方案 |
|------|----------|----------|
| `BUILD FAILED` + 依赖下载错误 | 网络问题 | 检查代理设置（GRADLE_OPTS）或移除代理直接连接 |
| `BUILD FAILED` + 编译错误 | 版本不兼容 | 检查 Kotlin 版本 ≥ 1.9.0；coroutines-test 回退到 1.8.1 |
| `RuntimeException("Stub!")` | Android API 未 mock | 检查是否遗漏 `mockkStatic(Log::class)` |
| `NoSuchMethodError` / `ClassNotFoundException` | 依赖冲突 | 运行 `.\gradlew.bat dependencies` 检查版本冲突 |
| 测试数量不匹配 | 测试过滤表达式错误 | 确认 `--tests` 参数匹配正确的包路径 |
| `IllegalStateException: No main dispatcher` | Dispatchers.Main 未设置 | 检查 `Dispatchers.setMain(UnconfinedTestDispatcher())` 在 @Before 中调用 |
