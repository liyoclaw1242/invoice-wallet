package tw.invoicewallet.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tw.invoicewallet.core.designsystem.components.IconCog
import tw.invoicewallet.core.designsystem.components.IconReceipt
import tw.invoicewallet.core.designsystem.components.IconTicket
import tw.invoicewallet.core.designsystem.components.WalletNavBar
import tw.invoicewallet.core.designsystem.components.WalletNavItem
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.feature.invoicedetail.InvoiceDetailRoute
import tw.invoicewallet.feature.invoicelist.InvoiceListRoute
import tw.invoicewallet.feature.lottery.LotteryRoute
import tw.invoicewallet.feature.pairing.PairingRoute
import tw.invoicewallet.feature.scan.ScanEvent
import tw.invoicewallet.feature.scan.ScanViewModel
import tw.invoicewallet.feature.scan.camera.CameraQrScanner
import tw.invoicewallet.feature.scan.ui.ScanScreen
import tw.invoicewallet.feature.settings.DefaultScanMode
import tw.invoicewallet.feature.settings.SettingsRoute

@Composable
fun InvoiceWalletApp(defaultScanMode: DefaultScanMode = DefaultScanMode.CAMERA) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // The bar shows only on the three top-level pages. Sub-pages (detail / pairing) and
    // full-screen camera flows hide it for focus + safe-area on small phones.
    val showBar = currentRoute in setOf(Routes.LIST, Routes.LOTTERY, Routes.SETTINGS)

    // MainActivity wraps in InvoiceWalletTheme (M3); this Scaffold reads WalletTheme
    // tokens for containerColor + the bottom bar, so we re-wrap here. Without it the
    // first read of WalletTheme.colors crashes — its CompositionLocal default lambda
    // throws by design when no theme is provided above.
    WalletTheme {
        androidx.compose.material3.Scaffold(
            containerColor = WalletTheme.colors.surfaceBase,
            bottomBar = {
                if (showBar) {
                    WalletNavBar(
                        items = NavTabs,
                        selectedRoute = currentRoute,
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.LIST,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.LIST) {
                    InvoiceListRoute(
                        onScanClick = { navController.navigate(Routes.SCAN) },
                        onInvoiceClick = { id -> navController.navigate(Routes.detail(id)) },
                        onLotteryClick = { navController.navigate(Routes.LOTTERY) },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.SCAN) {
                    ScanRoute(
                        onBack = { navController.popBackStack() },
                        onEditInvoice = { id -> navController.navigate(Routes.detail(id)) },
                        defaultScanMode = defaultScanMode,
                    )
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
                    SettingsRoute(
                        onBack = { navController.popBackStack() },
                        onPairRelayClick = { navController.navigate(Routes.PAIRING) },
                    )
                }
                composable(Routes.PAIRING) {
                    PairingRoute(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private val NavTabs = listOf(
    WalletNavItem(route = Routes.LIST, label = "發票", icon = IconReceipt, testTag = "nav-list"),
    WalletNavItem(route = Routes.LOTTERY, label = "對獎", icon = IconTicket, testTag = "nav-lottery"),
    WalletNavItem(route = Routes.SETTINGS, label = "設定", icon = IconCog, testTag = "nav-settings"),
)

private object Routes {
    const val LIST = "list"
    const val SCAN = "scan"
    const val LOTTERY = "lottery"
    const val SETTINGS = "settings"
    const val PAIRING = "pairing"
    const val INVOICE_ID = "invoiceId"
    const val DETAIL = "detail/{$INVOICE_ID}"
    fun detail(id: String) = "detail/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanRoute(onBack: () -> Unit, onEditInvoice: (String) -> Unit, defaultScanMode: DefaultScanMode) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val session by viewModel.session.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // A dedicated ToneGenerator gives a clearly audible "beep" — independent of the
    // device's "touch sounds" setting that the earlier SoundEffectConstants.CLICK relied
    // on. STREAM_NOTIFICATION still honours silent mode so it stays polite.
    val toneGen = remember {
        // volume is 0-100; 80 is clearly audible without being shrill.
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
    }
    DisposableEffect(toneGen) {
        onDispose { toneGen?.release() }
    }

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

    // Each scan emits a transient event → snackbar (3 s, action "編輯" jumps to detail).
    // Saved also plays the system click + a light haptic so a quick batch scan is felt as
    // well as seen; both honour system silent / vibrate settings. Lottery wins get a
    // richer banner that announces the prize alongside the merchant.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val (msg, action) = when (event) {
                is ScanEvent.Saved -> {
                    // Single short beep for a normal save; a brighter ACK2 (double beep)
                    // when the cached lottery match said the user just won.
                    val tone =
                        if (event.lotteryPrize != null) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
                    toneGen?.startTone(tone, 200)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val base = "已存 ${event.label}"
                    val msg = event.lotteryPrize?.let { "$base · 中獎 NT$%,d".format(it) } ?: base
                    msg to event.invoiceId
                }
                is ScanEvent.Duplicate -> "已存在 ${event.label}" to null
                is ScanEvent.Failed -> "無法辨識：${event.message}" to null
            }
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = action?.let { "編輯" },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && action != null) onEditInvoice(action)
        }
    }

    WalletTheme {
        Scaffold(
            containerColor = WalletTheme.colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (session.savedCount > 0) "已掃 ${session.savedCount} 張" else "掃描發票",
                            style = WalletTheme.typography.title,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text(
                                "完成",
                                style = WalletTheme.typography.pillLabel,
                                color = WalletTheme.colors.accentTealDeep,
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = WalletTheme.colors.surfaceBase,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            ScanScreen(
                state = state,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onConfirm = viewModel::onUserConfirm,
                onCancel = viewModel::onCancel,
                modifier = Modifier.padding(padding),
                savedCount = session.savedCount,
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
}
