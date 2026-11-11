package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.InsightAiService
import com.example.data.model.DocumentItem
import com.example.data.repository.DocumentRepository
import com.example.ui.components.DocumentCard
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.util.ExportHelper
import com.example.util.PdfProcessor
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentListScreen(
    documents: List<DocumentItem>,
    isLoading: Boolean,
    currentLanguage: String,
    repository: DocumentRepository,
    aiService: InsightAiService,
    onDocumentClick: (String) -> Unit,
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isProcessingFile by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("") }

    // PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingFile = true
                processingStatus = "Parsing PDF pages..."
                try {
                    val processed = PdfProcessor.processPdfUri(context, uri)
                    processingStatus = "Generating AI Summary & Findings..."

                    val summaryResult = aiService.summarizeDocument(
                        documentTitle = processed.title,
                        extractedText = processed.extractedText,
                        pageBitmaps = processed.previewBitmaps,
                        targetLanguage = currentLanguage
                    )

                    processingStatus = "Saving to Searchable Cloud Vault..."
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
                    val docId = repository.addDocument(newDoc)
                    Toast.makeText(context, "Document analyzed & synchronized!", Toast.LENGTH_SHORT).show()
                    onDocumentClick(docId)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessingFile = false
                    processingStatus = ""
                }
            }
        }
    }

    // Collect all available tags
    val allTags = remember(documents) {
        documents.flatMap { it.tags }.distinct().sorted()
    }

    // Filter documents by search and tag
    val filteredDocuments = remember(documents, searchQuery, selectedTag) {
        documents.filter { doc ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                doc.title.contains(searchQuery, ignoreCase = true) ||
                        doc.originalFileName.contains(searchQuery, ignoreCase = true) ||
                        doc.summary.contains(searchQuery, ignoreCase = true) ||
                        doc.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            val matchesTag = if (selectedTag == null) true else doc.tags.contains(selectedTag)
            matchesSearch && matchesTag
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, tags, or findings...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("document_search_input")
            )

            // Tags Filter Row
            if (allTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("All (${documents.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text("#$tag") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandSecondary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Processing Indicator
            AnimatedVisibility(visible = isProcessingFile) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = processingStatus,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Document List or Empty State
            if (isLoading && documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else if (filteredDocuments.isEmpty()) {
                EmptyDocumentsView(
                    isSearch = searchQuery.isNotBlank() || selectedTag != null,
                    onLoadSamples = {
                        scope.launch {
                            val samples = PdfProcessor.getSampleDocuments()
                            samples.forEach { sample ->
                                repository.addDocument(sample)
                            }
                            Toast.makeText(context, "Loaded 3 sample research documents!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUploadClick = { pdfPickerLauncher.launch("application/pdf") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(filteredDocuments, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onClick = { onDocumentClick(doc.id) },
                            onChatClick = { onChatClick(doc.id) },
                            onExportClick = { ExportHelper.shareSummary(context, doc) },
                            onDeleteClick = {
                                scope.launch {
                                    repository.deleteDocument(doc.id)
                                    Toast.makeText(context, "Document removed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB to upload PDF
        ExtendedFloatingActionButton(
            onClick = { pdfPickerLauncher.launch("application/pdf") },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
            text = { Text("Upload PDF", fontWeight = FontWeight.Bold) },
            containerColor = BrandPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("upload_pdf_fab")
        )
    }
}

@Composable
fun EmptyDocumentsView(
    isSearch: Boolean,
    onLoadSamples: () -> Unit,
    onUploadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Default.Search else Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearch) "No documents match your query" else "Your Knowledge Vault is Empty",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearch) "Try searching for a different keyword or clearing filters." else "Upload any PDF report, paper, or contract to get automatic summaries and conversational Q&A.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isSearch) {
            Button(
                onClick = onLoadSamples,
                modifier = Modifier.testTag("load_samples_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Sample Intelligence Docs")
            }
        }
    }
}
