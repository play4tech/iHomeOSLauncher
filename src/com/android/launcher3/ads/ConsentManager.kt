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
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Manages Google User Messaging Platform (UMP) consent flow for EEA & UK compliance.
 */
object ConsentManager {
    private const val TAG = "ConsentManager"

    fun interface ConsentCallback {
        fun onConsentGathered(canRequestAds: Boolean)
    }

    @JvmStatic
    fun canRequestAds(context: Context): Boolean {
        return try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(context)
            consentInformation.canRequestAds()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check canRequestAds", e)
            false
        }
    }

    /**
     * Helper method to request consent info update and load/show the consent form if required.
     */
    @JvmStatic
    fun gatherConsent(
        activity: Activity,
        callback: ConsentCallback
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error [${formError.errorCode}]: ${formError.message}")
                    }
                    callback.onConsentGathered(consentInformation.canRequestAds())
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update error [${requestConsentError.errorCode}]: ${requestConsentError.message}")
                callback.onConsentGathered(consentInformation.canRequestAds())
            }
        )
    }
}
