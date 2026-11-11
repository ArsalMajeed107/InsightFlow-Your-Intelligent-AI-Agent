package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentMessage
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DocumentRepository(private val db: FirebaseFirestore) {

    // Secondary constructor: resolves named database ID dynamically from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- Documents ---

    fun observeDocuments(): Flow<List<DocumentItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/documents"
        emitAll(
            db.collection("users").document(uid).collection("documents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DocumentItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDocument(documentId: String): Flow<DocumentItem?> = flow {
        val uid = requireUserId()
        val path = "users/$uid/documents/$documentId"
        emitAll(
            db.collection("users").document(uid).collection("documents").document(documentId)
                .snapshots()
                .map { snapshot -> snapshot.toObject(DocumentItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addDocument(doc: DocumentItem): String {
        val uid = requireUserId()
        val collection = db.collection("users").document(uid).collection("documents")
        val docRef = if (doc.id.isNotBlank()) collection.document(doc.id) else collection.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to doc.title,
            "originalFileName" to doc.originalFileName,
            "fileSizeBytes" to doc.fileSizeBytes,
            "pageCount" to doc.pageCount,
            "extractedText" to doc.extractedText,
            "summary" to doc.summary,
            "language" to doc.language,
            "tags" to doc.tags,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateDocumentSummary(docId: String, summary: String, tags: List<String>, title: String? = null) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("documents").document(docId)
        val snapshot = docRef.get().await()
        val current = snapshot.toObject(DocumentItem::class.java)
            ?: throw IllegalStateException("Document not found: $docId")
        
        val updated = current.copy(
            summary = summary,
            tags = tags,
            title = title ?: current.title,
            updatedAt = null // Triggers server timestamp while keeping createdAt
        )
        try {
            docRef.set(updated).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDocument(documentId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("documents").document(documentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Document Messages ---

    fun observeDocumentMessages(documentId: String): Flow<List<DocumentMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/documents/$documentId/messages"
        emitAll(
            db.collection("users").document(uid).collection("documents").document(documentId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DocumentMessage::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addDocumentMessage(
        documentId: String,
        role: String,
        content: String,
        pageReferences: List<Long> = emptyList()
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("documents").document(documentId)
            .collection("messages").document()

        val payload = mapOf(
            "userId" to uid,
            "documentId" to documentId,
            "role" to role,
            "content" to content,
            "pageReferences" to pageReferences,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Global Chats ---

    fun observeChats(): Flow<List<ChatSession>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chats"
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ChatSession::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChatSession(title: String, documentId: String = "", language: String = "English"): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chats").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title,
            "documentId" to documentId,
            "language" to language,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeChatMessages(chatId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(uid).collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(ChatMessage::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addChatMessage(chatId: String, role: String, content: String): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chats").document(chatId).collection("messages").document()
        val payload = mapOf(
            "userId" to uid,
            "chatId" to chatId,
            "role" to role,
            "content" to content,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            // Touch chat updatedAt
            db.collection("users").document(uid).collection("chats").document(chatId)
                .update("updatedAt", FieldValue.serverTimestamp()).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChatSession(chatId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- User Profile ---

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot -> snapshot.toObject(UserProfile::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(displayName: String, email: String, preferredLanguage: String, darkMode: Boolean) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "email" to email,
                "preferredLanguage" to preferredLanguage,
                "darkMode" to darkMode,
                "createdAt" to (snapshot.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        } else {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "email" to email,
                "preferredLanguage" to preferredLanguage,
                "darkMode" to darkMode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
    }
}
