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
}
