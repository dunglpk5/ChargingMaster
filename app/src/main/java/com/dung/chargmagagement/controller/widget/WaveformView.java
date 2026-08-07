package com.dung.chargmagagement.controller.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

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

    /** Vị trí gai theo tỉ lệ bề ngang. */
    private static final float SPIKE_CENTER_RATIO = 0.5f;

    /** Bề rộng của gai theo tỉ lệ bề ngang. */
    private static final float SPIKE_WIDTH_RATIO = 0.08f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float dotRadius;

    /** Biên độ gai, 0..1 so với nửa chiều cao. */
    private float amplitude = 0.8f;

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

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        final float centerY = height / 2f;
        final float peak = (height / 2f - dotRadius) * amplitude;

        final float spikeCenter = width * SPIKE_CENTER_RATIO;
        final float spikeHalf = width * SPIKE_WIDTH_RATIO / 2f;

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
