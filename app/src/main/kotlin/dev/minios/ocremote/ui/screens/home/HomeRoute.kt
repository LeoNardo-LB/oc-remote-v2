package dev.minios.ocremote.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Route wrapper for HomeScreen.
 * Handles ViewModel binding and navigation parameter extraction.
 * NavGraph calls this instead of HomeScreen directly.
 */
@Composable
fun HomeRoute(
    onNavigateToSessions: (String, String, String, String, String) -> Unit,
    onNavigateToServerSettings: (String, String, String, String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val viewModel: HomeViewModel = hiltViewModel()
    HomeScreen(
        viewModel = viewModel,
        onNavigateToSessions = onNavigateToSessions,
        onNavigateToServerSettings = onNavigateToServerSettings,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAbout = onNavigateToAbout
    )
}
