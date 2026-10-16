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

package com.android.launcher3.popup;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.Utilities.squaredTouchSlop;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;
import static com.android.launcher3.popup.PopupPopulator.MAX_SHORTCUTS;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.LayoutRes;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A container for shortcuts to deep links associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public class PopupContainerWithArrow<T extends Context & ActivityContext>
        extends ArrowPopup<T> implements DragSource, DragController.DragListener {

    private final List<DeepShortcutView> mDeepShortcuts = new ArrayList<>();
    private final PointF mInterceptTouchDown = new PointF();

    private final int mStartDragThreshold;

    private static final int SHORTCUT_COLLAPSE_THRESHOLD = 100;

    private BubbleTextView mIconProxyView;
    private View mTargetProxyView;
    private View mDarkOverlayView;

    // The liquid glass view behind the popup
    private foundation.e.bliss.glass.PopupGlassView mPopupGlassView;

    private final float mShortcutHeight;

    private BubbleTextView mOriginalIcon;
    private View mTargetView;
    private int mContainerWidth;

    private ViewGroup mWidgetContainer;
    private ViewGroup mDeepShortcutContainer;
    private ViewGroup mSystemShortcutContainer;

    protected PopupItemDragHandler mPopupItemDragHandler;
    protected LauncherAccessibilityDelegate mAccessibilityDelegate;

    public PopupContainerWithArrow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStartDragThreshold = getResources().getDimensionPixelSize(
                R.dimen.deep_shortcuts_start_drag_threshold);
        mContainerWidth = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_width);
        mShortcutHeight = getResources().getDimension(R.dimen.system_shortcut_header_height);
    }

    public PopupContainerWithArrow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupContainerWithArrow(Context context) {
        this(context, null, 0);
    }

    @Override
    protected View getAccessibilityInitialFocusView() {
        if (mSystemShortcutContainer != null) {
            return mSystemShortcutContainer.getChildAt(0);
        }
        return super.getAccessibilityInitialFocusView();
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mInterceptTouchDown.set(ev.getX(), ev.getY());
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return squaredHypot(mInterceptTouchDown.x - ev.getX(), mInterceptTouchDown.y - ev.getY())
                > squaredTouchSlop(getContext());
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ACTION_POPUP) != 0;
    }

    public OnClickListener getItemClickListener() {
        return (view) -> {
            mActivityContext.getItemOnClickListener().onClick(view);
        };
    }

    public void setPopupItemDragHandler(PopupItemDragHandler popupItemDragHandler) {
        mPopupItemDragHandler = popupItemDragHandler;
    }

    public PopupItemDragHandler getItemDragHandler() {
        return mPopupItemDragHandler;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = getPopupContainer();
            if (!dl.isEventOverView(this, ev)) {
                // TODO: add WW log if want to log if tap closed deep shortcut container.
                close(true);

                // We let touches on the original icon go through so that users can launch
                // the app with one tap if they don't find a shortcut they want.
                return (mOriginalIcon != null ? !dl.isEventOverView(mOriginalIcon, ev)
                        : (mTargetView == null || !dl.isEventOverView(mTargetView, ev)));
            }
        }
        return false;
    }

    /**
     * Returns true if we can show the container.
     *
     * @deprecated Left here since some dependent projects are using this method
     */
    @Deprecated
    public static boolean canShow(View icon, ItemInfo item) {
        return icon instanceof BubbleTextView && ShortcutUtil.supportsShortcuts(item);
    }

    /**
     * Shows a popup with shortcuts associated with a Launcher icon
     * @param icon the app icon to show the popup for
     * @return the container if shown or null.
     */
    public static PopupContainerWithArrow<Launcher> showForIcon(BubbleTextView icon) {
        Launcher launcher = Launcher.getLauncher(icon.getContext());
        if (getOpen(launcher) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() && launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
            return null; // Suppress Quick Actions entirely in Jiggle Mode
        }
        if (!icon.canShowLongPressPopup()) {
            icon.clearFocus();
            return null;
        }
        ItemInfo item = (ItemInfo) icon.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return null;
        }

        PopupContainerWithArrow<Launcher> container;
        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        int deepShortcutCount = popupDataProvider.getShortcutCountForItem(item);
        List<SystemShortcut> systemShortcuts = launcher.getSupportedShortcuts()
                .map(s -> s.getShortcut(launcher, item, icon))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        container = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                R.layout.popup_container, launcher.getDragLayer(), false);
        container.configureForLauncher(launcher, item);
        container.populateAndShowRows(icon, deepShortcutCount, systemShortcuts);
        launcher.refreshAndBindWidgetsForPackageUser(PackageUserKey.fromItemInfo(item));
        launcher.getInteractionController().beginPopup(container, icon, item);
        container.requestFocus();
        return container;
    }

    public void populateAndShowRowsForView(final View targetView, List<SystemShortcut> systemShortcuts) {
        populateAndShowRowsForView(targetView, (ItemInfo) targetView.getTag(), 0, systemShortcuts);
    }

    public void populateAndShowRowsForView(final View targetView, final ItemInfo itemInfo,
            int deepShortcutCount, List<SystemShortcut> systemShortcuts) {
        mTargetView = targetView;
        if (targetView instanceof BubbleTextView) {
            mOriginalIcon = (BubbleTextView) targetView;
        } else {
            mOriginalIcon = null;
        }
        mContainerWidth = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_width);

        if (deepShortcutCount > 0) {
            addAllShortcuts(deepShortcutCount, systemShortcuts);
        } else if (!systemShortcuts.isEmpty()) {
            addSystemShortcuts(systemShortcuts,
                    R.layout.system_shortcut_rows_container,
                    R.layout.system_shortcut);
        }

        if (itemInfo != null) {
            PopupPopulator.CachedShortcutData cachedData = PopupPopulator.getCachedShortcuts(itemInfo);
            if (cachedData != null && !mDeepShortcuts.isEmpty()) {
                for (int i = 0; i < cachedData.shortcuts.size() && i < mDeepShortcuts.size(); i++) {
                    mDeepShortcuts.get(i).applyShortcutInfo(
                            cachedData.itemInfos.get(i),
                            cachedData.shortcuts.get(i),
                            this);
                }
            }
        }

        show();
        if (deepShortcutCount > 0 && itemInfo != null) {
            loadAppShortcuts(itemInfo);
        }
    }

    public static PopupContainerWithArrow<Launcher> showForSearchItem(View targetView, com.android.launcher3.model.data.AppInfo app) {
        Launcher launcher = Launcher.getLauncher(targetView.getContext());
        if (getOpen(launcher) != null) {
            return null;
        }
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()
                && launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
            return null;
        }

        com.android.launcher3.applibrary.view.SpotlightSearchOverlay spotlight =
                com.android.launcher3.applibrary.view.SpotlightSearchOverlay.getOpen(launcher);
        if (spotlight != null) {
            spotlight.hideKeyboard();
        }
        com.android.launcher3.applibrary.view.AppLibraryPage alp = launcher.getAppLibraryPage();
        if (alp != null) {
            alp.hideKeyboard();
        }

        PopupDataProvider popupDataProvider = launcher.getPopupDataProvider();
        int deepShortcutCount = popupDataProvider.getShortcutCountForItem(app);
        List<SystemShortcut> systemShortcuts = launcher.getSupportedShortcuts()
                .filter(f -> f != SystemShortcut.EDIT_HOME_SCREEN)
                .map(s -> s.getShortcut(launcher, app, targetView))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        PopupContainerWithArrow<Launcher> container = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                R.layout.popup_container, launcher.getDragLayer(), false);
        container.configureForLauncher(launcher, app);
        container.populateAndShowRowsForView(targetView, app, deepShortcutCount, systemShortcuts);
        launcher.getInteractionController().beginPopup(container, targetView, app);
        container.requestFocus();
        return container;
    }

    public static PopupContainerWithArrow<Launcher> showForFolder(com.android.launcher3.folder.FolderIcon folderIcon) {
        Launcher launcher = Launcher.getLauncher(folderIcon.getContext());
        if (getOpen(launcher) != null) {
            return null;
        }
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() && launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
            return null;
        }

        ItemInfo item = (ItemInfo) folderIcon.getTag();
        List<SystemShortcut> shortcuts = new ArrayList<>();

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, folderIcon,
                R.drawable.ic_setting, "Edit Home Screen", false, v -> {
                    if (launcher.getWorkspace() != null) {
                        launcher.getWorkspace().wobbleLayouts(true);
                    }
                }));

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, folderIcon,
                R.drawable.gm_edit_24, "Rename", false, v -> {
                    AbstractFloatingView.closeOpenViews(launcher, false, TYPE_ACTION_POPUP);
                    com.android.launcher3.folder.Folder folder = folderIcon.getFolder();
                    if (folder != null) {
                        if (!folder.isOpen() && !folder.isDestroyed()) {
                            folder.animateOpen();
                        }
                        folderIcon.postDelayed(folder::startEditingFolderName, 300);
                    }
                }));

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, folderIcon,
                R.drawable.ic_uninstall_no_shadow, "Remove Folder", true, v -> {
                    CharSequence folderTitle = (item instanceof com.android.launcher3.model.data.FolderInfo)
                            ? ((com.android.launcher3.model.data.FolderInfo) item).title : "Folder";
                    foundation.e.bliss.glass.LiquidGlassAlertView.showRemoveFolderAlert(
                            launcher,
                            folderTitle,
                            () -> {
                                if (item instanceof com.android.launcher3.model.data.FolderInfo) {
                                    com.android.launcher3.model.data.FolderInfo fi =
                                            (com.android.launcher3.model.data.FolderInfo) item;
                                    for (WorkspaceItemInfo info : fi.contents) {
                                        android.content.ComponentName cn = info.getTargetComponent();
                                        if (cn != null) {
                                            com.android.launcher3.util.RemovedAppStore.markRemoved(
                                                    launcher, cn.flattenToString());
                                        }
                                    }
                                }
                                launcher.removeItem(folderIcon, item, true /* animate */, "User removed folder");
                            },
                            () -> {
                                if (launcher.getWorkspace() != null) {
                                    launcher.getWorkspace().resetWobbleAlarm();
                                }
                            }
                    );
                }));

        PopupContainerWithArrow<Launcher> container = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                R.layout.popup_container, launcher.getDragLayer(), false);
        container.configureForLauncher(launcher, item);
        container.populateAndShowRowsForView(folderIcon, shortcuts);
        launcher.getInteractionController().beginPopup(container, folderIcon, item);
        container.requestFocus();
        return container;
    }

    public static PopupContainerWithArrow<Launcher> showForWidget(com.android.launcher3.widget.LauncherAppWidgetHostView widgetView) {
        Launcher launcher = Launcher.getLauncher(widgetView.getContext());
        if (getOpen(launcher) != null) {
            return null;
        }
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() && launcher.getWorkspace() != null && launcher.getWorkspace().isWobbling()) {
            return null;
        }

        ItemInfo item = (ItemInfo) widgetView.getTag();
        List<SystemShortcut> shortcuts = new ArrayList<>();

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, widgetView,
                R.drawable.ic_setting, "Edit Home Screen", false, v -> {
                    if (launcher.getWorkspace() != null) {
                        launcher.getWorkspace().wobbleLayouts(true);
                    }
                }));

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, widgetView,
                R.drawable.ic_widget, "Resize Widget", false, v -> {
                    com.android.launcher3.CellLayout cellLayout = (widgetView.getParent() instanceof com.android.launcher3.ShortcutAndWidgetContainer)
                            ? (com.android.launcher3.CellLayout) widgetView.getParent().getParent() : null;
                    if (cellLayout != null) {
                        com.android.launcher3.AppWidgetResizeFrame.showForWidget(widgetView, cellLayout);
                    }
                }));

        shortcuts.add(new SystemShortcut.CustomShortcut(launcher, item, widgetView,
                R.drawable.ic_uninstall_no_shadow, "Remove Widget", true, v -> {
                    widgetView.showRemoveWidgetDialog();
                }));

        PopupContainerWithArrow<Launcher> container = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(
                R.layout.popup_container, launcher.getDragLayer(), false);
        container.configureForLauncher(launcher, item);
        container.populateAndShowRowsForView(widgetView, shortcuts);
        launcher.getInteractionController().beginPopup(container, widgetView, item);
        container.requestFocus();
        return container;
    }

    private void configureForLauncher(Launcher launcher, ItemInfo itemInfo) {
        addOnAttachStateChangeListener(new LauncherPopupLiveUpdateHandler(
                launcher, (PopupContainerWithArrow<Launcher>) this));
        if (!Flags.privateSpaceRestrictItemDrag()
                || !(itemInfo instanceof ItemInfoWithIcon itemInfoWithIcon)
                || (itemInfoWithIcon.runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0) {
            mPopupItemDragHandler = new LauncherPopupItemDragHandler(launcher, this);
        }
        mAccessibilityDelegate = new ShortcutMenuAccessibilityDelegate(launcher);
        launcher.getDragController().addDragListener(this);
    }

    /**
     * Populate and show shortcuts for the Launcher U app shortcut design.
     * Will inflate the container and shortcut View instances for the popup container.
     * @param originalIcon App icon that the popup is shown for
     * @param deepShortcutCount Number of DeepShortcutView instances to add to container
     * @param systemShortcuts List of SystemShortcuts to add to container
     */
    public void populateAndShowRows(final BubbleTextView originalIcon,
            int deepShortcutCount, List<SystemShortcut> systemShortcuts) {

        mOriginalIcon = originalIcon;
        mTargetView = originalIcon;
        mContainerWidth = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_width);

        if (deepShortcutCount > 0) {
            addAllShortcuts(deepShortcutCount, systemShortcuts);
        } else if (!systemShortcuts.isEmpty()) {
            addSystemShortcuts(systemShortcuts,
                    R.layout.system_shortcut_rows_container,
                    R.layout.system_shortcut);
        }

        ItemInfo originalItemInfo = (ItemInfo) originalIcon.getTag();
        PopupPopulator.CachedShortcutData cachedData = PopupPopulator.getCachedShortcuts(originalItemInfo);
        if (cachedData != null && !mDeepShortcuts.isEmpty()) {
            for (int i = 0; i < cachedData.shortcuts.size() && i < mDeepShortcuts.size(); i++) {
                mDeepShortcuts.get(i).applyShortcutInfo(
                        cachedData.itemInfos.get(i),
                        cachedData.shortcuts.get(i),
                        this);
            }
        }

        show();
        loadAppShortcuts(originalItemInfo);
    }

    /**
     * Animates and loads shortcuts on background thread for this popup container
     */
    private void loadAppShortcuts(ItemInfo originalItemInfo) {
        setAccessibilityPaneTitle(getTitleForAccessibility());
        if (mOriginalIcon != null) {
            mOriginalIcon.setForceHideDot(true);
        }
        // All views are added. Animate layout from now on.
        setLayoutTransition(new LayoutTransition());
        // Load the shortcuts on a background thread and update the container as it animates.
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                mActivityContext, originalItemInfo, new Handler(Looper.getMainLooper()),
                this, mDeepShortcuts));
    }

    /**
     * Adds any Deep Shortcuts, System Shortcuts and the Widget Shortcut to their respective
     * containers
     * @param deepShortcutCount number of DeepShortcutView instances
     * @param systemShortcuts List of SystemShortcuts
     */
    private void addAllShortcuts(int deepShortcutCount,
            List<SystemShortcut> systemShortcuts) {
        if (deepShortcutCount + systemShortcuts.size() <= SHORTCUT_COLLAPSE_THRESHOLD) {
            // add deep shortcuts first so they appear at the top
            addDeepShortcuts(deepShortcutCount, 0);
            float currentHeight = (mShortcutHeight * deepShortcutCount)
                    + mChildContainerMargin;
            // add all system shortcuts including widgets shortcut below deep shortcuts
            addSystemShortcuts(systemShortcuts,
                    R.layout.system_shortcut_rows_container,
                    R.layout.system_shortcut);
            return;
        }

        // add deep shortcuts first
        addDeepShortcuts(deepShortcutCount, 0);
        float currentHeight = (mShortcutHeight * deepShortcutCount) + mChildContainerMargin;

        List<SystemShortcut> nonWidgetSystemShortcuts =
                getNonWidgetSystemShortcuts(systemShortcuts);
        // If total shortcuts over threshold, collapse system shortcuts to single row
        addSystemShortcutsIconsOnly(nonWidgetSystemShortcuts);
        // May need to recalculate row width
        mContainerWidth = Math.max(mContainerWidth,
                nonWidgetSystemShortcuts.size() * getResources()
                        .getDimensionPixelSize(R.dimen.system_shortcut_header_icon_touch_size));
        // Add widget shortcut to separate container
        Optional<SystemShortcut.Widgets> widgetShortcutOpt = getWidgetShortcut(systemShortcuts);
        if (widgetShortcutOpt.isPresent()) {
            mWidgetContainer = inflateAndAdd(R.layout.widget_shortcut_container_material_u,
                    this);
            initializeWidgetShortcut(mWidgetContainer, widgetShortcutOpt.get());
        }
    }

    /**
     * Finds the first instance of the Widgets Shortcut from the SystemShortcut List
     * @param systemShortcuts List of SystemShortcut instances to search
     * @return Optional Widgets SystemShortcut
     */
    private static Optional<SystemShortcut.Widgets> getWidgetShortcut(
            List<SystemShortcut> systemShortcuts) {
        return systemShortcuts
                .stream()
                .filter(shortcut -> shortcut instanceof SystemShortcut.Widgets)
                .map(SystemShortcut.Widgets.class::cast)
                .findFirst();
    }

    /**
     * Returns list of [systemShortcuts] without the Widgets shortcut instance if found
     * @param systemShortcuts list of SystemShortcuts to filter from
     * @return systemShortcuts without the Widgets Shortcut
     */
    private static List<SystemShortcut> getNonWidgetSystemShortcuts(
            List<SystemShortcut> systemShortcuts) {

        return systemShortcuts
                .stream()
                .filter(shortcut -> !(shortcut instanceof SystemShortcut.Widgets))
                .collect(Collectors.toList());
    }

    /**
     * Inflates the given systemShortcutContainerLayout as a container, and populates with
     * the systemShortcuts as views using the systemShortcutLayout
     * @param systemShortcuts List of SystemShortcut to inflate as Views
     * @param systemShortcutContainerLayout Layout Resource for the Container of shortcut Views
     * @param systemShortcutLayout Layout Resource for the individual shortcut Views
     */
    private void addSystemShortcuts(List<SystemShortcut> systemShortcuts,
            @LayoutRes int systemShortcutContainerLayout, @LayoutRes int systemShortcutLayout) {

        if (systemShortcuts.size() == 0) {
            return;
        }
        mSystemShortcutContainer = inflateAndAdd(systemShortcutContainerLayout, this);
        mWidgetContainer = mSystemShortcutContainer;
        for (int i = 0; i < systemShortcuts.size(); i++) {
            initializeSystemShortcut(
                    systemShortcutLayout,
                    mSystemShortcutContainer,
                    systemShortcuts.get(i),
                    i < systemShortcuts.size() - 1);
        }
    }

    private void addSystemShortcutsIconsOnly(List<SystemShortcut> systemShortcuts) {
        if (systemShortcuts.size() == 0) {
            return;
        }

        mSystemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_icons_container, this);

        for (int i = 0; i < systemShortcuts.size(); i++) {
            @LayoutRes int shortcutIconLayout = R.layout.system_shortcut_icon_only;
            boolean shouldAppendSpacer = true;

            if (i == 0) {
                shortcutIconLayout = R.layout.system_shortcut_icon_only_start;
            } else if (i == systemShortcuts.size() - 1) {
                shortcutIconLayout = R.layout.system_shortcut_icon_only_end;
                shouldAppendSpacer = false;
            }
            initializeSystemShortcut(
                    shortcutIconLayout,
                    mSystemShortcutContainer,
                    systemShortcuts.get(i),
                    shouldAppendSpacer);
        }
    }

    /**
     * Inflates and adds [deepShortcutCount] number of DeepShortcutView for the  to a new container
     * @param deepShortcutCount number of DeepShortcutView instances to add
     * @param currentHeight height of popup before adding deep shortcuts
     */
    private void addDeepShortcuts(int deepShortcutCount, float currentHeight) {
        mDeepShortcutContainer = inflateAndAdd(R.layout.deep_shortcut_container, this);
        for (int i = deepShortcutCount; i > 0; i--) {
            currentHeight += mShortcutHeight;
            // when there is limited vertical screen space, limit total popup rows to fit
            if (currentHeight >= mActivityContext.getDeviceProfile().availableHeightPx) break;
            DeepShortcutView v = inflateAndAdd(R.layout.deep_shortcut,
                    mDeepShortcutContainer);
            v.getLayoutParams().width = mContainerWidth;
            mDeepShortcuts.add(v);
        }
        updateHiddenShortcuts();
    }

    protected BubbleTextView getOriginalIcon() {
        return mOriginalIcon;
    }

    protected ViewGroup getSystemShortcutContainer() {
        return mSystemShortcutContainer;
    }

    protected ViewGroup getWidgetContainer() {
        return mWidgetContainer;
    }

    protected void setWidgetContainer(ViewGroup widgetContainer) {
        mWidgetContainer = widgetContainer;
    }

    private String getTitleForAccessibility() {
        return getContext().getString(R.string.action_deep_shortcut);
    }

    @Override
    protected boolean shouldAddArrow() {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            return false;
        }
        return super.shouldAddArrow();
    }

    @Override
    protected void getTargetObjectLocation(Rect outPos) {
        if (mTargetView != null && !(mTargetView instanceof BubbleTextView)) {
            getPopupContainer().getDescendantRectRelativeToSelf(mTargetView, outPos);
            return;
        }
        if (mOriginalIcon != null) {
            getPopupContainer().getDescendantRectRelativeToSelf(mOriginalIcon, outPos);
            outPos.top += mOriginalIcon.getPaddingTop();
            outPos.left += mOriginalIcon.getPaddingLeft();
            outPos.right -= mOriginalIcon.getPaddingRight();
            outPos.bottom = outPos.top + (mOriginalIcon.getIcon() != null
                    ? mOriginalIcon.getIcon().getBounds().height()
                    : mOriginalIcon.getHeight());
        }
    }

    protected void updateHiddenShortcuts() {
        int total = mDeepShortcuts.size();
        for (int i = 0; i < total; i++) {
            DeepShortcutView view = mDeepShortcuts.get(i);
            view.setVisibility(i >= MAX_SHORTCUTS ? GONE : VISIBLE);
        }
    }

    private static final int IOS_DESTRUCTIVE_RED = 0xFFFF3B30;

    private void applyiOSStyleToShortcuts(android.view.View view, boolean isRoot) {
        if (view == null) return;
        
        if (view.getId() == R.id.icon) {
            // Do NOT remove background from the icon view: the shortcut icon is set as its background!
            android.view.ViewParent p = view.getParent();
            boolean isDeepShortcut = (p instanceof android.view.View
                    && ((android.view.View) p).getId() == R.id.deep_shortcut_material);
            if (!isDeepShortcut) {
                // Check if sibling text is destructive red
                boolean isDestructive = false;
                if (p instanceof android.view.ViewGroup) {
                    android.view.View text = ((android.view.ViewGroup) p).findViewById(R.id.bubble_text);
                    if (text instanceof android.widget.TextView) {
                        int color = ((android.widget.TextView) text).getCurrentTextColor();
                        if (color == IOS_DESTRUCTIVE_RED) {
                            isDestructive = true;
                        }
                    }
                }
                int tintColor = isDestructive
                        ? IOS_DESTRUCTIVE_RED
                        : android.graphics.Color.BLACK;
                view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tintColor));
            }
        } else {
            // Strip backgrounds from shortcut containers and rows so the liquid glass shines through
            view.setBackground(null);
        }
        
        // Force text color to black for contrast against white glass, preserving destructive red
        if (view instanceof android.widget.TextView) {
            int currentColor = ((android.widget.TextView) view).getCurrentTextColor();
            if (currentColor != IOS_DESTRUCTIVE_RED) {
                ((android.widget.TextView) view).setTextColor(android.graphics.Color.BLACK);
            }
        }
        
        // Force icon tint to black for ImageViews (e.g. collapsed icon headers)
        if (view instanceof android.widget.ImageView) {
            ((android.widget.ImageView) view).setImageTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
        }
        
        // Recurse for view groups
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyiOSStyleToShortcuts(vg.getChildAt(i), false);
            }
        }
    }

    @Override
    protected void assignMarginsAndBackgrounds(ViewGroup viewGroup, int backgroundColor) {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            int count = viewGroup.getChildCount();
            View lastView = null;
            for (int i = 0; i < count; i++) {
                View view = viewGroup.getChildAt(i);
                if (view.getVisibility() == VISIBLE) {
                    if (lastView != null && isShortcutContainer(lastView)) {
                        MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                        mlp.bottomMargin = mChildContainerMargin;
                    }
                    lastView = view;
                    MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                    mlp.bottomMargin = 0;
                    if (isShortcutOrWrapper(view) || isShortcutContainer(view)) {
                        applyiOSStyleToShortcuts(view, true);
                    }
                }
            }
            measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        } else {
            super.assignMarginsAndBackgrounds(viewGroup, backgroundColor);
        }
    }

    @Override
    protected void setupForDisplay() {
        super.setupForDisplay();

        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            // Remove popup background so we only see the glass
            setBackground(null);
            setClipToOutline(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                private final foundation.e.bliss.graphics.ContinuousCornerGeometry mGeometry =
                        new foundation.e.bliss.graphics.ContinuousCornerGeometry();

                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                    try {
                        android.graphics.Path path = mGeometry.getPath(
                                (float) view.getWidth(),
                                (float) view.getHeight(),
                                mOutlineRadius,
                                foundation.e.bliss.graphics.GlassShapeSpec.IOS_SMOOTHING);
                        outline.setConvexPath(path);
                    } catch (Exception e) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadius);
                    }
                }
            });
            
            // Create and add dark overlay
            mDarkOverlayView = new DarkOverlayView(getContext());
            mDarkOverlayView.setAlpha(0f);
            com.android.launcher3.views.BaseDragLayer.LayoutParams overlayLp =
                    new com.android.launcher3.views.BaseDragLayer.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            overlayLp.ignoreInsets = true;
            getPopupContainer().addView(mDarkOverlayView, getPopupContainer().indexOfChild(this), overlayLp);

            // Create and add proxy icon
            if (mOriginalIcon != null) {
                mIconProxyView = (BubbleTextView) mActivityContext.getLayoutInflater().inflate(R.layout.app_icon, getPopupContainer(), false);
                
                com.android.launcher3.model.data.ItemInfo info = (com.android.launcher3.model.data.ItemInfo) mOriginalIcon.getTag();
                if (info instanceof com.android.launcher3.model.data.WorkspaceItemInfo) {
                    mIconProxyView.applyFromWorkspaceItem((com.android.launcher3.model.data.WorkspaceItemInfo) info);
                } else if (info instanceof com.android.launcher3.model.data.AppInfo) {
                    mIconProxyView.applyFromApplicationInfo((com.android.launcher3.model.data.AppInfo) info);
                }
                mIconProxyView.setIconVisible(true);
                mIconProxyView.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
                mIconProxyView.setForceHideDot(true);
                mIconProxyView.setTextColor(mOriginalIcon.getTextColors());
                mIconProxyView.setPadding(mOriginalIcon.getPaddingLeft(), mOriginalIcon.getPaddingTop(), mOriginalIcon.getPaddingRight(), mOriginalIcon.getPaddingBottom());
                
                com.android.launcher3.views.BaseDragLayer.LayoutParams proxyLp =
                        new com.android.launcher3.views.BaseDragLayer.LayoutParams(mOriginalIcon.getWidth(), mOriginalIcon.getHeight());
                proxyLp.customPosition = true;
                
                android.graphics.Rect pos = new android.graphics.Rect();
                getPopupContainer().getDescendantRectRelativeToSelf(mOriginalIcon, pos);
                proxyLp.x = pos.left;
                proxyLp.y = pos.top;
                
                getPopupContainer().addView(mIconProxyView, getPopupContainer().indexOfChild(this), proxyLp);
                
                mOriginalIcon.setVisibility(INVISIBLE);
            } else if (mTargetView instanceof com.android.launcher3.widget.LauncherAppWidgetHostView) {
                // For widgets, keep the live widget visible on Workspace and cut out its rect in mDarkOverlayView.
                // This keeps the widget 100% visible, fully interactive, undarkened, and avoids RemoteViews/HWUI recording issues.
                mTargetView.setVisibility(VISIBLE);
                android.graphics.Rect pos = new android.graphics.Rect();
                getPopupContainer().getDescendantRectRelativeToSelf(mTargetView, pos);
                float radius;
                if (mTargetView instanceof foundation.e.bliss.widgets.clock.ClockWidgetHostView clockView) {
                    android.graphics.Rect squircle = new android.graphics.Rect();
                    clockView.getSquircleBoundsInSelf(squircle);
                    pos.set(
                        pos.left + squircle.left,
                        pos.top + squircle.top,
                        pos.left + squircle.right,
                        pos.top + squircle.bottom
                    );
                    radius = clockView.getSquircleRadius();
                } else {
                    try {
                        int resId = android.content.res.Resources.getSystem().getIdentifier("system_app_widget_background_radius", "dimen", "android");
                        radius = resId > 0 ? getContext().getResources().getDimension(resId) : getContext().getResources().getDimension(R.dimen.default_widget_corner_radius);
                    } catch (Exception e) {
                        radius = getContext().getResources().getDimension(R.dimen.default_widget_corner_radius);
                    }
                }
                if (mDarkOverlayView instanceof DarkOverlayView) {
                    ((DarkOverlayView) mDarkOverlayView).setCutout(new android.graphics.RectF(pos), radius);
                }
                mTargetProxyView = null;
            } else if (mTargetView != null && mTargetView.getWidth() > 0 && mTargetView.getHeight() > 0) {
                final View target = mTargetView;
                
                if (target instanceof android.widget.ImageView) {
                    android.widget.ImageView srcIv = (android.widget.ImageView) target;
                    android.widget.ImageView iv = new android.widget.ImageView(getContext());
                    android.graphics.drawable.Drawable d = srcIv.getDrawable();
                    if (d != null && d.getConstantState() != null) {
                        iv.setImageDrawable(d.getConstantState().newDrawable());
                    } else {
                        iv.setImageDrawable(d);
                    }
                    iv.setScaleType(srcIv.getScaleType());
                    iv.setPadding(srcIv.getPaddingLeft(), srcIv.getPaddingTop(), srcIv.getPaddingRight(), srcIv.getPaddingBottom());
                    mTargetProxyView = iv;
                } else {
                    int prevVis = target.getVisibility();
                    target.setVisibility(VISIBLE);
                    try {
                        final android.graphics.RenderNode renderNode = new android.graphics.RenderNode("ProxyView");
                        renderNode.setPosition(0, 0, target.getWidth(), target.getHeight());
                        android.graphics.RecordingCanvas recordingCanvas = renderNode.beginRecording(target.getWidth(), target.getHeight());
                        target.draw(recordingCanvas);
                        renderNode.endRecording();

                        mTargetProxyView = new View(getContext()) {
                            @Override
                            protected void onDraw(android.graphics.Canvas canvas) {
                                if (canvas.isHardwareAccelerated()) {
                                    canvas.drawRenderNode(renderNode);
                                }
                            }
                        };
                    } catch (Throwable t) {
                        try {
                            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(
                                    target.getWidth(), target.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas c = new android.graphics.Canvas(b);
                            target.draw(c);

                            android.widget.ImageView iv = new android.widget.ImageView(getContext());
                            iv.setImageBitmap(b);
                            mTargetProxyView = iv;
                        } catch (Throwable ignored) {}
                    }
                }

                if (mTargetProxyView != null) {
                    com.android.launcher3.views.BaseDragLayer.LayoutParams proxyLp =
                            new com.android.launcher3.views.BaseDragLayer.LayoutParams(target.getWidth(), target.getHeight());
                    proxyLp.customPosition = true;

                    android.graphics.Rect pos = new android.graphics.Rect();
                    getPopupContainer().getDescendantRectRelativeToSelf(target, pos);
                    proxyLp.x = pos.left;
                    proxyLp.y = pos.top;

                    getPopupContainer().addView(mTargetProxyView, getPopupContainer().indexOfChild(this), proxyLp);

                    target.setVisibility(INVISIBLE);
                }
            }

            // Add glass view
            mPopupGlassView = new foundation.e.bliss.glass.PopupGlassView(getContext());
            mPopupGlassView.setPopupRadius(mOutlineRadius);

            // If the icon is inside Spotlight, App Library search, or an expanded category, sample that container
            // so the list of apps behind the popup shines through the liquid glass.
            View spotlightOverlay = getSpotlightSearchOverlay(mTargetView != null ? mTargetView : mOriginalIcon);
            View alpSearch = getAppLibrarySearchBackdrop(mTargetView != null ? mTargetView : mOriginalIcon);
            View expandedCategory = getAppLibraryExpandedCategory(mTargetView != null ? mTargetView : mOriginalIcon);
            if (spotlightOverlay != null) {
                mPopupGlassView.setBackdropSource(spotlightOverlay);
            } else if (alpSearch != null) {
                mPopupGlassView.setBackdropSource(alpSearch);
            } else if (expandedCategory != null) {
                mPopupGlassView.setBackdropSource(expandedCategory);
            } else {
                View openFolder = getOpenFolder(mOriginalIcon != null ? mOriginalIcon : mTargetView);
                if (openFolder != null) {
                    mPopupGlassView.setBackdropSource(openFolder);
                }
            }

            // Insert it just below this popup in the drag layer
            getPopupContainer().addView(mPopupGlassView, getPopupContainer().indexOfChild(this));
            
            // Sync initial state
            syncGlassBounds();
            mPopupGlassView.setAlpha(getAlpha());
            mPopupGlassView.setScaleX(1.0f);
            mPopupGlassView.setScaleY(1.0f);
            updateGlassAnimatedShape();
        }
    }

    private View getSpotlightSearchOverlay(View icon) {
        if (icon == null) return null;
        android.view.ViewParent p = icon.getParent();
        while (p != null) {
            if (p instanceof com.android.launcher3.applibrary.view.SpotlightSearchOverlay) {
                return (View) p;
            }
            p = p.getParent();
        }
        return null;
    }

    private View getAppLibrarySearchBackdrop(View icon) {
        if (icon == null) return null;
        android.view.ViewParent p = icon.getParent();
        while (p != null) {
            if (p instanceof com.android.launcher3.applibrary.view.AppLibraryPage) {
                com.android.launcher3.applibrary.view.AppLibraryPage alp =
                        (com.android.launcher3.applibrary.view.AppLibraryPage) p;
                if (alp.isInSearchMode()) {
                    return alp;
                }
            }
            p = p.getParent();
        }
        return null;
    }

    private View getOpenFolder(View icon) {
        if (icon == null) return null;
        android.view.ViewParent p = icon.getParent();
        while (p != null) {
            if (p instanceof com.android.launcher3.folder.Folder) {
                return (View) p;
            }
            p = p.getParent();
        }
        return null;
    }

    private View getAppLibraryExpandedCategory(View icon) {
        if (icon != null) {
            android.view.ViewParent p = icon.getParent();
            while (p != null) {
                if (p instanceof com.android.launcher3.applibrary.view.AppLibraryCategoryExpandedView) {
                    return (View) p;
                }
                p = p.getParent();
            }
        }
        if (mActivityContext instanceof Launcher) {
            return com.android.launcher3.applibrary.view.AppLibraryCategoryExpandedView.getOpen((Launcher) mActivityContext);
        }
        try {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null) {
                return com.android.launcher3.applibrary.view.AppLibraryCategoryExpandedView.getOpen(launcher);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateGlassAnimatedShape() {
        if (mPopupGlassView != null) {
            float sx = getScaleX();
            float sy = getScaleY();
            if (Math.abs(sx - 1.0f) < 0.001f && Math.abs(sy - 1.0f) < 0.001f) {
                mPopupGlassView.setAnimatedShape(null, mOutlineRadius);
            } else {
                float px = getPivotX();
                float py = getPivotY();
                float w = getWidth();
                float h = getHeight();
                if (w > 0 && h > 0) {
                    float left = px * (1.0f - sx);
                    float top = py * (1.0f - sy);
                    float right = left + w * sx;
                    float bottom = top + h * sy;
                    mPopupGlassView.setAnimatedShape(new android.graphics.RectF(left, top, right, bottom), mOutlineRadius);
                }
            }
        }
    }

    private void syncGlassBounds() {
        if (mPopupGlassView != null) {
            int w = getWidth();
            int h = getHeight();
            int l = getLeft();
            int t = getTop();
            ViewGroup.LayoutParams existingLp = mPopupGlassView.getLayoutParams();
            com.android.launcher3.views.BaseDragLayer.LayoutParams lp;
            boolean boundsChanged = false;
            if (existingLp instanceof com.android.launcher3.views.BaseDragLayer.LayoutParams) {
                lp = (com.android.launcher3.views.BaseDragLayer.LayoutParams) existingLp;
                if (!lp.customPosition || lp.width != w || lp.height != h || lp.x != l || lp.y != t) {
                    lp.customPosition = true;
                    lp.width = w;
                    lp.height = h;
                    lp.x = l;
                    lp.y = t;
                    boundsChanged = true;
                }
            } else {
                lp = new com.android.launcher3.views.BaseDragLayer.LayoutParams(w, h);
                lp.customPosition = true;
                lp.x = l;
                lp.y = t;
                boundsChanged = true;
            }

            ViewGroup.LayoutParams thisLp = getLayoutParams();
            if (thisLp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) thisLp;
                if (lp.leftMargin != mlp.leftMargin || lp.topMargin != mlp.topMargin
                        || lp.rightMargin != mlp.rightMargin || lp.bottomMargin != mlp.bottomMargin) {
                    lp.leftMargin = mlp.leftMargin;
                    lp.topMargin = mlp.topMargin;
                    lp.rightMargin = mlp.rightMargin;
                    lp.bottomMargin = mlp.bottomMargin;
                    boundsChanged = true;
                }
            }
            if (thisLp instanceof android.widget.FrameLayout.LayoutParams) {
                int gravity = ((android.widget.FrameLayout.LayoutParams) thisLp).gravity;
                if (lp.gravity != gravity) {
                    lp.gravity = gravity;
                    boundsChanged = true;
                }
            }

            if (boundsChanged || mPopupGlassView.getLayoutParams() != lp) {
                mPopupGlassView.setLayoutParams(lp);
            }

            // Force immediate layout if bounds changed or not yet laid out
            if (boundsChanged || mPopupGlassView.getWidth() != w || mPopupGlassView.getHeight() != h
                    || mPopupGlassView.getLeft() != l || mPopupGlassView.getTop() != t) {
                mPopupGlassView.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                );
                mPopupGlassView.layout(l, t, getRight(), getBottom());
            }
            
            // Catch any translations that might have been applied before creation
            mPopupGlassView.setTranslationX(getTranslationX());
            mPopupGlassView.setTranslationY(getTranslationY());
            updateGlassAnimatedShape();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        syncGlassBounds();
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (mPopupGlassView != null) {
            mPopupGlassView.setAlpha(alpha);
        }
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        updateGlassAnimatedShape();
    }

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        updateGlassAnimatedShape();
    }

    @Override
    public void setPivotX(float pivotX) {
        super.setPivotX(pivotX);
        updateGlassAnimatedShape();
    }

    @Override
    public void setPivotY(float pivotY) {
        super.setPivotY(pivotY);
        updateGlassAnimatedShape();
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        if (mPopupGlassView != null) {
            mPopupGlassView.setTranslationX(translationX);
            mPopupGlassView.invalidate();
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (mPopupGlassView != null) {
            mPopupGlassView.setTranslationY(translationY);
            mPopupGlassView.invalidate();
        }
    }

    protected void initializeWidgetShortcut(ViewGroup container, SystemShortcut info) {
        View view = initializeSystemShortcut(R.layout.system_shortcut, container, info, false);
        view.getLayoutParams().width = mContainerWidth;
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            if (container != null) {
                container.setBackground(null);
                applyiOSStyleToShortcuts(container, true);
            } else {
                applyiOSStyleToShortcuts(view, true);
            }
        }
    }

    /**
     * Initializes and adds View for given SystemShortcut to a container.
     * @param resId Resource id to use for SystemShortcut View.
     * @param container ViewGroup to add the shortcut View to as a parent
     * @param info The SystemShortcut instance to create a View for.
     * @param shouldAppendSpacer If True, will add a spacer after the shortcut, when showing the
     *                        SystemShortcut as an icon only. Used to space the shortcut icons
     *                        evenly.
     * @return The view inflated for the SystemShortcut
     */
    protected View initializeSystemShortcut(int resId, ViewGroup container, SystemShortcut info,
            boolean shouldAppendSpacer) {
        View view = inflateAndAdd(resId, container);
        if (view instanceof DeepShortcutView) {
            // System shortcut takes entire row with icon and text
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            info.setIconAndLabelFor(shortcutView.getIconView(), shortcutView.getBubbleText());
        } else if (view instanceof ImageView) {
            // System shortcut is just an icon
            info.setIconAndContentDescriptionFor((ImageView) view);
            if (shouldAppendSpacer) inflateAndAdd(R.layout.system_shortcut_spacer, container);
            view.setTooltipText(view.getContentDescription());
        }
        view.setTag(info);
        view.setOnClickListener(info);
        return view;
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    public DragOptions.PreDragCondition createPreDragCondition(boolean updateIconUi) {
        return new DragOptions.PreDragCondition() {

            @Override
            public boolean shouldStartDrag(double distanceDragged) {
                return distanceDragged > mStartDragThreshold;
            }

            @Override
            public void onPreDragStart(DropTarget.DragObject dragObject) {
                if (!updateIconUi) {
                    return;
                }
                if (mOriginalIcon != null) {
                    if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                        mOriginalIcon.setVisibility(INVISIBLE);
                        return;
                    }
                    if (mIsAboveIcon) {
                        // Hide only the icon, keep the text visible.
                        mOriginalIcon.setIconVisible(false);
                        mOriginalIcon.setVisibility(VISIBLE);
                    } else {
                        // Hide both the icon and text.
                        mOriginalIcon.setVisibility(INVISIBLE);
                    }
                } else if (mTargetView != null) {
                    if (!(mTargetView instanceof com.android.launcher3.widget.LauncherAppWidgetHostView)) {
                        mTargetView.setVisibility(INVISIBLE);
                    }
                }
            }

            @Override
            public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                mIsOriginalViewDragged = dragStarted;
                if (!updateIconUi) {
                    return;
                }
                if (mOriginalIcon != null) {
                    mOriginalIcon.setIconVisible(true);
                    if (dragStarted) {
                        // Make sure we keep the original icon hidden while it is being dragged.
                        mOriginalIcon.setVisibility(INVISIBLE);
                    } else {
                        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                            if (mIconProxyView != null) {
                                mOriginalIcon.setVisibility(INVISIBLE);
                            } else {
                                mOriginalIcon.setVisibility(VISIBLE);
                            }
                            return;
                        }
                        // TODO: add WW logging if want to add logging for long press on popup
                        //  container.
                        //  mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mOriginalIcon);
                        if (!mIsAboveIcon) {
                            // Show the icon but keep the text hidden.
                            mOriginalIcon.setVisibility(VISIBLE);
                            mOriginalIcon.setTextVisibility(false);
                        }
                    }
                } else if (mTargetView != null) {
                    if (dragStarted) {
                        if (dragObject != null && dragObject.dragView != null
                                && dragObject.dragView.getContentView() == mTargetView) {
                            mTargetView.setVisibility(VISIBLE);
                        } else {
                            mTargetView.setVisibility(INVISIBLE);
                        }
                    } else {
                        if (dragObject != null && dragObject.dragView != null
                                && dragObject.dragView.getContentViewParent() != null) {
                            dragObject.dragView.detachContentView(/* reattachToPreviousParent= */ true);
                        }
                        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                            if (mTargetProxyView != null) {
                                mTargetView.setVisibility(INVISIBLE);
                            } else {
                                mTargetView.setVisibility(VISIBLE);
                            }
                            return;
                        }
                        mTargetView.setVisibility(VISIBLE);
                    }
                }
            }
        };
    }

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) {  }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        mDeferContainerRemoval = true;
        teardownVisuals(); // force-remove mIconProxyView/mDarkOverlayView/mPopupGlassView right now
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            close(false);
        } else {
            animateClose();
        }
    }

    @Override
    public void onDragEnd() {
        if (!mIsOpen) {
            if (mOpenCloseAnimator != null) {
                // Close animation is running.
                mDeferContainerRemoval = false;
            } else {
                // Close animation is not running.
                if (mDeferContainerRemoval) {
                    closeComplete();
                }
            }
        }
    }

    @Override
    protected void onCreateOpenAnimation(AnimatorSet anim) {
        super.onCreateOpenAnimation(anim);
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            if (mActivityContext instanceof com.android.launcher3.Launcher) {
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) mActivityContext;
                if (launcher.mBlurLayer != null) {
                    anim.play(android.animation.ObjectAnimator.ofFloat(launcher.mBlurLayer, View.ALPHA, launcher.mBlurLayer.getAlpha(), 1f));
                }
            }
            if (mDarkOverlayView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mDarkOverlayView, View.ALPHA, 0f, 0.4f));
            }
            if (mIconProxyView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mIconProxyView, View.SCALE_X, 1f, 1.10f).setDuration(260));
                anim.play(android.animation.ObjectAnimator.ofFloat(mIconProxyView, View.SCALE_Y, 1f, 1.10f).setDuration(260));
            } else if (mTargetProxyView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mTargetProxyView, View.SCALE_X, 1f, 1.10f).setDuration(260));
                anim.play(android.animation.ObjectAnimator.ofFloat(mTargetProxyView, View.SCALE_Y, 1f, 1.10f).setDuration(260));
            }
        }
    }

    @Override
    protected void onCreateCloseAnimation(AnimatorSet anim) {
        super.onCreateCloseAnimation(anim);
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            if (mActivityContext instanceof com.android.launcher3.Launcher) {
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) mActivityContext;
                if (launcher.mBlurLayer != null) {
                    com.android.launcher3.Workspace<?> ws = launcher.getWorkspace();
                    boolean keepBlur = (ws != null && (ws.isAppLibraryCurrentPage()
                            || (ws.getFirstPagePinnedItem() != null && ws.getNextPage() == 0)))
                            || com.android.launcher3.folder.Folder.getOpen(launcher) != null;
                    float targetAlpha = keepBlur
                            ? 1f
                            : launcher.getStateManager().getState().getWorkspaceBackgroundAlpha(launcher);
                    anim.play(android.animation.ObjectAnimator.ofFloat(
                            launcher.mBlurLayer, View.ALPHA, launcher.mBlurLayer.getAlpha(), targetAlpha).setDuration(180));
                }
            }
            if (mDarkOverlayView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mDarkOverlayView, View.ALPHA, mDarkOverlayView.getAlpha(), 0f).setDuration(180));
            }
            if (mIconProxyView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mIconProxyView, View.SCALE_X, mIconProxyView.getScaleX(), 1f).setDuration(180));
                anim.play(android.animation.ObjectAnimator.ofFloat(mIconProxyView, View.SCALE_Y, mIconProxyView.getScaleY(), 1f).setDuration(180));
            } else if (mTargetProxyView != null) {
                anim.play(android.animation.ObjectAnimator.ofFloat(mTargetProxyView, View.SCALE_X, mTargetProxyView.getScaleX(), 1f).setDuration(180));
                anim.play(android.animation.ObjectAnimator.ofFloat(mTargetProxyView, View.SCALE_Y, mTargetProxyView.getScaleY(), 1f).setDuration(180));
            }
        }
    }

    @Override
    public void onBackProgressed(@androidx.annotation.NonNull android.window.BackEvent backEvent) {
        super.onBackProgressed(backEvent);
        if (mDarkOverlayView != null) {
            float progress = backEvent.getProgress();
            mDarkOverlayView.setAlpha(0.4f * (1f - 0.4f * progress));
        }
    }

    @Override
    public void onBackCancelled() {
        super.onBackCancelled();
        if (mDarkOverlayView != null) {
            mDarkOverlayView.animate().alpha(0.4f).setDuration(150).start();
        }
    }

    private boolean mCancelledForDrag = false;
    private boolean mIsOriginalViewDragged = false;

    public void cancelForDrag() {
        if (mCancelledForDrag) return;
        mCancelledForDrag = true;
        android.util.Log.d("DragDebug", "PopupContainerWithArrow.cancelForDrag() invoked for item: " + (mOriginalIcon != null && mOriginalIcon.getTag() instanceof ItemInfo ? ((ItemInfo) mOriginalIcon.getTag()).title : "null"));
        teardownVisuals();
        destroy();
    }

    private void teardownVisuals() {
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            mOpenCloseAnimator.cancel();
        }
        if (mIconProxyView != null) {
            try {
                getPopupContainer().removeView(mIconProxyView);
            } catch (Exception ignored) {}
            mIconProxyView = null;
        }
        if (mTargetProxyView != null) {
            try {
                getPopupContainer().removeView(mTargetProxyView);
            } catch (Exception ignored) {}
            mTargetProxyView = null;
        }
        if (mDarkOverlayView != null) {
            try {
                getPopupContainer().removeView(mDarkOverlayView);
            } catch (Exception ignored) {}
            mDarkOverlayView = null;
        }
        if (mPopupGlassView != null) {
            try {
                getPopupContainer().removeView(mPopupGlassView);
            } catch (Exception ignored) {}
            mPopupGlassView = null;
        }
        boolean isDragging = mActivityContext.getDragController() != null && mActivityContext.getDragController().isDragging();
        boolean keepOriginalHidden = isDragging && mIsOriginalViewDragged;
        boolean isTargetFolderOpen = (mTargetView instanceof com.android.launcher3.folder.FolderIcon)
                && ((com.android.launcher3.folder.FolderIcon) mTargetView).getFolder() != null
                && ((com.android.launcher3.folder.FolderIcon) mTargetView).getFolder().isOpen();
        if (!keepOriginalHidden && !isTargetFolderOpen) {
            if (mOriginalIcon != null) {
                mOriginalIcon.setVisibility(android.view.View.VISIBLE);
                mOriginalIcon.setAlpha(1f);
            }
            if (mTargetView != null) {
                mTargetView.setVisibility(android.view.View.VISIBLE);
                mTargetView.setAlpha(1f);
            }
        }
    }

    private void destroy() {
        if (mActivityContext.getDragController() != null) {
            mActivityContext.getDragController().removeDragListener(this);
        }
        if (getParent() != null) {
            getPopupContainer().removeView(this);
        }
        if (mActivityContext instanceof Launcher) {
            ((Launcher) mActivityContext).getInteractionController().endPopup(this);
        }
    }

    @Override
    protected void closeComplete() {
        if (mCancelledForDrag) {
            teardownVisuals();
            destroy();
            return;
        }

        boolean isDragging = mActivityContext.getDragController() != null && mActivityContext.getDragController().isDragging();
        boolean keepOriginalHidden = isDragging && mIsOriginalViewDragged;
        boolean isTargetFolderOpen = (mTargetView instanceof com.android.launcher3.folder.FolderIcon)
                && ((com.android.launcher3.folder.FolderIcon) mTargetView).getFolder() != null
                && ((com.android.launcher3.folder.FolderIcon) mTargetView).getFolder().isOpen();
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() && !keepOriginalHidden && !isTargetFolderOpen) {
            if (mOriginalIcon != null) {
                mOriginalIcon.setVisibility(android.view.View.VISIBLE);
                mOriginalIcon.setAlpha(1f);
            }
            if (mTargetView != null) {
                if (mTargetView.getParent() instanceof com.android.launcher3.dragndrop.DragView) {
                    ((com.android.launcher3.dragndrop.DragView<?>) mTargetView.getParent()).detachContentView(/* reattachToPreviousParent= */ true);
                }
                mTargetView.setVisibility(android.view.View.VISIBLE);
                mTargetView.setAlpha(1f);
            }
        }

        super.closeComplete();

        if (mActivityContext.getDragController() != null) {
            mActivityContext.getDragController().removeDragListener(this);
        }
        PopupContainerWithArrow openPopup = getOpen(mActivityContext);
        if (openPopup == null || openPopup.mOriginalIcon != mOriginalIcon) {
            if (mOriginalIcon != null) {
                mOriginalIcon.setTextVisibility(mOriginalIcon.shouldTextBeVisible());
                mOriginalIcon.setForceHideDot(false);
                mOriginalIcon.setIconVisible(true);
                if (!isDragging) {
                    mOriginalIcon.setVisibility(VISIBLE);
                }
            }
        }
        teardownVisuals();
        destroy();
    }

    /**
     * Returns a PopupContainerWithArrow which is already open or null
     */
    public static <T extends Context & ActivityContext> PopupContainerWithArrow getOpen(T context) {
        return getOpenView(context, TYPE_ACTION_POPUP);
    }

    /**
     * Dismisses the popup if it is no longer valid
     */
    public static void dismissInvalidPopup(BaseDraggingActivity activity) {
        PopupContainerWithArrow popup = getOpen(activity);
        if (popup != null) {
            View target = popup.mOriginalIcon != null ? popup.mOriginalIcon : popup.mTargetView;
            if (target != null && (!target.isAttachedToWindow()
                    || !ShortcutUtil.supportsShortcuts((ItemInfo) target.getTag()))) {
                popup.animateClose();
            }
        }
    }

    /**
     * Handler to control drag-and-drop for popup items
     */
    public interface PopupItemDragHandler extends OnLongClickListener, OnTouchListener { }

    /**
     * Drag and drop handler for popup items in Launcher activity
     */
    public static class LauncherPopupItemDragHandler implements PopupItemDragHandler {

        protected final Point mIconLastTouchPos = new Point();
        private final Launcher mLauncher;
        private final PopupContainerWithArrow mContainer;

        LauncherPopupItemDragHandler(Launcher launcher, PopupContainerWithArrow container) {
            mLauncher = launcher;
            mContainer = container;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                    break;
            }
            return false;
        }

        @Override
        public boolean onLongClick(View v) {
            if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
            // Return early if not the correct view
            if (!(v.getParent() instanceof DeepShortcutView)) return false;
            // Return early if workspace edit is disabled
            if (!Utilities.isWorkspaceEditAllowed(mLauncher.getApplicationContext())) return false;

            // Long clicked on a shortcut.
            DeepShortcutView sv = (DeepShortcutView) v.getParent();
            sv.setWillDrawIcon(false);

            // Move the icon to align with the center-top of the touch point
            Point iconShift = new Point();
            iconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
            iconShift.y = mIconLastTouchPos.y - mLauncher.getDeviceProfile().iconSizePx;

            DraggableView draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON);
            WorkspaceItemInfo itemInfo = sv.getFinalInfo();
            itemInfo.container = CONTAINER_SHORTCUTS;
            DragView dv = mLauncher.getWorkspace().beginDragShared(sv.getIconView(), draggableView,
                    mContainer, itemInfo,
                    new ShortcutDragPreviewProvider(sv.getIconView(), iconShift),
                    new DragOptions());
            dv.animateShift(-iconShift.x, -iconShift.y);

            // TODO: support dragging from within folder without having to close it
            AbstractFloatingView.closeOpenContainer(mLauncher, AbstractFloatingView.TYPE_FOLDER);
            return false;
        }
    }

    private static class DarkOverlayView extends View {
        private final android.graphics.Paint mPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path mCutoutPath = new android.graphics.Path();
        private final android.graphics.RectF mCutoutRect = new android.graphics.RectF();
        private float mCutoutRadius = 0f;
        private boolean mHasCutout = false;

        public DarkOverlayView(Context context) {
            super(context);
            mPaint.setColor(android.graphics.Color.BLACK);
        }

        public void setCutout(android.graphics.RectF rect, float radius) {
            mCutoutRect.set(rect);
            mCutoutRadius = radius;
            mHasCutout = true;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (!mHasCutout) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
            } else {
                mCutoutPath.reset();
                mCutoutPath.setFillType(android.graphics.Path.FillType.EVEN_ODD);
                mCutoutPath.addRect(0, 0, getWidth(), getHeight(), android.graphics.Path.Direction.CW);
                mCutoutPath.addRoundRect(mCutoutRect, mCutoutRadius, mCutoutRadius, android.graphics.Path.Direction.CW);
                canvas.drawPath(mCutoutPath, mPaint);
            }
        }
    }
}
