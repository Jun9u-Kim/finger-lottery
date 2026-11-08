package com.lgtm.finger_lottery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Full-screen touch surface and animated lottery board. */
public class NeonLotteryView extends View {
    public interface Listener {
        void onEditCountRequested();
        void onHelpRequested();
        void onLanguageRequested();
    }

    private static final int BG = Color.BLACK;
    private static final int SURFACE = Color.rgb(17, 22, 31);
    private static final int MUTED = Color.rgb(134, 143, 158);
    private static final int WHITE = Color.rgb(241, 243, 248);
    private static final int MINT = Color.rgb(154, 251, 222);
    private static final int[] NEON = {
            0xFF8D72FF, 0xFF23DFFF, 0xFFFF5FCE, 0xFFB9FF63, 0xFFFFA24D,
            0xFF32E6A0, 0xFFFF5D77, 0xFF69A8FF, 0xFFFFE66A, 0xFFC98BFF,
            0xFF45D5DE, 0xFFFF7DCB, 0xFF9CFFC9, 0xFFFFC857, 0xFF8190FF,
            0xFFFF7653, 0xFF91F34B, 0xFFE68BFF, 0xFF56F2B3, 0xFFFF719E
    };

    private enum Phase { EMPTY, NEED_MORE, WAITING, COUNTDOWN, DONE }

    private static final class Finger {
        final int pointerId;
        final int color;
        final long bornAt;
        float x;
        float y;

        Finger(int pointerId, float x, float y, int color, long bornAt) {
            this.pointerId = pointerId;
            this.x = x;
            this.y = y;
            this.color = color;
            this.bornAt = bornAt;
        }
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Map<Integer, Finger> fingers = new HashMap<>();
    private final Map<Integer, Integer> selectedRanks = new HashMap<>();
    private final List<Finger> frozenFingers = new ArrayList<>();
    private final Random random = new Random();
    private final float density;
    private final long[] hapticPattern = {0, 62, 55, 112};

    private int targetCount = 1;
    private int roundTargetCount = 1;
    private String languageTag = "ko";
    private Context languageContext;
    private Phase phase = Phase.EMPTY;
    private long stableSince = 0L;
    private long countdownSince = 0L;
    private int controlPointerId = -1;
    private int pendingControl = 0;
    private float centerX;
    private float centerY;

    public NeonLotteryView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        languageContext = context;
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(true);
        setFocusable(true);
        updateAccessibilityDescription();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setLanguage(String languageTag, Context languageContext) {
        this.languageTag = languageTag;
        this.languageContext = languageContext;
        updateAccessibilityDescription();
        invalidate();
    }

    private String text(int stringId) {
        return languageContext.getString(stringId);
    }

    private String text(int stringId, Object... formatArgs) {
        return languageContext.getString(stringId, formatArgs);
    }

    public void setTargetCount(int count) {
        int next = Math.max(1, Math.min(20, count));
        if (next == targetCount) return;
        targetCount = next;
        if (phase == Phase.COUNTDOWN || phase == Phase.DONE) {
            invalidate();
            return;
        }
        onFingerCountChanged();
        invalidate();
    }

    private float dp(float value) {
        return value * density;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BG);
        float w = getWidth();
        float h = getHeight();
        float top = getPaddingTop();
        float bottom = h - getPaddingBottom();
        if (bottom <= top) bottom = h;
        long now = SystemClock.elapsedRealtime();

        updatePhase(now);
        drawAtmosphere(canvas, w, top, bottom);
        drawHeader(canvas, w, top, now);

        float boardTop = top + dp(116);
        float boardBottom = bottom - dp(126);
        if (boardBottom < boardTop + dp(160)) {
            boardTop = top + dp(92);
            boardBottom = bottom - dp(105);
        }
        centerX = w * 0.5f;
        centerY = (boardTop + boardBottom) * 0.5f;

        drawBoardHalo(canvas, centerX, centerY, w, boardBottom - boardTop);
        Iterable<Finger> visibleFingers = (phase == Phase.COUNTDOWN || phase == Phase.DONE)
                ? frozenFingers : fingers.values();
        for (Finger finger : visibleFingers) drawFinger(canvas, finger, now);
        drawPhaseMessage(canvas, now);
        drawFooter(canvas, w, bottom);

        if (!fingers.isEmpty()) postInvalidateOnAnimation();
    }

    private void drawAtmosphere(Canvas canvas, float w, float top, float bottom) {
        // Keep the screen edges uniform; decoration should not create a visible rim.
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BG);
        canvas.drawColor(BG);
    }
    private void drawHeader(Canvas canvas, float w, float top, long now) {
        float left = dp(24);
        float logoX = left + dp(17);
        float logoY = top + dp(29);

        paint.setShader(new RadialGradient(logoX, logoY, dp(22),
                new int[]{0x5536EED0, 0x1436EED0, 0x0036EED0}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(logoX, logoY, dp(22), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(0xFF51EAC7);
        canvas.drawCircle(logoX, logoY, dp(13), paint);
        paint.setColor(0xFF8275FF);
        canvas.drawCircle(logoX, logoY, dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(logoX, logoY, dp(2.8f), paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(dp(14));
        paint.setColor(WHITE);
        canvas.drawText("FINGER LOTTERY", left + dp(43), top + dp(27), paint);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(dp(9));
        paint.setColor(0xFF747D8D);
        canvas.drawText("LET THE FINGERTIPS DECIDE", left + dp(43), top + dp(43), paint);

        float languageLeft = w - dp(119);
        float languageRight = w - dp(55);
        rect.set(languageLeft, top + dp(15), languageRight, top + dp(43));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF171D27);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xFF303846);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(dp(10));
        paint.setColor(0xFF9AFBDE);
        canvas.drawText(AppLanguage.find(languageTag).code + "  ▾",
                (languageLeft + languageRight) * 0.5f, top + dp(33), paint);

        float helpX = w - dp(25);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xFF303846);
        canvas.drawCircle(helpX, top + dp(29), dp(15), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(dp(15));
        paint.setColor(0xFFBAC2CF);
        canvas.drawText("?", helpX, top + dp(34), paint);

        float statusTop = top + dp(66);
        String status;
        int statusColor;
        if (phase == Phase.DONE) {
            status = "RESULT IS IN";
            statusColor = 0xFFFFC857;
        } else if (phase == Phase.COUNTDOWN) {
            status = "POSITIONS LOCKED";
            statusColor = 0xFF9AFBDE;
        } else if (fingers.isEmpty()) {
            status = "READY TO DRAW";
            statusColor = 0xFF9AFBDE;
        } else {
            status = "FINGERS ON SCREEN";
            statusColor = 0xFF83A6FF;
        }
        float chipWidth = dp(140);
        rect.set(left, statusTop, left + chipWidth, statusTop + dp(26));
        paint.setColor(0xFF111720);
        canvas.drawRoundRect(rect, dp(13), dp(13), paint);
        paint.setColor(statusColor);
        canvas.drawCircle(left + dp(11), statusTop + dp(13), dp(3), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(9));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(0xFFABB4C1);
        canvas.drawText(status, left + dp(21), statusTop + dp(16), paint);

        int visibleCount = (phase == Phase.COUNTDOWN || phase == Phase.DONE)
                ? frozenFingers.size() : fingers.size();
        if (visibleCount > 0) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setColor(0xFF929AAA);
            canvas.drawText(String.format(java.util.Locale.KOREA, "%02d / 20", visibleCount),
                    w - dp(24), statusTop + dp(17), paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBoardHalo(Canvas canvas, float x, float y, float w, float h) {
        float radius = Math.min(Math.min(w * 0.43f, h * 0.40f), dp(205));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x0C9AFBDE);
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(0x089AFBDE);
        canvas.drawCircle(x, y, radius * 0.72f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFinger(Canvas canvas, Finger finger, long now) {
        float x = finger.x;
        float y = finger.y;
        Integer rank = selectedRanks.get(finger.pointerId);
        float breath = 0.5f + 0.5f * (float) Math.sin((now - finger.bornAt) / 460.0);
        float baseRadius = dp(31);

        paint.setShader(new RadialGradient(x, y, dp(rank == null ? 68 : 94),
                new int[]{withAlpha(finger.color, rank == null ? 100 : 205),
                        withAlpha(finger.color, rank == null ? 36 : 82), 0x00000000},
                new float[]{0f, 0.47f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, dp(rank == null ? 68 : 94), paint);
        paint.setShader(null);

        if (rank == null) {
            float elapsed = (now - finger.bornAt) % 1680L;
            for (int i = 0; i < 3; i++) {
                float progress = ((elapsed / 1680f) + i / 3f) % 1f;
                float radius = baseRadius + progress * dp(58);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.7f));
                paint.setColor(withAlpha(finger.color, Math.round(180 * (1f - progress))));
                canvas.drawCircle(x, y, radius, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(finger.color, 88));
            canvas.drawCircle(x, y, baseRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.2f));
            paint.setColor(withAlpha(finger.color, 220));
            canvas.drawCircle(x, y, baseRadius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(WHITE);
            canvas.drawCircle(x, y, dp(3.1f + breath * 1.4f), paint);
            return;
        }

        float selectedRadius = dp(43 + breath * 3f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.8f));
        paint.setColor(withAlpha(finger.color, 205));
        canvas.drawCircle(x, y, selectedRadius + dp(7 + breath * 2f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(finger.color, 135));
        canvas.drawCircle(x, y, selectedRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.8f));
        paint.setColor(withAlpha(finger.color, 255));
        canvas.drawCircle(x, y, selectedRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(withAlpha(WHITE, 205));
        canvas.drawCircle(x, y, selectedRadius - dp(5), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(24));
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        paint.setColor(Color.WHITE);
        canvas.drawText(String.valueOf(rank), x, y + dp(7), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPhaseMessage(Canvas canvas, long now) {
        float titleY = centerY;
        if (phase == Phase.EMPTY) {
            drawSoftOrb(canvas, centerX, centerY - dp(38));
            drawCentered(canvas, text(R.string.prompt_empty),
                    centerX, titleY + dp(30), 15, WHITE, true);
            return;
        }
        if (phase == Phase.NEED_MORE) {
            String prompt = targetCount == 1
                    ? text(R.string.prompt_need_one)
                    : text(R.string.prompt_need_many, targetCount);
            String count = text(R.string.prompt_need_detail, fingers.size());
            drawCentered(canvas, prompt, centerX, titleY, 17, WHITE, true);
            drawCentered(canvas, count, centerX, titleY + dp(25), 12, MUTED, false);
            return;
        }
        if (phase == Phase.WAITING) {
            float progress = Math.max(0f, Math.min(1f, (now - stableSince) / 3000f));
            float r = dp(28);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0x4235DAB9);
            canvas.drawCircle(centerX, titleY - dp(13), r, paint);
            paint.setStrokeWidth(dp(2.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFF62EACD);
            rect.set(centerX - r, titleY - dp(13) - r, centerX + r, titleY - dp(13) + r);
            canvas.drawArc(rect, -90, 360 * progress, false, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
            drawCentered(canvas, text(R.string.hold),
                    centerX, titleY + dp(37), 14, WHITE, true);
            drawCentered(canvas, text(R.string.checking),
                    centerX, titleY + dp(57), 11, MUTED, false);
            return;
        }
        if (phase == Phase.COUNTDOWN) {
            int step = Math.min(3, Math.max(1, 3 - (int) ((now - countdownSince) / 1000)));
            paint.setShader(new RadialGradient(centerX, titleY, dp(106),
                    new int[]{0x2836E4C1, 0x1236E4C1, 0x0036E4C1}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(centerX, titleY, dp(106), paint);
            paint.setShader(null);
            drawCentered(canvas, String.valueOf(step), centerX, titleY + dp(38), 94, MINT, true);
            return;
        }
        if (phase == Phase.DONE) {
            drawCentered(canvas, text(R.string.selection_complete),
                    centerX, titleY + dp(60), 30, 0xFFFFD86A, true);
        }
    }

    private void drawSoftOrb(Canvas canvas, float x, float y) {
        float r = dp(31);
        paint.setShader(new RadialGradient(x, y, r * 1.8f,
                new int[]{0x5540EAC8, 0x143B9FE0, 0x003B9FE0}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, r * 1.8f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(0x8C62EACD);
        canvas.drawCircle(x, y, r * 0.86f, paint);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x5D8B7BFF);
        canvas.drawCircle(x, y, r * 0.58f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFB9FFF0);
        canvas.drawCircle(x, y, dp(3), paint);
    }

    private void drawFooter(Canvas canvas, float w, float bottom) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(dp(10));
        paint.setColor(0xFF798292);
        if (phase == Phase.DONE) {
            float buttonWidth = dp(154);
            rect.set(w * 0.5f - buttonWidth * 0.5f, bottom - dp(137),
                    w * 0.5f + buttonWidth * 0.5f, bottom - dp(97));
            paint.setShader(new RadialGradient(w * 0.5f, rect.centerY(), buttonWidth * 0.68f,
                    new int[]{0x2439E9C7, 0x0039E9C7}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, dp(20), dp(20), paint);
            paint.setShader(null);
            paint.setColor(0xFF15231F);
            canvas.drawRoundRect(rect, dp(20), dp(20), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xFF347C6C);
            canvas.drawRoundRect(rect, dp(20), dp(20), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(dp(13));
            paint.setColor(MINT);
            canvas.drawText(text(R.string.restart), w * 0.5f, rect.centerY() + dp(5), paint);
        } else if (phase == Phase.COUNTDOWN) {
            canvas.drawText(text(R.string.please_wait), w * 0.5f, bottom - dp(99), paint);
        }

        float left = dp(20);
        float right = w - dp(20);
        float cardTop = bottom - dp(84);
        rect.set(left, cardTop, right, bottom - dp(8));
        paint.setColor(SURFACE);
        canvas.drawRoundRect(rect, dp(20), dp(20), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xFF252C38);
        canvas.drawRoundRect(rect, dp(20), dp(20), paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(18));
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setColor(WHITE);
        canvas.drawText("SELECT WINNERS", left + dp(18), cardTop + dp(44), paint);
        float buttonWidth = dp(76);
        rect.set(right - buttonWidth - dp(10), cardTop + dp(10), right - dp(10), bottom - dp(18));
        paint.setColor(0xFF202933);
        canvas.drawRoundRect(rect, dp(14), dp(14), paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(22));
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setColor(MINT);
        canvas.drawText(String.format(java.util.Locale.KOREA, "%02d", targetCount),
                rect.centerX() - dp(4), rect.centerY() + dp(8), paint);
        paint.setTextSize(dp(13));
        paint.setColor(0xFF8D98A8);
        canvas.drawText("›", rect.right - dp(13), rect.centerY() + dp(5), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCentered(Canvas canvas, String text, float x, float baseline, float sizeDp, int color, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(sizeDp));
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setColor(color);
        canvas.drawText(text, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void updatePhase(long now) {
        if (fingers.isEmpty()) return;
        if (phase == Phase.NEED_MORE || phase == Phase.EMPTY) return;
        if (phase == Phase.WAITING && now - stableSince >= 3000L) {
            phase = Phase.COUNTDOWN;
            countdownSince = stableSince + 3000L;
            roundTargetCount = targetCount;
            frozenFingers.clear();
            for (Finger finger : fingers.values()) {
                frozenFingers.add(new Finger(finger.pointerId, finger.x, finger.y, finger.color, finger.bornAt));
            }
            updateAccessibilityDescription();
            announceForAccessibility(text(R.string.access_countdown));
        }
        if (phase == Phase.COUNTDOWN && now - countdownSince >= 3000L) {
            chooseWinners();
        }
    }

    private void chooseWinners() {
        if (frozenFingers.size() < roundTargetCount) {
            phase = Phase.NEED_MORE;
            return;
        }
        List<Finger> candidates = new ArrayList<>(frozenFingers);
        Collections.shuffle(candidates, random);
        selectedRanks.clear();
        for (int i = 0; i < roundTargetCount; i++) selectedRanks.put(candidates.get(i).pointerId, i + 1);
        phase = Phase.DONE;
        updateAccessibilityDescription();
        performHaptics();
        announceForAccessibility(text(R.string.selected_count, roundTargetCount));
    }

    private void performHaptics() {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) getContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = manager == null ? null : manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(hapticPattern, -1));
            } else {
                vibrator.vibrate(hapticPattern, -1);
            }
        } catch (SecurityException ignored) {
            // Haptic feedback remains available even if system vibration is disabled.
        }
    }

    private int chooseColor() {
        Set<Integer> used = new HashSet<>();
        for (Finger finger : fingers.values()) used.add(finger.color);
        List<Integer> available = new ArrayList<>();
        for (int color : NEON) if (!used.contains(color)) available.add(color);
        if (available.isEmpty()) return NEON[random.nextInt(NEON.length)];
        return available.get(random.nextInt(available.size()));
    }

    private void addFinger(int pointerId, float x, float y) {
        if (fingers.size() >= NEON.length || fingers.containsKey(pointerId)) return;
        fingers.put(pointerId, new Finger(pointerId, x, y, chooseColor(), SystemClock.elapsedRealtime()));
        onFingerCountChanged();
    }

    private void onFingerCountChanged() {
        selectedRanks.clear();
        frozenFingers.clear();
        if (fingers.isEmpty()) {
            phase = Phase.EMPTY;
            stableSince = 0L;
        } else if (fingers.size() < targetCount) {
            phase = Phase.NEED_MORE;
            stableSince = 0L;
        } else {
            phase = Phase.WAITING;
            stableSince = SystemClock.elapsedRealtime();
        }
        invalidate();
        if (!fingers.isEmpty()) postInvalidateOnAnimation();
        updateAccessibilityDescription();
        if (phase == Phase.NEED_MORE) {
            announceForAccessibility(text(R.string.access_need_more, targetCount, fingers.size()));
        } else if (phase == Phase.WAITING) {
            announceForAccessibility(text(R.string.access_waiting, fingers.size()));
        }
    }

    private void restartRound() {
        fingers.clear();
        frozenFingers.clear();
        selectedRanks.clear();
        phase = Phase.EMPTY;
        stableSince = 0L;
        countdownSince = 0L;
        updateAccessibilityDescription();
        announceForAccessibility(text(R.string.prompt_empty));
        invalidate();
    }

    private boolean isCountControl(float x, float y) {
        float bottom = getHeight() - getPaddingBottom();
        return y >= bottom - dp(92) && y <= bottom - dp(4)
                && x >= dp(12) && x <= getWidth() - dp(12);
    }

    private boolean isHelpControl(float x, float y) {
        return x >= getWidth() - dp(43) && x <= getWidth() - dp(7)
                && y >= getPaddingTop() + dp(10) && y <= getPaddingTop() + dp(48);
    }

    private boolean isLanguageControl(float x, float y) {
        return x >= getWidth() - dp(119) && x <= getWidth() - dp(55)
                && y >= getPaddingTop() + dp(15) && y <= getPaddingTop() + dp(43);
    }

    private boolean isRestartControl(float x, float y) {
        float bottom = getHeight() - getPaddingBottom();
        return phase == Phase.DONE && y >= bottom - dp(141) && y <= bottom - dp(93)
                && x >= getWidth() * 0.5f - dp(92) && x <= getWidth() * 0.5f + dp(92);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float x = event.getX(index);
        float y = event.getY(index);

        if (action == MotionEvent.ACTION_DOWN) {
            if (isLanguageControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 4;
                return true;
            }
            if (isHelpControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 2;
                return true;
            }
            if (isRestartControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 3;
                return true;
            }
            if (isCountControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 1;
                return true;
            }
            addFinger(pointerId, x, y);
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN && controlPointerId == -1) {
            if (isLanguageControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 4;
                return true;
            }
            if (isHelpControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 2;
                return true;
            }
            if (isRestartControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 3;
                return true;
            }
            if (isCountControl(x, y)) {
                controlPointerId = pointerId;
                pendingControl = 1;
                return true;
            }
        }

        if (controlPointerId != -1) {
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    && pointerId == controlPointerId) {
                int control = pendingControl;
                controlPointerId = -1;
                pendingControl = 0;
                if (control == 1 && isCountControl(x, y)) {
                    if (phase == Phase.DONE) {
                        super.performClick();
                        listener.onEditCountRequested();
                    } else {
                        performClick();
                    }
                } else if (control == 2 && isHelpControl(x, y)) {
                    super.performClick();
                    listener.onHelpRequested();
                } else if (control == 3 && isRestartControl(x, y)) {
                    performClick();
                } else if (control == 4 && isLanguageControl(x, y)) {
                    super.performClick();
                    listener.onLanguageRequested();
                }
            }
            return true;
        }

        if (phase == Phase.COUNTDOWN || phase == Phase.DONE) return true;

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            addFinger(pointerId, x, y);
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                Finger finger = fingers.get(event.getPointerId(i));
                if (finger != null) {
                    finger.x = event.getX(i);
                    finger.y = event.getY(i);
                }
            }
            invalidate();
        } else if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            if (fingers.remove(pointerId) != null) onFingerCountChanged();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            if (!fingers.isEmpty()) {
                fingers.clear();
                onFingerCountChanged();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (phase == Phase.DONE) restartRound();
        else listener.onEditCountRequested();
        return true;
    }

    private void updateAccessibilityDescription() {
        int descriptionId;
        String description;
        if (phase == Phase.NEED_MORE) {
            description = text(R.string.access_need_more, targetCount, fingers.size());
        } else if (phase == Phase.WAITING) {
            description = text(R.string.access_waiting, fingers.size());
        } else if (phase == Phase.COUNTDOWN) {
            descriptionId = R.string.access_countdown;
            description = text(descriptionId);
        } else if (phase == Phase.DONE) {
            descriptionId = R.string.access_done;
            description = text(descriptionId);
        } else {
            descriptionId = R.string.access_home;
            description = text(descriptionId);
        }
        setContentDescription(description + " " + text(R.string.language_accessibility));
    }
}
