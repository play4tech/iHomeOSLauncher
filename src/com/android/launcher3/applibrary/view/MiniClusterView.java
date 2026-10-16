package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.applibrary.model.CategoryData;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MiniClusterView extends View {

    private final List<ItemInfoWithIcon> mIcons = new ArrayList<>();
    private final List<FastBitmapDrawable> mDrawables = new ArrayList<>();
    private final boolean[] mHiddenSlots = new boolean[4];
    private int mGap;
    private CategoryData mCategoryData;

    public MiniClusterView(Context context) {
        this(context, null);
    }

    public MiniClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiniClusterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Launcher launcher = Launcher.getLauncher(context);
        mGap = launcher.getDeviceProfile().iconDrawablePaddingPx / 2;
        
        setOnClickListener(v -> {
            Log.d("MiniClusterView", "Clicked on mini cluster!");
            if (mCategoryData != null) {
                Log.d("MiniClusterView", "CategoryData is not null, showing expanded view");
                View parent = (View) getParent();
                CategoryCardView card = (parent instanceof CategoryCardView) ? (CategoryCardView) parent : null;
                AppLibraryCategoryExpandedView.show(launcher, mCategoryData, card);
            } else {
                Log.d("MiniClusterView", "CategoryData is null!");
            }
        });
        
        setClickable(true);
        setFocusable(true);
    }


    public void reset() {
        mIcons.clear();
        mDrawables.clear();
        clearHiddenSlots();
        mCategoryData = null;
    }

    public void setCategoryData(CategoryData data) {
        mCategoryData = data;
    }

    public int getGap() {
        return mGap;
    }

    public void setSlotHidden(int slotIndex, boolean hidden) {
        if (slotIndex >= 0 && slotIndex < mHiddenSlots.length) {
            if (mHiddenSlots[slotIndex] != hidden) {
                mHiddenSlots[slotIndex] = hidden;
                invalidate();
            }
        }
    }

    public void clearHiddenSlots() {
        boolean changed = false;
        for (int i = 0; i < mHiddenSlots.length; i++) {
            if (mHiddenSlots[i]) {
                mHiddenSlots[i] = false;
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
    }

    public void addIcon(ItemInfoWithIcon info) {
        if (mIcons.size() < 4) {
            mIcons.add(info);
            FastBitmapDrawable d = info.newIcon(getContext());
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            mDrawables.add(d);
            invalidate();
        }
    }

    public List<Bitmap> getPreviewBitmaps() {
        List<Bitmap> bitmaps = new ArrayList<>();
        for (ItemInfoWithIcon info : mIcons) {
            if (info != null && info.bitmap != null && info.bitmap.icon != null) {
                bitmaps.add(info.bitmap.icon);
            }
        }
        return bitmaps;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawables.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        int iconSize = (width - mGap) / 2;

        for (int i = 0; i < mDrawables.size(); i++) {
            if (i < mHiddenSlots.length && mHiddenSlots[i]) continue;
            FastBitmapDrawable d = mDrawables.get(i);
            int row = i / 2;
            int col = i % 2;

            int left = col * (iconSize + mGap);
            int top = row * (iconSize + mGap);

            canvas.save();
            canvas.translate(left, top);
            // Scale drawable to fit
            float scale = (float) iconSize / Math.max(d.getIntrinsicWidth(), d.getIntrinsicHeight());
            canvas.scale(scale, scale);
            d.draw(canvas);
            canvas.restore();
        }
    }
}
