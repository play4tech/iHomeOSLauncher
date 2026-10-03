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

package com.android.launcher3.touch;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.util.TouchController;

/**
 * Intercepts downward swipe gestures on the workspace to expand the Android notification shade.
 */
public class NotificationShadeTouchController implements TouchController {

    private final Launcher mLauncher;
    private final float mTouchSlop;
    private final PointF mDownPoint = new PointF();
    private boolean mCanIntercept;

    public NotificationShadeTouchController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = ViewConfiguration.get(launcher).getScaledTouchSlop();
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(LauncherState.NORMAL)
                || AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        if (mLauncher.getWorkspace() != null && mLauncher.getWorkspace().isWobbling()) {
            return false;
        }
        if (mLauncher.getWorkspace() != null && mLauncher.getWorkspace().isAppLibraryCurrentPage()) {
            return false;
        }
        if (mLauncher.getDragController() != null && mLauncher.getDragController().isDragging()) {
            return false;
        }
        DeviceProfile dp = mLauncher.getDeviceProfile();
        if (ev.getY() > (mLauncher.getDragLayer().getHeight() - dp.getInsets().bottom)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mDownPoint.set(ev.getX(), ev.getY());
        }
        if (!mCanIntercept) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mLauncher.getDragController() != null && mLauncher.getDragController().isDragging()) {
                mCanIntercept = false;
                return false;
            }
            float dy = ev.getY() - mDownPoint.y;
            float dx = ev.getX() - mDownPoint.x;
            if (dy > mTouchSlop && dy > Math.abs(dx) * 1.3f && ev.getPointerCount() == 1) {
                return true;
            }
            if (Math.abs(dx) > mTouchSlop) {
                mCanIntercept = false;
            }
        }
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float dy = ev.getY() - mDownPoint.y;
            if (dy > mTouchSlop) {
                Launcher.expandNotificationShade(mLauncher);
                return true;
            }
        }
        return true;
    }
}
