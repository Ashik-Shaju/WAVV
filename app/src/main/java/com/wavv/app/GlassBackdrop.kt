package com.wavv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import android.graphics.PorterDuff
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal val HomeFrostedGlass = Color(0x761E2024)
internal val HomeFrostedFill = Color(0x3D282930)
internal val HomeFrostedBorder = Color.White.copy(alpha = .12f)
internal object FrostedBackdropFrames {
    private const val FRAME_SCALE = .30f
    private val bitmapCanvas = AndroidCanvas()
    private val captureInProgress = AtomicBoolean(false)
    private val surfaces = linkedMapOf<FrostedBackdropView, AtomicBoolean>()
    private var frameRoot: View? = null
    private var frame: Bitmap? = null
    private var captureBuffer: Bitmap? = null
    private var observedRoot: View? = null
    private var observedTreeObserver: android.view.ViewTreeObserver? = null
    private var preDrawListener: android.view.ViewTreeObserver.OnPreDrawListener? = null

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

        val bitmapWidth = (root.width * FRAME_SCALE).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (root.height * FRAME_SCALE).roundToInt().coerceAtLeast(1)
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
        target.eraseColor(android.graphics.Color.rgb(10, 10, 10))
        bitmapCanvas.setBitmap(target)
        bitmapCanvas.save()
        bitmapCanvas.scale(FRAME_SCALE, FRAME_SCALE)
        surfaces.values.forEach { it.set(true) }
        try {
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
                surface.updateBackdropPixels(target, root, FRAME_SCALE)
            ) surface.invalidate()
        }
    }

    fun drawFrame(view: FrostedBackdropView, canvas: AndroidCanvas, paint: AndroidPaint): Boolean {
        if (captureInProgress.get() && !canvas.isHardwareAccelerated) return false
        val currentFrame = frame ?: return false
        val root = frameRoot ?: return false
        if (view.findComposeRoot() !== root || view.width <= 0 || view.height <= 0) return false

        val rootLocation = IntArray(2)
        val surfaceLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        view.getLocationInWindow(surfaceLocation)
        val left = ((surfaceLocation[0] - rootLocation[0]) * FRAME_SCALE).toInt().coerceAtLeast(0)
        val top = ((surfaceLocation[1] - rootLocation[1]) * FRAME_SCALE).toInt().coerceAtLeast(0)
        val source = AndroidRect(
            left,
            top,
            (left + view.width * FRAME_SCALE).roundToInt().coerceAtMost(currentFrame.width),
            (top + view.height * FRAME_SCALE).roundToInt().coerceAtMost(currentFrame.height),
        )
        if (source.width() <= 0 || source.height() <= 0) return false
        canvas.drawBitmap(currentFrame, source, AndroidRect(0, 0, view.width, view.height), paint)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appliedBlurRadiusPx != blurRadiusPx) {
            setRenderEffect(
                AndroidRenderEffect.createBlurEffect(
                    blurRadiusPx,
                    blurRadiusPx,
                    AndroidShader.TileMode.CLAMP,
                ),
            )
            appliedBlurRadiusPx = blurRadiusPx
        }
        if (styleChanged) invalidate()
        if (isAttachedToWindow && wasRefreshEnabled != refreshEnabled && refreshEnabled) invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        // During the backdrop snapshot, omit only this glass layer. Foreground Compose
        // content (buttons, labels, icons) must remain drawable and must not be hidden
        // through a parent draw modifier, which can leave cached layers blank.
        if (captureState.get()) return
        super.onDraw(canvas)
        if (!FrostedBackdropFrames.drawFrame(this, canvas, paint)) {
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
