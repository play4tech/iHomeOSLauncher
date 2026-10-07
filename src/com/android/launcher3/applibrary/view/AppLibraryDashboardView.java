package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

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
        // iOS App Library uses a 2-column staggered grid
        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        setLayoutManager(layoutManager);

        // Disable default item animations for a smoother iOS-like feel when updating
        setItemAnimator(null);
        setClipToPadding(false);

        // Disable platform EdgeEffect distortion stretch so views translate physically with iOS rubber-band physics
        setOverScrollMode(OVER_SCROLL_NEVER);
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                    mReboundAnimator.cancel();
                }
                mLastTouchY = e.getY();
                mInitialDownY = e.getY();
                if (Math.abs(mOverscrollY) > 0.5f) {
                    mIsOverscrolling = true;
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - mLastTouchY;
                mLastTouchY = e.getY();
                if (!mIsOverscrolling) {
                    boolean atTop = !canScrollVertically(-1);
                    boolean atBottom = !canScrollVertically(1);
                    if ((atTop && dy > 0) || (atBottom && dy < 0)) {
                        if (Math.abs(e.getY() - mInitialDownY) > mTouchSlop) {
                            mIsOverscrolling = true;
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return true;
                        }
                    }
                } else {
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsOverscrolling && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                mIsOverscrolling = false;
                break;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                    mReboundAnimator.cancel();
                }
                mLastTouchY = e.getY();
                mInitialDownY = e.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - mLastTouchY;
                mLastTouchY = e.getY();

                boolean atTop = !canScrollVertically(-1);
                boolean atBottom = !canScrollVertically(1);

                if (mIsOverscrolling || (atTop && dy > 0) || (atBottom && dy < 0)) {
                    if (!mIsOverscrolling && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    mIsOverscrolling = true;

                    // iOS rubber-band resistance
                    float maxPull = getHeight() > 0 ? getHeight() * 0.45f : 600f;
                    float progress = Math.min(1f, Math.abs(mOverscrollY) / maxPull);
                    float damping = 0.42f * (1f - progress * 0.65f);

                    // If moving back toward zero, reduce resistance
                    if ((mOverscrollY > 0 && dy < 0) || (mOverscrollY < 0 && dy > 0)) {
                        damping = 0.75f;
                    }

                    mOverscrollY += dy * damping;

                    // Clamp to 0 if crossing while returning
                    if (atTop && !atBottom && mOverscrollY < 0) {
                        mOverscrollY = 0f;
                        mIsOverscrolling = false;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    } else if (atBottom && !atTop && mOverscrollY > 0) {
                        mOverscrollY = 0f;
                        mIsOverscrolling = false;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                    }

                    setTranslationY(mOverscrollY);
                    notifyOverscroll(mOverscrollY);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (mIsOverscrolling || Math.abs(mOverscrollY) > 0.5f) {
                    animateRebound();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        boolean canScroll = canScrollVertically(velocityY > 0 ? 1 : -1);
        if (!canScroll && Math.abs(velocityY) > 800) {
            float bounceDistance = Math.signum(-velocityY) * Math.min(
                    Math.abs(velocityY) * 0.04f,
                    45f * getResources().getDisplayMetrics().density
            );
            if (mReboundAnimator != null) {
                mReboundAnimator.cancel();
            }
            ValueAnimator bounceAnim = ValueAnimator.ofFloat(0f, bounceDistance, 0f);
            bounceAnim.setDuration(300);
            bounceAnim.setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.35f, 1f));
            bounceAnim.addUpdateListener(a -> {
                mOverscrollY = (float) a.getAnimatedValue();
                setTranslationY(mOverscrollY);
                notifyOverscroll(mOverscrollY);
            });
            bounceAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mOverscrollY = 0f;
                    setTranslationY(0f);
                    notifyOverscroll(0f);
                }
            });
            bounceAnim.start();
            return true;
        }
        return super.fling(velocityX, velocityY);
    }

    private void animateRebound() {
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
        mReboundAnimator = ValueAnimator.ofFloat(mOverscrollY, 0f);
        mReboundAnimator.setDuration(280);
        mReboundAnimator.setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.35f, 1f));
        mReboundAnimator.addUpdateListener(animation -> {
            mOverscrollY = (float) animation.getAnimatedValue();
            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        });
        mReboundAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOverscrollY = 0f;
                setTranslationY(0f);
                notifyOverscroll(0f);
                mIsOverscrolling = false;
            }
        });
        mReboundAnimator.start();
        mIsOverscrolling = false;
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
