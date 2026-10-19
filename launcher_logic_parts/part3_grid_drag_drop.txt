# Part 3: Grid Layout, Reorder Algorithm & Drag-and-Drop Engine

Modular code logic bundle designed to stay within Gemini attachment size limits (11 files).

## Table of Contents

- [src/com/android/launcher3/CellLayout.java](#srccomandroidlauncher3CellLayoutjava)
- [src/com/android/launcher3/ShortcutAndWidgetContainer.java](#srccomandroidlauncher3ShortcutAndWidgetContainerjava)
- [src/com/android/launcher3/BubbleTextView.java](#srccomandroidlauncher3BubbleTextViewjava)
- [src/com/android/launcher3/celllayout/ReorderAlgorithm.java](#srccomandroidlauncher3celllayoutReorderAlgorithmjava)
- [src/com/android/launcher3/celllayout/ItemConfiguration.kt](#srccomandroidlauncher3celllayoutItemConfigurationkt)
- [src/com/android/launcher3/dragndrop/DragController.java](#srccomandroidlauncher3dragndropDragControllerjava)
- [src/com/android/launcher3/dragndrop/DragLayer.java](#srccomandroidlauncher3dragndropDragLayerjava)
- [src/com/android/launcher3/dragndrop/DragView.java](#srccomandroidlauncher3dragndropDragViewjava)
- [src/com/android/launcher3/dragndrop/DragOptions.java](#srccomandroidlauncher3dragndropDragOptionsjava)
- [src/com/android/launcher3/dragndrop/DragDriver.java](#srccomandroidlauncher3dragndropDragDriverjava)
- [src/com/android/launcher3/DropTarget.java](#srccomandroidlauncher3DropTargetjava)

---

# src/com/android/launcher3/CellLayout.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/CellLayout.java`  
**Lines**: 2280 | **Bytes**: 94502

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.dragndrop.DraggableView.DRAGGABLE_ICON;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_BOUNCE_OFFSET;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_PREVIEW_OFFSET;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import com.android.app.animation.Interpolators;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.ReorderAlgorithm;
import com.android.launcher3.celllayout.ReorderParameters;
import com.android.launcher3.celllayout.ReorderPreviewAnimation;
import com.android.launcher3.celllayout.ViewCluster;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.CellAndSpan;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.ParcelableSparseArray;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import foundation.e.bliss.multimode.MultiModeController;

public class CellLayout extends ViewGroup {
    private static final String TAG = "CellLayout";
    private static final boolean LOGD = false;

    /** The color of the "leave-behind" shape when a folder is opened from Hotseat. */
    private static final int FOLDER_LEAVE_BEHIND_COLOR = Color.argb(160, 245, 245, 245);

    public final ActivityContext mActivity;
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellWidth;
    @ViewDebug.ExportedProperty(category = "launcher")
    @Thunk int mCellHeight;
    private int mFixedCellWidth;
    private int mFixedCellHeight;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected Point mBorderSpace;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCountY;

    private boolean mDropPending = false;
    private boolean mIsDragTarget = true;
    private boolean mJailContent = true;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    @Thunk final int[] mTmpPoint = new int[2];
    @Thunk
    public final int[] mTempLocation = new int[2];

    @Thunk final Rect mTempOnDrawCellToRect = new Rect();
    final PointF mTmpPointF = new PointF();

    protected GridOccupancy mOccupied;
    public GridOccupancy mTmpOccupied;

    private OnTouchListener mInterceptTouchListener;

    private final ArrayList<DelegatedCellDrawing> mDelegatedCellDrawings = new ArrayList<>();
    final PreviewBackground mFolderLeaveBehind = new PreviewBackground();

    private static final int[] BACKGROUND_STATE_ACTIVE = new int[] { android.R.attr.state_active };
    private static final int[] BACKGROUND_STATE_DEFAULT = EMPTY_STATE_SET;
    protected final Drawable mBackground;

    // These values allow a fixed measurement to be set on the CellLayout.
    private int mFixedWidth = -1;
    private int mFixedHeight = -1;

    // If we're actively dragging something over this screen, mIsDragOverlapping is true
    private boolean mIsDragOverlapping = false;

    // These arrays are used to implement the drag visualization on x-large screens.
    // They are used as circular arrays, indexed by mDragOutlineCurrent.
    @Thunk final CellLayoutLayoutParams[] mDragOutlines = new CellLayoutLayoutParams[4];
    @Thunk final float[] mDragOutlineAlphas = new float[mDragOutlines.length];
    private final InterruptibleInOutAnimator[] mDragOutlineAnims =
            new InterruptibleInOutAnimator[mDragOutlines.length];

    // Used as an index into the above 3 arrays; indicates which is the most current value.
    private int mDragOutlineCurrent = 0;
    private final Paint mDragOutlinePaint = new Paint();

    @Thunk final ArrayMap<CellLayoutLayoutParams, Animator> mReorderAnimators = new ArrayMap<>();
    @Thunk final ArrayMap<Reorderable, ReorderPreviewAnimation> mShakeAnimators = new ArrayMap<>();

    private boolean mItemPlacementDirty = false;

    // Used to visualize the grid and drop locations
    private boolean mVisualizeCells = false;
    private boolean mVisualizeDropLocation = false;
    private RectF mVisualizeGridRect = new RectF();
    private Paint mVisualizeGridPaint = new Paint();
    private int mGridVisualizationRoundingRadius;
    private float mGridAlpha = 0f;
    private int mGridColor = 0;
    protected float mSpringLoadedProgress = 0f;
    private float mScrollProgress = 0f;

    // When a drag operation is in progress, holds the nearest cell to the touch point
    private final int[] mDragCell = new int[2];
    private final int[] mDragCellSpan = new int[2];

    private boolean mDragging = false;

    private final TimeInterpolator mEaseOutInterpolator;
    protected final ShortcutAndWidgetContainer mShortcutsAndWidgets;
    @Px
    protected int mSpaceBetweenCellLayoutsPx = 0;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WORKSPACE, HOTSEAT, FOLDER})
    public @interface ContainerType{}
    public static final int WORKSPACE = 0;
    public static final int HOTSEAT = 1;
    public static final int FOLDER = 2;

    @ContainerType private final int mContainerType;

    public static final float DEFAULT_SCALE = 1f;

    public static final int MODE_SHOW_REORDER_HINT = 0;
    public static final int MODE_DRAG_OVER = 1;
    public static final int MODE_ON_DROP = 2;
    public static final int MODE_ON_DROP_EXTERNAL = 3;
    public static final int MODE_ACCEPT_DROP = 4;
    private static final boolean DESTRUCTIVE_REORDER = false;
    private static final boolean DEBUG_VISUALIZE_OCCUPIED = false;

    public static final float REORDER_PREVIEW_MAGNITUDE = 0.12f;
    public static final int REORDER_ANIMATION_DURATION = 220;
    @Thunk final float mReorderPreviewAnimationMagnitude;

    private final ArrayList<View> mIntersectingViews = new ArrayList<>();
    private final Rect mOccupiedRect = new Rect();
    public final int[] mDirectionVector = new int[2];

    ItemConfiguration mPreviousSolution = null;
    private ArrayList<View> mPendingOverflow;                       // set on drop, consumed by Workspace
    private final ArrayList<View> mOverflowPreview = new ArrayList<>(); // dimmed during hover preview
    private static final int INVALID_DIRECTION = -100;

    private final Rect mTempRect = new Rect();
    private final RectF mTempRectF = new RectF();
    private final float[] mTmpFloatArray = new float[4];

    private static final Paint sPaint = new Paint();

    // Related to accessible drag and drop
    DragAndDropAccessibilityDelegate mTouchHelper;

    CellLayoutContainer mCellLayoutContainer;

    ArrayList<CellAndSpan> mWidgetCellAndSpanList = new ArrayList<>();

    public static final FloatProperty<CellLayout> SPRING_LOADED_PROGRESS =
            new FloatProperty<CellLayout>("spring_loaded_progress") {
                @Override
                public Float get(CellLayout cl) {
                    return cl.getSpringLoadedProgress();
                }

                @Override
                public void setValue(CellLayout cl, float progress) {
                    cl.setSpringLoadedProgress(progress);
                }
            };

    public CellLayout(Context context, CellLayoutContainer container) {
        this(context, (AttributeSet) null);
        this.mCellLayoutContainer = container;
    }

    public CellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CellLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CellLayout, defStyle, 0);
        mContainerType = a.getInteger(R.styleable.CellLayout_containerType, WORKSPACE);
        a.recycle();

        // A ViewGroup usually does not draw, but CellLayout needs to draw a rectangle to show
        // the user where a dragged item will land when dropped.
        setWillNotDraw(false);
        setClipToPadding(false);
        setClipChildren(false);
        mActivity = ActivityContext.lookupContext(context);
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();

        resetCellSizeInternal(deviceProfile);

        mCountX = deviceProfile.inv.numColumns;
        mCountY = deviceProfile.inv.numRows;
        mOccupied =  new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);

        mFolderLeaveBehind.mDelegateCellX = -1;
        mFolderLeaveBehind.mDelegateCellY = -1;

        setAlwaysDrawnWithCacheEnabled(false);

        Resources res = getResources();

        mBackground = getContext().getDrawable(R.drawable.bg_celllayout);
        mBackground.setCallback(this);
        mBackground.setAlpha(0);

        mGridColor = Themes.getAttrColor(getContext(), R.attr.workspaceAccentColor);
        mGridVisualizationRoundingRadius =
                res.getDimensionPixelSize(R.dimen.grid_visualization_rounding_radius);
        mReorderPreviewAnimationMagnitude = (REORDER_PREVIEW_MAGNITUDE * deviceProfile.iconSizePx);

        // Initialize the data structures used for the drag visualization.
        mEaseOutInterpolator = Interpolators.DECELERATE_QUINT; // Quint ease out
        mDragCell[0] = mDragCell[1] = -1;
        mDragCellSpan[0] = mDragCellSpan[1] = -1;
        for (int i = 0; i < mDragOutlines.length; i++) {
            mDragOutlines[i] = new CellLayoutLayoutParams(0, 0, 0, 0);
        }
        mDragOutlinePaint.setColor(Themes.getAttrColor(context, R.attr.workspaceTextColor));

        // When dragging things around the home screens, we show a green outline of
        // where the item will land. The outlines gradually fade out, leaving a trail
        // behind the drag path.
        // Set up all the animations that are used to implement this fading.
        final int duration = res.getInteger(R.integer.config_dragOutlineFadeTime);
        final float fromAlphaValue = 0;
        final float toAlphaValue = (float)res.getInteger(R.integer.config_dragOutlineMaxAlpha);

        Arrays.fill(mDragOutlineAlphas, fromAlphaValue);

        for (int i = 0; i < mDragOutlineAnims.length; i++) {
            final InterruptibleInOutAnimator anim =
                    new InterruptibleInOutAnimator(duration, fromAlphaValue, toAlphaValue);
            anim.getAnimator().setInterpolator(mEaseOutInterpolator);
            final int thisIndex = i;
            anim.getAnimator().addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    // If an animation is started and then stopped very quickly, we can still
                    // get spurious updates we've cleared the tag. Guard against this.
                    mDragOutlineAlphas[thisIndex] = (Float) animation.getAnimatedValue();
                    CellLayout.this.invalidate();
                }
            });
            // The animation holds a reference to the drag outline bitmap as long is it's
            // running. This way the bitmap can be GCed when the animations are complete.
            mDragOutlineAnims[i] = anim;
        }

        mShortcutsAndWidgets = new ShortcutAndWidgetContainer(context, mContainerType);
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
        addView(mShortcutsAndWidgets);
    }

    public CellLayoutContainer getCellLayoutContainer() {
        return mCellLayoutContainer;
    }

    public void setCellLayoutContainer(CellLayoutContainer cellLayoutContainer) {
        mCellLayoutContainer = cellLayoutContainer;
    }

    /**
     * Sets or clears a delegate used for accessible drag and drop
     */
    public void setDragAndDropAccessibilityDelegate(DragAndDropAccessibilityDelegate delegate) {
        ViewCompat.setAccessibilityDelegate(this, delegate);

        mTouchHelper = delegate;
        int accessibilityFlag = mTouchHelper != null
                ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO;
        setImportantForAccessibility(accessibilityFlag);
        getShortcutsAndWidgets().setImportantForAccessibility(accessibilityFlag);
        // ExploreByTouchHelper sets focusability. Clear it when the delegate is cleared.
        setFocusable(delegate != null);
        // Invalidate the accessibility hierarchy
        if (getParent() != null) {
            getParent().notifySubtreeAccessibilityStateChanged(
                    this, this, AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        }
    }

    /**
     * Returns the currently set accessibility delegate
     */
    public DragAndDropAccessibilityDelegate getDragAndDropAccessibilityDelegate() {
        return mTouchHelper;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Always attempt to dispatch hover events to accessibility first.
        if (mTouchHelper != null && mTouchHelper.dispatchHoverEvent(event)) {
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mInterceptTouchListener != null && mInterceptTouchListener.onTouch(this, ev);
    }

    public void enableHardwareLayer(boolean hasLayer) {
        mShortcutsAndWidgets.setLayerType(hasLayer ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, sPaint);
    }

    public boolean isHardwareLayerEnabled() {
        return mShortcutsAndWidgets.getLayerType() == LAYER_TYPE_HARDWARE;
    }

    /**
     * Change sizes of cells
     *
     * @param width  the new width of the cells
     * @param height the new height of the cells
     */
    public void setCellDimensions(int width, int height) {
        mFixedCellWidth = mCellWidth = width;
        mFixedCellHeight = mCellHeight = height;
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
    }

    private void resetCellSizeInternal(DeviceProfile deviceProfile) {
        switch (mContainerType) {
            case FOLDER:
                mBorderSpace = new Point(deviceProfile.folderCellLayoutBorderSpacePx);
                break;
            case HOTSEAT:
                mBorderSpace = new Point(deviceProfile.hotseatBorderSpace,
                        deviceProfile.hotseatBorderSpace);
                break;
            case WORKSPACE:
            default:
                mBorderSpace = new Point(deviceProfile.cellLayoutBorderSpacePx);
                break;
        }

        mCellWidth = mCellHeight = -1;
        mFixedCellWidth = mFixedCellHeight = -1;
    }

    /**
     * Reset the cell sizes and border space
     */
    public void resetCellSize(DeviceProfile deviceProfile) {
        resetCellSizeInternal(deviceProfile);
        requestLayout();
    }

    public void setGridSize(int x, int y) {
        mCountX = x;
        mCountY = y;
        mOccupied = new GridOccupancy(mCountX, mCountY);
        mTmpOccupied = new GridOccupancy(mCountX, mCountY);
        mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                mBorderSpace);
        requestLayout();
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mShortcutsAndWidgets.setInvertIfRtl(invert);
    }

    public void setDropPending(boolean pending) {
        mDropPending = pending;
    }

    public boolean isDropPending() {
        return mDropPending;
    }

    void setIsDragOverlapping(boolean isDragOverlapping) {
        if (mIsDragOverlapping != isDragOverlapping) {
            mIsDragOverlapping = isDragOverlapping;
            mBackground.setState(mIsDragOverlapping
                    ? BACKGROUND_STATE_ACTIVE : BACKGROUND_STATE_DEFAULT);
            invalidate();
        }
    }

    void disableDragTarget() {
        mIsDragTarget = false;
    }

    public boolean isDragTarget() {
        return mIsDragTarget;
    }

    public void disableJailContent() {
        mJailContent = false;
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        if (mJailContent) {
            ParcelableSparseArray jail = getJailedArray(container);
            super.dispatchSaveInstanceState(jail);
            container.put(R.id.cell_layout_jail_id, jail);
        } else {
            super.dispatchSaveInstanceState(container);
        }
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(mJailContent ? getJailedArray(container) : container);
    }

    /**
     * Wrap the SparseArray in another Parcelable so that the item ids do not conflict with our
     * our internal resource ids
     */
    private ParcelableSparseArray getJailedArray(SparseArray<Parcelable> container) {
        final Parcelable parcelable = container.get(R.id.cell_layout_jail_id);
        return parcelable instanceof ParcelableSparseArray ?
                (ParcelableSparseArray) parcelable : new ParcelableSparseArray();
    }

    public boolean getIsDragOverlapping() {
        return mIsDragOverlapping;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsDragTarget) {
            return;
        }

        // When we're large, we are either drawn in a "hover" state (ie when dragging an item to
        // a neighboring page) or with just a normal background (if backgroundAlpha > 0.0f)
        // When we're small, we are either drawn normally or in the "accepts drops" state (during
        // a drag). However, we also drag the mini hover background *over* one of those two
        // backgrounds
        if (mBackground.getAlpha() > 0) {
            mBackground.draw(canvas);
        }

        if (DEBUG_VISUALIZE_OCCUPIED) {
            Rect cellBounds = new Rect();
            // Will contain the bounds of the cell including spacing between cells.
            Rect cellBoundsWithSpacing = new Rect();
            int[] targetCell = new int[2];
            int[] cellCenter = new int[2];
            Paint debugPaint = new Paint();
            debugPaint.setStrokeWidth(Utilities.dpToPx(1));
            for (int x = 0; x < mCountX; x++) {
                for (int y = 0; y < mCountY; y++) {
                    if (!mOccupied.cells[x][y]) {
                        continue;
                    }
                    targetCell[0] = x;
                    targetCell[1] = y;

                    boolean canCreateFolder = canCreateFolder(getChildAt(x, y));
                    cellToRect(x, y, 1, 1, cellBounds);
                    cellBoundsWithSpacing.set(cellBounds);
                    cellBoundsWithSpacing.inset(-mBorderSpace.x / 2, -mBorderSpace.y / 2);
                    getWorkspaceCellVisualCenter(x, y, cellCenter);

                    canvas.save();
                    canvas.clipRect(cellBoundsWithSpacing);

                    // Draw reorder drag target.
                    debugPaint.setColor(Color.RED);
                    canvas.drawCircle(cellCenter[0], cellCenter[1],
                            getReorderRadius(targetCell, 1, 1), debugPaint);

                    // Draw folder creation drag target.
                    if (canCreateFolder) {
                        debugPaint.setColor(Color.GREEN);
                        canvas.drawCircle(cellCenter[0], cellCenter[1],
                                getFolderCreationRadius(targetCell), debugPaint);
                    }

                    canvas.restore();
                }
            }
        }

        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing cellDrawing = mDelegatedCellDrawings.get(i);
            cellToPoint(cellDrawing.mDelegateCellX, cellDrawing.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            cellDrawing.drawUnderItem(canvas);
            canvas.restore();
        }

        if (mFolderLeaveBehind.mDelegateCellX >= 0 && mFolderLeaveBehind.mDelegateCellY >= 0) {
            cellToPoint(mFolderLeaveBehind.mDelegateCellX,
                    mFolderLeaveBehind.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            mFolderLeaveBehind.drawLeaveBehind(canvas, FOLDER_LEAVE_BEHIND_COLOR);
            canvas.restore();
        }

        if (mVisualizeCells || mVisualizeDropLocation) {
            visualizeGrid(canvas);
        }
    }

    /**
     * Returns whether dropping an icon on the given View can create (or add to) a folder.
     */
    private boolean canCreateFolder(View child) {
        return child instanceof DraggableView
                && ((DraggableView) child).getViewType() == DRAGGABLE_ICON;
    }

    /**
     * Indicates the progress of the Workspace entering the SpringLoaded state; allows the
     * CellLayout to update various visuals for this state.
     *
     * @param progress
     */
    public void setSpringLoadedProgress(float progress) {
        if (Float.compare(progress, mSpringLoadedProgress) != 0) {
            mSpringLoadedProgress = progress;
            if (!FeatureFlags.SHOW_HOME_GARDENING.get()) {
                updateBgAlpha();
            }
            setGridAlpha(progress);
        }
    }

    /**
     * See setSpringLoadedProgress
     * @return progress
     */
    public float getSpringLoadedProgress() {
        return mSpringLoadedProgress;
    }

    protected void updateBgAlpha() {
        mBackground.setAlpha((int) (mSpringLoadedProgress * 255));
    }

    /**
     * Set the progress of this page's scroll
     *
     * @param progress 0 if the screen is centered, +/-1 if it is to the right / left respectively
     */
    public void setScrollProgress(float progress) {
        if (Float.compare(Math.abs(progress), mScrollProgress) != 0) {
            mScrollProgress = Math.abs(progress);
            if (!FeatureFlags.SHOW_HOME_GARDENING.get()) {
                updateBgAlpha();
            }
        }
    }

    private void setGridAlpha(float gridAlpha) {
        if (Float.compare(gridAlpha, mGridAlpha) != 0) {
            mGridAlpha = gridAlpha;
            invalidate();
        }
    }

    protected void visualizeGrid(Canvas canvas) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        int paddingX = Math.min((mCellWidth - dp.iconSizePx) / 2, dp.gridVisualizationPaddingX);
        int paddingY = Math.min((mCellHeight - dp.iconSizePx) / 2, dp.gridVisualizationPaddingY);

        mVisualizeGridPaint.setStrokeWidth(8);

        // This is used for debugging purposes only
        if (mVisualizeCells) {
            int paintAlpha = (int) (120 * mGridAlpha);
            mVisualizeGridPaint.setColor(ColorUtils.setAlphaComponent(mGridColor, paintAlpha));
            for (int i = 0; i < mCountX; i++) {
                for (int j = 0; j < mCountY; j++) {
                    cellToRect(i, j, 1, 1, mTempOnDrawCellToRect);
                    mVisualizeGridRect.set(mTempOnDrawCellToRect);
                    mVisualizeGridRect.inset(paddingX, paddingY);
                    mVisualizeGridPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRoundRect(mVisualizeGridRect, mGridVisualizationRoundingRadius,
                            mGridVisualizationRoundingRadius, mVisualizeGridPaint);
                }
            }
        }

        if (mVisualizeDropLocation && !FeatureFlags.SHOW_HOME_GARDENING.get()) {
            for (int i = 0; i < mDragOutlines.length; i++) {
                final float alpha = mDragOutlineAlphas[i];
                if (alpha <= 0) continue;
                CellLayoutLayoutParams params = mDragOutlines[i];
                cellToRect(params.getCellX(), params.getCellY(), params.cellHSpan, params.cellVSpan,
                        mTempOnDrawCellToRect);
                mVisualizeGridRect.set(mTempOnDrawCellToRect);
                mVisualizeGridRect.inset(paddingX, paddingY);

                mVisualizeGridPaint.setAlpha(255);
                mVisualizeGridPaint.setStyle(Paint.Style.STROKE);
                mVisualizeGridPaint.setColor(Color.argb((int) (alpha),
                        Color.red(mGridColor), Color.green(mGridColor), Color.blue(mGridColor)));

                canvas.save();
                canvas.translate(getMarginForGivenCellParams(params), 0);
                canvas.drawRoundRect(mVisualizeGridRect, mGridVisualizationRoundingRadius,
                        mGridVisualizationRoundingRadius, mVisualizeGridPaint);
                canvas.restore();
            }
        }
    }

    protected float getMarginForGivenCellParams(CellLayoutLayoutParams params) {
        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        for (int i = 0; i < mDelegatedCellDrawings.size(); i++) {
            DelegatedCellDrawing bg = mDelegatedCellDrawings.get(i);
            cellToPoint(bg.mDelegateCellX, bg.mDelegateCellY, mTempLocation);
            canvas.save();
            canvas.translate(mTempLocation[0], mTempLocation[1]);
            bg.drawOverItem(canvas);
            canvas.restore();
        }
    }

    /**
     * Add Delegated cell drawing
     */
    public void addDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.add(bg);
    }

    /**
     * Remove item from DelegatedCellDrawings
     */
    public void removeDelegatedCellDrawing(DelegatedCellDrawing bg) {
        mDelegatedCellDrawings.remove(bg);
    }

    public void setFolderLeaveBehindCell(int x, int y) {
        View child = getChildAt(x, y);
        mFolderLeaveBehind.setup(getContext(), mActivity, null,
                child.getMeasuredWidth(), child.getPaddingTop());

        mFolderLeaveBehind.mDelegateCellX = x;
        mFolderLeaveBehind.mDelegateCellY = y;
        invalidate();
    }

    public void clearFolderLeaveBehind() {
        mFolderLeaveBehind.mDelegateCellX = -1;
        mFolderLeaveBehind.mDelegateCellY = -1;
        invalidate();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public void restoreInstanceState(SparseArray<Parcelable> states) {
        try {
            dispatchRestoreInstanceState(states);
        } catch (IllegalArgumentException ex) {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw ex;
            }
            // Mismatched viewId / viewType preventing restore. Skip restore on production builds.
            Log.e(TAG, "Ignoring an error while restoring a view instance state", ex);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    public void setOnInterceptTouchListener(View.OnTouchListener listener) {
        mInterceptTouchListener = listener;
    }

    public int getCountX() {
        return mCountX;
    }

    public int getCountY() {
        return mCountY;
    }

    public boolean acceptsWidget() {
        if (MultiModeController.isSingleLayerMode()) {
            return false;
        }
            return mContainerType == WORKSPACE;
    }

    /**
     * Adds the given view to the CellLayout
     *
     * @param child view to add.
     * @param index index of the CellLayout children where to add the view.
     * @param childId id of the view.
     * @param params represent the logic of the view on the CellLayout.
     * @param markCells if the occupied cells should be marked or not
     * @return if adding the view was successful
     */
    public boolean addViewToCellLayout(View child, int index, int childId,
            CellLayoutLayoutParams params, boolean markCells) {
        final CellLayoutLayoutParams lp = params;

        // Hotseat icons - remove text
        if (child instanceof BubbleTextView) {
            BubbleTextView bubbleChild = (BubbleTextView) child;
            bubbleChild.setTextVisibility(mContainerType != HOTSEAT);
        }

        child.setScaleX(DEFAULT_SCALE);
        child.setScaleY(DEFAULT_SCALE);

        // Generate an id for each view, this assumes we have at most 256x256 cells
        // per workspace screen
        if (mContainerType == HOTSEAT && (lp.getCellX() >= mCountX || lp.getCellY() >= mCountY)) {
            if (lp.getCellX() >= mCountX) mCountX = lp.getCellX() + 1;
            if (lp.getCellY() >= mCountY) mCountY = lp.getCellY() + 1;
        }

        if (lp.getCellX() >= 0 && lp.getCellX() <= mCountX - 1
                && lp.getCellY() >= 0 && lp.getCellY() <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;

            child.setId(childId);
            if (LOGD) {
                Log.d(TAG, "Adding view to ShortcutsAndWidgetsContainer: " + child);
            }
            mShortcutsAndWidgets.addView(child, index, lp);

            if (markCells) markCellsAsOccupiedForView(child);

            return true;
        }
        return false;
    }

    @Override
    public void removeAllViews() {
        mOccupied.clear();
        mShortcutsAndWidgets.removeAllViews();
    }

    @Override
    public void removeAllViewsInLayout() {
        if (mShortcutsAndWidgets.getChildCount() > 0) {
            mOccupied.clear();
            mShortcutsAndWidgets.removeAllViewsInLayout();
        }
    }

    @Override
    public void removeView(View view) {
        markCellsAsUnoccupiedForView(view);
        mShortcutsAndWidgets.removeView(view);
    }

    @Override
    public void removeViewAt(int index) {
        markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(index));
        mShortcutsAndWidgets.removeViewAt(index);
    }

    @Override
    public void removeViewInLayout(View view) {
        markCellsAsUnoccupiedForView(view);
        mShortcutsAndWidgets.removeViewInLayout(view);
    }

    @Override
    public void removeViews(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
        }
        mShortcutsAndWidgets.removeViews(start, count);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        for (int i = start; i < start + count; i++) {
            markCellsAsUnoccupiedForView(mShortcutsAndWidgets.getChildAt(i));
        }
        mShortcutsAndWidgets.removeViewsInLayout(start, count);
    }

    /**
     * Given a point, return the cell that strictly encloses that point
     * @param x X coordinate of the point
     * @param y Y coordinate of the point
     * @param result Array of 2 ints to hold the x and y coordinate of the cell
     */
    public void pointToCellExact(int x, int y, int[] result) {
        final int hStartPadding = getPaddingLeft();
        final int vStartPadding = getPaddingTop();

        result[0] = (x - hStartPadding) / (mCellWidth + mBorderSpace.x);
        result[1] = (y - vStartPadding) / (mCellHeight + mBorderSpace.y);

        final int xAxis = mCountX;
        final int yAxis = mCountY;

        if (result[0] < 0) result[0] = 0;
        if (result[0] >= xAxis) result[0] = xAxis - 1;
        if (result[1] < 0) result[1] = 0;
        if (result[1] >= yAxis) result[1] = yAxis - 1;
    }

    /**
     * Given a cell coordinate, return the point that represents the upper left corner of that cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToPoint(int cellX, int cellY, int[] result) {
        cellToRect(cellX, cellY, 1, 1, mTempRect);
        result[0] = mTempRect.left;
        result[1] = mTempRect.top;
    }

    /**
     * Given a cell coordinate, return the point that represents the center of the cell
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    void cellToCenterPoint(int cellX, int cellY, int[] result) {
        regionToCenterPoint(cellX, cellY, 1, 1, result);
    }

    /**
     * Given a cell coordinate and span return the point that represents the center of the region
     *
     * @param cellX X coordinate of the cell
     * @param cellY Y coordinate of the cell
     *
     * @param result Array of 2 ints to hold the x and y coordinate of the point
     */
    public void regionToCenterPoint(int cellX, int cellY, int spanX, int spanY, int[] result) {
        cellToRect(cellX, cellY, spanX, spanY, mTempRect);
        result[0] = mTempRect.centerX();
        result[1] = mTempRect.centerY();
    }

    /**
     * Returns the distance between the given coordinate and the visual center of the given cell.
     */
    public float getDistanceFromWorkspaceCellVisualCenter(float x, float y, int[] cell) {
        getWorkspaceCellVisualCenter(cell[0], cell[1], mTmpPoint);
        return (float) Math.hypot(x - mTmpPoint[0], y - mTmpPoint[1]);
    }

    private void getWorkspaceCellVisualCenter(int cellX, int cellY, int[] outPoint) {
        View child = getChildAt(cellX, cellY);
        if (child instanceof DraggableView) {
            DraggableView draggableChild = (DraggableView) child;
            if (draggableChild.getViewType() == DRAGGABLE_ICON) {
                cellToPoint(cellX, cellY, outPoint);
                draggableChild.getWorkspaceVisualDragBounds(mTempRect);
                mTempRect.offset(outPoint[0], outPoint[1]);
                outPoint[0] = mTempRect.centerX();
                outPoint[1] = mTempRect.centerY();
                return;
            }
        }
        cellToCenterPoint(cellX, cellY, outPoint);
    }

    /**
     * Returns the max distance from the center of a cell that can accept a drop to create a folder.
     */
    public float getFolderCreationRadius(int[] targetCell) {
        DeviceProfile grid = mActivity.getDeviceProfile();
        return 0.35f * grid.iconSizePx;
    }

    /**
     * Returns the max distance from the center of a cell that will start to reorder on drag over.
     */
    public float getReorderRadius(int[] targetCell, int spanX, int spanY) {
        Rect cellBoundsWithSpacing = mTempRect;
        cellToRect(targetCell[0], targetCell[1], spanX, spanY, cellBoundsWithSpacing);
        cellBoundsWithSpacing.inset(-mBorderSpace.x / 2, -mBorderSpace.y / 2);

        // Take up the entire cell, including space between this cell and the adjacent ones.
        // Multiply by span to scale radius
        return (float) Math.hypot(spanX * cellBoundsWithSpacing.width() / 2f,
                spanY * cellBoundsWithSpacing.height() / 2f);
    }

    public int getCellWidth() {
        return mCellWidth;
    }

    public int getCellHeight() {
        return mCellHeight;
    }

    public void setFixedSize(int width, int height) {
        mFixedWidth = width;
        mFixedHeight = height;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize =  MeasureSpec.getSize(heightMeasureSpec);
        int childWidthSize = widthSize - (getPaddingLeft() + getPaddingRight());
        int childHeightSize = heightSize - (getPaddingTop() + getPaddingBottom());

        if (mFixedCellWidth < 0 || mFixedCellHeight < 0) {
            int cw = DeviceProfile.calculateCellWidth(childWidthSize, mBorderSpace.x,
                    mCountX);
            int ch = DeviceProfile.calculateCellHeight(childHeightSize, mBorderSpace.y,
                    mCountY);
            if (cw != mCellWidth || ch != mCellHeight) {
                mCellWidth = cw;
                mCellHeight = ch;
                mShortcutsAndWidgets.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY,
                        mBorderSpace);
            }
        }

        int newWidth = childWidthSize;
        int newHeight = childHeightSize;
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            newWidth = mFixedWidth;
            newHeight = mFixedHeight;
        } else if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException("CellLayout cannot have UNSPECIFIED dimensions");
        }

        mShortcutsAndWidgets.measure(
                MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));

        int maxWidth = mShortcutsAndWidgets.getMeasuredWidth();
        int maxHeight = mShortcutsAndWidgets.getMeasuredHeight();
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            setMeasuredDimension(maxWidth, maxHeight);
        } else {
            setMeasuredDimension(widthSize, heightSize);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        left += (int) Math.ceil(getUnusedHorizontalSpace() / 2f);
        int right = r - l - getPaddingRight();
        right -= (int) Math.ceil(getUnusedHorizontalSpace() / 2f);

        int top = getPaddingTop();
        int bottom = b - t - getPaddingBottom();

        // Expand the background drawing bounds by the padding baked into the background drawable
        mBackground.getPadding(mTempRect);
        mBackground.setBounds(
                left - mTempRect.left - getPaddingLeft(),
                top - mTempRect.top - getPaddingTop(),
                right + mTempRect.right + getPaddingRight(),
                bottom + mTempRect.bottom + getPaddingBottom());

        mShortcutsAndWidgets.layout(left, top, right, bottom);
    }

    /**
     * Returns the amount of space left over after subtracting padding and cells. This space will be
     * very small, a few pixels at most, and is a result of rounding down when calculating the cell
     * width in {@link DeviceProfile#calculateCellWidth(int, int, int)}.
     */
    public int getUnusedHorizontalSpace() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - (mCountX * mCellWidth)
                - ((mCountX - 1) * mBorderSpace.x);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (mIsDragTarget && who == mBackground);
    }

    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        return mShortcutsAndWidgets;
    }

    public View getChildAt(int cellX, int cellY) {
        return mShortcutsAndWidgets.getChildAt(cellX, cellY);
    }

    public boolean animateChildToPosition(final View child, int cellX, int cellY, int duration,
            int delay, boolean permanent, boolean adjustOccupied) {
        ShortcutAndWidgetContainer clc = getShortcutsAndWidgets();

        if (clc.indexOfChild(child) != -1 && (child instanceof Reorderable)) {
            final CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            final ItemInfo info = (ItemInfo) child.getTag();
            final Reorderable item = (Reorderable) child;

            // We cancel any existing animations
            if (mReorderAnimators.containsKey(lp)) {
                mReorderAnimators.get(lp).cancel();
                mReorderAnimators.remove(lp);
            }


            if (adjustOccupied) {
                GridOccupancy occupied = permanent ? mOccupied : mTmpOccupied;
                occupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, false);
                occupied.markCells(cellX, cellY, lp.cellHSpan, lp.cellVSpan, true);
            }

            int[] oldCenter = new int[2];
            regionToCenterPoint(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, oldCenter);
            int[] newCenter = new int[2];
            regionToCenterPoint(cellX, cellY, lp.cellHSpan, lp.cellVSpan, newCenter);
            final float finalPreviewOffsetX = newCenter[0] - oldCenter[0];
            final float finalPreviewOffsetY = newCenter[1] - oldCenter[1];

            MultiTranslateDelegate mtd = item.getTranslateDelegate();
            float initPreviewOffsetX = mtd.getTranslationX(INDEX_REORDER_PREVIEW_OFFSET).getValue();
            float initPreviewOffsetY = mtd.getTranslationY(INDEX_REORDER_PREVIEW_OFFSET).getValue();

            DragTrace.log("animateChildToPosition child=" + (info != null ? info.title : child)
                    + " init=(" + initPreviewOffsetX + "," + initPreviewOffsetY + ") final=("
                    + finalPreviewOffsetX + "," + finalPreviewOffsetY + ") perm=" + permanent);

            // Exit early if already at target position
            if (Math.abs(finalPreviewOffsetX - initPreviewOffsetX) < 1f
                    && Math.abs(finalPreviewOffsetY - initPreviewOffsetY) < 1f) {
                item.getTranslateDelegate().setTranslation(INDEX_REORDER_PREVIEW_OFFSET,
                        finalPreviewOffsetX, finalPreviewOffsetY);
                lp.isLockedToGrid = true;
                return true;
            }

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(duration);
            va.setInterpolator(Interpolators.DECELERATE_1_5);
            mReorderAnimators.put(lp, va);

            va.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float r = (Float) animation.getAnimatedValue();
                    float x = (1 - r) * initPreviewOffsetX + r * finalPreviewOffsetX;
                    float y = (1 - r) * initPreviewOffsetY + r * finalPreviewOffsetY;
                    item.getTranslateDelegate().setTranslation(INDEX_REORDER_PREVIEW_OFFSET, x, y);
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;
                public void onAnimationEnd(Animator animation) {
                    // If the animation was cancelled, it means that another animation
                    // has interrupted this one, and we don't want to lock the item into
                    // place just yet.
                    if (!cancelled) {
                        lp.isLockedToGrid = true;
                        if (permanent) {
                            item.getTranslateDelegate()
                                    .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0, 0);
                            child.requestLayout();
                        } else {
                            item.getTranslateDelegate()
                                    .setTranslation(INDEX_REORDER_PREVIEW_OFFSET,
                                            finalPreviewOffsetX, finalPreviewOffsetY);
                        }
                    }
                    if (mReorderAnimators.containsKey(lp)) {
                        mReorderAnimators.remove(lp);
                    }
                }
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }
            });
            va.setStartDelay(delay);
            va.start();
            return true;
        }
        return false;
    }

    void visualizeDropLocation(int cellX, int cellY, int spanX, int spanY,
            DropTarget.DragObject dragObject) {
        if (mDragCell[0] != cellX || mDragCell[1] != cellY || mDragCellSpan[0] != spanX
                || mDragCellSpan[1] != spanY) {
            mDragCell[0] = cellX;
            mDragCell[1] = cellY;
            mDragCellSpan[0] = spanX;
            mDragCellSpan[1] = spanY;

            // Apply color extraction on a widget when dragging.
            applyColorExtractionOnWidget(dragObject, mDragCell, spanX, spanY);

            final int oldIndex = mDragOutlineCurrent;
            mDragOutlineAnims[oldIndex].animateOut();
            mDragOutlineCurrent = (oldIndex + 1) % mDragOutlines.length;

            CellLayoutLayoutParams cell = mDragOutlines[mDragOutlineCurrent];
            cell.setCellX(cellX);
            cell.setCellY(cellY);
            cell.cellHSpan = spanX;
            cell.cellVSpan = spanY;

            mDragOutlineAnims[mDragOutlineCurrent].animateIn();
            invalidate();

            if (dragObject.stateAnnouncer != null) {
                dragObject.stateAnnouncer.announce(getItemMoveDescription(cellX, cellY));
            }

        }
    }

    /** Applies the local color extraction to a dragging widget object. */
    private void applyColorExtractionOnWidget(DropTarget.DragObject dragObject, int[] targetCell,
            int spanX, int spanY) {
        // Apply local extracted color if the DragView is an AppWidgetHostViewDrawable.
        View view = dragObject.dragView.getContentView();
        if (view instanceof LauncherAppWidgetHostView) {
            int screenId = mCellLayoutContainer.getCellLayoutId(this);
            cellToRect(targetCell[0], targetCell[1], spanX, spanY, mTempRect);

            ((LauncherAppWidgetHostView) view).handleDrag(mTempRect, this, screenId);
        }
    }

    @SuppressLint("StringFormatMatches")
    public String getItemMoveDescription(int cellX, int cellY) {
        if (mContainerType == HOTSEAT) {
            return getContext().getString(R.string.move_to_hotseat_position,
                    Math.max(cellX, cellY) + 1);
        } else {
            int row = cellY + 1;
            int col = Utilities.isRtl(getResources()) ? mCountX - cellX : cellX + 1;
            int panelCount = mCellLayoutContainer.getPanelCount();
            int pageIndex = mCellLayoutContainer.getCellLayoutIndex(this);
            if (panelCount > 1) {
                // Increment the column if the target is on the right side of a two panel home
                col += (pageIndex % panelCount) * mCountX;
            }
            return getContext().getString(R.string.move_to_empty_cell_description, row, col,
                    mCellLayoutContainer.getPageDescription(pageIndex));
        }
    }

    public void clearDragOutlines() {
        final int oldIndex = mDragOutlineCurrent;
        mDragOutlineAnims[oldIndex].animateOut();
        mDragCell[0] = mDragCell[1] = -1;
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    public int[] findNearestVacantArea(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, int[] result, int[] resultSpan) {
        return findNearestArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, false,
                result, resultSpan);
    }

    /**
     * Find a vacant area that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     * @param relativeXPos The X location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param relativeYPos The Y location relative to the Cell layout at which you want to search
     *                     for a vacant area.
     * @param minSpanX The minimum horizontal span required
     * @param minSpanY The minimum vertical span required
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param ignoreOccupied If true, the result can be an occupied cell
     * @param result Array in which to place the result, or null (in which case a new array will
     *        be allocated)
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    protected int[] findNearestArea(int relativeXPos, int relativeYPos, int minSpanX, int minSpanY,
            int spanX, int spanY, boolean ignoreOccupied, int[] result, int[] resultSpan) {
        // For items with a spanX / spanY > 1, the passed in point (relativeXPos, relativeYPos)
        // corresponds to the center of the item, but we are searching based on the top-left cell,
        // so we translate the point over to correspond to the top-left.
        relativeXPos = (int) (relativeXPos - (mCellWidth + mBorderSpace.x) * (spanX - 1) / 2f);
        relativeYPos = (int) (relativeYPos - (mCellHeight + mBorderSpace.y) * (spanY - 1) / 2f);

        // Keep track of best-scoring drop area
        final int[] bestXY = result != null ? result : new int[2];
        double bestDistance = Double.MAX_VALUE;
        final Rect bestRect = new Rect(-1, -1, -1, -1);
        final Stack<Rect> validRegions = new Stack<>();

        final int countX = mCountX;
        final int countY = mCountY;

        if (minSpanX <= 0 || minSpanY <= 0 || spanX <= 0 || spanY <= 0 ||
                spanX < minSpanX || spanY < minSpanY) {
            return bestXY;
        }

        for (int y = 0; y < countY - (minSpanY - 1); y++) {
            inner:
            for (int x = 0; x < countX - (minSpanX - 1); x++) {
                int ySize = -1;
                int xSize = -1;
                if (!ignoreOccupied) {
                    // First, let's see if this thing fits anywhere
                    for (int i = 0; i < minSpanX; i++) {
                        for (int j = 0; j < minSpanY; j++) {
                            if (mOccupied.cells[x + i][y + j]) {
                                continue inner;
                            }
                        }
                    }
                    xSize = minSpanX;
                    ySize = minSpanY;

                    // We know that the item will fit at _some_ acceptable size, now let's see
                    // how big we can make it. We'll alternate between incrementing x and y spans
                    // until we hit a limit.
                    boolean incX = true;
                    boolean hitMaxX = xSize >= spanX;
                    boolean hitMaxY = ySize >= spanY;
                    while (!(hitMaxX && hitMaxY)) {
                        if (incX && !hitMaxX) {
                            for (int j = 0; j < ySize; j++) {
                                if (x + xSize > countX -1 || mOccupied.cells[x + xSize][y + j]) {
                                    // We can't move out horizontally
                                    hitMaxX = true;
                                }
                            }
                            if (!hitMaxX) {
                                xSize++;
                            }
                        } else if (!hitMaxY) {
                            for (int i = 0; i < xSize; i++) {
                                if (y + ySize > countY - 1 || mOccupied.cells[x + i][y + ySize]) {
                                    // We can't move out vertically
                                    hitMaxY = true;
                                }
                            }
                            if (!hitMaxY) {
                                ySize++;
                            }
                        }
                        hitMaxX |= xSize >= spanX;
                        hitMaxY |= ySize >= spanY;
                        incX = !incX;
                    }
                }
                final int[] cellXY = mTmpPoint;
                cellToCenterPoint(x, y, cellXY);

                // We verify that the current rect is not a sub-rect of any of our previous
                // candidates. In this case, the current rect is disqualified in favour of the
                // containing rect.
                Rect currentRect = new Rect(x, y, x + xSize, y + ySize);
                boolean contained = false;
                for (Rect r : validRegions) {
                    if (r.contains(currentRect)) {
                        contained = true;
                        break;
                    }
                }
                validRegions.push(currentRect);
                double distance = Math.hypot(cellXY[0] - relativeXPos,  cellXY[1] - relativeYPos);

                if ((distance <= bestDistance && !contained) ||
                        currentRect.contains(bestRect)) {
                    bestDistance = distance;
                    bestXY[0] = x;
                    bestXY[1] = y;
                    if (resultSpan != null) {
                        resultSpan[0] = xSize;
                        resultSpan[1] = ySize;
                    }
                    bestRect.set(currentRect);
                }
            }
        }

        // Return -1, -1 if no suitable location found
        if (bestDistance == Double.MAX_VALUE) {
            bestXY[0] = -1;
            bestXY[1] = -1;
        }
        return bestXY;
    }

    public GridOccupancy getOccupied() {
        return mOccupied;
    }

    private void copySolutionToTempState(ItemConfiguration solution, View dragView) {
        mTmpOccupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                lp.setTmpCellX(c.cellX);
                lp.setTmpCellY(c.cellY);
                lp.cellHSpan = c.spanX;
                lp.cellVSpan = c.spanY;
                mTmpOccupied.markCells(c, true);
            }
        }
        mTmpOccupied.markCells(solution, true);
    }

    private void animateItemsToSolution(ItemConfiguration solution, View dragView, boolean
            commitDragView) {

        GridOccupancy occupied = DESTRUCTIVE_REORDER ? mOccupied : mTmpOccupied;
        occupied.clear();

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            if (c != null) {
                animateChildToPosition(child, c.cellX, c.cellY, REORDER_ANIMATION_DURATION, 0,
                        DESTRUCTIVE_REORDER, false);
                occupied.markCells(c, true);
            }
        }
        if (commitDragView) {
            occupied.markCells(solution, true);
        }
    }


    // This method starts or changes the reorder preview animations
    private void beginOrAdjustReorderPreviewAnimations(ItemConfiguration solution,
            View dragView, int mode) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellAndSpan c = solution.map.get(child);
            boolean skip = mode == ReorderPreviewAnimation.MODE_HINT
                    && !solution.intersectingViews.contains(child);

            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            if (c != null && !skip && (child instanceof Reorderable)) {
                ReorderPreviewAnimation rha = new ReorderPreviewAnimation(child, mode,
                        lp.getCellX(), lp.getCellY(), c.cellX, c.cellY, c.spanX, c.spanY,
                        mReorderPreviewAnimationMagnitude, this, mShakeAnimators);
                rha.animate();
            }
        }
    }

    private void completeAndClearReorderPreviewAnimations() {
        for (ReorderPreviewAnimation a: mShakeAnimators.values()) {
            a.finishAnimation();
        }
        mShakeAnimators.clear();
    }

    private void commitTempPlacement(ItemConfiguration solution, View dragView) {
        DragTrace.log("CellLayout.commitTempPlacement solution=" + System.identityHashCode(solution)
                + " layout=" + System.identityHashCode(this));
        mTmpOccupied.copyTo(mOccupied);

        int screenId = mCellLayoutContainer.getCellLayoutId(this);
        int container = Favorites.CONTAINER_DESKTOP;

        if (mContainerType == HOTSEAT) {
            screenId = -1;
            container = Favorites.CONTAINER_HOTSEAT;
        }

        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            ItemInfo info = (ItemInfo) child.getTag();
            // We do a null check here because the item info can be null in the case of the
            // AllApps button in the hotseat.
            if (info != null && (child != dragView || dragView == null)) {
                if (solution != null && !solution.map.containsKey(child)) {
                    // Do not commit placement or update DB for views not in this solution
                    // (e.g. overflow views that will be relocated to another page).
                    continue;
                }
                CellPos presenterPos = mActivity.getCellPosMapper().mapModelToPresenter(info);
                final boolean requiresDbUpdate = (presenterPos.cellX != lp.getTmpCellX()
                        || presenterPos.cellY != lp.getTmpCellY() || info.spanX != lp.cellHSpan
                        || info.spanY != lp.cellVSpan || presenterPos.screenId != screenId);

                lp.setCellX(lp.getTmpCellX());
                lp.setCellY(lp.getTmpCellY());
                lp.isLockedToGrid = true;
                if (mReorderAnimators.containsKey(lp)) {
                    mReorderAnimators.get(lp).cancel();
                    mReorderAnimators.remove(lp);
                }
                if (child instanceof Reorderable) {
                    ((Reorderable) child).getTranslateDelegate()
                            .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0, 0);
                }
                if (requiresDbUpdate) {
                    Launcher.cast(mActivity).getModelWriter().modifyItemInDatabase(info, container,
                            screenId, lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan);
                }
            }
        }
    }

    private void setUseTempCoords(boolean useTempCoords) {
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) mShortcutsAndWidgets.getChildAt(
                    i).getLayoutParams();
            lp.useTmpCoords = useTempCoords;
        }
    }

    /**
     * For a given region, return the rectangle of the overlapping cell and span with the given
     * region including the region itself. If there is no overlap the rectangle will be
     * invalid i.e. -1, 0, -1, 0.
     */
    @Nullable
    public Rect getIntersectingRectanglesInRegion(final Rect region, final View dragView) {
        Rect boundingRect = new Rect(region);
        Rect r1 = new Rect();
        boolean isOverlapping = false;
        final int count = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child == dragView) continue;
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(lp.getCellX(), lp.getCellY(), lp.getCellX() + lp.cellHSpan,
                    lp.getCellY() + lp.cellVSpan);
            if (Rect.intersects(region, r1)) {
                isOverlapping = true;
                boundingRect.union(r1);
            } else if (mItemPlacementDirty) {
                r1.set(lp.getTmpCellX(), lp.getTmpCellY(), lp.getTmpCellX() + lp.cellHSpan,
                        lp.getTmpCellY() + lp.cellVSpan);
                if (Rect.intersects(region, r1)) {
                    isOverlapping = true;
                    boundingRect.union(r1);
                }
            }
        }
        return isOverlapping ? boundingRect : null;
    }

    public boolean isNearestDropLocationOccupied(int pixelX, int pixelY, int spanX, int spanY,
            View dragView, int[] result) {
        result = findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);
        return getIntersectingRectanglesInRegion(
                new Rect(result[0], result[1], result[0] + spanX, result[1] + spanY),
                dragView
        ) != null;
    }

    void revertTempState() {
        DragTrace.log("CellLayout.revertTempState isDirty=" + isItemPlacementDirty()
                + " layout=" + System.identityHashCode(this));
        mPreviousSolution = null;        // never let a reverted preview be reused
        restoreOverflowPreview();
        completeAndClearReorderPreviewAnimations();
        if (isItemPlacementDirty() && !DESTRUCTIVE_REORDER) {
            final int count = mShortcutsAndWidgets.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = mShortcutsAndWidgets.getChildAt(i);
                CellLayoutLayoutParams
                        lp = (CellLayoutLayoutParams) child.getLayoutParams();
                MultiTranslateDelegate mtd = (child instanceof Reorderable)
                        ? ((Reorderable) child).getTranslateDelegate() : null;
                boolean hasOffset = mtd != null && (
                        Math.abs(mtd.getTranslationX(INDEX_REORDER_PREVIEW_OFFSET).getValue()) > 0.5f
                        || Math.abs(mtd.getTranslationY(INDEX_REORDER_PREVIEW_OFFSET).getValue()) > 0.5f);
                if (lp.getTmpCellX() != lp.getCellX() || lp.getTmpCellY() != lp.getCellY() || hasOffset) {
                    lp.setTmpCellX(lp.getCellX());
                    lp.setTmpCellY(lp.getCellY());
                    animateChildToPosition(child, lp.getCellX(), lp.getCellY(),
                            REORDER_ANIMATION_DURATION, 0, false, false);
                }
            }
            setItemPlacementDirty(false);
        }
    }

    public void revertTempStateImmediately() {
        DragTrace.log("CellLayout.revertTempStateImmediately layout=" + System.identityHashCode(this));
        mPreviousSolution = null;        // never let a reverted preview be reused
        restoreOverflowPreview();
        completeAndClearReorderPreviewAnimations();
        for (Animator anim : mReorderAnimators.values()) {
            if (anim != null) anim.cancel();
        }
        mReorderAnimators.clear();
        final int count = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.setTmpCellX(lp.getCellX());
            lp.setTmpCellY(lp.getCellY());
            lp.isLockedToGrid = true;
            lp.useTmpCoords = false;
            if (child instanceof Reorderable) {
                ((Reorderable) child).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0f, 0f);
                ((Reorderable) child).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_BOUNCE_OFFSET, 0f, 0f);
            }
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            mShortcutsAndWidgets.measureChild(child);
            mShortcutsAndWidgets.layoutChild(child);
        }
        setItemPlacementDirty(false);
        setUseTempCoords(false);
        mShortcutsAndWidgets.requestLayout();
    }

    boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY,
            View dragView, int[] direction, boolean commit) {
        int[] pixelXY = new int[2];
        regionToCenterPoint(cellX, cellY, spanX, spanY, pixelXY);

        // First we determine if things have moved enough to cause a different layout
        ItemConfiguration swapSolution = findReorderSolution(pixelXY[0], pixelXY[1], spanX, spanY,
                spanX,  spanY, direction, dragView,  true);

        setUseTempCoords(true);
        if (swapSolution != null && swapSolution.isSolution) {
            // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
            // committing anything or animating anything as we just want to determine if a solution
            // exists
            copySolutionToTempState(swapSolution, dragView);
            setItemPlacementDirty(true);
            animateItemsToSolution(swapSolution, dragView, commit);

            if (commit) {
                commitTempPlacement(swapSolution, null);
                completeAndClearReorderPreviewAnimations();
                setItemPlacementDirty(false);
            }
            mShortcutsAndWidgets.requestLayout();
        }
        return swapSolution.isSolution;
    }

    public ReorderAlgorithm createReorderAlgorithm() {
        return new ReorderAlgorithm(this);
    }

    protected ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX) {
        ItemConfiguration configuration = new ItemConfiguration();
        copyCurrentStateToSolution(configuration);
        ReorderParameters parameters = new ReorderParameters(pixelX, pixelY, spanX, spanY, minSpanX,
                minSpanY, dragView, configuration);
        int[] directionVector = direction != null ? direction : mDirectionVector;
        return createReorderAlgorithm().findReorderSolution(parameters, directionVector, decX);
    }

    public void copyCurrentStateToSolution(ItemConfiguration solution) {
        mWidgetCellAndSpanList.clear();
        int childCount = mShortcutsAndWidgets.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            CellAndSpan c = new CellAndSpan(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan);
            if (child instanceof LauncherAppWidgetHostView) {
                mWidgetCellAndSpanList.add(c);
            }
            solution.add(child, c);
        }
    }

    public boolean reArrangeIcons(int x, int y) {
        ItemConfiguration solution = new ItemConfiguration();
        copyCurrentStateToSolution(solution);
        solution.save();
        int[] intersecting = new int[2];
        ArrayList<View> views = new ArrayList<>();

        if (x == 0) {
            intersecting[0] = getCountX() - 1;
            intersecting[1] = y - 1;
        } else {
            intersecting[0] = x - 1;
            intersecting[1] = y;
        }

        mOccupied.copyTo(mTmpOccupied);

        for (Map.Entry <View, CellAndSpan> keyValue : solution.map.entrySet()) {
            CellAndSpan c = keyValue.getValue();

            if (c.cellX == x && c.cellY == y) {
                mTmpOccupied.markCells(c, false);
                views.add(keyValue.getKey());
                pushIconByRow(c, mCountX, mCountY, ViewCluster.LEFT);
            }
        }
        if (views.isEmpty()) {
            return false;
        }
        View firstView = views.get(0);
        CellLayoutLayoutParams firstLp = (CellLayoutLayoutParams) firstView.getLayoutParams();
        if (!firstLp.canReorder || firstView instanceof LauncherAppWidgetHostView
                || firstLp.cellHSpan > 1 || firstLp.cellVSpan > 1) {
            return false;
        }
        ViewCluster cluster = new ViewCluster(this, views, solution);
        cluster.sortConfigurationForEdgePush(ViewCluster.LEFT);
        solution.sortedViews.sort((lhs, rhs) -> {
            CellLayoutLayoutParams lplhs = (CellLayoutLayoutParams) lhs.getLayoutParams();
            CellLayoutLayoutParams lprhs = (CellLayoutLayoutParams) rhs.getLayoutParams();
            if (lprhs.getCellY() + lprhs.cellVSpan == lplhs.getCellY() + lplhs.cellVSpan) {
                return lprhs.getCellX() - lplhs.getCellX();
            }
            return (lprhs.getCellY() + lprhs.cellVSpan) - (lplhs.getCellY() + lplhs.cellVSpan);
        });

        for (View v: solution.sortedViews) {
            CellAndSpan c = solution.map.get(v);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
            if (!lp.canReorder) {
                // The push solution includes the all apps button, this is not viable.
                break;
            }
            if (cluster.isViewTouchingEdge(v, ViewCluster.LEFT)) {
                if (!cluster.views.contains(v)) {
                    cluster.addView(v);
                }
                mTmpOccupied.markCells(c, false);
                pushIconByRow(c, getCountX(), getCountY(),ViewCluster.LEFT);
            }
        }
        cluster.shift(ViewCluster.LEFT, 1);

        Rect clusterRect = cluster.getBoundingRect();
        boolean isSolution = false;
        if (clusterRect.left >= 0 && clusterRect.right <= mCountX && clusterRect.top >= 0 &&
                clusterRect.bottom <= mCountY) {
            isSolution = true;
        } else {
            solution.restore();
        }

        for (View v: cluster.views) {
            CellAndSpan c = solution.map.get(v);
            mTmpOccupied.markCells(c, true);
        }

        if (isSolution) {
            solution.cellX = intersecting[0];
            solution.cellY = intersecting[1];
            solution.spanX = 1;
            solution.spanY = 1;
            solution.isSolution = true;

            if (views.size() > 0 && views.get(0) != null) {
                // Pass null so performReorder animates and commits EVERY view in the solution
                // instead of skipping views.get(0).
                performReorder(solution, null, MODE_ON_DROP);
            }
        }
        return isSolution;
    }

    /**
     * Compacts all reorderable 1x1 icons in reading order (top-to-bottom, left-to-right)
     * around fixed widgets in a single pass, animating and committing all moves at once.
     */
    public boolean compactIconsInReadingOrder() {
        // Disabled: preserve freeform placement of apps anywhere on the grid (iOS 18 style).
        return false;
    }

    public void pushIconByRow(CellAndSpan c, int countX, int countY, int whichEdge) {
        if (whichEdge == ViewCluster.LEFT) {
            if (c.cellX == 0 && c.cellY - c.spanY >= 0) {
                c.cellY = c.cellY - c.spanY;
                c.cellX = countX;
            }
            int result = isCellInLauncherAppWidget(c.cellX - 1, c.cellY, ViewCluster.LEFT);
            if (result != -1) {
                c.cellX = result;
                while (c.cellX == 0 && c.cellY - c.spanY >= 0) {
                    c.cellY = c.cellY - c.spanY;
                    c.cellX = countX;
                    int cellX = isCellInLauncherAppWidget(c.cellX - 1, c.cellY, ViewCluster.LEFT);
                    if (cellX != -1) {
                        c.cellX = cellX;
                    }
                }
            }
        } else if (whichEdge == ViewCluster.RIGHT) {
            if (c.cellX == countX - 1 && c.cellY + c.spanY <= countY - 1) {
                c.cellY = c.cellY + c.spanY;
                c.cellX = -1;
            }
            int result = isCellInLauncherAppWidget(c.cellX + 1, c.cellY, ViewCluster.RIGHT);
            if (result != -1) {
                c.cellX = result;
                while (c.cellX == countX - 1 && c.cellY + c.spanY <= countY - 1) {
                    c.cellY = c.cellY + c.spanY;
                    c.cellX = -1;
                    int cellX = isCellInLauncherAppWidget(c.cellX + 1, c.cellY, ViewCluster.RIGHT);
                    if (cellX != -1) {
                        c.cellX = cellX;
                    }
                }
            }
        }
    }

    private int isCellInLauncherAppWidget(int x, int y, int whichEdge) {
        for (CellAndSpan c : mWidgetCellAndSpanList) {
            if (x >= c.cellX && x < c.cellX + c.spanX && y >= c.cellY && y < c.cellY + c.spanY) {
                if (whichEdge == ViewCluster.LEFT) {
                    return c.cellX;
                } else if (whichEdge == ViewCluster.RIGHT) {
                    return c.cellX + c.spanX - 1;
                }
            }
        }
        return -1;
    }

    public int[] getLastOccupiedCells() {
        int[] loc = new int[]{-1, -1};
        out:
        for (int y = mCountY - 1; y >= 0; y--) {
            for (int x = mCountX - 1; x >= 0; x--) {
                if (isOccupied(x, y)) {
                    loc[0] = x;
                    loc[1] = y;
                    break out;
                }
            }
        }
        return loc;
    }

    /**
     * When the user drags an Item in the workspace sometimes we need to move the items already in
     * the workspace to make space for the new item, this function return a solution for that
     * reorder.
     *
     * @param pixelX X coordinate in the screen of the dragView in pixels
     * @param pixelY Y coordinate in the screen of the dragView in pixels
     * @param minSpanX minimum horizontal span the item can be shrunk to
     * @param minSpanY minimum vertical span the item can be shrunk to
     * @param spanX occupied horizontal span
     * @param spanY occupied vertical span
     * @param dragView the view of the item being draged
     * @return returns a solution for the given parameters, the solution contains all the icons and
     *         the locations they should be in the given solution.
     */
    public ItemConfiguration calculateReorder(int pixelX, int pixelY, int minSpanX, int minSpanY,
            int spanX, int spanY, View dragView) {
        ItemConfiguration configuration = new ItemConfiguration();
        copyCurrentStateToSolution(configuration);
        return createReorderAlgorithm().calculateReorder(
                new ReorderParameters(pixelX, pixelY, spanX, spanY,  minSpanX, minSpanY, dragView,
                        configuration)
        );
    }

    int[] performReorder(int pixelX, int pixelY, int minSpanX, int minSpanY, int spanX, int spanY,
            View dragView, int[] result, int[] resultSpan, int mode) {
        if (resultSpan == null) resultSpan = new int[]{-1, -1};
        if (result == null) result = new int[]{-1, -1};

        final boolean isDrop = mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL;

        ItemConfiguration finalSolution = calculateReorder(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY,
                dragView);
        mPreviousSolution = finalSolution;
        if (isDrop) {
            mPreviousSolution = null;
        }

        if (finalSolution == null || !finalSolution.isSolution) {
            DragTrace.log("CellLayout.performReorder mode=" + mode + " no-solution layout=" + System.identityHashCode(this));
            restoreOverflowPreview();
            if (isDrop) mPendingOverflow = null;
            result[0] = result[1] = resultSpan[0] = resultSpan[1] = -1;
        } else {
            DragTrace.log("CellLayout.performReorder mode=" + mode + " solution=" + System.identityHashCode(finalSolution)
                    + " target=(" + finalSolution.cellX + "," + finalSolution.cellY + ") layout=" + System.identityHashCode(this));
            result[0] = finalSolution.cellX;
            result[1] = finalSolution.cellY;
            resultSpan[0] = finalSolution.spanX;
            resultSpan[1] = finalSolution.spanY;
            handleOverflow(finalSolution, isDrop);
            performReorder(finalSolution, dragView, mode);
        }
        return result;
    }

    /** A cached solution is only valid if it describes exactly the current set of children. */
    private boolean isSolutionCurrent(ItemConfiguration s, View dragView) {
        int children = 0;
        for (int i = 0; i < mShortcutsAndWidgets.getChildCount(); i++) {
            View c = mShortcutsAndWidgets.getChildAt(i);
            if (c == dragView) continue;
            if (!s.map.containsKey(c)) {
                // Views that were pushed off-screen into overflow are legitimately absent.
                if (!s.overflowViews.contains(c)) return false;
            } else {
                children++;
            }
        }
        int mapped = s.map.size() - (dragView != null && s.map.containsKey(dragView) ? 1 : 0);
        return mapped == children;
    }

    private void handleOverflow(ItemConfiguration s, boolean isDrop) {
        restoreOverflowPreview();
        ArrayList<View> real = new ArrayList<>();
        for (View v : s.overflowViews) {
            if (!s.map.containsKey(v)) real.add(v);   // really removed from the solution
        }
        if (isDrop) {
            mPendingOverflow = real.isEmpty() ? null : real;
        } else {
            for (View v : real) {                      // hover preview: dim them
                v.setAlpha(0.25f);
                mOverflowPreview.add(v);
            }
        }
    }

    private void restoreOverflowPreview() {
        for (View v : mOverflowPreview) v.setAlpha(1f);
        mOverflowPreview.clear();
    }

    /** Called by Workspace.onDrop right after performReorder(MODE_ON_DROP). */
    public List<View> consumeOverflowViews() {
        restoreOverflowPreview();
        List<View> r = mPendingOverflow;
        mPendingOverflow = null;
        return r == null ? Collections.<View>emptyList() : r;
    }

    /**
     * Animates and submits in the DB the given ItemConfiguration depending of the mode.
     *
     * @param solution represents widgets on the screen which the Workspace will animate to and
     * would be submitted to the database.
     * @param dragView view which is being dragged over the workspace that trigger the reorder
     * @param mode depending on the mode different animations would be played and depending on the
     *             mode the solution would be submitted or not the database.
     *             The possible modes are {@link MODE_SHOW_REORDER_HINT}, {@link MODE_DRAG_OVER},
     *             {@link MODE_ON_DROP}, {@link MODE_ON_DROP_EXTERNAL}, {@link  MODE_ACCEPT_DROP}
     *             defined in {@link CellLayout}.
     */
    public void performReorder(ItemConfiguration solution, View dragView, int mode) {
        if (mode == MODE_SHOW_REORDER_HINT) {
            return;
        }
        // If we're just testing for a possible location (MODE_ACCEPT_DROP), we don't bother
        // committing anything or animating anything as we just want to determine if a solution
        // exists
        if (mode == MODE_DRAG_OVER) {
            copySolutionToTempState(solution, dragView);
            setItemPlacementDirty(true);
            animateItemsToSolution(solution, dragView, false);
        } else if (mode == MODE_ON_DROP || mode == MODE_ON_DROP_EXTERNAL) {
            copySolutionToTempState(solution, dragView);
            commitTempPlacement(solution, dragView);
            completeAndClearReorderPreviewAnimations();
            setItemPlacementDirty(false);
            mShortcutsAndWidgets.requestLayout();
        }
    }

    void setItemPlacementDirty(boolean dirty) {
        mItemPlacementDirty = dirty;
    }
    boolean isItemPlacementDirty() {
        return mItemPlacementDirty;
    }

    /**
     * Find a starting cell position that will fit the given bounds nearest the requested
     * cell location. Uses Euclidean distance to score multiple vacant areas.
     *
     * @param pixelX The X location at which you want to search for a vacant area.
     * @param pixelY The Y location at which you want to search for a vacant area.
     * @param spanX Horizontal span of the object.
     * @param spanY Vertical span of the object.
     * @param result Previously returned value to possibly recycle.
     * @return The X, Y cell of a vacant area that can contain this object,
     *         nearest the requested location.
     */
    public int[] findNearestAreaIgnoreOccupied(int pixelX, int pixelY, int spanX, int spanY,
            int[] result) {
        return findNearestArea(pixelX, pixelY, spanX, spanY, spanX, spanY, true, result, null);
    }

    boolean existsEmptyCell() {
        return findCellForSpan(null, 1, 1);
    }

    /**
     * Finds the upper-left coordinate of the first rectangle in the grid that can
     * hold a cell of the specified dimensions. If intersectX and intersectY are not -1,
     * then this method will only return coordinates for rectangles that contain the cell
     * (intersectX, intersectY)
     *
     * @param cellXY The array that will contain the position of a vacant cell if such a cell
     *               can be found.
     * @param spanX The horizontal span of the cell we want to find.
     * @param spanY The vertical span of the cell we want to find.
     *
     * @return True if a vacant cell of the specified dimension was found, false otherwise.
     */
    public boolean findCellForSpan(int[] cellXY, int spanX, int spanY) {
        if (cellXY == null) {
            cellXY = new int[2];
        }
        return mOccupied.findVacantCell(cellXY, spanX, spanY);
    }

    /**
     * A drag event has begun over this layout.
     * It may have begun over this layout (in which case onDragChild is called first),
     * or it may have begun on another layout.
     */
    void onDragEnter() {
        mDragging = true;
        mPreviousSolution = null;
    }

    /**
     * Called when drag has left this CellLayout or has been completed (successfully or not)
     */
    void onDragExit() {
        onDragExit(true);
    }

    void onDragExit(boolean revertTemp) {
        DragTrace.log("CellLayout.onDragExit revertTemp=" + revertTemp + " layout=" + System.identityHashCode(this));
        // This can actually be called when we aren't in a drag, e.g. when adding a new
        // item to this layout via the customize drawer.
        // Guard against that case.
        if (mDragging) {
            mDragging = false;
        }

        // Invalidate the drag data
        if (revertTemp) {
            mPreviousSolution = null;
            mDragCell[0] = mDragCell[1] = -1;
            mDragCellSpan[0] = mDragCellSpan[1] = -1;
        }
        mDragOutlineAnims[mDragOutlineCurrent].animateOut();
        mDragOutlineCurrent = (mDragOutlineCurrent + 1) % mDragOutlineAnims.length;
        if (revertTemp) {
            revertTempState();
        }
        setIsDragOverlapping(false);
    }

    /**
     * Mark a child as having been dropped.
     * At the beginning of the drag operation, the child may have been on another
     * screen, but it is re-parented before this method is called.
     *
     * @param child The child that is being dropped
     */
    void onDropChild(View child) {
        if (child != null) {
            CellLayoutLayoutParams
                    lp = (CellLayoutLayoutParams) child.getLayoutParams();
            lp.dropped = true;
            child.requestLayout();
            markCellsAsOccupiedForView(child);
        }
    }

    /**
     * Computes a bounding rectangle for a range of cells
     *
     * @param cellX X coordinate of upper left corner expressed as a cell position
     * @param cellY Y coordinate of upper left corner expressed as a cell position
     * @param cellHSpan Width in cells
     * @param cellVSpan Height in cells
     * @param resultRect Rect into which to put the results
     */
    public void cellToRect(int cellX, int cellY, int cellHSpan, int cellVSpan, Rect resultRect) {
        final int cellWidth = mCellWidth;
        final int cellHeight = mCellHeight;

        // We observe a shift of 1 pixel on the x coordinate compared to the actual cell coordinates
        final int hStartPadding = getPaddingLeft()
                + (int) Math.ceil(getUnusedHorizontalSpace() / 2f);
        final int vStartPadding = getPaddingTop();

        int x = hStartPadding + (cellX * mBorderSpace.x) + (cellX * cellWidth);
        int y = vStartPadding + (cellY * mBorderSpace.y) + (cellY * cellHeight);

        int width = cellHSpan * cellWidth + ((cellHSpan - 1) * mBorderSpace.x);
        int height = cellVSpan * cellHeight + ((cellVSpan - 1) * mBorderSpace.y);

        resultRect.set(x, y, x + width, y + height);
    }

    public void markCellsAsOccupiedForView(View view) {
        markCellsForView(view, true);
    }

    public void markCellsAsUnoccupiedForView(View view) {
        markCellsForView(view, false);
    }

    /** Explicit-coordinates variant, used by Workspace.onDrop after the final cell is known. */
    public void markCellsOccupied(int cellX, int cellY, int spanX, int spanY, boolean occupied) {
        mOccupied.markCells(cellX, cellY, spanX, spanY, occupied);
    }

    private void markCellsForView(View view, boolean occupied) {
        if (view == null) return;

        // 1) A real child of THIS layout: LayoutParams are the source of truth.
        if (view.getParent() == mShortcutsAndWidgets
                && view.getLayoutParams() instanceof CellLayoutLayoutParams) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) view.getLayoutParams();
            mOccupied.markCells(lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan, occupied);
            return;
        }

        // 2) A widget that is detached (lives inside the DragView): fall back to the model,
        //    but ONLY if the model says it belongs to this layout.
        if (view instanceof LauncherAppWidgetHostView
                && view.getTag() instanceof LauncherAppWidgetInfo) {
            LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
            CellPos pos = mActivity.getCellPosMapper().mapModelToPresenter(info);
            if (isModelItemOnThisLayout(info, pos)) {
                mOccupied.markCells(pos.cellX, pos.cellY, info.spanX, info.spanY, occupied);
            }
        }
    }

    private boolean isModelItemOnThisLayout(ItemInfo info, CellPos pos) {
        if (mContainerType == HOTSEAT) {
            return info.container == Favorites.CONTAINER_HOTSEAT;
        }
        return info.container == Favorites.CONTAINER_DESKTOP
                && mCellLayoutContainer != null
                && pos.screenId == mCellLayoutContainer.getCellLayoutId(this);
    }

    public int getDesiredWidth() {
        return getPaddingLeft() + getPaddingRight() + (mCountX * mCellWidth)
                + ((mCountX - 1) * mBorderSpace.x);
    }

    public int getDesiredHeight()  {
        return getPaddingTop() + getPaddingBottom() + (mCountY * mCellHeight)
                + ((mCountY - 1) * mBorderSpace.y);
    }

    public boolean isOccupied(int x, int y) {
        if (x >= 0 && x < mCountX && y >= 0 && y < mCountY) {
            return mOccupied.cells[x][y];
        }
        if (BuildConfig.IS_STUDIO_BUILD) {
            throw new RuntimeException("Position exceeds the bound of this CellLayout");
        }
        return true;
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new CellLayoutLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof CellLayoutLayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new CellLayoutLayoutParams(p);
    }

    /**
     * Returns whether an item can be placed in this CellLayout (after rearranging and/or resizing
     * if necessary).
     */
    public boolean hasReorderSolution(ItemInfo itemInfo) {
        int[] cellPoint = new int[2];
        // Check for a solution starting at every cell.
        for (int cellX = 0; cellX < getCountX(); cellX++) {
            for (int cellY = 0; cellY < getCountY(); cellY++) {
                cellToPoint(cellX, cellY, cellPoint);
                if (findReorderSolution(cellPoint[0], cellPoint[1], itemInfo.minSpanX,
                        itemInfo.minSpanY, itemInfo.spanX, itemInfo.spanY, mDirectionVector, null,
                        true).isSolution) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds solution to accept hotseat migration to cell layout. commits solution if commitConfig
     */
    public boolean makeSpaceForHotseatMigration(boolean commitConfig) {
        int[] cellPoint = new int[2];
        int[] directionVector = new int[]{0, -1};
        cellToPoint(0, mCountY, cellPoint);
        ItemConfiguration configuration = findReorderSolution(
                cellPoint[0] /* pixelX */,
                cellPoint[1] /* pixelY */,
                mCountX /* minSpanX */,
                1 /* minSpanY */,
                mCountX /* spanX */,
                1 /* spanY */,
                directionVector /* direction */,
                null /* dragView */,
                false /* decX */
        );
        if (configuration.isSolution) {
            if (commitConfig) {
                copySolutionToTempState(configuration, null);
                commitTempPlacement(configuration, null);
                // undo marking cells occupied since there is actually nothing being placed yet.
                mOccupied.markCells(0, mCountY - 1, mCountX, 1, false);
            }
            return true;
        }
        return false;
    }

    /**
     * returns a copy of cell layout's grid occupancy
     */
    public GridOccupancy cloneGridOccupancy() {
        GridOccupancy occupancy = new GridOccupancy(mCountX, mCountY);
        mOccupied.copyTo(occupancy);
        return occupancy;
    }

    public boolean isRegionVacant(int x, int y, int spanX, int spanY) {
        return mOccupied.isRegionVacant(x, y, spanX, spanY);
    }

    public void setSpaceBetweenCellLayoutsPx(@Px int spaceBetweenCellLayoutsPx) {
        mSpaceBetweenCellLayoutsPx = spaceBetweenCellLayoutsPx;
    }
}
```

---

# src/com/android/launcher3/ShortcutAndWidgetContainer.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/ShortcutAndWidgetContainer.java`  
**Lines**: 387 | **Bytes**: 14893

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.launcher3.CellLayout.FOLDER;
import static com.android.launcher3.CellLayout.HOTSEAT;
import static com.android.launcher3.CellLayout.WORKSPACE;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_PREVIEW_OFFSET;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_WIDGET_CENTERING;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Trace;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.NavigableAppWidgetHostView;

import foundation.e.bliss.folder.GridFolder;

public class ShortcutAndWidgetContainer extends ViewGroup implements FolderIcon.FolderIconParent {
    static final String TAG = "ShortcutAndWidgetContainer";

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private final int[] mTmpCellXY = new int[2];

    @ContainerType
    private final int mContainerType;
    private final WallpaperManager mWallpaperManager;

    private int mCellWidth;
    private int mCellHeight;
    private Point mBorderSpace;

    private int mCountX;
    private int mCountY;

    private final ActivityContext mActivity;
    private boolean mInvertIfRtl = false;

    @Nullable
    private TranslationProvider mTranslationProvider = null;

    public ShortcutAndWidgetContainer(Context context, @ContainerType int containerType) {
        super(context);
        mActivity = ActivityContext.lookupContext(context);
        mWallpaperManager = WallpaperManager.getInstance(context);
        mContainerType = containerType;
        setClipChildren(false);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int countX, int countY,
            Point borderSpace) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mCountX = countX;
        mCountY = countY;
        mBorderSpace = borderSpace;
    }

    public View getChildAt(int cellX, int cellY) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();

            if ((lp.getCellX() <= cellX) && (cellX < lp.getCellX() + lp.cellHSpan)
                    && (lp.getCellY() <= cellY) && (cellY < lp.getCellY() + lp.cellVSpan)) {
                return child;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();

        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSpecSize, heightSpecSize);

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child);
            }
        }
    }

    /**
     * Adds view to Layout a new position and it does not trigger a layout request.
     * For more information check documentation for
     * {@code ViewGroup#addViewInLayout(View, int, LayoutParams, boolean)}
     *
     * @param child view to be added
     * @return true if the child was added, false otherwise
     */
    public boolean addViewInLayout(View child, LayoutParams layoutParams) {
        return super.addViewInLayout(child, -1, layoutParams, true);
    }

    public void setupLp(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (child instanceof NavigableAppWidgetHostView) {
            DeviceProfile profile = mActivity.getDeviceProfile();
            final PointF appWidgetScale = profile.getAppWidgetScale((ItemInfo) child.getTag());
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    appWidgetScale.x, appWidgetScale.y, mBorderSpace, profile.widgetPadding);
        } else {
            lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                    mBorderSpace);
        }
    }

    // Set whether or not to invert the layout horizontally if the layout is in RTL mode.
    public void setInvertIfRtl(boolean invert) {
        mInvertIfRtl = invert;
    }

    public int getCellContentHeight() {
        return Math.min(getMeasuredHeight(),
                mActivity.getDeviceProfile().getCellContentHeight(mContainerType));
    }

    public void measureChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (!lp.isFullscreen) {
            final DeviceProfile dp = mActivity.getDeviceProfile();

            if (child instanceof NavigableAppWidgetHostView) {
                final PointF appWidgetScale = dp.getAppWidgetScale((ItemInfo) child.getTag());
                lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                        appWidgetScale.x, appWidgetScale.y, mBorderSpace, dp.widgetPadding);
            } else {
                lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX, mCountY,
                        mBorderSpace);
                // Center the icon/folder
                int cHeight = getCellContentHeight();
                int cellPaddingY =
                        dp.cellYPaddingPx >= 0 && mContainerType == WORKSPACE
                                ? dp.cellYPaddingPx
                                : (int) Math.max(0, ((lp.height - cHeight) / 2f));


                // No need to add padding when cell layout border spacing is present.
                boolean noPaddingX =
                        (dp.cellLayoutBorderSpacePx.x > 0 && mContainerType == WORKSPACE)
                                || (dp.folderCellLayoutBorderSpacePx.x > 0 && mContainerType == FOLDER)
                                || (dp.hotseatBorderSpace > 0 && mContainerType == HOTSEAT);
                int cellPaddingX = noPaddingX
                        ? 0
                        : mContainerType == WORKSPACE
                        ? dp.workspaceCellPaddingXPx
                        : (int) (dp.edgeMarginPx / 2f);
                child.setPadding(cellPaddingX, cellPaddingY, cellPaddingX, 0);
            }
        } else {
            lp.x = 0;
            lp.y = 0;
            lp.width = getMeasuredWidth();
            lp.height = getMeasuredHeight();
        }
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
    }

    public boolean invertLayoutHorizontally() {
        return mInvertIfRtl && Utilities.isRtl(getResources());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Trace.beginSection("ShortcutAndWidgetConteiner#onLayout");
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                layoutChild(child);
            }
        }
        Trace.endSection();
    }

    /**
     * Core logic to layout a child for this ViewGroup.
     */
    public void layoutChild(View child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (child instanceof NavigableAppWidgetHostView) {
            NavigableAppWidgetHostView nahv = (NavigableAppWidgetHostView) child;

            // Scale and center the widget to fit within its cells.
            DeviceProfile profile = mActivity.getDeviceProfile();
            final PointF appWidgetScale = profile.getAppWidgetScale((ItemInfo) child.getTag());
            float scaleX = appWidgetScale.x;
            float scaleY = appWidgetScale.y;

            nahv.setScaleToFit(Math.min(scaleX, scaleY));
            nahv.getTranslateDelegate().setTranslation(INDEX_WIDGET_CENTERING,
                    -(lp.width - (lp.width * scaleX)) / 2.0f,
                    -(lp.height - (lp.height * scaleY)) / 2.0f);
        }

        int childLeft = lp.x;
        int childTop = lp.y;

        // We want to get the layout position of the widget, but layout() is a final function in
        // ViewGroup which makes it impossible to be overridden. Overriding onLayout() will have no
        // effect since it will not be called when the transition is enabled. The only possible
        // solution here seems to be sending the positions when CellLayout is laying out the views
        if (child instanceof LauncherAppWidgetHostView widgetView
                && widgetView.getCellChildViewPreLayoutListener() != null) {
            widgetView.getCellChildViewPreLayoutListener().notifyBoundChangeOnPreLayout(child,
                    childLeft, childTop, childLeft + lp.width, childTop + lp.height);
        }
        child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);
        if (mTranslationProvider != null) {
            final float tx = mTranslationProvider.getTranslationX(child);
            if (child instanceof Reorderable) {
                ((Reorderable) child).getTranslateDelegate()
                        .getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM)
                        .setValue(tx);
            } else {
                child.setTranslationX(tx);
            }
        }

        if (lp.dropped) {
            lp.dropped = false;

            final int[] cellXY = mTmpCellXY;
            getLocationOnScreen(cellXY);
            mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                    WallpaperManager.COMMAND_DROP,
                    cellXY[0] + childLeft + lp.width / 2,
                    cellXY[1] + childTop + lp.height / 2, 0, null);
        }
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == ACTION_DOWN && getAlpha() == 0) {
            // Dont let children handle touch, if we are not visible.
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (child != null) {
            Rect r = new Rect();
            child.getDrawingRect(r);
            requestRectangleOnScreen(r);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        // Cancel long press for all children
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.cancelLongPress();
        }
    }

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
        if (!(child.getFolder() instanceof GridFolder)) {
            // While the folder is open, the position of the icon cannot change.
            // With Grid folder and auto reorder it is necessary we change position
            lp.canReorder = false;
        }
        if (mContainerType == HOTSEAT) {
            CellLayout cl = (CellLayout) getParent();
            cl.setFolderLeaveBehindCell(lp.getCellX(), lp.getCellY());
        }
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        ((CellLayoutLayoutParams) child.getLayoutParams()).canReorder = true;
        if (mContainerType == HOTSEAT) {
            CellLayout cl = (CellLayout) getParent();
            cl.clearFolderLeaveBehind();
        }
    }

    private void cleanupChildBeforeRemoval(View child) {
        if (child != null) {
            child.clearAnimation();
            child.setRotation(0f);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            if (child instanceof Reorderable) {
                ((Reorderable) child).getTranslateDelegate().setTranslation(
                        INDEX_REORDER_PREVIEW_OFFSET, 0f, 0f);
            }
        }
    }

    @Override
    public void removeView(View view) {
        cleanupChildBeforeRemoval(view);
        super.removeView(view);
    }

    @Override
    public void removeViewInLayout(View view) {
        cleanupChildBeforeRemoval(view);
        super.removeViewInLayout(view);
    }

    @Override
    public void removeViewAt(int index) {
        View view = getChildAt(index);
        cleanupChildBeforeRemoval(view);
        super.removeViewAt(index);
    }

    @Override
    public void removeAllViews() {
        for (int i = 0; i < getChildCount(); i++) cleanupChildBeforeRemoval(getChildAt(i));
        super.removeAllViews();
    }

    @Override
    public void removeAllViewsInLayout() {
        for (int i = 0; i < getChildCount(); i++) cleanupChildBeforeRemoval(getChildAt(i));
        super.removeAllViewsInLayout();
    }

    void setTranslationProvider(@Nullable TranslationProvider provider) {
        mTranslationProvider = provider;
    }

    /** Provides translation values to apply when laying out child views. */
    interface TranslationProvider {
        float getTranslationX(View child);
    }
}
```

---

# src/com/android/launcher3/BubbleTextView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/BubbleTextView.java`  
**Lines**: 1537 | **Bytes**: 59174

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static android.text.Layout.Alignment.ALIGN_NORMAL;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.InvariantDeviceProfile.KEY_ALLAPPS_THEMED_ICONS;
import static com.android.launcher3.InvariantDeviceProfile.KEY_SHOW_DESKTOP_LABELS;
import static com.android.launcher3.InvariantDeviceProfile.KEY_SHOW_DRAWER_LABELS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.BitmapInfo.FLAG_NO_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_SKIP_USER_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
import static com.android.launcher3.util.PackageManagerHelper.isSystemApp;
import static com.android.launcher3.util.ShortcutUtil.getShortcutIdIfPinnedShortcut;
import static com.android.launcher3.util.ShortcutUtil.isDeepShortcut;

import static foundation.e.bliss.utils.BlissUtilsKt.getUninstallTarget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.text.MessageFormat;
import android.net.Uri;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.PlaceHolderIconDrawable;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconLabelDotView;

import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;

import foundation.e.bliss.LauncherAppMonitor;
import foundation.e.bliss.multimode.MultiModeController;
import foundation.e.bliss.wobble.UninstallButtonRenderer;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver,
        IconLabelDotView, DraggableView, Reorderable {

    private static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_FOLDER = 2;
    protected static final int DISPLAY_TASKBAR = 5;
    public static final int DISPLAY_SEARCH_RESULT = 6;
    public static final int DISPLAY_SEARCH_RESULT_SMALL = 7;
    public static final int DISPLAY_PREDICTION_ROW = 8;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;
    public static final int DISPLAY_APP_LIBRARY_EXPANDED = 10;

    private static final float MIN_LETTER_SPACING = -0.05f;
    private static final int MAX_SEARCH_LOOP_COUNT = 20;
    private static final Character NEW_LINE = '\n';
    private static final String EMPTY = "";
    private static final StringMatcherUtility.StringMatcher MATCHER =
            StringMatcherUtility.StringMatcher.getInstance();

    private static final int[] STATE_PRESSED = new int[]{android.R.attr.state_pressed};

    private float mScaleForReorderBounce = 1f;

    private IntArray mBreakPointsIntArray;
    private CharSequence mLastOriginalText;
    private CharSequence mLastModifiedText;

    private int mStartDragThreshold;

    private UninstallButtonRenderer mUninstallButtonRenderer;

    private int touchX = 0;
    private int touchY = 0;

    public boolean isUninstallVisible = false;
    private boolean mDownOnMinusBadge = false;

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    public static final Property<BubbleTextView, Float> TEXT_ALPHA_PROPERTY
            = new Property<BubbleTextView, Float>(Float.class, "textAlpha") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mTextAlpha;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float alpha) {
            bubbleTextView.setTextAlpha(alpha);
        }
    };

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private final ActivityContext mActivity;
    private FastBitmapDrawable mIcon;
    private DeviceProfile mDeviceProfile;
    private boolean mCenterVertically;

    protected int mDisplay;

    private final CheckLongPressHelper mLongPressHelper;

    private boolean mLayoutHorizontal;
    private final boolean mIsRtl;
    private final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHideBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mSkipUserBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private ColorStateList mTextColorStateList;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTextAlpha = 1;

    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    private DotRenderer mDotRenderer;
    private Locale mCurrentLocale;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    protected DotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mStayPressed;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIgnorePressedStateChange;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisableRelayout = false;

    private boolean mShouldShowLabel;
    private boolean mThemeAllAppsIcons;

    private CancellableTask mIconLoadRequest;

    private boolean mEnableIconUpdateAnimation = false;

    private Launcher mLauncher;
    private final Rect mTempIconBounds = new Rect();
    private final Paint.FontMetrics mTempFontMetrics = new Paint.FontMetrics();

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        FastBitmapDrawable.setFlagHoverEnabled(enableCursorHoverStates());

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        mDeviceProfile = mActivity.getDeviceProfile();
        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, MultiModeController.isSingleLayerMode());

        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        final int defaultIconSize;
        if (mDisplay == DISPLAY_WORKSPACE) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mDeviceProfile.iconTextSizePx);
            setCompoundDrawablePadding(mDeviceProfile.iconDrawablePaddingPx);
            defaultIconSize = mDeviceProfile.iconSizePx;
            setCenterVertically(mDeviceProfile.iconCenterVertically);
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else if (mDisplay == DISPLAY_ALL_APPS || mDisplay == DISPLAY_PREDICTION_ROW
                || mDisplay == DISPLAY_SEARCH_RESULT_APP_ROW) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mDeviceProfile.allAppsIconTextSizePx);
            setCompoundDrawablePadding(mDeviceProfile.allAppsIconDrawablePaddingPx);
            defaultIconSize = mDeviceProfile.allAppsIconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DRAWER_LABELS, true);
            mThemeAllAppsIcons = prefs.getBoolean(KEY_ALLAPPS_THEMED_ICONS, false);
        } else if (mDisplay == DISPLAY_FOLDER) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mDeviceProfile.folderChildTextSizePx);
            setCompoundDrawablePadding(mDeviceProfile.folderChildDrawablePaddingPx);
            defaultIconSize = mDeviceProfile.folderChildIconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mDeviceProfile.allAppsIconTextSizePx);
            defaultIconSize = getResources().getDimensionPixelSize(R.dimen.search_row_icon_size);
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            defaultIconSize = getResources().getDimensionPixelSize(
                    R.dimen.search_row_small_icon_size);
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else if (mDisplay == DISPLAY_TASKBAR) {
            defaultIconSize = mDeviceProfile.iconSizePx;
        } else {
            // widget_selection or shortcut_popup
            defaultIconSize = mDeviceProfile.iconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        }

        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mLongPressHelper = new CheckLongPressHelper(this);

        mDotParams = new DotRenderer.DrawParams();

        mUninstallButtonRenderer = new UninstallButtonRenderer(getContext(), mActivity.getDeviceProfile().iconSizePx);

        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);

        mCurrentLocale = context.getResources().getConfiguration().locale;
        setEllipsize(TruncateAt.END);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        setTextAlpha(1f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Disable marques when not focused to that, so that updating text does not cause relayout.
        setEllipsize(focused ? TruncateAt.MARQUEE : TruncateAt.END);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    public void setHideBadge(boolean hideBadge) {
        mHideBadge = hideBadge;
    }

    public void setSkipUserBadge(boolean skipUserBadge) {
        mSkipUserBadge = skipUserBadge;
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        mDotInfo = null;
        mDotParams.dotColor = Color.TRANSPARENT;
        mDotParams.appColor = Color.TRANSPARENT;
        mDotParams.shadowDotColor = Color.TRANSPARENT;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        applyUninstallIconState(false);
        mDownOnMinusBadge = false;
        setOnTouchListener(null);
        clearAnimation();
        setBackground(null);

        setTag(null);
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        // Reset any shifty arrangements in case animation is disrupted.
        setPivotY(0);
        setAlpha(1);
        setScaleY(1);
        setTranslationY(0);
        setMaxLines(1);
        setVisibility(VISIBLE);
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyFromWorkspaceItem(info, /* animate = */ false, /* staggerIndex = */ 0);
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, boolean animate, int staggerIndex) {
        applyFromWorkspaceItem(info, null);
    }

    /**
     * Returns whether the newInfo differs from the current getTag().
     */
    public boolean shouldAnimateIconChange(WorkspaceItemInfo newInfo) {
        WorkspaceItemInfo oldInfo = getTag() instanceof WorkspaceItemInfo
                ? (WorkspaceItemInfo) getTag()
                : null;
        boolean changedIcons = oldInfo != null && oldInfo.getTargetComponent() != null
                && newInfo.getTargetComponent() != null
                && !oldInfo.getTargetComponent().equals(newInfo.getTargetComponent());
        return changedIcons && isShown();
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof BaseAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info, PreloadIconDrawable icon) {
        applyIconAndLabel(info);
        setItemInfo(info);
        applyLoadingState(icon);
        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);

        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);


        // Verify high res immediately
        verifyHighRes();

        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) != 0) {
            applyProgressLevel();
        }
        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    /**
     * Apply label and tag using a generic {@link ItemInfoWithIcon}
     */
    @UiThread
    public void applyFromItemInfoWithIcon(ItemInfoWithIcon info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
    }

    @VisibleForTesting
    @UiThread
    public void applyIconAndLabel(ItemInfoWithIcon info) {
        int flags = shouldUseTheme() ? FLAG_THEMED : 0;
        // Remove badge on icons smaller than 48dp.
        if (mHideBadge || mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            flags |= FLAG_NO_BADGE;
        }
        if (mSkipUserBadge) {
            flags |= FLAG_SKIP_USER_BADGE;
        }
        FastBitmapDrawable iconDrawable = info.newIcon(getContext(), flags);
        mDotParams.appColor = iconDrawable.getIconColor();
        mDotParams.dotColor = getContext().getResources()
                .getColor(R.color.notification_dot_bg, getContext().getTheme());
        mDotParams.shadowDotColor = getContext().getResources()
                .getColor(R.color.notification_dot_shadow, getContext().getTheme());
        setIcon(iconDrawable);
        applyLabel(info);
    }

    protected boolean shouldUseTheme() {
        return (mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_FOLDER
                || mDisplay == DISPLAY_TASKBAR
                || (mThemeAllAppsIcons && mDisplay == DISPLAY_ALL_APPS))
                && Themes.isThemedIconEnabled(getContext());
    }

    /**
     * Only if actual text can be displayed in two line, the {@code true} value will be effective.
     */
    protected boolean shouldUseTwoLine() {
        return isCurrentLanguageEnglish() && (mDisplay == DISPLAY_ALL_APPS
                || mDisplay == DISPLAY_PREDICTION_ROW) && (Flags.enableTwolineToggle()
                && LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE.get(getContext()));
    }

    protected boolean isCurrentLanguageEnglish() {
        return mCurrentLocale.equals(Locale.US);
    }

    @UiThread
    @VisibleForTesting
    public void applyLabel(ItemInfoWithIcon info) {
        if (mShouldShowLabel) {
            CharSequence label = info.title;
            if (label != null) {
                mLastOriginalText = label;
                mLastModifiedText = mLastOriginalText;
                mBreakPointsIntArray = StringMatcherUtility.getListOfBreakpoints(label, MATCHER);
                setIncludeFontPadding(false);
                setCompoundDrawablePadding(0);
                setText(label);
            }
        }
        if (info.contentDescription != null) {
            setContentDescription(info.isDisabled()
                    ? getContext().getString(R.string.disabled_app_label, info.contentDescription)
                    : info.contentDescription);
        }
    }

    /** This is used for testing to forcefully set the display. */
    @VisibleForTesting
    public void setDisplay(int display) {
        mDisplay = display;
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressHelper.setLongPressTimeoutFactor(longPressTimeoutFactor);
    }

    @Override
    public void refreshDrawableState() {
        if (!mIgnorePressedStateChange) {
            super.refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mStayPressed) {
            mergeDrawableStates(drawableState, STATE_PRESSED);
        }
        return drawableState;
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isUninstallVisible) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mDownOnMinusBadge = isOnUninstallIcon(event.getX(), event.getY());
                if (mDownOnMinusBadge && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            if (mDownOnMinusBadge) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    mDownOnMinusBadge = false;
                    Launcher launcher = Launcher.getLauncher(getContext());
                    if (launcher != null) {
                        showRemoveAppDialog(launcher);
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    mDownOnMinusBadge = false;
                    return true;
                }
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ignore events if they happen in padding area
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchX = (int) event.getX();
            touchY = (int) event.getY();
            // ignore events if they happen in padding area
            if (shouldIgnoreTouchDown(event.getX(), event.getY())) {
                return false;
            }
            if (isLongClickable() && getTag() instanceof com.android.launcher3.model.data.ItemInfo) {
                com.android.launcher3.popup.PopupPopulator.preloadShortcuts(getContext(), (com.android.launcher3.model.data.ItemInfo) getTag());
            }
        }
        if (isLongClickable()) {
            super.onTouchEvent(event);
            mLongPressHelper.onTouchEvent(event);
            // Keep receiving the rest of the events
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    public boolean shouldIgnoreTouchDown(float x, float y) {
        if (isUninstallVisible && isOnUninstallIcon(x, y)) {
            return false;
        }
        if (mDisplay == DISPLAY_TASKBAR) {
            // Allow touching within padding on taskbar, given icon sizes are smaller.
            return false;
        }
        if (mDisplay == DISPLAY_APP_LIBRARY_EXPANDED) {
            getIconBounds(mTempIconBounds);
            // Add 16px (~8dp) hit margin around the icon image and label
            int margin = 16;
            int hitLeft = mTempIconBounds.left - margin;
            int hitRight = mTempIconBounds.right + margin;
            int hitTop = mTempIconBounds.top - margin;
            getPaint().getFontMetrics(mTempFontMetrics);
            int textHeight = getText().length() > 0
                    ? (getCompoundDrawablePadding() + (int) Math.ceil(mTempFontMetrics.bottom - mTempFontMetrics.top))
                    : 0;
            int hitBottom = mTempIconBounds.bottom + textHeight + margin;
            if (x < hitLeft || x > hitRight || y < hitTop || y > hitBottom) {
                return true;
            }
            return false;
        }
        return y < getPaddingTop()
                || x < getPaddingLeft()
                || y > getHeight() - getPaddingBottom()
                || x > getWidth() - getPaddingRight();
    }

    private final Runnable mResetStayPressedRunnable = this::clearPressedBackground;

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        refreshDrawableState();
        removeCallbacks(mResetStayPressedRunnable);
        if (stayPressed) {
            // Guarantee that the icon never remains stuck enlarged if remote transition
            // callbacks are dropped (e.g. OEM ROMs like ColorOS / Realme UI).
            postDelayed(mResetStayPressedRunnable, 400);
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mIcon != null) {
            mIcon.setVisible(isVisible, false);
        }
        if (!isVisible && mStayPressed) {
            clearPressedBackground();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (mStayPressed) {
            clearPressedBackground();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mStayPressed) {
            clearPressedBackground();
        }
    }

    public void clearPressedBackground() {
        removeCallbacks(mResetStayPressedRunnable);
        setPressed(false);
        if (mStayPressed) {
            mStayPressed = false;
            refreshDrawableState();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);
        mIgnorePressedStateChange = false;
        refreshDrawableState();
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkForEllipsis();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        checkForEllipsis();
    }

    private void checkForEllipsis() {
        float width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (width <= 0) {
            return;
        }
        setLetterSpacing(0);

        String text = getText().toString();
        TextPaint paint = getPaint();
        if (paint.measureText(text) < width) {
            return;
        }

        float spacing = findBestSpacingValue(paint, text, width, MIN_LETTER_SPACING);
        // Reset the paint value so that the call to TextView does appropriate diff.
        paint.setLetterSpacing(0);
        setLetterSpacing(spacing);
    }

    /**
     * Find the appropriate text spacing to display the provided text
     *
     * @param paint          the paint used by the text view
     * @param text           the text to display
     * @param allowedWidthPx available space to render the text
     * @param minSpacingEm   minimum spacing allowed between characters
     * @return the final textSpacing value
     * @see #setLetterSpacing(float)
     */
    private float findBestSpacingValue(TextPaint paint, String text, float allowedWidthPx,
            float minSpacingEm) {
        paint.setLetterSpacing(minSpacingEm);
        if (paint.measureText(text) > allowedWidthPx) {
            // If there is no result at high limit, we can do anything more
            return minSpacingEm;
        }

        float lowLimit = 0;
        float highLimit = minSpacingEm;

        for (int i = 0; i < MAX_SEARCH_LOOP_COUNT; i++) {
            float value = (lowLimit + highLimit) / 2;
            paint.setLetterSpacing(value);
            if (paint.measureText(text) < allowedWidthPx) {
                highLimit = value;
            } else {
                lowLimit = value;
            }
        }

        // At the end error on the higher side
        return highLimit;
    }

    @SuppressWarnings("wrongcall")
    protected void drawWithoutDot(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     *
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (isUninstallVisible) {
            drawUninstallIcon(canvas);
        }
        if (!mForceHideDot && (hasDot() || mDotParams.scale > 0)) {
            getIconBounds(mDotParams.iconBounds);
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds,
                    IconShape.getNormalizationScale());
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotParams.leftAlign = false;
            mDotRenderer.draw(canvas, mDotParams, mDotInfo == null ? -1 : mDotInfo.getNotificationCount());
            canvas.translate(-scrollX, -scrollY);
        }
    }

    private void drawUninstallIcon(Canvas canvas) {
        ItemInfo tag = (ItemInfo) this.getTag();
        if (tag == null || tag.itemType == ITEM_TYPE_FOLDER || mUninstallButtonRenderer == null) {
            return;
        }

        getIconBounds(mTempIconBounds);

        final int scrollX = getScrollX();
        final int scrollY = getScrollY();

        canvas.translate(scrollX, scrollY);
        mUninstallButtonRenderer.draw(canvas, mTempIconBounds);
        canvas.translate(-scrollX, -scrollY);
    }

    public void applyUninstallIconState(boolean showUninstallIcon) {
        boolean wasUninstallVisible = isUninstallVisible;
        isUninstallVisible = showUninstallIcon;

        if (wasUninstallVisible || isUninstallVisible) {
            invalidate();
        }
    }

    public boolean isOnUninstallIcon(float x, float y) {
        if (!isUninstallVisible || mUninstallButtonRenderer == null) return false;
        ItemInfo tag = (ItemInfo) this.getTag();
        if (tag == null || tag.itemType == ITEM_TYPE_FOLDER) return false;
        getIconBounds(mTempIconBounds);
        Rect uninstallIconBounds = mUninstallButtonRenderer.getBoundsScaled(mTempIconBounds);
        return uninstallIconBounds.contains((int) x, (int) y);
    }

    public void showRemoveAppDialog(Launcher launcher) {
        ItemInfo tag = (ItemInfo) this.getTag();
        if (tag == null) return;

        Context context = getContext();
        CharSequence title = tag.title != null ? tag.title : "App";

        ComponentName cn = getUninstallTarget(launcher, tag);
        boolean canUninstall = (cn != null);

        if (launcher.getWorkspace() != null) {
            launcher.getWorkspace().pauseWobbleAlarm();
        }

        foundation.e.bliss.glass.LiquidGlassAlertView.showRemoveAppAlert(
                launcher,
                title,
                canUninstall,
                () -> {
                    try {
                        SecondaryDropTarget.performUninstall(context, cn, tag);
                    } catch (Exception e) {
                        android.util.Log.e("BubbleTextView", "Error deleting app", e);
                    }
                },
                () -> {
                    try {
                        clearAnimation();
                        if (tag instanceof ItemInfo) {
                            ComponentName targetCn = ((ItemInfo) tag).getTargetComponent();
                            if (targetCn != null) {
                                com.android.launcher3.util.RemovedAppStore.markRemoved(context, targetCn.flattenToString());
                            }
                        }
                        launcher.removeItem(this, tag, true /* deleteFromDb */, "User removed from home screen");
                    } catch (Exception e) {
                        android.util.Log.e("BubbleTextView", "Error removing item from home screen", e);
                    }
                },
                () -> {
                    if (launcher.getWorkspace() != null) {
                        launcher.getWorkspace().resetWobbleAlarm();
                    }
                }
        );
    }

    public boolean tryToHandleUninstallClick(Launcher launcher) {
        if (!isUninstallVisible) return false;
        if (isOnUninstallIcon(touchX, touchY)) {
            showRemoveAppDialog(launcher);
            touchX = 0;
            touchY = 0;
            return true;
        }
        return false;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    @VisibleForTesting
    public boolean getForceHideDot() {
        return mForceHideDot;
    }

    public boolean hasDot() {
        return mDotInfo != null;
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(Rect outBounds) {
        getIconBounds(mIconSize, outBounds);
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        outBounds.set(0, 0, iconSize, iconSize);
        if (mLayoutHorizontal) {
            int top = (getHeight() - iconSize) / 2;
            if (mIsRtl) {
                outBounds.offsetTo(getWidth() - iconSize - getPaddingRight(), top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), top);
            }
        } else {
            outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
        }
    }

    /**
     * Sets whether the layout is horizontal.
     */
    public void setLayoutHorizontal(boolean layoutHorizontal) {
        if (mLayoutHorizontal == layoutHorizontal) {
            return;
        }

        mLayoutHorizontal = layoutHorizontal;
        applyCompoundDrawables(getIconOrTransparentColor());
    }

    /**
     * Sets whether to vertically center the content.
     */
    public void setCenterVertically(boolean centerVertically) {
        mCenterVertically = centerVertically;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mCenterVertically && (getTag() != null && !(getTag() instanceof FolderInfo))) {
            int cellHeightPx = mIconSize;
            if (getText().length() > 0) {
                getPaint().getFontMetrics(mTempFontMetrics);
                cellHeightPx += getCompoundDrawablePadding() + (int) Math.ceil(mTempFontMetrics.bottom - mTempFontMetrics.top);
            }
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        // Only apply two line for all_apps and device search only if necessary.
        if (shouldUseTwoLine() && (mLastOriginalText != null)) {
            int allowedVerticalSpace = height - getPaddingTop() - getPaddingBottom()
                    - mDeviceProfile.allAppsIconSizePx
                    - mDeviceProfile.allAppsIconDrawablePaddingPx;
            CharSequence modifiedString = modifyTitleToSupportMultiLine(
                    MeasureSpec.getSize(widthMeasureSpec) - getCompoundPaddingLeft()
                            - getCompoundPaddingRight(),
                    allowedVerticalSpace,
                    mLastOriginalText,
                    getPaint(),
                    mBreakPointsIntArray,
                    getLineSpacingMultiplier(),
                    getLineSpacingExtra());
            if (!TextUtils.equals(modifiedString, mLastModifiedText)) {
                mLastModifiedText = modifiedString;
                setText(modifiedString);
                // if text contains NEW_LINE, set max lines to 2
                if (TextUtils.indexOf(modifiedString, NEW_LINE) != -1) {
                    setSingleLine(false);
                    setMaxLines(2);
                } else {
                    setSingleLine(true);
                    setMaxLines(1);
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        mTextColorStateList = null;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        mTextColorStateList = colors;
        if (Float.compare(mTextAlpha, 1) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    public boolean shouldShowLabel() {
        return mShouldShowLabel;
    }

    public boolean shouldTextBeVisible() {
        // Text should be visible everywhere but the hotseat.
        Object tag = getParent() instanceof FolderIcon ? ((View) getParent()).getTag() : getTag();
        ItemInfo info = tag instanceof ItemInfo ? (ItemInfo) tag : null;
        return info == null || (info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT
                && info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION);
    }

    public void setTextVisibility(boolean visible) {
        setTextAlpha(visible ? 1 : 0);
    }

    public void setTextAlpha(float alpha) {
        mTextAlpha = alpha;
        if (mTextColorStateList != null) {
            setTextColor(mTextColorStateList);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    private int getModifiedColor() {
        if (mTextAlpha == 0) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor) * mTextAlpha));
    }

    /**
     * Creates an animator to fade the text in or out.
     *
     * @param fadeIn Whether the text should fade in or fade out.
     */
    public ObjectAnimator createTextAlphaAnimator(boolean fadeIn) {
        float toAlpha = shouldTextBeVisible() && fadeIn ? 1 : 0;
        return ObjectAnimator.ofFloat(this, TEXT_ALPHA_PROPERTY, toAlpha);
    }

    /**
     * Generate a new string that will support two line text depending on the current string.
     * This method calculates the limited width of a text view and creates a string to fit as
     * many words as it can until the limit is reached. Once the limit is reached, we decide to
     * either return the original title or continue on a new line. How to get the new string is by
     * iterating through the list of break points and determining if the strings between the break
     * points can fit within the line it is in. We will show the modified string if there is enough
     * horizontal and vertical space, otherwise this method will just return the original string.
     * Example assuming each character takes up one spot:
     * title = "Battery Stats", breakpoint = [6], stringPtr = 0, limitedWidth = 7
     * We get the current word -> from sublist(0, breakpoint[i]+1) so sublist (0,7) -> Battery,
     * now stringPtr = 7 then from sublist(7) the current string is " Stats" and the runningWidth
     * at this point exceeds limitedWidth and so we put " Stats" onto the next line (after checking
     * if the first char is a SPACE, we trim to append "Stats". So resulting string would be
     * "Battery\nStats"
     */
    public static CharSequence modifyTitleToSupportMultiLine(int limitedWidth, int limitedHeight,
            CharSequence title, TextPaint paint, IntArray breakPoints, float spacingMultiplier,
            float spacingExtra) {
        // current title is less than the width allowed so we can just skip
        if (title == null || paint.measureText(title, 0, title.length()) <= limitedWidth) {
            return title;
        }
        float currentWordWidth, runningWidth = 0;
        CharSequence currentWord;
        StringBuilder newString = new StringBuilder();
        paint.setLetterSpacing(MIN_LETTER_SPACING);
        int stringPtr = 0;
        for (int i = 0; i < breakPoints.size() + 1; i++) {
            if (i < breakPoints.size()) {
                currentWord = title.subSequence(stringPtr, breakPoints.get(i) + 1);
            } else {
                // last word from recent breakpoint until the end of the string
                currentWord = title.subSequence(stringPtr, title.length());
            }
            currentWordWidth = paint.measureText(currentWord, 0, currentWord.length());
            runningWidth += currentWordWidth;
            if (runningWidth <= limitedWidth) {
                newString.append(currentWord);
            } else {
                if (i != 0) {
                    // If putting word onto a new line, make sure there is no space or new line
                    // character in the beginning of the current word and just put in the rest of
                    // the characters.
                    CharSequence lastCharacters = title.subSequence(stringPtr, title.length());
                    int beginningLetterType =
                            Character.getType(Character.codePointAt(lastCharacters, 0));
                    if (beginningLetterType == Character.SPACE_SEPARATOR
                            || beginningLetterType == Character.LINE_SEPARATOR) {
                        lastCharacters = lastCharacters.length() > 1
                                ? lastCharacters.subSequence(1, lastCharacters.length())
                                : EMPTY;
                    }
                    newString.append(NEW_LINE).append(lastCharacters);
                    StaticLayout staticLayout = new StaticLayout(newString, paint, limitedWidth,
                            ALIGN_NORMAL, spacingMultiplier, spacingExtra, false);
                    if (staticLayout.getHeight() < limitedHeight) {
                        return newString.toString();
                    }
                }
                // if the first words exceeds width, just return as the first line will ellipse
                return title;
            }
            if (i >= breakPoints.size()) {
                // no need to look forward into the string if we've already finished processing
                break;
            }
            stringPtr = breakPoints.get(i) + 1;
        }
        return newString.toString();
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /**
     * Applies the loading progress value to the progress bar.
     *
     * If this app is installing, the progress bar will be updated with the installation progress.
     * If this app is installed and downloading incrementally, the progress bar will be updated
     * with the total download progress.
     */
    public void applyLoadingState(PreloadIconDrawable icon) {
        if (getTag() instanceof ItemInfoWithIcon) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) getTag();
            if ((info.runtimeStatusFlags & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0
                    || info.hasPromiseIconUi()
                    || (info.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0
                    || (icon != null)) {
                updateProgressBarUi(info.getProgressLevel() == 100 ? icon : null);
            }
        }
    }

    private void updateProgressBarUi(PreloadIconDrawable oldIcon) {
        FastBitmapDrawable originalIcon = mIcon;
        PreloadIconDrawable preloadDrawable = applyProgressLevel();
        if (preloadDrawable != null && oldIcon != null) {
            preloadDrawable.maybePerformFinishedAnimation(oldIcon, () -> setIcon(originalIcon));
        }
    }

    /** Applies the given progress level to the this icon's progress bar. */
    @Nullable
    public PreloadIconDrawable applyProgressLevel() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        if (progressLevel >= 100) {
            setContentDescription(info.contentDescription != null
                    ? info.contentDescription : "");
        } else if (progressLevel > 0) {
            setDownloadStateContentDescription(info, progressLevel);
        } else {
            setContentDescription(getContext()
                    .getString(R.string.app_waiting_download_title, info.title));
        }
        if (mIcon != null) {
            PreloadIconDrawable preloadIconDrawable;
            if (mIcon instanceof PreloadIconDrawable) {
                preloadIconDrawable = (PreloadIconDrawable) mIcon;
                preloadIconDrawable.setLevel(progressLevel);
                preloadIconDrawable.setIsDisabled(isIconDisabled(info));
            } else {
                preloadIconDrawable = makePreloadIcon();
                setIcon(preloadIconDrawable);
            }
            return preloadIconDrawable;
        }
        return null;
    }

    /**
     * Creates a PreloadIconDrawable with the appropriate progress level without mutating this
     * object.
     */
    @Nullable
    public PreloadIconDrawable makePreloadIcon() {
        if (!(getTag() instanceof ItemInfoWithIcon)) {
            return null;
        }

        ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
        int progressLevel = info.getProgressLevel();
        final PreloadIconDrawable preloadDrawable = newPendingIcon(getContext(), info);

        preloadDrawable.setLevel(progressLevel);
        preloadDrawable.setIsDisabled(isIconDisabled(info));
        return preloadDrawable;
    }

    /**
     * Returns true to grey the icon if the icon is either suspended or if the icon is pending
     * download
     */
    public boolean isIconDisabled(ItemInfoWithIcon info) {
        return info.isDisabled() || info.isPendingDownload();
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon instanceof FastBitmapDrawable) {
            boolean wasDotted = mDotInfo != null;
            mDotInfo = mActivity.getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            DragTrace.log("BubbleTextView.applyDotState item=" + (itemInfo != null ? itemInfo.title : "null") + " isDotted=" + isDotted);
            float newDotScale = isDotted ? 1f : 0;
            if (mDisplay == DISPLAY_ALL_APPS) {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;
            } else {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            }
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (!TextUtils.isEmpty(itemInfo.contentDescription)) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(
                            getAppLabelPluralString(itemInfo.contentDescription.toString(), count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    private void setDownloadStateContentDescription(ItemInfoWithIcon info, int progressLevel) {
        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_ARCHIVED) != 0 && progressLevel == 0) {
            setContentDescription(getContext().getString(R.string.app_archived_title, info.title));
        } else if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK)
                != 0) {
            String percentageString = NumberFormat.getPercentInstance()
                    .format(progressLevel * 0.01);
            if ((info.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                                R.string.app_installing_title, info.title, percentageString));
            } else if ((info.runtimeStatusFlags
                    & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                                R.string.app_downloading_title, info.title, percentageString));
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    public void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
        }
    }

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = getIconOrTransparentColor();
        applyCompoundDrawables(icon);
    }

    private Drawable getIconOrTransparentColor() {
        return mIsIconVisible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
    }

    /** Sets the icon visual state to disabled or not. */
    public void setIconDisabled(boolean isDisabled) {
        if (mIcon != null) {
            mIcon.setIsDisabled(isDisabled);
        }
    }

    protected boolean iconUpdateAnimationEnabled() {
        return mEnableIconUpdateAnimation;
    }

    protected void applyCompoundDrawables(Drawable icon) {
        if (icon == null) {
            // Icon can be null when we use the BubbleTextView for text only.
            return;
        }

        // If we had already set an icon before, disable relayout as the icon size is the
        // same as before.
        mDisableRelayout = mIcon != null;

        icon.setBounds(0, 0, mIconSize, mIconSize);

        updateIcon(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIcon != null
                && mIcon instanceof PlaceHolderIconDrawable
                && iconUpdateAnimationEnabled()) {
            ((PlaceHolderIconDrawable) mIcon).animateIconUpdate(icon);
        }

        mDisableRelayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mDisableRelayout = true;
            mEnableIconUpdateAnimation = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
                mActivity.invalidateParent(info);
            } else if (info != null) {
                applyFromItemInfoWithIcon(info);
            }

            mDisableRelayout = false;
            mEnableIconUpdateAnimation = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (info.usingLowResIcon()) {
                mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                        .updateIconInBackground(BubbleTextView.this, info);
            }
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    public boolean isDisplaySearchResult() {
        return mDisplay == DISPLAY_SEARCH_RESULT
                || mDisplay == DISPLAY_SEARCH_RESULT_SMALL
                || mDisplay == DISPLAY_SEARCH_RESULT_APP_ROW;
    }

    public int getIconDisplay() {
        return mDisplay;
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        final boolean wasUninstallVisible = isUninstallVisible;
        applyUninstallIconState(false);
        return () -> {
            setForceHideDot(false);
            applyUninstallIconState(wasUninstallVisible);
        };
    }

    private void resetIconScale() {
        if (mIcon != null) {
            mIcon.resetScale();
        }
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    private String getAppLabelPluralString(String appName, int notificationCount) {
        MessageFormat icuCountFormat = new MessageFormat(
                getResources().getString(R.string.dotted_app_label),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("app_name", appName);
        args.put("count", notificationCount);
        return icuCountFormat.format(args);
    }

    /**
     * Starts a long press action and returns the corresponding pre-drag condition
     */
    public PreDragCondition startLongPressAction() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null) {
                if (launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
                    return null;
                }
                if (com.android.launcher3.folder.Folder.getOpen(launcher)
                        instanceof foundation.e.bliss.folder.GridFolder gf && gf.isFolderWobbling()) {
                    return null;
                }
            }
        }
        PopupContainerWithArrow popup = PopupContainerWithArrow.showForIcon(this);
        return popup != null ? popup.createPreDragCondition(true) : null;
    }

    public DragOptions.PreDragCondition createWobblePreDragCondition() {
        return new DragOptions.PreDragCondition() {
            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return true;
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
            }
        };
    }

    /**
     * Returns true if the view can show long-press popup
     */
    public boolean canShowLongPressPopup() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null) {
                if (launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
                    return false;
                }
                if (com.android.launcher3.folder.Folder.getOpen(launcher)
                        instanceof foundation.e.bliss.folder.GridFolder gf && gf.isFolderWobbling()) {
                    return false;
                }
            }
        }
        return getTag() instanceof ItemInfo && ShortcutUtil.supportsShortcuts((ItemInfo) getTag());
    }
}
```

---

# src/com/android/launcher3/celllayout/ReorderAlgorithm.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/celllayout/ReorderAlgorithm.java`  
**Lines**: 1023 | **Bytes**: 45742

```java
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

        if (isWidgetDrag && result[0] >= 0 && reflowIconsAroundWidget(result[0], result[1], spanX, spanY,
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

        // 2D Spatial Grid Reflow Solver
        SpatialGridSolver.Solution solverSolution = SpatialGridSolver.solve(
                mCellLayout,
                result[0], result[1],
                spanX, spanY,
                direction,
                dragView,
                isWidgetDrag
        );

        if (solverSolution.isSolution()) {
            solution.map.clear();
            for (Map.Entry<View, CellAndSpan> entry : solverSolution.getPlacements().entrySet()) {
                solution.map.put(entry.getKey(), entry.getValue());
            }
            if (dragView != null) {
                solution.map.put(dragView, new CellAndSpan(result[0], result[1], spanX, spanY));
            }
            solution.intersectingViews = new ArrayList<>(solverSolution.getDisplacedViews());
            mIntersectingViews.clear();
            mIntersectingViews.addAll(solverSolution.getDisplacedViews());
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = spanX;
            solution.spanY = spanY;
            solution.isSolution = true;
            updateTmpOccupiedForReflow(result[0], result[1], spanX, spanY, dragView, solution);
            return solution;
        }

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
                mIntersectingViews.add(child);
            }
        }

        solution.intersectingViews = new ArrayList<>(mIntersectingViews);

        return !mIntersectingViews.isEmpty();

    }

    public boolean rearrangementExists(int[] direction, View ignoreView, ItemConfiguration solution) {
        if (mIntersectingViews.isEmpty()) return true;

        for (View child : mIntersectingViews) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            if (!lp.canReorder || (!isWidget() && child instanceof LauncherAppWidgetHostView)) {
                return false;
            }
        }

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

        // Guard against distant teleportation: for 1x1 icons, only allow displacement
        // to an immediately adjacent cell (Euclidean distance <= 1.5f, i.e. 1 cell away).
        // Never allow distant vacant cell hunting that teleports icons across the screen.
        if (tmpLocation[0] >= 0 && tmpLocation[1] >= 0) {
            float dist = (float) Math.hypot(tmpLocation[0] - c.cellX, tmpLocation[1] - c.cellY);
            if (c.spanX == 1 && c.spanY == 1 && dist > 1.5f) {
                tmpLocation[0] = -1;
                tmpLocation[1] = -1;
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
                        fail = true;
                        break;
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
        if (mCellLayout.mDirectionVector[0] == 0 && mCellLayout.mDirectionVector[1] == 0) {
            getDirectionVectorForDrop(reorderParameters, mCellLayout.mDirectionVector);
        }

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

        boolean isInternalDrag = reorderParameters.getDragView() != null
                && reorderParameters.getDragView().getParent() == mCellLayout.getShortcutsAndWidgets();
        if (!isInternalDrag && !isWidget() && !mCellLayout.findCellForSpan(null, reorderParameters.getMinSpanX(), reorderParameters.getMinSpanY())) {
            if (DEBUG) {
                android.util.Log.d("ReorderDebug", "calculateReorder: NO EMPTY CELL FOUND -> returning no-solution");
            }
            ItemConfiguration solution = new ItemConfiguration();
            solution.cellX = solution.cellY = solution.spanX = solution.spanY = -1;
            solution.isSolution = false;
            return solution;
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
```

---

# src/com/android/launcher3/celllayout/ItemConfiguration.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/celllayout/ItemConfiguration.kt`  
**Lines**: 63 | **Bytes**: 2047

```kotlin
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
package com.android.launcher3.celllayout

import android.graphics.Rect
import android.util.ArrayMap
import android.view.View
import com.android.launcher3.util.CellAndSpan

/** Represents the solution to a reorder of items in the Workspace. */
class ItemConfiguration : CellAndSpan() {
    @JvmField val map = ArrayMap<View, CellAndSpan>()
    private val savedMap = ArrayMap<View, CellAndSpan>()

    @JvmField val sortedViews = ArrayList<View>()

    @JvmField var intersectingViews: ArrayList<View> = ArrayList()
    @JvmField val overflowViews = ArrayList<View>()

    @JvmField var isSolution = false
    fun save() {
        // Copy current state into savedMap
        map.forEach { (k, v) -> savedMap[k]?.copyFrom(v) }
    }

    fun restore() {
        // Restore current state from savedMap
        savedMap.forEach { (k, v) -> map[k]?.copyFrom(v) }
    }

    fun add(v: View, cs: CellAndSpan) {
        map[v] = cs
        savedMap[v] = CellAndSpan()
        sortedViews.add(v)
    }

    fun area(): Int {
        return spanX * spanY
    }

    fun getBoundingRectForViews(views: ArrayList<View>, outRect: Rect) {
        views
            .mapNotNull { v -> map[v] }
            .forEachIndexed { i, c ->
                if (i == 0) outRect.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY)
                else outRect.union(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY)
            }
    }
}
```

---

# src/com/android/launcher3/dragndrop/DragController.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/dragndrop/DragController.java`  
**Lines**: 640 | **Bytes**: 23735

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Flags;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Class for initiating a drag within a view or across multiple views.
 * @param <T>
 */
public abstract class DragController<T extends ActivityContext>
        implements DragDriver.EventListener, TouchController {

    /**
     * When a drag is started from a deep press, you need to drag this much farther than normal to
     * end a pre-drag. See {@link DragOptions.PreDragCondition#shouldStartDrag(double)}.
     */
    private static final int DEEP_PRESS_DISTANCE_FACTOR = 3;

    protected final T mActivity;

    // temporaries to avoid gc thrash
    private final Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    protected DragDriver mDragDriver = null;

    /** Options controlling the drag behavior. */
    protected DragOptions mOptions;

    /** Coordinate for motion down event */
    protected final Point mMotionDown = new Point();
    /** Coordinate for last touch event **/
    protected final Point mLastTouch = new Point();

    protected final Point mTmpPoint = new Point();

    protected DropTarget.DragObject mDragObject;

    /** Who can receive drop events */
    private final ArrayList<DropTarget> mDropTargets = new ArrayList<>();
    private final ArrayList<DragListener> mListeners = new ArrayList<>();

    protected DropTarget mLastDropTarget;

    private int mLastTouchClassification;
    protected int mDistanceSinceScroll = 0;

    /**
     * This variable is to differentiate between a long press and a drag, if it's true that means
     * it's a long press and when it's false means that we are no longer in a long press.
     */
    protected boolean mIsInPreDrag;

    private final int DRAG_VIEW_SCALE_DURATION_MS = 500;

    /**
     * Interface to receive notifications when a drag starts or stops
     */
    public interface DragListener {
        /**
         * A drag has begun
         *
         * @param dragObject The object being dragged
         * @param options Options used to start the drag
         */
        void onDragStart(DropTarget.DragObject dragObject, DragOptions options);

        /**
         * The drag has ended
         */
        void onDragEnd();
    }

    /**
     * Used to create a new DragLayer from XML.
     */
    public DragController(T activity) {
        mActivity = activity;
    }

    /**
     * Starts a drag.
     *
     * <p>When the drag is started, the UI automatically goes into spring loaded mode. On a
     * successful drop, it is the responsibility of the {@link DropTarget} to exit out of the spring
     * loaded mode. If the drop was cancelled for some reason, the UI will automatically exit out of
     * this mode.
     *
     * @param drawable The drawable to be displayed in the drag view.  It will be re-scaled to the
     *                 enlarged size.
     * @param originalView The source view (ie. icon, widget etc.) that is being dragged and which
     *                     the DragView represents
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *                   Makes dragging feel more precise, e.g. you can clip out a transparent
     *                   border
     */
    public DragView startDrag(
            Drawable drawable,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options) {
        return startDrag(drawable, /* view= */ null, originalView, dragLayerX, dragLayerY, source,
                dragInfo, dragRegion, initialDragViewScale, dragViewScaleOnDrop, options);
    }

    /**
     * Starts a drag.
     *
     * <p>When the drag is started, the UI automatically goes into spring loaded mode. On a
     * successful drop, it is the responsibility of the {@link DropTarget} to exit out of the spring
     * loaded mode. If the drop was cancelled for some reason, the UI will automatically exit out of
     * this mode.
     *
     * @param view The view to be displayed in the drag view.  It will be re-scaled to the
     *             enlarged size.
     * @param originalView The source view (ie. icon, widget etc.) that is being dragged and which
     *                     the DragView represents
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *                   Makes dragging feel more precise, e.g. you can clip out a transparent
     *                   border
     */
    public DragView startDrag(
            View view,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options) {
        return startDrag(/* drawable= */ null, view, originalView, dragLayerX, dragLayerY, source,
                dragInfo, dragRegion, initialDragViewScale, dragViewScaleOnDrop, options);
    }

    protected abstract DragView startDrag(
            @Nullable Drawable drawable,
            @Nullable View view,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options);

    protected void callOnDragStart() {
        if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, true /* dragStarted*/);
        }
        mIsInPreDrag = false;
        if (mOptions.preDragEndScale != 0) {
            mDragObject.dragView
                    .animate()
                    .scaleX(mOptions.preDragEndScale)
                    .scaleY(mOptions.preDragEndScale)
                    .setInterpolator(Interpolators.EMPHASIZED)
                    .setDuration(DRAG_VIEW_SCALE_DURATION_MS)
                    .start();
        }
        mDragObject.dragView.onDragStart();
        DragTrace.log("DragController.callOnDragStart item=" + (mDragObject.dragInfo != null ? mDragObject.dragInfo.title : "null"));
        android.util.Log.d("DragDebug", "DragController.callOnDragStart() called for: " + (mDragObject.dragInfo != null ? mDragObject.dragInfo.title : "null"));
        if (mActivity instanceof com.android.launcher3.Launcher) {
            ((com.android.launcher3.Launcher) mActivity).getInteractionController().beginDrag(mDragObject.dragView, mDragObject.dragInfo);
        }
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject, mOptions);
        }
    }

    protected boolean isItemPinnable() {
        return !Flags.privateSpaceRestrictItemDrag()
                || !(mDragObject.dragInfo instanceof ItemInfoWithIcon itemInfoWithIcon)
                || (itemInfoWithIcon.runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0;
    }

    public Optional<InstanceId> getLogInstanceId() {
        return Optional.ofNullable(mDragObject)
                .map(dragObject -> dragObject.logInstanceId);
    }

    /**
     * Call this from a drag source view like this:
     *
     * <pre>
     *  @Override
     *  public boolean dispatchKeyEvent(KeyEvent event) {
     *      return mDragController.dispatchKeyEvent(this, event)
     *              || super.dispatchKeyEvent(event);
     * </pre>
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragDriver != null;
    }

    public boolean isDragging() {
        return mDragDriver != null || (mOptions != null && mOptions.isAccessibleDrag);
    }

    /**
     * Stop dragging without dropping.
     */
    public void cancelDrag() {
        DragTrace.log("DragController.cancelDrag isDragging=" + isDragging()
                + " item=" + (mDragObject != null && mDragObject.dragInfo != null ? mDragObject.dragInfo.title : "null"));
        if (isDragging()) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mDragObject.deferDragViewCleanupPostAnimation = false;
            mDragObject.cancelled = true;
            mDragObject.dragComplete = true;
            if (mDragObject.dragView != null && mDragObject.dragView.getContentViewParent() != null) {
                mDragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
            }
            if (!mIsInPreDrag) {
                dispatchDropComplete(null, false);
            } else if (mDragObject != null && mDragObject.dragSource != null) {
                mDragObject.dragSource.onDropCompleted(null, mDragObject, false);
            }
        }
        endDrag();
    }

    private void dispatchDropComplete(View dropTarget, boolean accepted) {
        DragTrace.log("DragController.dispatchDropComplete accepted=" + accepted
                + " target=" + (dropTarget != null ? dropTarget.getClass().getSimpleName() : "null"));
        if (!accepted) {
            // If it was not accepted, cleanup the state. If it was accepted, it is the
            // responsibility of the drop target to cleanup the state.
            exitDrag();
            mDragObject.deferDragViewCleanupPostAnimation = false;
        }

        mDragObject.dragSource.onDropCompleted(dropTarget, mDragObject, accepted);
    }

    protected abstract void exitDrag();

    public void onAppsRemoved(Predicate<ItemInfo> matcher) {
        // Cancel the current drag if we are removing an app that we are dragging
        if (mDragObject != null) {
            ItemInfo dragInfo = mDragObject.dragInfo;
            if (dragInfo instanceof WorkspaceItemInfo && matcher.test(dragInfo)) {
                cancelDrag();
            }
        }
    }

    protected void endDrag() {
        DragTrace.log("DragController.endDrag isDragging=" + isDragging());
        if (isDragging()) {
            mDragDriver = null;
            boolean isDeferred = false;
            if (mDragObject.dragView != null) {
                if (mIsInPreDrag) {
                    mDragObject.deferDragViewCleanupPostAnimation = false;
                }
                isDeferred = mDragObject.deferDragViewCleanupPostAnimation;
                if (!isDeferred) {
                    if (mDragObject.dragView.getContentViewParent() != null) {
                        mDragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                    }
                    mDragObject.dragView.remove();
                } else if (mIsInPreDrag) {
                    if (mDragObject.dragView.getContentViewParent() != null) {
                        mDragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                    }
                    animateDragViewToOriginalPosition(null, null, -1);
                }
                mDragObject.dragView.clearAnimation();
                mDragObject.dragView = null;
            }
            // Only end the drag if we are not deferred
            if (!isDeferred) {
                callOnDragEnd();
            }
        }
    }

    public void animateDragViewToOriginalPosition(final Runnable onComplete,
            final View originalIcon, int duration) {
        Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                if (originalIcon != null) {
                    originalIcon.setVisibility(View.VISIBLE);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        };
        mDragObject.dragView.animateTo(mMotionDown.x, mMotionDown.y, onCompleteRunnable, duration);
    }

    protected void callOnDragEnd() {
        DragTrace.log("DragController.callOnDragEnd");
        if (mIsInPreDrag && mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, false /* dragStarted*/);
        }
        mIsInPreDrag = false;
        mOptions = null;
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragEnd();
        }
        if (mActivity instanceof com.android.launcher3.Launcher) {
            ((com.android.launcher3.Launcher) mActivity).getInteractionController().endDrag();
        }
    }

    /**
     * This only gets called as a result of drag view cleanup being deferred in endDrag();
     */
    void onDeferredEndDrag(DragView dragView) {
        dragView.remove();

        if (mDragObject.deferDragViewCleanupPostAnimation) {
            // If we skipped calling onDragEnd() before, do it now
            callOnDragEnd();
        }
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    protected Point getClampedDragLayerPos(float x, float y) {
        mActivity.getDragLayer().getLocalVisibleRect(mRectTemp);
        mTmpPoint.x = (int) Math.max(mRectTemp.left, Math.min(x, mRectTemp.right - 1));
        mTmpPoint.y = (int) Math.max(mRectTemp.top, Math.min(y, mRectTemp.bottom - 1));
        return mTmpPoint;
    }

    @Override
    public void onDriverDragMove(float x, float y) {
        Point dragLayerPos = getClampedDragLayerPos(x, y);
        handleMoveEvent(dragLayerPos.x, dragLayerPos.y);
    }

    @Override
    public void onDriverDragExitWindow() {
        if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
            mLastDropTarget = null;
        }
    }

    @Override
    public void onDriverDragEnd(float x, float y) {
        if (!endWithFlingAnimation()) {
            drop(findDropTarget((int) x, (int) y), null);
        }
        endDrag();
    }

    protected boolean endWithFlingAnimation() {
        return false;
    }

    @Override
    public void onDriverDragCancel() {
        cancelDrag();
    }

    /**
     * Call this from a drag source view.
     */
    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mOptions != null && mOptions.isAccessibleDrag) {
            return false;
        }

        if (!Utilities.isWorkspaceEditAllowed(mActivity.getDragLayer().getContext())) {
            cancelDrag();
            return false;
        }

        Point dragLayerPos = getClampedDragLayerPos(getX(ev), getY(ev));
        mLastTouch.set(dragLayerPos.x,  dragLayerPos.y);
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // Remember location of down touch
            mMotionDown.set(dragLayerPos.x,  dragLayerPos.y);
        }

        mLastTouchClassification = ev.getClassification();
        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);
    }

    protected float getX(MotionEvent ev) {
        return ev.getX();
    }

    protected float getY(MotionEvent ev) {
        return ev.getY();
    }

    /**
     * Call this from a drag source view.
     */
    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mDragDriver != null && mDragDriver.onTouchEvent(ev);
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onDragEvent(DragEvent event) {
        return mDragDriver != null && mDragDriver.onDragEvent(event);
    }

    protected void handleMoveEvent(int x, int y) {
        mDragObject.dragView.move(x, y);

        // Check if we are hovering over the scroll areas
        mDistanceSinceScroll += Math.hypot(mLastTouch.x - x, mLastTouch.y - y);
        mLastTouch.set(x, y);

        int distanceDragged = mDistanceSinceScroll;
        if (mLastTouchClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS) {
            distanceDragged /= DEEP_PRESS_DISTANCE_FACTOR;
        }
        if (mIsInPreDrag && mOptions.preDragCondition != null
                && mOptions.preDragCondition.shouldStartDrag(distanceDragged)) {
            callOnDragStart();
        }

        // Drop on someone?
        checkTouchMove(x, y);
    }

    public float getDistanceDragged() {
        return mDistanceSinceScroll;
    }

    public void forceTouchMove() {
        checkTouchMove(mLastTouch.x, mLastTouch.y);
    }

    private DropTarget checkTouchMove(final int x, final int y) {
        // If we are in predrag, don't trigger any other event until we get out of it
        if (mIsInPreDrag) {
            return mLastDropTarget;
        }
        DropTarget dropTarget = findDropTarget(x, y);
        if (dropTarget != null) {
            if (mLastDropTarget != dropTarget) {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragObject);
                }
                dropTarget.onDragEnter(mDragObject);
            }
            dropTarget.onDragOver(mDragObject);
        } else if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
        }
        mLastDropTarget = dropTarget;
        return mLastDropTarget;
    }

    /**
     * As above, since accessible drag and drop won't cause the same sequence of touch events,
     * we manually ensure appropriate drag and drop events get emulated for accessible drag.
     */
    public void completeAccessibleDrag(int[] location) {
        // We make sure that we prime the target for drop.
        DropTarget dropTarget = checkTouchMove(location[0], location[1]);

        dropTarget.prepareAccessibilityDrop();
        // Perform the drop
        drop(dropTarget, null);
        endDrag();
    }

    protected void drop(DropTarget dropTarget, Runnable flingAnimation) {
        // Move dragging to the final target.
        if (dropTarget != mLastDropTarget) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mLastDropTarget = dropTarget;
            if (dropTarget != null) {
                dropTarget.onDragEnter(mDragObject);
            }
        }

        mDragObject.dragComplete = true;
        if (mIsInPreDrag) {
            mDragObject.deferDragViewCleanupPostAnimation = false;
            if (mDragObject.dragView != null && mDragObject.dragView.getContentViewParent() != null) {
                mDragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
            }
            if (dropTarget != null) {
                dropTarget.onDragExit(mDragObject);
            }
            return;
        }

        // Drop onto the target.
        boolean accepted = false;
        if (dropTarget != null) {
            dropTarget.onDragExit(mDragObject);
            if (dropTarget.acceptDrop(mDragObject)) {
                if (flingAnimation != null) {
                    flingAnimation.run();
                } else {
                    dropTarget.onDrop(mDragObject, mOptions);
                }
                accepted = true;
            }
        }
        DragTrace.log("DragController.drop dropTarget=" + (dropTarget != null ? dropTarget.getClass().getSimpleName() : "null")
                + " accepted=" + accepted + " item=" + (mDragObject != null && mDragObject.dragInfo != null ? mDragObject.dragInfo.title : "null"));
        final View dropTargetAsView = dropTarget instanceof View ? (View) dropTarget : null;
        dispatchDropComplete(dropTargetAsView, accepted);
    }

    private DropTarget findDropTarget(final int x, final int y) {
        mCoordinatesTemp[0] = x;
        mCoordinatesTemp[1] = y;

        final Rect r = mRectTemp;
        final ArrayList<DropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i = count - 1; i >= 0; i--) {
            DropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRectRelativeToDragLayer(r);
            if (r.contains(x, y)) {
                mActivity.getDragLayer().mapCoordInSelfToDescendant((View) target,
                        mCoordinatesTemp);
                mDragObject.x = mCoordinatesTemp[0];
                mDragObject.y = mCoordinatesTemp[1];
                return target;
            }
        }
        DropTarget dropTarget = getDefaultDropTarget(mCoordinatesTemp);
        mDragObject.x = mCoordinatesTemp[0];
        mDragObject.y = mCoordinatesTemp[1];
        return dropTarget;
    }

    protected abstract DropTarget getDefaultDropTarget(int[] dropCoordinates);

    /**
     * Sets the drag listener which will be notified when a drag starts or ends.
     */
    public void addDragListener(DragListener l) {
        mListeners.add(l);
    }

    /**
     * Remove a previously installed drag listener.
     */
    public void removeDragListener(DragListener l) {
        mListeners.remove(l);
    }

    /**
     * Add a DropTarget to the list of potential places to receive drop events.
     */
    public void addDropTarget(DropTarget target) {
        mDropTargets.add(target);
    }

    /**
     * Don't send drop events to <em>target</em> any more.
     */
    public void removeDropTarget(DropTarget target) {
        mDropTargets.remove(target);
    }
}
```

---

# src/com/android/launcher3/dragndrop/DragLayer.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/dragndrop/DragLayer.java`  
**Lines**: 498 | **Bytes**: 20395

```java

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static android.animation.ObjectAnimator.ofFloat;

import static com.android.app.animation.Interpolators.DECELERATE_1_5;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DropTargetBar;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringProperty;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.graphics.Scrim;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends BaseDragLayer<Launcher> implements LauncherOverlayCallbacks {

    public static final int ALPHA_INDEX_OVERLAY = 0;
    private static final int ALPHA_CHANNEL_COUNT = 1;

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    private final boolean mIsRtl;

    private DragController mDragController;

    // Variables relating to animation of views after drop
    private Animator mDropAnim = null;

    private DragView mDropView = null;

    private boolean mHoverPointClosesFolder = false;

    private int mTopViewIndex;
    private int mChildCountOnLastUpdate = -1;

    // Related to adjacent page hints
    private final ViewGroupFocusHelper mFocusIndicatorHelper;
    private Scrim mWorkspaceDragScrim;

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, ALPHA_CHANNEL_COUNT);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
        setChildrenDrawingOrderEnabled(true);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        mIsRtl = Utilities.isRtl(getResources());
    }

    /**
     * Set up the drag layer with the parameters.
     */
    public void setup(DragController dragController, Workspace<?> workspace) {
        mDragController = dragController;
        recreateControllers();
        mWorkspaceDragScrim = new Scrim(this);
        workspace.addOverlayCallback(this);
    }

    @Override
    public void recreateControllers() {
        mControllers = mActivity.createTouchControllers();
    }

    public ViewGroupFocusHelper getFocusIndicatorHelper() {
        return mFocusIndicatorHelper;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    private boolean isEventOverAccessibleDropTargetBar(MotionEvent ev) {
        return isInAccessibleDrag() && isEventOverView(mActivity.getDropTargetBar(), ev);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        if (mActivity == null || mActivity.getWorkspace() == null) {
            return false;
        }
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
        if (!(topView instanceof Folder)) {
            return false;
        } else {
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isTouchExplorationEnabled()) {
                Folder currentFolder = (Folder) topView;
                final int action = ev.getAction();
                boolean isOverFolderOrSearchBar;
                switch (action) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
                        if (!isOverFolderOrSearchBar) {
                            sendTapOutsideFolderAccessibilityEvent(currentFolder.isEditingName());
                            mHoverPointClosesFolder = true;
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
                        if (!isOverFolderOrSearchBar && !mHoverPointClosesFolder) {
                            sendTapOutsideFolderAccessibilityEvent(currentFolder.isEditingName());
                            mHoverPointClosesFolder = true;
                            return true;
                        } else if (!isOverFolderOrSearchBar) {
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                }
            }
        }
        return false;
    }

    private void sendTapOutsideFolderAccessibilityEvent(boolean isEditingName) {
        int stringId = isEditingName ? R.string.folder_tap_to_rename : R.string.folder_tap_to_close;
        sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_VIEW_FOCUSED, getContext().getString(stringId));
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        // If we've received this, we've already done the necessary handling
        // in onInterceptHoverEvent. Return true to consume the event.
        return false;
    }


    private boolean isInAccessibleDrag() {
        return mActivity.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        if (isInAccessibleDrag() && child instanceof DropTargetBar) {
            return true;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            addAccessibleChildToList(topView, childrenForAccessibility);
            if (isInAccessibleDrag()) {
                addAccessibleChildToList(mActivity.getDropTargetBar(), childrenForAccessibility);
            }
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ev.offsetLocation(getTranslationX(), 0);
        try {
            return super.dispatchTouchEvent(ev);
        } finally {
            ev.offsetLocation(-getTranslationX(), 0);
        }
    }

    public void animateViewIntoPosition(DragView dragView, final int[] pos, float alpha,
            float scaleX, float scaleY, int animationEndStyle, Runnable onFinishRunnable,
            int duration) {
        animateViewIntoPosition(dragView, pos[0], pos[1], alpha, scaleX, scaleY,
                onFinishRunnable, animationEndStyle, duration, null);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, View anchorView) {
        animateViewIntoPosition(dragView, child, -1, anchorView);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, int duration,
            View anchorView) {

        ShortcutAndWidgetContainer parentChildren = (ShortcutAndWidgetContainer) child.getParent();
        CellLayoutLayoutParams lp =  (CellLayoutLayoutParams) child.getLayoutParams();
        parentChildren.measureChild(child);
        parentChildren.layoutChild(child);

        float coord[] = new float[2];
        float childScale = child.getScaleX();

        coord[0] = lp.x + (child.getMeasuredWidth() * (1 - childScale) / 2);
        coord[1] = lp.y + (child.getMeasuredHeight() * (1 - childScale) / 2);

        // Since the child hasn't necessarily been laid out, we force the lp to be updated with
        // the correct coordinates (above) and use these to determine the final location
        float scale = getDescendantCoordRelativeToSelf((View) child.getParent(), coord);

        // We need to account for the scale of the child itself, as the above only accounts for
        // for the scale in parents.
        scale *= childScale;
        int toX = Math.round(coord[0]);
        int toY = Math.round(coord[1]);

        float toScale = scale;

        if (child instanceof DraggableView) {
            // This code is fairly subtle. Please verify drag and drop is pixel-perfect in a number
            // of scenarios before modifying (from all apps, from workspace, different grid-sizes,
            // shortcuts from in and out of Launcher etc).
            DraggableView d = (DraggableView) child;
            Rect destRect = new Rect();
            d.getWorkspaceVisualDragBounds(destRect);

            // In most cases this additional scale factor should be a no-op (1). It mainly accounts
            // for alternate grids where the source and destination icon sizes are different
            toScale *= ((1f * destRect.width())
                    / (dragView.getMeasuredWidth() - dragView.getBlurSizeOutline()));

            // This accounts for the offset of the DragView created by scaling it about its
            // center as it animates into place.
            float scaleShiftX = dragView.getMeasuredWidth() * (1 - toScale) / 2;
            float scaleShiftY = dragView.getMeasuredHeight() * (1 - toScale) / 2;

            toX += scale * destRect.left - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftX;
            toY += scale * destRect.top - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftY;
        }

        child.setVisibility(INVISIBLE);
        Runnable onCompleteRunnable = () -> child.setVisibility(VISIBLE);
        animateViewIntoPosition(dragView, toX, toY, 1, toScale, toScale,
                onCompleteRunnable, ANIMATION_END_DISAPPEAR, duration, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     */
    public void animateViewIntoPosition(final DragView view,
            final int toX, final int toY, float finalAlpha,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, to, finalAlpha, finalScaleX, finalScaleY, duration,
                null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     * @param view The view to be animated. This view is drawn directly into DragLayer, and so
     *        doesn't need to be a child of DragLayer.
     * @param to The final location of the view. Only the left and top parameters are used. This
*        location doesn't account for scaling, and so should be centered about the desired
*        final location (including scaling).
     * @param finalAlpha The final alpha of the view, in case we want it to fade as it animates.
     * @param finalScaleX The final scale of the view. The view is scaled about its center.
     * @param finalScaleY The final scale of the view. The view is scaled about its center.
     * @param duration The duration of the animation.
     * @param motionInterpolator The interpolator to use for the location of the view.
     * @param onCompleteRunnable Optional runnable to run on animation completion.
     * @param animationEndStyle Whether or not to fade out the view once the animation completes.
*        {@link #ANIMATION_END_DISAPPEAR} or {@link #ANIMATION_END_REMAIN_VISIBLE}.
     * @param anchorView If not null, this represents the view which the animated view stays
     */
    public void animateView(final DragView view, final Rect to,
            final float finalAlpha, final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator, final Runnable onCompleteRunnable,
            final int animationEndStyle, View anchorView) {
        view.cancelAnimation();
        view.requestLayout();

        final int[] from = getViewLocationRelativeToSelf(view);

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from[0], to.top - from[1]);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= DECELERATE_1_5.getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator =
                motionInterpolator == null ? DECELERATE_1_5 : motionInterpolator;

        // Animate the view
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(ofFloat(view, View.SCALE_X, finalScaleX), interpolator, SpringProperty.DEFAULT);
        anim.add(ofFloat(view, View.SCALE_Y, finalScaleY), interpolator, SpringProperty.DEFAULT);
        anim.setViewAlpha(view, finalAlpha, interpolator);
        anim.setFloat(view, VIEW_TRANSLATE_Y, to.top, interpolator);

        ObjectAnimator xMotion = ofFloat(view, VIEW_TRANSLATE_X, to.left);
        if (anchorView != null) {
            final int startScroll = anchorView.getScrollX();
            TypeEvaluator<Float> evaluator = (f, s, e) -> mapRange(f, s, e)
                    + (anchorView.getScaleX() * (startScroll - anchorView.getScrollX()));
            xMotion.setEvaluator(evaluator);
        }
        anim.add(xMotion, interpolator, SpringProperty.DEFAULT);
        if (onCompleteRunnable != null) {
            anim.addListener(forEndCallback(onCompleteRunnable));
        }
        playDropAnimation(view, anim.buildAnim(), animationEndStyle);
    }

    /**
     * Runs a previously constructed drop animation
     */
    public void playDropAnimation(final DragView view, Animator animator, int animationEndStyle) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        // Create and start the animation
        mDropAnim = animator;
        mDropAnim.addListener(forEndCallback(() -> mDropAnim = null));
        if (animationEndStyle == ANIMATION_END_DISAPPEAR) {
            mDropAnim.addListener(forEndCallback(this::clearAnimatedView));
        }
        mDropAnim.start();
    }

    /**
     * Remove the drop view and end the drag animation.
     *
     * @return {@link DragView} that is removed.
     */
    @Nullable
    public DragView clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        mDropAnim = null;
        if (mDropView != null) {
            mDragController.onDeferredEndDrag(mDropView);
        }
        DragView ret = mDropView;
        mDropView = null;
        invalidate();
        return ret;
    }

    public View getAnimatedView() {
        return mDropView;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateChildIndices();
        mActivity.onDragLayerHierarchyChanged();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateChildIndices();
        mActivity.onDragLayerHierarchyChanged();
    }

    @Override
    public void bringChildToFront(View child) {
        super.bringChildToFront(child);
        updateChildIndices();
    }

    private void updateChildIndices() {
        mTopViewIndex = -1;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i) instanceof DragView) {
                mTopViewIndex = i;
            }
        }
        mChildCountOnLastUpdate = childCount;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mChildCountOnLastUpdate != childCount) {
            // between platform versions 17 and 18, behavior for onChildViewRemoved / Added changed.
            // Pre-18, the child was not added / removed by the time of those callbacks. We need to
            // force update our representation of things here to avoid crashing on pre-18 devices
            // in certain instances.
            updateChildIndices();
        }

        // i represents the current draw iteration
        if (mTopViewIndex == -1) {
            // in general we do nothing
            return i;
        } else if (i == childCount - 1) {
            // if we have a top index, we return it when drawing last item (highest z-order)
            return mTopViewIndex;
        } else if (i < mTopViewIndex) {
            return i;
        } else {
            // for indexes greater than the top index, we fetch one item above to shift for the
            // displacement of the top index
            return i + 1;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the background below children.
        mWorkspaceDragScrim.draw(canvas);
        mFocusIndicatorHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public Scrim getWorkspaceDragScrim() {
        return mWorkspaceDragScrim;
    }

    @Override
    public void onOverlayScrollChanged(float progress) {
        float alpha = 1 - Interpolators.DECELERATE_3.getInterpolation(progress);
        float transX = getMeasuredWidth() * progress;

        if (mIsRtl) {
            transX = -transX;
        }
        setTranslationX(transX);
        getAlphaProperty(ALPHA_INDEX_OVERLAY).setValue(alpha);
    }
}
```

---

# src/com/android/launcher3/dragndrop/DragView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/dragndrop/DragView.java`  
**Lines**: 644 | **Bytes**: 25140

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.icons.FastBitmapDrawable.getDisabledColorFilter;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import foundation.e.bliss.multimode.MultiModeController;

/** A custom view for rendering an icon, folder, shortcut or widget during drag-n-drop. */
public abstract class DragView<T extends Context & ActivityContext> extends FrameLayout {

    public static final int VIEW_ZOOM_DURATION = 150;

    private final View mContent;
    // The following are only used for rendering mContent directly during drag-n-drop.
    @Nullable private ViewGroup.LayoutParams mContentViewLayoutParams;
    @Nullable private ViewGroup mContentViewParent;
    private int mContentViewInParentViewIndex = -1;
    private final int mWidth;
    private final int mHeight;

    private final int mBlurSizeOutline;
    protected final int mRegistrationX;
    protected final int mRegistrationY;
    private final float mInitialScale;
    private final float mEndScale;
    protected final float mScaleOnDrop;
    protected final int[] mTempLoc = new int[2];

    private final RunnableList mOnDragStartCallback = new RunnableList();

    private boolean mHasDragOffset;
    private Rect mDragRegion = null;
    protected final T mActivity;
    private final BaseDragLayer<T> mDragLayer;
    private boolean mHasDrawn = false;

    final ValueAnimator mScaleAnim;
    final ValueAnimator mShiftAnim;

    // Whether mAnim has started. Unlike mAnim.isStarted(), this is true even after mAnim ends.
    private boolean mScaleAnimStarted;
    private boolean mShiftAnimStarted;
    private Runnable mOnScaleAnimEndCallback;
    private Runnable mOnShiftAnimEndCallback;

    private int mLastTouchX;
    private int mLastTouchY;
    private int mAnimatedShiftX;
    private int mAnimatedShiftY;

    // Below variable only needed IF FeatureFlags.LAUNCHER3_SPRING_ICONS is {@code true}
    private Drawable mBgSpringDrawable, mFgSpringDrawable;
    private SpringFloatValue mTranslateX, mTranslateY;
    private Path mScaledMaskPath;
    private Drawable mBadge;

    public DragView(T launcher, Drawable drawable, int registrationX,
            int registrationY, final float initialScale, final float scaleOnDrop,
            final float finalScaleDps) {
        this(launcher, getViewFromDrawable(launcher, drawable),
                drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                registrationX, registrationY, initialScale, scaleOnDrop, finalScaleDps);
    }

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     * @param activity The Launcher instance/ActivityContext this DragView is in.
     * @param content the view content that is attached to the drag view.
     * @param width the width of the dragView
     * @param height the height of the dragView
     * @param initialScale The view that we're dragging around.  We scale it up when we draw it.
     * @param registrationX The x coordinate of the registration point.
     * @param registrationY The y coordinate of the registration point.
     * @param scaleOnDrop the scale used in the drop animation.
     * @param finalScaleDps the scale used in the zoom out animation when the drag view is shown.
     */
    public DragView(T activity, View content, int width, int height, int registrationX,
            int registrationY, final float initialScale, final float scaleOnDrop,
            final float finalScaleDps) {
        super(activity);
        mActivity = activity;
        mDragLayer = activity.getDragLayer();

        mContent = content;
        mWidth = width;
        mHeight = height;
        mContentViewLayoutParams = mContent.getLayoutParams();
        if (mContent.getParent() instanceof ViewGroup) {
            mContentViewParent = (ViewGroup) mContent.getParent();
            mContentViewInParentViewIndex = mContentViewParent.indexOfChild(mContent);
            mContentViewParent.removeView(mContent);
        }

        addView(content, new LayoutParams(width, height));
        content.setVisibility(VISIBLE);

        // If there is already a scale set on the content, we don't want to clip the children.
        if (content.getScaleX() != 1 || content.getScaleY() != 1) {
            setClipChildren(false);
            setClipToPadding(false);
        }

        mEndScale = (width + finalScaleDps) / width;

        // Set the initial scale to avoid any jumps
        setScaleX(initialScale);
        setScaleY(initialScale);

        // Animate the view into the correct position
        mScaleAnim = ValueAnimator.ofFloat(0f, 1f);
        mScaleAnim.setDuration(VIEW_ZOOM_DURATION);
        mScaleAnim.addUpdateListener(animation -> {
            final float value = (Float) animation.getAnimatedValue();
            setScaleX(Utilities.mapRange(value, initialScale, mEndScale));
            setScaleY(Utilities.mapRange(value, initialScale, mEndScale));
            if (!isAttachedToWindow()) {
                animation.cancel();
            }
        });
        mScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mScaleAnimStarted = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mOnScaleAnimEndCallback != null) {
                    mOnScaleAnimEndCallback.run();
                }
            }
        });
        // Set up the shift animator.
        mShiftAnim = ValueAnimator.ofFloat(0f, 1f);
        mShiftAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mShiftAnimStarted = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mOnShiftAnimEndCallback != null) {
                    mOnShiftAnimEndCallback.run();
                }
            }
        });

        setDragRegion(new Rect(0, 0, width, height));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        mInitialScale = initialScale;
        mScaleOnDrop = scaleOnDrop;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));

        mBlurSizeOutline = getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        setElevation(getResources().getDimension(R.dimen.drag_elevation));
        setWillNotDraw(false);
    }

    /** Callback invoked when the scale animation ends. */
    public void setOnScaleAnimEndCallback(Runnable callback) {
        mOnScaleAnimEndCallback = callback;
    }

    /** Callback invoked when the shift animation ends. */
    public void setOnShiftAnimEndCallback(Runnable callback) {
        mOnShiftAnimEndCallback = callback;
    }

    /**
     * Initialize {@code #mIconDrawable} if the item can be represented using
     * an {@link AdaptiveIconDrawable} or {@link FolderAdaptiveIcon}.
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void setItemInfo(final ItemInfo info) {
        if (MultiModeController.isSingleLayerMode()) return;

        // Load the adaptive icon on a background thread and add the view in ui thread.
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            int w = mWidth;
            int h = mHeight;
            Pair<AdaptiveIconDrawable, Drawable> fullDrawable = Utilities.getFullDrawable(
                    mActivity, info, w, h, true /* shouldThemeIcon */);
            if (fullDrawable != null) {
                AdaptiveIconDrawable adaptiveIcon = fullDrawable.first;
                int blurMargin = (int) mActivity.getResources()
                        .getDimension(R.dimen.blur_size_medium_outline) / 2;

                Rect bounds = new Rect(0, 0, w, h);
                bounds.inset(blurMargin, blurMargin);
                // Badge is applied after icon normalization so the bounds for badge should not
                // be scaled down due to icon normalization.
                mBadge = fullDrawable.second;
                FastBitmapDrawable.setBadgeBounds(mBadge, bounds);

                try (LauncherIcons li = LauncherIcons.obtain(mActivity)) {
                    // Since we just want the scale, avoid heavy drawing operations
                    Utilities.scaleRectAboutCenter(bounds, li.getNormalizer().getScale(
                            new AdaptiveIconDrawable(new ColorDrawable(Color.BLACK), null),
                            null, null, null));
                }

                // Shrink very tiny bit so that the clip path is smaller than the original bitmap
                // that has anti aliased edges and shadows.
                Rect shrunkBounds = new Rect(bounds);
                Utilities.scaleRectAboutCenter(shrunkBounds, 0.98f);
                adaptiveIcon.setBounds(shrunkBounds);
                final Path mask = adaptiveIcon.getIconMask();

                mTranslateX = new SpringFloatValue(DragView.this,
                        w * AdaptiveIconDrawable.getExtraInsetFraction());
                mTranslateY = new SpringFloatValue(DragView.this,
                        h * AdaptiveIconDrawable.getExtraInsetFraction());

                bounds.inset(
                        (int) (-bounds.width() * AdaptiveIconDrawable.getExtraInsetFraction()),
                        (int) (-bounds.height() * AdaptiveIconDrawable.getExtraInsetFraction())
                );
                mBgSpringDrawable = adaptiveIcon.getBackground();
                if (mBgSpringDrawable == null) {
                    mBgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                }
                mBgSpringDrawable.setBounds(bounds);
                mFgSpringDrawable = adaptiveIcon.getForeground();
                if (mFgSpringDrawable == null) {
                    mFgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                }
                mFgSpringDrawable.setBounds(bounds);

                new Handler(Looper.getMainLooper()).post(() -> mOnDragStartCallback.add(() -> {
                    // TODO: Consider fade-in animation
                    // Assign the variable on the UI thread to avoid race conditions.
                    mScaledMaskPath = mask;
                    // Avoid relayout as we do not care about children affecting layout
                    removeAllViewsInLayout();

                    if (info.isDisabled()) {
                        ColorFilter filter = getDisabledColorFilter();
                        mBgSpringDrawable.setColorFilter(filter);
                        mFgSpringDrawable.setColorFilter(filter);
                        mBadge.setColorFilter(filter);
                    }
                    invalidate();
                }));
            }
        });
    }

    /**
     * Called when pre-drag finishes for an icon
     */
    public void onDragStart() {
        if (mContent != null) {
            mContent.setVisibility(VISIBLE);
        }
        mOnDragStartCallback.executeAllAndDestroy();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
    }

    public int getDragRegionWidth() {
        return mDragRegion.width();
    }

    public int getDragRegionHeight() {
        return mDragRegion.height();
    }

    public void setHasDragOffset(boolean hasDragOffset) {
        mHasDragOffset = hasDragOffset;
    }

    public boolean getHasDragOffset() {
        return mHasDragOffset;
    }

    public void setDragRegion(Rect r) {
        mDragRegion = r;
    }

    public Rect getDragRegion() {
        return mDragRegion;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        // Draw after the content
        mHasDrawn = true;
        if (mScaledMaskPath != null) {
            int cnt = canvas.save();
            canvas.clipPath(mScaledMaskPath);
            mBgSpringDrawable.draw(canvas);
            canvas.translate(mTranslateX.mValue, mTranslateY.mValue);
            mFgSpringDrawable.draw(canvas);
            canvas.restoreToCount(cnt);
            mBadge.draw(canvas);
        }
    }

    public void crossFadeContent(Drawable crossFadeDrawable, int duration) {
        if (mContent.getParent() == null) {
            // If the content is already removed, ignore
            return;
        }
        ImageView newContent = getViewFromDrawable(getContext(), crossFadeDrawable);
        // We need to fill the ImageView with the content, otherwise the shapes of the final view
        // and the drag view might not match exactly
        newContent.setScaleType(ImageView.ScaleType.FIT_XY);
        newContent.measure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
        newContent.layout(0, 0, mWidth, mHeight);
        addViewInLayout(newContent, 0, new LayoutParams(mWidth, mHeight));

        AnimatorSet anim = new AnimatorSet();
        anim.play(ObjectAnimator.ofFloat(newContent, VIEW_ALPHA, 0, 1));
        anim.play(ObjectAnimator.ofFloat(mContent, VIEW_ALPHA, 0));
        anim.setDuration(duration).setInterpolator(Interpolators.DECELERATE_1_5);
        anim.start();
    }

    public boolean hasDrawn() {
        return mHasDrawn;
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void show(int touchX, int touchY) {
        mDragLayer.addView(this);

        // Start the pick-up animation
        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(mWidth, mHeight);
        lp.customPosition = true;
        setLayoutParams(lp);

        if (mContent != null) {
            mContent.setVisibility(VISIBLE);
        }

        move(touchX, touchY);
        // Post the animation to skip other expensive work happening on the first frame
        post(mScaleAnim::start);
    }

    public void cancelAnimation() {
        if (mScaleAnim != null && mScaleAnim.isRunning()) {
            mScaleAnim.cancel();
        }
    }

    /** {@code true} if the scale animation has finished. */
    public boolean isScaleAnimationFinished() {
        return mScaleAnimStarted && !mScaleAnim.isRunning();
    }

    /** {@code true} if the shift animation has finished. */
    public boolean isShiftAnimationFinished() {
        return mShiftAnimStarted && !mShiftAnim.isRunning();
    }

    /**
     * Move the window containing this view.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void move(int touchX, int touchY) {
        if (touchX > 0 && touchY > 0 && mLastTouchX > 0 && mLastTouchY > 0
                && mScaledMaskPath != null) {
            mTranslateX.animateToPos(mLastTouchX - touchX);
            mTranslateY.animateToPos(mLastTouchY - touchY);
        }
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        applyTranslation();
    }

    /**
     * Animate this DragView to the given DragLayer coordinates and then remove it.
     */
    public abstract void animateTo(int toTouchX, int toTouchY, Runnable onCompleteRunnable,
            int duration);

    public void animateShift(final int shiftX, final int shiftY) {
        if (mShiftAnim.isStarted()) return;

        // Set mContent visibility to visible to show icon regardless in case it is INVISIBLE.
        if (mContent != null) mContent.setVisibility(VISIBLE);

        mAnimatedShiftX = shiftX;
        mAnimatedShiftY = shiftY;
        applyTranslation();
        mShiftAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = 1 - animation.getAnimatedFraction();
                mAnimatedShiftX = (int) (fraction * shiftX);
                mAnimatedShiftY = (int) (fraction * shiftY);
                applyTranslation();
            }
        });
        mShiftAnim.start();
    }

    private void applyTranslation() {
        setTranslationX(mLastTouchX - mRegistrationX + mAnimatedShiftX);
        setTranslationY(mLastTouchY - mRegistrationY + mAnimatedShiftY);
    }

    /**
     * Detaches {@link #mContent}, if previously attached, from this view.
     *
     * <p>In the case of no change in the drop position, sets {@code reattachToPreviousParent} to
     * {@code true} to attach the {@link #mContent} back to its previous parent.
     */
    public void detachContentView(boolean reattachToPreviousParent) {
        if (mContent != null && mContentViewParent != null && mContentViewInParentViewIndex >= 0) {
            mContent.setVisibility(VISIBLE);
            Picture picture = new Picture();
            mContent.draw(picture.beginRecording(mWidth, mHeight));
            picture.endRecording();
            View view = new View(mActivity);
            view.setBackground(new PictureDrawable(picture));
            view.measure(makeMeasureSpec(mWidth, EXACTLY), makeMeasureSpec(mHeight, EXACTLY));
            view.layout(mContent.getLeft(), mContent.getTop(),
                    mContent.getRight(), mContent.getBottom());
            setClipToOutline(mContent.getClipToOutline());
            setOutlineProvider(mContent.getOutlineProvider());
            addViewInLayout(view, indexOfChild(mContent), mContent.getLayoutParams(), true);

            removeViewInLayout(mContent);
            mContent.setVisibility(reattachToPreviousParent ? VISIBLE : INVISIBLE);
            mContent.setLayoutParams(mContentViewLayoutParams);
            if (reattachToPreviousParent) {
                if (mContent.getParent() instanceof ViewGroup) {
                    ((ViewGroup) mContent.getParent()).removeView(mContent);
                }
                int index = Math.min(mContentViewInParentViewIndex, mContentViewParent.getChildCount());
                mContentViewParent.addView(mContent, index);
            }
            mContentViewParent = null;
            mContentViewInParentViewIndex = -1;
        }
    }

    /**
     * Removes this view from the {@link DragLayer}.
     *
     * <p>If the drag content is a {@link #mContent}, this call doesn't reattach the
     * {@link #mContent} back to its previous parent. To reattach to previous parent, the caller
     * should call {@link #detachContentView} with {@code reattachToPreviousParent} sets to true
     * before this call.
     */
    public void remove() {
        if (mContentViewParent != null) {
            detachContentView(/* reattachToPreviousParent= */ true);
        }
        if (getParent() != null) {
            mDragLayer.removeView(DragView.this);
        }
    }

    public int getBlurSizeOutline() {
        return mBlurSizeOutline;
    }

    public float getInitialScale() {
        return mInitialScale;
    }

    public float getEndScale() {
        return mEndScale;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Returns the current content view that is rendered in the drag view. */
    public View getContentView() {
        return mContent;
    }

    /**
     * Returns the previous {@link ViewGroup} parent of the {@link #mContent} before the drag
     * content is attached to this view.
     */
    @Nullable
    public ViewGroup getContentViewParent() {
        return mContentViewParent;
    }

    /** Return true if {@link mContent} is a {@link AppWidgetHostView}. */
    public boolean containsAppWidgetHostView() {
        return mContent instanceof AppWidgetHostView;
    }

    private static class SpringFloatValue {

        private static final FloatPropertyCompat<SpringFloatValue> VALUE =
                new FloatPropertyCompat<SpringFloatValue>("value") {
                    @Override
                    public float getValue(SpringFloatValue object) {
                        return object.mValue;
                    }

                    @Override
                    public void setValue(SpringFloatValue object, float value) {
                        object.mValue = value;
                        object.mView.invalidate();
                    }
                };

        // Following three values are fine tuned with motion ux designer
        private static final int STIFFNESS = 4000;
        private static final float DAMPENING_RATIO = 1f;
        private static final int PARALLAX_MAX_IN_DP = 8;

        private final View mView;
        private final SpringAnimation mSpring;
        private final float mDelta;

        private float mValue;

        public SpringFloatValue(View view, float range) {
            mView = view;
            mSpring = new SpringAnimation(this, VALUE, 0)
                    .setMinValue(-range).setMaxValue(range)
                    .setSpring(new SpringForce(0)
                            .setDampingRatio(DAMPENING_RATIO)
                            .setStiffness(STIFFNESS));
            mDelta = Math.min(
                    range, view.getResources().getDisplayMetrics().density * PARALLAX_MAX_IN_DP);
        }

        public void animateToPos(float value) {
            mSpring.animateToFinalPosition(Utilities.boundToRange(value, -mDelta, mDelta));
        }
    }

    private static ImageView getViewFromDrawable(Context context, Drawable drawable) {
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(drawable);
        return iv;
    }

    /**
     * Removes any stray DragView from the DragLayer.
     */
    public static void removeAllViews(@NonNull ActivityContext activity) {
        BaseDragLayer dragLayer = activity.getDragLayer();
        // Iterate in reverse order. DragView is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof DragView) {
                dragLayer.removeView(child);
            }
        }
    }
}
```

---

# src/com/android/launcher3/dragndrop/DragOptions.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/dragndrop/DragOptions.java`  
**Lines**: 90 | **Bytes**: 3283

```java
/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.graphics.Point;

import com.android.launcher3.DropTarget;

/**
 * Set of options to control the drag and drop behavior.
 */
public class DragOptions {

    /** Whether or not an accessible drag operation is in progress. */
    public boolean isAccessibleDrag = false;

    /** Whether or not the drag operation is controlled by keyboard. */
    public boolean isKeyboardDrag = false;

    /**
     * Specifies the start location for a simulated DnD (like system drag or accessibility drag),
     * null when using internal DnD
     */
    public Point simulatedDndStartPoint = null;

    /** Determines when a pre-drag should transition to a drag. By default, this is immediate. */
    public PreDragCondition preDragCondition = null;

    /**
     * A drag scale that scales the original drag view size when the preDragCondition is met (or
     * is ignored if preDragEndScale is 0).
     */
    public float preDragEndScale;

    /** Scale of the icons over the workspace icon size. */
    public float intrinsicIconScaleFactor = 1f;

    public boolean isFlingToDelete;

    /**
     * Specifies a condition that must be met before DragListener#onDragStart() is called.
     * By default, there is no condition and onDragStart() is called immediately following
     * DragController#startDrag().
     *
     * This condition can be overridden, and callbacks are provided for the following cases:
     * - The pre-drag starts, but onDragStart() is deferred (onPreDragStart()).
     * - The pre-drag ends before the condition is met (onPreDragEnd(false)).
     * - The actual drag starts when the condition is met (onPreDragEnd(true)).
     */
    public interface PreDragCondition {

        public boolean shouldStartDrag(double distanceDragged);

        /**
         * The pre-drag has started, but onDragStart() is
         * deferred until shouldStartDrag() returns true.
         */
        void onPreDragStart(DropTarget.DragObject dragObject);

        /**
         * The pre-drag has ended. This gets called at the same time as onDragStart()
         * if the condition is met, otherwise at the same time as onDragEnd().
         * @param dragStarted Whether the pre-drag ended because the actual drag started.
         *                    This will be true if the condition was met, otherwise false.
         */
        void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted);

        /**
         * The offset points that should be overridden to update the dragLayer.
         */
        default Point getDragOffset() {
            return new Point(0,0);
        }
    }
}
```

---

# src/com/android/launcher3/dragndrop/DragDriver.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/dragndrop/DragDriver.java`  
**Lines**: 219 | **Bytes**: 7190

```java
/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.os.SystemClock;
import android.view.DragEvent;
import android.view.MotionEvent;

import java.util.function.Consumer;

/**
 * Base class for driving a drag/drop operation.
 */
public abstract class DragDriver {

    protected final EventListener mEventListener;
    protected final Consumer<MotionEvent> mSecondaryEventConsumer;

    public interface EventListener {
        void onDriverDragMove(float x, float y);
        void onDriverDragExitWindow();
        void onDriverDragEnd(float x, float y);
        void onDriverDragCancel();
    }

    public DragDriver(EventListener eventListener, Consumer<MotionEvent> sec) {
        mEventListener = eventListener;
        mSecondaryEventConsumer = sec;
    }

    /**
     * Called to handle system touch event
     */
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Called to handle system touch intercept event
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Called to handle system drag event
     */
    public boolean onDragEvent(DragEvent event) {
        return false;
    }

    /**
     * Created a driver for handing the actual events
     */
    public static DragDriver create(DragController dragController, DragOptions options,
            Consumer<MotionEvent> sec) {
        if (options.simulatedDndStartPoint != null) {
            if  (options.isAccessibleDrag) {
                return null;
            }
            return new SystemDragDriver(dragController, sec);
        } else {
            return new InternalDragDriver(dragController, sec);
        }
    }

    /**
     * Class for driving a system (i.e. framework) drag/drop operation.
     */
    static class SystemDragDriver extends DragDriver {

        private final long mDragStartTime;
        float mLastX = 0;
        float mLastY = 0;

        SystemDragDriver(DragController dragController, Consumer<MotionEvent> sec) {
            super(dragController, sec);
            mDragStartTime = SystemClock.uptimeMillis();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return false;
        }

        /**
         * It creates a temporary {@link MotionEvent} object for secondary consumer
         */
        private void simulateSecondaryMotionEvent(DragEvent event) {
            final int motionAction;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    motionAction = MotionEvent.ACTION_DOWN;
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    motionAction = MotionEvent.ACTION_MOVE;
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    motionAction = MotionEvent.ACTION_UP;
                    break;
                default:
                    return;
            }
            MotionEvent emulatedEvent = MotionEvent.obtain(mDragStartTime,
                    SystemClock.uptimeMillis(), motionAction, event.getX(), event.getY(), 0);
            mSecondaryEventConsumer.accept(emulatedEvent);
            emulatedEvent.recycle();
        }

        @Override
        public boolean onDragEvent(DragEvent event) {
            simulateSecondaryMotionEvent(event);
            final int action = event.getAction();

            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    mEventListener.onDriverDragMove(event.getX(), event.getY());
                    return true;

                case DragEvent.ACTION_DROP:
                    mLastX = event.getX();
                    mLastY = event.getY();
                    mEventListener.onDriverDragMove(event.getX(), event.getY());
                    mEventListener.onDriverDragEnd(mLastX, mLastY);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    mEventListener.onDriverDragExitWindow();
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    mEventListener.onDriverDragCancel();
                    return true;

                default:
                    return false;
            }
        }
    }

    /**
     * Class for driving an internal (i.e. not using framework) drag/drop operation.
     */
    static class InternalDragDriver extends DragDriver {
        private final DragController mDragController;

        InternalDragDriver(DragController dragController, Consumer<MotionEvent> sec) {
            super(dragController, sec);
            mDragController = dragController;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            mSecondaryEventConsumer.accept(ev);
            final int action = ev.getAction();

            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    mEventListener.onDriverDragMove(mDragController.getX(ev),
                            mDragController.getY(ev));
                    break;
                case MotionEvent.ACTION_UP:
                    mEventListener.onDriverDragMove(mDragController.getX(ev),
                            mDragController.getY(ev));
                    mEventListener.onDriverDragEnd(mDragController.getX(ev),
                            mDragController.getY(ev));
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mEventListener.onDriverDragCancel();
                    break;
            }

            return true;
        }


        public boolean onInterceptTouchEvent(MotionEvent ev) {
            mSecondaryEventConsumer.accept(ev);
            final int action = ev.getAction();

            switch (action) {
                case MotionEvent.ACTION_UP:
                    mEventListener.onDriverDragEnd(mDragController.getX(ev),
                            mDragController.getY(ev));
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mEventListener.onDriverDragCancel();
                    break;
            }
            return true;
        }
    }
}


```

---

# src/com/android/launcher3/DropTarget.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/DropTarget.java`  
**Lines**: 149 | **Bytes**: 5773

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Rect;

import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderNameProvider;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Executors;

/**
 * Interface defining an object that can receive a drag.
 *
 */
public interface DropTarget {

    class DragObject {
        public int x = -1;
        public int y = -1;

        /** X offset from the upper-left corner of the cell to where we touched.  */
        public int xOffset = -1;

        /** Y offset from the upper-left corner of the cell to where we touched.  */
        public int yOffset = -1;

        /** This indicates whether a drag is in final stages, either drop or cancel. It
         * differentiates onDragExit, since this is called when the drag is ending, above
         * the current drag target, or when the drag moves off the current drag object.
         */
        public boolean dragComplete = false;

        /** The view that moves around while you drag.  */
        public DragView dragView = null;

        /** The data associated with the object, after item is dropped. */
        public ItemInfo dragInfo = null;

        /** The data associated with the object  being dragged */
        public ItemInfo originalDragInfo = null;

        /** Where the drag originated */
        public DragSource dragSource = null;

        /** Indicates that the drag operation was cancelled */
        public boolean cancelled = false;

        /** Defers removing the DragView from the DragLayer until after the drop animation. */
        public boolean deferDragViewCleanupPostAnimation = true;

        public DragViewStateAnnouncer stateAnnouncer;

        public FolderNameProvider folderNameProvider;

        /** The source view (ie. icon, widget etc.) that is being dragged and which the
         * DragView represents. May be an actual View class or a virtual stand-in */
        public DraggableView originalView = null;

        /** Used for matching DROP event with its corresponding DRAG event on the server side. */
        public final InstanceId logInstanceId = new InstanceIdSequence().newInstanceId();

        public DragObject(Context context) {
            Executors.MODEL_EXECUTOR.post(() ->
                    folderNameProvider = FolderNameProvider.newInstance(context));
        }

        /**
         * This is used to compute the visual center of the dragView. This point is then
         * used to visualize drop locations and determine where to drop an item. The idea is that
         * the visual center represents the user's interpretation of where the item is, and hence
         * is the appropriate point to use when determining drop location.
         */
        public final float[] getVisualCenter(float[] recycle) {
            final float res[] = (recycle == null) ? new float[2] : recycle;
            Rect dragRegion = dragView.getDragRegion();

            // These represent the visual top and left of drag view if a dragRect was provided.
            // If a dragRect was not provided, then they correspond to the actual view left and
            // top, as the dragRect is in that case taken to be the entire dragView.
            int left = x - xOffset - dragRegion.left;
            int top = y - yOffset - dragRegion.top;

            // In order to find the visual center, we shift by half the dragRect
            res[0] = left + dragRegion.width() / 2;
            res[1] = top + dragRegion.height() / 2;

            return res;
        }
    }

    /**
     * Used to temporarily disable certain drop targets
     *
     * @return boolean specifying whether this drop target is currently enabled
     */
    boolean isDropEnabled();

    /**
     * Handle an object being dropped on the DropTarget.
     *
     * This will be called only if this target previously returned true for {@link #acceptDrop}. It
     * is the responsibility of this target to exit out of the spring loaded mode (either
     * immediately or after any pending animations).
     *
     * If the drop was cancelled for some reason, onDrop will never get called, the UI will
     * automatically exit out of this mode.
     */
    void onDrop(DragObject dragObject, DragOptions options);

    void onDragEnter(DragObject dragObject);

    void onDragOver(DragObject dragObject);

    void onDragExit(DragObject dragObject);

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This will be called just before onDrop.
     * @return True if the drop will be accepted, false otherwise.
     */
    boolean acceptDrop(DragObject dragObject);

    void prepareAccessibilityDrop();

    // These methods are implemented in Views
    void getHitRectRelativeToDragLayer(Rect outRect);
}
```

---

