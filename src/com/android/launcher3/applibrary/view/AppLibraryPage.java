package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.model.data.AppInfo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppLibraryPage extends FrameLayout implements Insettable {

    private View mContentContainer;
    private View mSearchContainer;
    private EditText mSearchInput;
    private TextView mSearchCancel;
    private AppLibraryDashboardView mDashboardView;
    private AppLibraryDashboardAdapter mDashboardAdapter;
    private FrameLayout mSearchResultsContainer;
    private RecyclerView mSearchResultsView;
    private AppLibrarySearchAdapter mSearchAdapter;
    private AppLibraryIndexScrollerView mIndexScroller;
    private FrameLayout mExpansionOverlay;
    private View mSearchBackdrop;
    private AppLibraryOverlayGlassView mSearchBackdropGlass;
    private AppLibrarySearchGlassView mSearchGlass;

    private AppLibraryModel mAppLibraryModel;
    private int mSavedScrollY = 0;
    private boolean mInSearchMode = false;
    private boolean mIsClearingSearch = false;
    private boolean mAppsStoreListenerRegistered = false;
    private ValueAnimator mTransitionAnimator;

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
        mSearchCancel = findViewById(R.id.app_library_search_cancel);
        mSearchGlass = findViewById(R.id.app_library_search_glass);
        mDashboardView = findViewById(R.id.app_library_dashboard);
        mSearchResultsContainer = findViewById(R.id.app_library_search_results_container);
        mSearchResultsView = findViewById(R.id.app_library_search_results);
        mIndexScroller = findViewById(R.id.app_library_index_scroller);
        mExpansionOverlay = findViewById(R.id.app_library_expansion_overlay);
        mSearchBackdrop = findViewById(R.id.app_library_search_backdrop);
        mSearchBackdropGlass = findViewById(R.id.app_library_search_backdrop_glass);
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setOnClickListener(v -> exitSearchMode());
        }

        mDashboardView.setAdapter(mDashboardAdapter);
        mDashboardView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                invalidateGlassSurfaces();
            }
        });

        Launcher launcher = Launcher.getLauncher(getContext());
        mSearchAdapter = new AppLibrarySearchAdapter(launcher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setAdapter(mSearchAdapter);

        ViewCompat.setOnApplyWindowInsetsListener(mSearchResultsView, (v, windowInsets) -> {
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottomPad = Math.max(imeInsets.bottom, navInsets.bottom);
            int baseBottom = (int) (48 * getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(baseBottom, bottomPad + (int) (12 * getResources().getDisplayMetrics().density)));
            return windowInsets;
        });

        // Bind update listener to AppLibraryModel
        mAppLibraryModel.addListener(() -> {
            Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
            for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
            }
            mDashboardAdapter.updateData(newData);
        });

        // Search Container & Input focus & click listeners
        mSearchContainer.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        mSearchInput.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        mSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterSearchMode();
            }
        });

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

        mSearchCancel.setOnClickListener(v -> exitSearchMode());

        mIndexScroller.setOnSectionSelectedListener(section -> {
            if (section != null && section.length() > 0) {
                int pos = mSearchAdapter.getPositionForSection(section.charAt(0));
                if (pos >= 0) {
                    RecyclerView.LayoutManager lm = mSearchResultsView.getLayoutManager();
                    if (lm instanceof LinearLayoutManager) {
                        ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
                    } else {
                        mSearchResultsView.scrollToPosition(pos);
                    }
                }
            }
        });

        // Setup updates from AllAppsStore
        post(this::refreshApps);
    }

    public void updateApps(List<AppInfo> apps) {
        if (apps != null) {
            if (mAppLibraryModel != null) {
                mAppLibraryModel.updateApps(apps);
            }
            if (mSearchAdapter != null) {
                mSearchAdapter.setApps(apps);
            }
        }
    }

    public void refreshApps() {
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {

            // Register the AppsStore listener exactly once
            if (!mAppsStoreListenerRegistered) {
                mAppsStoreListenerRegistered = true;
                launcher.getAppsView().getAppsStore().addUpdateListener(() -> {
                    AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
                    if (apps != null) {
                        updateApps(Arrays.asList(apps));
                    }
                });
            }

            // Always sync the current app list immediately
            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            if (apps != null && apps.length > 0) {
                updateApps(Arrays.asList(apps));
            }
        }
    }

    public void enterSearchMode() {
        if (mInSearchMode) return;
        mInSearchMode = true;
        refreshApps();

        if (mTransitionAnimator != null) {
            mTransitionAnimator.cancel();
        }

        // Prepare Search Backdrop
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(VISIBLE);
            mSearchBackdrop.setAlpha(0f);
            if (mSearchBackdropGlass != null) {
                mSearchBackdropGlass.ensureBound();
            }
        }

        // Prepare Search Results Container
        mSearchResultsContainer.setVisibility(VISIBLE);
        mSearchResultsContainer.setAlpha(0f);
        final float slideOffsetPx = 28f * getResources().getDisplayMetrics().density;
        mSearchResultsContainer.setTranslationY(slideOffsetPx);

        String query = mSearchInput.getText() != null ? mSearchInput.getText().toString() : "";
        mSearchAdapter.filter(query);
        mIndexScroller.setVisibility((query.trim().isEmpty()) ? VISIBLE : GONE);

        // Prepare Cancel Button
        mSearchCancel.setVisibility(VISIBLE);
        mSearchCancel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetCancelWidth = Math.max(mSearchCancel.getMeasuredWidth(),
                (int) (60 * getResources().getDisplayMetrics().density));
        mSearchCancel.setAlpha(0f);
        mSearchCancel.setTranslationX(targetCancelWidth * 0.4f);

        ViewGroup.LayoutParams cancelLp = mSearchCancel.getLayoutParams();
        cancelLp.width = 0;
        mSearchCancel.setLayoutParams(cancelLp);

        // Setup Dashboard for exit transition (scale downwards from top)
        mDashboardView.setVisibility(VISIBLE);
        mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
        mDashboardView.setPivotY(0f);

        mTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mTransitionAnimator.setDuration(260);
        mTransitionAnimator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f));
        mTransitionAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            // Cancel button width, alpha, and translation
            ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
            lp.width = (int) (targetCancelWidth * progress);
            mSearchCancel.setLayoutParams(lp);
            mSearchCancel.setAlpha(progress);
            mSearchCancel.setTranslationX((1f - progress) * (targetCancelWidth * 0.4f));

            // Search Backdrop fade-in
            if (mSearchBackdrop != null) {
                mSearchBackdrop.setAlpha(progress);
            }

            // Dashboard fade-out & subtle scale down
            mDashboardView.setAlpha(1f - progress);
            float dashScale = 1f - (0.04f * progress);
            mDashboardView.setScaleX(dashScale);
            mDashboardView.setScaleY(dashScale);

            // Search Results fade-in & upward slide
            mSearchResultsContainer.setAlpha(progress);
            mSearchResultsContainer.setTranslationY((1f - progress) * slideOffsetPx);
        });
        mTransitionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mSearchCancel.setLayoutParams(lp);
                mSearchCancel.setAlpha(1f);
                mSearchCancel.setTranslationX(0f);

                mDashboardView.setVisibility(GONE);
                mDashboardView.setScaleX(1f);
                mDashboardView.setScaleY(1f);

                mSearchResultsContainer.setAlpha(1f);
                mSearchResultsContainer.setTranslationY(0f);
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(1f);
                }
            }
        });
        mTransitionAnimator.start();
    }

    public void exitSearchMode() {
        if (!mInSearchMode) return;
        mIsClearingSearch = true;
        try {
            mInSearchMode = false;
            mSearchInput.setText("");
            mSearchInput.clearFocus();
            hideKeyboard();

            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null && launcher.getWorkspace() != null) {
                launcher.getWorkspace().onAppLibrarySearchExited();
            }

            if (mTransitionAnimator != null) {
                mTransitionAnimator.cancel();
            }

            // Restore Dashboard to VISIBLE behind the fading search results
            mDashboardView.setVisibility(VISIBLE);
            mDashboardView.setAlpha(0f);
            mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
            mDashboardView.setPivotY(0f);
            mDashboardView.setScaleX(0.96f);
            mDashboardView.setScaleY(0.96f);

            final float slideOffsetPx = 20f * getResources().getDisplayMetrics().density;
            final int startCancelWidth = mSearchCancel.getWidth() > 0
                    ? mSearchCancel.getWidth()
                    : (int) (60 * getResources().getDisplayMetrics().density);

            mTransitionAnimator = ValueAnimator.ofFloat(1f, 0f);
            mTransitionAnimator.setDuration(240);
            mTransitionAnimator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f));
            mTransitionAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();

                // Cancel button width, alpha, and translation
                ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
                lp.width = (int) (startCancelWidth * progress);
                mSearchCancel.setLayoutParams(lp);
                mSearchCancel.setAlpha(progress);
                mSearchCancel.setTranslationX((1f - progress) * (startCancelWidth * 0.4f));

                // Search Backdrop fade-out
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(progress);
                }

                // Dashboard fade-in & scale up
                mDashboardView.setAlpha(1f - progress);
                float dashScale = 0.96f + (0.04f * (1f - progress));
                mDashboardView.setScaleX(dashScale);
                mDashboardView.setScaleY(dashScale);

                // Search Results fade-out
                mSearchResultsContainer.setAlpha(progress);
                mSearchResultsContainer.setTranslationY((1f - progress) * slideOffsetPx);
            });
            mTransitionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSearchCancel.setVisibility(GONE);
                    ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    mSearchCancel.setLayoutParams(lp);
                    mSearchCancel.setAlpha(1f);
                    mSearchCancel.setTranslationX(0f);

                    if (mSearchBackdrop != null) {
                        mSearchBackdrop.setVisibility(GONE);
                        mSearchBackdrop.setAlpha(1f);
                    }

                    mSearchResultsContainer.setVisibility(GONE);
                    mSearchResultsContainer.setAlpha(1f);
                    mSearchResultsContainer.setTranslationY(0f);

                    mDashboardView.setAlpha(1f);
                    mDashboardView.setScaleX(1f);
                    mDashboardView.setScaleY(1f);
                }
            });
            mTransitionAnimator.start();
        } finally {
            mIsClearingSearch = false;
        }
    }

    public boolean canHandleBack() {
        return mInSearchMode;
    }

    public boolean handleBack() {
        if (mInSearchMode) {
            exitSearchMode();
            return true;
        }
        return false;
    }

    public void showKeyboard() {
        mSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void performSearch(String query) {
        if (mIsClearingSearch) return;
        if (!mInSearchMode && query != null && !query.isEmpty()) {
            enterSearchMode();
        }
        mSearchAdapter.filter(query);
        // Show alphabet index scroller only when viewing full list (query is empty)
        mIndexScroller.setVisibility((query == null || query.trim().isEmpty()) ? VISIBLE : GONE);
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

    public void invalidateGlassSurfaces() {
        if (mSearchGlass != null && mSearchGlass.getVisibility() == VISIBLE) {
            mSearchGlass.invalidate();
        }
        if (mSearchBackdropGlass != null && mSearchBackdropGlass.getVisibility() == VISIBLE) {
            mSearchBackdropGlass.invalidate();
        }
        if (mDashboardView != null) {
            for (int i = 0; i < mDashboardView.getChildCount(); i++) {
                View child = mDashboardView.getChildAt(i);
                if (child instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) child;
                    for (int j = 0; j < group.getChildCount(); j++) {
                        View sub = group.getChildAt(j);
                        if (sub instanceof CategoryCardView) {
                            AppLibraryCategoryGlassView gv = ((CategoryCardView) sub).getGlassView();
                            if (gv != null) {
                                gv.invalidate();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setInsets(Rect insets) {
        if (mContentContainer != null && insets != null) {
            int topPad = insets.top > 0
                    ? insets.top + (int) (8 * getResources().getDisplayMetrics().density)
                    : (int) (36 * getResources().getDisplayMetrics().density);
            mContentContainer.setPadding(
                    mContentContainer.getPaddingLeft(),
                    topPad,
                    mContentContainer.getPaddingRight(),
                    mContentContainer.getPaddingBottom()
            );
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            Insets statusBarInsets = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
                    .getInsets(WindowInsetsCompat.Type.statusBars());
            if (statusBarInsets.top > 0) {
                setInsets(new Rect(0, statusBarInsets.top, 0, 0));
            }
        }
    }
}
