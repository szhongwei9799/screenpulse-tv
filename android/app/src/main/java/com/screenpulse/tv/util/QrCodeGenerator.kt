package com.screenpulse.tv.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR 码生成工具
 *
 * 使用 ZXing 库生成 QR 二维码
 * 用于在首屏显示管理面板的访问二维码
 */
object QrCodeGenerator {

    /**
     * 生成 QR 码 Bitmap
     *
     * @param content 要编码的内容（通常是 URL）
     * @param width 图片宽度（像素）
     * @param height 图片高度（像素）
     * @param margin 边距（像素），默认 2 个模块宽度
     * @return 生成的 Bitmap，失败返回 null
     */
    fun generate(
        content: String,
        width: Int = 400,
        height: Int = 400,
        margin: Int = 2
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to margin
            )

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            bitmap
        } catch (e: Exception) {
            // QR 码生成失败
            null
        }
    }

    /**
     * 生成带圆角的 QR 码
     *
     * @param content 要编码的内容
     * @param size 图片大小
     * @param cornerRadius 圆角半径（像素）
     * @return 带圆角的 Bitmap
     */
    fun generateWithRoundedCorners(
        content: String,
        size: Int = 400,
        cornerRadius: Float = 20f
    ): Bitmap? {
        val qrBitmap = generate(content, size, size) ?: return null

        // 创建带圆角的 Bitmap
        val output = Bitmap.createBitmap(qrBitmap.width, qrBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(qrBitmap, 0f, 0f, paint)

        return output
    }
}
