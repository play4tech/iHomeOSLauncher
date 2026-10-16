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
