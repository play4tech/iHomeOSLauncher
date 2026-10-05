package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.example.liquidglass.LiquidGlassView;
import foundation.e.bliss.glass.GlassConfig;

public class AppLibrarySearchGlassView extends LiquidGlassView {

    public AppLibrarySearchGlassView(Context context) {
        this(context, null);
    }

    public AppLibrarySearchGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibrarySearchGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClipToOutline(true);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float w = view.getWidth();
                float h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float radius = h / 2f;
                outline.setRoundRect(0, 0, (int) w, (int) h, radius);
            }
        });

        // Standard master glass parameters from GlassConfig
        GlassConfig.applyDefaultConfig(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume touches so search bar input, focus, and clicks work properly
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        View root = getRootView();
        if (root != null) {
            View target = root.findViewById(com.android.launcher3.R.id.glass_wallpaper_proxy);
            if (target == null) {
                target = root.findViewById(com.android.launcher3.R.id.glass_source);
            }
            if (target != null) {
                setBackdropSource(target);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setBackdropSource(null);
    }

    private final Path mClipPath = new Path();
    private final RectF mClipRect = new RectF();
    private float mCachedW = -1;
    private float mCachedH = -1;

    @Override
    public void draw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            super.draw(canvas);
            return;
        }
        if (w != mCachedW || h != mCachedH) {
            mCachedW = w;
            mCachedH = h;
            mClipPath.rewind();
            float radius = h / 2f;
            mClipRect.set(0, 0, w, h);
            mClipPath.addRoundRect(mClipRect, radius, radius, Path.Direction.CW);
        }
        
        canvas.save();
        canvas.clipPath(mClipPath);
        super.draw(canvas);
        canvas.restore();
    }
}
