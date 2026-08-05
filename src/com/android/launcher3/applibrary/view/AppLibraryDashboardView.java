package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

public class AppLibraryDashboardView extends RecyclerView {

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

    private void init() {
        // iOS App Library uses a 2-column staggered grid
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        setLayoutManager(layoutManager);
        
        // Disable default item animations for a smoother iOS-like feel when updating
        setItemAnimator(null);
        setClipToPadding(false);
    }
}
