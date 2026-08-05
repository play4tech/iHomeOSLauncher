package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.qmdeve.liquidglass.widget.LiquidGlassView;

public class AppLibraryOverlayGlassView extends LiquidGlassView {

    public AppLibraryOverlayGlassView(Context context) {
        this(context, null);
    }

    public AppLibraryOverlayGlassView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryOverlayGlassView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // iOS App Library full-screen overlay glass properties
        // Heavy blur, dark tint (based on iOS screenshots where it goes dark, or we can use a transparent dark tint)
        setBlurRadius(45f);
        setDispersion(0.05f);
        setTintAlpha(0.6f);
        setTintColorRed(0.1f);
        setTintColorGreen(0.1f);
        setTintColorBlue(0.1f);

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
}
