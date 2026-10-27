# Part 6: Context Popups, Data Models, DB Loader, Widgets & Onboarding

Modular code logic bundle designed to stay within Gemini attachment size limits (18 files).

## Table of Contents

- [src/com/android/launcher3/popup/PopupContainerWithArrow.java](#srccomandroidlauncher3popupPopupContainerWithArrowjava)
- [src/com/android/launcher3/popup/ArrowPopup.java](#srccomandroidlauncher3popupArrowPopupjava)
- [src/com/android/launcher3/popup/LauncherPopupLiveUpdateHandler.java](#srccomandroidlauncher3popupLauncherPopupLiveUpdateHandlerjava)
- [src/com/android/launcher3/popup/SystemShortcut.java](#srccomandroidlauncher3popupSystemShortcutjava)
- [src/com/android/launcher3/shortcuts/DeepShortcutView.java](#srccomandroidlauncher3shortcutsDeepShortcutViewjava)
- [src/com/android/launcher3/LauncherModel.java](#srccomandroidlauncher3LauncherModeljava)
- [src/com/android/launcher3/model/ModelWriter.java](#srccomandroidlauncher3modelModelWriterjava)
- [src/com/android/launcher3/model/BgDataModel.java](#srccomandroidlauncher3modelBgDataModeljava)
- [src/com/android/launcher3/model/LoaderTask.java](#srccomandroidlauncher3modelLoaderTaskjava)
- [src/com/android/launcher3/model/data/ItemInfo.java](#srccomandroidlauncher3modeldataItemInfojava)
- [src/com/android/launcher3/model/data/FolderInfo.java](#srccomandroidlauncher3modeldataFolderInfojava)
- [src/com/android/launcher3/model/data/WorkspaceItemInfo.java](#srccomandroidlauncher3modeldataWorkspaceItemInfojava)
- [bliss/src/foundation/e/bliss/widgets/WidgetContainer.kt](#blisssrcfoundationeblisswidgetsWidgetContainerkt)
- [bliss/src/foundation/e/bliss/widgets/RoundedWidgetView.java](#blisssrcfoundationeblisswidgetsRoundedWidgetViewjava)
- [bliss/src/foundation/e/bliss/widgets/WidgetsDbHelper.kt](#blisssrcfoundationeblisswidgetsWidgetsDbHelperkt)
- [bliss/src/foundation/e/bliss/widgets/DefaultWidgets.kt](#blisssrcfoundationeblisswidgetsDefaultWidgetskt)
- [bliss/src/foundation/e/bliss/preferences/BlissPrefs.kt](#blisssrcfoundationeblisspreferencesBlissPrefskt)
- [bliss/src/foundation/e/blisslauncher/features/onboarding/DefaultHomeLayoutResolver.kt](#blisssrcfoundationeblisslauncherfeaturesonboardingDefaultHomeLayoutResolverkt)

---

# src/com/android/launcher3/popup/PopupContainerWithArrow.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/popup/PopupContainerWithArrow.java`  
**Lines**: 1634 | **Bytes**: 74985

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
import com.android.launcher3.util.DragTrace;
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
        DragTrace.log("PopupContainerWithArrow.showForIcon item=" + (item != null ? item.title : "null") + " deepShortcuts=" + deepShortcutCount);
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
                if (mTargetView instanceof foundation.e.bliss.widgets.GlassWidgetHostView glassWidget) {
                    android.graphics.Rect squircle = new android.graphics.Rect();
                    glassWidget.getSquircleBoundsInSelf(squircle);
                    pos.set(
                        pos.left + squircle.left,
                        pos.top + squircle.top,
                        pos.left + squircle.right,
                        pos.top + squircle.bottom
                    );
                    radius = glassWidget.getSquircleRadius();
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
```

---

# src/com/android/launcher3/popup/ArrowPopup.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/popup/ArrowPopup.java`  
**Lines**: 789 | **Bytes**: 31885

```java
/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static androidx.core.content.ContextCompat.getColorStateList;

import static com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.Property;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.DragTrace;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 *
 * @param <T> The activity on with the popup shows
 */
public abstract class ArrowPopup<T extends Context & ActivityContext>
        extends AbstractFloatingView {

    // Duration values (ms) for popup open and close animations.
    protected int mOpenDuration = 276;
    protected int mOpenFadeStartDelay = 0;
    protected int mOpenFadeDuration = 38;
    protected int mOpenChildFadeStartDelay = 38;
    protected int mOpenChildFadeDuration = 76;

    protected int mCloseDuration = 200;
    protected int mCloseFadeStartDelay = 140;
    protected int mCloseFadeDuration = 50;
    protected int mCloseChildFadeStartDelay = 0;
    protected int mCloseChildFadeDuration = 140;

    private static final int OPEN_DURATION_U = 200;
    private static final int OPEN_FADE_START_DELAY_U = 0;
    private static final int OPEN_FADE_DURATION_U = 83;
    private static final int OPEN_CHILD_FADE_START_DELAY_U = 0;
    private static final int OPEN_CHILD_FADE_DURATION_U = 83;
    private static final int OPEN_OVERSHOOT_DURATION_U = 200;

    private static final int CLOSE_DURATION_U  = 233;
    private static final int CLOSE_FADE_START_DELAY_U = 150;
    private static final int CLOSE_FADE_DURATION_U = 83;
    private static final int CLOSE_CHILD_FADE_START_DELAY_U = 150;
    private static final int CLOSE_CHILD_FADE_DURATION_U = 83;

    protected final Rect mTempRect = new Rect();

    protected final LayoutInflater mInflater;
    protected final float mOutlineRadius;
    protected final T mActivityContext;
    protected final boolean mIsRtl;

    protected final int mArrowOffsetVertical;
    protected final int mArrowOffsetHorizontal;
    protected final int mArrowWidth;
    protected final int mArrowHeight;
    protected final int mArrowPointRadius;
    protected final View mArrow;

    protected final int mChildContainerMargin;

    protected boolean mIsLeftAligned;
    protected boolean mIsAboveIcon;
    protected int mGravity;

    protected AnimatorSet mOpenCloseAnimator;
    protected boolean mDeferContainerRemoval;
    protected boolean shouldScaleArrow = false;
    protected boolean mIsArrowRotated = false;

    private final GradientDrawable mRoundedTop;
    private final GradientDrawable mRoundedBottom;

    private RunnableList mOnCloseCallbacks = new RunnableList();

    // The rect string of the view that the arrow is attached to, in screen reference frame.
    protected int mArrowColor;

    protected final float mElevation;

    // Tag for Views that have children that will need to be iterated to add styling.
    private final String mIterateChildrenTag;

    protected final int[] mColorIds;

    public ArrowPopup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInflater = LayoutInflater.from(context);
        mActivityContext = ActivityContext.lookupContext(context);
        mIsRtl = Utilities.isRtl(getResources());
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            mOutlineRadius = 26f * getResources().getDisplayMetrics().density;
            mElevation = getResources().getDisplayMetrics().density * 8; // 8dp
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                setOutlineAmbientShadowColor(android.graphics.Color.argb(20, 0, 0, 0)); // 8%
                setOutlineSpotShadowColor(android.graphics.Color.argb(46, 0, 0, 0)); // 18%
            }
        } else {
            mOutlineRadius = Themes.getDialogCornerRadius(context);
            mElevation = getResources().getDimension(R.dimen.deep_shortcuts_elevation);
        }

        // Initialize arrow view
        final Resources resources = getResources();
        mArrowColor = getColorStateList(getContext(), R.color.popup_color_background)
                .getDefaultColor();
        mChildContainerMargin = foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode() 
                ? 0 
                : resources.getDimensionPixelSize(R.dimen.popup_margin);
        mArrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width);
        mArrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height);
        mArrow = new View(context);
        mArrow.setLayoutParams(new DragLayer.LayoutParams(mArrowWidth, mArrowHeight));
        mArrowOffsetVertical = resources.getDimensionPixelSize(R.dimen.popup_arrow_vertical_offset);
        mArrowOffsetHorizontal = resources.getDimensionPixelSize(
                R.dimen.popup_arrow_horizontal_center_offset) - (mArrowWidth / 2);
        mArrowPointRadius = resources.getDimensionPixelSize(R.dimen.popup_arrow_corner_radius);

        int smallerRadius = resources.getDimensionPixelSize(R.dimen.popup_smaller_radius);
        mRoundedTop = new GradientDrawable();
        int popupPrimaryColor = Themes.getAttrColor(context, R.attr.popupColorPrimary);
        mRoundedTop.setColor(popupPrimaryColor);
        mRoundedTop.setCornerRadii(new float[] { mOutlineRadius, mOutlineRadius, mOutlineRadius,
                mOutlineRadius, smallerRadius, smallerRadius, smallerRadius, smallerRadius});

        mRoundedBottom = new GradientDrawable();
        mRoundedBottom.setColor(popupPrimaryColor);
        mRoundedBottom.setCornerRadii(new float[] { smallerRadius, smallerRadius, smallerRadius,
                smallerRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius, mOutlineRadius});

        mIterateChildrenTag = getContext().getString(R.string.popup_container_iterate_children);

        if (mActivityContext.canUseMultipleShadesForPopup()) {
            mColorIds = new int[]{R.color.popup_shade_first, R.color.popup_shade_second,
                    R.color.popup_shade_third};
        } else {
            mColorIds = new int[]{R.color.popup_color_background};
        }
    }

    public ArrowPopup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArrowPopup(Context context) {
        this(context, null, 0);
    }

    @Override
    protected void handleClose(boolean animate) {
        DragTrace.log("ArrowPopup.handleClose view=" + getClass().getSimpleName() + " animate=" + animate);
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    /**
     * Utility method for inflating and adding a view
     */
    public <R extends View> R inflateAndAdd(int resId, ViewGroup container) {
        View view = mInflater.inflate(resId, container, false);
        container.addView(view);
        return (R) view;
    }

    /**
     * Utility method for inflating and adding a view
     */
    public <R extends View> R inflateAndAdd(int resId, ViewGroup container, int index) {
        View view = mInflater.inflate(resId, container, false);
        container.addView(view, index);
        return (R) view;
    }

    /**
     * Set the margins and radius of backgrounds after views are properly ordered.
     */
    public void assignMarginsAndBackgrounds(ViewGroup viewGroup) {
        assignMarginsAndBackgrounds(viewGroup, Color.TRANSPARENT);
    }

    /**
     * @param backgroundColor When Color.TRANSPARENT, we get color from {@link #mColorIds}.
     *                        Otherwise, we will use this color for all child views.
     */
    protected void assignMarginsAndBackgrounds(ViewGroup viewGroup, int backgroundColor) {
        int[] colors = null;
        if (backgroundColor == Color.TRANSPARENT) {
            // Lazily get the colors so they match the current wallpaper colors.
            colors = Arrays.stream(mColorIds).map(
                    r -> getColorStateList(getContext(), r).getDefaultColor()).toArray();
        }

        int count = viewGroup.getChildCount();
        int totalVisibleShortcuts = 0;
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE && isShortcutOrWrapper(view)) {
                totalVisibleShortcuts++;
            }
        }

        int numVisibleShortcut = 0;
        View lastView = null;
        AnimatorSet colorAnimator = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            View view = viewGroup.getChildAt(i);
            if (view.getVisibility() == VISIBLE) {
                if (lastView != null && (isShortcutContainer(lastView))) {
                    MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                    mlp.bottomMargin = mChildContainerMargin;
                }
                lastView = view;
                MarginLayoutParams mlp = (MarginLayoutParams) lastView.getLayoutParams();
                mlp.bottomMargin = 0;

                if (colors != null && isShortcutContainer(view)) {
                    setChildColor(view, colors[0], colorAnimator);
                    mArrowColor = colors[0];
                }

                if (view instanceof ViewGroup && isShortcutContainer(view)) {
                    assignMarginsAndBackgrounds((ViewGroup) view, backgroundColor);
                    continue;
                }

                if (isShortcutOrWrapper(view)) {
                    if (totalVisibleShortcuts == 1) {
                        view.setBackgroundResource(R.drawable.single_item_primary);
                    } else if (totalVisibleShortcuts > 1) {
                        if (numVisibleShortcut == 0) {
                            view.setBackground(mRoundedTop.getConstantState().newDrawable());
                        } else if (numVisibleShortcut == (totalVisibleShortcuts - 1)) {
                            view.setBackground(mRoundedBottom.getConstantState().newDrawable());
                        } else {
                            view.setBackgroundResource(R.drawable.middle_item_primary);
                        }
                        numVisibleShortcut++;
                    }
                }

                setChildColor(view, backgroundColor, colorAnimator);
            }
        }

        colorAnimator.setDuration(0).start();
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    }

    /**
     * Returns {@code true} if the child is a shortcut or wraps a shortcut.
     */
    protected boolean isShortcutOrWrapper(View view) {
        return view instanceof DeepShortcutView;
    }

    /**
     * Returns {@code true} if view is a layout container of shortcuts
     */
    boolean isShortcutContainer(View view) {
        return mIterateChildrenTag.equals(view.getTag());
    }

    /**
     * Sets the background color of the child.
     */
    protected void setChildColor(View view, int color, AnimatorSet animatorSetOut) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) bg.mutate();
            int oldColor = ((GradientDrawable) bg).getColor().getDefaultColor();
            animatorSetOut.play(ObjectAnimator.ofArgb(gd, "color", oldColor, color));
        } else if (bg instanceof ColorDrawable) {
            ColorDrawable cd = (ColorDrawable) bg.mutate();
            int oldColor = ((ColorDrawable) bg).getColor();
            animatorSetOut.play(ObjectAnimator.ofArgb(cd, "color", oldColor, color));
        }
    }

    /**
     * Shows the popup at the desired location.
     */
    public void show() {
        setupForDisplay();
        assignMarginsAndBackgrounds(this);
        if (shouldAddArrow()) {
            addArrow();
        }
        animateOpen();
    }

    protected void setupForDisplay() {
        setVisibility(View.INVISIBLE);
        mIsOpen = true;
        getPopupContainer().addView(this);
        orientAboutObject();
    }

    private int getArrowLeft() {
        if (mIsLeftAligned) {
            return mArrowOffsetHorizontal;
        }
        return getMeasuredWidth() - mArrowOffsetHorizontal - mArrowWidth;
    }

    /**
     * @param show If true, shows arrow (when applicable), otherwise hides arrow.
     */
    public void showArrow(boolean show) {
        mArrow.setVisibility(show && shouldAddArrow() ? VISIBLE : INVISIBLE);
    }

    protected void addArrow() {
        getPopupContainer().addView(mArrow);
        mArrow.setX(getX() + getArrowLeft());

        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to showDefaultOptions the arrow.
            mArrow.setVisibility(INVISIBLE);
        } else {
            updateArrowColor();
        }

        mArrow.setPivotX(mArrowWidth / 2.0f);
        mArrow.setPivotY(mIsAboveIcon ? mArrowHeight : 0);
    }

    protected void updateArrowColor() {
        if (!Gravity.isVertical(mGravity)) {
            mArrow.setBackground(new RoundedArrowDrawable(
                    mArrowWidth, mArrowHeight, mArrowPointRadius,
                    mOutlineRadius, getMeasuredWidth(), getMeasuredHeight(),
                    mArrowOffsetHorizontal, -mArrowOffsetVertical,
                    !mIsAboveIcon, mIsLeftAligned,
                    mArrowColor));
            setElevation(mElevation);
            mArrow.setElevation(mElevation);
        }
    }

    /**
     * Returns whether or not we should add the arrow.
     */
    protected boolean shouldAddArrow() {
        return true;
    }

    /**
     * Provide the location of the target object relative to the dragLayer.
     */
    protected abstract void getTargetObjectLocation(Rect outPos);

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     */
    protected void orientAboutObject() {
        orientAboutObject(true /* allowAlignLeft */, true /* allowAlignRight */);
    }

    /**
     * @see #orientAboutObject()
     *
     * @param allowAlignLeft Set to false if we already tried aligning left and didn't have room.
     * @param allowAlignRight Set to false if we already tried aligning right and didn't have room.
     * TODO: Can we test this with all permutations of widths/heights and icon locations + RTL?
     */
    private void orientAboutObject(boolean allowAlignLeft, boolean allowAlignRight) {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

        int extraVerticalSpace = mArrowHeight + mArrowOffsetVertical + getExtraVerticalOffset();
        // The margins are added after we call this method, so we need to account for them here.
        int numVisibleChildren = 0;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i).getVisibility() == VISIBLE) {
                numVisibleChildren++;
            }
        }
        int childMargins = (numVisibleChildren - 1) * mChildContainerMargin;
        int height = getMeasuredHeight() + extraVerticalSpace + childMargins;
        int width = getMeasuredWidth() + getPaddingLeft() + getPaddingRight();

        getTargetObjectLocation(mTempRect);
        InsettableFrameLayout dragLayer = getPopupContainer();
        Rect insets = dragLayer.getInsets();

        // Align left (right in RTL) if there is room.
        int leftAlignedX = mTempRect.left;
        int rightAlignedX = mTempRect.right - width;
        mIsLeftAligned = !mIsRtl ? allowAlignLeft : !allowAlignRight;
        int x = mIsLeftAligned ? leftAlignedX : rightAlignedX;

        // Offset x so that the arrow and shortcut icons are center-aligned with the original icon.
        int iconWidth = mTempRect.width();
        int xOffset = iconWidth / 2 - mArrowOffsetHorizontal - mArrowWidth / 2;
        x += mIsLeftAligned ? xOffset : -xOffset;

        // Check whether we can still align as we originally wanted, now that we've calculated x.
        if (!allowAlignLeft && !allowAlignRight) {
            // We've already tried both ways and couldn't make it fit. onLayout() will set the
            // gravity to CENTER_HORIZONTAL, but continue below to update y.
        } else {
            boolean canBeLeftAligned = x + width + insets.left
                    < dragLayer.getWidth() - insets.right;
            boolean canBeRightAligned = x > insets.left;
            boolean alignmentStillValid = mIsLeftAligned && canBeLeftAligned
                    || !mIsLeftAligned && canBeRightAligned;
            if (!alignmentStillValid) {
                // Try again, but don't allow this alignment we already know won't work.
                orientAboutObject(allowAlignLeft && !mIsLeftAligned /* allowAlignLeft */,
                        allowAlignRight && mIsLeftAligned /* allowAlignRight */);
                return;
            }
        }

        // Open above icon if there is room.
        int iconHeight = mTempRect.height();
        int y = mTempRect.top - height;
        mIsAboveIcon = y > dragLayer.getTop() + insets.top;
        if (!mIsAboveIcon) {
            y = mTempRect.top + iconHeight + extraVerticalSpace;
            height -= extraVerticalSpace;
        }

        // Insets are added later, so subtract them now.
        x -= insets.left;
        y -= insets.top;

        mGravity = 0;
        if ((insets.top + y + height) > (dragLayer.getBottom() - insets.bottom)) {
            // The container is opening off the screen, so just center it in the drag layer instead.
            mGravity = Gravity.CENTER_VERTICAL;
            // Put the container next to the icon, preferring the right side in ltr (left in rtl).
            int rightSide = leftAlignedX + iconWidth - insets.left;
            int leftSide = rightAlignedX - iconWidth - insets.left;
            if (!mIsRtl) {
                if (rightSide + width < dragLayer.getRight()) {
                    x = rightSide;
                    mIsLeftAligned = true;
                } else {
                    x = leftSide;
                    mIsLeftAligned = false;
                }
            } else {
                if (leftSide > dragLayer.getLeft()) {
                    x = leftSide;
                    mIsLeftAligned = false;
                } else {
                    x = rightSide;
                    mIsLeftAligned = true;
                }
            }
            mIsAboveIcon = true;
        }

        setX(x);
        if (Gravity.isVertical(mGravity)) {
            return;
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        FrameLayout.LayoutParams arrowLp = (FrameLayout.LayoutParams) mArrow.getLayoutParams();
        if (mIsAboveIcon) {
            arrowLp.gravity = lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin =
                    getPopupContainer().getHeight() - y - getMeasuredHeight() - insets.top;
            arrowLp.bottomMargin =
                    lp.bottomMargin - arrowLp.height - mArrowOffsetVertical - insets.bottom;
        } else {
            arrowLp.gravity = lp.gravity = Gravity.TOP;
            lp.topMargin = y + insets.top;
            arrowLp.topMargin = lp.topMargin - insets.top - arrowLp.height - mArrowOffsetVertical;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // enforce contained is within screen
        BaseDragLayer dragLayer = getPopupContainer();
        Rect insets = dragLayer.getInsets();
        if (getTranslationX() + l < insets.left
                || getTranslationX() + r > dragLayer.getWidth() - insets.right) {
            // If we are still off screen, center horizontally too.
            mGravity |= Gravity.CENTER_HORIZONTAL;
        }

        if (Gravity.isHorizontal(mGravity)) {
            setX(dragLayer.getWidth() / 2 - getMeasuredWidth() / 2);
            mArrow.setVisibility(INVISIBLE);
        }
        if (Gravity.isVertical(mGravity)) {
            setY(dragLayer.getHeight() / 2 - getMeasuredHeight() / 2);
        }
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(this, "");
    }

    @Override
    protected View getAccessibilityInitialFocusView() {
        return getChildCount() > 0 ? getChildAt(0) : this;
    }

    protected void animateOpen() {
        setVisibility(View.VISIBLE);
        mOpenCloseAnimator = getOpenCloseAnimator(
                        true,
                        OPEN_DURATION_U,
                        OPEN_FADE_START_DELAY_U,
                        OPEN_FADE_DURATION_U,
                        OPEN_CHILD_FADE_START_DELAY_U,
                        OPEN_CHILD_FADE_DURATION_U,
                        EMPHASIZED_DECELERATE);

        onCreateOpenAnimation(mOpenCloseAnimator);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setAlpha(1f);
                announceAccessibilityChanges();
                mOpenCloseAnimator = null;
            }
        });
        mOpenCloseAnimator.start();
    }

    private void fadeInChildViews(ViewGroup group, float[] alphaValues, long startDelay,
            long duration, AnimatorSet out) {
        for (int i = group.getChildCount() - 1; i >= 0; --i) {
            View view = group.getChildAt(i);
            if (view.getVisibility() == VISIBLE && view instanceof ViewGroup) {
                if (isShortcutContainer(view)) {
                    fadeInChildViews((ViewGroup) view, alphaValues, startDelay, duration, out);
                    continue;
                }
                for (int j = ((ViewGroup) view).getChildCount() - 1; j >= 0; --j) {
                    View childView = ((ViewGroup) view).getChildAt(j);
                    childView.setAlpha(alphaValues[0]);
                    ValueAnimator childFade = ObjectAnimator.ofFloat(childView, ALPHA, alphaValues);
                    childFade.setStartDelay(startDelay);
                    childFade.setDuration(duration);
                    childFade.setInterpolator(LINEAR);

                    out.play(childFade);
                }
            }
        }
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mIsOpen = false;

        mOpenCloseAnimator = getOpenCloseAnimator(
                        false,
                        CLOSE_DURATION_U,
                        CLOSE_FADE_START_DELAY_U,
                        CLOSE_FADE_DURATION_U,
                        CLOSE_CHILD_FADE_START_DELAY_U,
                        CLOSE_CHILD_FADE_DURATION_U,
                        EMPHASIZED_ACCELERATE);

        onCreateCloseAnimation(mOpenCloseAnimator);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                if (mDeferContainerRemoval) {
                    setVisibility(INVISIBLE);
                } else {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator.start();
    }

    public int getExtraVerticalOffset() {
        return getResources().getDimensionPixelSize(R.dimen.popup_vertical_padding);
    }

    /**
     * Sets X and Y pivots for the view animation considering arrow position.
     */
    protected void setPivotForOpenCloseAnimation() {
        int arrowCenter = mArrowOffsetHorizontal + mArrowWidth / 2;
        if (mIsArrowRotated) {
            setPivotX(mIsLeftAligned ? 0f : getMeasuredWidth());
            setPivotY(arrowCenter);
        } else {
            setPivotX(mIsLeftAligned ? arrowCenter : getMeasuredWidth() - arrowCenter);
            setPivotY(mIsAboveIcon ? getMeasuredHeight() : 0f);
        }
    }


    protected AnimatorSet getOpenCloseAnimator(boolean isOpening, int scaleDuration,
            int fadeStartDelay, int fadeDuration, int childFadeStartDelay, int childFadeDuration,
            Interpolator interpolator) {

        setPivotForOpenCloseAnimation();

        float[] alphaValues = isOpening ? new float[] {0, 1} : new float[] {1, 0};
        float[] scaleValues = isOpening ? new float[] {0.5f, 1.02f} : new float[] {1f, 0.5f};
        
        Animator scaleY;
        Animator scaleX;
        
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            if (isOpening) {
                scaleValues = new float[] {0.72f, 1f};
                float damping = 0.82f;
                float stiffness = 160f;
                scaleX = new com.android.launcher3.anim.SpringAnimationBuilder(getContext())
                        .setStartValue(scaleValues[0]).setEndValue(scaleValues[1])
                        .setMinimumVisibleChange(0.001f)
                        .setDampingRatio(damping).setStiffness(stiffness).build(this, com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY);
                scaleY = ObjectAnimator.ofFloat(this, View.SCALE_Y, scaleValues[1], scaleValues[1]).setDuration(0); // dummy, scale handled by SCALE_PROPERTY
                fadeDuration = 260;
                childFadeStartDelay = 30;
                childFadeDuration = 200;
            } else {
                scaleValues = new float[] {1f, 0.78f};
                int closeDuration = 180;
                scaleX = getAnimatorOfFloat(this, View.SCALE_X, closeDuration, 0, EMPHASIZED_ACCELERATE, scaleValues);
                scaleY = getAnimatorOfFloat(this, View.SCALE_Y, closeDuration, 0, EMPHASIZED_ACCELERATE, scaleValues);
                fadeDuration = 180;
                childFadeStartDelay = 0;
                childFadeDuration = 150;
            }
        } else {
            scaleY = getAnimatorOfFloat(this, View.SCALE_Y, scaleDuration, 0, interpolator,
                    scaleValues);
            scaleX = getAnimatorOfFloat(this, View.SCALE_X, scaleDuration, 0, interpolator,
                    scaleValues);
        }

        Animator alpha = getAnimatorOfFloat(this, View.ALPHA, fadeDuration, fadeStartDelay,
                LINEAR, alphaValues);
        Animator arrowAlpha = getAnimatorOfFloat(mArrow, View.ALPHA, fadeDuration, fadeStartDelay,
                LINEAR, alphaValues);

        final AnimatorSet animatorSet = new AnimatorSet();
        List<Animator> animators = new ArrayList<>();
        animators.add(alpha);
        animators.add(arrowAlpha);
        animators.add(scaleY);
        animators.add(scaleX);

        if (isOpening) {
            if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
                animatorSet.playTogether(animators);
            } else {
                float[] scaleValuesOvershoot = new float[] {1.02f, 1f};
                PathInterpolator overshootInterpolator = new PathInterpolator(0.3f, 0, 0.33f, 1f);
                animators.add(getAnimatorOfFloat(this, View.SCALE_Y,
                        OPEN_OVERSHOOT_DURATION_U, scaleDuration, overshootInterpolator,
                        scaleValuesOvershoot));
                animators.add(getAnimatorOfFloat(this, View.SCALE_X,
                        OPEN_OVERSHOOT_DURATION_U, scaleDuration, overshootInterpolator,
                        scaleValuesOvershoot));
                animatorSet.playTogether(animators);
            }
        } else {
            animatorSet.playTogether(animators);
        }

        fadeInChildViews(this, alphaValues, childFadeStartDelay, childFadeDuration, animatorSet);
        return animatorSet;
    }

    private Animator getAnimatorOfFloat(View view, Property<View, Float> property,
            int duration, int startDelay, Interpolator interpolator,  float... values) {
        Animator animator = ObjectAnimator.ofFloat(view, property, values);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.setStartDelay(startDelay);
        return animator;
    }

    /**
     * Called when creating the open transition allowing subclass can add additional animations.
     */
    protected void onCreateOpenAnimation(AnimatorSet anim) { }

    /**
     * Called when creating the close transition allowing subclass can add additional animations.
     */
    protected void onCreateCloseAnimation(AnimatorSet anim) { }

    /**
     * Closes the popup without animation.
     */
    protected void closeComplete() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mDeferContainerRemoval = false;
        getPopupContainer().removeView(this);
        getPopupContainer().removeView(mArrow);
        mOnCloseCallbacks.executeAllAndClear();
    }

    /**
     * Callbacks to be called when the popup is closed
     */
    public void addOnCloseCallback(Runnable callback) {
        mOnCloseCallbacks.add(callback);
    }

    protected BaseDragLayer getPopupContainer() {
        return mActivityContext.getDragLayer();
    }
}
```

---

# src/com/android/launcher3/popup/LauncherPopupLiveUpdateHandler.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/popup/LauncherPopupLiveUpdateHandler.java`  
**Lines**: 94 | **Bytes**: 4314

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
package com.android.launcher3.popup;

import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;

/**
 * Utility class to handle updates while the popup is visible on the Launcher
 */
public class LauncherPopupLiveUpdateHandler extends PopupLiveUpdateHandler<Launcher> {

    public LauncherPopupLiveUpdateHandler(
            Launcher launcher, PopupContainerWithArrow<Launcher> popupContainerWithArrow) {
        super(launcher, popupContainerWithArrow);
    }

    private View getWidgetsView(ViewGroup container) {
        for (int i = container.getChildCount() - 1; i >= 0; --i) {
            View systemShortcutView = container.getChildAt(i);
            if (systemShortcutView.getTag() instanceof SystemShortcut.Widgets) {
                return systemShortcutView;
            }
        }
        return null;
    }

    @Override
    public void onWidgetsBound() {
        BubbleTextView originalIcon = mPopupContainerWithArrow.getOriginalIcon();
        if (originalIcon == null || !(originalIcon.getTag() instanceof ItemInfo)) {
            return;
        }
        SystemShortcut widgetInfo = SystemShortcut.WIDGETS.getShortcut(mContext,
                (ItemInfo) originalIcon.getTag(), originalIcon);
        View widgetsView = getWidgetsView(mPopupContainerWithArrow);
        if (widgetsView == null && mPopupContainerWithArrow.getWidgetContainer() != null) {
            widgetsView = getWidgetsView(mPopupContainerWithArrow.getWidgetContainer());
        }

        if (widgetInfo != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow) {
                if (mPopupContainerWithArrow.getWidgetContainer() == null) {
                    mPopupContainerWithArrow.setWidgetContainer(
                            mPopupContainerWithArrow.inflateAndAdd(
                                    R.layout.widget_shortcut_container,
                                    mPopupContainerWithArrow));
                }
                mPopupContainerWithArrow.initializeWidgetShortcut(
                        mPopupContainerWithArrow.getWidgetContainer(),
                        widgetInfo);
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need
                // to reopen the container to ensure measurements etc. all work out. While this
                // could be quite janky, in practice the user would typically see a small
                // flicker as the animation restarts partway through, and this is a very rare
                // edge case anyway.
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(mPopupContainerWithArrow.getOriginalIcon());
            }
        } else if (widgetInfo == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow
                    && mPopupContainerWithArrow.getWidgetContainer() != null) {
                mPopupContainerWithArrow.getWidgetContainer().removeView(widgetsView);
            } else {
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(mPopupContainerWithArrow.getOriginalIcon());
            }
        }
    }
}
```

---

# src/com/android/launcher3/popup/SystemShortcut.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/popup/SystemShortcut.java`  
**Lines**: 613 | **Bytes**: 28795

```java
package com.android.launcher3.popup;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.SecondaryDropTarget;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.WidgetsBottomSheet;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 *
 * @param <T> extends {@link ActivityContext}
 */
public abstract class SystemShortcut<T extends ActivityContext> extends ItemInfo
        implements View.OnClickListener {

    private final int mIconResId;
    protected final int mLabelResId;
    protected int mAccessibilityActionId;

    protected final T mTarget;
    protected final ItemInfo mItemInfo;
    protected final View mOriginalView;

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
        mOriginalView = originalView;
    }

    public SystemShortcut(SystemShortcut<T> other) {
        mIconResId = other.mIconResId;
        mLabelResId = other.mLabelResId;
        mAccessibilityActionId = other.mAccessibilityActionId;
        mTarget = other.mTarget;
        mItemInfo = other.mItemInfo;
        mOriginalView = other.mOriginalView;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        iconView.setBackgroundResource(mIconResId);
        labelView.setText(mLabelResId);
        labelView.setTextColor(android.graphics.Color.BLACK);
        if (iconView instanceof ImageView) {
            ((ImageView) iconView).setColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (iconView != null) {
            iconView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
        }
    }

    public void setIconAndContentDescriptionFor(ImageView view) {
        view.setImageResource(mIconResId);
        view.setContentDescription(view.getContext().getText(mLabelResId));
    }

    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(
                mAccessibilityActionId, context.getText(mLabelResId));
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    public interface Factory<T extends ActivityContext> {

        @Nullable
        SystemShortcut<T> getShortcut(T context, ItemInfo itemInfo, @NonNull View originalView);
    }

    public static final Factory<ActivityContext> WIDGETS = (context, itemInfo, originalView) -> {
        if (itemInfo.getTargetComponent() == null) return null;
        final List<WidgetItem> widgets =
                context.getPopupDataProvider().getWidgetsForPackageUser(new PackageUserKey(
                        itemInfo.getTargetComponent().getPackageName(), itemInfo.user));
        if (widgets.isEmpty()) {
            return null;
        }
        return new Widgets(context, itemInfo, originalView);
    };

    public static class Widgets<T extends ActivityContext> extends SystemShortcut<T> {
        public Widgets(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_widget, R.string.widget_button_text, target, itemInfo,
                    originalView);
        }

        @Override
        public void onClick(View view) {
            if (!Utilities.isWorkspaceEditAllowed((Context) mTarget)) return;
            AbstractFloatingView.closeAllOpenViews(mTarget);
            WidgetsBottomSheet widgetsBottomSheet =
                    (WidgetsBottomSheet) mTarget.getLayoutInflater().inflate(
                            R.layout.widgets_bottom_sheet, mTarget.getDragLayer(), false);
            widgetsBottomSheet.populateAndShow(mItemInfo);
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_WIDGETS_TAP);
        }
    }

    public static final Factory<ActivityContext> APP_INFO = AppInfo::new;

    public static class AppInfo<T extends ActivityContext> extends SystemShortcut<T> {

        @Nullable
        private SplitAccessibilityInfo mSplitA11yInfo;

        public AppInfo(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label, target,
                    itemInfo, originalView);
        }

        /**
         * Constructor used by overview for staged split to provide custom A11y information.
         *
         * Future improvements considerations:
         * Have the logic in {@link #createAccessibilityAction(Context)} be moved to super
         * call in {@link SystemShortcut#createAccessibilityAction(Context)} by having
         * SystemShortcut be aware of TaskContainers and staged split.
         * That way it could directly create the correct node info for any shortcut that supports
         * split, but then we'll need custom resIDs for each pair of shortcuts.
         */
        public AppInfo(T target, ItemInfo itemInfo, View originalView,
                SplitAccessibilityInfo accessibilityInfo) {
            this(target, itemInfo, originalView);
            mSplitA11yInfo = accessibilityInfo;
            mAccessibilityActionId = accessibilityInfo.nodeId;
        }

        @Override
        public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(
                Context context) {
            if (mSplitA11yInfo != null && mSplitA11yInfo.containsMultipleTasks) {
                String accessibilityLabel = context.getString(R.string.split_app_info_accessibility,
                        mSplitA11yInfo.taskTitle);
                return new AccessibilityNodeInfo.AccessibilityAction(mAccessibilityActionId,
                        accessibilityLabel);
            } else {
                return super.createAccessibilityAction(context);
            }
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            Rect sourceBounds = Utilities.getViewBounds(view);
            new PackageManagerHelper(view.getContext()).startDetailsActivityForInfo(
                    mItemInfo, sourceBounds, ActivityOptions.makeBasic().toBundle());
            mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_APP_INFO_TAP);
        }

        public static class SplitAccessibilityInfo {
            public final boolean containsMultipleTasks;
            public final CharSequence taskTitle;
            public final int nodeId;

            public SplitAccessibilityInfo(boolean containsMultipleTasks,
                    CharSequence taskTitle, int nodeId) {
                this.containsMultipleTasks = containsMultipleTasks;
                this.taskTitle = taskTitle;
                this.nodeId = nodeId;
            }
        }
    }

    public static final Factory<ActivityContext> PRIVATE_PROFILE_INSTALL =
            (context, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                if (itemInfo.getTargetComponent() == null
                        || !(itemInfo instanceof com.android.launcher3.model.data.AppInfo)
                        || !itemInfo.getContainerInfo().hasAllAppsContainer()
                        || !Process.myUserHandle().equals(itemInfo.user)) {
                    return null;
                }

                PrivateProfileManager privateProfileManager =
                        context.getAppsView().getPrivateProfileManager();
                if (privateProfileManager == null || !privateProfileManager.isEnabled()) {
                    return null;
                }

                UserHandle privateProfileUser = privateProfileManager.getProfileUser();
                if (privateProfileUser == null) {
                    return null;
                }
                // Do not show shortcut if an app is already installed to the space
                ComponentName targetComponent = itemInfo.getTargetComponent();
                if (context.getAppsView().getAppsStore().getApp(
                        new ComponentKey(targetComponent, privateProfileUser)) != null) {
                    return null;
                }

                // Do not show shortcut for settings
                String[] packagesToSkip =
                        originalView.getContext().getResources()
                                .getStringArray(R.array.skip_private_profile_shortcut_packages);
                if (Arrays.asList(packagesToSkip).contains(targetComponent.getPackageName())) {
                    return null;
                }

                return new InstallToPrivateProfile<>(
                        context, itemInfo, originalView, privateProfileUser);
            };

    static class InstallToPrivateProfile<T extends ActivityContext> extends SystemShortcut<T> {
        UserHandle mSpaceUser;

        InstallToPrivateProfile(T target, ItemInfo itemInfo, @NonNull View originalView,
                UserHandle spaceUser) {
            // TODO(b/302666597): update icon once available
            super(
                    R.drawable.ic_install_to_private,
                    R.string.install_private_system_shortcut_label,
                    target,
                    itemInfo,
                    originalView);
            mSpaceUser = spaceUser;
        }

        @Override
        public void onClick(View view) {
            Intent intent =
                    ApiWrapper.getAppMarketActivityIntent(
                            view.getContext(),
                            mItemInfo.getTargetComponent().getPackageName(),
                            mSpaceUser);
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_INSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    public static final Factory<ActivityContext> INSTALL =
            (activity, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                boolean supportsWebUI = (itemInfo instanceof WorkspaceItemInfo)
                        && ((WorkspaceItemInfo) itemInfo).hasStatusFlag(
                        WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI);
                boolean isInstantApp = false;
                if (itemInfo instanceof com.android.launcher3.model.data.AppInfo) {
                    com.android.launcher3.model.data.AppInfo
                            appInfo = (com.android.launcher3.model.data.AppInfo) itemInfo;
                    isInstantApp = InstantAppResolver.newInstance(
                            originalView.getContext()).isInstantApp(appInfo);
                }
                boolean enabled = supportsWebUI || isInstantApp;
                if (!enabled) {
                    return null;
                }
                return new Install(activity, itemInfo, originalView);
            };

    public static class Install<T extends ActivityContext> extends SystemShortcut<T> {

        public Install(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label,
                    target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            Intent intent = ApiWrapper.getAppMarketActivityIntent(view.getContext(),
                    mItemInfo.getTargetComponent().getPackageName(),
                    Process.myUserHandle());
            mTarget.startActivitySafely(view, intent, mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    public static final Factory<ActivityContext> DONT_SUGGEST_APP =
            (activity, itemInfo, originalView) -> {
                if (!itemInfo.isPredictedItem()) {
                    return null;
                }
                return new DontSuggestApp<>(activity, itemInfo, originalView);
            };

    private static class DontSuggestApp<T extends ActivityContext> extends SystemShortcut<T> {
        DontSuggestApp(T target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_block_no_shadow, R.string.dismiss_prediction_label, target,
                    itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            mTarget.getStatsLogManager().logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP);
        }
    }

    public static final Factory<ActivityContext> UNINSTALL_APP =
            (activityContext, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                boolean isWorkspace = (itemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
                        || itemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        || itemInfo.container > 0);
                ComponentName cn = SecondaryDropTarget.getUninstallTarget(originalView.getContext(),
                        itemInfo);
                if (!isWorkspace && cn == null) {
                    return null;
                }
                return new UninstallApp(activityContext, itemInfo, originalView, cn);
            };

    private static class UninstallApp<T extends ActivityContext> extends SystemShortcut<T> {
        @Nullable ComponentName mComponentName;

        UninstallApp(T target, ItemInfo itemInfo, @NonNull View originalView,
                @Nullable ComponentName cn) {
            super(R.drawable.ic_uninstall_no_shadow, R.string.remove_app_drop_target_label, target,
                    itemInfo, originalView);
            mComponentName = cn;

        }

        @Override
        public void setIconAndLabelFor(View iconView, TextView labelView) {
            super.setIconAndLabelFor(iconView, labelView);
            labelView.setText(R.string.remove_app_drop_target_label);
            int red = android.graphics.Color.parseColor("#FF3B30"); // iOS destructive red
            labelView.setTextColor(red);
            if (iconView instanceof ImageView) {
                ((ImageView) iconView).setColorFilter(red, android.graphics.PorterDuff.Mode.SRC_IN);
            } else if (iconView != null) {
                iconView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(red));
            }
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            boolean isWorkspace = (mItemInfo instanceof com.android.launcher3.model.data.WorkspaceItemInfo)
                    && (mItemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
                        || mItemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        || mItemInfo.container > 0);
            if (mTarget instanceof com.android.launcher3.Launcher) {
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) mTarget;
                if (launcher.getAppLibraryPage() != null && launcher.getAppLibraryPage().isInSearchMode()) {
                    isWorkspace = false;
                }
                if (com.android.launcher3.applibrary.view.AppLibraryCategoryExpandedView.getOpen(launcher) != null) {
                    isWorkspace = false;
                }
                if (com.android.launcher3.applibrary.view.SpotlightSearchOverlay.getOpen(launcher) != null) {
                    isWorkspace = false;
                }
            }
            if (isWorkspace && mOriginalView instanceof com.android.launcher3.BubbleTextView
                    && mTarget instanceof com.android.launcher3.Launcher) {
                ((com.android.launcher3.BubbleTextView) mOriginalView).showRemoveAppDialog(
                        (com.android.launcher3.Launcher) mTarget);
            } else if (mTarget instanceof com.android.launcher3.Launcher && mComponentName != null) {
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) mTarget;
                CharSequence title = mItemInfo.title != null ? mItemInfo.title : "App";
                foundation.e.bliss.glass.LiquidGlassAlertView.showDeleteAppAlert(
                        launcher,
                        title,
                        () -> {
                            SecondaryDropTarget.performUninstall(view.getContext(), mComponentName, mItemInfo);
                        },
                        null
                );
            } else if (mComponentName != null) {
                SecondaryDropTarget.performUninstall(view.getContext(), mComponentName, mItemInfo);
            }
            mTarget.getStatsLogManager()
                    .logger()
                    .withItemInfo(mItemInfo)
                    .log(LAUNCHER_PRIVATE_SPACE_UNINSTALL_SYSTEM_SHORTCUT_TAP);
        }
    }

    public static final Factory<ActivityContext> ADD_TO_HOME_SCREEN =
            (activityContext, itemInfo, originalView) -> {
                if (!(activityContext instanceof com.android.launcher3.Launcher)) {
                    return null;
                }
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) activityContext;
                ComponentName cn = itemInfo.getTargetComponent();
                if (cn == null) return null;
                boolean isWorkspace = (itemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
                        || itemInfo.container == com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
                        || itemInfo.container > 0);
                boolean isRemoved = com.android.launcher3.util.RemovedAppStore.isRemoved(
                        launcher, cn.flattenToString());
                if (isWorkspace && !isRemoved) {
                    return null;
                }
                if (launcher.getWorkspace() != null
                        && launcher.getWorkspace().getFirstMatch((info, v) -> {
                            if (info == null) return false;
                            if (cn.equals(info.getTargetComponent())
                                    && java.util.Objects.equals(info.user, itemInfo.user)) {
                                return true;
                            }
                            if (info instanceof com.android.launcher3.model.data.FolderInfo fi) {
                                for (WorkspaceItemInfo wii : fi.contents) {
                                    if (wii != null && cn.equals(wii.getTargetComponent())
                                            && java.util.Objects.equals(wii.user, itemInfo.user)) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        }) != null) {
                    return null;
                }
                return new AddToHomeScreen<>(activityContext, itemInfo, originalView);
            };

    private static class AddToHomeScreen<T extends ActivityContext> extends SystemShortcut<T> {
        AddToHomeScreen(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_add_to_home, R.string.add_to_home_screen, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (mTarget instanceof com.android.launcher3.Launcher) {
                com.android.launcher3.Launcher launcher = (com.android.launcher3.Launcher) mTarget;
                if (launcher.getAppLibraryPage() != null && launcher.getAppLibraryPage().isInSearchMode()) {
                    launcher.getAppLibraryPage().exitSearchMode(false);
                }
                com.android.launcher3.applibrary.view.SpotlightSearchOverlay spotlight =
                        com.android.launcher3.applibrary.view.SpotlightSearchOverlay.getOpen(launcher);
                if (spotlight != null) {
                    spotlight.close(false);
                }
                ComponentName cn = mItemInfo.getTargetComponent();
                if (cn != null) {
                    com.android.launcher3.util.RemovedAppStore.unmarkRemoved(
                            launcher, cn.flattenToString());
                }
                WorkspaceItemInfo itemToAdd = null;
                if (mItemInfo instanceof com.android.launcher3.model.data.WorkspaceItemFactory) {
                    itemToAdd = ((com.android.launcher3.model.data.WorkspaceItemFactory) mItemInfo)
                            .makeWorkspaceItem(launcher);
                } else if (mItemInfo instanceof WorkspaceItemInfo) {
                    itemToAdd = new WorkspaceItemInfo((WorkspaceItemInfo) mItemInfo);
                }
                if (itemToAdd != null) {
                    launcher.getModel().addAndBindAddedWorkspaceItems(
                            java.util.Collections.singletonList(android.util.Pair.create(itemToAdd, null)));
                    foundation.e.bliss.glass.LiquidGlassToast.show(launcher,
                            launcher.getString(R.string.add_to_home_screen),
                            R.drawable.ic_ios_check_circle);
                }
            }
        }
    }

    public static final Factory<ActivityContext> EDIT_HOME_SCREEN =
            (activityContext, itemInfo, originalView) -> {
                if (originalView == null) {
                    return null;
                }
                return new EditHomeScreen<>(activityContext, itemInfo, originalView);
            };

    private static class EditHomeScreen<T extends ActivityContext> extends SystemShortcut<T> {
        EditHomeScreen(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_setting, R.string.edit_home_screen, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (mTarget instanceof com.android.launcher3.Launcher launcher) {
                com.android.launcher3.Workspace<?> ws = launcher.getWorkspace();
                if (ws != null) {
                    if (ws.isAppLibraryCurrentPage()) {
                        int appLibIndex = ws.getPageIndexForScreenId(
                                com.android.launcher3.Workspace.APP_LIBRARY_SCREEN_ID);
                        ws.snapToPage(Math.max(0, appLibIndex - 1));
                    }
                    ws.wobbleLayouts(true);
                }
            }
        }
    }

    public static final Factory<ActivityContext> CHANGE_CATEGORY =
            (activityContext, itemInfo, originalView) -> {
                if (!(activityContext instanceof com.android.launcher3.Launcher)) {
                    return null;
                }
                if (itemInfo == null || itemInfo.getTargetComponent() == null) {
                    return null;
                }
                if (itemInfo.itemType == com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                        || itemInfo.itemType == com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET) {
                    return null;
                }
                return new ChangeCategory<>(activityContext, itemInfo, originalView);
            };

    private static class ChangeCategory<T extends ActivityContext> extends SystemShortcut<T> {
        ChangeCategory(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(R.drawable.ic_change_category, R.string.change_category, target, itemInfo, originalView);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (mTarget instanceof com.android.launcher3.Launcher launcher) {
                com.android.launcher3.applibrary.view.ChangeCategoryPickerView.show(launcher, mItemInfo);
            }
        }
    }

    public static <T extends ActivityContext> void dismissTaskMenuView(T activity) {
        AbstractFloatingView.closeOpenViews(activity, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }

    public static class CustomShortcut extends SystemShortcut<ActivityContext> {
        private final int mCustomIconResId;
        private final CharSequence mCustomLabel;
        private final boolean mIsDestructive;
        private final View.OnClickListener mListener;

        public CustomShortcut(ActivityContext target, ItemInfo itemInfo, View originalView,
                int iconResId, CharSequence label, boolean isDestructive, View.OnClickListener listener) {
            super(iconResId, 0, target, itemInfo, originalView);
            mCustomIconResId = iconResId;
            mCustomLabel = label;
            mIsDestructive = isDestructive;
            mListener = listener;
        }

        @Override
        public void setIconAndLabelFor(View iconView, TextView labelView) {
            if (mCustomIconResId != 0) {
                iconView.setBackgroundResource(mCustomIconResId);
                int tint = mIsDestructive ? android.graphics.Color.parseColor("#FF3B30") : android.graphics.Color.BLACK;
                if (iconView.getBackground() != null) {
                    iconView.getBackground().mutate().setTint(tint);
                }
            }
            labelView.setText(mCustomLabel);
            int textColor = mIsDestructive ? android.graphics.Color.parseColor("#FF3B30") : android.graphics.Color.BLACK;
            labelView.setTextColor(textColor);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (mListener != null) {
                mListener.onClick(view);
            }
        }
    }
}
```

---

# src/com/android/launcher3/shortcuts/DeepShortcutView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/shortcuts/DeepShortcutView.java`  
**Lines**: 195 | **Bytes**: 7087

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

package com.android.launcher3.shortcuts;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BubbleTextHolder;

/**
 * A {@link android.widget.FrameLayout} that contains an icon and a {@link BubbleTextView} for text.
 * This lets us animate the child BubbleTextView's background (transparent ripple) separately from
 * the {@link DeepShortcutView} background color.
 */
public class DeepShortcutView extends FrameLayout implements BubbleTextHolder {

    private static final Point sTempPoint = new Point();

    private final Drawable mTransparentDrawable = new ColorDrawable(Color.TRANSPARENT);

    private BubbleTextView mBubbleText;
    private View mIconView;

    private WorkspaceItemInfo mInfo;
    private ShortcutInfo mDetail;

    public DeepShortcutView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBubbleText = findViewById(R.id.bubble_text);
        mBubbleText.setHideBadge(true);
        mIconView = findViewById(R.id.icon);
        tryUpdateTextBackground();
    }

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
        tryUpdateTextBackground();
    }

    @Override
    public void setBackgroundResource(int resid) {
        super.setBackgroundResource(resid);
        tryUpdateTextBackground();
    }

    /**
     * Updates the text background to match the shape of this background (when applicable).
     */
    private void tryUpdateTextBackground() {
        if (!(getBackground() instanceof GradientDrawable) || mBubbleText == null) {
            return;
        }
        GradientDrawable background = (GradientDrawable) getBackground();

        int color = Themes.getAttrColor(getContext(), android.R.attr.colorControlHighlight);
        GradientDrawable backgroundMask = new GradientDrawable();
        backgroundMask.setColor(color);
        backgroundMask.setShape(GradientDrawable.RECTANGLE);
        if (background.getCornerRadii() != null) {
            backgroundMask.setCornerRadii(background.getCornerRadii());
        } else {
            backgroundMask.setCornerRadius(background.getCornerRadius());
        }

        RippleDrawable drawable = new RippleDrawable(ColorStateList.valueOf(color),
                mTransparentDrawable, backgroundMask);
        
        // Setting the background resets the view padding. We must preserve it.
        int padStart = mBubbleText.getPaddingStart();
        int padTop = mBubbleText.getPaddingTop();
        int padEnd = mBubbleText.getPaddingEnd();
        int padBottom = mBubbleText.getPaddingBottom();
        
        mBubbleText.setBackground(drawable);
        
        mBubbleText.setPaddingRelative(padStart, padTop, padEnd, padBottom);
    }

    @Override
    public BubbleTextView getBubbleText() {
        return mBubbleText;
    }

    public void setWillDrawIcon(boolean willDraw) {
        mIconView.setVisibility(willDraw ? View.VISIBLE : View.INVISIBLE);
    }

    public boolean willDrawIcon() {
        return mIconView.getVisibility() == View.VISIBLE;
    }

    /**
     * Returns the position of the center of the icon relative to the container.
     */
    public Point getIconCenter() {
        sTempPoint.y = sTempPoint.x = getMeasuredHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            sTempPoint.x = getMeasuredWidth() - sTempPoint.x;
        }
        return sTempPoint;
    }

    /** package private **/
    public void applyShortcutInfo(WorkspaceItemInfo info, ShortcutInfo detail,
            PopupContainerWithArrow container) {
        mInfo = info;
        mDetail = detail;
        mBubbleText.applyFromWorkspaceItem(info);
        mIconView.setBackground(mBubbleText.getIcon());

        // Use the long label as long as it exists and fits.
        CharSequence longLabel = mDetail.getLongLabel();
        int availableWidth = mBubbleText.getWidth() - mBubbleText.getTotalPaddingLeft()
                - mBubbleText.getTotalPaddingRight();
        boolean usingLongLabel = !TextUtils.isEmpty(longLabel)
                && mBubbleText.getPaint().measureText(longLabel.toString()) <= availableWidth;
        mBubbleText.setText(usingLongLabel ? longLabel : mDetail.getShortLabel());

        // TODO: Add the click handler to this view directly and not the child view.
        mBubbleText.setOnClickListener(container.getItemClickListener());
        mBubbleText.setOnLongClickListener(container.getItemDragHandler());
        mBubbleText.setOnTouchListener(container.getItemDragHandler());
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode()) {
            mBubbleText.setTextColor(android.graphics.Color.BLACK);
        }
        setVisibility(VISIBLE);
    }

    /**
     * Returns the shortcut info that is suitable to be added on the homescreen
     */
    public WorkspaceItemInfo getFinalInfo() {
        final WorkspaceItemInfo badged = new WorkspaceItemInfo(mInfo);
        // Queue an update task on the worker thread. This ensures that the badged
        // shortcut eventually gets its icon updated.
        Launcher.getLauncher(getContext()).getModel()
                .updateAndBindWorkspaceItem(badged, mDetail);
        return badged;
    }

    public View getIconView() {
        return mIconView;
    }

    public ShortcutInfo getDetail() {
        return mDetail;
    }
}
```

---

# src/com/android/launcher3/LauncherModel.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/LauncherModel.java`  
**Lines**: 719 | **Bytes**: 28410

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

package com.android.launcher3;

import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;

import static com.android.launcher3.LauncherAppState.ACTION_FORCE_ROLOAD;
import static com.android.launcher3.config.FeatureFlags.IS_STUDIO_BUILD;
import static com.android.launcher3.model.PackageUpdatedTask.OP_UPDATE;
import static com.android.launcher3.pm.UserCache.ACTION_PROFILE_AVAILABLE;
import static com.android.launcher3.pm.UserCache.ACTION_PROFILE_UNAVAILABLE;
import static com.android.launcher3.testing.shared.TestProtocol.sDebugTracing;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.AddWorkspaceItemsTask;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.CacheDataUpdatedTask;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.LauncherBinder;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.ModelDbController;
import com.android.launcher3.model.ModelDelegate;
import com.android.launcher3.model.ModelLauncherCallbacks;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.PackageInstallStateChangedTask;
import com.android.launcher3.model.PackageUpdatedTask;
import com.android.launcher3.model.ReloadStringCacheTask;
import com.android.launcher3.model.ShortcutsChangedTask;
import com.android.launcher3.model.UserLockStateChangedTask;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import foundation.e.bliss.LauncherAppMonitor;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel implements InstallSessionTracker.Callback {
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    @NonNull
    private final LauncherAppState mApp;
    @NonNull
    private final ModelDbController mModelDbController;
    @NonNull
    private final Object mLock = new Object();
    @Nullable
    private LoaderTask mLoaderTask;
    private boolean mIsLoaderTaskRunning;

    // only allow this once per reboot to reload work apps
    private boolean mShouldReloadWorkProfile = true;

    // Indicates whether the current model data is valid or not.
    // We start off with everything not loaded. After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery. This is only ever touched from the loader thread.
    private boolean mModelLoaded;
    private boolean mModelDestroyed = false;
    public boolean isModelLoaded() {
        synchronized (mLock) {
            return mModelLoaded && mLoaderTask == null && !mModelDestroyed;
        }
    }

    @NonNull
    private final ArrayList<Callbacks> mCallbacksList = new ArrayList<>(1);

    // < only access in worker thread >
    @NonNull
    private final AllAppsList mBgAllAppsList;

    /**
     * All the static data should be accessed on the background thread, A lock should be acquired
     * on this object when accessing any data from this model.
     */
    @NonNull
    public final BgDataModel mBgDataModel = new BgDataModel();

    @NonNull
    private final ModelDelegate mModelDelegate;

    private int mLastLoadId = -1;

    // Runnable to check if the shortcuts permission has changed.
    @NonNull
    private final Runnable mDataValidationCheck = new Runnable() {
        @Override
        public void run() {
            if (mModelLoaded) {
                mModelDelegate.validateData();
            }
        }
    };

    LauncherModel(@NonNull final Context context, @NonNull final LauncherAppState app,
            @NonNull final IconCache iconCache, @NonNull final AppFilter appFilter,
            final boolean isPrimaryInstance) {
        mApp = app;
        mModelDbController = new ModelDbController(context);
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
        mModelDelegate = ModelDelegate.newInstance(context, app, mBgAllAppsList, mBgDataModel,
                isPrimaryInstance);
    }

    @NonNull
    public ModelDelegate getModelDelegate() {
        return mModelDelegate;
    }

    public ModelDbController getModelDbController() {
        return mModelDbController;
    }

    public ModelLauncherCallbacks newModelCallbacks() {
        return new ModelLauncherCallbacks(this::enqueueModelUpdateTask);
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(
            @NonNull final List<Pair<ItemInfo, Object>> itemList) {
        for (Callbacks cb : getCallbacks()) {
            cb.preAddApps();
        }
        enqueueModelUpdateTask(new AddWorkspaceItemsTask(itemList, true));
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(List<Pair<ItemInfo, Object>> itemList,
                                              boolean animated, boolean ignoreLoaded) {
        for (Callbacks cb : getCallbacks()) {
            cb.preAddApps();
        }
        if (ignoreLoaded) {
            itemList.sort(Comparator.comparing(item -> {
                assert item.first.title != null;
                return item.first.title.toString().toLowerCase();
            }));
        }
        AddWorkspaceItemsTask addWorkspaceItemsTask =
                new AddWorkspaceItemsTask(itemList, ignoreLoaded);
        addWorkspaceItemsTask.setEnableAnimated(animated);
        enqueueModelUpdateTask(addWorkspaceItemsTask);
    }

    @NonNull
    public ModelWriter getWriter(final boolean verifyChanges, CellPosMapper cellPosMapper,
            @Nullable final Callbacks owner) {
        return new ModelWriter(mApp.getContext(), this, mBgDataModel, verifyChanges, cellPosMapper,
                owner);
    }

    /**
     * Called when the icon for an app changes, outside of package event
     */
    @WorkerThread
    public void onAppIconChanged(@NonNull final String packageName,
            @NonNull final UserHandle user) {
        // Update the icon for the calendar package
        Context context = mApp.getContext();
        enqueueModelUpdateTask(new PackageUpdatedTask(OP_UPDATE, user, packageName));

        List<ShortcutInfo> pinnedShortcuts = new ShortcutRequest(context, user)
                .forPackage(packageName).query(ShortcutRequest.PINNED);
        if (!pinnedShortcuts.isEmpty()) {
            enqueueModelUpdateTask(new ShortcutsChangedTask(packageName, pinnedShortcuts, user,
                    false));
        }
    }

    /**
     * Called when the workspace items have drastically changed
     */
    public void onWorkspaceUiChanged() {
        MODEL_EXECUTOR.execute(mModelDelegate::workspaceLoadComplete);
    }

    /**
     * Called when the model is destroyed
     */
    public void destroy() {
        mModelDestroyed = true;
        MODEL_EXECUTOR.execute(mModelDelegate::destroy);
    }

    public void onBroadcastIntent(@NonNull final Intent intent) {
        if (DEBUG_RECEIVER || sDebugTracing) Log.d(TAG, "onReceive intent=" + intent);
        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (ACTION_DEVICE_POLICY_RESOURCE_UPDATED.equals(action)) {
            enqueueModelUpdateTask(new ReloadStringCacheTask(mModelDelegate));
        } else if (IS_STUDIO_BUILD && ACTION_FORCE_ROLOAD.equals(action)) {
            for (Callbacks cb : getCallbacks()) {
                if (cb instanceof Launcher) {
                    ((Launcher) cb).recreate();
                }
            }
        }
        LauncherAppMonitor.getInstanceNoCreate().onReceive(intent);
    }

    /**
     * Called then there use a user event
     * @see UserCache#addUserEventListener
     */
    public void onUserEvent(UserHandle user, String action) {
        if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action)
                && mShouldReloadWorkProfile) {
            mShouldReloadWorkProfile = false;
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
            mShouldReloadWorkProfile = false;
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
        } else if (UserCache.ACTION_PROFILE_LOCKED.equals(action)
                || UserCache.ACTION_PROFILE_UNLOCKED.equals(action)) {
            enqueueModelUpdateTask(new UserLockStateChangedTask(
                    user, UserCache.ACTION_PROFILE_UNLOCKED.equals(action)));
        } else if (UserCache.ACTION_PROFILE_ADDED.equals(action)
                || UserCache.ACTION_PROFILE_REMOVED.equals(action)) {
            forceReload();
        } else if (ACTION_PROFILE_AVAILABLE.equals(action)
                || ACTION_PROFILE_UNAVAILABLE.equals(action)) {
            /*
             * This broadcast is only available when android.os.Flags.allowPrivateProfile() is set.
             * For Work-profile this broadcast will be sent in addition to
             * ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE.
             * So effectively, this if block only handles the non-work profile case.
             */
            enqueueModelUpdateTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE, user));
        }
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void forceReload() {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mModelLoaded to true later
            stopLoader();
            mModelLoaded = false;
        }

        // Start the loader if launcher is already running, otherwise the loader will run,
        // the next time launcher starts
        if (hasCallbacks()) {
            startLoader();
        }
    }

    /**
     * Rebinds all existing callbacks with already loaded model
     */
    public void rebindCallbacks() {
        if (hasCallbacks()) {
            startLoader();
        }
    }

    /**
     * Removes an existing callback
     */
    public void removeCallbacks(@NonNull final Callbacks callbacks) {
        synchronized (mCallbacksList) {
            Preconditions.assertUIThread();
            if (mCallbacksList.remove(callbacks)) {
                if (stopLoader()) {
                    // Rebind existing callbacks
                    startLoader();
                }
            }
        }
    }

    /**
     * Adds a callbacks to receive model updates
     * @return true if workspace load was performed synchronously
     */
    public boolean addCallbacksAndLoad(@NonNull final Callbacks callbacks) {
        synchronized (mLock) {
            addCallbacks(callbacks);
            return startLoader(new Callbacks[] { callbacks });

        }
    }

    /**
     * Adds a callbacks to receive model updates
     */
    public void addCallbacks(@NonNull final Callbacks callbacks) {
        Preconditions.assertUIThread();
        synchronized (mCallbacksList) {
            mCallbacksList.add(callbacks);
        }
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     * @return true if the page could be bound synchronously.
     */
    public boolean startLoader() {
        return startLoader(new Callbacks[0]);
    }

    private boolean startLoader(@NonNull final Callbacks[] newCallbacks) {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        ItemInstallQueue.INSTANCE.get(mApp.getContext())
                .pauseModelPush(ItemInstallQueue.FLAG_LOADER_RUNNING);
        synchronized (mLock) {
            // If there is already one running, tell it to stop.
            boolean wasRunning = stopLoader();
            boolean bindDirectly = mModelLoaded && !mIsLoaderTaskRunning;
            boolean bindAllCallbacks = wasRunning || !bindDirectly || newCallbacks.length == 0;
            final Callbacks[] callbacksList = bindAllCallbacks ? getCallbacks() : newCallbacks;

            if (callbacksList.length > 0) {
                // Clear any pending bind-runnables from the synchronized load process.
                for (Callbacks cb : callbacksList) {
                    MAIN_EXECUTOR.execute(cb::clearPendingBinds);
                }

                LauncherBinder launcherBinder = new LauncherBinder(
                        mApp, mBgDataModel, mBgAllAppsList, callbacksList);
                if (bindDirectly) {
                    // Divide the set of loaded items into those that we are binding synchronously,
                    // and everything else that is to be bound normally (asynchronously).
                    launcherBinder.bindWorkspace(bindAllCallbacks, /* isBindSync= */ true);
                    // For now, continue posting the binding of AllApps as there are other
                    // issues that arise from that.
                    launcherBinder.bindAllApps();
                    launcherBinder.bindDeepShortcuts();
                    launcherBinder.bindWidgets();
                    if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                        mModelDelegate.bindAllModelExtras(callbacksList);
                    }
                    return true;
                } else {
                    stopLoader();
                    mLoaderTask = new LoaderTask(
                            mApp, mBgAllAppsList, mBgDataModel, mModelDelegate, launcherBinder);

                    // Always post the loader task, instead of running directly
                    // (even on same thread) so that we exit any nested synchronized blocks
                    MODEL_EXECUTOR.post(mLoaderTask);
                }
            }
        }
        return false;
    }

    /**
     * If there is already a loader task running, tell it to stop.
     * @return true if an existing loader was stopped.
     */
    private boolean stopLoader() {
        synchronized (mLock) {
            LoaderTask oldTask = mLoaderTask;
            mLoaderTask = null;
            if (oldTask != null) {
                oldTask.stopLocked();
                return true;
            }
            return false;
        }
    }

    /**
     * Loads the model if not loaded
     * @param callback called with the data model upon successful load or null on model thread.
     */
    public void loadAsync(@NonNull final Consumer<BgDataModel> callback) {
        synchronized (mLock) {
            if (!mModelLoaded && !mIsLoaderTaskRunning) {
                startLoader();
            }
        }
        MODEL_EXECUTOR.post(() -> callback.accept(isModelLoaded() ? mBgDataModel : null));
    }

    @Override
    public void onInstallSessionCreated(@NonNull final PackageInstallInfo sessionInfo) {
        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            enqueueModelUpdateTask(new BaseModelUpdateTask() {
                @Override
                public void execute(@NonNull final LauncherAppState app,
                        @NonNull final BgDataModel dataModel, @NonNull final AllAppsList apps) {
                    apps.addPromiseApp(app.getContext(), sessionInfo);
                    bindApplicationsIfNeeded();
                }
            });
        }
    }

    @Override
    public void onSessionFailure(@NonNull final String packageName,
            @NonNull final UserHandle user) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(@NonNull final LauncherAppState app,
                    @NonNull final BgDataModel dataModel, @NonNull final AllAppsList apps) {
                IconCache iconCache = app.getIconCache();
                final IntSet removedIds = new IntSet();
                HashSet<WorkspaceItemInfo> archivedItemsToCacheRefresh = new HashSet<>();
                HashSet<String> archivedPackagesToCacheRefresh = new HashSet<>();
                synchronized (dataModel) {
                    for (ItemInfo info : dataModel.itemsIdMap) {
                        if (info instanceof WorkspaceItemInfo
                                && ((WorkspaceItemInfo) info).hasPromiseIconUi()
                                && user.equals(info.user)
                                && info.getIntent() != null) {
                            if (TextUtils.equals(packageName, info.getIntent().getPackage())) {
                                removedIds.add(info.id);
                            }
                            if (((WorkspaceItemInfo) info).isArchived()) {
                                WorkspaceItemInfo workspaceItem = (WorkspaceItemInfo) info;
                                // Remove package cache icon for archived app in case of a session
                                // failure.
                                mApp.getIconCache().removeIconsForPkg(packageName, user);
                                // Refresh icons on the workspace for archived apps.
                                iconCache.getTitleAndIcon(workspaceItem,
                                        workspaceItem.usingLowResIcon());
                                archivedPackagesToCacheRefresh.add(packageName);
                                archivedItemsToCacheRefresh.add(workspaceItem);
                            }
                        }
                    }
                    if (!archivedPackagesToCacheRefresh.isEmpty()) {
                        apps.updateIconsAndLabels(archivedPackagesToCacheRefresh, user);
                    }
                }

                if (!removedIds.isEmpty()) {
                    deleteAndBindComponentsRemoved(
                            ItemInfoMatcher.ofItemIds(removedIds),
                            "removed because install session failed");
                }
                if (!archivedItemsToCacheRefresh.isEmpty()) {
                    bindUpdatedWorkspaceItems(archivedItemsToCacheRefresh.stream().toList());
                    bindApplicationsIfNeeded();
                }
            }
        });
    }

    @Override
    public void onPackageStateChanged(@NonNull final PackageInstallInfo installInfo) {
        enqueueModelUpdateTask(new PackageInstallStateChangedTask(installInfo));
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    @Override
    public void onUpdateSessionDisplay(@NonNull final PackageUserKey key,
            @NonNull final PackageInstaller.SessionInfo info) {
        mApp.getIconCache().updateSessionCache(key, info);

        HashSet<String> packages = new HashSet<>();
        packages.add(key.mPackageName);
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_SESSION_UPDATE, key.mUser, packages));
    }

    public class LoaderTransaction implements AutoCloseable {

        @NonNull
        private final LoaderTask mTask;

        private LoaderTransaction(@NonNull final LoaderTask task) throws CancellationException {
            synchronized (mLock) {
                if (mLoaderTask != task) {
                    throw new CancellationException("Loader already stopped");
                }
                mLastLoadId++;
                mTask = task;
                mIsLoaderTaskRunning = true;
                mModelLoaded = false;
            }
        }

        public void commit() {
            synchronized (mLock) {
                // Everything loaded bind the data.
                mModelLoaded = true;
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == mTask) {
                    mLoaderTask = null;
                }
                mIsLoaderTaskRunning = false;
            }
        }
    }

    public LoaderTransaction beginLoader(@NonNull final LoaderTask task)
            throws CancellationException {
        return new LoaderTransaction(task);
    }

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed.
     * Current implementation simply reloads the workspace, but it can be optimized to
     * use partial updates similar to {@link UserCache}
     */
    public void validateModelDataOnResume() {
        MODEL_EXECUTOR.getHandler().removeCallbacks(mDataValidationCheck);
        MODEL_EXECUTOR.post(mDataValidationCheck);
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(@NonNull final HashSet<String> updatedPackages,
            @NonNull final UserHandle user) {
        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        enqueueModelUpdateTask(new CacheDataUpdatedTask(
                CacheDataUpdatedTask.OP_CACHE_UPDATE, user, updatedPackages));
    }

    /**
     * Called when the labels for the widgets has updated in the icon cache.
     */
    public void onWidgetLabelsUpdated(@NonNull final HashSet<String> updatedPackages,
            @NonNull final UserHandle user) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(@NonNull final LauncherAppState app,
                    @NonNull final BgDataModel dataModel, @NonNull final AllAppsList apps) {
                dataModel.widgetsModel.onPackageIconsUpdated(updatedPackages, user, app);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    public void enqueueModelUpdateTask(@NonNull final ModelUpdateTask task) {
        if (mModelDestroyed) {
            return;
        }
        task.init(mApp, this, mBgDataModel, mBgAllAppsList, MAIN_EXECUTOR);
        MODEL_EXECUTOR.execute(task);
    }

    /**
     * A task to be executed on the current callbacks on the UI thread.
     * If there is no current callbacks, the task is ignored.
     */
    public interface CallbackTask {

        void execute(@NonNull Callbacks callbacks);
    }

    /**
     * A runnable which changes/updates the data model of the launcher based on certain events.
     */
    public interface ModelUpdateTask extends Runnable {

        /**
         * Called before the task is posted to initialize the internal state.
         */
        void init(@NonNull LauncherAppState app, @NonNull LauncherModel model,
                @NonNull BgDataModel dataModel, @NonNull AllAppsList allAppsList,
                @NonNull Executor uiExecutor);

    }

    public void updateAndBindWorkspaceItem(@NonNull final WorkspaceItemInfo si,
            @NonNull final ShortcutInfo info) {
        updateAndBindWorkspaceItem(() -> {
            si.updateFromDeepShortcutInfo(info, mApp.getContext());
            mApp.getIconCache().getShortcutIcon(si, info);
            return si;
        });
    }

    /**
     * Utility method to update a shortcut on the background thread.
     */
    public void updateAndBindWorkspaceItem(
            @NonNull final Supplier<WorkspaceItemInfo> itemProvider) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(@NonNull final LauncherAppState app,
                    @NonNull final BgDataModel dataModel, @NonNull final AllAppsList apps) {
                WorkspaceItemInfo info = itemProvider.get();
                getModelWriter().updateItemInDatabase(info);
                ArrayList<WorkspaceItemInfo> update = new ArrayList<>();
                update.add(info);
                bindUpdatedWorkspaceItems(update);
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(@Nullable final PackageUserKey packageUser) {
        enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(@NonNull final LauncherAppState app,
                    @NonNull final BgDataModel dataModel, @NonNull final AllAppsList apps) {
                dataModel.widgetsModel.update(app, packageUser);
                bindUpdatedWidgets(dataModel);
            }
        });
    }

    public void dumpState(@Nullable final String prefix, @Nullable final FileDescriptor fd,
            @NonNull final PrintWriter writer, @NonNull final String[] args) {
        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "All apps list: size=" + mBgAllAppsList.data.size());
            for (AppInfo info : mBgAllAppsList.data) {
                writer.println(prefix + "   title=\"" + info.title
                        + "\" bitmapIcon=" + info.bitmap.icon
                        + " componentName=" + info.componentName.getPackageName());
            }
            writer.println();
        }
        mModelDelegate.dump(prefix, fd, writer, args);
        mBgDataModel.dump(prefix, fd, writer, args);
    }

    /**
     * Returns true if there are any callbacks attached to the model
     */
    public boolean hasCallbacks() {
        synchronized (mCallbacksList) {
            return !mCallbacksList.isEmpty();
        }
    }

    /**
     * Returns an array of currently attached callbacks
     */
    @NonNull
    public Callbacks[] getCallbacks() {
        synchronized (mCallbacksList) {
            return mCallbacksList.toArray(new Callbacks[mCallbacksList.size()]);
        }
    }

    /**
     * Returns the ID for the last model load. If the load ID doesn't match for a transaction, the
     * transaction should be ignored.
     */
    public int getLastLoadId() {
        return mLastLoadId;
    }
}
```

---

# src/com/android/launcher3/model/ModelWriter.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/ModelWriter.java`  
**Lines**: 581 | **Bytes**: 23312

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

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.getFavoritesTableName;
import static com.android.launcher3.provider.LauncherDbUtils.itemIdMatch;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.widget.LauncherWidgetHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class for handling model updates.
 */
public class ModelWriter {

    private static final String TAG = "ModelWriter";

    private final Context mContext;
    private final LauncherModel mModel;
    private final BgDataModel mBgDataModel;
    private final LooperExecutor mUiExecutor;

    @Nullable
    private final Callbacks mOwner;

    private final boolean mVerifyChanges;

    // Keep track of delete operations that occur when an Undo option is present; we may not commit.
    private final List<ModelTask> mDeleteRunnables = new ArrayList<>();
    private boolean mPreparingToUndo;
    private final CellPosMapper mCellPosMapper;

    public ModelWriter(Context context, LauncherModel model, BgDataModel dataModel,
            boolean verifyChanges, CellPosMapper cellPosMapper, @Nullable Callbacks owner) {
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mVerifyChanges = verifyChanges;
        mOwner = owner;
        mCellPosMapper = cellPosMapper;
        mUiExecutor = Executors.MAIN_EXECUTOR;
    }

    private void updateItemInfoProps(
            ItemInfo item, int container, int screenId, int cellX, int cellY) {
        CellPos modelPos = mCellPosMapper.mapPresenterToModel(cellX, cellY, screenId, container);

        android.util.Log.d("ICON_TRACE", "updateItemInfoProps id=" + item.id
                + " title=" + item.title
                + " container " + item.container + "->" + container
                + " screen " + item.screenId + "->" + modelPos.screenId
                + " cell(" + item.cellX + "," + item.cellY + ")->("
                + modelPos.cellX + "," + modelPos.cellY + ")",
                new Throwable("caller"));

        item.container = container;
        item.cellX = modelPos.cellX;
        item.cellY = modelPos.cellY;
        item.screenId = modelPos.screenId;
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public void addOrMoveItemInDatabase(ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        if (item.id == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY);
        }
    }

    private void checkItemInfoLocked(int itemId, ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (!Utilities.IS_DEBUG_DEVICE && !FeatureFlags.IS_STUDIO_BUILD
                    && modelItem instanceof WorkspaceItemInfo
                    && item instanceof WorkspaceItemInfo) {
                if (modelItem.title.toString().equals(item.title.toString()) &&
                        modelItem.getIntent().filterEquals(item.getIntent()) &&
                        modelItem.id == item.id &&
                        modelItem.itemType == item.itemType &&
                        modelItem.container == item.container &&
                        modelItem.screenId == item.screenId &&
                        modelItem.cellX == item.cellX &&
                        modelItem.cellY == item.cellY &&
                        modelItem.spanX == item.spanX &&
                        modelItem.spanY == item.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    public void moveItemInDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        notifyItemModified(item);

        enqueueDeleteRunnable(new UpdateItemRunnable(item, () ->
                new ContentWriter(mContext)
                        .put(Favorites.CONTAINER, item.container)
                        .put(Favorites.CELLX, item.cellX)
                        .put(Favorites.CELLY, item.cellY)
                        .put(Favorites.RANK, item.rank)
                        .put(Favorites.SCREEN, item.screenId)));
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public void moveItemsInDatabase(final ArrayList<ItemInfo> items, int container, int screen) {
        ArrayList<ContentValues> contentValues = new ArrayList<>();
        int count = items.size();
        notifyOtherCallbacks(c -> c.bindItemsModified(items));

        for (int i = 0; i < count; i++) {
            ItemInfo item = items.get(i);
            updateItemInfoProps(item, container, screen, item.cellX, item.cellY);

            final ContentValues values = new ContentValues();
            values.put(Favorites.CONTAINER, item.container);
            values.put(Favorites.CELLX, item.cellX);
            values.put(Favorites.CELLY, item.cellY);
            values.put(Favorites.RANK, item.rank);
            values.put(Favorites.SCREEN, item.screenId);

            contentValues.add(values);
        }
        enqueueDeleteRunnable(new UpdateItemsRunnable(items, contentValues));
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    public void modifyItemInDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY, int spanX, int spanY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        item.spanX = spanX;
        item.spanY = spanY;
        notifyItemModified(item);
        new UpdateItemRunnable(item, () ->
                new ContentWriter(mContext)
                        .put(Favorites.CONTAINER, item.container)
                        .put(Favorites.CELLX, item.cellX)
                        .put(Favorites.CELLY, item.cellY)
                        .put(Favorites.RANK, item.rank)
                        .put(Favorites.SPANX, item.spanX)
                        .put(Favorites.SPANY, item.spanY)
                        .put(Favorites.SCREEN, item.screenId))
                .executeOnModelThread();
    }

    /**
     * Update an item to the database in a specified container.
     */
    public void updateItemInDatabase(ItemInfo item) {
        notifyItemModified(item);
        new UpdateItemRunnable(item, () -> {
            ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            return writer;
        }).executeOnModelThread();
    }

    private void notifyItemModified(ItemInfo item) {
        notifyOtherCallbacks(c -> c.bindItemsModified(Collections.singletonList(item)));
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public void addItemToDatabase(final ItemInfo item,
            int container, int screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        item.id = mModel.getModelDbController().generateNewItemId();
        notifyOtherCallbacks(c -> c.bindItems(Collections.singletonList(item), false));

        ModelVerifier verifier = new ModelVerifier();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        newModelTask(() -> {
            // Write the item on background thread, as some properties might have been updated in
            // the background.
            final ContentWriter writer = new ContentWriter(mContext);
            item.onAddToDatabase(writer);
            writer.put(Favorites._ID, item.id);

            mModel.getModelDbController().insert(getFavoritesTableName(), writer.getValues(mContext));
            synchronized (mBgDataModel) {
                checkItemInfoLocked(item.id, item, stackTrace);
                mBgDataModel.addItem(mContext, item, true);
                verifier.verifyModel();
            }
        }).executeOnModelThread();
    }

    /**
     * Removes the specified item from the database
     */
    public void deleteItemFromDatabase(ItemInfo item, @Nullable final String reason) {
        deleteItemsFromDatabase(Arrays.asList(item), reason);
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(@NonNull final Predicate<ItemInfo> matcher,
            @Nullable final String reason) {
        deleteItemsFromDatabase(StreamSupport.stream(mBgDataModel.itemsIdMap.spliterator(), false)
                        .filter(matcher).collect(Collectors.toList()), reason);
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Collection<? extends ItemInfo> items,
            @Nullable final String reason) {
        ModelVerifier verifier = new ModelVerifier();
        FileLog.d(TAG, "removing items from db " + items.stream().map(
                (item) -> item.getTargetComponent() == null ? ""
                        : item.getTargetComponent().getPackageName()).collect(
                Collectors.joining(","))
                + ". Reason: [" + (TextUtils.isEmpty(reason) ? "unknown" : reason) + "]");
        notifyDelete(items);
        enqueueDeleteRunnable(newModelTask(() -> {
            for (ItemInfo item : items) {
                mModel.getModelDbController().delete(getFavoritesTableName(), itemIdMatch(item.id), null);
                mBgDataModel.removeItem(mContext, item);
                verifier.verifyModel();
            }
        }));
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public void deleteFolderAndContentsFromDatabase(final FolderInfo info) {
        ModelVerifier verifier = new ModelVerifier();
        notifyDelete(Collections.singleton(info));

        enqueueDeleteRunnable(newModelTask(() -> {
            mModel.getModelDbController().delete(getFavoritesTableName(),
                    Favorites.CONTAINER + "=" + info.id, null);
            mBgDataModel.removeItem(mContext, info.contents);
            info.contents.clear();

            mModel.getModelDbController().delete(getFavoritesTableName(),
                    Favorites._ID + "=" + info.id, null);
            mBgDataModel.removeItem(mContext, info);
            verifier.verifyModel();
        }));
    }

    /**
     * Deletes the widget info and the widget id.
     */
    public void deleteWidgetInfo(final LauncherAppWidgetInfo info, LauncherWidgetHolder holder,
            @Nullable final String reason) {
        notifyDelete(Collections.singleton(info));
        if (holder != null && !info.isCustomWidget() && info.isWidgetIdAllocated()) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            enqueueDeleteRunnable(newModelTask(() -> holder.deleteAppWidgetId(info.appWidgetId)));
        }
        deleteItemFromDatabase(info, reason);
    }

    private void notifyDelete(Collection<? extends ItemInfo> items) {
        notifyOtherCallbacks(c -> c.bindWorkspaceComponentsRemoved(ItemInfoMatcher.ofItems(items)));
    }

    /**
     * Delete operations tracked using {@link #enqueueDeleteRunnable} will only be called
     * if {@link #commitDelete} is called. Note that one of {@link #commitDelete()} or
     * {@link #abortDelete} MUST be called after this method, or else all delete
     * operations will remain uncommitted indefinitely.
     */
    public void prepareToUndoDelete() {
        if (!mPreparingToUndo) {
            if (!mDeleteRunnables.isEmpty() && FeatureFlags.IS_STUDIO_BUILD) {
                throw new IllegalStateException("There are still uncommitted delete operations!");
            }
            mDeleteRunnables.clear();
            mPreparingToUndo = true;
        }
    }

    /**
     * If {@link #prepareToUndoDelete} has been called, we store the Runnable to be run when
     * {@link #commitDelete()} is called (or abandoned if {@link #abortDelete} is called).
     * Otherwise, we run the Runnable immediately.
     */
    private void enqueueDeleteRunnable(ModelTask r) {
        if (mPreparingToUndo) {
            mDeleteRunnables.add(r);
        } else {
            r.executeOnModelThread();
        }
    }

    public void commitDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.forEach(ModelTask::executeOnModelThread);
        mDeleteRunnables.clear();
    }

    /**
     * Aborts a previous delete operation pending commit
     */
    public void abortDelete() {
        mPreparingToUndo = false;
        mDeleteRunnables.clear();
        // We do a full reload here instead of just a rebind because Folders change their internal
        // state when dragging an item out, which clobbers the rebind unless we load from the DB.
        mModel.forceReload();
    }

    private void notifyOtherCallbacks(CallbackTask task) {
        if (mOwner == null) {
            // If the call is happening from a model, it will take care of updating the callbacks
            return;
        }
        mUiExecutor.execute(() -> {
            for (Callbacks c : mModel.getCallbacks()) {
                if (c != mOwner) {
                    task.execute(c);
                }
            }
        });
    }

    private class UpdateItemRunnable extends UpdateItemBaseRunnable {
        private final ItemInfo mItem;
        private final Supplier<ContentWriter> mWriter;
        private final int mItemId;

        UpdateItemRunnable(ItemInfo item, Supplier<ContentWriter> writer) {
            mItem = item;
            mWriter = writer;
            mItemId = item.id;
        }

        @Override
        public void runImpl() {
            if (mItemId < 0) {
                return;
            }
            int rows = mModel.getModelDbController().update(
                    getFavoritesTableName(), mWriter.get().getValues(mContext), itemIdMatch(mItemId), null);
            if (rows == 0 && mItem != null && mItem.id >= 0
                    && (mItem.container == Favorites.CONTAINER_DESKTOP
                        || mItem.container == Favorites.CONTAINER_HOTSEAT
                        || mItem.container >= 0)) {
                ContentWriter writer = new ContentWriter(mContext);
                mItem.onAddToDatabase(writer);
                writer.put(Favorites._ID, mItemId);
                mModel.getModelDbController().insert(getFavoritesTableName(), writer.getValues(mContext));
            }
            updateItemArrays(mItem, mItemId);
        }
    }

    private class UpdateItemsRunnable extends UpdateItemBaseRunnable {
        private final ArrayList<ContentValues> mValues;
        private final ArrayList<ItemInfo> mItems;

        UpdateItemsRunnable(ArrayList<ItemInfo> items, ArrayList<ContentValues> values) {
            mValues = values;
            mItems = items;
        }

        @Override
        public void runImpl() {
            try (SQLiteTransaction t = mModel.getModelDbController().newTransaction()) {
                int count = mItems.size();
                for (int i = 0; i < count; i++) {
                    ItemInfo item = mItems.get(i);
                    final int itemId = item.id;
                    mModel.getModelDbController().update(
                            getFavoritesTableName(), mValues.get(i), itemIdMatch(itemId), null);
                    updateItemArrays(item, itemId);
                }
                t.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private abstract class UpdateItemBaseRunnable extends ModelTask {
        private final StackTraceElement[] mStackTrace;
        private final ModelVerifier mVerifier = new ModelVerifier();

        UpdateItemBaseRunnable() {
            mStackTrace = new Throwable().getStackTrace();
        }

        protected void updateItemArrays(ItemInfo item, int itemId) {
            // Lock on mBgLock *after* the db operation
            synchronized (mBgDataModel) {
                checkItemInfoLocked(itemId, item, mStackTrace);

                if (item.container != Favorites.CONTAINER_DESKTOP &&
                        item.container != Favorites.CONTAINER_HOTSEAT) {
                    // Item is in a folder, make sure this folder exists
                    if (!mBgDataModel.folders.containsKey(item.container)) {
                        // An items container is being set to a that of an item which is not in
                        // the list of Folders.
                        String msg = "item: " + item + " container being set to: " +
                                item.container + ", not in the list of folders";
                        Log.e(TAG, msg);
                    }
                }

                // Items are added/removed from the corresponding FolderInfo elsewhere, such
                // as in Workspace.onDrop. Here, we just add/remove them from the list of items
                // that are on the desktop, as appropriate
                ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
                if (modelItem != null &&
                        (modelItem.container == Favorites.CONTAINER_DESKTOP ||
                                modelItem.container == Favorites.CONTAINER_HOTSEAT)) {
                    switch (modelItem.itemType) {
                        case Favorites.ITEM_TYPE_APPLICATION:
                        case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case Favorites.ITEM_TYPE_FOLDER:
                        case Favorites.ITEM_TYPE_APP_PAIR:
                            if (!mBgDataModel.workspaceItems.contains(modelItem)) {
                                mBgDataModel.workspaceItems.add(modelItem);
                            }
                            break;
                        default:
                            break;
                    }
                } else {
                    mBgDataModel.workspaceItems.remove(modelItem);
                }
                mVerifier.verifyModel();
            }
        }
    }

    private abstract class ModelTask implements Runnable {

        private final int mLoadId = mBgDataModel.lastLoadId;

        @Override
        public final void run() {
            if (mLoadId != mModel.getLastLoadId()) {
                Log.d(TAG, "Model changed before the task could execute");
                return;
            }
            runImpl();
        }

        public final void executeOnModelThread() {
            MODEL_EXECUTOR.execute(this);
        }

        public abstract void runImpl();
    }

    private ModelTask newModelTask(Runnable r) {
        return new ModelTask() {
            @Override
            public void runImpl() {
                r.run();
            }
        };
    }

    /**
     * Utility class to verify model updates are propagated properly to the callback.
     */
    public class ModelVerifier {

        final int startId;

        ModelVerifier() {
            startId = mBgDataModel.lastBindId;
        }

        void verifyModel() {
            if (!mVerifyChanges || !mModel.hasCallbacks()) {
                return;
            }

            int executeId = mBgDataModel.lastBindId;

            mUiExecutor.post(() -> {
                int currentId = mBgDataModel.lastBindId;
                if (currentId > executeId) {
                    // Model was already bound after job was executed.
                    return;
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return;
                }

                // Bound model was changed between submitting the job and executing the job
                mModel.rebindCallbacks();
            });
        }
    }
}
```

---

# src/com/android/launcher3/model/BgDataModel.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/BgDataModel.java`  
**Lines**: 566 | **Bytes**: 22683

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
package com.android.launcher3.model;

import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY;

import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;
import static com.android.launcher3.model.WidgetsModel.GO_DISABLE_WIDGETS;
import static com.android.launcher3.shortcuts.ShortcutRequest.PINNED;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import foundation.e.bliss.utils.BlissDbUtils;

/**
 * All the data stored in-memory and managed by the LauncherModel
 */
public class BgDataModel {

    private static final String TAG = "BgDataModel";

    /**
     * Map of all the ItemInfos (shortcuts, folders, and widgets) created by
     * LauncherModel to their ids
     */
    public final IntSparseArrayMap<ItemInfo> itemsIdMap = new IntSparseArrayMap<>();

    /**
     * List of all the folders and shortcuts directly on the home screen (no widgets
     * or shortcuts within folders).
     */
    public final ArrayList<ItemInfo> workspaceItems = new ArrayList<>();

    /**
     * All LauncherAppWidgetInfo created by LauncherModel.
     */
    public final ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();

    /**
     * Map of id to FolderInfos of all the folders created by LauncherModel
     */
    public final IntSparseArrayMap<FolderInfo> folders = new IntSparseArrayMap<>();

    /**
     * Extra container based items
     */
    public final IntSparseArrayMap<FixedContainerItems> extraItems = new IntSparseArrayMap<>();

    /**
     * Maps all launcher activities to counts of their shortcuts.
     */
    public final HashMap<ComponentKey, Integer> deepShortcutMap = new HashMap<>();

    /**
     * Entire list of widgets.
     */
    public final WidgetsModel widgetsModel = new WidgetsModel();

    /**
     * Cache for strings used in launcher
     */
    public final StringCache stringCache = new StringCache();

    /**
     * Id when the model was last bound
     */
    public int lastBindId = 0;

    /**
     * Load id for which the callbacks were successfully bound
     */
    public int lastLoadId = -1;
    public boolean isFirstPagePinnedItemEnabled = BuildConfig.QSB_ON_FIRST_SCREEN
            && !ENABLE_SMARTSPACE_REMOVAL.get();

    /**
     * Clears all the data
     */
    public synchronized void clear() {
        workspaceItems.clear();
        appWidgets.clear();
        folders.clear();
        itemsIdMap.clear();
        deepShortcutMap.clear();
        extraItems.clear();
    }

    /**
     * Creates an array of valid workspace screens based on current items in the model.
     */
    public synchronized IntArray collectWorkspaceScreens() {
        IntSet screenSet = new IntSet();
        for (ItemInfo item: itemsIdMap) {
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                screenSet.add(item.screenId);
            }
        }
        if ((FeatureFlags.QSB_ON_FIRST_SCREEN.get()
                && !shouldShowFirstPageWidget())
                || screenSet.isEmpty()) {
            screenSet.add(Workspace.FIRST_SCREEN_ID);
        }
        return screenSet.getArray();
    }

    public synchronized void dump(String prefix, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        writer.println(prefix + "Data Model:");
        writer.println(prefix + " ---- workspace items ");
        for (int i = 0; i < workspaceItems.size(); i++) {
            writer.println(prefix + '\t' + workspaceItems.get(i).toString());
        }
        writer.println(prefix + " ---- appwidget items ");
        for (int i = 0; i < appWidgets.size(); i++) {
            writer.println(prefix + '\t' + appWidgets.get(i).toString());
        }
        writer.println(prefix + " ---- folder items ");
        for (int i = 0; i < folders.size(); i++) {
            writer.println(prefix + '\t' + folders.valueAt(i).toString());
        }
        writer.println(prefix + " ---- extra items ");
        for (int i = 0; i < extraItems.size(); i++) {
            writer.println(prefix + '\t' + extraItems.valueAt(i).toString());
        }
        writer.println(prefix + " ---- items id map ");
        for (int i = 0; i < itemsIdMap.size(); i++) {
            writer.println(prefix + '\t' + itemsIdMap.valueAt(i).toString());
        }

        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "shortcut counts ");
            for (Integer count : deepShortcutMap.values()) {
                writer.print(count + ", ");
            }
            writer.println();
        }
    }

    public synchronized void removeItem(Context context, ItemInfo... items) {
        removeItem(context, Arrays.asList(items));
    }

    public synchronized void removeItem(Context context, Iterable<? extends ItemInfo> items) {
        ArraySet<UserHandle> updatedDeepShortcuts = new ArraySet<>();
        for (ItemInfo item : items) {
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR:
                    folders.remove(item.id);
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        for (ItemInfo info : itemsIdMap) {
                            if (info.container == item.id) {
                                // We are deleting a folder which still contains items that
                                // think they are contained by that folder.
                                String msg = "deleting a collection (" + item + ") which still "
                                        + "contains items (" + info + ")";
                                Log.e(TAG, msg);
                            }
                        }
                    }
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    updatedDeepShortcuts.add(item.user);
                    // Fall through.
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                    appWidgets.remove(item);
                    break;
            }
            itemsIdMap.remove(item.id);
        }
        updatedDeepShortcuts.forEach(user -> updateShortcutPinnedState(context, user));
    }

    public synchronized void addItem(Context context, ItemInfo item, boolean newItem) {
        addItem(context, item, newItem, null);
    }

    public synchronized void addItem(
            Context context, ItemInfo item, boolean newItem, @Nullable LoaderMemoryLogger logger) {
        if (logger != null) {
            logger.addLog(
                    Log.DEBUG,
                    TAG,
                    String.format("Adding item to ID map: %s", item.toString()),
                    /* stackTrace= */ null);
        }
        if (item.id < 0) {
            Log.e(TAG, "Skipping invalid item with negative id: " + item);
            return;
        }
        itemsIdMap.put(item.id, item);
        switch (item.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
            case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR:
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    folders.put(item.id, (FolderInfo) item);
                    workspaceItems.add(item);
                } else {
                    Log.e(TAG, "Skipping folder with invalid container: " + item);
                }
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    workspaceItems.add(item);
                } else {
                    if (newItem) {
                        if (!folders.containsKey(item.container)) {
                            // Adding an item to a nonexistent collection.
                            String msg = "attempted to add item: " + item + " to a nonexistent app"
                                    + " collection";
                            Log.e(TAG, msg);
                        }
                    } else {
                        findOrMakeFolder(item.container).add((WorkspaceItemInfo) item, false);
                    }
                }
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                appWidgets.add((LauncherAppWidgetInfo) item);
                break;
        }
        if (newItem && item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            updateShortcutPinnedState(context, item.user);
        }
    }

    /**
     * Updates the deep shortucts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    public void updateShortcutPinnedState(Context context) {
        for (UserHandle user : UserCache.INSTANCE.get(context).getUserProfiles()) {
            updateShortcutPinnedState(context, user);
        }
    }

    /**
     * Updates the deep shortucts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    public synchronized void updateShortcutPinnedState(Context context, UserHandle user) {
        if (GO_DISABLE_WIDGETS) {
            return;
        }

        // Collect all system shortcuts
        QueryResult result = new ShortcutRequest(context, user)
                .query(PINNED | FLAG_GET_KEY_FIELDS_ONLY);
        if (!result.wasSuccess()) {
            return;
        }
        // Map of packageName to shortcutIds that are currently in the system
        Map<String, Set<String>> systemMap = result.stream()
                .collect(groupingBy(ShortcutInfo::getPackage,
                        mapping(ShortcutInfo::getId, Collectors.toSet())));

        // Collect all model shortcuts
        Stream.Builder<ShortcutKey> itemStream = Stream.builder();
        BlissDbUtils.queryDeepShortcutsFromDb(context).forEach(itemStream);
        // Map of packageName to shortcutIds that are currently in our model
        Map<String, Set<String>> modelMap = Stream.concat(
                    // Model shortcuts
                    itemStream.build(),
                    // Pending shortcuts
                    ItemInstallQueue.INSTANCE.get(context).getPendingShortcuts(user))
                .collect(groupingBy(ShortcutKey::getPackageName,
                        mapping(ShortcutKey::getId, Collectors.toSet())));

        // Check for diff
        for (Map.Entry<String, Set<String>> entry : modelMap.entrySet()) {
            Set<String> modelShortcuts = entry.getValue();
            Set<String> systemShortcuts = systemMap.remove(entry.getKey());
            if (systemShortcuts == null) {
                systemShortcuts = Collections.emptySet();
            }

            // Do not use .equals as it can vary based on the type of set
            if (systemShortcuts.size() != modelShortcuts.size()
                    || !systemShortcuts.containsAll(modelShortcuts)) {
                // Update system state for this package
                try {
                    context.getSystemService(LauncherApps.class).pinShortcuts(
                            entry.getKey(), new ArrayList<>(modelShortcuts), user);
                } catch (SecurityException | IllegalStateException e) {
                    Log.w(TAG, "Failed to pin shortcut", e);
                }
            }
        }

        // If there are any extra pinned shortcuts, remove them
        systemMap.keySet().forEach(packageName -> {
            // Update system state
            try {
                context.getSystemService(LauncherApps.class).pinShortcuts(
                        packageName, Collections.emptyList(), user);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "Failed to unpin shortcut", e);
            }
        });
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    public synchronized FolderInfo findOrMakeFolder(int id) {
        // See if a placeholder was created for us already
        FolderInfo folderInfo = folders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new FolderInfo();
            folders.put(id, folderInfo);
        }
        return folderInfo;
    }

    /**
     * Clear all the deep shortcut counts for the given package, and re-add the new shortcut counts.
     */
    public synchronized void updateDeepShortcutCounts(
            String packageName, UserHandle user, List<ShortcutInfo> shortcuts) {
        if (packageName != null) {
            Iterator<ComponentKey> keysIter = deepShortcutMap.keySet().iterator();
            while (keysIter.hasNext()) {
                ComponentKey next = keysIter.next();
                if (next.componentName.getPackageName().equals(packageName)
                        && next.user.equals(user)) {
                    keysIter.remove();
                }
            }
        }

        // Now add the new shortcuts to the map.
        for (ShortcutInfo shortcut : shortcuts) {
            boolean shouldShowInContainer = shortcut.isEnabled()
                    && (shortcut.isDeclaredInManifest() || shortcut.isDynamic())
                    && shortcut.getActivity() != null;
            if (shouldShowInContainer) {
                ComponentKey targetComponent
                        = new ComponentKey(shortcut.getActivity(), shortcut.getUserHandle());

                Integer previousCount = deepShortcutMap.get(targetComponent);
                deepShortcutMap.put(targetComponent, previousCount == null ? 1 : previousCount + 1);
            }
        }
    }

    /**
     * Returns a list containing all workspace items including widgets.
     */
    public synchronized ArrayList<ItemInfo> getAllWorkspaceItems() {
        ArrayList<ItemInfo> items = new ArrayList<>(workspaceItems.size() + appWidgets.size());
        items.addAll(workspaceItems);
        items.addAll(appWidgets);
        return items;
    }

    /**
     * Calls the provided {@code op} for all workspaceItems in the in-memory model (both persisted
     * items and dynamic/predicted items for the provided {@code userHandle}.
     * Note the call is not synchronized over the model, that should be handled by the called.
     */
    public void forAllWorkspaceItemInfos(UserHandle userHandle, Consumer<WorkspaceItemInfo> op) {
        for (ItemInfo info : itemsIdMap) {
            if (info instanceof WorkspaceItemInfo && userHandle.equals(info.user)) {
                op.accept((WorkspaceItemInfo) info);
            }
        }

        for (int i = extraItems.size() - 1; i >= 0; i--) {
            for (ItemInfo info : extraItems.valueAt(i).items) {
                if (info instanceof WorkspaceItemInfo && userHandle.equals(info.user)) {
                    op.accept((WorkspaceItemInfo) info);
                }
            }
        }
    }

    /**
     * An object containing items corresponding to a fixed container
     */
    public static class FixedContainerItems {

        public final int containerId;
        public final List<ItemInfo> items;

        public FixedContainerItems(int containerId, List<ItemInfo> items) {
            this.containerId = containerId;
            this.items = Collections.unmodifiableList(items);
        }

        @Override
        @NonNull
        public final String toString() {
            StringBuilder s = new StringBuilder();
            s.append("FixedContainerItems:");
            s.append(" id=").append(containerId);
            s.append(" itemCount=").append(items.size());
            for (int i = 0; i < items.size(); i++) {
                s.append(" item #").append(i).append(": ").append(items.get(i).toString());
            }
            return s.toString();
        }

    }


    public interface Callbacks {
        // If the launcher has permission to access deep shortcuts.
        int FLAG_HAS_SHORTCUT_PERMISSION = 1 << 0;
        // If quiet mode is enabled for any user
        int FLAG_QUIET_MODE_ENABLED = 1 << 1;
        // If launcher can change quiet mode
        int FLAG_QUIET_MODE_CHANGE_PERMISSION = 1 << 2;
        // If quiet mode is enabled for work profile user
        int FLAG_WORK_PROFILE_QUIET_MODE_ENABLED = 1 << 3;
        // If quiet mode is enabled for private profile user
        int FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED = 1 << 4;

        /**
         * Returns an IntSet of page ids to bind first, synchronously if possible
         * or an empty IntSet
         * @param orderedScreenIds All the page ids to be bound
         */
        @NonNull
        default IntSet getPagesToBindSynchronously(IntArray orderedScreenIds) {
            return new IntSet();
        }

        default void clearPendingBinds() { }
        default void startBinding() { }

        @Nullable
        default ItemInflater getItemInflater() {
            return null;
        }

        default void bindItems(@NonNull List<ItemInfo> shortcuts, boolean forceAnimateIcons) { }
        /** Alternate method to bind preinflated views */
        default void bindInflatedItems(@NonNull List<Pair<ItemInfo, View>> items) { }

        default void bindScreens(IntArray orderedScreenIds) { }
        default void setIsFirstPagePinnedItemEnabled(boolean isFirstPagePinnedItemEnabled) { }
        default void finishBindingItems(IntSet pagesBoundFirst) { }
        default void preAddApps() { }
        default void bindAppsAdded(IntArray newScreens,
                ArrayList<ItemInfo> addNotAnimated, ArrayList<ItemInfo> addAnimated) { }

        /**
         * Called when some persistent property of an item is modified
         */
        default void bindItemsModified(List<ItemInfo> items) { }

        /**
         * Binds updated incremental download progress
         */
        default void bindIncrementalDownloadProgressUpdated(AppInfo app) { }
        default void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) { }
        default void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) { }
        default void bindRestoreItemsChange(HashSet<ItemInfo> updates) { }
        default void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) { }
        default void bindAllWidgets(List<WidgetsListBaseEntry> widgets) { }
        default void bindSmartspaceWidget() { }

        /** Called when workspace has been bound. */
        default void onInitialBindComplete(@NonNull IntSet boundPages,
                @NonNull RunnableList pendingTasks,
                @NonNull RunnableList onCompleteSignal,
                int workspaceItemCount, boolean isBindSync) {
            pendingTasks.executeAllAndDestroy();
        }

        default void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap) { }

        /**
         * Binds extra item provided any external source
         */
        default void bindExtraContainerItems(FixedContainerItems item) { }

        default void bindAllApplications(AppInfo[] apps, int flags,
                Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        }

        /**
         * Binds the cache of string resources
         */
        default void bindStringCache(StringCache cache) { }
    }
}
```

---

# src/com/android/launcher3/model/LoaderTask.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/LoaderTask.java`  
**Lines**: 790 | **Bytes**: 34901

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

package com.android.launcher3.model;

import static com.android.launcher3.BuildConfig.WIDGET_ON_FIRST_SCREEN;
import static com.android.launcher3.Flags.enableLauncherBrMetricsFixed;
import static com.android.launcher3.LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE;
import static com.android.launcher3.LauncherPrefs.SHOULD_SHOW_SMARTSPACE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.getFavoritesTableName;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.SMARTSPACE_AS_A_WIDGET;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderGridOrganizer;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.folder.FolderNameProvider;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.icons.ComponentWithLabelAndIcon.ComponentWithIconCachingLogic;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.ShortcutCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.widget.WidgetInflater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

import foundation.e.bliss.LauncherAppMonitor;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
@SuppressWarnings("NewApi")
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";
    public static final String SMARTSPACE_ON_HOME_SCREEN = "pref_smartspace_home_screen";

    private static final boolean DEBUG = true;

    @NonNull
    protected final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;
    private final ModelDelegate mModelDelegate;
    private boolean mIsRestoreFromBackup;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    @NonNull
    private final LauncherBinder mLauncherBinder;

    private final LauncherApps mLauncherApps;
    private final UserManager mUserManager;
    private final UserCache mUserCache;

    private final InstallSessionHelper mSessionHelper;
    private final IconCache mIconCache;

    private final UserManagerState mUserManagerState;
    protected final Map<ComponentKey, AppWidgetProviderInfo> mWidgetProvidersMap = new ArrayMap<>();
    private Map<ShortcutKey, ShortcutInfo> mShortcutKeyToPinnedShortcuts;
    private HashMap<PackageUserKey, SessionInfo> mInstallingPkgsCached;

    private boolean mStopped;

    private final Set<PackageUserKey> mPendingPackages = new HashSet<>();
    private boolean mItemsDeleted = false;
    private String mDbName;

    public LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull LauncherBinder launcherBinder) {
        this(app, bgAllAppsList, bgModel, modelDelegate, launcherBinder, new UserManagerState());
    }

    @VisibleForTesting
    LoaderTask(@NonNull LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel bgModel,
            ModelDelegate modelDelegate, @NonNull LauncherBinder launcherBinder,
            UserManagerState userManagerState) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = bgModel;
        mModelDelegate = modelDelegate;
        mLauncherBinder = launcherBinder;
        mLauncherApps = mApp.getContext().getSystemService(LauncherApps.class);
        mUserManager = mApp.getContext().getSystemService(UserManager.class);
        mUserCache = UserCache.INSTANCE.get(mApp.getContext());
        mSessionHelper = InstallSessionHelper.INSTANCE.get(mApp.getContext());
        mIconCache = mApp.getIconCache();
        mUserManagerState = userManagerState;
        mInstallingPkgsCached = null;
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mLauncherBinder.newIdleLock(this);
        // Just in case mFlushingWorkerThread changes but we aren't woken up,
        // wait no longer than 1sec at a time. We don't loop here anymore to prevent
        // the loader from getting completely stuck if the UI thread never goes fully idle.
        if (!mStopped) {
            idleLock.awaitLocked(1000);
        }
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    private void sendFirstScreenActiveInstallsBroadcast() {
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();
        ArrayList<ItemInfo> allItems = mBgDataModel.getAllWorkspaceItems();

        // Screen set is never empty
        IntArray allScreens = mBgDataModel.collectWorkspaceScreens();
        final int firstScreen = allScreens.get(0);
        IntSet firstScreens = IntSet.wrap(firstScreen);

        filterCurrentWorkspaceItems(firstScreens, allItems, firstScreenItems,
                new ArrayList<>() /* otherScreenItems are ignored */);
        mFirstScreenBroadcast.sendBroadcasts(mApp.getContext(), firstScreenItems);
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.INSTANCE.beginSection(TAG);
        LoaderMemoryLogger memoryLogger = new LoaderMemoryLogger();
        mIsRestoreFromBackup =
                (Boolean) LauncherPrefs.get(mApp.getContext()).get(IS_FIRST_LOAD_AFTER_RESTORE);
        LauncherRestoreEventLogger restoreEventLogger = null;
        if (enableLauncherBrMetricsFixed()) {
            restoreEventLogger = LauncherRestoreEventLogger.Companion
                    .newInstance(mApp.getContext());
        }
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {

            List<ShortcutInfo> allShortcuts = new ArrayList<>();
            loadWorkspace(allShortcuts, "", memoryLogger, restoreEventLogger);

            // Sanitize data re-syncs widgets/shortcuts based on the workspace loaded from db.
            // sanitizeData should not be invoked if the workspace is loaded from a db different
            // from the main db as defined in the invariant device profile.
            // (e.g. both grid preview and minimal device mode uses a different db)
            if (Objects.equals(mApp.getInvariantDeviceProfile().dbFile, mDbName)) {
                verifyNotStopped();
                sanitizeFolders(mItemsDeleted);
                sanitizeWidgetsShortcutsAndPackages();
                logASplit("sanitizeData");
            }

            verifyNotStopped();
            mLauncherBinder.bindWorkspace(true /* incrementBindId */, /* isBindSync= */ false);
            logASplit("bindWorkspace");

            mModelDelegate.workspaceLoadComplete();
            // Notify the installer packages of packages with active installs on the first screen.
            sendFirstScreenActiveInstallsBroadcast();
            logASplit("sendFirstScreenActiveInstallsBroadcast");

            // Take a break
            waitForIdle();
            logASplit("step 1 complete");
            verifyNotStopped();

            // second step
            Trace.beginSection("LoadAllApps");
            List<LauncherActivityInfo> allActivityList;
            try {
                allActivityList = loadAllApps();
            } finally {
                Trace.endSection();
            }
            LauncherAppMonitor.getInstanceNoCreate()
                    .onLoadAllAppsEnd(new ArrayList<>(mBgAllAppsList.data));
            logASplit("loadAllApps");

            if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindAllAppsItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                logASplit("allAppsDelegateItems");
            }
            verifyNotStopped();
            mLauncherBinder.bindAllApps();
            logASplit("bindAllApps");

            verifyNotStopped();
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    mApp.getModel()::onPackageIconsUpdated);
            logASplit("update icon cache");

            verifyNotStopped();
            logASplit("save shortcuts in icon cache");
            updateHandler.updateIcons(allShortcuts, new ShortcutCachingLogic(),
                    mApp.getModel()::onPackageIconsUpdated);

            // Take a break
            waitForIdle();
            logASplit("step 2 complete");
            verifyNotStopped();

            // third step
            List<ShortcutInfo> allDeepShortcuts = loadDeepShortcuts();
            logASplit("loadDeepShortcuts");

            verifyNotStopped();
            mLauncherBinder.bindDeepShortcuts();
            logASplit("bindDeepShortcuts");

            verifyNotStopped();
            logASplit("save deep shortcuts in icon cache");
            updateHandler.updateIcons(allDeepShortcuts,
                    new ShortcutCachingLogic(), (pkgs, user) -> { });

            // Take a break
            waitForIdle();
            logASplit("step 3 complete");
            verifyNotStopped();

            // fourth step
            List<ComponentWithLabelAndIcon> allWidgetsList =
                    mBgDataModel.widgetsModel.update(mApp, null);
            logASplit("load widgets");

            verifyNotStopped();
            mLauncherBinder.bindWidgets();
            logASplit("bindWidgets");
            verifyNotStopped();
            LauncherPrefs prefs = LauncherPrefs.get(mApp.getContext());

            if (SMARTSPACE_AS_A_WIDGET.get() && prefs.get(SHOULD_SHOW_SMARTSPACE)) {
                mLauncherBinder.bindSmartspaceWidget();
                // Turn off pref.
                prefs.putSync(SHOULD_SHOW_SMARTSPACE.to(false));
                logASplit("bindSmartspaceWidget");
                verifyNotStopped();
            } else if (!SMARTSPACE_AS_A_WIDGET.get() && WIDGET_ON_FIRST_SCREEN
                    && !prefs.get(LauncherPrefs.SHOULD_SHOW_SMARTSPACE)) {
                // Turn on pref.
                prefs.putSync(SHOULD_SHOW_SMARTSPACE.to(true));
            }

            if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindOtherItems(mLauncherBinder.mCallbacksList);
                logASplit("otherDelegateItems");
                verifyNotStopped();
            }

            updateHandler.updateIcons(allWidgetsList,
                    new ComponentWithIconCachingLogic(mApp.getContext(), true),
                    mApp.getModel()::onWidgetLabelsUpdated);
            logASplit("save widgets in icon cache");

            // fifth step
            loadFolderNames();

            verifyNotStopped();
            updateHandler.finish();
            logASplit("finish icon update");

            mModelDelegate.modelLoadComplete();
            transaction.commit();
            memoryLogger.clearLogs();
            if (mIsRestoreFromBackup) {
                mIsRestoreFromBackup = false;
                LauncherPrefs.get(mApp.getContext()).putSync(IS_FIRST_LOAD_AFTER_RESTORE.to(false));
                if (restoreEventLogger != null) {
                    restoreEventLogger.reportLauncherRestoreResults();
                }
            }
        } catch (CancellationException e) {
            // Loader stopped, ignore
            logASplit("Cancelled");
        } catch (Exception e) {
            memoryLogger.printLogs();
            throw e;
        }
        TraceHelper.INSTANCE.endSection();
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    protected void loadWorkspace(
            List<ShortcutInfo> allDeepShortcuts,
            String selection,
            LoaderMemoryLogger memoryLogger,
            @Nullable LauncherRestoreEventLogger restoreEventLogger
    ) {
        Trace.beginSection("LoadWorkspace");
        try {
            loadWorkspaceImpl(allDeepShortcuts, selection, memoryLogger, restoreEventLogger);
        } finally {
            Trace.endSection();
        }
        logASplit("loadWorkspace");

        if (FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
            verifyNotStopped();
            mModelDelegate.loadAndBindWorkspaceItems(mUserManagerState,
                    mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
            mModelDelegate.markActive();
            logASplit("workspaceDelegateItems");
        }
        mBgDataModel.isFirstPagePinnedItemEnabled = FeatureFlags.QSB_ON_FIRST_SCREEN.get()
                && (!ENABLE_SMARTSPACE_REMOVAL.get() || LauncherPrefs.getPrefs(
                mApp.getContext()).getBoolean(SMARTSPACE_ON_HOME_SCREEN, true));
    }

    private void loadWorkspaceImpl(
            List<ShortcutInfo> allDeepShortcuts,
            String selection,
            @Nullable LoaderMemoryLogger memoryLogger,
            @Nullable LauncherRestoreEventLogger restoreEventLogger) {
        final Context context = mApp.getContext();
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final WidgetInflater widgetInflater = new WidgetInflater(context);

        ModelDbController dbController = mApp.getModel().getModelDbController();
        dbController.tryMigrateDB(restoreEventLogger);
        Log.d(TAG, "loadWorkspace: loading default favorites");
        dbController.loadDefaultFavoritesIfNecessary();

        synchronized (mBgDataModel) {
            mBgDataModel.clear();
            mPendingPackages.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mSessionHelper.getActiveSessions();
            if (Utilities.enableSupportForArchiving()) {
                mInstallingPkgsCached = installingPkgs;
            }
            installingPkgs.forEach(mApp.getIconCache()::updateSessionCache);
            FileLog.d(TAG, "loadWorkspace: Packages with active install sessions: "
                    + installingPkgs.keySet().stream().map(info -> info.mPackageName).toList());

            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            mShortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(
                    dbController.query(getFavoritesTableName(), null, selection, null, null),
                    mApp, mUserManagerState, mIsRestoreFromBackup ? restoreEventLogger : null);
            final Bundle extras = c.getExtras();
            mDbName = extras == null ? null : extras.getString(ModelDbController.EXTRA_DB_NAME);
            try {
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                queryPinnedShortcutsForUnlockedUsers(context, unlockedUsers);

                List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos = new ArrayList<>();

                WorkspaceItemProcessor itemProcessor = new WorkspaceItemProcessor(c, memoryLogger,
                        mUserManagerState, mLauncherApps, mPendingPackages,
                        mShortcutKeyToPinnedShortcuts, mApp, mBgDataModel,
                        mWidgetProvidersMap, installingPkgs, isSdCardReady,
                        widgetInflater, pmHelper, iconRequestInfos, unlockedUsers,
                        allDeepShortcuts);

                while (!mStopped && c.moveToNext()) {
                    itemProcessor.processItem();
                }
                tryLoadWorkspaceIconsInBulk(iconRequestInfos);
            } finally {
                IOUtils.closeSilently(c);
            }

            if (!FeatureFlags.CHANGE_MODEL_DELEGATE_LOADING_ORDER.get()) {
                mModelDelegate.loadAndBindWorkspaceItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                mModelDelegate.loadAndBindAllAppsItems(mUserManagerState,
                        mLauncherBinder.mCallbacksList, mShortcutKeyToPinnedShortcuts);
                mModelDelegate.loadAndBindOtherItems(mLauncherBinder.mCallbacksList);
                mModelDelegate.markActive();
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            // Remove dead items
            mItemsDeleted = c.commitDeleted();

            processFolderItems();
            processAppPairItems();

            c.commitRestoredItems();
        }
    }

    /**
     * After all items have been processed and added to the BgDataModel, this method requests
     * high-res icons for the items that are part of an app pair
     */
    private void processAppPairItems() {
        mBgDataModel.workspaceItems.stream()
                .filter((itemInfo -> itemInfo.itemType == ITEM_TYPE_APP_PAIR))
                .forEach(fi -> ((FolderInfo) fi).contents.forEach(item ->
                        mIconCache.getTitleAndIcon(item, false /*useLowResIcon*/)));
    }

    /**
     * Initialized the UserManagerState, and determines which users are unlocked. Additionally, if
     * the user is unlocked, it queries LauncherAppsService for pinned shortcuts and stores the
     * result in a class variable to be used in other methods while processing workspace items.
     *
     * @param context used to query LauncherAppsService
     * @param unlockedUsers this param is changed, and the updated value is used outside this method
     */
    @WorkerThread
    private void queryPinnedShortcutsForUnlockedUsers(Context context,
            LongSparseArray<Boolean> unlockedUsers) {
        mUserManagerState.init(mUserCache, mUserManager);

        for (UserHandle user : mUserCache.getUserProfiles()) {
            long serialNo = mUserCache.getSerialNumberForUser(user);
            boolean userUnlocked = mUserManager.isUserUnlocked(user);

            // We can only query for shortcuts when the user is unlocked.
            if (userUnlocked) {
                QueryResult pinnedShortcuts = new ShortcutRequest(context, user)
                        .query(ShortcutRequest.PINNED);
                if (pinnedShortcuts.wasSuccess()) {
                    for (ShortcutInfo shortcut : pinnedShortcuts) {
                        mShortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                                shortcut);
                    }
                    if (pinnedShortcuts.isEmpty()) {
                        FileLog.d(TAG, "No pinned shortcuts found for user " + user);
                    }
                } else {
                    // Shortcut manager can fail due to some race condition when the
                    // lock state changes too frequently. For the purpose of the loading
                    // shortcuts, consider the user is still locked.
                    FileLog.d(TAG, "Shortcut request failed for user "
                            + user + ", user may still be locked.");
                    userUnlocked = false;
                }
            }
            unlockedUsers.put(serialNo, userUnlocked);
        }

    }

    /**
     * After all items have been processed and added to the BgDataModel, this method can correctly
     * rank items inside folders and load the correct miniature preview icons to be shown when the
     * folder is collapsed.
     */
    @WorkerThread
    private void processFolderItems() {
        // Sort the folder items, update ranks, and make sure all preview items are high res.
        List<FolderGridOrganizer> verifiers = mApp.getInvariantDeviceProfile().supportedProfiles
                .stream().map(FolderGridOrganizer::new).toList();
        for (FolderInfo folder : mBgDataModel.folders) {
            Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
            verifiers.forEach(verifier -> verifier.setFolderInfo(folder));
            int size = folder.contents.size();

            // Update ranks here to ensure there are no gaps caused by removed folder items.
            // Ranks are the source of truth for folder items, so cellX and cellY can be
            // ignored for now. Database will be updated once user manually modifies folder.
            for (int rank = 0; rank < size; ++rank) {
                WorkspaceItemInfo info = folder.contents.get(rank);
                // rank is used differently in app pairs, so don't reset
                if (folder.itemType != ITEM_TYPE_APP_PAIR) {
                    info.rank = rank;
                }

                if (info.usingLowResIcon() && info.itemType == Favorites.ITEM_TYPE_APPLICATION
                        && verifiers.stream().anyMatch(it -> it.isItemInPreview(info.rank))) {
                    mIconCache.getTitleAndIcon(info, false);
                }
            }
        }
    }

    private void tryLoadWorkspaceIconsInBulk(
            List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos) {
        Trace.beginSection("LoadWorkspaceIconsInBulk");
        try {
            mIconCache.getTitlesAndIconsInBulk(iconRequestInfos);
            for (IconRequestInfo<WorkspaceItemInfo> iconRequestInfo : iconRequestInfos) {
                WorkspaceItemInfo wai = iconRequestInfo.itemInfo;
                if (mIconCache.isDefaultIcon(wai.bitmap, wai.user)) {
                    iconRequestInfo.loadWorkspaceIcon(mApp.getContext());
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    private void setIgnorePackages(IconCacheUpdateHandler updateHandler) {
        // Ignore packages which have a promise icon.
        synchronized (mBgDataModel) {
            for (ItemInfo info : mBgDataModel.itemsIdMap) {
                if (info instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo si = (WorkspaceItemInfo) info;
                    if (si.isPromise() && si.getTargetComponent() != null) {
                        updateHandler.addPackagesToIgnore(
                                si.user, si.getTargetComponent().getPackageName());
                    }
                } else if (info instanceof LauncherAppWidgetInfo) {
                    LauncherAppWidgetInfo lawi = (LauncherAppWidgetInfo) info;
                    if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                        updateHandler.addPackagesToIgnore(
                                lawi.user, lawi.providerName.getPackageName());
                    }
                }
            }
        }
    }

    private void sanitizeFolders(boolean itemsDeleted) {
        if (itemsDeleted) {
            // Remove any empty folder
            IntArray deletedFolderIds = mApp.getModel().getModelDbController().deleteEmptyFolders();
            synchronized (mBgDataModel) {
                for (int folderId : deletedFolderIds) {
                    mBgDataModel.workspaceItems.remove(mBgDataModel.folders.get(folderId));
                    mBgDataModel.folders.remove(folderId);
                    mBgDataModel.itemsIdMap.remove(folderId);
                }
            }
        }
    }

    private void sanitizeWidgetsShortcutsAndPackages() {
        Context context = mApp.getContext();

        // Remove any ghost widgets
        mApp.getModel().getModelDbController().removeGhostWidgets();

        // Update pinned state of model shortcuts
        mBgDataModel.updateShortcutPinnedState(context);

        if (!Utilities.isBootCompleted() && !mPendingPackages.isEmpty()) {
            context.registerReceiver(
                    new SdCardAvailableReceiver(mApp, mPendingPackages),
                    new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                    null,
                    MODEL_EXECUTOR.getHandler());
        }
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserCache.getUserProfiles();
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();

        List<IconRequestInfo<AppInfo>> iconRequestInfos = new ArrayList<>();
        boolean isWorkProfileQuiet = false;
        boolean isPrivateProfileQuiet = false;
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return allActivityList;
            }
            boolean quietMode = mUserManagerState.isUserQuiet(user);

            if (Flags.enablePrivateSpace()) {
                if (mUserCache.getUserInfo(user).isWork()) {
                    isWorkProfileQuiet = quietMode;
                } else if (mUserCache.getUserInfo(user).isPrivate()) {
                    isPrivateProfileQuiet = quietMode;
                }
            }
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                AppInfo appInfo = new AppInfo(app, mUserCache.getUserInfo(user), quietMode);
                if (Utilities.enableSupportForArchiving() && app.getApplicationInfo().isArchived) {
                    // For archived apps, include progress info in case there is a pending
                    // install session post restart of device.
                    String appPackageName = app.getApplicationInfo().packageName;
                    SessionInfo si = mInstallingPkgsCached != null ? mInstallingPkgsCached.get(
                            new PackageUserKey(appPackageName, user))
                            : mSessionHelper.getActiveSessionInfo(user,
                                    appPackageName);
                    if (si != null) {
                        appInfo.runtimeStatusFlags |= FLAG_INSTALL_SESSION_ACTIVE;
                        appInfo.setProgressLevel((int) (si.getProgress() * 100),
                                PackageInstallInfo.STATUS_INSTALLING);
                    }
                }

                iconRequestInfos.add(new IconRequestInfo<>(
                        appInfo, app, /* useLowResIcon= */ false));
                mBgAllAppsList.add(
                        appInfo, app, false);
            }
            allActivityList.addAll(apps);
        }


        if (FeatureFlags.PROMISE_APPS_IN_ALL_APPS.get()) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mSessionHelper.getAllVerifiedSessions()) {
                AppInfo promiseAppInfo = mBgAllAppsList.addPromiseApp(
                        mApp.getContext(),
                        PackageInstallInfo.fromInstallingState(info),
                        false);

                if (promiseAppInfo != null) {
                    iconRequestInfos.add(new IconRequestInfo<>(
                            promiseAppInfo,
                            /* launcherActivityInfo= */ null,
                            promiseAppInfo.usingLowResIcon()));
                }
            }
        }

        Trace.beginSection("LoadAllAppsIconsInBulk");
        try {
            mIconCache.getTitlesAndIconsInBulk(iconRequestInfos);
            iconRequestInfos.forEach(iconRequestInfo ->
                    mBgAllAppsList.updateSectionName(iconRequestInfo.itemInfo));
        } finally {
            Trace.endSection();
        }

        if (Flags.enablePrivateSpace()) {
            mBgAllAppsList.setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, isWorkProfileQuiet);
            mBgAllAppsList.setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, isPrivateProfileQuiet);
        } else {
            mBgAllAppsList.setFlags(FLAG_QUIET_MODE_ENABLED,
                    mUserManagerState.isAnyProfileQuietModeEnabled());
        }
        mBgAllAppsList.setFlags(FLAG_HAS_SHORTCUT_PERMISSION,
                hasShortcutsPermission(mApp.getContext()));
        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_CHANGE_PERMISSION,
                mApp.getContext().checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                        == PackageManager.PERMISSION_GRANTED);

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }

    private List<ShortcutInfo> loadDeepShortcuts() {
        List<ShortcutInfo> allShortcuts = new ArrayList<>();
        mBgDataModel.deepShortcutMap.clear();

        if (mBgAllAppsList.hasShortcutHostPermission()) {
            for (UserHandle user : mUserCache.getUserProfiles()) {
                if (mUserManager.isUserUnlocked(user)) {
                    List<ShortcutInfo> shortcuts = new ShortcutRequest(mApp.getContext(), user)
                            .query(ShortcutRequest.ALL);
                    allShortcuts.addAll(shortcuts);
                    mBgDataModel.updateDeepShortcutCounts(null, user, shortcuts);
                }
            }
        }
        return allShortcuts;
    }

    private void loadFolderNames() {
        FolderNameProvider provider = FolderNameProvider.newInstance(mApp.getContext(),
                mBgAllAppsList.data, mBgDataModel.folders);

        synchronized (mBgDataModel) {
            for (int i = 0; i < mBgDataModel.folders.size(); i++) {
                FolderNameInfos suggestionInfos = new FolderNameInfos();
                FolderInfo info = mBgDataModel.folders.valueAt(i);
                if (info.suggestedFolderNames == null) {
                    provider.getSuggestedFolderName(mApp.getContext(), info.contents,
                            suggestionInfos);
                    info.suggestedFolderNames = suggestionInfos;
                }
            }
        }
    }

    public static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }

    private static void logASplit(String label) {
        if (DEBUG) {
            Log.d(TAG, label);
        }
    }
}
```

---

# src/com/android/launcher3/model/data/ItemInfo.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/data/ItemInfo.java`  
**Lines**: 551 | **Bytes**: 21360

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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SETTINGS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WALLPAPERS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.LauncherSettings.Favorites.EXTENDED_CONTAINERS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_TASK;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.CONTAINER_NOT_SET;
import static com.android.launcher3.shortcuts.ShortcutKey.EXTRA_SHORTCUT_ID;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Animation;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Workspace;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.AllAppsContainer;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictionContainer;
import com.android.launcher3.logger.LauncherAtom.SettingsContainer;
import com.android.launcher3.logger.LauncherAtom.Shortcut;
import com.android.launcher3.logger.LauncherAtom.ShortcutsContainer;
import com.android.launcher3.logger.LauncherAtom.TaskSwitcherContainer;
import com.android.launcher3.logger.LauncherAtom.WallpapersContainer;
import com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.UserIconInfo;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.Optional;

/**
 * Represents an item in the launcher.
 */
public class ItemInfo {

    public static final boolean DEBUG = false;
    public static final int NO_ID = -1;
    // An id that doesn't match any item, including predicted apps with have an id=NO_ID
    public static final int NO_MATCHING_ID = Integer.MIN_VALUE;

    /** Hidden field Settings.Secure.NAV_BAR_KIDS_MODE */
    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor("nav_bar_kids_mode");

    /**
     * The id in the settings database for this item
     */
    public int id = NO_ID;

    /**
     * One of {@link Favorites#ITEM_TYPE_APPLICATION},
     * {@link Favorites#ITEM_TYPE_DEEP_SHORTCUT}
     * {@link Favorites#ITEM_TYPE_FOLDER},
     * {@link Favorites#ITEM_TYPE_APP_PAIR},
     * {@link Favorites#ITEM_TYPE_APPWIDGET} or
     * {@link Favorites#ITEM_TYPE_CUSTOM_APPWIDGET}.
     */
    public int itemType;

    /**
     * One of {@link Animation#DEFAULT},
     * {@link Animation#VIEW_BACKGROUND}.
     */
    public int animationType = Animation.DEFAULT;

    /**
     * The id of the container that holds this item. For the desktop, this will be
     * {@link Favorites#CONTAINER_DESKTOP}. For the all applications folder it
     * will be {@link #NO_ID} (since it is not stored in the settings DB). For user folders
     * it will be the id of the folder.
     */
    public int container = NO_ID;

    /**
     * Indicates the screen in which the shortcut appears if the container types is
     * {@link Favorites#CONTAINER_DESKTOP}. (i.e., ignore if the container type is
     * {@link Favorites#CONTAINER_HOTSEAT})
     */
    public int screenId = -1;

    /**
     * Indicates the X position of the associated cell.
     */
    public int cellX = -1;

    /**
     * Indicates the Y position of the associated cell.
     */
    public int cellY = -1;

    /**
     * Indicates the X cell span.
     */
    public int spanX = 1;

    /**
     * Indicates the Y cell span.
     */
    public int spanY = 1;

    /**
     * Indicates the minimum X cell span.
     */
    public int minSpanX = 1;

    /**
     * Indicates the minimum Y cell span.
     */
    public int minSpanY = 1;

    /**
     * Indicates the position in an ordered list.
     */
    public int rank = 0;

    /**
     * Title of the item
     */
    @Nullable
    public CharSequence title;

    /**
     * Optionally set: The appTitle might e.g. be different if {@code title} is used to
     * display progress (e.g. Downloading..).
     */
    @Nullable
    public CharSequence appTitle;

    /**
     * Content description of the item.
     */
    @Nullable
    public CharSequence contentDescription;

    /**
     * When the instance is created using {@link #copyFrom}, this field is used to keep track of
     * original {@link ComponentName}.
     */
    @Nullable
    private ComponentName mComponentName;

    @NonNull
    public UserHandle user;

    public ItemInfo() {
        user = Process.myUserHandle();
    }

    protected ItemInfo(@NonNull final ItemInfo info) {
        copyFrom(info);
    }

    public void copyFrom(@NonNull final ItemInfo info) {
        id = info.id;
        title = info.title;
        cellX = info.cellX;
        cellY = info.cellY;
        spanX = info.spanX;
        spanY = info.spanY;
        minSpanX = info.minSpanX;
        minSpanY = info.minSpanY;
        rank = info.rank;
        screenId = info.screenId;
        itemType = info.itemType;
        animationType = info.animationType;
        container = info.container;
        user = info.user;
        contentDescription = info.contentDescription;
        mComponentName = info.getTargetComponent();
    }

    @Nullable
    public Intent getIntent() {
        return null;
    }

    @Nullable
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(getIntent()).map(Intent::getComponent).orElse(mComponentName);
    }

    @Nullable
    public final ComponentKey getComponentKey() {
        ComponentName targetComponent = getTargetComponent();
        return targetComponent == null ? null : new ComponentKey(targetComponent, user);
    }

    /**
     * Returns this item's package name.
     *
     * Prioritizes the component package name, then uses the intent package name as a fallback.
     * This ensures deep shortcuts are supported.
     */
    @Nullable
    public String getTargetPackage() {
        ComponentName component = getTargetComponent();
        Intent intent = getIntent();

        return component != null
                ? component.getPackageName()
                : intent != null
                        ? intent.getPackage()
                        : null;
    }

    public void writeToValues(@NonNull final ContentWriter writer) {
        writer.put(LauncherSettings.Favorites.ITEM_TYPE, itemType)
                .put(LauncherSettings.Favorites.CONTAINER, container)
                .put(LauncherSettings.Favorites.SCREEN, screenId)
                .put(LauncherSettings.Favorites.CELLX, cellX)
                .put(LauncherSettings.Favorites.CELLY, cellY)
                .put(LauncherSettings.Favorites.SPANX, spanX)
                .put(LauncherSettings.Favorites.SPANY, spanY)
                .put(LauncherSettings.Favorites.RANK, rank);
    }

    public void readFromValues(@NonNull final ContentValues values) {
        itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        container = values.getAsInteger(LauncherSettings.Favorites.CONTAINER);
        screenId = values.getAsInteger(LauncherSettings.Favorites.SCREEN);
        cellX = values.getAsInteger(LauncherSettings.Favorites.CELLX);
        cellY = values.getAsInteger(LauncherSettings.Favorites.CELLY);
        spanX = values.getAsInteger(LauncherSettings.Favorites.SPANX);
        spanY = values.getAsInteger(LauncherSettings.Favorites.SPANY);
        rank = values.getAsInteger(LauncherSettings.Favorites.RANK);
    }

    /**
     * Write the fields of this item to the DB
     */
    public void onAddToDatabase(@NonNull final ContentWriter writer) {
        if (Workspace.EXTRA_EMPTY_SCREEN_IDS.contains(screenId)) {
            // We should never persist an item on the extra empty screen.
            throw new RuntimeException("Screen id should not be extra empty screen: " + screenId);
        }

        writeToValues(writer);
        writer.put(LauncherSettings.Favorites.PROFILE_ID, user);
    }

    @Override
    @NonNull
    public final String toString() {
        return getClass().getSimpleName() + "(" + dumpProperties() + ")";
    }

    @NonNull
    protected String dumpProperties() {
        return "id=" + id
                + " type=" + LauncherSettings.Favorites.itemTypeToString(itemType)
                + " container=" + getContainerInfo()
                + " targetComponent=" + getTargetComponent()
                + " screen=" + screenId
                + " cell(" + cellX + "," + cellY + ")"
                + " span(" + spanX + "," + spanY + ")"
                + " minSpan(" + minSpanX + "," + minSpanY + ")"
                + " rank=" + rank
                + " user=" + user
                + " title=" + title;
    }

    /**
     * Whether this item is disabled.
     */
    public boolean isDisabled() {
        return false;
    }

    public int getViewId() {
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        // This cast is safe as long as the id < 0x00FFFFFF
        // Since we jail all the dynamically generated views, there should be no clashes
        // with any other views.
        return id;
    }

    /**
     * Returns if an Item is a predicted item
     */
    public boolean isPredictedItem() {
        return container == CONTAINER_HOTSEAT_PREDICTION || container == CONTAINER_PREDICTION;
    }

    /**
     * Returns if an Item is in the hotseat.
     */
    public boolean isInHotseat() {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    /**
     * Returns whether this item should use the background animation.
     */
    public boolean shouldUseBackgroundAnimation() {
        return animationType == LauncherSettings.Animation.VIEW_BACKGROUND;
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     */
    @NonNull
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Creates {@link LauncherAtom.ItemInfo} with important fields and parent container info.
     * @param fInfo
     */
    @NonNull
    public LauncherAtom.ItemInfo buildProto(@Nullable final FolderInfo fInfo) {
        LauncherAtom.ItemInfo.Builder itemBuilder = getDefaultItemInfoBuilder();
        Optional<ComponentName> nullableComponent = Optional.ofNullable(getTargetComponent());
        switch (itemType) {
            case ITEM_TYPE_APPLICATION:
                itemBuilder
                        .setApplication(nullableComponent
                                .map(component -> LauncherAtom.Application.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setPackageName(component.getPackageName()))
                                .orElse(LauncherAtom.Application.newBuilder()));
                break;
            case ITEM_TYPE_DEEP_SHORTCUT:
                itemBuilder
                        .setShortcut(nullableComponent
                                .map(component -> {
                                    Shortcut.Builder lsb = Shortcut.newBuilder()
                                            .setShortcutName(component.flattenToShortString());
                                    Optional.ofNullable(getIntent())
                                            .map(i -> i.getStringExtra(EXTRA_SHORTCUT_ID))
                                            .ifPresent(lsb::setShortcutId);
                                    return lsb;
                                })
                                .orElse(LauncherAtom.Shortcut.newBuilder()));
                break;
            case ITEM_TYPE_APPWIDGET:
                itemBuilder
                        .setWidget(nullableComponent
                                .map(component -> LauncherAtom.Widget.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setPackageName(component.getPackageName()))
                                .orElse(LauncherAtom.Widget.newBuilder())
                                .setSpanX(spanX)
                                .setSpanY(spanY));
                break;
            case ITEM_TYPE_TASK:
                itemBuilder
                        .setTask(nullableComponent
                                .map(component -> LauncherAtom.Task.newBuilder()
                                        .setComponentName(component.flattenToShortString())
                                        .setIndex(screenId))
                                .orElse(LauncherAtom.Task.newBuilder()));
                break;
            default:
                break;
        }
        if (fInfo != null) {
            LauncherAtom.FolderContainer.Builder folderBuilder =
                    LauncherAtom.FolderContainer.newBuilder();
            folderBuilder.setGridX(cellX).setGridY(cellY).setPageIndex(screenId);

            switch (fInfo.container) {
                case CONTAINER_HOTSEAT:
                case CONTAINER_HOTSEAT_PREDICTION:
                    folderBuilder.setHotseat(LauncherAtom.HotseatContainer.newBuilder()
                            .setIndex(fInfo.screenId));
                    break;
                case CONTAINER_DESKTOP:
                    folderBuilder.setWorkspace(LauncherAtom.WorkspaceContainer.newBuilder()
                            .setPageIndex(fInfo.screenId)
                            .setGridX(fInfo.cellX).setGridY(fInfo.cellY));
                    break;
            }
            itemBuilder.setContainerInfo(ContainerInfo.newBuilder().setFolder(folderBuilder));
        } else {
            ContainerInfo containerInfo = getContainerInfo();
            if (!containerInfo.getContainerCase().equals(CONTAINER_NOT_SET)) {
                itemBuilder.setContainerInfo(containerInfo);
            }
        }
        return itemBuilder.build();
    }

    @NonNull
    protected LauncherAtom.ItemInfo.Builder getDefaultItemInfoBuilder() {
        LauncherAtom.ItemInfo.Builder itemBuilder = LauncherAtom.ItemInfo.newBuilder();
        UserIconInfo info = getUserInfo();
        itemBuilder.setIsWork(info != null && info.isWork());
        itemBuilder.setUserType(getUserType(info));
        SettingsCache settingsCache = SettingsCache.INSTANCE.getNoCreate();
        boolean isKidsMode = settingsCache != null && settingsCache.getValue(NAV_BAR_KIDS_MODE, 0);
        itemBuilder.setIsKidsMode(isKidsMode);
        itemBuilder.setRank(rank);
        return itemBuilder;
    }

    /**
     * Returns {@link ContainerInfo} used when logging this item.
     */
    @NonNull
    public ContainerInfo getContainerInfo() {
        switch (container) {
            case CONTAINER_HOTSEAT:
                return ContainerInfo.newBuilder()
                        .setHotseat(LauncherAtom.HotseatContainer.newBuilder().setIndex(screenId))
                        .build();
            case CONTAINER_HOTSEAT_PREDICTION:
                return ContainerInfo.newBuilder().setPredictedHotseatContainer(
                        LauncherAtom.PredictedHotseatContainer.newBuilder().setIndex(screenId))
                        .build();
            case CONTAINER_DESKTOP:
                return ContainerInfo.newBuilder()
                        .setWorkspace(
                                LauncherAtom.WorkspaceContainer.newBuilder()
                                        .setGridX(cellX)
                                        .setGridY(cellY)
                                        .setPageIndex(screenId))
                        .build();
            case CONTAINER_ALL_APPS:
                return ContainerInfo.newBuilder()
                        .setAllAppsContainer(
                                AllAppsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_WIDGETS_TRAY:
                return ContainerInfo.newBuilder()
                        .setWidgetsContainer(
                                LauncherAtom.WidgetsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_PREDICTION:
                return ContainerInfo.newBuilder()
                        .setPredictionContainer(PredictionContainer.getDefaultInstance())
                        .build();
            case CONTAINER_SHORTCUTS:
                return ContainerInfo.newBuilder()
                        .setShortcutsContainer(ShortcutsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_SETTINGS:
                return ContainerInfo.newBuilder()
                        .setSettingsContainer(SettingsContainer.getDefaultInstance())
                        .build();
            case CONTAINER_TASKSWITCHER:
                return ContainerInfo.newBuilder()
                        .setTaskSwitcherContainer(TaskSwitcherContainer.getDefaultInstance())
                        .build();
            case CONTAINER_WALLPAPERS:
                return ContainerInfo.newBuilder()
                        .setWallpapersContainer(WallpapersContainer.getDefaultInstance())
                        .build();
            default:
                if (container <= EXTENDED_CONTAINERS) {
                    return ContainerInfo.newBuilder()
                            .setExtendedContainers(getExtendedContainer())
                            .build();
                }
        }
        return ContainerInfo.getDefaultInstance();
    }

    /**
     * Returns non-AOSP container wrapped by {@link ExtendedContainers} object. Should be overridden
     * by build variants.
     */
    @NonNull
    protected ExtendedContainers getExtendedContainer() {
        return ExtendedContainers.getDefaultInstance();
    }

    /**
     * Returns shallow copy of the object.
     */
    @NonNull
    public ItemInfo makeShallowCopy() {
        ItemInfo itemInfo = new ItemInfo();
        itemInfo.copyFrom(this);
        return itemInfo;
    }

    /**
     * Sets the title of the item and writes to DB model if needed.
     */
    public void setTitle(@Nullable final CharSequence title,
            @Nullable final ModelWriter modelWriter) {
        this.title = title;
    }

    private UserIconInfo getUserInfo() {
        UserCache userCache = UserCache.INSTANCE.getNoCreate();
        if (userCache == null) {
            return null;
        }

        return userCache.getUserInfo(user);
    }

    private int getUserType(UserIconInfo info) {
        if (info == null) {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_UNKNOWN;
        } else if (info.isMain()) {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_MAIN;
        } else if (info.isPrivate()) {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE;
        } else if (info.isWork()) {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK;
        } else if (info.isCloned()) {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED;
        } else {
            return SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_UNKNOWN;
        }
    }
}
```

---

# src/com/android/launcher3/model/data/FolderInfo.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/data/FolderInfo.java`  
**Lines**: 394 | **Bytes**: 13502

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

package com.android.launcher3.model.data;

import static android.text.TextUtils.isEmpty;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.launcher3.logger.LauncherAtom.Attribute.EMPTY_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.MANUAL_LABEL;
import static com.android.launcher3.logger.LauncherAtom.Attribute.SUGGESTED_LABEL;

import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.folder.FolderNameInfos;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.Attribute;
import com.android.launcher3.logger.LauncherAtom.FolderIcon;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.util.ContentWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Represents a folder containing shortcuts or apps.
 */
public class FolderInfo extends ItemInfo {

    public static final int NO_FLAGS = 0x00000000;

    /**
     * The folder is locked in sorted mode
     */
    public static final int FLAG_ITEMS_SORTED = 0x00000001;

    /**
     * It is a work folder
     */
    public static final int FLAG_WORK_FOLDER = 0x00000002;

    /**
     * The multi-page animation has run for this folder
     */
    public static final int FLAG_MULTI_PAGE_ANIMATION = 0x00000004;

    public static final int FLAG_MANUAL_FOLDER_NAME = 0x00000008;

    /**
     * Different states of folder label.
     */
    public enum LabelState {
        // Folder's label is not yet assigned( i.e., title == null). Eligible for auto-labeling.
        UNLABELED(Attribute.UNLABELED),

        // Folder's label is empty(i.e., title == ""). Not eligible for auto-labeling.
        EMPTY(EMPTY_LABEL),

        // Folder's label is one of the non-empty suggested values.
        SUGGESTED(SUGGESTED_LABEL),

        // Folder's label is non-empty, manually entered by the user
        // and different from any of suggested values.
        MANUAL(MANUAL_LABEL);

        private final LauncherAtom.Attribute mLogAttribute;

        LabelState(Attribute logAttribute) {
            this.mLogAttribute = logAttribute;
        }
    }

    public static final String EXTRA_FOLDER_SUGGESTIONS = "suggest";

    public int options;

    public FolderNameInfos suggestedFolderNames;

    /**
     * The apps and shortcuts
     */
    public ArrayList<WorkspaceItemInfo> contents = new ArrayList<>();

    private ArrayList<FolderListener> mListeners = new ArrayList<>();

    public FolderInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
        user = Process.myUserHandle();
    }

    /**
     * Create an app pair, a type of app collection that launches multiple apps into split screen
     */
    public static FolderInfo createAppPair(WorkspaceItemInfo app1, WorkspaceItemInfo app2) {
        FolderInfo newAppPair = new FolderInfo();
        newAppPair.itemType = LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
        newAppPair.add(app1, /* animate */ false);
        newAppPair.add(app2, /* animate */ false);
        return newAppPair;
    }

    /**
     * Add an app or shortcut
     *
     * @param item
     */
    public void add(WorkspaceItemInfo item, boolean animate) {
        add(item, contents.size(), animate);
    }

    /**
     * Add an app or shortcut for a specified rank.
     */
    public void add(WorkspaceItemInfo item, int rank, boolean animate) {
        rank = Utilities.boundToRange(rank, 0, contents.size());
        contents.add(rank, item);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onAdd(item, rank);
        }
        itemsChanged(animate);
    }

    /**
     * Remove an app or shortcut. Does not change the DB.
     *
     * @param item
     */
    public void remove(WorkspaceItemInfo item, boolean animate) {
        removeAll(Collections.singletonList(item), animate);
    }

    /**
     * Remove all matching app or shortcut. Does not change the DB.
     */
    public void removeAll(List<WorkspaceItemInfo> items, boolean animate) {
        contents.removeAll(items);
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onRemove(items);
        }
        itemsChanged(animate);
    }

    @Override
    public void onAddToDatabase(@NonNull ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.TITLE, title)
                .put(LauncherSettings.Favorites.OPTIONS, options);
    }

    public void addListener(FolderListener listener) {
        if (listener.getClass().getName().endsWith(".Folder")) {
            mListeners.removeIf(l -> l.getClass().getName().endsWith(".Folder"));
        }
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(FolderListener listener) {
        mListeners.removeIf(l -> l == listener);
    }

    public void removeListenerIf(Predicate<FolderListener> filter) {
        mListeners.removeIf(filter);
    }

    public void itemsChanged(boolean animate) {
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onItemsChanged(animate);
        }
    }

    public interface FolderListener {
        void onAdd(WorkspaceItemInfo item, int rank);
        void onRemove(List<WorkspaceItemInfo> item);
        void onItemsChanged(boolean animate);
    }

    public boolean hasOption(int optionFlag) {
        return (options & optionFlag) != 0;
    }

    /**
     * @param option flag to set or clear
     * @param isEnabled whether to set or clear the flag
     * @param writer if not null, save changes to the db.
     */
    public void setOption(int option, boolean isEnabled, ModelWriter writer) {
        int oldOptions = options;
        if (isEnabled) {
            options |= option;
        } else {
            options &= ~option;
        }
        if (writer != null && oldOptions != options) {
            writer.updateItemInDatabase(this);
        }
    }

    @Override
    protected String dumpProperties() {
        return String.format("%s; labelState=%s", super.dumpProperties(), getLabelState());
    }

    @NonNull
    @Override
    public LauncherAtom.ItemInfo buildProto(@Nullable FolderInfo fInfo) {
        FolderIcon.Builder folderIcon = FolderIcon.newBuilder()
                .setCardinality(contents.size());
        if (LabelState.SUGGESTED.equals(getLabelState())) {
            folderIcon.setLabelInfo(title.toString());
        }
        return getDefaultItemInfoBuilder()
                .setFolderIcon(folderIcon)
                .setRank(rank)
                .addItemAttributes(getLabelState().mLogAttribute)
                .setContainerInfo(getContainerInfo())
                .build();
    }

    @Override
    public void setTitle(@Nullable CharSequence title, ModelWriter modelWriter) {
        // Updating label from null to empty is considered as false touch.
        // Retaining null title(ie., UNLABELED state) allows auto-labeling when new items added.
        if (isEmpty(title) && this.title == null) {
            return;
        }

        // Updating title to same value does not change any states.
        if (title != null && title.equals(this.title)) {
            return;
        }

        this.title = title;
        LabelState newLabelState =
                title == null ? LabelState.UNLABELED
                        : title.length() == 0 ? LabelState.EMPTY :
                                getAcceptedSuggestionIndex().isPresent() ? LabelState.SUGGESTED
                                        : LabelState.MANUAL;

        if (newLabelState.equals(LabelState.MANUAL)) {
            options |= FLAG_MANUAL_FOLDER_NAME;
        } else {
            options &= ~FLAG_MANUAL_FOLDER_NAME;
        }
        if (modelWriter != null) {
            modelWriter.updateItemInDatabase(this);
        }
    }

    /**
     * Returns current state of the current folder label.
     */
    public LabelState getLabelState() {
        return title == null ? LabelState.UNLABELED
                : title.length() == 0 ? LabelState.EMPTY :
                        hasOption(FLAG_MANUAL_FOLDER_NAME) ? LabelState.MANUAL
                                : LabelState.SUGGESTED;
    }

    @NonNull
    @Override
    public ItemInfo makeShallowCopy() {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.copyFrom(this);
        folderInfo.contents = this.contents;
        return folderInfo;
    }

    /**
     * Returns {@link LauncherAtom.FolderIcon} wrapped as {@link LauncherAtom.ItemInfo} for logging.
     */
    @NonNull
    @Override
    public LauncherAtom.ItemInfo buildProto() {
        return buildProto(null);
    }

    /**
     * Returns index of the accepted suggestion.
     */
    public OptionalInt getAcceptedSuggestionIndex() {
        String newLabel = checkNotNull(title,
                "Expected valid folder label, but found null").toString();
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return OptionalInt.empty();
        }
        CharSequence[] labels = suggestedFolderNames.getLabels();
        return IntStream.range(0, labels.length)
                .filter(index -> !isEmpty(labels[index])
                        && newLabel.equalsIgnoreCase(
                        labels[index].toString()))
                .sequential()
                .findFirst();
    }

    /**
     * Returns {@link FromState} based on current {@link #title}.
     */
    public LauncherAtom.FromState getFromLabelState() {
        switch (getLabelState()){
            case EMPTY:
                return LauncherAtom.FromState.FROM_EMPTY;
            case MANUAL:
                return LauncherAtom.FromState.FROM_CUSTOM;
            case SUGGESTED:
                return LauncherAtom.FromState.FROM_SUGGESTED;
            case UNLABELED:
            default:
                return LauncherAtom.FromState.FROM_STATE_UNSPECIFIED;
        }
    }

    /**
     * Returns {@link ToState} based on current {@link #title}.
     */
    public LauncherAtom.ToState getToLabelState() {
        if (title == null) {
            return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
        }

        // TODO: if suggestedFolderNames is null then it infrastructure issue, not
        // ranking issue. We should log these appropriately.
        if (suggestedFolderNames == null || !suggestedFolderNames.hasSuggestions()) {
            return title.length() > 0
                    ? LauncherAtom.ToState.TO_CUSTOM_WITH_EMPTY_SUGGESTIONS
                    : LauncherAtom.ToState.TO_EMPTY_WITH_EMPTY_SUGGESTIONS;
        }

        boolean hasValidPrimary = suggestedFolderNames != null && suggestedFolderNames.hasPrimary();
        if (title.length() == 0) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_EMPTY_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_EMPTY_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        OptionalInt accepted_suggestion_index = getAcceptedSuggestionIndex();
        if (!accepted_suggestion_index.isPresent()) {
            return hasValidPrimary ? LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_PRIMARY
                    : LauncherAtom.ToState.TO_CUSTOM_WITH_VALID_SUGGESTIONS_AND_EMPTY_PRIMARY;
        }

        switch (accepted_suggestion_index.getAsInt()) {
            case 0:
                return LauncherAtom.ToState.TO_SUGGESTION0;
            case 1:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION1_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION1_WITH_EMPTY_PRIMARY;
            case 2:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION2_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION2_WITH_EMPTY_PRIMARY;
            case 3:
                return hasValidPrimary ? LauncherAtom.ToState.TO_SUGGESTION3_WITH_VALID_PRIMARY
                        : LauncherAtom.ToState.TO_SUGGESTION3_WITH_EMPTY_PRIMARY;
            default:
                // fall through
        }
        return LauncherAtom.ToState.TO_STATE_UNSPECIFIED;
    }

    @Override
    public boolean isDisabled() {
        if (itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR) {
            return contents.stream().anyMatch((WorkspaceItemInfo::isDisabled));
        }

        return super.isDisabled();
    }
}
```

---

# src/com/android/launcher3/model/data/WorkspaceItemInfo.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/model/data/WorkspaceItemInfo.java`  
**Lines**: 246 | **Bytes**: 8258

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

package com.android.launcher3.model.data;

import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.util.ContentWriter;

import java.util.Arrays;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
public class WorkspaceItemInfo extends ItemInfoWithIcon {

    public static final int DEFAULT = 0;

    /**
     * The shortcut was restored from a backup and it not ready to be used. This is automatically
     * set during backup/restore
     */
    public static final int FLAG_RESTORED_ICON = 1;

    /**
     * The icon was added as an auto-install app, and is not ready to be used. This flag can't
     * be present along with {@link #FLAG_RESTORED_ICON}, and is set during default layout
     * parsing.
     *
     * OR this icon was added due to it being an active install session created by the user.
     */
    public static final int FLAG_AUTOINSTALL_ICON = 1 << 1;

    /**
     * Indicates that the widget restore has started.
     */
    public static final int FLAG_RESTORE_STARTED = 1 << 2;

    /**
     * Web UI supported.
     */
    public static final int FLAG_SUPPORTS_WEB_UI = 1 << 3;

    /**
     *
     */
    public static final int FLAG_START_FOR_RESULT = 1 << 4;

    /**
     * The app is flagged non-resizeable, meaning that it does not support multi-window on small
     * screens.
     */
    public static final int FLAG_NON_RESIZEABLE = 1 << 5;

    /**
     * The intent used to start the application.
     */
    @NonNull
    public Intent intent;

    /**
     * A message to display when the user tries to start a disabled shortcut.
     * This is currently only used for deep shortcuts.
     */
    public CharSequence disabledMessage;

    public int status;

    /**
     * A set of person's Id associated with the WorkspaceItemInfo, this is only used if the item
     * represents a deep shortcut.
     */
    @NonNull private String[] personKeys = Utilities.EMPTY_STRING_ARRAY;

    public int options;


    public WorkspaceItemInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    }

    public WorkspaceItemInfo(WorkspaceItemInfo info) {
        super(info);
        title = info.title;
        intent = new Intent(info.intent);
        status = info.status;
        personKeys = info.personKeys.clone();
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeWorkspaceItem. */
    public WorkspaceItemInfo(AppInfo info) {
        super(info);
        title = Utilities.trim(info.title);
        intent = new Intent(info.getIntent());
    }

    /**
     * Creates a {@link WorkspaceItemInfo} from a {@link ShortcutInfo}.
     */
    public WorkspaceItemInfo(ShortcutInfo shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        if (Flags.privateSpaceRestrictAccessibilityDrag()) {
            if (UserCache.INSTANCE.get(context).getUserInfo(user).isPrivate()) {
                runtimeStatusFlags |= FLAG_NOT_PINNABLE;
            }
        }
        updateFromDeepShortcutInfo(shortcutInfo, context);
    }

    @Override
    public void onAddToDatabase(@NonNull ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(Favorites.TITLE, title)
                .put(Favorites.INTENT, getIntent())
                .put(Favorites.OPTIONS, options)
                .put(Favorites.RESTORED, status);

        if (!usingLowResIcon()) {
            writer.putIcon(bitmap, user);
        }
    }

    @Override
    @NonNull
    public Intent getIntent() {
        return intent;
    }

    public boolean hasStatusFlag(int flag) {
        return (status & flag) != 0;
    }


    public final boolean isPromise() {
        return hasStatusFlag(FLAG_RESTORED_ICON | FLAG_AUTOINSTALL_ICON)
                // For archived apps, promise icons are always ready to be displayed.
                || isArchived();
    }

    /**
     * Returns true if the workspace item supports promise icon UI. There are a few cases where they
     * are supported:
     * 1. Icons to be restored via backup/restore.
     * 2. Icons added as an auto-install app.
     * 3. Icons added due to it being an active install session created by the user.
     * 4. Icons for archived apps.
     */
    public boolean hasPromiseIconUi() {
        return isPromise() && !hasStatusFlag(FLAG_SUPPORTS_WEB_UI);
    }

    public void updateFromDeepShortcutInfo(@NonNull final ShortcutInfo shortcutInfo,
            @NonNull final Context context) {
        // {@link ShortcutInfo#getActivity} can change during an update. Recreate the intent
        intent = ShortcutKey.makeIntent(shortcutInfo);
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        contentDescription = context.getPackageManager().getUserBadgedLabel(label, user);
        if (shortcutInfo.isEnabled()) {
            runtimeStatusFlags &= ~FLAG_DISABLED_BY_PUBLISHER;
        } else {
            runtimeStatusFlags |= FLAG_DISABLED_BY_PUBLISHER;
        }
        disabledMessage = shortcutInfo.getDisabledMessage();
        if (shortcutInfo.getDisabledReason() == ShortcutInfo.DISABLED_REASON_VERSION_LOWER) {
            runtimeStatusFlags |= FLAG_DISABLED_VERSION_LOWER;
        } else {
            runtimeStatusFlags &= ~FLAG_DISABLED_VERSION_LOWER;
        }

        Person[] persons = ApiWrapper.getPersons(shortcutInfo);
        personKeys = persons.length == 0 ? Utilities.EMPTY_STRING_ARRAY
            : Arrays.stream(persons).map(Person::getKey).sorted().toArray(String[]::new);
    }

    /**
     * {@code true} if the shortcut is disabled due to its app being a lower version.
     */
    public boolean isDisabledVersionLower() {
        return (runtimeStatusFlags & FLAG_DISABLED_VERSION_LOWER) != 0;
    }

    /** Returns the WorkspaceItemInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
                ? getIntent().getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) : null;
    }

    @NonNull
    public String[] getPersonKeys() {
        return personKeys;
    }

    @Override
    public ComponentName getTargetComponent() {
        ComponentName cn = super.getTargetComponent();
        if (cn == null && hasStatusFlag(
                FLAG_SUPPORTS_WEB_UI | FLAG_AUTOINSTALL_ICON | FLAG_RESTORED_ICON)) {
            // Legacy shortcuts and promise icons with web UI may not have a componentName but just
            // a packageName. In that case create a empty componentName instead of adding additional
            // check everywhere.
            String pkg = intent.getPackage();
            return pkg == null ? null : new ComponentName(pkg, IconCache.EMPTY_CLASS_NAME);
        }
        return cn;
    }

    @Override
    public WorkspaceItemInfo clone() {
        return new WorkspaceItemInfo(this);
    }
}
```

---

# bliss/src/foundation/e/bliss/widgets/WidgetContainer.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/widgets/WidgetContainer.kt`  
**Lines**: 554 | **Bytes**: 22353

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
package foundation.e.bliss.widgets

import android.animation.LayoutTransition
import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.forEach
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.R
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.graphics.FragmentWithPreview
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddShortcutInfo
import com.android.launcher3.widget.WidgetCell
import com.android.launcher3.widget.picker.WidgetsFullSheet
import com.android.launcher3.widget.util.WidgetSizes
import foundation.e.bliss.LauncherAppMonitor
import foundation.e.bliss.LauncherAppMonitorCallback
import foundation.e.bliss.utils.BlissDbUtils
import foundation.e.bliss.utils.Logger
import foundation.e.bliss.utils.ObservableList
import foundation.e.bliss.utils.disableComponent
import foundation.e.bliss.widgets.BlissAppWidgetHost.Companion.REQUEST_CONFIGURE_APPWIDGET
import foundation.e.bliss.widgets.DefaultWidgets.defaultWidgets
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Suppress("Deprecation", "NewApi")
class WidgetContainer(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private val mLauncher by lazy { Launcher.getLauncher(context) }

    private lateinit var mManageWidgetLayout: LinearLayout
    private lateinit var mRemoveWidgetLayout: FrameLayout
    private lateinit var mResizeContainer: RelativeLayout
    private lateinit var mWidgetLinearLayout: LinearLayout
    private lateinit var mWrapper: LinearLayout

    private val mResizeContainerRect = Rect()
    private val mInsetPadding =
        context.resources.getDimension(R.dimen.widget_page_inset_padding).toInt()

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (mResizeContainer.visibility == VISIBLE && ev.action == MotionEvent.ACTION_DOWN) {
            mResizeContainer.getHitRect(mResizeContainerRect)
            if (!mResizeContainerRect.contains(ev.x.toInt(), ev.y.toInt())) {
                mLauncher.hideWidgetResizeContainer()
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mManageWidgetLayout = findViewById(R.id.manage_widget_parent)!!
        mRemoveWidgetLayout = findViewById(R.id.remove_widget_parent)!!
        mResizeContainer = findViewById(R.id.widget_resizer_container)!!
        mWidgetLinearLayout = findViewById(R.id.widget_linear_layout)!!

        findViewById<Button>(R.id.manage_widgets)!!.setOnClickListener {
            WidgetsFullSheet.show(mLauncher, true, true)
        }

        findViewById<Button>(R.id.remove_widgets)!!.setOnClickListener {
            val intent =
                Intent(context, WidgetsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        mWrapper =
            findViewWithTag<LinearLayout?>("wrapper_children").apply {
                setOnHierarchyChangeListener(
                    object : OnHierarchyChangeListener {
                        override fun onChildViewAdded(parent: View?, child: View?) {
                            handleRemoveButtonVisibility((parent as LinearLayout).childCount)
                        }

                        override fun onChildViewRemoved(parent: View?, child: View?) {
                            handleRemoveButtonVisibility((parent as LinearLayout).childCount)
                        }
                    }
                )
            }

        updatePadding()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updatePadding()
    }

    private fun updatePadding() {
        val windowInsets = rootWindowInsets ?: mLauncher.workspace?.rootWindowInsets ?: return
        val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
        if (::mResizeContainer.isInitialized) {
            mResizeContainer.apply {
                val layoutParams = this.layoutParams as LayoutParams
                layoutParams.bottomMargin = mInsetPadding + (insets.bottom)
                this.layoutParams = layoutParams
            }
        }

        if (::mWidgetLinearLayout.isInitialized) {
            mWidgetLinearLayout.apply {
                setPadding(
                    this.paddingLeft,
                    mInsetPadding + (insets.top),
                    this.paddingRight,
                    (insets.bottom),
                )
            }
        }

        if (::mManageWidgetLayout.isInitialized) {
            mManageWidgetLayout.apply {
                setPadding(
                    this.paddingLeft,
                    mInsetPadding,
                    this.paddingRight,
                    (insets.top - mInsetPadding),
                )
            }
        }
    }

    private fun handleRemoveButtonVisibility(childCount: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            if (childCount == 0) {
                mRemoveWidgetLayout.visibility = View.GONE
            } else if (mRemoveWidgetLayout.visibility == View.GONE) {
                mRemoveWidgetLayout.visibility = View.VISIBLE
            }
        }
    }

    fun updateWidgets() {
        if (::mWrapper.isInitialized) {
            val widgetDbHelper = WidgetsDbHelper.getInstance(context)
            val widgetManager = AppWidgetManager.getInstance(context)

            mWrapper.forEach {
                val hostView = it as? AppWidgetHostView ?: return@forEach
                val info = hostView.appWidgetInfo ?: return@forEach
                val height = widgetDbHelper.getWidgetHeight(it.id) ?: 0

                val opts =
                    WidgetSizes.getWidgetSizeOptions(
                        mLauncher,
                        info.provider,
                        mLauncher.deviceProfile.inv.numColumns,
                        mLauncher.deviceProfile.inv.numRows
                    )

                if (height > 0) {
                    opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                }
                val blacklistedComponents =
                    mLauncher.resources.getStringArray(R.array.blacklisted_widget_options)
                if (!blacklistedComponents.contains(info.provider.className)) {
                    widgetManager.updateAppWidgetOptions(hostView.appWidgetId, opts)
                }
            }
        }
    }

    /** A fragment to display the default widgets. */
    class WidgetFragment : FragmentWithPreview() {
        private lateinit var mWrapper: LinearLayout
        private var widgetObserver: Disposable? = null
        private lateinit var widgetsDbHelper: WidgetsDbHelper

        private val mOldWidgets by lazy { BlissDbUtils.getWidgetDetails(context) }
        private val mWidgetManager by lazy { AppWidgetManager.getInstance(context) }
        private val mWidgetHost by lazy { BlissAppWidgetHost(context) }
        private val launcher by lazy { Launcher.getLauncher(context) }

        private val mAppMonitorCallback: LauncherAppMonitorCallback =
            object : LauncherAppMonitorCallback {
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                    if (!::widgetsDbHelper.isInitialized) {
                        return
                    }
                    val widgets =
                        widgetsDbHelper.getWidgets().filter {
                            it.component.packageName == packageName
                        }
                    if (packageName != null && widgets.isNotEmpty()) {
                        widgets.map { it.widgetId }.forEach { widgetsDbHelper.delete(it) }
                        rebindWidgets()
                    }
                }
            }
        private var mEventJob: Job? = null

        private var initialWidgetsAdded: Boolean
            set(value) {
                LauncherPrefs.getPrefs(context)
                    .edit()
                    .putBoolean(defaultWidgetsAdded, value)
                    .apply()
            }
            get() {
                return LauncherPrefs.getPrefs(context).getBoolean(defaultWidgetsAdded, false)
            }

        private val isQsbEnabled: Boolean
            get() = FeatureFlags.QSB_ON_FIRST_SCREEN.get()

        init {
            LauncherAppMonitor.getInstanceNoCreate().registerCallback(mAppMonitorCallback)
        }

        @Deprecated("Deprecated in Java")
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            widgetsDbHelper = WidgetsDbHelper.getInstance(context)

            mWrapper =
                LinearLayout(context, null).apply {
                    tag = "wrapper_children"
                    orientation = LinearLayout.VERTICAL
                }
            if (isQsbEnabled) {
                loadWidgets()
            }

            return mWrapper
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            if (isQsbEnabled) {
                mWidgetHost.startListening()
            }

            super.onViewCreated(view, savedInstanceState)
        }

        override fun onDestroyView() {
            mEventJob?.cancel()
            mEventJob = null
            mWidgetHost.stopListening()
            super.onDestroyView()
        }

        private fun loadWidgets() {
            if (!initialWidgetsAdded) {
                val oldWidgets = mWidgetHost.appWidgetIds
                if (oldWidgets.isEmpty()) {
                    mWidgetHost.deleteHost()
                    DefaultWidgets.defaultWidgets.forEach {
                        try {
                            bindWidget(it)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Could not add widget ${it.flattenToString()}")
                        }
                    }
                } else {
                    rebindWidgets(true)
                }

                disableComponent(context, DefaultWidgets.oldWeatherWidget)
                initialWidgetsAdded = true
            } else {
                rebindWidgets()
                Logger.e(TAG, "saved widgets added")
            }

            widgetObserver?.dispose()
            widgetObserver =
                defaultWidgets.observable.subscribe {
                    Logger.d(TAG, "Component: ${it.flattenToString()}")
                    bindWidget(it)
                }

            mEventJob?.cancel()
            mEventJob = CoroutineScope(Dispatchers.Main).launch { eventFlow.collect { rebindWidgets() } }
        }

        private fun rebindWidgets(backup: Boolean = false) {
            mWrapper.removeAllViews()
            if (!backup) {

                val dbWidgets =
                    widgetsDbHelper.getWidgets().sortedBy { it.position }.also { list ->
                        list.forEach { addView(it.widgetId) }
                    }

                // Remove all widgets not present in db
                mWidgetHost.appWidgetIds
                    .filter { id -> dbWidgets.all { info -> info.widgetId != id } }
                    .forEach { mWidgetHost.deleteAppWidgetId(it) }
            } else {
                if (mOldWidgets.isNotEmpty()) {
                    mOldWidgets
                        .filter { mWidgetHost.appWidgetIds.contains(it.id) }
                        .sortedWith(
                            compareBy(BlissDbUtils.WidgetItems::order, BlissDbUtils.WidgetItems::id)
                        )
                        .forEach { addView(it.id, true) }
                } else {
                    mWidgetHost.appWidgetIds.sorted().forEach { addView(it, true) }
                }
            }
        }

        private fun bindWidget(provider: ComponentName) {
            val widgetId = mWidgetHost.allocateAppWidgetId()
            val isWidgetBound = mWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)

            if (!isWidgetBound) {
                mWidgetHost.deleteAppWidgetId(widgetId)
                Logger.e(TAG, "Could not add widget ${provider.flattenToString()}")
                return
            }

            configureWidget(widgetId)
        }

        private fun configureWidget(widgetId: Int) {
            val info = mWidgetManager.getAppWidgetInfo(widgetId)
            if (info != null) {
                if (info.configure != null) {
                    sendIntent(widgetId)
                } else {
                    addView(widgetId)
                }
            } else {
                mWidgetHost.deleteAppWidgetId(widgetId)
            }
        }

        private fun addView(widgetId: Int, backup: Boolean = false) {
            val info = mWidgetManager.getAppWidgetInfo(widgetId)

            if (info != null) {
                val widgetInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, info)
                mWidgetHost
                    .createView(widgetId, widgetInfo)
                    .apply {
                        id = widgetId
                        layoutTransition = LayoutTransition()
                        setOnLongClickListener {
                            if (
                                (widgetInfo.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) ==
                                    AppWidgetProviderInfo.RESIZE_VERTICAL
                            ) {
                                launcher.hideWidgetResizeContainer()
                                launcher.showWidgetResizeContainer(this as RoundedWidgetView)
                            }
                            true
                        }
                    }
                    .also {
                        var opts = mWidgetManager.getAppWidgetOptions(it.appWidgetId)
                        val params =
                            LayoutParams(
                                -1,
                                opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                            )

                        if (backup) {
                            if (it.appWidgetInfo.provider.equals(DefaultWidgets.oldWeatherWidget)) {
                                mWidgetHost.deleteAppWidgetId(it.id)

                                // Swap with new widget
                                bindWidget(DefaultWidgets.weatherWidget)
                                return
                            }
                            val oldHeight =
                                if (mOldWidgets.isNotEmpty()) {
                                    mOldWidgets
                                        .find { widgetItems -> widgetItems.id == widgetId }
                                        ?.height
                                } else {
                                    0
                                }
                            val minHeight: Int = widgetInfo.minResizeHeight
                            val maxHeight: Int =
                                InvariantDeviceProfile.INSTANCE.get(context)
                                    .getDeviceProfile(context)
                                    .heightPx * 3 / 4
                            val normalisedDifference = (maxHeight - minHeight) / 100

                            if (oldHeight != null && oldHeight > 0) {
                                params.height = minHeight + normalisedDifference * oldHeight
                            } else {
                                params.height = 0
                            }
                            opts.remove(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                            opts.remove(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                            opts.remove(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                            opts.remove(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                            it.updateAppWidgetOptions(opts)
                        } else {
                            params.height = widgetsDbHelper.getWidgetHeight(it.id) ?: 0
                        }

                        if (params.height > 0) {
                            mWrapper.addView(it, params)
                        } else {
                            mWrapper.addView(it)
                        }

                        opts =
                            WidgetSizes.getWidgetSizeOptions(
                                launcher,
                                info.provider,
                                launcher.deviceProfile.inv.numColumns,
                                launcher.deviceProfile.inv.numRows
                            )

                        if (params.height > 0) {
                            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, params.height)
                            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, params.height)
                        }

                        val blacklistedComponents =
                            launcher.resources.getStringArray(R.array.blacklisted_widget_options)
                        if (!blacklistedComponents.contains(info.provider.className)) {
                            mWidgetManager.updateAppWidgetOptions(it.appWidgetId, opts)
                        }

                        widgetsDbHelper.insert(
                            WidgetInfo(
                                mWrapper.indexOfChild(it),
                                it.appWidgetInfo.provider,
                                it.appWidgetId,
                                params.height
                            )
                        )
                    }
            } else {
                mWidgetHost.deleteAppWidgetId(widgetId)
            }
        }

        private fun sendIntent(widgetId: Int) {
            val bundle = Bundle().apply { putInt(EXTRA_APPWIDGET_ID, widgetId) }
            mWidgetHost.startAppWidgetConfigureActivityForResult(
                launcher,
                widgetId,
                0,
                REQUEST_CONFIGURE_APPWIDGET,
                bundle
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            val widgetId = data?.getIntExtra(EXTRA_APPWIDGET_ID, -1) ?: -1
            Logger.d(TAG, "Request: $requestCode | Result: $resultCode | Widget: $widgetId")
            if (resultCode == RESULT_OK && requestCode == REQUEST_CONFIGURE_APPWIDGET) {
                addView(widgetId)
            } else {
                rebindWidgets()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onDestroy() {
            super.onDestroy()
            widgetObserver?.dispose()
            widgetObserver = null
            LauncherAppMonitor.getInstanceNoCreate()?.unregisterCallback(mAppMonitorCallback)
        }

        companion object {
            const val TAG = "WidgetFragment"
            const val defaultWidgetsAdded = "default_widgets_added"
            val defaultWidgets = ObservableList<ComponentName>()
            val eventFlow = MutableSharedFlow<Unit>()

            @JvmStatic
            fun onWidgetClick(context: Context, view: View, closeSheet: (Boolean) -> Unit) {
                val tag =
                    when {
                        view is WidgetCell -> {
                            view.getTag()
                        }
                        view.parent is WidgetCell -> {
                            (view.parent as WidgetCell).tag
                        }
                        else -> null
                    }

                if (tag is PendingAddShortcutInfo) {
                    Toast.makeText(context, "Please select a widget", Toast.LENGTH_SHORT).show()
                } else if (tag is PendingAddItemInfo) {
                    closeSheet(true)
                    defaultWidgets.add(tag.componentName)
                }
            }

            @JvmStatic
            fun onWidgetAdded(componentName: ComponentName) {
                defaultWidgets.add(componentName)
            }
        }
    }
}
```

---

# bliss/src/foundation/e/bliss/widgets/RoundedWidgetView.java

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/widgets/RoundedWidgetView.java`  
**Lines**: 192 | **Bytes**: 6494

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
package foundation.e.bliss.widgets;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

import foundation.e.bliss.blur.BlurViewDelegate;
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer;
import foundation.e.bliss.blur.OffsetParent;

@SuppressLint("ViewConstructor")
public class RoundedWidgetView extends LauncherAppWidgetHostView implements OffsetParent {
    private final Path stencilPath = new Path();
    private final float cornerRadius;
    private final Context mContext;
    private ImageView resizeBorder;
    private boolean mChildrenFocused;
    private BlurViewDelegate mBlurDelegate = null;
    protected Launcher mLauncher;

    private final OffsetParentDelegate offsetParentDelegate = new OffsetParentDelegate();

    public RoundedWidgetView(Context context, boolean blurBackground) {
        super(context);
        this.mContext = context;
        this.cornerRadius = context.getResources().getDimensionPixelSize(R.dimen.default_widget_corner_radius);
        if (blurBackground) {
            mBlurDelegate = new BlurViewDelegate(this, WallpaperBlurRenderer.blurConfigWidget, null);
            mBlurDelegate.setBlurCornerRadius(cornerRadius);
            setWillNotDraw(false);
            setOutlineProvider(mBlurDelegate.getOutlineProvider());
            setClipToOutline(true);
        }
    }

    public void setHeight(int newHeight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
        params.height = newHeight;
        setLayoutParams(params);
    }

    @Override
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        super.setAppWidget(appWidgetId, info);
        int widgetTopBottom = mContext.getResources().getDimensionPixelSize(R.dimen.widget_page_top_bottom_padding);
        int widgetLeftRight = mContext.getResources().getDimensionPixelSize(R.dimen.widget_page_all_padding);
        setPadding(widgetLeftRight, widgetTopBottom, widgetLeftRight, widgetTopBottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // compute the path
        stencilPath.reset();
        stencilPath.addRoundRect(0, 0, w, h, cornerRadius, cornerRadius, Path.Direction.CW);
        stencilPath.close();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(stencilPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBlurDelegate != null) {
            mBlurDelegate.draw(canvas);
        }
        super.onDraw(canvas);
    }

    @Override
    public int getDescendantFocusability() {
        return mChildrenFocused ? ViewGroup.FOCUS_BEFORE_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        if (gainFocus) {
            mChildrenFocused = false;
            dispatchChildFocus(false);
        }
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        dispatchChildFocus(mChildrenFocused && focused != null);
        if (focused != null) {
            focused.setFocusableInTouchMode(false);
        }
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mChildrenFocused;
    }

    private void dispatchChildFocus(boolean childIsFocused) {
        // The host view's background changes when selected, to indicate the focus is
        // inside.
        setSelected(childIsFocused);
    }

    public void addBorder() {
        if (resizeBorder != null) {
            removeBorder();
            return;
        }
        resizeBorder = new ImageView(mContext);
        resizeBorder.setImageResource(R.drawable.bliss_resize_frame);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        resizeBorder.setLayoutParams(layoutParams);
        addView(resizeBorder);
        startWobble();
    }

    public void startWobble() {
        mLauncher = Launcher.cast(Launcher.getLauncher(mContext));
        // startAnimation(mLauncher.getWorkspace().getWobbleAnimation());
    }

    public void removeBorder() {
        if (resizeBorder != null) {
            removeView(resizeBorder);
            clearAnimation();
            resizeBorder = null;
        }
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
}
```

---

# bliss/src/foundation/e/bliss/widgets/WidgetsDbHelper.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/widgets/WidgetsDbHelper.kt`  
**Lines**: 153 | **Bytes**: 5412

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
package foundation.e.bliss.widgets

import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import foundation.e.bliss.utils.Logger

class WidgetsDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, WIDGETS_DB, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        Logger.d(TAG, "Creating widgets database")
        db.execSQL(
            "CREATE TABLE $WIDGETS_TABLE (" +
                "position INTEGER NOT NULL," +
                "component TEXT NOT NULL," +
                "height INTEGER NOT NULL," +
                "widgetId INTEGER NOT NULL PRIMARY KEY" +
                ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Do nothing, since we are not creating a new database
    }

    fun getWidgets(): List<WidgetInfo> {
        val widgets = mutableListOf<WidgetInfo>()

        val db = writableDatabase ?: return widgets
        db.rawQuery("SELECT * FROM $WIDGETS_TABLE", null).use { cursor ->
            while (cursor.moveToNext()) {
                val position = cursor.getInt(cursor.getColumnIndexOrThrow("position"))
                val component = cursor.getString(cursor.getColumnIndexOrThrow("component"))
                val widgetId = cursor.getInt(cursor.getColumnIndexOrThrow("widgetId"))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow("height"))

                val componentName = ComponentName.unflattenFromString(component)
                if (componentName != null) {
                    widgets.add(
                        WidgetInfo(
                            position,
                            componentName,
                            widgetId,
                            height
                        )
                    )
                }
            }
        }

        return widgets
    }

    fun delete(id: Int) {
        Logger.d(TAG, "Deleting widget $id")
        val db = writableDatabase ?: return
        db.beginTransaction()
        try {
            var deletedPosition: Int? = null
            db.rawQuery(
                "SELECT position FROM $WIDGETS_TABLE WHERE widgetId = ?",
                arrayOf(id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    deletedPosition = cursor.getInt(cursor.getColumnIndexOrThrow("position"))
                }
            }

            db.execSQL("DELETE FROM $WIDGETS_TABLE WHERE widgetId = ?", arrayOf(id))
            if (deletedPosition != null) {
                db.execSQL(
                    "UPDATE $WIDGETS_TABLE SET position = position - 1 WHERE position > ?",
                    arrayOf(deletedPosition)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insert(info: WidgetInfo) {
        Logger.d(
            TAG,
            "Inserting widget at position ${info.position} | ${info.component} | ${info.widgetId}"
        )
        val db = writableDatabase ?: return
        db.execSQL(
            "INSERT OR REPLACE INTO $WIDGETS_TABLE (position, component, widgetId, height) VALUES (?, ?, ?, ?)",
            arrayOf(info.position, info.component.flattenToString(), info.widgetId, info.height)
        )
    }

    fun updateHeight(id: Int, height: Int) {
        Logger.d(TAG, "Updating widget $id height to $height")
        val db = writableDatabase ?: return
        db.execSQL(
            "UPDATE $WIDGETS_TABLE SET height = ? WHERE widgetId = ?",
            arrayOf(height, id)
        )
    }

    fun getWidgetHeight(id: Int): Int? {
        val db = writableDatabase ?: return null
        db.rawQuery(
            "SELECT height FROM $WIDGETS_TABLE WHERE widgetId = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow("height"))
            } else {
                null
            }
        }
    }

    companion object {
        private const val TAG = "WidgetsDbHelper"
        private const val DATABASE_VERSION = 1
        private const val WIDGETS_DB = "qsb_widgets"
        private const val WIDGETS_TABLE = "widget_items"

        private var instance: WidgetsDbHelper? = null

        @JvmStatic
        fun getInstance(context: Context): WidgetsDbHelper {
            if (instance == null) {
                instance = WidgetsDbHelper(context.applicationContext)
            }
            return requireNotNull(instance)
        }
    }
}
```

---

# bliss/src/foundation/e/bliss/widgets/DefaultWidgets.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/widgets/DefaultWidgets.kt`  
**Lines**: 40 | **Bytes**: 1459

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
package foundation.e.bliss.widgets

import android.content.ComponentName

object DefaultWidgets {
    private val ecloudWidget =
        ComponentName("foundation.e.drive", "foundation.e.drive.widgets.EDriveWidget")
    private val privacyWidget =
        ComponentName("foundation.e.advancedprivacy", "foundation.e.advancedprivacy.Widget")
    val oldWeatherWidget =
        ComponentName(
            "com.ihome.launcher",
            "foundation.e.blisslauncher.features.weather.WeatherAppWidgetProvider"
        )
    val weatherWidget =
        ComponentName(
            "foundation.e.blissweather",
            "foundation.e.blissweather.widget.WeatherAppWidgetProvider"
        )

    val defaultWidgets = listOf(ecloudWidget, privacyWidget, weatherWidget)
}
```

---

# bliss/src/foundation/e/bliss/preferences/BlissPrefs.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/bliss/preferences/BlissPrefs.kt`  
**Lines**: 26 | **Bytes**: 1002

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
package foundation.e.bliss.preferences

object BlissPrefs {
    const val PREF_SINGLE_LAYER_MODE = "pref_single_layer"
    const val PREF_NOTIF_COUNT = "pref_notif_count"
    /** Enables the LiquidGlass dock panel on API 33+. Default off. */
    const val PREF_GLASS_DOCK_ENABLED = "pref_glass_dock_enabled"
}
```

---

# bliss/src/foundation/e/blisslauncher/features/onboarding/DefaultHomeLayoutResolver.kt

**Absolute Path**: `D:\Code\OS_Launcher\bliss/src/foundation/e/blisslauncher/features/onboarding/DefaultHomeLayoutResolver.kt`  
**Lines**: 607 | **Bytes**: 25748

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
 */

package foundation.e.blisslauncher.features.onboarding

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.RemovedAppStore
import foundation.e.bliss.multimode.MultiModeController

/**
 * Resolves standard Android roles and categories to populate an iOS-style curated layout
 * on first run. Places 2x2 Clock and 2x2 Calendar Day widgets at the top of Screen 0,
 * followed by curated app icons below them, keeping the last 2 icon slots on Screen 0 blank,
 * and grouping work profile apps into a "Work" folder.
 */
object DefaultHomeLayoutResolver {
    private const val TAG = "DefaultHomeResolver"

    private class RoleDescriptor(
        val roleName: String,
        val intents: List<Intent>,
        val packageFallbacks: List<String> = emptyList()
    )

    private val CURATED_ROLES = listOf(
        RoleDescriptor(
            "Photos",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
                Intent(Intent.ACTION_VIEW).setType("image/*")
            ),
            listOf(
                "com.google.android.apps.photos",
                "com.android.gallery3d",
                "com.coloros.gallery3d",
                "com.oplus.gallery",
                "com.sec.android.gallery3d",
                "com.vivo.gallery",
                "com.miui.gallery",
                "com.oneplus.gallery"
            )
        ),
        RoleDescriptor(
            "Maps",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            ),
            listOf(
                "com.google.android.apps.maps"
            )
        ),
        RoleDescriptor(
            "Weather",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_WEATHER")
            ),
            listOf(
                "com.ihome.launcher.features.weather",
                "foundation.e.blisslauncher.features.weather",
                "com.google.android.apps.weather",
                "com.coloros.weather",
                "com.coloros.weather2",
                "com.oplus.weather",
                "com.sec.android.daemonapp",
                "com.vivo.weather",
                "com.vivo.weather.provider",
                "com.miui.weather2",
                "net.oneplus.weather",
                "com.accuweather.android"
            )
        ),
        RoleDescriptor(
            "Mail",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
                Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
            ),
            listOf(
                "com.google.android.gm",
                "com.android.email",
                "com.fsck.k9"
            )
        ),
        RoleDescriptor(
            "Contacts",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS),
                Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            ),
            listOf(
                "com.google.android.contacts",
                "com.android.contacts",
                "com.vivo.contacts",
                "com.oneplus.contacts"
            )
        ),
        RoleDescriptor(
            "Calculator",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            ),
            listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.coloros.calculator",
                "com.oplus.calculator",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator",
                "com.oneplus.calculator",
                "com.vivo.calculator",
                "com.android.bbkcalculator"
            )
        ),
        RoleDescriptor(
            "Files",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES),
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            ),
            listOf(
                "com.google.android.documentsui",
                "com.android.documentsui",
                "com.google.android.apps.nbu.files",
                "com.coloros.filemanager",
                "com.oplus.filemanager",
                "com.sec.android.app.myfiles",
                "com.mi.android.globalFileexplorer",
                "com.vivo.filemanager",
                "com.oneplus.filemanager"
            )
        ),
        RoleDescriptor(
            "Music",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            ),
            listOf(
                "com.google.android.apps.youtube.music",
                "com.android.music",
                "com.spotify.music"
            )
        ),
        RoleDescriptor(
            "Play Store",
            emptyList(),
            listOf(
                "com.android.vending"
            )
        ),
        RoleDescriptor(
            "Settings",
            listOf(
                Intent(Settings.ACTION_SETTINGS)
            ),
            listOf(
                "com.android.settings"
            )
        )
    )

    @JvmStatic
    fun populateCuratedPage0(context: Context, dbHelper: DatabaseHelper) {
        if (!MultiModeController.isSingleLayerMode) {
            return
        }

        // Clear any old removed-app records so fresh setup starts completely clean
        RemovedAppStore.clearAll(context)

        val db = dbHelper.writableDatabase
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val numCols = idp.numColumns
        val numRows = idp.numRows

        val occupiedCellsByScreen = HashMap<Int, HashSet<Pair<Int, Int>>>()
        val existingComponents = HashSet<ComponentName>()
        val existingPackages = HashSet<String>()
        val existingComponentKeys = HashSet<ComponentKey>()

        val myUser = Process.myUserHandle()
        val userCache = UserCache.INSTANCE.get(context)
        val userSerial = userCache.getSerialNumberForUser(myUser)
        val userManager = context.getSystemService(UserManager::class.java)
        val allProfiles = userManager?.userProfiles ?: listOf(myUser)
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        // 1. Inspect existing items in Favorites.TABLE_NAME_ALL (e.g. dock/hotseat items)
        val projection = arrayOf(
            Favorites._ID,
            Favorites.CONTAINER,
            Favorites.SCREEN,
            Favorites.CELLX,
            Favorites.CELLY,
            Favorites.SPANX,
            Favorites.SPANY,
            Favorites.INTENT,
            Favorites.PROFILE_ID,
            Favorites.ITEM_TYPE,
            Favorites.APPWIDGET_PROVIDER
        )
        var hasClockWidget = false
        var hasCalendarWidget = false
        val cursor = db.query(Favorites.TABLE_NAME_ALL, projection, null, null, null, null, null)
        cursor.use { c ->
            val colContainer = c.getColumnIndexOrThrow(Favorites.CONTAINER)
            val colScreen = c.getColumnIndexOrThrow(Favorites.SCREEN)
            val colCellX = c.getColumnIndexOrThrow(Favorites.CELLX)
            val colCellY = c.getColumnIndexOrThrow(Favorites.CELLY)
            val colSpanX = c.getColumnIndexOrThrow(Favorites.SPANX)
            val colSpanY = c.getColumnIndexOrThrow(Favorites.SPANY)
            val colIntent = c.getColumnIndexOrThrow(Favorites.INTENT)
            val colProfileId = c.getColumnIndexOrThrow(Favorites.PROFILE_ID)
            val colItemType = c.getColumnIndex(Favorites.ITEM_TYPE)
            val colProvider = c.getColumnIndex(Favorites.APPWIDGET_PROVIDER)

            while (c.moveToNext()) {
                val container = c.getLong(colContainer)
                val screen = c.getInt(colScreen)
                val cellX = c.getInt(colCellX)
                val cellY = c.getInt(colCellY)
                val spanX = c.getInt(colSpanX).coerceAtLeast(1)
                val spanY = c.getInt(colSpanY).coerceAtLeast(1)
                val intentStr = c.getString(colIntent)
                val profileSerial = c.getLong(colProfileId)
                val itemUser = userCache.getUserForSerialNumber(profileSerial) ?: myUser
                val itemType = if (colItemType >= 0) c.getInt(colItemType) else -1
                val providerStr = if (colProvider >= 0) c.getString(colProvider) else null

                if (itemType == Favorites.ITEM_TYPE_APPWIDGET && !providerStr.isNullOrEmpty()) {
                    if (providerStr.contains("ClockAppWidgetProvider") || providerStr.contains("DigitalClockAppWidgetProvider")) {
                        hasClockWidget = true
                    }
                    if (providerStr.contains("CalendarDayAppWidgetProvider") || providerStr.contains("CalendarMonthAppWidgetProvider")) {
                        hasCalendarWidget = true
                    }
                }

                if (container == Favorites.CONTAINER_DESKTOP.toLong()) {
                    val occupied = occupiedCellsByScreen.getOrPut(screen) { HashSet() }
                    for (x in cellX until (cellX + spanX)) {
                        for (y in cellY until (cellY + spanY)) {
                            occupied.add(Pair(x, y))
                        }
                    }
                }

                if (!intentStr.isNullOrEmpty()) {
                    try {
                        val parsedIntent = Intent.parseUri(intentStr, 0)
                        parsedIntent.component?.let {
                            existingComponents.add(it)
                            existingPackages.add(it.packageName)
                            existingComponentKeys.add(ComponentKey(it, itemUser))
                        } ?: run {
                            parsedIntent.`package`?.let { existingPackages.add(it) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing intent: $intentStr", e)
                    }
                }
            }
        }

        // 1b. Place 2x2 Clock widget (top-left) and 2x2 Calendar Day widget (top-right) at top of Screen 0
        val clockProvider = ComponentName(context.packageName, "foundation.e.bliss.widgets.clock.ClockAppWidgetProvider")
        val calendarProvider = ComponentName(context.packageName, "foundation.e.bliss.widgets.calendar.CalendarDayAppWidgetProvider")

        val widgetHolder = dbHelper.newLauncherWidgetHolder()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        fun placeWidgetIfMissing(cn: ComponentName, screen: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
            try {
                val appWidgetId = widgetHolder.allocateAppWidgetId()
                val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)
                if (bound) {
                    val values = ContentValues().apply {
                        put(Favorites._ID, dbHelper.generateNewItemId())
                        put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET)
                        put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                        put(Favorites.SCREEN, screen)
                        put(Favorites.CELLX, cellX)
                        put(Favorites.CELLY, cellY)
                        put(Favorites.SPANX, spanX)
                        put(Favorites.SPANY, spanY)
                        put(Favorites.APPWIDGET_ID, appWidgetId)
                        put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString())
                        put(Favorites.APPWIDGET_SOURCE, Favorites.CONTAINER_DESKTOP)
                        put(Favorites.PROFILE_ID, userSerial)
                        put(Favorites.MODIFIED, System.currentTimeMillis())
                    }
                    dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
                    val occupied = occupiedCellsByScreen.getOrPut(screen) { HashSet() }
                    for (x in cellX until (cellX + spanX)) {
                        for (y in cellY until (cellY + spanY)) {
                            occupied.add(Pair(x, y))
                        }
                    }
                    Log.d(TAG, "Placed widget $cn at screen=$screen ($cellX, $cellY, ${spanX}x${spanY}) id=$appWidgetId")
                } else {
                    Log.w(TAG, "bindAppWidgetIdIfAllowed returned false for $cn")
                    widgetHolder.deleteAppWidgetId(appWidgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to place widget $cn", e)
            }
        }

        if (!hasClockWidget) {
            placeWidgetIfMissing(clockProvider, 0, 0, 0, 2, 2)
        }
        if (!hasCalendarWidget) {
            placeWidgetIfMissing(calendarProvider, 0, 2, 0, 2, 2)
        }

        val workActivities = ArrayList<LauncherActivityInfo>()
        val personalActivities = ArrayList<LauncherActivityInfo>()

        for (profile in allProfiles) {
            val isWork = userCache.getUserInfo(profile).isWork
            val acts = launcherApps?.getActivityList(null, profile) ?: emptyList()
            if (isWork) {
                workActivities.addAll(acts)
            } else {
                personalActivities.addAll(acts)
            }
        }

        // 2. Resolve curated roles for personal profile
        val curatedToPlace = ArrayList<ComponentName>()
        for (role in CURATED_ROLES) {
            val resolved = resolveRole(context, role, launcherApps, myUser)
            if (resolved != null) {
                val key = ComponentKey(resolved, myUser)
                if (existingComponentKeys.contains(key) || existingPackages.contains(resolved.packageName)) {
                    Log.d(TAG, "Role ${role.roleName} resolved to $resolved but already in DB (deduped).")
                } else {
                    curatedToPlace.add(resolved)
                    existingComponents.add(resolved)
                    existingPackages.add(resolved.packageName)
                    existingComponentKeys.add(key)
                    Log.d(TAG, "Role ${role.roleName} resolved to $resolved to be placed on Home Screen.")
                }
            } else {
                Log.d(TAG, "Role ${role.roleName} not found on device.")
            }
        }

        // 3. Collect remaining personal activities (sorted alphabetically)
        val remainingPersonalToPlace = ArrayList<Pair<ComponentName, Long>>()
        val sortedPersonal = personalActivities.sortedBy { it.label?.toString()?.lowercase() ?: "" }
        for (act in sortedPersonal) {
            val comp = act.componentName
            val key = ComponentKey(comp, act.user)
            if (!existingComponentKeys.contains(key)) {
                val serial = userCache.getSerialNumberForUser(act.user)
                remainingPersonalToPlace.add(Pair(comp, serial))
                existingComponentKeys.add(key)
                existingComponents.add(comp)
            }
        }

        // 4. Reserve the last 2 cells on Screen 0 so it stays clean and not too crowded
        val reservedCellsScreen0 = HashSet<Pair<Int, Int>>()
        val totalCellsScreen0 = numCols * numRows
        for (idx in (totalCellsScreen0 - 2) until totalCellsScreen0) {
            if (idx >= 0) {
                val rx = idx % numCols
                val ry = idx / numCols
                reservedCellsScreen0.add(Pair(rx, ry))
            }
        }

        var currentScreen = 0
        var currentX = 0
        var currentY = 0

        fun getNextAvailableCell(): Triple<Int, Int, Int> {
            while (true) {
                val occupiedOnScreen = occupiedCellsByScreen.getOrPut(currentScreen) { HashSet() }
                val cell = Pair(currentX, currentY)
                val isReserved = (currentScreen == 0 && reservedCellsScreen0.contains(cell))
                val isOccupied = occupiedOnScreen.contains(cell)

                if (!isOccupied && !isReserved) {
                    val result = Triple(currentScreen, currentX, currentY)
                    occupiedOnScreen.add(cell)
                    currentX++
                    if (currentX >= numCols) {
                        currentX = 0
                        currentY++
                        if (currentY >= numRows) {
                            currentScreen++
                            currentX = 0
                            currentY = 0
                        }
                    }
                    return result
                }

                currentX++
                if (currentX >= numCols) {
                    currentX = 0
                    currentY++
                    if (currentY >= numRows) {
                        currentScreen++
                        currentX = 0
                        currentY = 0
                    }
                }
            }
        }

        // 5. Place curated personal apps
        for (component in curatedToPlace) {
            val (screen, cellX, cellY) = getNextAvailableCell()
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(component)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            val values = ContentValues().apply {
                put(Favorites._ID, dbHelper.generateNewItemId())
                put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                put(Favorites.SCREEN, screen)
                put(Favorites.CELLX, cellX)
                put(Favorites.CELLY, cellY)
                put(Favorites.SPANX, 1)
                put(Favorites.SPANY, 1)
                put(Favorites.INTENT, launchIntent.toUri(0))
                put(Favorites.PROFILE_ID, userSerial)
                put(Favorites.MODIFIED, System.currentTimeMillis())
            }
            dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
        }

        // 6. Place Work folder if work profile apps exist
        if (workActivities.isNotEmpty()) {
            val (folderScreen, folderCellX, folderCellY) = getNextAvailableCell()
            val folderId = dbHelper.generateNewItemId()
            val folderValues = ContentValues().apply {
                put(Favorites._ID, folderId)
                put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER)
                put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                put(Favorites.SCREEN, folderScreen)
                put(Favorites.CELLX, folderCellX)
                put(Favorites.CELLY, folderCellY)
                put(Favorites.SPANX, 1)
                put(Favorites.SPANY, 1)
                put(Favorites.TITLE, "Work")
                put(Favorites.PROFILE_ID, userSerial)
                put(Favorites.MODIFIED, System.currentTimeMillis())
            }
            dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, folderValues)

            var workRank = 0
            for (workAct in workActivities) {
                val workUserSerial = userCache.getSerialNumberForUser(workAct.user)
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setComponent(workAct.componentName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                val itemValues = ContentValues().apply {
                    put(Favorites._ID, dbHelper.generateNewItemId())
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.CONTAINER, folderId)
                    put(Favorites.SCREEN, 0)
                    put(Favorites.CELLX, 0)
                    put(Favorites.CELLY, 0)
                    put(Favorites.SPANX, 1)
                    put(Favorites.SPANY, 1)
                    put(Favorites.RANK, workRank++)
                    put(Favorites.INTENT, launchIntent.toUri(0))
                    put(Favorites.PROFILE_ID, workUserSerial)
                    put(Favorites.MODIFIED, System.currentTimeMillis())
                }
                dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, itemValues)
                existingComponents.add(workAct.componentName)
                existingPackages.add(workAct.componentName.packageName)
            }
            Log.d(TAG, "Created 'Work' folder on screen $folderScreen with ${workActivities.size} apps.")
        }

        // 7. Place all remaining personal apps across screens
        for ((component, serial) in remainingPersonalToPlace) {
            val (screen, cellX, cellY) = getNextAvailableCell()
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(component)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            val values = ContentValues().apply {
                put(Favorites._ID, dbHelper.generateNewItemId())
                put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                put(Favorites.SCREEN, screen)
                put(Favorites.CELLX, cellX)
                put(Favorites.CELLY, cellY)
                put(Favorites.SPANX, 1)
                put(Favorites.SPANY, 1)
                put(Favorites.INTENT, launchIntent.toUri(0))
                put(Favorites.PROFILE_ID, serial)
                put(Favorites.MODIFIED, System.currentTimeMillis())
            }
            dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
        }

        Log.d(TAG, "Completed default layout population: all apps placed, Screen 0 has Clock and Calendar widgets at top and last 2 slots blank.")
    }

    private fun resolveRole(
        context: Context,
        role: RoleDescriptor,
        launcherApps: LauncherApps?,
        user: android.os.UserHandle
    ): ComponentName? {
        val pm = context.packageManager

        // First try standard intents
        for (intent in role.intents) {
            try {
                val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo?.activityInfo != null) {
                    val pkg = resolveInfo.activityInfo.packageName
                    val comp = findLauncherActivity(launcherApps, pkg, user)
                    if (comp != null) return comp
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Second try known package fallbacks
        for (pkg in role.packageFallbacks) {
            val comp = findLauncherActivity(launcherApps, pkg, user)
            if (comp != null) return comp
        }

        // Third: for Weather, search installed packages containing 'weather'
        if (role.roleName == "Weather") {
            val all = launcherApps?.getActivityList(null, user) ?: emptyList()
            for (act in all) {
                if (act.applicationInfo.packageName.contains("weather", ignoreCase = true)) {
                    return act.componentName
                }
            }
        }

        return null
    }

    private fun findLauncherActivity(
        launcherApps: LauncherApps?,
        packageName: String,
        user: android.os.UserHandle
    ): ComponentName? {
        if (launcherApps == null) return null
        try {
            val activities = launcherApps.getActivityList(packageName, user)
            if (activities.isNotEmpty()) {
                return activities[0].componentName
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }
}
```

---

