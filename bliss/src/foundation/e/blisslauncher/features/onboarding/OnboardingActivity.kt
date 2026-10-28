/*
 * Copyright (c) 2024 Murena SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.blisslauncher.features.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.WindowCompat
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R

/**
 * First-run onboarding flow guiding the user through:
 * 1. Setting Bliss Launcher as default launcher
 * 2. Granting notification listener access for notification badges
 * 3. Quick tips for changing wallpaper and adding widgets
 *
 * Adapts dynamically if either step is already granted. Never blocks launcher access.
 */
class OnboardingActivity : Activity() {

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val REQUEST_CODE_ROLE = 1001
        @JvmField
        var sIsActive: Boolean = false
    }

    private enum class Step {
        DEFAULT_LAUNCHER,
        NOTIFICATION_ACCESS,
        CUSTOMIZE_TIPS
    }

    private var currentStep: Step = Step.DEFAULT_LAUNCHER
    private var isDefaultStepNeeded: Boolean = true
    private var isNotificationStepNeeded: Boolean = true
    private var initialTotalSteps: Int = 3

    private lateinit var heroImage: ImageView
    private lateinit var stepPill: TextView
    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var infoCard: View
    private lateinit var restrictedHintText: TextView
    private lateinit var tipsCard: View
    private lateinit var primaryButton: TextView
    private lateinit var secondaryButton: TextView

    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sIsActive = true
        registerBackDispatcher()
        setupSystemBars()
        setContentView(R.layout.activity_onboarding)

        initViews()
        evaluateSteps()
        initialTotalSteps = 3

        val targetStepName = intent.getStringExtra("target_step")
        if (targetStepName != null) {
            when (targetStepName) {
                "step1", "default" -> showStep(Step.DEFAULT_LAUNCHER)
                "step2", "notification" -> showStep(Step.NOTIFICATION_ACCESS)
                "step3", "tips", "customize" -> showStep(Step.CUSTOMIZE_TIPS)
            }
        } else if (!isDefaultStepNeeded && !isNotificationStepNeeded) {
            completeOnboarding()
            return
        } else if (isDefaultStepNeeded) {
            showStep(Step.DEFAULT_LAUNCHER)
        } else if (isNotificationStepNeeded) {
            showStep(Step.NOTIFICATION_ACCESS)
        } else {
            showStep(Step.CUSTOMIZE_TIPS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.hasExtra("target_step")) {
            return
        }
        evaluateSteps()
        // If user returns from settings or dialog, check if requirement was satisfied
        if (currentStep == Step.DEFAULT_LAUNCHER && isDefaultLauncherGranted()) {
            Log.d(TAG, "Default launcher role was granted.")
            showStep(Step.NOTIFICATION_ACCESS)
        } else if (currentStep == Step.NOTIFICATION_ACCESS && isNotificationAccessGranted()) {
            Log.d(TAG, "Notification listener access was granted.")
            showStep(Step.CUSTOMIZE_TIPS)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        evaluateSteps()
        val targetStepName = intent?.getStringExtra("target_step")
        if (targetStepName != null) {
            when (targetStepName) {
                "step1", "default" -> showStep(Step.DEFAULT_LAUNCHER)
                "step2", "notification" -> showStep(Step.NOTIFICATION_ACCESS)
                "step3", "tips", "customize" -> showStep(Step.CUSTOMIZE_TIPS)
            }
            return
        }
        if (currentStep == Step.DEFAULT_LAUNCHER && isDefaultLauncherGranted()) {
            showStep(Step.NOTIFICATION_ACCESS)
        }
    }

    private fun initViews() {
        heroImage = findViewById<ImageView>(R.id.onboarding_hero_image)!!
        stepPill = findViewById<TextView>(R.id.onboarding_step_pill)!!
        titleText = findViewById<TextView>(R.id.onboarding_title)!!
        bodyText = findViewById<TextView>(R.id.onboarding_body)!!
        infoCard = findViewById<View>(R.id.onboarding_info_card)!!
        restrictedHintText = findViewById<TextView>(R.id.onboarding_restricted_hint)!!
        tipsCard = findViewById<View>(R.id.onboarding_tips_card)!!
        primaryButton = findViewById<TextView>(R.id.onboarding_btn_primary)!!
        secondaryButton = findViewById<TextView>(R.id.onboarding_btn_secondary)!!

        primaryButton.setOnClickListener {
            when (currentStep) {
                Step.DEFAULT_LAUNCHER -> {
                    if (isDefaultLauncherGranted()) {
                        showStep(Step.NOTIFICATION_ACCESS)
                    } else {
                        requestDefaultLauncher()
                    }
                }
                Step.NOTIFICATION_ACCESS -> requestNotificationAccess()
                Step.CUSTOMIZE_TIPS -> completeOnboarding()
            }
        }

        secondaryButton.setOnClickListener {
            when (currentStep) {
                Step.DEFAULT_LAUNCHER -> {
                    showStep(Step.NOTIFICATION_ACCESS)
                }
                Step.NOTIFICATION_ACCESS -> {
                    showStep(Step.CUSTOMIZE_TIPS)
                }
                Step.CUSTOMIZE_TIPS -> {
                    completeOnboarding()
                }
            }
        }
    }

    private fun evaluateSteps() {
        isDefaultStepNeeded = !isDefaultLauncherGranted()
        isNotificationStepNeeded = !isNotificationAccessGranted()
    }

    private fun setupSystemBars() {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
    }

    private fun getStepPillText(step: Step): String {
        return when (step) {
            Step.DEFAULT_LAUNCHER -> getString(R.string.onboarding_step_1_of_3)
            Step.NOTIFICATION_ACCESS -> getString(R.string.onboarding_step_2_of_3)
            Step.CUSTOMIZE_TIPS -> getString(R.string.onboarding_step_3_of_3)
        }
    }

    private fun showStep(step: Step) {
        currentStep = step
        stepPill.text = getStepPillText(step)

        when (step) {
            Step.DEFAULT_LAUNCHER -> {
                heroImage.setImageResource(R.drawable.ic_onboarding_phone_mockup)
                titleText.setText(R.string.onboarding_default_title)
                bodyText.setText(R.string.onboarding_default_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                tipsCard.visibility = View.GONE
                if (isDefaultLauncherGranted()) {
                    primaryButton.setText(R.string.onboarding_continue_action)
                } else {
                    primaryButton.setText(R.string.onboarding_default_action)
                }
                secondaryButton.setText(R.string.onboarding_default_skip)
                secondaryButton.visibility = View.VISIBLE
            }
            Step.NOTIFICATION_ACCESS -> {
                heroImage.setImageResource(R.drawable.ic_onboarding_bell_badge)
                titleText.setText(R.string.onboarding_notification_title)
                bodyText.setText(R.string.onboarding_notification_body)
                infoCard.visibility = View.VISIBLE
                restrictedHintText.visibility = View.VISIBLE
                tipsCard.visibility = View.GONE
                primaryButton.setText(R.string.onboarding_notification_action)
                secondaryButton.setText(R.string.onboarding_notification_skip)
                secondaryButton.visibility = View.VISIBLE
            }
            Step.CUSTOMIZE_TIPS -> {
                heroImage.setImageResource(R.drawable.ic_onboarding_customize_hero)
                titleText.setText(R.string.onboarding_customize_title)
                bodyText.setText(R.string.onboarding_customize_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                tipsCard.visibility = View.VISIBLE
                primaryButton.setText(R.string.onboarding_customize_action)
                secondaryButton.setText(R.string.onboarding_default_skip)
                secondaryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun isDefaultLauncherGranted(): Boolean {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    /**
     * Checks if notification listener access is granted for this app.
     * Treats either NotificationListener or NotificationService as granted.
     */
    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        if (flat.isEmpty()) return false
        val pkg = packageName
        val cn1 = ComponentName(this, "com.android.launcher3.notification.NotificationListener").flattenToString()
        val cn2 = ComponentName(this, "foundation.e.blisslauncher.features.notification.NotificationService").flattenToString()
        return flat.contains(cn1) || flat.contains(cn2) || flat.contains(pkg)
    }

    private fun resolveNotificationComponent(): ComponentName {
        val cn1 = ComponentName(this, "com.android.launcher3.notification.NotificationListener")
        val cn2 = ComponentName(this, "foundation.e.blisslauncher.features.notification.NotificationService")
        return try {
            packageManager.getServiceInfo(cn2, 0)
            cn2
        } catch (e: Exception) {
            cn1
        }
    }

    private fun requestDefaultLauncher() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            try {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                startActivityForResult(intent, REQUEST_CODE_ROLE)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start createRequestRoleIntent", e)
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun requestNotificationAccess() {
        val component = resolveNotificationComponent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open detail settings", e)
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ROLE) {
            if (isDefaultLauncherGranted()) {
                if (isNotificationStepNeeded && !isNotificationAccessGranted()) {
                    showStep(Step.NOTIFICATION_ACCESS)
                } else {
                    showStep(Step.CUSTOMIZE_TIPS)
                }
            }
        }
    }

    private fun registerBackDispatcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback == null) {
            try {
                val callback = OnBackInvokedCallback {
                    handleBackNavigation()
                }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
                )
                backCallback = callback
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to register OnBackInvokedCallback", t)
            }
        }
    }

    private fun unregisterBackDispatcher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                backCallback?.let { callback ->
                    onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                    backCallback = null
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to unregister OnBackInvokedCallback", t)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (isFinishing || isDestroyed) return
        completeOnboarding()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBackDispatcher()
        sIsActive = false
    }

    private fun completeOnboarding() {
        LauncherPrefs.get(this).putSync(LauncherPrefs.FIRST_RUN_ONBOARDING_DONE.to(true))
        finish()
    }
}
