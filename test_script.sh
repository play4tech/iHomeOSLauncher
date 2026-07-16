sed -i '' 's/val isSupported: Boolean/val isSupported: Boolean = false \/\/ TEMP/g' bliss/src/foundation/e/bliss/glass/GlassViewDelegate.kt
./gradlew installBlissWithQuickstepDebug
/Users/sandhiya/Library/Android/sdk/platform-tools/adb shell input keyevent KEYCODE_HOME
sleep 2
/Users/sandhiya/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /Users/sandhiya/.gemini/antigravity/brain/21c948a5-e5e8-4a3e-84f1-00da270fd7ed/legacy_blur_screenshot.png
git checkout bliss/src/foundation/e/bliss/glass/GlassViewDelegate.kt
