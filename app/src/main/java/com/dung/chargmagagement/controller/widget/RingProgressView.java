package com.dung.chargmagagement.controller.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;

/**
 * Vòng tròn tiến trình cho ba thẻ chỉ số ở đầu tab Công cụ.
 *
 * <p>Một vòng nền mờ và một cung màu chạy từ đỉnh theo chiều kim đồng hồ. Con số
 * phần trăm là TextView đặt đè lên trong layout chứ không vẽ ở đây, để chữ vẫn theo
 * cỡ chữ hệ thống và đổi màu được mà không cần đụng vào view này.
 *
 * <p>Mọi Paint và RectF đều tạo sẵn trong hàm dựng – cấp phát trong {@code onDraw}
 * sinh rác mỗi khung hình và làm giật lúc bộ dọn rác chạy.
 */
public class RingProgressView extends View {

    /** Cung bắt đầu từ đỉnh; hệ toạ độ Canvas lấy mốc 0 độ ở bên phải. */
    private static final float START_ANGLE = -90f;

    /** Bề dày vòng theo tỉ lệ bán kính. */
    private static final float STROKE_RATIO = 0.13f;

    private static final long ANIM_DURATION_MS = 600L;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    /** Tỉ lệ đang vẽ và tỉ lệ đích, 0..1. */
    private float progress;
    private float targetProgress;

    @Nullable
    private ValueAnimator animator;

    public RingProgressView(@NonNull Context context) {
        this(context, null);
    }

    public RingProgressView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RingProgressView(@NonNull Context context, @Nullable AttributeSet attrs, int style) {
        super(context, attrs, style);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.ring_track));

        progressPaint.setStyle(Paint.Style.STROKE);
        // Bo tròn đầu cung cho khớp với các thẻ bo góc xung quanh
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(context, R.color.teal_primary));
    }

    /** Màu cung tiến trình; vòng nền giữ nguyên màu xám nhạt. */
    public void setProgressColor(int color) {
        if (progressPaint.getColor() == color) return;
        progressPaint.setColor(color);
        invalidate();
    }

    /**
     * Đặt mức tiến trình, chạy mượt tới giá trị mới.
     *
     * @param percent 0..100; ngoài khoảng sẽ bị kẹp lại
     */
    public void setPercent(int percent) {
        final float value = Math.max(0f, Math.min(1f, percent / 100f));
        if (Math.abs(value - targetProgress) < 0.001f) return;

        targetProgress = value;
        animateTo(value);
    }

    private void animateTo(float value) {
        if (animator != null) animator.cancel();

        if (!isAttachedToWindow()) {
            progress = value;
            invalidate();
            return;
        }

        animator = ValueAnimator.ofFloat(progress, value);
        animator.setDuration(ANIM_DURATION_MS);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        final float stroke = size * STROKE_RATIO;
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);

        // Lùi vào nửa bề dày nét, nếu không cung bị cắt mất một nửa ở mép view
        final float inset = stroke / 2f;
        final float left = (getWidth() - size) / 2f + inset;
        final float top = (getHeight() - size) / 2f + inset;
        arcBounds.set(left, top, left + size - stroke, top + size - stroke);

        canvas.drawOval(arcBounds, trackPaint);
        canvas.drawArc(arcBounds, START_ANGLE, 360f * progress, false, progressPaint);
    }
}
