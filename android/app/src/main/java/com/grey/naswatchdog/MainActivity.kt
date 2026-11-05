package com.grey.naswatchdog

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import com.grey.naswatchdog.charts.HistoryRange
import com.grey.naswatchdog.feature.*
import com.grey.naswatchdog.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

// ---------- formatting ----------

fun bytes(v: Long): String {
    if (v <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")
    var d = v.toDouble()
    var i = 0
    while (d >= 1024 && i < units.size - 1) { d /= 1024; i++ }
    return String.format(Locale.US, if (i == 0) "%.0f %s" else "%.1f %s", d, units[i])
}

fun rate(v: Double): String = bytes(v.toLong()) + "/s"
fun pct(v: Double, d: Int = 1): String = String.format(Locale.US, "%.${d}f%%", v)
fun num(v: Double, d: Int = 1): String = String.format(Locale.US, "%.${d}f", v)

fun duration(sec: Double): String {
    val s = sec.toLong()
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

// ---------- activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureAlertChannel(this)
        scheduleAlertWorker(this)
        setContent {
            WatchdogTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("naswatchdog", Context.MODE_PRIVATE) }
    var baseUrl by remember { mutableStateOf(prefs.getString("baseUrl", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var editing by remember { mutableStateOf(baseUrl.isBlank()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        syncAlertMonitoring(ctx)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify(ctx)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(editing, baseUrl, token) {
        if (!editing) syncAlertMonitoring(ctx)
    }

    if (editing) {
        SetupScreen(
            initialUrl = baseUrl,
            initialToken = token,
            onSave = { url, newToken ->
                baseUrl = url
                token = newToken
                prefs.edit().putString("baseUrl", url).putString("token", newToken).apply()
                editing = false
            },
            onCancel = if (baseUrl.isNotBlank()) ({ editing = false }) else null,
        )
    } else {
        Dashboard(
            baseUrl = baseUrl,
            token = token,
            prefs = prefs,
            onSettings = { editing = true },
            onConnectionChange = { url, newToken ->
                baseUrl = url
                token = newToken
                prefs.edit().putString("baseUrl", url).putString("token", newToken).apply()
            },
        )
    }
}

@Composable
fun SetupScreen(
    initialUrl: String,
    initialToken: String,
    onSave: (String, String) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var connecting by remember { mutableStateOf(false) }
    var revealToken by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (connecting) return
        scope.launch {
            connecting = true
            error = null
            try {
                val normalized = normalizeServerUrl(url)
                fetchStats(normalized, token.trim())
                onSave(normalized, token.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = connectionError(e)
            } finally {
                connecting = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            Surface(
                color = SurfaceHigh,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Line),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = "NAS Watchdog",
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("NAS Watchdog", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(8.dp))
            Text(
                "Live health, storage, and alerts from your server — wherever you are on your network.",
                fontSize = 15.sp, lineHeight = 22.sp, color = Dim,
            )
            Spacer(Modifier.height(30.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                label = { Text("Server address") },
                placeholder = { Text("http://nas.local:8090") },
                isError = error != null,
                enabled = !connecting,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; error = null },
                label = { Text("API token (optional)") },
                enabled = !connecting,
                singleLine = true,
                visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    TextButton(onClick = { revealToken = !revealToken }) {
                        Text(if (revealToken) "Hide" else "Show")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, fontSize = 13.sp, lineHeight = 18.sp, color = Red)
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = ::connect,
                enabled = url.isNotBlank() && !connecting,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Bg)
                    Spacer(Modifier.width(10.dp))
                    Text("Testing connection…")
                } else {
                    Text("Connect", fontWeight = FontWeight.SemiBold)
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

private data class NasActionRequest(val action: String, val target: String, val label: String)

@OptIn(ExperimentalSharedTransitionApi::class)
private val InstrumentBounds = BoundsTransform { _, _ ->
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
}

private object AppRoute {
    const val OVERVIEW = "overview"
    const val SYSTEM = "system"
    const val STORAGE = "storage"
    const val CONTAINERS = "containers"
    const val MORE = "more"
    const val CPU = "metric/cpu"
    const val THERMAL = "metric/thermal"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun Dashboard(
    baseUrl: String,
    token: String,
    prefs: android.content.SharedPreferences,
    onSettings: () -> Unit,
    onConnectionChange: (String, String) -> Unit,
) {
    val telemetryViewModel: TelemetryViewModel = viewModel()
    val telemetry by telemetryViewModel.state.collectAsStateWithLifecycle()
    var refreshMs by remember { mutableLongStateOf(prefs.getLong("refresh_ms", 10_000L)) }
    var historyRangeName by rememberSaveable { mutableStateOf(HistoryRange.ONE_HOUR.name) }
    val historyRange = HistoryRange.valueOf(historyRangeName)
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<NasActionRequest?>(null) }
    var runningAction by remember { mutableStateOf<String?>(null) }
    val requestAction: (String, String, String) -> Unit = { action, target, label ->
        val capabilities = telemetry.snapshot?.stats
        when {
            telemetry.feedStatus != FeedStatus.LIVE || telemetry.snapshot?.cached == true -> {
                scope.launch { snack.showSnackbar("Controls unavailable while telemetry is not live. Reconnect first.") }
            }
            capabilities?.actionsEnabled != true || (action == "reboot" && !capabilities.rebootEnabled) -> {
                scope.launch { snack.showSnackbar("This action is disabled by the watchdog.") }
            }
            runningAction != null -> {
                scope.launch { snack.showSnackbar("$runningAction is already in progress. Wait for the watchdog response.") }
            }
            pendingAction != null -> Unit
            else -> pendingAction = NasActionRequest(action, target, label)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: AppRoute.OVERVIEW
    val primary = listOf(AppRoute.OVERVIEW, AppRoute.SYSTEM, AppRoute.STORAGE, AppRoute.CONTAINERS, AppRoute.MORE)
    val titles = mapOf(
        AppRoute.OVERVIEW to "Overview", AppRoute.SYSTEM to "System", AppRoute.STORAGE to "Storage",
        AppRoute.CONTAINERS to "Containers", AppRoute.MORE to "More", "alerts" to "Alerts",
        "network" to "Network", "logs" to "Logs", "actions" to "Actions", "settings" to "Settings",
        AppRoute.CPU to "CPU instrument", AppRoute.THERMAL to "Thermal instrument",
    )

    LaunchedEffect(baseUrl, token, refreshMs) { telemetryViewModel.configure(baseUrl, token, refreshMs) }
    DisposableEffect(lifecycleOwner) {
        telemetryViewModel.setForegroundActive(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> telemetryViewModel.setForegroundActive(true)
                Lifecycle.Event.ON_PAUSE -> telemetryViewModel.setForegroundActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            telemetryViewModel.setForegroundActive(false)
        }
    }

    pendingAction?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(24.dp),
            title = { Text(request.label) },
            text = {
                Text(
                    buildString {
                        if (request.target.isNotBlank()) append("Target: ${request.target}\n\n")
                        append("The watchdog will receive this request once. Check the live telemetry for its outcome.")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        runningAction = request.label
                        scope.launch {
                            try {
                                val result = postAction(baseUrl, token, request.action, request.target)
                                snack.showSnackbar(
                                    result.message.ifBlank {
                                        if (result.ok) "${request.label} accepted by the watchdog"
                                        else "${request.label} was rejected by the watchdog"
                                    }
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                snack.showSnackbar(
                                    "${request.label}: outcome could not be confirmed. ${connectionError(e)} Check the NAS before retrying."
                                )
                            } finally {
                                runningAction = null
                            }
                        }
                    },
                    colors = if (request.action == "reboot")
                        ButtonDefaults.buttonColors(containerColor = Red, contentColor = Bg)
                    else ButtonDefaults.buttonColors(),
                ) { Text(request.label) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(titles[currentRoute] ?: "NAS Watchdog", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).background(
                                    when (telemetry.feedStatus) {
                                        FeedStatus.LIVE -> Green
                                        FeedStatus.FAILED -> Red
                                        else -> Amber
                                    },
                                    CircleShape,
                                )
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                telemetry.snapshot?.let {
                                    "${it.stats.hostname} · ${formatClock(it.receivedAt)}" +
                                        if (it.cached) " · cached" else ""
                                } ?: (telemetry.error ?: "Connecting to watchdog…"),
                                fontSize = 12.sp,
                                color = if (telemetry.feedStatus == FeedStatus.FAILED) Red else Dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (currentRoute !in primary && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextMain)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = telemetryViewModel::refresh, enabled = !telemetry.refreshing) {
                        if (telemetry.refreshing) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Green)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Dim)
                        }
                    }
                    IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Dim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
        bottomBar = {
            Column {
                if (runningAction != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Green, trackColor = Line)
                }
                if (currentRoute != AppRoute.CPU && currentRoute != AppRoute.THERMAL) {
                    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
                    val items = listOf(
                        Triple(AppRoute.OVERVIEW, "Overview", Icons.Default.Home),
                        Triple(AppRoute.SYSTEM, "System", Icons.Default.Build),
                        Triple(AppRoute.STORAGE, "Storage", Icons.AutoMirrored.Filled.List),
                        Triple(AppRoute.CONTAINERS, "Containers", Icons.Default.Menu),
                        Triple(AppRoute.MORE, "More", Icons.Default.MoreVert),
                    )
                    items.forEach { (id, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == id || (id == AppRoute.MORE && currentRoute !in primary),
                            onClick = {
                                if (currentRoute != id) {
                                    navController.navigate(id) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(AppRoute.OVERVIEW) { saveState = true }
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Green,
                                selectedTextColor = TextMain,
                                unselectedIconColor = Dim,
                                unselectedTextColor = Dim,
                                indicatorColor = Green.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
                }
            }
        },
    ) { pad ->
        SharedTransitionLayout(Modifier.padding(pad).fillMaxSize()) {
            fun navigateTo(route: String) {
                navController.navigate(route) { launchSingleTop = true }
            }
            NavHost(
                navController = navController,
                startDestination = AppRoute.OVERVIEW,
                enterTransition = { fadeIn(animationSpec = tween(140)) },
                exitTransition = { fadeOut(animationSpec = tween(110)) },
                popEnterTransition = { fadeIn(animationSpec = tween(140)) },
                popExitTransition = { fadeOut(animationSpec = tween(110)) },
            ) {
                composable(AppRoute.OVERVIEW) {
                    LaunchedEffect(Unit) { telemetryViewModel.showHistory(HistoryRange.ONE_HOUR) }
                    WatchdogOverview(
                        state = telemetry,
                        onOpenCpu = { navigateTo(AppRoute.CPU) },
                        onRetry = telemetryViewModel::refresh,
                        onNavigate = ::navigateTo,
                        listState = rememberLazyListState(),
                        modifier = with(this@SharedTransitionLayout) {
                            Modifier.sharedElement(
                                rememberSharedContentState("cpu-plate"),
                                this@composable,
                                boundsTransform = InstrumentBounds,
                            )
                        },
                        cpuValueModifier = with(this@SharedTransitionLayout) {
                            Modifier.sharedBounds(
                                rememberSharedContentState("cpu-value"),
                                this@composable,
                                boundsTransform = InstrumentBounds,
                            )
                        },
                    )
                }
                composable(AppRoute.SYSTEM) {
                    LaunchedEffect(Unit) { telemetryViewModel.showHistory(HistoryRange.ONE_HOUR) }
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        SystemOverviewScreen(it, telemetry, { navigateTo(AppRoute.CPU) })
                    }
                }
                composable(AppRoute.STORAGE) {
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        StorageOverviewScreen(it, telemetry, requestAction)
                    }
                }
                composable(AppRoute.CONTAINERS) {
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        ContainersOverviewScreen(it, baseUrl, token, requestAction)
                    }
                }
                composable("alerts") {
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) { AlertsOverviewScreen(it) }
                }
                composable("network") {
                    LaunchedEffect(Unit) { telemetryViewModel.showHistory(HistoryRange.ONE_HOUR) }
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        NetworkOverviewScreen(it, telemetry)
                    }
                }
                composable("logs") {
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        LogsOverviewScreen(it, baseUrl, token)
                    }
                }
                composable("actions") {
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) {
                        ActionsOverviewScreen(it, requestAction)
                    }
                }
                composable("settings") {
                    SettingsOverviewScreen(
                        telemetry.snapshot?.stats, baseUrl, token, prefs,
                        onSave = { newUrl, newToken, ms ->
                            val normalized = try {
                                normalizeServerUrl(newUrl)
                            } catch (e: IllegalArgumentException) {
                                scope.launch { snack.showSnackbar(connectionError(e)) }
                                return@SettingsOverviewScreen
                            }
                            prefs.edit().putLong("refresh_ms", ms).apply()
                            refreshMs = ms
                            onConnectionChange(normalized, newToken)
                            scope.launch { snack.showSnackbar("Settings saved") }
                        },
                        onEditConnection = onSettings,
                    )
                }
                composable(AppRoute.MORE) {
                    MoreOverviewScreen(
                        onNavigate = ::navigateTo,
                        alertCount = telemetry.snapshot?.stats?.alerts?.size ?: 0,
                    )
                }
                composable(AppRoute.THERMAL) {
                    LaunchedEffect(Unit) { telemetryViewModel.showHistory(historyRange) }
                    InstrumentTelemetryScreen(telemetry, telemetryViewModel::refresh) { stats ->
                        ThermalDetailScreen(
                            stats = stats,
                            telemetry = telemetry,
                            range = historyRange,
                            onRangeChange = {
                                historyRangeName = it.name
                                telemetryViewModel.showHistory(it)
                            },
                            baseUrl = baseUrl,
                            token = token,
                        )
                    }
                }
                composable(AppRoute.CPU) {
                    LaunchedEffect(Unit) { telemetryViewModel.showHistory(historyRange) }
                    telemetry.snapshot?.stats?.let { stats ->
                        CpuDetailScreen(
                            stats = stats,
                            telemetry = telemetry,
                            range = historyRange,
                            onRangeChange = {
                                historyRangeName = it.name
                                telemetryViewModel.showHistory(it)
                            },
                            modifier = with(this@SharedTransitionLayout) {
                                Modifier.sharedElement(
                                rememberSharedContentState("cpu-plate"),
                                this@composable,
                                boundsTransform = InstrumentBounds,
                            )
                            },
                            cpuValueModifier = with(this@SharedTransitionLayout) {
                                Modifier.sharedBounds(
                                rememberSharedContentState("cpu-value"),
                                this@composable,
                                boundsTransform = InstrumentBounds,
                            )
                            },
                        )
                    } ?: ConnectionState(telemetry.error, telemetry.refreshing, telemetryViewModel::refresh)
                }
            }
        }
    }
}

@Composable
private fun InstrumentTelemetryScreen(
    telemetry: TelemetryUiState,
    onRetry: () -> Unit,
    content: @Composable (Stats) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val stats = telemetry.snapshot?.stats
        if (stats == null) {
            ConnectionState(telemetry.error, telemetry.refreshing, onRetry)
        } else {
            if (telemetry.feedStatus != FeedStatus.LIVE) {
                Box(Modifier.padding(16.dp)) {
                    ConnectionBanner(
                        telemetry.error ?: "Showing the last known telemetry.",
                        formatClock(telemetry.snapshot.receivedAt),
                        onRetry,
                    )
                }
            }
            Box(Modifier.weight(1f)) { content(stats) }
        }
    }
}

// ---------- shared components ----------

@Composable
fun ConnectionState(error: String?, loading: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).background(
                (if (error == null) Blue else Red).copy(alpha = 0.12f), CircleShape
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (error == null) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Blue)
            else Icon(Icons.Default.Warning, contentDescription = null, tint = Red)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (error == null) "Finding your NAS" else "Couldn't reach your NAS",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "Loading the latest telemetry…",
            fontSize = 13.sp, lineHeight = 19.sp, color = Dim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (error != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRetry, enabled = !loading) { Text("Try again") }
        }
    }
}

@Composable
fun ConnectionBanner(error: String, updated: String, onRetry: () -> Unit) {
    Surface(color = Red.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Reconnecting automatically", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Red)
                Text(
                    "$error Showing data from $updated.", fontSize = 11.sp, lineHeight = 16.sp,
                    color = TextMain.copy(alpha = 0.78f),
                )
            }
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

