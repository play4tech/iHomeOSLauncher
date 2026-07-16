/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Extension of {@link WindowManagerProxy} with some assumption for the default system Launcher
 */
public class SystemWindowManagerProxy extends WindowManagerProxy {

    private static final String TAG = "SystemWindowManagerProxy";

    public SystemWindowManagerProxy(Context context) {
        super(true);
    }

    @Override
    public Rect getCurrentBounds(Context displayInfoContext) {
        try {
            return displayInfoContext.getResources().getConfiguration().windowConfiguration
                    .getMaxBounds();
        } catch (Throwable t) {
            // windowConfiguration APIs may be unavailable to non-system apps
            WindowManager wm = displayInfoContext.getSystemService(WindowManager.class);
            return new Rect(wm.getMaximumWindowMetrics().getBounds());
        }
    }

    @Override
    public int getRotation(Context displayInfoContext) {
        try {
            return displayInfoContext.getResources().getConfiguration().windowConfiguration
                    .getRotation();
        } catch (Throwable t) {
            WindowManager wm = displayInfoContext.getSystemService(WindowManager.class);
            return displayInfoContext.getDisplay() != null
                    ? displayInfoContext.getDisplay().getRotation()
                    : Surface.ROTATION_0;
        }
    }

    @Override
    protected int getStatusBarHeight(Context context, boolean isPortrait, int statusBarInset) {
        // Prefer SystemBarUtils on system images; fall back to the reported inset otherwise.
        // See b/264656380 for why system launcher calculates this manually.
        try {
            Class<?> systemBarUtils = Class.forName("com.android.internal.policy.SystemBarUtils");
            Method getStatusBarHeight = systemBarUtils.getMethod("getStatusBarHeight", Context.class);
            Object height = getStatusBarHeight.invoke(null, context);
            if (height instanceof Integer) {
                return (Integer) height;
            }
        } catch (Throwable ignored) {
            // Non-system app / AVD: SystemBarUtils is not accessible
        }
        return statusBarInset;
    }

    @Override
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        // getPossibleMaximumWindowMetrics is a @SystemApi. On stock AVDs / non-platform-signed
        // installs it is not present on the runtime interface and throws NoSuchMethodError.
        try {
            WindowManager windowManager = displayInfoContext.getSystemService(WindowManager.class);
            Method method = WindowManager.class.getMethod(
                    "getPossibleMaximumWindowMetrics", int.class);
            @SuppressWarnings("unchecked")
            Set<WindowMetrics> possibleMaximumWindowMetrics =
                    (Set<WindowMetrics>) method.invoke(windowManager, DEFAULT_DISPLAY);
            if (possibleMaximumWindowMetrics != null && !possibleMaximumWindowMetrics.isEmpty()) {
                ArrayMap<CachedDisplayInfo, List<WindowBounds>> result = new ArrayMap<>();
                for (WindowMetrics windowMetrics : possibleMaximumWindowMetrics) {
                    CachedDisplayInfo info = getDisplayInfo(windowMetrics, Surface.ROTATION_0);
                    List<WindowBounds> bounds = estimateWindowBounds(displayInfoContext, info);
                    result.put(info, bounds);
                }
                return result;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Falling back to default display bounds estimation", t);
        }
        return super.estimateInternalDisplayBounds(displayInfoContext);
    }

    @Override
    protected DisplayCutout rotateCutout(DisplayCutout original, int startWidth, int startHeight,
            int fromRotation, int toRotation) {
        try {
            return original.getRotated(startWidth, startHeight, fromRotation, toRotation);
        } catch (Throwable t) {
            return original;
        }
    }
}
