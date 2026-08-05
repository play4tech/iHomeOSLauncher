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
import com.android.launcher3.applibrary.model.CategoryData;
import com.android.launcher3.dragndrop.DragLayer;

public class AppLibraryCategoryExpandedView extends AbstractFloatingView implements Insettable {
    private RecyclerView mGrid;
    private TextView mTitle;

    // Whether we have fully settled after the first open gesture.
    // The ACTION_UP that ends the mini-cluster tap also hits this overlay
    // (since it appears during the down→up sequence). We must swallow that
    // first UP without closing, so we wait until the next DOWN→UP cycle.
    private boolean mIgnoreFirstUp = true;

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
        mGrid = findViewById(R.id.expanded_category_grid);
        mTitle = findViewById(R.id.expanded_category_title);

        mGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));

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

    @Override
    protected void handleClose(boolean animate) {
        if (!mIsOpen) return;
        mIsOpen = false;

        if (animate) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(this, View.ALPHA, 0f);
            anim.setDuration(250);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeViewFromDragLayer();
                }
            });
            anim.start();
        } else {
            removeViewFromDragLayer();
        }
    }

    private void removeViewFromDragLayer() {
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
        // TYPE_FOLDER allows back button to close this overlay
        return (type & TYPE_FOLDER) != 0;
    }

    public void bind(CategoryData data) {
        mTitle.setText(data.category.getTitle());
        mGrid.setAdapter(new ExpandedCategoryAdapter(Launcher.getLauncher(getContext()), data.apps));
    }

    public static AppLibraryCategoryExpandedView show(Launcher launcher, CategoryData data) {
        Log.d("ExpandedCategory", "show() called for category: " + data.category.getTitle());
        AppLibraryCategoryExpandedView view = (AppLibraryCategoryExpandedView) launcher.getLayoutInflater()
                .inflate(R.layout.app_library_expanded_category, launcher.getDragLayer(), false);

        view.bind(data);

        DragLayer dragLayer = launcher.getDragLayer();

        Rect insets = dragLayer.getInsets();
        int topInset = insets != null ? insets.top : 0;
        int bottomInset = insets != null ? insets.bottom : 0;

        InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        view.setLayoutParams(lp);

        dragLayer.addView(view);

        view.setAlpha(0f);
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                view.animate().alpha(1f).setDuration(250).start();
                return true;
            }
        });
        
        view.mIsOpen = true;
        // Ignore the first ACTION_UP because it's the tail end of the
        // mini-cluster tap that opened this overlay.
        view.mIgnoreFirstUp = true;

        return view;
    }
}
