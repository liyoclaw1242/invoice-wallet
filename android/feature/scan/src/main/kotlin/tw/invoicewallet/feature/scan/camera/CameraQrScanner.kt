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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Live camera preview that scans for an e-invoice QR in real time and reports it via
 * [onInvoiceQr]. Bound to the composition's lifecycle; analysis runs off the main thread.
 * Ships with a centred viewfinder reticle — 4 corner brackets framing where both QRs
 * should land — so users know where to aim without us reading the camera image back.
 */
@Composable
fun CameraQrScanner(onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQr by rememberUpdatedState(onInvoiceQr)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

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
                                QrCodeAnalyzer { left, right -> currentOnQr(left, right) },
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
        ScanViewfinder(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Static alignment reticle: four corner brackets framing the centre region where a
 * Taiwan e-invoice's two side-by-side QRs comfortably fit. Brackets gently pulse in
 * opacity so the overlay reads as "alive" without distracting from the preview.
 */
@Composable
private fun ScanViewfinder(modifier: Modifier = Modifier) {
    // Dusty sage-teal (WalletTheme.colors.accentTeal hard-coded — this is a sibling of
    // CameraQrScanner inside :feature:scan, which deliberately doesn't depend on the
    // design-system module just for one colour).
    val tealStroke = Color(0xFF8FB5B2)

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
        // The reticle covers roughly the centre 84 % × 64 % of the camera area — wide
        // enough for the two QRs side-by-side plus the 1-D barcode above them.
        val frameW = size.width * 0.84f
        val frameH = size.height * 0.64f
        val left = (size.width - frameW) / 2f
        val top = (size.height - frameH) / 2f

        val cornerLen = minOf(frameW, frameH) * 0.18f
        val strokePx = 3.dp.toPx()

        // Soft pulse 0.55 ↔ 1.0 so the overlay reads as alive without strobing.
        val color = tealStroke.copy(alpha = alpha)

        listOf(
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
        ).forEach { (corner, arms) ->
            arms.forEach { arm ->
                drawLine(color = color, start = corner, end = arm, strokeWidth = strokePx, cap = StrokeCap.Round)
            }
        }
    }
}
