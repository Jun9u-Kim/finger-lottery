package com.lgtm.finger_lottery;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

/** Presents a native ad in a clearly labeled app dialog with a separate close action. */
final class NativeAdDialog {
    private NativeAdDialog() { }

    static void show(@NonNull Activity activity, @NonNull NativeAd ad,
                     @NonNull String closeLabel, @NonNull Runnable onClosed) {
        Dialog dialog = new Dialog(activity);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        card.setBackground(roundRect(0xFF11151D, dp(activity, 22)));
        card.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, -2));

        NativeAdView adView = new NativeAdView(activity);
        adView.setBackground(roundRect(0xFF191E28, dp(activity, 16)));
        adView.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        LinearLayout assets = new LinearLayout(activity);
        assets.setOrientation(LinearLayout.VERTICAL);
        adView.addView(assets, new android.view.ViewGroup.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView attribution = new TextView(activity);
        attribution.setText("Ad");
        attribution.setTextColor(0xFF080B10);
        attribution.setTextSize(11);
        attribution.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        attribution.setGravity(Gravity.CENTER);
        attribution.setPadding(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 3));
        attribution.setBackground(roundRect(0xFF9AFBDE, dp(activity, 4)));
        top.addView(attribution, new LinearLayout.LayoutParams(-2, -2));

        ImageView icon = null;
        if (ad.getIcon() != null && ad.getIcon().getDrawable() != null) {
            icon = new ImageView(activity);
            icon.setImageDrawable(ad.getIcon().getDrawable());
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42));
            iconParams.leftMargin = dp(activity, 10);
            top.addView(icon, iconParams);
            adView.setIconView(icon);
        }
        TextView headline = new TextView(activity);
        headline.setText(ad.getHeadline());
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(17);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setMaxLines(2);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headlineParams.leftMargin = dp(activity, 10);
        top.addView(headline, headlineParams);
        assets.addView(top, new LinearLayout.LayoutParams(-1, -2));
        adView.setHeadlineView(headline);

        MediaView media = new MediaView(activity);
        media.setMinimumHeight(dp(activity, 120));
        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(-1, dp(activity, 140));
        mediaParams.topMargin = dp(activity, 10);
        assets.addView(media, mediaParams);
        adView.setMediaView(media);

        if (ad.getBody() != null && !ad.getBody().isEmpty()) {
            TextView body = new TextView(activity);
            body.setText(ad.getBody());
            body.setTextColor(0xFFD4D8E0);
            body.setTextSize(14);
            body.setMaxLines(3);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(activity, 8);
            assets.addView(body, bodyParams);
            adView.setBodyView(body);
        }

        if (ad.getCallToAction() != null && !ad.getCallToAction().isEmpty()) {
            Button cta = new Button(activity);
            cta.setText(ad.getCallToAction());
            cta.setAllCaps(false);
            LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
            ctaParams.topMargin = dp(activity, 10);
            assets.addView(cta, ctaParams);
            adView.setCallToActionView(cta);
        }

        adView.setNativeAd(ad);
        int maxAdHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.58f);
        NestedScrollView adScroll = new NestedScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int cappedHeight = maxAdHeight;
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    cappedHeight = Math.min(cappedHeight, MeasureSpec.getSize(heightMeasureSpec));
                }
                int cappedHeightSpec = MeasureSpec.makeMeasureSpec(cappedHeight, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        };
        adScroll.setFillViewport(false);
        adScroll.setClipToPadding(false);
        adScroll.addView(adView, new android.view.ViewGroup.LayoutParams(-1, -2));
        card.addView(adScroll, new LinearLayout.LayoutParams(-1, -2));

        TextView close = new TextView(activity);
        close.setText(closeLabel);
        close.setTextColor(0xFF080B10);
        close.setTextSize(15);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundRect(0xFF9AFBDE, dp(activity, 16)));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        closeParams.topMargin = dp(activity, 10);
        card.addView(close, closeParams);

        dialog.setContentView(card);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            ad.destroy();
            onClosed.run();
        });
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private static GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
