#!/bin/bash
SERIAL="693a9b29"
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s $SERIAL"

# 1. Wake & dismiss any shade / lock screen
$ADB shell "input keyevent 224; sleep 0.1; input keyevent 4; sleep 0.1; input swipe 540 1800 540 600 150; sleep 0.2; am start -n com.ihome.launcher/com.android.launcher3.Launcher; sleep 0.3"

# 2. Run action
if [ -n "$1" ]; then
    $ADB shell "$1"
fi

# 3. Screencap
if [ -n "$2" ]; then
    sleep 0.3
    $ADB exec-out screencap -p > "$2"
fi
