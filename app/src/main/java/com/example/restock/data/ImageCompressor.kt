package com.example.restock.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Reduz as fotografias antes do envio, para poupar dados, armazenamento e tempo. */
object ImageCompressor {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    val jpegMetadata: StorageMetadata
        get() = StorageMetadata.Builder().setContentType("image/jpeg").build()

    /** Mantém a proporção e nunca aumenta uma imagem mais pequena do que o limite. */
    fun targetSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Pair<Int, Int> {
        val largest = max(width, height)
        if (largest <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / largest
        return (width * scale).roundToInt() to (height * scale).roundToInt()
    }

    /** O ImageDecoder já aplica a orientação EXIF, por isso a foto não fica rodada. */
    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (width, height) = targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
