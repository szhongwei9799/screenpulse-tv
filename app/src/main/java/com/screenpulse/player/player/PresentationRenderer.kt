package com.screenpulse.player.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Renders PPTX presentation slides and PDF pages to Bitmap images.
 *
 * For PPTX: Extracts text content and embedded images from the ZIP archive.
 * For PDF: Uses Android's built-in PdfRenderer to render each page.
 */
class PresentationRenderer {

    companion object {
        private const val TAG = "PresentationRenderer"
        private const val SLIDE_DURATION_MS = 8000L
        private const val PDF_PAGE_DURATION_MS = 10000L
    }

    data class SlideResult(
        val bitmap: Bitmap,
        val durationMs: Long = SLIDE_DURATION_MS
    )

    /**
     * Render a file (PPTX or PDF) to a list of slide/page bitmaps.
     */
    fun render(filePath: String, width: Int, height: Int): List<SlideResult> {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $filePath")
            return emptyList()
        }

        return when {
            filePath.lowercase().endsWith(".pdf") -> renderPdf(filePath, width, height)
            filePath.lowercase().endsWith(".ppt") -> {
                Log.w(TAG, "Legacy .ppt format is not supported. Please convert to .pptx or PDF.")
                emptyList()
            }
            filePath.lowercase().endsWith(".pptx") -> renderPptx(filePath, width, height)
            else -> emptyList()
        }
    }

    // =====================================================================
    //  PDF Rendering (using Android PdfRenderer)
    // =====================================================================

    private fun renderPdf(filePath: String, width: Int, height: Int): List<SlideResult> {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "PDF not found: $filePath")
            return emptyList()
        }

        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val pageCount = renderer.pageCount
            Log.d(TAG, "PDF has $pageCount pages: $filePath")

            if (pageCount == 0) {
                renderer.close()
                fd.close()
                return emptyList()
            }

            val slides = mutableListOf<SlideResult>()
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // Calculate bitmap dimensions maintaining aspect ratio
                val pageWidth = page.width
                val pageHeight = page.height
                val scale = minOf(
                    width.toFloat() / pageWidth,
                    height.toFloat() / pageHeight
                )
                val bmpWidth = (pageWidth * scale).toInt()
                val bmpHeight = (pageHeight * scale).toInt()

                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                slides.add(SlideResult(bitmap, PDF_PAGE_DURATION_MS))
                Log.d(TAG, "Rendered PDF page $i: ${bmpWidth}x${bmpHeight}")
            }

            renderer.close()
            fd.close()
            slides
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF: $filePath", e)
            emptyList()
        }
    }

    // =====================================================================
    //  PPTX Rendering
    // =====================================================================

    data class SlideContent(
        val title: String = "",
        val paragraphs: List<String> = emptyList(),
        val images: List<Bitmap> = emptyList(),
        val bgColor: Int = Color.WHITE,
        val accentColor: Int = Color.parseColor("#1a73e8")
    )

    private fun renderPptx(filePath: String, width: Int, height: Int): List<SlideResult> {
        val file = File(filePath)
        return try {
            val zipFile = ZipFile(file)
            try {
                val slides = parseSlides(zipFile, width, height)
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
     * Parse all slides from the PPTX ZIP archive, extracting text and embedded images.
     */
    private fun parseSlides(zipFile: ZipFile, width: Int, height: Int): List<SlideContent> {
        val slides = mutableListOf<SlideContent>()

        // Find slide entries sorted by number
        val slideEntries = zipFile.entries().toList()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml$")) }
            .sortedBy { entry ->
                val name = entry.name.substringAfterLast('/').substringAfter("slide").substringBefore('.')
                name.toIntOrNull() ?: Int.MAX_VALUE
            }

        if (slideEntries.isEmpty()) {
            Log.w(TAG, "No standard slide entries found in PPTX")
            return slides
        }

        // Build a mapping of relationship IDs to image files
        val relsMap = buildRelsMap(zipFile)

        // Get the media files from the ZIP
        val mediaMap = mutableMapOf<String, Bitmap>()
        val mediaEntries = zipFile.entries().toList()
            .filter { it.name.startsWith("ppt/media/") && !it.isDirectory }
        for (entry in mediaEntries) {
            try {
                val bitmap = decodeMediaFromZip(zipFile, entry)
                if (bitmap != null) {
                    // Use filename (e.g., "image1.png") as key
                    val mediaName = entry.name.substringAfterLast('/')
                    mediaMap[mediaName] = bitmap
                    Log.d(TAG, "Loaded media: $mediaName (${bitmap.width}x${bitmap.height})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load media: ${entry.name}", e)
            }
        }

        for ((slideIdx, entry) in slideEntries.withIndex()) {
            try {
                val xml = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                val slideRels = relsMap["ppt/slides/_rels/slide${slideIdx + 1}.xml.rels"]
                    ?: relsMap["ppt/slides/_rels/slide${String.format("%02d", slideIdx + 1)}.xml.rels"]

                // Extract image references from the slide XML
                val slideImages = mutableListOf<Bitmap>()
                val imagePattern = Regex("<a:blip[^>]*r:embed=\"([^\"]+)\"")
                val embedMatches = imagePattern.findAll(xml)
                for (match in embedMatches) {
                    val rId = match.groupValues[1]
                    // Find the target in rels
                    val target = extractRelTarget(slideRels, rId)
                    if (target != null) {
                        val mediaName = target.substringAfterLast('/')
                        val bmp = mediaMap[mediaName]
                        if (bmp != null) {
                            // Scale bitmap to fit the slide dimensions
                            val scaled = scaleBitmap(bmp, width, height)
                            slideImages.add(scaled)
                        }
                    }
                }

                val content = parseSlideXml(xml)
                val finalContent = content.copy(images = slideImages)
                slides.add(finalContent)
                Log.d(TAG, "Parsed slide ${slideIdx + 1}: title='${content.title.take(30)}', images=${slideImages.size}, paragraphs=${content.paragraphs.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse slide: ${entry.name}", e)
                slides.add(SlideContent())
            }
        }

        // Clean up media bitmaps that weren't used
        for (bmp in mediaMap.values) {
            bmp.recycle()
        }

        return slides
    }

    /**
     * Build a mapping from rels file path to its content.
     */
    private fun buildRelsMap(zipFile: ZipFile): Map<String, String?> {
        val map = mutableMapOf<String, String?>()
        val relsEntries = zipFile.entries().toList()
            .filter { it.name.endsWith(".rels") }
        for (entry in relsEntries) {
            try {
                val content = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                map[entry.name] = content
            } catch (_: Exception) {}
        }
        return map
    }

    /**
     * Extract the Target attribute for a given relationship ID.
     */
    private fun extractRelTarget(relsContent: String?, rId: String): String? {
        if (relsContent == null) return null
        val pattern = Regex("<Relationship[^>]*Id=\"$rId\"[^>]*Target=\"([^\"]+)\"")
        return pattern.find(relsContent)?.groupValues?.getOrNull(1)
    }

    /**
     * Decode an image from a ZIP entry.
     */
    private fun decodeMediaFromZip(zipFile: ZipFile, entry: ZipEntry): Bitmap? {
        val ext = entry.name.substringAfterLast('.').lowercase()
        if (ext !in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "emf", "wmf")) {
            return null
        }
        // EMF/WMF are Windows metafiles, can't decode directly
        if (ext in listOf("emf", "wmf")) {
            return null
        }

        return zipFile.getInputStream(entry).use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        }
    }

    /**
     * Scale a bitmap to fit within max dimensions while maintaining aspect ratio.
     */
    private fun scaleBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / original.width,
            maxHeight.toFloat() / original.height
        )
        if (ratio >= 1f) return original // No scaling needed

        val width = (original.width * ratio).toInt()
        val height = (original.height * ratio).toInt()
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaled)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(original, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        return scaled
    }

    /**
     * Parse slide XML to extract text content.
     */
    private fun parseSlideXml(xml: String): SlideContent {
        val paragraphs = mutableListOf<String>()
        var title = ""

        val pBlocks = splitByTag(xml, "a:p")
        for (pBlock in pBlocks) {
            val text = extractAllText(pBlock).trim()
            if (text.isNotEmpty()) {
                paragraphs.add(text)
            }
        }
        if (paragraphs.isNotEmpty()) {
            title = paragraphs.first()
        }

        return SlideContent(title = title, paragraphs = paragraphs)
    }

    private fun splitByTag(xml: String, tag: String): List<String> {
        val blocks = mutableListOf<String>()
        val openTag = "<$tag"
        val closeTag = "</$tag>"
        var idx = 0
        while (idx < xml.length) {
            val openIdx = xml.indexOf(openTag, idx)
            if (openIdx < 0) break
            val tagEnd = xml.indexOf('>', openIdx)
            if (tagEnd < 0) break
            val closeIdx = xml.indexOf(closeTag, tagEnd)
            if (closeIdx < 0) break
            blocks.add(xml.substring(tagEnd + 1, closeIdx))
            idx = closeIdx + closeTag.length
        }
        return blocks
    }

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
        canvas.drawColor(content.bgColor)

        // If we have images, display them (prioritize images over text for PPTX)
        if (content.images.isNotEmpty()) {
            renderImages(canvas, content, width, height)
            return bitmap
        }

        // Text-only rendering
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.05f).coerceAtLeast(32f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = (width * 0.035f).coerceAtLeast(22f)
            typeface = Typeface.DEFAULT
        }

        val accentBarPaint = Paint().apply { color = content.accentColor }
        val titleAreaHeight = height * 0.18f
        val padding = width * 0.08f

        if (content.title.isNotEmpty()) {
            canvas.drawRect(0f, 0f, width.toFloat(), titleAreaHeight, accentBarPaint)
            canvas.drawRect(0f, titleAreaHeight, width.toFloat(), titleAreaHeight + 4f, accentBarPaint)
            val titleY = (titleAreaHeight + titlePaint.textSize) / 2f - titlePaint.descent()
            canvas.drawText(content.title, padding, titleY, titlePaint)
        }

        if (content.paragraphs.size > 1) {
            val bodyStartY = titleAreaHeight + padding * 1.5f
            val lineSpacing = bodyPaint.textSize * 1.6f
            var currentY = bodyStartY
            val bodyParagraphs = content.paragraphs.drop(1)

            for (para in bodyParagraphs) {
                val lines = wrapText(para, bodyPaint, width - padding * 2)
                for (line in lines) {
                    if (currentY + bodyPaint.textSize > height - padding) break
                    canvas.drawText(line, padding, currentY, bodyPaint)
                    currentY += lineSpacing
                }
                currentY += lineSpacing * 0.5f
            }
        } else if (content.title.isNotEmpty()) {
            val centeredPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#333333")
                textSize = (width * 0.06f).coerceAtLeast(36f)
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawColor(content.bgColor)
            val lineWidth = width * 0.15f
            val lineY = height * 0.42f
            canvas.drawRect((width - lineWidth) / 2, lineY, (width + lineWidth) / 2, lineY + 4f, accentBarPaint)
            val textWidth = centeredPaint.measureText(content.title)
            canvas.drawText(content.title, (width - textWidth) / 2, lineY + centeredPaint.textSize * 1.8f, centeredPaint)
        }

        return bitmap
    }

    /**
     * Render slide images to the bitmap.
     */
    private fun renderImages(canvas: Canvas, content: SlideContent, width: Int, height: Int) {
        val padding = width * 0.05f

        if (content.images.size == 1) {
            // Single image: center it on the slide
            val img = content.images[0]
            val left = (width - img.width) / 2f
            val top = (height - img.height) / 2f
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(img, left, top, paint)
        } else {
            // Multiple images: arrange in a grid
            val cols = if (content.images.size <= 2) 1 else 2
            val rows = (content.images.size + cols - 1) / cols
            val cellW = (width - padding * 2) / cols
            val cellH = (height - padding * 2) / rows

            for ((idx, img) in content.images.withIndex()) {
                val col = idx % cols
                val row = idx / cols
                val left = padding + col * cellW + (cellW - img.width) / 2f
                val top = padding + row * cellH + (cellH - img.height) / 2f
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(img, left, top, paint)
            }
        }

        // Draw slide title overlay at the bottom if we have text
        if (content.title.isNotEmpty()) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (width * 0.025f).coerceAtLeast(16f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val bgPaint = Paint().apply {
                color = Color.argb(150, 0, 0, 0)
            }

            val text = content.title
            val textWidth = overlayPaint.measureText(text)
            val barHeight = overlayPaint.textSize * 2f
            val barY = height - barHeight - 10f

            // Background bar
            canvas.drawRect(0f, barY, width.toFloat(), barY + barHeight, bgPaint)

            // Title text at bottom
            val textX = (width - textWidth) / 2f
            val textY = barY + overlayPaint.textSize * 1.2f
            canvas.drawText(text, textX, textY, overlayPaint)
        }
    }

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
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines.ifEmpty { listOf("") }
    }
}
