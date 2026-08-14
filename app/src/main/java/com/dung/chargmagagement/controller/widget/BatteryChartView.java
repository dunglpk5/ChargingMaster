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
import com.dung.chargmagagement.model.ui.ChartPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Biểu đồ mức pin theo giờ trong ngày (trục hoành 0:00–24:00, trục tung 0–100%).
 *
 * <p>Tự vẽ bằng Canvas thay vì kéo thêm thư viện biểu đồ: nhu cầu ở đây chỉ là một
 * đường gấp khúc với lưới nền, dùng MPAndroidChart sẽ cộng thêm ~700 KB vào APK
 * cho những tính năng không dùng tới.
 *
 * <p><b>Tối ưu vẽ:</b> mọi {@link Paint} và {@link Path} đều tạo sẵn trong hàm dựng.
 * Cấp phát đối tượng bên trong {@code onDraw()} là nguyên nhân giật khung hình
 * phổ biến nhất ở custom view vì nó kích hoạt GC ngay giữa lúc cuộn.
 */
public class BatteryChartView extends View {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int HORIZONTAL_STEP_HOURS = 4;   // nhãn 0:00, 4:00, 8:00…
    private static final int VERTICAL_STEP_PERCENT = 20;  // nhãn 0%, 20%, 40%…

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private final List<ChartPoint> points = new ArrayList<>();

    /** Chừa chỗ cho nhãn trục, tính theo dp trong hàm dựng. */
    private float paddingLeft;
    private float paddingBottom;
    private float paddingTop;
    private float paddingRight;
    private float dotRadius;

    public BatteryChartView(@NonNull Context context) {
        this(context, null);
    }

    public BatteryChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        final float density = context.getResources().getDisplayMetrics().density;

        paddingLeft = 36f * density;
        paddingBottom = 20f * density;
        paddingTop = 8f * density;
        paddingRight = 8f * density;
        dotRadius = 2.5f * density;

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(10f * density);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(ContextCompat.getColor(context, R.color.teal_primary));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, R.color.teal_primary));
        fillPaint.setAlpha(30);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ContextCompat.getColor(context, R.color.teal_primary));
    }

    /**
     * Đổ dữ liệu mới. Danh sách phải được sắp xếp tăng dần theo thời gian
     * (DAO đã bảo đảm điều này bằng {@code ORDER BY timestamp ASC}).
     */
    public void setPoints(@Nullable List<ChartPoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float left = paddingLeft;
        final float top = paddingTop;
        final float right = getWidth() - paddingRight;
        final float bottom = getHeight() - paddingBottom;
        if (right <= left || bottom <= top) return;

        drawGrid(canvas, left, top, right, bottom);
        drawData(canvas, left, top, right, bottom);
    }

    /** Lưới ngang theo % và lưới dọc theo giờ, kèm nhãn trục. */
    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        labelPaint.getFontMetrics(fontMetrics);
        final float textOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f;

        // Đường ngang: 0%, 20%, …, 100%
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int percent = 0; percent <= 100; percent += VERTICAL_STEP_PERCENT) {
            final float y = percentToY(percent, top, bottom);
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(percent + " %", left - 4f, y + textOffset, labelPaint);
        }

        // Đường dọc: 0:00, 4:00, …, 24:00
        labelPaint.setTextAlign(Paint.Align.CENTER);
        for (int hour = 0; hour <= 24; hour += HORIZONTAL_STEP_HOURS) {
            final float x = minuteToX(hour * 60, left, right);
            canvas.drawLine(x, top, x, bottom, gridPaint);
            canvas.drawText(String.format(Locale.US, "%d:00", hour),
                    x, bottom - fontMetrics.ascent + 2f, labelPaint);
        }
    }

    /** Đường gấp khúc mức pin cùng phần tô nhạt bên dưới. */
    private void drawData(Canvas canvas, float left, float top, float right, float bottom) {
        if (points.isEmpty()) return;

        // Một điểm duy nhất thì không vẽ được đường, chỉ chấm một dấu
        if (points.size() == 1) {
            ChartPoint only = points.get(0);
            canvas.drawCircle(minuteToX(only.getMinuteOfDay(), left, right),
                    percentToY(only.getPercent(), top, bottom), dotRadius, dotPaint);
            return;
        }

        linePath.reset();
        fillPath.reset();

        for (int i = 0; i < points.size(); i++) {
            final ChartPoint point = points.get(i);
            final float x = minuteToX(point.getMinuteOfDay(), left, right);
            final float y = percentToY(point.getPercent(), top, bottom);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, bottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // Khép kín vùng tô xuống đáy biểu đồ
        final float lastX = minuteToX(points.get(points.size() - 1).getMinuteOfDay(), left, right);
        fillPath.lineTo(lastX, bottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    private float minuteToX(int minuteOfDay, float left, float right) {
        final float ratio = Math.max(0f, Math.min(1f, minuteOfDay / (float) MINUTES_PER_DAY));
        return left + ratio * (right - left);
    }

    private float percentToY(int percent, float top, float bottom) {
        final float ratio = Math.max(0f, Math.min(1f, percent / 100f));
        // Trục tung lật ngược: 100% ở trên cùng
        return bottom - ratio * (bottom - top);
    }
}
