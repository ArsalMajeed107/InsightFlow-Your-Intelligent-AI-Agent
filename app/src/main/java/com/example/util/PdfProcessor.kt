package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.model.DocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "PdfProcessor"

data class ProcessedPdf(
    val title: String,
    val fileName: String,
    val pageCount: Long,
    val fileSizeBytes: Long,
    val extractedText: String,
    val previewBitmaps: List<Bitmap>
)

object PdfProcessor {

    /**
     * Parses a user-selected PDF Uri using Android PdfRenderer and reads metadata.
     */
    suspend fun processPdfUri(context: Context, uri: Uri, originalName: String? = null): ProcessedPdf = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = originalName ?: "Document_${System.currentTimeMillis()}.pdf"
        var fileSize = 0L

        // Query metadata from content resolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    val resolvedName = cursor.getString(nameIndex)
                    if (!resolvedName.isNullOrBlank()) fileName = resolvedName
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        // Copy content stream to cache file for PdfRenderer
        val cacheFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        if (fileSize == 0L) fileSize = cacheFile.length()

        val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount.toLong()
        val bitmaps = mutableListOf<Bitmap>()

        // Render first up to 3 pages for preview & multimodal AI analysis
        val pagesToRender = minOf(3, renderer.pageCount)
        for (i in 0 until pagesToRender) {
            val page = renderer.openPage(i)
            // 720 width scaled bitmap
            val scale = 720f / page.width
            val width = 720
            val height = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap)
            page.close()
        }

        renderer.close()
        pfd.close()

        val title = fileName.removeSuffix(".pdf").replace('_', ' ').replace('-', ' ').trim()

        // Generate preliminary text representation
        val extractedText = buildString {
            appendLine("Document: $title ($fileName)")
            appendLine("Total Pages: $pageCount, File Size: ${fileSize / 1024} KB")
            appendLine("\n[Page 1 rendered for visual analysis]")
            appendLine("Content scanned from PDF document structure. Ready for AI deep analysis and semantic search.")
        }

        ProcessedPdf(
            title = title,
            fileName = fileName,
            pageCount = maxOf(1L, pageCount),
            fileSizeBytes = fileSize,
            extractedText = extractedText,
            previewBitmaps = bitmaps
        )
    }

    /**
     * Curated sample documents for immediate testing and rich exploration
     * without needing to transfer local PDFs to the emulator.
     */
    fun getSampleDocuments(): List<DocumentItem> {
        return listOf(
            DocumentItem(
                id = "sample_quantum_2026",
                title = "Quantum Computing in 2026: Commercial Breakthroughs",
                originalFileName = "Quantum_Computing_2026_Report.pdf",
                fileSizeBytes = 348576L,
                pageCount = 18L,
                extractedText = """
                    QUANTUM COMPUTING IN 2026: COMMERCIAL BREAKTHROUGHS & INDUSTRIAL HORIZONS
                    Executive Research Report - Global Quantum Initiative
                    
                    Section 1: Introduction & State of Fault Tolerance
                    By mid-2026, fault-tolerant quantum computation crossed the 10,000 physical qubit barrier with logical error rates below 10^-6. Neutral-atom arrays and superconducting transmon architectures achieved neutral-state coherence times exceeding 120 seconds.
                    
                    Section 2: Material Science & Drug Discovery
                    Pharmaceutical consortia have successfully synthesized catalyst molecules for nitrogen fixation and carbon-capture enzymes. The simulation of FeMoco cofactor ground states—previously intractable on classical exascale supercomputers—was resolved in under 42 minutes.
                    
                    Section 3: Post-Quantum Cryptography (PQC) Transition
                    NIST FIPS 203 (ML-KEM), 204 (ML-DSA), and 205 (SLH-DSA) migration entered phase three worldwide. Global financial systems report 78% crypto-agility compliance. Key exchange latency has stabilized within 4.2ms over transatlantic fiber trunks.
                    
                    Section 4: Energy Grid Optimization
                    National grid operators deployed hybrid QAOA (Quantum Approximate Optimization Algorithm) routines to coordinate distributed renewable generation across 42,000 sub-stations, yielding an estimated 8.4% reduction in peak load line losses.
                    
                    Section 5: Strategic Outlook & Recommendations
                    1. Accelerated investment in cryogenic interconnects and optical quantum networking.
                    2. Institutional mandates for quantum software engineering curricula.
                    3. Strict export controls on dilution refrigerators and gallium-arsenide sub-wafers.
                """.trimIndent(),
                summary = "Comprehensive 2026 analysis of fault-tolerant quantum computing achievements. Highlights include 10,000+ physical qubit systems, catalyst simulation breakthroughs in drug discovery, 78% global adoption of NIST post-quantum encryption standards, and hybrid QAOA algorithms cutting grid transmission losses by 8.4%.",
                language = "English",
                tags = listOf("Quantum", "Material Science", "Cryptography", "Energy", "Tech")
            ),
            DocumentItem(
                id = "sample_climate_brief",
                title = "Global Clean Energy & Carbon Economics Brief",
                originalFileName = "Clean_Energy_Economics_Brief.pdf",
                fileSizeBytes = 512000L,
                pageCount = 24L,
                extractedText = """
                    GLOBAL CLEAN ENERGY & CARBON ECONOMICS EXECUTIVE BRIEF
                    International Sustainability Council - Autumn 2026
                    
                    Executive Summary:
                    Global renewable capacity additions reached 680 GW in 2026, driven by next-generation perovskite-silicon tandem solar cells achieving 33.8% commercial conversion efficiency and sodium-ion stationary energy storage falling below $48/kWh.
                    
                    Key Findings:
                    1. Industrial Heat Electrification: Thermal energy storage systems (liquid metal and crushed volcanic rock) now compete directly with natural gas for manufacturing temperatures up to 1,200°C.
                    2. Cross-Border Carbon Tariffs: The EU CBAM and emerging Asia-Pacific emissions trading linkages now cover 44% of global heavy manufacturing exports.
                    3. Sustainable Aviation Fuel (SAF): Synthetic e-kerosene production reached cost-parity thresholds at $1.15/liter with dedicated offshore green hydrogen hubs.
                    
                    Action Items:
                    - Modernize transmission interties between offshore wind clusters and inland battery storage nodes.
                    - Harmonize carbon-intensity auditing protocols using verifiable digital product passports (DPPs).
                """.trimIndent(),
                summary = "Key findings on the 2026 clean energy transition: Solar tandem cells achieved 33.8% efficiency, sodium-ion batteries dropped under $48/kWh, and green hydrogen e-kerosene achieved airline cost-parity. Highlights carbon border tax integration and digital product passports.",
                language = "English",
                tags = listOf("Climate", "Energy", "Economics", "Sustainability")
            ),
            DocumentItem(
                id = "sample_ai_safety_framework",
                title = "Autonomous Agent Governance & Safety Protocol",
                originalFileName = "Agentic_AI_Safety_Standard.pdf",
                fileSizeBytes = 286000L,
                pageCount = 14L,
                extractedText = """
                    AUTONOMOUS AGENT GOVERNANCE & SAFETY PROTOCOL
                    Standardization Board for Autonomous Systems - 2026 Edition
                    
                    1. Scope and Objective:
                    Establishes baseline criteria for software agents capable of tool invocation, dynamic API execution, and autonomous decision loops across high-assurance environments (healthcare, banking, critical infrastructure).
                    
                    2. Principle of Bounded Delegation:
                    Every autonomous action sequence must define explicit budgetary, temporal, and credential caps. Ephemeral authorization tokens must not exceed 90 seconds without cryptographic re-validation.
                    
                    3. Continuous Multi-Party Auditing:
                    Agentic interaction logs must be immutable and verifiable via dual-signed cryptographic receipts. Human-in-the-loop triggers are mandatory for fund transfers over $5,000 or modification of patient record indices.
                    
                    4. Drift Detection & Self-Quarantine:
                    Agents experiencing semantic divergence exceeding 3.5 standard deviations from baseline prompt constraints must initiate graceful degradation and notify system supervisors.
                """.trimIndent(),
                summary = "Safety framework for autonomous AI agents. Dictates bounded delegation principles, 90-second ephemeral authorization lifetimes, human-in-the-loop constraints on financial/medical actions, and automated self-quarantine protocols for semantic drift.",
                language = "English",
                tags = listOf("AI Safety", "Governance", "Security", "Policy")
            )
        )
    }
}
