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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
import com.android.launcher3.ads.AdManager;
import com.android.launcher3.ads.ConsentManager;
import com.android.launcher3.billing.BillingManager;
import com.android.launcher3.notification.NotificationListener;
import com.google.android.gms.ads.AdView;

import foundation.e.bliss.glass.LiquidGlassToast;
import foundation.e.bliss.multimode.MultiModeController;
import foundation.e.bliss.preferences.BlissPrefs;

/**
 * iOS-styled Customize / Settings screen for Bliss Launcher.
 */
public class SettingsActivity extends FragmentActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_CODE_ROLE = 1001;
    private long mRoleRequestStartTime = 0;

    @VisibleForTesting
    static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";
    public static final String KEY_TRUST_APPS = "pref_trust_apps";

    private BillingManager mBillingManager;
    private AdView mBannerAdView;
    private final kotlin.jvm.functions.Function1<Boolean, kotlin.Unit> mProListener = isPro -> {
        updateProStateUi(isPro);
        return kotlin.Unit.INSTANCE;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bliss_Onboarding);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_settings_ios);

        setupSystemBars();
        LauncherPrefs.getPrefs(this).edit().remove(InvariantDeviceProfile.KEY_WORKSPACE_LOCK).apply();
        initViews();
        handleSubScreenIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSubScreenIntent(intent);
    }

    private void handleSubScreenIntent(Intent intent) {
        if (intent == null) return;
        String subScreen = intent.getStringExtra("open_sub_screen");
        if ("icon_size".equals(subScreen)) {
            startActivity(new Intent(this, IconSizeCustomizeActivity.class));
        } else if ("app_library_only".equals(subScreen)) {
            startActivity(new Intent(this, AppLibraryOnlyActivity.class));
        } else if ("glass".equals(subScreen)) {
            startActivity(new Intent(this, GlassCustomizeActivity.class));
        }
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
        initBillingSection();
        initSwitches();
        initIconSizeRow();
        initAppLibraryOnlyRow();
        initLegalRow();
        initFooter();
        initAds();

        View badgeGlassPro = findViewById(R.id.badge_customize_glass_pro);
        if (badgeGlassPro != null) {
            badgeGlassPro.setVisibility(BillingManager.isProUser(this) ? View.GONE : View.VISIBLE);
        }
    }

    private void initAds() {
        if (BillingManager.isProUser(this)) return;

        ConsentManager.gatherConsent(this, canRequestAds -> {
            if (canRequestAds && !isFinishing() && !isDestroyed()) {
                AdManager.initialize(this, () -> {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            ViewGroup container = findViewById(R.id.ad_banner_container);
                            if (container != null) {
                                mBannerAdView = AdManager.loadBanner(this, container);
                            }
                        }
                    });
                });
            }
        });
    }

    private void initBillingSection() {
        mBillingManager = BillingManager.getInstance(this);

        View rowUpgrade = findViewById(R.id.row_upgrade_pro);
        View btnBuy = findViewById(R.id.btn_buy_pro);
        View rowRestore = findViewById(R.id.row_restore_purchases);

        View.OnClickListener buyClickListener = v -> {
            Log.i("SettingsActivity", "Upgrade/Buy Pro clicked");
            Toast.makeText(this, "Connecting to Google Play...", Toast.LENGTH_SHORT).show();
            mBillingManager.launchPurchaseFlow(this, error -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                });
                return kotlin.Unit.INSTANCE;
            });
        };

        if (rowUpgrade != null) {
            rowUpgrade.setOnClickListener(buyClickListener);
        }
        if (btnBuy != null) {
            btnBuy.setOnClickListener(buyClickListener);
        }

        if (rowRestore != null) {
            rowRestore.setOnClickListener(v -> {
                Log.i("SettingsActivity", "Restore Purchases clicked");
                Toast.makeText(this, "Checking Google Play purchases...", Toast.LENGTH_SHORT).show();
                mBillingManager.restorePurchases((success, message) -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    });
                    return kotlin.Unit.INSTANCE;
                });
            });
        }

        mBillingManager.addProStatusListener(mProListener);
    }

    private void updateProStateUi(boolean isPro) {
        runOnUiThread(() -> {
            TextView txtSubtitle = findViewById(R.id.txt_pro_subtitle);
            TextView btnBuy = findViewById(R.id.btn_buy_pro);
            View badgeActive = findViewById(R.id.badge_pro_active);
            View rowUpgrade = findViewById(R.id.row_upgrade_pro);

            if (txtSubtitle == null || btnBuy == null || badgeActive == null || rowUpgrade == null) {
                return;
            }

            View badgeGlassPro = findViewById(R.id.badge_customize_glass_pro);
            if (badgeGlassPro != null) {
                badgeGlassPro.setVisibility(isPro ? View.GONE : View.VISIBLE);
            }

            if (isPro) {
                txtSubtitle.setText("Pro Active — Enjoy full ad-free experience");
                btnBuy.setVisibility(View.GONE);
                badgeActive.setVisibility(View.VISIBLE);
                rowUpgrade.setClickable(false);
                ViewGroup container = findViewById(R.id.ad_banner_container);
                if (container != null) {
                    AdManager.destroyBanner(container);
                }
                if (mBannerAdView != null) {
                    mBannerAdView.destroy();
                    mBannerAdView = null;
                }
            } else {
                String price = mBillingManager.getFormattedPrice();
                btnBuy.setText(price.isEmpty() ? "Upgrade" : price);
                btnBuy.setVisibility(View.VISIBLE);
                badgeActive.setVisibility(View.GONE);
                rowUpgrade.setClickable(true);
            }
        });
    }

    private void initLegalRow() {
        View rowLegal = findViewById(R.id.row_open_source_licenses);
        if (rowLegal != null) {
            rowLegal.setOnClickListener(v -> {
                startActivity(new Intent(this, OpenSourceLicensesActivity.class));
            });
        }
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
            txtStatus.setText("iHomeOS Launcher is set as default");
            layoutBadge.setVisibility(View.VISIBLE);
            btnSetDefault.setVisibility(View.GONE);
            card.setOnClickListener(null);
            card.setClickable(false);
        } else {
            txtStatus.setText("Tap to set iHomeOS Launcher as default");
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
                mRoleRequestStartTime = SystemClock.elapsedRealtime();
                startActivityForResult(intent, REQUEST_CODE_ROLE);
                return;
            } catch (Exception e) {
                Log.w(TAG, "RoleManager request failed, trying fallback", e);
            }
        }
        openHomeSettingsFallback();
    }

    private void openHomeSettingsFallback() {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ROLE) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            boolean isDefault = roleManager != null && roleManager.isRoleHeld(RoleManager.ROLE_HOME);
            if (!isDefault) {
                long elapsed = SystemClock.elapsedRealtime() - mRoleRequestStartTime;
                // Only open fallback if the role intent was silently dismissed almost instantaneously (< 250ms)
                // by OEMs like Xiaomi HyperOS or Vivo OriginOS. If the user was shown the dialog and explicitly
                // tapped Cancel, respect their cancellation and do not force open System Settings.
                if (mRoleRequestStartTime > 0 && elapsed < 250) {
                    openHomeSettingsFallback();
                }
            }
            mRoleRequestStartTime = 0;
            updateDefaultLauncherState();
        }
    }

    private void updateNotificationAccessState() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        ComponentName myListener = new ComponentName(this, NotificationListener.class);
        boolean hasAccess = enabledListeners != null &&
                (enabledListeners.contains(myListener.flattenToString()) ||
                 enabledListeners.contains(myListener.flattenToShortString()));

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
            switchBadges.setOnCheckedChangeListener((btn, isChecked) -> handleBadgingToggle(switchBadges, isChecked));
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

    private void handleBadgingToggle(Switch switchBadges, boolean isChecked) {
        try {
            Settings.Secure.putInt(getContentResolver(), "notification_badging", isChecked ? 1 : 0);
        } catch (Exception e) {
            // Revert switch visually because writing to Settings.Secure requires WRITE_SECURE_SETTINGS
            switchBadges.post(() -> {
                switchBadges.setOnCheckedChangeListener(null);
                switchBadges.setChecked(!isChecked);
                switchBadges.setOnCheckedChangeListener((btn, checked) -> handleBadgingToggle(switchBadges, checked));
            });
            try {
                Bundle extras = new Bundle();
                extras.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, "notification_badging");
                Intent intent = new Intent("android.settings.NOTIFICATION_SETTINGS")
                        .putExtra(":settings:show_fragment_args", extras);
                startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to launch notification settings", ex);
            }
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

        // Customize Glass Effect
        View rowCustomizeGlass = findViewById(R.id.row_customize_glass);
        if (rowCustomizeGlass != null) {
            rowCustomizeGlass.setOnClickListener(v -> {
                startActivity(new Intent(this, GlassCustomizeActivity.class));
            });
        }
    }

    private void initIconSizeRow() {
        View rowIconSize = findViewById(R.id.row_customize_icon_size);
        if (rowIconSize != null) {
            rowIconSize.setOnClickListener(v -> {
                startActivity(new Intent(this, IconSizeCustomizeActivity.class));
            });
        }
        updateIconSizeSummary();
    }

    private void initAppLibraryOnlyRow() {
        View rowAppLibraryOnly = findViewById(R.id.row_app_library_only);
        if (rowAppLibraryOnly != null) {
            rowAppLibraryOnly.setOnClickListener(v -> {
                startActivity(new Intent(this, AppLibraryOnlyActivity.class));
            });
        }
        updateAppLibraryOnlySummary();
    }

    private void updateAppLibraryOnlySummary() {
        TextView txtSummary = findViewById(R.id.txt_app_library_only_count);
        if (txtSummary != null) {
            int count = com.android.launcher3.util.RemovedAppStore.getRemovedCount(this);
            if (count > 0) {
                txtSummary.setText(count + (count == 1 ? " app" : " apps"));
            } else {
                txtSummary.setText("");
            }
        }
    }

    private void updateIconSizeSummary() {
        TextView txtSummary = findViewById(R.id.txt_icon_size_summary);
        if (txtSummary != null) {
            float scale = 1.0f;
            try {
                scale = LauncherPrefs.get(this).get(LauncherPrefs.ICON_SIZE_SCALE);
            } catch (Exception ignored) {
            }
            int percent = Math.round(scale * 100f);
            txtSummary.setText(percent + "%");
        }
    }

    private void initFooter() {
        TextView txtFooter = findViewById(R.id.txt_footer_version);
        if (txtFooter != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                txtFooter.setText("iHomeOS Launcher v" + versionName);
            } catch (Exception e) {
                txtFooter.setText("iHomeOS Launcher v1.0");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBannerAdView != null) {
            mBannerAdView.resume();
        }
        updateDefaultLauncherState();
        updateNotificationAccessState();
        updateIconSizeSummary();
        updateAppLibraryOnlySummary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBannerAdView != null) {
            mBannerAdView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (mBannerAdView != null) {
            mBannerAdView.destroy();
            mBannerAdView = null;
        }
        super.onDestroy();
        if (mBillingManager != null) {
            mBillingManager.removeProStatusListener(mProListener);
        }
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
