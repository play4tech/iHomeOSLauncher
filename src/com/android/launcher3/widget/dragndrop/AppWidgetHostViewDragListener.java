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
package com.android.launcher3.widget.dragndrop;

import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/** A drag listener of {@link LauncherAppWidgetHostView}. */
public final class AppWidgetHostViewDragListener implements DragController.DragListener {
    private final Launcher mLauncher;
    @Nullable
    private LauncherAppWidgetHostView mAppWidgetHostView;

    public AppWidgetHostViewDragListener(Launcher launcher) {
        this(launcher, null);
    }

    public AppWidgetHostViewDragListener(Launcher launcher, @Nullable LauncherAppWidgetHostView appWidgetHostView) {
        mLauncher = launcher;
        mAppWidgetHostView = appWidgetHostView;
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions unused) {
        if (dragObject != null && dragObject.dragView != null
                && dragObject.dragView.getContentView() instanceof LauncherAppWidgetHostView) {
            mAppWidgetHostView = (LauncherAppWidgetHostView) dragObject.dragView.getContentView();
            mAppWidgetHostView.setVisibility(View.VISIBLE);
            mAppWidgetHostView.startDrag();
        } else if (mAppWidgetHostView != null) {
            mAppWidgetHostView.setVisibility(View.VISIBLE);
            mAppWidgetHostView.startDrag();
        } else {
            mLauncher.getDragController().removeDragListener(this);
        }
    }

    @Override
    public void onDragEnd() {
        if (mAppWidgetHostView != null) {
            mAppWidgetHostView.endDrag();
        }
        mLauncher.getDragController().removeDragListener(this);
    }
}
