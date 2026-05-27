package tw.invoicewallet.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tw.invoicewallet.feature.invoicedetail.InvoiceDetailRoute
import tw.invoicewallet.feature.invoicelist.InvoiceListRoute
import tw.invoicewallet.feature.lottery.LotteryRoute
import tw.invoicewallet.feature.scan.ScanViewModel
import tw.invoicewallet.feature.scan.camera.CameraQrScanner
import tw.invoicewallet.feature.scan.ui.ScanScreen
import tw.invoicewallet.feature.settings.DefaultScanMode
import tw.invoicewallet.feature.settings.SettingsRoute

@Composable
fun InvoiceWalletApp(defaultScanMode: DefaultScanMode = DefaultScanMode.CAMERA) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            InvoiceListRoute(
                onScanClick = { navController.navigate(Routes.SCAN) },
                onInvoiceClick = { id -> navController.navigate(Routes.detail(id)) },
                onLotteryClick = { navController.navigate(Routes.LOTTERY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCAN) {
            ScanRoute(onBack = { navController.popBackStack() }, defaultScanMode = defaultScanMode)
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.INVOICE_ID) { type = NavType.StringType }),
        ) {
            InvoiceDetailRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.LOTTERY) {
            LotteryRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}

private object Routes {
    const val LIST = "list"
    const val SCAN = "scan"
    const val LOTTERY = "lottery"
    const val SETTINGS = "settings"
    const val INVOICE_ID = "invoiceId"
    const val DETAIL = "detail/{$INVOICE_ID}"
    fun detail(id: String) = "detail/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanRoute(onBack: () -> Unit, defaultScanMode: DefaultScanMode) {
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
        if (defaultScanMode == DefaultScanMode.GALLERY) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else if (!hasCameraPermission) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("掃描發票") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
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
            modifier = Modifier.padding(padding),
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
}
