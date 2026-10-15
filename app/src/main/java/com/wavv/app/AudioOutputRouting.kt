package com.wavv.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val audioOutputRouteSelector = MediaRouteSelector.Builder()
    .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
    .build()

internal data class AudioOutputRoute(
    val id: String,
    val name: String,
    val kind: String,
    val deviceType: Int,
    val isSelected: Boolean,
    val isSystemRoute: Boolean,
    val volume: Int,
    val volumeMax: Int,
    val volumeHandling: Int,
) {
    val isRemote: Boolean get() = !isSystemRoute
}

internal data class AudioOutputRoutingState(
    val routes: List<AudioOutputRoute> = emptyList(),
    val selectedRoute: AudioOutputRoute? = null,
    val pendingRouteId: String? = null,
    val errorMessage: String? = null,
    val isReady: Boolean = false,
    val revision: Long = 0L,
)

/**
 * The playback service owns the MediaRouter callback for the lifetime of the media session.
 * UI observes snapshots and requests selections through this process-wide routing state holder.
 */
internal object AudioOutputRouting {
    private const val SELECTION_TIMEOUT_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(AudioOutputRoutingState())
    val state: StateFlow<AudioOutputRoutingState> = mutableState.asStateFlow()

    private var router: MediaRouter? = null
    private var audioManager: AudioManager? = null
    private var selectionTimeout: Runnable? = null

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            val pending = mutableState.value.pendingRouteId
            publish(router, clearError = true, clearPending = true)
            if (pending != null && pending != route.id) {
                mutableState.value = mutableState.value.copy(
                    errorMessage = "Android selected a different output.",
                )
            }
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            publish(router)
        }

        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publish(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            val pendingWasRemoved = mutableState.value.pendingRouteId == route.id
            publish(
                router,
                clearPending = pendingWasRemoved,
                errorMessage = if (pendingWasRemoved) "That output is no longer available." else null,
            )
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publish(router)
        }

        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            publish(router)
        }
    }

    fun attach(context: Context) {
        checkMainThread()
        val mediaRouter = MediaRouter.getInstance(context.applicationContext)
        if (router === mediaRouter) {
            publish(mediaRouter, clearError = true)
            return
        }
        detach()
        router = mediaRouter
        audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        mediaRouter.routerParams = MediaRouterParams.Builder()
            .setMediaTransferReceiverEnabled(true)
            .setOutputSwitcherEnabled(true)
            .setTransferToLocalEnabled(true)
            .build()
        // Observe System UI route transfers for the lifetime of the media session.
        mediaRouter.addCallback(audioOutputRouteSelector, callback, MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS)
        publish(mediaRouter, clearError = true)
    }

    fun detach() {
        checkMainThread()
        router?.removeCallback(callback)
        router = null
        audioManager = null
        clearSelectionTimeout()
        mutableState.value = AudioOutputRoutingState(revision = mutableState.value.revision + 1)
    }

    /** Refresh when the picker opens or returns to the foreground. */
    fun refresh() {
        checkMainThread()
        router?.let { publish(it, clearError = true) }
    }

    /** Requests a real AndroidX media route selection; success is confirmed by the route callback. */
    fun selectRoute(routeId: String) {
        checkMainThread()
        val mediaRouter = router
        if (mediaRouter == null) {
            mutableState.value = mutableState.value.copy(
                errorMessage = "Audio routing is not ready yet. Try again.",
            )
            return
        }

        val route = (mediaRouter.routes + mediaRouter.defaultRoute).firstOrNull {
            it.id == routeId && it.isEnabled && it.isSystemRoute &&
                (it.isDefault || it.isDeviceSpeaker || it.matchesSelector(audioOutputRouteSelector))
        }
        if (route == null) {
            publish(
                mediaRouter,
                clearPending = true,
                errorMessage = "That output is no longer available.",
            )
            return
        }
        if (mediaRouter.selectedRoute.id == route.id) {
            publish(mediaRouter, clearPending = true, clearError = true)
            return
        }

        clearSelectionTimeout()
        mutableState.value = mutableState.value.copy(
            pendingRouteId = route.id,
            errorMessage = null,
            revision = mutableState.value.revision + 1,
        )

        val timeout = Runnable {
            if (mutableState.value.pendingRouteId == route.id) {
                val selectedId = mediaRouter.selectedRoute.id
                publish(
                    mediaRouter,
                    clearPending = true,
                    clearError = selectedId == route.id,
                    errorMessage = if (selectedId == route.id) null else "Android couldn't switch to that output.",
                )
            }
        }
        selectionTimeout = timeout
        mainHandler.postDelayed(timeout, SELECTION_TIMEOUT_MS)
        try {
            mediaRouter.selectRoute(route)
        } catch (_: RuntimeException) {
            publish(
                mediaRouter,
                clearPending = true,
                errorMessage = "Android couldn't switch to that output.",
            )
            return
        }
    }

    fun setRouteVolume(routeId: String, volume: Int) {
        checkMainThread()
        val mediaRouter = router ?: return
        val route = mediaRouter.routes.firstOrNull {
            it.id == routeId && it.isSelected && it.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE
        } ?: return
        route.requestSetVolume(volume.coerceIn(0, route.volumeMax.coerceAtLeast(0)))
    }

    private fun publish(
        mediaRouter: MediaRouter,
        clearPending: Boolean = false,
        clearError: Boolean = false,
        errorMessage: String? = null,
    ) {
        val previous = mutableState.value
        val availableRoutes = (mediaRouter.routes + mediaRouter.defaultRoute)
            .asSequence()
            .filter {
                it.isEnabled && it.isSystemRoute &&
                    (it.isDefault || it.isDeviceSpeaker || it.matchesSelector(audioOutputRouteSelector))
            }
            .distinctBy { it.id }
            .map { it.toOutputRoute(mediaRouter.selectedRoute.id) }
            .sortedWith(compareBy<AudioOutputRoute> { it.kind.sortOrder() }.thenBy { it.name.lowercase() })
            .toList()
        val selected = mediaRouter.selectedRoute.toOutputRoute(mediaRouter.selectedRoute.id)
        val selectedId = selected.id
        val pendingId = when {
            clearPending || selectedId == previous.pendingRouteId -> null
            previous.pendingRouteId != null && availableRoutes.none { it.id == previous.pendingRouteId } -> null
            else -> previous.pendingRouteId
        }
        if (pendingId == null && previous.pendingRouteId != null) clearSelectionTimeout()

        mutableState.value = AudioOutputRoutingState(
            routes = availableRoutes,
            selectedRoute = selected,
            pendingRouteId = pendingId,
            errorMessage = errorMessage ?: if (clearError || previous.pendingRouteId != null && pendingId == null) null else previous.errorMessage,
            isReady = true,
            revision = previous.revision + 1,
        )
    }

    private fun MediaRouter.RouteInfo.toOutputRoute(selectedId: String): AudioOutputRoute {
        val selected = id == selectedId
        val isPhone = isDefault || isDeviceSpeaker || deviceType in LOCAL_DEVICE_TYPES
        val lowerName = name.lowercase()
        val kind = when {
            isPhone -> "phone"
            deviceType in TV_DEVICE_TYPES -> "tv"
            listOf("tv", "soundbar", "speaker", "homepod", "echo").any(lowerName::contains) -> "speaker"
            deviceType in WIRED_DEVICE_TYPES -> "wired"
            else -> "headphones"
        }
        // MediaRouter can retain the previous device's volume briefly during local route handoff.
        val displayVolume = if (selected && playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL && playbackStream >= 0) {
            audioManager?.let { manager ->
                try {
                    activeRouteVolume(
                        routeVolume = volume,
                        routeVolumeMax = volumeMax,
                        isSelected = true,
                        isLocalPlayback = true,
                        streamVolume = manager.getStreamVolume(playbackStream),
                        streamVolumeMax = manager.getStreamMaxVolume(playbackStream),
                    )
                } catch (_: IllegalArgumentException) {
                    volume
                }
            } ?: volume
        } else {
            volume
        }
        return AudioOutputRoute(
            id = id,
            name = if (isPhone) "This phone" else name.takeIf(String::isNotBlank) ?: "Audio output",
            kind = kind,
            deviceType = deviceType,
            isSelected = selected,
            isSystemRoute = isSystemRoute,
            volume = displayVolume,
            volumeMax = volumeMax,
            volumeHandling = volumeHandling,
        )
    }

    private fun String.sortOrder(): Int = when (this) {
        "phone" -> 0
        "wired" -> 1
        "headphones" -> 2
        "speaker" -> 3
        "tv" -> 4
        else -> 5
    }

    private fun clearSelectionTimeout() {
        selectionTimeout?.let(mainHandler::removeCallbacks)
        selectionTimeout = null
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "MediaRouter must be accessed on the main thread." }
    }

    private val LOCAL_DEVICE_TYPES = setOf(
        MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER,
        MediaRouter.RouteInfo.DEVICE_TYPE_SMARTPHONE,
        MediaRouter.RouteInfo.DEVICE_TYPE_TABLET,
        MediaRouter.RouteInfo.DEVICE_TYPE_TABLET_DOCKED,
    )
    private val TV_DEVICE_TYPES = setOf(
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI,
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI_ARC,
        MediaRouter.RouteInfo.DEVICE_TYPE_HDMI_EARC,
        MediaRouter.RouteInfo.DEVICE_TYPE_TV,
    )
    private val WIRED_DEVICE_TYPES = setOf(
        MediaRouter.RouteInfo.DEVICE_TYPE_WIRED_HEADSET,
        MediaRouter.RouteInfo.DEVICE_TYPE_WIRED_HEADPHONES,
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_ACCESSORY,
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_DEVICE,
        MediaRouter.RouteInfo.DEVICE_TYPE_USB_HEADSET,
    )
}
