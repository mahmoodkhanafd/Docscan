package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    private const val TAG = "AdManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Official AdMob Test Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5284542467"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    fun loadInterstitialAd(context: Context) {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context.applicationContext,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        mainHandler.post {
                            interstitialAd = ad
                            Log.d(TAG, "Interstitial Ad Loaded successfully.")
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        mainHandler.post {
                            Log.e(TAG, "Interstitial Ad failed: ${error.message}")
                            interstitialAd = null
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading interstitial ad", e)
        }
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        try {
            if (interstitialAd != null) {
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mainHandler.post {
                            interstitialAd = null
                            loadInterstitialAd(activity)
                            onAdDismissed()
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        mainHandler.post {
                            interstitialAd = null
                            onAdDismissed()
                        }
                    }
                }
                interstitialAd?.show(activity)
            } else {
                onAdDismissed()
                loadInterstitialAd(activity)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error showing interstitial ad", e)
            onAdDismissed()
        }
    }

    fun loadRewardedAd(context: Context) {
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                context.applicationContext,
                REWARDED_AD_UNIT_ID,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        mainHandler.post {
                            rewardedAd = ad
                            Log.d(TAG, "Rewarded Ad Loaded successfully.")
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        mainHandler.post {
                            Log.e(TAG, "Rewarded Ad failed: ${error.message}")
                            rewardedAd = null
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading rewarded ad", e)
        }
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: () -> Unit, onAdDismissed: () -> Unit) {
        try {
            if (rewardedAd != null) {
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mainHandler.post {
                            rewardedAd = null
                            loadRewardedAd(activity)
                            onAdDismissed()
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        mainHandler.post {
                            rewardedAd = null
                            onAdDismissed()
                        }
                    }
                }
                rewardedAd?.show(activity) {
                    mainHandler.post {
                        onRewardEarned()
                    }
                }
            } else {
                onRewardEarned()
                onAdDismissed()
                loadRewardedAd(activity)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error showing rewarded ad", e)
            onRewardEarned()
            onAdDismissed()
        }
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    var hasError by remember { mutableStateOf(false) }

    if (!hasError) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                try {
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdManager.BANNER_AD_UNIT_ID
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                Handler(Looper.getMainLooper()).post {
                                    hasError = true
                                }
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (e: Throwable) {
                    Log.e("AdManager", "Error inflating BannerAdView", e)
                    hasError = true
                    android.view.View(context)
                }
            }
        )
    }
}
