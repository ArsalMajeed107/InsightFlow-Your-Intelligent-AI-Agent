package com.example.data.model

data class SummaryResult(
    val title: String = "",
    val executiveSummary: String = "",
    val keyPoints: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String = "English",
    val estimatedReadTimeMinutes: Int = 2
)
