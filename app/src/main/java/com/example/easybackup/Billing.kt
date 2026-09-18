package com.example.easybackup

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the single one-time "unlock unlimited backups" purchase via
 * Google Play Billing — the Android equivalent of the iOS app's StoreKit
 * `Store.swift`.
 *
 * REQUIRED SETUP before this works:
 * 1. In Google Play Console, create this app's listing, then add an
 *    **in-app product** (one-time, not a subscription) with Product ID
 *    exactly matching `Billing.PRODUCT_ID` below.
 * 2. Set your own price, display name, and description there.
 * 3. The app must be uploaded at least as an internal testing release
 *    before Play Billing will return real product details — this is a
 *    Play Console requirement, not something fixable in code.
 * 4. For local testing without a full Play Console listing, add
 *    yourself as a **license tester** in Play Console → Setup → License
 *    testing, which lets test purchases go through without being charged.
 */
class Billing(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        /** MUST exactly match the Product ID you create in Play Console. */
        const val PRODUCT_ID = "unlock_unlimited_backups"
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProductDetails()
                    refreshEntitlement()
                } else {
                    _purchaseError.value = "Couldn't connect to Google Play. Check your connection and try again."
                }
            }
            override fun onBillingServiceDisconnected() {
                // BillingClient reconnects automatically on the next call.
            }
        })
    }

    fun refreshProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.firstOrNull()
                if (productDetailsList.isEmpty()) {
                    _purchaseError.value = "The unlock option isn't available yet. Please try again shortly."
                }
            } else {
                _purchaseError.value = "Couldn't load the unlock option. Check your connection and try again."
            }
        }
    }

    /** Checks existing entitlements (already-completed purchases) — call on launch and after purchase/restore. */
    fun refreshEntitlement() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isUnlocked.value = purchases.any { it.products.contains(PRODUCT_ID) }
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value ?: return
        _purchaseError.value = null
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /** Play Billing doesn't need a separate "restore" action — refreshEntitlement() re-syncs from Google's records. */
    fun restore() = refreshEntitlement()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<com.android.billingclient.api.Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.products.contains(PRODUCT_ID)) {
                        _isUnlocked.value = true
                        if (!purchase.isAcknowledged) {
                            val ackParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            client.acknowledgePurchase(ackParams) {}
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> { /* no error to show for a plain cancel */ }
            else -> _purchaseError.value = "Purchase failed. Please try again."
        }
    }
}
