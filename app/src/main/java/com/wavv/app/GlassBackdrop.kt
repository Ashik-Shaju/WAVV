package com.wavv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.graphics.PorterDuff
import android.os.Build
import android.view.View
import android.graphics.RenderNode as AndroidRenderNode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal val HomeFrostedGlass = Color(0x761E2024)
internal val HomeFrostedFill = Color(0x3D282930)
internal val HomeFrostedBorder = Color.White.copy(alpha = .12f)
internal fun Modifier.homeBlueVioletFill() = homeBlueVioletSurface(highlight = false)
internal fun Modifier.homeBlueVioletHighlightFill() = homeBlueVioletSurface(highlight = true)
internal fun Modifier.homeNavBarFill() = background(Color(0xFF191919))
internal fun Modifier.homeNavBubbleFill() = background(Color(0xFF2D2D2D))

internal fun Modifier.homeStackCardFill() = background(Color(0xFF161616))

internal fun Modifier.homeThumbnailAccentFill(accent: Color) = drawWithCache {
    val body = Color(
        red = (accent.red * .38f).coerceAtLeast(.018f),
        green = (accent.green * .38f).coerceAtLeast(.018f),
        blue = (accent.blue * .38f).coerceAtLeast(.018f),
    )
    val base = Brush.verticalGradient(
        listOf(body, Color(body.red * .82f, body.green * .82f, body.blue * .82f)),
        endY = size.height,
    )
    val accentGlow = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = .32f), accent.copy(alpha = .12f), Color.Transparent),
        center = Offset(size.width * .5f, size.height * .12f),
        radius = minOf(size.width * .9f, size.height * 2f).coerceAtLeast(1f),
    )
    onDrawBehind {
        drawRect(base)
        drawRect(accentGlow)
    }
}

internal fun Modifier.homeNowPlayingFill(accent: Color) = drawWithCache {
    val solidAccent = accent.copy(alpha = 1f)
    val deepAccent = Color(
        red = (accent.red * .68f).coerceAtLeast(.035f),
        green = (accent.green * .68f).coerceAtLeast(.035f),
        blue = (accent.blue * .68f).coerceAtLeast(.055f),
    )
    val accentGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to solidAccent,
            .38f to solidAccent,
            .72f to deepAccent,
            1f to deepAccent,
        ),
        endY = size.height,
    )
    onDrawBehind { drawRect(accentGradient) }
}

internal fun homeFallbackAccent(seed: Long): Color {
    val palette = listOf(
        Color(0xFF293D5A),
        Color(0xFF443451),
        Color(0xFF24464B),
        Color(0xFF49313F),
        Color(0xFF453822),
        Color(0xFF304554),
    )
    return palette[Math.floorMod(seed, palette.size.toLong()).toInt()]
}

private fun Modifier.homeBlueVioletSurface(highlight: Boolean) = drawWithCache {
    val base = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to if (highlight) Color(0xFF101735) else Color(0xFF0D1326),
            .22f to if (highlight) Color(0xFF111832) else Color(0xFF101630),
            .48f to if (highlight) Color(0xFF11152E) else Color(0xFF12172F),
            .76f to if (highlight) Color(0xFF0C1122) else Color(0xFF0C1121),
            1f to if (highlight) Color(0xFF090C18) else Color(0xFF090B16),
        ),
        endY = size.height,
    )
    val glowRadius = minOf(size.width * .64f, size.height * 1.25f).coerceAtLeast(1f)
    val glow = Brush.radialGradient(
        colorStops = if (highlight) {
            arrayOf(
                0f to Color(0xF53B3BAA),
                .22f to Color(0xE735359C),
                .44f to Color(0xC52B2F89),
                .64f to Color(0x9821276B),
                .84f to Color(0x58171D49),
                1f to Color.Transparent,
            )
        } else {
            arrayOf(
                0f to Color(0xF5312E96),
                .22f to Color(0xE8302D90),
                .44f to Color(0xC4262B7C),
                .64f to Color(0x961F245F),
                .84f to Color(0x56151B42),
                1f to Color.Transparent,
            )
        },
        center = Offset(size.width * .5f, size.height * .06f),
        radius = glowRadius,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(glow)
    }
}
internal fun Modifier.staticGlassFill(tint: Color) = background(
    Brush.verticalGradient(listOf(Color.White.copy(alpha = .075f), tint, Color.Black.copy(alpha = .18f))),
)

private const val BACKDROP_FRAME_SCALE = .30f
internal object FrostedBackdropFrames {
    private const val CAPTURE_PADDING_DP = 60f
    private val bitmapCanvas = AndroidCanvas()
    private val capturePath = AndroidPath()
    private val rootLocation = IntArray(2)
    private val surfaceLocation = IntArray(2)
    private val sourceRect = AndroidRect()
    private val destinationRect = AndroidRect()
    private val captureInProgress = AtomicBoolean(false)
    private val surfaces = linkedMapOf<FrostedBackdropView, AtomicBoolean>()
    private var frameRoot: View? = null
    private var frame: Bitmap? = null
    private var captureBuffer: Bitmap? = null
    private var observedRoot: View? = null
    private var observedTreeObserver: android.view.ViewTreeObserver? = null
    private var preDrawListener: android.view.ViewTreeObserver.OnPreDrawListener? = null

    /** Rebuild the sampled backdrop and hardware blur after the host window becomes visible again. */
    fun refreshAfterHostResume() {
        val registeredSurfaces = surfaces.keys.toList()
        registeredSurfaces.forEach(FrostedBackdropView::refreshAfterHostResume)
        val root = frameRoot ?: registeredSurfaces.firstNotNullOfOrNull(FrostedBackdropView::findComposeRoot)
        root?.postInvalidateOnAnimation()
    }

    fun register(view: FrostedBackdropView, captureState: AtomicBoolean) {
        surfaces[view] = captureState
        view.findComposeRoot()?.let(::observeBeforeDraw)
    }

    fun unregister(view: FrostedBackdropView) {
        surfaces.remove(view)
        if (surfaces.isEmpty()) {
            removePreDrawObserver()
            frame?.recycle()
            captureBuffer?.recycle()
            frame = null
            captureBuffer = null
            frameRoot = null
        }
    }

    private fun observeBeforeDraw(root: View) {
        if (observedRoot === root && preDrawListener != null) return
        removePreDrawObserver()
        val observer = root.viewTreeObserver
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            // Snapshot the updated Compose hierarchy immediately before the same frame
            // draws its frosted AndroidViews. This removes the old 16/250 ms polling lag.
            val requester = surfaces.keys.firstOrNull {
                it.isAttachedToWindow && it.shouldRefreshBackdrop() && it.findComposeRoot() === root
            }
            if (requester != null) capture(root)
            true
        }
        observedRoot = root
        observedTreeObserver = observer
        preDrawListener = listener
        observer.addOnPreDrawListener(listener)
    }

    private fun removePreDrawObserver() {
        val observer = observedTreeObserver
        val listener = preDrawListener
        if (observer?.isAlive == true && listener != null) observer.removeOnPreDrawListener(listener)
        observedRoot = null
        observedTreeObserver = null
        preDrawListener = null
    }

    private fun capture(root: View) {
        if (!captureInProgress.compareAndSet(false, true)) return
        if (root.width <= 0 || root.height <= 0) {
            captureInProgress.set(false)
            return
        }

        val bitmapWidth = (root.width * BACKDROP_FRAME_SCALE).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (root.height * BACKDROP_FRAME_SCALE).roundToInt().coerceAtLeast(1)
        if (frameRoot !== root || frame?.width != bitmapWidth || frame?.height != bitmapHeight) {
            frame?.recycle()
            captureBuffer?.recycle()
            frame = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            captureBuffer = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            frameRoot = root
        }

        val target = captureBuffer ?: run {
            captureInProgress.set(false)
            return
        }
        bitmapCanvas.setBitmap(target)
        bitmapCanvas.save()
        bitmapCanvas.scale(BACKDROP_FRAME_SCALE, BACKDROP_FRAME_SCALE)
        surfaces.values.forEach { it.set(true) }
        try {
            // Only rasterize areas sampled by active glass surfaces. The margin preserves
            // upstream artwork blur when the clipped root is rendered into this bitmap.
            // shortcut: 60dp covers current backdrop blurs; increase it if a larger one is added.
            root.getLocationInWindow(rootLocation)
            val padding = (CAPTURE_PADDING_DP * root.resources.displayMetrics.density).roundToInt()
            capturePath.reset()
            surfaces.keys.forEach { surface ->
                if (surface.isAttachedToWindow && surface.shouldRefreshBackdrop() &&
                    surface.findComposeRoot() === root && surface.width > 0 && surface.height > 0
                ) {
                    surface.getLocationInWindow(surfaceLocation)
                    val left = (surfaceLocation[0] - rootLocation[0] - padding).coerceAtLeast(0)
                    val top = (surfaceLocation[1] - rootLocation[1] - padding).coerceAtLeast(0)
                    val right = (surfaceLocation[0] - rootLocation[0] + surface.width + padding).coerceAtMost(root.width)
                    val bottom = (surfaceLocation[1] - rootLocation[1] + surface.height + padding).coerceAtMost(root.height)
                    if (right > left && bottom > top) {
                        capturePath.addRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), AndroidPath.Direction.CW)
                    }
                }
            }
            if (capturePath.isEmpty) return
            bitmapCanvas.clipPath(capturePath)
            bitmapCanvas.drawColor(android.graphics.Color.rgb(10, 10, 10), PorterDuff.Mode.SRC)
            root.draw(bitmapCanvas)
            val previous = frame
            frame = target
            captureBuffer = previous
        } finally {
            surfaces.values.forEach { it.set(false) }
            bitmapCanvas.restore()
            bitmapCanvas.setBitmap(null)
            captureInProgress.set(false)
        }
        // Compare only the exact crop behind each visible glass surface. A full-screen
        // Bitmap.sameAs() on every scroll frame scanned hundreds of thousands of pixels
        // and invalidated every glass view, even when only one card's backdrop moved.
        // Per-surface reusable buffers keep updates precise without missed color changes,
        // per-frame allocations, or a redraw cascade at the end of a fling.
        surfaces.keys.forEach { surface ->
            if (surface.isAttachedToWindow && surface.shouldRefreshBackdrop() &&
                surface.updateBackdropPixels(target, root, BACKDROP_FRAME_SCALE)
            ) surface.invalidate()
        }
    }

    fun drawFrame(
        view: FrostedBackdropView,
        canvas: AndroidCanvas,
        paint: AndroidPaint,
        destinationScale: Float = 1f,
    ): Boolean {
        if (captureInProgress.get() && !canvas.isHardwareAccelerated) return false
        val currentFrame = frame ?: return false
        val root = frameRoot ?: return false
        if (view.findComposeRoot() !== root || view.width <= 0 || view.height <= 0) return false

        root.getLocationInWindow(rootLocation)
        view.getLocationInWindow(surfaceLocation)
        val left = ((surfaceLocation[0] - rootLocation[0]) * BACKDROP_FRAME_SCALE).toInt().coerceAtLeast(0)
        val top = ((surfaceLocation[1] - rootLocation[1]) * BACKDROP_FRAME_SCALE).toInt().coerceAtLeast(0)
        sourceRect.set(
            left,
            top,
            (left + view.width * BACKDROP_FRAME_SCALE).roundToInt().coerceAtMost(currentFrame.width),
            (top + view.height * BACKDROP_FRAME_SCALE).roundToInt().coerceAtMost(currentFrame.height),
        )
        if (sourceRect.width() <= 0 || sourceRect.height() <= 0) return false
        destinationRect.set(
            0,
            0,
            (view.width * destinationScale).roundToInt().coerceAtLeast(1),
            (view.height * destinationScale).roundToInt().coerceAtLeast(1),
        )
        canvas.drawBitmap(currentFrame, sourceRect, destinationRect, paint)
        return true
    }
}

/** Draws a crop of the shared backdrop frame, then applies Android's hardware blur. */
internal class FrostedBackdropView(
    context: Context,
    private val captureState: AtomicBoolean,
    blurRadiusDp: Float,
    tint: Color,
) : View(context) {
    private val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    private val backdropRenderNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AndroidRenderNode("wavv-frosted-backdrop")
    } else null
    private var blurRadiusPx = blurRadiusDp * resources.displayMetrics.density
    private var appliedBlurRadiusPx = Float.NaN
    private var tintColor = tint.toArgb()
    private var refreshEnabled = true
    private var hasBackdropPixels = false
    private var backdropPixelWidth = 0
    private var backdropPixelHeight = 0
    private var lastBackdropPixels = IntArray(0)
    private var workingBackdropPixels = IntArray(0)
    private val backdropRootLocation = IntArray(2)
    private val backdropSurfaceLocation = IntArray(2)

    init {
        setWillNotDraw(false)
        // Avoid a persistent hardware layer: it can keep displaying the previous
        // backdrop frame when the shared sample changes beneath this AndroidView.
        setLayerType(LAYER_TYPE_NONE, null)
        val saturation = android.graphics.ColorMatrix().apply { setSaturation(1.05f) }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(saturation)
    }

    fun updateStyle(
        blurRadiusDp: Float,
        tint: Color,
        refreshEnabled: Boolean = true,
    ) {
        val wasRefreshEnabled = this.refreshEnabled
        val requestedBlurRadiusPx = blurRadiusDp * resources.displayMetrics.density
        val styleChanged = blurRadiusPx != requestedBlurRadiusPx || tintColor != tint.toArgb()
        blurRadiusPx = requestedBlurRadiusPx
        tintColor = tint.toArgb()
        this.refreshEnabled = refreshEnabled
        if (!wasRefreshEnabled && refreshEnabled) hasBackdropPixels = false
        applyBlurEffect()
        if (styleChanged) invalidate()
        if (isAttachedToWindow && wasRefreshEnabled != refreshEnabled && refreshEnabled) invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        // During the backdrop snapshot, omit only this glass layer. Foreground Compose
        // content (buttons, labels, icons) must remain drawable and must not be hidden
        // through a parent draw modifier, which can leave cached layers blank.
        if (captureState.get()) return
        super.onDraw(canvas)
        val frameDrawn = if (canvas.isHardwareAccelerated && backdropRenderNode != null) {
            val nodeWidth = (width * BACKDROP_FRAME_SCALE).roundToInt().coerceAtLeast(1)
            val nodeHeight = (height * BACKDROP_FRAME_SCALE).roundToInt().coerceAtLeast(1)
            backdropRenderNode.setPosition(0, 0, nodeWidth, nodeHeight)
            val recordingCanvas = backdropRenderNode.beginRecording(nodeWidth, nodeHeight)
            val drawn = try {
                FrostedBackdropFrames.drawFrame(this, recordingCanvas, paint, BACKDROP_FRAME_SCALE)
            } finally {
                backdropRenderNode.endRecording()
            }
            if (drawn) {
                canvas.save()
                canvas.scale(1f / BACKDROP_FRAME_SCALE, 1f / BACKDROP_FRAME_SCALE)
                canvas.drawRenderNode(backdropRenderNode)
                canvas.restore()
            }
            drawn
        } else {
            FrostedBackdropFrames.drawFrame(this, canvas, paint)
        }
        if (!frameDrawn) {
            // Keep frosted surfaces opaque before the first shared frame is ready.
            canvas.drawColor(android.graphics.Color.rgb(25, 26, 30))
        }
        canvas.drawColor(HomeFrostedFill.toArgb(), PorterDuff.Mode.SRC_OVER)
        canvas.drawColor(tintColor, PorterDuff.Mode.SRC_OVER)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        FrostedBackdropFrames.register(this, captureState)
    }

    fun refreshAfterHostResume() {
        hasBackdropPixels = false
        applyBlurEffect(force = true)
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) refreshAfterHostResume()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshAfterHostResume()
    }

    private fun applyBlurEffect(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (force || appliedBlurRadiusPx != blurRadiusPx)) {
            // Blur in the downsampled node, then scale it up so the on-screen blur radius stays unchanged.
            backdropRenderNode?.setRenderEffect(
                AndroidRenderEffect.createBlurEffect(
                    blurRadiusPx * BACKDROP_FRAME_SCALE,
                    blurRadiusPx * BACKDROP_FRAME_SCALE,
                    AndroidShader.TileMode.CLAMP,
                ),
            )
            appliedBlurRadiusPx = blurRadiusPx
        }
    }

    fun shouldRefreshBackdrop(): Boolean = refreshEnabled

    /** Returns true when pixels directly behind this surface changed. */
    fun updateBackdropPixels(frame: Bitmap, root: View, scale: Float): Boolean {
        if (findComposeRoot() !== root || width <= 0 || height <= 0) return false
        root.getLocationInWindow(backdropRootLocation)
        getLocationInWindow(backdropSurfaceLocation)
        val left = ((backdropSurfaceLocation[0] - backdropRootLocation[0]) * scale).toInt().coerceAtLeast(0)
        val top = ((backdropSurfaceLocation[1] - backdropRootLocation[1]) * scale).toInt().coerceAtLeast(0)
        val right = (left + width * scale).roundToInt().coerceAtMost(frame.width)
        val bottom = (top + height * scale).roundToInt().coerceAtMost(frame.height)
        if (right <= left || bottom <= top) return false

        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth != backdropPixelWidth || cropHeight != backdropPixelHeight) {
            backdropPixelWidth = cropWidth
            backdropPixelHeight = cropHeight
            val pixelCount = cropWidth * cropHeight
            lastBackdropPixels = IntArray(pixelCount)
            workingBackdropPixels = IntArray(pixelCount)
            hasBackdropPixels = false
        }
        frame.getPixels(workingBackdropPixels, 0, cropWidth, left, top, cropWidth, cropHeight)
        val changed = !hasBackdropPixels || !workingBackdropPixels.contentEquals(lastBackdropPixels)
        if (changed) {
            val previous = lastBackdropPixels
            lastBackdropPixels = workingBackdropPixels
            workingBackdropPixels = previous
            hasBackdropPixels = true
        }
        return changed
    }

    fun findComposeRoot(): View? {
        var current: View = this
        while (true) {
            val parent = current.parent as? View ?: return current
            if (parent.javaClass.name.contains("AndroidComposeView")) return parent
            current = parent
        }
    }

    override fun onDetachedFromWindow() {
        FrostedBackdropFrames.unregister(this)
        super.onDetachedFromWindow()
    }
}

@Composable
internal fun FrostedBackdrop(
    captureState: AtomicBoolean,
    blurRadiusDp: Float,
    tint: Color,
    modifier: Modifier,
    refreshEnabled: Boolean = true,
) {
    AndroidView(
        modifier = modifier,
        factory = { FrostedBackdropView(it, captureState, blurRadiusDp, tint) },
        update = { it.updateStyle(blurRadiusDp, tint, refreshEnabled) },
    )
}
