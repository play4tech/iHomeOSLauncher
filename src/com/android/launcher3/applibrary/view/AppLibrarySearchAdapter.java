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

import com.android.launcher3.util.ComponentKey;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppLibrarySearchAdapter extends RecyclerView.Adapter<AppLibrarySearchAdapter.ViewHolder> {

    private final Launcher mLauncher;
    private final List<AppInfo> mAllApps = new ArrayList<>();
    private final List<AppInfo> mFilteredApps = new ArrayList<>();
    private final Map<AppInfo, String> mLowerTitleCache = new HashMap<>();
    private final Collator mCollator = Collator.getInstance(Locale.getDefault());

    public AppLibrarySearchAdapter(Launcher launcher) {
        mLauncher = launcher;
        mCollator.setStrength(Collator.PRIMARY);
        setHasStableIds(true);
    }

    private String mCurrentQuery = "";

    public void setApps(List<AppInfo> apps) {
        mAllApps.clear();
        mLowerTitleCache.clear();
        if (apps != null) {
            mAllApps.addAll(apps);
            Collections.sort(mAllApps, (a, b) -> {
                String titleA = a.title != null ? a.title.toString() : "";
                String titleB = b.title != null ? b.title.toString() : "";
                return mCollator.compare(titleA, titleB);
            });
            for (AppInfo app : mAllApps) {
                String title = app.title != null ? app.title.toString().toLowerCase(Locale.getDefault()) : "";
                mLowerTitleCache.put(app, title);
            }
        }
        filter(mCurrentQuery);
    }

    public void filter(String query) {
        mCurrentQuery = query != null ? query : "";
        mFilteredApps.clear();
        if (mCurrentQuery.trim().isEmpty()) {
            mFilteredApps.addAll(mAllApps);
        } else {
            String lower = mCurrentQuery.trim().toLowerCase(Locale.getDefault());
            for (AppInfo app : mAllApps) {
                String title = mLowerTitleCache.get(app);
                if (title != null && title.contains(lower)) {
                    mFilteredApps.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    private final Map<ComponentKey, Long> mStableIdMap = new HashMap<>();
    private long mNextStableId = 1;

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mFilteredApps.size()) {
            AppInfo app = mFilteredApps.get(position);
            ComponentKey key = app.getComponentKey();
            if (key != null) {
                Long id = mStableIdMap.get(key);
                if (id == null) {
                    id = mNextStableId++;
                    mStableIdMap.put(key, id);
                }
                return id;
            }
            if (app.id != com.android.launcher3.model.data.ItemInfo.NO_ID) {
                return app.id;
            }
        }
        return RecyclerView.NO_ID;
    }

    public int getPositionForSection(char letter) {
        if (mFilteredApps.isEmpty()) return -1;
        char target = Character.toUpperCase(letter);
        if (target == '#') {
            for (int i = 0; i < mFilteredApps.size(); i++) {
                AppInfo app = mFilteredApps.get(i);
                if (app.title != null && app.title.length() > 0) {
                    char first = app.title.charAt(0);
                    if (!Character.isLetter(first)) return i;
                }
            }
            return mFilteredApps.size() - 1;
        }

        for (int i = 0; i < mFilteredApps.size(); i++) {
            AppInfo app = mFilteredApps.get(i);
            if (app.title != null && app.title.length() > 0) {
                char first = Character.toUpperCase(app.title.charAt(0));
                if (Character.isLetter(first) && first >= target) {
                    return i;
                }
            }
        }
        return mFilteredApps.size() - 1;
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
                AppLibraryPage alp = mLauncher.getAppLibraryPage();
                if (alp != null) {
                    alp.exitSearchMode();
                }
                SpotlightSearchOverlay spotlight = SpotlightSearchOverlay.getOpen(mLauncher);
                if (spotlight != null) {
                    spotlight.close(false);
                }
            }, 300);
        });
        View.OnLongClickListener longClickListener = v -> {
            holder.icon.setTag(app);
            holder.itemView.setTag(app);
            com.android.launcher3.popup.PopupContainerWithArrow.showForSearchItem(holder.icon, app);
            return true;
        };
        holder.itemView.setOnLongClickListener(longClickListener);
        holder.icon.setOnLongClickListener(longClickListener);
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
