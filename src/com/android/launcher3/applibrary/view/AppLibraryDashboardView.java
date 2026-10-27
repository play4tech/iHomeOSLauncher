package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * App Library grid with iOS-style rubber-band overscroll.
 *
 * RecyclerView keeps full ownership of touch handling, nested scrolling, recycling, and fling.
 * A custom EdgeEffect translates child item views with the iOS rubber-band curve on pull,
 * and rebounds / absorbs fling velocity using a critically damped SpringAnimation.
 */
public class AppLibraryDashboardView extends RecyclerView {

    public interface OnOverscrollListener {
        void onOverscrollChanged(float translationY);
    }

    // Rubber band: offset = d * (1 - 1 / (x * C / d + 1))
    // x = raw finger pull in px, d = RUBBER_RANGE * view height (asymptote).
    private static final float RUBBER_C = 0.55f;
    private static final float RUBBER_RANGE = 0.5f;

    // Critically damped spring (DAMPING_RATIO_NO_BOUNCY): natural deceleration without oscillation.
    private static final float SPRING_STIFFNESS = 280f;

    // Spring start velocity (px/s) per px/s of fling velocity absorbed at an edge.
    private static final float ABSORB_SCALE = 0.35f;

    private OnOverscrollListener mOverscrollListener;
    // Signed translation currently applied to every child (+ at top edge, - at bottom edge).
    private float mOverscrollY = 0f;
    private ElasticEdgeEffect mTopEdgeEffect;
    private ElasticEdgeEffect mBottomEdgeEffect;

    public AppLibraryDashboardView(@NonNull Context context) {
        this(context, null);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnOverscrollListener(OnOverscrollListener listener) {
        mOverscrollListener = listener;
    }

    private void init() {
        setLayoutManager(new GridLayoutManager(getContext(), 2));
        setHasFixedSize(true);
        setItemViewCacheSize(14);
        setItemAnimator(null);
        setClipToPadding(false);
        setClipChildren(false);

        // Required so RecyclerView routes boundary touches to the EdgeEffect
        setOverScrollMode(OVER_SCROLL_ALWAYS);

        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                if (direction == DIRECTION_TOP) {
                    mTopEdgeEffect = new ElasticEdgeEffect(view.getContext(), 1f);
                    return mTopEdgeEffect;
                } else if (direction == DIRECTION_BOTTOM) {
                    mBottomEdgeEffect = new ElasticEdgeEffect(view.getContext(), -1f);
                    return mBottomEdgeEffect;
                }
                return super.createEdgeEffect(view, direction);
            }
        });

        // Children bound/attached mid-overscroll must pick up the current offset, and
        // recycled children must not carry a stale one into their next bind.
        addOnChildAttachStateChangeListener(new OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                view.setTranslationY(mOverscrollY);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.setTranslationY(0f);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setClipBounds(new Rect(0, 0, w, h));
        }
    }

    /**
     * Translate the CHILDREN, not this view. RecyclerView reads touch coordinates in its own
     * local space, so translating the RecyclerView itself feeds motion back into the
     * next dy and distorts effective drag.
     */
    private void applyOverscroll(float translationY) {
        mOverscrollY = translationY;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            getChildAt(i).setTranslationY(translationY);
        }
        if (mOverscrollListener != null) {
            mOverscrollListener.onOverscrollChanged(translationY);
        }
    }

    private void claimTouch() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void releaseTouch() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private float pullRangePx() {
        return getHeight() * RUBBER_RANGE;
    }

    /** Raw finger pull (px) -> displayed offset (px). */
    private float toOffset(float rawPx) {
        float d = pullRangePx();
        if (d <= 0f) return 0f;
        return d * (1f - 1f / (rawPx * RUBBER_C / d + 1f));
    }

    /** Displayed offset (px) -> raw finger pull (px). Inverse of toOffset. */
    private float toRawPull(float offsetPx) {
        float d = pullRangePx();
        if (d <= 0f) return 0f;
        float y = Math.min(offsetPx, d * 0.999f);
        return y / (RUBBER_C * (1f - y / d));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mTopEdgeEffect != null) {
            mTopEdgeEffect.finish();
        }
        if (mBottomEdgeEffect != null) {
            mBottomEdgeEffect.finish();
        }
    }

    /**
     * One instance per vertical edge. Single source of truth is mOffsetPx (>= 0); both the
     * drag path and the spring write it through setOffset().
     */
    private class ElasticEdgeEffect extends EdgeEffect {
        private final float mSign;
        private float mOffsetPx = 0f;
        private SpringAnimation mSpring;

        ElasticEdgeEffect(Context context, float sign) {
            super(context);
            mSign = sign;
        }

        @Override
        public void onPull(float deltaDistance) {
            onPullDistance(deltaDistance, 0.5f);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            onPullDistance(deltaDistance, displacement);
        }

        /**
         * deltaDistance is finger movement / view height; positive = further into overscroll,
         * negative = retracting. Returns how much of it was consumed; RecyclerView scrolls
         * the content with whatever is left (handoff when pull crosses zero).
         * deltaDistance == 0 is RecyclerView's "finger went down on an active effect" call:
         * stopping the spring here is what makes the rebound interruptible.
         */
        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            stopSpring();
            float h = getHeight();
            if (h <= 0f) return 0f;
            if (deltaDistance > 0f) {
                claimTouch();
            }

            float oldRaw = toRawPull(mOffsetPx);
            float newRaw = Math.max(0f, oldRaw + deltaDistance * h);
            setOffset(toOffset(newRaw));
            return (newRaw - oldRaw) / h;
        }

        /** RecyclerView tests this against 0, so it remains non-zero during spring motion. */
        @Override
        public float getDistance() {
            float h = getHeight();
            return h > 0f ? mOffsetPx / h : 0f;
        }

        @Override
        public void onRelease() {
            if (mOffsetPx > 0f) {
                startSpring(0f);
            }
        }

        /** Fling reached the edge with this much velocity (px/s, positive). */
        @Override
        public void onAbsorb(int velocity) {
            startSpring(velocity * ABSORB_SCALE);
        }

        @Override
        public boolean isFinished() {
            return mOffsetPx == 0f && (mSpring == null || !mSpring.isRunning());
        }

        @Override
        public void finish() {
            stopSpring();
            setOffset(0f);
            releaseTouch();
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false; // Visual motion is handled by child view translation
        }

        private void setOffset(float px) {
            mOffsetPx = px;
            applyOverscroll(mSign * px);
        }

        private void stopSpring() {
            if (mSpring != null && mSpring.isRunning()) {
                mSpring.cancel();
            }
        }

        private void startSpring(float velocity) {
            stopSpring();

            mSpring = new SpringAnimation(new FloatValueHolder(mOffsetPx));
            mSpring.setSpring(new SpringForce(0f)
                    .setStiffness(SPRING_STIFFNESS)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
            mSpring.setStartVelocity(velocity);
            mSpring.addUpdateListener((anim, value, vel) -> setOffset(Math.max(0f, value)));
            mSpring.addEndListener((anim, canceled, value, vel) -> {
                if (!canceled) {
                    setOffset(0f);
                    releaseTouch();
                }
            });
            mSpring.start();
        }
    }
}
