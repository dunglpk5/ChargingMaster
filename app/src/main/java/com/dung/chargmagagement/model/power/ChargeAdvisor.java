package com.dung.chargmagagement.model.power;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.PlugType;

import java.util.ArrayList;
import java.util.List;

/**
 * Soi trạng thái sạc hiện tại và liệt kê những gì bất thường.
 *
 * <p>Tách khỏi Android hoàn toàn: nhận vào vài con số, trả về danh sách cảnh báo.
 * Nhờ vậy toàn bộ ngưỡng và điều kiện kiểm thử được bằng unit test thường – đây là
 * phần dễ sai lặng lẽ nhất, vì một điều kiện viết nhầm chỉ biểu hiện thành "app
 * chẳng bao giờ cảnh báo gì", rất khó phát hiện khi dùng tay.
 */
public final class ChargeAdvisor {

    /** Nhiệt độ pin coi là nóng bất thường khi đang sạc (℃). */
    public static final float WARN_TEMPERATURE = 41f;

    /** Dòng nạp dưới mức này là thấp bất thường với củ sạc rời (mA). */
    public static final int WARN_LOW_CURRENT_MA = 400;

    /** Trên mức này thì nên rút sạc để pin đỡ chai. */
    public static final int NEARLY_FULL_PERCENT = 90;

    private ChargeAdvisor() {
    }

    /**
     * Phân tích trạng thái hiện tại.
     *
     * @param info       trạng thái pin vừa đọc
     * @param smoothedMa dòng điện đã làm mượt (mA), dương là đang nạp
     * @return danh sách cảnh báo theo thứ tự ưu tiên, rỗng nếu mọi thứ đều ổn
     */
    @NonNull
    public static List<ChargeWarning> analyze(@NonNull BatteryInfo info, int smoothedMa) {
        List<ChargeWarning> warnings = new ArrayList<>();

        // Nhiệt độ kiểm tra cả khi không sạc: pin nóng là vấn đề an toàn
        if (info.getTemperatureCelsius() >= WARN_TEMPERATURE) {
            warnings.add(ChargeWarning.OVERHEAT);
        }

        if (!info.getPlugType().isPlugged()) return warnings;

        final boolean hasReading = smoothedMa != BatteryInfo.UNKNOWN_INT;

        if (hasReading && smoothedMa < 0) {
            // Cắm sạc mà dòng vẫn âm: nguồn không theo kịp mức tiêu thụ
            warnings.add(ChargeWarning.DRAINING);
        } else if (hasReading && smoothedMa > 0 && smoothedMa < WARN_LOW_CURRENT_MA) {
            warnings.add(ChargeWarning.LOW_CURRENT);
        }

        // Cổng USB máy tính giới hạn dòng ở mức rất thấp theo chuẩn, không phải lỗi
        // cáp – nên báo riêng thay vì gộp vào cảnh báo dòng thấp phía trên
        if (info.getPlugType() == PlugType.USB) {
            warnings.add(ChargeWarning.USB_SOURCE);
        }

        if (info.getPercent() >= NEARLY_FULL_PERCENT) {
            warnings.add(ChargeWarning.NEARLY_FULL);
        }

        return warnings;
    }
}
