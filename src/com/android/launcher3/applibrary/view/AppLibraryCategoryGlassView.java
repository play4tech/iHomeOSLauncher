package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import android.view.ViewGroup;
import android.view.ViewParent;

import com.android.launcher3.R;
import com.example.liquidglass.LiquidGlassView;
import foundation.e.bliss.glass.BorderedGlassView;
import foundation.e.bliss.glass.GlassConfig;
import foundation.e.bliss.graphics.ContinuousCornerGeometry;
import foundation.e.bliss.graphics.GlassShapeSpec;

public class AppLibraryCategoryGlassView extends LiquidGlassView implements BorderedGlassView {

    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWhiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mBorderEnabled = GlassConfig.DEFAULT_BORDER_ENABLED;
    private float mBorderWidthPx;
    private float mBorderOpacity = GlassConfig.DEFAULT_BORDER_OPACITY;
    private float mBorderFade = GlassConfig.DEFAULT_BORDER_FADE;
    private int mBorderPosition = GlassConfig.DEFAULT_BORDER_POSITION;
    private float mBorderFadeSpread = GlassConfig.DEFAULT_BORDER_FADE_SPREAD;

    private boolean mWhiteBorderEnabled = GlassConfig.DEFAULT_WHITE_BORDER_ENABLED;
    private float mWhiteBorderWidthPx;
    private float mWhiteBorderOpacity = GlassConfig.DEFAULT_WHITE_BORDER_OPACITY;
    private float mWhiteBorderFade = GlassConfig.DEFAULT_WHITE_BORDER_FADE;
    private int mWhiteBorderPosition = GlassConfig.DEFAULT_WHITE_BORDER_POSITION;
    private float mWhiteBorderFadeSpread = GlassConfig.DEFAULT_WHITE_BORDER_FADE_SPREAD;

    public AppLibraryCategoryGlassView(Context context) {
        this(context, null);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        mBorderWidthPx = GlassConfig.DEFAULT_BORDER_WIDTH * getResources().getDisplayMetrics().density;
        mWhiteBorderWidthPx = GlassConfig.DEFAULT_WHITE_BORDER_WIDTH * getResources().getDisplayMetrics().density;
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mWhiteBorderPaint.setStyle(Paint.Style.STROKE);
        init();
    }

    @Override
    public boolean getBorderEnabled() {
        return mBorderEnabled;
    }

    @Override
    public void setBorderEnabled(boolean borderEnabled) {
        mBorderEnabled = borderEnabled;
    }

    @Override
    public float getBorderWidthPx() {
        return mBorderWidthPx;
    }

    @Override
    public void setBorderWidthPx(float borderWidthPx) {
        mBorderWidthPx = borderWidthPx;
    }

    @Override
    public float getBorderOpacity() {
        return mBorderOpacity;
    }

    @Override
    public void setBorderOpacity(float borderOpacity) {
        mBorderOpacity = borderOpacity;
    }

    @Override
    public float getBorderFade() {
        return mBorderFade;
    }

    @Override
    public void setBorderFade(float borderFade) {
        mBorderFade = borderFade;
    }

    @Override
    public int getBorderPosition() {
        return mBorderPosition;
    }

    @Override
    public void setBorderPosition(int borderPosition) {
        mBorderPosition = borderPosition;
    }

    @Override
    public float getBorderFadeSpread() {
        return mBorderFadeSpread;
    }

    @Override
    public void setBorderFadeSpread(float borderFadeSpread) {
        mBorderFadeSpread = borderFadeSpread;
    }

    @Override
    public boolean getWhiteBorderEnabled() {
        return mWhiteBorderEnabled;
    }

    @Override
    public void setWhiteBorderEnabled(boolean whiteBorderEnabled) {
        mWhiteBorderEnabled = whiteBorderEnabled;
    }

    @Override
    public float getWhiteBorderWidthPx() {
        return mWhiteBorderWidthPx;
    }

    @Override
    public void setWhiteBorderWidthPx(float whiteBorderWidthPx) {
        mWhiteBorderWidthPx = whiteBorderWidthPx;
    }

    @Override
    public float getWhiteBorderOpacity() {
        return mWhiteBorderOpacity;
    }

    @Override
    public void setWhiteBorderOpacity(float whiteBorderOpacity) {
        mWhiteBorderOpacity = whiteBorderOpacity;
    }

    @Override
    public float getWhiteBorderFade() {
        return mWhiteBorderFade;
    }

    @Override
    public void setWhiteBorderFade(float whiteBorderFade) {
        mWhiteBorderFade = whiteBorderFade;
    }

    @Override
    public int getWhiteBorderPosition() {
        return mWhiteBorderPosition;
    }

    @Override
    public void setWhiteBorderPosition(int whiteBorderPosition) {
        mWhiteBorderPosition = whiteBorderPosition;
    }

    @Override
    public float getWhiteBorderFadeSpread() {
        return mWhiteBorderFadeSpread;
    }

    @Override
    public void setWhiteBorderFadeSpread(float whiteBorderFadeSpread) {
        mWhiteBorderFadeSpread = whiteBorderFadeSpread;
    }

    private final ContinuousCornerGeometry mGeometry = new ContinuousCornerGeometry();
    private final Path mClipPath = new Path();
    private float mCachedW = -1;
    private float mCachedH = -1;
    private float mCachedRadius = -1;

    public float getTargetRadius(float w, float h) {
        if (w <= 0 || h <= 0) return 0f;
        // iOS category pods use a ~14% continuous squircle radius (~25-26dp on standard devices)
        return Math.min(w, h) * 0.14f;
    }

    private Path updatePath(float w, float h) {
        float radius = getTargetRadius(w, h);
        if (w == mCachedW && h == mCachedH && radius == mCachedRadius && !mClipPath.isEmpty()) {
            return mClipPath;
        }
        mCachedW = w;
        mCachedH = h;
        mCachedRadius = radius;
        mClipPath.rewind();
        if (w <= 0 || h <= 0) return mClipPath;
        Path path = mGeometry.getPath(w, h, radius, GlassShapeSpec.IOS_SMOOTHING);
        mClipPath.set(path);
        return mClipPath;
    }

    private void init() {
        setEnableSensorHighlight(false);
        setClipToOutline(false);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float w = view.getWidth();
                float h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float radius = getTargetRadius(w, h);
                outline.setRoundRect(0, 0, (int) w, (int) h, radius);
            }
        });

        // Standard master glass parameters from GlassConfig
        GlassConfig.applyDefaultConfig(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume touches so category icons and touch events pass through
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GlassConfig.registerView(this);
        if (getBackdropSource() == null) {
            View proxy = findWallpaperProxy();
            if (proxy != null) {
                setBackdropSource(proxy);
            } else {
                post(() -> {
                    if (getBackdropSource() == null) {
                        setBackdropSource(findWallpaperProxy());
                    }
                });
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GlassConfig.unregisterView(this);
        setBackdropSource(null);
    }

    private View findWallpaperProxy() {
        View root = getRootView();
        if (root != null) {
            View proxy = root.findViewById(R.id.glass_wallpaper_proxy);
            if (proxy != null) return proxy;
            View source = root.findViewById(R.id.glass_source);
            if (source != null) return source;
        }
        ViewParent p = getParent();
        while (p instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) p;
            View proxy = vg.findViewById(R.id.glass_wallpaper_proxy);
            if (proxy != null) return proxy;
            View source = vg.findViewById(R.id.glass_source);
            if (source != null) return source;
            p = vg.getParent();
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setCornerRadius(getTargetRadius(w, h));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            super.draw(canvas);
            return;
        }
        float targetRadius = getTargetRadius(w, h);
        if (getCornerRadius() != targetRadius) {
            setCornerRadius(targetRadius);
        }

        super.draw(canvas);

        Path path = updatePath(w, h);
        if (mBorderEnabled && mBorderWidthPx > 0f && mBorderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, mBorderPaint,
                mBorderWidthPx, mBorderOpacity, mBorderFade,
                mBorderPosition, mBorderFadeSpread, false,
                h
            );
        }

        if (mWhiteBorderEnabled && mWhiteBorderWidthPx > 0f && mWhiteBorderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, mWhiteBorderPaint,
                mWhiteBorderWidthPx, mWhiteBorderOpacity, mWhiteBorderFade,
                mWhiteBorderPosition, mWhiteBorderFadeSpread, true,
                h
            );
        }
    }
}
