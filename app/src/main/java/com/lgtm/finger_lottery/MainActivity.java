package com.lgtm.finger_lottery;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.window.SplashScreen;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "finger_lottery_settings";
    private static final String PREF_TARGET = "target_count";
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_GUIDE_SHOWN = "guide_shown";
    private static final long SPLASH_HOLD_MS = 1000L;

    private int targetCount = 1;
    private String languageTag = "ko";
    private Context languageContext;
    private NeonLotteryView lotteryView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        targetCount = preferences.getInt(PREF_TARGET, 1);
        languageTag = AppLanguage.find(preferences.getString(PREF_LANGUAGE, "ko")).tag;
        languageContext = AppLanguage.localizedContext(this, languageTag);
        configureSystemBars();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splashScreen = getSplashScreen();
            splashScreen.setOnExitAnimationListener(splashView ->
                    splashView.postDelayed(splashView::remove, SPLASH_HOLD_MS));
            showMainScreen(preferences);
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> showMainScreen(preferences), SPLASH_HOLD_MS);
        }
    }

    private void showWordmarkSplash(SharedPreferences preferences) {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(Color.BLACK);

        TextView finger = new TextView(this);
        finger.setText("FINGER");
        finger.setTextColor(Color.WHITE);
        finger.setTextSize(30);
        finger.setLetterSpacing(0.12f);
        finger.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        finger.setGravity(Gravity.CENTER);

        TextView lottery = new TextView(this);
        lottery.setText("LOTTERY");
        lottery.setTextColor(0xFF9AFBDE);
        lottery.setTextSize(30);
        lottery.setLetterSpacing(0.12f);
        lottery.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        lottery.setGravity(Gravity.CENTER);

        splash.addView(finger, new LinearLayout.LayoutParams(-1, -2));
        splash.addView(lottery, new LinearLayout.LayoutParams(-1, -2));
        setContentView(splash);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> showMainScreen(preferences), SPLASH_HOLD_MS);
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
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(lotteryView);

        if (!preferences.getBoolean(PREF_GUIDE_SHOWN, false)) {
            lotteryView.postDelayed(() -> showGuideSheet(true), 150L);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
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

        ScrollView scroll = new ScrollView(languageContext);
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
                languageContext = AppLanguage.localizedContext(this, languageTag);
                preferences.edit().putString(PREF_LANGUAGE, languageTag).apply();
                lotteryView.setLanguage(languageTag, languageContext);
                dialog.dismiss();
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
