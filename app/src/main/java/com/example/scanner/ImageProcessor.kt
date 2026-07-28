package com.example.scanner

import android.graphics.*
import java.io.ByteArrayOutputStream

enum class ScanFilter {
    ORIGINAL,
    MAGIC_COLOR,
    AUTO,
    BLACK_AND_WHITE,
    GRAYSCALE,
    SHARPEN,
    HIGH_CONTRAST
}

object ImageProcessor {

    /**
     * Applies requested scan enhancement filter to a Bitmap
     */
    fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (filter) {
            ScanFilter.ORIGINAL -> return src

            ScanFilter.MAGIC_COLOR -> {
                // Boost saturation and light contrast for vivid document colors
                val cm = ColorMatrix()
                cm.setSaturation(1.35f)
                val scale = 1.15f
                val translate = -10f
                val colorScale = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(colorScale))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }

            ScanFilter.AUTO -> {
                // Balanced contrast boost
                val cm = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -15f,
                    0f, 1.2f, 0f, 0f, -15f,
                    0f, 0f, 1.2f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }

            ScanFilter.BLACK_AND_WHITE -> {
                // Crisp binary B&W for printed text
                val grayscale = applyFilter(src, ScanFilter.GRAYSCALE)
                val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)
                grayscale.getPixels(pixels, 0, width, 0, 0, width, height)

                val threshold = 128
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val red = Color.red(p)
                    pixels[i] = if (red > threshold) Color.WHITE else Color.BLACK
                }
                bwBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                return bwBitmap
            }

            ScanFilter.GRAYSCALE -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }

            ScanFilter.SHARPEN -> {
                // Sharpening kernel
                canvas.drawBitmap(src, 0f, 0f, paint)
                val cm = ColorMatrix(floatArrayOf(
                    1.3f, -0.1f, -0.1f, 0f, 0f,
                    -0.1f, 1.3f, -0.1f, 0f, 0f,
                    -0.1f, -0.1f, 1.3f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }

            ScanFilter.HIGH_CONTRAST -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.6f, 0f, 0f, 0f, -40f,
                    0f, 1.6f, 0f, 0f, -40f,
                    0f, 0f, 1.6f, 0f, -40f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
        }

        return output
    }

    /**
     * ID Card / Passport Dual-Side Scanning Mode
     * Fits front & back side images on a single A4 page canvas (width: 1240, height: 1754 at 150 DPI)
     */
    fun createIdCardA4Sheet(front: Bitmap, back: Bitmap, cardLabel: String = "ID CARD"): Bitmap {
        val a4Width = 1240
        val a4Height = 1754
        val result = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw white background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Title Header
        canvas.drawText(cardLabel, (a4Width / 2).toFloat(), 90f, paint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }

        // Draw Front Image Slot
        val cardWidth = 800
        val cardHeight = 500
        val left = (a4Width - cardWidth) / 2f
        val topFront = 180f

        val frontRect = RectF(left, topFront, left + cardWidth, topFront + cardHeight)
        val scaledFront = Bitmap.createScaledBitmap(front, cardWidth, cardHeight, true)
        canvas.drawBitmap(scaledFront, left, topFront, null)
        canvas.drawRect(frontRect, borderPaint)

        // Front Label
        paint.textSize = 24f
        paint.color = Color.DKGRAY
        canvas.drawText("FRONT SIDE", a4Width / 2f, topFront + cardHeight + 40f, paint)

        // Draw Back Image Slot
        val topBack = topFront + cardHeight + 120f
        val backRect = RectF(left, topBack, left + cardWidth, topBack + cardHeight)
        val scaledBack = Bitmap.createScaledBitmap(back, cardWidth, cardHeight, true)
        canvas.drawBitmap(scaledBack, left, topBack, null)
        canvas.drawRect(backRect, borderPaint)

        // Back Label
        canvas.drawText("BACK SIDE", a4Width / 2f, topBack + cardHeight + 40f, paint)

        // Footer note
        paint.textSize = 20f
        paint.color = Color.GRAY
        canvas.drawText("Scanned with DocScan: PDF & Cam Scanner", a4Width / 2f, a4Height - 50f, paint)

        return result
    }
}
