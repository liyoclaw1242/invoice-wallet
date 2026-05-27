package tw.invoicewallet.feature.scan.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/**
 * Helpers to make a hard, thermal-printed e-invoice photo decodable. The dense right
 * QR often has low contrast (grey modules), which default decoders can't binarize well
 * — the same reason desktop `zbar` only decoded these after grayscale + threshold. We
 * load at a useful resolution and produce a high-contrast black/white variant to feed
 * ML Kit when the raw image fails.
 */
object ImagePreprocess {

    /** Loads [uri] downsampled so the longest side is ~[maxDim] (caps memory; keeps detail). */
    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 2400): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Returns a black/white copy: grayscale by luminance, then Otsu global threshold. */
    fun binarize(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            lum[i] = l
            histogram[l]++
        }
        val threshold = otsuThreshold(histogram, pixels.size)
        for (i in pixels.indices) {
            pixels[i] = if (lum[i] > threshold) WHITE else BLACK
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0.0
        for (t in 0..255) sum += t * histogram[t]
        var sumB = 0.0
        var weightB = 0
        var maxVariance = 0.0
        var threshold = 127
        for (t in 0..255) {
            weightB += histogram[t]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += t * histogram[t]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (between > maxVariance) {
                maxVariance = between
                threshold = t
            }
        }
        return threshold
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}
