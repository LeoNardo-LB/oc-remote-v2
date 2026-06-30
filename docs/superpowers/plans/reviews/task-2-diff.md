## Task 2 Diff: 4160a4a6..1d8f6496

### Commits

1d8f6496 refactor(viewer): FileViewerViewModel @AssistedInject + remove SavedStateHandle

### Stat

 .../ui/screens/viewer/FileViewerViewModel.kt       |  78 ++++--------
 .../ui/navigation/routes/FileViewerNavTest.kt      | 100 ----------------
 .../ui/screens/viewer/FileViewerViewModelTest.kt   | 131 +++++++++------------
 3 files changed, 73 insertions(+), 236 deletions(-)

### Full Diff

diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt
index c8a5b069..60e8124b 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModel.kt
@@ -1,71 +1,71 @@
 ﻿package dev.leonardo.ocremotev2.ui.screens.viewer
 
-import androidx.lifecycle.SavedStateHandle
 import androidx.lifecycle.ViewModel
 import androidx.lifecycle.viewModelScope
-import dagger.hilt.android.lifecycle.HiltViewModel
+import dagger.assisted.Assisted
+import dagger.assisted.AssistedFactory
+import dagger.assisted.AssistedInject
 import dev.leonardo.ocremotev2.R
 import dev.leonardo.ocremotev2.domain.model.Annotation
 import dev.leonardo.ocremotev2.domain.model.ContentType
 import dev.leonardo.ocremotev2.domain.model.VcsDiffMode
 import dev.leonardo.ocremotev2.domain.repository.ToolSnapshotCache
 import dev.leonardo.ocremotev2.domain.usecase.GetFileContentUseCase
 import dev.leonardo.ocremotev2.domain.usecase.GetFileDiffUseCase
 import dev.leonardo.ocremotev2.domain.usecase.SubmitAnnotationsUseCase
-import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav
-import dev.leonardo.ocremotev2.ui.navigation.routes.ServerRouteParams
 import kotlinx.coroutines.flow.MutableStateFlow
 import kotlinx.coroutines.flow.StateFlow
 import kotlinx.coroutines.flow.asStateFlow
 import kotlinx.coroutines.flow.update
 import kotlinx.coroutines.launch
-import java.net.URLDecoder
 import javax.inject.Inject
 
-@HiltViewModel
-class FileViewerViewModel @Inject constructor(
-    private val savedStateHandle: SavedStateHandle,
+class FileViewerViewModel @AssistedInject constructor(
+    @Assisted private val params: FileViewerParams,
     private val getFileContent: GetFileContentUseCase,
     private val getFileDiff: GetFileDiffUseCase,
     private val toolSnapshotCache: ToolSnapshotCache,
     private val submitAnnotationsUseCase: SubmitAnnotationsUseCase
 ) : ViewModel() {
-    private val serverId = savedStateHandle.get<String>(ServerRouteParams.PARAM_SERVER_ID).orEmpty()
-    private val directory = URLDecoder.decode(savedStateHandle.get<String>(FileViewerNav.PARAM_DIRECTORY).orEmpty(), "UTF-8")
-    private val filePath = URLDecoder.decode(savedStateHandle.get<String>(FileViewerNav.PARAM_FILE_PATH).orEmpty(), "UTF-8")
-    private val source = savedStateHandle.get<String>(FileViewerNav.PARAM_SOURCE) ?: FileViewerNav.Source.LIVE
-    private val sessionId = URLDecoder.decode(savedStateHandle.get<String>(FileViewerNav.PARAM_SESSION_ID).orEmpty(), "UTF-8")
-    private val toolPartIds = URLDecoder.decode(
-        savedStateHandle.get<String>(FileViewerNav.PARAM_TOOL_PART_IDS).orEmpty(), "UTF-8"
-    ).split(",").filter { it.isNotBlank() }
+    private val serverId = params.serverId
+    private val directory = params.directory
+    private val filePath = params.filePath
+    private val source = params.source
+    private val sessionId = params.sessionId
+    private val toolPartIds = params.toolPartIds
     private val _uiState = MutableStateFlow(FileViewerUiState(filePath = filePath, directory = directory))
     val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()
     private val diffParser = DiffParser()
     private var annotationManager: AnnotationManager? = null
 
     // Phase 4: pagination — cache full content for loadMore slicing
     private var fullContentCache: String = ""
 
+    @AssistedFactory
+    interface Factory {
+        fun create(params: FileViewerParams): FileViewerViewModel
+    }
+
     private companion object {
         const val INITIAL_PAGE_SIZE = 500
         const val PAGE_SIZE = 500
         const val EXTREMELY_LARGE_THRESHOLD = 100_000
         const val EXTREMELY_LARGE_INITIAL = 10_000
     }
 
     init {
         when (source) {
-            FileViewerNav.Source.LIVE -> loadLive()
-            FileViewerNav.Source.GIT_DIFF -> loadGitDiff()
-            FileViewerNav.Source.TOOL_SNAPSHOT -> loadToolSnapshot()
-            FileViewerNav.Source.TOOL_SNAPSHOT_DIFF -> loadToolSnapshotDiff()
+            FileViewerSource.LIVE -> loadLive()
+            FileViewerSource.GIT_DIFF -> loadGitDiff()
+            FileViewerSource.TOOL_SNAPSHOT -> loadToolSnapshot()
+            FileViewerSource.TOOL_SNAPSHOT_DIFF -> loadToolSnapshotDiff()
         }
     }
 
     private fun loadLive() {
         viewModelScope.launch {
             getFileContent(serverId, directory, filePath)
                 .onSuccess { c ->
                     if (c.type == ContentType.BINARY) {
                         val ft = FileType.fromExtension(filePath)
                         if (ft == FileType.IMAGE) {
@@ -77,23 +77,20 @@ class FileViewerViewModel @Inject constructor(
                     else {
                         fullContentCache = c.content
                         val totalLines = if (c.content.isEmpty()) 0
                                          else c.content.count { it == '\n' } + if (c.content.endsWith('\n')) 0 else 1
                         val extremelyLarge = totalLines > EXTREMELY_LARGE_THRESHOLD
                         val initialVisible = if (extremelyLarge) EXTREMELY_LARGE_INITIAL
                                              else minOf(totalLines, INITIAL_PAGE_SIZE)
                         val visible = takeFirstLines(c.content, initialVisible)
                         // AnnotationManager uses full content so line numbers stay correct after loadMore
                         annotationManager = AnnotationManager(fullContentCache)
-                        // Phase 4: restore annotations from SavedStateHandle (rotation survival)
-                        val restored = restoreAnnotationsFromHandle()
-                        if (restored.isNotEmpty()) annotationManager?.restore(restored)
                         _uiState.update {
                             it.copy(
                                 isLoading = false,
                                 mode = FileViewerMode.SOURCE,
                                 content = visible,
                                 isEmpty = visible.isBlank(),
                                 fileType = FileType.fromExtension(filePath),
                                 renderMode = defaultRenderMode(filePath),
                                 totalLineCount = totalLines,
                                 visibleLineCount = initialVisible,
@@ -158,87 +155,52 @@ class FileViewerViewModel @Inject constructor(
     fun prevHunk() { _uiState.update { it.copy(currentHunkIndex = (it.currentHunkIndex - 1).coerceAtLeast(0)) } }
 
     // ============ Phase 3: Annotation Management ============
 
     fun addAnnotation(selectedText: String, startChar: Int, endChar: Int, note: String) {
         val manager = annotationManager ?: return
         if (_uiState.value.mode != FileViewerMode.SOURCE) return
         manager.add(selectedText, startChar, endChar, note)
         val all = manager.getAll()
         _uiState.update { it.copy(annotations = all) }
-        saveAnnotationsToHandle(all)
     }
 
     fun deleteAnnotation(id: String) {
         val manager = annotationManager ?: return
         manager.delete(id)
         val all = manager.getAll()
         _uiState.update { it.copy(annotations = all) }
-        saveAnnotationsToHandle(all)
     }
 
     fun updateAnnotation(id: String, note: String) {
         val manager = annotationManager ?: return
         manager.update(id, note)
         val all = manager.getAll()
         _uiState.update { it.copy(annotations = all) }
-        saveAnnotationsToHandle(all)
     }
 
     suspend fun submitAnnotations(overallNote: String, editedNotes: Map<String, String> = emptyMap()): Result<Unit> {
         val manager = annotationManager ?: return Result.failure(IllegalStateException("No annotation manager"))
         // Apply any edited notes before submitting
         editedNotes.forEach { (id, newNote) -> manager.update(id, newNote) }
         val anns = manager.getAll()
         if (anns.isEmpty()) return Result.failure(IllegalStateException("No annotations to submit"))
         val result = submitAnnotationsUseCase(serverId, sessionId, anns, overallNote, filePath, directory)
         if (result.isSuccess) {
             manager.clear()
             annotationManager = null
             fullContentCache = ""
             _uiState.update { it.copy(annotations = emptyList(), content = "", isEmpty = true) }
-            savedStateHandle.remove<Any>("annotations_flat")
         }
         return result
     }
 
-    // ============ Phase 4: Annotation SavedStateHandle persistence ============
-
-    private fun saveAnnotationsToHandle(annotations: List<Annotation>) {
-        if (annotations.isEmpty()) {
-            savedStateHandle.remove<Any>("annotations_flat")
-            return
-        }
-        val flat = ArrayList<Any>(annotations.size * 11)
-        annotations.forEach { ann ->
-            flat.add(ann.id); flat.add(ann.index); flat.add(ann.startChar); flat.add(ann.endChar)
-            flat.add(ann.startLine); flat.add(ann.startCol); flat.add(ann.endLine); flat.add(ann.endCol)
-            flat.add(ann.selectedText); flat.add(ann.note); flat.add(ann.createdAt)
-        }
-        savedStateHandle["annotations_flat"] = flat
-    }
-
-    private fun restoreAnnotationsFromHandle(): List<Annotation> {
-        val raw = savedStateHandle.get<ArrayList<*>>("annotations_flat") ?: return emptyList()
-        if (raw.isEmpty()) return emptyList()
-        return raw.chunked(11).map { items ->
-            Annotation(
-                id = items[0] as String, index = items[1] as Int,
-                startChar = items[2] as Int, endChar = items[3] as Int,
-                startLine = items[4] as Int, startCol = items[5] as Int,
-                endLine = items[6] as Int, endCol = items[7] as Int,
-                selectedText = items[8] as String, note = items[9] as String,
-                createdAt = items[10] as Long
-            )
-        }
-    }
-
     // ============ Phase 2: Multi-format render toggle ============
 
     private fun defaultRenderMode(path: String): FileViewerRenderMode =
         if (FileType.fromExtension(path).supportsRender) FileViewerRenderMode.RENDER_PREVIEW
         else FileViewerRenderMode.SOURCE
 
     fun toggleRenderMode() {
         val current = _uiState.value
         if (!current.fileType.supportsRender || current.mode == FileViewerMode.DIFF) return
         _uiState.update {
diff --git a/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNavTest.kt b/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNavTest.kt
deleted file mode 100644
index d0855b62..00000000
--- a/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNavTest.kt
+++ /dev/null
@@ -1,100 +0,0 @@
-﻿package dev.leonardo.ocremotev2.ui.navigation.routes
-
-import io.mockk.every
-import io.mockk.mockk
-import org.junit.Assert.assertEquals
-import org.junit.Test
-import java.net.URI
-
-class FileViewerNavTest {
-
-    private val serverParams = ServerRouteParams(
-        serverUrl = "http://192.168.1.100:4096",
-        username = "opencode",
-        password = "secret#123",
-        serverName = "Dev Server",
-        serverId = "srv-e5f6g7h8"
-    )
-
-    /** Build a mock NavBackStackEntry from a route string, so fromEntry can decode params. */
-    private fun buildEntry(route: String): androidx.navigation.NavBackStackEntry {
-        // Use java.net.URI (available on JVM) instead of android.net.Uri (stubbed in unit tests)
-        val uri = URI("http://dummy/$route")
-        val query = uri.rawQuery ?: ""
-        val paramMap = query.split("&").associate { part ->
-            val idx = part.indexOf('=')
-            if (idx >= 0) part.substring(0, idx) to part.substring(idx + 1) else part to ""
-        }
-
-        val bundle = mockk<android.os.Bundle>(relaxed = true)
-        every { bundle.getString(any()) } answers { paramMap[firstArg<String>()] }
-
-        val entry = mockk<androidx.navigation.NavBackStackEntry>(relaxed = true)
-        every { entry.arguments } returns bundle
-        return entry
-    }
-
-    @Test
-    fun `createRoute URL-encodes filePath with slashes`() {
-        val filePath = "src/main/kotlin/dev/minios/ocremote/Main.kt"
-
-        val route = FileViewerNav.createRoute(
-            serverUrl = serverParams.serverUrl,
-            username = serverParams.username,
-            password = serverParams.password,
-            serverName = serverParams.serverName,
-            serverId = serverParams.serverId,
-            sessionId = "01H2X3YZ9ABCDEF",
-            filePath = filePath,
-            source = FileViewerNav.Source.LIVE,
-            toolPartIds = ""
-        )
-
-        // Slashes in filePath must be encoded
-        assert(route.contains("filePath=src%2Fmain%2Fkotlin%2Fdev%2Fminios%2Focremote%2FMain.kt")) {
-            "filePath should be URL-encoded, got: $route"
-        }
-    }
-
-    @Test
-    fun `routePattern includes all 5 params`() {
-        val pattern = FileViewerNav.routePattern
-
-        val expected = "file_viewer?" +
-            "serverUrl={serverUrl}&username={username}&password={password}&" +
-            "serverName={serverName}&serverId={serverId}&" +
-            "sessionId={sessionId}&filePath={filePath}&source={source}&" +
-            "toolPartIds={toolPartIds}&directory={directory}"
-
-        assertEquals(expected, pattern)
-    }
-
-    @Test
-    fun `fromEntry round-trips source and toolPartIds`() {
-        val sessionId = "01H2X3YZ9ABCDEF"
-        val filePath = "src/main/kotlin/dev/minios/ocremote/Main.kt"
-        val source = FileViewerNav.Source.TOOL_SNAPSHOT
-        val toolPartIds = "part-11111111-2222-3333-4444-555555555555,part-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
-
-        val route = FileViewerNav.createRoute(
-            serverUrl = serverParams.serverUrl,
-            username = serverParams.username,
-            password = serverParams.password,
-            serverName = serverParams.serverName,
-            serverId = serverParams.serverId,
-            sessionId = sessionId,
-            filePath = filePath,
-            source = source,
-            toolPartIds = toolPartIds
-        )
-
-        val entry = buildEntry(route)
-        val params = FileViewerNav.fromEntry(entry)
-
-        assertEquals(sessionId, params.sessionId)
-        assertEquals(filePath, params.filePath)
-        assertEquals(FileViewerNav.Source.TOOL_SNAPSHOT, params.source)
-        assertEquals(toolPartIds, params.toolPartIds)
-        assertEquals("", params.directory)
-    }
-}
diff --git a/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt b/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt
index e64c26ca..b7bb3051 100644
--- a/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt
+++ b/app/src/test/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerViewModelTest.kt
@@ -1,71 +1,62 @@
 ﻿package dev.leonardo.ocremotev2.ui.screens.viewer
 
-import androidx.lifecycle.SavedStateHandle
 import dev.leonardo.ocremotev2.R
 import dev.leonardo.ocremotev2.domain.model.ContentType
 import dev.leonardo.ocremotev2.domain.model.FileContent
 import dev.leonardo.ocremotev2.domain.model.VcsDiffMode
 import dev.leonardo.ocremotev2.domain.model.VcsFileDiff
 import dev.leonardo.ocremotev2.domain.repository.ToolSnapshotCache
 import dev.leonardo.ocremotev2.domain.usecase.GetFileContentUseCase
 import dev.leonardo.ocremotev2.domain.usecase.GetFileDiffUseCase
 import dev.leonardo.ocremotev2.domain.usecase.SubmitAnnotationsUseCase
-import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav
-import dev.leonardo.ocremotev2.ui.navigation.routes.ServerRouteParams
 import io.mockk.coEvery
 import io.mockk.coVerify
 import io.mockk.mockk
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.ExperimentalCoroutinesApi
 import kotlinx.coroutines.test.UnconfinedTestDispatcher
 import kotlinx.coroutines.test.resetMain
 import kotlinx.coroutines.test.runTest
 import kotlinx.coroutines.test.setMain
 import org.junit.After
 import org.junit.Assert.assertEquals
 import org.junit.Assert.assertTrue
 import org.junit.Before
 import org.junit.Test
-import java.net.URLEncoder
 
 @OptIn(ExperimentalCoroutinesApi::class)
 class FileViewerViewModelTest {
 
     private val testDispatcher = UnconfinedTestDispatcher()
     private val getFileContent = mockk<GetFileContentUseCase>()
     private val getFileDiff = mockk<GetFileDiffUseCase>()
     private val toolSnapshotCache = ToolSnapshotCache()
     private val submitAnnotations = mockk<SubmitAnnotationsUseCase>()
 
     private val serverId = "srv-abc123"
     private val directory = "/home/user/project"
     private val filePath = "src/main/kotlin/dev/minios/ocremote/MainActivity.kt"
-    private val encodedDirectory = URLEncoder.encode(directory, "UTF-8")
-    private val encodedFilePath = URLEncoder.encode(filePath, "UTF-8")
     private val mdFilePath = "docs/README.md"
-    private val encodedMdFilePath = URLEncoder.encode(mdFilePath, "UTF-8")
-
-    private fun savedStateHandle(
-        id: String = serverId,
-        dir: String = encodedDirectory,
-        path: String = encodedFilePath,
-        source: String = FileViewerNav.Source.LIVE,
-        toolPartIds: String = ""
-    ) = SavedStateHandle(
-        mapOf(
-            ServerRouteParams.PARAM_SERVER_ID to id,
-            FileViewerNav.PARAM_DIRECTORY to dir,
-            FileViewerNav.PARAM_FILE_PATH to path,
-            FileViewerNav.PARAM_SOURCE to source,
-            FileViewerNav.PARAM_TOOL_PART_IDS to toolPartIds
-        )
+
+    private fun fileViewerParams(
+        source: String = FileViewerSource.LIVE,
+        path: String = filePath,
+        dir: String = directory,
+        toolPartIds: List<String> = emptyList()
+    ) = FileViewerParams(
+        serverId = serverId,
+        sessionId = "session-123",
+        filePath = path,
+        directory = dir,
+        source = source,
+        toolPartIds = toolPartIds
     )
 
     // --- Realistic test data (D7-003) ---
 
     private val sampleKotlinSource = """
         package dev.leonardo.ocremotev2
 
         import android.os.Bundle
         import androidx.appcompat.app.AppCompatActivity
 
@@ -134,56 +125,56 @@ class FileViewerViewModelTest {
     @After
     fun tearDown() {
         Dispatchers.resetMain()
     }
 
     // 1. LIVE source success loads content
     @Test
     fun `LIVE source success loads content`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false after success" }
         assert(state.content == sampleKotlinSource) { "content should match" }
         assert(!state.isBinary) { "isBinary should be false for text" }
         assert(state.error == null) { "error should be null on success" }
     }
 
     // 2. GIT_DIFF source success parses hunks
     @Test
     fun `GIT_DIFF source success parses hunks`() = runTest {
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(sampleDiffs)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(source = FileViewerNav.Source.GIT_DIFF),
+            fileViewerParams(source = FileViewerSource.GIT_DIFF),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false" }
         assert(state.mode == FileViewerMode.DIFF) { "mode should be DIFF" }
         assert(state.hunks.isNotEmpty()) { "hunks should be parsed from patch" }
         assert(state.hunks.size == 2) { "expected 2 hunks, got ${state.hunks.size}" }
         assert(state.diff != null) { "diff should be set" }
     }
 
     // 3. TOOL_SNAPSHOT source without cache sets missing error
     @Test
     fun `TOOL_SNAPSHOT source without cache sets missing error`() = runTest {
         toolSnapshotCache.clear()
         val vm = FileViewerViewModel(
-            savedStateHandle(source = FileViewerNav.Source.TOOL_SNAPSHOT),
+            fileViewerParams(source = FileViewerSource.TOOL_SNAPSHOT),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false" }
         assert(state.error != null) { "error should be set for missing snapshot" }
         assert(state.error == R.string.fileviewer_error_tool_snapshot_missing) {
@@ -194,54 +185,54 @@ class FileViewerViewModelTest {
         coVerify(exactly = 0) { getFileDiff(any(), any(), any()) }
     }
 
     // 4. Load failure sets error
     @Test
     fun `load failure sets error`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.failure(
             RuntimeException("Connection refused: port 4096")
         )
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false after failure" }
         assert(state.error == R.string.workspace_error_load_failed) {
             "error should be load failed resource, was: ${state.error}"
         }
     }
 
     // 5. Binary file sets isBinary + mimeType
     @Test
     fun `binary file sets isBinary and mimeType`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(binaryContent)
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false" }
         assert(state.isBinary) { "isBinary should be true" }
         assert(state.mimeType == "image/png") { "mimeType should be image/png" }
         assert(state.content.isEmpty()) { "content should be empty for binary" }
     }
 
     // 6. Empty content sets isEmpty
     @Test
     fun `empty content sets isEmpty`() = runTest {
         val emptyContent = FileContent(
             path = filePath,
             type = ContentType.TEXT,
             content = ""
         )
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(emptyContent)
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false" }
         assert(state.isEmpty) { "isEmpty should be true for blank content" }
         assert(state.content.isEmpty()) { "content should be empty" }
     }
 
     // 7. Empty patch sets hunks empty
     @Test
     fun `empty patch sets hunks empty`() = runTest {
@@ -250,40 +241,40 @@ class FileViewerViewModelTest {
             patch = "",
             additions = 0,
             deletions = 0,
             status = dev.leonardo.ocremotev2.domain.model.VcsStatus.MODIFIED
         )
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(
             listOf(emptyPatchDiff)
         )
 
         val vm = FileViewerViewModel(
-            savedStateHandle(source = FileViewerNav.Source.GIT_DIFF),
+            fileViewerParams(source = FileViewerSource.GIT_DIFF),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         val state = vm.uiState.value
         assert(!state.isLoading) { "isLoading should be false" }
         assert(state.hunks.isEmpty()) { "hunks should be empty for empty patch" }
         assert(state.isEmpty) { "isEmpty should be true when hunks are empty" }
     }
 
     // 8. nextHunk clamps at last index
     @Test
     fun `nextHunk clamps at last index`() = runTest {
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(sampleDiffs)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(source = FileViewerNav.Source.GIT_DIFF),
+            fileViewerParams(source = FileViewerSource.GIT_DIFF),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         val hunksCount = vm.uiState.value.hunks.size
         // Navigate to last hunk
         for (i in 0 until hunksCount) vm.nextHunk()
         assert(vm.uiState.value.currentHunkIndex == hunksCount - 1) {
@@ -295,21 +286,21 @@ class FileViewerViewModelTest {
             "currentHunkIndex should clamp at last (${hunksCount - 1})"
         }
     }
 
     // 9. prevHunk clamps at 0
     @Test
     fun `prevHunk clamps at 0`() = runTest {
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(sampleDiffs)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(source = FileViewerNav.Source.GIT_DIFF),
+            fileViewerParams(source = FileViewerSource.GIT_DIFF),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         // currentHunkIndex starts at 0
         assert(vm.uiState.value.currentHunkIndex == 0) {
             "currentHunkIndex should start at 0"
         }
@@ -321,96 +312,96 @@ class FileViewerViewModelTest {
     }
 
     // ===== Phase 2: Markdown toggle tests =====
 
     // 10. init with md file sets isMarkdown true
     @Test
     fun `init with md file sets isMarkdown true and defaults to RENDER_PREVIEW`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(sampleMarkdownContent)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(path = encodedMdFilePath),
+            fileViewerParams(path = mdFilePath),
             getFileContent, getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         assert(vm.uiState.value.fileType == FileType.MARKDOWN) { "fileType should be MARKDOWN for .md" }
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW) { "renderMode should default to RENDER_PREVIEW for renderable types" }
     }
 
     // 11. init with kt file sets isMarkdown false
     @Test
     fun `init with kt file sets isMarkdown false`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
 
         assert(vm.uiState.value.fileType == FileType.TEXT) { "fileType should be TEXT for .kt" }
     }
 
     // 12. toggleRenderMode switches RENDER_PREVIEW to SOURCE (default is now RENDER_PREVIEW)
     @Test
     fun `toggleRenderMode switches RENDER_PREVIEW to SOURCE for markdown files`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(sampleMarkdownContent)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(path = encodedMdFilePath),
+            fileViewerParams(path = mdFilePath),
             getFileContent, getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
         vm.toggleRenderMode()
 
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) {
             "renderMode should be SOURCE after toggle from RENDER_PREVIEW"
         }
     }
 
     // 13. toggleRenderMode no-op for TEXT (non-renderable)
     @Test
     fun `toggleRenderMode is no-op for TEXT files`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
 
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.toggleRenderMode()
 
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) {
             "renderMode should stay SOURCE for non-md"
         }
     }
 
     // 14. toggleRenderMode no-op in DIFF mode
     @Test
     fun `toggleRenderMode is no-op in DIFF mode`() = runTest {
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(sampleDiffs)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(path = encodedMdFilePath, source = FileViewerNav.Source.GIT_DIFF),
+            fileViewerParams(path = mdFilePath, source = FileViewerSource.GIT_DIFF),
             getFileContent, getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
         vm.toggleRenderMode()
 
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) {
             "renderMode should stay SOURCE in DIFF mode"
         }
     }
 
     // 15. toggleRenderMode toggles from default RENDER_PREVIEW to SOURCE
     @Test
     fun `toggleRenderMode back from RENDER_PREVIEW to SOURCE`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(sampleMarkdownContent)
 
         val vm = FileViewerViewModel(
-            savedStateHandle(path = encodedMdFilePath),
+            fileViewerParams(path = mdFilePath),
             getFileContent, getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
         // Default is RENDER_PREVIEW for markdown, one toggle → SOURCE
         vm.toggleRenderMode()
 
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) {
             "renderMode should be SOURCE after toggle from RENDER_PREVIEW"
         }
@@ -424,24 +415,24 @@ class FileViewerViewModelTest {
         toolSnapshotCache.clear()
         toolSnapshotCache.put(
             "part-1",
             ToolSnapshotCache.Snapshot(
                 filePath = "app/Main.kt", content = "class Main",
                 before = null, after = null, toolName = "read"
             )
         )
 
         val vm = FileViewerViewModel(
-            savedStateHandle(
-                path = URLEncoder.encode("app/Main.kt", "UTF-8"),
-                source = FileViewerNav.Source.TOOL_SNAPSHOT,
-                toolPartIds = "part-1"
+            fileViewerParams(
+                path = "app/Main.kt",
+                source = FileViewerSource.TOOL_SNAPSHOT,
+                toolPartIds = listOf("part-1")
             ),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         assert(vm.uiState.value.isToolSnapshot) { "isToolSnapshot should be true" }
         assert(vm.uiState.value.content == "class Main") { "content should match snapshot" }
         assert(!vm.uiState.value.isLoading) { "isLoading should be false" }
@@ -461,24 +452,24 @@ class FileViewerViewModelTest {
             )
         )
 
         // Edit tools now fetch the full file from server (snapshot only has the fragment)
         val fullFile = "line1-mod\nline2-new\nline3\nline4\n"
         coEvery { getFileContent(serverId, directory, "app/X.kt") } returns Result.success(
             FileContent(path = "app/X.kt", type = ContentType.TEXT, content = fullFile)
         )
 
         val vm = FileViewerViewModel(
-            savedStateHandle(
-                path = URLEncoder.encode("app/X.kt", "UTF-8"),
-                source = FileViewerNav.Source.TOOL_SNAPSHOT_DIFF,
-                toolPartIds = "p1,p2"
+            fileViewerParams(
+                path = "app/X.kt",
+                source = FileViewerSource.TOOL_SNAPSHOT_DIFF,
+                toolPartIds = listOf("p1", "p2")
             ),
             getFileContent,
             getFileDiff,
             toolSnapshotCache,
             submitAnnotations
         )
 
         assert(vm.uiState.value.mode == FileViewerMode.SOURCE) { "mode should be SOURCE (not DIFF)" }
         assert(vm.uiState.value.isToolSnapshot) { "isToolSnapshot should be true" }
         assert(vm.uiState.value.toolSnapshotBefore == "line1\nline2\n") {
@@ -490,264 +481,248 @@ class FileViewerViewModelTest {
         // Content should be the full file from server (includes lines beyond the edited fragment)
         assert(vm.uiState.value.content.contains("line3")) { "content should show full file (line3)" }
         assert(vm.uiState.value.content.contains("line4")) { "content should show full file (line4)" }
     }
 
     // ===== Phase 3: Annotation tests =====
 
     @Test
     fun `addAnnotation creates annotation and updates state`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         val pos = sampleKotlinSource.indexOf("import android.os.Bundle")
         vm.addAnnotation("import android.os.Bundle", pos, pos + 25, "use alias")
         assertEquals(1, vm.uiState.value.annotations.size)
         assertEquals(0, vm.uiState.value.annotations[0].index)
     }
 
     @Test
     fun `addAnnotation in DIFF mode is ignored`() = runTest {
         coEvery { getFileDiff(serverId, directory, VcsDiffMode.GIT) } returns Result.success(sampleDiffs)
-        val vm = FileViewerViewModel(savedStateHandle(source = FileViewerNav.Source.GIT_DIFF), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(source = FileViewerSource.GIT_DIFF), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.addAnnotation("text", 0, 4, "note")
         assertTrue(vm.uiState.value.annotations.isEmpty())
     }
 
     @Test
     fun `deleteAnnotation removes and renumbers`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.addAnnotation("import", 0, 6, "n1")
         vm.addAnnotation("class", 10, 15, "n2")
         vm.addAnnotation("override", 20, 28, "n3")
         val firstId = vm.uiState.value.annotations[0].id
         vm.deleteAnnotation(firstId)
         val anns = vm.uiState.value.annotations
         assertEquals(2, anns.size)
         assertEquals(0, anns[0].index)
         assertEquals(1, anns[1].index)
     }
 
     @Test
     fun `submitAnnotations calls use case and clears on success`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
         coEvery { submitAnnotations(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.addAnnotation("import", 0, 6, "n1")
         val result = vm.submitAnnotations("overall note")
         assertTrue(result.isSuccess)
         assertTrue(vm.uiState.value.annotations.isEmpty())
     }
 
     @Test
     fun `submitAnnotations failure does not clear`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
         coEvery { submitAnnotations(any(), any(), any(), any(), any(), any()) } returns Result.failure(RuntimeException("fail"))
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.addAnnotation("import", 0, 6, "n1")
         vm.submitAnnotations("note")
         assertEquals(1, vm.uiState.value.annotations.size)
     }
 
     // ===== Phase 4: Pagination tests =====
 
     @Test
     fun `loadLive paginates content - initial visibleLineCount is 500 for large files`() = runTest {
         val largeContent = buildString {
             append("package dev.leonardo.ocremotev2\n\n")
             append("class LargeFile {\n")
             for (i in 1..2000) append("    fun method$i(): Int = $i\n")
             append("}\n")
         }
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             FileContent(path = filePath, type = ContentType.TEXT, content = largeContent)
         )
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         val total = largeContent.count { it == '\n' } + if (largeContent.endsWith('\n')) 0 else 1
         assertEquals(500, vm.uiState.value.visibleLineCount)
         assertEquals(total, vm.uiState.value.totalLineCount)
         assertEquals(false, vm.uiState.value.isFullyLoaded)
         assertEquals(false, vm.uiState.value.isExtremelyLarge)
     }
 
     @Test
     fun `loadLive marks isExtremelyLarge for files over 100000 lines`() = runTest {
         val hugeContent = (1..100001).joinToString("\n") { "line $it" }
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             FileContent(path = filePath, type = ContentType.TEXT, content = hugeContent)
         )
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         assertEquals(true, vm.uiState.value.isExtremelyLarge)
         assertEquals(10000, vm.uiState.value.visibleLineCount)
     }
 
     @Test
     fun `loadLive for small file sets isFullyLoaded true and visibleLineCount equals total`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         val total = sampleKotlinSource.count { it == '\n' } + if (sampleKotlinSource.endsWith('\n')) 0 else 1
         assertEquals(total, vm.uiState.value.visibleLineCount)
         assertEquals(true, vm.uiState.value.isFullyLoaded)
     }
 
     @Test
     fun `loadMoreLines increases visibleLineCount by 500 and respects totalLineCount`() = runTest {
         val largeContent = buildString {
             append("package dev.leonardo.ocremotev2\n\n")
             append("class LargeFile {\n")
             for (i in 1..2000) append("    fun method$i(): Int = $i\n")
             append("}\n")
         }
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             FileContent(path = filePath, type = ContentType.TEXT, content = largeContent)
         )
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         val initialVisible = vm.uiState.value.visibleLineCount
         vm.loadMoreLines()
         assertEquals(initialVisible + 500, vm.uiState.value.visibleLineCount)
         assertEquals(false, vm.uiState.value.isFullyLoaded)
     }
 
     @Test
     fun `loadMoreLines clamps to totalLineCount and sets isFullyLoaded`() = runTest {
         val mediumContent = (1..600).joinToString("\n") { "line $it" }
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             FileContent(path = filePath, type = ContentType.TEXT, content = mediumContent)
         )
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         vm.loadMoreLines()
         assertEquals(600, vm.uiState.value.visibleLineCount)
         assertEquals(true, vm.uiState.value.isFullyLoaded)
     }
 
     @Test
     fun `loadMoreLines is no-op when isFullyLoaded`() = runTest {
         coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         val before = vm.uiState.value.visibleLineCount
         vm.loadMoreLines()
         assertEquals(before, vm.uiState.value.visibleLineCount)
     }
 
     @Test
     fun `loadMoreLines for extremely large file starts at 10000 not 500`() = runTest {
         val hugeContent = (1..150000).joinToString("\n") { "line $it" }
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             FileContent(path = filePath, type = ContentType.TEXT, content = hugeContent)
         )
-        val vm = FileViewerViewModel(savedStateHandle(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
+        val vm = FileViewerViewModel(fileViewerParams(), getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
         assertEquals(10000, vm.uiState.value.visibleLineCount)
         vm.loadMoreLines()
         assertEquals(10500, vm.uiState.value.visibleLineCount)
     }
 
-    // ===== Phase 4: Annotation rotation survival =====
-
-    @Test
-    fun `annotations survive ViewModel recreation via SavedStateHandle`() = runTest {
-        coEvery { getFileContent(serverId, directory, filePath) } returns Result.success(sampleFileContent)
-        val handle = savedStateHandle()
-        val vm1 = FileViewerViewModel(handle, getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
-        vm1.addAnnotation("import", 0, 6, "note1")
-        vm1.addAnnotation("class", 10, 15, "note2")
-        // Recreate ViewModel with the same SavedStateHandle (simulates rotation)
-        val vm2 = FileViewerViewModel(handle, getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations)
-        assertEquals(2, vm2.uiState.value.annotations.size)
-        assertEquals("note1", vm2.uiState.value.annotations[0].note)
-        assertEquals("note2", vm2.uiState.value.annotations[1].note)
-    }
-
     // ===== Multi-format render tests =====
 
     @Test
     fun `init with json file sets fileType JSON`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             dev.leonardo.ocremotev2.domain.model.FileContent(
                 path = "config.json",
                 type = ContentType.TEXT,
                 content = "{\"key\":\"value\"}"
             )
         )
         val vm = FileViewerViewModel(
-            savedStateHandle(path = java.net.URLEncoder.encode("config.json", "UTF-8")),
+            fileViewerParams(path = "config.json"),
             getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
         )
         assert(vm.uiState.value.fileType == FileType.JSON) { "fileType should be JSON" }
     }
 
     @Test
     fun `toggleRenderMode is no-op for JSON files`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             dev.leonardo.ocremotev2.domain.model.FileContent(
                 path = "config.json",
                 type = ContentType.TEXT,
                 content = "{\"key\":\"value\"}"
             )
         )
         val vm = FileViewerViewModel(
-            savedStateHandle(path = java.net.URLEncoder.encode("config.json", "UTF-8")),
+            fileViewerParams(path = "config.json"),
             getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
         )
         vm.toggleRenderMode()
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) {
             "JSON should stay SOURCE (CodeWebView already has syntax highlight)"
         }
     }
 
     @Test
     fun `init with png binary sets fileType IMAGE and retains base64 content`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             dev.leonardo.ocremotev2.domain.model.FileContent(
                 path = "photo.png",
                 type = ContentType.BINARY,
                 content = "iVBORw0KGgo=",
                 mimeType = "image/png"
             )
         )
         val vm = FileViewerViewModel(
-            savedStateHandle(path = java.net.URLEncoder.encode("photo.png", "UTF-8")),
+            fileViewerParams(path = "photo.png"),
             getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
         )
         assert(vm.uiState.value.fileType == FileType.IMAGE) { "fileType should be IMAGE" }
         assert(!vm.uiState.value.isBinary) { "IMAGE should not be marked isBinary" }
         assert(vm.uiState.value.content == "iVBORw0KGgo=") { "base64 content should be retained" }
         assert(vm.uiState.value.mimeType == "image/png") { "mimeType should be preserved" }
     }
 
     @Test
     fun `toggleRenderMode works for IMAGE files`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             dev.leonardo.ocremotev2.domain.model.FileContent(
                 path = "photo.png",
                 type = ContentType.BINARY,
                 content = "iVBORw0KGgo=",
                 mimeType = "image/png"
             )
         )
         val vm = FileViewerViewModel(
-            savedStateHandle(path = java.net.URLEncoder.encode("photo.png", "UTF-8")),
+            fileViewerParams(path = "photo.png"),
             getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
         )
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW) { "IMAGE should default to RENDER_PREVIEW" }
         vm.toggleRenderMode()
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) { "toggle should switch to SOURCE" }
     }
 
     @Test
     fun `toggleRenderMode works for CSV files`() = runTest {
         coEvery { getFileContent(any(), any(), any()) } returns Result.success(
             dev.leonardo.ocremotev2.domain.model.FileContent(
                 path = "data.csv",
                 type = ContentType.TEXT,
                 content = "a,b\n1,2"
             )
         )
         val vm = FileViewerViewModel(
-            savedStateHandle(path = java.net.URLEncoder.encode("data.csv", "UTF-8")),
+            fileViewerParams(path = "data.csv"),
             getFileContent, getFileDiff, toolSnapshotCache, submitAnnotations
         )
         assert(vm.uiState.value.fileType == FileType.CSV)
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.RENDER_PREVIEW) { "CSV should default to RENDER_PREVIEW" }
         vm.toggleRenderMode()
         assert(vm.uiState.value.renderMode == FileViewerRenderMode.SOURCE) { "toggle should switch to SOURCE" }
     }
 }
