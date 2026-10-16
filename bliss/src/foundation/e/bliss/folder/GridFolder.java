/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.folder;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.pageindicators.PageIndicatorDots;

import java.util.concurrent.atomic.AtomicInteger;

import foundation.e.bliss.blur.BlurBackgroundView;

public class GridFolder extends Folder implements OnAlarmListener {
    private static final int MIN_CONTENT_DIMEN = 5;
    private final Launcher mLauncher;
    private final Alarm wobbleExpireAlarm = new Alarm();
    public View mFolderTab;
    public int mFolderTabHeight;
    public View mGlassView;
    public FrameLayout mGridFolderPage;
    private LauncherState mLastStateBeforeOpen = NORMAL;
    private boolean mNeedResetState = false;

    private boolean isFolderWobbling = false;
    private boolean isAnimating = false;

    public GridFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = mLauncherDelegate.getLauncher();
        wobbleExpireAlarm.setOnAlarmListener(this);
    }

    @SuppressLint("InflateParams")
    public static <T extends Context & ActivityContext> Folder fromXml(T activityContext) {
        return (Folder) LayoutInflater.from(activityContext).cloneInContext(activityContext)
                .inflate(R.layout.grid_folder_icon_normalized, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGridFolderPage = findViewById(R.id.grid_folder_page);

        mFolderTab = findViewById(R.id.folder_tab);
        mFolderName.setSelectAllOnFocus(false);
        mFolderName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        mFooterHeight = getResources().getDimensionPixelSize(R.dimen.grid_folder_footer_height);

        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int cellIconGap = (grid.folderCellWidthPx - mActivityContext.getDeviceProfile().iconSizePx);
        mContent.setPadding(cellIconGap, cellIconGap, cellIconGap, cellIconGap);

        int measureSpec = MeasureSpec.UNSPECIFIED;
        mFolderTab.measure(measureSpec, measureSpec);
        mFolderTabHeight = mFolderTab.getMeasuredHeight();

        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            android.graphics.drawable.Drawable bg = mGridFolderPage.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) bg
                        .mutate();
                gd.setColor(android.graphics.Color.TRANSPARENT);
            }

            mGlassView = new foundation.e.bliss.glass.FolderOpenGlassView(getContext());
            mGlassView.setVisibility(VISIBLE);

            if (mGlassView != null) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                mGridFolderPage.addView(mGlassView, 0, lp);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentWidth = getContentAreaWidth();
        int contentHeight = getContentAreaHeight();

        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);
        int contentAreaHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);

        mContent.setFixedSize(contentWidth, contentHeight);
        mContent.measure(contentAreaWidthSpec, contentAreaHeightSpec);

        mFolderTab.measure(contentAreaWidthSpec, MeasureSpec.makeMeasureSpec(mFolderTabHeight, MeasureSpec.EXACTLY));

        mGridFolderPage.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(contentHeight + mFooterHeight, MeasureSpec.EXACTLY));

        mFooter.measure(contentAreaWidthSpec, MeasureSpec.makeMeasureSpec(mFooterHeight, MeasureSpec.EXACTLY));

        int folderWidth = getFolderWidth();
        int folderHeight = getFolderHeight();
        setMeasuredDimension(folderWidth, folderHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    public Drawable getBackground() {
        return mGridFolderPage.getBackground();
    }

    private int getContentAreaWidth() {
        return Math.max(mContent.getDesiredWidth(), MIN_CONTENT_DIMEN);
    }

    protected int getContentAreaHeight() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int maxContentAreaHeight = grid.availableHeightPx - grid.getTotalWorkspacePadding().y
                + (grid.isVerticalBarLayout() ? 0 : grid.hotseatBarSizePx);
        int height = Math.min(maxContentAreaHeight, mContent.getDesiredHeight());
        return Math.max(height, MIN_CONTENT_DIMEN);
    }

    private int getFolderWidth() {
        return getPaddingLeft() + getPaddingRight() + mContent.getDesiredWidth();
    }

    private int getFolderHeight() {
        return mFolderTabHeight + mContent.getDesiredHeight() + mFooterHeight + getPaddingTop() + getPaddingBottom();
    }

    @Override
    protected void centerAboutIcon() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();

        int width = getFolderWidth();
        int height = getFolderHeight();
        int insetsTop = grid.getInsets().top;

        lp.width = width;
        lp.height = height;
        lp.x = (grid.availableWidthPx - width) / 2;
        if (grid.isVerticalBarLayout()) {
            lp.y = grid.getTotalWorkspacePadding().y / 2 + insetsTop;
        } else {
            int minTopHeight = insetsTop + grid.dropTargetBarSizePx;
            lp.y = Math.max((grid.availableHeightPx - height) / 2, minTopHeight);
        }
    }

    public void setNeedResetState(boolean isReset) {
        mNeedResetState = isReset;
    }

    private boolean isCanRestoredState(LauncherState state) {
        return state == NORMAL || state == OVERVIEW;
    }

    @Override
    public void onFolderOpenStart() {
        mLastStateBeforeOpen = mLauncher.getStateManager().getState();
        if (!mLauncher.isInState(NORMAL)) {
            mLauncher.getStateManager().goToState(LauncherState.NORMAL, false);
            if (isCanRestoredState(mLastStateBeforeOpen)) {
                mNeedResetState = true;
            }
        }

        showOrHideDesktop(mLauncher, true);
        if (mLauncher.getWorkspace().isWobbling()) {
            wobbleFolder(true);
        } else if (isFolderWobbling) {
            wobbleFolder(false);
        }
    }

    @Override
    public boolean isFolderWobbling() {
        return isFolderWobbling || super.isFolderWobbling();
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mLauncher.isInState(mLastStateBeforeOpen)) {
            if (mLauncher.getDragController().isDragging()) {
                mNeedResetState = false;
            }

            if (mNeedResetState) {
                mLauncher.getStateManager().goToState(mLastStateBeforeOpen, false);
            }

            if (!mLauncher.isInState(NORMAL) && mNeedResetState) {
                animate = false;
            }

        } else if (mNeedResetState) {
            animate = false;
        }
        mNeedResetState = false;

        if (isFolderWobbling()) {
            wobbleFolder(false);
        }

        super.handleClose(animate);
    }

    @Override
    public void getHitRectRelativeToDragLayer(android.graphics.Rect outRect) {
        if (mGridFolderPage != null && mGridFolderPage.getWidth() > 0 && mActivityContext != null
                && mActivityContext.getDragLayer() != null) {
            mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(mGridFolderPage, outRect);
            if (mFolderTab != null && mFolderTab.getVisibility() == VISIBLE) {
                android.graphics.Rect tabRect = new android.graphics.Rect();
                mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(mFolderTab, tabRect);
                outRect.union(tabRect);
            }
            int offset = Math.max(mScrollAreaOffset, (int) (getContext().getResources().getDisplayMetrics().density * 48));
            outRect.left -= offset;
            outRect.right += offset;
        } else {
            super.getHitRectRelativeToDragLayer(outRect);
        }
    }

    @Override
    protected void onFolderCloseComplete() {
        showOrHideDesktop(mLauncher, false);
        Workspace<?> ws = mLauncher.getWorkspace();
        boolean onAppLibOrPinned = ws != null && (ws.isAppLibraryCurrentPage()
                || (ws.getFirstPagePinnedItem() != null && ws.getNextPage() == 0));
        if (!onAppLibOrPinned && Folder.getOpen(mLauncher) == null) {
            if (mLauncher.mBlurLayer != null) {
                mLauncher.mBlurLayer.setAlpha(0f);
            }
            View dockGlass = mLauncher.findViewById(R.id.dock_glass);
            if (dockGlass != null) {
                dockGlass.setVisibility(VISIBLE);
                dockGlass.setAlpha(1f);
            }
        }
        android.util.Log.d("GRID_FOLDER_TRACE", "onFolderCloseComplete restored desktop workspaceAlpha="
                + (mLauncher.getWorkspace() != null ? mLauncher.getWorkspace().getAlpha() : "null"));
    }

    @Override
    public boolean startDrag(View v, DragOptions options) {
        if (!isFolderWobbling && mLauncher.getWorkspace() != null
                && mLauncher.getWorkspace().isWobbling()) {
            wobbleFolder(true);
        }
        Object tag = v.getTag();
        if (tag instanceof WorkspaceItemInfo) {
            v.clearAnimation();
        }
        return super.startDrag(v, options);
    }

    @Override
    public void onDragEnd() {
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null && ws.isWobbling()) {
            wobbleFolder(true);
            ws.resetWobbleAlarm();
            wobbleExpireAlarm.setAlarm(Workspace.WOBBLE_EXPIRATION_TIMEOUT);
        } else if (isFolderWobbling) {
            wobbleFolder(false);
        }
        super.onDragEnd();
    }

    private AnimatorSet mDesktopVisibilityAnimator;

    private void showOrHideDesktop(Launcher launcher, boolean hide) {
        if (mDesktopVisibilityAnimator != null) {
            mDesktopVisibilityAnimator.cancel();
            mDesktopVisibilityAnimator = null;
        }

        boolean isSingleLayer = foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode();

        Workspace<?> workspace = launcher.getWorkspace();
        Hotseat hotseat = launcher.getHotseat();

        boolean isAppLib = workspace != null && workspace.isAppLibraryCurrentPage();

        if (!hide) {
            if (workspace != null) {
                workspace.setVisibility(VISIBLE);
                workspace.setAlpha(1f);
                for (int i = 0; i < workspace.getPageCount(); i++) {
                    View page = workspace.getChildAt(i);
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
            }
            if (!isAppLib) {
                if (workspace != null && workspace.getPageIndicator() != null) {
                    workspace.getPageIndicator().setVisibility(VISIBLE);
                    workspace.getPageIndicator().setAlpha(1f);
                    if (workspace.getPageIndicator() instanceof PageIndicatorDots) {
                        ((PageIndicatorDots) workspace.getPageIndicator()).resetToIdle();
                    }
                    if (launcher.isInState(LauncherState.SPRING_LOADED)) {
                        workspace.showPageIndicatorAtCurrentScroll();
                    }
                }
                if (hotseat != null) {
                    hotseat.setAlpha(1f);
                    hotseat.setIconsAlpha(1f);
                    if (hotseat.getQsb() != null) {
                        hotseat.getQsb().setVisibility(VISIBLE);
                        hotseat.getQsb().setAlpha(1f);
                    }
                    hotseat.setVisibility(VISIBLE);
                }
                View dockGlass = launcher.findViewById(R.id.dock_glass);
                if (dockGlass != null) {
                    dockGlass.setVisibility(VISIBLE);
                    dockGlass.setAlpha(1f);
                }
            }
            if (!isSingleLayer && !isAppLib) {
                ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
                if (scrimView != null) {
                    scrimView.setAlpha(1f);
                }
                BlurBackgroundView blur = launcher.mBlurLayer;
                if (blur != null) {
                    blur.setAlpha(0f);
                }
            }
            return;
        }

        AnimatorSet set = new AnimatorSet();

        if (workspace != null) {
            // In SingleLayerMode, we want to keep the workspace visible so it can be
            // blurred
            // behind the folder. We do not fade the workspace itself.
            if (!isSingleLayer) {
                play(set, getAnimator(workspace, 1f, 0f, hide));
            }
            if (workspace.getPageIndicator() != null) {
                play(set, getAnimator(workspace.getPageIndicator(), 1f, 0f, hide));
            }
        }

        if (hotseat != null) {
            hotseat.setIconsAlpha(hide ? 0f : 1f);
            play(set, getAnimator(hotseat, 1f, 0f, hide));
            View qsb = hotseat.getQsb();
            if (qsb != null) {
                play(set, getAnimator(qsb, 1f, 0f, hide));
            }
        }
        View dockGlass = launcher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            play(set, getAnimator(dockGlass, 1f, 0f, hide));
        }

        if (!isSingleLayer) {
            ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
            if (scrimView != null) {
                play(set, getAnimator(scrimView, 1f, 0f, hide));
            }

            BlurBackgroundView blur = launcher.mBlurLayer;
            if (blur != null) {
                play(set, getAnimator(blur, 0f, 1f, hide));
            }
        }
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDesktopVisibilityAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // force-apply the end state even if cancelled mid-flight
                if (workspace != null && !isSingleLayer) {
                    workspace.setAlpha(hide ? 0f : 1f);
                }
                if (workspace != null && workspace.getPageIndicator() != null) {
                    workspace.getPageIndicator().setAlpha(hide ? 0f : 1f);
                }
                if (hotseat != null) {
                    hotseat.setAlpha(hide ? 0f : 1f);
                    hotseat.setIconsAlpha(hide ? 0f : 1f);
                }
                if (dockGlass != null) {
                    dockGlass.setAlpha(hide ? 0f : 1f);
                }
                if (hotseat != null && hotseat.getQsb() != null) {
                    hotseat.getQsb().setAlpha(hide ? 0f : 1f);
                }
                if (!isSingleLayer) {
                    ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
                    if (scrimView != null)
                        scrimView.setAlpha(hide ? 0f : 1f);
                    BlurBackgroundView blur = launcher.mBlurLayer;
                    if (blur != null)
                        blur.setAlpha(hide ? 1f : 0f);
                }
            }
        });
        mDesktopVisibilityAnimator = set;
        set.start();
    }

    @Override
    public void updateFolderOnAnimate(boolean isOpening) {
        mFolderTab.setVisibility(isOpening ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getUnusedOffsetYOnAnimate(boolean isOpening) {
        // Tab height correction is now applied directly in FolderAnimationManager
        // (not through this method which gets scaled by initialScale, making it too
        // small).
        return 0;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        return super.onApplyWindowInsets(windowInsets);
    }

    public void wobbleFolder(boolean wobble) {
        wobbleFolder(wobble, false);
    }

    public void wobbleFolder(boolean wobble, boolean excludeDraggingView) {
        if (wobble) {
            isFolderWobbling = true;
            AtomicInteger index = new AtomicInteger();
            ItemInfo draggingViewInfo = mLauncher.getWorkspace().getDragObjectInfo();
            iterateOverItems((info, view) -> {
                view.setOnTouchListener(ItemLongClickListener.INSTANCE_WORKSPACE_WOBBLE);
                if (excludeDraggingView && draggingViewInfo != null) {
                    if (draggingViewInfo instanceof WorkspaceItemInfo && draggingViewInfo.equals(view.getTag())) {
                        return false;
                    }
                }
                index.getAndIncrement();
                if (index.get() % 2 == 0) {
                    view.startAnimation(mLauncher.getWorkspace().getWobbleAnimation());
                } else {
                    view.startAnimation(mLauncher.getWorkspace().getReverseWobbleAnimation());
                }
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(true);
                }
                return false;
            });
            wobbleExpireAlarm.setAlarm(Workspace.WOBBLE_EXPIRATION_TIMEOUT);
        } else {
            wobbleExpireAlarm.cancelAlarm();
            isFolderWobbling = false;
            iterateOverItems((info, view) -> {
                view.setOnTouchListener(null);
                view.clearAnimation();
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(false);
                }
                return false;
            });
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (alarm == wobbleExpireAlarm) {
            wobbleFolder(false);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends View & ClipPathView> T getAnimateObject() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            return (T) this;
        }
        return (T) mGridFolderPage;
    }

    private Animator getAnimator(View view, float v1, float v2, boolean hide) {
        return hide
                ? ObjectAnimator.ofFloat(view, View.ALPHA, v1, v2)
                : ObjectAnimator.ofFloat(view, View.ALPHA, v2, v1);
    }

    private void play(AnimatorSet as, Animator a) {
        a.setStartDelay(a.getStartDelay());
        a.setDuration(280);
        as.play(a);
    }
}
