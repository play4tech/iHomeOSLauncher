/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.settings;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.notification.NotificationListener;

import foundation.e.bliss.multimode.MultiModeController;
import foundation.e.bliss.preferences.BlissPrefs;
import foundation.e.blisslauncher.features.notification.NotificationService;

/**
 * iOS-styled Customize / Settings screen for Bliss Launcher.
 */
public class SettingsActivity extends FragmentActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_ROLE = 1001;

    @VisibleForTesting
    static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";
    public static final String KEY_TRUST_APPS = "pref_trust_apps";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bliss_Onboarding);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_ios);

        setupSystemBars();
        initViews();
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
        insetsController.setAppearanceLightNavigationBars(!isDark);
    }

    private void initViews() {
        // Back navigation
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Initialize preferences and toggles
        initSwitches();
        initFooter();
    }

    private void updateDefaultLauncherState() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        boolean isDefault = roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME);

        TextView txtStatus = findViewById(R.id.txt_default_launcher_status);
        View layoutBadge = findViewById(R.id.layout_default_badge);
        View btnSetDefault = findViewById(R.id.btn_set_default);
        View card = findViewById(R.id.card_default_launcher);

        if (txtStatus == null || layoutBadge == null || btnSetDefault == null || card == null) {
            return;
        }

        if (isDefault) {
            txtStatus.setText("Bliss Launcher is set as default");
            layoutBadge.setVisibility(View.VISIBLE);
            btnSetDefault.setVisibility(View.GONE);
            card.setOnClickListener(null);
            card.setClickable(false);
        } else {
            txtStatus.setText("Tap to set Bliss as default launcher");
            layoutBadge.setVisibility(View.GONE);
            btnSetDefault.setVisibility(View.VISIBLE);
            card.setClickable(true);
            View.OnClickListener clickListener = v -> requestDefaultLauncherRole(roleManager);
            card.setOnClickListener(clickListener);
            btnSetDefault.setOnClickListener(clickListener);
        }
    }

    private void requestDefaultLauncherRole(RoleManager roleManager) {
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            try {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                startActivityForResult(intent, REQUEST_CODE_ROLE);
                return;
            } catch (Exception e) {
                Log.w(TAG, "RoleManager request failed, trying fallback", e);
            }
        }

        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            } catch (Exception e2) {
                Log.e(TAG, "Failed to launch home settings", e2);
            }
        }
    }

    private void updateNotificationAccessState() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        ComponentName myListener = new ComponentName(this, NotificationListener.class);
        ComponentName myListener2 = new ComponentName(this, NotificationService.class);
        boolean hasAccess = enabledListeners != null &&
                (enabledListeners.contains(myListener.flattenToString()) ||
                 enabledListeners.contains(myListener.flattenToShortString()) ||
                 enabledListeners.contains(myListener2.flattenToString()) ||
                 enabledListeners.contains(myListener2.flattenToShortString()));

        View rowBadges = findViewById(R.id.row_notification_badges);
        TextView txtSubtitle = findViewById(R.id.txt_badge_dots_subtitle);
        Switch switchBadges = findViewById(R.id.switch_badge_dots);
        View chevron = findViewById(R.id.chevron_badge_access);

        if (rowBadges == null || txtSubtitle == null || switchBadges == null || chevron == null) {
            return;
        }

        if (hasAccess) {
            txtSubtitle.setText("Show badges on app icons");
            txtSubtitle.setTextColor(getColor(R.color.onboarding_text_secondary));
            switchBadges.setVisibility(View.VISIBLE);
            chevron.setVisibility(View.GONE);
            rowBadges.setClickable(false);
            rowBadges.setOnClickListener(null);

            boolean badgingEnabled = Settings.Secure.getInt(
                    getContentResolver(), "notification_badging", 1) == 1;
            switchBadges.setOnCheckedChangeListener(null);
            switchBadges.setChecked(badgingEnabled);
            switchBadges.setOnCheckedChangeListener((btn, isChecked) -> {
                try {
                    Settings.Secure.putInt(getContentResolver(), "notification_badging", isChecked ? 1 : 0);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent("android.settings.NOTIFICATION_SETTINGS"));
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to launch notification settings", ex);
                    }
                }
            });
        } else {
            txtSubtitle.setText("Notification access required — tap to grant");
            txtSubtitle.setTextColor(getColor(R.color.ios_icon_orange));
            switchBadges.setVisibility(View.GONE);
            chevron.setVisibility(View.VISIBLE);
            rowBadges.setClickable(true);
            rowBadges.setOnClickListener(v -> openNotificationAccessSettings());
        }

        // Row 2: Show Notification Count
        Switch switchCount = findViewById(R.id.switch_notif_count);
        if (switchCount != null) {
            boolean showCount = MultiModeController.isNotifCountEnabled();
            switchCount.setOnCheckedChangeListener(null);
            switchCount.setChecked(showCount);
            switchCount.setOnCheckedChangeListener((btn, isChecked) -> {
                LauncherPrefs.getPrefs(this).edit().putBoolean(BlissPrefs.PREF_NOTIF_COUNT, isChecked).apply();
            });
        }
    }

    private void openNotificationAccessSettings() {
        ComponentName comp = new ComponentName(this, NotificationListener.class);
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
        intent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, comp.flattenToString());
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e2) {
                Log.e(TAG, "Failed to launch notification listener settings", e2);
            }
        }
    }

    private void initSwitches() {
        // Add to Home Screen
        Switch switchAddToHome = findViewById(R.id.switch_add_to_home);
        if (switchAddToHome != null) {
            boolean addToHome = LauncherPrefs.getPrefs(this)
                    .getBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, true);
            switchAddToHome.setChecked(addToHome);
            switchAddToHome.setOnCheckedChangeListener((btn, isChecked) -> {
                LauncherPrefs.getPrefs(this).edit()
                        .putBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, isChecked).apply();
            });
        }

        // Lock Layout
        Switch switchLockLayout = findViewById(R.id.switch_lock_layout);
        if (switchLockLayout != null) {
            boolean isLocked = LauncherPrefs.getPrefs(this)
                    .getBoolean(InvariantDeviceProfile.KEY_WORKSPACE_LOCK, false);
            switchLockLayout.setChecked(isLocked);
            switchLockLayout.setOnCheckedChangeListener((btn, isChecked) -> {
                LauncherPrefs.getPrefs(this).edit()
                        .putBoolean(InvariantDeviceProfile.KEY_WORKSPACE_LOCK, isChecked).apply();
            });
        }

        // Liquid Glass Dock
        Switch switchGlassDock = findViewById(R.id.switch_glass_dock);
        if (switchGlassDock != null) {
            boolean glassDock = LauncherPrefs.getPrefs(this)
                    .getBoolean(BlissPrefs.PREF_GLASS_DOCK_ENABLED, true);
            switchGlassDock.setChecked(glassDock);
            switchGlassDock.setOnCheckedChangeListener((btn, isChecked) -> {
                LauncherPrefs.getPrefs(this).edit()
                        .putBoolean(BlissPrefs.PREF_GLASS_DOCK_ENABLED, isChecked).apply();
            });
        }

        // Customize Glass Effect
        View rowCustomizeGlass = findViewById(R.id.row_customize_glass);
        if (rowCustomizeGlass != null) {
            rowCustomizeGlass.setOnClickListener(v -> {
                startActivity(new Intent(this, GlassCustomizeActivity.class));
            });
        }
    }

    private void initFooter() {
        TextView txtFooter = findViewById(R.id.txt_footer_version);
        if (txtFooter != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                txtFooter.setText("Bliss Launcher v" + versionName);
            } catch (Exception e) {
                txtFooter.setText("Bliss Launcher");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultLauncherState();
        updateNotificationAccessState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(
            PreferenceFragmentCompat caller, PreferenceScreen pref) {
        return false;
    }
}
