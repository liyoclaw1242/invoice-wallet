package tw.invoicewallet.feature.scan.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Live camera preview that scans for an e-invoice QR in real time and reports it via
 * [onInvoiceQr]. Bound to the composition's lifecycle; analysis runs off the main thread.
 */
@Composable
fun CameraQrScanner(onInvoiceQr: (left: String, rightBytes: ByteArray?) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQr by rememberUpdatedState(onInvoiceQr)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    // Dense e-invoice QR codes need resolution: 640x480 (the default) often
                    // can't resolve the right code. Target 1920x1080, falling back to the
                    // nearest higher-then-lower option the device supports.
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
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                // Wire ML Kit auto-zoom to the camera now that we have CameraControl.
                val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                analysis.setAnalyzer(
                    analysisExecutor,
                    QrCodeAnalyzer(
                        maxZoomRatio = maxZoom,
                        applyZoom = { ratio -> camera.cameraControl.setZoomRatio(ratio) },
                        onInvoiceQr = { left, right -> currentOnQr(left, right) },
                    ),
                )
            }, ContextCompat.getMainExecutor(context))
            previewView
        },
    )
}
