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
        View.OnFocusChangeListener, DragListener, ExtendedEditText.OnBackKeyListener {
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
        if (mContent.getPageCount() > 1 && !mInfo.hasOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION)) {
            mFolderName.setTranslationX(0);
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
     * Returns the dynamic page-flip trigger width at the folder card edges.
     * Confined strictly within the card's outer gutter padding so it never overlaps
     * or interferes with the side column icons during rearrangement.
     */
    private float getPageFlipTriggerWidth() {
        int contentPadding = (mContent != null && mContent.getPaddingLeft() > 0)
                ? mContent.getPaddingLeft()
                : mActivityContext.getDeviceProfile().folderContentPaddingLeftRight;
        float density = getResources().getDisplayMetrics().density;
        // Keep trigger zone confined to the card gutter (up to 16dp / contentPadding)
        // so it never overlaps the side column icons or blocks cell placement.
        return Math.max(density * 10f, Math.min(contentPadding, density * 16f));
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

        float x = r[0];
        int currentPage = mContent.getNextPage();

        float cellOverlap = getPageFlipTriggerWidth();
        boolean isOutsideLeftEdge = x < cellOverlap;
        boolean isOutsideRightEdge = x > (getWidth() - cellOverlap);

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
        outRect.left -= mScrollAreaOffset;
        outRect.right += mScrollAreaOffset;
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
