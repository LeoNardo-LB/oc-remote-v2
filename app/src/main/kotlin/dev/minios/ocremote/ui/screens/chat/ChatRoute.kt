package dev.minios.ocremote.ui.screens.chat

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder

/**
 * Encapsulates the Chat screen's navigation route definition and parameter extraction.
 *
 * Usage in NavGraph:
 * ```
 * NavGraphBuilder.chatScreen(
 *     onNavigateBack = { ... },
 *     onNavigateToSession = { ... },
 *     onNavigateToChildSession = { ... },
 *     onOpenInWebView = { ... },
 *     getPendingShare = { sessionId -> ... },
 *     consumeShare = { ... }
 * )
 * ```
 *
 * Route creation is still handled by [Screen.Chat.createRoute] in the sealed Screen hierarchy
 * to keep consistency with the rest of the app.
 */
object ChatRoute {
    const val ROUTE_PATTERN =
        "chat?serverUrl={serverUrl}&username={username}&password={password}" +
            "&serverName={serverName}&serverId={serverId}&sessionId={sessionId}" +
            "&openTerminal={openTerminal}"

    val ARGUMENTS = listOf(
        navArgument("serverUrl") { type = NavType.StringType },
        navArgument("username") { type = NavType.StringType },
        navArgument("password") { type = NavType.StringType },
        navArgument("serverName") { type = NavType.StringType },
        navArgument("serverId") { type = NavType.StringType },
        navArgument("sessionId") { type = NavType.StringType },
        navArgument("openTerminal") { type = NavType.BoolType; defaultValue = false },
    )

    /** Extract all route parameters from a back-stack entry. */
    fun extractArgs(entry: androidx.navigation.NavBackStackEntry): Args {
        val args = entry.arguments
        return Args(
            serverUrl = URLDecoder.decode(args?.getString("serverUrl").orEmpty(), "UTF-8"),
            username = URLDecoder.decode(args?.getString("username").orEmpty(), "UTF-8"),
            password = URLDecoder.decode(args?.getString("password").orEmpty(), "UTF-8"),
            serverName = URLDecoder.decode(args?.getString("serverName").orEmpty(), "UTF-8"),
            serverId = URLDecoder.decode(args?.getString("serverId").orEmpty(), "UTF-8"),
            sessionId = URLDecoder.decode(args?.getString("sessionId").orEmpty(), "UTF-8"),
            openTerminal = args?.getBoolean("openTerminal") ?: false,
        )
    }

    data class Args(
        val serverUrl: String,
        val username: String,
        val password: String,
        val serverName: String,
        val serverId: String,
        val sessionId: String,
        val openTerminal: Boolean = false,
    )
}

/**
 * Registers the Chat screen composable destination in the navigation graph.
 * The caller provides callbacks that are forwarded directly to [ChatScreen].
 *
 * @param onNavigateBack pop to previous destination
 * @param onNavigateToSession navigate to a different session within the same server
 * @param onNavigateToChildSession navigate to a child (sub-agent) session
 * @param onOpenInWebView open the current session in the WebView screen
 * @param getPendingShare given a sessionId, return the pending shared images (if any)
 * @param consumeShare clear the pending share state after images have been consumed
 */
fun NavGraphBuilder.chatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (serverUrl: String, username: String, password: String,
                          serverName: String, serverId: String, sessionId: String) -> Unit,
    onNavigateToChildSession: (serverUrl: String, username: String, password: String,
                               serverName: String, serverId: String, sessionId: String) -> Unit,
    onOpenInWebView: (serverUrl: String, username: String, password: String,
                      serverName: String, sessionId: String) -> Unit,
    getPendingShare: (sessionId: String) -> List<Uri>,
    consumeShare: () -> Unit,
) {
    composable(
        route = ChatRoute.ROUTE_PATTERN,
        arguments = ChatRoute.ARGUMENTS,
    ) { backStackEntry ->
        val args = ChatRoute.extractArgs(backStackEntry)

        val sharedImages = getPendingShare(args.sessionId)

        ChatScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSession = { newSessionId ->
                onNavigateToSession(
                    args.serverUrl, args.username, args.password,
                    args.serverName, args.serverId, newSessionId,
                )
            },
            onNavigateToChildSession = { childSessionId ->
                onNavigateToChildSession(
                    args.serverUrl, args.username, args.password,
                    args.serverName, args.serverId, childSessionId,
                )
            },
            onOpenInWebView = {
                onOpenInWebView(
                    args.serverUrl, args.username, args.password,
                    args.serverName, args.sessionId,
                )
            },
            initialSharedImages = sharedImages,
            onSharedImagesConsumed = consumeShare,
            startInTerminalMode = args.openTerminal,
        )
    }
}
