package com.dung.chargmagagement.controller.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;

/**
 * Vòng tròn chứa "chất lỏng" dâng theo mức pin, mặt nước gợn sóng liên tục.
 *
 * <p>Sóng chuyển động là dấu hiệu duy nhất cho biết màn hình còn sống trong lúc
 * sạc – mọi con số khác gần như đứng yên hàng phút liền.
 *
 * <p>Cắt phần chất lỏng theo hình tròn bằng {@code clipPath} thay vì vẽ cung: mặt
 * nước là đường cong bất kỳ nên không có cách nào biểu diễn nó bằng một cung tròn.
 *
 * <p>Toàn bộ Paint và Path được tạo sẵn trong hàm dựng. Cấp phát trong
 * {@code onDraw} là lỗi kinh điển: hàm này chạy 60 lần mỗi giây, sinh rác liên tục
 * và làm khung hình giật mỗi khi bộ dọn rác chạy.
 */
public class LiquidBatteryView extends View {

    /** Thời gian sóng chạy hết một chu kỳ (ms). */
    private static final long WAVE_DURATION_MS = 2_600L;

    /** Chiều cao sóng so với bán kính. */
    private static final float WAVE_HEIGHT_RATIO = 0.035f;

    /** Số bước vẽ mặt nước; càng nhiều càng mượt nhưng càng tốn. */
    private static final int WAVE_STEPS = 60;

    /** Mực nước dâng mượt tới giá trị mới trong bấy nhiêu ms. */
    private static final long LEVEL_ANIM_MS = 700L;

    /** Số vạch trên vòng ngoài. Dày như vậy để nhìn ra một vòng liền chứ không rời rạc. */
    private static final int TICK_COUNT = 72;

    /** Độ dài vạch và khe hở tới vòng tròn, tính theo tỉ lệ bán kính ngoài. */
    private static final float TICK_LENGTH_RATIO = 0.14f;
    private static final float TICK_GAP_RATIO = 0.06f;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickInactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final Path clipPath = new Path();
    private final RectF bounds = new RectF();

    /** Mức nước đang vẽ và mức đích, 0..1. */
    private float level;
    private float targetLevel;

    /** Pha sóng 0..1. */
    private float wavePhase;

    /** Có gợn sóng hay để mặt nước phẳng. */
    private boolean waveEnabled = true;

    /** Có vẽ vòng vạch chỉ mức pin ở ngoài hay không. */
    private boolean tickRingEnabled;

    @Nullable
    private ValueAnimator waveAnimator;

    @Nullable
    private ValueAnimator levelAnimator;

    public LiquidBatteryView(@NonNull Context context) {
        this(context, null);
    }

    public LiquidBatteryView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidBatteryView(@NonNull Context context, @Nullable AttributeSet attrs, int style) {
        super(context, attrs, style);

        final float density = context.getResources().getDisplayMetrics().density;

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.5f * density);
        ringPaint.setColor(ContextCompat.getColor(context, R.color.liquid_ring));

        liquidPaint.setStyle(Paint.Style.FILL);
        liquidPaint.setColor(ContextCompat.getColor(context, R.color.liquid_fill));

        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(Color.TRANSPARENT);

        tickActivePaint.setStyle(Paint.Style.STROKE);
        tickActivePaint.setStrokeWidth(2f * density);
        tickInactivePaint.setStyle(Paint.Style.STROKE);
        tickInactivePaint.setStrokeWidth(2f * density);
    }

    /**
     * Bật vòng vạch bao ngoài, chạy như một thanh tiến trình vòng.
     *
     * <p>Số vạch được tô đậm tỉ lệ với mức pin, chạy theo chiều kim đồng hồ từ đỉnh.
     * Vòng này đọc được từ xa hơn hẳn con số bên trong: liếc một cái là ước lượng
     * được mức pin mà không cần đọc chữ.
     *
     * <p>Khi bật, vòng tròn chất lỏng bên trong tự thu nhỏ lại để nhường chỗ.
     *
     * @param activeColor   màu vạch đã đạt tới
     * @param inactiveColor màu vạch còn lại
     */
    public void setTickRing(int activeColor, int inactiveColor) {
        tickRingEnabled = true;
        tickActivePaint.setColor(activeColor);
        tickInactivePaint.setColor(inactiveColor);
        invalidate();
    }

    /**
     * Đổi bảng màu để dùng lại view này ở nhiều màn.
     *
     * @param circleColor màu nền cả vòng tròn; {@link Color#TRANSPARENT} thì không vẽ
     * @param fillColor   màu phần chất lỏng
     * @param ringColor   màu viền; {@link Color#TRANSPARENT} thì không vẽ
     */
    public void setPalette(int circleColor, int fillColor, int ringColor) {
        circlePaint.setColor(circleColor);
        liquidPaint.setColor(fillColor);
        ringPaint.setColor(ringColor);
        invalidate();
    }

    /**
     * Bật/tắt sóng gợn trên mặt nước.
     *
     * <p>Tắt ở những màn chỉ hiển thị mức pin tĩnh: sóng vẽ lại 60 lần mỗi giây, ở
     * màn nào cũng bật thì chính app tiết kiệm pin lại là thứ ngốn pin.
     */
    public void setWaveEnabled(boolean enabled) {
        if (waveEnabled == enabled) return;

        waveEnabled = enabled;
        if (enabled) {
            startWave();
        } else {
            stopWave();
            wavePhase = 0f;
            invalidate();
        }
    }

    /**
     * Đặt mức pin.
     *
     * @param percent 0..100
     */
    public void setPercent(int percent) {
        final float value = Math.max(0f, Math.min(1f, percent / 100f));
        if (Math.abs(value - targetLevel) < 0.001f) return;

        targetLevel = value;
        animateLevel(value);
    }

    private void animateLevel(float value) {
        if (levelAnimator != null) levelAnimator.cancel();

        if (!isAttachedToWindow()) {
            level = value;
            invalidate();
            return;
        }

        levelAnimator = ValueAnimator.ofFloat(level, value);
        levelAnimator.setDuration(LEVEL_ANIM_MS);
        levelAnimator.addUpdateListener(a -> {
            level = (float) a.getAnimatedValue();
            invalidate();
        });
        levelAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startWave();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimators();
        super.onDetachedFromWindow();
    }

    /**
     * Dừng sóng khi view bị che khuất. Vẽ lại 60 lần mỗi giây cho một hình không
     * ai nhìn thấy là kiểu hao pin khó chịu nhất trong một app về tiết kiệm pin.
     */
    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startWave();
        } else {
            stopWave();
        }
    }

    private void startWave() {
        if (!waveEnabled || waveAnimator != null || !isAttachedToWindow()) return;

        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(WAVE_DURATION_MS);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(a -> {
            wavePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        waveAnimator.start();
    }

    private void stopWave() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
    }

    private void stopAnimators() {
        stopWave();
        if (levelAnimator != null) {
            levelAnimator.cancel();
            levelAnimator = null;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        final float stroke = ringPaint.getStrokeWidth();
        final float outerRadius = size / 2f - stroke;
        final float centerX = getWidth() / 2f;
        final float centerY = getHeight() / 2f;

        // Vòng vạch chiếm phần vành ngoài, vòng tròn chất lỏng lùi vào trong
        final float tickLength = tickRingEnabled ? outerRadius * TICK_LENGTH_RATIO : 0f;
        final float gap = tickRingEnabled ? outerRadius * TICK_GAP_RATIO : 0f;
        final float radius = outerRadius - tickLength - gap;

        if (tickRingEnabled) {
            drawTickRing(canvas, centerX, centerY, outerRadius, tickLength);
        }

        bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        if (circlePaint.getColor() != Color.TRANSPARENT) {
            canvas.drawCircle(centerX, centerY, radius, circlePaint);
        }
        drawLiquid(canvas, centerX, centerY, radius);
        if (ringPaint.getColor() != Color.TRANSPARENT) {
            canvas.drawCircle(centerX, centerY, radius, ringPaint);
        }
    }

    /**
     * Vòng vạch chỉ mức pin.
     *
     * <p>Bắt đầu từ đỉnh và chạy theo chiều kim đồng hồ, nên góc gốc là -90 độ chứ
     * không phải 0 – hệ toạ độ của Canvas lấy mốc 0 độ ở phía bên phải.
     */
    private void drawTickRing(@NonNull Canvas canvas, float centerX, float centerY,
                              float outerRadius, float tickLength) {
        final float innerRadius = outerRadius - tickLength;
        final int activeCount = Math.round(TICK_COUNT * level);

        for (int i = 0; i < TICK_COUNT; i++) {
            final double angle = Math.toRadians(-90.0 + i * 360.0 / TICK_COUNT);
            final float cos = (float) Math.cos(angle);
            final float sin = (float) Math.sin(angle);

            canvas.drawLine(
                    centerX + innerRadius * cos, centerY + innerRadius * sin,
                    centerX + outerRadius * cos, centerY + outerRadius * sin,
                    i < activeCount ? tickActivePaint : tickInactivePaint);
        }
    }

    private void drawLiquid(@NonNull Canvas canvas, float centerX, float centerY, float radius) {
        if (level <= 0f) return;

        // Mặt nước: level = 0 nằm ở đáy vòng tròn, level = 1 nằm ở đỉnh
        final float surfaceY = centerY + radius - level * radius * 2f;
        final float waveHeight = radius * WAVE_HEIGHT_RATIO;
        final float left = centerX - radius;
        final float width = radius * 2f;

        wavePath.reset();
        wavePath.moveTo(left, surfaceY);

        if (waveEnabled) {
            for (int i = 0; i <= WAVE_STEPS; i++) {
                final float x = left + width * i / (float) WAVE_STEPS;
                // Hai sóng sin lệch tần số chồng lên nhau: một sóng đơn trông quá
                // đều và lộ ngay là đồ hoạ máy tính
                final double angle = 2 * Math.PI * (i / (float) WAVE_STEPS + wavePhase);
                final float y = surfaceY
                        + (float) (Math.sin(angle) * waveHeight)
                        + (float) (Math.sin(angle * 2.3 + 1.1) * waveHeight * 0.45);
                wavePath.lineTo(x, y);
            }
        } else {
            wavePath.lineTo(centerX + radius, surfaceY);
        }

        wavePath.lineTo(centerX + radius, centerY + radius);
        wavePath.lineTo(left, centerY + radius);
        wavePath.close();

        clipPath.reset();
        clipPath.addOval(bounds, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawPath(wavePath, liquidPaint);
        canvas.restore();
    }
}
