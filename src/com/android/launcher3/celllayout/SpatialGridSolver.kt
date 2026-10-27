/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.celllayout

import android.view.View
import com.android.launcher3.CellLayout
import com.android.launcher3.util.CellAndSpan
import com.android.launcher3.widget.LauncherAppWidgetHostView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 2D Spatial Grid Reflow Solver.
 * Non-destructive solver that computes preview and drop configurations
 * against a committed snapshot of the grid (lp.cellX, lp.cellY).
 */
object SpatialGridSolver {

    data class ItemSnapshot(
        val view: View? = null,
        val id: Any? = null,
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val canReorder: Boolean = true,
        val isLockedToGrid: Boolean = true,
        val isWidget: Boolean = false
    )

    data class Solution(
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val placements: Map<View, CellAndSpan>,
        val displacedViews: List<View>,
        val isSolution: Boolean
    )

    data class GridSolution(
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val placements: Map<ItemSnapshot, CellAndSpan>,
        val displacedItems: List<ItemSnapshot>,
        val isSolution: Boolean
    )

    private data class CellPos(val x: Int, val y: Int)

    private fun intersects(
        x1: Int, y1: Int, w1: Int, h1: Int,
        x2: Int, y2: Int, w2: Int, h2: Int
    ): Boolean {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
    }

    /**
     * Solves reorder layout against committed snapshot coordinates from a CellLayout.
     */
    @JvmStatic
    fun solve(
        cellLayout: CellLayout,
        targetCellX: Int,
        targetCellY: Int,
        spanX: Int,
        spanY: Int,
        direction: IntArray,
        dragView: View?,
        isWidgetDrag: Boolean
    ): Solution {
        val countX = cellLayout.countX
        val countY = cellLayout.countY

        // Build committed snapshot of children, strictly excluding dragView (ignoreView)
        // so its vacated origin cell is treated as free for relocation.
        val container = cellLayout.shortcutsAndWidgets
        val childCount = container.childCount
        val items = ArrayList<ItemSnapshot>(childCount)

        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            if (child === dragView) continue
            val lp = child.layoutParams as? CellLayoutLayoutParams ?: continue
            val cellX = lp.cellX
            val cellY = lp.cellY
            if (cellX < 0 || cellY < 0) continue

            val isWidget = lp.cellHSpan > 1 || lp.cellVSpan > 1 || child is LauncherAppWidgetHostView
            val canReorder = lp.canReorder && lp.isLockedToGrid && (isWidgetDrag || !isWidget)

            items.add(
                ItemSnapshot(
                    view = child,
                    id = child,
                    cellX = cellX,
                    cellY = cellY,
                    spanX = lp.cellHSpan,
                    spanY = lp.cellVSpan,
                    canReorder = canReorder,
                    isLockedToGrid = lp.isLockedToGrid,
                    isWidget = isWidget
                )
            )
        }

        val gridSolution = solveGrid(countX, countY, items, targetCellX, targetCellY, spanX, spanY, direction)
        if (!gridSolution.isSolution) {
            return Solution(targetCellX, targetCellY, spanX, spanY, emptyMap(), emptyList(), false)
        }

        val placements = HashMap<View, CellAndSpan>(gridSolution.placements.size)
        for ((item, cellAndSpan) in gridSolution.placements) {
            if (item.view != null) {
                placements[item.view] = cellAndSpan
            }
        }
        val displacedViews = gridSolution.displacedItems.mapNotNull { it.view }
        return Solution(targetCellX, targetCellY, spanX, spanY, placements, displacedViews, true)
    }

    /**
     * Pure 2D Spatial Grid Reflow Solver.
     * Takes grid dimensions, a snapshot list of stationary items (with the dragged item excluded),
     * and target drop coordinates with cardinal direction vector.
     */
    @JvmStatic
    fun solveGrid(
        countX: Int,
        countY: Int,
        items: List<ItemSnapshot>,
        targetCellX: Int,
        targetCellY: Int,
        spanX: Int,
        spanY: Int,
        direction: IntArray
    ): GridSolution {
        // 1. Boundary bounds check
        if (targetCellX < 0 || targetCellY < 0 ||
            targetCellX + spanX > countX || targetCellY + spanY > countY
        ) {
            return GridSolution(targetCellX, targetCellY, spanX, spanY, emptyMap(), emptyList(), false)
        }

        // 2. Find colliding items with target placement
        val colliding = items.filter { item ->
            intersects(targetCellX, targetCellY, spanX, spanY, item.cellX, item.cellY, item.spanX, item.spanY)
        }

        // 3. Case: No collisions (Target cell is vacant)
        if (colliding.isEmpty()) {
            val placements = items.associateWith { CellAndSpan(it.cellX, it.cellY, it.spanX, it.spanY) }
            return GridSolution(targetCellX, targetCellY, spanX, spanY, placements, emptyList(), true)
        }

        // 4. Determine primary cardinal direction
        var dirX = if (direction.isNotEmpty()) direction[0] else 0
        var dirY = if (direction.size > 1) direction[1] else 0

        if (countY == 1) {
            // Hotseat: 1D horizontal row - strictly ignore vertical component
            dirY = 0
            dirX = if (dirX != 0) (if (dirX > 0) 1 else -1) else (if (targetCellX + spanX >= countX) -1 else 1)
        } else {
            val absX = abs(dirX)
            val absY = abs(dirY)
            if (absX >= absY && dirX != 0) {
                dirX = if (dirX > 0) 1 else -1
                dirY = 0
            } else if (dirY != 0) {
                dirX = 0
                dirY = if (dirY > 0) 1 else -1
            } else {
                dirX = if (targetCellX + spanX >= countX) -1 else 1
                dirY = 0
            }
        }

        // -------------------------------------------------------------
        // Stage A: Directional Block Push along cardinal vector (dirX, dirY)
        // -------------------------------------------------------------
        var stageAFailed = false
        val proposedPos = HashMap<ItemSnapshot, CellPos>(items.size)
        for (item in items) {
            proposedPos[item] = CellPos(item.cellX, item.cellY)
        }

        val queue = ArrayDeque<ItemSnapshot>()
        val moved = HashSet<ItemSnapshot>()

        for (c in colliding) {
            if (!c.canReorder) {
                stageAFailed = true
                break
            }
            val newX: Int
            val newY: Int
            if (dirX > 0) {
                newX = targetCellX + spanX
                newY = c.cellY
            } else if (dirX < 0) {
                newX = targetCellX - c.spanX
                newY = c.cellY
            } else if (dirY > 0) {
                newX = c.cellX
                newY = targetCellY + spanY
            } else {
                newX = c.cellX
                newY = targetCellY - c.spanY
            }

            if (newX < 0 || newX + c.spanX > countX || newY < 0 || newY + c.spanY > countY) {
                stageAFailed = true
                break
            }

            proposedPos[c] = CellPos(newX, newY)
            moved.add(c)
            queue.add(c)
        }

        if (!stageAFailed) {
            var iterations = 0
            val maxIterations = items.size * 3
            while (queue.isNotEmpty() && !stageAFailed) {
                iterations++
                if (iterations > maxIterations) {
                    stageAFailed = true
                    break
                }
                val current = queue.removeFirst()
                val curPos = proposedPos[current]!!

                if (intersects(curPos.x, curPos.y, current.spanX, current.spanY, targetCellX, targetCellY, spanX, spanY)) {
                    stageAFailed = true
                    break
                }

                for (other in items) {
                    if (other === current) continue
                    val otherPos = proposedPos[other]!!
                    if (intersects(curPos.x, curPos.y, current.spanX, current.spanY, otherPos.x, otherPos.y, other.spanX, other.spanY)) {
                        if (!other.canReorder) {
                            stageAFailed = true
                            break
                        }
                        val otherNewX: Int
                        val otherNewY: Int
                        if (dirX > 0) {
                            otherNewX = curPos.x + current.spanX
                            otherNewY = otherPos.y
                        } else if (dirX < 0) {
                            otherNewX = curPos.x - other.spanX
                            otherNewY = otherPos.y
                        } else if (dirY > 0) {
                            otherNewX = otherPos.x
                            otherNewY = curPos.y + current.spanY
                        } else {
                            otherNewX = otherPos.x
                            otherNewY = curPos.y - other.spanY
                        }

                        if (otherNewX < 0 || otherNewX + other.spanX > countX || otherNewY < 0 || otherNewY + other.spanY > countY) {
                            stageAFailed = true
                            break
                        }

                        proposedPos[other] = CellPos(otherNewX, otherNewY)
                        moved.add(other)
                        queue.add(other)
                    }
                }
            }
        }

        if (!stageAFailed) {
            // Validate all items do not overlap with target or each other
            for (i in items.indices) {
                val i1 = items[i]
                val p1 = proposedPos[i1]!!
                if (intersects(p1.x, p1.y, i1.spanX, i1.spanY, targetCellX, targetCellY, spanX, spanY)) {
                    stageAFailed = true
                    break
                }
                for (j in i + 1 until items.size) {
                    val i2 = items[j]
                    val p2 = proposedPos[i2]!!
                    if (intersects(p1.x, p1.y, i1.spanX, i1.spanY, p2.x, p2.y, i2.spanX, i2.spanY)) {
                        stageAFailed = true
                        break
                    }
                }
                if (stageAFailed) break
            }
        }

        if (!stageAFailed) {
            val placements = proposedPos.mapValues { (item, pos) ->
                CellAndSpan(pos.x, pos.y, item.spanX, item.spanY)
            }
            return GridSolution(targetCellX, targetCellY, spanX, spanY, placements, moved.toList(), true)
        }

        // -------------------------------------------------------------
        // Stage B: Euclidean Nearest-Vacant Fallback
        // -------------------------------------------------------------
        val occupied = Array(countX) { BooleanArray(countY) }

        // 1. Mark target drop rect as occupied
        for (cx in targetCellX until (targetCellX + spanX)) {
            for (cy in targetCellY until (targetCellY + spanY)) {
                if (cx in 0 until countX && cy in 0 until countY) {
                    occupied[cx][cy] = true
                }
            }
        }

        // 2. Unmovable items under target rect abort Stage B
        for (c in colliding) {
            if (!c.canReorder) {
                return GridSolution(targetCellX, targetCellY, spanX, spanY, emptyMap(), emptyList(), false)
            }
        }

        // 3. Mark non-colliding items at committed positions
        val nonColliding = items.filter { item ->
            !intersects(targetCellX, targetCellY, spanX, spanY, item.cellX, item.cellY, item.spanX, item.spanY)
        }

        val bPlacements = HashMap<ItemSnapshot, CellAndSpan>(items.size)
        for (nc in nonColliding) {
            bPlacements[nc] = CellAndSpan(nc.cellX, nc.cellY, nc.spanX, nc.spanY)
            for (cx in nc.cellX until (nc.cellX + nc.spanX)) {
                for (cy in nc.cellY until (nc.cellY + nc.spanY)) {
                    if (cx in 0 until countX && cy in 0 until countY) {
                        occupied[cx][cy] = true
                    }
                }
            }
        }

        // 4. Sort colliding items largest span first, then reading order
        val sortedColliding = colliding.sortedWith(
            compareByDescending<ItemSnapshot> { it.spanX * it.spanY }
                .thenBy { it.cellY * countX + it.cellX }
        )

        val bDisplaced = ArrayList<ItemSnapshot>(colliding.size)

        for (item in sortedColliding) {
            var bestX = -1
            var bestY = -1
            var bestDist = Float.MAX_VALUE
            var bestTie = Float.MAX_VALUE

            for (y in 0..(countY - item.spanY)) {
                for (x in 0..(countX - item.spanX)) {
                    var free = true
                    for (cy in y until (y + item.spanY)) {
                        for (cx in x until (x + item.spanX)) {
                            if (occupied[cx][cy]) {
                                free = false
                                break
                            }
                        }
                        if (!free) break
                    }
                    if (free) {
                        val dist = hypot((x - item.cellX).toFloat(), (y - item.cellY).toFloat())
                        val dot = (x - item.cellX) * dirX + (y - item.cellY) * dirY
                        val tie = -dot * 10f + (y * countX + x)
                        if (dist < bestDist || (dist == bestDist && tie < bestTie)) {
                            bestDist = dist
                            bestTie = tie
                            bestX = x
                            bestY = y
                        }
                    }
                }
            }

            if (bestX >= 0 && bestY >= 0) {
                for (cx in bestX until (bestX + item.spanX)) {
                    for (cy in bestY until (bestY + item.spanY)) {
                        occupied[cx][cy] = true
                    }
                }
                bPlacements[item] = CellAndSpan(bestX, bestY, item.spanX, item.spanY)
                bDisplaced.add(item)
            } else {
                return GridSolution(targetCellX, targetCellY, spanX, spanY, emptyMap(), emptyList(), false)
            }
        }

        return GridSolution(targetCellX, targetCellY, spanX, spanY, bPlacements, bDisplaced, true)
    }
}
