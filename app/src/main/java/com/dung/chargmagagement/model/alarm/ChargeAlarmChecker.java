package com.dung.chargmagagement.model.alarm;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Quyết định lúc nào cần phát cảnh báo trong khi sạc.
 *
 * <p>Tách hẳn khỏi Android để kiểm thử được: lớp này chỉ nhận vào con số và
 * trả về loại cảnh báo, việc hiện thông báo do tầng service lo.
 *
 * <p><b>Chống lặp cảnh báo:</b> mỗi loại chỉ báo <b>một lần cho mỗi phiên sạc</b>.
 * Nếu không có cơ chế này thì khi pin đứng yên ở 80%, cứ 2 giây lấy mẫu là lại
 * kêu một lần – đủ để người dùng gỡ app ngay lập tức. Trạng thái đã báo được
 * đặt lại khi rút sạc.
 */
public final class ChargeAlarmChecker {

    /** Loại cảnh báo. */
    public enum AlarmType {

        NONE(0, 0),
        THRESHOLD(R.string.alarm_threshold_title, R.string.alarm_threshold_message),
        FULL(R.string.alarm_full_title, R.string.alarm_full_message),
        OVERHEAT(R.string.alarm_overheat_title, R.string.alarm_overheat_message);

        @StringRes
        private final int titleRes;

        @StringRes
        private final int messageRes;

        AlarmType(@StringRes int titleRes, @StringRes int messageRes) {
            this.titleRes = titleRes;
            this.messageRes = messageRes;
        }

        @StringRes
        public int getTitleRes() {
            return titleRes;
        }

        @StringRes
        public int getMessageRes() {
            return messageRes;
        }
    }

    /**
     * Nhiệt độ phải hạ xuống dưới mức này mới cho phép báo quá nhiệt lần nữa
     * trong cùng phiên. Không có vùng đệm thì nhiệt độ dao động quanh ngưỡng sẽ
     * làm cảnh báo bật tắt liên tục.
     */
    private static final float OVERHEAT_RESET_MARGIN = 2f;

    // Trạng thái của phiên sạc hiện tại
    private boolean thresholdFired;
    private boolean fullFired;
    private boolean overheatFired;

    /**
     * Kiểm tra xem có cần cảnh báo không.
     *
     * @param percent  mức pin hiện tại
     * @param charging đang cắm nguồn hay không
     * @param celsius  nhiệt độ pin
     * @param settings cấu hình người dùng
     * @return loại cảnh báo cần phát, hoặc {@link AlarmType#NONE}
     */
    @NonNull
    public AlarmType check(int percent, boolean charging, float celsius,
                           @NonNull AlarmSettings settings) {
        // Quá nhiệt là cảnh báo an toàn nên vẫn kiểm tra cả khi không sạc
        if (settings.isOverheatEnabled()) {
            if (celsius >= settings.getOverheatTemp() && !overheatFired) {
                overheatFired = true;
                return AlarmType.OVERHEAT;
            }
            if (celsius < settings.getOverheatTemp() - OVERHEAT_RESET_MARGIN) {
                // Máy đã nguội, cho phép báo lại nếu lại nóng lên
                overheatFired = false;
            }
        }

        // Các cảnh báo còn lại chỉ có ý nghĩa khi đang nạp điện
        if (!charging) return AlarmType.NONE;

        if (settings.isFullEnabled() && percent >= 100 && !fullFired) {
            fullFired = true;
            return AlarmType.FULL;
        }

        if (settings.isThresholdEnabled()
                && percent >= settings.getThresholdPercent()
                && !thresholdFired) {
            thresholdFired = true;
            return AlarmType.THRESHOLD;
        }

        return AlarmType.NONE;
    }

    /**
     * Nạp lại trạng thái đã lưu của phiên hiện tại.
     *
     * <p>Cần thiết vì báo động chạy bằng hẹn giờ chứ không bằng tiến trình thường
     * trú: giữa hai lần dậy, đối tượng này đã bị huỷ cùng cả tiến trình. Không khôi
     * phục thì mỗi lần dậy lại kêu thêm một lần nữa.
     */
    public void restore(boolean threshold, boolean full, boolean overheat) {
        thresholdFired = threshold;
        fullFired = full;
        overheatFired = overheat;
    }

    public boolean isThresholdFired() {
        return thresholdFired;
    }

    public boolean isFullFired() {
        return fullFired;
    }

    public boolean isOverheatFired() {
        return overheatFired;
    }

    /** Bắt đầu phiên sạc mới: cho phép mọi cảnh báo phát lại. */
    public void resetSession() {
        thresholdFired = false;
        fullFired = false;
        overheatFired = false;
    }
}
