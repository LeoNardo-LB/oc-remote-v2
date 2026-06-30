## Task 3 Diff: 161b757b..d653df8f

### Commits

d653df8f feat(viewer): FileViewerOverlay + migrate all callers

### Stat

 .../leonardo/ocremotev2/ui/navigation/NavGraph.kt  |  93 +-------------
 .../ui/navigation/routes/FileViewerNav.kt          |  78 ------------
 .../ocremotev2/ui/screens/chat/ChatRoute.kt        |   2 +
 .../ocremotev2/ui/screens/chat/ChatScreen.kt       |  68 ++++++----
 .../ocremotev2/ui/screens/chat/ChatViewModel.kt    |  15 +--
 .../ui/screens/chat/components/PartContent.kt      |   6 +-
 .../ui/screens/viewer/FileViewerEntryPoint.kt      |  11 ++
 .../ui/screens/viewer/FileViewerOverlay.kt         | 141 +++++++++++++++++++++
 .../ui/screens/viewer/FileViewerRoute.kt           |  84 ------------
 .../ui/screens/workspace/WorkspaceScreen.kt        |  40 +++++-
 10 files changed, 240 insertions(+), 298 deletions(-)

### Full Diff

diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt
index 0236d222..cd49e76e 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/NavGraph.kt
@@ -20,32 +20,29 @@ import androidx.navigation.compose.composable
 import androidx.navigation.compose.rememberNavController
 import dev.leonardo.ocremotev2.SessionDeepLink
 import dev.leonardo.ocremotev2.domain.repository.ServerRepository
 import dev.leonardo.ocremotev2.domain.repository.FileRepository
 import dev.leonardo.ocremotev2.domain.repository.SessionRepository
 import dev.leonardo.ocremotev2.domain.repository.SettingsRepository
 import dev.leonardo.ocremotev2.domain.model.Session
 import dev.leonardo.ocremotev2.ui.navigation.routes.*
 import kotlinx.coroutines.launch
 import dev.leonardo.ocremotev2.ui.screens.about.AboutScreen
-import androidx.compose.runtime.CompositionLocalProvider
 import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
 import dev.leonardo.ocremotev2.ui.screens.chat.ChatScreen
 import dev.leonardo.ocremotev2.ui.screens.chat.ChatViewModel
-import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnViewTool
 import dev.leonardo.ocremotev2.ui.screens.home.HomeRoute
 import dev.leonardo.ocremotev2.ui.screens.sessions.SessionListRoute
 import dev.leonardo.ocremotev2.ui.screens.server.ServerModelFilterRoute
 import dev.leonardo.ocremotev2.ui.screens.server.ServerProvidersRoute
 import dev.leonardo.ocremotev2.ui.screens.server.ServerSettingsRoute
 import dev.leonardo.ocremotev2.ui.screens.settings.SettingsRoute
-import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerRoute
 import dev.leonardo.ocremotev2.ui.screens.webview.WebViewScreen
 import dev.leonardo.ocremotev2.ui.screens.workspace.WorkspaceRoute
 import kotlinx.coroutines.flow.MutableSharedFlow
 import kotlinx.coroutines.flow.SharedFlow
 import kotlinx.coroutines.flow.firstOrNull
 import java.net.URLDecoder
 import androidx.compose.material3.windowsizeclass.WindowSizeClass
 
 private const val TAG = "NavGraph"
 
@@ -409,44 +406,23 @@ fun NavGraph(
             val context = LocalContext.current
 
             // Only pass shared images to the targeted session, then clear them
             val imagesForThisSession = if (pendingShareSessionId == params.sessionId && pendingShareUris.isNotEmpty()) {
                 pendingShareUris
             } else {
                 emptyList()
             }
 
             val chatViewModel = hiltViewModel<ChatViewModel>()
-            CompositionLocalProvider(
-                LocalOnViewTool provides { request ->
-                    chatViewModel.cacheToolPart(request.part)
-                    scope.launch {
-                        val session = sessionRepository.getSession(params.server.serverId, params.sessionId).getOrNull()
-                        val dir = session?.directory ?: params.directory
-                        navController.navigate(
-                            FileViewerNav.createRoute(
-                                serverUrl = params.server.serverUrl,
-                                username = params.server.username,
-                                password = params.server.password,
-                                serverName = params.server.serverName,
-                                serverId = params.server.serverId,
-                                sessionId = params.sessionId,
-                                filePath = request.filePath,
-                                source = request.source,
-                                toolPartIds = request.part.id,
-                                directory = dir
-                            )
-                        )
-                    }
-                }
-            ) {
                 ChatScreen(
+                    serverId = params.server.serverId,
+                    sessionId = params.sessionId,
                     viewModel = chatViewModel,
                     onNavigateBack = {
                     navController.popBackStack()
                 },
                 onNavigateToSession = { newSessionId ->
                     val route = ChatNav.createRoute(
                         serverUrl = params.server.serverUrl,
                         username = params.server.username,
                         password = params.server.password,
                         serverName = params.server.serverName,
@@ -501,39 +477,20 @@ fun NavGraph(
                                 username = params.server.username,
                                 password = params.server.password,
                                 serverName = params.server.serverName,
                                 serverId = params.server.serverId,
                                 sessionId = params.sessionId,
                                 directory = dir
                             )
                         ) { launchSingleTop = true }
                     }
                 },
-                onOpenFile = { filePath ->
-                    scope.launch {
-                        val session = sessionRepository.getSession(params.server.serverId, params.sessionId).getOrNull()
-                        val dir = session?.directory ?: params.directory
-                        navController.navigate(
-                                FileViewerNav.createRoute(
-                                    serverUrl = params.server.serverUrl,
-                                    username = params.server.username,
-                                    password = params.server.password,
-                                    serverName = params.server.serverName,
-                                    serverId = params.server.serverId,
-                                    sessionId = params.sessionId,
-                                    filePath = filePath,
-                                    source = FileViewerNav.Source.LIVE,
-                                    directory = dir
-                                )
-                            )
-                    }
-                },
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
@@ -546,66 +503,26 @@ fun NavGraph(
                     val result = fileRepository.getFileContent(params.server.serverId, dir, filePath)
                     result.isSuccess && result.getOrNull()?.content?.isNotEmpty() == true
                 },
                 initialSharedImages = imagesForThisSession,
                 onSharedImagesConsumed = {
                     pendingShareUris = emptyList()
                     pendingShareSessionId = null
                 },
                 startInTerminalMode = params.openTerminal
             )
-            }
         }
 
         // ============ Workspace Screen ============
         composable(
             route = WorkspaceNav.routePattern,
             arguments = WorkspaceNav.navArguments
         ) { entry ->
             val p = WorkspaceNav.fromEntry(entry)
             WorkspaceRoute(
-                onBack = { navController.popBackStack() },
-                onOpenFile = { filePath ->
-                    navController.navigate(
-                        FileViewerNav.createRoute(
-                            serverUrl = p.server.serverUrl,
-                            username = p.server.username,
-                            password = p.server.password,
-                            serverName = p.server.serverName,
-                            serverId = p.server.serverId,
-                            sessionId = p.sessionId,
-                            filePath = filePath,
-                            source = FileViewerNav.Source.LIVE,
-                            directory = p.directory
-                        )
-                    )
-                },
-                onOpenGitDiff = { filePath ->
-                    navController.navigate(
-                        FileViewerNav.createRoute(
-                            serverUrl = p.server.serverUrl,
-                            username = p.server.username,
-                            password = p.server.password,
-                            serverName = p.server.serverName,
-                            serverId = p.server.serverId,
-                            sessionId = p.sessionId,
-                            filePath = filePath,
-                            source = FileViewerNav.Source.GIT_DIFF,
-                            directory = p.directory
-                        )
-                    )
-                }
-            )
-        }
-
-        // ============ File Viewer Screen ============
-        composable(
-            route = FileViewerNav.routePattern,
-            arguments = FileViewerNav.navArguments
-        ) {
-            FileViewerRoute(
-                onBack = { navController.popBackStack() },
-                onSubmitted = { navController.popBackStack() }
+                serverId = p.server.serverId,
+                sessionId = p.sessionId,
+                onBack = { navController.popBackStack() }
             )
         }
     }
 }
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNav.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNav.kt
deleted file mode 100644
index 68cafb78..00000000
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/navigation/routes/FileViewerNav.kt
+++ /dev/null
@@ -1,78 +0,0 @@
-﻿package dev.leonardo.ocremotev2.ui.navigation.routes
-
-import androidx.navigation.NavBackStackEntry
-import androidx.navigation.NavType
-import androidx.navigation.navArgument
-import java.net.URLDecoder
-import java.net.URLEncoder
-
-/**
- * Navigation route definition for the File Viewer screen.
- * Parameters: server params + sessionId, filePath, source, toolPartIds, directory
- */
-object FileViewerNav {
-    const val ROUTE = "file_viewer"
-    const val PARAM_SESSION_ID = "sessionId"
-    const val PARAM_FILE_PATH = "filePath"
-    const val PARAM_SOURCE = "source"
-    const val PARAM_TOOL_PART_IDS = "toolPartIds"
-    const val PARAM_DIRECTORY = "directory"
-
-    object Source {
-        const val LIVE = "live"
-        const val GIT_DIFF = "git_diff"
-        const val TOOL_SNAPSHOT = "tool_snapshot"
-        const val TOOL_SNAPSHOT_DIFF = "tool_snapshot_diff"
-    }
-
-    val navArguments = ServerRouteParams.navArguments + listOf(
-        navArgument(PARAM_SESSION_ID) { type = NavType.StringType },
-        navArgument(PARAM_FILE_PATH) { type = NavType.StringType },
-        navArgument(PARAM_SOURCE) { type = NavType.StringType },
-        navArgument(PARAM_TOOL_PART_IDS) { type = NavType.StringType },
-        navArgument(PARAM_DIRECTORY) { type = NavType.StringType; defaultValue = "" },
-    )
-
-    val routePattern: String
-        get() = "$ROUTE?${ServerRouteParams.queryPattern()}&$PARAM_SESSION_ID={$PARAM_SESSION_ID}&$PARAM_FILE_PATH={$PARAM_FILE_PATH}&$PARAM_SOURCE={$PARAM_SOURCE}&$PARAM_TOOL_PART_IDS={$PARAM_TOOL_PART_IDS}&$PARAM_DIRECTORY={$PARAM_DIRECTORY}"
-
-    data class Params(
-        val server: ServerRouteParams,
-        val sessionId: String,
-        val filePath: String,
-        val source: String,
-        val toolPartIds: String = "",
-        val directory: String = ""
-    )
-
-    fun createRoute(
-        serverUrl: String,
-        username: String,
-        password: String,
-        serverName: String,
-        serverId: String,
-        sessionId: String,
-        filePath: String,
-        source: String,
-        toolPartIds: String = "",
-        directory: String = ""
-    ): String {
-        val serverQuery = ServerRouteParams.queryString(serverUrl, username, password, serverName, serverId)
-        val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
-        val encodedFilePath = URLEncoder.encode(filePath, "UTF-8")
-        val encodedSource = URLEncoder.encode(source, "UTF-8")
-        val encodedToolPartIds = URLEncoder.encode(toolPartIds, "UTF-8")
-        val encodedDirectory = URLEncoder.encode(directory, "UTF-8")
-        return "$ROUTE?$serverQuery&$PARAM_SESSION_ID=$encodedSessionId&$PARAM_FILE_PATH=$encodedFilePath&$PARAM_SOURCE=$encodedSource&$PARAM_TOOL_PART_IDS=$encodedToolPartIds&$PARAM_DIRECTORY=$encodedDirectory"
-    }
-
-    fun fromEntry(entry: NavBackStackEntry): Params {
-        val server = entry.serverRouteParams()
-        val sessionId = URLDecoder.decode(entry.arguments?.getString(PARAM_SESSION_ID).orEmpty(), "UTF-8")
-        val filePath = URLDecoder.decode(entry.arguments?.getString(PARAM_FILE_PATH).orEmpty(), "UTF-8")
-        val source = URLDecoder.decode(entry.arguments?.getString(PARAM_SOURCE).orEmpty(), "UTF-8")
-        val toolPartIds = URLDecoder.decode(entry.arguments?.getString(PARAM_TOOL_PART_IDS).orEmpty(), "UTF-8")
-        val directory = URLDecoder.decode(entry.arguments?.getString(PARAM_DIRECTORY).orEmpty(), "UTF-8")
-        return Params(server = server, sessionId = sessionId, filePath = filePath, source = source, toolPartIds = toolPartIds, directory = directory)
-    }
-}
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatRoute.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatRoute.kt
index 6b6d2922..561709cd 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatRoute.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatRoute.kt
@@ -40,20 +40,22 @@ fun NavGraphBuilder.chatScreen(
 ) {
     composable(
         route = ChatNav.routePattern,
         arguments = ChatNav.navArguments,
     ) { backStackEntry ->
         val args = ChatNav.fromEntry(backStackEntry)
 
         val sharedImages = getPendingShare(args.sessionId)
 
         ChatScreen(
+            serverId = args.server.serverId,
+            sessionId = args.sessionId,
             onNavigateBack = onNavigateBack,
             onNavigateToSession = { newSessionId ->
                 onNavigateToSession(
                     args.server.serverUrl, args.server.username, args.server.password,
                     args.server.serverName, args.server.serverId, newSessionId,
                 )
             },
             onNavigateToChildSession = { childSessionId ->
                 onNavigateToChildSession(
                     args.server.serverUrl, args.server.username, args.server.password,
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt
index f8d2ce9e..a465649f 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatScreen.kt
@@ -232,40 +232,46 @@ import dev.leonardo.ocremotev2.ui.screens.chat.components.MessageCardRole
 import dev.leonardo.ocremotev2.ui.screens.chat.components.ChatEmptyState
 import dev.leonardo.ocremotev2.ui.screens.chat.components.ChatErrorState
 import dev.leonardo.ocremotev2.ui.screens.chat.components.ChatMessageList
 import dev.leonardo.ocremotev2.ui.screens.chat.components.ChatTopBar
 import dev.leonardo.ocremotev2.ui.screens.chat.components.ErrorPayloadContent
 import dev.leonardo.ocremotev2.ui.components.indicators.PulsingDotsIndicator
 import dev.leonardo.ocremotev2.ui.screens.chat.components.RevertBanner
 import dev.leonardo.ocremotev2.ui.screens.chat.terminal.ChatTerminalView
 import dev.leonardo.ocremotev2.ui.screens.chat.dialog.RenameSessionDialog
 import dev.leonardo.ocremotev2.ui.screens.chat.dialog.SendConfirmDialog
+import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnViewTool
 import dev.leonardo.ocremotev2.ui.screens.chat.util.snapToBottom
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerOverlay
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerParams
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource
 import dev.leonardo.ocremotev2.ui.theme.AlphaTokens
 import dev.leonardo.ocremotev2.ui.theme.SpacingTokens
 
 
 /**
  * Chat Screen - conversation view with native markdown rendering.
  * Shows messages with streaming text rendered via mikepenz markdown renderer.
  */
 
 private const val TAG_SCROLL = "ChatScroll"
 
 // jumpToBottom / animateScrollToBottom removed — reverseLayout=true anchors at bottom natively.
 
 
 
 
 @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
 @Composable
 fun ChatScreen(
+    serverId: String,
+    sessionId: String,
     onNavigateBack: () -> Unit,
     onNavigateToSession: (sessionId: String) -> Unit = {},
     onNavigateToChildSession: (String) -> Unit = {},
     onOpenInWebView: () -> Unit = {},
     onOpenWorkspace: () -> Unit = {},
     onOpenFile: (filePath: String) -> Unit = {},
     onOpenDirectory: (directoryPath: String) -> Unit = {},
     checkFileExists: suspend (filePath: String) -> Boolean = { true },
     initialSharedImages: List<Uri> = emptyList(),
     onSharedImagesConsumed: () -> Unit = {},
@@ -296,62 +302,49 @@ fun ChatScreen(
         viewModel.revertedDraftEvent.collect { payload ->
             inputText = TextFieldValue(payload.text, TextRange(payload.text.length))
         }
     }
     // listState is hoisted to ViewModel — survives navigation.
     val listState = viewModel.listState
 
     var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
     var forceScrollTick by remember { mutableIntStateOf(0) }
 
+    // FileViewer overlay state — replaces navigation to FileViewerNav route.
+    var fileViewerRequest by remember { mutableStateOf<FileViewerParams?>(null) }
+    val handleOpenFile: (String) -> Unit = { filePath ->
+        fileViewerRequest = FileViewerParams(
+            serverId = serverId,
+            sessionId = sessionId,
+            filePath = filePath,
+            directory = directory,
+            source = FileViewerSource.LIVE
+        )
+    }
+
     val isAtBottom by remember {
         derivedStateOf {
             listState.firstVisibleItemIndex == 0 &&
                 listState.firstVisibleItemScrollOffset < 100
         }
     }
 
-    // Save scroll position (key + offset) when leaving ChatScreen.
-    DisposableEffect(Unit) {
-        onDispose {
-            val firstVisible = listState.layoutInfo.visibleItemsInfo
-                .firstOrNull { it.index == listState.firstVisibleItemIndex }
-            viewModel.pendingScrollKey = firstVisible?.key?.toString()
-            viewModel.pendingScrollOffset = listState.firstVisibleItemScrollOffset
-        }
-    }
-
     LaunchedEffect(listState.isScrollInProgress) {
         if (listState.isScrollInProgress) {
             autoScrollEnabled = false
         } else if (isAtBottom) {
             autoScrollEnabled = true
         }
     }
 
     val messageCount = messageState.messages.size
     LaunchedEffect(messageCount) {
-        // Restore scroll position using saved key + requestScrollToItem.
-        // requestScrollToItem is non-suspend — sets position for the NEXT measure
-        // pass, overriding the normalization that scrollToItem suffers from.
-        val savedKey = viewModel.pendingScrollKey
-        if (savedKey != null && !autoScrollEnabled) {
-            viewModel.pendingScrollKey = null // consume
-            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
-            val target = listState.layoutInfo.visibleItemsInfo
-                .firstOrNull { it.key?.toString() == savedKey }
-            if (target != null && target.index != listState.firstVisibleItemIndex) {
-                val cappedOffset = if (target.size > 0) viewModel.pendingScrollOffset.coerceAtMost(target.size - 1) else 0
-                listState.requestScrollToItem(target.index, cappedOffset)
-            }
-        }
-
         if (messageCount > 0 && autoScrollEnabled && !listState.isScrollInProgress) {
             listState.scrollToItem(0)
         }
     }
 
     LaunchedEffect(forceScrollTick) {
         if (forceScrollTick > 0) {
             listState.snapToBottom()
         }
     }
@@ -365,21 +358,21 @@ fun ChatScreen(
     }
 
     var showModelPicker by remember { mutableStateOf(false) }
     var showRenameDialog by remember { mutableStateOf(false) }
     var showMenu by remember { mutableStateOf(false) }
     var isTerminalMode by rememberSaveable { mutableStateOf(startInTerminalMode) }
     val snackbarHostState = remember { SnackbarHostState() }
     val coroutineScope = rememberCoroutineScope()
     val linkUriHandler = rememberLinkUriHandler(
         directory = directory,
-        onOpenFile = onOpenFile,
+        onOpenFile = handleOpenFile,
         onOpenDirectory = onOpenDirectory,
         fileChecker = checkFileExists,
         snackbarHostState = snackbarHostState,
         coroutineScope = coroutineScope,
     )
     val context = LocalContext.current
     val isAmoled = isAmoledTheme()
     val keyboardController = LocalSoftwareKeyboardController.current
     val clipboard = androidx.compose.ui.platform.LocalClipboard.current
     val view = LocalView.current
@@ -499,20 +492,31 @@ fun ChatScreen(
     // ChatScreen recomposition — only this wrapper recomposes.
     ChatSettingsProvider(viewModel = viewModel) {
     CompositionLocalProvider(
         LocalHapticFeedbackEnabled provides hapticEnabled,
         LocalImageSaveRequest provides attachmentHandler.requestSaveImage,
         LocalToolExpandedStates provides messageState.toolExpandedStates,
         LocalOnToggleToolExpanded provides { toolId, defaultExpanded -> viewModel.toggleToolExpanded(toolId, defaultExpanded) },
         LocalToolCardResolver provides viewModel.toolCardResolver,
         LocalSessionDiffs provides mapOf(viewModel.sessionId to sessionDiffs),
         LocalUriHandler provides linkUriHandler,
+        LocalOnViewTool provides { request ->
+            viewModel.cacheToolPart(request.part)
+            fileViewerRequest = FileViewerParams(
+                serverId = serverId,
+                sessionId = sessionId,
+                filePath = request.filePath,
+                directory = directory,
+                source = request.source,
+                toolPartIds = listOf(request.part.id)
+            )
+        },
     ) {
     Scaffold(
         snackbarHost = {
             SnackbarHost(snackbarHostState) { data ->
                 Snackbar(
                     modifier = Modifier.padding(horizontal = SpacingTokens.LG.dp),
                     containerColor = MaterialTheme.colorScheme.surfaceVariant,
                     contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                     actionContentColor = MaterialTheme.colorScheme.primary,
                     action = {
@@ -1003,21 +1007,21 @@ fun ChatScreen(
                                 isAmoled = isAmoled,
                                 messageSpacing = messageSpacing,
                                 isMainSession = true,
                                 coroutineScope = coroutineScope,
                                 snackbarHostState = snackbarHostState,
                                 context = context,
                                 clipboard = clipboard,
                                 keyboardController = keyboardController,
                                 viewModel = viewModel,
                                 navigateToChildSession = onNavigateToChildSession,
-                                onOpenFile = onOpenFile,
+                                onOpenFile = handleOpenFile,
                                 onForceScrollToBottom = { forceScrollTick++ },
                                 agents = modelConfig.agents,
 
                                 modifier = Modifier.fillMaxSize(),
                             )
                         } else {
                             ChatMessageList(
                                 listState = listState,
                                 messageState = messageState,
                                 sessionMeta = sessionMeta,
@@ -1028,21 +1032,21 @@ fun ChatScreen(
                                 isAmoled = isAmoled,
                                 messageSpacing = messageSpacing,
                                 isMainSession = false,
                                 coroutineScope = coroutineScope,
                                 snackbarHostState = snackbarHostState,
                                 context = context,
                                 clipboard = clipboard,
                                 keyboardController = keyboardController,
                                 viewModel = viewModel,
                                 navigateToChildSession = onNavigateToChildSession,
-                                onOpenFile = onOpenFile,
+                                onOpenFile = handleOpenFile,
                                 onForceScrollToBottom = { forceScrollTick++ },
                                 agents = modelConfig.agents,
 
                                 modifier = Modifier.fillMaxSize(),
                             )
                         }
                   }
               }
            }
        }
@@ -1085,20 +1089,28 @@ fun ChatScreen(
                 pendingSendAction?.invoke()
                 pendingSendAction = null
             },
             onDismiss = {
                 pendingSendAction = null
             }
         )
     }
     } // CompositionLocalProvider
     } // ChatSettingsProvider
+
+    // FileViewer overlay — rendered on top of ChatScreen when requested.
+    fileViewerRequest?.let { params ->
+        FileViewerOverlay(
+            params = params,
+            onDismiss = { fileViewerRequest = null }
+        )
+    }
 }
 
 /**
  * Wrapper composable that collects settings flows and provides them via CompositionLocals.
  * Sunk from ChatScreen to prevent settings changes from triggering ChatScreen recomposition.
  * Only this wrapper recomposes when settings change.
  */
 @Composable
 private fun ChatSettingsProvider(
     viewModel: ChatViewModel,
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt
index 5b8691d8..6207ee58 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/ChatViewModel.kt
@@ -488,34 +488,25 @@ class ChatViewModel @Inject constructor(
     )
     // ============ Tool Expand / Pagination (delegated — Phase 3 Task 5) ============
     val toolExpandedStates: StateFlow<Map<String, Boolean>> get() = messageData.toolExpandedStates
 
     fun toggleToolExpanded(toolId: String, defaultExpanded: Boolean = false) =
         messageData.toggleToolExpanded(toolId, defaultExpanded)
 
     fun isToolExpanded(toolId: String, autoExpand: Boolean): Boolean =
         messageData.isToolExpanded(toolId, autoExpand)
 
-    // ============ Scroll State (hoisted to survive navigation) ============
-    // LazyListState lives in the ViewModel so it survives FileViewer/sub-session
-    // navigation. This preserves lastKnownFirstItemKey, allowing the LazyColumn's
-    // key-based position tracking to correct index shifts from conditional items
-    // (banners/questions/permissions) changing count while away.
+    // ============ Scroll State ============
+    // LazyListState lives in the ViewModel so it is retained across configuration
+    // changes and recomposition (key-based position tracking for conditional items).
     val listState = androidx.compose.foundation.lazy.LazyListState()
 
-    // Pending scroll restore: saved when leaving ChatScreen, consumed on return.
-    // Uses the first-visible item's KEY (not index) because conditional items
-    // (banners/questions/permissions) change count during navigation, causing
-    // raw index to drift.
-    var pendingScrollKey: String? = null
-    var pendingScrollOffset: Int = 0
-
     val expandReasoning = settingsRepository.getSettingsFlow().map { it.expandReasoning }.stateIn(
         viewModelScope, SharingStarted.WhileSubscribed(5000), false
     )
     val showTurnDividers = settingsRepository.getSettingsFlow().map { it.showTurnDividers }.stateIn(
         viewModelScope, SharingStarted.WhileSubscribed(5000), true
     )
     val hapticFeedback = settingsRepository.getSettingsFlow().map { it.hapticFeedback }.stateIn(
         viewModelScope, SharingStarted.WhileSubscribed(5000), true
     )
     val keepScreenOn = settingsRepository.getSettingsFlow().map { it.keepScreenOn }.stateIn(
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt
index e3f04f15..100f06c0 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/chat/components/PartContent.kt
@@ -63,21 +63,21 @@ import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.ToolCardScaffold
 import dev.leonardo.ocremotev2.ui.screens.chat.util.ParsedQuestion
 import dev.leonardo.ocremotev2.ui.screens.chat.util.QHistItem
 import dev.leonardo.ocremotev2.ui.screens.chat.util.QuestionParser
 import dev.leonardo.ocremotev2.ui.screens.chat.util.isAmoledTheme
 import dev.leonardo.ocremotev2.ui.theme.ShapeTokens
 import androidx.compose.foundation.text.selection.SelectionContainer
 import dev.leonardo.ocremotev2.ui.screens.chat.markdown.MarkdownContent
 import dev.leonardo.ocremotev2.ui.screens.chat.tools.ToolCallCard
 import dev.leonardo.ocremotev2.ui.screens.chat.tools.ViewToolRequest
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnViewTool
-import dev.leonardo.ocremotev2.ui.navigation.routes.FileViewerNav
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource
 import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.PatchCard
 import dev.leonardo.ocremotev2.ui.screens.chat.tools.cards.TodoListCard
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalCollapseTools
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalToolCardResolver
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalExpandReasoning
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalHapticFeedbackEnabled
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalOnToggleToolExpanded
 import dev.leonardo.ocremotev2.ui.screens.chat.util.LocalToolExpandedStates
 import kotlinx.serialization.json.contentOrNull
 import kotlinx.serialization.json.jsonPrimitive
@@ -186,22 +186,22 @@ internal fun PartContent(
                 val expanded = toolExpandedStates[part.id] ?: autoExpand
                 val toggleExpand = { onToggleToolExpanded(part.id, autoExpand) }
 
                 // Phase 2: intercept onOpenFile for Read/Write/Edit → TOOL_SNAPSHOT
                 val viewTool = onViewTool ?: LocalOnViewTool.current
                 val toolName = part.tool.lowercase()
                 val isFileTool = toolName in setOf("read", "write", "edit", "multiedit")
                 val isDiffTool = toolName in setOf("edit", "multiedit")
                 val effectiveOnOpenFile: ((String) -> Unit)? = if (viewTool != null && isFileTool) {
                     { filePath ->
-                        val source = if (isDiffTool) FileViewerNav.Source.TOOL_SNAPSHOT_DIFF
-                        else FileViewerNav.Source.TOOL_SNAPSHOT
+                        val source = if (isDiffTool) FileViewerSource.TOOL_SNAPSHOT_DIFF
+                        else FileViewerSource.TOOL_SNAPSHOT
                         viewTool(ViewToolRequest(filePath, source, part))
                     }
                 } else onOpenFile
 
                 val resolved = LocalToolCardResolver.current.resolve(
                     tool = part,
                     isExpanded = expanded,
                     onToggleExpand = toggleExpand,
                     onViewSubSession = onViewSubSession,
                     turnAgentName = turnAgentName,
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerEntryPoint.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerEntryPoint.kt
new file mode 100644
index 00000000..8abfc842
--- /dev/null
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerEntryPoint.kt
@@ -0,0 +1,11 @@
+package dev.leonardo.ocremotev2.ui.screens.viewer
+
+import dagger.hilt.EntryPoint
+import dagger.hilt.InstallIn
+import dagger.hilt.components.SingletonComponent
+
+@EntryPoint
+@InstallIn(SingletonComponent::class)
+interface FileViewerEntryPoint {
+    fun fileViewerViewModelFactory(): FileViewerViewModel.Factory
+}
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerOverlay.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerOverlay.kt
new file mode 100644
index 00000000..126bb0fe
--- /dev/null
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerOverlay.kt
@@ -0,0 +1,141 @@
+package dev.leonardo.ocremotev2.ui.screens.viewer
+
+import android.content.Intent
+import android.widget.Toast
+import androidx.compose.material3.SnackbarHostState
+import androidx.compose.runtime.Composable
+import androidx.compose.runtime.CompositionLocalProvider
+import androidx.compose.runtime.DisposableEffect
+import androidx.compose.runtime.getValue
+import androidx.compose.runtime.mutableStateOf
+import androidx.compose.runtime.remember
+import androidx.compose.runtime.rememberCoroutineScope
+import androidx.compose.runtime.setValue
+import androidx.compose.ui.platform.LocalContext
+import androidx.compose.ui.platform.LocalClipboardManager
+import androidx.compose.ui.text.AnnotatedString
+import androidx.compose.ui.window.Dialog
+import androidx.compose.ui.window.DialogProperties
+import androidx.lifecycle.ViewModel
+import androidx.lifecycle.ViewModelProvider
+import androidx.lifecycle.ViewModelStore
+import androidx.lifecycle.ViewModelStoreOwner
+import androidx.lifecycle.compose.collectAsStateWithLifecycle
+import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
+import androidx.lifecycle.viewmodel.compose.viewModel
+import dagger.hilt.android.EntryPointAccessors
+import dev.leonardo.ocremotev2.R
+import kotlinx.coroutines.launch
+
+@Composable
+fun FileViewerOverlay(
+    params: FileViewerParams,
+    onDismiss: () -> Unit
+) {
+    val overlayOwner = remember { OverlayViewModelStoreOwner() }
+    DisposableEffect(overlayOwner) {
+        onDispose { overlayOwner.viewModelStore.clear() }
+    }
+
+    val context = LocalContext.current
+    val assistedFactory = remember {
+        EntryPointAccessors.fromApplication(
+            context.applicationContext,
+            FileViewerEntryPoint::class.java
+        ).fileViewerViewModelFactory()
+    }
+
+    CompositionLocalProvider(LocalViewModelStoreOwner provides overlayOwner) {
+        val fileViewerViewModel: FileViewerViewModel = viewModel(
+            factory = SimpleViewModelFactory { assistedFactory.create(params) }
+        )
+
+        FileViewerDialogContent(
+            viewModel = fileViewerViewModel,
+            onDismiss = onDismiss
+        )
+    }
+}
+
+@Composable
+private fun FileViewerDialogContent(
+    viewModel: FileViewerViewModel,
+    onDismiss: () -> Unit
+) {
+    Dialog(
+        onDismissRequest = onDismiss,
+        properties = DialogProperties(
+            usePlatformDefaultWidth = false,
+            dismissOnBackPress = true,
+            dismissOnClickOutside = false
+        )
+    ) {
+        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
+        val snackbarHostState = remember { SnackbarHostState() }
+        val clipboard = LocalClipboardManager.current
+        val context = LocalContext.current
+        val scope = rememberCoroutineScope()
+        var isSubmitting by remember { mutableStateOf(false) }
+
+        FileViewerScreen(
+            uiState = uiState,
+            snackbarHostState = snackbarHostState,
+            onBack = onDismiss,
+            onNextHunk = viewModel::nextHunk,
+            onPrevHunk = viewModel::prevHunk,
+            onCopyPath = {
+                clipboard.setText(AnnotatedString(uiState.filePath))
+                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
+            },
+            onShare = {
+                val sendIntent = Intent(Intent.ACTION_SEND).apply {
+                    type = "text/plain"
+                    putExtra(Intent.EXTRA_TEXT, uiState.content.ifBlank { uiState.filePath })
+                }
+                runCatching {
+                    context.startActivity(Intent.createChooser(sendIntent, null))
+                }
+            },
+            onCopyAllContent = {
+                clipboard.setText(AnnotatedString(uiState.content))
+                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
+            },
+            onToggleRenderMode = viewModel::toggleRenderMode,
+            onSwitchToSource = viewModel::switchToSource,
+            onAddAnnotation = { selectedText, startChar, endChar, note ->
+                if (startChar >= 0) {
+                    viewModel.addAnnotation(selectedText, startChar, endChar, note)
+                }
+            },
+            onDeleteAnnotation = viewModel::deleteAnnotation,
+            onUpdateAnnotation = viewModel::updateAnnotation,
+            onLoadMoreLines = viewModel::loadMoreLines,
+            onSubmitAnnotations = { overallNote, editedNotes ->
+                if (!isSubmitting) {
+                    isSubmitting = true
+                    scope.launch {
+                        val result = viewModel.submitAnnotations(overallNote, editedNotes)
+                        isSubmitting = false
+                        if (result.isSuccess) {
+                            Toast.makeText(context, context.getString(R.string.annotation_submitted_toast), Toast.LENGTH_SHORT).show()
+                            onDismiss()
+                        } else {
+                            snackbarHostState.showSnackbar(context.getString(R.string.annotation_submit_failed))
+                        }
+                    }
+                }
+            }
+        )
+    }
+}
+
+private class OverlayViewModelStoreOwner : ViewModelStoreOwner {
+    override val viewModelStore = ViewModelStore()
+}
+
+private class SimpleViewModelFactory(
+    private val create: () -> ViewModel
+) : ViewModelProvider.Factory {
+    @Suppress("UNCHECKED_CAST")
+    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
+}
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerRoute.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerRoute.kt
deleted file mode 100644
index a30c7388..00000000
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/viewer/FileViewerRoute.kt
+++ /dev/null
@@ -1,84 +0,0 @@
-﻿package dev.leonardo.ocremotev2.ui.screens.viewer
-
-import android.content.Intent
-import android.widget.Toast
-import androidx.compose.material3.SnackbarHostState
-import androidx.compose.runtime.Composable
-import androidx.compose.runtime.getValue
-import androidx.compose.runtime.mutableStateOf
-import androidx.compose.runtime.remember
-import androidx.compose.runtime.rememberCoroutineScope
-import androidx.compose.runtime.setValue
-import androidx.compose.ui.platform.LocalClipboardManager
-import androidx.compose.ui.platform.LocalContext
-import androidx.compose.ui.text.AnnotatedString
-import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
-import androidx.lifecycle.compose.collectAsStateWithLifecycle
-import dev.leonardo.ocremotev2.R
-import kotlinx.coroutines.launch
-
-@Composable
-fun FileViewerRoute(
-    viewModel: FileViewerViewModel = hiltViewModel(),
-    onBack: () -> Unit,
-    onSubmitted: () -> Unit = {}
-) {
-    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
-    val clipboard = LocalClipboardManager.current
-    val context = LocalContext.current
-    val snackbarHostState = remember { SnackbarHostState() }
-    val scope = rememberCoroutineScope()
-    var isSubmitting by remember { mutableStateOf(false) }
-
-    FileViewerScreen(
-        uiState = uiState,
-        snackbarHostState = snackbarHostState,
-        onBack = onBack,
-        onNextHunk = viewModel::nextHunk,
-        onPrevHunk = viewModel::prevHunk,
-        onCopyPath = {
-            clipboard.setText(AnnotatedString(uiState.filePath))
-            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
-        },
-        onShare = {
-            val sendIntent = Intent(Intent.ACTION_SEND).apply {
-                type = "text/plain"
-                putExtra(Intent.EXTRA_TEXT, uiState.content.ifBlank { uiState.filePath })
-            }
-            runCatching {
-                context.startActivity(Intent.createChooser(sendIntent, null))
-            }
-        },
-        onCopyAllContent = {
-            clipboard.setText(AnnotatedString(uiState.content))
-            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.menu_copied_to_clipboard)) }
-        },
-        onToggleRenderMode = viewModel::toggleRenderMode,
-        onSwitchToSource = viewModel::switchToSource,
-        // Phase 3: Annotation callbacks
-        onAddAnnotation = { selectedText, startChar, endChar, note ->
-            if (startChar >= 0) {
-                viewModel.addAnnotation(selectedText, startChar, endChar, note)
-            }
-        },
-        onDeleteAnnotation = viewModel::deleteAnnotation,
-        onUpdateAnnotation = viewModel::updateAnnotation,
-        onLoadMoreLines = viewModel::loadMoreLines,
-        onSubmitAnnotations = { overallNote, editedNotes ->
-            if (!isSubmitting) {
-                isSubmitting = true
-                scope.launch {
-                    val result = viewModel.submitAnnotations(overallNote, editedNotes)
-                    isSubmitting = false
-                    if (result.isSuccess) {
-                        // Return immediately — Toast doesn't block (unlike suspend showSnackbar)
-                        Toast.makeText(context, context.getString(R.string.annotation_submitted_toast), Toast.LENGTH_SHORT).show()
-                        onBack()
-                    } else {
-                        snackbarHostState.showSnackbar(context.getString(R.string.annotation_submit_failed))
-                    }
-                }
-            }
-        }
-    )
-}
diff --git a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt
index cbf74f96..11988425 100644
--- a/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt
+++ b/app/src/main/kotlin/dev/leonardo/ocremotev2/ui/screens/workspace/WorkspaceScreen.kt
@@ -15,57 +15,87 @@ import androidx.compose.material3.Badge
 import androidx.compose.material3.BadgedBox
 import androidx.compose.material3.ExperimentalMaterial3Api
 import androidx.compose.material3.Icon
 import androidx.compose.material3.IconButton
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.Scaffold
 import androidx.compose.material3.Text
 import androidx.compose.material3.TopAppBar
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.getValue
+import androidx.compose.runtime.mutableStateOf
+import androidx.compose.runtime.remember
+import androidx.compose.runtime.setValue
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.platform.testTag
 import androidx.compose.ui.res.stringResource
 import androidx.compose.ui.text.style.TextOverflow
 import androidx.compose.ui.unit.dp
 import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
 import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import dev.leonardo.ocremotev2.R
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerOverlay
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerParams
+import dev.leonardo.ocremotev2.ui.screens.viewer.FileViewerSource
 import dev.leonardo.ocremotev2.ui.screens.workspace.git.GitChangesPanel
 import dev.leonardo.ocremotev2.ui.screens.workspace.search.SearchOverlay
 import dev.leonardo.ocremotev2.ui.screens.workspace.search.SearchTopBar
 import dev.leonardo.ocremotev2.ui.screens.workspace.tree.FileTreePanel
 
 @Composable
 fun WorkspaceRoute(
     viewModel: WorkspaceViewModel = hiltViewModel(),
-    onBack: () -> Unit,
-    onOpenFile: (filePath: String) -> Unit,
-    onOpenGitDiff: (filePath: String) -> Unit
+    serverId: String,
+    sessionId: String,
+    onBack: () -> Unit
 ) {
     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
+    var fileViewerRequest by remember { mutableStateOf<FileViewerParams?>(null) }
     WorkspaceScreen(
         uiState = uiState,
         onBack = onBack,
         onSwitchPanel = viewModel::switchPanel,
         onRefreshRoot = viewModel::refreshRoot,
         onToggleShowIgnored = viewModel::toggleShowIgnored,
         onToggleExpand = viewModel::toggleExpand,
         onRefreshGit = viewModel::loadGitChanges,
-        onOpenFile = onOpenFile,
-        onOpenGitDiff = onOpenGitDiff,
+        onOpenFile = { filePath ->
+            fileViewerRequest = FileViewerParams(
+                serverId = serverId,
+                sessionId = sessionId,
+                filePath = filePath,
+                directory = uiState.directory,
+                source = FileViewerSource.LIVE
+            )
+        },
+        onOpenGitDiff = { filePath ->
+            fileViewerRequest = FileViewerParams(
+                serverId = serverId,
+                sessionId = sessionId,
+                filePath = filePath,
+                directory = uiState.directory,
+                source = FileViewerSource.GIT_DIFF
+            )
+        },
         // Phase 2: Search
         onEnterSearch = viewModel::enterSearch,
         onExitSearch = viewModel::exitSearch,
         onSearchQueryChange = viewModel::updateSearchQuery
     )
+
+    fileViewerRequest?.let { params ->
+        FileViewerOverlay(
+            params = params,
+            onDismiss = { fileViewerRequest = null }
+        )
+    }
 }
 
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable
 fun WorkspaceScreen(
     uiState: WorkspaceUiState,
     onBack: () -> Unit,
     onSwitchPanel: (WorkspacePanel) -> Unit,
     onRefreshRoot: () -> Unit,
     onToggleShowIgnored: () -> Unit,
