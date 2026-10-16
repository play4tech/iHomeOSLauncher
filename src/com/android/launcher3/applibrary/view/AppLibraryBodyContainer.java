package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Container wrapping the App Library dashboard and search results below the search row.
 * Strictly enforces hardware RenderNode clipping and canvas clipping to guarantee that
 * any overscroll, rubber-banding, or flinging inside the dashboard can never draw above
 * the search row or overlap search bar liquid glass shaders.
 */
public class AppLibraryBodyContainer extends FrameLayout {

    public AppLibraryBodyContainer(@NonNull Context context) {
        this(context, null);
    }

    public AppLibraryBodyContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryBodyContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setClipBounds(new Rect(0, 0, w, h));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
