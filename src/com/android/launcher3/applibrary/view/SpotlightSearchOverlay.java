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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Arrays;

/**
 * iOS Spotlight-style search overlay that opens in-place over whatever home screen
 * page is currently active, without navigating away to the App Library page.
 */
public class SpotlightSearchOverlay extends AbstractFloatingView {

    private static final Interpolator IOS_SPRING_INTERPOLATOR = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private static final Interpolator IOS_EXIT_INTERPOLATOR = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private final Launcher mLauncher;
    private AppLibraryOverlayGlassView mBackdropGlass;
    private View mSearchContent;
    private View mSearchRow;
    private EditText mSearchInput;
    private ImageView mSearchClear;
    private TextView mSearchCancel;
    private RecyclerView mSearchResultsView;
    private AppLibraryIndexScrollerView mIndexScroller;
    private AppLibrarySearchAdapter mSearchAdapter;

    private ValueAnimator mOpenCloseAnimator;

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackdropGlass = findViewById(R.id.spotlight_search_backdrop_glass);
        mSearchContent = findViewById(R.id.spotlight_search_content);
        mSearchRow = findViewById(R.id.spotlight_search_row);
        mSearchInput = findViewById(R.id.spotlight_search_input);
        mSearchClear = findViewById(R.id.spotlight_search_clear);
        mSearchCancel = findViewById(R.id.spotlight_search_cancel);
        mSearchResultsView = findViewById(R.id.spotlight_search_results);
        mIndexScroller = findViewById(R.id.spotlight_index_scroller);

        if (mSearchContent != null) {
            int statusBarHeight = mLauncher.getDeviceProfile().getInsets().top;
            if (statusBarHeight <= 0) {
                statusBarHeight = (int) (44f * getResources().getDisplayMetrics().density);
            }
            mSearchContent.setPadding(0, statusBarHeight + (int) (10f * getResources().getDisplayMetrics().density), 0, 0);
        }

        mSearchAdapter = new AppLibrarySearchAdapter(mLauncher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setAdapter(mSearchAdapter);

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
                String query = s != null ? s.toString() : "";
                mSearchAdapter.filter(query);
                if (mSearchClear != null) {
                    mSearchClear.setVisibility(query.isEmpty() ? GONE : VISIBLE);
                }
                if (mIndexScroller != null) {
                    mIndexScroller.setVisibility(query.trim().isEmpty() ? VISIBLE : GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (mSearchClear != null) {
            mSearchClear.setOnClickListener(v -> mSearchInput.setText(""));
        }

        if (mSearchCancel != null) {
            mSearchCancel.setOnClickListener(v -> close(true));
        }

        if (mBackdropGlass != null) {
            mBackdropGlass.setOnClickListener(v -> close(true));
        }
    }

    public static SpotlightSearchOverlay getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_SPOTLIGHT_SEARCH);
    }

    public static SpotlightSearchOverlay show(Launcher launcher) {
        SpotlightSearchOverlay existing = getOpen(launcher);
        if (existing != null) {
            return existing;
        }

        SpotlightSearchOverlay overlay = (SpotlightSearchOverlay) LayoutInflater.from(launcher)
                .inflate(R.layout.spotlight_search_overlay, launcher.getDragLayer(), false);

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        overlay.setLayoutParams(lp);

        launcher.getDragLayer().addView(overlay);
        overlay.mIsOpen = true;

        // Load apps
        AppInfo[] apps = launcher.getAppsView() != null && launcher.getAppsView().getAppsStore() != null
                ? launcher.getAppsView().getAppsStore().getApps() : null;
        if (apps != null && apps.length > 0) {
            overlay.mSearchAdapter.setApps(Arrays.asList(apps));
        }

        // Animate entrance
        overlay.post(overlay::animateOpen);
        return overlay;
    }

    private void animateOpen() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        final float density = getResources().getDisplayMetrics().density;
        final float contentSlidePx = 40f * density;
        final float rowSlidePx = -24f * density;
        final float cancelSlidePx = 20f * density;

        setAlpha(0f);
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(0f);
        }
        if (mSearchRow != null) {
            mSearchRow.setTranslationY(rowSlidePx);
            mSearchRow.setScaleX(0.92f);
            mSearchRow.setScaleY(0.92f);
            mSearchRow.setAlpha(0f);
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

        Workspace<?> ws = mLauncher.getWorkspace();

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(340);
        mOpenCloseAnimator.setInterpolator(IOS_SPRING_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setAlpha(1f);
            if (mBackdropGlass != null) {
                mBackdropGlass.setAlpha(progress);
            }
            if (mSearchRow != null) {
                mSearchRow.setTranslationY((1f - progress) * rowSlidePx);
                float rowScale = 0.92f + 0.08f * progress;
                mSearchRow.setScaleX(rowScale);
                mSearchRow.setScaleY(rowScale);
                mSearchRow.setAlpha(Math.min(1f, progress * 1.5f));
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
            @Override
            public void onAnimationEnd(Animator animation) {
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
                showKeyboard();
            }
        });
        mOpenCloseAnimator.start();
    }

    public void showKeyboard() {
        if (mSearchInput != null) {
            mSearchInput.requestFocus();
            postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 60);
        }
    }

    public void hideKeyboard() {
        if (mSearchInput != null) {
            mSearchInput.clearFocus();
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        hideKeyboard();
        mIsOpen = false;

        Workspace<?> ws = mLauncher.getWorkspace();

        if (!animate) {
            if (mOpenCloseAnimator != null) {
                mOpenCloseAnimator.cancel();
            }
            if (ws != null) {
                ws.setScaleX(1f);
                ws.setScaleY(1f);
            }
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
            return;
        }

        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        final float density = getResources().getDisplayMetrics().density;
        final float slideOffsetPx = 28f * density;
        final float startAlpha = getAlpha();
        final float startWsScale = ws != null ? ws.getScaleX() : 0.95f;

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(240);
        mOpenCloseAnimator.setInterpolator(IOS_EXIT_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float inv = 1f - progress;
            setAlpha(inv * startAlpha);
            if (mSearchContent != null) {
                mSearchContent.setTranslationY(progress * slideOffsetPx);
                float contentScale = 1f - 0.05f * progress;
                mSearchContent.setScaleX(contentScale);
                mSearchContent.setScaleY(contentScale);
            }
            if (ws != null) {
                float wsScale = startWsScale + (1f - startWsScale) * progress;
                ws.setScaleX(wsScale);
                ws.setScaleY(wsScale);
            }
        });
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (ws != null) {
                    ws.setScaleX(1f);
                    ws.setScaleY(1f);
                }
                if (getParent() instanceof ViewGroup) {
                    ((ViewGroup) getParent()).removeView(SpotlightSearchOverlay.this);
                }
            }
        });
        mOpenCloseAnimator.start();
    }

    private OnBackInvokedCallback mBackCallback;
    private float mTouchDownX;
    private float mTouchDownY;

    @Override
    public void onBackStarted(@NonNull BackEvent backEvent) {
        super.onBackStarted(backEvent);
    }

    @Override
    public void onBackProgressed(@NonNull BackEvent backEvent) {
        float progress = backEvent.getProgress();
        float scale = 1f - 0.08f * progress;
        setScaleX(scale);
        setScaleY(scale);
        setAlpha(1f - 0.35f * progress);
        if (mSearchContent != null) {
            mSearchContent.setTranslationY(progress * 24f * getResources().getDisplayMetrics().density);
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
        animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
        if (mSearchContent != null) {
            mSearchContent.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(160).start();
        }
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) {
            ws.animate().scaleX(0.95f).scaleY(0.95f).setDuration(160).start();
        }
    }

    @Override
    public void onBackInvoked() {
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        if (mSearchContent != null) {
            mSearchContent.setTranslationY(0f);
            mSearchContent.setScaleX(1f);
            mSearchContent.setScaleY(1f);
        }
        close(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Utilities.ATLEAST_T) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
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
        if (Utilities.ATLEAST_T && mBackCallback != null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
            } catch (Throwable ignored) {}
            mBackCallback = null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            close(true);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = ev.getRawX();
                mTouchDownY = ev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - mTouchDownX;
                float dy = ev.getRawY() - mTouchDownY;
                float screenWidth = getResources().getDisplayMetrics().widthPixels;
                float density = getResources().getDisplayMetrics().density;
                // If edge swipe back gesture (from left edge towards right OR right edge towards left)
                boolean fromLeft = mTouchDownX < 60f * density && dx > 50f * density;
                boolean fromRight = mTouchDownX > screenWidth - 60f * density && dx < -50f * density;
                if ((fromLeft || fromRight) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    close(true);
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
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
