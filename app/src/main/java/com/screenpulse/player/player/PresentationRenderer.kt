package com.screenpulse.player.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.Log
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Renders PPTX presentation slides to Bitmap images.
 * PPTX files are ZIP archives containing XML slide definitions.
 * This parser extracts text content and renders each slide as an image.
 */
class PresentationRenderer {

    companion object {
        private const val TAG = "PresentationRenderer"
        private const val SLIDE_DURATION_MS = 8000L // 8 seconds per slide default
    }

    data class SlideResult(
        val bitmap: Bitmap,
        val durationMs: Long = SLIDE_DURATION_MS
    )

    data class SlideContent(
        val title: String = "",
        val paragraphs: List<String> = emptyList(),
        val bgColor: Int = Color.WHITE,
        val accentColor: Int = Color.parseColor("#1a73e8") // Blue accent
    )

    /**
     * Render all slides from a PPTX file to Bitmaps.
     * Returns empty list if the file is not a valid PPTX.
     */
    fun render(filePath: String, width: Int, height: Int): List<SlideResult> {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $filePath")
            return emptyList()
        }

        // Check if it's a PDF - we can't render those easily without PdfRenderer
        if (filePath.lowercase().endsWith(".pdf")) {
            Log.w(TAG, "PDF files are not directly supported. Convert to images first.")
            return emptyList()
        }

        return try {
            val zipFile = ZipFile(file)
            try {
                val slides = parseSlides(zipFile)
                if (slides.isEmpty()) {
                    Log.w(TAG, "No slides found in PPTX: $filePath")
                    emptyList()
                } else {
                    slides.map { content ->
                        SlideResult(
                            bitmap = renderSlideToBitmap(content, width, height),
                            durationMs = SLIDE_DURATION_MS
                        )
                    }
                }
            } finally {
                zipFile.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PPTX: $filePath", e)
            emptyList()
        }
    }

    /**
     * Parse all slides from the PPTX ZIP archive.
     */
    private fun parseSlides(zipFile: ZipFile): List<SlideContent> {
        val slides = mutableListOf<SlideContent>()

        // Find slide entries, sorted by number
        val slideEntries = zipFile.entries().toList()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml$")) }
            .sortedBy { entry ->
                val name = entry.name.substringAfterLast('/').substringAfter("slide").substringBefore('.')
                name.toIntOrNull() ?: Int.MAX_VALUE
            }

        if (slideEntries.isEmpty()) {
            // Maybe the structure is different - look for any slide files
            Log.w(TAG, "No standard slide entries found, searching recursively...")
            return slides
        }

        for (entry in slideEntries) {
            try {
                val xml = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                val content = parseSlideXml(xml)
                slides.add(content)
                Log.d(TAG, "Parsed slide: ${entry.name} -> title='${content.title.take(30)}', paragraphs=${content.paragraphs.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse slide: ${entry.name}", e)
                slides.add(SlideContent()) // Add empty slide
            }
        }

        return slides
    }

    /**
     * Parse slide XML to extract text content.
     * PPTX uses Office Open XML format.
     * Text is in <a:t> elements within <a:p> (paragraph) elements within <a:sp> (shape) elements.
     */
    private fun parseSlideXml(xml: String): SlideContent {
        val paragraphs = mutableListOf<String>()
        var title = ""

        // Extract all text runs, grouped by paragraphs
        // PPTX structure: <p:sp> -> <p:txBody> -> <a:p> -> <a:r> -> <a:t>
        // We use a simpler approach: just extract all <a:t> text

        // Split by paragraph elements <a:p>
        val pBlocks = splitByTag(xml, "a:p")

        for (pBlock in pBlocks) {
            val text = extractAllText(pBlock).trim()
            if (text.isNotEmpty()) {
                paragraphs.add(text)
            }
        }

        // First non-empty paragraph is typically the title
        if (paragraphs.isNotEmpty()) {
            title = paragraphs.first()
        }

        return SlideContent(
            title = title,
            paragraphs = paragraphs
        )
    }

    /**
     * Split XML content by occurrences of a tag.
     * Returns blocks of content between opening and closing of the specified tag.
     */
    private fun splitByTag(xml: String, tag: String): List<String> {
        val blocks = mutableListOf<String>()
        val openTag = "<$tag"
        val closeTag = "</$tag>"
        var idx = 0

        while (idx < xml.length) {
            val openIdx = xml.indexOf(openTag, idx)
            if (openIdx < 0) break

            // Find the actual '>' after the tag name (might have attributes)
            val tagEnd = xml.indexOf('>', openIdx)
            if (tagEnd < 0) break

            val closeIdx = xml.indexOf(closeTag, tagEnd)
            if (closeIdx < 0) break

            blocks.add(xml.substring(tagEnd + 1, closeIdx))
            idx = closeIdx + closeTag.length
        }

        return blocks
    }

    /**
     * Extract all text content from <a:t> elements within a block of XML.
     */
    private fun extractAllText(xml: String): String {
        val sb = StringBuilder()
        val openTag = "<a:t>"
        val closeTag = "</a:t>"
        var idx = 0

        while (idx < xml.length) {
            val openIdx = xml.indexOf(openTag, idx)
            if (openIdx < 0) break

            val textStart = openIdx + openTag.length
            val closeIdx = xml.indexOf(closeTag, textStart)
            if (closeIdx < 0) break

            sb.append(xml.substring(textStart, closeIdx))
            idx = closeIdx + closeTag.length
        }

        return sb.toString()
    }

    /**
     * Render a single slide's content to a Bitmap.
     */
    private fun renderSlideToBitmap(content: SlideContent, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(content.bgColor)

        // Title area (top portion with accent bar)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.05f).coerceAtLeast(32f)
            typeface = Typeface.DEFAULT_BOLD
            isShadowLayer = true
            shadowColor = Color.argb(50, 0, 0, 0)
            shadowDx = 2f
            shadowDy = 2f
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = (width * 0.035f).coerceAtLeast(22f)
            typeface = Typeface.DEFAULT
        }

        val accentBarPaint = Paint().apply {
            color = content.accentColor
        }

        val titleAreaHeight = height * 0.18f
        val padding = width * 0.08f

        // Draw title background bar
        if (content.title.isNotEmpty()) {
            canvas.drawRect(0f, 0f, width.toFloat(), titleAreaHeight, accentBarPaint)
            canvas.drawRect(0f, titleAreaHeight, width.toFloat(), titleAreaHeight + 4f, accentBarPaint)

            // Draw title text
            val titleX = padding
            val titleY = (titleAreaHeight + titlePaint.textSize) / 2f - titlePaint.descent()
            canvas.drawText(content.title, titleX, titleY, titlePaint)
        }

        // Draw body paragraphs
        if (content.paragraphs.size > 1) {
            val bodyStartY = titleAreaHeight + padding * 1.5f
            val lineSpacing = bodyPaint.textSize * 1.6f
            var currentY = bodyStartY

            // Skip first paragraph (it's the title)
            val bodyParagraphs = content.paragraphs.drop(1)

            for (para in bodyParagraphs) {
                // Word wrap the paragraph text
                val lines = wrapText(para, bodyPaint, width - padding * 2)

                for (line in lines) {
                    if (currentY + bodyPaint.textSize > height - padding) {
                        break // Don't draw below bottom padding
                    }
                    canvas.drawText(line, padding, currentY, bodyPaint)
                    currentY += lineSpacing
                }
                currentY += lineSpacing * 0.5f // Extra spacing between paragraphs
            }
        } else if (content.title.isNotEmpty() && content.paragraphs.size <= 1) {
            // Only title, center it on the slide
            val centeredPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#333333")
                textSize = (width * 0.06f).coerceAtLeast(36f)
                typeface = Typeface.DEFAULT_BOLD
            }

            // Clear the title bar since we're centering
            canvas.drawColor(content.bgColor)

            // Draw accent line above
            val lineWidth = width * 0.15f
            val lineY = height * 0.42f
            canvas.drawRect((width - lineWidth) / 2, lineY, (width + lineWidth) / 2, lineY + 4f, accentBarPaint)

            // Draw centered title
            val textWidth = centeredPaint.measureText(content.title)
            canvas.drawText(
                content.title,
                (width - textWidth) / 2,
                lineY + centeredPaint.textSize * 1.8f,
                centeredPaint
            )
        }

        // Draw slide number indicator at bottom
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#cccccc")
            textSize = 16f
        }

        return bitmap
    }

    /**
     * Wrap text to fit within a given width.
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = if (currentLine.isEmpty()) StringBuilder(word) else {
                    currentLine.append(" ").append(word)
                    currentLine
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf("") }
    }
}
