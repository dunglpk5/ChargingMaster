package com.dung.chargmagagement.controller.home.tab;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.controller.settings.SettingsActivity;
import com.dung.chargmagagement.controller.tools.BatteryMonitorActivity;
import com.dung.chargmagagement.controller.vip.VipActivity;
import com.dung.chargmagagement.databinding.FragmentDashboardBinding;
import com.dung.chargmagagement.databinding.ViewChargeStatRowBinding;
import com.dung.chargmagagement.databinding.ViewDeviceInfoRowBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.BatteryUsageStats;
import com.dung.chargmagagement.model.stats.UsageCalculator;

import java.util.Locale;

/**
 * Tab "Trang chủ" – màn hình chính của app.
 *
 * <p>Vai trò Controller trong MVC: nhận dữ liệu từ {@link BatteryMonitor} (Model)
 * và đổ ra View, không tự tính toán nghiệp vụ. Mọi công thức đều nằm ở
 * {@link UsageCalculator}.
 */
public class DashboardFragment extends BaseFragment<FragmentDashboardBinding>
        implements BatteryMonitor.Listener {

    /** Dòng nạp coi là mức tối đa của thanh tiến trình (mA). */
    private static final int MAX_CURRENT_MA = 3_000;

    /** Dải điện áp pin lithium để quy thanh tiến trình về phần trăm (V). */
    private static final float MIN_VOLTAGE = 3.0f;
    private static final float MAX_VOLTAGE = 4.5f;

    /** Tốc độ sạc coi là mức tối đa của thanh tiến trình (%/giờ). */
    private static final float MAX_CHARGE_SPEED = 60f;

    private BatteryMonitor monitor;
    private BatteryRepository repository;

    /**
     * Dung lượng pin dùng để ước tính thời gian sạc đầy.
     * Đọc một lần từ database khi mở màn, không đọc lại ở mỗi lần cập nhật.
     */
    private int usableCapacityMah = BatteryInfo.UNKNOWN_INT;

    /** Tốc độ sạc trung bình lấy từ lịch sử; 0 nghĩa là chưa đủ dữ liệu. */
    private float averageChargeSpeed;

    @NonNull
    @Override
    protected FragmentDashboardBinding onCreateBinding(@NonNull LayoutInflater inflater,
                                                       @Nullable ViewGroup container) {
        return FragmentDashboardBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(requireContext());
        repository = BatteryRepository.get(requireContext());

        setupBatteryCircle();
        setupStaticLabels();
        setupActions();

        loadUsableCapacity();
    }

    /**
     * Vòng tròn mức pin: nền tối, phần pin còn lại màu thương hiệu, mặt nước gợn
     * sóng, bao ngoài là vòng vạch chạy như thanh tiến trình.
     */
    private void setupBatteryCircle() {
        binding.batteryCircle.setPalette(
                ContextCompat.getColor(requireContext(), R.color.battery_circle_bg),
                ContextCompat.getColor(requireContext(), R.color.teal_primary),
                Color.TRANSPARENT);
        binding.batteryCircle.setTickRing(
                ContextCompat.getColor(requireContext(), R.color.teal_primary),
                ContextCompat.getColor(requireContext(), R.color.battery_tick_inactive));
    }

    /** Nhãn của các dòng dùng chung layout, gán một lần lúc dựng màn. */
    private void setupStaticLabels() {
        binding.rowCurrent.tvStatLabel.setText(R.string.home_current);
        binding.rowVoltage.tvStatLabel.setText(R.string.home_voltage);
        binding.rowSpeed.tvStatLabel.setText(R.string.home_charge_speed_avg);

        binding.rowStatus.tvInfoLabel.setText(R.string.home_health_label);
        binding.rowTemperature.tvInfoLabel.setText(R.string.home_temperature);
        binding.rowCapacity.tvInfoLabel.setText(R.string.home_capacity);
        binding.rowTechnology.tvInfoLabel.setText(R.string.home_technology);
        binding.rowModel.tvInfoLabel.setText(R.string.home_model);
        binding.rowPlugged.tvInfoLabel.setText(R.string.home_plugged);
        binding.rowTimeToFull.tvInfoLabel.setText(R.string.home_time_to_full);

        // Tên máy không đổi khi máy đang chạy nên gán luôn ở đây
        binding.rowModel.tvInfoValue.setText(String.format(Locale.US, "%s %s",
                Build.BRAND.toUpperCase(Locale.US), Build.MODEL));
    }

    private void setupActions() {
        binding.btnDetect.setOnClickListener(v -> CheckPowerActivity.start(requireContext()));
        binding.btnRemoveAds.setOnClickListener(v -> VipActivity.start(requireContext()));
        binding.btnMore.setOnClickListener(v -> SettingsActivity.start(requireContext()));
        // Thẻ trạng thái pin mở màn Giám sát pin, cùng đích với nút "Giám sát" ở tab Công cụ
        binding.cardBatteryStatus.setOnClickListener(
                v -> BatteryMonitorActivity.start(requireContext()));
    }

    /** Đọc dung lượng khả dụng và tốc độ sạc trung bình ở thread nền (có database). */
    private void loadUsableCapacity() {
        executors.execute(repository::getUsableCapacityMah, result -> {
            if (result != null) usableCapacityMah = result;
        });

        executors.execute(repository::getUsageStatsSync, stats -> {
            if (binding == null || stats == null) return;
            averageChargeSpeed = stats.getAverageChargedPercentPerDay();
            bindAverageSpeed();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        monitor.addListener(this);
        binding.batteryCircle.setWaveEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        monitor.removeListener(this);

        // Tắt sóng khi rời tab. ViewPager2 giữ tab này sống ở nền chứ không ẩn view
        // đi, nên nếu chỉ dựa vào onVisibilityChanged của chính view thì sóng vẫn
        // vẽ lại 60 lần mỗi giây trong lúc người dùng đang xem tab khác.
        binding.batteryCircle.setWaveEnabled(false);
    }

    // ==================== Cập nhật giao diện ====================

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        bindBatteryCard(info, smoothedMa);
        bindChargeStatus(info, smoothedMa);
        bindBatteryStatus(info, smoothedMa);
    }

    /** Thẻ "Trạng thái pin": bảy dòng thông tin, cập nhật theo từng lần đo. */
    private void bindBatteryStatus(@NonNull BatteryInfo info, int smoothedMa) {
        binding.rowStatus.tvInfoValue.setText(info.getHealth().getLabelRes());

        // Hiện cả hai thang đo: người dùng quen ℉ không phải tự quy đổi
        final float celsius = info.getTemperatureCelsius();
        binding.rowTemperature.tvInfoValue.setText(String.format(Locale.getDefault(),
                "%.1f℃/ %.0f℉", celsius, FormatUtils.celsiusToFahrenheit(celsius)));

        binding.rowCapacity.tvInfoValue.setText(
                info.getDesignCapacityMah() == BatteryInfo.UNKNOWN_INT
                        ? getString(R.string.value_placeholder)
                        : String.format(Locale.US, "%d mAh", info.getDesignCapacityMah()));

        binding.rowTechnology.tvInfoValue.setText(info.getTechnology().isEmpty()
                ? getString(R.string.value_placeholder)
                : info.getTechnology());

        binding.rowPlugged.tvInfoValue.setText(info.getPlugType().getLabelRes());
        binding.rowTimeToFull.tvInfoValue.setText(formatTimeRemaining(info, smoothedMa));
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        // Vừa cắm/rút: dung lượng ước tính có thể đã đổi sau phiên vừa kết thúc
        loadUsableCapacity();
    }

    private void bindBatteryCard(@NonNull BatteryInfo info, int smoothedMa) {
        binding.tvPercent.setText(String.format(Locale.US, "%d%%", info.getPercent()));
        binding.batteryCircle.setPercent(info.getPercent());
        binding.tvBatteryLifeValue.setText(formatBatteryLife(info, smoothedMa));
    }

    /**
     * Thời lượng pin còn dùng được.
     *
     * <p>Chỉ tính khi đang <b>dùng pin</b>: lúc cắm sạc thì pin đang lên chứ không
     * xuống, con số "còn dùng được bao lâu" không có nghĩa gì.
     */
    private String formatBatteryLife(@NonNull BatteryInfo info, int smoothedMa) {
        if (info.getPlugType().isPlugged() || smoothedMa >= 0
                || smoothedMa == BatteryInfo.UNKNOWN_INT
                || usableCapacityMah == BatteryInfo.UNKNOWN_INT) {
            return getString(R.string.value_placeholder);
        }

        final float remainingMah = usableCapacityMah * info.getPercent() / 100f;
        final float hours = remainingMah / Math.abs(smoothedMa);
        return FormatUtils.formatDuration(Math.round(hours * 3_600_000f));
    }

    /**
     * Thời gian còn lại để sạc xong: chỉ hiện khi đang nạp thật sự. Lúc dùng pin thì
     * con số này không có ý nghĩa nên để "-" đúng như bản thiết kế.
     */
    private String formatTimeRemaining(@NonNull BatteryInfo info, int smoothedMa) {
        if (!info.isCharging() || smoothedMa <= 0) {
            return getString(R.string.value_placeholder);
        }
        long remainingMs = UsageCalculator.estimateTimeToFull(
                info.getPercent(), smoothedMa, usableCapacityMah);
        return remainingMs > 0
                ? FormatUtils.formatDuration(remainingMs)
                : getString(R.string.value_placeholder);
    }

    // ==================== Thẻ "Trạng thái sạc" ====================

    private void bindChargeStatus(@NonNull BatteryInfo info, int smoothedMa) {
        bindCurrentRow(info, smoothedMa);
        bindVoltageRow(info);
        bindAverageSpeed();
    }

    /** Dòng sạc hiển thị kèm công suất, đúng như bản thiết kế: "3.5 W / 892 mA". */
    private void bindCurrentRow(@NonNull BatteryInfo info, int smoothedMa) {
        final boolean charging = info.getPlugType().isPlugged() && smoothedMa > 0;
        if (!charging) {
            setRow(binding.rowCurrent, getString(R.string.value_placeholder), 0);
            return;
        }

        final float watt = info.getVoltage() * smoothedMa / 1000f;
        setRow(binding.rowCurrent,
                String.format(Locale.US, "%.1f W / %d mA", watt, smoothedMa),
                Math.round(smoothedMa * 100f / MAX_CURRENT_MA));
    }

    private void bindVoltageRow(@NonNull BatteryInfo info) {
        if (info.getVoltage() <= 0) {
            setRow(binding.rowVoltage, getString(R.string.value_placeholder), 0);
            return;
        }

        // Quy về phần trăm trong dải làm việc của pin lithium chứ không phải 0..max:
        // pin gần cạn vẫn ở khoảng 3.2 V, để thang bắt đầu từ 0 thì thanh lúc nào
        // cũng gần đầy và không nói lên điều gì
        final float ratio = (info.getVoltage() - MIN_VOLTAGE) / (MAX_VOLTAGE - MIN_VOLTAGE);
        setRow(binding.rowVoltage,
                String.format(Locale.US, "%.0f mV", info.getVoltage() * 1000f),
                Math.round(ratio * 100f));
    }

    private void bindAverageSpeed() {
        if (binding == null) return;

        if (averageChargeSpeed <= 0f) {
            setRow(binding.rowSpeed, getString(R.string.value_placeholder), 0);
            return;
        }
        setRow(binding.rowSpeed,
                String.format(Locale.US, "%.1f%%/h", averageChargeSpeed),
                Math.round(averageChargeSpeed * 100f / MAX_CHARGE_SPEED));
    }

    private void setRow(@NonNull ViewChargeStatRowBinding row, @NonNull String value, int percent) {
        row.tvStatValue.setText(value);
        row.progressStat.setProgress(Math.max(0, Math.min(100, percent)));
    }
}
