package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AppLibraryDashboardView extends RecyclerView {

    public interface OnOverscrollListener {
        void onOverscrollChanged(float translationY);
    }

    private OnOverscrollListener mOverscrollListener;
    private float mLastTouchY = 0f;
    private float mInitialDownY = 0f;
    private float mOverscrollY = 0f;
    private boolean mIsOverscrolling = false;
    private int mTouchSlop;
    private ValueAnimator mReboundAnimator;

    public AppLibraryDashboardView(@NonNull Context context) {
        this(context, null);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnOverscrollListener(OnOverscrollListener listener) {
        mOverscrollListener = listener;
    }

    private void init() {
        // iOS App Library uses a 2-column grid
        GridLayoutManager layoutManager =
                new GridLayoutManager(getContext(), 2);
        setLayoutManager(layoutManager);
        setHasFixedSize(true);
        setItemViewCacheSize(14);

        // Disable default item animations for a smoother iOS-like feel when updating
        setItemAnimator(null);
        setClipToPadding(false);
        setClipChildren(false);

        // Enable EdgeEffect and drive physical translationY rubber-band stretch via custom EdgeEffectFactory
        setOverScrollMode(OVER_SCROLL_ALWAYS);
        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                return new DashboardRubberBandEdgeEffect(getContext(), direction);
            }
        });
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private class DashboardRubberBandEdgeEffect extends EdgeEffect {
        private final int mDirection;

        DashboardRubberBandEdgeEffect(Context context, int direction) {
            super(context);
            mDirection = direction;
        }

        @Override
        public void onPull(float deltaDistance) {
            handlePull(deltaDistance);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
            return deltaDistance;
        }

        @Override
        public float getDistance() {
            if (getHeight() <= 0) return 0f;
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) {
                return mOverscrollY < -0.5f ? -mOverscrollY / getHeight() : 0f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP) {
                return mOverscrollY > 0.5f ? mOverscrollY / getHeight() : 0f;
            }
            return 0f;
        }

        private void handlePull(float deltaDistance) {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                mReboundAnimator.cancel();
            }
            mIsOverscrolling = true;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }

            // deltaDistance is relative to view height.
            // DIRECTION_BOTTOM: dragging up beyond bottom -> negative translationY
            // DIRECTION_TOP: dragging down beyond top -> positive translationY
            float sign = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) ? -1f : 1f;
            float pixelDelta = deltaDistance * (getHeight() > 0 ? getHeight() : 1000f);

            float maxPull = getHeight() > 0 ? getHeight() * 0.45f : 600f;
            float progress = Math.min(1f, Math.abs(mOverscrollY) / maxPull);
            float damping = 0.42f * (1f - progress * 0.65f);

            // If pulling back toward zero, reduce resistance
            if ((mOverscrollY > 0 && sign * deltaDistance < 0) || (mOverscrollY < 0 && sign * deltaDistance > 0)) {
                damping = 0.75f;
            }

            mOverscrollY += sign * pixelDelta * damping;

            // Clamp to zero if crossing back
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM && mOverscrollY > 0f) {
                mOverscrollY = 0f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP && mOverscrollY < 0f) {
                mOverscrollY = 0f;
            }

            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        }

        @Override
        public void onRelease() {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            boolean isOverscrolledInMyDirection = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM && mOverscrollY < -0.5f)
                    || (mDirection == EdgeEffectFactory.DIRECTION_TOP && mOverscrollY > 0.5f);
            if (isOverscrolledInMyDirection) {
                animateRebound();
            } else if (Math.abs(mOverscrollY) <= 0.5f) {
                mIsOverscrolling = false;
            }
        }

        @Override
        public void onAbsorb(int velocity) {
            int signedVelocity = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) ? velocity : -velocity;
            playFlingBounce(signedVelocity);
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                return false;
            }
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) {
                return mOverscrollY >= -0.5f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP) {
                return mOverscrollY <= 0.5f;
            }
            return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setClipBounds(new Rect(0, 0, w, h));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                mReboundAnimator.cancel();
            }
            mLastTouchY = e.getY();
            mInitialDownY = e.getY();
            if (Math.abs(mOverscrollY) > 0.5f) {
                mIsOverscrolling = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                    mReboundAnimator.cancel();
                }
                mLastTouchY = e.getY();
                mInitialDownY = e.getY();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (Math.abs(mOverscrollY) > 0.5f && (mReboundAnimator == null || !mReboundAnimator.isRunning())) {
                    animateRebound();
                }
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        boolean canScroll = canScrollVertically(velocityY > 0 ? 1 : -1);
        if (!canScroll && Math.abs(velocityY) > 250) {
            playFlingBounce(velocityY);
            return true;
        }
        return super.fling(velocityX, velocityY);
    }

    private void playFlingBounce(int velocityY) {
        if (Math.abs(velocityY) < 250) return;
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
        mIsOverscrolling = true;
        float density = getResources().getDisplayMetrics().density;
        float maxBounce = 55f * density;
        float distance = Math.min(Math.abs(velocityY) * 0.045f, maxBounce);
        float peakY = (velocityY > 0 ? -1f : 1f) * distance;
        float startY = mOverscrollY;

        // Phase 1: Decelerate outward to peak (iOS spring compression)
        ValueAnimator outwardAnim = ValueAnimator.ofFloat(startY, peakY);
        mReboundAnimator = outwardAnim;
        outwardAnim.setDuration(160);
        outwardAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f));
        outwardAnim.addUpdateListener(a -> {
            mOverscrollY = (float) a.getAnimatedValue();
            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        });
        outwardAnim.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                // Phase 2: Spring back from peak to 0 (smooth iOS recovery)
                ValueAnimator reboundAnim = ValueAnimator.ofFloat(peakY, 0f);
                mReboundAnimator = reboundAnim;
                reboundAnim.setDuration(280);
                reboundAnim.setInterpolator(new PathInterpolator(0.25f, 1f, 0.5f, 1f));
                reboundAnim.addUpdateListener(ra -> {
                    mOverscrollY = (float) ra.getAnimatedValue();
                    setTranslationY(mOverscrollY);
                    notifyOverscroll(mOverscrollY);
                });
                reboundAnim.addListener(new AnimatorListenerAdapter() {
                    private boolean mReboundCancelled = false;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mReboundCancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!mReboundCancelled) {
                            mOverscrollY = 0f;
                            setTranslationY(0f);
                            notifyOverscroll(0f);
                            mIsOverscrolling = false;
                        }
                    }
                });
                reboundAnim.start();
            }
        });
        outwardAnim.start();
    }

    private void animateRebound() {
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
        mReboundAnimator = ValueAnimator.ofFloat(mOverscrollY, 0f);
        mReboundAnimator.setDuration(280);
        mReboundAnimator.setInterpolator(new PathInterpolator(0.25f, 1f, 0.5f, 1f));
        mReboundAnimator.addUpdateListener(animation -> {
            mOverscrollY = (float) animation.getAnimatedValue();
            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        });
        mReboundAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;
            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCancelled) {
                    mOverscrollY = 0f;
                    setTranslationY(0f);
                    notifyOverscroll(0f);
                    mIsOverscrolling = false;
                }
            }
        });
        mReboundAnimator.start();
    }

    private void notifyOverscroll(float translationY) {
        if (mOverscrollListener != null) {
            mOverscrollListener.onOverscrollChanged(translationY);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
    }
}
