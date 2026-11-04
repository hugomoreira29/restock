package com.example.restock.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.restock.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Centraliza o consentimento (RGPD) e os anúncios.
 * Nenhum anúncio é pedido antes de o UMP indicar que é permitido.
 * Com ADS_ENABLED a false, nada aqui contacta os serviços de anúncios.
 */
object AdsManager {

    private const val TAG = "AdsManager"
    private const val INTERSTITIAL_MIN_INTERVAL_MS = 30 * 60 * 1000L

    private val sdkStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var lastInterstitialShownAt: Long? = null

    /** Pede o consentimento, se necessário, e chama [onAdsAllowed] quando há anúncios permitidos. */
    fun gatherConsent(activity: Activity, onAdsAllowed: () -> Unit) {
        if (!BuildConfig.ADS_ENABLED) return

        UserMessagingPlatform.getConsentInformation(activity).requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Formulário de consentimento: ${it.message}") }
                    startIfAllowed(activity, onAdsAllowed)
                }
            },
            { requestError ->
                Log.w(TAG, "Atualização do consentimento: ${requestError.message}")
                startIfAllowed(activity, onAdsAllowed)
            }
        )

        // Um consentimento dado noutra sessão permite anúncios sem esperar pela atualização
        startIfAllowed(activity, onAdsAllowed)
    }

    private fun startIfAllowed(activity: Activity, onAdsAllowed: () -> Unit) {
        if (!UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) return

        val appContext = activity.applicationContext
        if (sdkStarted.compareAndSet(false, true)) {
            thread(name = "ads-init") {
                MobileAds.initialize(appContext) {
                    Handler(Looper.getMainLooper()).post {
                        onAdsAllowed()
                        preloadInterstitial(appContext)
                    }
                }
            }
        } else {
            onAdsAllowed()
        }
    }

    /** O RGPD exige uma forma de rever o consentimento depois de o dar. */
    fun isPrivacyOptionsRequired(context: Context): Boolean =
        BuildConfig.ADS_ENABLED &&
            UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let { Log.w(TAG, "Opções de privacidade: ${it.message}") }
        }
    }

    fun createBanner(activity: Activity): AdView {
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        return AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp))
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner: ${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    private fun preloadInterstitial(context: Context) {
        if (interstitial != null || isLoadingInterstitial) return
        isLoadingInterstitial = true

        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    Log.w(TAG, "Intersticial: ${error.message}")
                }
            }
        )
    }

    /** Mostra um intersticial se houver um carregado e já tiver passado o intervalo mínimo. */
    fun showInterstitialIfDue(activity: Activity) {
        val ad = interstitial ?: return
        val now = SystemClock.elapsedRealtime()
        val lastShown = lastInterstitialShownAt
        if (lastShown != null && now - lastShown < INTERSTITIAL_MIN_INTERVAL_MS) return

        val appContext = activity.applicationContext
        interstitial = null
        lastInterstitialShownAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preloadInterstitial(appContext)
        }
        ad.show(activity)
    }
}
