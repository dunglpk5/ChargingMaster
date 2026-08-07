package com.dung.chargmagagement.controller.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;

/**
 * Vòng cung nét đứt chỉ tốc độ sạc, kèm chấm tròn đánh dấu vị trí hiện tại.
 *
 * <p>Vẽ nửa cung 180 độ từ trái sang phải: mép trái ứng với mức 0, mép phải ứng
 * với mức tối đa. Nội dung chữ ở giữa (tên tốc độ, dòng vào, dòng chờ) là các
 * TextView đặt đè lên trong layout, không vẽ ở đây – như vậy chữ vẫn dịch được
 * theo ngôn ngữ và tự xuống dòng khi cần.
 *
 * <p>Các nét đứt được vẽ thủ công bằng vòng lặp thay vì dùng {@code DashPathEffect}:
 * hiệu ứng nét đứt tính theo chiều dài đường nên độ dài mỗi vạch bị méo ở hai đầu
 * cung, còn vẽ theo góc thì các vạch cách đều nhau tuyệt đối.
 *
 * <p><b>Hoạt động như một thanh tiến trình:</b> các vạch nằm trước chấm đánh dấu
 * được tô màu đậm, phần còn lại để mờ – nhìn là biết ngay đang ở đâu trên thang đo.
 * Mỗi lần đổi giá trị, chấm và phần tô chạy mượt tới vị trí mới thay vì nhảy cóc:
 * dòng sạc dao động vài trăm mA giữa các lần đo là bình thường, để nhảy thẳng thì
 * đồng hồ giật liên tục và rất khó đọc.
 */
public class ArcGaugeView extends View {

    /** Cung nửa vòng tròn: bắt đầu ở 180 độ, quét 180 độ. */
    private static final float START_ANGLE = 180f;
    private static final float SWEEP_ANGLE = 180f;

    /** Số vạch trên cung. */
    private static final int TICK_COUNT = 36;

    /** Tỉ lệ độ dài vạch trên tổng khoảng cách giữa hai vạch. */
    private static final float TICK_FILL_RATIO = 0.55f;

    /** Thời gian chạy từ giá trị cũ sang giá trị mới (ms). */
    private static final long ANIM_DURATION_MS = 450L;

    /** Độ mờ của phần vạch chưa đạt tới. */
    private static final int INACTIVE_ALPHA = 90;

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float strokeWidth;
    private float dotRadius;

    /** Vị trí chấm đánh dấu đang được vẽ, 0..1. */
    private float progress;

    /** Vị trí đích mà chấm đang chạy tới. */
    private float targetProgress;

    @Nullable
    private ValueAnimator animator;

    public ArcGaugeView(@NonNull Context context) {
        this(context, null);
    }

    public ArcGaugeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArcGaugeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        strokeWidth = 5f * density;
        dotRadius = 4f * density;

        final int baseColor = ContextCompat.getColor(context, R.color.text_on_primary);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(strokeWidth);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(baseColor);
        tickPaint.setAlpha(INACTIVE_ALPHA);

        activeTickPaint.setStyle(Paint.Style.STROKE);
        activeTickPaint.setStrokeWidth(strokeWidth);
        activeTickPaint.setStrokeCap(Paint.Cap.ROUND);
        activeTickPaint.setColor(baseColor);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.state_warning));
    }

    /**
     * Đặt vị trí chấm đánh dấu, chạy mượt từ vị trí hiện tại tới đó.
     *
     * @param value giá trị 0..1; ngoài khoảng sẽ bị kẹp lại
     */
    public void setProgress(float value) {
        final float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - targetProgress) < 0.001f) return; // đã đang chạy tới đó

        targetProgress = clamped;
        animateTo(clamped);
    }

    private void animateTo(float value) {
        if (animator != null) animator.cancel();

        // Chưa gắn vào cửa sổ thì animation không chạy được, nhảy thẳng cho xong
        if (!isAttachedToWindow()) {
            progress = value;
            invalidate();
            return;
        }

        animator = ValueAnimator.ofFloat(progress, value);
        animator.setDuration(ANIM_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        // Animation còn chạy sau khi view bị gỡ là rò rỉ tài nguyên vô ích
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    /** Đổi màu chấm đánh dấu và phần vạch đã đạt tới, theo mức tốc độ sạc. */
    public void setDotColor(int color) {
        if (dotPaint.getColor() == color) return;
        dotPaint.setColor(color);
        activeTickPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float padding = strokeWidth / 2f + dotRadius;
        final float width = getWidth() - padding * 2f;
        if (width <= 0) return;

        // Bán kính lấy theo bề ngang; chiều cao chỉ cần bằng nửa đường kính
        final float radius = width / 2f;
        arcBounds.set(padding, padding, padding + width, padding + width);

        drawTicks(canvas);
        drawDot(canvas, radius, padding);
    }

    /**
     * Vẽ các vạch nét đứt cách đều nhau theo góc; vạch nằm trước chấm đánh dấu
     * được tô đậm để cả cung đọc được như một thanh tiến trình.
     */
    private void drawTicks(@NonNull Canvas canvas) {
        final float anglePerTick = SWEEP_ANGLE / TICK_COUNT;
        final float tickSweep = anglePerTick * TICK_FILL_RATIO;
        final int activeCount = Math.round(TICK_COUNT * progress);

        for (int i = 0; i < TICK_COUNT; i++) {
            final float startAngle = START_ANGLE + i * anglePerTick;
            canvas.drawArc(arcBounds, startAngle, tickSweep, false,
                    i < activeCount ? activeTickPaint : tickPaint);
        }
    }

    /** Chấm tròn nằm trên cung, tại vị trí ứng với progress. */
    private void drawDot(@NonNull Canvas canvas, float radius, float padding) {
        final double angleRad = Math.toRadians(START_ANGLE + SWEEP_ANGLE * progress);
        final float centerX = padding + radius;
        final float centerY = padding + radius;

        final float x = centerX + radius * (float) Math.cos(angleRad);
        final float y = centerY + radius * (float) Math.sin(angleRad);

        canvas.drawCircle(x, y, dotRadius, dotPaint);
    }
}
