package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DocumentMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val documentId: String = "",
    val role: String = "user", // "user" or "model"
    val content: String = "",
    val pageReferences: List<Long> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)
