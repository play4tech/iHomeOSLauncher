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

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.FLAG_MULTI_PAGE;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_INACCESSIBLE;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;
import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPELEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPERIGHT;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_BOUNCE_OFFSET;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_PREVIEW_OFFSET;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.util.SystemUiController;

import com.android.app.animation.Interpolators;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.accessibility.WorkspaceAccessibilityHelper;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.celllayout.ReorderAlgorithm;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.dragndrop.SpringLoadedDragController;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.PreviewBackground;
import com.android.launcher3.folder.PreviewItemManager;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.touch.WorkspaceTouchListener;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.util.EdgeEffectCompat;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.OverlayEdgeEffect;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.WallpaperOffsetInterpolator;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.LauncherWidgetHolder.ProviderChangedListener;
import com.android.launcher3.widget.NavigableAppWidgetHostView;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.dragndrop.AppWidgetHostViewDragListener;
import com.android.launcher3.widget.util.WidgetSizes;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayTouchProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import foundation.e.bliss.LauncherAppMonitor;
import foundation.e.bliss.LauncherAppMonitorCallback;
import foundation.e.bliss.folder.GridFolder;
import foundation.e.bliss.glass.FolderGlassView;
import foundation.e.bliss.multimode.MultiModeController;
import foundation.e.bliss.wallpaper.WallpaperEnvironmentBuilder;
import foundation.e.bliss.widgets.WidgetContainer;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 *
 * @param <T> Class that extends View and PageIndicator
 */
public class Workspace<T extends View & PageIndicator> extends PagedView<T>
        implements DropTarget, DragSource, View.OnTouchListener, CellLayoutContainer,
        DragController.DragListener, Insettable, StateHandler<LauncherState>,
        WorkspaceLayoutManager, LauncherBindableItemsContainer, LauncherOverlayCallbacks,
        OnAlarmListener, OnBackPressedHandler {

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #transitionStateShouldAllowDrop()} to return true.
     */
    private static final float ALLOW_DROP_TRANSITION_PROGRESS = 0.25f;

    /**
     * The value that {@link #mTransitionProgress} must be greater than for
     * {@link #isFinishedSwitchingState()} ()} to return true.
     */
    private static final float FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS = 0.5f;

    private static final float SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE = 0.15f;

    private static final boolean ENFORCE_DRAG_EVENT_ORDER = false;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    private static final boolean DEBUG_DRAG_LOG = false;
    private View mCachedDockGlass;

    // DEFAULT_PAGE has been replaced with getDefaultPage()

    private final int mAllAppsIconSize;

    private LayoutTransition mLayoutTransition;
    @Thunk
    final WallpaperManager mWallpaperManager;

    protected ShortcutAndWidgetContainer mDragSourceInternal;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Thunk
    public final IntSparseArrayMap<CellLayout> mWorkspaceScreens = new IntSparseArrayMap<>();

    @Thunk
    final IntArray mScreenOrder = new IntArray();

    @Thunk
    boolean mDeferRemoveExtraEmptyScreen = false;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    protected CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    @Thunk
    int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    /**
     * The CellLayout that is currently being dragged over
     */
    @Thunk
    CellLayout mDragTargetLayout = null;
    /**
     * The CellLayout that we will show as highlighted
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    @Thunk
    final Launcher mLauncher;
    @Thunk
    DragController mDragController;

    protected final int[] mTempXY = new int[2];
    private final float[] mTempFXY = new float[2];
    private final Rect mTempRect = new Rect();
    @Thunk
    float[] mDragViewVisualCenter = new float[2];

    private SpringLoadedDragController mSpringLoadedDragController;

    private boolean mIsSwitchingState = false;

    boolean mChildrenLayersEnabled = true;

    private boolean mStripScreensOnPageStopMoving = false;

    private boolean mWorkspaceFadeInAdjacentScreens;

    final WallpaperOffsetInterpolator mWallpaperOffset;
    private boolean mUnlockWallpaperFromDefaultPageOnLayout;

    public static final int REORDER_TIMEOUT = 35;
    protected final Alarm mReorderAlarm = new Alarm();
    public static final int FOLDER_CREATION_TIMEOUT = 650;
    protected final Alarm mFolderCreationAlarm = new Alarm();
    public static final int REVERT_TIMEOUT = 150;
    protected final Alarm mRevertAlarm = new Alarm();
    private final float[] mPrevDragViewVisualCenter = new float[]{-1f, -1f};
    private View mPendingFolderDragOverView = null;
    private PreviewBackground mFolderCreateBg;
    private FolderIcon mDragOverFolderIcon = null;
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;

    // Variables relating to touch disambiguation (scrolling workspace vs. scrolling a widget)
    private float mXDown;
    private float mYDown;
    private View mFirstPagePinnedItem;
    private View mAppLibraryPinnedItem;
    private CellLayout mAppLibraryCellLayout;

    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    // Relating to the animation of items being dropped externally
    public static final int ANIMATE_INTO_POSITION_AND_DISAPPEAR = 0;
    public static final int ANIMATE_INTO_POSITION_AND_REMAIN = 1;
    public static final int ANIMATE_INTO_POSITION_AND_RESIZE = 2;
    public static final int COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION = 3;
    public static final int CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION = 4;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_CREATE_FOLDER = 1;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;
    private static final int DRAG_MODE_REORDER = 3;
    protected int mDragMode = DRAG_MODE_NONE;
    @Thunk
    int mLastReorderX = -1;
    @Thunk
    int mLastReorderY = -1;

    private SparseArray<Parcelable> mSavedStates;
    private final IntArray mRestoredPages = new IntArray();

    private float mCurrentScale;
    private float mTransitionProgress;

    // State related to Launcher Overlay
    private OverlayEdgeEffect mOverlayEdgeEffect;
    private boolean mOverlayShown = false;
    private float mOverlayProgress; // 1 -> overlay completely visible, 0 -> home visible
    private final List<LauncherOverlayCallbacks> mOverlayCallbacks = new ArrayList<>();

    private boolean mForceDrawAdjacentPages = false;

    // Handles workspace state transitions
    private final WorkspaceStateTransitionAnimation mStateTransitionAnimation;

    private final StatsLogManager mStatsLogManager;

    private boolean isWobbling = false;
    private ItemInfo mDragObjectInfo;
    private Animation mWobbleAnimation;
    private Animation mReverseWobbleAnimation;

    private Alarm wobbleExpireAlarm = new Alarm();
    public static final int WOBBLE_EXPIRATION_TIMEOUT = 120000;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context  The application's context.
     * @param attrs    The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLauncher = Launcher.getLauncher(context);
        mStateTransitionAnimation = new WorkspaceStateTransitionAnimation(mLauncher, this);

        mStatsLogManager = StatsLogManager.newInstance(context);        
        mWallpaperManager = WallpaperManager.getInstance(context);
        mAllAppsIconSize = mLauncher.getDeviceProfile().allAppsIconSizePx;
        mWallpaperOffset = new WallpaperOffsetInterpolator(this);

        setHapticFeedbackEnabled(false);
        initWorkspace();

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);
        setOnTouchListener(new WorkspaceTouchListener(mLauncher, this));
        wobbleExpireAlarm.setOnAlarmListener(this);
        mWobbleAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.wobble);
        mReverseWobbleAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.wobble_reverse);
    }

    private final LauncherAppMonitorCallback mLauncherAppMonitorCallback =
            new LauncherAppMonitorCallback() {
        @Override
        public void onReceiveHomeIntent() {
            if (isWobbling() && Folder.getOpen(mLauncher) == null) {
                wobbleLayouts(false);
            }
        }
    };

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();

        mWorkspaceFadeInAdjacentScreens = grid.shouldFadeAdjacentWorkspaceScreens();

        Rect padding = grid.workspacePadding;
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        mInsets.set(insets);

        if (mWorkspaceFadeInAdjacentScreens) {
            // In landscape mode the page spacing is set to the default.
            setPageSpacing(grid.edgeMarginPx);
        } else {
            // In portrait, we want the pages spaced such that there is no
            // overhang of the previous / next page into the current page viewport.
            // We assume symmetrical padding in portrait mode.
            int maxInsets = Math.max(insets.left, insets.right);
            int maxPadding = Math.max(grid.edgeMarginPx, padding.left + 1);
            setPageSpacing(Math.max(maxInsets, maxPadding));
        }

        updateCellLayoutMeasures();
        updateWorkspaceWidgetsSizes();
        if (mFirstPagePinnedItem instanceof WidgetContainer) {
            ((WidgetContainer) mFirstPagePinnedItem).updateWidgets();
        }
        if (mAppLibraryPinnedItem instanceof Insettable) {
            ((Insettable) mAppLibraryPinnedItem).setInsets(insets);
        }
        setPageIndicatorInset(insets);
    }

    private void setPageIndicatorInset() {
        setPageIndicatorInset(mLauncher.getDeviceProfile().getInsets());
    }

    private void setPageIndicatorInset(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPageIndicator.getLayoutParams();

        // Set insets for page indicator
        Rect padding = grid.workspacePadding;
        if (grid.isVerticalBarLayout()) {
            lp.leftMargin = padding.left + grid.workspaceCellPaddingXPx;
            lp.rightMargin = padding.right + grid.workspaceCellPaddingXPx;
            lp.bottomMargin = padding.bottom;
        } else {
            lp.leftMargin = lp.rightMargin = 0;
            int bottomInset = insets != null ? insets.bottom : grid.getInsets().bottom;
            if (grid.isFloatingDockEnabled() && grid.hotseatGeometry != null) {
                lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                int dockTopFromBottom = bottomInset + grid.hotseatBarBottomSpacePx
                        + grid.hotseatGeometry.getBounds().height();
                lp.bottomMargin = dockTopFromBottom + grid.hotseatGeometry.getIndicatorMargin();
                lp.topMargin = 0;
            } else {
                lp.bottomMargin = bottomInset + grid.hotseatBarSizePx;
            }
        }
        mPageIndicator.setLayoutParams(lp);
    }

    private void updateCellLayoutMeasures() {
        Rect padding = mLauncher.getDeviceProfile().cellLayoutPaddingPx;
        boolean qsbOnFirstScreen = FeatureFlags.QSB_ON_FIRST_SCREEN.get();
        mWorkspaceScreens.forEach(
                s -> {
                    if (s == mWorkspaceScreens.get(APP_LIBRARY_SCREEN_ID)) {
                        s.setPadding(0, 0, 0, 0);
                    } else if (qsbOnFirstScreen && s == mWorkspaceScreens.get(FIRST_SCREEN_ID)) {
                        int widgetPadding = getResources().getDimensionPixelSize(R.dimen.widget_page_all_padding);
                        s.setPadding(widgetPadding, 0, widgetPadding, 0);
                    } else {
                        s.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                    }
                    s.setSpaceBetweenCellLayoutsPx(getPageSpacing() / 4);
                });
    }

    private void updateWorkspaceWidgetsSizes() {
        int numberOfScreens = mScreenOrder.size();
        for (int i = 0; i < numberOfScreens; i++) {
            ShortcutAndWidgetContainer shortcutAndWidgetContainer =
                    mWorkspaceScreens.get(mScreenOrder.get(i)).getShortcutsAndWidgets();
            int shortcutsAndWidgetCount = shortcutAndWidgetContainer.getChildCount();
            for (int j = 0; j < shortcutsAndWidgetCount; j++) {
                View view = shortcutAndWidgetContainer.getChildAt(j);
                if (view instanceof LauncherAppWidgetHostView
                        && view.getTag() instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo launcherAppWidgetInfo =
                            (LauncherAppWidgetInfo) view.getTag();
                    WidgetSizes.updateWidgetSizeRanges((LauncherAppWidgetHostView) view,
                            mLauncher, launcherAppWidgetInfo.spanX, launcherAppWidgetInfo.spanY);
                }
            }
        }
    }

    /**
     * Estimates the size of an item using spans: hSpan, vSpan.
     *
     * @return MAX_VALUE for each dimension if unsuccessful.
     */
    public int[] estimateItemSize(ItemInfo itemInfo) {
        int[] size = new int[2];
        if (getChildCount() > 0) {
            // Use the first page to estimate the child position
            CellLayout cl = (CellLayout) getChildAt(0);
            boolean isWidget = itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;

            Rect r = estimateItemPosition(cl, 0, 0, itemInfo.spanX, itemInfo.spanY);

            float scale = 1;
            if (isWidget) {
                DeviceProfile profile = mLauncher.getDeviceProfile();
                final PointF appWidgetScale = profile.getAppWidgetScale(null);
                scale = Utilities.shrinkRect(r, appWidgetScale.x, appWidgetScale.y);
            }
            size[0] = r.width();
            size[1] = r.height();

            if (isWidget) {
                size[0] /= scale;
                size[1] /= scale;
            }
            return size;
        } else {
            size[0] = Integer.MAX_VALUE;
            size[1] = Integer.MAX_VALUE;
            return size;
        }
    }

    public float getWallpaperOffsetForCenterPage() {
        return getWallpaperOffsetForPage(getPageNearestToCenterOfScreen());
    }

    private float getWallpaperOffsetForPage(int page) {
        int pageScroll = getScrollForPage(page);
        return mWallpaperOffset.wallpaperOffsetForScroll(pageScroll);
    }

    /**
     * Returns the number of pages used for the wallpaper parallax.
     */
    public int getNumPagesForWallpaperParallax() {
        return mWallpaperOffset.getNumPagesForWallpaperParallax();
    }

    public Rect estimateItemPosition(CellLayout cl, int hCell, int vCell, int hSpan, int vSpan) {
        Rect r = new Rect();
        cl.cellToRect(hCell, vCell, hSpan, vSpan, r);
        return r;
    }

    public void logIconLocations(String stage) {
        if (!DEBUG_DRAG_LOG) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== [IconDragLogger] ").append(stage).append(" (Page ").append(mCurrentPage).append(") ===\n");
        CellLayout currentLayout = (CellLayout) getChildAt(mCurrentPage);
        if (currentLayout != null) {
            ShortcutAndWidgetContainer container = currentLayout.getShortcutsAndWidgets();
            for (int i = 0; i < container.getChildCount(); i++) {
                View v = container.getChildAt(i);
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                Object info = v.getTag();
                String title = "Unknown";
                if (info instanceof ItemInfo) {
                    ItemInfo itemInfo = (ItemInfo) info;
                    title = itemInfo.title != null ? itemInfo.title.toString() : itemInfo.getClass().getSimpleName();
                }
                sb.append("  • Icon '").append(title)
                  .append("' -> Cell(").append(lp.getCellX()).append(",").append(lp.getCellY()).append(")")
                  .append(" [pixel: (").append(lp.x).append(",").append(lp.y).append(")]\n");
            }
        }
        android.util.Log.d("IconDragLogger", sb.toString());
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        if (dragObject.dragSource == this) {
            setAlpha(1f);
        }
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragStart", 0, 0);
        }
        mDragObjectInfo = dragObject.dragInfo;
        if (DEBUG_DRAG_LOG) {
            String itemTitle = (dragObject.dragInfo != null && dragObject.dragInfo.title != null) ? dragObject.dragInfo.title.toString() : "Item";
            android.util.Log.d("IconDragLogger", "START DRAG -> Picked up icon '" + itemTitle + "'");
            logIconLocations("START DRAG");
        }

        if (mDragInfo != null && mDragInfo.cell != null) {
            CellLayout layout = getParentCellLayoutForView(mDragInfo.cell);
            if (layout == null) {
                View parent = (mDragInfo.cell instanceof LauncherAppWidgetHostView
                        && dragObject.dragView != null && dragObject.dragView.getContentViewParent() != null)
                        ? (View) dragObject.dragView.getContentViewParent().getParent()
                        : (mDragInfo.cell.getParent() != null ? (View) mDragInfo.cell.getParent().getParent() : null);
                if (parent instanceof CellLayout) {
                    layout = (CellLayout) parent;
                }
            }
            if (layout != null) {
                layout.markCellsAsUnoccupiedForView(mDragInfo.cell);
            }
        }

        updateChildrenLayersEnabled();

        // Do not add a new page if it is a accessible drag which was not started by the workspace.
        // We do not support accessibility drag from other sources and instead provide a direct
        // action for move/add to homescreen.
        // When a accessible drag is started by the folder, we only allow rearranging withing the
        // folder.
        boolean addNewPage = !(options.isAccessibleDrag && dragObject.dragSource != this);
        if (addNewPage) {
            mDeferRemoveExtraEmptyScreen = false;
            addExtraEmptyScreenOnDrag(dragObject);

            if (dragObject.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    && dragObject.dragSource != this) {
                // When dragging a widget from different source, move to a page which has
                // enough space to place this widget (after rearranging/resizing). We special case
                // widgets as they cannot be placed inside a folder.
                // Start at the current page and search right (on LTR) until finding a page with
                // enough space. Since an empty screen is the furthest right, a page must be found.
                int currentPage = getDestinationPage();
                for (int pageIndex = currentPage; pageIndex < getPageCount(); pageIndex++) {
                    View pageView = getPageAt(pageIndex);
                    if (pageView instanceof CellLayout) {
                        CellLayout page = (CellLayout) pageView;
                        if (page.hasReorderSolution(dragObject.dragInfo)) {
                            setCurrentPage(pageIndex);
                            break;
                        }
                    }
                }
            }
        }

        if (!mLauncher.isInState(EDIT_MODE)) {
            mLauncher.getStateManager().goToState(SPRING_LOADED);
        }
        mStatsLogManager.logger().withItemInfo(dragObject.dragInfo)
                .withInstanceId(dragObject.logInstanceId)
                .log(LauncherEvent.LAUNCHER_ITEM_DRAG_STARTED);
    }

    private boolean isTwoPanelEnabled() {
        return !FOLDABLE_SINGLE_PAGE.get() && mLauncher.mDeviceProfile.isTwoPanels;
    }

    public void deferRemoveExtraEmptyScreen() {
        mDeferRemoveExtraEmptyScreen = true;
    }

    @Override
    public void onDragEnd() {
        DragTrace.log("Workspace.onDragEnd");
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnd", 0, 0);
        }

        updateChildrenLayersEnabled();
        StateManager<LauncherState> stateManager = mLauncher.getStateManager();
        stateManager.addStateListener(new StateManager.StateListener<LauncherState>() {
            @Override
            public void onStateTransitionComplete(LauncherState finalState) {
                if (finalState == NORMAL) {
                    if (!mDeferRemoveExtraEmptyScreen) {
                        removeExtraEmptyScreen(true /* stripEmptyScreens */);
                    }
                    stateManager.removeStateListener(this);
                }
            }
        });

        mRevertAlarm.cancelAlarm();
        mPrevDragViewVisualCenter[0] = mPrevDragViewVisualCenter[1] = -1f;
        mDragInfo = null;
        mDragSourceInternal = null;
        mDragObjectInfo = null;
        isWidget = false;
        if (isWobbling()) {
            resetWobbleAlarm();
            wobbleLayouts(true);
        }
        if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
            setAlpha(1f);
            if (isAppLibraryCurrentPage()) {
                hideDockForAppLibrary();
            } else {
                if (mLauncher.getHotseat() != null) {
                    mLauncher.getHotseat().setIconsAlpha(1f);
                }
            }
        }
        for (int i = 0; i < getPageCount(); i++) {
            View page = getChildAt(i);
            if (page instanceof CellLayout) {
                CellLayout cl = (CellLayout) page;
                if (cl.isItemPlacementDirty()) {
                    cl.revertTempState();
                }
            }
        }
        if (mLauncher.getHotseat() != null && mLauncher.getHotseat().isItemPlacementDirty()) {
            mLauncher.getHotseat().revertTempState();
        }
        dumpFullWorkspaceState("Workspace.onDragEnd");
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        mCurrentPage = getDefaultPage();
        setClipToPadding(false);
        setClipChildren(false);

        setupLayoutTransition();

        // Set the wallpaper dimensions when Launcher starts up
        setWallpaperDimension();
    }

    // Removed recursive onMeasure re-measurement of mAppLibraryCellLayout to prevent infinite layout passes

    private void setupLayoutTransition() {
        // We want to show layout transitions when pages are deleted, to close the gap.
        mLayoutTransition = new LayoutTransition();

        mLayoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        mLayoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        // Change the interpolators such that the fade animation plays before the move animation.
        // This prevents empty adjacent pages to overlay during animation
        mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING,
                Interpolators.clampToProgress(Interpolators.ACCELERATE_DECELERATE, 0, 0.5f));
        mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
                Interpolators.clampToProgress(Interpolators.ACCELERATE_DECELERATE, 0.5f, 1));

        mLayoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        setLayoutTransition(mLayoutTransition);
    }

    void enableLayoutTransitions() {
        setLayoutTransition(mLayoutTransition);
    }

    void disableLayoutTransitions() {
        setLayoutTransition(null);
    }

    @Override
    public void onViewAdded(View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        super.onViewAdded(child);
    }

    /**
     * Initializes and binds the first page
     */
    public void bindAndInitFirstWorkspaceScreen() {
        if ((!FeatureFlags.QSB_ON_FIRST_SCREEN.get()
                || !mLauncher.getIsFirstPagePinnedItemEnabled())
                || shouldShowFirstPageWidget()) {
            mFirstPagePinnedItem = null;
            return;
        }

        // Add the first page
        CellLayout firstPage = insertNewWorkspaceScreen(Workspace.FIRST_SCREEN_ID, getChildCount());
        if (mFirstPagePinnedItem == null) {
            // In transposed layout, we add the first page pinned widget in the Grid.
            // As workspace does not touch the edges, we do not need a full
            // width first page pinned item.
            mFirstPagePinnedItem = LayoutInflater.from(getContext())
                    .inflate(R.layout.search_container_workspace_v2, firstPage, false);
        }

        int spanX = firstPage.getCountX();
        int spanY = firstPage.getCountY();
        CellLayoutLayoutParams lp = new CellLayoutLayoutParams(0, 0, spanX, spanY);

        lp.canReorder = false;
        lp.isFullscreen = true;

        if (mFirstPagePinnedItem instanceof Insettable) {
            ((Insettable)mFirstPagePinnedItem).setInsets(mInsets);
        }

        if (!firstPage.addViewToCellLayout(
                mFirstPagePinnedItem, 0, R.id.search_container_workspace, lp, true)) {
            Log.e(TAG, "Failed to add to item at (0, 0) to CellLayout");
            mFirstPagePinnedItem = null;
        }

        insertNewWorkspaceScreen(SECOND_SCREEN_ID, getChildCount());
    }

    public void bindAndInitAppLibraryScreen() {
        // Remove existing App Library page if present (handles re-bind)
        if (mAppLibraryCellLayout != null) {
            mWorkspaceScreens.remove(APP_LIBRARY_SCREEN_ID);
            mScreenOrder.removeValue(APP_LIBRARY_SCREEN_ID);
            removeView(mAppLibraryCellLayout);
        }
        
        // Create CellLayout and add as the absolute last child
        mAppLibraryCellLayout = insertNewWorkspaceScreen(
                APP_LIBRARY_SCREEN_ID, getChildCount());
        
        // Mirror FIRST_SCREEN_ID setup: disable drag, disable jail, full padding control
        mAppLibraryCellLayout.disableJailContent();
        mAppLibraryCellLayout.disableDragTarget();
        mAppLibraryCellLayout.setPadding(0, 0, 0, 0);
        
        // Inflate and pin the AppLibraryPage
        mAppLibraryPinnedItem = LayoutInflater.from(getContext())
                .inflate(R.layout.app_library_page_container, mAppLibraryCellLayout, false);
        
        int spanX = mAppLibraryCellLayout.getCountX();
        int spanY = mAppLibraryCellLayout.getCountY();
        CellLayoutLayoutParams lp = new CellLayoutLayoutParams(0, 0, spanX, spanY);
        lp.canReorder = false;
        lp.isFullscreen = true;
        
        if (mAppLibraryPinnedItem instanceof Insettable) {
            ((Insettable) mAppLibraryPinnedItem).setInsets(mInsets);
        }
        
        mAppLibraryCellLayout.addViewToCellLayout(
                mAppLibraryPinnedItem, 0, R.id.app_library_page, lp, true);
    }

    public View getAppLibraryPinnedItem() {
        return mAppLibraryPinnedItem;
    }

    public com.android.launcher3.applibrary.view.AppLibraryPage getAppLibraryPage() {
        if (mAppLibraryPinnedItem instanceof com.android.launcher3.applibrary.view.AppLibraryPage) {
            return (com.android.launcher3.applibrary.view.AppLibraryPage) mAppLibraryPinnedItem;
        }
        return null;
    }

    @Nullable
    public CellLayout getAppLibraryCellLayout() {
        return mAppLibraryCellLayout;
    }

    private int mPreviousPageBeforeSearch = -1;

    public void openAppLibrarySearch() {
        com.android.launcher3.applibrary.view.SpotlightSearchOverlay.show(mLauncher);
    }

    public void onAppLibrarySearchExited() {
    }

    public boolean isAppLibraryCurrentPage() {
        int appLibIndex = getPageIndexForScreenId(APP_LIBRARY_SCREEN_ID);
        if (appLibIndex == -1) return false;
        if (isPageInTransition()) return false;
        return getCurrentPage() == appLibIndex;
    }

    public void removeAllWorkspaceScreens() {
        // Disable all layout transitions before removing all pages to ensure that we don't get the
        // transition animations competing with us changing the scroll when we add pages
        disableLayoutTransitions();

        // Recycle the first page pinned item
        if (mFirstPagePinnedItem != null && mFirstPagePinnedItem.getParent() != null) {
            ((ViewGroup) mFirstPagePinnedItem.getParent()).removeView(mFirstPagePinnedItem);
        }
        
        // Recycle the app library pinned item
        if (mAppLibraryPinnedItem != null && mAppLibraryPinnedItem.getParent() != null) {
            ((ViewGroup) mAppLibraryPinnedItem.getParent()).removeView(mAppLibraryPinnedItem);
        }
        mAppLibraryPinnedItem = null;
        mAppLibraryCellLayout = null;

        // Remove the pages and clear the screen models
        removeFolderListeners();
        removeAllViews();
        mScreenOrder.clear();
        mWorkspaceScreens.clear();

        // Ensure that the first page is always present
        if (!ENABLE_SMARTSPACE_REMOVAL.get()) {
            bindAndInitFirstWorkspaceScreen();
        }

        // Remove any deferred refresh callbacks
        mLauncher.mHandler.removeCallbacksAndMessages(DeferredWidgetRefresh.class);

        // Re-enable the layout transitions
        enableLayoutTransitions();
    }

    public void insertNewWorkspaceScreenBeforeEmptyScreen(int screenId) {
        // Find the index to insert this view into.  If the empty screen exists, then
        // insert it before that.
        int insertIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        }
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.size();
        }
        insertNewWorkspaceScreen(screenId, insertIndex);
    }

    public void insertNewWorkspaceScreen(int screenId) {
        int insertIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = getChildCount();
        }
        insertNewWorkspaceScreen(screenId, insertIndex);
    }

    public CellLayout insertNewWorkspaceScreen(int screenId, int insertIndex) {
        if (mWorkspaceScreens.containsKey(screenId)) {
            throw new RuntimeException("Screen id " + screenId + " already exists!");
        }

        // Inflate the cell layout, but do not add it automatically so that we can get the newly
        // created CellLayout.
        DeviceProfile dp = mLauncher.getDeviceProfile();
        CellLayout newScreen;
        if (FOLDABLE_SINGLE_PAGE.get() && dp.isTwoPanels) {
            newScreen = (CellLayout) LayoutInflater.from(getContext()).inflate(
                    R.layout.workspace_screen_foldable, this, false /* attachToRoot */);
        } else {
            newScreen = (CellLayout) LayoutInflater.from(getContext()).inflate(
                    R.layout.workspace_screen, this, false /* attachToRoot */);
        }
        newScreen.setCellLayoutContainer(this);

        mWorkspaceScreens.put(screenId, newScreen);
        mScreenOrder.add(insertIndex, screenId);
        if ((screenId == FIRST_SCREEN_ID && FeatureFlags.QSB_ON_FIRST_SCREEN.get())
                || screenId == APP_LIBRARY_SCREEN_ID) {
            newScreen.disableJailContent();
            newScreen.disableDragTarget();
            newScreen.setPadding(0, 0, 0, 0);

            addFullScreenPage(newScreen, insertIndex);
        } else {
            addView(newScreen, insertIndex);
        }
        mStateTransitionAnimation.applyChildState(
                mLauncher.getStateManager().getState(), newScreen, insertIndex);

        updatePageScrollValues();
        updateCellLayoutMeasures();
        return newScreen;
    }

    private void addExtraEmptyScreenOnDrag(DragObject dragObject) {
        boolean lastChildOnScreen = false;
        boolean childOnFinalScreen = false;

        if (mDragSourceInternal != null) {
            int dragSourceChildCount = mDragSourceInternal.getChildCount();

            // If the icon was dragged from Hotseat, there is no page pair
            if (isTwoPanelEnabled() && !(mDragSourceInternal.getParent() instanceof Hotseat)) {
                int pagePairScreenId = getScreenPair(getCellPosMapper().mapModelToPresenter(
                        dragObject.dragInfo).screenId);
                CellLayout pagePair = mWorkspaceScreens.get(pagePairScreenId);
                dragSourceChildCount += pagePair.getShortcutsAndWidgets().getChildCount();
            }

            // When the drag view content is a LauncherAppWidgetHostView, we should increment the
            // drag source child count by 1 because the widget in drag has been detached from its
            // original parent, ShortcutAndWidgetContainer, and reattached to the DragView.
            if (dragObject.dragView.getContentView() instanceof LauncherAppWidgetHostView) {
                dragSourceChildCount++;
            }

            if (dragSourceChildCount == 1) {
                lastChildOnScreen = true;
            }
            CellLayout cl = (CellLayout) mDragSourceInternal.getParent();
            int finalWorkspacePageIndex = getPageCount() - 1;
            if (mAppLibraryPinnedItem != null && mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID) >= 0) {
                finalWorkspacePageIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID) - 1;
            }
            if (!FOLDABLE_SINGLE_PAGE.get() && getLeftmostVisiblePageForIndex(indexOfChild(cl))
                    == getLeftmostVisiblePageForIndex(finalWorkspacePageIndex)) {
                childOnFinalScreen = true;
            }
        }

        // If this is the last item on the final screen
        if (lastChildOnScreen && childOnFinalScreen) {
            return;
        }

        forEachExtraEmptyPageId(extraEmptyPageId -> {
            if (!mWorkspaceScreens.containsKey(extraEmptyPageId)) {
                insertNewWorkspaceScreen(extraEmptyPageId);
            }
        });
    }


    /**
     * Returns if the given screenId is already in the Workspace
     */
    public boolean containsScreenId(int screenId) {
        return this.mWorkspaceScreens.containsKey(screenId);
    }

    /**
     * Inserts extra empty pages to the end of the existing workspaces.
     * Usually we add one extra empty screen, but when two panel home is enabled we add
     * two extra screens.
     **/
    public void addExtraEmptyScreens() {
        forEachExtraEmptyPageId(extraEmptyPageId -> {
            if (!mWorkspaceScreens.containsKey(extraEmptyPageId)) {
                insertNewWorkspaceScreen(extraEmptyPageId);
            }
        });
    }

    /**
     * Calls the consumer with all the necessary extra empty page IDs.
     * On a normal one panel Workspace that means only EXTRA_EMPTY_SCREEN_ID,
     * but in a two panel scenario this also includes EXTRA_EMPTY_SCREEN_SECOND_ID.
     */
    private void forEachExtraEmptyPageId(Consumer<Integer> callback) {
        callback.accept(EXTRA_EMPTY_SCREEN_ID);
        if (isTwoPanelEnabled()) {
            callback.accept(EXTRA_EMPTY_SCREEN_SECOND_ID);
        }
    }

    /**
     * If two panel home is enabled we convert the last two screens that are visible at the same
     * time. In other cases we only convert the last page.
     */
    private void convertFinalScreenToEmptyScreenIfNecessary() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return;
        }

        int panelCount = getPanelCount();
        if (hasExtraEmptyScreens() || mScreenOrder.size() < panelCount) {
            return;
        }

        SparseArray<CellLayout> finalScreens = new SparseArray<>();

        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        int pageCount = appLibIndex >= 0 ? appLibIndex : mScreenOrder.size();

        // If there are no real (non-app-library) pages left, there's nothing to convert.
        // Without this guard, pageIndex below can go negative and IntArray.get() throws.
        if (pageCount < panelCount) {
            return;
        }

        // First we add the last page(s) to the finalScreens collection. The number of final pages
        // depends on the panel count.
        for (int pageIndex = pageCount - panelCount; pageIndex < pageCount; pageIndex++) {
            int screenId = mScreenOrder.get(pageIndex);
            CellLayout screen = mWorkspaceScreens.get(screenId);
            if (screen == null || screen.getShortcutsAndWidgets().getChildCount() != 0
                    || screen.isDropPending()
                    || (FeatureFlags.QSB_ON_FIRST_SCREEN.get() && screenId == SECOND_SCREEN_ID)) {
                // Final screen doesn't exist or it isn't empty or there's a pending drop or
                // It is an empty page used when QSB is there
                return;
            }
            finalScreens.append(screenId, screen);
        }

        // Then we remove the final screens from the collections (but not from the view hierarchy)
        // and we store them as extra empty screens.
        for (int i = 0; i < finalScreens.size(); i++) {
            int screenId = finalScreens.keyAt(i);

            // We don't want to remove the first screen even if it's empty because that's where
            // first page pinned item would go if it gets turned back on.
            if (ENABLE_SMARTSPACE_REMOVAL.get() && screenId == FIRST_SCREEN_ID) {
                continue;
            }

            int emptyIndex = mScreenOrder.indexOf(screenId);
            CellLayout screen = finalScreens.get(screenId);

            mWorkspaceScreens.remove(screenId);
            mScreenOrder.removeValue(screenId);

            int newScreenId = mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)
                    ? EXTRA_EMPTY_SCREEN_SECOND_ID : EXTRA_EMPTY_SCREEN_ID;
            mWorkspaceScreens.put(newScreenId, screen);
            if (emptyIndex >= 0) {
                mScreenOrder.add(emptyIndex, newScreenId);
            } else {
                addScreenToOrder(newScreenId);
            }
            ensureAppLibraryAtEnd();
        }
    }

    private void addScreenToOrder(int screenId) {
        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (appLibIndex >= 0) {
            mScreenOrder.add(appLibIndex, screenId);
        } else {
            mScreenOrder.add(screenId);
        }
    }

    private void ensureAppLibraryAtEnd() {
        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (appLibIndex >= 0 && appLibIndex != mScreenOrder.size() - 1) {
            mScreenOrder.removeValue(APP_LIBRARY_SCREEN_ID);
            mScreenOrder.add(APP_LIBRARY_SCREEN_ID);
        }
        if (mAppLibraryCellLayout != null) {
            int childIndex = indexOfChild(mAppLibraryCellLayout);
            if (childIndex >= 0 && childIndex != getChildCount() - 1) {
                removeView(mAppLibraryCellLayout);
                addView(mAppLibraryCellLayout);
            }
        }
    }

    public void removeExtraEmptyScreen(boolean stripEmptyScreens) {
        removeExtraEmptyScreenDelayed(0, stripEmptyScreens, null);
    }

    /**
     * The purpose of this method is to remove empty pages from Workspace.
     * Empty page(s) from the end of mWorkspaceScreens will always be removed. The pages with
     * ID = Workspace.EXTRA_EMPTY_SCREEN_IDS will be removed if there are other non-empty pages.
     * If there are no more non-empty pages left, extra empty page(s) will either stay or get added.
     * <p>
     * If stripEmptyScreens is true, all empty pages (not just the ones on the end) will be removed
     * from the Workspace, and if there are no more pages left then extra empty page(s) will be
     * added.
     * <p>
     * The number of extra empty pages is equal to what getPanelCount() returns.
     * <p>
     * After the method returns the possible pages are:
     * stripEmptyScreens = true : [non-empty pages, extra empty page(s) alone]
     * stripEmptyScreens = false : [non-empty pages, empty pages (not in the end),
     * extra empty page(s) alone]
     */
    public void removeExtraEmptyScreenDelayed(
            int delay, boolean stripEmptyScreens, Runnable onComplete) {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading
            return;
        }

        if (delay > 0) {
            postDelayed(
                    () -> removeExtraEmptyScreenDelayed(0, stripEmptyScreens, onComplete), delay);
            return;
        }

        // First we convert the last page to an extra page if the last page is empty
        // and we don't already have an extra page.
        convertFinalScreenToEmptyScreenIfNecessary();
        // Then we remove the extra page(s) if they are not the only pages left in Workspace.
        if (hasExtraEmptyScreens()) {
            forEachExtraEmptyPageId(extraEmptyPageId -> {
                removeView(mWorkspaceScreens.get(extraEmptyPageId));
                mWorkspaceScreens.remove(extraEmptyPageId);
                mScreenOrder.removeValue(extraEmptyPageId);
            });

            setCurrentPage(getNextPage());

            // Update the page indicator to reflect the removed page.
            showPageIndicatorAtCurrentScroll();
        }

        if (stripEmptyScreens) {
            // This will remove all empty pages from the Workspace. If there are no more pages left,
            // it will add extra page(s) so that users can put items on at least one page.
            stripEmptyScreens();
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    public boolean hasExtraEmptyScreens() {
        return mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)
                && getChildCount() > getPanelCount()
                && (!isTwoPanelEnabled()
                || mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_SECOND_ID));
    }

    /**
     * Commits the extra empty pages then returns the screen ids of those new screens.
     * Usually there's only one extra empty screen, but when two panel home is enabled we commit
     * two extra screens.
     * <p>
     * Returns an empty IntSet in case we cannot commit any new screens.
     */
    public IntSet commitExtraEmptyScreens() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return new IntSet();
        }

        IntSet extraEmptyPageIds = new IntSet();
        forEachExtraEmptyPageId(extraEmptyPageId ->
                extraEmptyPageIds.add(commitExtraEmptyScreen(extraEmptyPageId)));

        return extraEmptyPageIds;
    }

    private int commitExtraEmptyScreen(int emptyScreenId) {
        CellLayout cl = mWorkspaceScreens.get(emptyScreenId);
        mWorkspaceScreens.remove(emptyScreenId);
        int emptyIndex = mScreenOrder.indexOf(emptyScreenId);
        mScreenOrder.removeValue(emptyScreenId);

        int newScreenId = LauncherAppState.getInstance(getContext())
                .getModel().getModelDbController().getNewScreenId();
        // Launcher database isn't aware of empty pages that are already bound, so we need to
        // skip those IDs manually.
        while (mWorkspaceScreens.containsKey(newScreenId)) {
            newScreenId++;
        }

        mWorkspaceScreens.put(newScreenId, cl);
        if (emptyIndex >= 0) {
            mScreenOrder.add(emptyIndex, newScreenId);
        } else {
            addScreenToOrder(newScreenId);
        }
        ensureAppLibraryAtEnd();

        return newScreenId;
    }

    @Override
    public Hotseat getHotseat() {
        return mLauncher.getHotseat();
    }

    @Override
    public void onAddDropTarget(DropTarget target) {
        mDragController.addDropTarget(target);
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public int getCellLayoutId(CellLayout layout) {
        int index = mWorkspaceScreens.indexOfValue(layout);
        if (index != -1) {
            return mWorkspaceScreens.keyAt(index);
        }
        return -1;
    }

    public int getPageIndexForScreenId(int screenId) {
        return indexOfChild(mWorkspaceScreens.get(screenId));
    }

    @Override
    public int getCellLayoutIndex(CellLayout cellLayout) {
        return indexOfChild(mWorkspaceScreens.get(getCellLayoutId(cellLayout)));
    }

    @Override
    public int getPanelCount() {
        return isTwoPanelEnabled() ? 2 : super.getPanelCount();
    }

    public IntSet getCurrentPageScreenIds() {
        return IntSet.wrap(getScreenIdForPageIndex(getCurrentPage()));
    }

    public int getScreenIdForPageIndex(int index) {
        if (0 <= index && index < mScreenOrder.size()) {
            return mScreenOrder.get(index);
        }
        return -1;
    }

    public IntArray getScreenOrder() {
        return mScreenOrder;
    }

    public View getFirstPagePinnedItem() {
        return mFirstPagePinnedItem;
    }

    /**
     * Returns the screen ID of a page that is shown together with the given page screen ID when the
     * two panel UI is enabled.
     */
    public int getScreenPair(int screenId) {
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            return EXTRA_EMPTY_SCREEN_SECOND_ID;
        } else if (screenId == EXTRA_EMPTY_SCREEN_SECOND_ID) {
            return EXTRA_EMPTY_SCREEN_ID;
        } else if (screenId % 2 == 0) {
            return screenId + 1;
        } else {
            return screenId - 1;
        }
    }

    /**
     * Returns {@link CellLayout} that is shown together with the given {@link CellLayout} when the
     * two panel UI is enabled.
     */
    @Nullable
    public CellLayout getScreenPair(CellLayout cellLayout) {
        if (!isTwoPanelEnabled()) {
            return null;
        }
        int screenId = getCellLayoutId(cellLayout);
        if (screenId == -1) {
            return null;
        }
        return getScreenWithId(getScreenPair(screenId));
    }

    public void stripEmptyScreens() {
        DragTrace.log("Workspace.stripEmptyScreens screenCount=" + getPanelCount());
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading.
            // This is dangerous and can result in data loss.
            return;
        }

        if (isPageInTransition()) {
            mStripScreensOnPageStopMoving = true;
            return;
        }

        int currentPage = getNextPage();
        IntArray removeScreens = new IntArray();
        int total = mWorkspaceScreens.size();
        int maxId = SECOND_SCREEN_ID;
        if (MultiModeController.isSingleLayerMode() && total > 2) {
            maxId = FIRST_SCREEN_ID;
        }

        for (int i = 0; i < total; i++) {
            int id = mWorkspaceScreens.keyAt(i);
            CellLayout cl = mWorkspaceScreens.valueAt(i);
            // FIRST_SCREEN_ID and APP_LIBRARY_SCREEN_ID can never be removed.
            boolean isRemovable;
            if (MultiModeController.isSingleLayerMode()) {
                isRemovable = (id != FIRST_SCREEN_ID && id != APP_LIBRARY_SCREEN_ID
                        && id != EXTRA_EMPTY_SCREEN_ID && id != EXTRA_EMPTY_SCREEN_SECOND_ID);
            } else {
                isRemovable = (id != APP_LIBRARY_SCREEN_ID && id > maxId);
            }
            if (isRemovable && cl.getShortcutsAndWidgets().getChildCount() == 0) {
                removeScreens.add(id);
            }
        }

        // When two panel home is enabled we only remove an empty page if both visible pages are
        // empty.
        if (isTwoPanelEnabled()) {
            // We go through all the pages that were marked as removable and check their page pair
            Iterator<Integer> removeScreensIterator = removeScreens.iterator();
            while (removeScreensIterator.hasNext()) {
                int pageToRemove = removeScreensIterator.next();
                int pagePair = getScreenPair(pageToRemove);
                if (!removeScreens.contains(pagePair)) {
                    // The page pair isn't empty so we want to remove the current page from the
                    // removable pages' collection
                    removeScreensIterator.remove();
                }
            }
        }

        // We enforce at least one page (two pages on two panel home) to add new items to.
        // In the case that we remove the last such screen(s), we convert the last screen(s)
        // to the empty screen(s)
        int minScreens = getPanelCount();

        int pageShift = 0;
        for (int i = 0; i < removeScreens.size(); i++) {
            int id = removeScreens.get(i);
            CellLayout cl = mWorkspaceScreens.get(id);
            int clIndex = indexOfChild(cl);
            int emptyIndex = mScreenOrder.indexOf(id);
            mWorkspaceScreens.remove(id);
            mScreenOrder.removeValue(id);

            if (getChildCount() > minScreens) {
                // If this isn't the last page, just remove it
                if (clIndex <= currentPage && currentPage > 0) {
                    pageShift++;
                }
                removeView(cl);
            } else {
                // The last page(s) should be converted into extra empty page(s)
                int extraScreenId = isTwoPanelEnabled() && id % 2 == 1
                        // This is the right panel in a two panel scenario
                        ? EXTRA_EMPTY_SCREEN_SECOND_ID
                        // This is either the last screen in a one panel scenario, or the left panel
                        // in a two panel scenario when there are only two empty pages left
                        : EXTRA_EMPTY_SCREEN_ID;
                mWorkspaceScreens.put(extraScreenId, cl);
                if (emptyIndex >= 0) {
                    mScreenOrder.add(emptyIndex, extraScreenId);
                } else {
                    addScreenToOrder(extraScreenId);
                }
                ensureAppLibraryAtEnd();
            }
        }

        if (removeScreens.size() > 0) {
            int targetPage = Math.max(0, currentPage - pageShift);
            int maxValidPage = getPageCount() - 1;
            if (targetPage > maxValidPage) {
                targetPage = Math.max(0, maxValidPage);
            }
            setCurrentPage(targetPage);
        }
        // Update the page indicator to reflect the removed page.
        showPageIndicatorAtCurrentScroll();
    }

    /**
     * Needed here because launcher has a fullscreen exclusion rect and doesn't pilfer the pointers.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isTrackpadMultiFingerSwipe(ev)) {
            return false;
        }
        if (isAppLibraryCurrentPage()) {
            com.android.launcher3.applibrary.view.AppLibraryPage alp = getAppLibraryPage();
            if (alp != null && alp.isInSearchMode()) {
                return false;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Needed here because launcher has a fullscreen exclusion rect and doesn't pilfer the pointers.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isWobbling) {
            resetWobbleAlarm();
        }
        if (isTrackpadMultiFingerSwipe(ev)) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return shouldConsumeTouch(v);
    }

    private boolean shouldConsumeTouch(View v) {
        return !workspaceIconsCanBeDragged()
                || (!workspaceInModalState() && !isVisible(v));
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /**
     * This differs from isSwitchingState in that we take into account how far the transition
     * has completed.
     */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState
                || (mTransitionProgress > FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (workspaceInModalState() || !isFinishedSwitchingState()) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    protected void updateIsBeingDraggedOnTouchDown(MotionEvent ev) {
        super.updateIsBeingDraggedOnTouchDown(ev);

        mXDown = ev.getX();
        mYDown = ev.getY();
        if (mFirstPagePinnedItem != null) {
            final float[] tempFXY = new float[2];
            tempFXY[0] = mXDown;
            tempFXY[1] = mYDown;
            Utilities.mapCoordInSelfToDescendant(mFirstPagePinnedItem, this, tempFXY);
        }
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!isFinishedSwitchingState()  || (isWobbling() && mDragInfo != null)) return;

        float deltaX = ev.getX() - mXDown;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(ev.getY() - mYDown);

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            // Above MAX_SWIPE_ANGLE, we don't want to ever start scrolling the workspace
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            // Above START_DAMPING_TOUCH_SLOP_ANGLE and below MAX_SWIPE_ANGLE, we want to
            // increase the touch slop to make it harder to begin scrolling the workspace. This
            // results in vertically scrolling widgets to more easily. The higher the angle, the
            // more we increase touch slop.
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                    Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            // Below START_DAMPING_TOUCH_SLOP_ANGLE, we don't do anything special
            super.determineScrollingStart(ev);
        }
    }

    @Override
    protected void pageEndTransition() {
        // Do not allow premature transition termination while scroller is targeting a page
        if (mNextPage != INVALID_PAGE) {
            return;
        }
        super.pageEndTransition();
    }

    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        updateChildrenLayersEnabled();
        com.android.launcher3.applibrary.view.AppLibraryPage alp = getAppLibraryPage();
        if (alp != null && alp.isInSearchMode()) {
            alp.exitSearchMode(false);
        }
    }

    protected void onPageEndTransition() {
        super.onPageEndTransition();
        updateChildrenLayersEnabled();

        if (mDragController.isDragging()) {
            if (workspaceInModalState()) {
                // If we are in springloaded mode, then force an event to check if the current touch
                // is under a new page (to scroll to)
                mDragController.forceTouchMove();
            }
        }

        if (mStripScreensOnPageStopMoving) {
            stripEmptyScreens();
            mStripScreensOnPageStopMoving = false;
        }

        int targetPage = (mNextPage != INVALID_PAGE) ? mNextPage : getDestinationPage();
        boolean isAppLibrary = (mAppLibraryCellLayout != null
                && getChildAt(targetPage) == mAppLibraryCellLayout);

        if (MultiModeController.isSingleLayerMode()) {
            if (isAppLibrary) {
                if (isWobbling()) {
                    wobbleLayouts(false);
                }
                hideDockForAppLibrary();
                if (mAppLibraryPinnedItem instanceof com.android.launcher3.applibrary.view.AppLibraryPage) {
                    ((com.android.launcher3.applibrary.view.AppLibraryPage) mAppLibraryPinnedItem).refreshApps();
                }
            } else {
                restoreDockForHomeScreen();
            }
        }

        if (!isAppLibrary) {
            com.android.launcher3.applibrary.view.AppLibraryPage alp = getAppLibraryPage();
            if (alp != null && alp.isInSearchMode()) {
                alp.exitSearchMode(false);
            }
        }

        invalidateGlassSurfaces();

        // Inform the Launcher activity that the page transition ended so that it can react to the
        // newly visible page if it wants to.
        mLauncher.onPageEndTransition();
    }

    void hideDockForAppLibrary() {
        Hotseat hotseat = getHotseat();
        View pageIndicator = getPageIndicator();
        int piTop = pageIndicator != null ? pageIndicator.getTop() : 0;
        int piHeight = pageIndicator != null ? pageIndicator.getHeight() : 0;
        int dlHeight = mLauncher.getDragLayer() != null ? mLauncher.getDragLayer().getHeight() : 0;
        int slideDistance = dlHeight - piTop;
        if (slideDistance <= 0) {
            slideDistance = (hotseat != null ? hotseat.getHeight() : 0) + piHeight + 200;
        }
        if (hotseat != null) {
            hotseat.setForcedTranslationY(slideDistance);
            hotseat.setAlpha(0f);
        }
        if (pageIndicator instanceof PageIndicatorDots) {
            ((PageIndicatorDots) pageIndicator).setForcedTranslationY(slideDistance);
            pageIndicator.setAlpha(0f);
        }
        View dockGlass = mLauncher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            dockGlass.setTranslationY(slideDistance);
            dockGlass.setAlpha(0f);
        }
        if (mLauncher.mBlurLayer != null) {
            mLauncher.mBlurLayer.setAlpha(1f);
        }
    }

    void restoreDockForHomeScreen() {
        Hotseat hotseat = getHotseat();
        if (hotseat != null) {
            hotseat.setForcedTranslationY(0f);
            if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                hotseat.setAlpha(1f);
                hotseat.setIconsAlpha(1f);
                hotseat.setVisibility(VISIBLE);
            }
        }
        View pageIndicator = getPageIndicator();
        if (pageIndicator instanceof PageIndicatorDots) {
            ((PageIndicatorDots) pageIndicator).setForcedTranslationY(0f);
            if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                pageIndicator.setVisibility(VISIBLE);
                pageIndicator.setAlpha(1f);
            }
        }
        View dockGlass = mLauncher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            dockGlass.setTranslationY(0f);
            if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                dockGlass.setVisibility(VISIBLE);
                dockGlass.setAlpha(1f);
            }
        }
        if (mLauncher.mBlurLayer != null && com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
            mLauncher.mBlurLayer.setAlpha(0f);
        }
        if (mLauncher.getSystemUiController() != null) {
            mLauncher.getSystemUiController().updateUiState(
                    SystemUiController.UI_STATE_ALL_APPS, 0);
        }
    }

    public void setLauncherOverlay(LauncherOverlayTouchProxy overlay) {
        final EdgeEffectCompat newEffect;
        if (overlay == null) {
            newEffect = new EdgeEffectCompat(getContext());
            mOverlayEdgeEffect = null;
        } else {
            newEffect = mOverlayEdgeEffect = new OverlayEdgeEffect(getContext(), overlay);
            overlay.setOverlayCallbacks(this);
        }

        if (mIsRtl) {
            mEdgeGlowRight = newEffect;
        } else {
            mEdgeGlowLeft = newEffect;
        }
        onOverlayScrollChanged(0);
    }

    public boolean hasOverlay() {
        return mOverlayEdgeEffect != null;
    }

    @Override
    protected void snapToDestination() {
        if (mOverlayEdgeEffect != null && !mOverlayEdgeEffect.isFinished()) {
            snapToPageImmediately(0);
        } else {
            super.snapToDestination();
        }
    }

    /**
     * Invalidates all active Liquid Glass surfaces across visible pages and the dock so that
     * refraction updates live in real-time during user drags, scrolling, and page flings.
     */
    public void invalidateGlassSurfaces() {
        try {
            if (mCachedDockGlass == null) {
                mCachedDockGlass = mLauncher.findViewById(R.id.dock_glass);
            }
            if (mCachedDockGlass != null && mCachedDockGlass.getVisibility() == VISIBLE) {
                mCachedDockGlass.invalidate();
            }

            final int[] visibleScreens = getVisibleChildrenRange();
            if (visibleScreens != null && visibleScreens.length >= 2 && visibleScreens[0] >= 0) {
                int leftScreen = visibleScreens[0];
                int rightScreen = visibleScreens[1];
                int pageCount = getChildCount();
                for (int p = leftScreen; p <= rightScreen; p++) {
                    if (p < 0 || p >= pageCount) continue;
                    View page = getChildAt(p);
                    if (page instanceof CellLayout) {
                        ShortcutAndWidgetContainer container = ((CellLayout) page).getShortcutsAndWidgets();
                        if (container != null) {
                            for (int i = 0; i < container.getChildCount(); i++) {
                                View child = container.getChildAt(i);
                                if (child instanceof FolderIcon) {
                                    FolderGlassView gv = ((FolderIcon) child).getGlassView();
                                    if (gv != null) {
                                        gv.invalidate();
                                    }
                                } else if (child instanceof foundation.e.bliss.widgets.clock.ClockWidgetHostView) {
                                    FolderGlassView gv = ((foundation.e.bliss.widgets.clock.ClockWidgetHostView) child).getGlassView();
                                    if (gv != null) {
                                        gv.invalidate();
                                    }
                                }
                            }
                        }
                    }
                }

                int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
                if (appLibIndex >= 0 && appLibIndex >= leftScreen && appLibIndex <= rightScreen
                        && mAppLibraryPinnedItem instanceof com.android.launcher3.applibrary.view.AppLibraryPage) {
                    ((com.android.launcher3.applibrary.view.AppLibraryPage) mAppLibraryPinnedItem).invalidateGlassSurfaces();
                }
            }

            Hotseat hotseat = getHotseat();
            if (hotseat != null) {
                ShortcutAndWidgetContainer hotseatContainer = hotseat.getShortcutsAndWidgets();
                if (hotseatContainer != null) {
                    for (int i = 0; i < hotseatContainer.getChildCount(); i++) {
                        View child = hotseatContainer.getChildAt(i);
                        if (child instanceof FolderIcon) {
                            FolderGlassView gv = ((FolderIcon) child).getGlassView();
                            if (gv != null) {
                                gv.invalidate();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void invalidateScrollingPageGlassSurfaces() {
        try {
            final int[] visibleScreens = getVisibleChildrenRange();
            if (visibleScreens != null && visibleScreens.length >= 2 && visibleScreens[0] >= 0) {
                int leftScreen = visibleScreens[0];
                int rightScreen = visibleScreens[1];
                int pageCount = getChildCount();
                for (int p = leftScreen; p <= rightScreen; p++) {
                    if (p < 0 || p >= pageCount) continue;
                    View page = getChildAt(p);
                    if (page instanceof CellLayout) {
                        ShortcutAndWidgetContainer container = ((CellLayout) page).getShortcutsAndWidgets();
                        if (container != null) {
                            for (int i = 0; i < container.getChildCount(); i++) {
                                View child = container.getChildAt(i);
                                if (child instanceof FolderIcon) {
                                    FolderGlassView gv = ((FolderIcon) child).getGlassView();
                                    if (gv != null) {
                                        gv.invalidate();
                                    }
                                } else if (child instanceof foundation.e.bliss.widgets.clock.ClockWidgetHostView) {
                                    FolderGlassView gv = ((foundation.e.bliss.widgets.clock.ClockWidgetHostView) child).getGlassView();
                                    if (gv != null) {
                                        gv.invalidate();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isNearAppLibrary() {
        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (appLibIndex < 0) return false;
        int scrollForAppLib = getScrollForPage(appLibIndex);
        int pageWidth = getWidth();
        return pageWidth > 0 && Math.abs(getScrollX() - scrollForAppLib) < (pageWidth * 1.5f);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (l != oldl) {
            if (mWallpaperOffset != null) {
                float offset = MultiModeController.isSingleLayerMode() ? 0.5f : mWallpaperOffset.wallpaperOffsetForScroll(l);
                WallpaperEnvironmentBuilder envBuilder = WallpaperEnvironmentBuilder.getInstanceNoCreate();
                if (envBuilder != null) {
                    envBuilder.setParallaxOffset(offset);
                }
            }
            if (!MultiModeController.isSingleLayerMode() || isNearAppLibrary()) {
                invalidateGlassSurfaces();
            } else {
                invalidateScrollingPageGlassSurfaces();
            }
        }

        // Update the page indicator progress.
        // Unlike from other states, we show the page indicator when transitioning from HINT_STATE.
        boolean isSwitchingState = mIsSwitchingState
                && mLauncher.getStateManager().getCurrentStableState() != HINT_STATE;
        boolean isTransitioning = isSwitchingState
                || (getLayoutTransition() != null && getLayoutTransition().isRunning());
        if (!isTransitioning) {
            showPageIndicatorAtCurrentScroll();
        }

        updatePageAlphaValues();
        updatePageScrollValues();
        enableHwLayersOnVisiblePages();

        if (mIsPageInTransition && MultiModeController.isSingleLayerMode()) {
            mLauncher.hideWidgetResizeContainer();
            // Only run the hotseat slide animation when the WidgetContainer is actually
            // pinned to FIRST_SCREEN_ID. Without this guard, swiping near page 0 causes
            // a transient dock dip/flicker even with no widget pinned there.
            if (mFirstPagePinnedItem != null) {
                firstPageItemHideHotseat();
            }
            if (mAppLibraryPinnedItem != null) {
                lastPageItemHideHotseat();
            }
        }
    }

    /**
     * Slides the hotseat, page indicator, and dock glass off-screen as the user scrolls toward
     * FIRST_SCREEN_ID. This only applies when a WidgetContainer is pinned there (the original
     * -1 screen feature) — ordinary home screen pages should leave the dock alone.
     * Call site is guarded on {@code mFirstPagePinnedItem != null}.
     */
    private void firstPageItemHideHotseat() {
        final int index = mScreenOrder.indexOf(FIRST_SCREEN_ID);
        if (index < 0 || index + 1 >= getChildCount() || index + 1 >= mScreenOrder.size()) {
            return;
        }

        int page0Scroll = getScrollForPage(index);
        int page1Scroll = getScrollForPage(index + 1);
        int minFirstScroll = Math.min(page0Scroll, page1Scroll);
        int maxFirstScroll = Math.max(page0Scroll, page1Scroll);
        int currentScroll = getScrollX();

        if (currentScroll > maxFirstScroll) {
            return;
        }

        final int scrollDelta = currentScroll - page0Scroll -
                getLayoutTransitionOffsetForPage(index);
        float scrollRange = page1Scroll - page0Scroll;

        if (scrollRange == 0)
            return;

        final float progress = (scrollRange - scrollDelta) / scrollRange;

        View pi = getPageIndicator();
        if (progress <= 0) {
            if (getHotseat() != null) getHotseat().setForcedTranslationY(0);
            if (pi instanceof PageIndicatorDots) {
                ((PageIndicatorDots) pi).setForcedTranslationY(0);
            }
            View dockGlass = mLauncher.findViewById(R.id.dock_glass);
            if (dockGlass != null) dockGlass.setTranslationY(0);
            if (mLauncher.mBlurLayer != null && com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) mLauncher.mBlurLayer.setAlpha(0);
            return;
        }

        int piTop = pi != null ? pi.getTop() : 0;
        int piHeight = pi != null ? pi.getHeight() : 0;
        int dragLayerHeight = mLauncher.getDragLayer() != null ? mLauncher.getDragLayer().getHeight() : 0;
        int slideDistance = dragLayerHeight - piTop;
        if (slideDistance <= 0) {
            slideDistance = (getHotseat() != null ? getHotseat().getHeight() : 0) + piHeight + 200;
        }
        float dockTranslationY = progress * slideDistance;

        if (getHotseat() != null) getHotseat().setForcedTranslationY(dockTranslationY);
        if (pi instanceof PageIndicatorDots) {
            ((PageIndicatorDots) pi).setForcedTranslationY(dockTranslationY);
        }

        View dockGlass = mLauncher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            dockGlass.setTranslationY(dockTranslationY);
        }

        if (mLauncher.mBlurLayer != null) mLauncher.mBlurLayer.setAlpha(progress);
    }

    /**
     * Slides the hotseat, page indicator, and dock glass off-screen as the user scrolls toward
     * APP_LIBRARY_SCREEN_ID.
     * Call site is guarded on {@code mAppLibraryPinnedItem != null}.
     */
    private void lastPageItemHideHotseat() {
        int index = (mAppLibraryCellLayout != null)
                ? indexOfChild(mAppLibraryCellLayout)
                : mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (index <= 0 || index >= getChildCount()) return;
        
        int prevPageScroll = getScrollForPage(index - 1);
        int appLibScroll = getScrollForPage(index);
        int minLastScroll = Math.min(prevPageScroll, appLibScroll);
        int currentScroll = getScrollX();

        if (currentScroll < minLastScroll) {
            // Out of range for App Library transition.
            // If first page pinned item is present and scroll is within its range, do NOT reset dock/blur!
            int firstIndex = mScreenOrder.indexOf(FIRST_SCREEN_ID);
            if (mFirstPagePinnedItem != null && firstIndex >= 0 && firstIndex + 1 < getChildCount()) {
                int maxFirstScroll = Math.max(getScrollForPage(firstIndex), getScrollForPage(firstIndex + 1));
                if (currentScroll <= maxFirstScroll) {
                    return;
                }
            }
        }

        final int scrollDelta = currentScroll - appLibScroll -
                getLayoutTransitionOffsetForPage(index);
        float scrollRange = prevPageScroll - appLibScroll;

        if (scrollRange == 0)
            return;

        final float progress = Math.max(0f, Math.min(1f, (scrollRange - scrollDelta) / scrollRange));
        View pi = getPageIndicator();

        if (progress <= 0) {
            if (getHotseat() != null) {
                getHotseat().setForcedTranslationY(0);
                if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                    getHotseat().setVisibility(VISIBLE);
                    getHotseat().setAlpha(1f);
                }
            }
            if (pi instanceof PageIndicatorDots) {
                ((PageIndicatorDots) pi).setForcedTranslationY(0);
                if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                    pi.setVisibility(VISIBLE);
                    pi.setAlpha(1f);
                }
            }
            View dockGlass = mLauncher.findViewById(R.id.dock_glass);
            if (dockGlass != null) {
                dockGlass.setTranslationY(0);
                if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
                    dockGlass.setVisibility(VISIBLE);
                    dockGlass.setAlpha(1f);
                }
            }
            if (mLauncher.mBlurLayer != null && com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) mLauncher.mBlurLayer.setAlpha(0);
            return;
        }

        int piTop = pi != null ? pi.getTop() : 0;
        int piHeight = pi != null ? pi.getHeight() : 0;
        int dragLayerHeight = mLauncher.getDragLayer() != null ? mLauncher.getDragLayer().getHeight() : 0;
        int slideDistance = dragLayerHeight - piTop;
        if (slideDistance <= 0) {
            slideDistance = (getHotseat() != null ? getHotseat().getHeight() : 0) + piHeight + 200;
        }
        float dockTranslationY = progress * slideDistance;

        boolean folderOpen = com.android.launcher3.folder.Folder.getOpen(mLauncher) != null;
        float dockAlpha = folderOpen ? 0f : (progress >= 1f ? 0f : 1f);

        if (getHotseat() != null) {
            getHotseat().setForcedTranslationY(dockTranslationY);
            getHotseat().setAlpha(dockAlpha);
        }
        if (pi instanceof PageIndicatorDots) {
            ((PageIndicatorDots) pi).setForcedTranslationY(dockTranslationY);
            pi.setAlpha(dockAlpha);
        }

        View dockGlass = mLauncher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            dockGlass.setTranslationY(dockTranslationY);
            dockGlass.setAlpha(dockAlpha);
        }

        if (mLauncher.mBlurLayer != null) mLauncher.mBlurLayer.setAlpha(progress);
    }

    public void showPageIndicatorAtCurrentScroll() {
        if (mPageIndicator != null) {
            mPageIndicator.setScroll(getScrollX(), computeMaxScroll());
        }
        if (mLauncher.mBlurLayer != null && !isAppLibraryCurrentPage()
                && (getPageIndexForScreenId(APP_LIBRARY_SCREEN_ID) != mCurrentPage)
                && com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
            mLauncher.mBlurLayer.setAlpha(0f);
        }
    }

    @Override
    protected boolean shouldFlingForVelocity(int velocityX) {
        // When the overlay is moving, the fling or settle transition is controlled by the overlay.
        return Float.compare(Math.abs(mOverlayProgress), 0) == 0
                && super.shouldFlingForVelocity(velocityX);
    }

    /**
     * The overlay scroll is being controlled locally, just update our overlay effect
     */
    @Override
    public void onOverlayScrollChanged(float scroll) {
        mOverlayProgress = Utilities.boundToRange(scroll, 0, 1);
        if (Float.compare(mOverlayProgress, 1f) == 0) {
            if (!mOverlayShown) {
                mOverlayShown = true;
                mLauncher.onOverlayVisibilityChanged(true);
            }
        } else if (Float.compare(mOverlayProgress, 0f) == 0) {
            if (mOverlayShown) {
                mOverlayShown = false;
                mLauncher.onOverlayVisibilityChanged(false);
            }
        }
        int count = mOverlayCallbacks.size();
        for (int i = 0; i < count; i++) {
            mOverlayCallbacks.get(i).onOverlayScrollChanged(mOverlayProgress);
        }
    }

    /**
     * Adds a callback for receiving overlay progress
     */
    public void addOverlayCallback(LauncherOverlayCallbacks callback) {
        mOverlayCallbacks.add(callback);
        callback.onOverlayScrollChanged(mOverlayProgress);
    }

    /**
     * Removes a previously added overlay progress callback
     */
    public void removeOverlayCallback(LauncherOverlayCallbacks callback) {
        mOverlayCallbacks.remove(callback);
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (prevPage != mCurrentPage) {
            StatsLogManager.EventEnum event = (prevPage < mCurrentPage)
                    ? LAUNCHER_SWIPERIGHT : LAUNCHER_SWIPELEFT;
            mLauncher.getStatsLogManager().logger()
                    .withSrcState(LAUNCHER_STATE_HOME)
                    .withDstState(LAUNCHER_STATE_HOME)
                    .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                            .setWorkspace(
                                    LauncherAtom.WorkspaceContainer.newBuilder()
                                            .setPageIndex(prevPage)).build())
                    .log(event);

            boolean goingToAppLibrary = (mAppLibraryCellLayout != null
                    && getChildAt(mCurrentPage) == mAppLibraryCellLayout);

            if (MultiModeController.isSingleLayerMode()) {
                if (goingToAppLibrary) {
                    hideDockForAppLibrary();
                } else {
                    // Returning to or switching between home screen pages — ensure dock,
                    // page indicator, and status bar are fully restored.
                    restoreDockForHomeScreen();

                    if (prevPage == 0 && mCurrentPage == 1) {
                        if (mFirstPagePinnedItem != null) {
                            mFirstPagePinnedItem.clearFocus();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setCurrentPage(int currentPage, int overridePrevPage) {
        // Only hide the dock/status-bar for page 0 when a pinned first-page widget is actually
        // present. Without this guard, returning from the App Library (or any other navigation
        // to page 0) unconditionally pushes the hotseat off-screen and hides the status bar
        // even when no widget is pinned there.
        int validPage = validateNewPage(currentPage);
        if (MultiModeController.isSingleLayerMode()) {
            int firstPageIndex = getPageIndexForScreenId(FIRST_SCREEN_ID);
            boolean isFirstPage = (mFirstPagePinnedItem != null && validPage == firstPageIndex);
            boolean isAppLibrary = (mAppLibraryCellLayout != null && validPage >= 0
                    && validPage < getChildCount() && getChildAt(validPage) == mAppLibraryCellLayout);
            
            if (isFirstPage || isAppLibrary) {
                hideDockForAppLibrary();
            } else {
                restoreDockForHomeScreen();
            }
            android.util.Log.d("BlurDebug", "page=" + currentPage + " validPage=" + validPage
                    + " isFirstPage=" + isFirstPage + " isAppLibrary=" + isAppLibrary
                    + " blurAlpha=" + (mLauncher.mBlurLayer != null ? mLauncher.mBlurLayer.getAlpha() : -1));
        }
        
        if (mAppLibraryCellLayout != null && validPage >= 0 && validPage < getChildCount()
                && getChildAt(validPage) == mAppLibraryCellLayout) {
            if (mAppLibraryPinnedItem instanceof com.android.launcher3.applibrary.view.AppLibraryPage) {
                ((com.android.launcher3.applibrary.view.AppLibraryPage) mAppLibraryPinnedItem).refreshApps();
            }
        }

        super.setCurrentPage(currentPage, overridePrevPage);
    }

    protected void setWallpaperDimension() {
        final DeviceProfile dp = mLauncher.getDeviceProfile();
        Executors.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Point size;
                if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                    if (dp != null) {
                        size = new Point(dp.widthPx, dp.heightPx);
                    } else {
                        size = DisplayController.INSTANCE.get(getContext()).getInfo().currentSize;
                    }
                } else {
                    size = LauncherAppState.getIDP(getContext()).defaultWallpaperSize;
                }
                if (size.x != mWallpaperManager.getDesiredMinimumWidth()
                        || size.y != mWallpaperManager.getDesiredMinimumHeight()) {
                    mWallpaperManager.suggestDesiredDimensions(size.x, size.y);
                }
            }
        });
    }

    public void lockWallpaperToDefaultPage() {
        mWallpaperOffset.setLockToDefaultPage(true);
    }

    public void unlockWallpaperFromDefaultPageOnNextLayout() {
        if (mWallpaperOffset.isLockedToDefaultPage()) {
            mUnlockWallpaperFromDefaultPageOnLayout = true;
            requestLayout();
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        mWallpaperOffset.syncWithScroll();
    }

    @Override
    public void announceForAccessibility(CharSequence text) {
        // Don't announce if apps is on top of us.
        if (!mLauncher.isInState(ALL_APPS)) {
            super.announceForAccessibility(text);
        }
    }

    private void updatePageAlphaValues() {
        // We need to check the isDragging case because updatePageAlphaValues is called between
        // goToState(SPRING_LOADED) and onStartStateTransition.
        if (!workspaceInModalState() && !mIsSwitchingState && !mDragController.isDragging()) {
            int screenCenter = getScrollX() + getMeasuredWidth() / 2;
            for (int i = 0; i < getChildCount(); i++) {
                CellLayout child = (CellLayout) getChildAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    if (mWorkspaceFadeInAdjacentScreens) {
                        child.getShortcutsAndWidgets().setAlpha(alpha);
                    } else {
                        // Pages that are off-screen aren't important for accessibility.
                        child.getShortcutsAndWidgets().setImportantForAccessibility(
                                alpha > 0 ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    }
                }
            }
        }
    }

    private void updatePageScrollValues() {
        int screenCenter = getScrollX() + getMeasuredWidth() / 2;
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout child = (CellLayout) getChildAt(i);
            if (child != null) {
                float scrollProgress = getScrollProgress(screenCenter, child, i);
                child.setScrollProgress(scrollProgress);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWallpaperOffset.setWindowToken(getWindowToken());
        computeScroll();
        if (LauncherAppMonitor.getInstanceNoCreate() != null) {
            LauncherAppMonitor.getInstanceNoCreate().registerCallback(mLauncherAppMonitorCallback);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperOffset.setWindowToken(null);
        if (LauncherAppMonitor.getInstanceNoCreate() != null) {
            LauncherAppMonitor.getInstanceNoCreate().unregisterCallback(mLauncherAppMonitorCallback);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mUnlockWallpaperFromDefaultPageOnLayout) {
            mWallpaperOffset.setLockToDefaultPage(false);
            mUnlockWallpaperFromDefaultPageOnLayout = false;
        }
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mWallpaperOffset.syncWithScroll();
            mWallpaperOffset.jumpToFinal();
        }
        super.onLayout(changed, left, top, right, bottom);
        updatePageAlphaValues();
        
        // PagedView vertically centers pages that are taller/shorter than the available space.
        // Because we forced App Library to be taller, it gets pushed up off the screen!
        // We fix this by manually snapping it back down to fill from paddingTop to the bottom edge.
        if (mAppLibraryCellLayout != null) {
            int appLeft = mAppLibraryCellLayout.getLeft();
            int appRight = mAppLibraryCellLayout.getRight();
            int appTop = 0;
            int appBottom = getHeight();
            mAppLibraryCellLayout.layout(appLeft, appTop, appRight, appBottom);
        }
    }

    @Override
    public int getDescendantFocusability() {
        if (workspaceInModalState()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    private boolean workspaceInModalState() {
        return !mLauncher.isInState(NORMAL);
    }

    private boolean workspaceInScrollableState() {
        return mLauncher.isInState(SPRING_LOADED) || mLauncher.isInState(EDIT_MODE)
                || !workspaceInModalState();
    }

    /**
     * Returns whether a drag should be allowed to be started from the current workspace state.
     */
    public boolean workspaceIconsCanBeDragged() {
        return mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED);
    }

    private void updateChildrenLayersEnabled() {
        boolean enableChildrenLayers = mIsSwitchingState || isPageInTransition();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            if (mChildrenLayersEnabled) {
                enableHwLayersOnVisiblePages();
            } else {
                for (int i = 0; i < getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) getChildAt(i);
                    if (cl != null) {
                        cl.enableHardwareLayer(false);
                    }
                }
            }
        }
    }

    private void enableHwLayersOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            final int screenCount = getChildCount();

            final int[] visibleScreens = getVisibleChildrenRange();
            int leftScreen = visibleScreens[0];
            int rightScreen = visibleScreens[1];
            if (mForceDrawAdjacentPages) {
                // In overview mode, make sure that the two side pages are visible.
                leftScreen = Utilities.boundToRange(getCurrentPage() - 1, 0, rightScreen);
                rightScreen = Utilities.boundToRange(getCurrentPage() + 1,
                        leftScreen, getPageCount() - 1);
            }

            if (leftScreen == rightScreen) {
                // make sure we're caching at least two pages always
                if (rightScreen < screenCount - 1) {
                    rightScreen++;
                } else if (leftScreen > 0) {
                    leftScreen--;
                }
            }

            for (int i = 0; i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getPageAt(i);
                if (layout != null) {
                    // enable layers between left and right screen inclusive.
                    boolean enableLayer = leftScreen <= i && i <= rightScreen;
                    layout.enableHardwareLayer(enableLayer);
                }
            }
        }
    }

    public void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempXY;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    private void onStartStateTransition() {
        mIsSwitchingState = true;
        mTransitionProgress = 0;

        updateChildrenLayersEnabled();
    }

    private void onEndStateTransition() {
        mIsSwitchingState = false;
        mForceDrawAdjacentPages = false;
        mTransitionProgress = 1;

        updateChildrenLayersEnabled();
        updateAccessibilityFlags();
    }

    /**
     * Sets the current workspace {@link LauncherState} and updates the UI without any animations
     */
    @Override
    public void setState(LauncherState toState) {
        onStartStateTransition();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setState(toState);
        onEndStateTransition();
    }

    /**
     * Sets the current workspace {@link LauncherState}, then animates the UI
     */
    @Override
    public void setStateWithAnimation(
            LauncherState toState, StateAnimationConfig config, PendingAnimation animation) {
        StateTransitionListener listener = new StateTransitionListener();
        mLauncher.getStateManager().getState().onLeavingState(mLauncher, toState);
        mStateTransitionAnimation.setStateWithAnimation(toState, config, animation);

        // Invalidate the pages now, so that we have the visible pages before the
        // animation is started
        if (toState.hasFlag(FLAG_MULTI_PAGE)) {
            mForceDrawAdjacentPages = true;
        }
        invalidate(); // This will call dispatchDraw(), which calls getVisiblePages().

        ValueAnimator stepAnimator = ValueAnimator.ofFloat(0, 1);
        stepAnimator.addUpdateListener(listener);
        stepAnimator.addListener(listener);
        animation.add(stepAnimator);
    }

    public WorkspaceStateTransitionAnimation getStateTransitionAnimation() {
        return mStateTransitionAnimation;
    }

    public void updateAccessibilityFlags() {
        // TODO: Update the accessibility flags appropriately when dragging.
        int accessibilityFlag =
                mLauncher.getStateManager().getState().hasFlag(FLAG_WORKSPACE_INACCESSIBLE)
                        ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        if (!mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            int total = getPageCount();
            for (int i = 0; i < total; i++) {
                View pageView = getPageAt(i);
                if (pageView instanceof CellLayout) {
                    updateAccessibilityFlags(accessibilityFlag, (CellLayout) pageView);
                }
            }
            setImportantForAccessibility(accessibilityFlag);
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            // TAPL tests verify that workspace is not present in Overview and AllApps states.
            // TAPL can work only if UIDevice is set up as setCompressedLayoutHeirarchy(false).
            // Hiding workspace from the tests when it's
            // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS.
            return AccessibilityNodeInfo.obtain();
        }
        return super.createAccessibilityNodeInfo();
    }

    private void updateAccessibilityFlags(int accessibilityFlag, CellLayout page) {
        page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        page.getShortcutsAndWidgets().setImportantForAccessibility(accessibilityFlag);
        page.setContentDescription(null);
        page.setAccessibilityDelegate(null);
    }

    public void startDrag(CellInfo cellInfo, DragOptions options) {
        View child = cellInfo.cell;

        if (MultiModeController.isSingleLayerMode()) {
            child.setOnTouchListener(null);
        }

        // The single layer mode wobbleLayouts call was moved to PopupContainerWithArrow 
        // to match iOS Quick Action state machine.

        if (wobbleExpireAlarm.alarmPending()) {
            wobbleExpireAlarm.cancelAlarm();
        }

        mDragInfo = cellInfo;
        child.clearAnimation();
        if (!(child instanceof LauncherAppWidgetHostView && MultiModeController.isSingleLayerMode())) {
            child.setVisibility(INVISIBLE);
        }

        if (options.isAccessibleDrag) {
            mDragController.addDragListener(
                    new AccessibleDragListenerAdapter(this, WorkspaceAccessibilityHelper::new) {
                        @Override
                        protected void enableAccessibleDrag(boolean enable,
                                @Nullable DragObject dragObject) {
                            super.enableAccessibleDrag(enable, dragObject);
                            setEnableForLayout(mLauncher.getHotseat(), enable);
                            if (enable && dragObject != null
                                    && dragObject.dragInfo instanceof LauncherAppWidgetInfo) {
                                mLauncher.getHotseat().setImportantForAccessibility(
                                        IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                            }
                        }
                    });
        }

        beginDragShared(child, this, options);
    }

    public void beginDragShared(View child, DragSource source, DragOptions options) {
        android.util.Log.d("DragDebug", "beginDragShared called for: " + child.getTag());
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, null, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }

    /**
     * Core functionality for beginning a drag operation for an item that will be dropped within
     * the workspace
     */
    public DragView beginDragShared(View child, DraggableView draggableView, DragSource source,
            ItemInfo dragObject, DragPreviewProvider previewProvider, DragOptions dragOptions) {

        float iconScale = 1f;
        if (child instanceof BubbleTextView) {
            Drawable icon = ((BubbleTextView) child).getIcon();
            if (icon instanceof FastBitmapDrawable) {
                iconScale = ((FastBitmapDrawable) icon).getAnimatedScale();
            }
        }

        // Clear the pressed state if necessary
        child.clearFocus();
        child.setPressed(false);
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (draggableView == null && child instanceof DraggableView) {
            draggableView = (DraggableView) child;
        }

        final View contentView = previewProvider.getContentView();
        final float scale;
        // The draggable drawable follows the touch point around on the screen
        final Drawable drawable;
        if (contentView == null) {
            drawable = previewProvider.createDrawable();
            scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        } else {
            drawable = null;
            scale = previewProvider.getScaleAndPosition(contentView, mTempXY);
        }

        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();

        if (draggableView != null) {
            draggableView.getSourceVisualDragBounds(dragRect);
            dragLayerY += dragRect.top;
        }


        if (child.getParent() instanceof ShortcutAndWidgetContainer) {
            mDragSourceInternal = (ShortcutAndWidgetContainer) child.getParent();
        }

        if (child instanceof BubbleTextView) {
            BubbleTextView btv = (BubbleTextView) child;
            if (!dragOptions.isAccessibleDrag) {
                dragOptions.preDragCondition = btv.startLongPressAction();
            }
            if (btv.isDisplaySearchResult()) {
                dragOptions.preDragEndScale = (float) mAllAppsIconSize / btv.getIconSize();
            }
        } else if (child instanceof com.android.launcher3.folder.FolderIcon) {
            com.android.launcher3.folder.FolderIcon fi = (com.android.launcher3.folder.FolderIcon) child;
            if (!dragOptions.isAccessibleDrag) {
                dragOptions.preDragCondition = fi.startLongPressAction();
            }
        } else if (child instanceof LauncherAppWidgetHostView) {
            LauncherAppWidgetHostView widgetView = (LauncherAppWidgetHostView) child;
            if (!dragOptions.isAccessibleDrag) {
                dragOptions.preDragCondition = widgetView.startLongPressAction();
            }
        }

        if (dragOptions.preDragCondition == null && MultiModeController.isSingleLayerMode()) {
            final int threshold = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold);
            dragOptions.preDragCondition = new DragOptions.PreDragCondition() {
                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return distanceDragged > threshold;
                }
                @Override
                public void onPreDragStart(DropTarget.DragObject dragObject) {}
                @Override
                public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                    if (!dragStarted) {
                        if (dragObject != null && dragObject.dragView != null
                                && dragObject.dragView.getContentViewParent() != null) {
                            dragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                        }
                        child.setVisibility(VISIBLE);
                    } else if (child instanceof LauncherAppWidgetHostView) {
                        child.setVisibility(VISIBLE);
                    }
                }
            };
        }

        if (dragOptions.preDragCondition != null) {
            int xDragOffSet = dragOptions.preDragCondition.getDragOffset().x;
            int yDragOffSet = dragOptions.preDragCondition.getDragOffset().y;
            if (xDragOffSet != 0 || yDragOffSet != 0) {
                dragLayerX += xDragOffSet;
                dragLayerY += yDragOffSet;
            }
        }

        final DragView dv;
        if (contentView instanceof View) {
            if (contentView instanceof LauncherAppWidgetHostView) {
                mDragController.addDragListener(
                        new AppWidgetHostViewDragListener(mLauncher, (LauncherAppWidgetHostView) contentView));
            }
            dv = mDragController.startDrag(
                    contentView,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    dragOptions);
        } else {
            dv = mDragController.startDrag(
                    drawable,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    dragOptions);
        }
        return dv;
    }

    private boolean transitionStateShouldAllowDrop() {
        // If we have an active workspace drag, always allow drop events to be processed.
        // The drag was already started from workspace so blocking it mid-drag causes the icon
        // to not land where the user dropped it.
        if (mDragInfo != null) {
            return true;
        }
        boolean switching = isSwitchingState();
        boolean canDrag = workspaceIconsCanBeDragged();
        boolean progress = mTransitionProgress > ALLOW_DROP_TRANSITION_PROGRESS;
        boolean result = (!switching || progress) && canDrag;
        if (!result) {
            android.util.Log.d("DropDebug", "transitionStateShouldAllowDrop=false: isSwitchingState=" + switching
                    + " transitionProgress=" + mTransitionProgress
                    + " workspaceIconsCanBeDragged=" + canDrag
                    + " currentState=" + mLauncher.getStateManager().getState());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptDrop(DragObject d) {
        isDragWidget(d);
        // If it's an external drop (e.g. from All Apps), check if it should be accepted
        CellLayout dropTargetLayout = mDropToLayout;
        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a valid drop target at time of drop
            if (dropTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);

            int spanX;
            int spanY;
            if (mDragInfo != null) {
                final CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                spanX = d.dragInfo.spanX;
                spanY = d.dragInfo.spanY;
            }

            int minSpanX = spanX;
            int minSpanY = spanY;
            if (d.dragInfo instanceof PendingAddWidgetInfo) {
                minSpanX = ((PendingAddWidgetInfo) d.dragInfo).minSpanX;
                minSpanY = ((PendingAddWidgetInfo) d.dragInfo).minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
            View targetView = dropTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
            boolean withinFolderRadius = distance <= dropTargetLayout.getFolderCreationRadius(mTargetCell);
            boolean canCreateFolder = (mCreateUserFolderOnDrop || mDragMode == DRAG_MODE_CREATE_FOLDER)
                    && withinFolderRadius
                    && willCreateUserFolder(d.dragInfo, targetView, false);
            boolean canAddToFolder = (mAddToExistingFolderOnDrop || mDragMode == DRAG_MODE_ADD_TO_FOLDER)
                    && withinFolderRadius
                    && willAddToExistingUserFolder(d.dragInfo, targetView);
            if (targetView != null && (canCreateFolder || canAddToFolder)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                return false;
            }
        }

        int screenId = getCellLayoutId(dropTargetLayout);
        if (Workspace.EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
            commitExtraEmptyScreens();
        }

        return true;
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell,
                                 float distance, boolean considerTimeout) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willCreateUserFolder(info, dropOverView, considerTimeout);
    }

    boolean willCreateUserFolder(ItemInfo info, View dropOverView, boolean considerTimeout) {
        if (mDragTargetLayout != null && mDragTargetLayout.isItemPlacementDirty()) {
            return false;
        }
        if (mDragMode == DRAG_MODE_REORDER) {
            return false;
        }
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.getTmpCellX() != lp.getCellX() || lp.getTmpCellY() != lp.getCellY()) {
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            hasntMoved = dropOverView == mDragInfo.cell;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        boolean aboveShortcut = (dropOverView.getTag() instanceof WorkspaceItemInfo
                && ((WorkspaceItemInfo) dropOverView.getTag()).container
                != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION);
        boolean willBecomeShortcut =
                (info.itemType == ITEM_TYPE_APPLICATION ||
                        info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, CellLayout target, int[] targetCell,
                                        float distance) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        return willAddToExistingUserFolder(dragInfo, dropOverView);

    }

    boolean willAddToExistingUserFolder(ItemInfo dragInfo, View dropOverView) {
        if (dropOverView != null) {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.getTmpCellX() != lp.getCellX()
                    || lp.getTmpCellY() != lp.getCellY())) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    boolean createUserFolderIfNecessary(View newView, int container, CellLayout target,
            int[] targetCell, float distance, boolean external, DragObject d) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;

        View v = target.getChildAt(targetCell[0], targetCell[1]);
        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved) return false;

        boolean aboveShortcut = (v.getTag() instanceof WorkspaceItemInfo);
        boolean willBecomeShortcut = (newView.getTag() instanceof WorkspaceItemInfo);
        if (!aboveShortcut || !willBecomeShortcut) return false;

        boolean allowCreateFolder = mCreateUserFolderOnDrop || mDragMode == DRAG_MODE_CREATE_FOLDER;
        if (!allowCreateFolder) return false;

        DragTrace.log("Workspace.createUserFolder confirmed targetCell=(" + targetCell[0] + "," + targetCell[1] + ")");
        mCreateUserFolderOnDrop = false;

        // Immediately cancel reorder alarm and snap all views in target layout back to true permanent positions
        cleanupReorder(true);
        if (target != null) {
            target.revertTempStateImmediately();
        }

        if (v instanceof BubbleTextView || v instanceof Folder) {
            v.clearAnimation();
        }
        if (v != null) {
            v.setTranslationX(0f);
            v.setTranslationY(0f);
            if (v instanceof Reorderable) {
                ((Reorderable) v).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0f, 0f);
                ((Reorderable) v).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_BOUNCE_OFFSET, 0f, 0f);
            }
            if (target != null && target.getShortcutsAndWidgets() != null) {
                target.getShortcutsAndWidgets().measureChild(v);
                target.getShortcutsAndWidgets().layoutChild(v);
            }
        }
        final int screenId = getCellLayoutId(target);
        if (aboveShortcut && willBecomeShortcut) {
            WorkspaceItemInfo sourceInfo = (WorkspaceItemInfo) newView.getTag();
            WorkspaceItemInfo destInfo = (WorkspaceItemInfo) v.getTag();

            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            target.removeView(v);
            mStatsLogManager.logger().withItemInfo(destInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_FOLDER_CREATED);
            FolderIcon fi = mLauncher.addFolder(target, container, screenId, targetCell[0],
                    targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(fi, folderLocation);

            // If the dragView is null, we can't animate
            boolean animate = d != null;
            if (animate) {
                // In order to keep everything continuous, we hand off the currently rendered
                // folder background to the newly created icon. This preserves animation state.
                if (mFolderCreateBg == null) {
                    mFolderCreateBg = new PreviewBackground();
                    mFolderCreateBg.setup(mLauncher, mLauncher, null,
                            v.getMeasuredWidth(), v.getPaddingTop());
                }
                mFolderCreateBg.animateToAccept(target, targetCell[0], targetCell[1]);
                fi.setFolderBackground(mFolderCreateBg);
                mFolderCreateBg = new PreviewBackground();
                fi.performCreateAnimation(destInfo, v, sourceInfo, d, folderLocation, scale);
            } else {
                fi.prepareCreateAnimation(v);
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
            float distance, DragObject d, boolean external) {
        if (distance > target.getFolderCreationRadius(targetCell)) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        boolean allowAddToFolder = mAddToExistingFolderOnDrop || mDragMode == DRAG_MODE_ADD_TO_FOLDER
                || willAddToExistingUserFolder(d.dragInfo, dropOverView);
        if (!allowAddToFolder) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                DragTrace.log("Workspace.addToExistingFolder folder=" + fi.mInfo.id + " item=" + (d.dragInfo != null ? d.dragInfo.title : "null"));
                mStatsLogManager.logger().withItemInfo(fi.mInfo).withInstanceId(d.logInstanceId)
                        .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED_ON_FOLDER_ICON);
                fi.onDrop(d, false /* itemReturnedOnFailedDrop */);
                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void prepareAccessibilityDrop() {}

    @Override
    public void onDrop(final DragObject d, DragOptions options) {
        DragTrace.log("Workspace.onDrop item=" + (d.dragInfo != null ? d.dragInfo.title : "null")
                + " source=" + (d.dragSource != null ? d.dragSource.getClass().getSimpleName() : "null"));
        isDragWidget(d);
        mRevertAlarm.cancelAlarm();
        mPrevDragViewVisualCenter[0] = mPrevDragViewVisualCenter[1] = -1f;
        android.util.Log.d("DropDebug", "onDrop called. d.dragSource==this: " + (d.dragSource == this) + " mDragInfo==null: " + (mDragInfo == null) + " mDropToLayout==null: " + (mDropToLayout == null));
        dumpFullWorkspaceState("Workspace.onDrop");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        CellLayout dropTargetLayout = mDropToLayout != null ? mDropToLayout : mDragTargetLayout;
        if (dropTargetLayout == null) {
            dropTargetLayout = (CellLayout) getChildAt(mCurrentPage);
        }
        android.util.Log.d("DropDebug", "dropTargetLayout resolved to: " + dropTargetLayout);

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);
        }

        if (d.dragInfo != null && d.dragInfo.getTargetComponent() != null) {
            com.android.launcher3.util.RemovedAppStore.unmarkRemoved(
                    getContext(), d.dragInfo.getTargetComponent().flattenToString());
        }

        boolean droppedOnOriginalCell = false;

        boolean snappedToNewPage = false;
        boolean resizeOnDrop = false;
        Runnable onCompleteRunnable = null;
        if (d.dragSource != this || mDragInfo == null) {
            final int[] touchXY = new int[]{(int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1]};
            onDropExternal(touchXY, dropTargetLayout, d);
        } else {
            final View cell = mDragInfo.cell;
            boolean droppedOnOriginalCellDuringTransition = false;

            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                int container = hasMovedIntoHotseat ?
                        LauncherSettings.Favorites.CONTAINER_HOTSEAT :
                        LauncherSettings.Favorites.CONTAINER_DESKTOP;
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;

                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.
                if (mDragMode == DRAG_MODE_CREATE_FOLDER && mDragOverX >= 0 && mDragOverY >= 0) {
                    mTargetCell[0] = mDragOverX;
                    mTargetCell[1] = mDragOverY;
                } else {
                    mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                            mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                }
                int dropLayoutId = getCellLayoutId(dropTargetLayout);
                if (Workspace.EXTRA_EMPTY_SCREEN_IDS.contains(dropLayoutId)) {
                    commitExtraEmptyScreens();
                    dropLayoutId = getCellLayoutId(dropTargetLayout);
                }
                int screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : dropLayoutId;
                float distance = dropTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (createUserFolderIfNecessary(cell, container, dropTargetLayout, mTargetCell,
                        distance, false, d)
                        || addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    if (dropTargetLayout != null && dropTargetLayout.isItemPlacementDirty()) {
                        dropTargetLayout.revertTempState();
                    }
                    if (!mLauncher.isInState(EDIT_MODE)) {
                        mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                    }
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                CellPos originalPresenterPos = getCellPosMapper().mapModelToPresenter(item);
                droppedOnOriginalCell = originalPresenterPos.screenId == screenId
                        && item.container == container
                        && originalPresenterPos.cellX == mTargetCell[0]
                        && originalPresenterPos.cellY == mTargetCell[1];
                droppedOnOriginalCellDuringTransition = droppedOnOriginalCell && mIsSwitchingState;

                // When quickly moving an item, a user may accidentally rearrange their
                // workspace. So instead we move the icon back safely to its original position.
                boolean returnToOriginalCellToPreventShuffling = !isFinishedSwitchingState()
                        && !droppedOnOriginalCellDuringTransition && !dropTargetLayout
                        .isRegionVacant(mTargetCell[0], mTargetCell[1], spanX, spanY);
                int[] resultSpan = new int[2];
                List<View> overflowViews = Collections.emptyList();
                if (returnToOriginalCellToPreventShuffling) {
                    android.util.Log.d("DropDebug", "returnToOriginalCellToPreventShuffling is true");
                    mTargetCell[0] = mTargetCell[1] = -1;
                } else {
                    // Free the original cell BEFORE performReorder so mOccupied is accurate.
                    // Without this, commitTempPlacement copies tmpOccupied (which has the dragged
                    // icon at its original position) into mOccupied, leaving a phantom occupied
                    // cell that breaks subsequent drag calculations.
                    dropTargetLayout.markCellsAsUnoccupiedForView(cell);
                    android.util.Log.d("DropDebug", "Freed original cell before performReorder");

                    // Find the target cell first without modifying layout params
                    int[] tempTarget = new int[]{mTargetCell[0], mTargetCell[1]};
                    tempTarget = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                            (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                            cell, tempTarget, resultSpan, CellLayout.MODE_ON_DROP);
                    overflowViews = dropTargetLayout.consumeOverflowViews();
                    mTargetCell[0] = tempTarget[0];
                    mTargetCell[1] = tempTarget[1];
                    DragTrace.log("Workspace.onDrop performReorder returned targetCell=(" + mTargetCell[0] + "," + mTargetCell[1] + ")");
                    android.util.Log.d("DropDebug", "performReorder returned: " + mTargetCell[0] + ", " + mTargetCell[1]);
                }

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                // if the widget resizes on drop
                if (foundCell && (cell instanceof AppWidgetHostView) &&
                        (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)) {
                    resizeOnDrop = true;
                    item.spanX = resultSpan[0];
                    item.spanY = resultSpan[1];
                    AppWidgetHostView awhv = (AppWidgetHostView) cell;
                    WidgetSizes.updateWidgetSizeRanges(awhv, mLauncher, resultSpan[0],
                            resultSpan[1]);
                }

                if (foundCell) {
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (mDragInfo != null && mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                        if (hasMovedLayouts) {
                            CellLayout parentCell = getParentCellLayoutForView(cell);
                            if (parentCell != null) {
                                parentCell.removeView(cell);
                            }
                            d.dragView.detachContentView(/* reattachToPreviousParent= */ false);
                            addInScreen(cell, container, screenId, mTargetCell[0], mTargetCell[1],
                                    info.spanX, info.spanY);
                        } else {
                            d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                        }
                    }

                    int targetScreenIndex = getPageIndexForScreenId(screenId);
                    int snapScreen = getLeftmostVisiblePageForIndex(targetScreenIndex);
                    // On large screen devices two pages can be shown at the same time, and snap
                    // isn't needed if the source and target screens appear at the same time
                    if (snapScreen != mCurrentPage && !hasMovedIntoHotseat) {
                        snapToPage(snapScreen);
                        snappedToNewPage = true;
                    }
                    if (hasMovedLayouts && !(mDragInfo != null && mDragInfo.cell instanceof LauncherAppWidgetHostView)) {
                        // Reparent FIRST: removeView() un-occupies using the icon's SOURCE cell.
                        CellLayout parentCell = getParentCellLayoutForView(cell);
                        if (parentCell != null) {
                            parentCell.removeView(cell);
                        }
                        addInScreen(cell, container, screenId, mTargetCell[0], mTargetCell[1],
                                info.spanX, info.spanY);
                    }

                    // Only now overwrite LayoutParams with the target cell.
                    CellLayoutLayoutParams lpEarly = (cell.getLayoutParams() instanceof CellLayoutLayoutParams)
                            ? (CellLayoutLayoutParams) cell.getLayoutParams() : null;
                    if (lpEarly != null) {
                        lpEarly.setTmpCellX(mTargetCell[0]);
                        lpEarly.setCellX(mTargetCell[0]);
                        lpEarly.setTmpCellY(mTargetCell[1]);
                        lpEarly.setCellY(mTargetCell[1]);
                        lpEarly.cellHSpan = item.spanX;
                        lpEarly.cellVSpan = item.spanY;
                        lpEarly.isLockedToGrid = true;
                        if (cell.getParent() instanceof ShortcutAndWidgetContainer) {
                            ((ShortcutAndWidgetContainer) cell.getParent()).setupLp(cell);
                        }
                    }
                    android.util.Log.d("IconDragLogger", "DROP SUCCESS -> Moved icon '" + (item.title != null ? item.title : "Item") + "' to Cell(" + mTargetCell[0] + ", " + mTargetCell[1] + ")");
                    post(() -> logIconLocations("AFTER DROP FINAL"));

                    if (container != LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                            cell instanceof LauncherAppWidgetHostView) {

                        // We post this call so that the widget has a chance to be placed
                        // in its final location
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, dropTargetLayout);
                    }

                    // Mark the FINAL cells explicitly (never via the stale model coordinates).
                    dropTargetLayout.markCellsOccupied(mTargetCell[0], mTargetCell[1],
                            item.spanX, item.spanY, true);
                    mLauncher.getModelWriter().modifyItemInDatabase(info, container, screenId,
                            mTargetCell[0], mTargetCell[1], item.spanX, item.spanY);

                    // Icons that no longer fit on this page go to the next one(s).
                    relocateOverflowViews(dropTargetLayout, overflowViews);
                } else {
                    android.util.Log.d("DropDebug", "foundCell is false. returnToOriginalCellToPreventShuffling=" + returnToOriginalCellToPreventShuffling);
                    if (!returnToOriginalCellToPreventShuffling) {
                        onNoCellFound(dropTargetLayout, d.dragInfo, d.logInstanceId);
                    }
                    if (mDragInfo != null && mDragInfo.cell instanceof LauncherAppWidgetHostView) {
                        d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                        mDragInfo.cell.setVisibility(VISIBLE);
                    }

                    // If we can't find a drop location, we return the item to its original position
                    if (cell.getLayoutParams() instanceof CellLayoutLayoutParams) {
                        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) cell.getLayoutParams();
                        mTargetCell[0] = lp.getCellX();
                        mTargetCell[1] = lp.getCellY();
                    }
                    if (cell.getParent() != null && cell.getParent().getParent() instanceof CellLayout) {
                        CellLayout layout = (CellLayout) cell.getParent().getParent();
                        layout.markCellsAsOccupiedForView(cell);
                    }
                }
            } else {
                // When drag is cancelled, reattach content view back to its original parent.
                if (cell instanceof LauncherAppWidgetHostView) {
                    d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                    cell.setVisibility(VISIBLE);

                    final CellLayout cellLayout = getParentCellLayoutForView(cell);
                    boolean pageIsVisible = isVisible(cellLayout);

                    if (pageIsVisible) {
                        onCompleteRunnable = getWidgetResizeFrameRunnable(options,
                                (LauncherAppWidgetHostView) cell, cellLayout);
                    }
                }
            }

            final CellLayout parent = (cell.getParent() != null && cell.getParent().getParent() instanceof CellLayout)
                    ? (CellLayout) cell.getParent().getParent()
                    : dropTargetLayout;
            if (d.dragView.hasDrawn()) {
                if (droppedOnOriginalCellDuringTransition) {
                    // Animate the item to its original position, while simultaneously exiting
                    // spring-loaded mode so the page meets the icon where it was picked up.
                    final RunnableList callbackList = new RunnableList();
                    final Runnable onCompleteCallback = onCompleteRunnable;
                    LauncherState currentState = mLauncher.getStateManager().getState();
                    mLauncher.getDragController().animateDragViewToOriginalPosition(
                            /* onComplete= */ callbackList::executeAllAndDestroy, cell,
                            currentState.getTransitionDuration(mLauncher, true /* isToState */));
                    if (!mLauncher.isInState(EDIT_MODE)) {
                        mLauncher.getStateManager().goToState(NORMAL, /* delay= */ 0,
                                onCompleteCallback == null
                                        ? null
                                        : forSuccessCallback(
                                                () -> callbackList.add(onCompleteCallback)));
                    } else if (onCompleteCallback != null) {
                        forSuccessCallback(() -> callbackList.add(onCompleteCallback));
                    }
                    mLauncher.getDropTargetBar().onDragEnd();
                    parent.onDropChild(cell);
                    return;
                }
                final ItemInfo info = (ItemInfo) cell.getTag();
                boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                        || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
                if (isWidget && dropTargetLayout != null) {
                    // animate widget to a valid place
                    int animationType = resizeOnDrop ? ANIMATE_INTO_POSITION_AND_RESIZE :
                            ANIMATE_INTO_POSITION_AND_DISAPPEAR;
                    animateWidgetDrop(info, parent, d.dragView, null, animationType, cell, false);
                } else {
                    int duration = snappedToNewPage ? ADJACENT_SCREEN_DROP_DURATION : -1;
                    mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                            this);
                }
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);

            if (!mLauncher.isInState(EDIT_MODE)) {
                mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY,
                        onCompleteRunnable == null ? null : forSuccessCallback(onCompleteRunnable));
            } else if (onCompleteRunnable != null) {
                forSuccessCallback(onCompleteRunnable);
            }
            mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
        }

        if (d.stateAnnouncer != null && !droppedOnOriginalCell) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
    }

    /**
     * Moves icons that were pushed off dropTarget to the first free cell of the following pages,
     * creating a new page if every following page is full.
     * Overflow icons are appended in reading order, not inserted at the front of the next page.
     */
    private void relocateOverflowViews(CellLayout source, List<View> views) {
        if (views == null || views.isEmpty()) return;
        final int sourceIndex = indexOfChild(source);
        if (sourceIndex < 0) return;

        final int[] cell = new int[2];
        for (View v : views) {
            CellLayout target = findOverflowTarget(sourceIndex, cell);
            if (target == null) {
                if (!hasExtraEmptyScreens()) addExtraEmptyScreens();
                target = findOverflowTarget(sourceIndex, cell);
            }
            if (target == null) break;   // cannot create a page: leave the icon where the solution put it

            int screenId = getCellLayoutId(target);
            if (EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
                screenId = commitExtraEmptyScreen(screenId);
                target = getScreenWithId(screenId);
                if (target == null || !target.findCellForSpan(cell, 1, 1)) continue;
            }

            ItemInfo info = (ItemInfo) v.getTag();
            v.setTranslationX(0f);
            v.setTranslationY(0f);
            if (v instanceof Reorderable) {
                ((Reorderable) v).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_PREVIEW_OFFSET, 0f, 0f);
                ((Reorderable) v).getTranslateDelegate()
                        .setTranslation(INDEX_REORDER_BOUNCE_OFFSET, 0f, 0f);
            }
            v.setAlpha(1f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            // Occupancy of the source was already rebuilt (overflow cells excluded) by the commit,
            // so detach WITHOUT going through CellLayout.removeView() (it would un-occupy by
            // stale LayoutParams and could free a cell now used by a reflowed icon).
            source.getShortcutsAndWidgets().removeView(v);
            addInScreen(v, LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId,
                    cell[0], cell[1], 1, 1);
            mLauncher.getModelWriter().modifyItemInDatabase(info,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId, cell[0], cell[1], 1, 1);
        }
    }

    private CellLayout findOverflowTarget(int sourceIndex, int[] outCell) {
        for (int i = sourceIndex + 1; i < getChildCount(); i++) {
            View page = getChildAt(i);
            if (!(page instanceof CellLayout)) continue;
            CellLayout cl = (CellLayout) page;
            if (cl == mAppLibraryCellLayout || getCellLayoutId(cl) == APP_LIBRARY_SCREEN_ID) continue;
            if (cl.findCellForSpan(outCell, 1, 1)) return cl;
        }
        return null;
    }

    @Nullable
    private Runnable getWidgetResizeFrameRunnable(DragOptions options,
            LauncherAppWidgetHostView hostView, CellLayout cellLayout) {
        AppWidgetProviderInfo pInfo = hostView.getAppWidgetInfo();
        if (pInfo != null) {
            return () -> {
                if (!isPageInTransition()) {
                    AppWidgetResizeFrame.showForWidget(hostView, cellLayout);
                }
            };
        }
        return null;
    }

    public void onNoCellFound(
            View dropTargetLayout, ItemInfo itemInfo, @Nullable InstanceId logInstanceId) {
        int strId = mLauncher.isHotseatLayout(dropTargetLayout)
                ? R.string.hotseat_out_of_space : R.string.out_of_space;
        Toast.makeText(mLauncher, mLauncher.getString(strId), Toast.LENGTH_SHORT).show();
        StatsLogManager.StatsLogger logger = mStatsLogManager.logger().withItemInfo(itemInfo);
        if (logInstanceId != null) {
            logger = logger.withInstanceId(logInstanceId);
        }
        logger.log(LauncherEvent.LAUNCHER_ITEM_DROP_FAILED_INSUFFICIENT_SPACE);
    }

    /**
     * Computes and returns the area relative to dragLayer which is used to display a page.
     * In case we have multiple pages displayed at the same time, we return the union of the areas.
     */
    public Rect getPageAreaRelativeToDragLayer() {
        Rect area = new Rect();
        int nextPage = getNextPage();
        int panelCount = getPanelCount();
        for (int page = nextPage; page < nextPage + panelCount; page++) {
            CellLayout child = (CellLayout) getChildAt(page);
            if (child == null) {
                break;
            }

            ShortcutAndWidgetContainer boundingLayout = child.getShortcutsAndWidgets();
            Rect tmpRect = new Rect();
            mLauncher.getDragLayer().getDescendantRectRelativeToSelf(boundingLayout, tmpRect);
            area.union(tmpRect);
        }

        return area;
    }

    @Override
    public void onDragEnter(DragObject d) {
        DragTrace.log("Workspace.onDragEnter item=" + (d.dragInfo != null ? d.dragInfo.title : "null")
                + " folderOpen=" + (com.android.launcher3.folder.Folder.getOpen(mLauncher) != null));
        if (com.android.launcher3.folder.Folder.getOpen(mLauncher) == null) {
            setAlpha(1f);
            setVisibility(VISIBLE);
            for (int i = 0; i < getPageCount(); i++) {
                View page = getChildAt(i);
                if (page instanceof CellLayout) {
                    CellLayout cl = (CellLayout) page;
                    cl.setVisibility(VISIBLE);
                    cl.setAlpha(1f);
                    if (cl.getShortcutsAndWidgets() != null) {
                        cl.getShortcutsAndWidgets().setVisibility(VISIBLE);
                        cl.getShortcutsAndWidgets().setAlpha(1f);
                    }
                }
            }
            if (!isAppLibraryCurrentPage()) {
                if (mLauncher.getHotseat() != null) {
                    mLauncher.getHotseat().setAlpha(1f);
                    mLauncher.getHotseat().setIconsAlpha(1f);
                    mLauncher.getHotseat().setVisibility(VISIBLE);
                    mLauncher.getHotseat().setForcedTranslationY(0f);
                }
                View dockGlass = mLauncher.findViewById(R.id.dock_glass);
                if (dockGlass != null) {
                    dockGlass.setVisibility(VISIBLE);
                    dockGlass.setAlpha(1f);
                    dockGlass.setTranslationY(0f);
                }
                if (mPageIndicator != null) {
                    mPageIndicator.setVisibility(VISIBLE);
                    mPageIndicator.setAlpha(1f);
                    if (mPageIndicator instanceof PageIndicatorDots) {
                        ((PageIndicatorDots) mPageIndicator).setForcedTranslationY(0f);
                    }
                    showPageIndicatorAtCurrentScroll();
                }
                if (mLauncher.mBlurLayer != null) {
                    mLauncher.mBlurLayer.setAlpha(0f);
                }
            }
        }
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnter", 1, 1);
        }

        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;

        mDropToLayout = null;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1]);
    }

    @Override
    public void onDragExit(DragObject d) {
        DragTrace.log("Workspace.onDragExit dragComplete=" + d.dragComplete + " revertTemp=" + !d.dragComplete);
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragExit", -1, 0);
        }

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout;
        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the previous drag target
        mRevertAlarm.cancelAlarm();
        mPrevDragViewVisualCenter[0] = mPrevDragViewVisualCenter[1] = -1f;
        setCurrentDropLayout(null, /* revertTemp= */ !d.dragComplete);
        setCurrentDragOverlappingLayout(null);
        isWidget = false;

        mSpringLoadedDragController.cancel();
    }

    private void enforceDragParity(String event, int update, int expectedValue) {
        enforceDragParity(this, event, update, expectedValue);
        for (int i = 0; i < getChildCount(); i++) {
            enforceDragParity(getChildAt(i), event, update, expectedValue);
        }
    }

    private void enforceDragParity(View v, String event, int update, int expectedValue) {
        Object tag = v.getTag(R.id.drag_event_parity);
        int value = tag == null ? 0 : (Integer) tag;
        value += update;
        v.setTag(R.id.drag_event_parity, value);

        if (value != expectedValue) {
            Log.e(TAG, event + ": Drag contract violated: " + value);
        }
    }

    void setCurrentDropLayout(CellLayout layout) {
        setCurrentDropLayout(layout, true);
    }

    private void setCurrentDropLayout(CellLayout layout, boolean revertTemp) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragExit(revertTemp);
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    protected void cleanupFolderCreation() {
        mFolderCreationAlarm.cancelAlarm();
        mPendingFolderDragOverView = null;
        if (mFolderCreateBg != null) {
            mFolderCreateBg.animateToRest();
        }
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit();
            mDragOverFolderIcon = null;
        }
    }

    protected void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mRevertAlarm.cancelAlarm();
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    /*
     *
     * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
     * coordinate space. The argument xy is modified with the return result.
     */
    private void mapPointFromSelfToChild(View v, float[] xy) {
        xy[0] = xy[0] - v.getLeft();
        xy[1] = xy[1] - v.getTop();
    }

    /**
     * Updates the point in {@param xy} to point to the co-ordinate space of {@param layout}
     *
     * @param layout either hotseat of a page in workspace
     * @param xy     the point location in workspace co-ordinate space
     */
    private void mapPointFromDropLayout(CellLayout layout, float[] xy) {
        if (mLauncher.isHotseatLayout(layout)) {
            mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(this, xy, true);
            mLauncher.getDragLayer().mapCoordInSelfToDescendant(layout, xy);
        } else {
            mapPointFromSelfToChild(layout, xy);
        }
    }
    public static boolean isWidget;

    private boolean isDragWidget(DragObject d) {
        if (d != null && d.dragInfo != null) {
            isWidget = (d.dragInfo instanceof LauncherAppWidgetInfo
                    || d.dragInfo instanceof PendingAddWidgetInfo
                    || d.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    || d.dragInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
                    || (mDragInfo != null && mDragInfo.cell instanceof LauncherAppWidgetHostView));
        } else {
            isWidget = false;
        }
        return isWidget;
    }

    public void onDragOver(DragObject d) {
        isDragWidget(d);
        // Skip drag over events while the workspace is actively scrolling between pages
        if (isPageInTransition() || !transitionStateShouldAllowDrop()) {
            return;
        }

        ItemInfo item = d.dragInfo;
        if (item == null) {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

        if (mPrevDragViewVisualCenter[0] >= 0 && mPrevDragViewVisualCenter[1] >= 0) {
            float deltaX = mDragViewVisualCenter[0] - mPrevDragViewVisualCenter[0];
            float deltaY = mDragViewVisualCenter[1] - mPrevDragViewVisualCenter[1];
            if (mDragTargetLayout != null) {
                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 2f) {
                    mDragTargetLayout.mDirectionVector[0] = deltaX > 0 ? 1 : -1;
                    mDragTargetLayout.mDirectionVector[1] = 0;
                } else if (Math.abs(deltaY) > 2f) {
                    mDragTargetLayout.mDirectionVector[0] = 0;
                    mDragTargetLayout.mDirectionVector[1] = deltaY > 0 ? 1 : -1;
                }
            }
        }
        mPrevDragViewVisualCenter[0] = mDragViewVisualCenter[0];
        mPrevDragViewVisualCenter[1] = mDragViewVisualCenter[1];

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (setDropLayoutForDragObject(d, mDragViewVisualCenter[0], mDragViewVisualCenter[1])) {
            if (mDragTargetLayout == null || mLauncher.isHotseatLayout(mDragTargetLayout)) {
                mSpringLoadedDragController.cancel();
            } else {
                mSpringLoadedDragController.setAlarm(mDragTargetLayout);
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(mDragTargetLayout, mDragViewVisualCenter);

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], item.spanX, item.spanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];
            DragTrace.log("Workspace.onDragOver targetCell=(" + reorderX + "," + reorderY + ") item=" + (item.title != null ? item.title : "item"));

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromWorkspaceCellVisualCenter(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            if (isWobbling() && child != null) {
                child.clearAnimation();
            }
            manageFolderFeedback(targetCellDistance, d);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            android.util.Log.d("IconDragLogger", "DRAGGING OVER -> Item: '" + (item.title != null ? item.title : "Item") + "' over Target Cell: (" + reorderX + ", " + reorderY + ")");
            manageReorderOnDragOver(d, targetCellDistance, nearestDropOccupied, minSpanX, minSpanY,
                    reorderX, reorderY);

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
                if (mDragTargetLayout != null && mDragTargetLayout.isItemPlacementDirty()) {
                    mDragTargetLayout.revertTempStateImmediately();
                }
            } else if (!nearestDropOccupied) {
                // Debounce reverting when temporarily crossing gutters / empty space
                if (mDragTargetLayout != null && mDragTargetLayout.isItemPlacementDirty()) {
                    if (!mRevertAlarm.alarmPending()) {
                        final CellLayout targetToRevert = mDragTargetLayout;
                        mRevertAlarm.setOnAlarmListener(alarm -> {
                            if (targetToRevert != null && targetToRevert.isItemPlacementDirty()) {
                                DragTrace.log("Workspace.mRevertAlarm fired -> reverting reorder dirty=true");
                                targetToRevert.revertTempState();
                                setDragMode(DRAG_MODE_NONE);
                            }
                        });
                        mRevertAlarm.setAlarm(REVERT_TIMEOUT);
                    }
                } else {
                    setDragMode(DRAG_MODE_NONE);
                }
            } else {
                if (mRevertAlarm.alarmPending()) {
                    mRevertAlarm.cancelAlarm();
                }
            }
        } else {
            DragTrace.log("Workspace.onDragOver null-dragTargetLayout visualCenter=(" + (int) mDragViewVisualCenter[0] + "," + (int) mDragViewVisualCenter[1] + ")");
        }
    }

    protected void manageReorderOnDragOver(DragObject d, float targetCellDistance,
            boolean nearestDropOccupied, int minSpanX, int minSpanY, int reorderX, int reorderY) {

        if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            cleanupReorder(true);
            return;
        }

        ItemInfo item = d.dragInfo;
        final View child = (mDragInfo == null) ? null : mDragInfo.cell;

        if (!nearestDropOccupied) {
            cleanupReorder(true);
            int[] span = new int[]{item.spanX, item.spanY};
            int[] hintCell = new int[]{mTargetCell[0], mTargetCell[1]};
            mDragTargetLayout.visualizeDropLocation(hintCell[0], hintCell[1], span[0],
                    span[1], d);
        } else if (targetCellDistance < mDragTargetLayout.getReorderRadius(mTargetCell, item.spanX, item.spanY)) {
            if (mRevertAlarm.alarmPending()) {
                mRevertAlarm.cancelAlarm();
            }
            final boolean cellChanged = (mLastReorderX != reorderX || mLastReorderY != reorderY);
            if (cellChanged) {
                mReorderAlarm.cancelAlarm();
                mLastReorderX = reorderX;
                mLastReorderY = reorderY;
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d, child);
                mReorderAlarm.setOnAlarmListener(listener);
                int timeout = (mDragTargetLayout != null && mDragTargetLayout.isItemPlacementDirty())
                        ? 25 : REORDER_TIMEOUT;
                DragTrace.log("manageReorderOnDragOver scheduleReorder target=(" + reorderX + "," + reorderY + ") timeout=" + timeout);
                mReorderAlarm.setAlarm(timeout);
            } else if (!mReorderAlarm.alarmPending() && (mDragTargetLayout != null && !mDragTargetLayout.isItemPlacementDirty())) {
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d, child);
                mReorderAlarm.setOnAlarmListener(listener);
                DragTrace.log("manageReorderOnDragOver scheduleInitialReorder target=(" + reorderX + "," + reorderY + ") timeout=" + REORDER_TIMEOUT);
                mReorderAlarm.setAlarm(REORDER_TIMEOUT);
            }
        }
    }

    /**
     * Updates {@link #mDragTargetLayout} and {@link #mDragOverlappingLayout}
     * based on the DragObject's position.
     *
     * The layout will be:
     * - The Hotseat if the drag object is over it
     * - A side page if we are in spring-loaded mode and the drag object is over it
     * - The current page otherwise
     *
     * @return whether the layout is different from the current {@link #mDragTargetLayout}.
     */
    private boolean setDropLayoutForDragObject(DragObject d, float centerX, float centerY) {
        if (isPageInTransition()) {
            return false;
        }
        CellLayout layout = null;
        if (shouldUseHotseatAsDropLayout(d)) {
            layout = mLauncher.getHotseat();
        } else if (!isDragObjectOverSmartSpace(d)) {
            // If the object is over qsb/smartspace, we don't want to highlight anything.

            // Check neighbour pages
            layout = checkDragObjectIsOverNeighbourPages(d, centerX);

            if (layout == null) {
                // Check visible pages
                IntSet visiblePageIndices = getVisiblePageIndices();
                for (int visiblePageIndex : visiblePageIndices) {
                    layout = verifyInsidePage(visiblePageIndex, d.x, d.y);
                    if (layout != null) break;
                }
            }
            if (layout == null && mDragTargetLayout != null && !shouldUseHotseatAsDropLayout(d)) {
                layout = mDragTargetLayout;
            }
        }

        // Update the current drop layout if the target changed
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    private boolean shouldUseHotseatAsDropLayout(DragObject dragObject) {
        if (mLauncher.getHotseat() == null
                || mLauncher.getHotseat().getShortcutsAndWidgets() == null
                || isDragWidget(dragObject)) {
            return false;
        }
        View hotseatShortcuts = mLauncher.getHotseat().getShortcutsAndWidgets();
        getViewBoundsRelativeToWorkspace(hotseatShortcuts, mTempRect);
        return mTempRect.contains(dragObject.x, dragObject.y);
    }

    private boolean isDragObjectOverSmartSpace(DragObject dragObject) {
        if (mFirstPagePinnedItem == null) {
            return false;
        }
        // In iOS-style single layer mode, the first-page pinned item spans the entire
        // first screen. Using its full bounds would wrongly block ALL drops on page 1.
        // Only do this check in the standard (non-iOS) launcher mode.
        if (MultiModeController.isSingleLayerMode()) {
            return false;
        }
        getViewBoundsRelativeToWorkspace(mFirstPagePinnedItem, mTempRect);
        return mTempRect.contains(dragObject.x, dragObject.y);
    }

    private CellLayout checkDragObjectIsOverNeighbourPages(DragObject d, float centerX) {
        if (isPageInTransition()) {
            return null;
        }

        int nextPage = getNextPage();
        CellLayout leftLayout = (CellLayout) getChildAt(nextPage);
        if (leftLayout == null) {
            return null;
        }
        CellLayout rightLayout = isTwoPanelEnabled()
                ? (CellLayout) getChildAt(nextPage + 1)
                : leftLayout;
        if (rightLayout == null) {
            rightLayout = leftLayout;
        }

        int screenLeft = leftLayout.getLeft();
        int screenRight = rightLayout.getRight();
        int screenWidth = screenRight - screenLeft;

        // Coordinates relative to the visible screen area
        float relTouchX = d.x - screenLeft;
        float relCenterX = centerX - screenLeft;

        // Edge gutter threshold: only trigger neighbor page snap when the user deliberately
        // pushes into the screen edge gutter (<= 24dp from outer bezel).
        // Inside Column 0 (center ~57dp, left bound 16dp) or Column 3, neither the finger
        // nor the icon center can reach <= 24dp, keeping in-page reordering fully responsive.
        float edgeGutter = getResources().getDisplayMetrics().density * 24f;

        boolean isAtLeftEdge = (relCenterX <= edgeGutter || relTouchX <= edgeGutter);
        boolean isAtRightEdge = (relCenterX >= screenWidth - edgeGutter || relTouchX >= screenWidth - edgeGutter);

        IntSet pageIndexesToVerify = IntSet.wrap(nextPage - 1,
                nextPage + (isTwoPanelEnabled() ? 2 : 1));

        for (int pageIndex : pageIndexesToVerify) {
            boolean isLeftNeighbor = ((pageIndex < nextPage) && !mIsRtl) || (pageIndex > nextPage && mIsRtl);
            if (isLeftNeighbor) {
                if (!isAtLeftEdge) {
                    continue;
                }
            } else {
                if (!isAtRightEdge) {
                    continue;
                }
            }

            CellLayout neighborLayout = (CellLayout) getPageAt(pageIndex);
            if (neighborLayout == null) {
                continue;
            }

            // In verifyInsidePage, the coordinate must fall within the neighbor page's bounds.
            // Since neighbor pages are completely off-screen in single-layer 1:1 scale mode,
            // supply a coordinate just inside the neighbor's boundary to pass verifyInsidePage checks.
            float touchX = isLeftNeighbor
                    ? (neighborLayout.getRight() - 10)
                    : (neighborLayout.getLeft() + 10);
            float touchY = Math.max(neighborLayout.getTop() + 10,
                    Math.min(d.y, neighborLayout.getBottom() - 10));

            CellLayout layout = verifyInsidePage(pageIndex, touchX, touchY);
            if (layout != null) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Gets the given view's bounds relative to Workspace
     */
    private void getViewBoundsRelativeToWorkspace(View view, Rect outRect) {
        mLauncher.getDragLayer()
                .getDescendantRectRelativeToSelf(view, mTempRect);
        // map draglayer relative bounds to workspace
        mLauncher.getDragLayer().mapRectInSelfToDescendant(this, mTempRect);
        outRect.set(mTempRect);
    }

    /**
     * Returns the child CellLayout if the point is inside the page coordinates, null otherwise.
     */
    private CellLayout verifyInsidePage(int pageNo, float x, float y) {
        // In iOS single-layer mode, only skip the page that actually holds the pinned
        // search/smartspace widget. Without this guard the old code skipped page 0 even when
        // no pinned widget existed, leaving mDragTargetLayout null on the first home screen
        // and breaking reordering, folder creation, and drop-target highlighting.
        if (MultiModeController.isSingleLayerMode() && mFirstPagePinnedItem != null) {
            int firstPinnedPageIndex = getPageIndexForScreenId(FIRST_SCREEN_ID);
            if (pageNo == firstPinnedPageIndex) {
                return null;
            }
        }

        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        if (mAppLibraryPinnedItem != null && appLibIndex >= 0 && pageNo == appLibIndex) {
            return null;
        }
        if (pageNo >= 0 && pageNo < getPageCount()) {
            CellLayout cl = (CellLayout) getChildAt(pageNo);
            if (cl != null && x >= cl.getLeft() && x <= cl.getRight()
                    && y >= cl.getTop() && y <= cl.getBottom()) {
                // This point is inside the cell layout
                return cl;
            }
        }
        return null;
    }

    private void manageFolderFeedback(float distance, DragObject dragObject) {
        if (distance > mDragTargetLayout.getFolderCreationRadius(mTargetCell)
                || (mDragTargetLayout != null && mDragTargetLayout.isItemPlacementDirty())
                || mDragMode == DRAG_MODE_REORDER) {
            mFolderCreationAlarm.cancelAlarm();
            mPendingFolderDragOverView = null;
            if ((mDragMode == DRAG_MODE_ADD_TO_FOLDER
                    || mDragMode == DRAG_MODE_CREATE_FOLDER)) {
                setDragMode(DRAG_MODE_NONE);
            }
            return;
        }

        final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0], mTargetCell[1]);
        ItemInfo info = dragObject.dragInfo;
        boolean userFolderPending = willCreateUserFolder(info, dragOverView, false);
        if (userFolderPending) {
            if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
                return;
            }
            if (mPendingFolderDragOverView != dragOverView) {
                mPendingFolderDragOverView = dragOverView;
                mFolderCreationAlarm.cancelAlarm();
                final int folderCellX = mTargetCell[0];
                final int folderCellY = mTargetCell[1];
                final CellLayout targetLayout = mDragTargetLayout;
                mFolderCreationAlarm.setOnAlarmListener(alarm -> {
                    if (targetLayout != null && !targetLayout.isItemPlacementDirty()
                            && mDragMode != DRAG_MODE_REORDER
                            && mPendingFolderDragOverView != null
                            && mPendingFolderDragOverView == dragOverView) {
                        mFolderCreateBg = new PreviewBackground();
                        mFolderCreateBg.setup(mLauncher, mLauncher, null,
                                mPendingFolderDragOverView.getMeasuredWidth(),
                                mPendingFolderDragOverView.getPaddingTop());

                        // The full preview background should appear behind the icon
                        mFolderCreateBg.isClipping = false;

                        mFolderCreateBg.animateToAccept(targetLayout, folderCellX, folderCellY);
                        targetLayout.clearDragOutlines();
                        setDragMode(DRAG_MODE_CREATE_FOLDER);

                        if (dragObject.stateAnnouncer != null) {
                            dragObject.stateAnnouncer.announce(
                                    WorkspaceAccessibilityHelper.getDescriptionForDropOver(
                                            mPendingFolderDragOverView, getContext()));
                        }
                    }
                });
                mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            }
            return;
        }

        mFolderCreationAlarm.cancelAlarm();
        mPendingFolderDragOverView = null;

        boolean willAddToFolder = willAddToExistingUserFolder(info, dragOverView);
        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);
            if (mDragTargetLayout != null) {
                mDragTargetLayout.clearDragOutlines();
            }
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);

            if (dragObject.stateAnnouncer != null) {
                dragObject.stateAnnouncer.announce(WorkspaceAccessibilityHelper
                        .getDescriptionForDropOver(dragOverView, getContext()));
            }
            return;
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE);
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (alarm == wobbleExpireAlarm) {
            if (AbstractFloatingView.hasOpenView(mLauncher, AbstractFloatingView.TYPE_ALL)) {
                wobbleExpireAlarm.setAlarm(WOBBLE_EXPIRATION_TIMEOUT);
                return;
            }
            wobbleLayouts(false);
        }
    }

    @Override
    public void onBackInvoked() {
        if (isWobbling()) {
            wobbleLayouts(false);
        }
    }

    class ReorderAlarmListener implements OnAlarmListener {
        final float[] dragViewCenter;
        final int minSpanX, minSpanY, spanX, spanY;
        final DragObject dragObject;
        final View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                                    int spanY, DragObject dragObject, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragObject = dragObject;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, mDragTargetLayout,
                    mTargetCell);

            mTargetCell = mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);
            DragTrace.log("ReorderAlarmListener.onAlarm targetCell=(" + mTargetCell[0] + "," + mTargetCell[1] + ")");

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                cleanupFolderCreation();
                setDragMode(DRAG_MODE_REORDER);
            }

            mDragTargetLayout.visualizeDropLocation(mTargetCell[0], mTargetCell[1],
                    resultSpan[0], resultSpan[1], dragObject);
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     * <p>
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final CellLayout cellLayout, DragObject d) {
        if (d.dragInfo instanceof PendingAddShortcutInfo) {
            WorkspaceItemInfo si = ((PendingAddShortcutInfo) d.dragInfo)
                    .getActivityInfo(mLauncher).createWorkspaceItemInfo();
            if (si != null) {
                d.dragInfo = si;
            }
        }

        ItemInfo info = d.dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final int container = mLauncher.isHotseatLayout(cellLayout)
                ? LauncherSettings.Favorites.CONTAINER_HOTSEAT
                : LauncherSettings.Favorites.CONTAINER_DESKTOP;
        final int screenId = getCellLayoutId(cellLayout);
        if (!mLauncher.isHotseatLayout(cellLayout)
                && screenId != getScreenIdForPageIndex(mCurrentPage)
                && !mLauncher.isInState(SPRING_LOADED)
                && !mLauncher.isInState(EDIT_MODE)) {
            snapToPage(getPageIndexForScreenId(screenId));
        }

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) info;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
                if (willCreateUserFolder(d.dragInfo, cellLayout, mTargetCell, distance, true)
                        || willAddToExistingUserFolder(
                        d.dragInfo, cellLayout, mTargetCell, distance)) {
                    findNearestVacantCell = false;
                }
            }

            final ItemInfo item = d.dragInfo;
            boolean updateWidgetSize = false;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, info.spanX, info.spanY,
                        null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL);
                relocateOverflowViews(cellLayout, cellLayout.consumeOverflowViews());

                if (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY) {
                    updateWidgetSize = true;
                }
                item.spanX = resultSpan[0];
                item.spanY = resultSpan[1];
            }

            Runnable onAnimationCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    // Normally removeExtraEmptyScreen is called in Workspace#onDrop, but when
                    // adding an item that may not be dropped right away (due to a config activity)
                    // we defer the removal until the activity returns.
                    deferRemoveExtraEmptyScreen();

                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    mLauncher.addPendingItem(pendingInfo, container, screenId, mTargetCell,
                            item.spanX, item.spanY);
                    mStatsLogManager.logger().withItemInfo(d.dragInfo)
                            .withInstanceId(d.logInstanceId)
                            .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
                }
            };
            boolean isWidget = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    || pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

            AppWidgetHostView finalView = isWidget ?
                    ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;

            if (finalView != null && updateWidgetSize) {
                WidgetSizes.updateWidgetSizeRanges(finalView, mLauncher, item.spanX, item.spanY);
            }

            int animationStyle = ANIMATE_INTO_POSITION_AND_DISAPPEAR;
            if (isWidget && ((PendingAddWidgetInfo) pendingInfo).info != null &&
                    ((PendingAddWidgetInfo) pendingInfo).getHandler().needsConfigure()) {
                animationStyle = ANIMATE_INTO_POSITION_AND_REMAIN;
            }
            animateWidgetDrop(info, cellLayout, d.dragView, onAnimationCompleteRunnable,
                    animationStyle, finalView, true);
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            View view = mLauncher.getItemInflater()
                    .inflateItem(info, mLauncher.getModelWriter(), cellLayout);
            d.dragInfo = info = (ItemInfo) view.getTag();

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromWorkspaceCellVisualCenter(
                        mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);
                if (createUserFolderIfNecessary(view, container, cellLayout, mTargetCell, distance,
                        true, d)) {
                    return;
                }
                if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, distance, d,
                        true)) {
                    return;
                }
            }

            CellLayout targetCellLayout = cellLayout;
            int targetScreenId = screenId;
            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = targetCellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
                relocateOverflowViews(targetCellLayout, targetCellLayout.consumeOverflowViews());
            } else {
                targetCellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            if (mTargetCell == null || mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                if (mTargetCell == null) {
                    mTargetCell = new int[2];
                }
                boolean found = false;
                int startIdx = mScreenOrder.indexOf(targetScreenId);
                if (startIdx < 0) startIdx = 0;
                for (int i = startIdx; i < mScreenOrder.size(); i++) {
                    int sid = mScreenOrder.get(i);
                    if (sid == APP_LIBRARY_SCREEN_ID || (mFirstPagePinnedItem != null && sid == FIRST_SCREEN_ID)) {
                        continue;
                    }
                    CellLayout cl = getScreenWithId(sid);
                    if (cl != null && cl.findCellForSpan(mTargetCell, 1, 1)) {
                        targetCellLayout = cl;
                        targetScreenId = sid;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (int i = 0; i < startIdx; i++) {
                        int sid = mScreenOrder.get(i);
                        if (sid == APP_LIBRARY_SCREEN_ID || (mFirstPagePinnedItem != null && sid == FIRST_SCREEN_ID)) {
                            continue;
                        }
                        CellLayout cl = getScreenWithId(sid);
                        if (cl != null && cl.findCellForSpan(mTargetCell, 1, 1)) {
                            targetCellLayout = cl;
                            targetScreenId = sid;
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    IntSet extraEmpty = commitExtraEmptyScreens();
                    if (!extraEmpty.isEmpty()) {
                        int newSid = extraEmpty.getArray().get(0);
                        CellLayout cl = getScreenWithId(newSid);
                        if (cl != null && cl.findCellForSpan(mTargetCell, 1, 1)) {
                            targetCellLayout = cl;
                            targetScreenId = newSid;
                            found = true;
                        }
                    }
                }
                if (!found) {
                    onNoCellFound(targetCellLayout, info, d.logInstanceId);
                    return;
                }
            }
            // Add the item to DB before adding to screen ensures that the container and other
            // values of the info is properly updated.
            mLauncher.getModelWriter().addOrMoveItemInDatabase(info, container, targetScreenId,
                    mTargetCell[0], mTargetCell[1]);

            addInScreen(view, container, targetScreenId, mTargetCell[0], mTargetCell[1],
                    info.spanX, info.spanY);
            targetCellLayout.onDropChild(view);
            targetCellLayout.getShortcutsAndWidgets().measureChild(view);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform();
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view, this);
                resetTransitionTransform();
            }
            mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                    .log(LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED);
        }
    }

    private Drawable createWidgetDrawable(ItemInfo widgetInfo, View layout) {
        int[] unScaledSize = estimateItemSize(widgetInfo);
        int visibility = layout.getVisibility();
        layout.setVisibility(VISIBLE);

        int width = MeasureSpec.makeMeasureSpec(unScaledSize[0], MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(unScaledSize[1], MeasureSpec.EXACTLY);
        layout.measure(width, height);
        layout.layout(0, 0, unScaledSize[0], unScaledSize[1]);
        Bitmap b = BitmapRenderer.createHardwareBitmap(
                unScaledSize[0], unScaledSize[1], layout::draw);
        layout.setVisibility(visibility);
        return new FastBitmapDrawable(b);
    }

    private void getFinalPositionForDropAnimation(int[] loc, float[] scaleXY,
            DragView dragView, CellLayout layout, ItemInfo info, int[] targetCell, boolean scale,
            final View finalView) {
        // Now we animate the dragView, (ie. the widget or shortcut preview) into its final
        // location and size on the home screen.
        int spanX = info.spanX;
        int spanY = info.spanY;

        Rect r = estimateItemPosition(layout, targetCell[0], targetCell[1], spanX, spanY);
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {
            DeviceProfile profile = mLauncher.getDeviceProfile();
            if (finalView instanceof NavigableAppWidgetHostView) {
                Rect widgetPadding = profile.widgetPadding;
                r.left -= widgetPadding.left;
                r.right += widgetPadding.right;
                r.top -= widgetPadding.top;
                r.bottom += widgetPadding.bottom;
            }
            PointF appWidgetScale = profile.getAppWidgetScale(null);
            Utilities.shrinkRect(r, appWidgetScale.x, appWidgetScale.y);
        }

        mTempFXY[0] = r.left;
        mTempFXY[1] = r.top;
        setFinalTransitionTransform();
        float cellLayoutScale =
                mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(layout, mTempFXY, true);
        resetTransitionTransform();
        Utilities.roundArray(mTempFXY, loc);

        if (scale) {
            float dragViewScaleX = (1.0f * r.width()) / dragView.getMeasuredWidth();
            float dragViewScaleY = (1.0f * r.height()) / dragView.getMeasuredHeight();

            // The animation will scale the dragView about its center, so we need to center about
            // the final location.
            loc[0] -= (dragView.getMeasuredWidth() - cellLayoutScale * r.width()) / 2
                    - Math.ceil(layout.getUnusedHorizontalSpace() / 2f);
            loc[1] -= (dragView.getMeasuredHeight() - cellLayoutScale * r.height()) / 2;
            scaleXY[0] = dragViewScaleX * cellLayoutScale;
            scaleXY[1] = dragViewScaleY * cellLayoutScale;
        } else {
            // Since we are not cross-fading the dragView, align the drag view to the
            // final cell position.
            float dragScale = dragView.getInitialScale() * cellLayoutScale;
            loc[0] += (dragScale - 1) * dragView.getWidth() / 2;
            loc[1] += (dragScale - 1) * dragView.getHeight() / 2;
            scaleXY[0] = scaleXY[1] = dragScale;

            // If a dragRegion was provided, offset the final position accordingly.
            Rect dragRegion = dragView.getDragRegion();
            if (dragRegion != null) {
                loc[0] += cellLayoutScale * dragRegion.left;
                loc[1] += cellLayoutScale * dragRegion.top;
            }
        }
    }

    public void animateWidgetDrop(ItemInfo info, CellLayout cellLayout, final DragView dragView,
            final Runnable onCompleteRunnable, int animationType, @Nullable final View finalView,
            boolean external) {
        int[] finalPos = new int[2];
        float scaleXY[] = new float[2];
        boolean scalePreview = !(info instanceof PendingAddShortcutInfo);
        getFinalPositionForDropAnimation(finalPos, scaleXY, dragView, cellLayout, info, mTargetCell,
                scalePreview, finalView);

        Resources res = mLauncher.getResources();
        final int duration = res.getInteger(R.integer.config_dropAnimMaxDuration) - 200;

        boolean isWidget = info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET ||
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        if ((animationType == ANIMATE_INTO_POSITION_AND_RESIZE || external)
                && finalView != null
                && dragView.getContentView() != finalView) {
            Drawable crossFadeDrawable = createWidgetDrawable(info, finalView);
            dragView.crossFadeContent(crossFadeDrawable, (int) (duration * 0.8f));
        } else if (isWidget && external) {
            scaleXY[0] = scaleXY[1] = Math.min(scaleXY[0], scaleXY[1]);
        }

        DragLayer dragLayer = mLauncher.getDragLayer();
        if (animationType == CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION) {
            mLauncher.getDragLayer().animateViewIntoPosition(dragView, finalPos, 0f, 0.1f, 0.1f,
                    DragLayer.ANIMATION_END_DISAPPEAR, onCompleteRunnable, duration);
        } else {
            int endStyle;
            if (animationType == ANIMATE_INTO_POSITION_AND_REMAIN) {
                endStyle = DragLayer.ANIMATION_END_REMAIN_VISIBLE;
            } else {
                endStyle = DragLayer.ANIMATION_END_DISAPPEAR;
            }

            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    if (finalView != null) {
                        finalView.setVisibility(VISIBLE);
                    }
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                }
            };
            dragLayer.animateViewIntoPosition(dragView, finalPos[0],
                    finalPos[1], 1, scaleXY[0], scaleXY[1], onComplete, endStyle,
                    duration, this);
        }
    }

    public void setFinalTransitionTransform() {
        if (isSwitchingState()) {
            mCurrentScale = getScaleX();
            setScaleX(mStateTransitionAnimation.getFinalScale());
            setScaleY(mStateTransitionAnimation.getFinalScale());
        }
    }

    public void resetTransitionTransform() {
        if (isSwitchingState()) {
            setScaleX(mCurrentScale);
            setScaleY(mCurrentScale);
        }
    }

    /**
     * Return the current CellInfo describing our current drag; this method exists
     * so that Launcher can sync this object with the correct info when the activity is created/
     * destroyed
     */
    public CellInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     * <p>
     * pixelX and pixelY should be in the coordinate system of layout
     */
    @Thunk
    int[] findNearestArea(int pixelX, int pixelY,
                          int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestAreaIgnoreOccupied(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    void setup(DragController dragController) {
        mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled();
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(final View target, final DragObject d,
                                final boolean success) {
        if (success) {
            if (target != this && mDragInfo != null) {
                removeWorkspaceItem(mDragInfo.cell);
            }
        } else if (mDragInfo != null) {
            // When drag is cancelled, reattach content view back to its original parent.
            if (mDragInfo.cell instanceof LauncherAppWidgetHostView && d.dragView != null) {
                d.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                mDragInfo.cell.setVisibility(VISIBLE);
            }
            final CellLayout cellLayout = mLauncher.getCellLayout(
                    mDragInfo.container, mDragInfo.screenId);
            if (cellLayout != null) {
                cellLayout.onDropChild(mDragInfo.cell);
            } else if (FeatureFlags.IS_STUDIO_BUILD) {
                throw new RuntimeException("Invalid state: cellLayout == null in "
                        + "Workspace#onDropCompleted. Please file a bug. ");
            }
        }
        View cell = getHomescreenIconByItemId(d.originalDragInfo.id);
        if (d.cancelled && cell != null) {
            cell.setVisibility(VISIBLE);
        }
        if (d.cancelled && mDragInfo != null && mDragInfo.cell != null) {
            mDragInfo.cell.setVisibility(VISIBLE);
        }
        mDragInfo = null;
    }

    private void clearEmptyCell() {
        // Let Hotseat readjust as well
        needCellCleanup(getHotseat());

        int numberOfScreens = mScreenOrder.size();
        for (int i = 0; i < numberOfScreens; i++) {
            int screenId = mScreenOrder.get(i);
            if (screenId == FIRST_SCREEN_ID && mFirstPagePinnedItem != null) {
                continue;
            }
            if (screenId == APP_LIBRARY_SCREEN_ID) {
                continue;
            }
            CellLayout cellLayout = mWorkspaceScreens.get(screenId);
            needCellCleanup(cellLayout);
        }
    }

    public void needCellCleanup(CellLayout cellLayout) {
        // Disabled: do not auto-compact icons in reading order.
        // Users can place apps anywhere on the grid (iOS 18 freeform grid placement).
    }

    /**
     * For opposite operation. See {@link #addInScreen}.
     */
    public void removeWorkspaceItem(View v) {
        CellLayout parentCell = getParentCellLayoutForView(v);
        if (parentCell != null) {
            parentCell.removeView(v);
        } else if (FeatureFlags.IS_STUDIO_BUILD) {
            // When an app is uninstalled using the drop target, we wait until resume to remove
            // the icon. We also remove all the corresponding items from the workspace at
            // {@link Launcher#bindComponentsRemoved}. That call can come before or after
            // {@link Launcher#mOnResumeCallbacks} depending on how busy the worker thread is.
            Log.e(TAG, "mDragInfo.cell has null parent");
        }
        if (v instanceof DropTarget) {
            mDragController.removeDropTarget((DropTarget) v);
        }
    }

    /**
     * Removed widget from workspace by appWidgetId
     *
     * @param appWidgetId
     */
    public void removeWidget(int appWidgetId) {
        mapOverItems((info, view) -> {
            if (info instanceof LauncherAppWidgetInfo) {
                LauncherAppWidgetInfo appWidgetInfo = (LauncherAppWidgetInfo) info;
                if (appWidgetInfo.appWidgetId == appWidgetId) {
                    mLauncher.removeItem(view, appWidgetInfo, true,
                            "widget is removed in response to widget remove broadcast");
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Removes all folder listeners
     */
    public void removeFolderListeners() {
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                if (view instanceof FolderIcon) {
                    ((FolderIcon) view).removeListeners();
                }
                return false;
            }
        });
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // We don't dispatch restoreInstanceState to our children using this code path.
        // Some pages will be restored immediately as their items are bound immediately, and
        // others we will need to wait until after their items are bound.
        mSavedStates = container;
    }

    public void restoreInstanceStateForChild(int child) {
        if (mSavedStates != null) {
            mRestoredPages.add(child);
            CellLayout cl = (CellLayout) getChildAt(child);
            if (cl != null) {
                cl.restoreInstanceState(mSavedStates);
            }
        }
    }

    public void restoreInstanceStateForRemainingPages() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            if (!mRestoredPages.contains(i)) {
                restoreInstanceStateForChild(i);
            }
        }
        mRestoredPages.clear();
        mSavedStates = null;
    }

    @Override
    public boolean scrollLeft() {
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollLeft();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
        return result;
    }

    @Override
    public boolean scrollRight() {
        if (mDragController != null && mDragController.isDragging() && mAppLibraryPinnedItem != null) {
            int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
            if (appLibIndex >= 0 && getNextPage() >= appLibIndex - 1) {
                return false;
            }
        }
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollRight();
        }
        Folder openFolder = Folder.getOpen(mLauncher);
        if (openFolder != null) {
            openFolder.completeDragExit();
        }
        return result;
    }

    /**
     * Returns a specific CellLayout
     */
    CellLayout getParentCellLayoutForView(View v) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            if (layout.getShortcutsAndWidgets().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Returns a list of all the CellLayouts on the Homescreen.
     */
    private CellLayout[] getWorkspaceAndHotseatCellLayouts() {
        int screenCount = getChildCount();
        final CellLayout[] layouts;
        if (mLauncher.getHotseat() != null) {
            layouts = new CellLayout[screenCount + 1];
            layouts[screenCount] = mLauncher.getHotseat();
        } else {
            layouts = new CellLayout[screenCount];
        }
        for (int screen = 0; screen < screenCount; screen++) {
            layouts[screen] = (CellLayout) getChildAt(screen);
        }
        return layouts;
    }

    public View getHomescreenIconByItemId(final int id) {
        return getFirstMatch((info, v) -> info != null && info.id == id);
    }

    public LauncherAppWidgetHostView getWidgetForAppWidgetId(final int appWidgetId) {
        return (LauncherAppWidgetHostView) getFirstMatch((info, v) ->
                (info instanceof LauncherAppWidgetInfo) &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == appWidgetId);
    }

    public View getFirstMatch(final ItemOperator operator) {
        final View[] value = new View[1];
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (operator.evaluate(info, v)) {
                    value[0] = v;
                    return true;
                }
                return false;
            }
        });
        return value[0];
    }

    void clearDropTargets() {
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
                // not done, process all the shortcuts
                return false;
            }
        });
    }

    /**
     * Removes items that match the {@param matcher}. When applications are removed
     * as a part of an update, this is called to ensure that other widgets and application
     * shortcuts are not removed.
     */
    public void removeItemsByMatcher(final Predicate<ItemInfo> matcher) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            ShortcutAndWidgetContainer container = layout.getShortcutsAndWidgets();
            // Iterate in reverse order as we are removing items
            for (int i = container.getChildCount() - 1; i >= 0; i--) {
                View child = container.getChildAt(i);
                ItemInfo info = (ItemInfo) child.getTag();

                if (matcher.test(info)) {
                    if (isWobbling()) {
                        child.clearAnimation();
                    }
                    layout.removeViewInLayout(child);
                    if (child instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) child);
                    }
                } else if (child instanceof FolderIcon) {
                    FolderInfo folderInfo = (FolderInfo) info;
                    List<WorkspaceItemInfo> matches = folderInfo.contents.stream()
                            .filter(matcher)
                            .collect(Collectors.toList());
                    if (!matches.isEmpty()) {
                        folderInfo.removeAll(matches, false);
                        Folder folder = ((FolderIcon) child).getFolder();
                        if (!(folder instanceof GridFolder) && folder.isOpen()) {
                            folder.close(false /* animate */);
                        }
                    }
                }
            }
        }

        // Strip all the empty screens
        stripEmptyScreens();
    }

    @Override
    public void mapOverItems(ItemOperator op) {
        for (CellLayout layout : getWorkspaceAndHotseatCellLayouts()) {
            if (mapOverCellLayout(layout, op) != null) {
                return;
            }
        }
    }

    /**
     * Perform {param operator} over all the items in a given {param layout}.
     *
     * @return The first item that satisfies the operator or null.
     */
    public View mapOverCellLayout(CellLayout layout, ItemOperator operator) {
        // TODO(b/128460496) Potential race condition where layout is not yet loaded
        if (layout == null) {
            return null;
        }
        ShortcutAndWidgetContainer container = layout.getShortcutsAndWidgets();
        // map over all the shortcuts on the workspace
        final int itemCount = container.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = container.getChildAt(itemIdx);
            if (operator.evaluate((ItemInfo) item.getTag(), item)) {
                return item;
            }
        }
        return null;
    }

    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        Predicate<ItemInfo> matcher = info -> !packageUserKey.updateFromItemInfo(info)
                || updatedDots.test(packageUserKey);

        ItemOperator op = (info, v) -> {
            if (info instanceof WorkspaceItemInfo && v instanceof BubbleTextView) {
                if (matcher.test(info)) {
                    ((BubbleTextView) v).applyDotState(info, true /* animate */);
                }
            } else if (info instanceof FolderInfo && v instanceof FolderIcon) {
                FolderInfo fi = (FolderInfo) info;
                if (fi.contents.stream().anyMatch(matcher)) {
                    FolderDotInfo folderDotInfo = new FolderDotInfo();
                    for (WorkspaceItemInfo si : fi.contents) {
                        folderDotInfo.addDotInfo(mLauncher.getDotInfoForItem(si));
                    }
                    ((FolderIcon) v).setDotInfo(folderDotInfo);
                }
            }

            // process all the shortcuts
            return false;
        };

        mapOverItems(op);
        Folder folder = Folder.getOpen(mLauncher);
        if (folder != null) {
            folder.iterateOverItems(op);
        }
        com.android.launcher3.applibrary.view.AppLibraryPage alp = getAppLibraryPage();
        if (alp != null) {
            alp.updateNotificationDots(updatedDots);
        }
    }

    /**
     * Remove workspace icons & widget information related to items in matcher.
     *
     * @param matcher the matcher generated by the caller.
     */
    public void persistRemoveItemsByMatcher(Predicate<ItemInfo> matcher,
                                            @Nullable final String reason) {
        mLauncher.getModelWriter().deleteItemsFromDatabase(matcher, reason);
        removeItemsByMatcher(matcher);
    }

    public void widgetsRestored(final ArrayList<LauncherAppWidgetInfo> changedInfo) {
        if (!changedInfo.isEmpty()) {
            DeferredWidgetRefresh widgetRefresh = new DeferredWidgetRefresh(changedInfo,
                    mLauncher.getAppWidgetHolder());

            LauncherAppWidgetInfo item = changedInfo.get(0);
            final AppWidgetProviderInfo widgetInfo;
            WidgetManagerHelper widgetHelper = new WidgetManagerHelper(getContext());
            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                widgetInfo = widgetHelper.findProvider(item.providerName, item.user);
            } else {
                widgetInfo = widgetHelper.getLauncherAppWidgetInfo(item.appWidgetId,
                        item.getTargetComponent());
            }

            if (widgetInfo != null) {
                // Re-inflate the widgets which have changed status
                widgetRefresh.run();
            } else {
                // widgetRefresh will automatically run when the packages are updated.
                // For now just update the progress bars
                mapOverItems(new ItemOperator() {
                    @Override
                    public boolean evaluate(ItemInfo info, View view) {
                        if (view instanceof PendingAppWidgetHostView
                                && changedInfo.contains(info)) {
                            ((LauncherAppWidgetInfo) info).installProgress = 100;
                            ((PendingAppWidgetHostView) view).applyState();
                        }
                        // process all the shortcuts
                        return false;
                    }
                });
            }
        }
    }

    public boolean isOverlayShown() {
        return mOverlayShown;
    }

    /**
     * Calls {@link #snapToPage(int)} on the {@link #getDefaultPage()}, then requests focus on it.
     */
    public void moveToDefaultScreen() {
        int page = getDefaultPage();
        if (!workspaceInModalState() && getNextPage() != page) {
            snapToPage(page);
        }
        View child = getChildAt(page);
        if (child != null) {
            child.requestFocus();
        }
    }

    public int getDefaultPage() {
        return 0;
    }

    /**
     * Set the given view's pivot point to match the workspace's, so that it scales together. Since
     * both this view and workspace can move, transform the point manually instead of using
     * dragLayer.getDescendantCoordRelativeToSelf and related methods.
     */
    public void setPivotToScaleWithSelf(View sibling) {
        sibling.setPivotY(getPivotY() + getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(getPivotX() + getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }

    @Override
    public int getExpectedHeight() {
        return getMeasuredHeight() <= 0 || !mIsLayoutValid
                ? mLauncher.getDeviceProfile().heightPx : getMeasuredHeight();
    }

    @Override
    public int getExpectedWidth() {
        return getMeasuredWidth() <= 0 || !mIsLayoutValid
                ? mLauncher.getDeviceProfile().widthPx : getMeasuredWidth();
    }

    @Override
    protected boolean canAnnouncePageDescription() {
        // Disable announcements while overscrolling potentially to overlay screen because if we end
        // up on the overlay screen, it will take care of announcing itself.
        return Float.compare(mOverlayProgress, 0f) == 0;
    }

    @Override
    protected String getCurrentPageDescription() {
        int pageIndex = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        return getPageDescription(pageIndex);
    }

    /**
     * @param page page index.
     * @return Description of the page at the given page index.
     */
    @Override
    public String getPageDescription(int page) {
        int nScreens = getChildCount();
        int extraScreenId = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (extraScreenId >= 0 && nScreens > 1) {
            if (page == extraScreenId) {
                return getContext().getString(R.string.workspace_new_page);
            }
            nScreens--;
        }
        if (nScreens == 0) {
            // When the workspace is not loaded, we do not know how many screen will be bound.
            return getContext().getString(R.string.home_screen);
        }
        int panelCount = getPanelCount();
        int currentPage = (page / panelCount) + 1;
        int totalPages = nScreens / panelCount + nScreens % panelCount;
        return getContext().getString(R.string.workspace_scroll_format, currentPage, totalPages);
    }

    @Override
    protected boolean isSignificantMove(float absoluteDelta, int pageOrientedSize) {
        if (mCurrentPage == getPageCount() - 1 && mAppLibraryPinnedItem != null) {
            // Make it much easier to dismiss the App Library to match iOS behavior
            return absoluteDelta > pageOrientedSize * 0.15f;
        }

        DeviceProfile deviceProfile = mLauncher.getDeviceProfile();
        if (!deviceProfile.isTablet) {
            return super.isSignificantMove(absoluteDelta, pageOrientedSize);
        }

        return absoluteDelta
                > deviceProfile.availableWidthPx * SIGNIFICANT_MOVE_SCREEN_WIDTH_PERCENTAGE;
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return mLauncher.getCellPosMapper();
    }

    public boolean isWobbling() {
        return isWobbling;
    }


    public void wobbleLayouts(boolean wobble) {
        wobbleLayouts(wobble, false);
    }
    public void wobbleLayouts(boolean wobble, boolean excludeDraggingView) {
        if (!MultiModeController.isSingleLayerMode()) return;
        if (wobble && isAppLibraryCurrentPage()) return;

        isWobbling = wobble;
        if (wobble) {
            AtomicInteger index = new AtomicInteger();

            mapOverItems((info, view) -> {
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(true);
                } else if (view instanceof LauncherAppWidgetHostView) {
                    ((LauncherAppWidgetHostView) view).applyUninstallIconState(true);
                }

                view.setOnTouchListener(ItemLongClickListener.INSTANCE_WORKSPACE_WOBBLE);

                if (excludeDraggingView && mDragObjectInfo != null) {
                    if ((mDragObjectInfo instanceof WorkspaceItemInfo ||
                            mDragObjectInfo instanceof FolderInfo)
                            && mDragObjectInfo.equals(view.getTag())) {
                        return false;
                    }
                }
                index.getAndIncrement();
                if (view instanceof LauncherAppWidgetHostView) {
                    boolean reverse = (index.get() % 2 != 0);
                    view.startAnimation(getWidgetWobbleAnimation((LauncherAppWidgetHostView) view, info, reverse));
                } else if (view instanceof BubbleTextView || view instanceof FolderIcon) {
                    if (index.get() % 2 == 0) {
                        view.startAnimation(getWobbleAnimation());
                    } else {
                        view.startAnimation(getReverseWobbleAnimation());
                    }
                }
                return false;
            });
            wobbleExpireAlarm.setAlarm(WOBBLE_EXPIRATION_TIMEOUT);
            Folder openFolder = Folder.getOpen(mLauncher);
            if (openFolder instanceof GridFolder) {
                ((GridFolder) openFolder).wobbleFolder(true, excludeDraggingView);
            }
            View topBar = mLauncher.findViewById(R.id.jiggle_top_bar);
            if (topBar instanceof foundation.e.bliss.wobble.JiggleTopBarView) {
                ((foundation.e.bliss.wobble.JiggleTopBarView) topBar).show();
            }
            float wobbleY = getResources().getDisplayMetrics().density * 32f;
            animate().translationY(wobbleY)
                    .setDuration(250)
                    .setInterpolator(com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE)
                    .start();
            if (mAppLibraryCellLayout != null) {
                mAppLibraryCellLayout.animate().translationY(-wobbleY)
                        .setDuration(250)
                        .setInterpolator(com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE)
                        .start();
            }
        } else {
            wobbleExpireAlarm.cancelAlarm();
            View topBar = mLauncher.findViewById(R.id.jiggle_top_bar);
            if (topBar instanceof foundation.e.bliss.wobble.JiggleTopBarView) {
                ((foundation.e.bliss.wobble.JiggleTopBarView) topBar).hide();
            }
            animate().translationY(0f)
                    .setDuration(200)
                    .setInterpolator(com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE)
                    .start();
            if (mAppLibraryCellLayout != null) {
                mAppLibraryCellLayout.animate().translationY(0f)
                        .setDuration(200)
                        .setInterpolator(com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE)
                        .start();
            }
            Folder openFolder = Folder.getOpen(mLauncher);
            if (openFolder != null && openFolder.isOpen()) {
                AbstractFloatingView.closeAllOpenViewsExcept(mLauncher, true, AbstractFloatingView.TYPE_FOLDER);
            } else {
                AbstractFloatingView.closeAllOpenViews(mLauncher);
            }
            mapOverItems((info, view) -> {
                view.setOnTouchListener(null);
                view.clearAnimation();
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(false);
                } else if (view instanceof LauncherAppWidgetHostView) {
                    ((LauncherAppWidgetHostView) view).applyUninstallIconState(false);
                } else if (view instanceof FolderIcon) {
                    Folder folder = ((FolderIcon) view).getFolder();
                    if (folder instanceof GridFolder && ((GridFolder) folder).isFolderWobbling()) {
                        ((GridFolder) folder).wobbleFolder(false);
                    }
                }
                return false;
            });
        }
        mLauncher.updateDisallowBack();
    }

    public void resetWobbleAlarm() {
        if (isWobbling) {
            wobbleExpireAlarm.setAlarm(WOBBLE_EXPIRATION_TIMEOUT);
        }
    }

    public void pauseWobbleAlarm() {
        if (isWobbling) {
            wobbleExpireAlarm.cancelAlarm();
        }
    }

    public ItemInfo getDragObjectInfo() {
        return mDragObjectInfo;
    }

    public Animation getWobbleAnimation() {
        return mWobbleAnimation;
    }

    public Animation getReverseWobbleAnimation() {
        return mReverseWobbleAnimation;
    }

    public Animation getWidgetWobbleAnimation(LauncherAppWidgetHostView widgetView, ItemInfo info, boolean reverse) {
        int span = 2;
        if (info != null) {
            span = Math.max(info.spanX, info.spanY);
        } else if (widgetView.getWidth() > 0) {
            int iconSize = mLauncher.getDeviceProfile().iconSizePx;
            span = Math.max(2, Math.max(widgetView.getWidth(), widgetView.getHeight()) / Math.max(1, iconSize));
        }
        // Damped rotation angle inversely proportional to widget size:
        // Small widgets (span 2): ~0.8 deg
        // Large widgets (span 4, e.g. Calendar): ~0.4 deg (prevents excessive corner excursions)
        float degrees = Math.max(0.35f, 1.6f / Math.max(2, span));
        long duration = 135; // slightly slower cadence for a natural weighted feel

        float from = reverse ? degrees : -degrees;
        float to = reverse ? -degrees : degrees;

        RotateAnimation anim = new RotateAnimation(
                from, to,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        anim.setDuration(duration);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        return anim;
    }

    @Override
    public void addInScreen(View child, int container, int screenId, int x, int y, int spanX, int spanY) {
        if (container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                && !EXTRA_EMPTY_SCREEN_IDS.contains(screenId)
                && screenId != APP_LIBRARY_SCREEN_ID
                && getScreenWithId(screenId) == null) {
            // The screen this item wants doesn't exist in memory yet (e.g. FIRST_SCREEN_ID
            // was skipped during bindWorkspace, or the DB and the live workspace briefly
            // disagree after a folder move). Create it instead of dropping the item — silently
            // discarding items here is what leaves mScreenOrder without a real page, which is
            // what crashes convertFinalScreenToEmptyScreenIfNecessary() afterward.
            insertNewWorkspaceScreenBeforeEmptyScreen(screenId);
        }
        WorkspaceLayoutManager.super.addInScreen(child, container, screenId, x, y, spanX, spanY);
        if (isWobbling()) {
            wobbleSingleChild(child);
        }
    }

    public void wobbleSingleChild(View view) {
        if (!MultiModeController.isSingleLayerMode() || !isWobbling || view == null) return;
        if (view instanceof BubbleTextView) {
            ((BubbleTextView) view).applyUninstallIconState(true);
        } else if (view instanceof LauncherAppWidgetHostView) {
            ((LauncherAppWidgetHostView) view).applyUninstallIconState(true);
        }
        view.setOnTouchListener(ItemLongClickListener.INSTANCE_WORKSPACE_WOBBLE);
        if (view instanceof LauncherAppWidgetHostView) {
            ItemInfo info = (ItemInfo) view.getTag();
            view.startAnimation(getWidgetWobbleAnimation((LauncherAppWidgetHostView) view, info, false));
        } else if (view instanceof BubbleTextView || view instanceof FolderIcon) {
            view.startAnimation(getWobbleAnimation());
        }
    }

    /**
     * Used as a workaround to ensure that the AppWidgetService receives the
     * PACKAGE_ADDED broadcast before updating widgets.
     */
    private class DeferredWidgetRefresh implements Runnable, ProviderChangedListener {
        private final ArrayList<LauncherAppWidgetInfo> mInfos;
        private final LauncherWidgetHolder mWidgetHolder;
        private final Handler mHandler;

        private boolean mRefreshPending;

        DeferredWidgetRefresh(ArrayList<LauncherAppWidgetInfo> infos,
                              LauncherWidgetHolder holder) {
            mInfos = infos;
            mWidgetHolder = holder;
            mHandler = mLauncher.mHandler;
            mRefreshPending = true;

            mWidgetHolder.addProviderChangeListener(this);
            // Force refresh after 10 seconds, if we don't get the provider changed event.
            // This could happen when the provider is no longer available in the app.
            Message msg = Message.obtain(mHandler, this);
            msg.obj = DeferredWidgetRefresh.class;
            mHandler.sendMessageDelayed(msg, 10000);
        }

        @Override
        public void run() {
            mWidgetHolder.removeProviderChangeListener(this);
            mHandler.removeCallbacks(this);

            if (!mRefreshPending) {
                return;
            }

            mRefreshPending = false;

            ArrayList<PendingAppWidgetHostView> views = new ArrayList<>(mInfos.size());
            mapOverItems((info, view) -> {
                if (view instanceof PendingAppWidgetHostView && mInfos.contains(info)) {
                    views.add((PendingAppWidgetHostView) view);
                }
                // process all children
                return false;
            });
            for (PendingAppWidgetHostView view : views) {
                view.reInflate();
            }
        }

        @Override
        public void notifyWidgetProvidersChanged() {
            run();
        }
    }

    private class StateTransitionListener extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator anim) {
            mTransitionProgress = anim.getAnimatedFraction();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            onStartStateTransition();
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onEndStateTransition();
        }
    }

    @Override
    protected int validateNewPage(int newPage) {
        int maxPage = getChildCount() - 1;
        newPage = Utilities.boundToRange(newPage, 0, maxPage);
        return newPage;
    }

    @Override
    protected int getDestinationPage(int primaryScroll) {
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        final int pageCount = getChildCount();
        int appLibIndex = mScreenOrder.indexOf(APP_LIBRARY_SCREEN_ID);
        
        for (int i = 0; i < pageCount; ++i) {
            int distanceFromScreenCenter = Math.abs(
                    getDisplacementFromScreenCenterForScroll(i, primaryScroll));
                    
            if (appLibIndex >= 0 && i == appLibIndex - 1 && mCurrentPage == appLibIndex) {
                // When we are currently on the App Library, heavily bias the Home Screen
                // so that a gentle swipe easily returns to the Home Screen to match iOS.
                distanceFromScreenCenter = (int) (distanceFromScreenCenter * 0.2f);
            }

            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    private int getDisplacementFromScreenCenterForScroll(int childIndex, int primaryScroll) {
        int screenCenter = getScreenCenter(primaryScroll);
        int childSize = getChildVisibleSize(childIndex);
        int halfChildSize = (childSize / 2);
        int childCenter = getChildOffset(childIndex) + halfChildSize;
        return childCenter - screenCenter;
    }

    public void dumpFullWorkspaceState(String reason) {
        if (!DEBUG_DRAG_LOG) return;
        android.util.Log.d("WORKSPACE_TRACE", "=== DUMP WORKSPACE STATE [" + reason + "] ===");
        android.util.Log.d("WORKSPACE_TRACE", "Workspace alpha=" + getAlpha() + " visibility=" + getVisibility() + " scaleX=" + getScaleX() + " transX=" + getTranslationX() + " isWobbling=" + isWobbling());
        int pageCount = getChildCount();
        for (int i = 0; i < pageCount; i++) {
            View page = getChildAt(i);
            if (page instanceof CellLayout) {
                CellLayout cellLayout = (CellLayout) page;
                int screenId = getScreenIdForPageIndex(i);
                android.util.Log.d("WORKSPACE_TRACE", "  Page " + i + " (screenId=" + screenId + "): CellLayout alpha=" + cellLayout.getAlpha() + " vis=" + cellLayout.getVisibility() + " scaleX=" + cellLayout.getScaleX() + " transX=" + cellLayout.getTranslationX() + " layerType=" + cellLayout.getLayerType());
                ShortcutAndWidgetContainer container = cellLayout.getShortcutsAndWidgets();
                if (container != null) {
                    int childCount = container.getChildCount();
                    android.util.Log.d("WORKSPACE_TRACE", "    ShortcutAndWidgetContainer count=" + childCount + " alpha=" + container.getAlpha() + " vis=" + container.getVisibility() + " scaleX=" + container.getScaleX() + " layerType=" + container.getLayerType());
                    for (int j = 0; j < childCount; j++) {
                        View child = container.getChildAt(j);
                        Object tag = child.getTag();
                        String itemTitle = (tag instanceof ItemInfo) ? String.valueOf(((ItemInfo) tag).title) : "no-item";
                        int itemId = (tag instanceof ItemInfo) ? ((ItemInfo) tag).id : -1;
                        android.util.Log.d("WORKSPACE_TRACE", "      Item [" + j + "]: " + child.getClass().getSimpleName() + " (id=" + itemId + " title=" + itemTitle + ") vis=" + child.getVisibility() + " alpha=" + child.getAlpha() + " scaleX=" + child.getScaleX() + " scaleY=" + child.getScaleY() + " transX=" + child.getTranslationX() + " layerType=" + child.getLayerType() + " bounds=(" + child.getLeft() + "," + child.getTop() + "-" + child.getRight() + "," + child.getBottom() + ")");
                    }
                }
            }
        }
        android.util.Log.d("WORKSPACE_TRACE", "=================================================");
    }
}
