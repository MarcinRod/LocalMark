package pl.marrod.localmark.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.scale
import com.google.android.gms.maps.model.LatLng
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt


fun LatLng.toDegreesString(): String {
    val latDir = if (latitude >= 0) "N" else "S"
    val lngDir = if (longitude >= 0) "E" else "W"
    return "%.2f°%s, %.2f°%s".format(abs(latitude), latDir, abs(longitude), lngDir)
}

/**
 * Resizes the JPEG image at [file] so that neither dimension exceeds [maxDimension] pixels,
 * preserving the original aspect ratio. The result is written back to the **same file** at
 * the given [quality] (0–100 JPEG compression level).
 *
 * Must be called from a background thread / IO dispatcher — BitmapFactory and file I/O
 * are blocking operations.
 *
 * @param file         The image file to resize in-place.
 * @param maxDimension Maximum allowed width or height in pixels (default 1024).
 * @param quality      JPEG compression quality 0–100 (default 85).
 */
// android.media.ExifInterface is safe here — minSdk 26 exceeds the API 24 requirement.
fun resizeImageFile(
    file: File,
    maxDimension: Int = 1024,
    quality: Int = 85,
) {
    if (!file.exists()) return

    // Read EXIF orientation before any decoding — BitmapFactory strips it.
    val exif = ExifInterface(file.absolutePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val rotationDegrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else                                 -> 0f
    }

    // Decode just the dimensions first (no pixel allocation)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)

    val srcWidth = options.outWidth
    val srcHeight = options.outHeight
    if (srcWidth <= 0 || srcHeight <= 0) return

    // If the image already fits, nothing to do
    if (srcWidth <= maxDimension && srcHeight <= maxDimension) return

    // Calculate scale factor and target dimensions
    val scale = maxDimension.toFloat() / maxOf(srcWidth, srcHeight)
    val targetWidth = (srcWidth * scale).roundToInt()
    val targetHeight = (srcHeight * scale).roundToInt()

    // Decode a sub-sampled version to avoid OOM on large camera images
    val sampleSize = run {
        var sampling = 1
        while (srcWidth / (sampling * 2) >= targetWidth && srcHeight / (sampling * 2) >= targetHeight) sampling *= 2
        sampling
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return

    // Scale to exact target size and write back
    val resized = sampled.scale(targetWidth, targetHeight)

    // Re-apply orientation by rotating the pixels — the output has no EXIF, so
    // the pixels themselves must already be in the correct display orientation.
    val oriented = if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        Bitmap.createBitmap(resized, 0, 0, resized.width, resized.height, matrix, true)
            .also { if (it !== resized) resized.recycle() }
    } else {
        resized
    }

    file.outputStream().use { out ->
        oriented.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    if (oriented !== sampled) oriented.recycle()
    sampled.recycle()
}

