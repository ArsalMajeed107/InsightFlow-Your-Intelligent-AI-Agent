package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DocumentItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val originalFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val pageCount: Long = 1L,
    val extractedText: String = "",
    val summary: String = "",
    val language: String = "English",
    val tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
