package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;

public class DragTrace {
    public static void log(String m) {
        Log.d("DragTrace", SystemClock.uptimeMillis() + " " + m);
    }
}
