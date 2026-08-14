package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ViewChargeStageBinding;

/**
 * Tô màu một ô giai đoạn sạc, dùng chung cho màn X-Sạc và màn sạc phủ.
 *
 * <p>Giai đoạn đang chạy được làm nổi bằng cách đổi màu <b>viền và biểu tượng</b>
 * sang trắng, không tô đặc cả vòng tròn.
 */
final class StageStyler {

    private StageStyler() {
    }

    static void apply(@NonNull Context context, @NonNull ViewChargeStageBinding stage,
                      int color) {
        stage.tvStage.setTextColor(color);
        ImageViewCompat.setImageTintList(stage.imgStage, ColorStateList.valueOf(color));
        applyRingColor(context, stage, color);
    }

    /**
     * Đổi màu riêng đường viền vòng tròn.
     *
     * <p>Không dùng {@code setTint} lên background: bộ lọc màu phủ lên toàn bộ hình
     * đã vẽ nên vòng tròn bị tô đặc thành một đĩa trắng thay vì chỉ đổi màu nét
     * viền. Gọi thẳng {@code setStroke} thì chỉ đúng nét viền đổi màu, phần ruột
     * vẫn trong suốt.
     *
     * <p>Bắt buộc {@code mutate()}: ba vòng tròn nạp từ cùng một tệp drawable nên
     * dùng chung ConstantState – đổi một cái là cả ba đổi theo.
     */
    private static void applyRingColor(@NonNull Context context,
                                       @NonNull ViewChargeStageBinding stage, int color) {
        final Drawable background = stage.imgStage.getBackground();
        if (!(background instanceof GradientDrawable)) return;

        final int strokeWidth =
                context.getResources().getDimensionPixelSize(R.dimen.stage_ring_stroke);
        ((GradientDrawable) background.mutate()).setStroke(strokeWidth, color);
    }
}
