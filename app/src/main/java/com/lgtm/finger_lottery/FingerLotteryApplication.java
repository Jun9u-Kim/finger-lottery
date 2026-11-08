package com.lgtm.finger_lottery;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;

/** Initializes Mobile Ads after consent and loads the launch native ad. */
public final class FingerLotteryApplication extends Application {
    private static final String TAG = "FingerLotteryAds";
    private static final long STARTUP_AD_WAIT_MS = 2_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean adsAllowed;
    private boolean isInitializingSdk;
    private boolean isSdkInitialized;
    private boolean isLoadingAd;
    private boolean startupPending;
    private NativeAd preloadedStartupAd;
    private Activity startupActivity;
    private Runnable startupCallback;
    private Runnable startupTimeout;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void setAdsAllowed(boolean allowed) {
        adsAllowed = allowed;
        if (allowed) {
            initializeSdk();
            loadNativeAdIfReady();
        } else {
            if (preloadedStartupAd != null) {
                preloadedStartupAd.destroy();
                preloadedStartupAd = null;
            }
            if (startupPending) finishStartup();
        }
    }

    public void showStartupNativeAd(@NonNull Activity activity, @NonNull Runnable onFinished) {
        if (!adsAllowed || !isActivityUsable(activity)) {
            onFinished.run();
            return;
        }
        startupActivity = activity;
        startupCallback = onFinished;
        startupPending = true;
        startupTimeout = this::finishStartup;
        mainHandler.postDelayed(startupTimeout, STARTUP_AD_WAIT_MS);
        if (preloadedStartupAd != null) {
            NativeAd ad = preloadedStartupAd;
            preloadedStartupAd = null;
            showNativeAd(ad);
            return;
        }
        initializeSdk();
        loadNativeAdIfReady();
    }

    private void initializeSdk() {
        if (!adsAllowed || isSdkInitialized || isInitializingSdk) return;
        isInitializingSdk = true;
        new Thread(() -> MobileAds.initialize(this, status -> {
            isInitializingSdk = false;
            isSdkInitialized = true;
            loadNativeAdIfReady();
        }), "mobile-ads-init").start();
    }

    private void loadNativeAdIfReady() {
        if (!adsAllowed || !isSdkInitialized || isLoadingAd || preloadedStartupAd != null) return;
        isLoadingAd = true;
        AdLoader loader = new AdLoader.Builder(this, getString(R.string.admob_native_ad_unit_id))
                .forNativeAd(ad -> {
                    isLoadingAd = false;
                    if (!adsAllowed) {
                        ad.destroy();
                        return;
                    }
                    if (!startupPending || !isActivityUsable(startupActivity)) {
                        preloadedStartupAd = ad;
                        return;
                    }
                    showNativeAd(ad);
                })
                .withAdListener(new com.google.android.gms.ads.AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Log.d(TAG, "Native ad failed to load: " + error.getMessage());
                        isLoadingAd = false;
                        if (startupPending) finishStartup();
                    }
                })
                .build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private void showNativeAd(@NonNull NativeAd ad) {
        Activity activity = startupActivity;
        if (!startupPending || !isActivityUsable(activity)) {
            ad.destroy();
            return;
        }
        if (startupTimeout != null) {
            mainHandler.removeCallbacks(startupTimeout);
            startupTimeout = null;
        }
        try {
            NativeAdDialog.show(activity, ad, "Close", this::finishStartup);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not display native ad; continuing into the app.", error);
            ad.destroy();
            finishStartup();
        }
    }

    private boolean isActivityUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void finishStartup() {
        if (!startupPending) return;
        startupPending = false;
        if (startupTimeout != null) {
            mainHandler.removeCallbacks(startupTimeout);
            startupTimeout = null;
        }
        startupActivity = null;
        Runnable callback = startupCallback;
        startupCallback = null;
        if (callback != null) callback.run();
    }
}
