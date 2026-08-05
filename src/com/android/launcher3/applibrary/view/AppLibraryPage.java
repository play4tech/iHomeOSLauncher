package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.model.data.AppInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppLibraryPage extends FrameLayout {

    private View mContentContainer;
    private View mSearchContainer;
    private EditText mSearchInput;
    private AppLibraryDashboardView mDashboardView;
    private AppLibraryDashboardAdapter mDashboardAdapter;
    private AllAppsRecyclerView mSearchResultsView;
    private FrameLayout mExpansionOverlay;

    private AppLibraryModel mAppLibraryModel;
    private int mSavedScrollY = 0;

    public AppLibraryPage(Context context) {
        this(context, null);
    }

    public AppLibraryPage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryPage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mAppLibraryModel = new AppLibraryModel(getContext());
        mDashboardAdapter = new AppLibraryDashboardAdapter(getContext());

        // Inflate layout contents
        LayoutInflater.from(getContext()).inflate(R.layout.app_library_page, this, true);

        mContentContainer = findViewById(R.id.app_library_content_container);
        mSearchContainer = findViewById(R.id.app_library_search_container);
        mSearchInput = findViewById(R.id.app_library_search_input);
        mDashboardView = findViewById(R.id.app_library_dashboard);
        mSearchResultsView = findViewById(R.id.app_library_search_results);
        mExpansionOverlay = findViewById(R.id.app_library_expansion_overlay);

        mDashboardView.setAdapter(mDashboardAdapter);

        // Bind update listener to AppLibraryModel
        mAppLibraryModel.addListener(() -> {
            Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
            for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
            }
            mDashboardAdapter.updateData(newData);
        });

        // Search Input listener
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup updates from AllAppsStore
        post(this::refreshApps);
    }

    public void updateApps(List<AppInfo> apps) {
        if (apps != null && mAppLibraryModel != null) {
            android.widget.Toast.makeText(getContext(), "AppLibraryPage updateApps called with " + apps.size() + " apps", android.widget.Toast.LENGTH_LONG).show();
            android.util.Log.d("AppLibraryPage", "updateApps called with " + apps.size() + " apps");
            mAppLibraryModel.updateApps(apps);
        }
    }

    private boolean mAppsStoreListenerRegistered = false;

    public void refreshApps() {
        android.util.Log.d("AppLibraryPage", "refreshApps called");
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {
            
            android.util.Log.d("AppLibraryPage", "refreshApps: launcher and appsStore are valid");

            // Register the AppsStore listener exactly once
            if (!mAppsStoreListenerRegistered) {
                android.util.Log.d("AppLibraryPage", "Registering AppsStore update listener");
                mAppsStoreListenerRegistered = true;
                launcher.getAppsView().getAppsStore().addUpdateListener(() -> {
                    AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
                    android.util.Log.d("AppLibraryPage", "AppsStore update listener fired with " + (apps != null ? apps.length : "null") + " apps");
                    if (apps != null) {
                        updateApps(Arrays.asList(apps));
                    }
                });
            }

            // Always sync the current app list immediately
            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            android.util.Log.d("AppLibraryPage", "Manual sync in refreshApps found " + (apps != null ? apps.length : "null") + " apps");
            if (apps != null && apps.length > 0) {
                updateApps(Arrays.asList(apps));
            }
        } else {
            android.util.Log.d("AppLibraryPage", "refreshApps: launcher, appsView, or appsStore is null!");
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            mSearchResultsView.setVisibility(GONE);
            mDashboardView.setVisibility(VISIBLE);
        } else {
            mDashboardView.setVisibility(GONE);
            mSearchResultsView.setVisibility(VISIBLE);
            // Search implementation will filter the list and rebind
        }
    }

    public void saveScrollState() {
        if (mDashboardView != null) {
            mSavedScrollY = mDashboardView.computeVerticalScrollOffset();
        }
    }

    public void restoreScrollState() {
        if (mDashboardView != null && mSavedScrollY > 0) {
            mDashboardView.scrollBy(0, mSavedScrollY);
        }
    }
}
