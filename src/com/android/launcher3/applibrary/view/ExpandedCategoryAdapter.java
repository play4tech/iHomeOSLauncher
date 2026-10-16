package com.android.launcher3.applibrary.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;

import java.util.List;

public class ExpandedCategoryAdapter extends RecyclerView.Adapter<ExpandedCategoryAdapter.ViewHolder> {

    private final Launcher mLauncher;
    private final List<AppInfo> mApps;

    public ExpandedCategoryAdapter(Launcher launcher, List<AppInfo> apps) {
        mLauncher = launcher;
        mApps = apps;
    }

    private int getCellSize(ViewGroup parent) {
        if (parent != null && parent.getWidth() > 0) {
            return parent.getWidth() / 4;
        }
        DeviceProfile dp = mLauncher.getDeviceProfile();
        if (dp != null && dp.getCellSize().x > 0) {
            return dp.getCellSize().x;
        }
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BubbleTextView icon = (BubbleTextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_icon, parent, false);
        int cellSize = getCellSize(parent);
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp != null) {
            lp.height = cellSize;
        } else {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellSize);
        }
        icon.setLayoutParams(lp);
        icon.setCenterVertically(true);
        icon.setDisplay(BubbleTextView.DISPLAY_APP_LIBRARY_EXPANDED);
        icon.setTextColor(android.graphics.Color.WHITE);
        // Bind the standard item click listener so clicking an app launches it
        icon.setOnClickListener(mLauncher.getItemOnClickListener());
        return new ViewHolder(icon);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = mApps.get(position);
        holder.icon.applyFromApplicationInfo(app);
        holder.icon.setCenterVertically(true);
        int cellSize = getCellSize((ViewGroup) holder.icon.getParent());
        if (cellSize > 0 && holder.icon.getLayoutParams() != null && holder.icon.getLayoutParams().height != cellSize) {
            holder.icon.getLayoutParams().height = cellSize;
        }
        holder.icon.setOnLongClickListener(v -> {
            com.android.launcher3.popup.PopupContainerWithArrow.showForIcon(holder.icon);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        BubbleTextView icon;
        ViewHolder(BubbleTextView itemView) {
            super(itemView);
            icon = itemView;
        }
    }
}
