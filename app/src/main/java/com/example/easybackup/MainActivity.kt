package com.example.easybackup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.text.format.Formatter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS_APP = "easybackup_app"
private const val KEY_LAST_DEST_URI = "last_dest_uri"
private const val KEY_LAST_RUN_KIND = "last_run_kind"
private const val KEY_LAST_RUN_COUNT = "last_run_count"
private const val KEY_LAST_RUN_TIME = "last_run_time"

class MainActivity : ComponentActivity() {

    private lateinit var engine: CopyEngine
    private lateinit var billing: Billing

    private var pendingKind: MediaKind = MediaKind.PHOTO
    private var onFolderPicked: ((Uri) -> Unit)? = null
    private var onPermsGranted: (() -> Unit)? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onFolderPicked?.invoke(uri)
        }
    }

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onPermsGranted?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.statusBarColor = 0xFFF3F5F8.toInt()
        window.navigationBarColor = 0xFFF3F5F8.toInt()
        engine = CopyEngine(applicationContext)
        billing = Billing(applicationContext)
        billing.start()
        AppAnalytics.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = Theme.base,
                    surface = Theme.card,
                    onBackground = Theme.textPrimary,
                    onSurface = Theme.textPrimary,
                    primary = Theme.action,
                    onPrimary = Theme.onAction,
                    secondary = Theme.accentBlue,
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Theme.base) {
                    AppRoot(
                        engine = engine,
                        billing = billing,
                        onRequestPermissionsAndPickFolder = { kind, onPicked ->
                            pendingKind = kind
                            onFolderPicked = onPicked
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            onPermsGranted = { launchPicker(rememberedDestination(applicationContext)) }
                            requestPerms.launch(perms)
                        },
                        onLaunchPurchase = { billing.launchPurchase(this) },
                    )
                }
            }
        }
    }

    private fun launchPicker(initialUri: Uri?) {
        // Best-effort hint at where to open — mirrors the iOS app's remembered
        // destination trick. Not guaranteed to work on every OEM's file picker,
        // same honest caveat as the iOS version's directoryURL approach.
        pickFolder.launch(initialUri)
    }
}

/** Persisted "remembered destination" — Android's SAF tree URI stays valid across
 * launches once persistable permission is taken, so (unlike iOS) no bookmark
 * resolution step is needed — just the URI string itself. */
private fun rememberedDestination(context: android.content.Context): Uri? {
    val s = context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
        .getString(KEY_LAST_DEST_URI, null) ?: return null
    return try { Uri.parse(s) } catch (_: Exception) { null }
}

private fun saveRememberedDestination(context: android.content.Context, uri: Uri) {
    context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_DEST_URI, uri.toString()).apply()
}

private data class LastRun(val kindLabel: String, val count: Int, val whenMillis: Long)

private fun loadLastRun(context: android.content.Context): LastRun? {
    val p = context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
    val kind = p.getString(KEY_LAST_RUN_KIND, null) ?: return null
    val count = p.getInt(KEY_LAST_RUN_COUNT, 0)
    val time = p.getLong(KEY_LAST_RUN_TIME, 0L)
    if (count <= 0) return null
    return LastRun(kind, count, time)
}

private fun saveLastRun(context: android.content.Context, kindLabel: String, count: Int) {
    context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_RUN_KIND, kindLabel)
        .putInt(KEY_LAST_RUN_COUNT, count)
        .putLong(KEY_LAST_RUN_TIME, System.currentTimeMillis())
        .apply()
}

private enum class Screen { HOME, COPY, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(
    engine: CopyEngine,
    billing: Billing,
    onRequestPermissionsAndPickFolder: (MediaKind, (Uri) -> Unit) -> Unit,
    onLaunchPurchase: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var pendingKind by remember { mutableStateOf(MediaKind.PHOTO) }
    var showingStorageIntro by remember { mutableStateOf(false) }
    var showingPaywall by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showingAccountId by remember { mutableStateOf(false) }

    var folderUnits by remember { mutableStateOf<List<AlbumUnit>>(emptyList()) }
    var folderStates by remember { mutableStateOf<List<FolderState>>(emptyList()) }
    var activeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var destUri by remember { mutableStateOf<Uri?>(null) }

    var usageRefreshId by remember { mutableStateOf(0) }

    var photoCount by remember { mutableStateOf(0) }
    var videoCount by remember { mutableStateOf(0) }
    var storageUsedFraction by remember { mutableStateOf(0f) }
    var storageUsedBytes by remember { mutableStateOf(0L) }
    var storageFreeBytes by remember { mutableStateOf(0L) }
    var lastRun by remember { mutableStateOf<LastRun?>(null) }

    val isUnlocked by billing.isUnlocked.collectAsState()
    val productDetails by billing.productDetails.collectAsState()
    val purchaseError by billing.purchaseError.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                billing.refreshEntitlement()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshHomeData() {
        val stats = deviceStorageStats()
        storageUsedFraction = stats.usedFraction
        storageUsedBytes = stats.usedBytes
        storageFreeBytes = stats.freeBytes
        lastRun = loadLastRun(context)
        scope.launch {
            photoCount = quickCount(context, MediaKind.PHOTO)
            videoCount = quickCount(context, MediaKind.VIDEO)
        }
    }

    LaunchedEffect(Unit) { refreshHomeData() }

    fun openFolderList(kind: MediaKind, uri: Uri) {
        scope.launch {
            saveRememberedDestination(context, uri)
            val units = engine.discoverFolders(kind)
            if (units.isEmpty()) {
                Haptics.warning(haptics)
                alertMessage = "No matching media was found in your library."
                return@launch
            }
            destUri = uri
            folderUnits = units
            folderStates = units.map { FolderState(it.bucketId, it.name, it.count) }
            activeIds = emptySet()
            pendingKind = kind
            AppAnalytics.backupDestinationPicked(kind = if (kind == MediaKind.PHOTO) "photos" else "videos")
            screen = Screen.COPY
        }
    }

    fun startFolder(id: String) {
        val unit = folderUnits.firstOrNull { it.bucketId == id } ?: return
        val existing = folderStates.firstOrNull { it.bucketId == id } ?: return
        if (!(existing.isWaiting || existing.quotaBlocked)) return
        if (activeIds.contains(id)) return
        val root = destUri ?: return

        if (!isUnlocked && !AllowList.currentUserIsFree(context) && !UsageTracker.hasFreeQuotaRemaining(context)) {
            showingPaywall = true
            return
        }

        activeIds = activeIds + id
        val kindLabel = if (pendingKind == MediaKind.PHOTO) "photos" else "videos"
        AppAnalytics.backupFolderStarted(kind = kindLabel, itemCount = unit.count)
        scope.launch {
            engine.runFolder(
                kind = pendingKind,
                unit = unit,
                treeUri = root,
                existing = existing,
                hasQuota = { isUnlocked || AllowList.currentUserIsFree(context) || UsageTracker.hasFreeQuotaRemaining(context) },
                onBytesCopied = { bytes -> UsageTracker.addBytes(context, bytes) },
            ) { progress ->
                folderStates = folderStates.map { if (it.bucketId == id) progress else it }
                if (progress.done) {
                    activeIds = activeIds - id
                    AppAnalytics.backupFolderCompleted(kind = kindLabel, copied = progress.copied)
                } else if (progress.quotaBlocked) {
                    activeIds = activeIds - id
                    AppAnalytics.backupQuotaBlocked(kind = kindLabel)
                    Haptics.warning(haptics)
                    showingPaywall = true
                }
            }
        }
    }

    fun finishCopyScreen() {
        engine.cancel()
        val totalCopied = folderStates.sumOf { it.copied }
        if (totalCopied > 0) {
            saveLastRun(context, if (pendingKind == MediaKind.PHOTO) "photos" else "videos", totalCopied)
        }
        destUri = null
        screen = Screen.HOME
        refreshHomeData()
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.HOME -> {
                LaunchedEffect(Unit) { AppAnalytics.screen("home") }
                HomeScreen(
                photoCount = photoCount,
                videoCount = videoCount,
                storageUsedFraction = storageUsedFraction,
                storageUsedBytes = storageUsedBytes,
                storageFreeBytes = storageFreeBytes,
                lastRun = lastRun,
                isUnlocked = isUnlocked || AllowList.currentUserIsFree(context),
                usageFraction = UsageTracker.usedFraction(context),
                usageText = UsageTracker.formattedUsage(context),
                usageRefreshId = usageRefreshId,
                onCopyPhotos = {
                    pendingKind = MediaKind.PHOTO
                    AppAnalytics.backupFlowStarted(kind = "photos")
                    showingStorageIntro = true
                },
                onCopyVideos = {
                    pendingKind = MediaKind.VIDEO
                    AppAnalytics.backupFlowStarted(kind = "videos")
                    showingStorageIntro = true
                },
                onFreeCardTap = { showingPaywall = true },
                onTitleLongPress = { showingAccountId = true },
                onOpenSettings = { screen = Screen.SETTINGS },
            )
            }
            Screen.COPY -> {
                LaunchedEffect(Unit) { AppAnalytics.screen("copy") }
                CopyScreen(
                pendingKind = pendingKind,
                folderStates = folderStates,
                activeIds = activeIds,
                onStartSelected = { ids -> ids.forEach { startFolder(it) } },
                onFinish = ::finishCopyScreen,
            )
            }
            Screen.SETTINGS -> {
                LaunchedEffect(Unit) {
                    AppAnalytics.screen("settings")
                    AppAnalytics.settingsOpened()
                }
                SettingsScreen(
                isUnlocked = isUnlocked || AllowList.currentUserIsFree(context),
                onBack = { screen = Screen.HOME },
                onRateApp = {
                    AppAnalytics.supportLink(name = "rate")
                    AppLinks.rateApp(context)
                },
                onWriteFeedback = {
                    AppAnalytics.supportLink(name = "feedback")
                    AppLinks.writeFeedback(context)
                },
                onPrivacyPolicy = {
                    AppAnalytics.supportLink(name = "privacy")
                    AppLinks.openUrl(context, AppLinks.privacyPolicy)
                },
                onTermsOfService = {
                    AppAnalytics.supportLink(name = "terms")
                    AppLinks.openUrl(context, AppLinks.termsOfService)
                },
                onRestorePurchase = { alertMessage = billing.restore() },
            )
            }
        }
    }

    if (showingStorageIntro) {
        StorageIntroSheet(
            pendingKind = pendingKind,
            onDismiss = { showingStorageIntro = false },
            onContinue = {
                showingStorageIntro = false
                onRequestPermissionsAndPickFolder(pendingKind) { uri -> openFolderList(pendingKind, uri) }
            },
        )
    }

    if (showingPaywall) {
        LaunchedEffect(Unit) { AppAnalytics.paywallShown(source = "paywall") }
        PaywallSheet(
            productDetails = productDetails,
            purchaseError = purchaseError,
            showResetQuota = !UsageTracker.hasFreeQuotaRemaining(context),
            onDismiss = { showingPaywall = false },
            onPurchase = onLaunchPurchase,
            onRestore = { billing.restore() },
            onResetQuota = {
                UsageTracker.resetQuotaForTesting(context)
                usageRefreshId += 1
                Haptics.tick(haptics)
                showingPaywall = false
            },
        )
        LaunchedEffect(isUnlocked) { if (isUnlocked) showingPaywall = false }
    }

    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = { TextButton(onClick = { alertMessage = null }) { Text("OK") } },
            title = { Text("Notice") },
            text = { Text(alertMessage ?: "") },
        )
    }

    if (showingAccountId) {
        val email = AccountGate.currentAccount(context)?.email ?: "Not signed in."
        AlertDialog(
            onDismissRequest = { showingAccountId = false },
            title = { Text("Account") },
            text = { Text(email) },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(email)); showingAccountId = false }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { showingAccountId = false }) { Text("OK") } },
        )
    }
}

// MARK: - Home

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    photoCount: Int,
    videoCount: Int,
    storageUsedFraction: Float,
    storageUsedBytes: Long,
    storageFreeBytes: Long,
    lastRun: LastRun?,
    isUnlocked: Boolean,
    usageFraction: Float,
    usageText: String,
    usageRefreshId: Int,
    onCopyPhotos: () -> Unit,
    onCopyVideos: () -> Unit,
    onFreeCardTap: () -> Unit,
    onTitleLongPress: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    }

    Box(Modifier.fillMaxSize()) {
        HomeMeshBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppearLift(0) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .combinedClickable(onClick = {}, onLongClick = onTitleLongPress),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Theme.accentBlue),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "EASY BACKUP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.6.sp,
                                color = Theme.textSecondary,
                            )
                        }
                        Text(
                            dateLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Theme.textSecondary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = "Settings",
                            tint = Theme.textPrimary,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.78f))
                                .clickable {
                                    Haptics.tap(haptics)
                                    onOpenSettings()
                                }
                                .padding(8.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(greeting, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Theme.textSecondary)
                        Text(
                            "Your library,\nkept off-device",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.textPrimary,
                            lineHeight = 36.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryChip(
                            icon = Icons.Filled.Photo,
                            text = if (photoCount > 0) "${formatCount(photoCount)} photos" else "Photos",
                            tint = Theme.accentPurple,
                        )
                        LibraryChip(
                            icon = Icons.Filled.Videocam,
                            text = if (videoCount > 0) "${formatCount(videoCount)} videos" else "Videos",
                            tint = Theme.accentPurple,
                        )
                    }
                }
            }

            AppearLift(50) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 22.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = Theme.accentPurple.copy(alpha = 0.20f),
                            spotColor = Theme.accentPurple.copy(alpha = 0.28f),
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Theme.accentBlue,
                                    Color(0.18f, 0.42f, 0.58f),
                                    Theme.accentPurple.copy(alpha = 0.95f),
                                ),
                            ),
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "DEVICE STORAGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = Color.White.copy(alpha = 0.72f),
                            )
                            Text(
                                if (storageUsedFraction > 0.9f) "Almost full" else "Ready to back up",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                if (storageUsedFraction > 0.9f) {
                                    "Free space now so new shots have room."
                                } else {
                                    "Copy albums to a drive, then delete from this phone."
                                },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                        StorageGauge(usedFraction = storageUsedFraction, inverted = true)
                    }
                    if (storageUsedBytes > 0L || storageFreeBytes > 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.18f)),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "USED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White.copy(alpha = 0.68f),
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, storageUsedBytes),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.22f)),
                            )
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    "FREE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White.copy(alpha = 0.68f),
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, storageFreeBytes),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            AppearLift(100) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    HomeActionCard(
                        title = "Photos",
                        countText = if (photoCount > 0) "${formatCount(photoCount)} in library" else "Scan your library",
                        icon = Icons.Filled.PhotoLibrary,
                        tint = Theme.accentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onCopyPhotos,
                    )
                    HomeActionCard(
                        title = "Videos",
                        countText = if (videoCount > 0) "${formatCount(videoCount)} in library" else "Scan your library",
                        icon = Icons.Filled.Videocam,
                        tint = Theme.accentPurple,
                        modifier = Modifier.weight(1f),
                        onClick = onCopyVideos,
                    )
                }
            }

            if (!isUnlocked) {
                AppearLift(160) {
                    key(usageRefreshId) {
                        NeumorphicSurface(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onFreeCardTap() },
                            cornerRadius = 20.dp,
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Free plan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Theme.textPrimary)
                                        Text(usageText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Theme.textSecondary)
                                    }
                                    Text(
                                        "Unlock",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Theme.accentPurple,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Theme.accentPurple.copy(alpha = 0.10f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Theme.shadowDark.copy(alpha = 0.12f)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(usageFraction.coerceIn(0.04f, 1f))
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Theme.accentBlue, Theme.accentPurple),
                                                ),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (lastRun != null) {
                AppearLift(200) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("ACTIVITY")
                        HomeInfoCard(
                            icon = Icons.Filled.CheckCircle,
                            tint = Theme.accentGreen,
                            title = "Last backup",
                            subtitle = "${lastRun.count} ${lastRun.kindLabel} · ${relativeTime(lastRun.whenMillis)}",
                        )
                    }
                }
            }

            AppearLift(240) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Theme.accentPurple.copy(alpha = 0.06f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = Theme.accentPurple.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(14.dp),
                    )
                    Text(
                        "Verify files on the drive before deleting anything from this phone.",
                        fontSize = 12.sp,
                        color = Theme.textSecondary,
                    )
                }
            }
        }
    }
}

// MARK: - Settings

@Composable
private fun SettingsScreen(
    isUnlocked: Boolean,
    onBack: () -> Unit,
    onRateApp: () -> Unit,
    onWriteFeedback: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    onRestorePurchase: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val versionName = remember {
        try {
            // App version shown in footer; packageManager may throw on odd devices.
            "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        HomeMeshBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Back",
                tint = Theme.textPrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.78f))
                    .clickable {
                        Haptics.tap(haptics)
                        onBack()
                    }
                    .padding(8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Settings",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                )
                Text(
                    "Feedback, legal, and purchases",
                    fontSize = 14.sp,
                    color = Theme.textSecondary,
                )
            }

            SettingsGroup("APP") {
                SettingsRow(
                    icon = Icons.Filled.Star,
                    title = "Rate this app",
                    subtitle = "Share a quick Play Store review",
                    onClick = onRateApp,
                )
                Divider(Modifier.padding(start = 68.dp), color = Theme.hairlineDark)
                SettingsRow(
                    icon = Icons.Filled.Email,
                    title = "Write Feedback",
                    subtitle = "Email ideas or issues",
                    onClick = onWriteFeedback,
                )
            }

            SettingsGroup("LEGAL") {
                SettingsRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How Easy Backup handles data",
                    onClick = onPrivacyPolicy,
                )
                Divider(Modifier.padding(start = 68.dp), color = Theme.hairlineDark)
                SettingsRow(
                    icon = Icons.Filled.Description,
                    title = "Terms of Service",
                    subtitle = "Rules for using the app",
                    onClick = onTermsOfService,
                )
            }

            SettingsGroup("PURCHASES") {
                SettingsRow(
                    icon = Icons.Filled.Restore,
                    title = "Restore Purchase",
                    subtitle = if (isUnlocked) "Unlimited unlock is active" else "Re-enable unlock on this Google account",
                    onClick = onRestorePurchase,
                )
            }

            Text(
                "Easy Backup · $versionName",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Theme.textSecondary.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        NeumorphicSurface(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                Haptics.tap(haptics)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccentIconWell(icon = icon, tint = Theme.accentPurple, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Theme.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = Theme.textSecondary, maxLines = 2)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Theme.textSecondary.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

// MARK: - Storage intro (Material bottom sheet — a real system sheet, so no
// custom black-border workaround is needed the way iOS required)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageIntroSheet(pendingKind: MediaKind, onDismiss: () -> Unit, onContinue: () -> Unit) {
    val tint = if (pendingKind == MediaKind.PHOTO) Theme.accentBlue else Theme.accentPurple
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.base) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NeumorphicIconBadge(Icons.Filled.Save, tint, size = 52.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Where should we save the backup?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Plug in your drive first. On the next screen, choose the drive and pick a folder where your ${if (pendingKind == MediaKind.PHOTO) "photos" else "videos"} will be saved.",
                fontSize = 14.sp,
                color = Theme.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Theme.depth.copy(alpha = 0.07f),
                        spotColor = Theme.depth.copy(alpha = 0.10f),
                    )
                    .clip(shape)
                    .background(Theme.action)
                    .clickable {
                        Haptics.tap(haptics)
                        onContinue()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.onAction,
                )
            }
        }
    }
}

// MARK: - Paywall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallSheet(
    productDetails: ProductDetails?,
    purchaseError: String?,
    showResetQuota: Boolean,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onResetQuota: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.base) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NeumorphicIconBadge(Icons.Filled.Lock, Theme.accentPurple, size = 60.dp)
            Spacer(Modifier.height(16.dp))
            Text("You've used your free 1 GB", fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                color = Theme.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Backing up is free for your first 1 GB. Unlock unlimited backups to keep going — no size limit, one-time purchase.",
                fontSize = 14.sp, color = Theme.textSecondary, textAlign = TextAlign.Center,
            )
            if (purchaseError != null) {
                Spacer(Modifier.height(8.dp))
                Text(purchaseError, fontSize = 12.sp, color = Color.Red, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            Button(
                onClick = onPurchase,
                enabled = productDetails != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.action, contentColor = Theme.onAction),
            ) {
                Text(if (price != null) "Unlock Unlimited — $price" else "Unlock Unlimited", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRestore) { Text("Restore Purchases", color = Theme.textSecondary) }
            if (BuildConfig.DEBUG && showResetQuota) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onResetQuota) {
                    Text("Reset Quota (Testing)", color = Theme.textSecondary.copy(alpha = 0.85f))
                }
            }
        }
    }
}

// MARK: - Copy screen (folder list, manual per-folder start)

@Composable
private fun CopyScreen(
    pendingKind: MediaKind,
    folderStates: List<FolderState>,
    activeIds: Set<String>,
    onStartSelected: (List<String>) -> Unit,
    onFinish: () -> Unit,
) {
    val kindLabel = if (pendingKind == MediaKind.PHOTO) "photos" else "videos"
    val tint = if (pendingKind == MediaKind.PHOTO) Theme.accentBlue else Theme.accentPurple
    val haptics = LocalHapticFeedback.current

    val selectableIds = remember(folderStates) {
        folderStates.filter { it.isWaiting || it.quotaBlocked }.map { it.bucketId }.toSet()
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(selectableIds) {
        selectedIds = selectedIds.intersect(selectableIds)
    }

    val selectedWaiting = selectedIds.filter { id ->
        folderStates.any { it.bucketId == id && (it.isWaiting || it.quotaBlocked) }
    }
    val isBusy = activeIds.isNotEmpty() ||
        folderStates.any { !it.done && !it.isWaiting && !it.quotaBlocked }
    val doneCount = folderStates.count { it.done }
    val allSelectableSelected =
        selectableIds.isNotEmpty() && selectedIds.containsAll(selectableIds)
    val showConfirm = selectedWaiting.isNotEmpty()

    BackHandler(onBack = onFinish)

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.base)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Backup $kindLabel",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Theme.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Theme.textSecondary,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                Haptics.tap(haptics)
                                onFinish()
                            }
                            .padding(6.dp),
                    )
                }
                Text(
                    when {
                        showConfirm -> "Slide to send selected folders to your drive"
                        isBusy -> "Copying to your drive…"
                        selectableIds.isEmpty() && doneCount > 0 -> "All folders backed up"
                        else -> "Select the folders you want to back up"
                    },
                    fontSize = 14.sp,
                    color = Theme.textSecondary,
                )

                if (selectableIds.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildString {
                                append("${folderStates.size} folders")
                                if (doneCount > 0) append(" · $doneCount done")
                                if (selectedWaiting.isNotEmpty()) {
                                    append(" · ${selectedWaiting.size} selected")
                                }
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Theme.textSecondary,
                        )
                        Text(
                            if (allSelectableSelected) "Deselect all" else "Select all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    Haptics.tap(haptics)
                                    selectedIds =
                                        if (allSelectableSelected) emptySet() else selectableIds
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = if (showConfirm || isBusy) 110.dp else 24.dp,
                ),
            ) {
                items(
                    items = folderStates,
                    key = { it.bucketId },
                ) { folder ->
                    FolderSelectCard(
                        folder = folder,
                        tint = tint,
                        selected = selectedIds.contains(folder.bucketId),
                        onToggle = {
                            selectedIds = if (selectedIds.contains(folder.bucketId)) {
                                selectedIds - folder.bucketId
                            } else {
                                selectedIds + folder.bucketId
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showConfirm || isBusy,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .fillMaxWidth()
                .background(Theme.base)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            enter = fadeIn(tween(220)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(280),
                ),
            exit = fadeOut(tween(160)) +
                slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(200),
                ),
        ) {
            if (showConfirm) {
                SlideToPowerOffControl(
                    accent = tint,
                    label = "slide to back up",
                    onCompleted = {
                        val ids = selectedWaiting
                        onStartSelected(ids)
                        selectedIds = selectedIds - ids.toSet()
                    },
                )
            } else {
                // Busy, nothing left to select — compact progress + cancel
                val active = folderStates.filter { !it.done && !it.isWaiting && !it.quotaBlocked }
                val overall =
                    if (active.isEmpty()) 0f else active.map { it.fraction }.average().toFloat()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Theme.card)
                        .border(1.dp, Theme.hairlineDark, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Backing up…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.textPrimary,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Theme.shadowDark.copy(alpha = 0.10f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(overall.coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(tint),
                            )
                        }
                    }
                    Text(
                        "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                Haptics.tap(haptics)
                                onFinish()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

private fun relativeTime(whenMillis: Long): String {
    val diff = System.currentTimeMillis() - whenMillis
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 1440 -> "${minutes / 60} hr ago"
        else -> "${minutes / 1440} d ago"
    }
}

private data class StorageStats(val usedFraction: Float, val usedBytes: Long, val freeBytes: Long)

private fun deviceStorageStats(): StorageStats {
    return try {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val used = (total - available).coerceAtLeast(0L)
        StorageStats(
            usedFraction = if (total <= 0L) 0f else (used.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            usedBytes = used,
            freeBytes = available,
        )
    } catch (_: Exception) {
        StorageStats(0f, 0L, 0L)
    }
}

private suspend fun quickCount(context: android.content.Context, kind: MediaKind): Int {
    return try {
        val collection = if (kind == MediaKind.PHOTO)
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)?.use { it.count } ?: 0
    } catch (_: Exception) { 0 }
}
