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
            return obj.mPaginationPaint.getAlpha();
        }

        @Override
        public void setValue(PageIndicatorDots obj, int alpha) {
            obj.mPaginationPaint.setAlpha(alpha);
            obj.invalidate();
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
    private int mToAlpha = VISIBLE_ALPHA;

    private float mCurrentPosition;
    private float mFinalPosition;
    private boolean mIsScrollPaused;
    private boolean mIsTwoPanels;
    private ObjectAnimator mAnimator;
    private @Nullable ObjectAnimator mAlphaAnimator;

    private float[] mEntryAnimationRadiusFactors;
    private SearchPillGlassView mPillGlassView;
    private boolean mTouchDownOnPill = false;

    private boolean isWorkspaceIndicator() {
        return getId() == R.id.page_indicator;
    }

    private final Runnable mHidePaginationRunnable = () -> {
        android.util.Log.d("PageIndicatorDots", "mHidePaginationRunnable executed, animating to INVISIBLE_ALPHA");
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
        mPaginationPaint.setColor(Themes.getAttrColor(context, R.attr.folderPaginationColor));
        mDotRadius = getResources().getDimension(R.dimen.page_indicator_dot_size) / 2;
        mCircleGap = DOT_GAP_FACTOR * mDotRadius;
        setOutlineProvider(new MyOutlineProver());
        mIsRtl = Utilities.isRtl(getResources());
        setClickable(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isWorkspaceIndicator() && MultiModeController.isSingleLayerMode()) {
            float pillAlpha = (PAGE_INDICATOR_ALPHA - mPaginationPaint.getAlpha()) / (float) PAGE_INDICATOR_ALPHA;
            if (mNumPages < 2) {
                pillAlpha = 1f;
            }
            if (pillAlpha <= 0.02f || getVisibility() != View.VISIBLE || getAlpha() <= 0.02f) {
                mTouchDownOnPill = false;
                return false;
            }

            float density = getResources().getDisplayMetrics().density;
            float pillWidth = 78f * density;
            float pillHeight = 26f * density;
            float pillLeft = (getWidth() - pillWidth) / 2f;
            float pillTop = (getHeight() - pillHeight) / 2f;
            RectF pillRect = new RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (pillRect.contains(event.getX(), event.getY())) {
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
                    RectF slopRect = new RectF(
                            pillRect.left - touchSlop,
                            pillRect.top - touchSlop,
                            pillRect.right + touchSlop,
                            pillRect.bottom + touchSlop);
                    if (!slopRect.contains(event.getX(), event.getY())) {
                        mTouchDownOnPill = false;
                        return false;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (mTouchDownOnPill) {
                        mTouchDownOnPill = false;
                        if (pillRect.contains(event.getX(), event.getY())) {
                            Launcher launcher = Launcher.getLauncher(getContext());
                            if (launcher != null && launcher.getWorkspace() != null && !launcher.getWorkspace().isWobbling()) {
                                com.android.launcher3.applibrary.view.SpotlightSearchOverlay.show(launcher);
                                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                return true;
                            }
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
        android.util.Log.d("PageIndicatorDots", "setScroll: current=" + currentScroll + " total=" + totalScroll
                + " numPages=" + mNumPages + " shouldAutoHide=" + mShouldAutoHide);
        if (currentScroll == 0 && totalScroll == 0) {
            CURRENT_POSITION.set(this, (float) mActivePage);
            return;
        }

        if (mNumPages <= 1) {
            return;
        }

        // Skip scroll update during binding. We will update it when binding completes.
        if (mIsScrollPaused) {
            return;
        }

        if (mShouldAutoHide) {
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
            if (mShouldAutoHide) {
                hideAfterDelay();
            }
        } else if (currentScroll > pageToRightScroll - scrollThreshold) {
            // scroll is far enough from left page to go to the right page
            animateToPosition(pageToLeft + 1);
            if (mShouldAutoHide) {
                hideAfterDelay();
            }
        } else {
            // scroll is between left and right page
            animateToPosition(pageToLeft + SHIFT_PER_ANIMATION);
            if (mShouldAutoHide) {
                mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        android.util.Log.d("PageIndicatorDots", "setShouldAutoHide called: " + shouldAutoHide);
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mPaginationPaint.getAlpha() > INVISIBLE_ALPHA) {
            hideAfterDelay();
        } else if (!shouldAutoHide) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void setPaintColor(int color) {
        mPaginationPaint.setColor(color);
    }

    private void hideAfterDelay() {
        android.util.Log.d("PageIndicatorDots", "hideAfterDelay() called, posting runnable for " + PAGINATION_FADE_DELAY + "ms");
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        mDelayedPaginationFadeHandler.postDelayed(mHidePaginationRunnable, PAGINATION_FADE_DELAY);
    }

    private void animatePaginationToAlpha(int alpha) {
        android.util.Log.d("PageIndicatorDots", "animatePaginationToAlpha() targetAlpha=" + alpha
                + " currentAlpha=" + mPaginationPaint.getAlpha() + " mToAlpha=" + mToAlpha);
        if (alpha == mToAlpha && mPaginationPaint.getAlpha() == alpha) {
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
        mAlphaAnimator.setDuration(alpha < mPaginationPaint.getAlpha()
                ? PAGINATION_FADE_OUT_DURATION
                : PAGINATION_FADE_IN_DURATION);
        mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                android.util.Log.d("PageIndicatorDots", "mAlphaAnimator ended, alpha=" + mPaginationPaint.getAlpha());
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
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;

        // If the last page gets removed we want to go to the previous page.
        if (mNumPages > 0 && mNumPages == mActivePage) {
            mActivePage--;
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        requestLayout();
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
        attachPillGlassIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachPillGlass();
    }

    private void attachPillGlassIfNeeded() {
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
        int myIndex = parent.indexOfChild(this);
        if (myIndex >= 0) {
            parent.addView(mPillGlassView, myIndex);
        } else {
            parent.addView(mPillGlassView);
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
        if (mPillGlassView != null) {
            float density = getResources().getDisplayMetrics().density;
            int pillWidth = (int) (78f * density);
            int pillHeight = (int) (26f * density);
            int pillLeftInSelf = (getWidth() - pillWidth) / 2;
            int pillTopInSelf = (getHeight() - pillHeight) / 2;
            int pillX = left + pillLeftInSelf;
            int pillY = top + pillTopInSelf;
            mPillGlassView.layout(pillX, pillY, pillX + pillWidth, pillY + pillHeight);
            post(this::syncPillGlassBounds);
        }
    }

    private void syncPillGlassBounds() {
        if (mPillGlassView == null) return;
        float density = getResources().getDisplayMetrics().density;
        int pillWidth = (int) (78f * density);
        int pillHeight = (int) (26f * density);
        int pillLeftInSelf = (getWidth() - pillWidth) / 2;
        int pillTopInSelf = (getHeight() - pillHeight) / 2;
        int pillX = getLeft() + pillLeftInSelf;
        int pillY = getTop() + pillTopInSelf;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPillGlassView.getLayoutParams();
        if (lp != null) {
            if (lp.leftMargin != pillX || lp.topMargin != pillY
                    || lp.width != pillWidth || lp.height != pillHeight) {
                lp.width = pillWidth;
                lp.height = pillHeight;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = pillX;
                lp.topMargin = pillY;
                mPillGlassView.setLayoutParams(lp);
            }
        }
    }

    public void syncPillGlassAlpha() {
        if (mPillGlassView == null) return;
        if (!MultiModeController.isSingleLayerMode() || !isWorkspaceIndicator()) {
            mPillGlassView.setVisibility(View.GONE);
            return;
        }
        float pillAlpha = (PAGE_INDICATOR_ALPHA - mPaginationPaint.getAlpha()) / (float) PAGE_INDICATOR_ALPHA;
        if (mNumPages < 2) {
            pillAlpha = 1f;
        }
        float effectiveAlpha = getAlpha() * pillAlpha;
        if (effectiveAlpha <= 0.02f || getVisibility() != View.VISIBLE) {
            mPillGlassView.setVisibility(View.GONE);
        } else {
            mPillGlassView.setVisibility(View.VISIBLE);
            mPillGlassView.setAlpha(effectiveAlpha);
        }
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
        float pillAlpha = (PAGE_INDICATOR_ALPHA - mPaginationPaint.getAlpha()) / (float) PAGE_INDICATOR_ALPHA;
        if (mNumPages < 2) {
            drawSearchPillIfSingleLayer(canvas, 1f);
            return;
        }

        // Draw iOS Search pill when idle/faded
        drawSearchPillIfSingleLayer(canvas, pillAlpha);

        if (mPaginationPaint.getAlpha() <= 0) {
            return;
        }

        // Draw all page indicators;
        float circleGap = mCircleGap;
        float startX = ((float) getWidth() / 2)
                - (mCircleGap * (((float) mNumPages - 1) / 2))
                - mDotRadius;

        float x = startX + mDotRadius;
        float y = getHeight() / 2;

        if (mEntryAnimationRadiusFactors != null) {
            // During entry animation, only draw the circles
            if (mIsRtl) {
                x = getWidth() - x;
                circleGap = -circleGap;
            }
            for (int i = 0; i < mEntryAnimationRadiusFactors.length; i++) {
                mPaginationPaint.setAlpha(i == mActivePage ? PAGE_INDICATOR_ALPHA : DOT_ALPHA);
                canvas.drawCircle(x, y, mDotRadius * mEntryAnimationRadiusFactors[i],
                        mPaginationPaint);
                x += circleGap;
            }
        } else {
            int alpha = mPaginationPaint.getAlpha();

            // Here we draw the dots
            mPaginationPaint.setAlpha((int) (alpha * DOT_ALPHA_FRACTION));
            for (int i = 0; i < mNumPages; i++) {
                canvas.drawCircle(x, y, mDotRadius, mPaginationPaint);
                x += circleGap;
            }

            // Here we draw the current page indicator
            mPaginationPaint.setAlpha(alpha);
            canvas.drawRoundRect(getActiveRect(), mDotRadius, mDotRadius, mPaginationPaint);
        }
    }

    private void drawSearchPillIfSingleLayer(Canvas canvas, float pillAlpha) {
        if (!isWorkspaceIndicator() || !foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() || pillAlpha <= 0.02f) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float pillWidth = 78f * density;
        float pillHeight = 26f * density;
        float pillLeft = (getWidth() - pillWidth) / 2f;
        float pillTop = (getHeight() - pillHeight) / 2f;
        RectF pillRect = new RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

        // Glass frosted pill background - only draw fallback if glass view is absent/hidden
        if (mPillGlassView == null || mPillGlassView.getVisibility() != View.VISIBLE) {
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(android.graphics.Color.argb((int) (55 * pillAlpha), 255, 255, 255));
            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, bgPaint);
        }

        // Rim stroke
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(0.8f * density);
        strokePaint.setColor(android.graphics.Color.argb((int) (40 * pillAlpha), 255, 255, 255));
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, strokePaint);

        // Magnifying glass icon
        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(1.4f * density);
        iconPaint.setColor(android.graphics.Color.argb((int) (225 * pillAlpha), 255, 255, 255));

        float iconRadius = 3.5f * density;
        float iconCenterX = pillLeft + 14f * density;
        float iconCenterY = getHeight() / 2f - 0.5f * density;
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, iconPaint);
        canvas.drawLine(
                iconCenterX + iconRadius * 0.707f,
                iconCenterY + iconRadius * 0.707f,
                iconCenterX + iconRadius * 0.707f + 3f * density,
                iconCenterY + iconRadius * 0.707f + 3f * density,
                iconPaint
        );

        // "Search" text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(android.graphics.Color.argb((int) (225 * pillAlpha), 255, 255, 255));
        textPaint.setTextSize(12f * density);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        float textX = iconCenterX + iconRadius + 7f * density;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = (getHeight() / 2f) - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("Search", textX, textY, textPaint);
    }

    private RectF getActiveRect() {
        float startCircle = (int) mCurrentPosition;
        float delta = mCurrentPosition - startCircle;
        float diameter = 2 * mDotRadius;
        float startX = ((float) getWidth() / 2)
                - (mCircleGap * (((float) mNumPages - 1) / 2))
                - mDotRadius;
        sTempRect.top = (getHeight() * 0.5f) - mDotRadius;
        sTempRect.bottom = (getHeight() * 0.5f) + mDotRadius;
        sTempRect.left = startX + (startCircle * mCircleGap);
        sTempRect.right = sTempRect.left + diameter;

        if (delta < SHIFT_PER_ANIMATION) {
            // dot is capturing the right circle.
            sTempRect.right += delta * mCircleGap * 2;
        } else {
            // Dot is leaving the left circle.
            sTempRect.right += mCircleGap;

            delta -= SHIFT_PER_ANIMATION;
            sTempRect.left += delta * mCircleGap * 2;
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
            if (mEntryAnimationRadiusFactors == null) {
                RectF activeRect = getActiveRect();
                outline.setRoundRect(
                        (int) activeRect.left,
                        (int) activeRect.top,
                        (int) activeRect.right,
                        (int) activeRect.bottom,
                        mDotRadius
                );
            }
        }
    }

    @Override
    public void setTranslationY(float translationY) {
    }

    public void setForcedTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (mPillGlassView != null) {
            mPillGlassView.setTranslationY(translationY);
        }
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
                if (mShouldAutoHide) {
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
            int realDockTop = insets.top + grid.hotseatGeometry.getBounds().top;
            int dockTopFromBottom = grid.heightPx - realDockTop;
            lp.bottomMargin = dockTopFromBottom + grid.hotseatGeometry.getIndicatorMargin();
            lp.topMargin = 0;
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int gapPx = (int) (8 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = grid.hotseatBarSizePx + gapPx;
        }
        setLayoutParams(lp);
    }
}
