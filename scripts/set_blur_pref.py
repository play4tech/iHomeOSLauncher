import subprocess

def set_blur(val):
    pull_cmd = "adb shell run-as com.ihome.launcher cat shared_prefs/com.android.launcher3.prefs.xml"
    content = subprocess.run(pull_cmd, shell=True, capture_output=True, text=True).stdout
    if 'name="pref_glass_blur_amount"' in content:
        import re
        content = re.sub(r'<float name="pref_glass_blur_amount" value="[^"]*" />', f'<float name="pref_glass_blur_amount" value="{val}" />', content)
        with open("scratch_prefs.xml", "w") as f:
            f.write(content)
        subprocess.run("adb push scratch_prefs.xml /data/local/tmp/prefs.xml", shell=True)
        subprocess.run("adb shell run-as com.ihome.launcher cp /data/local/tmp/prefs.xml shared_prefs/com.android.launcher3.prefs.xml", shell=True)
        subprocess.run("adb shell am force-stop com.ihome.launcher", shell=True)
        subprocess.run("adb shell am start -n com.ihome.launcher/com.android.launcher3.Launcher", shell=True)
        print(f"Set pref_glass_blur_amount to {val} and restarted launcher.")

if __name__ == "__main__":
    import sys
    val = sys.argv[1] if len(sys.argv) > 1 else "0.14"
    set_blur(val)
