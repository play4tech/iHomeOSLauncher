package com.android.launcher3.applibrary.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.AppInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AppLibrarySearchAdapter extends RecyclerView.Adapter<AppLibrarySearchAdapter.ViewHolder> {

    private final Launcher mLauncher;
    private final List<AppInfo> mAllApps = new ArrayList<>();
    private final List<AppInfo> mFilteredApps = new ArrayList<>();
    private final Collator mCollator = Collator.getInstance(Locale.getDefault());

    public AppLibrarySearchAdapter(Launcher launcher) {
        mLauncher = launcher;
        mCollator.setStrength(Collator.PRIMARY);
    }

    public void setApps(List<AppInfo> apps) {
        mAllApps.clear();
        if (apps != null) {
            mAllApps.addAll(apps);
            Collections.sort(mAllApps, (a, b) -> {
                String titleA = a.title != null ? a.title.toString() : "";
                String titleB = b.title != null ? b.title.toString() : "";
                return mCollator.compare(titleA, titleB);
            });
        }
        filter("");
    }

    public void filter(String query) {
        mFilteredApps.clear();
        if (query == null || query.trim().isEmpty()) {
            mFilteredApps.addAll(mAllApps);
        } else {
            String lower = query.trim().toLowerCase(Locale.getDefault());
            for (AppInfo app : mAllApps) {
                String title = app.title != null ? app.title.toString().toLowerCase(Locale.getDefault()) : "";
                if (title.contains(lower)) {
                    mFilteredApps.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getPositionForSection(char letter) {
        char target = Character.toUpperCase(letter);
        for (int i = 0; i < mFilteredApps.size(); i++) {
            AppInfo app = mFilteredApps.get(i);
            if (app.title != null && app.title.length() > 0) {
                char first = Character.toUpperCase(app.title.charAt(0));
                if (target == '#') {
                    if (!Character.isLetter(first)) return i;
                } else if (first >= target) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean isEmpty() {
        return mFilteredApps.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_library_search_item, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = mFilteredApps.get(position);
        holder.title.setText(app.title);
        FastBitmapDrawable icon = app.newIcon(mLauncher);
        holder.icon.setImageDrawable(icon);
        holder.itemView.setTag(app);
        holder.itemView.setOnClickListener(v -> {
            View.OnClickListener clickListener = mLauncher.getItemOnClickListener();
            if (clickListener != null) {
                clickListener.onClick(v);
            } else {
                mLauncher.startActivitySafely(v, app.getIntent(), app);
            }
            v.postDelayed(() -> {
                View appLib = mLauncher.findViewById(R.id.app_library_page);
                if (appLib instanceof AppLibraryPage) {
                    ((AppLibraryPage) appLib).exitSearchMode();
                }
                SpotlightSearchOverlay spotlight = SpotlightSearchOverlay.getOpen(mLauncher);
                if (spotlight != null) {
                    spotlight.close(false);
                }
            }, 300);
        });
        holder.itemView.setOnLongClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(mLauncher, v);
            popup.getMenu().add(0, 1, 0, R.string.add_to_home_screen);
            popup.getMenu().add(0, 2, 1, R.string.app_info_drop_target_label);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    android.content.ComponentName cn = app.getTargetComponent();
                    if (cn != null) {
                        com.android.launcher3.util.RemovedAppStore.unmarkRemoved(
                                mLauncher, cn.flattenToString());
                    }
                    com.android.launcher3.model.data.WorkspaceItemInfo itemToAdd =
                            app.makeWorkspaceItem(mLauncher);
                    mLauncher.getModel().addAndBindAddedWorkspaceItems(
                            java.util.Collections.singletonList(android.util.Pair.create(itemToAdd, null)));
                    android.widget.Toast.makeText(mLauncher, R.string.add_to_home_screen,
                            android.widget.Toast.LENGTH_SHORT).show();
                    SpotlightSearchOverlay spotlight = SpotlightSearchOverlay.getOpen(mLauncher);
                    if (spotlight != null) {
                        spotlight.close(true);
                    }
                    return true;
                } else if (item.getItemId() == 2) {
                    new com.android.launcher3.util.PackageManagerHelper(mLauncher)
                            .startDetailsActivityForInfo(app, null, null);
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mFilteredApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_search_item_icon);
            title = itemView.findViewById(R.id.app_search_item_title);
        }
    }
}
