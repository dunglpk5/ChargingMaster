package com.dung.chargmagagement.controller.widget;

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
 */
public class ArcGaugeView extends View {

    /** Cung nửa vòng tròn: bắt đầu ở 180 độ, quét 180 độ. */
    private static final float START_ANGLE = 180f;
    private static final float SWEEP_ANGLE = 180f;

    /** Số vạch trên cung. */
    private static final int TICK_COUNT = 36;

    /** Tỉ lệ độ dài vạch trên tổng khoảng cách giữa hai vạch. */
    private static final float TICK_FILL_RATIO = 0.55f;

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float strokeWidth;
    private float dotRadius;

    /** Vị trí chấm đánh dấu, 0..1. */
    private float progress;

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

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(strokeWidth);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(ContextCompat.getColor(context, R.color.text_on_primary));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.state_warning));
    }

    /**
     * Đặt vị trí chấm đánh dấu.
     *
     * @param value giá trị 0..1; ngoài khoảng sẽ bị kẹp lại
     */
    public void setProgress(float value) {
        final float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - progress) < 0.001f) return; // tránh vẽ lại vô ích

        progress = clamped;
        invalidate();
    }

    /** Đổi màu chấm đánh dấu theo mức độ tốc độ sạc. */
    public void setDotColor(int color) {
        if (dotPaint.getColor() == color) return;
        dotPaint.setColor(color);
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

    /** Vẽ các vạch nét đứt cách đều nhau theo góc. */
    private void drawTicks(@NonNull Canvas canvas) {
        final float anglePerTick = SWEEP_ANGLE / TICK_COUNT;
        final float tickSweep = anglePerTick * TICK_FILL_RATIO;

        for (int i = 0; i < TICK_COUNT; i++) {
            final float startAngle = START_ANGLE + i * anglePerTick;
            canvas.drawArc(arcBounds, startAngle, tickSweep, false, tickPaint);
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
