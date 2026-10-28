package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;
import com.android.launcher3.BuildConfig;

public class DragTrace {
    public static void log(String m) {
        if (!BuildConfig.DEBUG) return;
        Log.d("DragTrace", SystemClock.uptimeMillis() + " " + m);
    }
}
