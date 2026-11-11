package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.model.DocumentItem

object ExportHelper {

    fun formatMarkdownSummary(doc: DocumentItem): String {
        return buildString {
            appendLine("# ${doc.title}")
            appendLine("**Original File:** ${doc.originalFileName} | **Pages:** ${doc.pageCount} | **Language:** ${doc.language}")
            if (doc.tags.isNotEmpty()) {
                appendLine("**Tags:** " + doc.tags.joinToString(", ") { "#$it" })
            }
            appendLine()
            appendLine("## Executive Summary")
            appendLine(doc.summary)
            appendLine()
            appendLine("---")
            appendLine("*Analyzed and synchronized via InsightFlow AI Intelligence*")
        }
    }

    fun shareSummary(context: Context, doc: DocumentItem) {
        val markdown = formatMarkdownSummary(doc)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, markdown)
            putExtra(Intent.EXTRA_SUBJECT, "InsightFlow Summary: ${doc.title}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Summary via...")
        context.startActivity(shareIntent)
    }

    fun copyToClipboard(context: Context, text: String, label: String = "InsightFlow Summary") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
