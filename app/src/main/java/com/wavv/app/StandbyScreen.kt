package com.wavv.app

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.os.BatteryManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

internal object StandbyPreference {
    private const val PREFS = "standby_preferences"
    private const val ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }
}

internal class WavvDeviceAdminReceiver : DeviceAdminReceiver()

internal object StandbyDeviceLock {
    private fun adminComponent(context: Context) = ComponentName(context, WavvDeviceAdminReceiver::class.java)

    fun isEnabled(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

    fun activationIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Wavv uses this permission only to lock your phone from Standby after motion or the unplugged idle limit.",
        )

    fun lockNow(context: Context): Boolean = runCatching {
        context.getSystemService(DevicePolicyManager::class.java).lockNow()
        true
    }.getOrDefault(false)
}

@Composable
internal fun StandbyScreen(
    song: Song,
    isPlaying: Boolean,
    isActive: Boolean,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val motionSensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    val motionWakeLock = remember(powerManager) {
        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Wavv:StandbyMotionWake",
        ).apply { setReferenceCounted(false) }
    }
    val batteryStatus = remember(context) {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    var isPluggedIn by remember(context) {
        mutableStateOf((batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
    }
    val deviceAdminEnabled = remember(context) { StandbyDeviceLock.isEnabled(context) }
    var screenAwake by remember { mutableStateOf(true) }
    var contentRefreshBlackout by remember { mutableStateOf(false) }
    var secureMode by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var inactivityToken by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var burnInStep by remember { mutableIntStateOf(0) }
    var lastMotionAt by remember { mutableLongStateOf(0L) }
    var idleSince by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var lockIssued by remember { mutableStateOf(false) }
    val pillMorph = remember { Animatable(0f) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val latestOnToggle by rememberUpdatedState(onTogglePlayback)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnExit by rememberUpdatedState(onExit)
    val latestKeepScreenOnChange by rememberUpdatedState(onKeepScreenOnChange)
    val latestScreenAwake by rememberUpdatedState(screenAwake)
    val latestIdleSince by rememberUpdatedState(idleSince)
    val latestLockIssued by rememberUpdatedState(lockIssued)
    val latestAdminEnabled by rememberUpdatedState(deviceAdminEnabled)
    val latestSecureMode by rememberUpdatedState(secureMode)
    val latestLockIssuedSetter by rememberUpdatedState<(Boolean) -> Unit> { lockIssued = it }
    val latestKeepScreenOnSetter by rememberUpdatedState<(Boolean) -> Unit> { enabled ->
        keepScreenOn = enabled
        latestKeepScreenOnChange(enabled)
    }
    val latestInteraction by rememberUpdatedState<(Boolean) -> Unit> { wake ->
        idleSince = SystemClock.elapsedRealtime()
        lockIssued = false
        secureMode = false
        if (wake) {
            screenAwake = true
            if (!keepScreenOn) {
                keepScreenOn = true
                latestKeepScreenOnChange(true)
            }
            runCatching { motionWakeLock.acquire(3_500L) }
        }
        inactivityToken++
    }
    val latestMotion by rememberUpdatedState<(Long) -> Unit> { now ->
        if (now - lastMotionAt >= 2_000L) {
            lastMotionAt = now
            val idleFor = now - latestIdleSince
            if (latestSecureMode || idleFor >= 5 * 60 * 1_000L) {
                if (!latestLockIssued) {
                    latestLockIssuedSetter(true)
                    latestKeepScreenOnSetter(false)
                    if (latestAdminEnabled) StandbyDeviceLock.lockNow(context)
                }
            } else {
                latestInteraction(false)
            }
        }
    }

    DisposableEffect(motionWakeLock) {
        onDispose { if (motionWakeLock.isHeld) motionWakeLock.release() }
    }
    DisposableEffect(isActive, sensorManager, motionSensor) {
        val listener = object : SensorEventListener {
            private var previousX = Float.NaN
            private var previousY = Float.NaN
            private var previousZ = Float.NaN

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                if (previousX.isNaN()) {
                    previousX = x
                    previousY = y
                    previousZ = z
                    return
                }
                val deltaX = x - previousX
                val deltaY = y - previousY
                val deltaZ = z - previousZ
                val movement = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                previousX = x
                previousY = y
                previousZ = z
                if (movement >= 2.4f) latestMotion(SystemClock.elapsedRealtime())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (isActive && motionSensor != null) {
            sensorManager.registerListener(listener, motionSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    DisposableEffect(isActive, context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> isPluggedIn = true
                    Intent.ACTION_POWER_DISCONNECTED -> isPluggedIn = false
                }
            }
        }
        if (isActive) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(isActive) {
        pillMorph.animateTo(
            targetValue = if (isActive) 1f else 0f,
            animationSpec = tween(420),
        )
    }

    LaunchedEffect(inactivityToken, isActive) {
        if (!isActive) return@LaunchedEffect
        delay(5_000L)
        screenAwake = false
    }
    LaunchedEffect(inactivityToken, isActive) {
        if (!isActive) {
            contentRefreshBlackout = false
            return@LaunchedEffect
        }
        try {
            while (true) {
                delay(10 * 60 * 1_000L)
                contentRefreshBlackout = true
                delay(3_000L)
                contentRefreshBlackout = false
            }
        } finally {
            contentRefreshBlackout = false
        }
    }
    LaunchedEffect(inactivityToken, isActive, isPluggedIn) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            val idleFor = SystemClock.elapsedRealtime() - idleSince
            if (idleFor >= 5 * 60 * 1_000L) secureMode = true
            if (!isPluggedIn && idleFor >= 20 * 60 * 1_000L) {
                if (!lockIssued) {
                    lockIssued = true
                    keepScreenOn = false
                    latestKeepScreenOnChange(false)
                    if (deviceAdminEnabled) StandbyDeviceLock.lockNow(context)
                }
                break
            }
            delay(1_000L)
        }
    }
    LaunchedEffect(keepScreenOn) { latestKeepScreenOnChange(keepScreenOn) }
    LaunchedEffect(screenAwake, isActive) {
        if (!isActive || screenAwake) {
            burnInStep = 0
        } else {
            while (true) {
                delay(60_000L)
                burnInStep = (burnInStep + 1) % 8
            }
        }
    }
    LaunchedEffect(screenAwake) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(if (screenAwake) 1_000 else 30_000)
        }
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (screenAwake) 1f else 0f,
        animationSpec = tween(if (screenAwake) 360 else 1_100),
        label = "standby acrylic sleep fade",
    )
    val contentRefreshAlpha by animateFloatAsState(
        targetValue = if (contentRefreshBlackout) 1f else 0f,
        animationSpec = tween(if (contentRefreshBlackout) 650 else 800),
        label = "standby periodic content refresh",
    )
    val clockTime = remember(currentTime) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(currentTime)) }
    val clockDate = remember(currentTime) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(currentTime)) }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var previousTapTime = 0L
                var previousTapPosition = Offset.Zero
                val doubleTapDistance = with(density) { 56.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var finalPosition = down.position
                    var upTime = down.uptimeMillis
                    var didMove = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        finalPosition = change.position
                        upTime = change.uptimeMillis
                        if ((finalPosition - down.position).getDistance() > viewConfiguration.touchSlop) didMove = true
                        if (!change.pressed) break
                    }
                    if (!didMove) {
                        if (
                            previousTapTime > 0L &&
                            upTime - previousTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
                            (finalPosition - previousTapPosition).getDistance() <= doubleTapDistance
                        ) {
                            latestInteraction(true)
                            previousTapTime = 0L
                        } else {
                            latestInteraction(false)
                            previousTapTime = upTime
                            previousTapPosition = finalPosition
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = backgroundAlpha },
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "standby blurred artwork",
            ) { backgroundSong ->
                AlbumArt(
                    backgroundSong,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.16f; scaleY = 1.16f }.blur(58.dp),
                    contentDescription = null,
                    radius = 0.dp,
                )
            }
            Box(Modifier.fillMaxSize().homeAcrylicBackground(hasArtwork = true))
        }

        val landscape = maxWidth > maxHeight
        val portrait = !landscape
        val burnInX by animateFloatAsState(
            targetValue = when (burnInStep % 4) { 0 -> 0f; 1 -> 2.2f; 2 -> 0f; else -> -2.2f },
            animationSpec = tween(12_000),
            label = "standby burn in horizontal drift",
        )
        val burnInY by animateFloatAsState(
            targetValue = when (burnInStep / 2) { 0 -> 0f; 1 -> 2f; 2 -> 0f; else -> -2f },
            animationSpec = tween(12_000),
            label = "standby burn in vertical drift",
        )
        val burnInModifier = Modifier.graphicsLayer {
            translationX = with(density) { burnInX.dp.toPx() }
            translationY = with(density) { burnInY.dp.toPx() }
        }
        val artSize = if (landscape) {
            minOf(maxHeight * .76f, maxWidth * .48f)
        } else {
            minOf(maxWidth * .68f, maxHeight * .38f)
        }
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp).then(burnInModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StandbyClock(clockTime, clockDate, Modifier.weight(1.12f).fillMaxHeight(), landscape = true)
                StandbyArtworkAndPill(
                    song = song,
                    isPlaying = isPlaying,
                    size = artSize,
                    latestAwake = latestScreenAwake,
                    latestOnToggle = latestOnToggle,
                    latestOnPrevious = latestOnPrevious,
                    latestOnNext = latestOnNext,
                    onInteraction = { wake -> latestInteraction(wake) },
                    pillMorph = pillMorph.value,
                    isPortrait = portrait,
                    onExit = { latestOnExit() },
                    modifier = Modifier.weight(.88f).fillMaxHeight(),
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp).then(burnInModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StandbyClock(clockTime, clockDate, Modifier.weight(.48f).fillMaxWidth(), landscape = false)
                StandbyArtworkAndPill(
                    song = song,
                    isPlaying = isPlaying,
                    size = artSize,
                    latestAwake = latestScreenAwake,
                    latestOnToggle = latestOnToggle,
                    latestOnPrevious = latestOnPrevious,
                    latestOnNext = latestOnNext,
                    onInteraction = { wake -> latestInteraction(wake) },
                    pillMorph = pillMorph.value,
                    isPortrait = portrait,
                    onExit = { latestOnExit() },
                    modifier = Modifier.weight(.52f).fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = contentRefreshAlpha)),
        )
    }
}

@Composable
private fun StandbyClock(time: String, date: String, modifier: Modifier, landscape: Boolean) {
    BoxWithConstraints(modifier) {
        val requestedClockSize = if (landscape) 160.sp else 148.sp
        // Text is measured before the horizontal scale transform, so size it to the actual
        // available width to keep the final digit from being clipped in portrait.
        val availableClockSize = with(LocalDensity.current) {
            maxWidth.toSp().value * if (landscape) .42f else .36f
        }
        val clockSize = minOf(requestedClockSize.value, availableClockSize).sp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                time,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = .62f },
                color = Color.White,
                fontSize = clockSize,
                lineHeight = clockSize * 1.03f,
                letterSpacing = if (landscape) (-3.5).sp else (-5).sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                date,
                color = Color.White.copy(alpha = .68f),
                fontSize = if (landscape) 14.sp else 16.sp,
                letterSpacing = .5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StandbyArtworkAndPill(
    song: Song,
    isPlaying: Boolean,
    size: androidx.compose.ui.unit.Dp,
    latestAwake: Boolean,
    latestOnToggle: () -> Unit,
    latestOnPrevious: () -> Unit,
    latestOnNext: () -> Unit,
    onInteraction: (Boolean) -> Unit,
    pillMorph: Float,
    isPortrait: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val artworkPressed = remember(song.id) { mutableStateOf(false) }
    val draggingHorizontally = remember(song.id) { mutableStateOf(false) }
    val dragTranslation = remember(song.id) { mutableFloatStateOf(0f) }
    val settledTranslation by animateFloatAsState(
        targetValue = if (artworkPressed.value) dragTranslation.floatValue else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "standby artwork drag return",
    )
    val scale by animateFloatAsState(
        targetValue = if (artworkPressed.value && !draggingHorizontally.value) .94f else 1f,
        animationSpec = spring(dampingRatio = .52f, stiffness = Spring.StiffnessMedium),
        label = "standby artwork touch scale",
    )
    val pendingTap = remember(song.id) { mutableStateOf<Job?>(null) }
    val previousTapTime = remember(song.id) { mutableLongStateOf(0L) }
    val previousTapPosition = remember(song.id) { mutableStateOf(Offset.Zero) }
    val latestInteraction by rememberUpdatedState(onInteraction)
    val currentAwake by rememberUpdatedState(latestAwake)
    val toggle by rememberUpdatedState(latestOnToggle)
    val previous by rememberUpdatedState(latestOnPrevious)
    val next by rememberUpdatedState(latestOnNext)

    Column(
        modifier = modifier.graphicsLayer {
            translationX = if (draggingHorizontally.value) dragTranslation.floatValue else settledTranslation
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = if (isPortrait) with(density) { (-24).dp.toPx() } else 0f
                }
                .clip(RoundedCornerShape(22.dp))
                .semantics { contentDescription = "${song.title}, album artwork. Tap to play or pause; swipe horizontally to change track." }
                .pointerInput(song.id) {
                    val threshold = with(density) { 66.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        artworkPressed.value = true
                        draggingHorizontally.value = false
                        dragTranslation.floatValue = 0f
                        latestInteraction(false)
                        var totalX = 0f
                        var totalY = 0f
                        var horizontalDrag = false
                        var moved = false
                        var upTime = down.uptimeMillis
                        var upPosition = down.position
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y
                                upTime = change.uptimeMillis
                                upPosition = change.position
                                if (abs(totalX) > viewConfiguration.touchSlop || abs(totalY) > viewConfiguration.touchSlop) moved = true
                                if (!horizontalDrag && abs(totalX) > viewConfiguration.touchSlop && abs(totalX) > abs(totalY)) {
                                    horizontalDrag = true
                                    draggingHorizontally.value = true
                                }
                                if (horizontalDrag) {
                                    change.consume()
                                    dragTranslation.floatValue = (totalX * .24f).coerceIn(-64f, 64f)
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            artworkPressed.value = false
                            draggingHorizontally.value = false
                        }

                        if (horizontalDrag) {
                            latestInteraction(false)
                            if (currentAwake && abs(totalX) >= threshold) {
                                val swipedLeft = totalX < 0f
                                if (swipedLeft) previous() else next()
                            }
                        } else if (!moved) {
                            val doubleTap = previousTapTime.longValue > 0L &&
                                upTime - previousTapTime.longValue <= viewConfiguration.doubleTapTimeoutMillis &&
                                (upPosition - previousTapPosition.value).getDistance() <= with(density) { 56.dp.toPx() }
                            if (doubleTap) {
                                pendingTap.value?.cancel()
                                pendingTap.value = null
                                previousTapTime.longValue = 0L
                                latestInteraction(true)
                            } else {
                                previousTapTime.longValue = upTime
                                previousTapPosition.value = upPosition
                                val thisTapTime = upTime
                                pendingTap.value?.cancel()
                                pendingTap.value = scope.launch {
                                    delay(viewConfiguration.doubleTapTimeoutMillis)
                                    if (previousTapTime.longValue == thisTapTime) {
                                        previousTapTime.longValue = 0L
                                        if (currentAwake) toggle()
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "standby cover transition",
            ) { displayedSong -> AlbumArt(displayedSong, Modifier.fillMaxSize(), displayedSong.title, 22.dp) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = artworkPressed.value && latestAwake,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(160)),
                ) {
                    Box(
                        Modifier.size(62.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StandbySongPill(song.title, onInteraction, pillMorph, latestAwake, isPortrait, onExit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandbySongPill(
    title: String,
    onInteraction: (Boolean) -> Unit,
    morph: Float,
    backgroundAwake: Boolean,
    isPortrait: Boolean,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val pillDragging = remember { mutableStateOf(false) }
    val pillDragX = remember { mutableFloatStateOf(0f) }
    val tapNudgeX = remember { mutableFloatStateOf(0f) }
    val settledDragX by animateFloatAsState(
        targetValue = if (pillDragging.value) pillDragX.floatValue else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "standby pill force return",
    )
    val tapNudge by animateFloatAsState(
        targetValue = tapNudgeX.floatValue,
        animationSpec = tween(100),
        label = "standby pill tap jiggle",
    )
    val latestInteraction by rememberUpdatedState(onInteraction)
    val latestExit by rememberUpdatedState(onExit)
    val widthFraction = .92f + (.70f - .92f) * morph
    val pillHeight = 62.dp + (48.dp - 62.dp) * morph

    Box(
        Modifier.widthIn(min = 150.dp, max = 300.dp)
            .fillMaxWidth(widthFraction)
            .height(pillHeight)
            .graphicsLayer {
                translationX = if (pillDragging.value) pillDragX.floatValue else settledDragX + tapNudge
            }
            .clip(CircleShape)
            .background(Color(0xB51A1A1D))
            .border(.8.dp, Color.White.copy(alpha = .14f), CircleShape)
            .semantics { contentDescription = "Now playing: $title. Tap in portrait to return home; tap in landscape to jiggle." }
            .pointerInput(title, isPortrait) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pillDragging.value = true
                    pillDragX.floatValue = 0f
                    tapNudgeX.floatValue = 0f
                    var totalX = 0f
                    var totalY = 0f
                    var moved = false
                    var horizontalDrag = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        totalX = change.position.x - down.position.x
                        totalY = change.position.y - down.position.y
                        moved = moved || abs(totalX) > viewConfiguration.touchSlop || abs(totalY) > viewConfiguration.touchSlop
                        if (!horizontalDrag && abs(totalX) > viewConfiguration.touchSlop && abs(totalX) > abs(totalY)) {
                            horizontalDrag = true
                        }
                        if (horizontalDrag) {
                            change.consume()
                            pillDragX.floatValue = (totalX * .30f).coerceIn(-52f, 52f)
                        }
                        if (!change.pressed) break
                    }
                    pillDragging.value = false
                    pillDragX.floatValue = 0f
                    latestInteraction(false)
                    if (!moved) {
                        if (isPortrait) {
                            latestExit()
                        } else {
                            tapNudgeX.floatValue = with(density) { 8.dp.toPx() }
                            scope.launch {
                                delay(90)
                                tapNudgeX.floatValue = 0f
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().then(
                if (backgroundAwake) Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 22.dp)
                else Modifier,
            ),
            color = Color.White.copy(alpha = .92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    }
}
