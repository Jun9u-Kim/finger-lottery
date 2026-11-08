package com.lgtm.finger_lottery;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "finger_lottery_settings";
    private static final String PREF_TARGET = "target_count";
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_GUIDE_SHOWN = "guide_shown";

    private int targetCount = 1;
    private String languageTag = "ko";
    private Context languageContext;
    private NeonLotteryView lotteryView;
    private ConsentInformation consentInformation;
    private boolean privacyOptionsRequired;
    private boolean consentFlowFinished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        targetCount = preferences.getInt(PREF_TARGET, 1);
        languageTag = AppLanguage.find(preferences.getString(PREF_LANGUAGE, "ko")).tag;
        applyStoredLanguage();
        languageContext = AppLanguage.localizedContext(this, languageTag);
        configureSystemBars();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        showMainScreen(preferences);
        requestConsentThenStart(preferences, savedInstanceState == null);
    }

    private void applyStoredLanguage() {
        String appLanguageTag = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (appLanguageTag.isEmpty()) {
            if (!"ko".equals(languageTag)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
            }
        } else if (!appLanguageTag.equalsIgnoreCase(languageTag)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
        }
    }

    private void requestConsentThenStart(SharedPreferences preferences, boolean showStartupAd) {
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        FingerLotteryApplication ads = (FingerLotteryApplication) getApplication();
        consentInformation.requestConsentInfoUpdate(
                this,
                parameters,
                () -> {
                    updatePrivacyOptionsRequirement();
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                        updatePrivacyOptionsRequirement();
                        continueAfterConsent(preferences, showStartupAd);
                    });
                },
                requestError -> {
                    updatePrivacyOptionsRequirement();
                    continueAfterConsent(preferences, showStartupAd);
                });
        // UMP retains the prior session's consent decision. Start preparing
        // the ad now so it can be shown as soon as the consent flow completes.
        if (consentInformation.canRequestAds()) {
            ads.setAdsAllowed(true);
        }
    }

    private void continueAfterConsent(SharedPreferences preferences, boolean showStartupAd) {
        if (consentFlowFinished || isFinishing() || isDestroyed()) return;
        consentFlowFinished = true;
        boolean canRequestAds = consentInformation.canRequestAds();
        FingerLotteryApplication ads = (FingerLotteryApplication) getApplication();
        ads.setAdsAllowed(canRequestAds);
        if (canRequestAds && showStartupAd) {
            ads.showStartupNativeAd(this, () -> showGuideIfNeeded(preferences));
        } else {
            showGuideIfNeeded(preferences);
        }
    }

    private void updatePrivacyOptionsRequirement() {
        privacyOptionsRequired = consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    private void showPrivacyOptions() {
        if (consentInformation == null) return;
        UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
            updatePrivacyOptionsRequirement();
            boolean canRequestAds = consentInformation.canRequestAds();
            ((FingerLotteryApplication) getApplication()).setAdsAllowed(canRequestAds);
        });
    }

    private void showMainScreen(SharedPreferences preferences) {
        if (isFinishing() || isDestroyed()) return;
        lotteryView = new NeonLotteryView(this, new NeonLotteryView.Listener() {
            @Override
            public void onEditCountRequested() {
                showCountSheet();
            }

            @Override
            public void onHelpRequested() {
                showGuideSheet(false);
            }

            @Override
            public void onLanguageRequested() {
                showLanguageSheet(preferences);
            }
        });
        lotteryView.setTargetCount(targetCount);
        lotteryView.setLanguage(languageTag, languageContext);
        lotteryView.setPadding(0, 0, 0, 0);
        ViewCompat.setOnApplyWindowInsetsListener(lotteryView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(lotteryView);
        ViewCompat.requestApplyInsets(lotteryView);
    }

    private void showGuideIfNeeded(SharedPreferences preferences) {
        if (lotteryView == null || isFinishing() || isDestroyed()) return;
        if (!preferences.getBoolean(PREF_GUIDE_SHOWN, false)) {
            lotteryView.postDelayed(() -> showGuideSheet(true), 150L);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.rgb(5, 7, 11));
        window.setNavigationBarColor(Color.rgb(5, 7, 11));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        window.getDecorView().setSystemUiVisibility(0);
    }

    private void showCountSheet() {
        final int[] draft = {targetCount};
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout content = sheetContainer(this);
        addGrabber(content);
        addText(content, text(R.string.count_title), 23, Color.WHITE, true, 0, 5, 0, 18);

        LinearLayout picker = new LinearLayout(this);
        picker.setGravity(Gravity.CENTER);
        picker.setOrientation(LinearLayout.HORIZONTAL);

        TextView minus = pickerButton("−");
        TextView number = pickerNumber(draft[0]);
        TextView plus = pickerButton("+");
        picker.addView(minus, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(0, dp(72), 1);
        picker.addView(number, numberParams);
        picker.addView(plus, new LinearLayout.LayoutParams(dp(56), dp(56)));
        content.addView(picker, new LinearLayout.LayoutParams(-1, dp(78)));

        addText(content, text(R.string.count_max), 12, 0xFF777F8D, false,
                0, 8, 0, 24).setGravity(Gravity.CENTER);

        minus.setOnClickListener(v -> {
            draft[0] = Math.max(1, draft[0] - 1);
            number.setText(String.format(java.util.Locale.KOREA, "%02d", draft[0]));
            number.setContentDescription(text(R.string.selected_count, draft[0]));
            minus.setAlpha(draft[0] == 1 ? 0.42f : 1f);
        });
        plus.setOnClickListener(v -> {
            draft[0] = Math.min(20, draft[0] + 1);
            number.setText(String.format(java.util.Locale.KOREA, "%02d", draft[0]));
            number.setContentDescription(text(R.string.selected_count, draft[0]));
            plus.setAlpha(draft[0] == 20 ? 0.42f : 1f);
        });

        TextView confirm = actionButton(text(R.string.apply));
        content.addView(confirm, new LinearLayout.LayoutParams(-1, dp(54)));
        confirm.setOnClickListener(v -> {
            targetCount = draft[0];
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_TARGET, targetCount).apply();
            lotteryView.setTargetCount(targetCount);
            dialog.dismiss();
        });
        dialog.setContentView(content);
        dialog.show();
        clearBottomSheetOuterBackground(dialog);
    }

    private void showGuideSheet(boolean firstRun) {
        if (isFinishing() || isDestroyed()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout content = sheetContainer(this);
        addGrabber(content);
        addText(content, text(R.string.guide_title), 23, Color.WHITE, true, 0, 5, 0, 7);
        addText(content, text(R.string.guide_intro), 14, 0xFF969DAC, false, 0, 0, 0, 23);

        addGuideStep(content, "01", text(R.string.guide_step1_title), text(R.string.guide_step1_detail));
        addGuideStep(content, "02", text(R.string.guide_step2_title), text(R.string.guide_step2_detail));
        addGuideStep(content, "03", text(R.string.guide_step3_title), text(R.string.guide_step3_detail));

        if (privacyOptionsRequired) {
            TextView privacyOptions = actionButton(text(R.string.privacy_options));
            privacyOptions.setContentDescription(text(R.string.privacy_options));
            LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, dp(48));
            privacyParams.topMargin = dp(8);
            content.addView(privacyOptions, privacyParams);
            privacyOptions.setOnClickListener(v -> showPrivacyOptions());
        }

        TextView action = actionButton(text(firstRun ? R.string.start : R.string.back));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(54));
        actionParams.topMargin = dp(25);
        content.addView(action, actionParams);
        action.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_GUIDE_SHOWN, true).apply();
            dialog.dismiss();
        });
        dialog.setContentView(content);
        dialog.show();
        clearBottomSheetOuterBackground(dialog);
    }

    private void showLanguageSheet(SharedPreferences preferences) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout content = sheetContainer(this);
        addGrabber(content);
        addText(content, text(R.string.language_sheet_title), 23, Color.WHITE, true, 0, 5, 0, 5);
        addText(content, text(R.string.language_sheet_description), 14, 0xFF969DAC, false,
                0, 0, 0, 16);

        NestedScrollView scroll = new NestedScrollView(languageContext);
        scroll.setFillViewport(true);
        scroll.setNestedScrollingEnabled(true);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout options = new LinearLayout(languageContext);
        options.setOrientation(LinearLayout.VERTICAL);
        for (AppLanguage.Option option : AppLanguage.OPTIONS) {
            boolean selected = option.tag.equals(languageTag);
            TextView row = new TextView(languageContext);
            row.setText((selected ? "✓  " : "") + option.label);
            row.setTextColor(selected ? 0xFF9AFBDE : 0xFFE8EAF0);
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setBackground(roundRect(selected ? 0xFF1B2928 : 0xFF191E28, 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
            rowParams.bottomMargin = dp(6);
            options.addView(row, rowParams);
            row.setOnClickListener(v -> {
                languageTag = option.tag;
                preferences.edit().putString(PREF_LANGUAGE, languageTag).apply();
                dialog.dismiss();
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
            });
        }
        scroll.addView(options);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(420)));
        dialog.setContentView(content);
        dialog.show();
        clearBottomSheetOuterBackground(dialog);
    }

    @NonNull
    private void clearBottomSheetOuterBackground(BottomSheetDialog dialog) {
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.BLACK);
            ViewCompat.setBackgroundTintList(bottomSheet, android.content.res.ColorStateList.valueOf(Color.BLACK));
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private LinearLayout sheetContainer(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(12), dp(24), dp(28));
        layout.setLayoutDirection(languageContext == null ? View.LAYOUT_DIRECTION_LTR
                : languageContext.getResources().getConfiguration().getLayoutDirection());
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF11151D);
        background.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        layout.setBackground(background);
        layout.setClipToOutline(true);
        return layout;
    }

    private void addGrabber(LinearLayout layout) {
        View grabber = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF3A404C);
        shape.setCornerRadius(dp(4));
        grabber.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(23);
        layout.addView(grabber, params);
    }

    private void addGuideStep(LinearLayout parent, String index, String title, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF191E28);
        background.setCornerRadius(dp(16));
        row.setBackground(background);

        TextView badge = new TextView(this);
        badge.setText(index);
        badge.setTextColor(0xFF8E83FF);
        badge.setTextSize(12);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x223F46FF);
        badgeBg.setCornerRadius(dp(12));
        badge.setBackground(badgeBg);
        row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView titleView = addText(copy, title, 14, 0xFFF1F2F5, true, 0, 0, 0, 3);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        addText(copy, detail, 12, 0xFF858D9B, false, 0, 0, 0, 0);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);
        parent.addView(row, rowParams);
    }

    private TextView addText(LinearLayout parent, String text, int size, int color, boolean bold,
                             int left, int top, int right, int bottom) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(view, params);
        return view;
    }

    private TextView pickerButton(String symbol) {
        TextView view = new TextView(this);
        view.setText(symbol);
        view.setTextSize(26);
        view.setTextColor(0xFFE8EAF0);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundRect(0xFF202632, 18));
        view.setContentDescription(symbol.equals("+")
                ? text(R.string.add_one) : text(R.string.remove_one));
        return view;
    }

    private TextView pickerNumber(int value) {
        TextView view = new TextView(this);
        view.setText(String.format(java.util.Locale.KOREA, "%02d", value));
        view.setTextColor(Color.WHITE);
        view.setTextSize(38);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setContentDescription(text(R.string.selected_count, value));
        return view;
    }

    private TextView actionButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFF080B10);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setBackground(roundRect(0xFF9AFBDE, 16));
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String text(int stringId) {
        return languageContext.getString(stringId);
    }

    private String text(int stringId, int value) {
        return languageContext.getString(stringId, value);
    }
}
