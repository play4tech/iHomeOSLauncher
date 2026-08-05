package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.applibrary.model.CategoryData;

import java.util.List;

public class CategoryCardView extends ViewGroup {

    private AppLibraryCategoryGlassView mGlassView;
    private int mIconSize;
    private int mSmallIconSize;
    private int mPadding;
    private int mGap;
    private CategoryData mCategoryData;

    public CategoryCardView(Context context) {
        this(context, null);
    }

    public CategoryCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CategoryCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mGlassView = new AppLibraryCategoryGlassView(getContext());
        addView(mGlassView, 0); // Add as first child (background)
        
        Launcher launcher = Launcher.getLauncher(getContext());
        DeviceProfile dp = launcher.getDeviceProfile();
        mIconSize = dp.iconSizePx;
        mSmallIconSize = mIconSize / 2;
        mPadding = getResources().getDimensionPixelSize(R.dimen.app_library_card_padding);
        mGap = getResources().getDimensionPixelSize(R.dimen.app_library_card_padding) / 2;

        // Tapping any part of the card that ISN'T one of the app icons
        // (background/gaps/padding) opens the full list. This is what makes
        // categories with <=3 apps openable at all — they never get a
        // MiniClusterView tile, so previously nothing on them was clickable.
        setOnClickListener(v -> {
            if (mCategoryData != null) {
                AppLibraryCategoryExpandedView.show(launcher, mCategoryData);
            }
        });
    }

    public void bind(CategoryData categoryData) {
        mCategoryData = categoryData;
        List<AppInfo> apps = categoryData.apps;
        // Remove all views EXCEPT the glass background
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child != mGlassView) {
                removeViewAt(i);
            }
        }
        
        Launcher launcher = Launcher.getLauncher(getContext());
        
        // Add up to 3 large icons
        int largeIconCount = Math.min(3, apps.size());
        for (int i = 0; i < largeIconCount; i++) {
            BubbleTextView icon = (BubbleTextView) launcher.getLayoutInflater().inflate(
                    R.layout.app_library_card_icon, this, false);
            icon.applyFromApplicationInfo(apps.get(i));
            icon.setText(""); // Ensure no text for App Library icons in the card
            icon.setOnClickListener(launcher.getItemOnClickListener());
            addView(icon);
        }
        
        // If more than 4 apps, add the mini cluster
        if (apps.size() > 3) {
            MiniClusterView miniCluster = new MiniClusterView(getContext());
            miniCluster.setCategoryData(categoryData);
            int smallCount = Math.min(4, apps.size() - 3);
            for (int i = 0; i < smallCount; i++) {
                miniCluster.addIcon(apps.get(3 + i));
            }
            addView(miniCluster);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Card is typically square
        int height = width;
        
        int childSize = (width - 2 * mPadding - mGap) / 2;
        int childMeasureSpec = MeasureSpec.makeMeasureSpec(childSize, MeasureSpec.EXACTLY);
        
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mGlassView) {
                child.measure(widthMeasureSpec, heightMeasureSpec);
            } else {
                child.measure(childMeasureSpec, childMeasureSpec);
            }
        }
        
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        int childSize = (width - 2 * mPadding - mGap) / 2;
        
        int iconIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mGlassView) {
                child.layout(0, 0, width, height);
            } else {
                int row = iconIndex / 2;
                int col = iconIndex % 2;
                
                int left = mPadding + col * (childSize + mGap);
                int top = mPadding + row * (childSize + mGap);
                
                child.layout(left, top, left + childSize, top + childSize);
                iconIndex++;
            }
        }
    }
}
