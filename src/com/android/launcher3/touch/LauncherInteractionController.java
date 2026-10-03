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
 * distributed under the Apache License, Version 2.0 (the "License");
 * installation, training, or fine-tuning purposes.
 */
package com.android.launcher3.touch;

import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;

/**
 * Lightweight coordinator managing interaction sessions (PopupSession vs DragSession)
 * to enforce explicit ownership transitions and eliminate asynchronous race conditions.
 */
public class LauncherInteractionController {

    private static final String TAG = "InteractionController";

    /**
     * Immutable session describing an active Popup interaction.
     */
    public record PopupSession(
            PopupContainerWithArrow popupContainer,
            View originalIcon,
            ItemInfo itemInfo,
            long startTime
    ) {
        public void cancel() {
            if (popupContainer != null) {
                popupContainer.cancelForDrag();
            }
        }
    }

    /**
     * Immutable session describing an active Drag interaction.
     */
    public record DragSession(
            View draggedView,
            ItemInfo itemInfo,
            long startTime
    ) {}

    private PopupSession mActivePopupSession;
    private DragSession mActiveDragSession;

    public synchronized void beginPopup(PopupContainerWithArrow container, View icon, ItemInfo info) {
        if (mActivePopupSession != null && mActivePopupSession.popupContainer() != container) {
            Log.w(TAG, "beginPopup called while another popup session was active; cancelling previous popup.");
            mActivePopupSession.cancel();
        }
        mActivePopupSession = new PopupSession(container, icon, info, System.currentTimeMillis());
        Log.d(TAG, "PopupSession started for: " + (info != null ? info.title : "unknown"));
    }

    public synchronized void endPopup(PopupContainerWithArrow container) {
        if (mActivePopupSession != null && mActivePopupSession.popupContainer() == container) {
            Log.d(TAG, "PopupSession ended.");
            mActivePopupSession = null;
        }
    }

    public synchronized void beginDrag(View draggedView, ItemInfo info) {
        if (mActivePopupSession != null) {
            Log.d(TAG, "Ownership transition: PopupSession -> DragSession. Cancelling active popup.");
            mActivePopupSession.cancel();
            mActivePopupSession = null;
        }

        mActiveDragSession = new DragSession(draggedView, info, System.currentTimeMillis());
        Log.d(TAG, "DragSession started for: " + (info != null ? info.title : "unknown"));
    }

    public synchronized void endDrag() {
        if (mActiveDragSession != null) {
            Log.d(TAG, "DragSession ended.");
            mActiveDragSession = null;
        }
    }

    public synchronized boolean hasActivePopup() {
        return mActivePopupSession != null;
    }

    public synchronized boolean hasActiveDrag() {
        return mActiveDragSession != null;
    }

    @Nullable
    public synchronized PopupSession getActivePopupSession() {
        return mActivePopupSession;
    }

    @Nullable
    public synchronized DragSession getActiveDragSession() {
        return mActiveDragSession;
    }
}
