package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DocumentItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun authenticatedUser_canCreateAndReadDocument() = runBlocking {
    val uid = signInTestUser("alice@insightflow.app")
    val repository = DocumentRepository(firestore)

    val doc = DocumentItem(
      title = "AI Research Paper",
      originalFileName = "paper.pdf",
      fileSizeBytes = 4096L,
      pageCount = 12L,
      extractedText = "This research studies LLM agents in production.",
      summary = "Breakthroughs in agent autonomy and multimodal retrieval.",
      language = "English",
      tags = listOf("AI", "Research")
    )

    val docId = repository.addDocument(doc)
    assertNotNull(docId)
    assertTrue(docId.isNotBlank())

    val readBack = repository.observeDocument(docId).first()
    assertNotNull(readBack)
    assertEquals("AI Research Paper", readBack?.title)
    assertEquals(uid, readBack?.userId)
  }

  @Test
  fun unauthenticatedUser_cannotWriteDocument() = runBlocking {
    auth.signOut()
    val repository = DocumentRepository(firestore)

    val doc = DocumentItem(
      title = "Unauth Document",
      originalFileName = "unauth.pdf"
    )

    try {
      repository.addDocument(doc)
      fail("Expected exception when unauthenticated")
    } catch (e: Exception) {
      assertTrue(e is IllegalStateException || e.message?.contains("User must be signed in") == true)
    }
  }

  @Test
  fun authenticatedUser_canAddMessageToDocument() = runBlocking {
    val uid = signInTestUser("bob@insightflow.app")
    val repository = DocumentRepository(firestore)

    val doc = DocumentItem(
      title = "Financial Report Q3",
      originalFileName = "q3.pdf",
      fileSizeBytes = 2048L,
      pageCount = 5L,
      extractedText = "Revenue grew 24% YoY.",
      summary = "Strong Q3 earnings with margin expansion."
    )
    val docId = repository.addDocument(doc)

    val msgId = repository.addDocumentMessage(
      documentId = docId,
      role = "user",
      content = "What was the revenue growth?",
      pageReferences = listOf(2L)
    )
    assertTrue(msgId.isNotBlank())

    val messages = repository.observeDocumentMessages(docId).first()
    assertEquals(1, messages.size)
    assertEquals("What was the revenue growth?", messages[0].content)
    assertEquals(uid, messages[0].userId)
  }
}
