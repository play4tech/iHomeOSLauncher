package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.graphics.IconShape;
import com.qmdeve.liquidglass.widget.LiquidGlassView;

public class AppLibraryCategoryGlassView extends LiquidGlassView {

    public AppLibraryCategoryGlassView(Context context) {
        this(context, null);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryCategoryGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
                float radius = Math.min(w, h) / 2f;
                Path path = new Path();
                IconShape.getShape().addToPath(path, 0f, 0f, radius);
                outline.setConvexPath(path);
            }
        });

        // Standard master glass parameters from GlassConfig
        foundation.e.bliss.glass.GlassConfig.applyDefaultConfig(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Bind to wallpaper proxy for glass effect
        View target = getRootView().findViewById(com.android.launcher3.R.id.glass_wallpaper_proxy);
        if (target instanceof android.view.ViewGroup) {
            bind((android.view.ViewGroup) target);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        Path path = new Path();
        float radius = Math.min(w, h) / 2f;
        IconShape.getShape().addToPath(path, 0f, 0f, radius);
        
        canvas.save();
        canvas.clipPath(path);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
