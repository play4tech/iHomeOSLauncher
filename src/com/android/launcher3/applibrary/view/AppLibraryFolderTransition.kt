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
        val tier: Int, // 0 = Large Card Hero (Slots 0..2), 1 = Mini-Cluster Hero (Slots 3..6), 2 = Outer Perimeter Overflow, 3 = Inner Core Overflow
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

    private fun buildConfigs(
        n: Int,
        hasMiniCluster: Boolean,
        startCenters: List<PointF>,
        startScales: List<Float>,
        ovLeft: Float,
        ovTop: Float,
        miniGridCenterInOverlay: PointF,
        miniScale: Float,
        finals: List<PointF>,
        gridCenterX: Float,
        gridCenterY: Float,
        totalRows: Int
    ): ArrayList<ItemConfig> {
        val configs = ArrayList<ItemConfig>(n)
        for (i in 0 until n) {
            val finalPos = finals[i]
            val row = i / 4
            val col = i % 4

            if (hasMiniCluster) {
                if (i < 3) {
                    // Tier 0: Large Card Preview Hero (Slots 0..2)
                    // Starts from its exact resting slot on the card at scale 1.0f
                    val startCenter = if (i < startCenters.size) {
                        PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                    } else {
                        finalPos
                    }
                    val startS = if (i < startScales.size) startScales[i] else 1.0f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 0,
                            startX = startCenter.x,
                            startY = startCenter.y,
                            startScale = startS,
                            finalPos = finalPos
                        )
                    )
                } else if (i < 7) {
                    // Tier 1: Mini-Cluster Preview Hero (Slots 3..6)
                    // Starts from its visible sub-slot in the mini-cluster at scale miniScale (~0.45f)
                    val startCenter = if (i < startCenters.size) {
                        PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                    } else {
                        miniGridCenterInOverlay
                    }
                    val startS = if (i < startScales.size) startScales[i] else miniScale
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
                } else if (col == 0 || col == 3 || row == 0 || row == totalRows - 1) {
                    // Tier 2: Outer Perimeter Overflow Apps (N >= 8)
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
                    // Tier 3: Inner Core Overflow Apps (Trailing Cushion)
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
                        tier = 0,
                        startX = startCenter.x,
                        startY = startCenter.y,
                        startScale = startS,
                        finalPos = finalPos
                    )
                )
            }
        }
        return configs
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
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Setup frosted glass overlay
        overlayGlass?.visibility = View.VISIBLE
        (overlayGlass as? AppLibraryOverlayGlassView)?.ensureBound()
        overlayGlass?.alpha = 0f

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f
        val totalRows = (n + 3) / 4

        // Hide card's preview slots during flight so they do not duplicate
        val previewSlotsToHide = if (hasMiniCluster) minOf(n, 7) else n
        for (s in 0 until previewSlotsToHide) {
            card?.setPreviewSlotHidden(s, true)
        }

        // Overlay offsets relative to DragLayer
        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (hasMiniCluster && startCenters.size > 3) {
            PointF(startCenters[3].x - ovLeft, startCenters[3].y - ovTop)
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        val configs = buildConfigs(
            n, hasMiniCluster, startCenters, startScales, ovLeft, ovTop,
            miniGridCenterInOverlay, miniScale, finals, gridCenterX, gridCenterY, totalRows
        )

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
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Ensure card preview slots stay hidden during reverse flight
        val previewSlotsToHide = if (hasMiniCluster) minOf(n, 7) else n
        for (s in 0 until previewSlotsToHide) {
            card?.setPreviewSlotHidden(s, true)
        }

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f
        val totalRows = (n + 3) / 4

        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (hasMiniCluster && startCenters.size > 3) {
            PointF(startCenters[3].x - ovLeft, startCenters[3].y - ovTop)
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        val configs = buildConfigs(
            n, hasMiniCluster, startCenters, startScales, ovLeft, ovTop,
            miniGridCenterInOverlay, miniScale, finals, gridCenterX, gridCenterY, totalRows
        )

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

        // 2. Frosted Glass Backdrop Overlay (identical to Search backdrop)
        overlayGlass?.let { og ->
            og.alpha = invLerp(0f, 0.7f, p)
        }

        // 3. Category Title
        val titleP = invLerp(0.42f, 0.88f, p)
        val titleAlpha = spring(titleP, 2.5f)
        val titleScale = lerp(0.88f, 1.0f, spring(titleP, 2.5f))
        title.alpha = titleAlpha
        title.scaleX = titleScale
        title.scaleY = titleScale

        // 4. Per-app choreographies
        for (cfg in configs) {
            val child = grid.getChildAt(cfg.index) ?: continue

            val curX: Float
            val curY: Float
            val curScale: Float
            val curAlpha: Float

            if (hasMiniCluster) {
                when (cfg.tier) {
                    0 -> {
                        // Tier 0: Large Card Preview Hero (Slots 0..2)
                        // Smoothly moves between card position and expanded grid position at scale 1.0
                        val pPos = spring(p, 3.2f)
                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = 1.0f
                        curAlpha = 1.0f

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.08f, 0.45f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    1 -> {
                        // Tier 1: Mini-Cluster Preview Hero (Slots 3..6)
                        // Expands from miniScale to 1.0
                        val pPos = spring(invLerp(0.00f, 0.88f, p), 3.4f)
                        val pScale = spring(invLerp(0.00f, 0.82f, p), 3.8f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(cfg.startScale, 1.0f, pScale)
                        curAlpha = 1.0f

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.12f, 0.55f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    2 -> {
                        // Tier 2: Outer Perimeter Overflow Apps (k_pos = 2.7, k_scale = 2.5)
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
                        // Tier 3: Inner Core Overflow Apps / Trailing Cushion (k_pos = 2.0, k_scale = 1.75)
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
                curAlpha = 1.0f

                if (child is BubbleTextView) {
                    val labelP = invLerp(0.15f, 0.55f, p)
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
