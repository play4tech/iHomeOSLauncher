/*
 * Copyright (C) 2026 iHomeOS Launcher Authors
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

package com.android.launcher3.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.launcher3.LauncherPrefs
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages Google Play Billing Library (v7+) integration for one-time in-app Pro purchase.
 *
 * Responsibilities:
 * - Maintains active BillingClient connection with auto-reconnect
 * - Queries localized price for `pro_lifetime`
 * - Launches one-time in-app product purchase flow
 * - Acknowledges purchases within Google Play's required 3-day window
 * - Queries existing purchases (Restore Purchases)
 * - Persists Pro entitlement in LauncherPrefs
 */
class BillingManager private constructor(context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_PRO_LIFETIME = "pro_lifetime"

        @Volatile
        private var sInstance: BillingManager? = null

        @JvmStatic
        fun getInstance(context: Context): BillingManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: BillingManager(context.applicationContext).also { sInstance = it }
            }
        }

        @JvmStatic
        fun isProUser(context: Context): Boolean {
            return try {
                LauncherPrefs.get(context).get(LauncherPrefs.PRO_USER_ENABLED)
            } catch (e: Exception) {
                false
            }
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val proStatusListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private val pendingReadyCallbacks = ArrayList<() -> Unit>()
    private val pendingErrorCallbacks = ArrayList<(String) -> Unit>()

    private var billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var isConnecting = false
    private var isConnected = false
    private var cachedProductDetails: ProductDetails? = null

    init {
        startConnection()
    }

    fun addProStatusListener(listener: (Boolean) -> Unit) {
        proStatusListeners.add(listener)
        listener(isProUser(appContext))
    }

    fun removeProStatusListener(listener: (Boolean) -> Unit) {
        proStatusListeners.remove(listener)
    }

    private fun notifyProStatusListeners() {
        val pro = isProUser(appContext)
        proStatusListeners.forEach { it.invoke(pro) }
    }

    fun getCachedProductDetails(): ProductDetails? = cachedProductDetails

    fun getFormattedPrice(): String {
        return cachedProductDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$2.99"
    }

    fun startConnection(onReady: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        if (isConnected) {
            onReady?.let { mainHandler.post(it) }
            return
        }

        synchronized(pendingReadyCallbacks) {
            if (onReady != null) pendingReadyCallbacks.add(onReady)
            if (onError != null) pendingErrorCallbacks.add(onError)
        }

        if (isConnecting) return

        isConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                val readyList: List<() -> Unit>
                val errorList: List<(String) -> Unit>
                synchronized(pendingReadyCallbacks) {
                    readyList = ArrayList(pendingReadyCallbacks)
                    errorList = ArrayList(pendingErrorCallbacks)
                    pendingReadyCallbacks.clear()
                    pendingErrorCallbacks.clear()
                }

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "BillingClient connected successfully")
                    isConnected = true
                    queryProductDetails()
                    queryActivePurchases()
                    mainHandler.post {
                        readyList.forEach { it.invoke() }
                    }
                } else {
                    val errMsg = "Google Play Billing setup failed (${billingResult.responseCode}): ${billingResult.debugMessage}"
                    Log.w(TAG, errMsg)
                    isConnected = false
                    mainHandler.post {
                        errorList.forEach { it.invoke(errMsg) }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                isConnected = false
                Log.w(TAG, "Billing service disconnected. Will retry connection on next demand.")
                val errorList: List<(String) -> Unit>
                synchronized(pendingReadyCallbacks) {
                    errorList = ArrayList(pendingErrorCallbacks)
                    pendingReadyCallbacks.clear()
                    pendingErrorCallbacks.clear()
                }
                mainHandler.post {
                    errorList.forEach { it.invoke("Google Play Billing disconnected. Please check internet connection.") }
                }
            }
        })
    }

    fun queryProductDetails(
        onComplete: ((ProductDetails?) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (!isConnected) {
            startConnection(
                onReady = { queryProductDetails(onComplete, onError) },
                onError = { err ->
                    mainHandler.post {
                        onError?.invoke(err)
                        onComplete?.invoke(null)
                    }
                }
            )
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                val details = productDetailsList.firstOrNull { it.productId == PRODUCT_PRO_LIFETIME }
                cachedProductDetails = details
                Log.i(TAG, "ProductDetails loaded: id=${details?.productId}, price=${details?.oneTimePurchaseOfferDetails?.formattedPrice}")
                mainHandler.post {
                    notifyProStatusListeners()
                    onComplete?.invoke(details)
                }
            } else {
                val errMsg = if (productDetailsList.isNullOrEmpty()) {
                    "Product 'pro_lifetime' is not active yet in Google Play Console. Once activated, the purchase dialog will appear."
                } else {
                    "Google Play error (${billingResult.responseCode}): ${billingResult.debugMessage}"
                }
                Log.w(TAG, "queryProductDetails: $errMsg")
                mainHandler.post {
                    onError?.invoke(errMsg)
                    onComplete?.invoke(null)
                }
            }
        }
    }

    /**
     * Launch Google Play Billing Purchase Flow for Pro lifetime unlock.
     */
    fun launchPurchaseFlow(activity: Activity, onError: ((String) -> Unit)? = null) {
        val details = cachedProductDetails
        if (details == null) {
            queryProductDetails(
                onComplete = { loadedDetails ->
                    if (loadedDetails != null) {
                        executeBillingFlow(activity, loadedDetails, onError)
                    }
                },
                onError = { err ->
                    onError?.invoke(err)
                }
            )
            return
        }
        executeBillingFlow(activity, details, onError)
    }

    private fun executeBillingFlow(activity: Activity, details: ProductDetails, onError: ((String) -> Unit)?) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "launchBillingFlow failed: code=${result.responseCode}, msg=${result.debugMessage}")
            val msg = "Google Play Billing error (${result.responseCode}): ${result.debugMessage}"
            mainHandler.post { onError?.invoke(msg) }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "User canceled billing flow")
        } else {
            Log.w(TAG, "onPurchasesUpdated error code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.products.contains(PRODUCT_PRO_LIFETIME)) {
                // Must acknowledge within 3 days or Google Play auto-refunds
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.acknowledgePurchase(ackParams) { ackResult ->
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
                            setProState(true)
                        } else {
                            Log.e(TAG, "Failed to acknowledge purchase: code=${ackResult.responseCode}")
                        }
                    }
                } else {
                    setProState(true)
                }
            }
        }
    }

    /**
     * Restore existing purchases by querying Google Play Store purchase cache.
     */
    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        Log.i(TAG, "restorePurchases initiated. isConnected=$isConnected")
        if (!isConnected) {
            startConnection(
                onReady = { restorePurchases(onResult) },
                onError = { err ->
                    Log.w(TAG, "restorePurchases connection failed: $err")
                    mainHandler.post { onResult(false, err) }
                }
            )
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundPro = false
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_PRO_LIFETIME) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        foundPro = true
                        handlePurchase(purchase)
                    }
                }
                Log.i(TAG, "restorePurchases completed: foundPro=$foundPro (total purchases=${purchases.size})")
                if (foundPro) {
                    setProState(true)
                    mainHandler.post { onResult(true, "iHomeOS Pro restored successfully!") }
                } else {
                    mainHandler.post { onResult(false, "No previous Pro purchase found on this Google account.") }
                }
            } else {
                val msg = "Google Play error (${billingResult.responseCode}): ${billingResult.debugMessage}"
                Log.w(TAG, "restorePurchases error: $msg")
                mainHandler.post { onResult(false, msg) }
            }
        }
    }

    private fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any {
                    it.products.contains(PRODUCT_PRO_LIFETIME) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                setProState(hasPro)
            }
        }
    }

    private fun setProState(enabled: Boolean) {
        try {
            LauncherPrefs.get(appContext).putSync(LauncherPrefs.PRO_USER_ENABLED.to(enabled))
        } catch (e: Exception) {
            Log.e(TAG, "Error writing Pro state to LauncherPrefs", e)
        }
        mainHandler.post {
            for (listener in proStatusListeners) {
                listener(enabled)
            }
        }
    }
}
