package io.github.honggi82.vr3dmobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.honggi82.vr3dmobile.R
import io.github.honggi82.vr3dmobile.domain.GridKey
import io.github.honggi82.vr3dmobile.domain.ViewGrid
import io.github.honggi82.vr3dmobile.domain.Vr3dManifest
import io.github.honggi82.vr3dmobile.domain.WeightedView
import java.nio.file.Path
import java.util.concurrent.Executors

class SpatialImageView(context: Context) : View(context), AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sourceRect = Rect()
    private val targetRect = Rect()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        textSize = resources.displayMetrics.scaledDensity * 16f
        textAlign = Paint.Align.CENTER
    }
    private val cache = object : LinkedHashMap<GridKey, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GridKey, Bitmap>): Boolean {
            val remove = size > MAX_CACHE_SIZE
            if (remove) eldest.value.recycle()
            return remove
        }
    }
    private val pending = mutableSetOf<GridKey>()
    private val failed = mutableSetOf<GridKey>()
    private var directory: Path? = null
    private var manifest: Vr3dManifest? = null
    private var selection: List<WeightedView> = ViewGrid.interpolate(0f, 0f)
    @Volatile private var desiredKeys: Set<GridKey> = selection.map { it.key }.toSet()
    private var closed = false

    init {
        setBackgroundColor(Ui.BACKGROUND)
    }

    fun setProject(projectDirectory: Path, projectManifest: Vr3dManifest) {
        directory = projectDirectory
        manifest = projectManifest
        requestNearbyViews()
        invalidate()
    }

    fun setTilt(pitch: Float, roll: Float) {
        selection = ViewGrid.interpolate(pitch, roll)
        requestNearbyViews()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var totalWeight = 0f
        selection.forEach { weighted -> if (cache[weighted.key] != null) totalWeight += weighted.weight }
        if (totalWeight <= 0f) {
            canvas.drawText(context.getString(R.string.loading_view), width / 2f, height / 2f, textPaint)
            return
        }
        targetRect.set(0, 0, width, height)
        selection.forEach { weighted ->
            val bitmap = cache[weighted.key] ?: return@forEach
            paint.alpha = ((weighted.weight / totalWeight) * 255f).toInt().coerceIn(0, 255)
            updateCenterCropSource(bitmap, width, height)
            canvas.drawBitmap(bitmap, sourceRect, targetRect, paint)
        }
        paint.alpha = 255
    }

    override fun close() {
        if (closed) return
        closed = true
        worker.shutdownNow()
        cache.values.forEach(Bitmap::recycle)
        cache.clear()
        pending.clear()
    }

    private fun requestNearbyViews() {
        val root = directory ?: return
        val projectManifest = manifest ?: return
        desiredKeys = selection.map { it.key }.toSet()
        selection.forEach { weighted ->
            val key = weighted.key
            if (cache.containsKey(key) || key in pending || key in failed || closed) return@forEach
            val view = projectManifest.views.firstOrNull { it.row == key.row && it.column == key.column }
                ?: return@forEach
            pending += key
            worker.execute {
                if (key !in desiredKeys) {
                    mainHandler.post { pending -= key }
                    return@execute
                }
                val bitmap = decodeBounded(root.resolve(view.path), projectManifest)
                mainHandler.post {
                    pending -= key
                    if (closed) {
                        bitmap?.recycle()
                        return@post
                    }
                    if (bitmap == null) failed += key else cache[key] = bitmap
                    invalidate()
                }
            }
        }
    }

    private fun decodeBounded(path: Path, projectManifest: Vr3dManifest): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path.toString(), bounds)
        if (bounds.outWidth != projectManifest.source.width || bounds.outHeight != projectManifest.source.height) return null
        if (bounds.outWidth !in 1..1920 || bounds.outHeight !in 1..1920) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return try {
            BitmapFactory.decodeFile(path.toString(), options)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun updateCenterCropSource(bitmap: Bitmap, targetWidth: Int, targetHeight: Int) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            sourceRect.set(0, 0, bitmap.width, bitmap.height)
            return
        }
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        if (bitmapRatio > targetRatio) {
            val sourceWidth = (bitmap.height * targetRatio).toInt().coerceAtLeast(1)
            val left = (bitmap.width - sourceWidth) / 2
            sourceRect.set(left, 0, left + sourceWidth, bitmap.height)
        } else {
            val sourceHeight = (bitmap.width / targetRatio).toInt().coerceAtLeast(1)
            val top = (bitmap.height - sourceHeight) / 2
            sourceRect.set(0, top, bitmap.width, top + sourceHeight)
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 5
    }
}
