package com.android.systemui.shared.system;

import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import com.android.internal.jank.InteractionJankMonitor;

/**
 * Wrapper for InteractionJankMonitor to guard against OEM ROM incompatibilities (e.g. ColorOS / Realme UI).
 */
public final class InteractionJankMonitorWrapper {

    public InteractionJankMonitorWrapper() {
    }

    public static void begin(View view, int cuj) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InteractionJankMonitor.getInstance().begin(view, cuj);
        } catch (Throwable ignored) {
        }
    }

    public static void begin(View view, int cuj, long timeout) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InteractionJankMonitor.Configuration.Builder builder =
                    InteractionJankMonitor.Configuration.Builder.withView(cuj, view).setTimeout(timeout);
            InteractionJankMonitor.getInstance().begin(builder);
        } catch (Throwable ignored) {
        }
    }

    public static void begin(View view, int cuj, String tag) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InteractionJankMonitor.Configuration.Builder builder =
                    InteractionJankMonitor.Configuration.Builder.withView(cuj, view);
            if (!TextUtils.isEmpty(tag)) {
                builder.setTag(tag);
            }
            InteractionJankMonitor.getInstance().begin(builder);
        } catch (Throwable ignored) {
        }
    }

    public static void end(int cuj) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InteractionJankMonitor.getInstance().end(cuj);
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(int cuj) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InteractionJankMonitor.getInstance().cancel(cuj);
        } catch (Throwable ignored) {
        }
    }
}
