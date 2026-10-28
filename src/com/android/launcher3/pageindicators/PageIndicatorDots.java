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

package com.android.launcher3.pageindicators;

import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import foundation.e.bliss.glass.SearchPillGlassView;
import foundation.e.bliss.multimode.MultiModeController;

/**
 * {@link PageIndicator} which shows dots per page. The active page is shown with the current
 * accent color.
 */
public class PageIndicatorDots extends View implements Insettable, PageIndicator {

    private static final float SHIFT_PER_ANIMATION = 0.5f;
    private static final float SHIFT_THRESHOLD = 0.1f;
    private static final long ANIMATION_DURATION = 150;
    private static final int PAGINATION_FADE_DELAY = 1000;
    private static final int PAGINATION_FADE_IN_DURATION = 150;
    private static final int PAGINATION_FADE_OUT_DURATION = 400;

    private static final int ENTER_ANIMATION_START_DELAY = 300;
    private static final int ENTER_ANIMATION_STAGGERED_DELAY = 150;
    private static final int ENTER_ANIMATION_DURATION = 400;

    private static final int PAGE_INDICATOR_ALPHA = 255;
    private static final int DOT_ALPHA = 128;
    private static final float DOT_ALPHA_FRACTION = 0.5f;
    private static final int DOT_GAP_FACTOR = 4;
    private static final int VISIBLE_ALPHA = 255;
    private static final int INVISIBLE_ALPHA = 0;
    private Paint mPaginationPaint;

    // This value approximately overshoots to 1.5 times the original size.
    private static final float ENTER_ANIMATION_OVERSHOOT_TENSION = 4.9f;

    private static final RectF sTempRect = new RectF();

    private static final FloatProperty<PageIndicatorDots> CURRENT_POSITION =
            new FloatProperty<PageIndicatorDots>("current_position") {
                @Override
                public Float get(PageIndicatorDots obj) {
                    return obj.mCurrentPosition;
                }

                @Override
                public void setValue(PageIndicatorDots obj, float pos) {
                    obj.mCurrentPosition = pos;
                    obj.invalidate();
                    obj.invalidateOutline();
                }
    };

    private static final IntProperty<PageIndicatorDots> PAGINATION_ALPHA =
            new IntProperty<PageIndicatorDots>("pagination_alpha") {
        @Override
        public Integer get(PageIndicatorDots obj) {
            return obj.mPaginationAlpha;
        }

        @Override
        public void setValue(PageIndicatorDots obj, int alpha) {
            obj.mPaginationAlpha = Utilities.boundToRange(alpha, INVISIBLE_ALPHA, VISIBLE_ALPHA);
            obj.invalidate();
            obj.invalidateOutline();
            obj.syncPillGlassAlpha();
        }
    };

    private final Handler mDelayedPaginationFadeHandler = new Handler(Looper.getMainLooper());
    private final float mDotRadius;
    private final float mCircleGap;
    private final boolean mIsRtl;

    private int mNumPages;
    private int mActivePage;
    private int mTotalScroll;
    private boolean mShouldAutoHide = true;
    private int mPaginationAlpha = INVISIBLE_ALPHA;
    private int mToAlpha = INVISIBLE_ALPHA;
    private boolean mForceDotsMode = false;
    private int mLastScrollX = -1;
    private boolean mIsDarkText = false;

    private float mCurrentPosition;
    private float mFinalPosition;
    private boolean mIsScrollPaused;
    private boolean mIsTwoPanels;
    private ObjectAnimator mAnimator;
    private @Nullable ObjectAnimator mAlphaAnimator;

    private float[] mEntryAnimationRadiusFactors;
    private static final boolean ENABLE_SEARCH_PILL_GLASS = true;
    private SearchPillGlassView mPillGlassView;
    private boolean mTouchDownOnPill = false;

    // Pre-allocated objects for search pill drawing and touch to prevent GC thrashing at 120Hz
    private final RectF mPillRect = new RectF();
    private final RectF mSlopRect = new RectF();
    private final Paint mPillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mPillFontMetrics = new Paint.FontMetrics();

    private boolean isWorkspaceIndicator() {
        return getId() == R.id.page_indicator;
    }

    private final Runnable mHidePaginationRunnable = () -> {
        animatePaginationToAlpha(INVISIBLE_ALPHA);
    };

    public PageIndicatorDots(Context context) {
        this(context, null);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mPaginationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaginationPaint.setStyle(Style.FILL);
        updateThemeColors();
        mDotRadius = getResources().getDimension(R.dimen.page_indicator_dot_size) / 2;
        mCircleGap = DOT_GAP_FACTOR * mDotRadius;
        setOutlineProvider(new MyOutlineProver());
        mIsRtl = Utilities.isRtl(getResources());
        setClickable(false);

        mPillBgPaint.setStyle(Style.FILL);
        mPillStrokePaint.setStyle(Style.STROKE);
        mPillIconPaint.setStyle(Style.STROKE);
        mPillIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mPillTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        }
    }

    private void updateThemeColors() {
        mIsDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
        if (mPaginationPaint != null) {
            boolean useWhiteInGlassPill = isWorkspaceIndicator()
                    && MultiModeController.isSingleLayerMode();
            mPaginationPaint.setColor((!useWhiteInGlassPill && mIsDarkText)
                    ? Color.BLACK : Color.WHITE);
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateThemeColors();
        invalidate();
    }

    public void setForceDotsMode(boolean forceDots) {
        if (mForceDotsMode != forceDots) {
            mForceDotsMode = forceDots;
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            if (forceDots) {
                animatePaginationToAlpha(VISIBLE_ALPHA);
            } else if (mShouldAutoHide) {
                animatePaginationToAlpha(INVISIBLE_ALPHA);
            }
        }
    }

    private float getPillWidth() {
        float density = getResources().getDisplayMetrics().density;
        return 78f * density;
    }

    private float getEffectiveCircleGap() {
        if (mNumPages <= 1) return mCircleGap;
        if (!isWorkspaceIndicator()) return mCircleGap;
        float density = getResources().getDisplayMetrics().density;
        float maxDotsSpan = (getPillWidth() - 24f * density) - 2f * mDotRadius;
        float naturalSpan = (mNumPages - 1) * mCircleGap;
        if (naturalSpan > maxDotsSpan) {
            return maxDotsSpan / (mNumPages - 1);
        }
        return mCircleGap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isWorkspaceIndicator() && MultiModeController.isSingleLayerMode()) {
            if (getVisibility() != View.VISIBLE || getAlpha() <= 0.02f) {
                mTouchDownOnPill = false;
                return false;
            }

            float density = getResources().getDisplayMetrics().density;
            float pillWidth = getPillWidth();
            float pillHeight = 26f * density;
            float pillLeft = (getWidth() - pillWidth) / 2f;
            float pillTop = (getHeight() - pillHeight) / 2f;
            mPillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!mForceDotsMode && mPillRect.contains(event.getX(), event.getY())) {
                        mTouchDownOnPill = true;
                        return true;
                    }
                    mTouchDownOnPill = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!mTouchDownOnPill) {
                        return false;
                    }
                    float touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    mSlopRect.set(
                            mPillRect.left - touchSlop,
                            mPillRect.top - touchSlop,
                            mPillRect.right + touchSlop,
                            mPillRect.bottom + touchSlop);
                    if (!mSlopRect.contains(event.getX(), event.getY())) {
                        mTouchDownOnPill = false;
                        return false;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (mTouchDownOnPill) {
                        mTouchDownOnPill = false;
                        if (mPillRect.contains(event.getX(), event.getY())) {
                            Launcher launcher = Launcher.getLauncher(getContext());
                            if (launcher != null && launcher.getWorkspace() != null
                                    && !launcher.getWorkspace().isWobbling() && !mForceDotsMode) {
                                android.util.Log.d("SearchPillDebug", "PageIndicatorDots: Opening Spotlight search");
                                com.android.launcher3.applibrary.view.SpotlightSearchOverlay.show(launcher);
                                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                return true;
                            }
                            return true; // Consume touch so it never leaks to underlying views
                        }
                    }
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    mTouchDownOnPill = false;
                    return false;
            }
        }
        return false;
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (currentScroll == 0 && totalScroll == 0) {
            CURRENT_POSITION.set(this, (float) mActivePage);
            mLastScrollX = currentScroll;
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
            return;
        }

        if (mNumPages <= 1) {
            mLastScrollX = currentScroll;
            return;
        }

        // Skip scroll update during binding. We will update it when binding completes.
        if (mIsScrollPaused) {
            mLastScrollX = currentScroll;
            return;
        }

        boolean isFirstScrollSeed = (mLastScrollX == -1);
        boolean scrollPositionChanged = !isFirstScrollSeed && (mLastScrollX != currentScroll);
        mLastScrollX = currentScroll;

        if (mForceDotsMode) {
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        } else if (scrollPositionChanged && mShouldAutoHide && isWorkspaceIndicator()) {
            animatePaginationToAlpha(VISIBLE_ALPHA);
        }

        if (mIsRtl) {
            currentScroll = totalScroll - currentScroll;
        }

        currentScroll = Utilities.boundToRange(currentScroll, 0, totalScroll);
        mTotalScroll = totalScroll;

        int scrollPerPage = totalScroll / (mNumPages - 1);
        int pageToLeft = scrollPerPage == 0 ? 0 : currentScroll / scrollPerPage;
        int pageToLeftScroll = pageToLeft * scrollPerPage;
        int pageToRightScroll = pageToLeftScroll + scrollPerPage;

        float scrollThreshold = SHIFT_THRESHOLD * scrollPerPage;
        if (currentScroll < pageToLeftScroll + scrollThreshold) {
            // scroll is within the left page's threshold
            animateToPosition(pageToLeft);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
        } else if (currentScroll > pageToRightScroll - scrollThreshold) {
            // scroll is far enough from left page to go to the right page
            animateToPosition(pageToLeft + 1);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
        } else {
            // scroll is between left and right page
            animateToPosition(pageToLeft + SHIFT_PER_ANIMATION);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            return;
        }
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mPaginationAlpha > INVISIBLE_ALPHA && !mForceDotsMode) {
            hideAfterDelay();
        } else if (!shouldAutoHide || mForceDotsMode) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void setPaintColor(int color) {
        mPaginationPaint.setColor(color);
    }

    private void hideAfterDelay() {
        if (!isWorkspaceIndicator() || mForceDotsMode) {
            return;
        }
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        mDelayedPaginationFadeHandler.postDelayed(mHidePaginationRunnable, PAGINATION_FADE_DELAY);
    }

    private void animatePaginationToAlpha(int alpha) {
        if (!isWorkspaceIndicator() && alpha < VISIBLE_ALPHA) {
            return;
        }
        if (alpha == mToAlpha && mPaginationAlpha == alpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }

        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mToAlpha = alpha;
        mAlphaAnimator = ObjectAnimator.ofInt(this, PAGINATION_ALPHA,
                alpha);
        // If we are animating to decrease the alpha, then it's a fade out animation
        // whereas if we are animating to increase the alpha, it's a fade in animation.
        mAlphaAnimator.setDuration(alpha < mPaginationAlpha
                ? PAGINATION_FADE_OUT_DURATION
                : PAGINATION_FADE_IN_DURATION);
        mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAlphaAnimator = null;
            }
        });
        mAlphaAnimator.start();
    }

    /**
     * Pauses all currently running animations.
     */
    @Override
    public void pauseAnimations() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.pause();
        }
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    @Override
    public void skipAnimationsToEnd() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.end();
        }
    }

    private void animateToPosition(float position) {
        mFinalPosition = position;
        if (Math.abs(mCurrentPosition - mFinalPosition) < SHIFT_THRESHOLD) {
            mCurrentPosition = mFinalPosition;
        }
        if (mAnimator == null && Float.compare(mCurrentPosition, mFinalPosition) != 0) {
            float positionForThisAnim = mCurrentPosition > mFinalPosition ?
                    mCurrentPosition - SHIFT_PER_ANIMATION : mCurrentPosition + SHIFT_PER_ANIMATION;
            mAnimator = ObjectAnimator.ofFloat(this, CURRENT_POSITION, positionForThisAnim);
            mAnimator.addListener(new AnimationCycleListener());
            mAnimator.setDuration(ANIMATION_DURATION);
            mAnimator.start();
        }
    }

    public void stopAllAnimations() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mFinalPosition = mActivePage;
        CURRENT_POSITION.set(this, mFinalPosition);
    }

    /**
     * Sets up up the page indicator to play the entry animation.
     * {@link #playEntryAnimation()} must be called after this.
     */
    public void prepareEntryAnimation() {
        mEntryAnimationRadiusFactors = new float[mNumPages];
        invalidate();
    }

    public void playEntryAnimation() {
        int count = mEntryAnimationRadiusFactors.length;
        if (count == 0) {
            mEntryAnimationRadiusFactors = null;
            invalidate();
            return;
        }

        Interpolator interpolator = new OvershootInterpolator(ENTER_ANIMATION_OVERSHOOT_TENSION);
        AnimatorSet animSet = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(ENTER_ANIMATION_DURATION);
            final int index = i;
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mEntryAnimationRadiusFactors[index] = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            anim.setInterpolator(interpolator);
            anim.setStartDelay(ENTER_ANIMATION_START_DELAY + ENTER_ANIMATION_STAGGERED_DELAY * i);
            animSet.play(anim);
        }

        animSet.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mEntryAnimationRadiusFactors = null;
                invalidateOutline();
                invalidate();
            }
        });
        animSet.start();
    }

    @Override
    public void setActiveMarker(int activePage) {
        // In unfolded foldables, every page has two CellLayouts, so we need to halve the active
        // page for it to be accurate.
        if (mIsTwoPanels && !FOLDABLE_SINGLE_PAGE.get()) {
            activePage = activePage / 2;
        }

        if (mActivePage != activePage) {
            mActivePage = activePage;
            invalidate();
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        }

        // If the last page gets removed we want to go to the previous page.
        if (mNumPages > 0 && mNumPages == mActivePage) {
            mActivePage--;
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        requestLayout();
        post(this::syncPillGlassBounds);
    }

    @Override
    public void setPauseScroll(boolean pause, boolean isTwoPanels) {
        mIsTwoPanels = isTwoPanels;

        // Reapply correct current position which was skipped during setScroll.
        if (mIsScrollPaused && !pause) {
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        mIsScrollPaused = pause;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Add extra spacing of mDotRadius on all sides so than entry animation could be run.
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ?
                MeasureSpec.getSize(widthMeasureSpec) : (int) ((mNumPages * 3 + 2) * mDotRadius);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : (int) (4 * mDotRadius);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        } else if (MultiModeController.isSingleLayerMode() && mShouldAutoHide) {
            mPaginationAlpha = INVISIBLE_ALPHA;
            mToAlpha = INVISIBLE_ALPHA;
        }
        attachPillGlassIfNeeded();
        post(this::syncPillGlassBounds);
    }

    public void resetToIdle() {
        if (isWorkspaceIndicator() && MultiModeController.isSingleLayerMode()) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            if (mAlphaAnimator != null) {
                mAlphaAnimator.cancel();
                mAlphaAnimator = null;
            }
            if (mAnimator != null) {
                mAnimator.cancel();
                mAnimator = null;
            }
            mForceDotsMode = false;
            mPaginationAlpha = INVISIBLE_ALPHA;
            mToAlpha = INVISIBLE_ALPHA;
            syncPillGlassBounds();
            syncPillGlassAlpha();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
            mAlphaAnimator = null;
        }
        detachPillGlass();
    }

    private void attachPillGlassIfNeeded() {
        if (!ENABLE_SEARCH_PILL_GLASS) {
            return;
        }
        if (!isWorkspaceIndicator() || !MultiModeController.isSingleLayerMode()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (mPillGlassView != null) {
            return;
        }
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            return;
        }
        mPillGlassView = new SearchPillGlassView(getContext());
        float density = getResources().getDisplayMetrics().density;
        int pillWidth = Math.round(getPillWidth());
        int pillHeight = Math.round(26f * density);
        mPillGlassView.setCornerRadius(pillHeight / 2f);
        View proxy = parent.findViewById(R.id.glass_wallpaper_proxy);
        if (proxy == null) {
            proxy = parent.findViewById(R.id.glass_source);
        }
        if (proxy != null) {
            mPillGlassView.setBackdropSource(proxy);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pillWidth, pillHeight);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        int pillLeftInSelf = (getWidth() > 0 ? (getWidth() - pillWidth) / 2 : 0);
        int pillTopInSelf = (getHeight() > 0 ? (getHeight() - pillHeight) / 2 : 0);
        lp.leftMargin = getLeft() + pillLeftInSelf;
        lp.topMargin = getTop() + pillTopInSelf;
        int myIndex = parent.indexOfChild(this);
        if (myIndex >= 0) {
            parent.addView(mPillGlassView, myIndex, lp);
        } else {
            parent.addView(mPillGlassView, lp);
        }
        syncPillGlassBounds();
        syncPillGlassAlpha();
    }

    private void detachPillGlass() {
        if (mPillGlassView != null) {
            final SearchPillGlassView glassView = mPillGlassView;
            mPillGlassView = null;
            // Never mutate the parent's children array synchronously during dispatchDetachedFromWindow.
            // Removing a sibling during parent's detachment loop corrupts the array and causes an NPE
            // in ViewGroup.dispatchDetachedFromWindow. Post the removal instead.
            post(() -> {
                ViewGroup parent = (ViewGroup) glassView.getParent();
                if (parent != null) {
                    parent.removeView(glassView);
                }
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutPillGlassView();
    }

    private void layoutPillGlassView() {
        if (mPillGlassView == null || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int pillWidth = Math.round(getPillWidth());
        int pillHeight = Math.round(26f * density);
        int pillLeftInSelf = (getWidth() - pillWidth) / 2;
        int pillTopInSelf = (getHeight() - pillHeight) / 2;
        int pillX = getLeft() + pillLeftInSelf;
        int pillY = getTop() + pillTopInSelf;

        if (mPillGlassView.getLeft() == pillX && mPillGlassView.getTop() == pillY
                && mPillGlassView.getWidth() == pillWidth && mPillGlassView.getHeight() == pillHeight) {
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPillGlassView.getLayoutParams();
        if (lp != null) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = pillX;
            lp.topMargin = pillY;
            lp.width = pillWidth;
            lp.height = pillHeight;
        }

        mPillGlassView.measure(
                MeasureSpec.makeMeasureSpec(pillWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(pillHeight, MeasureSpec.EXACTLY));
        mPillGlassView.layout(pillX, pillY, pillX + pillWidth, pillY + pillHeight);
        mPillGlassView.invalidate();
    }

    private void syncPillGlassBounds() {
        if (mPillGlassView == null || getWidth() <= 0 || getHeight() <= 0) return;
        layoutPillGlassView();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPillGlassView.getLayoutParams();
        if (lp != null) {
            mPillGlassView.setLayoutParams(lp);
        }
    }

    public void syncPillGlassAlpha() {
        if (mPillGlassView == null) return;
        if (!MultiModeController.isSingleLayerMode() || !isWorkspaceIndicator()) {
            mPillGlassView.setVisibility(View.GONE);
            return;
        }
        float effectiveAlpha = getAlpha();
        if (effectiveAlpha <= 0f || getVisibility() != View.VISIBLE) {
            mPillGlassView.setVisibility(View.INVISIBLE);
        } else {
            mPillGlassView.setVisibility(View.VISIBLE);
            mPillGlassView.setAlpha(effectiveAlpha);
        }
    }

    public SearchPillGlassView getPillGlassView() {
        return mPillGlassView;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        syncPillGlassAlpha();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncPillGlassAlpha();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mNumPages < 1) {
            return;
        }
        if (mNumPages < 2 && isWorkspaceIndicator()) {
            drawSearchPillIfSingleLayer(canvas, 1f);
            return;
        }

        boolean isSingleLayerWorkspace = isWorkspaceIndicator()
                && MultiModeController.isSingleLayerMode();

        // Zero-deadband gamma-corrected crossfade at midpoint 128:
        float pillAlpha;
        int dotAlpha;
        if (isSingleLayerWorkspace) {
            if (mPaginationAlpha < 128) {
                float t = (128f - mPaginationAlpha) / 128f;
                pillAlpha = (float) Math.sqrt(t);
                dotAlpha = 0;
            } else {
                float t = (mPaginationAlpha - 128f) / 127f;
                pillAlpha = 0f;
                dotAlpha = Math.round((float) Math.sqrt(t) * PAGE_INDICATOR_ALPHA);
            }
        } else {
            pillAlpha = 0f;
            dotAlpha = (!isWorkspaceIndicator()) ? PAGE_INDICATOR_ALPHA : mPaginationAlpha;
        }

        // Draw iOS Search pill background/rim and (when pillAlpha > 0) "Search" icon + text
        drawSearchPillIfSingleLayer(canvas, pillAlpha);

        if (dotAlpha <= 0) {
            return;
        }

        // Draw all page indicators
        float circleGap = getEffectiveCircleGap();
        float startX = ((float) getWidth() / 2f)
                - (circleGap * (((float) mNumPages - 1) / 2f))
                - mDotRadius;

        float x = startX + mDotRadius;
        float y = getHeight() / 2f;

        // Always use bright WHITE dots inside the Single Layer glass pill
        mPaginationPaint.setColor((!isSingleLayerWorkspace && mIsDarkText)
                ? Color.BLACK : Color.WHITE);

        if (mEntryAnimationRadiusFactors != null) {
            if (mIsRtl) {
                x = getWidth() - x;
                circleGap = -circleGap;
            }
            for (int i = 0; i < mEntryAnimationRadiusFactors.length; i++) {
                mPaginationPaint.setAlpha(i == mActivePage
                        ? dotAlpha
                        : (int) (dotAlpha * DOT_ALPHA_FRACTION));
                canvas.drawCircle(x, y, mDotRadius * mEntryAnimationRadiusFactors[i],
                        mPaginationPaint);
                x += circleGap;
            }
        } else {
            mPaginationPaint.setAlpha((int) (dotAlpha * DOT_ALPHA_FRACTION));
            for (int i = 0; i < mNumPages; i++) {
                canvas.drawCircle(x, y, mDotRadius, mPaginationPaint);
                x += circleGap;
            }

            mPaginationPaint.setAlpha(dotAlpha);
            canvas.drawRoundRect(getActiveRect(), mDotRadius, mDotRadius, mPaginationPaint);
        }
    }

    private void drawSearchPillIfSingleLayer(Canvas canvas, float pillAlpha) {
        if (!isWorkspaceIndicator() || !MultiModeController.isSingleLayerMode()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float pillWidth = getPillWidth();
        float pillHeight = 26f * density;
        float pillLeft = (getWidth() - pillWidth) / 2f;
        float pillTop = (getHeight() - pillHeight) / 2f;
        mPillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

        float viewAlpha = getAlpha();
        if (viewAlpha <= 0.02f) {
            return;
        }

        // Always use white foreground inside the frosted glass pill
        int pillFgRgb = 255;

        // Draw fallback frosted background and rim only when SearchPillGlassView is absent/not ready
        boolean glassReady = (mPillGlassView != null && mPillGlassView.getParent() != null
                && mPillGlassView.getWidth() > 0 && mPillGlassView.getHeight() > 0
                && mPillGlassView.getVisibility() == View.VISIBLE);
        if (!glassReady) {
            mPillBgPaint.setColor(Color.argb((int) (55 * viewAlpha), 255, 255, 255));
            canvas.drawRoundRect(mPillRect, pillHeight / 2f, pillHeight / 2f, mPillBgPaint);

            mPillStrokePaint.setStrokeWidth(0.8f * density);
            mPillStrokePaint.setColor(Color.argb((int) (40 * viewAlpha), 255, 255, 255));
            canvas.drawRoundRect(mPillRect, pillHeight / 2f, pillHeight / 2f, mPillStrokePaint);
        }

        float contentAlpha = pillAlpha * viewAlpha;
        if (contentAlpha <= 0.02f) {
            return;
        }

        String label = "Search";
        mPillTextPaint.setColor(Color.argb((int) (235 * contentAlpha), pillFgRgb, pillFgRgb, pillFgRgb));
        mPillTextPaint.setTextSize(12f * density);
        float textWidth = mPillTextPaint.measureText(label);

        mPillIconPaint.setStrokeWidth(1.4f * density);
        mPillIconPaint.setColor(Color.argb((int) (235 * contentAlpha), pillFgRgb, pillFgRgb, pillFgRgb));

        float iconRadius = 3.5f * density;
        float handleExtent = iconRadius * 0.707f + 3f * density;
        float iconWidth = iconRadius + handleExtent;
        float iconTextGap = 4.5f * density;

        float totalContentWidth = iconWidth + iconTextGap + textWidth;
        float contentStartX = (getWidth() - totalContentWidth) / 2f;

        float iconCenterX = contentStartX + iconRadius;
        float iconCenterY = getHeight() / 2f - (handleExtent - iconRadius) / 2f;
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, mPillIconPaint);
        canvas.drawLine(
                iconCenterX + iconRadius * 0.707f,
                iconCenterY + iconRadius * 0.707f,
                iconCenterX + handleExtent,
                iconCenterY + handleExtent,
                mPillIconPaint
        );

        float textX = contentStartX + iconWidth + iconTextGap;
        mPillTextPaint.getFontMetrics(mPillFontMetrics);
        float textY = (getHeight() / 2f) - (mPillFontMetrics.ascent + mPillFontMetrics.descent) / 2f;
        canvas.drawText(label, textX, textY, mPillTextPaint);
    }

    private RectF getActiveRect() {
        float startCircle = (int) mCurrentPosition;
        float delta = mCurrentPosition - startCircle;
        float diameter = 2 * mDotRadius;
        float circleGap = getEffectiveCircleGap();
        float startX = ((float) getWidth() / 2)
                - (circleGap * (((float) mNumPages - 1) / 2))
                - mDotRadius;
        sTempRect.top = (getHeight() * 0.5f) - mDotRadius;
        sTempRect.bottom = (getHeight() * 0.5f) + mDotRadius;
        sTempRect.left = startX + (startCircle * circleGap);
        sTempRect.right = sTempRect.left + diameter;

        if (delta < SHIFT_PER_ANIMATION) {
            // dot is capturing the right circle.
            sTempRect.right += delta * circleGap * 2;
        } else {
            // Dot is leaving the left circle.
            sTempRect.right += circleGap;

            delta -= SHIFT_PER_ANIMATION;
            sTempRect.left += delta * circleGap * 2;
        }

        if (mIsRtl) {
            float rectWidth = sTempRect.width();
            sTempRect.right = getWidth() - sTempRect.left;
            sTempRect.left = sTempRect.right - rectWidth;
        }

        return sTempRect;
    }

    private class MyOutlineProver extends ViewOutlineProvider {

        @Override
        public void getOutline(View view, Outline outline) {
            float density = getResources().getDisplayMetrics().density;
            float pillWidth = getPillWidth();
            float pillHeight = 26f * density;
            float pillLeft = (getWidth() - pillWidth) / 2f;
            float pillTop = (getHeight() - pillHeight) / 2f;
            outline.setRoundRect(
                    (int) pillLeft,
                    (int) pillTop,
                    (int) (pillLeft + pillWidth),
                    (int) (pillTop + pillHeight),
                    pillHeight / 2f
            );
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (mPillGlassView != null) {
            mPillGlassView.setTranslationY(translationY);
        }
    }

    public void setForcedTranslationY(float translationY) {
        setTranslationY(translationY);
    }

    /**
     * Listener for keep running the animation until the final state is reached.
     */
    private class AnimationCycleListener extends AnimatorListenerAdapter {

        private boolean mCancelled = false;

        @Override
        public void onAnimationCancel(Animator animation) {
            mCancelled = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCancelled) {
                if (isWorkspaceIndicator() && mShouldAutoHide) {
                    hideAfterDelay();
                }
                mAnimator = null;
                animateToPosition(mFinalPosition);
            }
        }
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the pagination.
     */
    @Override
    public void setInsets(Rect insets) {
        if (!isWorkspaceIndicator()) {
            return;
        }
        DeviceProfile grid = ActivityContext.lookupContext(getContext()).getDeviceProfile();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (lp == null) return;
        if (grid.isFloatingDockEnabled() && grid.hotseatGeometry != null) {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int dockTopFromBottom = insets.bottom + grid.hotseatBarBottomSpacePx
                    + grid.hotseatGeometry.getBounds().height();
            lp.bottomMargin = dockTopFromBottom + grid.hotseatGeometry.getIndicatorMargin();
            lp.topMargin = 0;
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int gapPx = (int) (8 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = insets.bottom + grid.hotseatBarSizePx + gapPx;
        }
        setLayoutParams(lp);
        post(this::syncPillGlassBounds);
    }
}
