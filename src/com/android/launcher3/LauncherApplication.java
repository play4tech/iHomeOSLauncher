/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.app.Application;
import android.os.Looper;
import android.util.Log;

/**
 * Main application class for Launcher
 */
public class LauncherApplication extends Application {

    private static final String TAG = "LauncherApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        setupUncaughtExceptionHandler();
        MainProcessInitializer.initialize(this);
        foundation.e.bliss.glass.GlassConfig.disableSensors(this);

        if (!BuildConfig.DEBUG && BuildConfig.SENTRY_DSN != null && !BuildConfig.SENTRY_DSN.isEmpty()) {
            try {
                foundation.e.lib.telemetry.Telemetry.init(BuildConfig.SENTRY_DSN, this, true);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Sentry", e);
            }
        }
    }

    private void setupUncaughtExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (shouldSuppressException(thread, throwable)) {
                Log.w(TAG, "Suppressed external/OEM crash on thread ["
                        + (thread != null ? thread.getName() : "unknown") + "]: "
                        + (throwable != null ? throwable.toString() : "null"), throwable);
                return;
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private static boolean isLauncherOwnedThread(Thread thread) {
        if (thread == null) return false;
        if (Looper.getMainLooper().getThread() == thread) return true;
        String name = thread.getName();
        if (name == null) return false;
        return name.equals("launcher-loader")
                || name.equals("BackgroundExecutor")
                || name.equals("UiThreadHelper")
                || name.startsWith("preinflate-")
                || name.startsWith("IconCache-");
    }

    private static boolean shouldSuppressException(Thread thread, Throwable throwable) {
        if (throwable == null || isLauncherOwnedThread(thread)) {
            return false;
        }

        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            // Check stack trace elements
            StackTraceElement[] stack = current.getStackTrace();
            if (stack != null) {
                for (StackTraceElement element : stack) {
                    String className = element.getClassName();
                    if (className == null) {
                        continue;
                    }

                    // Known OEM and third-party widget packages that may crash on background threads
                    if (className.contains(".vivo.")
                            || className.contains(".bbk.")
                            || className.contains(".oppo.")
                            || className.contains(".coloros.")
                            || className.contains(".heytap.")
                            || className.contains(".oplus.")
                            || className.contains(".miui.")
                            || className.contains(".xiaomi.")
                            || className.contains(".samsung.")
                            || className.contains(".sec.android.")
                            || className.contains(".transsion.")
                            || className.contains(".huawei.")
                            || className.contains(".honor.")
                            || className.contains("timerwidget")
                            || className.contains("analogworldclockwidget")) {
                        return true;
                    }

                    // On background threads: if the crash originates in an appwidget / remoteviews component
                    if (className.startsWith("android.appwidget.")
                            || className.contains("RemoteViews")
                            || className.startsWith("com.android.server.appwidget.")) {
                        return true;
                    }
                }
            }

            // Also on background threads, catch LinkageError (NoSuchMethodError, NoClassDefFoundError, etc.)
            // caused by vendor OEM code calling hidden/removed framework APIs on Android 16
            if (current instanceof LinkageError) {
                boolean containsLauncherCode = false;
                if (stack != null) {
                    for (StackTraceElement element : stack) {
                        String className = element.getClassName();
                        if (className != null && (className.startsWith("com.android.launcher3")
                                || className.startsWith("foundation.e.bliss")
                                || className.startsWith("com.ihome.launcher"))) {
                            containsLauncherCode = true;
                            break;
                        }
                    }
                }
                if (!containsLauncherCode) {
                    return true;
                }
            }

            current = current.getCause();
            depth++;
        }

        return false;
    }
}
