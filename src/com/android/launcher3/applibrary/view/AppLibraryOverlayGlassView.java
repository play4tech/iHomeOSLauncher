package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.example.liquidglass.GlassMaterial;
import com.example.liquidglass.LiquidGlassView;

public class AppLibraryOverlayGlassView extends LiquidGlassView {

    public AppLibraryOverlayGlassView(Context context) {
        this(context, null);
    }

    public AppLibraryOverlayGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryOverlayGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs);
        init();
    }

    private void init() {
        // iOS App Library full-screen overlay glass properties:
        // Frosted blur with translucent dark tint allowing wallpaper depth to shine through
        setMaterial(GlassMaterial.REGULAR);
        setRefractionHeight(0f);
        setBevelWidth(0f);
        setRefractionFalloff(0f);
        setDispersionStrength(0f);
        setEnableSensorHighlight(false);
        setEnableAdaptiveTint(false);
        setBlurAmount(0.28f);
        setGlassTint(Color.BLACK, 0.38f);
        setEnablePressEffect(false);
        setEnableDynamicBackground(false);
        setCollectFrameStats(false);
        setCornerRadius(0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Never consume touches so App Library scrolling, search, and gestures work unimpeded
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureBound();
        post(this::ensureBound);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setBackdropSource(null);
    }

    public void ensureBound() {
        View root = getRootView();
        if (root == null) return;
        View target = root.findViewById(com.android.launcher3.R.id.glass_wallpaper_proxy);
        if (target == null) {
            target = root.findViewById(com.android.launcher3.R.id.glass_source);
        }
        if (target != null && target != getBackdropSource()) {
            try {
                setBackdropSource(target);
                Log.d("AppLibraryOverlayGlass", "ensureBound: bound to " + target);
            } catch (Exception e) {
                Log.w("AppLibraryOverlayGlass", "Failed to set backdropSource", e);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (getBackdropSource() == null) {
            ensureBound();
        }
        super.draw(canvas);
    }
}
