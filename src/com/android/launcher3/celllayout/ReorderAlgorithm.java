/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static com.android.launcher3.CellLayout.MODE_ON_DROP;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Contains the logic of a reorder.
 *
 * The content of this class was extracted from {@link CellLayout} and should mimic the exact
 * same behaviour.
 */
public class ReorderAlgorithm {

    private static final boolean DEBUG = false;

    CellLayout mCellLayout;
    private boolean mIsWidget;

    public ReorderAlgorithm(CellLayout cellLayout) {
        mCellLayout = cellLayout;
    }

    private final ArrayList<View> mIntersectingViews = new ArrayList<>();

    private final Rect mOccupiedRect = new Rect();

    /**
     * This method differs from closestEmptySpaceReorder and dropInPlaceSolution because this method
     * will move items around and will change the shape of the item if possible to try to find a
     * solution.
     * <p>
     * When changing the size of the widget this method will try first subtracting -1 in the x
     * dimension and then subtracting -1 in the y dimension until finding a possible solution or
     * until it no longer can reduce the span.
     * @param decX     whether it will decrease the horizontal or vertical span if it can't find a
     *                 solution for the current span.
     * @return the same solution variable
     */
    public ItemConfiguration findReorderSolution(ReorderParameters reorderParameters,
            boolean decX) {
        return findReorderSolution(reorderParameters, mCellLayout.mDirectionVector, decX);
    }

    /**
     * This method differs from closestEmptySpaceReorder and dropInPlaceSolution because this method
     * will move items around and will change the shape of the item if possible to try to find a
     * solution.
     * <p>
     * When changing the size of the widget this method will try first subtracting -1 in the x
     * dimension and then subtracting -1 in the y dimension until finding a possible solution or
     * until it no longer can reduce the span.
     * @param direction Direction to attempt to push items if needed
     * @param decX     whether it will decrease the horizontal or vertical span if it can't find a
     *                 solution for the current span.
     * @return the same solution variable
     */
    public ItemConfiguration findReorderSolution(ReorderParameters reorderParameters,
            int[] direction, boolean decX) {
        mIsWidget = Workspace.isWidget
                || reorderParameters.getSpanX() > 1
                || reorderParameters.getSpanY() > 1
                || (reorderParameters.getDragView() instanceof LauncherAppWidgetHostView);
        return findReorderSolutionRecursive(reorderParameters.getPixelX(),
                reorderParameters.getPixelY(), reorderParameters.getMinSpanX(),
                reorderParameters.getMinSpanY(), reorderParameters.getSpanX(),
                reorderParameters.getSpanY(), direction,
                reorderParameters.getDragView(), decX, reorderParameters.getSolution());
    }

    private ItemConfiguration findReorderSolutionRecursive(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX,
            ItemConfiguration solution) {
        // Copy the current state into the solution. This solution will be manipulated as necessary.
        mCellLayout.copyCurrentStateToSolution(solution);
        solution.overflowViews.clear();
        // Copy the current occupied array into the temporary occupied array. This array will be
        // manipulated as necessary to find a solution.
        mCellLayout.getOccupied().copyTo(mCellLayout.mTmpOccupied);

        // We find the nearest cell into which we would place the dragged item, assuming there's
        // nothing in its way.
        int[] result = new int[2];
        result = mCellLayout.findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);

        boolean success;
        // First we try the exact nearest position of the item being dragged,
        // we will then want to try to move this around to other neighbouring positions
        boolean isWidgetDrag = isWidget() || spanX > 1 || spanY > 1
                || (dragView instanceof LauncherAppWidgetHostView);

        if (!isWidgetDrag && intersectingViewsExists(result[0], result[1], spanX, spanY, direction, dragView, solution)) {
            int[] nearestResult = new int[2];
            mCellLayout.markCellsAsOccupiedForView(dragView);
            boolean hasVacant = mCellLayout.findCellForSpan(nearestResult, spanX, spanY);
            if (hasVacant && (nearestResult[1] < result[1]
                    || (nearestResult[1] == result[1] && nearestResult[0] < result[0]))) {
                int candidateX = nearestResult[0] == 0 ? mCellLayout.getCountX() - 1 : nearestResult[0] - 1;
                int candidateY = nearestResult[0] == 0 ? nearestResult[1] - 1 : nearestResult[1];
                if (candidateX >= 0 && candidateY >= 0) {
                    result[0] = candidateX;
                    result[1] = candidateY;
                } else {
                    result[0] = nearestResult[0];
                    result[1] = nearestResult[1];
                }
            }
            mCellLayout.markCellsAsUnoccupiedForView(dragView);
            if (result[0] >= 0 && result[1] >= 0) {
                intersectingViewsExists(result[0], result[1], spanX, spanY, direction, dragView, solution);
            }
            mCellLayout.markCellsAsUnoccupiedForView(dragView);
        } else if (isWidgetDrag) {
            if (result[0] >= 0 && reflowIconsAroundWidget(result[0], result[1], spanX, spanY,
                    dragView, solution)) {
                mIntersectingViews.clear();          // rearrangementExists() -> true
                solution.intersectingViews = new ArrayList<>();
                solution.isSolution = true;
                solution.cellX = result[0];
                solution.cellY = result[1];
                solution.spanX = spanX;
                solution.spanY = spanY;
                return solution;
            }
            // Legacy rigid push (widget onto widget etc.)
            intersectingViewsExists(result[0], result[1], spanX, spanY, direction, dragView, solution);
        }
        success = rearrangementExists(direction, dragView, solution);


        if (!success) {
            // We try shrinking the widget down to size in an alternating pattern, shrink 1 in
            // x, then 1 in y etc.
            if (spanX > minSpanX && (minSpanY == spanY || decX)) {
                return findReorderSolutionRecursive(pixelX, pixelY, minSpanX, minSpanY, spanX - 1,
                        spanY, direction, dragView, false, solution);
            } else if (spanY > minSpanY) {
                return findReorderSolutionRecursive(pixelX, pixelY, minSpanX, minSpanY, spanX,
                        spanY - 1, direction, dragView, true, solution);
            }
            solution.isSolution = false;
        } else {
            solution.isSolution = true;
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = spanX;
            solution.spanY = spanY;
        }
        return solution;
    }

    /**
     * iOS-style placement of a widget at (cellX, cellY, spanX, spanY).
     * Icons flow in reading order around widgets / non-reorderable items. Only icons from the first
     * displaced one onward move. Icons that run off the end of the grid go to solution.overflowViews.
     *
     * @return false (solution untouched) if the rect is out of bounds or collides with another
     *         widget / fixed item. The caller then falls back to the legacy push.
     */
    private boolean reflowIconsAroundWidget(int cellX, int cellY, int spanX, int spanY,
            View dragView, ItemConfiguration solution) {
        final int countX = mCellLayout.getCountX();
        final int countY = mCellLayout.getCountY();
        if (cellX < 0 || cellY < 0 || cellX + spanX > countX || cellY + spanY > countY) {
            return false;
        }

        final boolean[][] blocked = new boolean[countX][countY];
        for (int x = cellX; x < cellX + spanX; x++) {
            for (int y = cellY; y < cellY + spanY; y++) blocked[x][y] = true;
        }

        final ArrayList<View> icons = new ArrayList<>();
        for (Map.Entry<View, CellAndSpan> e : solution.map.entrySet()) {
            View child = e.getKey();
            if (child == dragView) continue;
            CellAndSpan c = e.getValue();
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            boolean fixed = c.spanX > 1 || c.spanY > 1 || !lp.canReorder
                    || child instanceof LauncherAppWidgetHostView;
            if (fixed) {
                for (int x = c.cellX; x < c.cellX + c.spanX; x++) {
                    for (int y = c.cellY; y < c.cellY + c.spanY; y++) {
                        if (x < 0 || y < 0 || x >= countX || y >= countY) continue;
                        if (blocked[x][y]) return false;   // collides with the dropped widget
                        blocked[x][y] = true;
                    }
                }
            } else {
                icons.add(child);
            }
        }

        icons.sort(Comparator
                .comparingInt((View v) -> solution.map.get(v).cellY)
                .thenComparingInt(v -> solution.map.get(v).cellX));

        // First icon sitting under the widget.
        int first = -1;
        for (int i = 0; i < icons.size(); i++) {
            CellAndSpan c = solution.map.get(icons.get(i));
            if (blocked[c.cellX][c.cellY]) { first = i; break; }
        }
        if (first < 0) {
            updateTmpOccupiedForReflow(cellX, cellY, spanX, spanY, dragView, solution);
            return true;   // nothing displaced
        }

        final int total = countX * countY;
        CellAndSpan start = solution.map.get(icons.get(first));
        int cursor = start.cellY * countX + start.cellX;

        for (int i = first; i < icons.size(); i++) {
            View icon = icons.get(i);
            while (cursor < total && blocked[cursor % countX][cursor / countX]) cursor++;
            if (cursor >= total) {
                // Out of room: this and every following icon overflow, order preserved.
                for (int j = i; j < icons.size(); j++) {
                    View o = icons.get(j);
                    solution.map.remove(o);
                    solution.overflowViews.add(o);
                }
                break;
            }
            CellAndSpan c = solution.map.get(icon);
            c.cellX = cursor % countX;
            c.cellY = cursor / countX;
            cursor++;
        }
        updateTmpOccupiedForReflow(cellX, cellY, spanX, spanY, dragView, solution);
        return true;
    }

    private void updateTmpOccupiedForReflow(int cellX, int cellY, int spanX, int spanY,
            View dragView, ItemConfiguration solution) {
        mCellLayout.mTmpOccupied.clear();
        for (Map.Entry<View, CellAndSpan> entry : solution.map.entrySet()) {
            if (entry.getKey() != dragView) {
                CellAndSpan c = entry.getValue();
                mCellLayout.mTmpOccupied.markCells(c.cellX, c.cellY, c.spanX, c.spanY, true);
            }
        }
        mCellLayout.mTmpOccupied.markCells(cellX, cellY, spanX, spanY, true);
    }

    private boolean intersectingViewsExists(int cellX, int cellY, int spanX, int spanY, int[] direction,
            View ignoreView, ItemConfiguration solution) {
        // Return early if get invalid cell positions
        if (cellX < 0 || cellY < 0) return false;
        mIntersectingViews.clear();
        mOccupiedRect.set(cellX, cellY, cellX + spanX, cellY + spanY);

        // Mark the desired location of the view currently being dragged.
        if (ignoreView != null) {
            CellAndSpan c = solution.map.get(ignoreView);
            if (c != null) {
                c.cellX = cellX;
                c.cellY = cellY;
            }
        }
        Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
        Rect r1 = new Rect();
        // The views need to be sorted so that the results are deterministic on the views positions
        // and not by the views hash which is "random".
        // The views are sorted twice, once for the X position and a second time for the Y position
        // to ensure same order everytime.
        Comparator comparator = Comparator.comparing(
                view -> ((CellLayoutLayoutParams) ((View) view).getLayoutParams()).getCellX()
        ).thenComparing(
                view -> ((CellLayoutLayoutParams) ((View) view).getLayoutParams()).getCellY()
        );
        List<View> views = solution.map.keySet().stream().sorted(comparator).toList();
        for (View child : views) {
            if (child == ignoreView) continue;
            CellAndSpan c = solution.map.get(child);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY);
            if (Rect.intersects(r0, r1)) {
                if (!lp.canReorder) {
                    return false;
                }
                if (!isWidget() && child instanceof LauncherAppWidgetHostView) {
                    return false;
                }
                mIntersectingViews.add(child);
            }
        }

        solution.intersectingViews = new ArrayList<>(mIntersectingViews);

        return !mIntersectingViews.isEmpty();

    }

    public boolean rearrangementExists(int[] direction, View ignoreView, ItemConfiguration solution) {
        if (mIntersectingViews.isEmpty()) return true;

        final Map<View, int[]> snap = snapshot(solution);

        if (mIntersectingViews.size() == 1 || isWidget()) {
            if (attemptPushInDirection(mIntersectingViews, mOccupiedRect, direction, ignoreView,
                    solution)) {
                return true;
            }
            restore(solution, snap);
        }
        if (addViewsToTempLocation(mIntersectingViews, mOccupiedRect, direction, ignoreView,
                solution)) {
            return true;
        }
        restore(solution, snap);

        for (View v : mIntersectingViews) {
            if (!addViewToTempLocation(v, mOccupiedRect, direction, solution)) {
                restore(solution, snap);
                return false;
            }
        }
        return true;
    }

    private static Map<View, int[]> snapshot(ItemConfiguration s) {
        Map<View, int[]> m = new HashMap<>();
        for (Map.Entry<View, CellAndSpan> e : s.map.entrySet()) {
            CellAndSpan c = e.getValue();
            m.put(e.getKey(), new int[]{c.cellX, c.cellY, c.spanX, c.spanY});
        }
        return m;
    }

    private static void restore(ItemConfiguration s, Map<View, int[]> snap) {
        for (Map.Entry<View, int[]> e : snap.entrySet()) {
            CellAndSpan c = s.map.get(e.getKey());
            if (c == null) continue;
            int[] v = e.getValue();
            c.cellX = v[0]; c.cellY = v[1]; c.spanX = v[2]; c.spanY = v[3];
        }
    }


    private boolean addViewToTempLocation(View v, Rect rectOccupiedByPotentialDrop, int[] direction,
            ItemConfiguration currentState) {
        CellAndSpan c = currentState.map.get(v);
        boolean success = false;
        mCellLayout.mTmpOccupied.markCells(c, false);
        mCellLayout.mTmpOccupied.markCells(rectOccupiedByPotentialDrop, true);

        int[] tmpLocation = findNearestArea(c.cellX, c.cellY, c.spanX, c.spanY, direction,
                mCellLayout.mTmpOccupied.cells, null, new int[2]);

        if (tmpLocation[0] < 0 || tmpLocation[1] < 0) {
            int[] vacantCell = new int[2];
            if (mCellLayout.mTmpOccupied.findVacantCell(vacantCell, c.spanX, c.spanY)) {
                tmpLocation[0] = vacantCell[0];
                tmpLocation[1] = vacantCell[1];
            }
        }

        if (tmpLocation[0] >= 0 && tmpLocation[1] >= 0) {
            c.cellX = tmpLocation[0];
            c.cellY = tmpLocation[1];
            success = true;
        }
        mCellLayout.mTmpOccupied.markCells(c, true);
        return success;
    }

    private boolean pushViewsToTempLocation(ArrayList<View> views, Rect rectOccupiedByPotentialDrop,
            int[] direction, View dragView, ItemConfiguration currentState) {

        int countX = mCellLayout.getCountX();
        int countY = mCellLayout.getCountY();

        ViewCluster cluster = new ViewCluster(mCellLayout, views, currentState);
        Rect clusterRect = cluster.getBoundingRect();
        int whichEdge;
        int pushDistance;
        boolean fail = false;

        // Determine the edge of the cluster that will be leading the push and how far
        // the cluster must be shifted.
        if (direction[0] < 0) {
            whichEdge = ViewCluster.LEFT;
            pushDistance = clusterRect.right - rectOccupiedByPotentialDrop.left;
        } else if (direction[0] > 0) {
            whichEdge = ViewCluster.RIGHT;
            pushDistance = rectOccupiedByPotentialDrop.right - clusterRect.left;
        } else if (direction[1] < 0) {
            whichEdge = ViewCluster.TOP;
            pushDistance = clusterRect.bottom - rectOccupiedByPotentialDrop.top;
        } else {
            whichEdge = ViewCluster.BOTTOM;
            pushDistance = rectOccupiedByPotentialDrop.bottom - clusterRect.top;
        }

        // Break early for invalid push distance.
        if (pushDistance <= 0) {
            return false;
        }

        // Mark the occupied state as false for the group of views we want to move.
        for (View v : views) {
            CellAndSpan c = currentState.map.get(v);
            mCellLayout.mTmpOccupied.markCells(c, false);
        }

        // We save the current configuration -- if we fail to find a solution we will revert
        // to the initial state. The process of finding a solution modifies the configuration
        // in place, hence the need for revert in the failure case.
        currentState.save();

        // The pushing algorithm is simplified by considering the views in the order in which
        // they would be pushed by the cluster. For example, if the cluster is leading with its
        // left edge, we consider sort the views by their right edge, from right to left.
        cluster.sortConfigurationForEdgePush(whichEdge);

        if (!isWidget()) {
            if (whichEdge == ViewCluster.LEFT) {
                //order by row desc
                currentState.sortedViews.sort((lhs, rhs) -> {
                    CellLayoutLayoutParams lplhs = (CellLayoutLayoutParams) lhs.getLayoutParams();
                    CellLayoutLayoutParams lprhs = (CellLayoutLayoutParams) rhs.getLayoutParams();
                    if (lprhs.getCellY() + lprhs.cellVSpan == lplhs.getCellY() + lplhs.cellVSpan) {
                        return lprhs.getCellX() - lplhs.getCellX();
                    }
                    return (lprhs.getCellY() + lprhs.cellVSpan) - (lplhs.getCellY() + lplhs.cellVSpan);
                });

            } else if (whichEdge == ViewCluster.RIGHT) {
                //order by row asc
                currentState.sortedViews.sort((lhs, rhs) -> {
                    CellLayoutLayoutParams lplhs = (CellLayoutLayoutParams) lhs.getLayoutParams();
                    CellLayoutLayoutParams lprhs = (CellLayoutLayoutParams) rhs.getLayoutParams();
                    if (lplhs.getCellY() + lplhs.cellVSpan == lprhs.getCellY() + lprhs.cellVSpan) {
                        return lplhs.getCellX() - lprhs.getCellX();
                    }
                    return (lplhs.getCellY() + lplhs.cellVSpan) - (lprhs.getCellY() + lprhs.cellVSpan);
                });
            }

            if (cluster.views.size() == 1) {
                View v = cluster.views.get(0);
                CellAndSpan c = currentState.map.get(v);
                mCellLayout.pushIconByRow(c, countX, countY, whichEdge);
            }
        }

        while (pushDistance > 0 && !fail) {
            for (View v : currentState.sortedViews) {
                // For each view that isn't in the cluster, we see if the leading edge of the
                // cluster is contacting the edge of that view. If so, we add that view to the
                // cluster.
                if (!isWidget()) {
                    if (v instanceof LauncherAppWidgetHostView) {
                        continue;
                    }
                }
                if (!cluster.views.contains(v) && v != dragView) {
                    if (cluster.isViewTouchingEdge(v, whichEdge)) {
                        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                        if (!lp.canReorder) {
                            // The push solution includes the all apps button, this is not viable.
                            fail = true;
                            break;
                        }
                        cluster.addView(v);
                        CellAndSpan c = currentState.map.get(v);

                        // Adding view to cluster, mark it as not occupied.
                        mCellLayout.mTmpOccupied.markCells(c, false);
                        if (!isWidget()) {
                            mCellLayout.pushIconByRow(c, countX, countY, whichEdge);
                        }
                    }
                }
            }
            pushDistance--;

            // The cluster has been completed, now we move the whole thing over in the appropriate
            // direction.
            cluster.shift(whichEdge, 1);
        }

        boolean foundSolution = false;
        clusterRect = cluster.getBoundingRect();

        // Due to the nature of the algorithm, the only check required to verify a valid solution
        // is to ensure that completed shifted cluster lies completely within the cell layout.
        if (!fail && clusterRect.left >= 0 && clusterRect.right <= mCellLayout.getCountX()
                && clusterRect.top >= 0 && clusterRect.bottom <= mCellLayout.getCountY()) {
            foundSolution = true;
        } else {
            currentState.restore();
        }

        // In either case, we set the occupied array as marked for the location of the views
        for (View v : cluster.views) {
            CellAndSpan c = currentState.map.get(v);
            mCellLayout.mTmpOccupied.markCells(c, true);
        }

        return foundSolution;
    }

    private void revertDir(int[] direction) {
        direction[0] *= -1;
        direction[1] *= -1;
    }

    // This method tries to find a reordering solution which satisfies the push mechanic by trying
    // to push items in each of the cardinal directions, in an order based on the direction vector
    // passed.
    private boolean attemptPushInDirection(ArrayList<View> intersectingViews, Rect occupied,
                                           int[] direction, View ignoreView, ItemConfiguration solution) {
        if ((Math.abs(direction[0]) + Math.abs(direction[1])) > 1) {
            // If the direction vector has two non-zero components, we try pushing
            // separately in each of the components.
            int temp = direction[1];
            direction[1] = 0;

            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            direction[1] = temp;
            temp = direction[0];
            direction[0] = 0;

            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Revert the direction
            direction[0] = temp;

            // Now we try pushing in each component of the opposite direction
            direction[0] *= -1;
            direction[1] *= -1;
            temp = direction[1];
            direction[1] = 0;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }

            direction[1] = temp;
            temp = direction[0];
            direction[0] = 0;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // revert the direction
            direction[0] = temp;
            direction[0] *= -1;
            direction[1] *= -1;

        } else {
            // If the direction vector has a single non-zero component, we push first in the
            // direction of the vector
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Then we try the opposite direction
            direction[0] *= -1;
            direction[1] *= -1;
            if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                    ignoreView, solution)) {
                return true;
            }
            // Switch the direction back
            direction[0] *= -1;
            direction[1] *= -1;

            // If we have failed to find a push solution with the above, then we try
            // to find a solution by pushing along the perpendicular axis.

            // Swap the components
            if (isWidget()) {
                int temp = direction[1];
                direction[1] = direction[0];
                direction[0] = temp;
                if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                        ignoreView, solution)) {
                    return true;
                }

                // Then we try the opposite direction
                direction[0] *= -1;
                direction[1] *= -1;
                if (pushViewsToTempLocation(intersectingViews, occupied, direction,
                        ignoreView, solution)) {
                    return true;
                }
                // Switch the direction back
                direction[0] *= -1;
                direction[1] *= -1;

                // Swap the components back
                temp = direction[1];
                direction[1] = direction[0];
                direction[0] = temp;
            }
        }
        return false;
    }

    private boolean addViewsToTempLocation(ArrayList<View> views, Rect rectOccupiedByPotentialDrop,
            int[] direction, View dragView, ItemConfiguration currentState) {
        if (views.isEmpty()) return true;

        boolean success = false;
        Rect boundingRect = new Rect();
        // We construct a rect which represents the entire group of views passed in
        currentState.getBoundingRectForViews(views, boundingRect);

        // Mark the occupied state as false for the group of views we want to move.
        for (View v : views) {
            CellAndSpan c = currentState.map.get(v);
            mCellLayout.mTmpOccupied.markCells(c, false);
        }

        GridOccupancy blockOccupied = new GridOccupancy(boundingRect.width(),
                boundingRect.height());
        int top = boundingRect.top;
        int left = boundingRect.left;
        // We mark more precisely which parts of the bounding rect are truly occupied, allowing
        // for interlocking.
        for (View v : views) {
            CellAndSpan c = currentState.map.get(v);
            blockOccupied.markCells(c.cellX - left, c.cellY - top, c.spanX, c.spanY, true);
        }

        mCellLayout.mTmpOccupied.markCells(rectOccupiedByPotentialDrop, true);

        int[] tmpLocation = findNearestArea(boundingRect.left, boundingRect.top,
                boundingRect.width(), boundingRect.height(), direction,
                mCellLayout.mTmpOccupied.cells, blockOccupied.cells, new int[2]);

        // If we successfully found a location by pushing the block of views, we commit it
        if (tmpLocation[0] >= 0 && tmpLocation[1] >= 0) {
            int deltaX = tmpLocation[0] - boundingRect.left;
            int deltaY = tmpLocation[1] - boundingRect.top;
            for (View v : views) {
                CellAndSpan c = currentState.map.get(v);
                c.cellX += deltaX;
                c.cellY += deltaY;
            }
            success = true;
        }

        // In either case, we set the occupied array as marked for the location of the views
        for (View v : views) {
            CellAndSpan c = currentState.map.get(v);
            mCellLayout.mTmpOccupied.markCells(c, true);
        }
        return success;
    }

    /**
     * Returns a "reorder" if there is empty space without rearranging anything.
     *
     * @return the configuration that represents the found reorder
     */
    public ItemConfiguration dropInPlaceSolution(ReorderParameters reorderParameters) {
        int[] result = mCellLayout.findNearestAreaIgnoreOccupied(reorderParameters.getPixelX(),
                reorderParameters.getPixelY(), reorderParameters.getSpanX(),
                reorderParameters.getSpanY(), new int[2]);
        ItemConfiguration solution = new ItemConfiguration();
        mCellLayout.copyCurrentStateToSolution(solution);

        solution.isSolution = !isConfigurationRegionOccupied(
                new Rect(result[0], result[1], result[0] + reorderParameters.getSpanX(),
                        result[1] + reorderParameters.getSpanY()), solution,
                reorderParameters.getDragView());
        if (!solution.isSolution) {
            return solution;
        }
        solution.cellX = result[0];
        solution.cellY = result[1];
        solution.spanX = reorderParameters.getSpanX();
        solution.spanY = reorderParameters.getSpanY();
        return solution;
    }

    private boolean isConfigurationRegionOccupied(Rect region, ItemConfiguration configuration,
            View ignoreView) {
        for (Map.Entry<View, CellAndSpan> entry : configuration.map.entrySet()) {
            if (entry.getKey() == ignoreView) continue;
            CellAndSpan cs = entry.getValue();
            if (Math.max(region.left, cs.cellX) < Math.min(region.right, cs.cellX + cs.spanX)
                    && Math.max(region.top, cs.cellY) < Math.min(region.bottom, cs.cellY + cs.spanY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a "reorder" where we simply drop the item in the closest empty space, without moving
     * any other item in the way.
     *
     * @return the configuration that represents the found reorder
     */
    public ItemConfiguration closestEmptySpaceReorder(ReorderParameters reorderParameters) {
        ItemConfiguration solution = new ItemConfiguration();
        int[] result = new int[2];
        int[] resultSpan = new int[2];
        mCellLayout.findNearestVacantArea(reorderParameters.getPixelX(),
                reorderParameters.getPixelY(), reorderParameters.getMinSpanX(),
                reorderParameters.getMinSpanY(), reorderParameters.getSpanX(),
                reorderParameters.getSpanY(), result, resultSpan);
        if (result[0] >= 0 && result[1] >= 0) {
            mCellLayout.copyCurrentStateToSolution(solution);
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = resultSpan[0];
            solution.spanY = resultSpan[1];
            solution.isSolution = true;
        } else {
            solution.isSolution = false;
        }
        return solution;
    }

    /**
     * When the user drags an Item in the workspace sometimes we need to move the items already in
     * the workspace to make space for the new item, this function return a solution for that
     * reorder.
     *
     * @return returns a solution for the given parameters, the solution contains all the icons and
     * the locations they should be in the given solution.
     */
    public ItemConfiguration calculateReorder(ReorderParameters reorderParameters) {
        mIsWidget = Workspace.isWidget
                || reorderParameters.getSpanX() > 1
                || reorderParameters.getSpanY() > 1
                || (reorderParameters.getDragView() instanceof LauncherAppWidgetHostView);
        getDirectionVectorForDrop(reorderParameters, mCellLayout.mDirectionVector);

        if (DEBUG) {
            android.util.Log.d("ReorderDebug", "calculateReorder START pixelXY=(" +
                    reorderParameters.getPixelX() + "," + reorderParameters.getPixelY() + ")" +
                    " span=(" + reorderParameters.getSpanX() + "," + reorderParameters.getSpanY() + ")");

            StringBuilder occupiedLog = new StringBuilder("mOccupied grid:\n");
            for (int row = 0; row < mCellLayout.getCountY(); row++) {
                occupiedLog.append("  row").append(row).append(": ");
                for (int col = 0; col < mCellLayout.getCountX(); col++) {
                    occupiedLog.append(mCellLayout.isOccupied(col, row) ? "X" : ".").append(" ");
                }
                occupiedLog.append("\n");
            }
            android.util.Log.d("ReorderDebug", occupiedLog.toString());
        }

        if (!isWidget() && !mCellLayout.findCellForSpan(null, reorderParameters.getMinSpanX(), reorderParameters.getMinSpanY())) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "calculateReorder: NO EMPTY CELL FOUND -> returning no-solution");
            }
            ItemConfiguration solution = new ItemConfiguration();
            solution.cellX = solution.cellY = solution.spanX = solution.spanY = -1;
            solution.isSolution = false;
            return solution;
        }

        if (!isWidget()) {
            mCellLayout.mDirectionVector[0] = -1;
            mCellLayout.mDirectionVector[1] = 0;
        }

        ItemConfiguration dropInPlaceSolution = dropInPlaceSolution(reorderParameters);
        if (DEBUG) {
            android.util.Log.d("ReorderDebug", "dropInPlaceSolution: isSolution=" + dropInPlaceSolution.isSolution
                    + " cell=(" + dropInPlaceSolution.cellX + "," + dropInPlaceSolution.cellY + ")");
        }

        // When the target cell is unoccupied, place the app directly there without rearranging other views
        if (dropInPlaceSolution.isSolution) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "CHOSEN: dropInPlaceSolution -> Cell("
                        + dropInPlaceSolution.cellX + "," + dropInPlaceSolution.cellY + ")");
            }
            return dropInPlaceSolution;
        }

        // Find a solution involving pushing / displacing any items in the way
        ItemConfiguration swapSolution = findReorderSolution(reorderParameters, true);
        if (DEBUG) {
            android.util.Log.d("ReorderDebug", "swapSolution:        isSolution=" + swapSolution.isSolution
                    + " cell=(" + swapSolution.cellX + "," + swapSolution.cellY + ")"
                    + " area=" + swapSolution.area());
        }

        // iOS behaviour: a widget dropped on icons pushes them, it does not hop to the nearest hole.
        if (isWidget() && swapSolution.isSolution) {
            return swapSolution;
        }

        // We attempt the approach which doesn't shuffle views at all
        ItemConfiguration closestSpaceSolution = closestEmptySpaceReorder(reorderParameters);
        if (DEBUG) {
            android.util.Log.d("ReorderDebug", "closestSpaceSolution: isSolution=" + closestSpaceSolution.isSolution
                    + " cell=(" + closestSpaceSolution.cellX + "," + closestSpaceSolution.cellY + ")"
                    + " area=" + closestSpaceSolution.area());
        }

        // If the reorder solution requires resizing (shrinking) the item being dropped, we instead
        // favor a solution in which the item is not resized, but
        if (swapSolution.isSolution && swapSolution.area() >= closestSpaceSolution.area()) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "CHOSEN: swapSolution -> Cell(" + swapSolution.cellX + "," + swapSolution.cellY + ")");
            }
            return swapSolution;
        } else if (closestSpaceSolution.isSolution) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "CHOSEN: closestSpaceSolution -> Cell(" + closestSpaceSolution.cellX + "," + closestSpaceSolution.cellY + ")");
            }
            return closestSpaceSolution;
        } else if (swapSolution.isSolution) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "CHOSEN: swapSolution (fallback) -> Cell(" + swapSolution.cellX + "," + swapSolution.cellY + ")");
            }
            return swapSolution;
        }
        if (DEBUG) {
            android.util.Log.d("ReorderDebug", "CHOSEN: null (no solution found)");
        }
        return null;
    }

    /*
     * Returns a pair (x, y), where x,y are in {-1, 0, 1} corresponding to vector between
     * the provided point and the provided cell
     */
    private void computeDirectionVector(float deltaX, float deltaY, int[] result) {
        double angle = Math.atan(deltaY / deltaX);

        result[0] = 0;
        result[1] = 0;
        if (Math.abs(Math.cos(angle)) > 0.5f) {
            result[0] = (int) Math.signum(deltaX);
        }
        if (Math.abs(Math.sin(angle)) > 0.5f) {
            result[1] = (int) Math.signum(deltaY);
        }
    }

    /**
     * This seems like it should be obvious and straight-forward, but when the direction vector
     * needs to match with the notion of the dragView pushing other views, we have to employ
     * a slightly more subtle notion of the direction vector. The question is what two points is
     * the vector between? The center of the dragView and its desired destination? Not quite, as
     * this doesn't necessarily coincide with the interaction of the dragView and items occupying
     * those cells. Instead we use some heuristics to often lock the vector to up, down, left
     * or right, which helps make pushing feel right.
     */
    public void getDirectionVectorForDrop(ReorderParameters reorderParameters,
            int[] resultDirection) {

        //TODO(adamcohen) b/151776141 use the items visual center for the direction vector
        int[] targetDestination = new int[2];

        mCellLayout.findNearestAreaIgnoreOccupied(reorderParameters.getPixelX(),
                reorderParameters.getPixelY(), reorderParameters.getSpanX(),
                reorderParameters.getSpanY(), targetDestination);
        Rect dragRect = new Rect();
        mCellLayout.cellToRect(targetDestination[0], targetDestination[1],
                reorderParameters.getSpanX(), reorderParameters.getSpanY(), dragRect);
        dragRect.offset(reorderParameters.getPixelX() - dragRect.centerX(),
                reorderParameters.getPixelY() - dragRect.centerY());

        Rect region = new Rect(targetDestination[0], targetDestination[1],
                targetDestination[0] + reorderParameters.getSpanX(),
                targetDestination[1] + reorderParameters.getSpanY());
        Rect dropRegionRect = mCellLayout.getIntersectingRectanglesInRegion(region,
                reorderParameters.getDragView());
        if (dropRegionRect == null) dropRegionRect = new Rect(region);

        int dropRegionSpanX = dropRegionRect.width();
        int dropRegionSpanY = dropRegionRect.height();

        mCellLayout.cellToRect(dropRegionRect.left, dropRegionRect.top, dropRegionRect.width(),
                dropRegionRect.height(), dropRegionRect);

        int deltaX = (dropRegionRect.centerX() - reorderParameters.getPixelX())
                / reorderParameters.getSpanX();
        int deltaY = (dropRegionRect.centerY() - reorderParameters.getPixelY())
                / reorderParameters.getSpanY();

        if (dropRegionSpanX == mCellLayout.getCountX()
                || reorderParameters.getSpanX() == mCellLayout.getCountX()) {
            deltaX = 0;
        }
        if (dropRegionSpanY == mCellLayout.getCountY()
                || reorderParameters.getSpanY() == mCellLayout.getCountY()) {
            deltaY = 0;
        }

        if (deltaX == 0 && deltaY == 0) {
            // No idea what to do, give a random direction.
            resultDirection[0] = 1;
            resultDirection[1] = 0;
        } else {
            computeDirectionVector(deltaX, deltaY, resultDirection);
        }
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location, and will also weigh in a suggested direction vector of the
     * desired location. This method computers distance based on unit grid distances,
     * not pixel distances.
     *
     * @param cellX         The X cell nearest to which you want to search for a vacant area.
     * @param cellY         The Y cell nearest which you want to search for a vacant area.
     * @param spanX         Horizontal span of the object.
     * @param spanY         Vertical span of the object.
     * @param direction     The favored direction in which the views should move from x, y
     * @param occupied      The array which represents which cells in the CellLayout are occupied
     * @param blockOccupied The array which represents which cells in the specified block (cellX,
     *                      cellY, spanX, spanY) are occupied. This is used when try to move a group
     *                      of views.
     * @param result        Array in which to place the result, or null (in which case a new array
     *                      will
     *                      be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     * nearest the requested location.
     */
    public int[] findNearestArea(int cellX, int cellY, int spanX, int spanY, int[] direction,
            boolean[][] occupied, boolean[][] blockOccupied, int[] result) {
        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        float bestDistance = Float.MAX_VALUE;
        int bestDirectionScore = Integer.MIN_VALUE;

        final int countX = mCellLayout.getCountX();
        final int countY = mCellLayout.getCountY();

        for (int y = 0; y < countY - (spanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (spanX - 1); x++) {
                // First, let's see if this thing fits anywhere
                for (int i = 0; i < spanX; i++) {
                    for (int j = 0; j < spanY; j++) {
                        if (occupied[x + i][y + j] && (blockOccupied == null
                                || blockOccupied[i][j])) {
                            continue inner;
                        }
                    }
                }

                float distance = (float) Math.hypot(x - cellX, y - cellY);
                int[] curDirection = new int[2];
                computeDirectionVector(x - cellX, y - cellY, curDirection);
                // The direction score is just the dot product of the two candidate direction
                // and that passed in.
                int curDirectionScore =
                        direction[0] * curDirection[0] + direction[1] * curDirection[1];
                if (Float.compare(distance, bestDistance) < 0 || (Float.compare(distance,
                        bestDistance) == 0 && curDirectionScore > bestDirectionScore)) {
                    bestDistance = distance;
                    bestDirectionScore = curDirectionScore;
                    bestXY[0] = x;
                    bestXY[1] = y;
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Float.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    public boolean isWidget() {
        return mIsWidget || Workspace.isWidget || (mOccupiedRect != null && (mOccupiedRect.width() > 1 || mOccupiedRect.height() > 1));
    }



    public Workspace<?> getWorkspace() {
        return Launcher.cast(mCellLayout.mActivity).getWorkspace();
    }

}
