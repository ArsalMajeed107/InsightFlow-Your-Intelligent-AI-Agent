package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ai.InsightAiService
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentMessage
import com.example.data.repository.DocumentRepository
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.SupportedLanguages
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.util.VoiceInputHelper
import com.example.util.VoiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    documents: List<DocumentItem>,
    initialDocumentId: String?,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    repository: DocumentRepository,
    aiService: InsightAiService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedDocumentId by remember { mutableStateOf(initialDocumentId ?: documents.firstOrNull()?.id) }
    val activeDocument = remember(selectedDocumentId, documents) {
        documents.find { it.id == selectedDocumentId }
    }

    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var currentStreamResponse by remember { mutableStateOf("") }
    var showDocSelector by remember { mutableStateOf(false) }

    // Persistent messages loaded from repository
    val messages = remember { mutableStateListOf<DocumentMessage>() }

    // Load messages whenever activeDocument changes
    LaunchedEffect(selectedDocumentId) {
        if (!selectedDocumentId.isNullOrBlank()) {
            scope.launch {
                try {
                    val loaded = repository.observeDocumentMessages(selectedDocumentId!!).first()
                    messages.clear()
                    messages.addAll(loaded)
                } catch (e: Exception) {
                    // Start fresh
                    messages.clear()
                }
            }
        }
    }

    // Voice recognition helper
    val voiceHelper = remember { VoiceInputHelper(context) }
    val voiceState by voiceHelper.voiceState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.stopListening()
        }
    }

    // Handle voice transcriptions
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.PartialResult -> {
                inputText = state.text
            }
            is VoiceState.FinalResult -> {
                inputText = state.text
                voiceHelper.resetState()
            }
            is VoiceState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                voiceHelper.resetState()
            }
            else -> {}
        }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceHelper.startListening()
        } else {
            Toast.makeText(context, "Microphone permission required for voice questions", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSend(textToSend: String) {
        if (textToSend.isBlank() || isStreaming) return
        val question = textToSend.trim()
        inputText = ""

        val userMessage = DocumentMessage(
            documentId = selectedDocumentId ?: "general",
            role = "user",
            content = question
        )
        messages.add(userMessage)

        scope.launch {
            // Scroll to bottom
            listState.animateScrollToItem(messages.size)

            // Persist user message to Firestore if grounded in document
            if (!selectedDocumentId.isNullOrBlank()) {
                try {
                    repository.addDocumentMessage(selectedDocumentId!!, "user", question)
                } catch (e: Exception) {
                    // Logged by repository
                }
            }

            // Stream AI response
            isStreaming = true
            currentStreamResponse = ""

            val history = messages.dropLast(1).map { it.role to it.content }
            val docTitle = activeDocument?.title ?: "Knowledge Vault"
            val docText = activeDocument?.extractedText ?: ""

            val streamFlow = if (docText.isNotBlank()) {
                aiService.answerQuestionStream(
                    question = question,
                    documentTitle = docTitle,
                    documentText = docText,
                    previousConversation = history,
                    targetLanguage = currentLanguage
                )
            } else {
                aiService.chatStream(
                    prompt = question,
                    history = history,
                    targetLanguage = currentLanguage
                )
            }

            val accumulated = StringBuilder()
            streamFlow.collect { chunk ->
                accumulated.append(chunk)
                currentStreamResponse = accumulated.toString()
                listState.scrollToItem(messages.size)
            }

            val finalReply = accumulated.toString()
            isStreaming = false
            currentStreamResponse = ""

            val aiMessage = DocumentMessage(
                documentId = selectedDocumentId ?: "general",
                role = "model",
                content = finalReply
            )
            messages.add(aiMessage)

            // Persist model response
            if (!selectedDocumentId.isNullOrBlank() && finalReply.isNotBlank()) {
                try {
                    repository.addDocumentMessage(selectedDocumentId!!, "model", finalReply)
                } catch (e: Exception) {
                    // Handled
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Document & Language Context Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.example.ui.components.ThreeDAiOrb(isThinking = isStreaming, size = 32.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    // Grounded Document Chip
                    Box {
                        Surface(
                            color = BrandPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showDocSelector = true }
                                .testTag("select_active_document_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeDocument?.title ?: "Select Document",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDocSelector,
                            onDismissRequest = { showDocSelector = false }
                        ) {
                            documents.forEach { doc ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = doc.title,
                                            fontWeight = if (doc.id == selectedDocumentId) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedDocumentId = doc.id
                                        showDocSelector = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Language indicator
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = BrandSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentLanguage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Chat Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isStreaming) {
                // Empty state with suggested starter prompts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    com.example.ui.components.ThreeDAiOrb(isThinking = isStreaming, size = 100.dp)

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Ask InsightFlow Agent",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (activeDocument != null) "Grounded in: ${activeDocument.title}" else "Select a document to ask grounded questions or chat freely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Starter prompt buttons
                    val starterPrompts = listOf(
                        "Summarize key takeaways with citations",
                        "What are the main risks or limitations?",
                        "What action items are recommended?",
                        "Explain the core concept in simple terms"
                    )

                    starterPrompts.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { handleSend(prompt) }
                        ) {
                            Text(
                                text = "💡 $prompt",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(messages) { msg ->
                        ChatMessageBubble(
                            role = msg.role,
                            content = msg.content,
                            pageReferences = msg.pageReferences
                        )
                    }

                    if (isStreaming && currentStreamResponse.isNotBlank()) {
                        item {
                            ChatMessageBubble(
                                role = "model",
                                content = currentStreamResponse,
                                isStreaming = true
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        // Voice Listening Indicator Banner
        AnimatedVisibility(visible = voiceState is VoiceState.Listening) {
            Surface(
                color = AccentRose.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AccentRose)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Listening... Speak your question now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRose
                        )
                    }

                    IconButton(
                        onClick = { voiceHelper.stopListening() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Listening",
                            tint = AccentRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Input Mic Button
                val isListening = voiceState is VoiceState.Listening
                IconButton(
                    onClick = {
                        if (isListening) {
                            voiceHelper.stopListening()
                        } else {
                            val permissionCheck = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                voiceHelper.startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) AccentRose else MaterialTheme.colorScheme.primary
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask about document or type...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .testTag("chat_message_input")
                )

                // Send Button
                FilledIconButton(
                    onClick = { handleSend(inputText) },
                    enabled = inputText.isNotBlank() && !isStreaming,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = BrandPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    if (isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
