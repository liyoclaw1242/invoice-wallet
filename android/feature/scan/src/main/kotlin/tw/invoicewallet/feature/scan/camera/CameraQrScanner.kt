package tw.invoicewallet.feature.scan.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

private val TealStroke = Color(0xFF8FB5B2) // WalletTheme.colors.accentTeal — sage-teal

/**
 * Live camera preview that scans for an e-invoice QR in real time and reports it via
 * [onInvoiceQr]. Bound to the composition's lifecycle; analysis runs off the main thread.
 * Overlays a viewfinder: a faint static reticle as an "aim here" guide, plus a vivid
 * dynamic box drawn around any QR zxing-cpp is currently seeing.
 */
@Composable
fun CameraQrScanner(onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQr by rememberUpdatedState(onInvoiceQr)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    // Per-frame detection envelope from the analyzer thread → Compose state. StateFlow
    // gives a thread-safe single-writer, single-reader (collector) channel; writing
    // .value from the background thread is fine, collection lands on Main.
    val detections = remember { MutableStateFlow<DetectionFrame?>(null) }
    val frame by detections.collectAsState()

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        // Dense e-invoice QR codes need resolution: 640x480 (the default)
                        // often can't resolve the right code. Target 1920x1080, falling
                        // back to the nearest higher-then-lower option the device supports.
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1920, 1080),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                analysisExecutor,
                                QrCodeAnalyzer(
                                    onInvoiceQr = { left, right -> currentOnQr(left, right) },
                                    onFrame = { f -> detections.value = f },
                                ),
                            )
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(context))
                previewView
            },
        )
        ScanViewfinder(frame = frame, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Two-layer overlay:
 *  1. A faint static reticle (corner brackets) always shown — gives the user "aim here"
 *     guidance even before any QR is detected.
 *  2. A vivid polygon stroke per detected QR, mapped from image-buffer space to canvas
 *     space via rotation + FILL_CENTER scaling.
 */
@Composable
private fun ScanViewfinder(frame: DetectionFrame?, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "viewfinder-pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "viewfinder-alpha",
    )

    Canvas(modifier = modifier) {
        val hasDetection = frame?.polygons?.isNotEmpty() == true

        // Static "aim here" guide — dimmed when there's an active detection so the
        // vivid box can shine, full opacity otherwise.
        drawStaticReticle(
            alphaBase = if (hasDetection) 0.18f else alpha,
        )

        // Per-QR dynamic polygons.
        if (frame == null) return@Canvas
        val transform = computeTransform(frame, size.width, size.height)
        frame.polygons.forEach { poly ->
            drawDynamicBox(poly, frame.rotationDegrees, frame.imageWidth, frame.imageHeight, transform)
        }
    }
}

/** Computed once per frame: how the upright image lands inside the canvas (FILL_CENTER). */
private data class ViewTransform(val scale: Float, val offsetX: Float, val offsetY: Float)

private fun computeTransform(frame: DetectionFrame, canvasW: Float, canvasH: Float): ViewTransform {
    val (uprightW, uprightH) = if (frame.rotationDegrees == 90 || frame.rotationDegrees == 270) {
        frame.imageHeight.toFloat() to frame.imageWidth.toFloat()
    } else {
        frame.imageWidth.toFloat() to frame.imageHeight.toFloat()
    }
    // PreviewView's default ScaleType is FILL_CENTER → scale to cover, crop the excess.
    val scale = maxOf(canvasW / uprightW, canvasH / uprightH)
    val displayW = uprightW * scale
    val displayH = uprightH * scale
    return ViewTransform(
        scale = scale,
        offsetX = (canvasW - displayW) / 2f,
        offsetY = (canvasH - displayH) / 2f,
    )
}

/** Rotate image-space point (px, py) by [rotationDegrees] CW to get upright coordinates. */
private fun rotatePoint(px: Float, py: Float, imageW: Int, imageH: Int, rotationDegrees: Int): Pair<Float, Float> =
    when (rotationDegrees) {
        0 -> px to py
        90 -> (imageH - py) to px
        180 -> (imageW - px) to (imageH - py)
        270 -> py to (imageW - px)
        else -> px to py
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDynamicBox(
    poly: QrPolygon,
    rotation: Int,
    imageW: Int,
    imageH: Int,
    t: ViewTransform,
) {
    val pts = listOf(poly.topLeft, poly.topRight, poly.bottomRight, poly.bottomLeft)
        .map { (px, py) ->
            val (ux, uy) = rotatePoint(px.toFloat(), py.toFloat(), imageW, imageH, rotation)
            Offset(ux * t.scale + t.offsetX, uy * t.scale + t.offsetY)
        }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        lineTo(pts[1].x, pts[1].y)
        lineTo(pts[2].x, pts[2].y)
        lineTo(pts[3].x, pts[3].y)
        close()
    }
    val strokePx = 4.dp.toPx()
    drawPath(
        path = path,
        color = TealStroke,
        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStaticReticle(alphaBase: Float) {
    val frameW = size.width * 0.84f
    val frameH = size.height * 0.64f
    val left = (size.width - frameW) / 2f
    val top = (size.height - frameH) / 2f
    val cornerLen = minOf(frameW, frameH) * 0.18f
    val strokePx = 3.dp.toPx()
    val color = TealStroke.copy(alpha = alphaBase)

    val corners = listOf(
        // top-left
        Offset(left, top) to listOf(
            Offset(left + cornerLen, top),
            Offset(left, top + cornerLen),
        ),
        // top-right
        Offset(left + frameW, top) to listOf(
            Offset(left + frameW - cornerLen, top),
            Offset(left + frameW, top + cornerLen),
        ),
        // bottom-left
        Offset(left, top + frameH) to listOf(
            Offset(left + cornerLen, top + frameH),
            Offset(left, top + frameH - cornerLen),
        ),
        // bottom-right
        Offset(left + frameW, top + frameH) to listOf(
            Offset(left + frameW - cornerLen, top + frameH),
            Offset(left + frameW, top + frameH - cornerLen),
        ),
    )
    corners.forEach { (corner, arms) ->
        arms.forEach { arm ->
            drawLine(color = color, start = corner, end = arm, strokeWidth = strokePx, cap = StrokeCap.Round)
        }
    }
}
