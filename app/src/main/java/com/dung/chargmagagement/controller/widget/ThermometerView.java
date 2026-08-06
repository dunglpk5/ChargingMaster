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
import com.dung.chargmagagement.common.FormatUtils;

import java.util.Locale;

/**
 * Nhiệt kế dạng ống đứng có hai thang đo: ℉ bên trái và ℃ bên phải.
 *
 * <p>Thang chạy từ {@link #MIN_CELSIUS} tới {@link #MAX_CELSIUS} – khoảng nhiệt độ
 * pin điện thoại thực tế. Vượt ngoài khoảng thì cột thuỷ ngân dừng ở mép, không
 * tràn ra ngoài ống.
 *
 * <p>Mọi đối tượng vẽ đều tạo sẵn trong hàm dựng để {@code onDraw()} không cấp
 * phát bộ nhớ, vì view này được vẽ lại mỗi lần lấy mẫu nhiệt độ.
 */
public class ThermometerView extends View {

    private static final float MIN_CELSIUS = 20f;
    private static final float MAX_CELSIUS = 60f;

    /** Khoảng cách giữa hai vạch có ghi số (℃). */
    private static final int MAJOR_STEP = 5;

    /** Số vạch phụ giữa hai vạch chính. */
    private static final int MINOR_PER_MAJOR = 2;

    private final Paint tubePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bulbBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bulbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF tubeRect = new RectF();
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private float density;
    private float tubeWidth;
    private float bulbRadius;

    /** Nhiệt độ đang hiển thị (℃). */
    private float celsius = MIN_CELSIUS;

    public ThermometerView(@NonNull Context context) {
        this(context, null);
    }

    public ThermometerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThermometerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        density = context.getResources().getDisplayMetrics().density;
        tubeWidth = 22f * density;
        bulbRadius = 30f * density;

        final int hotColor = ContextCompat.getColor(context, R.color.thermo_fill);
        final int emptyColor = ContextCompat.getColor(context, R.color.thermo_empty);

        tubePaint.setStyle(Paint.Style.FILL);
        tubePaint.setColor(emptyColor);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(hotColor);

        bulbBackPaint.setStyle(Paint.Style.FILL);
        bulbBackPaint.setColor(ContextCompat.getColor(context, R.color.thermo_bulb_light));

        bulbPaint.setStyle(Paint.Style.FILL);
        bulbPaint.setColor(hotColor);

        majorTickPaint.setStyle(Paint.Style.STROKE);
        majorTickPaint.setStrokeWidth(1.5f * density);
        majorTickPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));

        minorTickPaint.setStyle(Paint.Style.STROKE);
        minorTickPaint.setStrokeWidth(1f * density);
        minorTickPaint.setColor(ContextCompat.getColor(context, R.color.divider));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        labelPaint.setTextSize(13f * density);

        unitPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        unitPaint.setTextSize(13f * density);
        unitPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Cập nhật nhiệt độ hiển thị. */
    public void setCelsius(float value) {
        if (Math.abs(value - celsius) < 0.05f) return;
        celsius = value;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float centerX = getWidth() / 2f;
        final float top = 16f * density;
        final float bottom = getHeight() - bulbRadius * 2f;
        if (bottom <= top) return;

        drawTube(canvas, centerX, top, bottom);
        drawScales(canvas, centerX, top, bottom);
        drawBulb(canvas, centerX, bottom);
    }

    /** Ống thuỷ tinh và cột thuỷ ngân bên trong. */
    private void drawTube(Canvas canvas, float centerX, float top, float bottom) {
        final float left = centerX - tubeWidth / 2f;
        final float right = centerX + tubeWidth / 2f;
        final float radius = tubeWidth / 2f;

        tubeRect.set(left, top, right, bottom);
        canvas.drawRoundRect(tubeRect, radius, radius, tubePaint);

        // Cột thuỷ ngân dâng từ đáy lên theo nhiệt độ
        final float fillTop = valueToY(celsius, top, bottom);
        tubeRect.set(left, fillTop, right, bottom);
        canvas.drawRoundRect(tubeRect, radius, radius, fillPaint);
    }

    /** Bầu nhiệt kế ở đáy, luôn đầy màu vì đó là nơi chứa thuỷ ngân. */
    private void drawBulb(Canvas canvas, float centerX, float bottom) {
        final float bulbCenterY = bottom + bulbRadius * 0.6f;
        canvas.drawCircle(centerX, bulbCenterY, bulbRadius, bulbBackPaint);
        canvas.drawCircle(centerX, bulbCenterY, bulbRadius * 0.82f, bulbPaint);
    }

    /** Vạch chia và số cho cả hai thang ℉ và ℃. */
    private void drawScales(Canvas canvas, float centerX, float top, float bottom) {
        final float tubeEdge = tubeWidth / 2f + 4f * density;
        final float majorTickLength = 22f * density;
        final float minorTickLength = 12f * density;

        labelPaint.getFontMetrics(fontMetrics);
        final float textOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f;

        // Nhãn đơn vị ở đỉnh mỗi cột
        canvas.drawText("℉", centerX - tubeEdge - majorTickLength - 12f * density,
                top - 4f * density, unitPaint);
        canvas.drawText("℃", centerX + tubeEdge + majorTickLength + 12f * density,
                top - 4f * density, unitPaint);

        final float minorStep = MAJOR_STEP / (float) (MINOR_PER_MAJOR + 1);

        for (float value = MIN_CELSIUS; value <= MAX_CELSIUS + 0.01f; value += minorStep) {
            final float y = valueToY(value, top, bottom);
            // Sai số dấu phẩy động: coi là vạch chính nếu đủ gần bội của MAJOR_STEP
            final boolean isMajor = Math.abs(value / MAJOR_STEP - Math.round(value / MAJOR_STEP))
                    < 0.01f;

            final float length = isMajor ? majorTickLength : minorTickLength;
            final Paint paint = isMajor ? majorTickPaint : minorTickPaint;

            canvas.drawLine(centerX - tubeEdge - length, y, centerX - tubeEdge, y, paint);
            canvas.drawLine(centerX + tubeEdge, y, centerX + tubeEdge + length, y, paint);

            if (!isMajor) continue;

            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(
                    String.format(Locale.getDefault(), "%.1f",
                            FormatUtils.celsiusToFahrenheit(value)),
                    centerX - tubeEdge - length - 6f * density, y + textOffset, labelPaint);

            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format(Locale.US, "%.0f", value),
                    centerX + tubeEdge + length + 6f * density, y + textOffset, labelPaint);
        }
    }

    /** Đổi nhiệt độ thành toạ độ Y; thang lật ngược vì số lớn nằm trên. */
    private float valueToY(float value, float top, float bottom) {
        final float clamped = Math.max(MIN_CELSIUS, Math.min(MAX_CELSIUS, value));
        final float ratio = (clamped - MIN_CELSIUS) / (MAX_CELSIUS - MIN_CELSIUS);
        return bottom - ratio * (bottom - top);
    }
}
