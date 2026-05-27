package tw.invoicewallet.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import tw.invoicewallet.feature.scan.ScanViewModel
import tw.invoicewallet.feature.scan.camera.CameraQrScanner
import tw.invoicewallet.feature.scan.ui.ScanScreen

@Composable
fun InvoiceWalletApp() {
    InvoiceWalletScaffold { padding ->
        ScanRoute(modifier = Modifier.padding(padding))
    }
}

/** App chrome (title bar). Stateless so it can be rendered in tests without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceWalletScaffold(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Invoice Wallet") }) },
    ) { padding ->
        content(padding)
    }
}

@Composable
private fun ScanRoute(modifier: Modifier = Modifier) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    ScanScreen(
        state = state,
        onPickImage = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onParse = { viewModel.onQrDetected(it, rightBytes = null) },
        onConfirm = viewModel::onUserConfirm,
        onCancel = viewModel::onCancel,
        modifier = modifier,
        cameraContent = {
            if (hasCameraPermission) {
                CameraQrScanner(
                    onInvoiceQr = { left, right -> viewModel.onQrDetected(left, right) },
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
            } else {
                Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                    Text("授權相機以即時掃描")
                }
            }
        },
    )
}
