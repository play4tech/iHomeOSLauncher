package com.android.launcher3.applibrary.model;

import com.android.launcher3.model.data.AppInfo;

import java.util.List;

public class CategoryData {
    public final AppLibraryCategory category;
    public final List<AppInfo> apps;

    public CategoryData(AppLibraryCategory category, List<AppInfo> apps) {
        this.category = category;
        this.apps = apps;
    }
}
