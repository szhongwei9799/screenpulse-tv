package com.screenpulse.player.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Hashtable

/**
 * Generates QR code bitmaps using the ZXing library.
 * Used to display a scannable QR code on the screen that points to the
 * device's management web interface.
 */
object QRCodeGenerator {

    private const val TAG = "QRCodeGenerator"

    /**
     * Generates a QR code bitmap from the given text content.
     *
     * @param content The text or URL to encode in the QR code.
     * @param size The desired width and height of the output bitmap in pixels.
     * @return A [Bitmap] containing the QR code, or null if generation fails.
     */
    fun generate(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = Hashtable<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            Log.d(TAG, "Generated QR code: ${content.length} chars, ${width}x${height}px")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        }
    }

    /**
     * Generates a QR code with a logo overlay in the center.
     *
     * @param content The text or URL to encode.
     * @param logo A [Bitmap] to overlay in the center of the QR code.
     * @param size The desired size of the output bitmap.
     * @return A [Bitmap] with the QR code and logo overlay, or null on failure.
     */
    fun generateWithLogo(content: String, logo: Bitmap?, size: Int = 512): Bitmap? {
        val qrBitmap = generate(content, size) ?: return null
        if (logo == null) return qrBitmap

        return try {
            val result = qrBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(result)

            // Draw the logo with a white border in the center
            val logoSize = (size * 0.2).toInt()
            val borderSize = (size * 0.02).toInt()
            val centerX = (size - logoSize) / 2f
            val centerY = (size - logoSize) / 2f

            // White background for logo
            val borderPaint = android.graphics.Paint().apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRoundRect(
                centerX - borderSize,
                centerY - borderSize,
                centerX + logoSize + borderSize,
                centerY + logoSize + borderSize,
                8f,
                8f,
                borderPaint
            )

            // Draw logo
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
            canvas.drawBitmap(scaledLogo, centerX, centerY, null)

            scaledLogo.recycle()
            Log.d(TAG, "Generated QR code with logo overlay")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code with logo", e)
            qrBitmap
        }
    }
}
