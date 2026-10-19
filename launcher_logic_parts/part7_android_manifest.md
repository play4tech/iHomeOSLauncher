# Part 7: Android Manifests & Permissions (Common, Bliss, Quickstep Variants)

Modular code logic bundle designed to stay within Gemini attachment size limits (6 files).

## Table of Contents

- [AndroidManifest-common.xml](#AndroidManifestcommonxml)
- [AndroidManifest.xml](#AndroidManifestxml)
- [bliss/AndroidManifest.xml](#blissAndroidManifestxml)
- [bliss/AndroidManifest-withoutQuickstep.xml](#blissAndroidManifestwithoutQuickstepxml)
- [quickstep/AndroidManifest.xml](#quickstepAndroidManifestxml)
- [quickstep/AndroidManifest-launcher.xml](#quickstepAndroidManifestlauncherxml)

---

# AndroidManifest-common.xml

**Absolute Path**: `D:\Code\OS_Launcher\AndroidManifest-common.xml`  
**Lines**: 209 | **Bytes**: 9429

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
**
** Copyright 2016, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
-->
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.android.launcher3">

    <!--
    The manifest defines the common entries that should be present in any derivative of Launcher3.
    The components should generally not require any changes.

    Rest of the components are defined in AndroidManifest.xml which is merged with this manifest
    at compile time. Note that the components defined in AndroidManifest.xml are also required,
    with some minor changed based on the derivative app.
    -->

    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.SET_WALLPAPER" />
    <uses-permission android:name="android.permission.SET_WALLPAPER_HINTS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />

    <!--
    Permissions required for read/write access to the workspace data. These permission name
    should not conflict with that defined in other apps, as such an app should embed its package
    name in the permissions. eq com.mypackage.permission.READ_SETTINGS
    -->
    <permission
        android:name="${applicationId}.permission.READ_SETTINGS"
        android:protectionLevel="signatureOrSystem"
        android:label="@string/permlab_read_settings"
        android:description="@string/permdesc_read_settings"/>
    <permission
        android:name="${applicationId}.permission.WRITE_SETTINGS"
        android:protectionLevel="signatureOrSystem"
        android:label="@string/permlab_write_settings"
        android:description="@string/permdesc_write_settings"/>

    <uses-permission android:name="${applicationId}.permission.READ_SETTINGS" />
    <uses-permission android:name="${applicationId}.permission.WRITE_SETTINGS" />

    <application
        android:backupAgent="com.android.launcher3.LauncherBackupAgent"
        android:name="com.android.launcher3.LauncherApplication"
        android:fullBackupOnly="true"
        android:backupInForeground="true"
        android:fullBackupContent="@xml/backupscheme"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        tools:replace="android:label"
        android:label="@string/derived_app_name"
        android:usesCleartextTraffic="true"
        android:allowClearUserData="true"
        android:largeHeap="@bool/config_largeHeap"
        android:restoreAnyVersion="true"
        android:supportsRtl="true"
        android:enableOnBackInvokedCallback="true" >

        <!-- Intent received when a session is committed -->
        <receiver
            android:name="com.android.launcher3.SessionCommitReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.content.pm.action.SESSION_COMMITTED" />
            </intent-filter>
        </receiver>

        <!-- Intent received used to initialize a restored widget -->
        <receiver android:name="com.android.launcher3.AppWidgetsRestoredReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_HOST_RESTORED"/>
            </intent-filter>
        </receiver>

        <service
            android:name="com.android.launcher3.notification.NotificationListener"
            android:label="@string/notification_dots_service_title"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <meta-data android:name="android.nfc.disable_beam_default"
                       android:value="true" />

        <activity android:name="com.android.launcher3.dragndrop.AddItemActivity"
            android:theme="@style/AddItemActivityTheme"
            android:excludeFromRecents="true"
            android:autoRemoveFromRecents="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.content.pm.action.CONFIRM_PIN_SHORTCUT" />
                <action android:name="android.content.pm.action.CONFIRM_PIN_APPWIDGET" />
            </intent-filter>
        </activity>

        <!--
        The settings provider contains Home's data, like the workspace favorites. The permissions
        should be changed to what is defined above. The authorities should also be changed to
        represent the package name.
        -->
        <provider
            android:name="com.android.launcher3.LauncherProvider"
            android:authorities="${applicationId}.settings"
            android:exported="true"
            android:writePermission="${applicationId}.permission.WRITE_SETTINGS"
            android:readPermission="${applicationId}.permission.READ_SETTINGS" />

        <!--
        The content provider for exposing various launcher grid options.
        -->
        <provider
            android:name="com.android.launcher3.graphics.GridCustomizationsProvider"
            android:authorities="${applicationId}.grid_control"
            android:exported="true"
            android:writePermission="${applicationId}.permission.WRITE_SETTINGS"
            android:readPermission="${applicationId}.permission.READ_SETTINGS" />

        <!--
        The settings activity. To extend point settings_fragment_name to appropriate fragment class
        -->
        <activity
            android:name="com.android.launcher3.settings.SettingsActivity"
            android:label="@string/settings_button_text"
            android:theme="@style/HomeSettings.Theme"
            android:exported="true"
            android:autoRemoveFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.APPLICATION_PREFERENCES" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.android.launcher3.settings.GlassCustomizeActivity"
            android:label="Customize Glass Effect"
            android:theme="@style/Theme.Bliss.Onboarding"
            android:exported="false"
            android:autoRemoveFromRecents="true" />

        <provider
            android:name="com.android.launcher3.testing.TestInformationProvider"
            android:authorities="${applicationId}.TestInfo"
            android:readPermission="android.permission.WRITE_SECURE_SETTINGS"
            android:writePermission="android.permission.WRITE_SECURE_SETTINGS"
            android:exported="true"
            android:enabled="false" />

        <!--
        Launcher activity for secondary display
        -->
        <activity
            android:name="com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher"
            android:theme="@style/AppTheme"
            android:launchMode="singleTop"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.SECONDARY_HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- [b/197780098] Disable eager initialization of Jetpack libraries. -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            tools:node="remove" />

        <property
            android:name="android.window.PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED"
            android:value="true" />

        <activity
            android:name="com.android.launcher3.lineage.trust.TrustAppsActivity"
            android:label="@string/trust_apps_manager_name"
            android:theme="@android:style/Theme.DeviceDefault.Settings"
            android:autoRemoveFromRecents="true" />
    </application>
</manifest>
```

---

# AndroidManifest.xml

**Absolute Path**: `D:\Code\OS_Launcher\AndroidManifest.xml`  
**Lines**: 78 | **Bytes**: 3308

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
-->
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.android.launcher3">
    <uses-sdk android:targetSdkVersion="36" android:minSdkVersion="33"/>
    <!--
    Manifest entries specific to Launcher3. This is merged with AndroidManifest-common.xml.
    Refer comments around specific entries on how to extend individual components.
    -->

    <application
        android:backupAgent="com.android.launcher3.LauncherBackupAgent"
        android:fullBackupOnly="true"
        android:fullBackupContent="@xml/backupscheme"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/derived_app_name"
        android:theme="@style/AppTheme"
        android:largeHeap="@bool/config_largeHeap"
        android:restoreAnyVersion="true"
        android:supportsRtl="true"
        android:enableOnBackInvokedCallback="true"
        tools:ignore="GoogleAppIndexingWarning"
        tools:replace="android:icon">

        <!--
        Main launcher activity. When extending only change the name, and keep all the
        attributes and intent filters the same
        -->
        <activity
            android:name="com.android.launcher3.Launcher"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:windowSoftInputMode="adjustPan"
            android:screenOrientation="unspecified"
            android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
            android:resizeableActivity="true"
            android:resumeWhilePausing="true"
            android:taskAffinity=""
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <action android:name="android.intent.action.SHOW_WORK_APPS" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.MONKEY"/>
                <category android:name="android.intent.category.LAUNCHER_APP" />
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <meta-data
                android:name="com.android.launcher3.grid.control"
                android:value="${applicationId}.grid_control" />
        </activity>

    </application>
</manifest>
```

---

# bliss/AndroidManifest.xml

**Absolute Path**: `D:\Code\OS_Launcher\bliss/AndroidManifest.xml`  
**Lines**: 59 | **Bytes**: 2263

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="foundation.e.pwaplayer.provider.READ_WRITE" />

    <application
        android:icon="@mipmap/ic_launcher"
        android:enableOnBackInvokedCallback="true">

        <activity
            android:name="foundation.e.bliss.widgets.WidgetsActivity"
            android:autoRemoveFromRecents="true"
            android:clearTaskOnLaunch="true"
            android:excludeFromRecents="true"
            android:exported="false"
            android:label="@string/remove_widgets"
            android:theme="@style/HomeSettings.Theme" />

        <activity
            android:name="foundation.e.blisslauncher.features.onboarding.OnboardingActivity"
            android:autoRemoveFromRecents="true"
            android:excludeFromRecents="true"
            android:exported="false"
            android:taskAffinity=""
            android:theme="@style/Theme.Bliss.Onboarding" />

        <!-- Dummy receiver for the migration of weather app -->
        <receiver
            android:name="foundation.e.blisslauncher.features.weather.WeatherAppWidgetProvider"
            android:exported="false"
            android:label="@string/app_name"
            tools:ignore="Instantiatable">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/weather_appwidget_info" />
        </receiver>

        <!-- iOS 2x2 Analog Clock Widget -->
        <receiver
            android:name="foundation.e.bliss.widgets.clock.ClockAppWidgetProvider"
            android:exported="true"
            android:label="@string/clock_widget_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/clock_appwidget_info" />
        </receiver>


    </application>

</manifest>
```

---

# bliss/AndroidManifest-withoutQuickstep.xml

**Absolute Path**: `D:\Code\OS_Launcher\bliss/AndroidManifest-withoutQuickstep.xml`  
**Lines**: 74 | **Bytes**: 3185

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
-->
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.android.launcher3">
    <uses-sdk android:targetSdkVersion="36" android:minSdkVersion="33"/>

    <application
        android:backupAgent="com.android.launcher3.LauncherBackupAgent"
        android:fullBackupOnly="true"
        android:fullBackupContent="@xml/backupscheme"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/derived_app_name"
        android:theme="@style/AppTheme"
        android:largeHeap="@bool/config_largeHeap"
        android:restoreAnyVersion="true"
        android:supportsRtl="true"
        android:enableOnBackInvokedCallback="true"
        tools:ignore="GoogleAppIndexingWarning"
        tools:replace="android:icon,android:roundIcon">

        <!--
        Main launcher activity. When extending only change the name, and keep all the
        attributes and intent filters the same
        -->
        <activity
            android:name="com.android.launcher3.Launcher"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:windowSoftInputMode="adjustPan"
            android:screenOrientation="unspecified"
            android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
            android:resizeableActivity="true"
            android:resumeWhilePausing="true"
            android:taskAffinity=""
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <action android:name="android.intent.action.SHOW_WORK_APPS" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.MONKEY"/>
                <category android:name="android.intent.category.LAUNCHER_APP" />
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <meta-data
                android:name="com.android.launcher3.grid.control"
                android:value="${applicationId}.grid_control" />
        </activity>

    </application>
</manifest>
```

---

# quickstep/AndroidManifest.xml

**Absolute Path**: `D:\Code\OS_Launcher\quickstep/AndroidManifest.xml`  
**Lines**: 165 | **Bytes**: 7841

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
**
** Copyright 2017, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
-->

<manifest xmlns:android="http://schemas.android.com/apk/res/android"
     xmlns:tools="http://schemas.android.com/tools"
     package="com.android.launcher3">

    <uses-permission android:name="android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS" />
    <uses-permission android:name="android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS"/>
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
    <uses-permission android:name="android.permission.BIND_APPWIDGET" />
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
    <uses-permission android:name="android.permission.START_TASKS_FROM_RECENTS"/>
    <uses-permission android:name="android.permission.REMOVE_TASKS"/>
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>
    <uses-permission android:name="android.permission.MANAGE_ACTIVITY_TASKS"/>
    <uses-permission android:name="android.permission.INTERNAL_SYSTEM_WINDOW"/>
    <uses-permission android:name="android.permission.STATUS_BAR"/>
    <uses-permission android:name="android.permission.STATUS_BAR_SERVICE"/>
    <uses-permission android:name="android.permission.STOP_APP_SWITCHES"/>
    <uses-permission android:name="android.permission.SET_ORIENTATION"/>
    <uses-permission android:name="android.permission.READ_FRAME_BUFFER"/>
    <uses-permission android:name="android.permission.MANAGE_ACCESSIBILITY"/>
    <uses-permission android:name="android.permission.MONITOR_INPUT"/>
    <uses-permission android:name="android.permission.ALLOW_SLIPPERY_TOUCHES"/>
    <uses-permission android:name="android.permission.ACCESS_SHORTCUTS"/>

    <uses-permission android:name="android.permission.SYSTEM_APPLICATION_OVERLAY" />

    <!--
    Permission required to access profiles which are otherwise hidden
    from being visible via APIs, e.g. private profile.
    -->
    <uses-permission android:name="android.permission.ACCESS_HIDDEN_PROFILES_FULL" />

    <!-- Permission required to start a WidgetPickerActivity. -->
    <permission android:name="${applicationId}.permission.START_WIDGET_PICKER_ACTIVITY"
        android:protectionLevel="signature|privileged" />

    <application android:backupAgent="com.android.launcher3.LauncherBackupAgent"
         android:fullBackupOnly="true"
         android:fullBackupContent="@xml/backupscheme"
         android:hardwareAccelerated="true"
         android:icon="@mipmap/ic_launcher"
         android:label="@string/derived_app_name"
         android:theme="@style/AppTheme"
         android:largeHeap="@bool/config_largeHeap"
         android:restoreAnyVersion="true"
         android:supportsRtl="true">

        <service android:name="com.android.quickstep.TouchInteractionService"
             android:permission="android.permission.STATUS_BAR_SERVICE"
             android:directBootAware="true"
             android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.QUICKSTEP_SERVICE"/>
            </intent-filter>
        </service>

        <activity android:name="com.android.quickstep.RecentsActivity"
             android:excludeFromRecents="true"
             android:launchMode="singleTask"
             android:clearTaskOnLaunch="true"
             android:stateNotNeeded="true"
             android:theme="@style/LauncherTheme"
             android:screenOrientation="behind"
             android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
             android:resizeableActivity="true"
             android:resumeWhilePausing="true"
             android:taskAffinity=""/>

        <!-- Content provider to settings search. The autority should be same as the packageName -->
        <provider android:name="com.android.quickstep.LauncherSearchIndexablesProvider"
             android:authorities="${applicationId}"
             android:grantUriPermissions="true"
             android:multiprocess="true"
             android:permission="android.permission.READ_SEARCH_INDEXABLES"
             android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.SEARCH_INDEXABLES_PROVIDER"/>
            </intent-filter>
        </provider>

        <!-- FileProvider used for sharing images. -->
        <provider android:name="androidx.core.content.FileProvider"
             android:authorities="${applicationId}.overview.fileprovider"
             android:exported="false"
             android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                 android:resource="@xml/overview_file_provider_paths"/>
        </provider>

        <activity android:name="com.android.launcher3.proxy.ProxyActivityStarter"
            android:theme="@style/ProxyActivityStarterTheme"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:exported="false"
            />

        <activity android:name="com.android.quickstep.interaction.GestureSandboxActivity"
            android:autoRemoveFromRecents="true"
            android:excludeFromRecents="true"
            android:theme="@style/GestureTutorialActivity"
            android:exported="true"
            android:configChanges="orientation">
            <intent-filter>
                <action android:name="com.android.quickstep.action.GESTURE_SANDBOX"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <!--
        Activity following gesture nav onboarding.
        It's protected by android.permission.REBOOT to ensure that only system apps can start it
        (setup wizard already has this permission)
        -->
        <activity android:name="com.android.quickstep.interaction.AllSetActivity"
            android:autoRemoveFromRecents="true"
            android:excludeFromRecents="true"
            android:permission="android.permission.REBOOT"
            android:theme="@style/AllSetTheme"
            android:label="@string/allset_title"
            android:exported="true">
            <intent-filter>
                <action android:name="com.android.quickstep.action.GESTURE_ONBOARDING_ALL_SET"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <activity android:name="com.android.launcher3.WidgetPickerActivity"
            android:theme="@style/WidgetPickerActivityTheme"
            android:excludeFromRecents="true"
            android:autoRemoveFromRecents="true"
            android:showOnLockScreen="true"
            android:launchMode="singleTop"
            android:exported="true"
            android:permission="android.permission.START_WIDGET_PICKER_ACTIVITY">
            <intent-filter>
                <action android:name="android.intent.action.PICK" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

# quickstep/AndroidManifest-launcher.xml

**Absolute Path**: `D:\Code\OS_Launcher\quickstep/AndroidManifest-launcher.xml`  
**Lines**: 73 | **Bytes**: 3071

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
/*
**
** Copyright 2019, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
-->
<manifest
    xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.android.launcher3">
    <!--
    Manifest entries specific to Launcher3. This is merged with AndroidManifest-common.xml.
    Refer comments around specific entries on how to extend individual components.
    -->

    <application
        android:backupAgent="com.android.launcher3.LauncherBackupAgent"
        android:fullBackupOnly="true"
        android:fullBackupContent="@xml/backupscheme"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/derived_app_name"
        android:theme="@style/AppTheme"
        android:largeHeap="@bool/config_largeHeap"
        android:restoreAnyVersion="true"
        android:supportsRtl="true" >

        <!--
        Main launcher activity. When extending only change the name, and keep all the
        attributes and intent filters the same
        -->
        <activity
            android:name="com.android.launcher3.uioverrides.QuickstepLauncher"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:windowSoftInputMode="adjustPan"
            android:screenOrientation="unspecified"
            android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
            android:resizeableActivity="true"
            android:resumeWhilePausing="true"
            android:taskAffinity=""
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <action android:name="android.intent.action.SHOW_WORK_APPS" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.MONKEY"/>
                <category android:name="android.intent.category.LAUNCHER_APP" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data
                android:name="com.android.launcher3.grid.control"
                android:value="${applicationId}.grid_control" />
        </activity>

    </application>
</manifest>
```

---

