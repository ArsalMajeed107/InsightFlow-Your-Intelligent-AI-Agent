package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.InsightAiService
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentMessage
import com.example.data.repository.DocumentRepository
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.TagBadge
import com.example.ui.components.ThreeDAiOrb
import com.example.ui.components.ThreeDCard
import com.example.ui.components.ThreeDPageStack
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.util.ExportHelper
import com.example.util.PdfProcessor
import com.example.util.VoiceInputHelper
import com.example.util.VoiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DesktopDualPaneScreen(
    documents: List<DocumentItem>,
    currentLanguage: String,
    repository: DocumentRepository,
    aiService: InsightAiService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDocumentId by remember { mutableStateOf(documents.firstOrNull()?.id) }
    val selectedDoc = remember(selectedDocumentId, documents) {
        documents.find { it.id == selectedDocumentId }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isProcessingFile by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("") }

    // Chat states for the right pane
    val chatMessages = remember { mutableStateListOf<DocumentMessage>() }
    var chatInput by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var currentStreamResponse by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()
    var rightPaneTab by remember { mutableIntStateOf(0) } // 0: Live Q&A, 1: Full Document Text

    // Voice helper
    val voiceHelper = remember { VoiceInputHelper(context) }
    val voiceState by voiceHelper.voiceState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { voiceHelper.stopListening() }
    }

    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceState.PartialResult -> chatInput = state.text
            is VoiceState.FinalResult -> {
                chatInput = state.text
                voiceHelper.resetState()
            }
            is VoiceState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                voiceHelper.resetState()
            }
            else -> {}
        }
    }

    // Load messages when selected document changes
    LaunchedEffect(selectedDocumentId) {
        if (!selectedDocumentId.isNullOrBlank()) {
            scope.launch {
                try {
                    val msgs = repository.observeDocumentMessages(selectedDocumentId!!).first()
                    chatMessages.clear()
                    chatMessages.addAll(msgs)
                } catch (e: Exception) {
                    chatMessages.clear()
                }
            }
        }
    }

    // PDF picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingFile = true
                processingStatus = "Processing PDF pages..."
                try {
                    val processed = PdfProcessor.processPdfUri(context, uri)
                    processingStatus = "Generating AI Summary & Findings..."

                    val summaryResult = aiService.summarizeDocument(
                        documentTitle = processed.title,
                        extractedText = processed.extractedText,
                        pageBitmaps = processed.previewBitmaps,
                        targetLanguage = currentLanguage
                    )

                    processingStatus = "Saving to Searchable Vault..."
                    val newDoc = DocumentItem(
                        title = summaryResult.title.ifBlank { processed.title },
                        originalFileName = processed.fileName,
                        fileSizeBytes = processed.fileSizeBytes,
                        pageCount = processed.pageCount,
                        extractedText = processed.extractedText,
                        summary = summaryResult.executiveSummary,
                        language = currentLanguage,
                        tags = summaryResult.tags
                    )
                    val newId = repository.addDocument(newDoc)
                    selectedDocumentId = newId
                    Toast.makeText(context, "Document added to Desktop Workspace!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingFile = false
                    processingStatus = ""
                }
            }
        }
    }

    val filteredDocs = remember(documents, searchQuery, selectedTag) {
        documents.filter { doc ->
            val matchQuery = if (searchQuery.isBlank()) true else {
                doc.title.contains(searchQuery, ignoreCase = true) ||
                        doc.originalFileName.contains(searchQuery, ignoreCase = true) ||
                        doc.summary.contains(searchQuery, ignoreCase = true) ||
                        doc.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            val matchTag = if (selectedTag == null) true else doc.tags.contains(selectedTag)
            matchQuery && matchTag
        }
    }

    fun handleSend(text: String) {
        if (text.isBlank() || isStreaming) return
        val q = text.trim()
        chatInput = ""

        val userMsg = DocumentMessage(
            documentId = selectedDocumentId ?: "general",
            role = "user",
            content = q
        )
        chatMessages.add(userMsg)

        scope.launch {
            chatListState.animateScrollToItem(chatMessages.size)

            if (!selectedDocumentId.isNullOrBlank()) {
                try {
                    repository.addDocumentMessage(selectedDocumentId!!, "user", q)
                } catch (e: Exception) {}
            }

            isStreaming = true
            currentStreamResponse = ""

            val history = chatMessages.dropLast(1).map { it.role to it.content }
            val docTitle = selectedDoc?.title ?: "Knowledge Vault"
            val docText = selectedDoc?.extractedText ?: ""

            val stream = if (docText.isNotBlank()) {
                aiService.answerQuestionStream(
                    question = q,
                    documentTitle = docTitle,
                    documentText = docText,
                    previousConversation = history,
                    targetLanguage = currentLanguage
                )
            } else {
                aiService.chatStream(q, history, currentLanguage)
            }

            val sb = StringBuilder()
            stream.collect { chunk ->
                sb.append(chunk)
                currentStreamResponse = sb.toString()
                chatListState.scrollToItem(chatMessages.size)
            }

            val finalReply = sb.toString()
            isStreaming = false
            currentStreamResponse = ""

            val aiMsg = DocumentMessage(
                documentId = selectedDocumentId ?: "general",
                role = "model",
                content = finalReply
            )
            chatMessages.add(aiMsg)

            if (!selectedDocumentId.isNullOrBlank() && finalReply.isNotBlank()) {
                try {
                    repository.addDocumentMessage(selectedDocumentId!!, "model", finalReply)
                } catch (e: Exception) {}
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ==========================================
        // LEFT PANE: KNOWLEDGE VAULT (38% - 42% WIDTH)
        // ==========================================
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .padding(14.dp)
        ) {
            // Desktop 3D Stats Header Card
            ThreeDCard(
                highlightColor = BrandPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HD Cloud Vault",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${documents.size} Documents Indexed",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                        )
                        Text(
                            text = "Real-time Multi-device Sync Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ThreeDAiOrb(isThinking = isProcessingFile, size = 48.dp)
                }
            }

            // Search Bar & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search vault...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.testTag("desktop_upload_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sample Knowledge loader if empty
            if (documents.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val samples = PdfProcessor.getSampleDocuments()
                            samples.forEach { repository.addDocument(it) }
                            Toast.makeText(context, "Loaded sample research briefs!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Sample Intelligence Briefs")
                }
            }

            // Processing indicator
            AnimatedVisibility(visible = isProcessingFile) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(processingStatus, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Document List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDocs, key = { it.id }) { doc ->
                    val isSelected = doc.id == selectedDocumentId

                    ThreeDCard(
                        highlightColor = if (isSelected) BrandSecondary else BrandPrimary,
                        onClick = { selectedDocumentId = doc.id },
                        modifier = Modifier.testTag("desktop_doc_${doc.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) BrandPrimary.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThreeDPageStack(pageCount = doc.pageCount)

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${doc.pageCount} pages • ${doc.language}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Surface(
                                    color = BrandSecondary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // ==========================================
        // RIGHT PANE: 3D DEEP DIVE & LIVE CHAT Q&A
        // ==========================================
        if (selectedDoc == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ThreeDAiOrb(isThinking = false, size = 120.dp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "InsightFlow Desktop Workspace",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a document from the vault to inspect findings or start live AI Q&A.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                // Top Document Header Bar with Actions
                ThreeDCard(
                    highlightColor = BrandSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDoc.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedDoc.originalFileName} • ${selectedDoc.pageCount} pages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                selectedDoc.tags.take(3).forEach { tag ->
                                    TagBadge(tag = tag)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { ExportHelper.shareSummary(context, selectedDoc) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repository.deleteDocument(selectedDoc.id)
                                        selectedDocumentId = null
                                        Toast.makeText(context, "Deleted document", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Summary / Key Findings Card (Top section of right pane)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = BrandSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Executive Findings & Summary",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = BrandSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = selectedDoc.summary,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Live Conversational Agent Chat Stream (Bottom section of right pane)
                Text(
                    text = "Grounded AI Chat (${currentLanguage})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Chat Messages List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chatMessages.isEmpty() && !isStreaming) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ThreeDAiOrb(isThinking = false, size = 64.dp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Ask any question about this document",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "The agent answers accurately with page references in $currentLanguage.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(chatMessages) { msg ->
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
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Desktop Input Row (Mic + Text Field + Send)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isListening = voiceState is VoiceState.Listening
                        IconButton(
                            onClick = {
                                if (isListening) voiceHelper.stopListening() else voiceHelper.startListening()
                            }
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = if (isListening) MaterialTheme.colorScheme.error else BrandPrimary
                            )
                        }

                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask a question about ${selectedDoc.title}...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .testTag("desktop_chat_input")
                        )

                        FilledIconButton(
                            onClick = { handleSend(chatInput) },
                            enabled = chatInput.isNotBlank() && !isStreaming,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPrimary),
                            modifier = Modifier.testTag("desktop_send_button")
                        ) {
                            if (isStreaming) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
