/*
 * Copyright (C) 2024 The /e/ Foundation
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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Safe access to LineageSettings for devices that do not ship LineageOS /e/ framework APIs.
 * Stock AVDs and non-Lineage builds throw {@link NoSuchMethodError} from hidden APIs such as
 * {@code ContentResolver#getUserId()}.
 */
public final class LineageSettingsCompat {

    private static final String TAG = "LineageSettingsCompat";
    private static Boolean sIsLineageSettingsAvailable = null;

    private LineageSettingsCompat() {}

    /** Checks if the LineageSettings provider is available on this device. */
    public static boolean isLineageSettingsAvailable(Context context) {
        if (sIsLineageSettingsAvailable == null) {
            sIsLineageSettingsAvailable = context.getPackageManager()
                    .resolveContentProvider("lineagesettings", 0) != null;
        }
        return sIsLineageSettingsAvailable;
    }

    /** Returns the setting value, or {@code defaultValue} if LineageSettings is unavailable. */
    public static int getSystemInt(Context context, String name, int defaultValue) {
        if (!isLineageSettingsAvailable(context)) {
            return defaultValue;
        }
        try {
            Class<?> lineageSystem = Class.forName("lineageos.providers.LineageSettings$System");
            return (Integer) lineageSystem
                    .getMethod("getInt", ContentResolver.class, String.class, int.class)
                    .invoke(null, context.getContentResolver(), name, defaultValue);
        } catch (Throwable t) {
            Log.w(TAG, "LineageSettings.System.getInt unavailable for " + name
                    + ", using default=" + defaultValue);
            return defaultValue;
        }
    }

    /** Returns the setting URI, or {@code null} if LineageSettings is unavailable. */
    public static Uri getSystemUriFor(String name) {
        try {
            Class<?> lineageSystem = Class.forName("lineageos.providers.LineageSettings$System");
            return (Uri) lineageSystem.getMethod("getUriFor", String.class).invoke(null, name);
        } catch (Throwable t) {
            Log.w(TAG, "LineageSettings.System.getUriFor unavailable for " + name);
            return null;
        }
    }

    public static final String ENABLE_TASKBAR = "enable_taskbar";
    public static final String NAVIGATION_BAR_HINT = "navigation_bar_hint";
}
