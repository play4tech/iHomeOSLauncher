/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.ads.AdManager;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Arrays;

/**
 * iOS Spotlight-style search overlay that opens in-place over whatever home screen
 * page is currently active, without navigating away to the App Library page.
 */
public class SpotlightSearchOverlay extends AbstractFloatingView {

    private static final Interpolator IOS_SPRING_INTERPOLATOR = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private static final Interpolator IOS_EXIT_INTERPOLATOR = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private final Launcher mLauncher;
    private AppLibraryOverlayGlassView mBackdropGlass;
    private AppLibrarySearchGlassView mSearchGlass;
    private View mSearchContent;
    private View mSearchRow;
    private View mSearchContainer;
    private ExtendedEditText mSearchInput;
    private ImageView mSearchClear;
    private TextView mSearchCancel;
    private RecyclerView mSearchResultsView;
    private AppLibraryIndexScrollerView mIndexScroller;
    private AppLibrarySearchAdapter mSearchAdapter;

    private ValueAnimator mOpenCloseAnimator;
    private boolean mIsClosing = false;
    private boolean mKeyboardHiddenForClose = false;
    private String mLastFilterQuery = "";
    private final com.android.launcher3.allapps.AllAppsStore.OnUpdateListener mAppsUpdateListener;

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mAppsUpdateListener = () -> {
            if (mLauncher != null && mLauncher.getAppsView() != null
                    && mLauncher.getAppsView().getAppsStore() != null && mSearchAdapter != null) {
                AppInfo[] apps = mLauncher.getAppsView().getAppsStore().getApps();
                if (apps != null) {
                    mSearchAdapter.setApps(Arrays.asList(apps));
                }
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackdropGlass = findViewById(R.id.spotlight_search_backdrop_glass);
        mSearchGlass = findViewById(R.id.spotlight_search_glass);
        mSearchContent = findViewById(R.id.spotlight_search_content);
        mSearchRow = findViewById(R.id.spotlight_search_row);
        mSearchContainer = findViewById(R.id.spotlight_search_container);
        mSearchInput = findViewById(R.id.spotlight_search_input);
        mSearchClear = findViewById(R.id.spotlight_search_clear);
        mSearchCancel = findViewById(R.id.spotlight_search_cancel);
        mSearchResultsView = findViewById(R.id.spotlight_search_results);
        mIndexScroller = null;

        if (mSearchContent != null) {
            int statusBarHeight = mLauncher.getDeviceProfile().getInsets().top;
            if (statusBarHeight <= 0) {
                statusBarHeight = (int) (44f * getResources().getDisplayMetrics().density);
            }
            mSearchContent.setPadding(0, statusBarHeight + (int) (10f * getResources().getDisplayMetrics().density), 0, 0);
        }

        mSearchAdapter = new AppLibrarySearchAdapter(mLauncher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setItemAnimator(null);
        mSearchResultsView.setAdapter(mSearchAdapter);
        mSearchResultsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboardOnScroll();
                }
            }
        });

        if (mIndexScroller != null) {
            mIndexScroller.setOnSectionSelectedListener(section -> {
                if (section != null && section.length() > 0) {
                    int pos = mSearchAdapter.getPositionForSection(section.charAt(0));
                    if (pos >= 0) {
                        RecyclerView.LayoutManager lm = mSearchResultsView.getLayoutManager();
                        if (lm instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
                        }
                    }
                }
            });
        }

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mIsOpen || mIsClosing) {
                    return;
                }
                String query = s != null ? s.toString() : "";
                if (mSearchClear != null) {
                    mSearchClear.setVisibility(query.isEmpty() ? GONE : VISIBLE);
                }
                String trimmed = query.trim();
                if (mIndexScroller != null) {
                    mIndexScroller.setVisibility(trimmed.isEmpty() ? VISIBLE : GONE);
                }
                if (!trimmed.equals(mLastFilterQuery)) {
                    mLastFilterQuery = trimmed;
                    mSearchAdapter.filter(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Ensure clicking any search bar element activates editing mode
        View.OnClickListener activateInputClick = v -> activateSearchInput();
        if (mSearchInput != null) {
            mSearchInput.setOnClickListener(activateInputClick);
            mSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && mIsOpen && !mIsClosing) {
                    activateSearchInput();
                }
            });
            mSearchInput.setOnBackKeyListener(() -> {
                Log.d("SpotlightBack", "mSearchInput.onKeyPreIme back key listener fired");
                close(true);
                return true;
            });
            mSearchInput.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        close(true);
                    }
                    return true;
                }
                return false;
            });
        }

        if (mSearchContainer != null) {
            mSearchContainer.setOnClickListener(activateInputClick);
        }
        if (mSearchGlass != null) {
            mSearchGlass.setOnClickListener(activateInputClick);
        }
        if (mSearchRow != null) {
            mSearchRow.setOnClickListener(activateInputClick);
        }
        View searchIcon = findViewById(R.id.spotlight_search_icon);
        if (searchIcon != null) {
            searchIcon.setOnClickListener(activateInputClick);
        }

        if (mSearchClear != null) {
            mSearchClear.setOnClickListener(v -> mSearchInput.setText(""));
        }

        if (mSearchCancel != null) {
            mSearchCancel.setOnClickListener(v -> close(true));
        }

        if (mBackdropGlass != null) {
            mBackdropGlass.setOnClickListener(v -> close(true));
        }

        applyThemeColors();
    }

    private void applyThemeColors() {
        if (mLauncher == null) return;
        boolean isDarkText = Themes.getAttrBoolean(mLauncher, R.attr.isWorkspaceDarkText);
        int textColor = Themes.getAttrColor(mLauncher, R.attr.workspaceTextColor);
        int hintColor = isDarkText ? 0x99000000 : 0x99FFFFFF;

        if (mSearchInput != null) {
            mSearchInput.setTextColor(textColor);
            mSearchInput.setHintTextColor(hintColor);
        }
        if (mSearchCancel != null) {
            mSearchCancel.setTextColor(textColor);
        }
        ImageView searchIcon = findViewById(R.id.spotlight_search_icon);
        if (searchIcon != null) {
            searchIcon.setImageTintList(ColorStateList.valueOf(textColor));
        }
        if (mSearchClear != null) {
            mSearchClear.setImageTintList(ColorStateList.valueOf(hintColor));
        }
    }

    public static SpotlightSearchOverlay getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_SPOTLIGHT_SEARCH);
    }

    public static SpotlightSearchOverlay show(Launcher launcher) {
        DragLayer dragLayer = launcher.getDragLayer();
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof SpotlightSearchOverlay) {
                SpotlightSearchOverlay existing = (SpotlightSearchOverlay) child;
                if (existing.mIsOpen && !existing.mIsClosing) {
                    existing.activateSearchInput();
                    return existing;
                }
                if (existing.mOpenCloseAnimator != null) {
                    existing.mOpenCloseAnimator.cancel();
                    existing.mOpenCloseAnimator = null;
                }
                dragLayer.removeView(existing);
            }
        }

        SpotlightSearchOverlay overlay = (SpotlightSearchOverlay) LayoutInflater.from(launcher)
                .inflate(R.layout.spotlight_search_overlay, launcher.getDragLayer(), false);

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        overlay.setLayoutParams(lp);

        overlay.prepareForEntrance();
        overlay.mIsOpen = true;
        overlay.mIsClosing = false;
        overlay.mKeyboardHiddenForClose = false;
        com.example.liquidglass.LiquidGlassView.setBackdropScrollListenerPaused(true);
        launcher.getDragLayer().addView(overlay);
        launcher.onDragLayerHierarchyChanged();

        FrameLayout adContainer = overlay.findViewById(R.id.spotlight_search_ad_banner_container);
        if (adContainer != null) {
            AdManager.loadBanner(launcher, adContainer);
        }

        // Load apps
        AppInfo[] apps = launcher.getAppsView() != null && launcher.getAppsView().getAppsStore() != null
                ? launcher.getAppsView().getAppsStore().getApps() : null;
        if (apps != null && apps.length > 0) {
            overlay.mSearchAdapter.setApps(Arrays.asList(apps));
        }

        if (overlay.mSearchInput != null) {
            overlay.mSearchInput.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (overlay.mSearchInput != null) {
                                overlay.mSearchInput.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            }
                            if (overlay.mIsOpen && !overlay.mIsClosing) {
                                overlay.activateSearchInput();
                            }
                        }
                    });
        }

        // Animate entrance and immediately request soft keyboard & active editing mode
        overlay.activateSearchInput();
        overlay.post(overlay::animateOpen);
        overlay.postDelayed(overlay::activateSearchInput, 50);
        return overlay;
    }

    public void prepareForEntrance() {
        applyThemeColors();
        final float density = getResources().getDisplayMetrics().density;
        final float contentSlidePx = 40f * density;
        final float cancelSlidePx = 20f * density;

        setAlpha(1f);
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(0f);
        }
        if (mSearchContent != null) {
            mSearchContent.setAlpha(1f);
            mSearchContent.setTranslationY(0f);
            mSearchContent.setScaleX(1f);
            mSearchContent.setScaleY(1f);
        }
        if (mSearchRow != null) {
            mSearchRow.setTranslationY(0f);
            mSearchRow.setScaleX(1f);
            mSearchRow.setScaleY(1f);
            mSearchRow.setAlpha(1f);
        }
        if (mSearchCancel != null) {
            mSearchCancel.setTranslationX(cancelSlidePx);
            mSearchCancel.setAlpha(0f);
        }
        if (mSearchResultsView != null) {
            mSearchResultsView.setTranslationY(contentSlidePx);
            mSearchResultsView.setScaleX(0.94f);
            mSearchResultsView.setScaleY(0.94f);
            mSearchResultsView.setAlpha(0f);
        }
        if (mIndexScroller != null) {
            mIndexScroller.setAlpha(0f);
        }
    }

    private void animateOpen() {
        if (mIsClosing || !mIsOpen) {
            return;
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        final float density = getResources().getDisplayMetrics().density;
        final float contentSlidePx = 40f * density;
        final float cancelSlidePx = 20f * density;

        setAlpha(1f);
        if (mSearchRow != null) {
            mSearchRow.setAlpha(1f);
            mSearchRow.setTranslationY(0f);
            mSearchRow.setScaleX(1f);
            mSearchRow.setScaleY(1f);
        }

        Workspace<?> ws = mLauncher.getWorkspace();

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(340);
        mOpenCloseAnimator.setInterpolator(IOS_SPRING_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (mBackdropGlass != null) {
                mBackdropGlass.setAlpha(progress);
            }
            if (mSearchCancel != null) {
                mSearchCancel.setTranslationX((1f - progress) * cancelSlidePx);
                mSearchCancel.setAlpha(Math.min(1f, progress * 2f));
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.setTranslationY((1f - progress) * contentSlidePx);
                float contentScale = 0.94f + 0.06f * progress;
                mSearchResultsView.setScaleX(contentScale);
                mSearchResultsView.setScaleY(contentScale);
                mSearchResultsView.setAlpha(Math.min(1f, progress * 1.3f));
            }
            if (mIndexScroller != null) {
                mIndexScroller.setAlpha(progress);
            }
            if (ws != null) {
                float wsScale = 1f - 0.05f * progress;
                ws.setScaleX(wsScale);
                ws.setScaleY(wsScale);
            }
        });
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled || mIsClosing || !mIsOpen) {
                    return;
                }
                setAlpha(1f);
                if (mBackdropGlass != null) mBackdropGlass.setAlpha(1f);
                if (mSearchRow != null) {
                    mSearchRow.setTranslationY(0f);
                    mSearchRow.setScaleX(1f);
                    mSearchRow.setScaleY(1f);
                    mSearchRow.setAlpha(1f);
                }
                if (mSearchCancel != null) {
                    mSearchCancel.setTranslationX(0f);
                    mSearchCancel.setAlpha(1f);
                }
                if (mSearchResultsView != null) {
                    mSearchResultsView.setTranslationY(0f);
                    mSearchResultsView.setScaleX(1f);
                    mSearchResultsView.setScaleY(1f);
                    mSearchResultsView.setAlpha(1f);
                }
                if (mIndexScroller != null) {
                    mIndexScroller.setAlpha(1f);
                }
                if (ws != null) {
                    ws.setScaleX(0.95f);
                    ws.setScaleY(0.95f);
                }
                if (mSearchGlass != null) {
                    mSearchGlass.invalidate();
                }
                activateSearchInput();
            }
        });
        activateSearchInput();
        mOpenCloseAnimator.start();
    }

    public void activateSearchInput() {
        if (mSearchInput == null || mIsClosing || !mIsOpen) {
            return;
        }
        mInternalKeyboardHide = false;
        mKeyboardHiddenForClose = false;
        mSearchInput.setFocusable(true);
        mSearchInput.setFocusableInTouchMode(true);
        mSearchInput.setCursorVisible(true);
        if (!mSearchInput.isFocused()) {
            mSearchInput.requestFocus();
        }
        if (mSearchInput.getText() != null) {
            mSearchInput.setSelection(mSearchInput.getText().length());
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
            mSearchInput.post(() -> {
                if (mIsOpen && !mIsClosing && mSearchInput != null) {
                    mSearchInput.requestFocus();
                    imm.showSoftInput(mSearchInput, 0);
                }
            });
        }
    }

    public void showKeyboard() {
        activateSearchInput();
    }

    public void hideKeyboardOnScroll() {
        if (!mIsOpen || mIsClosing) {
            return;
        }
        if (mSearchInput != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
            }
        }
    }

    public void hideKeyboard() {
        mInternalKeyboardHide = true;
        if (mKeyboardHiddenForClose) {
            return;
        }
        mKeyboardHiddenForClose = true;
        if (mSearchInput != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsClosing && animate) {
            return;
        }
        mIsClosing = true;
        mIsOpen = false;
        com.example.liquidglass.LiquidGlassView.setBackdropScrollListenerPaused(false);
        unregisterBackCallback();
        hideKeyboard();

        FrameLayout adContainer = findViewById(R.id.spotlight_search_ad_banner_container);
        AdManager.destroyBanner(adContainer);

        if (mBackdropGlass != null) mBackdropGlass.animate().cancel();
        if (mSearchContent != null) mSearchContent.animate().cancel();
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) ws.animate().cancel();

        if (!animate) {
            if (mOpenCloseAnimator != null) {
                mOpenCloseAnimator.cancel();
            }
            resetTransforms(ws);
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
            if (mLauncher != null) {
                mLauncher.onDragLayerHierarchyChanged();
            }
            return;
        }

        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        if (mSearchResultsView != null) {
            mSearchResultsView.stopScroll();
            mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        // Keep root ViewGroup at 1f scale/alpha so LiquidGlassViews never render inside a scaled/translucent saveLayer
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);

        final float density = getResources().getDisplayMetrics().density;
        final float slideOffsetPx = 28f * density;
        final float startBackdropAlpha = mBackdropGlass != null ? mBackdropGlass.getAlpha() : 1f;
        final float startContentAlpha = mSearchContent != null ? mSearchContent.getAlpha() : 1f;
        final float startContentTy = mSearchContent != null ? mSearchContent.getTranslationY() : 0f;
        final float startContentScale = mSearchContent != null ? mSearchContent.getScaleX() : 1f;
        final float startWsScale = ws != null ? ws.getScaleX() : 0.95f;

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(250);
        mOpenCloseAnimator.setInterpolator(IOS_EXIT_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            float inv = 1f - p;
            if (mBackdropGlass != null) {
                mBackdropGlass.setAlpha(startBackdropAlpha * inv);
            }
            if (mSearchContent != null) {
                mSearchContent.setAlpha(startContentAlpha * inv);
                mSearchContent.setTranslationY(startContentTy + (slideOffsetPx - startContentTy) * p);
                float s = startContentScale + (0.94f - startContentScale) * p;
                mSearchContent.setScaleX(s);
                mSearchContent.setScaleY(s);
            }
            if (ws != null) {
                float w = startWsScale + (1f - startWsScale) * p;
                ws.setScaleX(w);
                ws.setScaleY(w);
            }
        });
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSearchResultsView != null) {
                    mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
                }
                // Only restore workspace scale if a new Spotlight overlay hasn't opened
                if (ws != null && getOpen(mLauncher) == null) {
                    ws.setScaleX(1f);
                    ws.setScaleY(1f);
                }
                resetTransforms(null);
                setVisibility(GONE);
                if (mLauncher != null) {
                    mLauncher.onDragLayerHierarchyChanged();
                }
                // Defer detaching the focused EditText slightly so any in-flight IME hide animation finishes cleanly
                postDelayed(() -> {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(SpotlightSearchOverlay.this);
                    }
                }, 120);
            }
        });
        mOpenCloseAnimator.start();
    }

    private void resetTransforms(Workspace<?> ws) {
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(1f);
        }
        if (mSearchContent != null) {
            mSearchContent.setAlpha(1f);
            mSearchContent.setTranslationY(0f);
            mSearchContent.setScaleX(1f);
            mSearchContent.setScaleY(1f);
        }
        if (mSearchRow != null) {
            mSearchRow.setAlpha(1f);
            mSearchRow.setTranslationY(0f);
            mSearchRow.setScaleX(1f);
            mSearchRow.setScaleY(1f);
        }
        if (ws != null) {
            ws.setScaleX(1f);
            ws.setScaleY(1f);
        }
    }

    private OnBackInvokedCallback mBackCallback;
    private boolean mImeWasVisible;
    private boolean mInternalKeyboardHide;
    private final ViewTreeObserver.OnGlobalLayoutListener mImeLayoutListener = this::checkRootImeVisibility;

    private void checkRootImeVisibility() {
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            handleImeVisibility(rootInsets.isVisible(WindowInsets.Type.ime()));
        }
    }

    private void handleImeVisibility(boolean imeVisible) {
        if (mIsClosing || !mIsOpen) {
            mImeWasVisible = imeVisible;
            return;
        }
        if (mImeWasVisible && !imeVisible) {
            if (mInternalKeyboardHide) {
                mInternalKeyboardHide = false;
            } else {
                Log.d("SpotlightBack", "IME hidden externally while Spotlight open");
                mImeWasVisible = false;
                return;
            }
        }
        mImeWasVisible = imeVisible;
    }

    @Override
    public void onBackStarted(@NonNull BackEvent backEvent) {
        Log.d("SpotlightBack", "onBackStarted progress=" + backEvent.getProgress());
        super.onBackStarted(backEvent);
        if (mIsClosing) {
            return;
        }
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            mOpenCloseAnimator.cancel();
        }
        if (mBackdropGlass != null) mBackdropGlass.animate().cancel();
        if (mSearchContent != null) mSearchContent.animate().cancel();
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) ws.animate().cancel();
        if (mSearchResultsView != null) {
            mSearchResultsView.stopScroll();
            mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }
    }

    @Override
    public void onBackProgressed(@NonNull BackEvent backEvent) {
        Log.d("SpotlightBack", "onBackProgressed progress=" + backEvent.getProgress());
        if (mIsClosing) {
            return;
        }
        float progress = backEvent.getProgress();
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(1f - 0.35f * progress);
        }
        if (mSearchContent != null) {
            float scale = 1f - 0.06f * progress;
            mSearchContent.setScaleX(scale);
            mSearchContent.setScaleY(scale);
            mSearchContent.setAlpha(1f - 0.25f * progress);
            mSearchContent.setTranslationY(progress * 20f * getResources().getDisplayMetrics().density);
        }
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) {
            float wsScale = 0.95f + 0.05f * progress;
            ws.setScaleX(wsScale);
            ws.setScaleY(wsScale);
        }
    }

    @Override
    public void onBackCancelled() {
        Log.d("SpotlightBack", "onBackCancelled called");
        if (mIsClosing) {
            return;
        }
        if (mSearchResultsView != null) {
            mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        }
        if (mBackdropGlass != null) {
            mBackdropGlass.animate().alpha(1f).setDuration(160).start();
        }
        if (mSearchContent != null) {
            mSearchContent.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(160).start();
        }
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) {
            ws.animate().scaleX(0.95f).scaleY(0.95f).setDuration(160).start();
        }
    }

    @Override
    public void onBackInvoked() {
        Log.d("SpotlightBack", "onBackInvoked called");
        close(true);
    }

    private void unregisterBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback != null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null && mLauncher != null) {
                    dispatcher = mLauncher.getOnBackInvokedDispatcher();
                }
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
            } catch (Throwable ignored) {}
            mBackCallback = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setOnApplyWindowInsetsListener((v, insets) -> {
            checkRootImeVisibility();
            return v.onApplyWindowInsets(insets);
        });

        if (mLauncher != null && mLauncher.getAppsView() != null
                && mLauncher.getAppsView().getAppsStore() != null) {
            mLauncher.getAppsView().getAppsStore().addUpdateListener(mAppsUpdateListener);
        }

        if (Utilities.ATLEAST_T) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null && mLauncher != null) {
                    dispatcher = mLauncher.getOnBackInvokedDispatcher();
                }
                if (dispatcher != null) {
                    if (Utilities.ATLEAST_U) {
                        mBackCallback = new OnBackAnimationCallback() {
                            @Override
                            public void onBackStarted(@NonNull BackEvent backEvent) {
                                SpotlightSearchOverlay.this.onBackStarted(backEvent);
                            }

                            @Override
                            public void onBackProgressed(@NonNull BackEvent backEvent) {
                                SpotlightSearchOverlay.this.onBackProgressed(backEvent);
                            }

                            @Override
                            public void onBackCancelled() {
                                SpotlightSearchOverlay.this.onBackCancelled();
                            }

                            @Override
                            public void onBackInvoked() {
                                SpotlightSearchOverlay.this.onBackInvoked();
                            }
                        };
                        dispatcher.registerOnBackInvokedCallback(
                                OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                    } else {
                        mBackCallback = () -> close(true);
                        dispatcher.registerOnBackInvokedCallback(
                                OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                    }
                }
            } catch (Throwable t) {
                Log.w("SpotlightSearchOverlay", "Failed to register OnBackInvokedCallback", t);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        if (mLauncher != null && mLauncher.getWorkspace() != null && getOpen(mLauncher) == null) {
            mLauncher.getWorkspace().setScaleX(1f);
            mLauncher.getWorkspace().setScaleY(1f);
        }
        if (mLauncher != null && mLauncher.getAppsView() != null
                && mLauncher.getAppsView().getAppsStore() != null) {
            mLauncher.getAppsView().getAppsStore().removeUpdateListener(mAppsUpdateListener);
        }
        com.example.liquidglass.LiquidGlassView.setBackdropScrollListenerPaused(false);
        unregisterBackCallback();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsClosing || !mIsOpen) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && mIsOpen && !mIsClosing) {
            activateSearchInput();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                close(true);
            }
            return true;
        }
        if (mSearchInput != null && !mSearchInput.isFocused() && mIsOpen && !mIsClosing) {
            mSearchInput.setFocusable(true);
            mSearchInput.setFocusableInTouchMode(true);
            mSearchInput.requestFocus();
            return mSearchInput.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_SPOTLIGHT_SEARCH) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}
