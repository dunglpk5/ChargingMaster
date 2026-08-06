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
 * Biểu đồ nhỏ hiển thị mức tải của một nhân CPU theo thời gian.
 *
 * <p>Trục tung cố định 0–100% có nhãn, trục hoành là cửa sổ trượt các mẫu gần
 * nhất. Màn Sử dụng CPU đặt 8 view loại này cạnh nhau nên mỗi view phải thật nhẹ:
 * dùng mảng nguyên thuỷ kiểu vòng, không cấp phát gì trong {@code onDraw()}.
 */
public class MiniCpuChartView extends View {

    /** Số mẫu giữ trong cửa sổ trượt. */
    private static final int CAPACITY = 40;

    /** Các mốc có kẻ đường và ghi số. */
    private static final int[] GRID_PERCENTS = {0, 20, 40, 60, 80, 100};

    private final int[] samples = new int[CAPACITY];
    private int size;
    private int head;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private float labelWidth;
    private float verticalPadding;

    public MiniCpuChartView(@NonNull Context context) {
        this(context, null);
    }

    public MiniCpuChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiniCpuChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        labelWidth = 34f * density;
        verticalPadding = 6f * density;

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(9f * density);
        labelPaint.setTextAlign(Paint.Align.RIGHT);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1f * density);
        linePaint.setColor(ContextCompat.getColor(context, R.color.cpu_chart_line));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, R.color.cpu_chart_fill));
    }

    /** Thêm một mẫu mức tải (0..100). */
    public void addSample(int percent) {
        samples[head] = Math.max(0, Math.min(100, percent));
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) size++;
        invalidate();
    }

    public void clear() {
        size = 0;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float left = labelWidth;
        final float right = getWidth();
        final float top = verticalPadding;
        final float bottom = getHeight() - verticalPadding;
        if (right <= left || bottom <= top) return;

        drawGrid(canvas, left, right, top, bottom);
        drawData(canvas, left, right, top, bottom);
    }

    private void drawGrid(Canvas canvas, float left, float right, float top, float bottom) {
        labelPaint.getFontMetrics(fontMetrics);
        final float textOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f;

        for (int percent : GRID_PERCENTS) {
            final float y = percentToY(percent, top, bottom);
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(percent + "%", left - 3f, y + textOffset, labelPaint);
        }
    }

    private void drawData(Canvas canvas, float left, float right, float top, float bottom) {
        if (size < 2) return;

        final float stepX = (right - left) / (CAPACITY - 1f);
        fillPath.reset();

        // Đẩy dữ liệu về sát mép phải khi cửa sổ chưa đầy
        final float firstX = left + (CAPACITY - size) * stepX;
        fillPath.moveTo(firstX, bottom);

        for (int i = 0; i < size; i++) {
            final float x = left + (CAPACITY - size + i) * stepX;
            final float y = percentToY(sampleAt(i), top, bottom);
            fillPath.lineTo(x, y);
        }

        final float lastX = left + (CAPACITY - 1) * stepX;
        fillPath.lineTo(lastX, bottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(fillPath, linePaint);
    }

    private int sampleAt(int index) {
        final int start = (head - size + CAPACITY) % CAPACITY;
        return samples[(start + index) % CAPACITY];
    }

    private float percentToY(int percent, float top, float bottom) {
        return bottom - (percent / 100f) * (bottom - top);
    }
}
