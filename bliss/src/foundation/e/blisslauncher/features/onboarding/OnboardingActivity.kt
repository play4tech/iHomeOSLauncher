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
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.WindowCompat
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.billing.BillingManager
import com.android.launcher3.util.Executors
import foundation.e.bliss.wallpaper.WallpaperRepository

/**
 * First-run onboarding flow guiding the user through:
 * 1. Setting Bliss Launcher as default launcher
 * 2. Granting notification listener access for notification badges
 * 3. Choosing a home screen wallpaper from 4 curated options
 * 4. Quick tips for changing wallpaper and adding widgets
 * 5. Option to upgrade to Pro (Ad-free, full glass customization, lifetime)
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
        WALLPAPER_SELECTION,
        CUSTOMIZE_TIPS,
        BUY_PRO
    }

    private var currentStep: Step = Step.DEFAULT_LAUNCHER
    private var isDefaultStepNeeded: Boolean = true
    private var isNotificationStepNeeded: Boolean = true

    private lateinit var heroImage: ImageView
    private lateinit var stepPill: TextView
    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var infoCard: View
    private lateinit var restrictedHintText: TextView
    private lateinit var wallpaperContainer: View
    private lateinit var wallpaperCards: Array<View>
    private lateinit var wallpaperBorders: Array<View>
    private lateinit var wallpaperBadges: Array<View>
    private var selectedWallpaperIndex: Int = 0

    private val wallpaperResIds = intArrayOf(
        R.drawable.wallpaper_onboarding_1,
        R.drawable.wallpaper_onboarding_2,
        R.drawable.wallpaper_onboarding_3,
        R.drawable.wallpaper_onboarding_4
    )

    private lateinit var tipsCard: View
    private lateinit var proCard: View
    private lateinit var primaryButton: TextView
    private lateinit var secondaryButton: TextView
    private lateinit var restoreButton: TextView

    private var billingManager: BillingManager? = null
    private val proStatusListener: (Boolean) -> Unit = { isPro ->
        if (isPro && currentStep == Step.BUY_PRO) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, getString(R.string.onboarding_pro_restored), Toast.LENGTH_SHORT).show()
                    completeOnboarding()
                }
            }
        }
    }

    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sIsActive = true
        registerBackDispatcher()
        setupSystemBars()
        setContentView(R.layout.activity_onboarding)

        billingManager = BillingManager.getInstance(this)
        billingManager?.addProStatusListener(proStatusListener)

        initViews()
        evaluateSteps()

        val targetStepName = intent.getStringExtra("target_step")
        val savedStepName = savedInstanceState?.getString("current_step")
        if (targetStepName != null) {
            when (targetStepName) {
                "step1", "default" -> showStep(Step.DEFAULT_LAUNCHER)
                "step2", "notification" -> showStep(Step.NOTIFICATION_ACCESS)
                "step3", "wallpaper" -> showStep(Step.WALLPAPER_SELECTION)
                "step4", "tips", "customize" -> showStep(Step.CUSTOMIZE_TIPS)
                "step5", "pro", "paywall" -> showStep(Step.BUY_PRO)
                else -> showStep(Step.DEFAULT_LAUNCHER)
            }
        } else if (savedStepName != null) {
            try {
                showStep(Step.valueOf(savedStepName))
            } catch (e: Exception) {
                showStep(Step.WALLPAPER_SELECTION)
            }
        } else if (isDefaultStepNeeded) {
            showStep(Step.DEFAULT_LAUNCHER)
        } else if (isNotificationStepNeeded) {
            showStep(Step.NOTIFICATION_ACCESS)
        } else {
            showStep(Step.WALLPAPER_SELECTION)
        }

        val savedWallpaperIndex = savedInstanceState?.getInt("selected_wallpaper_index", -1) ?: -1
        if (savedWallpaperIndex in wallpaperCards.indices) {
            selectWallpaper(savedWallpaperIndex)
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.hasExtra("target_step")) {
            return
        }
        evaluateSteps()
        if (currentStep == Step.DEFAULT_LAUNCHER && isDefaultLauncherGranted()) {
            Log.d(TAG, "Default launcher role was granted.")
            showStep(Step.NOTIFICATION_ACCESS)
        } else if (currentStep == Step.NOTIFICATION_ACCESS && isNotificationAccessGranted()) {
            Log.d(TAG, "Notification listener access was granted.")
            showStep(Step.WALLPAPER_SELECTION)
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
                "step3", "wallpaper" -> showStep(Step.WALLPAPER_SELECTION)
                "step4", "tips", "customize" -> showStep(Step.CUSTOMIZE_TIPS)
                "step5", "pro", "paywall" -> showStep(Step.BUY_PRO)
                else -> showStep(Step.DEFAULT_LAUNCHER)
            }
            return
        }
        if (currentStep == Step.DEFAULT_LAUNCHER && isDefaultLauncherGranted()) {
            showStep(Step.NOTIFICATION_ACCESS)
        }
    }

    private fun initViews() {
        heroImage = findViewById(R.id.onboarding_hero_image)!!
        stepPill = findViewById(R.id.onboarding_step_pill)!!
        titleText = findViewById(R.id.onboarding_title)!!
        bodyText = findViewById(R.id.onboarding_body)!!
        infoCard = findViewById(R.id.onboarding_info_card)!!
        restrictedHintText = findViewById(R.id.onboarding_restricted_hint)!!
        wallpaperContainer = findViewById(R.id.onboarding_wallpaper_container)!!
        tipsCard = findViewById(R.id.onboarding_tips_card)!!
        proCard = findViewById(R.id.onboarding_pro_card)!!
        primaryButton = findViewById(R.id.onboarding_btn_primary)!!
        secondaryButton = findViewById(R.id.onboarding_btn_secondary)!!
        restoreButton = findViewById(R.id.onboarding_btn_restore)!!

        wallpaperCards = arrayOf(
            findViewById(R.id.onboarding_wallpaper_card_1)!!,
            findViewById(R.id.onboarding_wallpaper_card_2)!!,
            findViewById(R.id.onboarding_wallpaper_card_3)!!,
            findViewById(R.id.onboarding_wallpaper_card_4)!!
        )
        wallpaperBorders = arrayOf(
            findViewById(R.id.onboarding_wallpaper_border_1)!!,
            findViewById(R.id.onboarding_wallpaper_border_2)!!,
            findViewById(R.id.onboarding_wallpaper_border_3)!!,
            findViewById(R.id.onboarding_wallpaper_border_4)!!
        )
        wallpaperBadges = arrayOf(
            findViewById(R.id.onboarding_wallpaper_badge_1)!!,
            findViewById(R.id.onboarding_wallpaper_badge_2)!!,
            findViewById(R.id.onboarding_wallpaper_badge_3)!!,
            findViewById(R.id.onboarding_wallpaper_badge_4)!!
        )

        for (i in wallpaperCards.indices) {
            wallpaperCards[i].setOnClickListener {
                selectWallpaper(i)
            }
        }
        selectWallpaper(0)

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
                Step.WALLPAPER_SELECTION -> applySelectedWallpaperAndAdvance()
                Step.CUSTOMIZE_TIPS -> {
                    if (BillingManager.isProUser(this)) {
                        completeOnboarding()
                    } else {
                        showStep(Step.BUY_PRO)
                    }
                }
                Step.BUY_PRO -> launchProPurchase()
            }
        }

        secondaryButton.setOnClickListener {
            when (currentStep) {
                Step.DEFAULT_LAUNCHER -> {
                    showStep(Step.NOTIFICATION_ACCESS)
                }
                Step.NOTIFICATION_ACCESS -> {
                    showStep(Step.WALLPAPER_SELECTION)
                }
                Step.WALLPAPER_SELECTION -> {
                    showStep(Step.CUSTOMIZE_TIPS)
                }
                Step.CUSTOMIZE_TIPS -> {
                    if (BillingManager.isProUser(this)) {
                        completeOnboarding()
                    } else {
                        showStep(Step.BUY_PRO)
                    }
                }
                Step.BUY_PRO -> {
                    completeOnboarding()
                }
            }
        }

        restoreButton.setOnClickListener {
            val bm = billingManager ?: return@setOnClickListener
            Toast.makeText(this, getString(R.string.onboarding_pro_restoring), Toast.LENGTH_SHORT).show()
            bm.restorePurchases { success, message ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, getString(R.string.onboarding_pro_restored), Toast.LENGTH_SHORT).show()
                        completeOnboarding()
                    } else {
                        val msg = if (message.isNullOrBlank()) getString(R.string.onboarding_pro_restore_none) else message
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun selectWallpaper(index: Int) {
        selectedWallpaperIndex = index
        for (i in wallpaperCards.indices) {
            val isSelected = (i == index)
            wallpaperBorders[i].visibility = if (isSelected) View.VISIBLE else View.GONE
            wallpaperBadges[i].visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    private fun applySelectedWallpaperAndAdvance() {
        val resId = wallpaperResIds.getOrElse(selectedWallpaperIndex) { wallpaperResIds[0] }
        showStep(Step.CUSTOMIZE_TIPS)

        Executors.THREAD_POOL_EXECUTOR.execute {
            try {
                val bitmap = BitmapFactory.decodeResource(resources, resId)
                if (bitmap != null) {
                    val wm = WallpaperManager.getInstance(this@OnboardingActivity)
                    wm.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    )
                    val newId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                    } else {
                        -1
                    }
                    val repo = WallpaperRepository.getInstance(this@OnboardingActivity)
                    repo.saveWallpaperToCache(bitmap, newId)
                    repo.updateDirectly(bitmap)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to apply onboarding wallpaper", t)
            }
        }
    }

    private fun launchProPurchase() {
        val bm = billingManager
        if (bm != null) {
            Toast.makeText(this, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()
            bm.launchPurchaseFlow(this) { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            completeOnboarding()
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
            Step.DEFAULT_LAUNCHER -> getString(R.string.onboarding_step_1_of_5)
            Step.NOTIFICATION_ACCESS -> getString(R.string.onboarding_step_2_of_5)
            Step.WALLPAPER_SELECTION -> getString(R.string.onboarding_step_3_of_5)
            Step.CUSTOMIZE_TIPS -> getString(R.string.onboarding_step_4_of_5)
            Step.BUY_PRO -> getString(R.string.onboarding_step_5_of_5)
        }
    }

    private fun showStep(step: Step) {
        currentStep = step
        stepPill.text = getStepPillText(step)

        when (step) {
            Step.DEFAULT_LAUNCHER -> {
                heroImage.visibility = View.VISIBLE
                heroImage.setImageResource(R.drawable.ic_onboarding_phone_mockup)
                titleText.setText(R.string.onboarding_default_title)
                bodyText.setText(R.string.onboarding_default_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                wallpaperContainer.visibility = View.GONE
                tipsCard.visibility = View.GONE
                proCard.visibility = View.GONE
                restoreButton.visibility = View.GONE
                if (isDefaultLauncherGranted()) {
                    primaryButton.setText(R.string.onboarding_continue_action)
                } else {
                    primaryButton.setText(R.string.onboarding_default_action)
                }
                secondaryButton.setText(R.string.onboarding_default_skip)
                secondaryButton.visibility = View.VISIBLE
            }
            Step.NOTIFICATION_ACCESS -> {
                heroImage.visibility = View.VISIBLE
                heroImage.setImageResource(R.drawable.ic_onboarding_bell_badge)
                titleText.setText(R.string.onboarding_notification_title)
                bodyText.setText(R.string.onboarding_notification_body)
                infoCard.visibility = View.VISIBLE
                restrictedHintText.visibility = View.VISIBLE
                wallpaperContainer.visibility = View.GONE
                tipsCard.visibility = View.GONE
                proCard.visibility = View.GONE
                restoreButton.visibility = View.GONE
                primaryButton.setText(R.string.onboarding_notification_action)
                secondaryButton.setText(R.string.onboarding_notification_skip)
                secondaryButton.visibility = View.VISIBLE
            }
            Step.WALLPAPER_SELECTION -> {
                heroImage.visibility = View.GONE
                titleText.setText(R.string.onboarding_wallpaper_title)
                bodyText.setText(R.string.onboarding_wallpaper_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                wallpaperContainer.visibility = View.VISIBLE
                tipsCard.visibility = View.GONE
                proCard.visibility = View.GONE
                restoreButton.visibility = View.GONE
                primaryButton.setText(R.string.onboarding_wallpaper_action)
                secondaryButton.visibility = View.GONE
            }
            Step.CUSTOMIZE_TIPS -> {
                heroImage.visibility = View.VISIBLE
                heroImage.setImageResource(R.drawable.ic_onboarding_customize_hero)
                titleText.setText(R.string.onboarding_customize_title)
                bodyText.setText(R.string.onboarding_customize_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                wallpaperContainer.visibility = View.GONE
                tipsCard.visibility = View.VISIBLE
                proCard.visibility = View.GONE
                restoreButton.visibility = View.GONE
                primaryButton.setText(R.string.onboarding_customize_action)
                secondaryButton.visibility = View.GONE
            }
            Step.BUY_PRO -> {
                heroImage.visibility = View.GONE
                titleText.setText(R.string.onboarding_pro_title)
                bodyText.setText(R.string.onboarding_pro_body)
                infoCard.visibility = View.GONE
                restrictedHintText.visibility = View.GONE
                wallpaperContainer.visibility = View.GONE
                tipsCard.visibility = View.GONE
                proCard.visibility = View.VISIBLE
                restoreButton.visibility = View.VISIBLE
                primaryButton.setText(R.string.onboarding_pro_action)
                secondaryButton.setText(R.string.onboarding_pro_skip)
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
                    showStep(Step.WALLPAPER_SELECTION)
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
        when (currentStep) {
            Step.BUY_PRO -> showStep(Step.CUSTOMIZE_TIPS)
            Step.CUSTOMIZE_TIPS -> showStep(Step.WALLPAPER_SELECTION)
            Step.WALLPAPER_SELECTION -> showStep(Step.NOTIFICATION_ACCESS)
            Step.NOTIFICATION_ACCESS -> showStep(Step.DEFAULT_LAUNCHER)
            Step.DEFAULT_LAUNCHER -> handleExitOnboarding()
        }
    }

    private fun handleExitOnboarding() {
        if (!isDefaultLauncherGranted()) {
            moveTaskToBack(true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("current_step", currentStep.name)
        outState.putInt("selected_wallpaper_index", selectedWallpaperIndex)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager?.removeProStatusListener(proStatusListener)
        unregisterBackDispatcher()
        sIsActive = false
    }

    private fun completeOnboarding() {
        LauncherPrefs.get(this).putSync(LauncherPrefs.FIRST_RUN_ONBOARDING_DONE.to(true))
        try {
            val launcherIntent = Intent(this, com.android.launcher3.Launcher::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(launcherIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch Launcher on onboarding completion", e)
        }
        finish()
    }
}
