package com.dung.chargmagagement.controller.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;

/**
 * Hình sóng điện trong hộp thoại kiểm tra nguồn điện.
 *
 * <p>Một đường nằm ngang có đúng một gai nhọn ở giữa, kết thúc bằng chấm sáng ở
 * mép phải – mô phỏng dạng sóng trên máy đo. Biên độ gai phản ánh mức chênh lệch
 * giữa công suất định mức và công suất thực tế: nguồn càng yếu so với định mức
 * thì gai càng sâu.
 */
public class WaveformView extends View {

    /** Vị trí gai lúc đứng yên, theo tỉ lệ bề ngang. */
    private static final float SPIKE_CENTER_RATIO = 0.5f;

    /** Bề rộng của gai theo tỉ lệ bề ngang. */
    private static final float SPIKE_WIDTH_RATIO = 0.08f;

    /** Thời gian gai chạy hết một lượt từ trái sang phải (ms). */
    private static final long SWEEP_DURATION_MS = 1_800L;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float dotRadius;

    /** Biên độ gai, 0..1 so với nửa chiều cao. */
    private float amplitude = 0.8f;

    /** Vị trí gai hiện tại theo tỉ lệ bề ngang. */
    private float spikeRatio = SPIKE_CENTER_RATIO;

    private boolean animating;

    @Nullable
    private ValueAnimator sweepAnimator;

    public WaveformView(@NonNull Context context) {
        this(context, null);
    }

    public WaveformView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveformView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        dotRadius = 4f * density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStrokeJoin(Paint.Join.MITER);
        linePaint.setColor(ContextCompat.getColor(context, R.color.wave_line));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.green_accent));
    }

    /**
     * Đặt biên độ gai.
     *
     * @param value 0..1; ngoài khoảng sẽ bị kẹp lại
     */
    public void setAmplitude(float value) {
        final float clamped = Math.max(0.1f, Math.min(1f, value));
        if (Math.abs(clamped - amplitude) < 0.01f) return;

        amplitude = clamped;
        invalidate();
    }

    /**
     * Bật/tắt hiệu ứng gai chạy ngang, mô phỏng đầu dò của máy đo đang quét.
     *
     * <p>Hộp thoại kiểm tra nguồn điện hiện ra rồi đứng im hoàn toàn thì người dùng
     * không biết app đang làm việc hay đã treo. Gai chạy đều là dấu hiệu rẻ nhất
     * cho biết màn hình còn sống.
     */
    public void setAnimating(boolean value) {
        if (animating == value) return;

        animating = value;
        if (value) {
            startSweep();
        } else {
            stopSweep();
            spikeRatio = SPIKE_CENTER_RATIO;
            invalidate();
        }
    }

    private void startSweep() {
        stopSweep();
        if (!isAttachedToWindow()) return; // sẽ chạy lại ở onAttachedToWindow

        sweepAnimator = ValueAnimator.ofFloat(0f, 1f);
        sweepAnimator.setDuration(SWEEP_DURATION_MS);
        sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnimator.setInterpolator(new LinearInterpolator());
        sweepAnimator.addUpdateListener(a -> {
            spikeRatio = (float) a.getAnimatedValue();
            invalidate();
        });
        sweepAnimator.start();
    }

    private void stopSweep() {
        if (sweepAnimator != null) {
            sweepAnimator.cancel();
            sweepAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animating) startSweep();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSweep();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        final float centerY = height / 2f;
        final float peak = (height / 2f - dotRadius) * amplitude;

        final float spikeHalf = width * SPIKE_WIDTH_RATIO / 2f;
        // Giữ gai nằm trọn trong khung: chạy sát mép thì nửa gai bị cắt cụt
        final float spikeCenter = spikeHalf
                + spikeRatio * (width - dotRadius - spikeHalf * 2f);

        path.reset();
        path.moveTo(0f, centerY);
        path.lineTo(spikeCenter - spikeHalf, centerY);
        // Gai lên rồi cắm xuống, giống dạng xung trên máy đo
        path.lineTo(spikeCenter - spikeHalf / 2f, centerY - peak);
        path.lineTo(spikeCenter + spikeHalf / 2f, centerY + peak);
        path.lineTo(spikeCenter + spikeHalf, centerY);
        path.lineTo(width - dotRadius, centerY);

        canvas.drawPath(path, linePaint);
        canvas.drawCircle(width - dotRadius, centerY, dotRadius, dotPaint);
    }
}
