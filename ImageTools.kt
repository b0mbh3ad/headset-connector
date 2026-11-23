package com.headsetconnector.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64

object ImageTools {

    /** Сжимает картинку в квадрат 256×256 и возвращает base64 JPEG. */
    fun avatarBase64(context: Context, uri: Uri): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(
                decoded,
                (decoded.width - side) / 2,
                (decoded.height - side) / 2,
                side,
                side,
            )
            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== square) scaled.recycle()
            if (square !== decoded) square.recycle()
            decoded.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Сжимает картинку в баннер 800×300 (16:6) и возвращает base64 JPEG. */
    fun bannerBase64(context: Context, uri: Uri): String? {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val targetRatio = 800f / 300f
            val sourceRatio = decoded.width.toFloat() / decoded.height.toFloat()
            val cropWidth: Int
            val cropHeight: Int
            if (sourceRatio > targetRatio) {
                cropHeight = decoded.height
                cropWidth = (decoded.height * targetRatio).toInt().coerceAtLeast(1)
            } else {
                cropWidth = decoded.width
                cropHeight = (decoded.width / targetRatio).toInt().coerceAtLeast(1)
            }
            val crop = Bitmap.createBitmap(
                decoded,
                (decoded.width - cropWidth) / 2,
                (decoded.height - cropHeight) / 2,
                cropWidth,
                cropHeight,
            )
            val scaled = Bitmap.createScaledBitmap(crop, 800, 300, true)
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            if (scaled !== crop) scaled.recycle()
            if (crop !== decoded) crop.recycle()
            decoded.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Читает картинку, ужимая до maxDim по большей стороне (для обрезки). */
    fun decode(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val matrix = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** Квадратная обрезка с учётом поворота, зума и сдвига. Возвращает base64 JPEG 256×256. */
    fun cropSquareBase64(
        src: Bitmap,
        rotation: Int,
        zoom: Float,
        panX: Float,
        panY: Float,
        viewportPx: Float,
    ): String? = runCatching {
        val rotated = rotate(src, rotation)
        val minDim = minOf(rotated.width, rotated.height).toFloat()
        val scale = (viewportPx / minDim) * zoom
        val half = viewportPx / 2f
        val size = (viewportPx / scale).toInt().coerceAtLeast(1)
        val left = ((rotated.width / 2f) - (panX + half) / scale)
            .coerceIn(0f, (rotated.width - size).toFloat().coerceAtLeast(0f))
            .toInt()
        val top = ((rotated.height / 2f) - (panY + half) / scale)
            .coerceIn(0f, (rotated.height - size).toFloat().coerceAtLeast(0f))
            .toInt()
        val crop = Bitmap.createBitmap(rotated, left, top, size, size)
        val out = Bitmap.createScaledBitmap(crop, 256, 256, true)
        val stream = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
