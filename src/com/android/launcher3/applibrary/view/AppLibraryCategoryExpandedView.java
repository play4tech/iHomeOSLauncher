package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.applibrary.model.CategoryData;
import android.graphics.PointF;
import com.android.launcher3.dragndrop.DragLayer;

import java.util.ArrayList;
import java.util.List;

public class AppLibraryCategoryExpandedView extends AbstractFloatingView implements Insettable {
    private RecyclerView mGrid;
    private TextView mTitle;
    private CategoryCardView mSourceCard;
    private View mBackgroundPage;
    private AppLibraryFolderTransition mTransition;
    private final List<PointF> mStartCenters = new ArrayList<>();
    private final List<Float> mStartScales = new ArrayList<>();

    // Whether we have fully settled after the first open gesture.
    // The ACTION_UP that ends the mini-cluster tap also hits this overlay
    // (since it appears during the down→up sequence). We must swallow that
    // first UP without closing, so we wait until the next DOWN→UP cycle.
    private boolean mIgnoreFirstUp = true;

    private CategoryData mCategoryData;
    private final com.android.launcher3.applibrary.model.AppLibraryModel.AppLibraryUpdateListener mModelListener =
            this::onAppLibraryModelUpdated;

    private void onAppLibraryModelUpdated() {
        if (!mIsOpen || mCategoryData == null) return;
        if (mTransition != null && mTransition.isAnimating()) {
            post(() -> {
                if (mIsOpen && mCategoryData != null) {
                    onAppLibraryModelUpdated();
                }
            });
            return;
        }
        List<com.android.launcher3.model.data.AppInfo> updated =
                com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                        .getAppsForCategory(mCategoryData.category);
        if (updated == null || updated.isEmpty()) {
            close(true);
        } else if (mCategoryData.apps != null && mCategoryData.apps.equals(updated)) {
            return;
        } else {
            mCategoryData = new CategoryData(mCategoryData.category, updated);
            mGrid.setAdapter(new ExpandedCategoryAdapter(Launcher.getLauncher(getContext()), updated));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                .addListener(mModelListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                .removeListener(mModelListener);
    }

    public AppLibraryCategoryExpandedView(Context context) {
        this(context, null);
    }

    public AppLibraryCategoryExpandedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryCategoryExpandedView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mIsOpen = false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setClipChildren(false);
        setClipToPadding(false);
        mGrid = findViewById(R.id.expanded_category_grid);
        mTitle = findViewById(R.id.expanded_category_title);
        if (mTitle != null) {
            mTitle.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        }

        if (mGrid != null) {
            mGrid.setClipChildren(false);
            mGrid.setClipToPadding(false);
            mGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));
            android.view.ViewParent p = mGrid.getParent();
            while (p instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) p;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                if (vg == this) break;
                p = vg.getParent();
            }
        }

        // Close on a genuine tap (not a scroll/fling) that lands on empty
        // grid space or outside icon bounds.
        GestureDetector emptySpaceTap = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        View child = mGrid.findChildViewUnder(e.getX(), e.getY());
                        if (child == null) {
                            close(true);
                        } else if (child instanceof com.android.launcher3.BubbleTextView) {
                            float childX = e.getX() - child.getLeft();
                            float childY = e.getY() - child.getTop();
                            if (((com.android.launcher3.BubbleTextView) child).shouldIgnoreTouchDown(childX, childY)) {
                                close(true);
                            }
                        }
                        return false; // never consume — let icons still get clicks
                    }
                });
        mGrid.setOnTouchListener((v, e) -> {
            emptySpaceTap.onTouchEvent(e);
            return false;
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't intercept — let child views receive events normally.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // This is called only when a touch is NOT handled by any child view
        // (i.e. touch landed on empty background area).
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            // Mark that we've seen a real DOWN on the background
            // (as opposed to the stale UP from the mini-cluster tap)
            mIgnoreFirstUp = false;
        } else if (action == MotionEvent.ACTION_UP) {
            if (!mIgnoreFirstUp) {
                // Genuine tap on empty space → close
                close(true);
            }
            // Reset for next gesture
            mIgnoreFirstUp = false;
        }

        // Always consume background touches so nothing behind gets them.
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public void setInsets(Rect insets) {
        // Do nothing to avoid getting margins applied by DragLayer
    }

    private void restoreBackgroundViews() {
        if (mTransition != null) {
            mTransition.cancelAndRestoreBackground();
        }
        if (mBackgroundPage != null) {
            mBackgroundPage.setScaleX(1f);
            mBackgroundPage.setScaleY(1f);
            mBackgroundPage.setAlpha(1f);
        }
        if (mSourceCard != null) {
            mSourceCard.clearHiddenPreviewSlots();
            mSourceCard.setAlpha(1f);
        }
    }

    @Override
    public void onBackProgressed(@androidx.annotation.NonNull android.window.BackEvent backEvent) {
        // No-op: AppLibraryFolderTransition handles custom geometry morphing
        // and expects overlay scale to remain 1f.
    }

    @Override
    public void onBackCancelled() {
        super.onBackCancelled();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mIsOpen) return;
        mIsOpen = false;
        setScaleX(1f);
        setScaleY(1f);

        if (animate && mTransition != null) {
            mTransition.close(mStartCenters, mStartScales, this::removeViewFromDragLayer);
        } else if (animate) {
            restoreBackgroundViews();
            ObjectAnimator anim = ObjectAnimator.ofFloat(this, View.ALPHA, 0f);
            anim.setDuration(200);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeViewFromDragLayer();
                }
            });
            anim.start();
        } else {
            restoreBackgroundViews();
            removeViewFromDragLayer();
        }
    }

    private void removeViewFromDragLayer() {
        restoreBackgroundViews();
        DragLayer dragLayer = Launcher.getLauncher(getContext()).getDragLayer();
        dragLayer.removeView(this);
    }
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        ViewGroup.LayoutParams params = getLayoutParams();
        int topMargin = -1;
        if (params instanceof MarginLayoutParams) {
            topMargin = ((MarginLayoutParams) params).topMargin;
        }
        Log.d("ExpandedCategory", "onLayout bounds: left=" + l + " top=" + t + " right=" + r + " bottom=" + b + " topMargin=" + topMargin + " paddingTop=" + getPaddingTop() + " class=" + params.getClass().getName());
        View glass = findViewById(R.id.app_library_overlay_glass);
        if (glass != null) {
            Log.d("ExpandedCategory", "glass bounds: left=" + glass.getLeft() + " top=" + glass.getTop() + " right=" + glass.getRight() + " bottom=" + glass.getBottom());
        }
    }
    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_APP_LIBRARY_EXPANDED) != 0;
    }

    public static AppLibraryCategoryExpandedView getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_APP_LIBRARY_EXPANDED);
    }

    public void bind(CategoryData data) {
        mCategoryData = data;
        mTitle.setText(data.category.getTitle());
        mTitle.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mGrid.setAdapter(new ExpandedCategoryAdapter(Launcher.getLauncher(getContext()), data.apps));
    }

    public static AppLibraryCategoryExpandedView show(Launcher launcher, CategoryData data) {
        return show(launcher, data, null);
    }

    public static AppLibraryCategoryExpandedView show(Launcher launcher, CategoryData data, CategoryCardView sourceCard) {
        Log.d("ExpandedCategory", "show() called for category: " + data.category.getTitle());
        closeOpenViews(launcher, false, TYPE_APP_LIBRARY_EXPANDED);
        AppLibraryCategoryExpandedView view = (AppLibraryCategoryExpandedView) launcher.getLayoutInflater()
                .inflate(R.layout.app_library_expanded_category, launcher.getDragLayer(), false);

        view.bind(data);
        view.mSourceCard = sourceCard;
        view.mBackgroundPage = launcher.findViewById(R.id.app_library_content_container);

        DragLayer dragLayer = launcher.getDragLayer();

        if (sourceCard != null) {
            sourceCard.getIconStartLocations(dragLayer, view.mStartCenters, view.mStartScales);
        }

        InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        view.setLayoutParams(lp);

        view.mIsOpen = true;
        // Ignore the first ACTION_UP because it's the tail end of the
        // mini-cluster tap that opened this overlay.
        view.mIgnoreFirstUp = true;

        dragLayer.addView(view);
        launcher.onDragLayerHierarchyChanged();

        View glass = view.findViewById(R.id.app_library_overlay_glass);
        if (glass instanceof AppLibraryOverlayGlassView) {
            ((AppLibraryOverlayGlassView) glass).ensureBound();
        }
        if (glass != null) {
            glass.setAlpha(0f);
        }
        view.mTitle.setAlpha(0f);
        view.setAlpha(1f);

        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!view.mIsOpen) {
                    return true;
                }
                view.mTransition = new AppLibraryFolderTransition(
                        view.mBackgroundPage,
                        view.mSourceCard,
                        view,
                        glass,
                        view.mTitle,
                        view.mGrid
                );
                view.mTransition.open(view.mStartCenters, view.mStartScales, () -> {
                    // settled
                });
                return true;
            }
        });

        return view;
    }
}
