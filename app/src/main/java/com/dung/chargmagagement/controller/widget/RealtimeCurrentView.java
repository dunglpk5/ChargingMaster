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
 * Biểu đồ dòng điện theo thời gian thực, dạng cửa sổ trượt.
 *
 * <p>Giữ {@link #CAPACITY} mẫu gần nhất trong một mảng vòng: mẫu mới đẩy mẫu cũ
 * nhất ra. Dùng mảng nguyên thuỷ thay vì {@code List<Integer>} để mỗi lần lấy mẫu
 * (2 giây/lần, kéo dài suốt phiên sạc) không sinh ra rác cho bộ dọn bộ nhớ.
 *
 * <p>Trục tung tự co giãn theo giá trị lớn nhất trong cửa sổ, nên đường biểu diễn
 * luôn chiếm trọn chiều cao dù đang sạc 300 mA hay 5000 mA.
 */
public class RealtimeCurrentView extends View {

    /** Số mẫu hiển thị; với chu kỳ 2 giây thì tương đương khoảng 2 phút gần nhất. */
    private static final int CAPACITY = 60;

    /** Trần tối thiểu của trục tung để đường không giật khi dòng điện rất nhỏ. */
    private static final int MIN_SCALE_MA = 500;

    private final int[] samples = new int[CAPACITY];
    private int size;
    private int head;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float verticalPadding;

    public RealtimeCurrentView(@NonNull Context context) {
        this(context, null);
    }

    public RealtimeCurrentView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RealtimeCurrentView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        verticalPadding = 6f * density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(ContextCompat.getColor(context, R.color.green_accent));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(ContextCompat.getColor(context, R.color.green_accent));
        fillPaint.setAlpha(40);

        baselinePaint.setStyle(Paint.Style.STROKE);
        baselinePaint.setStrokeWidth(1f * density);
        baselinePaint.setColor(ContextCompat.getColor(context, R.color.divider));
    }

    /** Thêm một mẫu mới (mA) và vẽ lại. */
    public void addSample(int milliAmp) {
        samples[head] = Math.abs(milliAmp);
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) size++;
        invalidate();
    }

    /** Xoá sạch khi bắt đầu phiên đo mới. */
    public void clear() {
        size = 0;
        head = 0;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        final float bottom = height - verticalPadding;
        final float top = verticalPadding;

        canvas.drawLine(0, bottom, width, bottom, baselinePaint);
        if (size < 2) return;

        final int maxValue = Math.max(MIN_SCALE_MA, findMax());
        final float stepX = width / (CAPACITY - 1f);

        linePath.reset();
        fillPath.reset();

        for (int i = 0; i < size; i++) {
            final int value = sampleAt(i);
            // Đẩy đồ thị về sát mép phải khi cửa sổ chưa đầy
            final float x = (CAPACITY - size + i) * stepX;
            final float y = bottom - (value / (float) maxValue) * (bottom - top);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, bottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        fillPath.lineTo((CAPACITY - 1) * stepX, bottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    /** Mẫu thứ i tính từ cũ nhất tới mới nhất. */
    private int sampleAt(int index) {
        final int start = (head - size + CAPACITY) % CAPACITY;
        return samples[(start + index) % CAPACITY];
    }

    private int findMax() {
        int max = 0;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, sampleAt(i));
        }
        return max;
    }
}
