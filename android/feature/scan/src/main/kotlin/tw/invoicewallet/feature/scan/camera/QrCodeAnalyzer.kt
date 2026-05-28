package tw.invoicewallet.feature.scan.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

/** One QR's 4 corner points in image-buffer pixel space (top-left origin, pre-rotation). */
data class QrPolygon(
    val topLeft: Pair<Int, Int>,
    val topRight: Pair<Int, Int>,
    val bottomRight: Pair<Int, Int>,
    val bottomLeft: Pair<Int, Int>,
)

/** A single analyzer frame's detection state — enough for the overlay to map points to
 *  display coordinates (size + rotation), plus 0..N detected QR polygons. */
data class DetectionFrame(
    val imageWidth: Int,
    val imageHeight: Int,
    /** Clockwise degrees to rotate the buffer for upright display (0 / 90 / 180 / 270). */
    val rotationDegrees: Int,
    val polygons: List<QrPolygon>,
)

/**
 * CameraX frame analyzer that decodes a Taiwan e-invoice's two QR codes live with
 * zxing-cpp (tryHarder / tryRotate / tryInvert + internal binarizers) — far more robust
 * on faint, dense, or skewed thermal-printed codes than the default. The two codes
 * rarely decode in the same frame, so we **accumulate across frames**: emit as soon as
 * both are captured, or the left alone after a short grace window. Runs on the caller's
 * single-thread analysis executor; reads are synchronous.
 *
 * [onFrame] (optional) fires for every analyzed frame and carries every QR's polygon
 * — used by the UI overlay to draw boxes that follow detected QRs in real time. The
 * decoding path (accumulator + [onInvoiceQr] emit) is independent.
 */
class QrCodeAnalyzer(
    private val onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit,
    private val onFrame: (DetectionFrame) -> Unit = {},
) : ImageAnalysis.Analyzer {

    private val reader = BarcodeReader().apply {
        options = BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
        )
    }

    private var left: String? = null
    private var right: ByteArray? = null
    private var framesSinceLeft = 0

    override fun analyze(imageProxy: ImageProxy) {
        // Grab frame metadata BEFORE the imageProxy.use {} closes it.
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        val results = try {
            imageProxy.use { reader.read(it) }
        } catch (_: Exception) {
            emptyList()
        }

        // Emit the per-frame detection envelope for the UI overlay — every QR seen,
        // not just the invoice halves. Cheap; only feeds the canvas.
        val polygons = results.mapNotNull { it.position?.toPolygon() }
        onFrame(DetectionFrame(imageWidth, imageHeight, rotation, polygons))

        results.forEach { result ->
            val bytes = result.bytes
            if (bytes != null && bytes.size >= 2 && bytes[0] == MARKER && bytes[1] == MARKER) {
                if (right == null) right = bytes
            } else {
                val value = result.text
                if (left == null && value != null && LEFT_CODE.containsMatchIn(value)) left = value
            }
        }
        val l = left
        if (l != null) framesSinceLeft++
        if (l != null && (right != null || framesSinceLeft >= GRACE_FRAMES)) {
            onInvoiceQr(l, right)
            // Continuous scan: clear the accumulator so the next physical receipt can be
            // captured. The ViewModel's 3-second dedup absorbs the re-emit of the SAME QR
            // that's still in frame after the user has already saved it.
            reset()
        }
    }

    private fun BarcodeReader.Position.toPolygon(): QrPolygon = QrPolygon(
        topLeft = topLeft.x to topLeft.y,
        topRight = topRight.x to topRight.y,
        bottomRight = bottomRight.x to bottomRight.y,
        bottomLeft = bottomLeft.x to bottomLeft.y,
    )

    private fun reset() {
        left = null
        right = null
        framesSinceLeft = 0
    }

    private companion object {
        const val MARKER: Byte = 0x2A // '*'

        // Frames to keep trying for the dense right code once the left is captured,
        // before emitting the left alone.
        const val GRACE_FRAMES = 12
        val LEFT_CODE = Regex("""^[A-Z]{2}\d{8}\d{7}""")
    }
}
