package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.applibrary.model.CategoryData;

import java.util.Collections;
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

        // Clip children to card's squircle/rounded outline
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                float radius = mGlassView != null ? mGlassView.getTargetRadius(view.getWidth(), view.getHeight()) : view.getWidth() * 0.14f;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setClipChildren(false);
        setClipToPadding(false);

        // Tapping any part of the card that ISN'T one of the app icons
        // (background/gaps/padding) opens the full list. This is what makes
        // categories with <=3 apps openable at all — they never get a
        // MiniClusterView tile, so previously nothing on them was clickable.
        setOnClickListener(v -> {
            if (mCategoryData != null) {
                AppLibraryCategoryExpandedView.show(launcher, mCategoryData, this);
            }
        });
    }

    public void bind(CategoryData categoryData) {
        mCategoryData = categoryData;
        List<AppInfo> apps = categoryData.apps;
        Launcher launcher = Launcher.getLauncher(getContext());
        
        int largeIconsNeeded = apps.size() <= 4 ? apps.size() : 3;
        boolean needsMiniCluster = apps.size() > 4;
        
        // 1. Ensure slots 0 to largeIconsNeeded - 1 are BubbleTextViews
        for (int i = 0; i < largeIconsNeeded; i++) {
            int childIndex = i + 1; // child 0 is mGlassView
            View child = childIndex < getChildCount() ? getChildAt(childIndex) : null;
            BubbleTextView icon;
            if (child instanceof BubbleTextView) {
                icon = (BubbleTextView) child;
            } else {
                if (child != null) {
                    removeViewAt(childIndex);
                }
                icon = (BubbleTextView) launcher.getLayoutInflater().inflate(
                        R.layout.app_library_card_icon, this, false);
                icon.setOnClickListener(launcher.getItemOnClickListener());
                icon.setOnLongClickListener(v -> {
                    com.android.launcher3.popup.PopupContainerWithArrow.showForIcon((BubbleTextView) v);
                    return true;
                });
                addView(icon, childIndex);
            }
            icon.applyFromApplicationInfo(apps.get(i));
            icon.setText(""); // Ensure no text for App Library icons in the card
            icon.setCompoundDrawablePadding(0);
        }
        
        // 2. Handle slot 3 if needed
        int slot3Index = 4; // 1 + 3
        if (needsMiniCluster) {
            View child = slot3Index < getChildCount() ? getChildAt(slot3Index) : null;
            MiniClusterView miniCluster;
            if (child instanceof MiniClusterView) {
                miniCluster = (MiniClusterView) child;
                miniCluster.reset();
            } else {
                if (child != null) {
                    removeViewAt(slot3Index);
                }
                miniCluster = new MiniClusterView(getContext());
                addView(miniCluster, slot3Index);
            }
            miniCluster.setCategoryData(categoryData);
            int smallCount = Math.min(4, apps.size() - 3);
            for (int i = 0; i < smallCount; i++) {
                miniCluster.addIcon(apps.get(3 + i));
            }
        }
        
        // 3. Remove any remaining trailing views
        int totalExpectedChildren = 1 + largeIconsNeeded + (needsMiniCluster ? 1 : 0);
        while (getChildCount() > totalExpectedChildren) {
            removeViewAt(getChildCount() - 1);
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

    public AppLibraryCategoryGlassView getGlassView() {
        return mGlassView;
    }

    public void setPreviewSlotHidden(int appIndex, boolean hidden) {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            ((MiniClusterView) getChildAt(4)).setSlotHidden(appIndex - 3, hidden);
        }
    }

    public void clearHiddenPreviewSlots() {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            ((MiniClusterView) getChildAt(4)).clearHiddenSlots();
        }
    }

    public void getIconStartLocations(DragLayer dragLayer, List<PointF> outCenters, List<Float> outScales) {
        if (mCategoryData == null || mCategoryData.apps == null) return;
        int[] cardLoc = new int[2];
        dragLayer.getLocationInDragLayer(this, cardLoc);

        int totalApps = mCategoryData.apps.size();
        if (totalApps <= 4) {
            for (int i = 0; i < totalApps; i++) {
                View child = getChildAt(i + 1); // child 0 is mGlassView
                if (child != null) {
                    float cx = cardLoc[0] + child.getLeft() + child.getWidth() / 2f;
                    float cy = cardLoc[1] + child.getTop() + child.getHeight() / 2f;
                    outCenters.add(new PointF(cx, cy));
                    outScales.add(1.0f);
                }
            }
        } else {
            // First 3 are large icons
            for (int i = 0; i < 3; i++) {
                View child = getChildAt(i + 1);
                if (child != null) {
                    float cx = cardLoc[0] + child.getLeft() + child.getWidth() / 2f;
                    float cy = cardLoc[1] + child.getTop() + child.getHeight() / 2f;
                    outCenters.add(new PointF(cx, cy));
                    outScales.add(1.0f);
                }
            }
            // Slot 4 is MiniClusterView (child 4)
            View miniCluster = getChildAt(4);
            if (miniCluster instanceof MiniClusterView) {
                MiniClusterView mc = (MiniClusterView) miniCluster;
                float miniLeft = cardLoc[0] + mc.getLeft();
                float miniTop = cardLoc[1] + mc.getTop();
                int width = mc.getWidth();
                int clusterGap = mc.getGap();
                int miniIconSize = (width - clusterGap) / 2;
                float miniScale = (mIconSize > 0 && miniIconSize > 0)
                        ? (float) miniIconSize / (float) mIconSize
                        : 0.45f;
                if (miniScale <= 0f) miniScale = 0.45f;

                int clusterApps = totalApps - 3;
                for (int i = 0; i < clusterApps; i++) {
                    if (i < 4) {
                        int col = i % 2;
                        int row = i / 2;
                        float cx = miniLeft + col * (miniIconSize + clusterGap) + miniIconSize / 2f;
                        float cy = miniTop + row * (miniIconSize + clusterGap) + miniIconSize / 2f;
                        outCenters.add(new PointF(cx, cy));
                        outScales.add(miniScale);
                    } else {
                        float cx = miniLeft + mc.getWidth() / 2f;
                        float cy = miniTop + mc.getHeight() / 2f;
                        outCenters.add(new PointF(cx, cy));
                        outScales.add(miniScale);
                    }
                }
            }
        }
    }

    public List<Bitmap> getMiniClusterBitmaps() {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            return ((MiniClusterView) getChildAt(4)).getPreviewBitmaps();
        }
        return Collections.emptyList();
    }

    public PointF getMiniClusterCenter(DragLayer dragLayer) {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            View mc = getChildAt(4);
            int[] cardLoc = new int[2];
            dragLayer.getLocationInDragLayer(this, cardLoc);
            float cx = cardLoc[0] + mc.getLeft() + mc.getWidth() / 2f;
            float cy = cardLoc[1] + mc.getTop() + mc.getHeight() / 2f;
            return new PointF(cx, cy);
        }
        return null;
    }
}
