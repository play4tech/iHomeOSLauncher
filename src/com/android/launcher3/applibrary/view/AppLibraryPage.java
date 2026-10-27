package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.PackageUserKey;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppLibraryPage extends FrameLayout implements Insettable {

    private View mContentContainer;
    private View mSearchRow;
    private View mSearchContainer;
    private View mSearchContent;
    private ExtendedEditText mSearchInput;
    private TextView mSearchQueryOverlay;
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
    private int mSavedScrollPos = RecyclerView.NO_POSITION;
    private int mSavedScrollOffset = 0;
    private final int[] mTempWindowPos = new int[2];
    private static int sDoseResponseOverride = -1;
    private boolean mInSearchMode = false;
    private boolean mIsClearingSearch = false;
    private boolean mAppsStoreListenerRegistered = false;
    private ValueAnimator mTransitionAnimator;
    private OnBackInvokedCallback mBackCallback;
    private long mLastSearchExitedTime = 0;
    private boolean mImeWasVisible = false;
    private boolean mInternalKeyboardHide = false;
    private boolean mBackGestureActive = false;
    private final ViewTreeObserver.OnGlobalLayoutListener mImeLayoutListener = this::checkRootImeVisibility;
    private final com.android.launcher3.allapps.AllAppsStore.OnUpdateListener mAppsStoreUpdateListener = () -> {
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {
            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            if (apps != null && apps.length > 0) {
                updateApps(Arrays.asList(apps));
            }
        }
    };
    private final com.android.launcher3.applibrary.model.AppLibraryModel.AppLibraryUpdateListener mAppLibraryUpdateListener = () -> {
        if (mAppLibraryModel == null || mDashboardAdapter == null) return;
        Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
        for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
            newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
        }
        mDashboardAdapter.updateData(newData);
    };

    /** Listener that only runs its end logic if the animator was NOT cancelled. */
    private abstract static class EndOnlyListener extends AnimatorListenerAdapter {
        private boolean mCancelled;
        @Override public void onAnimationCancel(Animator a) { mCancelled = true; }
        @Override public void onAnimationEnd(Animator a) { if (!mCancelled) onFinished(); }
        abstract void onFinished();
    }

    /** Stop every ViewPropertyAnimator a back-gesture cancel may have left running. */
    private void cancelViewAnimators() {
        if (mDashboardView != null) mDashboardView.animate().cancel();
        if (mSearchResultsContainer != null) mSearchResultsContainer.animate().cancel();
        if (mSearchBackdrop != null) mSearchBackdrop.animate().cancel();
        if (mSearchQueryOverlay != null) mSearchQueryOverlay.animate().cancel();
        if (mSearchInput != null) mSearchInput.animate().cancel();
    }

    /** The one place that guarantees the category grid is shown. */
    private void settleDashboardVisible() {
        if (mDashboardView == null) return;
        mDashboardView.animate().cancel();
        mDashboardView.setVisibility(VISIBLE);
        mDashboardView.setAlpha(1f);
        mDashboardView.setScaleX(1f);
        mDashboardView.setScaleY(1f);
    }

    /** Final state of "search open". */
    private void applySearchSteadyState() {
        if (mSearchContent != null) mSearchContent.setTranslationX(0f);

        if (mSearchQueryOverlay != null) {
            mSearchQueryOverlay.setVisibility(GONE);
            mSearchQueryOverlay.setText("");
        }
        if (mSearchInput != null) {
            mSearchInput.setAlpha(1f);
        }

        if (mSearchCancel.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) mSearchCancel.getLayoutParams();
            mlp.setMarginEnd(0);
            mlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            mSearchCancel.setLayoutParams(mlp);
        }
        mSearchCancel.setVisibility(VISIBLE);
        mSearchCancel.setAlpha(1f);
        mSearchCancel.setTranslationX(0f);

        mDashboardView.animate().cancel();
        mDashboardView.setVisibility(GONE);
        mDashboardView.setAlpha(1f);      // never leave a hidden view at alpha 0
        mDashboardView.setScaleX(1f);
        mDashboardView.setScaleY(1f);

        mSearchResultsContainer.setVisibility(VISIBLE);
        mSearchResultsContainer.setAlpha(1f);
        mSearchResultsContainer.setTranslationY(0f);
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(VISIBLE);
            mSearchBackdrop.setAlpha(1f);
        }
    }

    /** Final state of "browsing the App Library". Called when an exit animation completes. */
    private void applyBrowseSteadyState(float targetShift) {
        if (mInSearchMode) return;        // user re-entered search in the meantime

        if (mSearchResultsView != null) {
            mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        }
        if (mSearchQueryOverlay != null) {
            mSearchQueryOverlay.setVisibility(GONE);
            mSearchQueryOverlay.setText("");
        }
        mIsClearingSearch = true;
        try {
            mSearchInput.setText("");
            mSearchInput.clearFocus();
        } finally {
            mIsClearingSearch = false;
        }
        if (mSearchInput != null) {
            mSearchInput.setAlpha(1f);
            boolean isDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
            mSearchInput.setHintTextColor(isDarkText ? 0x99000000 : 0xD9FFFFFF);
        }
        if (mSearchContent != null) mSearchContent.setTranslationX(targetShift);

        mSearchCancel.setVisibility(GONE);
        if (mSearchCancel.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) mSearchCancel.getLayoutParams();
            mlp.setMarginEnd(0);
            mlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            mSearchCancel.setLayoutParams(mlp);
        }
        mSearchCancel.setAlpha(1f);
        mSearchCancel.setTranslationX(0f);

        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(GONE);
            mSearchBackdrop.setAlpha(1f);
        }
        mSearchResultsContainer.setVisibility(GONE);
        mSearchResultsContainer.setAlpha(1f);
        mSearchResultsContainer.setTranslationY(0f);

        settleDashboardVisible();         // <- the guarantee
    }

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
        mAppLibraryModel = AppLibraryModel.getInstance(getContext());
        mDashboardAdapter = new AppLibraryDashboardAdapter(getContext());

        // Inflate layout contents
        LayoutInflater.from(getContext()).inflate(R.layout.app_library_page, this, true);

        mContentContainer = findViewById(R.id.app_library_content_container);
        mSearchRow = findViewById(R.id.app_library_search_row);
        if (mSearchRow != null) {
            float rowElevation = 12f * getResources().getDisplayMetrics().density;
            mSearchRow.setElevation(rowElevation);
            mSearchRow.setTranslationZ(rowElevation);
        }
        mSearchContainer = findViewById(R.id.app_library_search_container);
        mSearchContent = findViewById(R.id.app_library_search_content);
        mSearchInput = findViewById(R.id.app_library_search_input);
        mSearchQueryOverlay = findViewById(R.id.app_library_search_query_overlay);
        mSearchCancel = findViewById(R.id.app_library_search_cancel);
        android.widget.ImageView searchIcon = findViewById(R.id.app_library_search_icon);
        boolean isDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
        int textColor = Themes.getAttrColor(getContext(), R.attr.workspaceTextColor);
        if (mSearchInput != null) {
            mSearchInput.setTextColor(textColor);
            mSearchInput.setHintTextColor(isDarkText ? 0x99000000 : 0xD9FFFFFF);
        }
        if (mSearchCancel != null) {
            mSearchCancel.setTextColor(textColor);
        }
        if (searchIcon != null) {
            searchIcon.setImageTintList(android.content.res.ColorStateList.valueOf(isDarkText ? 0x99000000 : 0xD9FFFFFF));
        }
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
        if (mAppLibraryModel != null && !mAppLibraryModel.getOrderedCategories().isEmpty()) {
            Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> initialData = new LinkedHashMap<>();
            for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                initialData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
            }
            mDashboardAdapter.updateData(initialData);
        }
        mDashboardView.setOnOverscrollListener(translationY -> {
            invalidateCategoryPodsOnly();
        });
        mDashboardView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    invalidateCategoryPodsOnly();
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    invalidateCategoryPodsOnly();
                }
            }
        });

        Launcher launcher = Launcher.getLauncher(getContext());
        mSearchAdapter = new AppLibrarySearchAdapter(launcher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setHasFixedSize(true);
        mSearchResultsView.setItemViewCacheSize(20);
        mSearchResultsView.setItemAnimator(null);
        mSearchResultsView.setAdapter(mSearchAdapter);
        mSearchResultsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(mSearchResultsView, (v, windowInsets) -> {
            handleImeVisibility(windowInsets.isVisible(WindowInsetsCompat.Type.ime()));
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottomPad = Math.max(imeInsets.bottom, navInsets.bottom);
            int baseBottom = (int) (48 * getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(baseBottom, bottomPad + (int) (12 * getResources().getDisplayMetrics().density)));
            return windowInsets;
        });



        // Search Container & Input focus & click listeners
        mSearchContainer.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        if (mSearchContent != null) {
            mSearchContent.setOnClickListener(v -> {
                enterSearchMode();
                showKeyboard();
            });
        }

        mSearchInput.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        mSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !mIsClearingSearch) {
                enterSearchMode();
            }
        });

        mSearchInput.setOnBackKeyListener(() -> {
            android.util.Log.d("BACK_DEBUG", "mSearchInput.onBackKey() called, mInSearchMode=" + mInSearchMode);
            if (mInSearchMode) {
                exitSearchMode(true);
                return true;
            }
            return false;
        });

        mSearchInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                android.util.Log.d("BACK_DEBUG", "mSearchInput.onKey(KEYCODE_BACK) called, action=" + event.getAction() + ", mInSearchMode=" + mInSearchMode);
                if (mInSearchMode) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        exitSearchMode(true);
                    }
                    return true;
                }
            }
            return false;
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
        if (launcher != null && mContentContainer != null
                && AppLibraryCategoryExpandedView.getOpen(launcher) == null) {
            mContentContainer.setAlpha(1f);
            mContentContainer.setScaleX(1f);
            mContentContainer.setScaleY(1f);
        }
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {

            if (!mAppsStoreListenerRegistered) {
                mAppsStoreListenerRegistered = true;
                launcher.getAppsView().getAppsStore().addUpdateListener(mAppsStoreUpdateListener);
            }

            // Immediately bind existing categorized data if adapter is empty
            if (mAppLibraryModel != null && !mAppLibraryModel.getOrderedCategories().isEmpty()
                    && mDashboardAdapter != null && mDashboardAdapter.getItemCount() == 0) {
                Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
                for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                    newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
                }
                mDashboardAdapter.updateData(newData);
            }

            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            if (apps != null && apps.length > 0) {
                if (mSearchAdapter != null && mSearchAdapter.getItemCount() == 0) {
                    mSearchAdapter.setApps(Arrays.asList(apps));
                }
                // Only trigger updateApps if model has not categorized yet
                if (mAppLibraryModel != null && mAppLibraryModel.getOrderedCategories().isEmpty()) {
                    updateApps(Arrays.asList(apps));
                }
            }
        }
    }

    public void enterSearchMode() {
        Log.d("AppLib", "enterSearchMode inSearch=" + mInSearchMode
                + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
        if (mInSearchMode) return;
        mInSearchMode = true;
        cancelViewAnimators();
        if (mSearchQueryOverlay != null) {
            mSearchQueryOverlay.setVisibility(GONE);
            mSearchQueryOverlay.setText("");
        }
        if (mSearchInput != null) {
            mSearchInput.setAlpha(1f);
        }
        if (mSearchResultsView != null) mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        refreshApps();
        registerBackCallback();
        showKeyboard();
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null) {
            launcher.onDragLayerHierarchyChanged();
            boolean isDarkText = Themes.getAttrBoolean(launcher, R.attr.isWorkspaceDarkText);
            int textColor = Themes.getAttrColor(launcher, R.attr.workspaceTextColor);
            mSearchInput.setTextColor(textColor);
            mSearchInput.setHintTextColor(isDarkText ? 0x99000000 : 0x99FFFFFF);
            mSearchCancel.setTextColor(textColor);
            ImageView searchIcon = findViewById(R.id.app_library_search_icon);
            if (searchIcon != null) {
                searchIcon.setImageTintList(ColorStateList.valueOf(textColor));
            }
        }

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

        // Prepare Cancel Button with negative end margin so search bar starts at 100% full width
        mSearchCancel.setVisibility(VISIBLE);
        mSearchCancel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetCancelWidth = Math.max(mSearchCancel.getMeasuredWidth(),
                (int) (60 * getResources().getDisplayMetrics().density));
        final ViewGroup.MarginLayoutParams cancelEnterLp = (ViewGroup.MarginLayoutParams) mSearchCancel.getLayoutParams();
        cancelEnterLp.width = targetCancelWidth;
        cancelEnterLp.setMarginEnd(-targetCancelWidth);
        mSearchCancel.setLayoutParams(cancelEnterLp);
        mSearchCancel.setAlpha(0f);
        mSearchCancel.setTranslationX(0f);

        // Setup Dashboard for exit transition (scale downwards from top)
        mDashboardView.setVisibility(VISIBLE);
        mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
        mDashboardView.setPivotY(0f);

        final float startShift = mSearchContent != null ? mSearchContent.getTranslationX() : 0f;

        mTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mTransitionAnimator.setDuration(260);
        mTransitionAnimator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f));
        mTransitionAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            // Search Content slide to left
            if (mSearchContent != null) {
                mSearchContent.setTranslationX((1f - progress) * startShift);
            }

            // Cancel button alpha and margin animation (smoothly shrinks search bar length)
            mSearchCancel.setAlpha(progress);
            cancelEnterLp.setMarginEnd((int) (-targetCancelWidth * (1f - progress)));
            mSearchCancel.setLayoutParams(cancelEnterLp);

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
        mTransitionAnimator.addListener(new EndOnlyListener() {
            @Override
            void onFinished() {
                if (mInSearchMode) applySearchSteadyState();
            }
        });
        mTransitionAnimator.start();
    }

    public void exitSearchMode() {
        exitSearchMode(true);
    }

    public void exitSearchMode(boolean animate) {
        Log.d("AppLib", "exitSearchMode(animate=" + animate + ") inSearch=" + mInSearchMode
                + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
        if (!mInSearchMode) return;
        mIsClearingSearch = true;
        try {
            mInSearchMode = false;
            mLastSearchExitedTime = SystemClock.uptimeMillis();
            hideKeyboard();

            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null) {
                if (launcher.getWorkspace() != null) {
                    launcher.getWorkspace().onAppLibrarySearchExited();
                }
                launcher.onDragLayerHierarchyChanged();
            }

            post(this::unregisterBackCallback);

            if (mTransitionAnimator != null) {
                mTransitionAnimator.cancel();
            }
            cancelViewAnimators();     // kill any onBackCancelled / onBackProgressed leftovers

            final float targetShift = calculateSearchCenteredShift();
            final String outgoingQuery = mSearchInput != null && mSearchInput.getText() != null
                    ? mSearchInput.getText().toString() : "";
            final boolean hasOutgoingQuery = !outgoingQuery.isEmpty();

            if (!animate) {
                if (mSearchQueryOverlay != null) {
                    mSearchQueryOverlay.setVisibility(GONE);
                    mSearchQueryOverlay.setText("");
                }
                mSearchInput.setText("");
                mSearchInput.setAlpha(1f);
                mSearchInput.clearFocus();
                boolean isDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
                mSearchInput.setHintTextColor(isDarkText ? 0x99000000 : 0xD9FFFFFF);
                if (mSearchContent != null) {
                    mSearchContent.setTranslationX(targetShift);
                }

                mSearchCancel.setVisibility(GONE);
                if (mSearchCancel.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) mSearchCancel.getLayoutParams();
                    mlp.setMarginEnd(0);
                    mlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    mSearchCancel.setLayoutParams(mlp);
                }
                mSearchCancel.setAlpha(1f);
                mSearchCancel.setTranslationX(0f);

                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setVisibility(GONE);
                    mSearchBackdrop.setAlpha(1f);
                }

                mSearchResultsContainer.setVisibility(GONE);
                mSearchResultsContainer.setAlpha(1f);
                mSearchResultsContainer.setTranslationY(0f);

                settleDashboardVisible();
                return;
            }

            // Prepare cross-fade: outgoing typed query starts on overlay at 100% alpha;
            // mSearchInput is immediately cleared to reveal the centered "App Library" hint,
            // starting at 0% alpha and cross-fading smoothly in.
            if (hasOutgoingQuery && mSearchQueryOverlay != null) {
                mSearchQueryOverlay.setText(outgoingQuery);
                mSearchQueryOverlay.setTextColor(mSearchInput.getCurrentTextColor());
                mSearchQueryOverlay.setPaddingRelative(
                        mSearchInput.getPaddingStart(),
                        mSearchInput.getPaddingTop(),
                        mSearchInput.getPaddingEnd(),
                        mSearchInput.getPaddingBottom()
                );
                mSearchQueryOverlay.setTypeface(mSearchInput.getTypeface());
                mSearchQueryOverlay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, mSearchInput.getTextSize());
                mSearchQueryOverlay.setVisibility(VISIBLE);
                mSearchQueryOverlay.setAlpha(1f);
            }

            mSearchInput.setText("");
            mSearchInput.clearFocus();
            boolean isDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
            mSearchInput.setHintTextColor(isDarkText ? 0x99000000 : 0xD9FFFFFF);
            if (hasOutgoingQuery) {
                mSearchInput.setAlpha(0f);
            } else {
                mSearchInput.setAlpha(1f);
            }

            if (mSearchResultsView != null) {
                mSearchResultsView.stopScroll();
                mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
            }

            // Capture current (potentially scrubbed) states for smooth handoff
            final float startResultsAlpha = mSearchResultsContainer != null ? mSearchResultsContainer.getAlpha() : 1f;
            final float startResultsTy = mSearchResultsContainer != null ? mSearchResultsContainer.getTranslationY() : 0f;
            final float startBackdropAlpha = mSearchBackdrop != null ? mSearchBackdrop.getAlpha() : 1f;
            final boolean dashAlreadyVisible = mDashboardView.getVisibility() == VISIBLE && mDashboardView.getAlpha() > 0f;
            final float startDashAlpha = dashAlreadyVisible ? mDashboardView.getAlpha() : 0f;
            final float startDashScale = dashAlreadyVisible ? mDashboardView.getScaleX() : 0.96f;

            // Restore Dashboard to VISIBLE behind the fading search results
            mDashboardView.setVisibility(VISIBLE);
            mDashboardView.setAlpha(startDashAlpha);
            mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
            mDashboardView.setPivotY(0f);
            mDashboardView.setScaleX(startDashScale);
            mDashboardView.setScaleY(startDashScale);

            final float slideOffsetPx = 20f * getResources().getDisplayMetrics().density;
            final int startCancelWidth = mSearchCancel.getWidth() > 0
                    ? mSearchCancel.getWidth()
                    : (int) (60 * getResources().getDisplayMetrics().density);

            final ViewGroup.MarginLayoutParams cancelExitLp = (ViewGroup.MarginLayoutParams) mSearchCancel.getLayoutParams();
            cancelExitLp.width = startCancelWidth;
            cancelExitLp.setMarginEnd(0);
            mSearchCancel.setLayoutParams(cancelExitLp);
            mSearchCancel.setTranslationX(0f);

            final float exitStartShift = mSearchContent != null ? mSearchContent.getTranslationX() : 0f;

            mTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
            mTransitionAnimator.setDuration(250);
            mTransitionAnimator.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
            mTransitionAnimator.addUpdateListener(animation -> {
                float p = (float) animation.getAnimatedValue();
                float inv = 1f - p;

                // Smoothly expand search bar length back to full width by collapsing Cancel button margin
                cancelExitLp.setMarginEnd((int) (-startCancelWidth * p));
                mSearchCancel.setLayoutParams(cancelExitLp);
                mSearchCancel.setAlpha(inv);

                // Search Content slide back to center
                if (mSearchContent != null) {
                    mSearchContent.setTranslationX(exitStartShift + (targetShift - exitStartShift) * p);
                }

                // Search Backdrop fade-out
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(startBackdropAlpha * inv);
                }

                // Dashboard fade-in & scale up
                mDashboardView.setAlpha(startDashAlpha + (1f - startDashAlpha) * p);
                float dashScale = startDashScale + (1f - startDashScale) * p;
                mDashboardView.setScaleX(dashScale);
                mDashboardView.setScaleY(dashScale);

                // Search Results fade-out
                mSearchResultsContainer.setAlpha(startResultsAlpha * inv);
                mSearchResultsContainer.setTranslationY(startResultsTy + (slideOffsetPx - startResultsTy) * p);

                // Smooth cross-fade between outgoing typed query and incoming "App Library" hint
                if (hasOutgoingQuery && mSearchQueryOverlay != null) {
                    float queryAlpha = Math.max(0f, 1f - p * 1.8f);
                    mSearchQueryOverlay.setAlpha(queryAlpha);
                    float hintAlpha = Math.min(1f, p * 1.5f);
                    mSearchInput.setAlpha(hintAlpha);
                }
            });
            mTransitionAnimator.addListener(new EndOnlyListener() {
                @Override
                void onFinished() {
                    if (mSearchQueryOverlay != null) {
                        mSearchQueryOverlay.setVisibility(GONE);
                        mSearchQueryOverlay.setText("");
                    }
                    if (mSearchInput != null) {
                        mSearchInput.setAlpha(1f);
                    }
                    applyBrowseSteadyState(targetShift);
                }
            });
            mTransitionAnimator.start();
        } finally {
            mIsClearingSearch = false;
        }
    }

    public boolean isInSearchMode() {
        return mInSearchMode;
    }

    public boolean canHandleBack() {
        return mInSearchMode;
    }

    public boolean wasRecentlyInSearchMode() {
        return SystemClock.uptimeMillis() - mLastSearchExitedTime < 500;
    }

    public boolean handleBack() {
        if (mInSearchMode) {
            exitSearchMode(true);
            return true;
        }
        return wasRecentlyInSearchMode();
    }

    private void registerBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback == null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    if (launcher != null) {
                        dispatcher = launcher.getOnBackInvokedDispatcher();
                    }
                }
                if (dispatcher != null) {
                    if (Utilities.ATLEAST_U) {
                        mBackCallback = getOnBackAnimationCallback();
                    } else {
                        mBackCallback = this::exitSearchMode;
                    }
                    dispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                }
            } catch (Throwable t) {
                Log.w("AppLibraryPage", "Failed to register OnBackInvokedCallback", t);
            }
        }
    }

    private void unregisterBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback != null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    if (launcher != null) {
                        dispatcher = launcher.getOnBackInvokedDispatcher();
                    }
                }
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
            } catch (Throwable ignored) {}
            mBackCallback = null;
        }
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public OnBackAnimationCallback getOnBackAnimationCallback() {
        return new OnBackAnimationCallback() {
            @Override
            public void onBackStarted(@NonNull BackEvent backEvent) {
                Log.d("AppLib", "onBackStarted inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                if (!mInSearchMode) return;                  // exit already owns the views
                mBackGestureActive = true;
                if (mTransitionAnimator != null && mTransitionAnimator.isRunning()) {
                    mTransitionAnimator.cancel();            // EndOnlyListener: no end logic runs
                }
                cancelViewAnimators();
                if (mSearchResultsView != null) {
                    mSearchResultsView.stopScroll();
                    mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
                }
                // The IME hide below is ours: don't let handleImeVisibility() start a 2nd exit.
                if (mImeWasVisible) mInternalKeyboardHide = true;
                hideKeyboard();

                mDashboardView.setVisibility(VISIBLE);
                mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
                mDashboardView.setPivotY(0f);
            }

            @Override
            public void onBackProgressed(@NonNull BackEvent backEvent) {
                if (!mInSearchMode) return;                  // late event after exit started
                float progress = backEvent.getProgress();
                if (mSearchResultsContainer != null) {
                    mSearchResultsContainer.setAlpha(1f - 0.45f * progress);
                    float slideOffsetPx = 20f * getResources().getDisplayMetrics().density;
                    mSearchResultsContainer.setTranslationY(progress * slideOffsetPx);
                }
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(1f - 0.35f * progress);
                }
                if (mDashboardView != null) {
                    mDashboardView.setVisibility(VISIBLE);
                    mDashboardView.setAlpha(0.6f * progress);
                    float dashScale = 0.96f + (0.04f * progress);
                    mDashboardView.setScaleX(dashScale);
                    mDashboardView.setScaleY(dashScale);
                }
            }

            @Override
            public void onBackCancelled() {
                Log.d("AppLib", "onBackCancelled inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                mBackGestureActive = false;
                // If exit already started (late cancel from unregisterBackCallback), it owns
                // every view. Touching the dashboard here is what leaves it at alpha 0.
                if (!mInSearchMode) return;

                if (mSearchResultsView != null) {
                    mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
                }
                if (mSearchResultsContainer != null) {
                    mSearchResultsContainer.animate().alpha(1f).translationY(0f)
                            .setDuration(160).start();
                }
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.animate().alpha(1f).setDuration(160).start();
                }
                if (mDashboardView != null) {
                    mDashboardView.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                // withEndAction is NOT run if the animator is cancelled.
                                if (mInSearchMode) applySearchSteadyState();
                            }).start();
                }
            }

            @Override
            public void onBackInvoked() {
                Log.d("AppLib", "onBackInvoked inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                mBackGestureActive = false;
                exitSearchMode(true);
            }
        };
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            android.util.Log.d("BACK_DEBUG", "AppLibraryPage.dispatchKeyEvent(KEYCODE_BACK) called, action=" + event.getAction() + ", mInSearchMode=" + mInSearchMode);
            if (mInSearchMode) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    exitSearchMode(true);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void checkRootImeVisibility() {
        if (!mInSearchMode) return;
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            handleImeVisibility(rootInsets.isVisible(WindowInsets.Type.ime()));
        }
    }

    private void handleImeVisibility(boolean imeVisible) {
        if (mInSearchMode && mImeWasVisible && !imeVisible) {
            if (mInternalKeyboardHide) {
                mInternalKeyboardHide = false;
            } else {
                Log.d("BACK_DEBUG", "AppLibraryPage IME hidden externally while in search mode -> exiting search mode");
                mImeWasVisible = false;
                post(() -> {
                    if (mInSearchMode) {
                        exitSearchMode(true);
                    }
                });
                return;
            }
        }
        mImeWasVisible = imeVisible;
    }

    public void showKeyboard() {
        mInternalKeyboardHide = false;
        mSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideKeyboard() {
        if (mImeWasVisible) {
            mInternalKeyboardHide = true;
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void performSearch(String query) {
        if (mIsClearingSearch || !mInSearchMode) return;
        mSearchAdapter.filter(query);
        // Show alphabet index scroller only when viewing full list (query is empty)
        mIndexScroller.setVisibility((query == null || query.trim().isEmpty()) ? VISIBLE : GONE);
    }

    public void saveScrollState() {
        if (mDashboardView != null && mDashboardView.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) mDashboardView.getLayoutManager();
            mSavedScrollPos = glm.findFirstVisibleItemPosition();
            View firstChild = glm.findViewByPosition(mSavedScrollPos);
            mSavedScrollOffset = (firstChild != null) ? (firstChild.getTop() - mDashboardView.getPaddingTop()) : 0;
        }
    }

    public void restoreScrollState() {
        if (mDashboardView != null && mSavedScrollPos != RecyclerView.NO_POSITION
                && mDashboardView.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) mDashboardView.getLayoutManager();
            glm.scrollToPositionWithOffset(mSavedScrollPos, mSavedScrollOffset);
        }
    }

    public static void setDoseResponseOverride(int count) {
        sDoseResponseOverride = count;
    }

    public void invalidateCategoryPodsOnly() {
        if (mDashboardView == null) return;

        Launcher launcher = Launcher.getLauncher(getContext());
        int windowWidth = launcher != null ? launcher.getDeviceProfile().widthPx : getWidth();
        int windowHeight = launcher != null ? launcher.getDeviceProfile().heightPx : getHeight();
        int margin = (int) (120 * getResources().getDisplayMetrics().density / 2.75f); // ~120px pre-reveal lead margin

        mDashboardView.getLocationInWindow(mTempWindowPos);
        int dashX = mTempWindowPos[0];
        int dashY = mTempWindowPos[1];

        int podCountLimit = sDoseResponseOverride;
        int activePodCount = 0;

        for (int i = 0; i < mDashboardView.getChildCount(); i++) {
            View child = mDashboardView.getChildAt(i);
            if (child == null) continue;

            CategoryCardView cardView = null;
            if (child instanceof CategoryCardView) {
                cardView = (CategoryCardView) child;
            } else if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                for (int j = 0; j < vg.getChildCount(); j++) {
                    View subChild = vg.getChildAt(j);
                    if (subChild instanceof CategoryCardView) {
                        cardView = (CategoryCardView) subChild;
                        break;
                    }
                }
            }
            if (cardView == null) continue;

            AppLibraryCategoryGlassView gv = cardView.getGlassView();
            if (gv == null) continue;

            int x = dashX + child.getLeft() + (int) child.getTranslationX();
            int y = dashY + child.getTop() + (int) child.getTranslationY();
            int w = cardView.getWidth();
            int h = cardView.getHeight();
            if (w == 0 || h == 0) continue;

            int dashW = mDashboardView.getWidth() > 0 ? mDashboardView.getWidth() : windowWidth;
            boolean isHorizontallyVisible = (dashX + dashW > -margin) && (dashX < windowWidth + margin);
            boolean isVerticallyVisible = (y + h > -margin) && (y < windowHeight + margin);
            boolean shouldBeVisible = (isHorizontallyVisible && isVerticallyVisible);

            if (podCountLimit >= 0) {
                if (activePodCount >= podCountLimit) {
                    shouldBeVisible = false;
                }
            }

            if (shouldBeVisible) {
                activePodCount++;
                if (gv.getVisibility() != View.VISIBLE) {
                    gv.setVisibility(View.VISIBLE);
                    gv.invalidate(); // warm backdrop immediately upon reveal
                } else {
                    gv.invalidate();
                }
            } else {
                if (gv.getVisibility() != View.INVISIBLE) {
                    gv.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    public void invalidateGlassSurfaces() {
        if (mSearchGlass != null && mSearchGlass.getVisibility() == VISIBLE) {
            mSearchGlass.invalidate();
        }
        if (mSearchBackdropGlass != null && mSearchBackdropGlass.getVisibility() == VISIBLE) {
            mSearchBackdropGlass.invalidate();
        }
        invalidateCategoryPodsOnly();
    }

    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        if (mDashboardView != null) {
            for (int i = 0; i < mDashboardView.getChildCount(); i++) {
                View child = mDashboardView.getChildAt(i);
                updateCardNotificationDots(child, updatedDots, packageUserKey);
            }
        }
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null) {
            AppLibraryCategoryExpandedView expandedView = AppLibraryCategoryExpandedView.getOpen(launcher);
            if (expandedView != null) {
                RecyclerView grid = expandedView.findViewById(R.id.expanded_category_grid);
                if (grid != null) {
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View child = grid.getChildAt(i);
                        if (child instanceof BubbleTextView) {
                            BubbleTextView btv = (BubbleTextView) child;
                            if (btv.getTag() instanceof ItemInfo) {
                                ItemInfo info = (ItemInfo) btv.getTag();
                                if (!packageUserKey.updateFromItemInfo(info) || updatedDots.test(packageUserKey)) {
                                    btv.applyDotState(info, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateCardNotificationDots(View view, Predicate<PackageUserKey> updatedDots, PackageUserKey packageUserKey) {
        if (view instanceof CategoryCardView) {
            CategoryCardView card = (CategoryCardView) view;
            for (int j = 0; j < card.getChildCount(); j++) {
                View sub = card.getChildAt(j);
                if (sub instanceof BubbleTextView) {
                    BubbleTextView btv = (BubbleTextView) sub;
                    if (btv.getTag() instanceof ItemInfo) {
                        ItemInfo info = (ItemInfo) btv.getTag();
                        if (!packageUserKey.updateFromItemInfo(info) || updatedDots.test(packageUserKey)) {
                            btv.applyDotState(info, true);
                        }
                    }
                }
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int j = 0; j < vg.getChildCount(); j++) {
                updateCardNotificationDots(vg.getChildAt(j), updatedDots, packageUserKey);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!mInSearchMode && (mTransitionAnimator == null || !mTransitionAnimator.isRunning())) {
            updateSearchCenteredPosition();
        }
    }

    public void updateSearchCenteredPosition() {
        if (mInSearchMode) return;
        if (mSearchContent != null) {
            mSearchContent.setTranslationX(calculateSearchCenteredShift());
        }
    }

    private float calculateSearchCenteredShift() {
        if (mSearchContainer == null || mSearchInput == null) return 0f;
        int containerWidth = mSearchRow != null && mSearchRow.getWidth() > 0
                ? mSearchRow.getWidth()
                : mSearchContainer.getWidth();
        if (containerWidth <= 0) return 0f;

        float density = getResources().getDisplayMetrics().density;
        float iconWidth = 17f * density;
        float iconPadding = 7f * density;
        float leftPadding = 14f * density;
        CharSequence hint = mSearchInput.getHint();
        float textWidth = hint != null ? mSearchInput.getPaint().measureText(hint.toString()) : 0f;
        float clusterWidth = iconWidth + iconPadding + textWidth;

        return Math.max(0f, (containerWidth - clusterWidth) / 2f - leftPadding);
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
        if (mAppLibraryModel != null) {
            mAppLibraryModel.addListener(mAppLibraryUpdateListener);
        }
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null && !mAppsStoreListenerRegistered) {
            mAppsStoreListenerRegistered = true;
            launcher.getAppsView().getAppsStore().addUpdateListener(mAppsStoreUpdateListener);
        }
        getViewTreeObserver().addOnGlobalLayoutListener(mImeLayoutListener);
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            Insets statusBarInsets = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
                    .getInsets(WindowInsetsCompat.Type.statusBars());
            if (statusBarInsets.top > 0) {
                setInsets(new Rect(0, statusBarInsets.top, 0, 0));
            }
        }
        if (mInSearchMode) {
            registerBackCallback();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAppLibraryModel != null) {
            mAppLibraryModel.removeListener(mAppLibraryUpdateListener);
        }
        if (mAppsStoreListenerRegistered) {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null && launcher.getAppsView() != null
                    && launcher.getAppsView().getAppsStore() != null) {
                launcher.getAppsView().getAppsStore().removeUpdateListener(mAppsStoreUpdateListener);
            }
            mAppsStoreListenerRegistered = false;
        }
        getViewTreeObserver().removeOnGlobalLayoutListener(mImeLayoutListener);
        unregisterBackCallback();
    }
}
