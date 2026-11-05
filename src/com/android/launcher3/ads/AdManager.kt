/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.billing.BillingManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centrally manages AdMob ad lifecycle across the launcher.
 *
 * AdMob & Google Play Policy Rules Enforced:
 * 1. Pro Users: Completely ad-free (no banner, no interstitial).
 * 2. Home Screen & System Integrity: Zero ads on desktop, dock, folders, widgets, or Home button.
 * 3. Accidental Click Prevention: Banners are pinned cleanly at the bottom outside interactive lists/grids.
 * 4. Frequency Cooldown: Interstitials enforce a minimum 60-second cooldown so users are never spammed.
 * 5. First-Run Grace: Interstitials are skipped on the user's initial customization visit.
 * 6. Non-Blocking: Dismissal or load failures immediately proceed with user navigation.
 */
object AdManager {
    private const val TAG = "AdManager"

    /** Official Google AdMob sample test ad unit IDs */
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    /** Minimum cooldown between full-screen ads to prevent disruptive ad spam (Google Play Better Ads policy) */
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    /** Grace period after earning a reward during which user can freely re-enter customization */
    private const val REWARDED_GRACE_PERIOD_MS = 60_000L

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    @Volatile
    private var lastInterstitialTimeMs: Long = 0L

    @Volatile
    private var lastRewardedTimeMs: Long = 0L

    /**
     * Initializes the Mobile Ads SDK on the main thread once consent is gathered.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, onInitComplete: Runnable? = null) {
        if (BillingManager.isProUser(context)) {
            Log.d(TAG, "Pro user active; suppressing Mobile Ads initialization.")
            return
        }

        if (isMobileAdsInitialized.getAndSet(true)) {
            onInitComplete?.run()
            return
        }

        try {
            if (com.android.launcher3.BuildConfig.DEBUG) {
                try {
                    val requestConfiguration = MobileAds.getRequestConfiguration().toBuilder()
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                        .build()
                    MobileAds.setRequestConfiguration(requestConfiguration)
                } catch (ignored: Exception) {}
            }
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "MobileAds initialized: $status (debug test ads: ${com.android.launcher3.BuildConfig.DEBUG})")
                onInitComplete?.run()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds", e)
            onInitComplete?.run()
        }
    }

    @JvmStatic
    fun getBannerAdUnitId(context: Context): String {
        return if (com.android.launcher3.BuildConfig.DEBUG) {
            TEST_BANNER_ID
        } else {
            context.getString(R.string.admob_banner_id)
        }
    }

    @JvmStatic
    fun getInterstitialAdUnitId(context: Context): String {
        return if (com.android.launcher3.BuildConfig.DEBUG) {
            TEST_INTERSTITIAL_ID
        } else {
            context.getString(R.string.admob_interstitial_id)
        }
    }

    @JvmStatic
    fun getRewardedAdUnitId(context: Context): String {
        return if (com.android.launcher3.BuildConfig.DEBUG) {
            TEST_REWARDED_ID
        } else {
            context.getString(R.string.admob_rewarded_id)
        }
    }

    /**
     * Loads an adaptive banner ad into [container].
     * The banner is placed cleanly away from interactive items.
     */
    @JvmStatic
    fun loadBanner(activity: Activity, container: ViewGroup): AdView? {
        if (BillingManager.isProUser(activity)) {
            container.visibility = View.GONE
            return null
        }

        return try {
            val adView = AdView(activity).apply {
                adUnitId = getBannerAdUnitId(activity)
                setAdSize(AdSize.BANNER)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        container.visibility = View.VISIBLE
                        Log.d(TAG, "Banner ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        container.visibility = View.GONE
                        Log.w(TAG, "Banner ad failed to load: ${loadAdError.message}")
                    }
                }
            }

            container.removeAllViews()
            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
            adView
        } catch (e: Exception) {
            Log.e(TAG, "Error loading banner ad", e)
            container.visibility = View.GONE
            null
        }
    }

    /**
     * Destroys any active banner ad inside [container] and hides the container.
     */
    @JvmStatic
    fun destroyBanner(container: ViewGroup?) {
        if (container == null) return
        try {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is AdView) {
                    child.destroy()
                }
            }
            container.removeAllViews()
            container.visibility = View.GONE
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying banner ad", e)
        }
    }

    /**
     * Preloads an interstitial ad in the background.
     */
    @JvmStatic
    fun preloadInterstitial(context: Context) {
        if (BillingManager.isProUser(context)) {
            return
        }

        if (interstitialAd != null || isInterstitialLoading) {
            return
        }

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getInterstitialAdUnitId(context)

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Preloads a rewarded ad in the background.
     */
    @JvmStatic
    fun preloadRewarded(context: Context) {
        if (BillingManager.isProUser(context)) {
            return
        }

        if (rewardedAd != null || isRewardedLoading) {
            return
        }

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getRewardedAdUnitId(context)

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    @JvmStatic
    fun isRewardedAdReady(): Boolean = rewardedAd != null

    /**
     * Shows an interstitial ad when user explicitly clicks an edit/customization action
     * ("Change Wallpaper", "Add Widget", "Customize"), subject to a 60-second cooldown.
     * Invokes [onFinished] immediately if on cooldown, ineligible, or dismissed.
     */
    @JvmStatic
    fun showInterstitialWithCooldown(activity: Activity, onFinished: () -> Unit) {
        // 1. Pro Users are exempt
        if (BillingManager.isProUser(activity)) {
            Log.d(TAG, "Skipping interstitial: Pro user.")
            onFinished()
            return
        }

        // 2. Cooldown check to avoid disruptive back-to-back full-screen ads
        val now = SystemClock.elapsedRealtime()
        if (lastInterstitialTimeMs > 0 && (now - lastInterstitialTimeMs) < INTERSTITIAL_COOLDOWN_MS) {
            val remainingSec = (INTERSTITIAL_COOLDOWN_MS - (now - lastInterstitialTimeMs)) / 1000
            Log.d(TAG, "Skipping interstitial: Cooldown active (${remainingSec}s remaining).")
            onFinished()
            return
        }

        // 3. Check ad readiness
        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "Interstitial ad not ready; proceeding without blocking user.")
            preloadInterstitial(activity)
            onFinished()
            return
        }

        // 4. Show ad
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Interstitial ad was clicked.")
            }

            override fun onAdImpression() {
                Log.d(TAG, "Interstitial ad impression recorded.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed full screen content.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed by user.")
                interstitialAd = null
                lastInterstitialTimeMs = SystemClock.elapsedRealtime()
                preloadInterstitial(activity)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                interstitialAd = null
                preloadInterstitial(activity)
                onFinished()
            }
        }

        ad.show(activity)
    }

    /**
     * Shows a rewarded ad to unlock a customization action (e.g. Icon Size, App Library Only).
     *
     * Invariants enforced:
     * 1. Pro users bypass ads completely and immediately unlock.
     * 2. If the user recently earned the reward (within [REWARDED_GRACE_PERIOD_MS]),
     *    they can access the feature freely without back-to-back ad fatigue.
     * 3. If an ad is shown, [onRewarded] is invoked ONLY if the user watches through
     *    and earns the reward.
     * 4. If the user dismisses the ad early (e.g., via the Android Back button or early close),
     *    [onDismissedWithoutReward] is invoked and [onRewarded] is NOT called.
     * 5. If the ad fails to show or is not ready, user navigation proceeds gracefully to prevent blocking.
     */
    @JvmStatic
    @JvmOverloads
    fun showRewardedAd(
        activity: Activity,
        onRewarded: Runnable,
        onDismissedWithoutReward: Runnable? = null
    ) {
        // 1. Pro Users are exempt
        if (BillingManager.isProUser(activity)) {
            Log.d(TAG, "Skipping rewarded ad: Pro user.")
            onRewarded.run()
            return
        }

        // 2. Grace period check: if user already earned reward recently, let them in
        val now = SystemClock.elapsedRealtime()
        if (lastRewardedTimeMs > 0 && (now - lastRewardedTimeMs) < REWARDED_GRACE_PERIOD_MS) {
            val remainingSec = (REWARDED_GRACE_PERIOD_MS - (now - lastRewardedTimeMs)) / 1000
            Log.d(TAG, "Skipping rewarded ad: Reward grace period active (${remainingSec}s remaining).")
            onRewarded.run()
            return
        }

        // 3. Check ad readiness
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad not ready; preloading and proceeding gracefully.")
            preloadRewarded(activity)
            onRewarded.run()
            return
        }

        // 4. Show rewarded ad and track reward completion
        var isRewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad clicked.")
            }

            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad impression recorded.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed full screen content.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed by user. Earned: $isRewardEarned")
                rewardedAd = null
                preloadRewarded(activity)
                if (isRewardEarned) {
                    lastRewardedTimeMs = SystemClock.elapsedRealtime()
                    onRewarded.run()
                } else {
                    onDismissedWithoutReward?.run()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                rewardedAd = null
                preloadRewarded(activity)
                onRewarded.run()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.type} (${rewardItem.amount})")
            isRewardEarned = true
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showRewardedAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: (() -> Unit)? = null
    ) {
        showRewardedAd(
            activity,
            Runnable { onRewarded() },
            if (onDismissedWithoutReward != null) Runnable { onDismissedWithoutReward() } else null
        )
    }

    /**
     * Deprecated: Exit interstitials are prohibited under Google Play's Disruptive Ads Policy.
     * Invokes [onFinished] immediately without showing an ad.
     */
    @Deprecated("Prohibited by Google Play Disruptive Ads Policy")
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun showInterstitialOnExitIfEligible(activity: Activity, onFinished: () -> Unit) {
        onFinished()
    }
}
