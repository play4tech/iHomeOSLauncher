# Part 4: Folders, Dock (Hotseat), Page Indicators & Wobble Edit Mode

Modular code logic bundle designed to stay within Gemini attachment size limits (12 files).

## Table of Contents

- [src/com/android/launcher3/folder/Folder.java](#srccomandroidlauncher3folderFolderjava)
- [src/com/android/launcher3/folder/FolderAnimationManager.java](#srccomandroidlauncher3folderFolderAnimationManagerjava)
- [bliss/src/foundation/e/bliss/folder/GridFolder.java](#blisssrcfoundationeblissfolderGridFolderjava)
- [src/com/android/launcher3/folder/FolderIcon.java](#srccomandroidlauncher3folderFolderIconjava)
- [src/com/android/launcher3/folder/FolderPagedView.java](#srccomandroidlauncher3folderFolderPagedViewjava)
- [src/com/android/launcher3/folder/LauncherDelegate.java](#srccomandroidlauncher3folderLauncherDelegatejava)
- [src/com/android/launcher3/Hotseat.java](#srccomandroidlauncher3Hotseatjava)
- [src/com/android/launcher3/pageindicators/PageIndicatorDots.java](#srccomandroidlauncher3pageindicatorsPageIndicatorDotsjava)
- [src/com/android/launcher3/pageindicators/WorkspacePageIndicator.java](#srccomandroidlauncher3pageindicatorsWorkspacePageIndicatorjava)
- [bliss/src/foundation/e/bliss/wobble/JiggleTopBarView.kt](#blisssrcfoundationeblisswobbleJiggleTopBarViewkt)
- [bliss/src/foundation/e/bliss/wobble/JiggleEditMenuPopup.kt](#blisssrcfoundationeblisswobbleJiggleEditMenuPopupkt)
- [src/com/android/launcher3/util/RemovedAppStore.java](#srccomandroidlauncher3utilRemovedAppStorejava)

---

# src/com/android/launcher3/folder/Folder.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/folder/Folder.java`  
**Lines**: 2180 | **Bytes**: 83746

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.folder;

import static android.text.TextUtils.isEmpty;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.config.FeatureFlags.ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_LABEL_UPDATED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED;
import static com.android.launcher3.testing.shared.TestProtocol.FOLDER_OPENED_MESSAGE;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;

import com.android.launcher3.util.DragTrace;
import com.android.launcher3.util.Themes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.window.BackEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.accessibility.FolderAccessibilityHelper;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.FolderListener;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ClipPathView;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import foundation.e.bliss.folder.GridFolder;
import foundation.e.bliss.multimode.MultiModeController;

/**
 * Represents a set of icons chosen by the user or generated by the system.
 */
public class Folder extends AbstractFloatingView implements ClipPathView, DragSource,
        View.OnLongClickListener, DropTarget, FolderListener, TextView.OnEditorActionListener,
        View.OnFocusChangeListener, DragListener, ExtendedEditText.OnBackKeyListener,
        KeyboardInsetAnimationCallback.KeyboardInsetListener {
    private static final String TAG = "Launcher.Folder";
    private static final boolean DEBUG = false;

    /**
     * Used for separating folder title when logging together.
     */
    private static final CharSequence FOLDER_LABEL_DELIMITER = "~";

    /**
     * We avoid measuring {@link #mContent} with a 0 width or height, as this
     * results in CellLayout being measured as UNSPECIFIED, which it does not support.
     */
    private static final int MIN_CONTENT_DIMEN = 5;

    public static final int STATE_CLOSED = 0;
    public static final int STATE_ANIMATING = 1;
    public static final int STATE_OPEN = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_CLOSED, STATE_ANIMATING, STATE_OPEN})
    public @interface FolderState {}

    /**
     * Time for which the scroll hint is shown before automatically changing page.
     */
    public static final int SCROLL_HINT_DURATION = 500;
    private static final int RESCROLL_EXTRA_DELAY = 150;

    public static final int SCROLL_NONE = -1;
    public static final int SCROLL_LEFT = 0;
    public static final int SCROLL_RIGHT = 1;

    /**
     * Fraction of icon width which behave as scroll region.
     */
    private static final float ICON_OVERSCROLL_WIDTH_FACTOR = 0.70f;

    private static final int FOLDER_NAME_ANIMATION_DURATION = 633;
    private static final int FOLDER_COLOR_ANIMATION_DURATION = 200;

    private static final int REORDER_DELAY = 150;
    private static final int ON_EXIT_CLOSE_DELAY = 400;
    private static final Rect sTempRect = new Rect();
    private static final int MIN_FOLDERS_FOR_HARDWARE_OPTIMIZATION = 10;

    private final Alarm mReorderAlarm = new Alarm(Looper.getMainLooper());
    private final Alarm mOnExitAlarm = new Alarm(Looper.getMainLooper());
    private final Alarm mOnScrollHintAlarm = new Alarm(Looper.getMainLooper());
    final Alarm mScrollPauseAlarm = new Alarm(Looper.getMainLooper());

    final ArrayList<View> mItemsInReadingOrder = new ArrayList<View>();

    private AnimatorSet mCurrentAnimator;
    private boolean mIsAnimatingClosed = false;

    // Folder can be displayed in Launcher's activity or a separate window (e.g. Taskbar).
    // Anything specific to Launcher should use mLauncherDelegate, otherwise should
    // use mActivityContext.
    public final LauncherDelegate mLauncherDelegate;
    public final ActivityContext mActivityContext;

    protected DragController mDragController;
    public FolderInfo mInfo;
    private CharSequence mFromTitle;
    private FromState mFromLabelState;

    @Thunk
    FolderIcon mFolderIcon;

    @Thunk
    protected FolderPagedView mContent;
    public FolderNameEditText mFolderName;
    private PageIndicatorDots mPageIndicator;

    protected View mFooter;
    protected int mFooterHeight;

    // Cell ranks used for drag and drop
    @Thunk
    int mTargetRank, mPrevTargetRank, mEmptyCellRank;

    private Path mClipPath;

    @ViewDebug.ExportedProperty(category = "launcher",
            mapping = {
                    @ViewDebug.IntToString(from = STATE_CLOSED, to = "STATE_CLOSED"),
                    @ViewDebug.IntToString(from = STATE_ANIMATING, to = "STATE_ANIMATING"),
                    @ViewDebug.IntToString(from = STATE_OPEN, to = "STATE_OPEN"),
            })
    private int mState = STATE_CLOSED;
    private final List<OnFolderStateChangedListener> mOnFolderStateChangedListeners =
            new ArrayList<>();
    private OnFolderStateChangedListener mPriorityOnFolderStateChangedListener;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mRearrangeOnClose = false;
    boolean mItemsInvalidated = false;
    private View mCurrentDragView;
    private boolean mIsExternalDrag;
    private boolean mDragInProgress = false;
    private boolean mDragListenerRegistered = false;
    private boolean mDeleteFolderOnDropCompleted = false;
    private boolean mSuppressFolderDeletion = false;
    private boolean mItemAddedBackToSelfViaIcon = false;
    private boolean mIsEditingName = false;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDestroyed;

    // Folder scrolling
    protected int mScrollAreaOffset;

    @Thunk
    int mScrollHintDir = SCROLL_NONE;
    @Thunk
    int mCurrentScrollDir = SCROLL_NONE;

    private StatsLogManager mStatsLogManager;

    @Nullable
    private KeyboardInsetAnimationCallback mKeyboardInsetAnimationCallback;

    public GradientDrawable mBackground;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the Workspace's customization values.
     */
    public Folder(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlwaysDrawnWithCacheEnabled(false);

        mActivityContext = ActivityContext.lookupContext(context);
        mLauncherDelegate = LauncherDelegate.from(mActivityContext);

        mStatsLogManager = StatsLogManager.newInstance(context);
        // We need this view to be focusable in touch mode so that when text editing of the folder
        // name is complete, we have something to focus on, thus hiding the cursor and giving
        // reliable behavior when clicking the text field (since it will always gain focus on
        // click).
        setFocusableInTouchMode(true);

    }

    @Override
    public Drawable getBackground() {
        return mBackground;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final DeviceProfile dp = mActivityContext.getDeviceProfile();
        final int paddingLeftRight = dp.folderContentPaddingLeftRight;

        mBackground = (GradientDrawable) ResourcesCompat.getDrawable(getResources(),
                R.drawable.round_rect_folder, getContext().getTheme());

        mContent = findViewById(R.id.folder_content);
        mContent.setPadding(paddingLeftRight, dp.folderContentPaddingTop, paddingLeftRight, 0);
        mContent.setFolder(this);

        mPageIndicator = findViewById(R.id.folder_page_indicator);
        if (mPageIndicator != null) {
            mPageIndicator.setVisibility(View.VISIBLE);
        }
        mFolderName = findViewById(R.id.folder_name);
        mFolderName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        mFolderName.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mFolderName.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mFolderName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mFolderName.setShadowLayer(0, 0, 0, 0);
        mFolderName.setOnBackKeyListener(this);
        mFolderName.setOnEditorActionListener(this);
        mFolderName.setSelectAllOnFocus(true);
        mFolderName.setInputType(mFolderName.getInputType()
                & ~InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mFolderName.forceDisableSuggestions(true);
        updateFolderNamePadding();

        mFooter = findViewById(R.id.folder_footer);
        mFooterHeight = dp.folderFooterHeightPx;

        mKeyboardInsetAnimationCallback = new KeyboardInsetAnimationCallback(this);
        setWindowInsetsAnimationCallback(mKeyboardInsetAnimationCallback);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateFolderNamePadding();
    }

    public void updateFolderNamePadding() {
        if (mFolderName == null) return;
        int iconLeft = getFolderTitleLeftPadding();
        if (iconLeft > 0 && (mFolderName.getPaddingStart() != iconLeft || mFolderName.getPaddingEnd() != iconLeft)) {
            mFolderName.setPaddingRelative(iconLeft, 0, iconLeft, 0);
        }
    }

    public int getFolderTitleLeftPadding() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int cellIconGap = (grid.folderCellWidthPx - grid.iconSizePx);
        int contentPadding = (mContent != null && mContent.getPaddingLeft() > 0)
                ? mContent.getPaddingLeft()
                : cellIconGap;

        int cellWidth = grid.folderCellWidthPx;
        int iconSize = grid.iconSizePx;

        if (mContent != null && mContent.getPageCount() > 0) {
            CellLayout page = mContent.getPageAt(0);
            if (page != null) {
                if (page.getCellWidth() > 0) {
                    cellWidth = page.getCellWidth();
                }
                if (page.getShortcutsAndWidgets() != null && page.getShortcutsAndWidgets().getChildCount() > 0) {
                    View firstChild = page.getShortcutsAndWidgets().getChildAt(0);
                    if (firstChild instanceof BubbleTextView) {
                        BubbleTextView btv = (BubbleTextView) firstChild;
                        if (btv.getWidth() > 0) {
                            cellWidth = btv.getWidth();
                        }
                        if (btv.getIconSize() > 0) {
                            iconSize = btv.getIconSize();
                        }
                    }
                }
            }
        }

        int iconOffset = Math.max(0, (cellWidth - iconSize) / 2);
        int targetPadding = contentPadding + iconOffset;
        return Math.max(0, targetPadding);
    }

    public boolean onLongClick(View v) {
        // Return if global dragging is not enabled
        if (!mLauncherDelegate.isDraggingEnabled()) return true;
        return startDrag(v, new DragOptions());
    }

    public boolean isFolderWobbling() {
        Launcher launcher = mLauncherDelegate.getLauncher();
        if (launcher != null && launcher.getWorkspace() != null) {
            return launcher.getWorkspace().isWobbling();
        } else return false;
    }

    public boolean startDrag(View v) {
        return startDrag(v, new DragOptions());
    }

    public boolean startDrag(View v, DragOptions options) {
        Object tag = v.getTag();
        if (tag instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo item = (WorkspaceItemInfo) tag;

            if (MultiModeController.isSingleLayerMode()) {
                v.setOnTouchListener(null);
                if (isFolderWobbling()) {
                    v.setVisibility(INVISIBLE);
                }
            }

            mEmptyCellRank = item.rank;
            mCurrentDragView = v;
            mDragInProgress = true;
            DragTrace.log("Folder.startDrag item=" + (item != null ? item.title : "null") + " rank=" + mEmptyCellRank);

            registerDragListener();
            if (options.isAccessibleDrag) {
                mDragController.addDragListener(new AccessibleDragListenerAdapter(
                        mContent, FolderAccessibilityHelper::new) {
                    @Override
                    protected void enableAccessibleDrag(boolean enable,
                            @Nullable DragObject dragObject) {
                        super.enableAccessibleDrag(enable, dragObject);
                        mFooter.setImportantForAccessibility(enable
                                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    }
                });
            }

            mLauncherDelegate.beginDragShared(v, this, options);
        }
        return true;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (dragObject.dragSource != this) {
            return;
        }

        mDragInProgress = true;
        mItemAddedBackToSelfViaIcon = false;

        mContent.removeItem(mCurrentDragView);
        if (dragObject.dragInfo instanceof WorkspaceItemInfo) {
            mItemsInvalidated = true;

            // We do not want to get events for the item being removed, as they will get handled
            // when the drop completes
            try (SuppressInfoChanges s = new SuppressInfoChanges()) {
                mInfo.remove((WorkspaceItemInfo) dragObject.dragInfo, true);
            }
        }
    }

    @Override
    public void onDragEnd() {
        if (mIsExternalDrag && mDragInProgress) {
            completeDragExit();
        }
        mDragInProgress = false;
        unregisterDragListener();
        if (mContent != null) {
            mContent.cleanupEmptyPages();
        }
    }

    public boolean isEditingName() {
        return mIsEditingName;
    }

    public void startEditingFolderName() {
        post(() -> {
            if (mIsEditingName && mFolderName.hasFocus()) {
                return;
            }
            showLabelSuggestions();
            mFolderName.setHint("");
            mIsEditingName = true;
            if (!mFolderName.hasFocus()) {
                mFolderName.requestFocus();
                mFolderName.selectAll();
            }
            mFolderName.showKeyboard();
        });
    }

    @Override
    public boolean onBackKey() {
        // Convert to a string here to ensure that no other state associated with the text field
        // gets saved.
        String newTitle = mFolderName.getText().toString();
        if (DEBUG) {
            Log.d(TAG, "onBackKey newTitle=" + newTitle);
        }
        DragTrace.log("Folder.onTitleChanged newTitle=" + newTitle + " folder=" + mInfo.id);
        mInfo.setTitle(newTitle, mLauncherDelegate.getModelWriter());
        mFolderIcon.onTitleChanged(newTitle);

        if (TextUtils.isEmpty(mInfo.title)) {
            mFolderName.setHint(R.string.untitled_folder);
            mFolderName.setText(R.string.untitled_folder);
        } else {
            mFolderName.setHint(null);
        }

        sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                getContext().getString(R.string.folder_renamed, newTitle));

        // This ensures that focus is gained every time the field is clicked, which selects all
        // the text and brings up the soft keyboard if necessary.
        mFolderName.clearFocus();

        Selection.setSelection(mFolderName.getText(), 0, 0);
        mIsEditingName = false;
        return true;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onEditorAction actionId=" + actionId + " key="
                    + (event != null ? event.getKeyCode() : "null event"));
        }
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            mFolderName.dispatchBackKey();
            return true;
        }
        return false;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        this.setTranslationY(0);

        if (windowInsets.isVisible(WindowInsets.Type.ime())) {
            Insets keyboardInsets = windowInsets.getInsets(WindowInsets.Type.ime());
            int folderHeightFromBottom = getHeightFromBottom();

            if (keyboardInsets.bottom > folderHeightFromBottom) {
                // Translate this folder above the keyboard, then add the folder name's padding
                this.setTranslationY(folderHeightFromBottom - keyboardInsets.bottom
                        - mFolderName.getPaddingBottom());
            }
        }

        return windowInsets;
    }

    @Override
    public void setTranslationY(float translationY) {
        float old = getTranslationY();
        super.setTranslationY(translationY);
        if (Float.compare(old, translationY) != 0) {
            invalidateFolderGlass();
        }
    }

    @Override
    public void setTranslationX(float translationX) {
        float old = getTranslationX();
        super.setTranslationX(translationX);
        if (Float.compare(old, translationX) != 0) {
            invalidateFolderGlass();
        }
    }

    public View getGlassView() {
        return null;
    }

    public void invalidateFolderGlass() {
        View glass = getGlassView();
        if (glass != null) {
            glass.invalidate();
        }
    }

    @Override
    public void onTranslationStart() {
        invalidateFolderGlass();
    }

    @Override
    public void onTranslationEnd() {
        invalidateFolderGlass();
    }

    public FolderIcon getFolderIcon() {
        return mFolderIcon;
    }

    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    public void setFolderIcon(FolderIcon icon) {
        mFolderIcon = icon;
        mLauncherDelegate.init(this, icon);
    }

    private OnBackInvokedCallback mBackCallback;

    private void registerBackCallback() {
        if (!Utilities.ATLEAST_T) return;
        try {
            OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
            if (dispatcher == null && mActivityContext instanceof Launcher) {
                dispatcher = ((Launcher) mActivityContext).getOnBackInvokedDispatcher();
            }
            if (dispatcher != null && mBackCallback == null) {
                mBackCallback = () -> {
                    if (isEditingName()) {
                        mFolderName.dispatchBackKey();
                    } else {
                        close(true);
                    }
                };
                dispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register back callback", t);
        }
    }

    private void unregisterBackCallback() {
        if (!Utilities.ATLEAST_T) return;
        try {
            if (mBackCallback != null) {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null && mActivityContext instanceof Launcher) {
                    dispatcher = ((Launcher) mActivityContext).getOnBackInvokedDispatcher();
                }
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
                mBackCallback = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to unregister back callback", t);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        // requestFocus() causes the focus onto the folder itself, which doesn't cause visual
        // effect but the next arrow key can start the keyboard focus inside of the folder, not
        // the folder itself.
        requestFocus();
        super.onAttachedToWindow();
        mFolderName.addOnFocusChangeListener(this);
        if (mIsOpen) {
            registerBackCallback();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFolderName.removeOnFocusChangeListener(this);
        unregisterBackCallback();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // When the folder gets focus, we don't want to announce the list of items.
        return true;
    }

    @Override
    public View focusSearch(int direction) {
        // When the folder is focused, further focus search should be within the folder contents.
        return FocusFinder.getInstance().findNextFocus(this, null, direction);
    }

    /**
     * @return the FolderInfo object associated with this folder
     */
    public FolderInfo getInfo() {
        return mInfo;
    }

    void bind(FolderInfo info) {
        mInfo = info;
        mFromTitle = info.title;
        mFromLabelState = info.getFromLabelState();
        ArrayList<WorkspaceItemInfo> children = info.contents;
        Collections.sort(children, ITEM_POS_COMPARATOR);
        updateItemLocationsInDatabaseBatch(true);

        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) getLayoutParams();
        if (lp == null) {
            lp = new BaseDragLayer.LayoutParams(0, 0);
            lp.customPosition = true;
            setLayoutParams(lp);
        }
        mItemsInvalidated = true;
        mInfo.addListener(this);

        if (!isEmpty(mInfo.title)) {
            mFolderName.setText(mInfo.title);
            mFolderName.setHint(null);
        } else {
            mFolderName.setText(R.string.untitled_folder);
            mFolderName.setHint(R.string.untitled_folder);
        }
        mFolderName.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mFolderName.setHintTextColor(Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText) ? 0x99000000 : 0x99FFFFFF);
        // In case any children didn't come across during loading, clean up the folder accordingly
        mFolderIcon.post(() -> {
            if (!isDragActive() && getItemCount() <= 1) {
                replaceFolderWithFinalItem();
            }
        });
    }


    /**
     * Show suggested folder title in FolderEditText if the first suggestion is non-empty, push
     * rest of the suggestions to InputMethodManager.
     */
    private void showLabelSuggestions() {
        if (mInfo.suggestedFolderNames == null) {
            return;
        }
        if (mInfo.suggestedFolderNames.hasSuggestions()) {
            // update the primary suggestion if the folder name is empty.
            if (isEmpty(mFolderName.getText())) {
                if (mInfo.suggestedFolderNames.hasPrimary()) {
                    mFolderName.setHint("");
                    mFolderName.setText(mInfo.suggestedFolderNames.getLabels()[0]);
                    mFolderName.selectAll();
                }
            }
            mFolderName.showKeyboard();
            mFolderName.displayCompletions(
                    Stream.of(mInfo.suggestedFolderNames.getLabels())
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .filter(s -> !s.isEmpty())
                            .filter(s -> !s.equalsIgnoreCase(mFolderName.getText().toString()))
                            .collect(Collectors.toList()));
        }
    }

    /**
     * Creates a new UserFolder, inflated from R.layout.user_folder.
     *
     * @param activityContext The main ActivityContext in which to inflate this Folder. It must also
     *                        be an instance or ContextWrapper around the Launcher activity context.
     * @return A new UserFolder.
     */
    @SuppressLint("InflateParams")
    public static <T extends Context & ActivityContext> Folder fromXml(T activityContext) {
        return (Folder) LayoutInflater.from(activityContext).cloneInContext(activityContext)
                .inflate(R.layout.user_folder_icon_normalized, null);
    }

    private void addAnimationStartListeners(AnimatorSet a) {
        mLauncherDelegate.forEachVisibleWorkspacePage(
                visiblePage -> addAnimatorListenerForPage(a, (CellLayout) visiblePage));

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setState(STATE_ANIMATING);
                mCurrentAnimator = a;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }
        });
    }

    private void addAnimatorListenerForPage(AnimatorSet a, CellLayout currentCellLayout) {
        final boolean useHardware = shouldUseHardwareLayerForAnimation(currentCellLayout);
        final boolean wasHardwareAccelerated = currentCellLayout.isHardwareLayerEnabled();

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (useHardware) {
                    currentCellLayout.enableHardwareLayer(true);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (useHardware) {
                    currentCellLayout.enableHardwareLayer(wasHardwareAccelerated);
                }
            }
        });
    }

    private boolean shouldUseHardwareLayerForAnimation(CellLayout currentCellLayout) {
        if (ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS.get()) return true;

        int folderCount = 0;
        final ShortcutAndWidgetContainer container = currentCellLayout.getShortcutsAndWidgets();
        for (int i = container.getChildCount() - 1; i >= 0; --i) {
            final View child = container.getChildAt(i);
            if (child instanceof AppWidgetHostView) return false;
            if (child instanceof FolderIcon) ++folderCount;
        }
        return folderCount >= MIN_FOLDERS_FOR_HARDWARE_OPTIMIZATION;
    }

    /**
     * Opens the folder as part of a drag operation
     */
    public void beginExternalDrag() {
        mIsExternalDrag = true;
        mDragInProgress = true;

        // Since this folder opened by another controller, it might not get onDrop or
        // onDropComplete. Perform cleanup once drag-n-drop ends.
        registerDragListener();

        ArrayList<WorkspaceItemInfo> items = new ArrayList<>(mInfo.contents);
        mEmptyCellRank = items.size();
        items.add(null);    // Add an empty spot at the end

        animateOpen(items, mEmptyCellRank / mContent.itemsPerPage());
    }

    private void registerDragListener() {
        if (!mDragListenerRegistered) {
            mDragController.addDragListener(this);
            mDragListenerRegistered = true;
        }
    }

    private void unregisterDragListener() {
        if (mDragListenerRegistered) {
            mDragController.removeDragListener(this);
            mDragListenerRegistered = false;
        }
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     */
    public void animateOpen() {
        animateOpen(mInfo.contents, 0);
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     */
    private void animateOpen(List<WorkspaceItemInfo> items, int pageNo) {
        if (items == null || items.size() <= 1) {
            Log.d(TAG, "Couldn't animate folder open because items is: " + items);
            return;
        }

        Folder openFolder = getOpen(mActivityContext);
        if (openFolder != null && openFolder != this) {
            // Close any open folder before opening a folder.
            openFolder.close(true);
        }

        mContent.bindItems(items);
        centerAboutIcon();
        mItemsInvalidated = true;
        updateTextViewFocus();

        mIsOpen = true;
        DragTrace.log("Folder.animateOpen folder=" + (mInfo != null ? mInfo.id : "null") + " items=" + (items != null ? items.size() : 0));
        onFolderOpenStart();
        registerBackCallback();

        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (getParent() == null) {
            dragLayer.addView(this);
            mDragController.addDropTarget(this);
        } else {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                Log.e(TAG, "Opening folder (" + this + ") which already has a parent:"
                        + getParent());
            }
        }

        mContent.completePendingPageChanges();
        mContent.setCurrentPage(pageNo);

        // Ensure all icons inside folder reflect up-to-date notification dots upon opening
        iterateOverItems((info, v) -> {
            if (info instanceof WorkspaceItemInfo && v instanceof BubbleTextView) {
                ((BubbleTextView) v).applyDotState(info, false);
            }
            return false;
        });

        // This is set to true in close(), but isn't reset to false until onDropCompleted(). This
        // leads to an inconsistent state if you drag out of the folder and drag back in without
        // dropping. One resulting issue is that replaceFolderWithFinalItem() can be called twice.
        mDeleteFolderOnDropCompleted = false;

        cancelRunningAnimations();
        FolderAnimationManager fam = new FolderAnimationManager(this, true /* isOpening */);
        AnimatorSet anim = fam.getAnimator();
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFolderIcon.setIconVisible(false);
                mFolderIcon.drawLeaveBehindIfExists();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setState(STATE_OPEN);
                announceAccessibilityChanges();
                AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                        FOLDER_OPENED_MESSAGE);

                if (!mIsEditingName) {
                    mContent.setFocusOnFirstChild();
                }
            }
        });

        // Footer animation
        mFolderName.setTranslationX(0);
        if (mPageIndicator != null) {
            mPageIndicator.setVisibility(View.VISIBLE);
        }
        if (mContent.getPageCount() > 1 && !mInfo.hasOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION)) {
            mPageIndicator.prepareEntryAnimation();

            // Do not update the flag if we are in drag mode. The flag will be updated, when we
            // actually drop the icon.
            final boolean updateAnimationFlag = !mDragInProgress;
            anim.addListener(new AnimatorListenerAdapter() {

                @SuppressLint("InlinedApi")
                @Override
                public void onAnimationEnd(Animator animation) {
                    mPageIndicator.playEntryAnimation();

                    if (updateAnimationFlag) {
                        mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true,
                                mLauncherDelegate.getModelWriter());
                    }
                }
            });
        } else {
            mFolderName.setTranslationX(0);
        }

        mPageIndicator.stopAllAnimations();

        // b/282158620 because setCurrentPlayTime() below will start animator, we need to register
        // {@link AnimatorListener} before it so that {@link AnimatorListener#onAnimationStart} can
        // be called to register mCurrentAnimator, which will be used to cancel animator
        addAnimationStartListeners(anim);
        // Because t=0 has the folder match the folder icon, we can skip the
        // first frame and have the same movement one frame earlier.
        anim.setCurrentPlayTime(Math.min(getSingleFrameMs(getContext()), anim.getTotalDuration()));
        anim.start();

        // Make sure the folder picks up the last drag move even if the finger doesn't move.
        if (mDragController.isDragging()) {
            mDragController.forceTouchMove();
        }
        mContent.verifyVisibleHighResIcons(mContent.getNextPage());
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER) != 0;
    }

    @Override
    protected void handleClose(boolean animate) {
        DragTrace.log("Folder.handleClose folder=" + (mInfo != null ? mInfo.id : "null") + " animate=" + animate);
        mIsOpen = false;

        if (!animate && mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

        if (isEditingName()) {
            mFolderName.dispatchBackKey();
        }

        if (mFolderIcon != null) {
            mFolderIcon.clearLeaveBehindIfExists();
        }

        if (animate) {
            animateClosed();
        } else {
            closeComplete(false);
            post(this::announceAccessibilityChanges);
        }

        // Notify the accessibility manager that this folder "window" has disappeared and no
        // longer occludes the workspace items
        mActivityContext.getDragLayer().sendAccessibilityEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void cancelRunningAnimations() {
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
    }

    private void animateClosed() {
        if (mIsAnimatingClosed) {
            return;
        }

        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);

        int size = getIconsInReadingOrder().size();
        if (size <= 1 && !MultiModeController.isSingleLayerMode()) {
            Log.d(TAG, "Couldn't animate folder closed because there's " + size + " icons");
            closeComplete(false);
            post(this::announceAccessibilityChanges);
            return;
        }

        mContent.completePendingPageChanges();
        mContent.snapToPageImmediately(mContent.getDestinationPage());

        cancelRunningAnimations();
        AnimatorSet a = new FolderAnimationManager(this, false /* isOpening */).getAnimator();
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setWindowInsetsAnimationCallback(null);
                mIsAnimatingClosed = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mKeyboardInsetAnimationCallback != null) {
                    setWindowInsetsAnimationCallback(mKeyboardInsetAnimationCallback);
                }
                closeComplete(true);
                announceAccessibilityChanges();
                mIsAnimatingClosed = false;
            }
        });
        addAnimationStartListeners(a);
        a.start();
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(mContent, mIsOpen ? mContent.getAccessibilityDescription()
                : getContext().getString(R.string.folder_closed));
    }

    @Override
    protected View getAccessibilityInitialFocusView() {
        View firstItem = mContent.getFirstItem();
        return firstItem != null ? firstItem : super.getAccessibilityInitialFocusView();
    }

    private void closeComplete(boolean wasAnimated) {
        Log.d("ICON_TRACE", "closeComplete folder=" + mInfo.id + " mDragInProgress=" + mDragInProgress + " itemCount=" + getItemCount());
        if (mContent != null) {
            mContent.cleanupEmptyPages();
        }
        unregisterBackCallback();
        // TODO: Clear all active animations.
        BaseDragLayer parent = (BaseDragLayer) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
        mDragController.removeDropTarget(this);
        clearFocus();
        if (mFolderIcon != null) {
            mFolderIcon.setVisibility(View.VISIBLE);
            mFolderIcon.setIconVisible(true);
            mFolderIcon.setAlpha(1f);
            mFolderIcon.mFolderName.setTextVisibility(true);
            if (wasAnimated) {
                mFolderIcon.animateBgShadowAndStroke();
                mFolderIcon.onFolderClose(mContent.getCurrentPage());
                if (mFolderIcon.hasDot()) {
                    mFolderIcon.setDotScale(1f);
                }
                mFolderIcon.requestFocus();
            }
        }

        if (mRearrangeOnClose) {
            rearrangeChildren();
            mRearrangeOnClose = false;
        }
        boolean isDragging = isDragActive();
        if (getItemCount() <= 1) {
            if (!isDragging && !mSuppressFolderDeletion) {
                replaceFolderWithFinalItem();
            } else if (isDragging) {
                mDeleteFolderOnDropCompleted = true;
            }
        } else if (!isDragging) {
            mContent.unbindItems();
        }
        mSuppressFolderDeletion = false;
        clearDragInfo();
        setState(STATE_CLOSED);
        mContent.setCurrentPage(0);
        onFolderCloseComplete();
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        final ItemInfo item = d.dragInfo;
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT));
    }

    public void onDragEnter(DragObject d) {
        DragTrace.log("Folder.onDragEnter item=" + (d.dragInfo != null ? d.dragInfo.title : "null"));
        mPrevTargetRank = -1;
        mOnExitAlarm.cancelAlarm();
        // Get the area offset such that the folder only closes if half the drag icon width
        // is outside the folder area
        mScrollAreaOffset = d.dragView.getDragRegionWidth() / 2 - d.xOffset;
    }

    OnAlarmListener mReorderAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            DragTrace.log("Folder.reorderAlarm emptyRank=" + mEmptyCellRank + " -> targetRank=" + mTargetRank);
            mContent.realTimeReorder(mEmptyCellRank, mTargetRank);
            mEmptyCellRank = mTargetRank;
            mItemsInvalidated = true;
        }
    };

    public boolean isLayoutRtl() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    private final float[] mTargetRankCoord = new float[2];

    private int getTargetRank(DragObject d, float[] recycle) {
        float[] visualCenter = d.getVisualCenter(recycle);
        if (mContent != null) {
            int pageIndex = mContent.getNextPage();
            com.android.launcher3.CellLayout page = mContent.getPageAt(pageIndex);
            if (page != null) {
                mTargetRankCoord[0] = visualCenter[0];
                mTargetRankCoord[1] = visualCenter[1];
                com.android.launcher3.Utilities.mapCoordInSelfToDescendant(page, this, mTargetRankCoord);
                int rank = mContent.findNearestArea((int) mTargetRankCoord[0], (int) mTargetRankCoord[1]);
                DragTrace.log("Folder.getTargetRank visualCenter=(" + (int) visualCenter[0] + "," + (int) visualCenter[1]
                        + ") local=(" + (int) mTargetRankCoord[0] + "," + (int) mTargetRankCoord[1]
                        + ") rank=" + rank + " page=" + pageIndex);
                return rank;
            }
        }
        int fallback = mContent != null ? mContent.findNearestArea(
                (int) visualCenter[0] - getPaddingLeft(),
                (int) visualCenter[1] - getPaddingTop()) : 0;
        DragTrace.log("Folder.getTargetRank fallback rank=" + fallback);
        return fallback;
    }

    /**
     * Returns the dynamic page-flip trigger width inside the folder card edges.
     * Configured to 26dp (~78px on 3.0x display) to provide a generous, comfortable
     * touch target while keeping a safe buffer from the side column icons.
     */
    private float getPageFlipTriggerWidth() {
        float density = getResources().getDisplayMetrics().density;
        return density * 26f;
    }

    /**
     * Returns the extension beyond the folder squircle card boundaries so page flipping
     * can be comfortably triggered near or past the card edges without prematurely exiting
     * to the workspace.
     *
     * Uses Math.abs(mScrollAreaOffset) + padding to guarantee strictly symmetric, positive
     * reach regardless of where on the icon the user initiated the drag touch.
     */
    public int getPageFlipBeyondGutter() {
        float density = getResources().getDisplayMetrics().density;
        return Math.max((int) (density * 28f), Math.abs(mScrollAreaOffset) + (int) (density * 16f));
    }

    @Override
    public void onDragOver(DragObject d) {
        if (mScrollPauseAlarm.alarmPending()) {
            return;
        }
        final float[] r = new float[2];
        mTargetRank = getTargetRank(d, r);

        if (mTargetRank != mPrevTargetRank) {
            DragTrace.log("Folder.onDragOver targetRankChanged: " + mPrevTargetRank + " -> " + mTargetRank);
            mReorderAlarm.cancelAlarm();
            mReorderAlarm.setOnAlarmListener(mReorderAlarmListener);
            mReorderAlarm.setAlarm(REORDER_DELAY);
            mPrevTargetRank = mTargetRank;

            if (d.stateAnnouncer != null) {
                d.stateAnnouncer.announce(getContext().getString(R.string.move_to_position,
                        mTargetRank + 1));
            }
        }

        float visualX = r[0];
        float touchX = d.x;
        int currentPage = mContent.getNextPage();

        float cellOverlap = getPageFlipTriggerWidth();
        // Trigger if either the visual center of the icon OR the user's touch finger
        // enters the edge trigger zone or moves beyond the card edge.
        boolean isOutsideLeftEdge = visualX < cellOverlap || touchX < cellOverlap;
        boolean isOutsideRightEdge = visualX > (getWidth() - cellOverlap)
                || touchX > (getWidth() - cellOverlap);

        if (currentPage > 0 && (mContent.mIsRtl ? isOutsideRightEdge : isOutsideLeftEdge)) {
            showScrollHint(SCROLL_LEFT, d);
        } else if (currentPage < (mContent.getPageCount() - 1)
                && (mContent.mIsRtl ? isOutsideLeftEdge : isOutsideRightEdge)) {
            showScrollHint(SCROLL_RIGHT, d);
        } else if (currentPage == (mContent.getPageCount() - 1)
                && (mContent.mIsRtl ? isOutsideLeftEdge : isOutsideRightEdge)) {
            if (mScrollHintDir != SCROLL_RIGHT) {
                mContent.createAndAddNewPage();
            }
            showScrollHint(SCROLL_RIGHT, d);
        } else {
            mOnScrollHintAlarm.cancelAlarm();
            if (mScrollHintDir != SCROLL_NONE) {
                mContent.clearScrollHint();
                mScrollHintDir = SCROLL_NONE;
                if (mContent != null) {
                    mContent.cleanupEmptyPages();
                }
            }
        }
    }

    private void showScrollHint(int direction, DragObject d) {
        DragTrace.log("Folder.showScrollHint dir=" + direction + " currentPage=" + (mContent != null ? mContent.getNextPage() : -1));
        // Show scroll hint on the right
        if (mScrollHintDir != direction) {
            mContent.showScrollHint(direction);
            mScrollHintDir = direction;
        }

        // Set alarm for when the hint is complete
        if (!mOnScrollHintAlarm.alarmPending() || mCurrentScrollDir != direction) {
            mCurrentScrollDir = direction;
            mOnScrollHintAlarm.cancelAlarm();
            mOnScrollHintAlarm.setOnAlarmListener(new OnScrollHintListener(d));
            mOnScrollHintAlarm.setAlarm(SCROLL_HINT_DURATION);

            mReorderAlarm.cancelAlarm();
            mTargetRank = mEmptyCellRank;
        }
    }

    OnAlarmListener mOnExitAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            completeDragExit();
        }
    };

    public void completeDragExit() {
        DragTrace.log("Folder.completeDragExit closing folder=" + mInfo.id + " restoring dock/workspace alphas");
        if (mContent != null) {
            mContent.cleanupEmptyPages();
        }
        if (mIsOpen) {
            close(true);
            mIsOpen = false;
            mRearrangeOnClose = true;
        } else if (mState == STATE_ANIMATING) {
            mRearrangeOnClose = true;
        } else {
            rearrangeChildren();
            clearDragInfo();
        }

        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            Launcher launcher = mLauncherDelegate.getLauncher();
            if (launcher != null) {
                Workspace<?> workspace = launcher.getWorkspace();
                if (workspace != null) {
                    workspace.setVisibility(VISIBLE);
                    workspace.setAlpha(1f);
                    for (int i = 0; i < workspace.getPageCount(); i++) {
                        View page = workspace.getChildAt(i);
                        if (page instanceof CellLayout) {
                            CellLayout cl = (CellLayout) page;
                            cl.setVisibility(VISIBLE);
                            cl.setAlpha(1f);
                            if (cl.getShortcutsAndWidgets() != null) {
                                cl.getShortcutsAndWidgets().setVisibility(VISIBLE);
                                cl.getShortcutsAndWidgets().setAlpha(1f);
                            }
                        }
                    }
                    boolean isAppLib = workspace.isAppLibraryCurrentPage();
                    if (!isAppLib) {
                        View pi = workspace.getPageIndicator();
                        if (pi != null) {
                            pi.setVisibility(VISIBLE);
                            pi.setAlpha(1f);
                            if (pi instanceof PageIndicatorDots) {
                                ((PageIndicatorDots) pi).setForcedTranslationY(0f);
                                ((PageIndicatorDots) pi).resetToIdle();
                            }
                            if (launcher.isInState(SPRING_LOADED)) {
                                workspace.showPageIndicatorAtCurrentScroll();
                            }
                        }
                    }
                }
                boolean isAppLib = workspace != null && workspace.isAppLibraryCurrentPage();
                if (!isAppLib) {
                    Hotseat hotseat = launcher.getHotseat();
                    if (hotseat != null) {
                        hotseat.setVisibility(VISIBLE);
                        hotseat.setAlpha(1f);
                        hotseat.setIconsAlpha(1f);
                        hotseat.setForcedTranslationY(0f);
                    }
                    View dockGlass = launcher.findViewById(R.id.dock_glass);
                    if (dockGlass != null) {
                        dockGlass.setVisibility(VISIBLE);
                        dockGlass.setAlpha(1f);
                        dockGlass.setTranslationY(0f);
                    }
                    if (launcher.mBlurLayer != null) {
                        launcher.mBlurLayer.setAlpha(0f);
                    }
                }
            }
        }
    }

    private void clearDragInfo() {
        mCurrentDragView = null;
        mIsExternalDrag = false;
    }

    public void onDragExit(DragObject d) {
        DragTrace.log("Folder.onDragExit dragComplete=" + d.dragComplete);
        // We only close the folder if this is a true drag exit, ie. not because
        // a drop has occurred above the folder.
        if (!d.dragComplete) {
            if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                mOnExitAlarm.cancelAlarm();
                completeDragExit();
            } else {
                mOnExitAlarm.setOnAlarmListener(mOnExitAlarmListener);
                mOnExitAlarm.setAlarm(ON_EXIT_CLOSE_DELAY);
            }
        }
        mReorderAlarm.cancelAlarm();

        mOnScrollHintAlarm.cancelAlarm();
        mScrollPauseAlarm.cancelAlarm();
        if (mScrollHintDir != SCROLL_NONE) {
            mContent.clearScrollHint();
            mScrollHintDir = SCROLL_NONE;
            if (mContent != null) {
                mContent.cleanupEmptyPages();
            }
        }
    }

    /**
     * When performing an accessibility drop, onDrop is sent immediately after onDragEnter. So we
     * need to complete all transient states based on timers.
     */
    @Override
    public void prepareAccessibilityDrop() {
        if (mReorderAlarm.alarmPending()) {
            mReorderAlarm.cancelAlarm();
            mReorderAlarmListener.onAlarm(mReorderAlarm);
        }
    }

    @Override
    public void onDropCompleted(final View target, final DragObject d,
            final boolean success) {
        if (success) {
            DragTrace.log("Folder.onDropCompleted folder=" + mInfo.id
                    + " itemCount=" + getItemCount() + " target=" + target
                    + " thisFolder=" + (target == this));
            if (getItemCount() <= 1) {
                mDeleteFolderOnDropCompleted = true;
            }
            if (mDeleteFolderOnDropCompleted && !mItemAddedBackToSelfViaIcon && target != this) {
                replaceFolderWithFinalItem();
            }
        } else {
            // The drag failed, we need to return the item to the folder
            WorkspaceItemInfo info = (WorkspaceItemInfo) d.dragInfo;
            View icon = (mCurrentDragView != null && mCurrentDragView.getTag() == info)
                    ? mCurrentDragView : mContent.createNewView(info);
            ArrayList<View> views = getIconsInReadingOrder();
            info.rank = Utilities.boundToRange(info.rank, 0, views.size());
            views.add(info.rank, icon);
            mContent.arrangeChildren(views);
            mItemsInvalidated = true;

            try (SuppressInfoChanges s = new SuppressInfoChanges()) {
                mFolderIcon.onDrop(d, true /* itemReturnedOnFailedDrop */);
                Collections.sort(mInfo.contents, ITEM_POS_COMPARATOR);
            }
        }

        if (target != this) {
            if (mIsOpen) {
                close(true);
            }
            if (mOnExitAlarm.alarmPending()) {
                mOnExitAlarm.cancelAlarm();
                if (!success) {
                    mSuppressFolderDeletion = true;
                }
                mScrollPauseAlarm.cancelAlarm();
                completeDragExit();
            }
        }

        mDeleteFolderOnDropCompleted = false;
        mDragInProgress = false;
        mItemAddedBackToSelfViaIcon = false;
        mCurrentDragView = null;

        // Reordering may have occured, and we need to save the new item locations. We do this once
        // at the end to prevent unnecessary database operations.
        updateItemLocationsInDatabaseBatch(false);
        // Use the item count to check for multi-page as the folder UI may not have
        // been refreshed yet.
        if (getItemCount() <= mContent.itemsPerPage()) {
            // Show the animation, next time something is added to the folder.
            mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, false,
                    mLauncherDelegate.getModelWriter());
        }
    }

    private void updateItemLocationsInDatabaseBatch(boolean isBind) {
        if (!isBind && mContent != null && mContent.areViewsBound()) {
            // Reordering updates the visible child positions, while mInfo.contents can retain
            // its pre-reorder list order. Persist in the live reading order so a successful
            // drag out does not write the old folder order back to the database.
            ArrayList<WorkspaceItemInfo> orderedItems = new ArrayList<>();
            for (View iconView : getIconsInReadingOrder()) {
                Object tag = iconView.getTag();
                if (!(tag instanceof WorkspaceItemInfo)) {
                    orderedItems.clear();
                    break;
                }
                orderedItems.add((WorkspaceItemInfo) tag);
            }
            if (orderedItems.size() == mInfo.contents.size()) {
                mInfo.contents.clear();
                mInfo.contents.addAll(orderedItems);
            }
        }

        FolderGridOrganizer verifier = new FolderGridOrganizer(
                mActivityContext.getDeviceProfile()).setFolderInfo(mInfo);

        ArrayList<ItemInfo> items = new ArrayList<>();
        int total = mInfo.contents.size();
        for (int i = 0; i < total; i++) {
            WorkspaceItemInfo itemInfo = mInfo.contents.get(i);
            if (verifier.updateRankAndPos(itemInfo, i)) {
                items.add(itemInfo);
            }
        }

        if (!items.isEmpty()) {
            mLauncherDelegate.getModelWriter().moveItemsInDatabase(items, mInfo.id, 0);
        }
        if (!isBind && total > 1 /* no need to update if there's one icon */) {
            Executors.MODEL_EXECUTOR.post(() -> {
                FolderNameInfos nameInfos = new FolderNameInfos();
                FolderNameProvider fnp = FolderNameProvider.newInstance(getContext());
                fnp.getSuggestedFolderName(
                        getContext(), mInfo.contents, nameInfos);
                mInfo.suggestedFolderNames = nameInfos;
            });
        }
    }

    public void notifyDrop() {
        if (mDragInProgress) {
            mItemAddedBackToSelfViaIcon = true;
        }
    }

    public boolean isDropEnabled() {
        return mIsOpen && mState != STATE_ANIMATING;
    }

    protected void centerAboutIcon() {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) getLayoutParams();
        BaseDragLayer parent = mActivityContext.getDragLayer();
        int width = getFolderWidth();
        int height = getFolderHeight();

        parent.getDescendantRectRelativeToSelf(mFolderIcon, sTempRect);
        int centerX = sTempRect.centerX();
        int centerY = sTempRect.centerY();
        int centeredLeft = centerX - width / 2;
        int centeredTop = centerY - height / 2;

        sTempRect.set(mActivityContext.getFolderBoundingBox());
        int left = Utilities.boundToRange(centeredLeft, sTempRect.left, sTempRect.right - width);
        int top = Utilities.boundToRange(centeredTop, sTempRect.top, sTempRect.bottom - height);
        int[] inOutPosition = new int[]{left, top};
        mActivityContext.updateOpenFolderPosition(inOutPosition, sTempRect, width, height);
        left = inOutPosition[0];
        top = inOutPosition[1];

        int folderPivotX = width / 2 + (centeredLeft - left);
        int folderPivotY = height / 2 + (centeredTop - top);
        setPivotX(folderPivotX);
        setPivotY(folderPivotY);

        lp.width = width;
        lp.height = height;
        lp.x = left;
        lp.y = top;

        mBackground.setBounds(0, 0, width, height);
    }

    protected int getContentAreaHeight() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int maxContentAreaHeight = grid.availableHeightPx - grid.getTotalWorkspacePadding().y
                - mFooterHeight;
        int height = Math.min(maxContentAreaHeight,
                mContent.getDesiredHeight());
        return Math.max(height, MIN_CONTENT_DIMEN);
    }

    private int getContentAreaWidth() {
        return Math.max(mContent.getDesiredWidth(), MIN_CONTENT_DIMEN);
    }

    private int getFolderWidth() {
        return getPaddingLeft() + getPaddingRight() + mContent.getDesiredWidth();
    }

    private int getFolderHeight() {
        return getFolderHeight(getContentAreaHeight());
    }

    private int getFolderHeight(int contentAreaHeight) {
        return getPaddingTop() + getPaddingBottom() + contentAreaHeight + mFooterHeight;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentWidth = getContentAreaWidth();
        int contentHeight = getContentAreaHeight();

        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);
        int contentAreaHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);

        mContent.setFixedSize(contentWidth, contentHeight);
        mContent.measure(contentAreaWidthSpec, contentAreaHeightSpec);

        mFooter.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(mFooterHeight, MeasureSpec.EXACTLY));

        int folderWidth = getPaddingLeft() + getPaddingRight() + contentWidth;
        int folderHeight = getFolderHeight(contentHeight);
        setMeasuredDimension(folderWidth, folderHeight);
    }

    /**
     * Rearranges the children based on their rank.
     */
    public void rearrangeChildren() {
        if (!mContent.areViewsBound()) {
            return;
        }
        mContent.arrangeChildren(getIconsInReadingOrder());
        mItemsInvalidated = true;
    }

    public int getItemCount() {
        return mInfo.contents.size();
    }

    void replaceFolderWithFinalItem() {
        if (mDestroyed) {
            return;
        }
        mDestroyed = mLauncherDelegate.replaceFolderWithFinalItem(this);
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    public boolean isDragInProgress() {
        return mDragInProgress;
    }

    private boolean isDragActive() {
        return mDragInProgress;
    }

    // This method keeps track of the first and last item in the folder for the purposes
    // of keyboard focus
    public void updateTextViewFocus() {
        final View firstChild = mContent.getFirstItem();
        final View lastChild = mContent.getLastItem();
        if (firstChild != null && lastChild != null) {
            mFolderName.setNextFocusDownId(lastChild.getId());
            mFolderName.setNextFocusRightId(lastChild.getId());
            mFolderName.setNextFocusLeftId(lastChild.getId());
            mFolderName.setNextFocusUpId(lastChild.getId());
            // Hitting TAB from the folder name wraps around to the first item on the current
            // folder page, and hitting SHIFT+TAB from that item wraps back to the folder name.
            mFolderName.setNextFocusForwardId(firstChild.getId());
            // When clicking off the folder when editing the name, this Folder gains focus. When
            // pressing an arrow key from that state, give the focus to the first item.
            this.setNextFocusDownId(firstChild.getId());
            this.setNextFocusRightId(firstChild.getId());
            this.setNextFocusLeftId(firstChild.getId());
            this.setNextFocusUpId(firstChild.getId());
            // When pressing shift+tab in the above state, give the focus to the last item.
            setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    boolean isShiftPlusTab = keyCode == KeyEvent.KEYCODE_TAB &&
                            event.hasModifiers(KeyEvent.META_SHIFT_ON);
                    if (isShiftPlusTab && Folder.this.isFocused()) {
                        return lastChild.requestFocus();
                    }
                    return false;
                }
            });
        } else {
            setOnKeyListener(null);
        }
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        // If the icon was dropped while the page was being scrolled, or if a target
        // change occurred within the reorder delay, compute target location and flush reorder.
        int dropRank = getTargetRank(d, null);
        boolean flushAlarm = !mContent.rankOnCurrentPage(mEmptyCellRank) || dropRank != mEmptyCellRank;
        DragTrace.log("Folder.onDrop dropRank=" + dropRank + " emptyRank=" + mEmptyCellRank
                + " flushAlarm=" + flushAlarm + " item=" + (d.dragInfo != null ? d.dragInfo.title : "null"));
        if (flushAlarm) {
            mTargetRank = dropRank;
            mReorderAlarmListener.onAlarm(mReorderAlarm);
            mOnScrollHintAlarm.cancelAlarm();
            mScrollPauseAlarm.cancelAlarm();
        }
        mContent.completePendingPageChanges();
        Launcher launcher = mLauncherDelegate.getLauncher();
        if (launcher == null) {
            return;
        }

        PendingAddShortcutInfo pasi = d.dragInfo instanceof PendingAddShortcutInfo
                ? (PendingAddShortcutInfo) d.dragInfo : null;
        WorkspaceItemInfo pasiSi =
                pasi != null ? pasi.getActivityInfo(launcher).createWorkspaceItemInfo() : null;
        if (pasi != null && pasiSi == null) {
            // There is no WorkspaceItemInfo, so we have to go through a configuration activity.
            pasi.container = mInfo.id;
            pasi.rank = mEmptyCellRank;

            launcher.addPendingItem(pasi, pasi.container, pasi.screenId, null, pasi.spanX,
                    pasi.spanY);
            d.deferDragViewCleanupPostAnimation = false;
            mRearrangeOnClose = true;
        } else {
            final WorkspaceItemInfo si;
            if (pasiSi != null) {
                si = pasiSi;
            } else if (d.dragInfo instanceof WorkspaceItemFactory) {
                // Came from all apps -- make a copy.
                si = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(launcher);
            } else {
                // WorkspaceItemInfo
                si = (WorkspaceItemInfo) d.dragInfo;
            }

            if (si != null && si.getTargetComponent() != null) {
                com.android.launcher3.util.RemovedAppStore.unmarkRemoved(
                        getContext(), si.getTargetComponent().flattenToString());
            }

            View currentDragView;
            if (mIsExternalDrag) {
                currentDragView = mContent.createAndAddViewForRank(si, mEmptyCellRank);

                // Actually move the item in the database if it was an external drag. Call this
                // before creating the view, so that WorkspaceItemInfo is updated appropriately.
                mLauncherDelegate.getModelWriter().addOrMoveItemInDatabase(
                        si, mInfo.id, 0, si.cellX, si.cellY);
                mIsExternalDrag = false;
            } else {
                currentDragView = mCurrentDragView;
                mContent.addViewForRank(currentDragView, si, mEmptyCellRank);
            }

            if (d.dragView.hasDrawn()) {
                // Temporarily reset the scale such that the animation target gets calculated
                // correctly.
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                launcher.getDragLayer().animateViewIntoPosition(d.dragView, currentDragView, null);
                setScaleX(scaleX);
                setScaleY(scaleY);
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                currentDragView.setVisibility(VISIBLE);
            }

            mItemsInvalidated = true;
            rearrangeChildren();

            // Temporarily suppress the listener, as we did all the work already here.
            try (SuppressInfoChanges s = new SuppressInfoChanges()) {
                mInfo.add(si, mEmptyCellRank, false);
                Collections.sort(mInfo.contents, ITEM_POS_COMPARATOR);
            }

            // We only need to update the locations if it doesn't get handled in
            // #onDropCompleted.
            if (d.dragSource != this) {
                updateItemLocationsInDatabaseBatch(false);
            }
        }

        // Clear the drag info, as it is no longer being dragged.
        mDragInProgress = false;
        if (mContent != null) {
            mContent.cleanupEmptyPages();
        }

        if (mContent.getPageCount() > 1) {
            // The animation has already been shown while opening the folder.
            mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true,
                    mLauncherDelegate.getModelWriter());
        }

        if (!launcher.isInState(EDIT_MODE)) {
            launcher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
        }

        if (d.stateAnnouncer != null) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
        mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                .log(LAUNCHER_ITEM_DROP_COMPLETED);
    }

    // This is used so the item doesn't immediately appear in the folder when added. In one case
    // we need to create the illusion that the item isn't added back to the folder yet, to
    // to correspond to the animation of the icon back into the folder. This is
    public void hideItem(WorkspaceItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(INVISIBLE);
        }
    }

    public void showItem(WorkspaceItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onAdd(WorkspaceItemInfo item, int rank) {
        FolderGridOrganizer verifier = new FolderGridOrganizer(
                mActivityContext.getDeviceProfile()).setFolderInfo(mInfo);
        verifier.updateRankAndPos(item, rank);
        mLauncherDelegate.getModelWriter().addOrMoveItemInDatabase(item, mInfo.id, 0, item.cellX,
                item.cellY);
        updateItemLocationsInDatabaseBatch(false);

        if (mContent.areViewsBound()) {
            mContent.createAndAddViewForRank(item, rank);
        }
        mItemsInvalidated = true;
    }

    @Override
    public void onRemove(List<WorkspaceItemInfo> items) {
        Log.d("ICON_TRACE", "onRemove folder=" + mInfo.id + " items=" + items + " mDragInProgress=" + mDragInProgress + " itemCount=" + getItemCount());
        mItemsInvalidated = true;
        items.stream().map(this::getViewForInfo).forEach(mContent::removeItem);
        if (mState == STATE_ANIMATING) {
            mRearrangeOnClose = true;
        } else {
            rearrangeChildren();
        }
        boolean isDragging = isDragActive();
        if (!isDragging && getItemCount() <= 1) {
            if (mIsOpen) {
                close(true);
            } else {
                replaceFolderWithFinalItem();
            }
        } else if (isDragging && getItemCount() <= 1) {
            mDeleteFolderOnDropCompleted = true;
        }
    }

    private View getViewForInfo(final WorkspaceItemInfo item) {
        return mContent.iterateOverItems((info, view) -> info == item);
    }

    @Override
    public void onItemsChanged(boolean animate) {
        updateTextViewFocus();
    }

    /**
     * Utility methods to iterate over items of the view
     */
    public void iterateOverItems(ItemOperator op) {
        mContent.iterateOverItems(op);
    }

    /**
     * Returns the sorted list of all the icons in the folder
     */
    public ArrayList<View> getIconsInReadingOrder() {
        if (mItemsInvalidated) {
            mItemsInReadingOrder.clear();
            mContent.iterateOverItems((i, v) -> !mItemsInReadingOrder.add(v));
            mItemsInvalidated = false;
        }
        return mItemsInReadingOrder;
    }

    public List<BubbleTextView> getItemsOnPage(int page) {
        ArrayList<View> allItems = getIconsInReadingOrder();
        int totalItemsInFolder = allItems.size();
        int itemsPerPage = mContent.itemsPerPage();
        if (page < 0 || itemsPerPage <= 0) {
            return Collections.emptyList();
        }

        // The current page can briefly outlive its items while a drag removes the last
        // shortcuts from it. Derive the range from the live item count and reject a stale
        // page before calculating an ArrayList capacity.
        long startIndexLong = (long) page * itemsPerPage;
        if (startIndexLong >= totalItemsInFolder) {
            return Collections.emptyList();
        }

        int startIndex = (int) startIndexLong;
        int endIndex = (int) Math.min(startIndexLong + itemsPerPage, totalItemsInFolder);
        List<BubbleTextView> itemsOnCurrentPage = new ArrayList<>(endIndex - startIndex);
        for (int i = startIndex; i < endIndex; ++i) {
            itemsOnCurrentPage.add((BubbleTextView) allItems.get(i));
        }
        return itemsOnCurrentPage;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mFolderName) {
            if (hasFocus) {
                mFromLabelState = mInfo.getFromLabelState();
                mFromTitle = mInfo.title;
                if (!mIsEditingName) {
                    startEditingFolderName();
                }
            } else {
                StatsLogger statsLogger = mStatsLogManager.logger()
                        .withItemInfo(mInfo)
                        .withFromState(mFromLabelState);

                // If the folder label is suggested, it is logged to improve prediction model.
                // When both old and new labels are logged together delimiter is used.
                StringJoiner labelInfoBuilder = new StringJoiner(FOLDER_LABEL_DELIMITER);
                if (mFromLabelState.equals(FromState.FROM_SUGGESTED)) {
                    labelInfoBuilder.add(mFromTitle);
                }

                ToState toLabelState;
                if (mFromTitle != null && mFromTitle.equals(mInfo.title)) {
                    toLabelState = ToState.UNCHANGED;
                } else {
                    toLabelState = mInfo.getToLabelState();
                    if (toLabelState.toString().startsWith("TO_SUGGESTION")) {
                        labelInfoBuilder.add(mInfo.title);
                    }
                }
                statsLogger.withToState(toLabelState);

                if (labelInfoBuilder.length() > 0) {
                    statsLogger.withEditText(labelInfoBuilder.toString());
                }

                statsLogger.log(LAUNCHER_FOLDER_LABEL_UPDATED);
                mFolderName.dispatchBackKey();
            }
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        getHitRect(outRect);
        int pageFlipExtension = getPageFlipBeyondGutter();
        outRect.left -= pageFlipExtension;
        outRect.right += pageFlipExtension;
    }

    private class OnScrollHintListener implements OnAlarmListener {

        private final DragObject mDragObject;

        OnScrollHintListener(DragObject object) {
            mDragObject = object;
        }

        /**
         * Scroll hint has been shown long enough. Now scroll to appropriate page.
         */
        @Override
        public void onAlarm(Alarm alarm) {
            if (mCurrentScrollDir == SCROLL_LEFT) {
                mContent.scrollLeft();
                mScrollHintDir = SCROLL_NONE;
            } else if (mCurrentScrollDir == SCROLL_RIGHT) {
                mContent.scrollRight();
                mScrollHintDir = SCROLL_NONE;
            } else {
                // This should not happen
                return;
            }
            mCurrentScrollDir = SCROLL_NONE;

            // Pause drag event until the scrolling is finished
            mScrollPauseAlarm.setOnAlarmListener(new OnScrollFinishedListener(mDragObject));
            int rescrollDelay = getResources().getInteger(
                    R.integer.config_pageSnapAnimationDuration) + RESCROLL_EXTRA_DELAY;
            mScrollPauseAlarm.setAlarm(rescrollDelay);
        }
    }

    private class OnScrollFinishedListener implements OnAlarmListener {

        private final DragObject mDragObject;

        OnScrollFinishedListener(DragObject object) {
            mDragObject = object;
        }

        /**
         * Page scroll is complete.
         */
        @Override
        public void onAlarm(Alarm alarm) {
            // Reorder immediately on page change.
            onDragOver(mDragObject);
        }
    }

    // Compares item position based on rank and position giving priority to the rank.
    public static final Comparator<ItemInfo> ITEM_POS_COMPARATOR = new Comparator<ItemInfo>() {

        @Override
        public int compare(ItemInfo lhs, ItemInfo rhs) {
            if (lhs.rank != rhs.rank) {
                return lhs.rank - rhs.rank;
            } else if (lhs.cellY != rhs.cellY) {
                return lhs.cellY - rhs.cellY;
            } else {
                return lhs.cellX - rhs.cellX;
            }
        }
    };

    /**
     * Temporary resource held while we don't want to handle info changes
     */
    private class SuppressInfoChanges implements AutoCloseable {

        SuppressInfoChanges() {
            mInfo.removeListenerIf(l -> l instanceof Folder);
        }

        @Override
        public void close() {
            mInfo.removeListenerIf(l -> l instanceof Folder);
            mInfo.addListener(Folder.this);
            updateTextViewFocus();
        }
    }

    /**
     * Returns a folder which is already open or null
     */
    public static Folder getOpen(ActivityContext activityContext) {
        return getOpenView(activityContext, TYPE_FOLDER);
    }

    @Override
    public void onBackStarted(@NonNull BackEvent backEvent) {
        // Prevent predictive back gestures from distorting folder scale/alpha
    }

    @Override
    public void onBackProgressed(@NonNull BackEvent backEvent) {
        // Do not distort scale or alpha during back gesture
    }

    @Override
    public void onBackCancelled() {
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
    }

    /** Navigation bar back key or hardware input back key has been issued. */
    @Override
    public void onBackInvoked() {
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        if (isEditingName()) {
            mFolderName.dispatchBackKey();
        } else {
            close(true);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = (BaseDragLayer) getParent();

            if (isEditingName()) {
                if (!dl.isEventOverView(mFolderName, ev)) {
                    mFolderName.dispatchBackKey();
                    return true;
                }
                return false;
            } else if (!dl.isEventOverView(this, ev)
                    && mLauncherDelegate.interceptOutsideTouch(ev, dl, this)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return true;
    }

    /**
     * Alternative to using {@link #getClipToOutline()} as it only works with derivatives of
     * rounded rect.
     */
    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mClipPath != null) {
            int count = canvas.save();
            canvas.clipPath(mClipPath);
            mBackground.draw(canvas);
            canvas.restoreToCount(count);
            super.dispatchDraw(canvas);
        } else {
            mBackground.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    public FolderPagedView getContent() {
        return mContent;
    }

    /** Returns the height of the current folder's bottom edge from the bottom of the screen. */
    private int getHeightFromBottom() {
        BaseDragLayer.LayoutParams layoutParams = (BaseDragLayer.LayoutParams) getLayoutParams();
        int folderBottomPx = layoutParams.y + layoutParams.height;
        int windowBottomPx = mActivityContext.getDeviceProfile().heightPx;

        return windowBottomPx - folderBottomPx;
    }

    /**
     * Save this listener for the special case of when we update the state and concurrently
     * add another listener to {@link #mOnFolderStateChangedListeners} to avoid a
     * ConcurrentModificationException
     */
    public void setPriorityOnFolderStateChangedListener(OnFolderStateChangedListener listener) {
        mPriorityOnFolderStateChangedListener = listener;
    }

    private void setState(@FolderState int newState) {
        mState = newState;
        if (mPriorityOnFolderStateChangedListener != null) {
            mPriorityOnFolderStateChangedListener.onFolderStateChanged(mState);
        }
        for (OnFolderStateChangedListener listener : mOnFolderStateChangedListeners) {
            if (listener != null) {
                listener.onFolderStateChanged(mState);
            }
        }
    }

    /**
     * Adds the provided listener to the running list of Folder listeners
     * {@link #mOnFolderStateChangedListeners}
     */
    public void addOnFolderStateChangedListener(@Nullable OnFolderStateChangedListener listener) {
        if (listener != null) {
            mOnFolderStateChangedListeners.add(listener);
        }
    }

    /** Removes the provided listener from the running list of Folder listeners */
    public void removeOnFolderStateChangedListener(OnFolderStateChangedListener listener) {
        mOnFolderStateChangedListeners.remove(listener);
    }

    /** Listener that can be registered via {@link #addOnFolderStateChangedListener} */
    public interface OnFolderStateChangedListener {
        /** See {@link Folder.FolderState} */
        void onFolderStateChanged(@FolderState int newState);
    }

    protected void onFolderOpenStart() { }

    protected void onFolderCloseComplete() { }

    public void updateFolderOnAnimate(boolean isOpening) { }

    public int getUnusedOffsetYOnAnimate(boolean isOpening) {
        return 0;
    }

    @SuppressWarnings("unchecked")
    public <T extends View & ClipPathView> T getAnimateObject() {
        return (T) this;
    }
}
```

---

# src/com/android/launcher3/folder/FolderAnimationManager.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/folder/FolderAnimationManager.java`  
**Lines**: 669 | **Bytes**: 31395

```java
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

package com.android.launcher3.folder;

import static android.view.View.ALPHA;

import static com.android.launcher3.BubbleTextView.TEXT_ALPHA_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.graphics.IconShape.getShape;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.Property;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.PathInterpolator;

import androidx.core.content.ContextCompat;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;

import java.util.List;

import foundation.e.bliss.multimode.MultiModeController;

/**
 * Manages the opening and closing animations for a {@link Folder}.
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
public class FolderAnimationManager {

    private static final int FOLDER_NAME_ALPHA_DURATION = 32;
    private static final int LARGE_FOLDER_FOOTER_DURATION = 128;

    private Folder mFolder;
    private FolderPagedView mContent;
    private GradientDrawable mFolderBackground;

    private FolderIcon mFolderIcon;
    private PreviewBackground mPreviewBackground;

    private Context mContext;

    private final boolean mIsOpening;

    private final int mDuration;
    private final int mDelay;

    private final TimeInterpolator mFolderInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemOpenInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemCloseInterpolator;

    private final PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final FolderGridOrganizer mPreviewVerifier;

    private ObjectAnimator mBgColorAnimator;

    private DeviceProfile mDeviceProfile;

    public FolderAnimationManager(Folder folder, boolean isOpening) {
        mFolder = folder;
        mContent = folder.mContent;
        mFolderBackground = (GradientDrawable) mFolder.getBackground();

        mFolderIcon = folder.mFolderIcon;
        mPreviewBackground = mFolderIcon.mBackground;

        mContext = folder.getContext();
        mDeviceProfile = folder.mActivityContext.getDeviceProfile();
        mPreviewVerifier = new FolderGridOrganizer(mDeviceProfile);

        mIsOpening = isOpening;

        Resources res = mContent.getResources();
        mDelay = res.getInteger(R.integer.config_folderDelay);

        if (MultiModeController.isSingleLayerMode()) {
            mDuration = 280;
            mFolderInterpolator = new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
        } else {
            mDuration = res.getInteger(R.integer.config_materialFolderExpandDuration);
            mFolderInterpolator = AnimationUtils.loadInterpolator(mContext,
                    R.interpolator.standard_interpolator);
        }
        mLargeFolderPreviewItemOpenInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.large_folder_preview_item_open_interpolator);
        mLargeFolderPreviewItemCloseInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.standard_accelerate_interpolator);
    }

    /**
     * Returns the animator that changes the background color.
     */
    public ObjectAnimator getBgColorAnimator() {
        return mBgColorAnimator;
    }

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    public AnimatorSet getAnimator() {
        android.util.Log.d("ANIM_TRACE", "FolderAnimationManager.getAnimator isOpening=" + mIsOpening + " folderId=" + mFolder.mInfo.id);
        final BaseDragLayer.LayoutParams lp =
                (BaseDragLayer.LayoutParams) mFolder.getLayoutParams();
        mFolderIcon.getPreviewItemManager().recomputePreviewDrawingParams();
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<BubbleTextView> itemsInPreview = getPreviewIconsOnPage(0);

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = mFolder.mActivityContext.getDragLayer()
                .getDescendantRectRelativeToSelf(mFolderIcon, folderIconPos);
        int scaledRadius = mPreviewBackground.getScaledRadius();
        float initialSize = (MultiModeController.isSingleLayerMode()
                ? mPreviewBackground.previewSize
                : (scaledRadius * 2)) * scaleRelativeToDragLayer;

        // Match size/scale of icons in the preview
        int numItems = Math.max(itemsInPreview.size(), 1);
        float previewScale = rule.scaleForItem(numItems);
        float previewSize = rule.getIconSize() * previewScale;
        float itemIconSize = itemsInPreview.isEmpty()
                ? mDeviceProfile.iconSizePx
                : itemsInPreview.get(0).getIconSize();
        float initialScale = previewSize / itemIconSize
                * scaleRelativeToDragLayer;
        final float finalScale = 1f;
        float scale = mIsOpening ? initialScale : finalScale;
        mFolder.setPivotX(0);
        mFolder.setPivotY(0);

        // Scale the contents of the folder.
        float contentScale = MultiModeController.isSingleLayerMode() ? 1f : scale;
        mFolder.mContent.setScaleX(contentScale);
        mFolder.mContent.setScaleY(contentScale);
        mFolder.mContent.setPivotX(0);
        mFolder.mContent.setPivotY(0);
        mFolder.mFooter.setScaleX(contentScale);
        mFolder.mFooter.setScaleY(contentScale);
        mFolder.mFooter.setPivotX(0);
        mFolder.mFooter.setPivotY(0);

        mFolder.updateFolderOnAnimate(mIsOpening);
        int unusedOffsetY = (int) (mFolder.getUnusedOffsetYOnAnimate(mIsOpening) * initialScale);


        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        int previewItemOffsetX = MultiModeController.isSingleLayerMode() ?  0: (int) (previewSize / 2);
        if (Utilities.isRtl(mContext.getResources())) {
            previewItemOffsetX = (int) (lp.width * initialScale - initialSize - previewItemOffsetX);
        }

        final int paddingOffsetX = (int) (mContent.getPaddingLeft() * initialScale);
        final int paddingOffsetY = (int) (mContent.getPaddingTop() * initialScale);

        int initialX = folderIconPos.left + mFolder.getPaddingLeft()
                + Math.round(mPreviewBackground.getOffsetX() * scaleRelativeToDragLayer)
                - paddingOffsetX - previewItemOffsetX;
        int initialY = folderIconPos.top + mFolder.getPaddingTop()
                + Math.round(mPreviewBackground.getOffsetY() * scaleRelativeToDragLayer)
                - paddingOffsetY;

        // In single-layer mode (GridFolder), we scale the entire folder (iOS style).
        // This requires aligning the scaled content position to the icon position.
        if (MultiModeController.isSingleLayerMode()
                && mFolder instanceof foundation.e.bliss.folder.GridFolder) {
            float folderScaleEnd = initialSize / Math.max(lp.width, 1);
            int folderTabHeight = ((foundation.e.bliss.folder.GridFolder) mFolder).mFolderTabHeight;

            float scaledOffsetX = mFolder.getPaddingLeft() * folderScaleEnd;
            float scaledOffsetY = (mFolder.getPaddingTop() + folderTabHeight) * folderScaleEnd;

            initialX = (int) (folderIconPos.left + Math.round(mPreviewBackground.basePreviewOffsetX * scaleRelativeToDragLayer) - scaledOffsetX);
            initialY = (int) (folderIconPos.top + Math.round(mPreviewBackground.basePreviewOffsetY * scaleRelativeToDragLayer) - scaledOffsetY);
        }
        
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y - unusedOffsetY;

        // Set up the Folder background.
        int initialColor = Themes.getAttrColor(mContext, R.attr.folderPreviewColor);
        int finalColor = Themes.getAttrColor(mContext, R.attr.folderBackgroundColor);

        if (MultiModeController.isSingleLayerMode()) {
            initialColor = android.graphics.Color.TRANSPARENT;
            finalColor = android.graphics.Color.TRANSPARENT;
        }

        mFolderBackground.mutate();
        mFolderBackground.setColor(mIsOpening ? initialColor : finalColor);

        // Set up the reveal animation that clips the Folder.
        int totalOffsetX = paddingOffsetX + previewItemOffsetX;
        Rect startRect = new Rect(totalOffsetX,
                paddingOffsetY,
                Math.round((totalOffsetX + initialSize)),
                Math.round((paddingOffsetY + initialSize)));
        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        float finalRadius = mFolderBackground.getCornerRadius();

        // Create the animators.
        AnimatorSet a = new AnimatorSet();

        // Initialize the Folder items' text.
        PropertyResetListener colorResetListener =
                new PropertyResetListener<>(TEXT_ALPHA_PROPERTY, 1f);
        for (BubbleTextView icon : mFolder.getItemsOnPage(mFolder.mContent.getCurrentPage())) {
            if (mIsOpening) {
                icon.setTextVisibility(false);
            }
            ObjectAnimator anim = icon.createTextAlphaAnimator(mIsOpening);
            anim.addListener(colorResetListener);
            play(a, anim);
        }

        mBgColorAnimator = getAnimator(mFolderBackground, "color", initialColor, finalColor);
        play(a, mBgColorAnimator);
        play(a, getAnimator(mFolder, View.TRANSLATION_X, xDistance, 0f));
        play(a, getAnimator(mFolder, View.TRANSLATION_Y, yDistance, 0f));
        
        // In standard Launcher3, the content and footer scale individually.
        // In iOS-style single-layer mode, we scale the ENTIRE folder view instead.
        if (!MultiModeController.isSingleLayerMode()) {
            play(a, getAnimator(mFolder.mContent, SCALE_PROPERTY, initialScale, finalScale));
            play(a, getAnimator(mFolder.mFooter, SCALE_PROPERTY, initialScale, finalScale));
        }

        final int footerAlphaDuration;
        final int footerStartDelay;
        if (isLargeFolder()) {
            if (mIsOpening) {
                mFolder.mFooter.setAlpha(0);
                footerAlphaDuration = LARGE_FOLDER_FOOTER_DURATION;
                footerStartDelay = mDuration - footerAlphaDuration;
            } else {
                footerAlphaDuration = 0;
                footerStartDelay = 0;
            }
        } else {
            footerStartDelay = 0;
            footerAlphaDuration = mDuration;
        }
        play(a, getAnimator(mFolder.mFooter, ALPHA, 0, 1f), footerStartDelay, footerAlphaDuration);

        // Create reveal animator for the folder background.
        if (MultiModeController.isSingleLayerMode()) {
            // iOS-style: scale the entire folder view symmetrically toward the icon,
            // and fade it out. This avoids the asymmetric clip-reveal that cuts
            // the right side off with a sharp edge.
            float folderScaleEnd = initialSize / Math.max(lp.width, 1);
            mFolder.setPivotX(0f);
            mFolder.setPivotY(0f);
            
            float folderScale = mIsOpening ? folderScaleEnd : 1f;
            mFolder.setScaleX(folderScale);
            mFolder.setScaleY(folderScale);
            
            Animator scaleXAnim = getAnimator(mFolder, View.SCALE_X, folderScaleEnd, 1f);
            if (mFolder instanceof foundation.e.bliss.folder.GridFolder) {
                View glassView = ((foundation.e.bliss.folder.GridFolder) mFolder).mGlassView;
                if (scaleXAnim instanceof ObjectAnimator && glassView != null) {
                    ((ObjectAnimator) scaleXAnim).addUpdateListener(anim -> glassView.invalidate());
                }
            }
            play(a, scaleXAnim);
            play(a, getAnimator(mFolder, View.SCALE_Y, folderScaleEnd, 1f));
            // Fade out the folder as it shrinks
            play(a, getAnimator(mFolder, View.ALPHA, 0f, 1f));
        } else {
            play(a, getShape().createRevealAnimator(
                    mFolder.getAnimateObject(), startRect, endRect, finalRadius, !mIsOpening));
        }
        if (MultiModeController.isSingleLayerMode()) {
            com.android.launcher3.Launcher launcher = com.android.launcher3.Launcher.getLauncher(mContext);
            BaseDragLayer dragLayer = mFolder.mActivityContext.getDragLayer();
            View dockGlass = dragLayer.findViewById(R.id.dock_glass);
            View hotseat = dragLayer.findViewById(R.id.hotseat);
            View pageIndicator = dragLayer.findViewById(R.id.page_indicator);
            View workspace = dragLayer.findViewById(R.id.workspace);
            View scrimView = dragLayer.findViewById(R.id.scrim_view);
            foundation.e.bliss.blur.BlurBackgroundView blurLayer = launcher.mBlurLayer;
            View qsb = hotseat instanceof com.android.launcher3.Hotseat ? ((com.android.launcher3.Hotseat) hotseat).getQsb() : null;
            
            // v1 = closed-state value, v2 = open-state value (fixed constants -- getAnimator's
            // own mIsOpening check handles direction, so don't also branch on it here or read
            // a live .getAlpha(); doing both was double-applying direction and caused these
            // views to animate the wrong way on close).
            boolean isDraggingExit = !mIsOpening && (mFolder.isDragInProgress() || launcher.getDragController().isDragging());
            if (dockGlass != null) {
                dockGlass.setVisibility(View.VISIBLE);
                if (isDraggingExit) {
                    dockGlass.setAlpha(1f);
                } else {
                    play(a, getAnimator(dockGlass, View.ALPHA, 1f, 0f));
                }
            }
            if (hotseat != null) {
                hotseat.setVisibility(View.VISIBLE);
                if (hotseat instanceof com.android.launcher3.Hotseat) {
                    ((com.android.launcher3.Hotseat) hotseat).setIconsAlpha(1f);
                }
                if (isDraggingExit) {
                    hotseat.setAlpha(1f);
                } else {
                    play(a, getAnimator(hotseat, View.ALPHA, 1f, 0f));
                }
            }
            if (pageIndicator != null) {
                pageIndicator.setVisibility(View.VISIBLE);
                if (isDraggingExit) {
                    pageIndicator.setAlpha(1f);
                } else {
                    play(a, getAnimator(pageIndicator, View.ALPHA, 1f, 0f));
                }
            }
            if (workspace != null) {
                workspace.setVisibility(View.VISIBLE);
                if (isDraggingExit) {
                    workspace.setAlpha(1f);
                } else {
                    play(a, getAnimator(workspace, View.ALPHA, 1f, 0f));
                }
            }
            if (qsb != null) {
                if (isDraggingExit) {
                    qsb.setAlpha(1f);
                } else {
                    play(a, getAnimator(qsb, View.ALPHA, 1f, 0f));
                }
            }
            if (scrimView != null) {
                if (isDraggingExit) {
                    scrimView.setAlpha(1f);
                } else {
                    play(a, getAnimator(scrimView, View.ALPHA, 1f, 0f));
                }
            }
            if (blurLayer != null) {
                if (isDraggingExit) {
                    blurLayer.setAlpha(0f);
                } else {
                    play(a, getAnimator(blurLayer, View.ALPHA, 0f, 1f));
                }
            }
            
            // Cross-fade the folder icon back in during close so it doesn't blink at the end.
            // Use ObjectAnimator directly — getAnimator() reverses values on close, which would
            // fade the icon OUT (1→0) instead of IN (0→1).
            if (!mIsOpening && mFolderIcon != null) {
                if (mFolder.isDragInProgress()) {
                    mFolderIcon.onItemsChanged(false);
                }
                // Restore mBackgroundIsVisible so the canvas actually draws the icon content.
                // setIconVisible(false) was called when opening; without this the icon draws nothing.
                mFolderIcon.setIconVisible(true);
                mFolderIcon.setAlpha(0f);
                mFolderIcon.setVisibility(View.VISIBLE);
                int iconFadeStartDelay = mDuration / 2;
                int iconFadeDuration = mDuration - iconFadeStartDelay;
                ObjectAnimator iconFade = ObjectAnimator.ofFloat(mFolderIcon, View.ALPHA, 0f, 1f);
                play(a, iconFade, iconFadeStartDelay, iconFadeDuration);
            }
        }

        int column = MultiModeController.isSingleLayerMode() ? 3 : 2;
        int row = MultiModeController.isSingleLayerMode() ? 3 : 2;

        // Create reveal animator for the folder content (capture the top 4 icons 2x2)
        int width = mDeviceProfile.folderCellLayoutBorderSpacePx.x
                + mDeviceProfile.folderCellWidthPx * row;
        int height = mDeviceProfile.folderCellLayoutBorderSpacePx.y
                + mDeviceProfile.folderCellHeightPx * column;
        int page = mIsOpening ? mContent.getCurrentPage() : mContent.getDestinationPage();
        int left = mContent.getPaddingLeft() + page * lp.width;
        Rect contentStart = new Rect(left, 0, left + width, height);
        Rect contentEnd = new Rect(left, 0, left + lp.width, lp.height);
        if (!MultiModeController.isSingleLayerMode()) {
            play(a, getShape().createRevealAnimator(
                    mFolder.getContent(), contentStart, contentEnd, finalRadius, !mIsOpening));
        }


        // Fade in the folder name, as the text can overlap the icons when grid size is small.
        mFolder.mFolderName.setAlpha(mIsOpening ? 0f : 1f);
        play(a, getAnimator(mFolder.mFolderName, View.ALPHA, 0, 1),
                mIsOpening ? FOLDER_NAME_ALPHA_DURATION : 0,
                mIsOpening ? mDuration - FOLDER_NAME_ALPHA_DURATION : FOLDER_NAME_ALPHA_DURATION);

        // Translate the footer so that it tracks the bottom of the content.
        if (!MultiModeController.isSingleLayerMode()) {
            float normalHeight = mFolder.getContentAreaHeight();
            float scaledHeight = normalHeight * initialScale;
            float diff = normalHeight - scaledHeight;
            play(a, getAnimator(mFolder.mFooter, View.TRANSLATION_Y, -diff, 0f));
        }

        // Animate the elevation midway so that the shadow is not noticeable in the background.
        int midDuration = mDuration / 2;
        Animator z = getAnimator(mFolder, View.TRANSLATION_Z, -mFolder.getElevation(), 0);
        play(a, z, mIsOpening ? midDuration : 0, midDuration);

        // Store clip variables.
        // Because {@link #onAnimationStart} and {@link #onAnimationEnd} callbacks are sent to
        // message queue and executed on separate frame, we should save states in
        // {@link #onAnimationStart} instead of before creating animator, so that cancelling
        // animation A and restarting animation B allows A to reset states in
        // {@link #onAnimationEnd} before B reads new UI state from {@link #onAnimationStart}.
        a.addListener(new AnimatorListenerAdapter() {
            private CellLayout mCellLayout;

            private boolean mFolderClipChildren;
            private boolean mFolderClipToPadding;
            private boolean mContentClipChildren;
            private boolean mContentClipToPadding;
            private boolean mCellLayoutClipChildren;
            private boolean mCellLayoutClipPadding;

            @Override
            public void onAnimationStart(Animator animator) {
                super.onAnimationStart(animator);
                mCellLayout = mContent.getCurrentCellLayout();
                mFolderClipChildren = mFolder.getClipChildren();
                mFolderClipToPadding = mFolder.getClipToPadding();
                mContentClipChildren = mContent.getClipChildren();
                mContentClipToPadding = mContent.getClipToPadding();
                mCellLayoutClipChildren = mCellLayout.getClipChildren();
                mCellLayoutClipPadding = mCellLayout.getClipToPadding();

                mFolder.setClipChildren(false);
                mFolder.setClipToPadding(false);
                mContent.setClipChildren(false);
                mContent.setClipToPadding(false);
                mCellLayout.setClipChildren(false);
                mCellLayout.setClipToPadding(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFolder.setTranslationX(0.0f);
                mFolder.setTranslationY(0.0f);
                mFolder.setTranslationZ(0.0f);
                mFolder.setScaleX(1f);
                mFolder.setScaleY(1f);
                mFolder.setAlpha(1f);
                mFolder.mContent.setScaleX(1f);
                mFolder.mContent.setScaleY(1f);
                mFolder.mFooter.setScaleX(1f);
                mFolder.mFooter.setScaleY(1f);
                mFolder.mFooter.setTranslationX(0f);
                mFolder.mFolderName.setAlpha(1f);

                mFolder.setClipChildren(mFolderClipChildren);
                mFolder.setClipToPadding(mFolderClipToPadding);
                mContent.setClipChildren(mContentClipChildren);
                mContent.setClipToPadding(mContentClipToPadding);
                mCellLayout.setClipChildren(mCellLayoutClipChildren);
                mCellLayout.setClipToPadding(mCellLayoutClipPadding);
                if (mFolder instanceof foundation.e.bliss.folder.GridFolder) {
                    View glassView = ((foundation.e.bliss.folder.GridFolder) mFolder).mGlassView;
                    if (glassView != null) {
                        glassView.invalidate();
                    }
                }
            }
        });

        // We set the interpolator on all current child animators here, because the preview item
        // animators may use a different interpolator.
        for (Animator animator : a.getChildAnimations()) {
            animator.setInterpolator(mFolderInterpolator);
        }

        int radiusDiff = scaledRadius - mPreviewBackground.getRadius();
        if (!MultiModeController.isSingleLayerMode() && !mFolder.isDragInProgress()) {
            addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,
                    // Background can have a scaled radius in drag and drop mode, so we need to add the
                    // difference to keep the preview items centered.
                    (int) (previewItemOffsetX / scaleRelativeToDragLayer) + radiusDiff, radiusDiff);
        }
        return a;
    }

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    private List<BubbleTextView> getPreviewIconsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mFolder.mInfo)
                .previewItemsForPage(page, mFolder.getIconsInReadingOrder());
    }

    /**
     * Animate the items on the current page.
     */
    private void addPreviewItemAnimators(AnimatorSet animatorSet, final float folderScale,
            int previewItemOffsetX, int previewItemOffsetY) {
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        boolean isOnFirstPage = mFolder.mContent.getCurrentPage() == 0;
        final List<BubbleTextView> itemsInPreview = getPreviewIconsOnPage(
                isOnFirstPage ? 0 : mFolder.mContent.getCurrentPage());
        final int numItemsInPreview = itemsInPreview.size();
        if (numItemsInPreview == 0) {
            return;
        }
        final int numItemsInFirstPagePreview = isOnFirstPage
                ? numItemsInPreview : mFolderIcon.getLayoutRule().getMaxNumItemsInPreview();

        TimeInterpolator previewItemInterpolator = getPreviewItemInterpolator();

        ShortcutAndWidgetContainer cwc = mContent.getPageAt(0).getShortcutsAndWidgets();
        for (int i = 0; i < numItemsInPreview; ++i) {
            final BubbleTextView btv = itemsInPreview.get(i);
            CellLayoutLayoutParams btvLp = (CellLayoutLayoutParams) btv.getLayoutParams();

            // Calculate the final values in the LayoutParams.
            btvLp.isLockedToGrid = true;
            cwc.setupLp(btv);

            // Match scale of icons in the preview of the items on the first page.
            float previewScale = rule.scaleForItem(numItemsInFirstPagePreview);
            float previewSize = rule.getIconSize() * previewScale;
            float iconScale = previewSize / itemsInPreview.get(i).getIconSize();

            final float initialScale = iconScale / folderScale;
            final float finalScale = 1f;
            float scale = mIsOpening ? initialScale : finalScale;
            btv.setScaleX(scale);
            btv.setScaleY(scale);

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, mTmpParams);
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            int iconOffsetX = (int) ((btvLp.width - btv.getIconSize()) * iconScale) / 2;

            final int previewPosX =
                    (int) ((mTmpParams.transX - iconOffsetX + previewItemOffsetX) / folderScale);
            final float paddingTop = btv.getPaddingTop() * iconScale;
            final int previewPosY = (int) ((mTmpParams.transY + previewItemOffsetY - paddingTop)
                    / folderScale);

            final float xDistance = previewPosX - btvLp.x;
            final float yDistance = previewPosY - btvLp.y;

            Animator translationX = getAnimator(btv, View.TRANSLATION_X, xDistance, 0f);
            translationX.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationX);

            Animator translationY = getAnimator(btv, View.TRANSLATION_Y, yDistance, 0f);
            translationY.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationY);

            Animator scaleAnimator = getAnimator(btv, SCALE_PROPERTY, initialScale, finalScale);
            scaleAnimator.setInterpolator(previewItemInterpolator);
            play(animatorSet, scaleAnimator);

            if (mFolder.getItemCount() > mFolderIcon.getLayoutRule().getMaxNumItemsInPreview()) {
                // These delays allows the preview items to move as part of the Folder's motion,
                // and its only necessary for large folders because of differing interpolators.
                int delay = mIsOpening ? mDelay : mDelay * 2;
                if (mIsOpening) {
                    translationX.setStartDelay(delay);
                    translationY.setStartDelay(delay);
                    scaleAnimator.setStartDelay(delay);
                }
                translationX.setDuration(translationX.getDuration() - delay);
                translationY.setDuration(translationY.getDuration() - delay);
                scaleAnimator.setDuration(scaleAnimator.getDuration() - delay);
            }

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    // Necessary to initialize values here because of the start delay.
                    if (mIsOpening) {
                        btv.setTranslationX(xDistance);
                        btv.setTranslationY(yDistance);
                        btv.setScaleX(initialScale);
                        btv.setScaleY(initialScale);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    btv.setTranslationX(0.0f);
                    btv.setTranslationY(0.0f);
                    btv.setScaleX(1f);
                    btv.setScaleY(1f);
                }
            });
        }
    }

    private void play(AnimatorSet as, Animator a) {
        play(as, a, a.getStartDelay(), mDuration);
    }

    private void play(AnimatorSet as, Animator a, long startDelay, int duration) {
        a.setStartDelay(startDelay);
        a.setDuration(duration);
        as.play(a);
    }

    private boolean isLargeFolder() {
        return mFolder.getItemCount() > mFolderIcon.getLayoutRule().getMaxNumItemsInPreview();
    }

    private TimeInterpolator getPreviewItemInterpolator() {
        if (isLargeFolder()) {
            // With larger folders, we want the preview items to reach their final positions faster
            // (when opening) and later (when closing) so that they appear aligned with the rest of
            // the folder items when they are both visible.
            return mIsOpening
                    ? mLargeFolderPreviewItemOpenInterpolator
                    : mLargeFolderPreviewItemCloseInterpolator;
        }
        return mFolderInterpolator;
    }

    private Animator getAnimator(View view, Property property, float v1, float v2) {
        return mIsOpening
                ? ObjectAnimator.ofFloat(view, property, v1, v2)
                : ObjectAnimator.ofFloat(view, property, v2, v1);
    }

    private ObjectAnimator getAnimator(GradientDrawable drawable, String property, int v1, int v2) {
        return mIsOpening
                ? ObjectAnimator.ofArgb(drawable, property, v1, v2)
                : ObjectAnimator.ofArgb(drawable, property, v2, v1);
    }
}
```

---

# bliss/src/foundation/e/bliss/folder/GridFolder.java

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/folder/GridFolder.java`  
**Lines**: 573 | **Bytes**: 22235

```java
/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.folder;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.util.Themes;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.pageindicators.PageIndicatorDots;

import java.util.concurrent.atomic.AtomicInteger;

import foundation.e.bliss.blur.BlurBackgroundView;

public class GridFolder extends Folder implements OnAlarmListener {
    private static final int MIN_CONTENT_DIMEN = 5;
    private final Launcher mLauncher;
    private final Alarm wobbleExpireAlarm = new Alarm();
    public View mFolderTab;
    public int mFolderTabHeight;
    public View mGlassView;
    public FrameLayout mGridFolderPage;
    private LauncherState mLastStateBeforeOpen = NORMAL;
    private boolean mNeedResetState = false;

    private boolean isFolderWobbling = false;
    private boolean isAnimating = false;

    public GridFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = mLauncherDelegate.getLauncher();
        wobbleExpireAlarm.setOnAlarmListener(this);
    }

    @SuppressLint("InflateParams")
    public static <T extends Context & ActivityContext> Folder fromXml(T activityContext) {
        return (Folder) LayoutInflater.from(activityContext).cloneInContext(activityContext)
                .inflate(R.layout.grid_folder_icon_normalized, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGridFolderPage = findViewById(R.id.grid_folder_page);

        mFolderTab = findViewById(R.id.folder_tab);
        mFolderName.setSelectAllOnFocus(false);
        mFolderName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        mFolderName.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mFolderName.setTextColor(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        mFolderName.setHintTextColor(Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText) ? 0x99000000 : 0x99FFFFFF);
        mFolderName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        mFolderName.setShadowLayer(0, 0, 0, 0);
        mFooterHeight = getResources().getDimensionPixelSize(R.dimen.grid_folder_footer_height);

        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int cellIconGap = (grid.folderCellWidthPx - mActivityContext.getDeviceProfile().iconSizePx);
        mContent.setPadding(cellIconGap, cellIconGap, cellIconGap, cellIconGap);
        updateFolderNamePadding();

        int measureSpec = MeasureSpec.UNSPECIFIED;
        mFolderTab.measure(measureSpec, measureSpec);
        mFolderTabHeight = mFolderTab.getMeasuredHeight();

        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            android.graphics.drawable.Drawable bg = mGridFolderPage.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) bg
                        .mutate();
                gd.setColor(android.graphics.Color.TRANSPARENT);
            }

            mGlassView = new foundation.e.bliss.glass.FolderOpenGlassView(getContext());
            mGlassView.setVisibility(VISIBLE);

            if (mGlassView != null) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                mGridFolderPage.addView(mGlassView, 0, lp);
            }
        }
    }

    @Override
    public View getGlassView() {
        return mGlassView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentWidth = getContentAreaWidth();
        int contentHeight = getContentAreaHeight();

        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);
        int contentAreaHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);

        mContent.setFixedSize(contentWidth, contentHeight);
        mContent.measure(contentAreaWidthSpec, contentAreaHeightSpec);

        mFolderTab.measure(contentAreaWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mFolderTabHeight = mFolderTab.getMeasuredHeight();

        mGridFolderPage.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(contentHeight + mFooterHeight, MeasureSpec.EXACTLY));

        mFooter.measure(contentAreaWidthSpec, MeasureSpec.makeMeasureSpec(mFooterHeight, MeasureSpec.EXACTLY));

        int folderWidth = getFolderWidth();
        int folderHeight = getFolderHeight();
        setMeasuredDimension(folderWidth, folderHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    public Drawable getBackground() {
        return mGridFolderPage.getBackground();
    }

    private int getContentAreaWidth() {
        return Math.max(mContent.getDesiredWidth(), MIN_CONTENT_DIMEN);
    }

    protected int getContentAreaHeight() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        int maxContentAreaHeight = grid.availableHeightPx - grid.getTotalWorkspacePadding().y
                + (grid.isVerticalBarLayout() ? 0 : grid.hotseatBarSizePx);
        int height = Math.min(maxContentAreaHeight, mContent.getDesiredHeight());
        return Math.max(height, MIN_CONTENT_DIMEN);
    }

    private int getFolderWidth() {
        return getPaddingLeft() + getPaddingRight() + mContent.getDesiredWidth();
    }

    private int getFolderHeight() {
        return mFolderTabHeight + mContent.getDesiredHeight() + mFooterHeight + getPaddingTop() + getPaddingBottom();
    }

    @Override
    protected void centerAboutIcon() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();

        int width = getFolderWidth();
        int height = getFolderHeight();
        int insetsTop = grid.getInsets().top;

        lp.width = width;
        lp.height = height;
        lp.x = (grid.availableWidthPx - width) / 2;
        if (grid.isVerticalBarLayout()) {
            lp.y = grid.getTotalWorkspacePadding().y / 2 + insetsTop;
        } else {
            int minTopHeight = insetsTop + grid.dropTargetBarSizePx;
            lp.y = Math.max((grid.availableHeightPx - height) / 2, minTopHeight);
        }
    }

    public void setNeedResetState(boolean isReset) {
        mNeedResetState = isReset;
    }

    private boolean isCanRestoredState(LauncherState state) {
        return state == NORMAL || state == OVERVIEW;
    }

    @Override
    public void onFolderOpenStart() {
        mLastStateBeforeOpen = mLauncher.getStateManager().getState();
        if (!mLauncher.isInState(NORMAL)) {
            mLauncher.getStateManager().goToState(LauncherState.NORMAL, false);
            if (isCanRestoredState(mLastStateBeforeOpen)) {
                mNeedResetState = true;
            }
        }

        updateFolderNamePadding();
        showOrHideDesktop(mLauncher, true);
        if (mLauncher.getWorkspace().isWobbling()) {
            wobbleFolder(true);
        } else if (isFolderWobbling) {
            wobbleFolder(false);
        }
    }

    @Override
    public boolean isFolderWobbling() {
        return isFolderWobbling || super.isFolderWobbling();
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mLauncher.isInState(mLastStateBeforeOpen)) {
            if (mLauncher.getDragController().isDragging()) {
                mNeedResetState = false;
            }

            if (mNeedResetState) {
                mLauncher.getStateManager().goToState(mLastStateBeforeOpen, false);
            }

            if (!mLauncher.isInState(NORMAL) && mNeedResetState) {
                animate = false;
            }

        } else if (mNeedResetState) {
            animate = false;
        }
        mNeedResetState = false;

        if (isFolderWobbling()) {
            wobbleFolder(false);
        }

        super.handleClose(animate);
    }

    @Override
    public void getHitRectRelativeToDragLayer(android.graphics.Rect outRect) {
        if (mGridFolderPage != null && mGridFolderPage.getWidth() > 0 && mActivityContext != null
                && mActivityContext.getDragLayer() != null) {
            mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(mGridFolderPage, outRect);
            if (mFolderTab != null && mFolderTab.getVisibility() == VISIBLE) {
                android.graphics.Rect tabRect = new android.graphics.Rect();
                mActivityContext.getDragLayer().getDescendantRectRelativeToSelf(mFolderTab, tabRect);
                outRect.union(tabRect);
            }
        } else {
            super.getHitRectRelativeToDragLayer(outRect);
        }
    }

    @Override
    protected void onFolderCloseComplete() {
        showOrHideDesktop(mLauncher, false);
        Workspace<?> ws = mLauncher.getWorkspace();
        boolean onAppLibOrPinned = ws != null && (ws.isAppLibraryCurrentPage()
                || (ws.getFirstPagePinnedItem() != null && ws.getNextPage() == 0));
        if (!onAppLibOrPinned && Folder.getOpen(mLauncher) == null) {
            if (mLauncher.mBlurLayer != null) {
                mLauncher.mBlurLayer.setAlpha(0f);
            }
            View dockGlass = mLauncher.findViewById(R.id.dock_glass);
            if (dockGlass != null) {
                dockGlass.setVisibility(VISIBLE);
                dockGlass.setAlpha(1f);
            }
        }
        android.util.Log.d("GRID_FOLDER_TRACE", "onFolderCloseComplete restored desktop workspaceAlpha="
                + (mLauncher.getWorkspace() != null ? mLauncher.getWorkspace().getAlpha() : "null"));
    }

    @Override
    public boolean startDrag(View v, DragOptions options) {
        if (!isFolderWobbling && mLauncher.getWorkspace() != null
                && mLauncher.getWorkspace().isWobbling()) {
            wobbleFolder(true);
        }
        Object tag = v.getTag();
        if (tag instanceof WorkspaceItemInfo) {
            v.clearAnimation();
        }
        return super.startDrag(v, options);
    }

    @Override
    public void onDragEnd() {
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null && ws.isWobbling()) {
            wobbleFolder(true);
            ws.resetWobbleAlarm();
            wobbleExpireAlarm.setAlarm(Workspace.WOBBLE_EXPIRATION_TIMEOUT);
        } else if (isFolderWobbling) {
            wobbleFolder(false);
        }
        super.onDragEnd();
    }

    private AnimatorSet mDesktopVisibilityAnimator;

    private void showOrHideDesktop(Launcher launcher, boolean hide) {
        if (mDesktopVisibilityAnimator != null) {
            mDesktopVisibilityAnimator.cancel();
            mDesktopVisibilityAnimator = null;
        }

        boolean isSingleLayer = foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode();

        Workspace<?> workspace = launcher.getWorkspace();
        Hotseat hotseat = launcher.getHotseat();

        boolean isAppLib = workspace != null && workspace.isAppLibraryCurrentPage();

        if (!hide) {
            if (workspace != null) {
                workspace.setVisibility(VISIBLE);
                workspace.setAlpha(1f);
                for (int i = 0; i < workspace.getPageCount(); i++) {
                    View page = workspace.getChildAt(i);
                    if (page instanceof CellLayout) {
                        CellLayout cl = (CellLayout) page;
                        cl.setVisibility(VISIBLE);
                        cl.setAlpha(1f);
                        if (cl.getShortcutsAndWidgets() != null) {
                            cl.getShortcutsAndWidgets().setVisibility(VISIBLE);
                            cl.getShortcutsAndWidgets().setAlpha(1f);
                        }
                    }
                }
            }
            if (!isAppLib) {
                if (workspace != null && workspace.getPageIndicator() != null) {
                    workspace.getPageIndicator().setVisibility(VISIBLE);
                    workspace.getPageIndicator().setAlpha(1f);
                    if (workspace.getPageIndicator() instanceof PageIndicatorDots) {
                        ((PageIndicatorDots) workspace.getPageIndicator()).resetToIdle();
                    }
                    if (launcher.isInState(LauncherState.SPRING_LOADED)) {
                        workspace.showPageIndicatorAtCurrentScroll();
                    }
                }
                if (hotseat != null) {
                    hotseat.setAlpha(1f);
                    hotseat.setIconsAlpha(1f);
                    if (hotseat.getQsb() != null) {
                        hotseat.getQsb().setVisibility(VISIBLE);
                        hotseat.getQsb().setAlpha(1f);
                    }
                    hotseat.setVisibility(VISIBLE);
                }
                View dockGlass = launcher.findViewById(R.id.dock_glass);
                if (dockGlass != null) {
                    dockGlass.setVisibility(VISIBLE);
                    dockGlass.setAlpha(1f);
                }
            }
            if (!isSingleLayer && !isAppLib) {
                ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
                if (scrimView != null) {
                    scrimView.setAlpha(1f);
                }
                BlurBackgroundView blur = launcher.mBlurLayer;
                if (blur != null) {
                    blur.setAlpha(0f);
                }
            }
            return;
        }

        AnimatorSet set = new AnimatorSet();

        if (workspace != null) {
            // In SingleLayerMode, we want to keep the workspace visible so it can be
            // blurred
            // behind the folder. We do not fade the workspace itself.
            if (!isSingleLayer) {
                play(set, getAnimator(workspace, 1f, 0f, hide));
            }
            if (workspace.getPageIndicator() != null) {
                play(set, getAnimator(workspace.getPageIndicator(), 1f, 0f, hide));
            }
        }

        if (hotseat != null) {
            hotseat.setIconsAlpha(hide ? 0f : 1f);
            play(set, getAnimator(hotseat, 1f, 0f, hide));
            View qsb = hotseat.getQsb();
            if (qsb != null) {
                play(set, getAnimator(qsb, 1f, 0f, hide));
            }
        }
        View dockGlass = launcher.findViewById(R.id.dock_glass);
        if (dockGlass != null) {
            play(set, getAnimator(dockGlass, 1f, 0f, hide));
        }

        if (!isSingleLayer) {
            ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
            if (scrimView != null) {
                play(set, getAnimator(scrimView, 1f, 0f, hide));
            }

            BlurBackgroundView blur = launcher.mBlurLayer;
            if (blur != null) {
                play(set, getAnimator(blur, 0f, 1f, hide));
            }
        }
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDesktopVisibilityAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // force-apply the end state even if cancelled mid-flight
                if (workspace != null && !isSingleLayer) {
                    workspace.setAlpha(hide ? 0f : 1f);
                }
                if (workspace != null && workspace.getPageIndicator() != null) {
                    workspace.getPageIndicator().setAlpha(hide ? 0f : 1f);
                }
                if (hotseat != null) {
                    hotseat.setAlpha(hide ? 0f : 1f);
                    hotseat.setIconsAlpha(hide ? 0f : 1f);
                }
                if (dockGlass != null) {
                    dockGlass.setAlpha(hide ? 0f : 1f);
                }
                if (hotseat != null && hotseat.getQsb() != null) {
                    hotseat.getQsb().setAlpha(hide ? 0f : 1f);
                }
                if (!isSingleLayer) {
                    ScrimView scrimView = launcher.findViewById(R.id.scrim_view);
                    if (scrimView != null)
                        scrimView.setAlpha(hide ? 0f : 1f);
                    BlurBackgroundView blur = launcher.mBlurLayer;
                    if (blur != null)
                        blur.setAlpha(hide ? 1f : 0f);
                }
            }
        });
        mDesktopVisibilityAnimator = set;
        set.start();
    }

    @Override
    public void updateFolderOnAnimate(boolean isOpening) {
        mFolderTab.setVisibility(isOpening ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getUnusedOffsetYOnAnimate(boolean isOpening) {
        // Tab height correction is now applied directly in FolderAnimationManager
        // (not through this method which gets scaled by initialScale, making it too
        // small).
        return 0;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        return super.onApplyWindowInsets(windowInsets);
    }

    public void wobbleFolder(boolean wobble) {
        wobbleFolder(wobble, false);
    }

    public void wobbleFolder(boolean wobble, boolean excludeDraggingView) {
        if (wobble) {
            isFolderWobbling = true;
            AtomicInteger index = new AtomicInteger();
            ItemInfo draggingViewInfo = mLauncher.getWorkspace().getDragObjectInfo();
            iterateOverItems((info, view) -> {
                view.setOnTouchListener(ItemLongClickListener.INSTANCE_WORKSPACE_WOBBLE);
                if (excludeDraggingView && draggingViewInfo != null) {
                    if (draggingViewInfo instanceof WorkspaceItemInfo && draggingViewInfo.equals(view.getTag())) {
                        return false;
                    }
                }
                index.getAndIncrement();
                if (index.get() % 2 == 0) {
                    view.startAnimation(mLauncher.getWorkspace().getWobbleAnimation());
                } else {
                    view.startAnimation(mLauncher.getWorkspace().getReverseWobbleAnimation());
                }
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(true);
                }
                return false;
            });
            wobbleExpireAlarm.setAlarm(Workspace.WOBBLE_EXPIRATION_TIMEOUT);
        } else {
            wobbleExpireAlarm.cancelAlarm();
            isFolderWobbling = false;
            iterateOverItems((info, view) -> {
                view.setOnTouchListener(null);
                view.clearAnimation();
                if (view instanceof BubbleTextView) {
                    ((BubbleTextView) view).applyUninstallIconState(false);
                }
                return false;
            });
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (alarm == wobbleExpireAlarm) {
            wobbleFolder(false);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends View & ClipPathView> T getAnimateObject() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            return (T) this;
        }
        return (T) mGridFolderPage;
    }

    private Animator getAnimator(View view, float v1, float v2, boolean hide) {
        return hide
                ? ObjectAnimator.ofFloat(view, View.ALPHA, v1, v2)
                : ObjectAnimator.ofFloat(view, View.ALPHA, v2, v1);
    }

    private void play(AnimatorSet as, Animator a) {
        a.setStartDelay(a.getStartDelay());
        a.setDuration(280);
        as.play(a);
    }
}
```

---

# src/com/android/launcher3/folder/FolderIcon.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/folder/FolderIcon.java`  
**Lines**: 1073 | **Bytes**: 41954

```java
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import foundation.e.bliss.glass.FolderGlassView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.FolderListener;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconLabelDotView;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import foundation.e.bliss.LauncherAppMonitor;
import foundation.e.bliss.folder.GridFolder;
import foundation.e.bliss.multimode.MultiModeController;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener, IconLabelDotView,
        DraggableView, Reorderable {

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    private CheckLongPressHelper mLongPressHelper;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;

    PreviewBackground mBackground = new PreviewBackground();
    private boolean mBackgroundIsVisible = true;

    /** Liquid glass background view. Non-null only on API 33+ when attached to a window. */
    @Nullable private FolderGlassView mGlassView;
    private boolean mGlassAttachAttempted = false;
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();

    FolderGridOrganizer mPreviewVerifier;
    private ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private List<WorkspaceItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private Alarm mOpenAlarm = new Alarm(Looper.getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    private float mScaleForReorderBounce = 1f;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
            folderIcon.invalidate();
        }
    };

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        if (MultiModeController.isSingleLayerMode()) {
            mPreviewLayoutRule = LauncherAppMonitor.getInstanceNoCreate()
                    .getGridFolderController().getGridFolderIconLayoutRule();
        } else{
            mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        }

        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
    }

    public static <T extends Context & ActivityContext> FolderIcon inflateFolderAndIcon(int resId,
            T activityContext, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = MultiModeController.isSingleLayerMode() ?
                GridFolder.fromXml(activityContext) : Folder.fromXml(activityContext);

        FolderIcon icon = inflateIcon(resId, activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        return icon;
    }

    /**
     * Builds a FolderIcon to be added to the Launcher
     */
    public static FolderIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        FolderIcon icon = (FolderIcon) inflater.inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        if (icon.mFolderName.shouldShowLabel()) {
            icon.mFolderName.setText(folderInfo.title);
        }
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();

        if (MultiModeController.isSingleLayerMode()) {
            Paint.FontMetrics fm = icon.mFolderName.getPaint().getFontMetrics();
            int cellHeightPx = icon.mFolderName.getIconSize() + icon.mFolderName.getCompoundDrawablePadding() +
                    (int) Math.ceil(fm.bottom - fm.top);

            lp.topMargin = grid.iconSizePx + Math.min(((grid.getCellSize().y - cellHeightPx) / 2), 0);
            icon.mFolderName.setIncludeFontPadding(false);
        } else {
            lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;
        }

        icon.setTag(folderInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = folderInfo;
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));

        // Keep the notification dot up to date with the sum of all the content's dots.
        FolderDotInfo folderDotInfo = new FolderDotInfo();
        for (WorkspaceItemInfo si : folderInfo.contents) {
            folderDotInfo.addDotInfo(activity.getDotInfoForItem(si));
        }
        icon.setDotInfo(folderDotInfo);

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = new FolderGridOrganizer(activity.getDeviceProfile());
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);

        folderInfo.addListener(icon);

        return icon;
    }

    public void showFolderOptionsPopup() {
        com.android.launcher3.popup.PopupContainerWithArrow.showForFolder(this);
    }

    public com.android.launcher3.dragndrop.DragOptions.PreDragCondition startLongPressAction() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null && launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
                return null;
            }
        }
        com.android.launcher3.popup.PopupContainerWithArrow popup =
                com.android.launcher3.popup.PopupContainerWithArrow.showForFolder(this);
        return popup != null ? popup.createPreDragCondition(true) : null;
    }

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public BubbleTextView getFolderName() {
        return mFolderName;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public Folder getFolder() {
        return mFolder;
    }

    @Nullable
    public FolderGlassView getGlassView() {
        return mGlassView;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) &&
                item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void addItem(WorkspaceItemInfo item) {
        mInfo.add(item, true);
    }

    public void removeItem(WorkspaceItemInfo item, boolean animate) {
        mInfo.remove(item, animate);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof WorkspaceItemFactory)
                        || (dragInfo instanceof WorkspaceItemInfo)
                        || (dragInfo instanceof PendingAddShortcutInfo))) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final WorkspaceItemInfo destInfo, final View destView,
            final WorkspaceItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        final DragView srcView = d.dragView;
        prepareCreateAnimation(destView);
        addItem(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        FolderPreviewItemAnim anim = mPreviewItemManager.createFirstItemAnimation(
                true /* reverse */, onCompleteRunnable);
        if (anim != null) {
            anim.start();
        } else if (onCompleteRunnable != null) {
            onCompleteRunnable.run();
        }
    }

    public void onDragExit() {
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final WorkspaceItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform();
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform();
            }

            int numItemsInPreview = Math.min(mPreviewLayoutRule.getMaxNumItemsInPreview(), index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= mPreviewLayoutRule.getMaxNumItemsInPreview()) {
                List<WorkspaceItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                mInfo.add(item, index, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    int newIndex = mCurrentPreviewItems.indexOf(item);
                    if (newIndex >= 0) {
                        // If the item dropped is going to be in the preview, we update the
                        // index here to reflect its position in the preview.
                        index = newIndex;
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    removeItem(item, false);
                }
            }

            if (!itemAdded) {
                mInfo.add(item, index, true);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < mPreviewLayoutRule.getMaxNumItemsInPreview() ? 1f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;

            // Account for potentially different icon sizes with non-default grid settings
            if (d.dragSource instanceof ActivityAllAppsContainerView) {
                DeviceProfile grid = mActivity.getDeviceProfile();
                float containerScale = (1f * grid.iconSizePx / grid.allAppsIconSizePx);
                finalScale *= containerScale;
            }

            final int finalIndex = index;
            dragLayer.animateView(animateView, to, finalAlpha,
                    finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DECELERATE_2,
                    () -> {
                        mPreviewItemManager.hidePreviewItem(finalIndex, false);
                        mFolder.showItem(item);
                        mBackground.animateToRest();
                    },
                    DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);

            FolderNameInfos nameInfos = new FolderNameInfos();
            Executors.MODEL_EXECUTOR.post(() -> {
                d.folderNameProvider.getSuggestedFolderName(
                        getContext(), mInfo.contents, nameInfos);
                postDelayed(() -> {
                    setLabelSuggestion(nameInfos, d.logInstanceId);
                    invalidate();
                }, DROP_IN_ANIMATION_DURATION);
            });
        } else {
            addItem(item);
        }
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
        if (!mInfo.getLabelState().equals(LabelState.UNLABELED)) {
            return;
        }
        if (nameInfos == null || !nameInfos.hasSuggestions()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS);
            return;
        }
        if (!nameInfos.hasPrimary()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY);
            return;
        }
        CharSequence newTitle = nameInfos.getLabels()[0];
        FromState fromState = mInfo.getFromLabelState();

        mInfo.setTitle(newTitle, mFolder.mLauncherDelegate.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.mFolderName.setText(mInfo.title);

        // Logging for folder creation flow
        StatsLogManager.newInstance(getContext()).logger()
                .withInstanceId(instanceId)
                .withItemInfo(mInfo)
                .withFromState(fromState)
                .withToState(ToState.TO_SUGGESTION0)
                // When LAUNCHER_FOLDER_LABEL_UPDATED event.edit_text does not have delimiter,
                // event is assumed to be folder creation on the server side.
                .withEditText(newTitle.toString())
                .log(LAUNCHER_FOLDER_AUTO_LABELED);
    }


    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        WorkspaceItemInfo item;
        if (d.dragInfo instanceof WorkspaceItemFactory) {
            // Came from all apps -- make a copy
            item = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(getContext());
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
        } else {
            item = (WorkspaceItemInfo) d.dragInfo;
        }
        if (item != null && item.getTargetComponent() != null) {
            com.android.launcher3.util.RemovedAppStore.unmarkRemoved(
                    getContext(), item.getTargetComponent().flattenToString());
        }
        mFolder.notifyDrop();
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.contents.size(),
                itemReturnedOnFailedDrop
        );
    }

    public void setDotInfo(FolderDotInfo dotInfo) {
        updateDotScale(mDotInfo.hasDot(), dotInfo.hasDot());
        mDotInfo = dotInfo;
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    /**
     * Sets mDotScale to 1 or 0, animating if wasDotted or isDotted is false
     * (the dot is being added or removed).
     */
    private void updateDotScale(boolean wasDotted, boolean isDotted) {
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((wasDotted ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void setDotScale(float dotScale) {
        cancelDotScaleAnim();
        mDotScale = dotScale;
        invalidate();
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(mPreviewLayoutRule.getMaxNumItemsInPreview(), index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.basePreviewOffsetX;
        mTmpParams.transY += mBackground.basePreviewOffsetY;

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    @Override
    public void setIconVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        if (mGlassView != null) {
            mGlassView.setAlpha(visible ? 1f : 0f);
        }
        invalidate();
    }

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mGlassView) {
            if (mBackgroundIsVisible && !mBackground.drawingDelegated()) {
                mBackground.drawShadow(canvas);
            }
            int saveCount = canvas.save();
            DeviceProfile grid = mActivity.getDeviceProfile();
            float centerX = (mBackground.previewSize > 0 ? mBackground.basePreviewOffsetX : (getWidth() - child.getWidth()) / 2f) + child.getWidth() / 2f;
            float centerY = (mBackground.previewSize > 0 ? mBackground.basePreviewOffsetY : getPaddingTop() + (grid != null ? grid.folderIconOffsetYPx : 0)) + child.getHeight() / 2f;
            canvas.scale(mBackground.mScale, mBackground.mScale, centerX, centerY);
            
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // super.dispatchDraw draws children — including the FolderGlassView if present.
        // It will call our drawChild override for mGlassView, handling scale and shadow.
        super.dispatchDraw(canvas);

        if (!mBackgroundIsVisible) return;

        mPreviewItemManager.recomputePreviewDrawingParams();

        // Draw the fallback squircle background when the glass view is absent OR hidden.
        // When hidden (e.g. during drag-preview bitmap capture), we fall back to the
        // solid/blurred PreviewBackground so the squircle is visible in the drag shadow.
        boolean glassVisible = mGlassView != null && mGlassView.getVisibility() == VISIBLE;
        if (!mBackground.drawingDelegated()) {
            if (!glassVisible) {
                mBackground.drawBackground(canvas);
            }
        }

        if (mCurrentPreviewItems.isEmpty() && !mAnimating) return;

        mPreviewItemManager.draw(canvas);

        if (!mBackground.drawingDelegated()) {
            if (!glassVisible) {
                mBackground.drawBackgroundStroke(canvas);
            }
        }

        drawDot(canvas);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachGlassView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachGlassView();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mGlassView == null && !mGlassAttachAttempted) {
            attachGlassView();
        }
        updateGlassViewPosition();
    }

    /**
     * Finds the launcher's glass_source ViewGroup and adds a {@link FolderGlassView}
     * as the first child of this FrameLayout, positioned to exactly cover the preview
     * background area. Requires API 33+ (Android 13) to match the dock's glass behavior.
     */
    private void attachGlassView() {
        mGlassAttachAttempted = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (mGlassView != null) return; // already attached

        DeviceProfile grid = mActivity.getDeviceProfile();
        if (grid == null || !grid.isFloatingDockEnabled()) return; // Match dock toggle

        View root = getRootView();
        View glassSourceView = null;
        if (root != null) {
            glassSourceView = root.findViewById(com.android.launcher3.R.id.glass_wallpaper_proxy);
            if (glassSourceView == null) {
                glassSourceView = root.findViewById(com.android.launcher3.R.id.glass_source);
            }
        }

        mGlassView = new FolderGlassView(getContext());
        if (glassSourceView != null) {
            mGlassView.bind(glassSourceView);
        }
        mGlassView.setVisibility(VISIBLE);
        mGlassView.setAlpha(mBackgroundIsVisible ? 1f : 0f);

        int size = mBackground.previewSize > 0 ? mBackground.previewSize : grid.folderIconSizePx;
        int left = mBackground.previewSize > 0 ? mBackground.basePreviewOffsetX : (getWidth() - size) / 2;
        int top = mBackground.previewSize > 0 ? mBackground.basePreviewOffsetY : getPaddingTop() + grid.folderIconOffsetYPx;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = left;
        lp.topMargin = top;
        // Insert at index 0 so glass renders below preview items and dot
        addView(mGlassView, 0, lp);
        if (getWidth() > 0 && getHeight() > 0) {
            mGlassView.layout(left, top, left + size, top + size);
        }
    }

    /** Removes the glass view and cleans up the reference. */
    private void detachGlassView() {
        mGlassAttachAttempted = false;
        if (mGlassView != null) {
            mGlassView.unbind();
            mGlassView.setVisibility(GONE);
            if (mGlassView.getParent() == this) {
                removeView(mGlassView);
            }
            mGlassView = null;
        }
    }

    /**
     * Updates the glass view's LayoutParams to match the current preview background bounds.
     * Called when the FolderIcon is laid out again (e.g. after device rotation).
     */
    private void updateGlassViewPosition() {
        if (mGlassView == null) return;
        DeviceProfile grid = mActivity.getDeviceProfile();
        if (grid == null) return;

        int size = mBackground.previewSize > 0 ? mBackground.previewSize : grid.folderIconSizePx;
        if (size <= 0) return;
        int targetLeft = mBackground.previewSize > 0 ? mBackground.basePreviewOffsetX : (getWidth() - size) / 2;
        int targetTop = mBackground.previewSize > 0 ? mBackground.basePreviewOffsetY : getPaddingTop() + grid.folderIconOffsetYPx;

        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mGlassView.getLayoutParams();
        if (lp != null) {
            lp.width = size;
            lp.height = size;
            lp.leftMargin = targetLeft;
            lp.topMargin = targetTop;
        }
        mGlassView.layout(targetLeft, targetTop, targetLeft + size, targetTop + size);
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;
            // FolderIcon draws the icon to be top-aligned (with padding) & horizontally-centered
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            iconBounds.left = (getWidth() - iconSize) / 2;
            iconBounds.right = iconBounds.left + iconSize;
            iconBounds.top = getPaddingTop();
            iconBounds.bottom = iconBounds.top + iconSize;

            float iconScale = (float) mBackground.previewSize / iconSize;
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            mDotParams.dotColor = getContext().getResources()
                    .getColor(R.color.notification_dot_bg, getContext().getTheme());
            mDotParams.leftAlign = false;
            mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            mDotRenderer.draw(canvas, mDotParams, mDotInfo == null ? -1 : mDotInfo.getNotificationCount());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MultiModeController.isSingleLayerMode()) {
            boolean shouldCenterIcon = mActivity.getDeviceProfile().iconCenterVertically;
            if (shouldCenterIcon) {
                int iconSize = mActivity.getDeviceProfile().iconSizePx;
                mFolderName.getPaint().getFontMetrics(mFontMetrics);
                int cellHeightPx = iconSize + mFolderName.getCompoundDrawablePadding()
                        + (int) Math.ceil(mFontMetrics.bottom - mFontMetrics.top);
                setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                        - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Sets the visibility of the icon's title text */
    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    /**
     * Returns the list of items which should be visible in the preview
     */
    public List<WorkspaceItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.contents);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    @Override
    public void onItemsChanged(boolean animate) {
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<WorkspaceItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    @Override
    public void onAdd(WorkspaceItemInfo item, int rank) {
        DragTrace.log("FolderIcon.onAdd item=" + (item != null ? item.title : "null") + " rank=" + rank);
        updatePreviewItems(false);
        boolean wasDotted = mDotInfo.hasDot();
        mDotInfo.addDotInfo(mActivity.getDotInfoForItem(item));
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
        setContentDescription(getAccessiblityTitle(mInfo.title));
        invalidate();
        requestLayout();
    }

    @Override
    public void onRemove(List<WorkspaceItemInfo> items) {
        DragTrace.log("FolderIcon.onRemove itemsCount=" + (items != null ? items.size() : 0));
        updatePreviewItems(false);
        boolean wasDotted = mDotInfo.hasDot();
        items.stream().map(mActivity::getDotInfoForItem).forEach(mDotInfo::subtractDotInfo);
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
        setContentDescription(getAccessiblityTitle(mInfo.title));
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        DragTrace.log("FolderIcon.onTitleChanged title=" + title);
        mFolderName.setText(title);
        setContentDescription(getAccessiblityTitle(title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }

        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        super.onTouchEvent(event);
        mLongPressHelper.onTouchEvent(event);
        // Keep receiving the rest of the events
        return true;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        mTouchArea.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    public void removeListeners() {
        mInfo.removeListener(this);
        mInfo.removeListener(mFolder);
    }

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    @NonNull
    public SafeCloseable prepareDrawDragView() {
        // The FolderGlassView renders the squircle via live-wallpaper blur, which cannot
        // be sampled into an offscreen bitmap. Hide it before the drag-preview bitmap is
        // captured so that dispatchDraw falls back to the solid PreviewBackground and the
        // squircle is visible in the drag shadow, then restore it afterwards.
        if (mGlassView != null && mGlassView.getVisibility() == VISIBLE) {
            mGlassView.setVisibility(GONE);
            return () -> {
                if (mGlassView != null) {
                    mGlassView.setVisibility(VISIBLE);
                }
            };
        }
        return () -> {};
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getPreviewBounds(bounds);
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        int size = mInfo.contents.size();
        if (size < mPreviewLayoutRule.getMaxNumItemsInPreview()) {
            return getContext().getString(R.string.folder_name_format_exact, title, size);
        } else {
            return getContext().getString(R.string.folder_name_format_overflow, title,
                    mPreviewLayoutRule.getMaxNumItemsInPreview());
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        if (enableCursorHoverStates()) {
            mBackground.setHovered(hovered);
        }
    }

    /**
     * Interface that provides callbacks to a parent ViewGroup that hosts this FolderIcon.
     */
    public interface FolderIconParent {
        /**
         * Tells the FolderIconParent to draw a "leave-behind" when the Folder is open and leaving a
         * gap where the FolderIcon would be when the Folder is closed.
         */
        void drawFolderLeaveBehindForIcon(FolderIcon child);
        /**
         * Tells the FolderIconParent to stop drawing the "leave-behind" as the Folder is closed.
         */
        void clearFolderLeaveBehind(FolderIcon child);
    }
}
```

---

# src/com/android/launcher3/folder/FolderPagedView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/folder/FolderPagedView.java`  
**Lines**: 825 | **Bytes**: 29987

```java
/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import foundation.e.bliss.folder.GridFolder;
import foundation.e.bliss.multimode.MultiModeController;

public class FolderPagedView extends PagedView<PageIndicatorDots> implements ClipPathView {

    private static final String TAG = "FolderPagedView";

    private static final int REORDER_ANIMATION_DURATION = 230;
    private static final int START_VIEW_REORDER_DELAY = 30;
    private static final float VIEW_REORDER_DELAY_FACTOR = 0.9f;

    /**
     * Fraction of the width to scroll when showing the next page hint.
     */
    private static final float SCROLL_HINT_FRACTION = 0.07f;

    private static final int[] sTmpArray = new int[2];

    public final boolean mIsRtl;

    private final ViewGroupFocusHelper mFocusIndicatorHelper;

    @Thunk final ArrayMap<View, Runnable> mPendingAnimations = new ArrayMap<>();

    private final FolderGridOrganizer mOrganizer;
    private final ViewCache mViewCache;

    private int mAllocatedContentSize;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountX;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mGridCountY;

    private Folder mFolder;

    private Path mClipPath;

    // If the views are attached to the folder or not. A folder should be bound when its
    // animating or is open.
    private boolean mViewsBound = false;

    public FolderPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ActivityContext activityContext = ActivityContext.lookupContext(context);
        DeviceProfile profile = activityContext.getDeviceProfile();
        mOrganizer = new FolderGridOrganizer(profile);

        mIsRtl = Utilities.isRtl(getResources());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        mViewCache = activityContext.getViewCache();
    }

    public void setFolder(Folder folder) {
        mFolder = folder;
        mPageIndicator = folder.findViewById(R.id.folder_page_indicator);
        initParentViews(folder);
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    private void setupContentDimensions(int count) {
        mAllocatedContentSize = count;
        mOrganizer.setContentSize(count);
        mGridCountX = mOrganizer.getCountX();
        mGridCountY = mOrganizer.getCountY();

        // Update grid size
        for (int i = getPageCount() - 1; i >= 0; i--) {
            getPageAt(i).setGridSize(mGridCountX, mGridCountY);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mClipPath != null) {
            int count = canvas.save();
            canvas.clipPath(mClipPath);
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(count);
        } else {
            mFocusIndicatorHelper.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    /**
     * Binds items to the layout.
     */
    public void bindItems(List<WorkspaceItemInfo> items) {
        if (mViewsBound) {
            unbindItems();
        }
        arrangeChildren(items.stream().map(this::createNewView).collect(Collectors.toList()));
        mViewsBound = true;
    }

    /**
     * Removes all the icons from the folder
     */
    public void unbindItems() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            CellLayout page = (CellLayout) getChildAt(i);
            ShortcutAndWidgetContainer container = page.getShortcutsAndWidgets();
            for (int j = container.getChildCount() - 1; j >= 0; j--) {
                View child = container.getChildAt(j);
                child.setVisibility(View.VISIBLE);
                if (child instanceof BubbleTextView) {
                    ((BubbleTextView) child).reset();
                }
                mViewCache.recycleView(R.layout.folder_application, child);
            }
            page.removeAllViews();
            mViewCache.recycleView(R.layout.folder_page, page);
        }
        removeAllViews();
        mViewsBound = false;
    }

    /**
     * Returns true if the icons are bound to the folder
     */
    public boolean areViewsBound() {
        return mViewsBound;
    }

    /**
     * Creates and adds an icon corresponding to the provided rank
     * @return the created icon
     */
    public View createAndAddViewForRank(WorkspaceItemInfo item, int rank) {
        View icon = createNewView(item);
        if (!mViewsBound) {
            return icon;
        }
        ArrayList<View> views = new ArrayList<>(mFolder.getIconsInReadingOrder());
        views.add(rank, icon);
        arrangeChildren(views);
        return icon;
    }

    /**
     * Adds the {@param view} to the layout based on {@param rank} and updated the position
     * related attributes. It assumes that {@param item} is already attached to the view.
     */
    public void addViewForRank(View view, WorkspaceItemInfo item, int rank) {
        int pageNo = rank / mOrganizer.getMaxItemsPerPage();

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) view.getLayoutParams();
        lp.setCellXY(mOrganizer.getPosForRank(rank));
        if (item != null) {
            item.rank = rank;
        }
        getPageAt(pageNo).addViewToCellLayout(view, -1, item.getViewId(), lp, true);
    }

    @SuppressLint("InflateParams")
    public View createNewView(WorkspaceItemInfo item) {
        if (item == null) {
            return null;
        }
        final BubbleTextView textView = mViewCache.getView(
                R.layout.folder_application, getContext(), null);
        textView.applyFromWorkspaceItem(item);
        textView.setOnClickListener(mFolder.mActivityContext.getItemOnClickListener());
        textView.setOnLongClickListener(mFolder);
        textView.setOnFocusChangeListener(mFocusIndicatorHelper);

        if (MultiModeController.isSingleLayerMode()) {
            int textColor = Themes.getAttrColor(getContext(), R.attr.workspaceTextColor);
            textView.setTextColor(textColor);
        }

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) textView.getLayoutParams();
        if (lp == null) {
            textView.setLayoutParams(new CellLayoutLayoutParams(
                    item.cellX, item.cellY, item.spanX, item.spanY));
        } else {
            lp.setCellX(item.cellX);
            lp.setCellY(item.cellY);
            lp.cellHSpan = lp.cellVSpan = 1;
        }
        return textView;
    }

    @Nullable
    @Override
    public CellLayout getPageAt(int index) {
        return (CellLayout) getChildAt(index);
    }

    @Nullable
    public CellLayout getCurrentCellLayout() {
        return getPageAt(getNextPage());
    }

    public CellLayout createAndAddNewPage() {
        DeviceProfile grid = mFolder.mActivityContext.getDeviceProfile();
        CellLayout page = mViewCache.getView(R.layout.folder_page, getContext(), this);
        page.setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx);
        page.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        page.setInvertIfRtl(true);
        page.setGridSize(mGridCountX, mGridCountY);

        if (getPageCount() > 0) {
            CellLayout firstPage = getPageAt(0);
            if (firstPage != null && firstPage.getDesiredWidth() > 0) {
                page.setFixedSize(firstPage.getDesiredWidth(), firstPage.getDesiredHeight());
            }
        }

        addView(page, -1, generateDefaultLayoutParams());
        setEnableOverscroll(getPageCount() > 1);
        if (mPageIndicator != null) {
            mPageIndicator.setVisibility(View.VISIBLE);
        }
        return page;
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        return getPaddingLeft() + getPaddingRight();
    }

    public void setFixedSize(int width, int height) {
        width -= (getPaddingLeft() + getPaddingRight());
        height -= (getPaddingTop() + getPaddingBottom());
        for (int i = getChildCount() - 1; i >= 0; i --) {
            ((CellLayout) getChildAt(i)).setFixedSize(width, height);
        }
    }

    public void removeItem(View v) {
        if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()
                && v != null) {
            v.clearAnimation();
        }

        for (int i = getChildCount() - 1; i >= 0; i --) {
            getPageAt(i).removeView(v);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mMaxScroll > 0) mPageIndicator.setScroll(l, mMaxScroll);
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param list the ordered list of children.
     */
    @SuppressLint("RtlHardcoded")
    public void arrangeChildren(List<View> list) {
        int itemCount = list.size();
        boolean wobbling = false;
        if (mFolder instanceof GridFolder) {
            wobbling = ((GridFolder) mFolder).isFolderWobbling();
        }
        ArrayList<CellLayout> pages = new ArrayList<>();

        if (wobbling) {
            ((GridFolder) mFolder).wobbleFolder(false);
        }
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout page = (CellLayout) getChildAt(i);
            page.removeAllViews();
            pages.add(page);
        }
        mOrganizer.setFolderInfo(mFolder.getInfo());
        setupContentDimensions(itemCount);

        Iterator<CellLayout> pageItr = pages.iterator();
        CellLayout currentPage = null;

        int position = 0;
        int rank = 0;

        for (int i = 0; i < itemCount; i++) {
            View v = list.size() > i ? list.get(i) : null;
            if (currentPage == null || position >= mOrganizer.getMaxItemsPerPage()) {
                // Next page
                if (pageItr.hasNext()) {
                    currentPage = pageItr.next();
                } else {
                    currentPage = createAndAddNewPage();
                }
                position = 0;
            }

            if (v != null) {
                CellLayoutLayoutParams lp = (CellLayoutLayoutParams) v.getLayoutParams();
                ItemInfo info = (ItemInfo) v.getTag();
                lp.setCellXY(mOrganizer.getPosForRank(rank));
                if (info instanceof WorkspaceItemInfo) {
                    ((WorkspaceItemInfo) info).rank = rank;
                }
                currentPage.addViewToCellLayout(v, -1, info.getViewId(), lp, true);

                if (mOrganizer.isItemInPreview(rank) && v instanceof BubbleTextView) {
                    ((BubbleTextView) v).verifyHighRes();
                }
            }

            rank++;
            position++;
        }

        // Remove extra views.
        boolean removed = false;
        while (pageItr.hasNext()) {
            removeView(pageItr.next());
            removed = true;
        }
        if (removed) {
            setCurrentPage(0);
        }

        setEnableOverscroll(getPageCount() > 1);

        // Update footer - always show folder page indicator
        if (mPageIndicator != null) {
            mPageIndicator.setVisibility(View.VISIBLE);
        }

        if (mFolder != null && mFolder.mFolderName != null) {
            mFolder.mFolderName.setGravity((mIsRtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            mFolder.updateFolderNamePadding();
        }

        if (wobbling) {
            ((GridFolder) mFolder).wobbleFolder(true, true);
        }
    }

    public int getDesiredWidth() {
        return getPageCount() > 0 ?
                (getPageAt(0).getDesiredWidth() + getPaddingLeft() + getPaddingRight()) : 0;
    }

    public int getDesiredHeight()  {
        return getPageCount() > 0 ?
                (getPageAt(0).getDesiredHeight() + getPaddingTop() + getPaddingBottom()) : 0;
    }

    /**
     * @return the rank of the cell nearest to the provided pixel position.
     */
    public int findNearestArea(int pixelX, int pixelY) {
        int pageIndex = getNextPage();
        CellLayout page = getPageAt(pageIndex);
        if (page == null) {
            return 0;
        }
        page.findNearestAreaIgnoreOccupied(pixelX, pixelY, 1, 1, sTmpArray);
        if (mFolder.isLayoutRtl()) {
            sTmpArray[0] = page.getCountX() - sTmpArray[0] - 1;
        }
        int maxItems = mOrganizer.getMaxItemsPerPage();
        int pageMin = pageIndex * maxItems;
        int targetRank = pageMin + sTmpArray[1] * mGridCountX + sTmpArray[0];

        int childCountOnPage = page.getShortcutsAndWidgets() != null
                ? page.getShortcutsAndWidgets().getChildCount() : 0;
        int maxRankOnPage = pageMin + Math.min(childCountOnPage, maxItems - 1);

        return Math.max(pageMin, Math.min(maxRankOnPage, targetRank));
    }

    public View getFirstItem() {
        return getViewInCurrentPage(c -> 0);
    }

    public View getLastItem() {
        return getViewInCurrentPage(c -> c.getChildCount() - 1);
    }

    private View getViewInCurrentPage(ToIntFunction<ShortcutAndWidgetContainer> rankProvider) {
        if (getChildCount() < 1 || getCurrentCellLayout() == null) {
            return null;
        }
        ShortcutAndWidgetContainer container = getCurrentCellLayout().getShortcutsAndWidgets();
        int rank = rankProvider.applyAsInt(container);
        if (mGridCountX > 0) {
            return container.getChildAt(rank % mGridCountX, rank / mGridCountX);
        } else {
            return container.getChildAt(rank);
        }
    }

    /**
     * Iterates over all its items in a reading order.
     * @return the view for which the operator returned true.
     */
    public View iterateOverItems(ItemOperator op) {
        for (int k = 0 ; k < getChildCount(); k++) {
            CellLayout page = getPageAt(k);
            for (int j = 0; j < page.getCountY(); j++) {
                for (int i = 0; i < page.getCountX(); i++) {
                    View v = page.getChildAt(i, j);
                    if ((v != null) && op.evaluate((ItemInfo) v.getTag(), v)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    public String getAccessibilityDescription() {
        return getContext().getString(R.string.folder_opened, mGridCountX, mGridCountY);
    }

    /**
     * Sets the focus on the first visible child.
     */
    public void setFocusOnFirstChild() {
        CellLayout currentCellLayout = getCurrentCellLayout();
        if (currentCellLayout == null) {
            return;
        }
        View firstChild = currentCellLayout.getChildAt(0, 0);
        if (firstChild == null) {
            return;
        }
        firstChild.requestFocus();
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (mFolder != null) {
            mFolder.updateTextViewFocus();
        }
    }

    /**
     * Scrolls the current view by a fraction
     */
    public void showScrollHint(int direction) {
        float fraction = (direction == Folder.SCROLL_LEFT) ^ mIsRtl
                ? -SCROLL_HINT_FRACTION : SCROLL_HINT_FRACTION;
        int hint = (int) (fraction * getWidth());
        int scroll = getScrollForPage(getNextPage()) + hint;
        int delta = scroll - getScrollX();
        if (delta != 0) {
            mScroller.startScroll(getScrollX(), 0, delta, 0, Folder.SCROLL_HINT_DURATION);
            invalidate();
        }
    }

    public void clearScrollHint() {
        if (getScrollX() != getScrollForPage(getNextPage())) {
            snapToPage(getNextPage());
        }
    }

    /**
     * Finish animation all the views which are animating across pages
     */
    public void completePendingPageChanges() {
        if (!mPendingAnimations.isEmpty()) {
            ArrayMap<View, Runnable> pendingViews = new ArrayMap<>(mPendingAnimations);
            for (Map.Entry<View, Runnable> e : pendingViews.entrySet()) {
                e.getKey().animate().cancel();
                e.getValue().run();
            }
        }
    }

    public boolean rankOnCurrentPage(int rank) {
        int p = rank / mOrganizer.getMaxItemsPerPage();
        return p == getNextPage();
    }

    @Override
    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        // Ensure that adjacent pages have high resolution icons
        verifyVisibleHighResIcons(getCurrentPage() - 1);
        verifyVisibleHighResIcons(getCurrentPage() + 1);
    }

    /**
     * Ensures that all the icons on the given page are of high-res
     */
    public void verifyVisibleHighResIcons(int pageNo) {
        CellLayout page = getPageAt(pageNo);
        if (page != null) {
            ShortcutAndWidgetContainer parent = page.getShortcutsAndWidgets();
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                BubbleTextView icon = ((BubbleTextView) parent.getChildAt(i));
                icon.verifyHighRes();
                // Set the callback back to the actual icon, in case
                // it was captured by the FolderIcon
                Drawable d = icon.getIcon();
                if (d != null) {
                    d.setCallback(icon);
                }
            }
        }
    }

    public int getAllocatedContentSize() {
        return mAllocatedContentSize;
    }

    /**
     * Reorders the items such that the {@param empty} spot moves to {@param target}
     */
    public void realTimeReorder(int empty, int target) {
        if (!mViewsBound) {
            return;
        }
        completePendingPageChanges();
        int delay = 0;
        float delayAmount = START_VIEW_REORDER_DELAY;

        // Animation only happens on the current page.
        int pageToAnimate = getNextPage();
        int maxItemsPerPage = mOrganizer.getMaxItemsPerPage();

        int pageT = target / maxItemsPerPage;
        int pagePosT = target % maxItemsPerPage;

        if (pageT != pageToAnimate) {
            Log.e(TAG, "Cannot animate when the target cell is invisible");
        }
        int pagePosE = empty % maxItemsPerPage;
        int pageE = empty / maxItemsPerPage;

        int startPos, endPos;
        int moveStart, moveEnd;
        int direction;

        if (target == empty) {
            // No animation
            return;
        }

        if (pageE == pageToAnimate && pageT == pageToAnimate) {
            int emptyRow = pagePosE / mGridCountX;
            int emptyCol = pagePosE % mGridCountX;
            int targetRow = pagePosT / mGridCountX;
            int targetCol = pagePosT % mGridCountX;

            CellLayout page = getPageAt(pageToAnimate);
            if (page == null) {
                return;
            }

            if (emptyRow == targetRow) {
                // Same row: shift items horizontally within this row only
                int row = emptyRow;
                if (emptyCol < targetCol) {
                    for (int col = emptyCol; col < targetCol; col++) {
                        int nextCol = col + 1;
                        View v = page.getChildAt(nextCol, row);
                        if (v != null) {
                            if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()) {
                                v.clearAnimation();
                            }
                            if (page.animateChildToPosition(v, col, row,
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                                delay += delayAmount;
                                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
                            }
                            if (v.getTag() instanceof WorkspaceItemInfo) {
                                ((WorkspaceItemInfo) v.getTag()).rank = pageToAnimate * maxItemsPerPage + row * mGridCountX + col;
                            }
                        }
                    }
                } else if (emptyCol > targetCol) {
                    for (int col = emptyCol; col > targetCol; col--) {
                        int prevCol = col - 1;
                        View v = page.getChildAt(prevCol, row);
                        if (v != null) {
                            if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()) {
                                v.clearAnimation();
                            }
                            if (page.animateChildToPosition(v, col, row,
                                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                                delay += delayAmount;
                                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
                            }
                            if (v.getTag() instanceof WorkspaceItemInfo) {
                                ((WorkspaceItemInfo) v.getTag()).rank = pageToAnimate * maxItemsPerPage + row * mGridCountX + col;
                            }
                        }
                    }
                }
            } else {
                // Different rows: 2D direct swap with the target item (no domino ripple across other rows)
                View targetView = page.getChildAt(targetCol, targetRow);
                if (targetView != null) {
                    if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()) {
                        targetView.clearAnimation();
                    }
                    page.animateChildToPosition(targetView, emptyCol, emptyRow,
                            REORDER_ANIMATION_DURATION, 0, true, true);
                    if (targetView.getTag() instanceof WorkspaceItemInfo) {
                        ((WorkspaceItemInfo) targetView.getTag()).rank = empty;
                    }
                }
            }

            if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()) {
                ((GridFolder) mFolder).wobbleFolder(true);
            }
            return;
        }

        if (target > empty) {
            // Items will move backwards to make room for the empty cell.
            direction = 1;

            // If empty cell is in a different page, move them instantly.
            if (pageE < pageToAnimate) {
                moveStart = empty;
                // Instantly move the first item in the current page.
                moveEnd = pageToAnimate * maxItemsPerPage;
                // Animate the 2nd item in the current page, as the first item was already moved to
                // the last page.
                startPos = 0;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        } else {
            // The items will move forward.
            direction = -1;

            if (pageE > pageToAnimate) {
                // Move the items immediately.
                moveStart = empty;
                // Instantly move the last item in the current page.
                moveEnd = (pageToAnimate + 1) * maxItemsPerPage - 1;

                // Animations start with the second last item in the page
                startPos = maxItemsPerPage - 1;
            } else {
                moveStart = moveEnd = -1;
                startPos = pagePosE;
            }

            endPos = pagePosT;
        }

        // Instant moving views.
        while (moveStart != moveEnd) {
            int rankToMove = moveStart + direction;
            int p = rankToMove / maxItemsPerPage;
            int pagePos = rankToMove % maxItemsPerPage;
            int x = pagePos % mGridCountX;
            int y = pagePos / mGridCountX;

            final CellLayout page = getPageAt(p);
            final View v = page.getChildAt(x, y);
            if (v != null) {
                v.clearAnimation();
                if (pageToAnimate != p) {
                    page.removeView(v);
                    addViewForRank(v, (WorkspaceItemInfo) v.getTag(), moveStart);
                } else {
                    // Do a fake animation before removing it.
                    final int newRank = moveStart;
                    final float oldTranslateX = v.getTranslationX();

                    Runnable endAction = new Runnable() {

                        @Override
                        public void run() {
                            mPendingAnimations.remove(v);
                            v.setTranslationX(oldTranslateX);
                            ((CellLayout) v.getParent().getParent()).removeView(v);
                            addViewForRank(v, (WorkspaceItemInfo) v.getTag(), newRank);
                        }
                    };
                    v.animate()
                        .translationXBy((direction > 0 ^ mIsRtl) ? -v.getWidth() : v.getWidth())
                        .setDuration(REORDER_ANIMATION_DURATION)
                        .setStartDelay(0)
                        .withEndAction(endAction);
                    mPendingAnimations.put(v, endAction);
                }
            }
            moveStart = rankToMove;
        }

        if ((endPos - startPos) * direction <= 0) {
            // No animation
            return;
        }

        CellLayout page = getPageAt(pageToAnimate);
        for (int i = startPos; i != endPos; i += direction) {
            int nextPos = i + direction;
            View v = page.getChildAt(nextPos % mGridCountX, nextPos / mGridCountX);
            if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling() && v != null) {
                v.clearAnimation();
            }
            if (page.animateChildToPosition(v, i % mGridCountX, i / mGridCountX,
                    REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount;
                delayAmount *= VIEW_REORDER_DELAY_FACTOR;
            }
        }

        if (mFolder instanceof GridFolder && ((GridFolder) mFolder).isFolderWobbling()) {
            ((GridFolder) mFolder).wobbleFolder(true);
        }
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return AbstractFloatingView.getTopOpenViewWithType(mFolder.mActivityContext,
                TYPE_ALL & ~TYPE_FOLDER) == null;
    }

    public int itemsPerPage() {
        return mOrganizer.getMaxItemsPerPage();
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    public void cleanupEmptyPages() {
        boolean removed = false;
        while (getPageCount() > 1) {
            CellLayout lastPage = getPageAt(getPageCount() - 1);
            if (lastPage.getShortcutsAndWidgets() == null
                    || lastPage.getShortcutsAndWidgets().getChildCount() == 0) {
                removeView(lastPage);
                removed = true;
            } else {
                break;
            }
        }
        if (removed) {
            setEnableOverscroll(getPageCount() > 1);
            if (mPageIndicator != null) {
                mPageIndicator.setVisibility(View.VISIBLE);
            }
            if (getNextPage() >= getPageCount()) {
                snapToPageImmediately(getPageCount() - 1);
            }
        }
    }
}
```

---

# src/com/android/launcher3/folder/LauncherDelegate.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/folder/LauncherDelegate.java`  
**Lines**: 220 | **Bytes**: 8277

```java
/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.folder;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_CONVERTED_TO_ICON;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Optional;
import java.util.function.Consumer;

import foundation.e.bliss.folder.GridFolder;

/**
 * Wrapper around Launcher methods to allow folders in non-launcher context
 */
public class LauncherDelegate {

    private final Launcher mLauncher;

    private LauncherDelegate(Launcher launcher) {
        mLauncher = launcher;
    }

    void init(Folder folder, FolderIcon icon) {
        folder.setDragController(mLauncher.getDragController());
        icon.setOnFocusChangeListener(mLauncher.getFocusHandler());
    }

    boolean isDraggingEnabled() {
        return mLauncher.isDraggingEnabled();
    }

    void beginDragShared(View child, DragSource source, DragOptions options) {
        mLauncher.getWorkspace().beginDragShared(child, source, options);
    }

    ModelWriter getModelWriter() {
        return mLauncher.getModelWriter();
    }

    void forEachVisibleWorkspacePage(Consumer<View> callback) {
        mLauncher.getWorkspace().forEachVisiblePage(callback);
    }

    @Nullable
    public Launcher getLauncher() {
        return mLauncher;
    }

    boolean replaceFolderWithFinalItem(Folder folder) {
        // Add the last remaining child to the workspace in place of the folder
        Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                int itemCount = folder.getItemCount();
                FolderInfo info = folder.mInfo;
                android.util.Log.d("ICON_TRACE", "replaceFolderWithFinalItem folder=" + info.id
                        + " itemCount=" + itemCount
                        + " container=" + info.container + " screenId=" + info.screenId);
                if (itemCount <= 1) {
                    View newIcon = null;
                    WorkspaceItemInfo finalItem = null;
                    CellPos presenterPos =
                            mLauncher.getCellPosMapper().mapModelToPresenter(info);
                    CellLayout cellLayout = mLauncher.getCellLayout(info.container,
                            presenterPos.screenId);

                    if (itemCount == 1 && !info.contents.isEmpty() && cellLayout != null) {
                        // Move the item from the folder to the workspace, in the position of the
                        // folder
                        finalItem = info.contents.remove(0);
                        newIcon = mLauncher.getItemInflater().inflateItem(
                                finalItem, mLauncher.getModelWriter(), cellLayout);
                        mLauncher.getModelWriter().addOrMoveItemInDatabase(finalItem,
                                info.container, presenterPos.screenId, presenterPos.cellX, presenterPos.cellY);
                    }

                    folder.mFolderIcon.clearAnimation();

                    if (newIcon != null && cellLayout != null) {
                        folder.mFolderIcon.removeListeners();
                        cellLayout.removeView(folder.mFolderIcon);
                        if (folder.mFolderIcon instanceof DropTarget) {
                            folder.mDragController.removeDropTarget((DropTarget) folder.mFolderIcon);
                        }
                        mLauncher.getModelWriter().deleteItemFromDatabase(info,
                                "folder removed because there's only 1 item in it");
                        mLauncher.getWorkspace().addInScreenFromBind(newIcon, finalItem);
                        newIcon.requestFocus();
                    } else if (itemCount <= 0) {
                        mLauncher.removeItem(folder.mFolderIcon, info, true /* deleteFromDb */,
                                "folder removed because it is empty");
                    }
                    if (finalItem != null) {
                        StatsLogger logger = mLauncher.getStatsLogManager().logger()
                                .withItemInfo(finalItem);
                        ((Optional<InstanceId>) folder.mDragController.getLogInstanceId())
                                .map(logger::withInstanceId)
                                .orElse(logger)
                                .log(LAUNCHER_FOLDER_CONVERTED_TO_ICON);
                    }
                }
            }
        };
        View finalChild = folder.mContent.getLastItem();
        if (finalChild != null) {
            folder.mFolderIcon.performDestroyAnimation(onCompleteRunnable);
        } else {
            onCompleteRunnable.run();
        }
        return true;
    }


    boolean interceptOutsideTouch(MotionEvent ev, BaseDragLayer dl, Folder folder) {
        if (mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            // Do not close the container if in drag and drop.
            if (!dl.isEventOverView(mLauncher.getDropTargetBar(), ev)) {
                return true;
            }
        } else {
            // TODO: add ww log if need to gather tap outside to close folder
            folder.close(true);
            return true;
        }
        return false;
    }

    private static class FallbackDelegate extends LauncherDelegate {

        private final ActivityContext mContext;
        private ModelWriter mWriter;

        FallbackDelegate(ActivityContext context) {
            super(null);
            mContext = context;
        }

        @Override
        void init(Folder folder, FolderIcon icon) {
            folder.setDragController(mContext.getDragController());
        }

        @Override
        boolean isDraggingEnabled() {
            return false;
        }

        @Override
        void beginDragShared(View child, DragSource source, DragOptions options) { }

        @Override
        ModelWriter getModelWriter() {
            if (mWriter == null) {
                mWriter = LauncherAppState.getInstance((Context) mContext).getModel().getWriter(
                        false, mContext.getCellPosMapper(), null);
            }
            return mWriter;
        }

        @Override
        void forEachVisibleWorkspacePage(Consumer<View> callback) { }

        @Override
        public Launcher getLauncher() {
            return null;
        }

        @Override
        boolean replaceFolderWithFinalItem(Folder folder) {
            return false;
        }

        @Override
        boolean interceptOutsideTouch(MotionEvent ev, BaseDragLayer dl, Folder folder) {
            folder.close(true);
            return true;
        }
    }

    static LauncherDelegate from(ActivityContext context) {
        return context instanceof Launcher
                ? new LauncherDelegate((Launcher) context)
                : new FallbackDelegate(context);
    }
}
```

---

# src/com/android/launcher3/Hotseat.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/Hotseat.java`  
**Lines**: 387 | **Bytes**: 14453

```java
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.launcher3.folder.Folder;

import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.views.ActivityContext;

import androidx.annotation.NonNull;

import foundation.e.bliss.blur.BlurViewDelegate;
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer;
import foundation.e.bliss.blur.OffsetParent;
import foundation.e.bliss.folder.GridFolder;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable, OffsetParent {

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;

    private final OffsetParent.OffsetParentDelegate offsetParentDelegate =
            new OffsetParent.OffsetParentDelegate();
    public final BlurViewDelegate mBlurDelegate;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;

    private final View mQsb;
    public boolean drawBlur;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);
        
        mBlurDelegate = new BlurViewDelegate(this, WallpaperBlurRenderer.blurConfigDock, attrs);
        drawBlur = true;
        setWillNotDraw(false);
        addView(mQsb);
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            float adjustedBorderSpace = dp.getHotseatAdjustedBorderSpaceForBubbleBar(getContext());
            if (hasBubbles && Float.compare(adjustedBorderSpace, 0f) != 0) {
                getShortcutsAndWidgets().setTranslationProvider(child -> {
                    int index = getShortcutsAndWidgets().indexOfChild(child);
                    float borderSpaceDelta = adjustedBorderSpace - dp.hotseatBorderSpace;
                    return dp.iconSizePx + index * borderSpaceDelta;
                });
                if (mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction = (float) dp.iconSizePx / dp.hotseatQsbWidth;
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.numShownHotseatIcons);
        } else {
            setGridSize(dp.numShownHotseatIcons, 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        float adjustedBorderSpace = dp.getHotseatAdjustedBorderSpaceForBubbleBar(getContext());
        if (Float.compare(adjustedBorderSpace, 0f) == 0) {
            return;
        }

        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        AnimatorSet animatorSet = new AnimatorSet();
        float borderSpaceDelta = adjustedBorderSpace - dp.hotseatBorderSpace;

        // update the translation provider for future layout passes of hotseat icons.
        if (isBubbleBarVisible) {
            icons.setTranslationProvider(child -> {
                int index = icons.indexOfChild(child);
                return dp.iconSizePx + index * borderSpaceDelta;
            });
        } else {
            icons.setTranslationProvider(null);
        }

        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            float tx = isBubbleBarVisible ? dp.iconSizePx + i * borderSpaceDelta : 0;
            if (child instanceof Reorderable) {
                MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                animatorSet.play(
                        mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
            } else {
                animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
            }
        }
        if (mQsb instanceof HorizontalInsettableView) {
            HorizontalInsettableView horizontalInsettableQsb = (HorizontalInsettableView) mQsb;
            ValueAnimator qsbAnimator = ValueAnimator.ofFloat(0f, 1f);
            qsbAnimator.addUpdateListener(animation -> {
                float fraction = qsbAnimator.getAnimatedFraction();
                float insetFraction = isBubbleBarVisible
                        ? (float) dp.iconSizePx * fraction / dp.hotseatQsbWidth
                        : (float) dp.iconSizePx * (1 - fraction) / dp.hotseatQsbWidth;
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else if (grid.isFloatingDockEnabled() && grid.hotseatGeometry != null) {
            mQsb.setVisibility(View.GONE);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.width = grid.hotseatGeometry.getBounds().width();
            lp.height = grid.hotseatGeometry.getBounds().height();
            lp.bottomMargin = insets.bottom + grid.hotseatBarBottomSpacePx;
        } else {
            mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx;
            lp.bottomMargin = 0;
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBlurDelegate != null && drawBlur) {
            mBlurDelegate.draw(canvas);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        DeviceProfile dp = mActivity.getDeviceProfile();
        mQsb.measure(MeasureSpec.makeMeasureSpec(dp.hotseatQsbWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp.hotseatQsbHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int left;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp.isQsbInline) {
            int qsbSpace = dp.hotseatBorderSpace;
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (r - l - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int bottom = b - t - dp.getQsbOffsetY();
        int top = bottom - dp.hotseatQsbHeight;
        mQsb.layout(left, top, right, bottom);
    }

    /**
     * Sets the alpha value of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha) {
        getShortcutsAndWidgets().setAlpha(alpha);
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha) {
        mQsb.setAlpha(alpha);
    }

    public float getIconsAlpha() {
        return getShortcutsAndWidgets().getAlpha();
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public void setForcedTranslationY(float translationY){
        super.setTranslationY(translationY);
        offsetParentDelegate.notifyOffsetChanged();
    }

    @Override
    public float getOffsetX() {
        return getTranslationX();
    }

    @Override
    public float getOffsetY() {
        return getTranslationY();
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        offsetParentDelegate.notifyOffsetChanged();
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        offsetParentDelegate.notifyOffsetChanged();
    }

    @Override
    public boolean getNeedWallpaperScroll() {
        return true;
    }

    @Override
    public void addOnOffsetChangeListener(@NonNull OnOffsetChangeListener listener) {
        offsetParentDelegate.addOnOffsetChangeListener(listener);
    }

    @Override
    public void removeOnOffsetChangeListener(@NonNull OnOffsetChangeListener listener) {
        offsetParentDelegate.removeOnOffsetChangeListener(listener);
    }

    @Override
    public void setAlpha(float alpha) {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            if (mWorkspace != null && mWorkspace.isAppLibraryCurrentPage() && !mWorkspace.isPageInTransition()) {
                super.setAlpha(0f);
                return;
            }
        }

        super.setAlpha(alpha);
    }
}
```

---

# src/com/android/launcher3/pageindicators/PageIndicatorDots.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/pageindicators/PageIndicatorDots.java`  
**Lines**: 1044 | **Bytes**: 39619

```java
/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.pageindicators;

import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import foundation.e.bliss.glass.SearchPillGlassView;
import foundation.e.bliss.multimode.MultiModeController;

/**
 * {@link PageIndicator} which shows dots per page. The active page is shown with the current
 * accent color.
 */
public class PageIndicatorDots extends View implements Insettable, PageIndicator {

    private static final float SHIFT_PER_ANIMATION = 0.5f;
    private static final float SHIFT_THRESHOLD = 0.1f;
    private static final long ANIMATION_DURATION = 150;
    private static final int PAGINATION_FADE_DELAY = 1000;
    private static final int PAGINATION_FADE_IN_DURATION = 150;
    private static final int PAGINATION_FADE_OUT_DURATION = 400;

    private static final int ENTER_ANIMATION_START_DELAY = 300;
    private static final int ENTER_ANIMATION_STAGGERED_DELAY = 150;
    private static final int ENTER_ANIMATION_DURATION = 400;

    private static final int PAGE_INDICATOR_ALPHA = 255;
    private static final int DOT_ALPHA = 128;
    private static final float DOT_ALPHA_FRACTION = 0.5f;
    private static final int DOT_GAP_FACTOR = 4;
    private static final int VISIBLE_ALPHA = 255;
    private static final int INVISIBLE_ALPHA = 0;
    private Paint mPaginationPaint;

    // This value approximately overshoots to 1.5 times the original size.
    private static final float ENTER_ANIMATION_OVERSHOOT_TENSION = 4.9f;

    private static final RectF sTempRect = new RectF();

    private static final FloatProperty<PageIndicatorDots> CURRENT_POSITION =
            new FloatProperty<PageIndicatorDots>("current_position") {
                @Override
                public Float get(PageIndicatorDots obj) {
                    return obj.mCurrentPosition;
                }

                @Override
                public void setValue(PageIndicatorDots obj, float pos) {
                    obj.mCurrentPosition = pos;
                    obj.invalidate();
                    obj.invalidateOutline();
                }
    };

    private static final IntProperty<PageIndicatorDots> PAGINATION_ALPHA =
            new IntProperty<PageIndicatorDots>("pagination_alpha") {
        @Override
        public Integer get(PageIndicatorDots obj) {
            return obj.mPaginationAlpha;
        }

        @Override
        public void setValue(PageIndicatorDots obj, int alpha) {
            obj.mPaginationAlpha = Utilities.boundToRange(alpha, INVISIBLE_ALPHA, VISIBLE_ALPHA);
            obj.invalidate();
            obj.invalidateOutline();
            obj.syncPillGlassAlpha();
        }
    };

    private final Handler mDelayedPaginationFadeHandler = new Handler(Looper.getMainLooper());
    private final float mDotRadius;
    private final float mCircleGap;
    private final boolean mIsRtl;

    private int mNumPages;
    private int mActivePage;
    private int mTotalScroll;
    private boolean mShouldAutoHide = true;
    private int mPaginationAlpha = INVISIBLE_ALPHA;
    private int mToAlpha = INVISIBLE_ALPHA;
    private boolean mForceDotsMode = false;
    private int mLastScrollX = -1;
    private boolean mIsDarkText = false;

    private float mCurrentPosition;
    private float mFinalPosition;
    private boolean mIsScrollPaused;
    private boolean mIsTwoPanels;
    private ObjectAnimator mAnimator;
    private @Nullable ObjectAnimator mAlphaAnimator;

    private float[] mEntryAnimationRadiusFactors;
    private static final boolean ENABLE_SEARCH_PILL_GLASS = true;
    private SearchPillGlassView mPillGlassView;
    private boolean mTouchDownOnPill = false;

    // Pre-allocated objects for search pill drawing and touch to prevent GC thrashing at 120Hz
    private final RectF mPillRect = new RectF();
    private final RectF mSlopRect = new RectF();
    private final Paint mPillBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPillIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mPillFontMetrics = new Paint.FontMetrics();

    private boolean isWorkspaceIndicator() {
        return getId() == R.id.page_indicator;
    }

    private final Runnable mHidePaginationRunnable = () -> {
        animatePaginationToAlpha(INVISIBLE_ALPHA);
    };

    public PageIndicatorDots(Context context) {
        this(context, null);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mPaginationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaginationPaint.setStyle(Style.FILL);
        updateThemeColors();
        mDotRadius = getResources().getDimension(R.dimen.page_indicator_dot_size) / 2;
        mCircleGap = DOT_GAP_FACTOR * mDotRadius;
        setOutlineProvider(new MyOutlineProver());
        mIsRtl = Utilities.isRtl(getResources());
        setClickable(false);

        mPillBgPaint.setStyle(Style.FILL);
        mPillStrokePaint.setStyle(Style.STROKE);
        mPillIconPaint.setStyle(Style.STROKE);
        mPillIconPaint.setStrokeCap(Paint.Cap.ROUND);
        mPillTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        }
    }

    private void updateThemeColors() {
        mIsDarkText = Themes.getAttrBoolean(getContext(), R.attr.isWorkspaceDarkText);
        if (mPaginationPaint != null) {
            boolean useWhiteInGlassPill = isWorkspaceIndicator()
                    && MultiModeController.isSingleLayerMode();
            mPaginationPaint.setColor((!useWhiteInGlassPill && mIsDarkText)
                    ? Color.BLACK : Color.WHITE);
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateThemeColors();
        invalidate();
    }

    public void setForceDotsMode(boolean forceDots) {
        if (mForceDotsMode != forceDots) {
            mForceDotsMode = forceDots;
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            if (forceDots) {
                animatePaginationToAlpha(VISIBLE_ALPHA);
            } else if (mShouldAutoHide) {
                animatePaginationToAlpha(INVISIBLE_ALPHA);
            }
        }
    }

    private float getPillWidth() {
        float density = getResources().getDisplayMetrics().density;
        return 78f * density;
    }

    private float getEffectiveCircleGap() {
        if (mNumPages <= 1) return mCircleGap;
        if (!isWorkspaceIndicator()) return mCircleGap;
        float density = getResources().getDisplayMetrics().density;
        float maxDotsSpan = (getPillWidth() - 24f * density) - 2f * mDotRadius;
        float naturalSpan = (mNumPages - 1) * mCircleGap;
        if (naturalSpan > maxDotsSpan) {
            return maxDotsSpan / (mNumPages - 1);
        }
        return mCircleGap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isWorkspaceIndicator() && MultiModeController.isSingleLayerMode()) {
            if (getVisibility() != View.VISIBLE || getAlpha() <= 0.02f) {
                mTouchDownOnPill = false;
                return false;
            }

            float density = getResources().getDisplayMetrics().density;
            float pillWidth = getPillWidth();
            float pillHeight = 26f * density;
            float pillLeft = (getWidth() - pillWidth) / 2f;
            float pillTop = (getHeight() - pillHeight) / 2f;
            mPillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!mForceDotsMode && mPillRect.contains(event.getX(), event.getY())) {
                        mTouchDownOnPill = true;
                        return true;
                    }
                    mTouchDownOnPill = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!mTouchDownOnPill) {
                        return false;
                    }
                    float touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    mSlopRect.set(
                            mPillRect.left - touchSlop,
                            mPillRect.top - touchSlop,
                            mPillRect.right + touchSlop,
                            mPillRect.bottom + touchSlop);
                    if (!mSlopRect.contains(event.getX(), event.getY())) {
                        mTouchDownOnPill = false;
                        return false;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (mTouchDownOnPill) {
                        mTouchDownOnPill = false;
                        if (mPillRect.contains(event.getX(), event.getY())) {
                            Launcher launcher = Launcher.getLauncher(getContext());
                            if (launcher != null && launcher.getWorkspace() != null
                                    && !launcher.getWorkspace().isWobbling() && !mForceDotsMode) {
                                android.util.Log.d("SearchPillDebug", "PageIndicatorDots: Opening Spotlight search");
                                com.android.launcher3.applibrary.view.SpotlightSearchOverlay.show(launcher);
                                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                                return true;
                            }
                            return true; // Consume touch so it never leaks to underlying views
                        }
                    }
                    return false;

                case MotionEvent.ACTION_CANCEL:
                    mTouchDownOnPill = false;
                    return false;
            }
        }
        return false;
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (currentScroll == 0 && totalScroll == 0) {
            CURRENT_POSITION.set(this, (float) mActivePage);
            mLastScrollX = currentScroll;
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
            return;
        }

        if (mNumPages <= 1) {
            mLastScrollX = currentScroll;
            return;
        }

        // Skip scroll update during binding. We will update it when binding completes.
        if (mIsScrollPaused) {
            mLastScrollX = currentScroll;
            return;
        }

        boolean isFirstScrollSeed = (mLastScrollX == -1);
        boolean scrollPositionChanged = !isFirstScrollSeed && (mLastScrollX != currentScroll);
        mLastScrollX = currentScroll;

        if (mForceDotsMode) {
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        } else if (scrollPositionChanged && mShouldAutoHide && isWorkspaceIndicator()) {
            animatePaginationToAlpha(VISIBLE_ALPHA);
        }

        if (mIsRtl) {
            currentScroll = totalScroll - currentScroll;
        }

        currentScroll = Utilities.boundToRange(currentScroll, 0, totalScroll);
        mTotalScroll = totalScroll;

        int scrollPerPage = totalScroll / (mNumPages - 1);
        int pageToLeft = scrollPerPage == 0 ? 0 : currentScroll / scrollPerPage;
        int pageToLeftScroll = pageToLeft * scrollPerPage;
        int pageToRightScroll = pageToLeftScroll + scrollPerPage;

        float scrollThreshold = SHIFT_THRESHOLD * scrollPerPage;
        if (currentScroll < pageToLeftScroll + scrollThreshold) {
            // scroll is within the left page's threshold
            animateToPosition(pageToLeft);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
        } else if (currentScroll > pageToRightScroll - scrollThreshold) {
            // scroll is far enough from left page to go to the right page
            animateToPosition(pageToLeft + 1);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                hideAfterDelay();
            }
        } else {
            // scroll is between left and right page
            animateToPosition(pageToLeft + SHIFT_PER_ANIMATION);
            if (isWorkspaceIndicator() && mShouldAutoHide && !mForceDotsMode) {
                mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            return;
        }
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mPaginationAlpha > INVISIBLE_ALPHA && !mForceDotsMode) {
            hideAfterDelay();
        } else if (!shouldAutoHide || mForceDotsMode) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void setPaintColor(int color) {
        mPaginationPaint.setColor(color);
    }

    private void hideAfterDelay() {
        if (!isWorkspaceIndicator() || mForceDotsMode) {
            return;
        }
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        mDelayedPaginationFadeHandler.postDelayed(mHidePaginationRunnable, PAGINATION_FADE_DELAY);
    }

    private void animatePaginationToAlpha(int alpha) {
        if (!isWorkspaceIndicator() && alpha < VISIBLE_ALPHA) {
            return;
        }
        if (alpha == mToAlpha && mPaginationAlpha == alpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }

        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mToAlpha = alpha;
        mAlphaAnimator = ObjectAnimator.ofInt(this, PAGINATION_ALPHA,
                alpha);
        // If we are animating to decrease the alpha, then it's a fade out animation
        // whereas if we are animating to increase the alpha, it's a fade in animation.
        mAlphaAnimator.setDuration(alpha < mPaginationAlpha
                ? PAGINATION_FADE_OUT_DURATION
                : PAGINATION_FADE_IN_DURATION);
        mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAlphaAnimator = null;
            }
        });
        mAlphaAnimator.start();
    }

    /**
     * Pauses all currently running animations.
     */
    @Override
    public void pauseAnimations() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.pause();
        }
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    @Override
    public void skipAnimationsToEnd() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.end();
        }
    }

    private void animateToPosition(float position) {
        mFinalPosition = position;
        if (Math.abs(mCurrentPosition - mFinalPosition) < SHIFT_THRESHOLD) {
            mCurrentPosition = mFinalPosition;
        }
        if (mAnimator == null && Float.compare(mCurrentPosition, mFinalPosition) != 0) {
            float positionForThisAnim = mCurrentPosition > mFinalPosition ?
                    mCurrentPosition - SHIFT_PER_ANIMATION : mCurrentPosition + SHIFT_PER_ANIMATION;
            mAnimator = ObjectAnimator.ofFloat(this, CURRENT_POSITION, positionForThisAnim);
            mAnimator.addListener(new AnimationCycleListener());
            mAnimator.setDuration(ANIMATION_DURATION);
            mAnimator.start();
        }
    }

    public void stopAllAnimations() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mFinalPosition = mActivePage;
        CURRENT_POSITION.set(this, mFinalPosition);
    }

    /**
     * Sets up up the page indicator to play the entry animation.
     * {@link #playEntryAnimation()} must be called after this.
     */
    public void prepareEntryAnimation() {
        mEntryAnimationRadiusFactors = new float[mNumPages];
        invalidate();
    }

    public void playEntryAnimation() {
        int count = mEntryAnimationRadiusFactors.length;
        if (count == 0) {
            mEntryAnimationRadiusFactors = null;
            invalidate();
            return;
        }

        Interpolator interpolator = new OvershootInterpolator(ENTER_ANIMATION_OVERSHOOT_TENSION);
        AnimatorSet animSet = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(ENTER_ANIMATION_DURATION);
            final int index = i;
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mEntryAnimationRadiusFactors[index] = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            anim.setInterpolator(interpolator);
            anim.setStartDelay(ENTER_ANIMATION_START_DELAY + ENTER_ANIMATION_STAGGERED_DELAY * i);
            animSet.play(anim);
        }

        animSet.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mEntryAnimationRadiusFactors = null;
                invalidateOutline();
                invalidate();
            }
        });
        animSet.start();
    }

    @Override
    public void setActiveMarker(int activePage) {
        // In unfolded foldables, every page has two CellLayouts, so we need to halve the active
        // page for it to be accurate.
        if (mIsTwoPanels && !FOLDABLE_SINGLE_PAGE.get()) {
            activePage = activePage / 2;
        }

        if (mActivePage != activePage) {
            mActivePage = activePage;
            invalidate();
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        }

        // If the last page gets removed we want to go to the previous page.
        if (mNumPages > 0 && mNumPages == mActivePage) {
            mActivePage--;
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        requestLayout();
        post(this::syncPillGlassBounds);
    }

    @Override
    public void setPauseScroll(boolean pause, boolean isTwoPanels) {
        mIsTwoPanels = isTwoPanels;

        // Reapply correct current position which was skipped during setScroll.
        if (mIsScrollPaused && !pause) {
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        mIsScrollPaused = pause;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Add extra spacing of mDotRadius on all sides so than entry animation could be run.
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ?
                MeasureSpec.getSize(widthMeasureSpec) : (int) ((mNumPages * 3 + 2) * mDotRadius);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : (int) (4 * mDotRadius);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isWorkspaceIndicator()) {
            mShouldAutoHide = false;
            mPaginationAlpha = VISIBLE_ALPHA;
            mToAlpha = VISIBLE_ALPHA;
            setVisibility(View.VISIBLE);
        } else if (MultiModeController.isSingleLayerMode() && mShouldAutoHide) {
            mPaginationAlpha = INVISIBLE_ALPHA;
            mToAlpha = INVISIBLE_ALPHA;
        }
        attachPillGlassIfNeeded();
        post(this::syncPillGlassBounds);
    }

    public void resetToIdle() {
        if (isWorkspaceIndicator() && MultiModeController.isSingleLayerMode()) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
            if (mAlphaAnimator != null) {
                mAlphaAnimator.cancel();
                mAlphaAnimator = null;
            }
            if (mAnimator != null) {
                mAnimator.cancel();
                mAnimator = null;
            }
            mForceDotsMode = false;
            mPaginationAlpha = INVISIBLE_ALPHA;
            mToAlpha = INVISIBLE_ALPHA;
            syncPillGlassBounds();
            syncPillGlassAlpha();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
            mAlphaAnimator = null;
        }
        detachPillGlass();
    }

    private void attachPillGlassIfNeeded() {
        if (!ENABLE_SEARCH_PILL_GLASS) {
            return;
        }
        if (!isWorkspaceIndicator() || !MultiModeController.isSingleLayerMode()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (mPillGlassView != null) {
            return;
        }
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            return;
        }
        mPillGlassView = new SearchPillGlassView(getContext());
        float density = getResources().getDisplayMetrics().density;
        int pillWidth = Math.round(getPillWidth());
        int pillHeight = Math.round(26f * density);
        mPillGlassView.setCornerRadius(pillHeight / 2f);
        View proxy = parent.findViewById(R.id.glass_wallpaper_proxy);
        if (proxy == null) {
            proxy = parent.findViewById(R.id.glass_source);
        }
        if (proxy != null) {
            mPillGlassView.setBackdropSource(proxy);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pillWidth, pillHeight);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        int pillLeftInSelf = (getWidth() > 0 ? (getWidth() - pillWidth) / 2 : 0);
        int pillTopInSelf = (getHeight() > 0 ? (getHeight() - pillHeight) / 2 : 0);
        lp.leftMargin = getLeft() + pillLeftInSelf;
        lp.topMargin = getTop() + pillTopInSelf;
        int myIndex = parent.indexOfChild(this);
        if (myIndex >= 0) {
            parent.addView(mPillGlassView, myIndex, lp);
        } else {
            parent.addView(mPillGlassView, lp);
        }
        syncPillGlassBounds();
        syncPillGlassAlpha();
    }

    private void detachPillGlass() {
        if (mPillGlassView != null) {
            final SearchPillGlassView glassView = mPillGlassView;
            mPillGlassView = null;
            // Never mutate the parent's children array synchronously during dispatchDetachedFromWindow.
            // Removing a sibling during parent's detachment loop corrupts the array and causes an NPE
            // in ViewGroup.dispatchDetachedFromWindow. Post the removal instead.
            post(() -> {
                ViewGroup parent = (ViewGroup) glassView.getParent();
                if (parent != null) {
                    parent.removeView(glassView);
                }
            });
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutPillGlassView();
    }

    private void layoutPillGlassView() {
        if (mPillGlassView == null || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int pillWidth = Math.round(getPillWidth());
        int pillHeight = Math.round(26f * density);
        int pillLeftInSelf = (getWidth() - pillWidth) / 2;
        int pillTopInSelf = (getHeight() - pillHeight) / 2;
        int pillX = getLeft() + pillLeftInSelf;
        int pillY = getTop() + pillTopInSelf;

        if (mPillGlassView.getLeft() == pillX && mPillGlassView.getTop() == pillY
                && mPillGlassView.getWidth() == pillWidth && mPillGlassView.getHeight() == pillHeight) {
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPillGlassView.getLayoutParams();
        if (lp != null) {
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = pillX;
            lp.topMargin = pillY;
            lp.width = pillWidth;
            lp.height = pillHeight;
        }

        mPillGlassView.measure(
                MeasureSpec.makeMeasureSpec(pillWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(pillHeight, MeasureSpec.EXACTLY));
        mPillGlassView.layout(pillX, pillY, pillX + pillWidth, pillY + pillHeight);
        mPillGlassView.invalidate();
    }

    private void syncPillGlassBounds() {
        if (mPillGlassView == null || getWidth() <= 0 || getHeight() <= 0) return;
        layoutPillGlassView();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPillGlassView.getLayoutParams();
        if (lp != null) {
            mPillGlassView.setLayoutParams(lp);
        }
    }

    public void syncPillGlassAlpha() {
        if (mPillGlassView == null) return;
        if (!MultiModeController.isSingleLayerMode() || !isWorkspaceIndicator()) {
            mPillGlassView.setVisibility(View.GONE);
            return;
        }
        float effectiveAlpha = getAlpha();
        if (effectiveAlpha <= 0f || getVisibility() != View.VISIBLE) {
            mPillGlassView.setVisibility(View.INVISIBLE);
        } else {
            mPillGlassView.setVisibility(View.VISIBLE);
            mPillGlassView.setAlpha(effectiveAlpha);
        }
    }

    public SearchPillGlassView getPillGlassView() {
        return mPillGlassView;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        syncPillGlassAlpha();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncPillGlassAlpha();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mNumPages < 1) {
            return;
        }
        if (mNumPages < 2 && isWorkspaceIndicator()) {
            drawSearchPillIfSingleLayer(canvas, 1f);
            return;
        }

        boolean isSingleLayerWorkspace = isWorkspaceIndicator()
                && MultiModeController.isSingleLayerMode();

        // Zero-deadband gamma-corrected crossfade at midpoint 128:
        float pillAlpha;
        int dotAlpha;
        if (isSingleLayerWorkspace) {
            if (mPaginationAlpha < 128) {
                float t = (128f - mPaginationAlpha) / 128f;
                pillAlpha = (float) Math.sqrt(t);
                dotAlpha = 0;
            } else {
                float t = (mPaginationAlpha - 128f) / 127f;
                pillAlpha = 0f;
                dotAlpha = Math.round((float) Math.sqrt(t) * PAGE_INDICATOR_ALPHA);
            }
        } else {
            pillAlpha = 0f;
            dotAlpha = (!isWorkspaceIndicator()) ? PAGE_INDICATOR_ALPHA : mPaginationAlpha;
        }

        // Draw iOS Search pill background/rim and (when pillAlpha > 0) "Search" icon + text
        drawSearchPillIfSingleLayer(canvas, pillAlpha);

        if (dotAlpha <= 0) {
            return;
        }

        // Draw all page indicators
        float circleGap = getEffectiveCircleGap();
        float startX = ((float) getWidth() / 2f)
                - (circleGap * (((float) mNumPages - 1) / 2f))
                - mDotRadius;

        float x = startX + mDotRadius;
        float y = getHeight() / 2f;

        // Always use bright WHITE dots inside the Single Layer glass pill
        mPaginationPaint.setColor((!isSingleLayerWorkspace && mIsDarkText)
                ? Color.BLACK : Color.WHITE);

        if (mEntryAnimationRadiusFactors != null) {
            if (mIsRtl) {
                x = getWidth() - x;
                circleGap = -circleGap;
            }
            for (int i = 0; i < mEntryAnimationRadiusFactors.length; i++) {
                mPaginationPaint.setAlpha(i == mActivePage
                        ? dotAlpha
                        : (int) (dotAlpha * DOT_ALPHA_FRACTION));
                canvas.drawCircle(x, y, mDotRadius * mEntryAnimationRadiusFactors[i],
                        mPaginationPaint);
                x += circleGap;
            }
        } else {
            mPaginationPaint.setAlpha((int) (dotAlpha * DOT_ALPHA_FRACTION));
            for (int i = 0; i < mNumPages; i++) {
                canvas.drawCircle(x, y, mDotRadius, mPaginationPaint);
                x += circleGap;
            }

            mPaginationPaint.setAlpha(dotAlpha);
            canvas.drawRoundRect(getActiveRect(), mDotRadius, mDotRadius, mPaginationPaint);
        }
    }

    private void drawSearchPillIfSingleLayer(Canvas canvas, float pillAlpha) {
        if (!isWorkspaceIndicator() || !MultiModeController.isSingleLayerMode()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float pillWidth = getPillWidth();
        float pillHeight = 26f * density;
        float pillLeft = (getWidth() - pillWidth) / 2f;
        float pillTop = (getHeight() - pillHeight) / 2f;
        mPillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight);

        float viewAlpha = getAlpha();
        if (viewAlpha <= 0.02f) {
            return;
        }

        // Always use white foreground inside the frosted glass pill
        int pillFgRgb = 255;

        // Draw fallback frosted background and rim only when SearchPillGlassView is absent/not ready
        boolean glassReady = (mPillGlassView != null && mPillGlassView.getParent() != null
                && mPillGlassView.getWidth() > 0 && mPillGlassView.getHeight() > 0
                && mPillGlassView.getVisibility() == View.VISIBLE);
        if (!glassReady) {
            mPillBgPaint.setColor(Color.argb((int) (55 * viewAlpha), 255, 255, 255));
            canvas.drawRoundRect(mPillRect, pillHeight / 2f, pillHeight / 2f, mPillBgPaint);

            mPillStrokePaint.setStrokeWidth(0.8f * density);
            mPillStrokePaint.setColor(Color.argb((int) (40 * viewAlpha), 255, 255, 255));
            canvas.drawRoundRect(mPillRect, pillHeight / 2f, pillHeight / 2f, mPillStrokePaint);
        }

        float contentAlpha = pillAlpha * viewAlpha;
        if (contentAlpha <= 0.02f) {
            return;
        }

        String label = "Search";
        mPillTextPaint.setColor(Color.argb((int) (235 * contentAlpha), pillFgRgb, pillFgRgb, pillFgRgb));
        mPillTextPaint.setTextSize(12f * density);
        float textWidth = mPillTextPaint.measureText(label);

        mPillIconPaint.setStrokeWidth(1.4f * density);
        mPillIconPaint.setColor(Color.argb((int) (235 * contentAlpha), pillFgRgb, pillFgRgb, pillFgRgb));

        float iconRadius = 3.5f * density;
        float handleExtent = iconRadius * 0.707f + 3f * density;
        float iconWidth = iconRadius + handleExtent;
        float iconTextGap = 4.5f * density;

        float totalContentWidth = iconWidth + iconTextGap + textWidth;
        float contentStartX = (getWidth() - totalContentWidth) / 2f;

        float iconCenterX = contentStartX + iconRadius;
        float iconCenterY = getHeight() / 2f - (handleExtent - iconRadius) / 2f;
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, mPillIconPaint);
        canvas.drawLine(
                iconCenterX + iconRadius * 0.707f,
                iconCenterY + iconRadius * 0.707f,
                iconCenterX + handleExtent,
                iconCenterY + handleExtent,
                mPillIconPaint
        );

        float textX = contentStartX + iconWidth + iconTextGap;
        mPillTextPaint.getFontMetrics(mPillFontMetrics);
        float textY = (getHeight() / 2f) - (mPillFontMetrics.ascent + mPillFontMetrics.descent) / 2f;
        canvas.drawText(label, textX, textY, mPillTextPaint);
    }

    private RectF getActiveRect() {
        float startCircle = (int) mCurrentPosition;
        float delta = mCurrentPosition - startCircle;
        float diameter = 2 * mDotRadius;
        float circleGap = getEffectiveCircleGap();
        float startX = ((float) getWidth() / 2)
                - (circleGap * (((float) mNumPages - 1) / 2))
                - mDotRadius;
        sTempRect.top = (getHeight() * 0.5f) - mDotRadius;
        sTempRect.bottom = (getHeight() * 0.5f) + mDotRadius;
        sTempRect.left = startX + (startCircle * circleGap);
        sTempRect.right = sTempRect.left + diameter;

        if (delta < SHIFT_PER_ANIMATION) {
            // dot is capturing the right circle.
            sTempRect.right += delta * circleGap * 2;
        } else {
            // Dot is leaving the left circle.
            sTempRect.right += circleGap;

            delta -= SHIFT_PER_ANIMATION;
            sTempRect.left += delta * circleGap * 2;
        }

        if (mIsRtl) {
            float rectWidth = sTempRect.width();
            sTempRect.right = getWidth() - sTempRect.left;
            sTempRect.left = sTempRect.right - rectWidth;
        }

        return sTempRect;
    }

    private class MyOutlineProver extends ViewOutlineProvider {

        @Override
        public void getOutline(View view, Outline outline) {
            float density = getResources().getDisplayMetrics().density;
            float pillWidth = getPillWidth();
            float pillHeight = 26f * density;
            float pillLeft = (getWidth() - pillWidth) / 2f;
            float pillTop = (getHeight() - pillHeight) / 2f;
            outline.setRoundRect(
                    (int) pillLeft,
                    (int) pillTop,
                    (int) (pillLeft + pillWidth),
                    (int) (pillTop + pillHeight),
                    pillHeight / 2f
            );
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (mPillGlassView != null) {
            mPillGlassView.setTranslationY(translationY);
        }
    }

    public void setForcedTranslationY(float translationY) {
        setTranslationY(translationY);
    }

    /**
     * Listener for keep running the animation until the final state is reached.
     */
    private class AnimationCycleListener extends AnimatorListenerAdapter {

        private boolean mCancelled = false;

        @Override
        public void onAnimationCancel(Animator animation) {
            mCancelled = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCancelled) {
                if (isWorkspaceIndicator() && mShouldAutoHide) {
                    hideAfterDelay();
                }
                mAnimator = null;
                animateToPosition(mFinalPosition);
            }
        }
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the pagination.
     */
    @Override
    public void setInsets(Rect insets) {
        if (!isWorkspaceIndicator()) {
            return;
        }
        DeviceProfile grid = ActivityContext.lookupContext(getContext()).getDeviceProfile();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (lp == null) return;
        if (grid.isFloatingDockEnabled() && grid.hotseatGeometry != null) {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int dockTopFromBottom = insets.bottom + grid.hotseatBarBottomSpacePx
                    + grid.hotseatGeometry.getBounds().height();
            lp.bottomMargin = dockTopFromBottom + grid.hotseatGeometry.getIndicatorMargin();
            lp.topMargin = 0;
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int gapPx = (int) (8 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = insets.bottom + grid.hotseatBarSizePx + gapPx;
        }
        setLayoutParams(lp);
        post(this::syncPillGlassBounds);
    }
}
```

---

# src/com/android/launcher3/pageindicators/WorkspacePageIndicator.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/pageindicators/WorkspacePageIndicator.java`  
**Lines**: 283 | **Bytes**: 10259

```java
package com.android.launcher3.pageindicators;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Property;
import android.view.View;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

/**
 * A PageIndicator that briefly shows a fraction of a line when moving between pages
 *
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
public class WorkspacePageIndicator extends View implements Insettable, PageIndicator {

    private static final int LINE_ANIMATE_DURATION = 400; // fade-out duration in ms
    private static final int LINE_FADE_DELAY = 1500;      // stay visible 1.5s then fade away
    public static final int WHITE_ALPHA = (int) (0.70f * 255);
    public static final int BLACK_ALPHA = (int) (0.65f * 255);

    private static final int LINE_ALPHA_ANIMATOR_INDEX = 0;
    private static final int NUM_PAGES_ANIMATOR_INDEX = 1;
    private static final int TOTAL_SCROLL_ANIMATOR_INDEX = 2;
    private static final int ANIMATOR_COUNT = 3;

    private ValueAnimator[] mAnimators = new ValueAnimator[ANIMATOR_COUNT];

    private final Handler mDelayedLineFadeHandler = new Handler(Looper.getMainLooper());
    private final Launcher mLauncher;

    private boolean mShouldAutoHide = true;

    // The alpha of the line when it is showing.
    private int mActiveAlpha = 0;
    // The alpha that the line is being animated to or already at (either 0 or mActiveAlpha).
    private int mToAlpha;
    // A float value representing the number of pages, to allow for an animation when it changes.
    private float mNumPagesFloat;
    private int mCurrentScroll;
    private int mTotalScroll;
    private Paint mLinePaint;
    private final int mLineHeight;

    private static final Property<WorkspacePageIndicator, Integer> PAINT_ALPHA
            = new Property<WorkspacePageIndicator, Integer>(Integer.class, "paint_alpha") {
        @Override
        public Integer get(WorkspacePageIndicator obj) {
            return obj.mLinePaint.getAlpha();
        }

        @Override
        public void set(WorkspacePageIndicator obj, Integer alpha) {
            obj.mLinePaint.setAlpha(alpha);
            obj.invalidate();
        }
    };

    private static final Property<WorkspacePageIndicator, Float> NUM_PAGES
            = new Property<WorkspacePageIndicator, Float>(Float.class, "num_pages") {
        @Override
        public Float get(WorkspacePageIndicator obj) {
            return obj.mNumPagesFloat;
        }

        @Override
        public void set(WorkspacePageIndicator obj, Float numPages) {
            obj.mNumPagesFloat = numPages;
            obj.invalidate();
        }
    };

    private static final Property<WorkspacePageIndicator, Integer> TOTAL_SCROLL
            = new Property<WorkspacePageIndicator, Integer>(Integer.class, "total_scroll") {
        @Override
        public Integer get(WorkspacePageIndicator obj) {
            return obj.mTotalScroll;
        }

        @Override
        public void set(WorkspacePageIndicator obj, Integer totalScroll) {
            obj.mTotalScroll = totalScroll;
            obj.invalidate();
        }
    };

    private Runnable mHideLineRunnable = () -> animateLineToAlpha(0);

    public WorkspacePageIndicator(Context context) {
        this(context, null);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();
        mLinePaint = new Paint();
        mLinePaint.setAlpha(0);

        mLauncher = Launcher.getLauncher(context);
        mLineHeight = res.getDimensionPixelSize(R.dimen.workspace_page_indicator_line_height);

        boolean darkText = Themes.getAttrBoolean(mLauncher, R.attr.isWorkspaceDarkText);
        mActiveAlpha = darkText ? BLACK_ALPHA : WHITE_ALPHA;
        mLinePaint.setColor(darkText ? Color.BLACK : Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTotalScroll == 0 || mNumPagesFloat == 0) {
            return;
        }

        // Compute and draw line rect.
        float progress = Utilities.boundToRange(((float) mCurrentScroll) / mTotalScroll, 0f, 1f);
        int availableWidth = getWidth();
        int lineWidth = (int) (availableWidth / mNumPagesFloat);
        int lineLeft = (int) (progress * (availableWidth - lineWidth));
        int lineRight = lineLeft + lineWidth;

        canvas.drawRoundRect(lineLeft, getHeight() / 2 - mLineHeight / 2, lineRight,
                getHeight() / 2 + mLineHeight / 2, mLineHeight, mLineHeight, mLinePaint);
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (getAlpha() == 0) {
            return;
        }
        animateLineToAlpha(mActiveAlpha);

        mCurrentScroll = currentScroll;
        if (mTotalScroll == 0) {
            mTotalScroll = totalScroll;
        } else if (mTotalScroll != totalScroll) {
            animateToTotalScroll(totalScroll);
        } else {
            invalidate();
        }

        if (mShouldAutoHide) {
            hideAfterDelay();
        }
    }

    private void hideAfterDelay() {
        mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        mDelayedLineFadeHandler.postDelayed(mHideLineRunnable, LINE_FADE_DELAY);
    }

    @Override
    public void setActiveMarker(int activePage) { }

    @Override
    public void setMarkersCount(int numMarkers) {
        if (Float.compare(numMarkers, mNumPagesFloat) != 0) {
            setupAndRunAnimation(ObjectAnimator.ofFloat(this, NUM_PAGES, numMarkers),
                    NUM_PAGES_ANIMATOR_INDEX);
        } else {
            if (mAnimators[NUM_PAGES_ANIMATOR_INDEX] != null) {
                mAnimators[NUM_PAGES_ANIMATOR_INDEX].cancel();
                mAnimators[NUM_PAGES_ANIMATOR_INDEX] = null;
            }
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mLinePaint.getAlpha() > 0) {
            hideAfterDelay();
        } else if (!shouldAutoHide) {
            mDelayedLineFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    private void animateLineToAlpha(int alpha) {
        if (alpha == mToAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }
        mToAlpha = alpha;
        setupAndRunAnimation(ObjectAnimator.ofInt(this, PAINT_ALPHA, alpha),
                LINE_ALPHA_ANIMATOR_INDEX);
    }

    private void animateToTotalScroll(int totalScroll) {
        setupAndRunAnimation(ObjectAnimator.ofInt(this, TOTAL_SCROLL, totalScroll),
                TOTAL_SCROLL_ANIMATOR_INDEX);
    }

    /**
     * Starts the given animator and stores it in the provided index in {@link #mAnimators} until
     * the animation ends.
     *
     * If an animator is already at the index (i.e. it is already playing), it is canceled and
     * replaced with the new animator.
     */
    private void setupAndRunAnimation(ValueAnimator animator, final int animatorIndex) {
        if (mAnimators[animatorIndex] != null) {
            mAnimators[animatorIndex].cancel();
        }
        mAnimators[animatorIndex] = animator;
        mAnimators[animatorIndex].addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimators[animatorIndex] = null;
            }
        });
        mAnimators[animatorIndex].setDuration(LINE_ANIMATE_DURATION);
        mAnimators[animatorIndex].start();
    }

    /**
     * Pauses all currently running animations.
     */
    @Override
    public void pauseAnimations() {
        for (int i = 0; i < ANIMATOR_COUNT; i++) {
            if (mAnimators[i] != null) {
                mAnimators[i].pause();
            }
        }
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    @Override
    public void skipAnimationsToEnd() {
        for (int i = 0; i < ANIMATOR_COUNT; i++) {
            if (mAnimators[i] != null) {
                mAnimators[i].end();
            }
        }
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the page indicator.
     */
    @Override
    public void setInsets(Rect insets) {
        com.android.launcher3.DeviceProfile grid =
                com.android.launcher3.views.ActivityContext.lookupContext(getContext())
                        .getDeviceProfile();
        android.widget.FrameLayout.LayoutParams lp =
                (android.widget.FrameLayout.LayoutParams) getLayoutParams();
        if (grid.isFloatingDockEnabled() && grid.hotseatGeometry != null) {
            lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            int dockTopFromBottom = insets.bottom + grid.hotseatBarBottomSpacePx
                    + grid.hotseatGeometry.getBounds().height();
            lp.bottomMargin = dockTopFromBottom + grid.hotseatGeometry.getIndicatorMargin();
            lp.topMargin = 0;
        } else {
            // Position indicator just above the dock: hotseatBarSizePx + small 8dp gap
            lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            int gapPx = (int) (8 * getResources().getDisplayMetrics().density);
            lp.bottomMargin = insets.bottom + grid.hotseatBarSizePx + gapPx;
        }
        setLayoutParams(lp);
    }
}
```

---

# bliss/src/foundation/e/bliss/wobble/JiggleTopBarView.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/wobble/JiggleTopBarView.kt`  
**Lines**: 359 | **Bytes**: 13877

```kotlin
/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.wobble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher

class JiggleTopBarView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), Insettable {

    private val editButtonContainer: FrameLayout
    private val doneButtonContainer: FrameLayout
    private val editTextView: TextView
    private val doneTextView: TextView
    private val contentRow: FrameLayout
    private var isShowing = false
    private var activePressedView: View? = null
    private var currentAnimator: ObjectAnimator? = null

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false
        val density = resources.displayMetrics.density
        setPadding(0, 0, 0, (12 * density).toInt())

        contentRow =
            FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                clipChildren = false
                clipToPadding = false
            }

        // --- Edit Button (Liquid Glass Pill) ---
        val editWidth = (62 * density).toInt()
        val buttonHeight = (32 * density).toInt()

        editButtonContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(editWidth, buttonHeight).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                isClickable = true
                isFocusable = true
            }

        val editGlassBg =
            foundation.e.bliss.glass.JiggleButtonGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(editWidth, buttonHeight)
            }
        editButtonContainer.addView(editGlassBg)

        editTextView =
            TextView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                text = "Edit"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }
        editButtonContainer.addView(editTextView)

        val onEditClick = View.OnClickListener {
            JiggleEditMenuPopup.showPopup(editButtonContainer)
        }
        editButtonContainer.setOnClickListener(onEditClick)
        editTextView.setOnClickListener(onEditClick)
        contentRow.addView(editButtonContainer)

        // --- Done Button (Liquid Glass Pill) ---
        val doneWidth = (68 * density).toInt()

        doneButtonContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(doneWidth, buttonHeight).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    }
                isClickable = true
                isFocusable = true
            }

        val doneGlassBg =
            foundation.e.bliss.glass.JiggleButtonGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(doneWidth, buttonHeight)
            }
        doneButtonContainer.addView(doneGlassBg)

        doneTextView =
            TextView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                text = "Done"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        doneButtonContainer.addView(doneTextView)

        val onDoneClick = View.OnClickListener {
            val launcher = Launcher.getLauncher(context)
            launcher.workspace?.wobbleLayouts(false)
        }
        doneButtonContainer.setOnClickListener(onDoneClick)
        doneTextView.setOnClickListener(onDoneClick)
        contentRow.addView(doneButtonContainer)

        addView(contentRow)
        elevation = 100f
    }

    override fun setInsets(insets: Rect) {
        val density = resources.displayMetrics.density
        val topMargin = maxOf((12 * density).toInt(), insets.top + (6 * density).toInt())
        val lp = contentRow.layoutParams as? LayoutParams ?: return
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            contentRow.layoutParams = lp
        }
    }

    private fun getExpandedHitRect(child: View, horizontalPadDp: Float, verticalPadDp: Float): Rect {
        val rect = Rect()
        child.getHitRect(rect)
        rect.offset(contentRow.left, contentRow.top)
        val density = resources.displayMetrics.density
        val hPad = (horizontalPadDp * density).toInt()
        val vPad = (verticalPadDp * density).toInt()
        rect.inset(-hPad, -vPad)
        return rect
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (visibility != VISIBLE || alpha < 0.1f) return false

        val x = ev.x.toInt()
        val y = ev.y.toInt()

        val editHitRect = getExpandedHitRect(editButtonContainer, 24f, 24f)
        val doneHitRect = getExpandedHitRect(doneButtonContainer, 24f, 24f)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (editHitRect.contains(x, y)) {
                    activePressedView = editButtonContainer
                    animateButtonPress(editButtonContainer, true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                } else if (doneHitRect.contains(x, y)) {
                    activePressedView = doneButtonContainer
                    animateButtonPress(doneButtonContainer, true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val pressed = activePressedView
                if (pressed != null) {
                    val hitRect = if (pressed == editButtonContainer) editHitRect else doneHitRect
                    val isInside = hitRect.contains(x, y)
                    animateButtonPress(pressed, isInside)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val pressed = activePressedView
                activePressedView = null
                if (pressed != null) {
                    animateButtonPress(pressed, false)
                    val hitRect = if (pressed == editButtonContainer) editHitRect else doneHitRect
                    if (hitRect.contains(x, y)) {
                        pressed.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        if (pressed == editButtonContainer) {
                            JiggleEditMenuPopup.showPopup(editButtonContainer)
                        } else {
                            val launcher = Launcher.getLauncher(context)
                            launcher.workspace?.wobbleLayouts(false)
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                val pressed = activePressedView
                activePressedView = null
                if (pressed != null) {
                    animateButtonPress(pressed, false)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun animateButtonPress(view: View, pressed: Boolean) {
        val targetScale = if (pressed) 0.92f else 1.0f
        val targetAlpha = if (pressed) 0.65f else 1.0f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .alpha(targetAlpha)
            .setDuration(100)
            .start()
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        currentAnimator?.cancel()
        currentAnimator = null
        visibility = VISIBLE
        bringToFront()
        alpha = 0f
        val density = resources.displayMetrics.density
        translationY = -15f * density

        val launcher = try { Launcher.getLauncher(context) } catch (e: Exception) { null }
        val statusBarHeight = launcher?.deviceProfile?.insets?.top ?: 0
        val topMargin = maxOf((12 * density).toInt(), statusBarHeight + (6 * density).toInt())
        val lp = contentRow.layoutParams as? LayoutParams
        if (lp != null && lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            contentRow.layoutParams = lp
        }

        editButtonContainer.scaleX = 1f
        editButtonContainer.scaleY = 1f
        editButtonContainer.alpha = 1f
        doneButtonContainer.scaleX = 1f
        doneButtonContainer.scaleY = 1f
        doneButtonContainer.alpha = 1f

        // Trigger wallpaper binding on glass views
        post {
            for (i in 0 until editButtonContainer.childCount) {
                (editButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                    ?.bindToWallpaper()
            }
            for (i in 0 until doneButtonContainer.childCount) {
                (doneButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                    ?.bindToWallpaper()
            }
        }

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        val transAnim = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -15f * density, 0f)

        val anim = ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 250
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            addUpdateListener { invalidateGlassButtons() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentAnimator === animation) {
                        currentAnimator = null
                    }
                    invalidateGlassButtons()
                }
            })
            start()
        }
        currentAnimator = anim
    }

    fun invalidateGlassButtons() {
        for (i in 0 until editButtonContainer.childCount) {
            (editButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                ?.invalidate()
        }
        for (i in 0 until doneButtonContainer.childCount) {
            (doneButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                ?.invalidate()
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        activePressedView = null
        currentAnimator?.cancel()
        currentAnimator = null
        val density = resources.displayMetrics.density

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, alpha, 0f)
        val transAnim =
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, translationY, -15f * density)

        val anim = ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 200
            interpolator = Interpolators.EMPHASIZED_ACCELERATE
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentAnimator === animation) {
                            currentAnimator = null
                        }
                        if (!isShowing) {
                            visibility = GONE
                            editButtonContainer.scaleX = 1f
                            editButtonContainer.scaleY = 1f
                            editButtonContainer.alpha = 1f
                            doneButtonContainer.scaleX = 1f
                            doneButtonContainer.scaleY = 1f
                            doneButtonContainer.alpha = 1f
                        }
                    }
                }
            )
            start()
        }
        currentAnimator = anim
    }
}
```

---

# bliss/src/foundation/e/bliss/wobble/JiggleEditMenuPopup.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/wobble/JiggleEditMenuPopup.kt`  
**Lines**: 402 | **Bytes**: 14528

```kotlin
/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.wobble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.ads.AdManager
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.OptionsPopupView
import foundation.e.bliss.glass.PopupGlassView
import foundation.e.bliss.wallpaper.WallpaperPickerHelper

class JiggleEditMenuPopup
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractFloatingView(context, attrs, defStyleAttr) {

    private val launcher = Launcher.getLauncher(context)
    private var isOpen = false
    private val cardContainer: FrameLayout
    private val glassBg: PopupGlassView
    private val menuRows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val density = resources.displayMetrics.density
        val cardWidth = (196 * density).toInt()
        val rowHeight = (44 * density).toInt()
        val dividerHeight = maxOf(1, (0.55f * density).toInt())
        val cardHeight = (rowHeight * 3) + (dividerHeight * 2)

        cardContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(cardWidth, cardHeight).apply {
                        gravity = Gravity.START or Gravity.TOP
                    }
            }

        glassBg =
            PopupGlassView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                setPopupRadius(26f * density)
            }
        cardContainer.addView(glassBg)

        // Vertical rows container
        val rowsColumn =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

        // --- Row 1: Add Widget ---
        val rowAddWidget = createRow(
            iconRes = R.drawable.ic_add_widget,
            label = "Add Widget",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                OptionsPopupView.openWidgets(launcher)
            }
        }
        rowsColumn.addView(rowAddWidget)
        menuRows.add(rowAddWidget)

        rowsColumn.addView(createDivider(density, dividerHeight))

        // --- Row 2: Customize ---
        val rowCustomize = createRow(
            iconRes = R.drawable.ic_palette,
            label = "Customize",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                try {
                    launcher.startActivity(Intent(launcher, com.android.launcher3.settings.SettingsActivity::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("JiggleEditMenuPopup", "Error launching settings", e)
                }
            }
        }
        rowsColumn.addView(rowCustomize)
        menuRows.add(rowCustomize)

        rowsColumn.addView(createDivider(density, dividerHeight))

        // --- Row 3: Change Wallpaper ---
        val rowEditWallpaper = createRow(
            iconRes = R.drawable.ic_edit_wallpaper,
            label = "Change Wallpaper",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                launcher.workspace?.wobbleLayouts(false)
                WallpaperPickerHelper.launchGalleryPicker(launcher)
            }
        }
        rowsColumn.addView(rowEditWallpaper)
        menuRows.add(rowEditWallpaper)

        cardContainer.addView(rowsColumn)
        addView(cardContainer)
        isClickable = true
        isFocusable = true
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun createRow(
        iconRes: Int,
        label: String,
        density: Float,
        rowHeight: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            isClickable = true
            isFocusable = true

            val textColor = Color.BLACK
            val rippleColor = Color.argb(28, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, ColorDrawable(Color.WHITE))

            setOnClickListener { onClick() }

            val iconView = ImageView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
                        .apply { marginEnd = (12 * density).toInt() }
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(textColor)
            }
            addView(iconView)

            val labelView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                text = label
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(labelView)
        }
    }

    private fun createDivider(density: Float, dividerHeight: Int): View {
        val dividerColor = Color.parseColor("#1F000000")
        return View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerHeight)
                    .apply {
                        marginStart = (48 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
            setBackgroundColor(dividerColor)
        }
    }

    fun show(anchorView: View) {
        val dragLayer = launcher.dragLayer ?: return
        val density = resources.displayMetrics.density

        val anchorRect = Rect()
        dragLayer.getDescendantRectRelativeToSelf(anchorView, anchorRect)

        val left = if (anchorRect.left > 0) anchorRect.left else (16 * density).toInt()
        val top = if (anchorRect.bottom > 0) {
            anchorRect.bottom + (4 * density).toInt()
        } else {
            val statusBarHeight = launcher.deviceProfile?.insets?.top ?: (24 * density).toInt()
            statusBarHeight + (42 * density).toInt()
        }

        val cardWidth = cardContainer.layoutParams.width
        val maxLeft = if (dragLayer.width > cardWidth) dragLayer.width - cardWidth - (12 * density).toInt() else left
        val safeLeft = if (maxLeft > (12 * density).toInt()) left.coerceIn((12 * density).toInt(), maxLeft) else left

        val lp = cardContainer.layoutParams as LayoutParams
        lp.marginStart = safeLeft
        lp.topMargin = top
        cardContainer.layoutParams = lp

        layoutParams =
            BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                ignoreInsets = true
            }
        elevation = 200f
        isOpen = true
        mIsOpen = true
        dragLayer.addView(this)
        launcher.updateDisallowBack()

        post { glassBg.bindToWallpaper() }

        cardContainer.pivotX = (16 * density)
        cardContainer.pivotY = 0f
        cardContainer.scaleX = 0.82f
        cardContainer.scaleY = 0.82f
        cardContainer.alpha = 0f

        val spring = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.82f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.82f, 1f)
        val alpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)

        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 240
            interpolator = spring
            start()
        }
    }

    override fun canHandleBack(): Boolean = true

    override fun onBackProgressed(backEvent: android.window.BackEvent) {
        val progress = backEvent.progress
        val scale = 1f - 0.08f * progress
        cardContainer.scaleX = scale
        cardContainer.scaleY = scale
        cardContainer.alpha = 1f - 0.25f * progress
    }

    override fun onBackCancelled() {
        cardContainer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
    }

    override fun onBackInvoked() {
        cardContainer.scaleX = 1f
        cardContainer.scaleY = 1f
        cardContainer.alpha = 1f
        close(true)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            val dragLayer = launcher.dragLayer
            if (dragLayer != null) {
                for (row in menuRows) {
                    if (dragLayer.isEventOverView(row, event)) {
                        row.performClick()
                        return true
                    }
                }
                if (dragLayer.isEventOverView(cardContainer, event)) {
                    return true
                }
            }
            close(true)
            return true
        }
        return true
    }

    override fun handleClose(animate: Boolean) {
        if (!isOpen) return
        isOpen = false
        mIsOpen = false
        launcher.updateDisallowBack()
        if (animate) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f)
            ObjectAnimator.ofPropertyValuesHolder(cardContainer, scaleX, scaleY, alpha).apply {
                duration = 180
                interpolator = AccelerateInterpolator()
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            launcher.dragLayer?.removeView(this@JiggleEditMenuPopup)
                            launcher.updateDisallowBack()
                        }
                    }
                )
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
            launcher.updateDisallowBack()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and (TYPE_ACTION_POPUP or TYPE_OPTIONS_POPUP)) != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dragLayer = launcher.dragLayer ?: return false
            if (!dragLayer.isEventOverView(cardContainer, ev)) {
                close(true)
                return true
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        if (ev.action == MotionEvent.ACTION_UP) {
            val dragLayer = launcher.dragLayer
            if (dragLayer != null) {
                for (row in menuRows) {
                    if (dragLayer.isEventOverView(row, ev)) {
                        row.performClick()
                        return true
                    }
                }
                if (dragLayer.isEventOverView(cardContainer, ev)) {
                    return true
                }
            }
            close(true)
            return true
        }
        return true
    }

    companion object {
        @JvmStatic
        fun showPopup(anchorView: View): JiggleEditMenuPopup {
            val launcher = Launcher.getLauncher(anchorView.context)
            AdManager.preloadInterstitial(launcher)
            AbstractFloatingView.closeOpenViews(launcher, false, TYPE_OPTIONS_POPUP)
            val popup = JiggleEditMenuPopup(anchorView.context)
            popup.show(anchorView)
            return popup
        }
    }
}
```

---

# src/com/android/launcher3/util/RemovedAppStore.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/util/RemovedAppStore.java`  
**Lines**: 81 | **Bytes**: 3497

```java
/*
 * Copyright (C) 2024 MURENA SAS
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

package com.android.launcher3.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.launcher3.LauncherPrefs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks components of applications that the user explicitly removed from the Home Screen
 * via "Remove from Home Screen". Prevents VerifyIdleAppTask from restoring them on reboot.
 */
public class RemovedAppStore {

    private static final String PREF_REMOVED_APPS = "removed_from_homescreen_apps";

    public static synchronized void markRemoved(Context context, String componentName) {
        if (context == null || componentName == null) return;
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        Set<String> current = prefs.getStringSet(PREF_REMOVED_APPS, Collections.emptySet());
        Set<String> updated = new HashSet<>(current);
        updated.add(componentName);
        prefs.edit().putStringSet(PREF_REMOVED_APPS, updated).apply();
    }

    public static synchronized boolean isRemoved(Context context, String componentName) {
        if (context == null || componentName == null) return false;
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        Set<String> current = prefs.getStringSet(PREF_REMOVED_APPS, Collections.emptySet());
        return current.contains(componentName);
    }

    public static synchronized void unmarkRemoved(Context context, String componentName) {
        if (context == null || componentName == null) return;
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        Set<String> current = prefs.getStringSet(PREF_REMOVED_APPS, Collections.emptySet());
        if (current.contains(componentName)) {
            Set<String> updated = new HashSet<>(current);
            updated.remove(componentName);
            prefs.edit().putStringSet(PREF_REMOVED_APPS, updated).apply();
        }
    }

    public static synchronized Set<String> getRemovedApps(Context context) {
        if (context == null) return Collections.emptySet();
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        return new HashSet<>(prefs.getStringSet(PREF_REMOVED_APPS, Collections.emptySet()));
    }

    public static synchronized int getRemovedCount(Context context) {
        if (context == null) return 0;
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        return prefs.getStringSet(PREF_REMOVED_APPS, Collections.emptySet()).size();
    }

    public static synchronized void clearAll(Context context) {
        if (context == null) return;
        SharedPreferences prefs = LauncherPrefs.getPrefs(context.getApplicationContext());
        prefs.edit().remove(PREF_REMOVED_APPS).apply();
    }
}
```

---

