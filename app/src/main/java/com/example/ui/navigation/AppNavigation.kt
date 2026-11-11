package com.example.ui.navigation

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.ai.InsightAiService
import com.example.data.model.DocumentItem
import com.example.data.repository.DocumentRepository
import com.example.ui.components.InsightTopAppBar
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DesktopDualPaneScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentListScreen
import com.example.ui.screens.SettingsScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenTab(val title: String) {
    VAULT("Vault"),
    CHAT("Agent Chat"),
    SETTINGS("Settings")
}

@Composable
fun AppNavigation(
    currentUserId: String,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember { context.getSharedPreferences("insightflow_prefs", Context.MODE_PRIVATE) }

    val repository = remember(currentUserId) { DocumentRepository(context) }
    val aiService = remember { InsightAiService() }

    var isDesktopMode by remember {
        mutableStateOf(prefs.getBoolean("desktop_mode_preference", false))
    }

    var currentTab by remember { mutableStateOf(ScreenTab.VAULT) }
    var selectedDetailDocId by remember { mutableStateOf<String?>(null) }
    var chatGroundedDocId by remember { mutableStateOf<String?>(null) }
    var currentLanguage by remember { mutableStateOf("English") }

    // Observe documents in real time
    val documents by repository.observeDocuments().collectAsState(initial = emptyList())

    val activeDetailDoc = remember(selectedDetailDocId, documents) {
        documents.find { it.id == selectedDetailDocId }
    }

    fun toggleDesktopMode() {
        val next = !isDesktopMode
        isDesktopMode = next
        prefs.edit().putBoolean("desktop_mode_preference", next).apply()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val effectiveDesktopMode = isDesktopMode || maxWidth >= 720.dp

        if (effectiveDesktopMode && currentTab != ScreenTab.SETTINGS) {
            // ==========================================
            // DESKTOP DUAL-PANE MODE
            // ==========================================
            Scaffold(
                topBar = {
                    InsightTopAppBar(
                        title = "InsightFlow Workspace",
                        currentLanguage = currentLanguage,
                        onLanguageSelected = { currentLanguage = it },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode,
                        isDesktopMode = true,
                        onToggleDesktopMode = { toggleDesktopMode() }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("desktop_bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.VAULT || currentTab == ScreenTab.CHAT,
                            onClick = { currentTab = ScreenTab.VAULT },
                            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Workspace") },
                            label = { Text("Dual Workspace") },
                            modifier = Modifier.testTag("nav_item_dual_workspace")
                        )
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.SETTINGS,
                            onClick = { currentTab = ScreenTab.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    }
                }
            ) { innerPadding ->
                DesktopDualPaneScreen(
                    documents = documents,
                    currentLanguage = currentLanguage,
                    repository = repository,
                    aiService = aiService,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        } else if (selectedDetailDocId != null) {
            // ==========================================
            // MOBILE DOCUMENT DETAIL / 3D DEEP DIVE
            // ==========================================
            DocumentDetailScreen(
                document = activeDetailDoc,
                currentLanguage = currentLanguage,
                repository = repository,
                aiService = aiService,
                onBack = { selectedDetailDocId = null },
                onStartChat = { docId ->
                    selectedDetailDocId = null
                    chatGroundedDocId = docId
                    currentTab = ScreenTab.CHAT
                }
            )
        } else {
            // ==========================================
            // MOBILE SINGLE-PANE MODE
            // ==========================================
            Scaffold(
                topBar = {
                    InsightTopAppBar(
                        title = when (currentTab) {
                            ScreenTab.VAULT -> "Knowledge Vault"
                            ScreenTab.CHAT -> "InsightFlow Agent"
                            ScreenTab.SETTINGS -> "Settings"
                        },
                        currentLanguage = currentLanguage,
                        onLanguageSelected = { currentLanguage = it },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode,
                        isDesktopMode = false,
                        onToggleDesktopMode = { toggleDesktopMode() }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("main_bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.VAULT,
                            onClick = { currentTab = ScreenTab.VAULT },
                            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Vault") },
                            label = { Text("Vault") },
                            modifier = Modifier.testTag("nav_item_vault")
                        )
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.CHAT,
                            onClick = { currentTab = ScreenTab.CHAT },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Agent Chat") },
                            label = { Text("Agent Chat") },
                            modifier = Modifier.testTag("nav_item_chat")
                        )
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.SETTINGS,
                            onClick = { currentTab = ScreenTab.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_item_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        ScreenTab.VAULT -> {
                            DocumentListScreen(
                                documents = documents,
                                isLoading = documents.isEmpty(),
                                currentLanguage = currentLanguage,
                                repository = repository,
                                aiService = aiService,
                                onDocumentClick = { docId -> selectedDetailDocId = docId },
                                onChatClick = { docId ->
                                    chatGroundedDocId = docId
                                    currentTab = ScreenTab.CHAT
                                }
                            )
                        }
                        ScreenTab.CHAT -> {
                            BackHandler {
                                currentTab = ScreenTab.VAULT
                            }
                            ChatScreen(
                                documents = documents,
                                initialDocumentId = chatGroundedDocId,
                                currentLanguage = currentLanguage,
                                onLanguageChange = { currentLanguage = it },
                                repository = repository,
                                aiService = aiService
                            )
                        }
                        ScreenTab.SETTINGS -> {
                            BackHandler {
                                currentTab = ScreenTab.VAULT
                            }
                            SettingsScreen(
                                currentLanguage = currentLanguage,
                                onLanguageChange = { currentLanguage = it },
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = onToggleDarkMode,
                                isDesktopMode = isDesktopMode,
                                onToggleDesktopMode = { toggleDesktopMode() },
                                onSignOut = {
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
