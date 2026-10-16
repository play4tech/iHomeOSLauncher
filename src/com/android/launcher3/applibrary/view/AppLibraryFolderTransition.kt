package com.android.launcher3.applibrary.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.icons.CrossFadeBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawable
import kotlin.math.pow

/**
 * Handles the authentic iOS App Library category folder expansion and collapse transitions.
 * Implements the Mini-Grid-Origin Staggered Radial Expansion with 4-Corner Cross-Fade.
 */
class AppLibraryFolderTransition(
    private val page: View?,
    private val folderTile: View?,
    private val overlay: ViewGroup,
    private val overlayGlass: View?,
    private val title: View,
    private val grid: RecyclerView
) {
    private val tempRect = Rect()
    private var anim: ValueAnimator? = null
    private var isClosing = false

    // Track original drawables on the corner hero views for cleanup
    private val originalDrawables = mutableMapOf<BubbleTextView, FastBitmapDrawable>()

    fun isAnimating(): Boolean = anim?.isRunning == true

    private fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)
    private fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    private fun invLerp(a: Float, b: Float, v: Float): Float {
        if (a >= b) return 1f
        return ((v - a) / (b - a)).coerceIn(0f, 1f)
    }
    private fun spring(t: Float, k: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(k)

    /**
     * Item layout and animation configuration for a grid child.
     */
    private data class ItemConfig(
        val index: Int,
        val tier: Int, // 1 = 4 corners, 2 = outer perimeter, 3 = inner core
        val cornerSlot: Int = -1, // 0 = TL, 1 = TR, 2 = BL, 3 = BR (for Tier 1)
        val startX: Float,
        val startY: Float,
        val startScale: Float,
        val finalPos: PointF
    )

    /**
     * Computes the untransformed layout center of [child]'s icon graphic in [overlay] coordinates,
     * and sets [child]'s pivot to its icon graphic center so scaling lands directly on target slots.
     */
    private fun computeChildIconCenterAndSetPivot(child: View): PointF {
        child.translationX = 0f
        child.translationY = 0f
        child.scaleX = 1f
        child.scaleY = 1f

        var leftInOverlay = 0f
        var topInOverlay = 0f
        var cur: View? = child
        while (cur != null && cur !== overlay) {
            leftInOverlay += cur.left
            topInOverlay += cur.top
            cur = cur.parent as? View
        }

        val icX: Float
        val icY: Float
        if (child is BubbleTextView) {
            child.getIconBounds(tempRect)
            icX = tempRect.exactCenterX()
            icY = tempRect.exactCenterY()
        } else {
            icX = child.width / 2f
            icY = child.height / 2f
        }
        child.pivotX = icX
        child.pivotY = icY

        return PointF(leftInOverlay + icX, topInOverlay + icY)
    }

    private fun determineCornerIndices(n: Int): IntArray {
        if (n <= 0) return intArrayOf()
        if (n <= 4) {
            return IntArray(n) { it }
        }
        val totalRows = (n + 3) / 4
        val tl = 0
        val tr = minOf(3, n - 1)

        val blCandidate = (totalRows - 1) * 4
        val bl = if (blCandidate < n) blCandidate else 0

        var brCandidate = if ((totalRows - 1) * 4 + 3 < n) {
            (totalRows - 1) * 4 + 3
        } else if (totalRows >= 2 && (totalRows - 2) * 4 + 3 < n) {
            (totalRows - 2) * 4 + 3
        } else {
            n - 1
        }

        val corners = mutableListOf(tl, tr)
        if (bl !in corners) corners.add(bl)
        if (brCandidate !in corners) {
            corners.add(brCandidate)
        } else {
            for (idx in (n - 1) downTo 0) {
                if (idx !in corners) {
                    corners.add(idx)
                    break
                }
            }
        }
        return corners.toIntArray()
    }

    fun open(
        startCenters: List<PointF>,
        startScales: List<Float>,
        onEnd: Runnable
    ) {
        isClosing = false
        val n = grid.childCount
        if (n == 0) {
            onEnd.run()
            return
        }

        val card = folderTile as? CategoryCardView
        val miniClusterBitmaps = card?.miniClusterBitmaps ?: emptyList()
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Setup dimming overlay
        overlayGlass?.visibility = View.VISIBLE
        overlayGlass?.setBackgroundColor(0x22000000.toInt())
        overlayGlass?.alpha = 0f

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f

        val cornerIndices = if (hasMiniCluster) determineCornerIndices(n) else intArrayOf()
        val totalRows = (n + 3) / 4

        // Hide card's mini preview slots during flight
        if (hasMiniCluster) {
            for (s in 0..3) {
                card?.setPreviewSlotHidden(s + 3, true)
            }
        }

        // Overlay offsets relative to DragLayer
        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        // Prepare item configurations and cross-fade drawables
        val configs = ArrayList<ItemConfig>(n)
        originalDrawables.clear()

        for (i in 0 until n) {
            val child = grid.getChildAt(i)
            val finalPos = finals[i]
            val row = i / 4
            val col = i % 4

            if (hasMiniCluster) {
                val cornerPos = cornerIndices.indexOf(i)
                if (cornerPos in 0..3) {
                    // Tier 1: Corner Hero
                    val startCenter = if (3 + cornerPos < startCenters.size) {
                        PointF(startCenters[3 + cornerPos].x - ovLeft, startCenters[3 + cornerPos].y - ovTop)
                    } else {
                        miniGridCenterInOverlay
                    }
                    val startS = if (3 + cornerPos < startScales.size) startScales[3 + cornerPos] else miniScale

                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 1,
                            cornerSlot = cornerPos,
                            startX = startCenter.x,
                            startY = startCenter.y,
                            startScale = startS,
                            finalPos = finalPos
                        )
                    )

                    // Install CrossFadeBitmapDrawable if bitmaps available
                    if (child is BubbleTextView && cornerPos < miniClusterBitmaps.size) {
                        val sourceBmp = miniClusterBitmaps[cornerPos]
                        val targetFast = child.icon
                        if (targetFast != null) {
                            val targetBmp = targetFast.bitmap
                            if (sourceBmp != null && targetBmp != null) {
                                originalDrawables[child] = targetFast
                                val xfadeDrawable = CrossFadeBitmapDrawable(
                                    sourceBmp,
                                    targetBmp,
                                    targetFast.iconColor
                                )
                                child.setIcon(xfadeDrawable)
                            }
                        }
                    }
                } else if (col == 0 || col == 3 || row == 0 || row == totalRows - 1) {
                    // Tier 2: Outer Perimeter
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 2,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                } else {
                    // Tier 3: Inner Core (Trailing Cushion)
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 3,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                }
            } else {
                // Small folder without mini-cluster (N <= 4)
                val startCenter = if (i < startCenters.size) {
                    PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                } else {
                    finalPos
                }
                val startS = if (i < startScales.size) startScales[i] else 1.0f
                configs.add(
                    ItemConfig(
                        index = i,
                        tier = 1,
                        startX = startCenter.x,
                        startY = startCenter.y,
                        startScale = startS,
                        finalPos = finalPos
                    )
                )
            }
        }

        overlay.scaleX = 1f
        overlay.scaleY = 1f

        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                applyFrame(p, configs, hasMiniCluster)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        applyFrame(1f, configs, hasMiniCluster)
                        restoreOriginalDrawables()
                        onEnd.run()
                    }
                }
            })
            start()
        }
    }

    fun close(
        startCenters: List<PointF>,
        startScales: List<Float>,
        onEnd: Runnable
    ) {
        if (isClosing) return
        isClosing = true

        val n = grid.childCount
        if (n == 0) {
            restoreBackground()
            onEnd.run()
            return
        }

        val card = folderTile as? CategoryCardView
        val miniClusterBitmaps = card?.miniClusterBitmaps ?: emptyList()
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Ensure card mini slots are hidden during reverse flight
        if (hasMiniCluster) {
            for (s in 0..3) {
                card?.setPreviewSlotHidden(s + 3, true)
            }
        }

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f

        val cornerIndices = if (hasMiniCluster) determineCornerIndices(n) else intArrayOf()
        val totalRows = (n + 3) / 4

        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        val configs = ArrayList<ItemConfig>(n)
        originalDrawables.clear()

        for (i in 0 until n) {
            val child = grid.getChildAt(i)
            val finalPos = finals[i]
            val row = i / 4
            val col = i % 4

            if (hasMiniCluster) {
                val cornerPos = cornerIndices.indexOf(i)
                if (cornerPos in 0..3) {
                    val startCenter = if (3 + cornerPos < startCenters.size) {
                        PointF(startCenters[3 + cornerPos].x - ovLeft, startCenters[3 + cornerPos].y - ovTop)
                    } else {
                        miniGridCenterInOverlay
                    }
                    val startS = if (3 + cornerPos < startScales.size) startScales[3 + cornerPos] else miniScale

                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 1,
                            cornerSlot = cornerPos,
                            startX = startCenter.x,
                            startY = startCenter.y,
                            startScale = startS,
                            finalPos = finalPos
                        )
                    )

                    // Re-install CrossFadeBitmapDrawable for reverse flight
                    if (child is BubbleTextView && cornerPos < miniClusterBitmaps.size) {
                        val sourceBmp = miniClusterBitmaps[cornerPos]
                        val targetFast = child.icon
                        if (targetFast != null) {
                            val targetBmp = targetFast.bitmap
                            if (sourceBmp != null && targetBmp != null) {
                                originalDrawables[child] = targetFast
                                val xfadeDrawable = CrossFadeBitmapDrawable(
                                    sourceBmp,
                                    targetBmp,
                                    targetFast.iconColor
                                )
                                xfadeDrawable.setCrossFadeProgress(1f)
                                child.setIcon(xfadeDrawable)
                            }
                        }
                    }
                } else if (col == 0 || col == 3 || row == 0 || row == totalRows - 1) {
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 2,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                } else {
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 3,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                }
            } else {
                val startCenter = if (i < startCenters.size) {
                    PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                } else {
                    finalPos
                }
                val startS = if (i < startScales.size) startScales[i] else 1.0f
                configs.add(
                    ItemConfig(
                        index = i,
                        tier = 1,
                        startX = startCenter.x,
                        startY = startCenter.y,
                        startScale = startS,
                        finalPos = finalPos
                    )
                )
            }
        }

        overlay.scaleX = 1f
        overlay.scaleY = 1f

        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 290
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                applyFrame(p, configs, hasMiniCluster)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        restoreOriginalDrawables()
                        restoreBackground()
                        onEnd.run()
                    }
                }
            })
            start()
        }
    }

    private fun applyFrame(p: Float, configs: List<ItemConfig>, hasMiniCluster: Boolean) {
        // 1. Collapsed card stays stationary at scale 1.0 and fades smoothly
        val cardAlpha = 1.0f - invLerp(0.12f, 0.52f, p)
        folderTile?.let { ft ->
            ft.scaleX = 1f
            ft.scaleY = 1f
            ft.alpha = cardAlpha
        }
        page?.let { pg ->
            pg.scaleX = 1f
            pg.scaleY = 1f
            pg.alpha = cardAlpha
        }

        // 2. Dim Scrim Overlay
        overlayGlass?.let { og ->
            og.alpha = invLerp(0f, 0.6f, p) * 0.35f
        }

        // 3. Category Title
        val titleP = invLerp(0.42f, 0.88f, p)
        val titleAlpha = spring(titleP, 2.5f)
        val titleScale = lerp(0.88f, 1.0f, spring(titleP, 2.5f))
        title.alpha = titleAlpha
        title.scaleX = titleScale
        title.scaleY = titleScale

        // 4. Per-app choreographies
        val xfadeVal = invLerp(0.22f, 0.78f, p)

        for (cfg in configs) {
            val child = grid.getChildAt(cfg.index) ?: continue

            val curX: Float
            val curY: Float
            val curScale: Float
            val curAlpha: Float

            if (hasMiniCluster) {
                when (cfg.tier) {
                    1 -> {
                        // Tier 1: 4 Corner Heroes (k_pos = 3.6, k_scale = 4.2)
                        val pPos = spring(invLerp(0.00f, 0.86f, p), 3.6f)
                        val pScale = spring(invLerp(0.00f, 0.78f, p), 4.2f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(cfg.startScale, 1.0f, pScale)
                        curAlpha = 1.0f

                        // Update dual-icon crossfade
                        if (child is BubbleTextView) {
                            val iconDrawable = child.icon
                            if (iconDrawable is CrossFadeBitmapDrawable) {
                                iconDrawable.setCrossFadeProgress(xfadeVal)
                            }
                            val labelP = invLerp(0.12f, 0.45f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    2 -> {
                        // Tier 2: Outer Perimeter (k_pos = 2.7, k_scale = 2.5)
                        val pPos = spring(invLerp(0.06f, 0.98f, p), 2.7f)
                        val pScale = spring(invLerp(0.06f, 0.98f, p), 2.5f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(0.14f, 1.0f, pScale)
                        curAlpha = invLerp(0.04f, 0.34f, p)

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.22f, 0.68f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    else -> {
                        // Tier 3: Inner Core / Trailing Cushion (k_pos = 2.0, k_scale = 1.75)
                        val pPos = spring(invLerp(0.16f, 0.98f, p), 2.0f)
                        val pScale = spring(invLerp(0.16f, 0.98f, p), 1.75f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(0.14f, 1.0f, pScale)
                        curAlpha = invLerp(0.04f, 0.34f, p)

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.22f, 0.68f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                }
            } else {
                // Folder without mini cluster (N <= 4)
                val pPos = spring(p, 3.0f)
                curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                curScale = lerp(cfg.startScale, 1.0f, pPos)
                curAlpha = invLerp(0.0f, 0.3f, p)

                if (child is BubbleTextView) {
                    val labelP = invLerp(0.2f, 0.6f, p)
                    child.setTextAlpha(labelP)
                }
            }

            child.translationX = curX - cfg.finalPos.x
            child.translationY = curY - cfg.finalPos.y
            child.scaleX = curScale
            child.scaleY = curScale
            child.alpha = curAlpha
        }
    }

    private fun restoreOriginalDrawables() {
        for ((view, orig) in originalDrawables) {
            view.setIcon(orig)
        }
        originalDrawables.clear()
    }

    fun cancelAndRestoreBackground() {
        anim?.cancel()
        restoreOriginalDrawables()
        restoreBackground()
    }

    fun restoreBackground() {
        (folderTile as? CategoryCardView)?.clearHiddenPreviewSlots()
        page?.let { p ->
            p.scaleX = 1f
            p.scaleY = 1f
            p.alpha = 1f
        }
        folderTile?.let { ft ->
            ft.scaleX = 1f
            ft.scaleY = 1f
            ft.alpha = 1f
        }
    }
}
