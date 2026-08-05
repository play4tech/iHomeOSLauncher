#!/bin/bash
adb shell 'dumpsys activity top | grep -A 2 -B 2 "DoubleShadowBubbleTextView{6091d9e"'
