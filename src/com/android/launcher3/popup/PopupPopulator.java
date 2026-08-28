/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.popup;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contains logic relevant to populating a {@link PopupContainerWithArrow}. In particular,
 * this class determines which items appear in the container, and in what order.
 */
public class PopupPopulator {

    public static final int MAX_SHORTCUTS = 4;
    @VisibleForTesting
    static final int NUM_DYNAMIC = 2;

    /**
     * Sorts shortcuts in rank order, with manifest shortcuts coming before dynamic shortcuts.
     */
    private static final Comparator<ShortcutInfo> SHORTCUT_RANK_COMPARATOR = (a, b) -> {
        if (a.isDeclaredInManifest() && !b.isDeclaredInManifest()) {
            return -1;
        }
        if (!a.isDeclaredInManifest() && b.isDeclaredInManifest()) {
            return 1;
        }
        return Integer.compare(a.getRank(), b.getRank());
    };

    /**
     * Filters the shortcuts so that only MAX_SHORTCUTS or fewer shortcuts are retained.
     * We want the filter to include both static and dynamic shortcuts, so we always
     * include NUM_DYNAMIC dynamic shortcuts, if at least that many are present.
     *
     * @return a subset of shortcuts, in sorted order, with size <= MAX_SHORTCUTS.
     */
    public static List<ShortcutInfo> sortAndFilterShortcuts(List<ShortcutInfo> shortcuts) {
        shortcuts.sort(SHORTCUT_RANK_COMPARATOR);
        if (shortcuts.size() <= MAX_SHORTCUTS) {
            return shortcuts;
        }

        // The list of shortcuts is now sorted with static shortcuts followed by dynamic
        // shortcuts. We want to preserve this order, but only keep MAX_SHORTCUTS.
        List<ShortcutInfo> filteredShortcuts = new ArrayList<>(MAX_SHORTCUTS);
        int numDynamic = 0;
        int size = shortcuts.size();
        for (int i = 0; i < size; i++) {
            ShortcutInfo shortcut = shortcuts.get(i);
            int filteredSize = filteredShortcuts.size();
            if (filteredSize < MAX_SHORTCUTS) {
                // Always add the first MAX_SHORTCUTS to the filtered list.
                filteredShortcuts.add(shortcut);
                if (shortcut.isDynamic()) {
                    numDynamic++;
                }
                continue;
            }
            // At this point, we have MAX_SHORTCUTS already, but they may all be static.
            // If there are dynamic shortcuts, remove static shortcuts to add them.
            if (shortcut.isDynamic() && numDynamic < NUM_DYNAMIC) {
                numDynamic++;
                int lastStaticIndex = filteredSize - numDynamic;
                filteredShortcuts.remove(lastStaticIndex);
                filteredShortcuts.add(shortcut);
            }
        }
        return filteredShortcuts;
    }

    public static class CachedShortcutData {
        public final List<ShortcutInfo> shortcuts;
        public final List<WorkspaceItemInfo> itemInfos;

        public CachedShortcutData(List<ShortcutInfo> shortcuts, List<WorkspaceItemInfo> itemInfos) {
            this.shortcuts = shortcuts;
            this.itemInfos = itemInfos;
        }
    }

    private static final android.util.LruCache<com.android.launcher3.util.ComponentKey, CachedShortcutData> sShortcutCache =
            new android.util.LruCache<>(30);

    public static CachedShortcutData getCachedShortcuts(ItemInfo info) {
        if (info == null || info.getTargetComponent() == null || info.user == null) return null;
        return sShortcutCache.get(new com.android.launcher3.util.ComponentKey(info.getTargetComponent(), info.user));
    }

    public static void preloadShortcuts(
            final Context context, final ItemInfo originalInfo) {
        if (originalInfo == null || originalInfo.getTargetComponent() == null || originalInfo.user == null) return;
        final com.android.launcher3.util.ComponentKey key =
                new com.android.launcher3.util.ComponentKey(originalInfo.getTargetComponent(), originalInfo.user);
        if (sShortcutCache.get(key) != null) return;

        com.android.launcher3.util.Executors.MODEL_EXECUTOR.execute(() -> {
            loadAndCacheShortcuts(context, originalInfo);
        });
    }

    public static CachedShortcutData loadAndCacheShortcuts(
            final Context context, final ItemInfo originalInfo) {
        final ComponentName activity = originalInfo.getTargetComponent();
        final UserHandle user = originalInfo.user;
        if (activity == null || user == null) return null;
        final com.android.launcher3.util.ComponentKey key = new com.android.launcher3.util.ComponentKey(activity, user);

        try {
            List<ShortcutInfo> shortcuts = new ShortcutRequest(context, user)
                    .withContainer(activity)
                    .query(ShortcutRequest.PUBLISHED);
            shortcuts = PopupPopulator.sortAndFilterShortcuts(shortcuts);
            IconCache cache = LauncherAppState.getInstance(context).getIconCache();
            List<WorkspaceItemInfo> itemInfos = new ArrayList<>(shortcuts.size());
            for (int i = 0; i < shortcuts.size(); i++) {
                final ShortcutInfo shortcut = shortcuts.get(i);
                final WorkspaceItemInfo si = new WorkspaceItemInfo(shortcut, context);
                cache.getShortcutIcon(si, shortcut);
                si.rank = i;
                si.container = CONTAINER_SHORTCUTS;
                itemInfos.add(si);
            }
            CachedShortcutData data = new CachedShortcutData(shortcuts, itemInfos);
            sShortcutCache.put(key, data);
            return data;
        } catch (Throwable t) {
            android.util.Log.e("PopupPopulator", "Failed to load and cache shortcuts", t);
            return null;
        }
    }

    /**
     * Returns a runnable to update the provided shortcuts
     */
    public static Runnable createUpdateRunnable(
            final Context context,
            final ItemInfo originalInfo,
            final Handler uiHandler, final PopupContainerWithArrow container,
            final List<DeepShortcutView> shortcutViews) {
        return () -> {
            CachedShortcutData data = loadAndCacheShortcuts(context, originalInfo);
            if (data == null) return;
            for (int i = 0; i < data.shortcuts.size() && i < shortcutViews.size(); i++) {
                final ShortcutInfo shortcut = data.shortcuts.get(i);
                final WorkspaceItemInfo si = data.itemInfos.get(i);
                final DeepShortcutView view = shortcutViews.get(i);
                uiHandler.post(() -> view.applyShortcutInfo(si, shortcut, container));
            }
        };
    }
}
