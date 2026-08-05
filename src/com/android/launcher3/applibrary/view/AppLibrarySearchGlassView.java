package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.qmdeve.liquidglass.widget.LiquidGlassView;

public class AppLibrarySearchGlassView extends LiquidGlassView {

    public AppLibrarySearchGlassView(Context context) {
        this(context, null);
    }

    public AppLibrarySearchGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibrarySearchGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
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
                float radius = h / 2f;
                outline.setRoundRect(0, 0, (int) w, (int) h, radius);
            }
        });

        // iOS App Library search bar glass properties
        // Slightly less blurred and more opaque than folders
        setBlurRadius(20f);
        setDispersion(0.05f);
        setTintAlpha(0.25f);
        setTintColorRed(1.0f);
        setTintColorGreen(1.0f);
        setTintColorBlue(1.0f);

        setDraggableEnabled(false);
        setElasticEnabled(false);
        setTouchEffectEnabled(false);
        setCornerRadius(0f);
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
        float radius = h / 2f;
        path.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
        
        canvas.save();
        canvas.clipPath(path);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
