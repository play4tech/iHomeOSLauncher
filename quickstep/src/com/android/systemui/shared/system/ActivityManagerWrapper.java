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

package com.android.systemui.shared.system;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityClient;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.window.TaskSnapshot;

import com.android.internal.app.IVoiceInteractionManagerService;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ActivityManagerWrapper {

    private static final String TAG = "ActivityManagerWrapper";
    private static final int NUM_RECENT_ACTIVITIES_REQUEST = 3;
    private static final ActivityManagerWrapper sInstance = new ActivityManagerWrapper();

    // Should match the values in PhoneWindowManager
    public static final String CLOSE_SYSTEM_WINDOWS_REASON_RECENTS = "recentapps";
    public static final String CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY = "homekey";

    // Should match the value in AssistManager
    private static final String INVOCATION_TIME_MS_KEY = "invocation_time_ms";

    private final ActivityTaskManager mAtm;

    private ActivityManagerWrapper() {
        ActivityTaskManager atm = null;
        try {
            atm = ActivityTaskManager.getInstance();
        } catch (Throwable t) {
            Log.w(TAG, "ActivityTaskManager.getInstance() not available on OEM ROM", t);
        }
        mAtm = atm;
    }

    public static ActivityManagerWrapper getInstance() {
        return sInstance;
    }

    private static IActivityTaskManager getAtmService() {
        try {
            return ActivityTaskManager.getService();
        } catch (Throwable t) {
            try {
                return IActivityTaskManager.Stub.asInterface(
                        ServiceManager.getService(Context.ACTIVITY_TASK_SERVICE));
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static IActivityManager getAmService() {
        try {
            return ActivityManager.getService();
        } catch (Throwable t) {
            try {
                return android.app.ActivityManagerNative.getDefault();
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /**
     * @return the current user's id.
     */
    public int getCurrentUserId() {
        try {
            IActivityManager am = getAmService();
            if (am != null) {
                UserInfo ui = am.getCurrentUser();
                return ui != null ? ui.id : 0;
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to get current user id", e);
        }
        return 0;
    }

    /**
     * @return the top running task (can be {@code null}).
     */
    public ActivityManager.RunningTaskInfo getRunningTask() {
        return getRunningTask(false /* filterVisibleRecents */);
    }

    /**
     * @return the top running task filtering only for tasks that can be visible in the recent tasks
     * list (can be {@code null}).
     */
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {
        if (mAtm == null) {
            return null;
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    mAtm.getTasks(1, filterOnlyVisibleRecents);
            if (tasks != null && !tasks.isEmpty()) {
                return tasks.get(0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get running task", t);
        }
        return null;
    }

    /**
     * @see #getRunningTasks(boolean , int)
     */
    public ActivityManager.RunningTaskInfo[] getRunningTasks(boolean filterOnlyVisibleRecents) {
        return getRunningTasks(filterOnlyVisibleRecents, Display.INVALID_DISPLAY);
    }

    /**
     * We ask for {@link #NUM_RECENT_ACTIVITIES_REQUEST} activities because when in split screen,
     * we'll get back 2 activities for each split app and one for launcher. Launcher might be more
     * "recently" used than one of the split apps so if we only request 2 tasks, then we might miss
     * out on one of the split apps
     *
     * @return an array of up to {@link #NUM_RECENT_ACTIVITIES_REQUEST} running tasks
     *         filtering only for tasks that can be visible in the recent tasks list.
     */
    public ActivityManager.RunningTaskInfo[] getRunningTasks(boolean filterOnlyVisibleRecents,
            int displayId) {
        if (mAtm == null) {
            return new ActivityManager.RunningTaskInfo[0];
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    mAtm.getTasks(NUM_RECENT_ACTIVITIES_REQUEST,
                            filterOnlyVisibleRecents, /* keepInExtras= */ false, displayId);
            if (tasks != null) {
                return tasks.toArray(new RunningTaskInfo[tasks.size()]);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to get running tasks", t);
        }
        return new ActivityManager.RunningTaskInfo[0];
    }

    /**
     * @return a {@link ThumbnailData} with {@link TaskSnapshot} for the given {@param taskId}.
     *         The snapshot will be triggered if no cached {@link TaskSnapshot} exists.
     */
    public @NonNull ThumbnailData getTaskThumbnail(int taskId, boolean isLowResolution) {
        TaskSnapshot snapshot = null;
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                snapshot = atm.getTaskSnapshot(taskId, isLowResolution);
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to retrieve task snapshot", e);
        }
        if (snapshot != null) {
            return new ThumbnailData(snapshot);
        } else {
            return new ThumbnailData();
        }
    }

    /**
     * @return a {@link ThumbnailData} with {@link TaskSnapshot} for the given {@param taskId}.
     *         The snapshot will be triggered if no cached {@link TaskSnapshot} exists.
     */
    public @NonNull ThumbnailData takeTaskThumbnail(int taskId) {
        TaskSnapshot snapshot = null;
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                snapshot = atm.takeTaskSnapshot(taskId, true);
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to take task snapshot", e);
        }
        if (snapshot != null) {
            return new ThumbnailData(snapshot);
        } else {
            return new ThumbnailData();
        }
    }

    /**
     * Removes the outdated snapshot of home task.
     *
     * @param homeActivity The home task activity, or null if you have the
     *                     {@link android.Manifest.permission#MANAGE_ACTIVITY_TASKS} permission and
     *                     want us to find the home task for you.
     */
    public void invalidateHomeTaskSnapshot(@Nullable final Activity homeActivity) {
        try {
            ActivityClient.getInstance().invalidateHomeTaskSnapshot(
                    homeActivity == null ? null : homeActivity.getActivityToken());
        } catch (Throwable e) {
            Log.w(TAG, "Failed to invalidate home snapshot", e);
        }
    }

    /**
     * Starts the recents activity. The caller should manage the thread on which this is called.
     */
    public void startRecentsActivity(Intent intent, long eventTime,
            final RecentsAnimationListener animationHandler, final Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        boolean result = startRecentsActivity(intent, eventTime, animationHandler);
        if (resultCallback != null && resultCallbackHandler != null) {
            resultCallbackHandler.post(() -> resultCallback.accept(result));
        }
    }

    /**
     * Starts the recents activity. The caller should manage the thread on which this is called.
     */
    public boolean startRecentsActivity(
            Intent intent, long eventTime, RecentsAnimationListener animationHandler) {
        try {
            IRecentsAnimationRunner runner = null;
            if (animationHandler != null) {
                runner = new IRecentsAnimationRunner.Stub() {
                    @Override
                    public void onAnimationStart(IRecentsAnimationController controller,
                            RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
                            Rect homeContentInsets, Rect minimizedHomeBounds, Bundle extras) {
                        final RecentsAnimationControllerCompat controllerCompat =
                                new RecentsAnimationControllerCompat(controller);
                        animationHandler.onAnimationStart(controllerCompat, apps,
                                wallpapers, homeContentInsets, minimizedHomeBounds, extras);
                    }

                    @Override
                    public void onAnimationCanceled(int[] taskIds, TaskSnapshot[] taskSnapshots) {
                        animationHandler.onAnimationCanceled(
                                ThumbnailData.wrap(taskIds, taskSnapshots));
                    }

                    @Override
                    public void onTasksAppeared(RemoteAnimationTarget[] apps) {
                        animationHandler.onTasksAppeared(apps);
                    }
                };
            }
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                atm.startRecentsActivity(intent, eventTime, runner);
                return true;
            }
            return false;
        } catch (Throwable e) {
            Log.w(TAG, "startRecentsActivity failed", e);
            return false;
        }
    }

    /**
     * Cancels the remote recents animation started from {@link #startRecentsActivity}.
     */
    public void cancelRecentsAnimation(boolean restoreHomeRootTaskPosition) {
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                atm.cancelRecentsAnimation(restoreHomeRootTaskPosition);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to cancel recents animation", e);
        }
    }

    /**
     * Starts a task from Recents synchronously.
     */
    public boolean startActivityFromRecents(Task.TaskKey taskKey, ActivityOptions options) {
        return startActivityFromRecents(taskKey.id, options);
    }

    /**
     * Starts a task from Recents synchronously.
     */
    public boolean startActivityFromRecents(int taskId, ActivityOptions options) {
        try {
            Bundle optsBundle = options == null ? null : options.toBundle();
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                return ActivityManager.isStartResultSuccessful(
                        atm.startActivityFromRecents(taskId, optsBundle));
            }
            return false;
        } catch (Throwable e) {
            Log.w(TAG, "startActivityFromRecents failed", e);
            return false;
        }
    }

    /**
     * Requests that the system close any open system windows (including other SystemUI).
     */
    public void closeSystemWindows(final String reason) {
        try {
            IActivityManager am = getAmService();
            if (am != null) {
                am.closeSystemDialogs(reason);
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to close system windows", e);
        }
    }

    /**
     * Removes a task by id.
     */
    public void removeTask(final int taskId) {
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                atm.removeTask(taskId);
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to remove task=" + taskId, e);
        }
    }

    /**
     * Removes all the recent tasks.
     */
    public void removeAllRecentTasks() {
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                atm.removeAllVisibleRecentTasks();
            }
        } catch (Throwable e) {
            Log.w(TAG, "Failed to remove all tasks", e);
        }
    }

    /**
     * @return whether screen pinning is enabled.
     */
    public boolean isScreenPinningEnabled() {
        try {
            final ContentResolver cr = AppGlobals.getInitialApplication().getContentResolver();
            return Settings.System.getInt(cr, Settings.System.LOCK_TO_APP_ENABLED, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return whether there is currently a locked task (ie. in screen pinning).
     */
    public boolean isLockToAppActive() {
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                return atm.getLockTaskModeState() != LOCK_TASK_MODE_NONE;
            }
        } catch (Throwable e) {
            // ignore
        }
        return false;
    }

    /**
     * @return whether lock task mode is active in kiosk-mode (not screen pinning).
     */
    public boolean isLockTaskKioskModeActive() {
        try {
            IActivityTaskManager atm = getAtmService();
            if (atm != null) {
                return atm.getLockTaskModeState() == LOCK_TASK_MODE_LOCKED;
            }
        } catch (Throwable e) {
            // ignore
        }
        return false;
    }

    /**
     * Shows a voice session identified by {@code token}
     * @return true if the session was shown, false otherwise
     */
    public boolean showVoiceSession(@NonNull IBinder token, @NonNull Bundle args, int flags,
            @Nullable String attributionTag) {
        try {
            IVoiceInteractionManagerService service = IVoiceInteractionManagerService.Stub.asInterface(
                    ServiceManager.getService(Context.VOICE_INTERACTION_MANAGER_SERVICE));
            if (service == null) {
                return false;
            }
            args.putLong(INVOCATION_TIME_MS_KEY, SystemClock.elapsedRealtime());
            return service.showSessionFromSession(token, args, flags, attributionTag);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Returns true if the system supports freeform multi-window.
     */
    public boolean supportsFreeformMultiWindow(Context context) {
        try {
            final boolean freeformDevOption = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) != 0;
            boolean multiWindowSupported = false;
            try {
                multiWindowSupported = ActivityTaskManager.supportsMultiWindow(context);
            } catch (Throwable t) {
                multiWindowSupported = false;
            }
            return multiWindowSupported
                    && (context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                    || freeformDevOption);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns true if the running task represents the home task
     */
    public static boolean isHomeTask(RunningTaskInfo info) {
        try {
            return info.configuration.windowConfiguration.getActivityType()
                    == WindowConfiguration.ACTIVITY_TYPE_HOME;
        } catch (Throwable t) {
            return false;
        }
    }
}
