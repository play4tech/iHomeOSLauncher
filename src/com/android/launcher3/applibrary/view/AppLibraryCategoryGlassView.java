package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.graphics.IconShape;
import com.example.liquidglass.LiquidGlassView;
import foundation.e.bliss.glass.GlassConfig;

public class AppLibraryCategoryGlassView extends LiquidGlassView {

    public AppLibraryCategoryGlassView(Context context) {
        this(context, null);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        init();
    }

    private final Path mClipPath = new Path();
    private float mCachedW = -1;
    private float mCachedH = -1;

    private Path updatePath(float w, float h) {
        if (w == mCachedW && h == mCachedH && !mClipPath.isEmpty()) {
            return mClipPath;
        }
        mCachedW = w;
        mCachedH = h;
        mClipPath.rewind();
        if (w <= 0 || h <= 0) return mClipPath;
        float radius = Math.min(w, h) / 2f;
        IconShape.getShape().addToPath(mClipPath, 0f, 0f, radius);
        return mClipPath;
    }

    private void init() {
        setClipToOutline(true);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float w = view.getWidth();
                float h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                Path path = updatePath(w, h);
                outline.setConvexPath(path);
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

    @Override
    public void draw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            super.draw(canvas);
            return;
        }
        Path path = updatePath(w, h);
        canvas.save();
        canvas.clipPath(path);
        super.draw(canvas);
        canvas.restore();
    }
}
