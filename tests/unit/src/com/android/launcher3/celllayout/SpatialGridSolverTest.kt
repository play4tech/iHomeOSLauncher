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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialGridSolverTest {

    @Test
    fun testCardinalPushRight() {
        // Grid 4x4. Item A at (1, 0). (2, 0) is vacant.
        val itemA = SpatialGridSolver.ItemSnapshot(
            id = "A", cellX = 1, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val items = listOf(itemA)
        // Drag to (1, 0) pushing rightwards
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(1, 0)
        )

        assertTrue("Stage A should succeed for right push", solution.isSolution)
        val posA = solution.placements[itemA]!!
        assertEquals("Item A must be pushed to x=2", 2, posA.cellX)
        assertEquals("Item A y must remain 0", 0, posA.cellY)
    }

    @Test
    fun testCardinalPushLeft() {
        // Grid 4x4. Item A at (1, 0). (0, 0) is vacant.
        val itemA = SpatialGridSolver.ItemSnapshot(
            id = "A", cellX = 1, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val items = listOf(itemA)
        // Drag to (1, 0) pushing leftwards
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(-1, 0)
        )

        assertTrue("Stage A should succeed for left push", solution.isSolution)
        val posA = solution.placements[itemA]!!
        assertEquals("Item A must be pushed to x=0", 0, posA.cellX)
        assertEquals("Item A y must remain 0", 0, posA.cellY)
    }

    @Test
    fun testCardinalPushDown() {
        // Grid 4x4. Item A at (0, 1). (0, 2) is vacant.
        val itemA = SpatialGridSolver.ItemSnapshot(
            id = "A", cellX = 0, cellY = 1, spanX = 1, spanY = 1, canReorder = true
        )
        val items = listOf(itemA)
        // Drag to (0, 1) pushing downwards
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 0, targetCellY = 1, spanX = 1, spanY = 1,
            direction = intArrayOf(0, 1)
        )

        assertTrue("Stage A should succeed for down push", solution.isSolution)
        val posA = solution.placements[itemA]!!
        assertEquals("Item A x must remain 0", 0, posA.cellX)
        assertEquals("Item A must be pushed to y=2", 2, posA.cellY)
    }

    @Test
    fun testCardinalPushUp() {
        // Grid 4x4. Item A at (0, 1). (0, 0) is vacant.
        val itemA = SpatialGridSolver.ItemSnapshot(
            id = "A", cellX = 0, cellY = 1, spanX = 1, spanY = 1, canReorder = true
        )
        val items = listOf(itemA)
        // Drag to (0, 1) pushing upwards
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 0, targetCellY = 1, spanX = 1, spanY = 1,
            direction = intArrayOf(0, -1)
        )

        assertTrue("Stage A should succeed for up push", solution.isSolution)
        val posA = solution.placements[itemA]!!
        assertEquals("Item A x must remain 0", 0, posA.cellX)
        assertEquals("Item A must be pushed to y=0", 0, posA.cellY)
    }

    @Test
    fun test100PercentFullGridSwap() {
        // Grid 4x4 has 16 cells.
        // User drags icon originally at (0, 0) to (1, 0).
        // The dragged item is excluded, leaving 15 items occupying all cells except (0, 0).
        val items = ArrayList<SpatialGridSolver.ItemSnapshot>()
        var collidingItem: SpatialGridSolver.ItemSnapshot? = null

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                if (x == 0 && y == 0) continue // Excluded origin of dragged item
                val item = SpatialGridSolver.ItemSnapshot(
                    id = "item_${x}_$y", cellX = x, cellY = y, spanX = 1, spanY = 1, canReorder = true
                )
                items.add(item)
                if (x == 1 && y == 0) {
                    collidingItem = item
                }
            }
        }
        assertEquals("Grid must have exactly 15 stationary items", 15, items.size)

        // Drag to (1, 0) with direction [1, 0]
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(1, 0)
        )

        assertTrue("Solver must succeed on 100% full page by relocating into vacated cell", solution.isSolution)
        val posColliding = solution.placements[collidingItem!!]!!
        assertEquals("Colliding item must relocate to vacated origin (0, 0)", 0, posColliding.cellX)
        assertEquals("Colliding item must relocate to vacated origin (0, 0)", 0, posColliding.cellY)
    }

    @Test
    fun testDirectDropOnPinnedItemRejects() {
        // Grid 4x4. Pinned item at (1, 0).
        val pinned = SpatialGridSolver.ItemSnapshot(
            id = "pinned", cellX = 1, cellY = 0, spanX = 1, spanY = 1, canReorder = false
        )
        val items = listOf(pinned)

        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(1, 0)
        )

        assertFalse("Dropping directly onto a pinned item must be rejected", solution.isSolution)
    }

    @Test
    fun testPushHitsPinnedItemRoutesAroundWithoutMovingPinned() {
        // Grid 4x4.
        // Movable item M at (1, 0).
        // Pinned item P at (2, 0) (blocking the rightward push).
        // Vacant cell at (1, 1).
        val movable = SpatialGridSolver.ItemSnapshot(
            id = "M", cellX = 1, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val pinned = SpatialGridSolver.ItemSnapshot(
            id = "P", cellX = 2, cellY = 0, spanX = 1, spanY = 1, canReorder = false
        )
        val items = listOf(movable, pinned)

        // Drag to (1, 0) pushing right [1, 0]
        val solution = SpatialGridSolver.solveGrid(
            countX = 4, countY = 4, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(1, 0)
        )

        assertTrue("Stage B must find nearest vacant space routing around pinned item", solution.isSolution)
        val posP = solution.placements[pinned]!!
        assertEquals("Pinned item P must NOT move (x=2)", 2, posP.cellX)
        assertEquals("Pinned item P must NOT move (y=0)", 0, posP.cellY)

        val posM = solution.placements[movable]!!
        // M should be relocated to a vacant cell (such as (0, 0) or (1, 1))
        assertTrue("Movable item M must be relocated to a vacant cell",
            (posM.cellX == 0 && posM.cellY == 0) || (posM.cellX == 1 && posM.cellY == 1))
    }

    @Test
    fun testHotseatHorizontalConstraint() {
        // Hotseat 5x1 (countX = 5, countY = 1).
        // Items at (0, 0), (1, 0), (2, 0). Vacancies at (3, 0), (4, 0).
        val itemA = SpatialGridSolver.ItemSnapshot(
            id = "A", cellX = 0, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val itemB = SpatialGridSolver.ItemSnapshot(
            id = "B", cellX = 1, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val itemC = SpatialGridSolver.ItemSnapshot(
            id = "C", cellX = 2, cellY = 0, spanX = 1, spanY = 1, canReorder = true
        )
        val items = listOf(itemA, itemB, itemC)

        // Drag target (1, 0) with a vertical direction vector [0, 1]
        val solution = SpatialGridSolver.solveGrid(
            countX = 5, countY = 1, items = items,
            targetCellX = 1, targetCellY = 0, spanX = 1, spanY = 1,
            direction = intArrayOf(0, 1) // vertical direction
        )

        assertTrue("Hotseat reorder must succeed horizontally", solution.isSolution)
        for ((_, pos) in solution.placements) {
            assertEquals("All hotseat items must stay in row y=0", 0, pos.cellY)
        }
        val posB = solution.placements[itemB]!!
        val posC = solution.placements[itemC]!!
        assertEquals("Item B should be pushed right to x=2", 2, posB.cellX)
        assertEquals("Item C should be pushed right to x=3", 3, posC.cellX)
    }
}
