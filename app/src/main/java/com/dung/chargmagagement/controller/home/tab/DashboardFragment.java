package com.dung.chargmagagement.controller.home.tab;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.InfoAdapter;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.databinding.FragmentDashboardBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.UsageCalculator;
import com.dung.chargmagagement.model.ui.InfoItem;

import java.util.ArrayList;
import java.util.List;
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

    // Khoá của từng dòng thông tin, giữ cố định để DiffUtil so sánh đúng
    private static final String KEY_PLUGGED = "plugged";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_HEALTH = "health";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_VOLTAGE = "voltage";
    private static final String KEY_CAPACITY = "capacity";
    private static final String KEY_TECHNOLOGY = "technology";
    private static final String KEY_MODEL = "model";

    private BatteryMonitor monitor;
    private BatteryRepository repository;
    private InfoAdapter infoAdapter;

    /**
     * Dung lượng pin dùng để ước tính thời gian sạc đầy.
     * Đọc một lần từ database khi mở màn, không đọc lại ở mỗi lần cập nhật.
     */
    private int usableCapacityMah = BatteryInfo.UNKNOWN_INT;

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

        setupInfoList();
        setupActions();
        loadUsableCapacity();
    }

    private void setupInfoList() {
        infoAdapter = new InfoAdapter();
        binding.rvInfo.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvInfo.setAdapter(infoAdapter);
        // Số dòng cố định nên tắt tính lại kích thước cho nhẹ
        binding.rvInfo.setHasFixedSize(true);
    }

    private void setupActions() {
        binding.btnDetect.setOnClickListener(v -> onDetectClicked());

        // Hai nút này sẽ nối vào màn VIP và menu ở các phase sau
        binding.btnRemoveAds.setOnClickListener(v -> showComingSoon());
        binding.btnMore.setOnClickListener(v -> showComingSoon());
    }

    /** Mở màn kiểm tra nguồn sạc; màn đó tự bắt đầu một phiên đo mới. */
    private void onDetectClicked() {
        CheckPowerActivity.start(requireContext());
    }

    private void showComingSoon() {
        Toast.makeText(requireContext(), R.string.msg_coming_soon, Toast.LENGTH_SHORT).show();
    }

    /** Đọc dung lượng khả dụng ở thread nền (có truy vấn database). */
    private void loadUsableCapacity() {
        executors.execute(repository::getUsableCapacityMah, result -> {
            if (result != null) {
                usableCapacityMah = result;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        monitor.addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        monitor.removeListener(this);
    }

    // ==================== Cập nhật giao diện ====================

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        bindHeader(info, smoothedMa);
        infoAdapter.submitList(buildInfoItems(info, smoothedMa));
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        // Vừa cắm/rút: dung lượng ước tính có thể đã đổi sau phiên vừa kết thúc
        loadUsableCapacity();
    }

    private void bindHeader(@NonNull BatteryInfo info, int smoothedMa) {
        binding.tvPercent.setText(String.format(Locale.US, "%d%%", info.getPercent()));
        binding.tvRemainingValue.setText(formatTimeRemaining(info, smoothedMa));
        binding.tvPowerValue.setText(formatChargingPower(info, smoothedMa));
    }

    /**
     * Thời gian còn lại: chỉ hiện khi đang nạp thật sự. Lúc dùng pin thì con số
     * này không có ý nghĩa nên để "-" đúng như bản thiết kế.
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

    /** Công suất sạc = U × I, chỉ hiện khi đang cắm nguồn. */
    private String formatChargingPower(@NonNull BatteryInfo info, int smoothedMa) {
        if (!info.getPlugType().isPlugged() || smoothedMa <= 0 || info.getVoltage() <= 0) {
            return getString(R.string.value_placeholder);
        }
        float watt = info.getVoltage() * smoothedMa / 1000f;
        return String.format(Locale.US, "%.1f W", watt);
    }

    /** Dựng lại danh sách 8 dòng thông tin; DiffUtil lo phần chỉ vẽ dòng đổi. */
    private List<InfoItem> buildInfoItems(@NonNull BatteryInfo info, int smoothedMa) {
        final String placeholder = getString(R.string.value_placeholder);
        List<InfoItem> items = new ArrayList<>(8);

        items.add(new InfoItem(KEY_PLUGGED, R.drawable.ic_plug, R.string.home_plugged,
                getString(info.getPlugType().getLabelRes())));

        items.add(new InfoItem(KEY_TEMPERATURE, R.drawable.ic_thermometer, R.string.home_temperature,
                FormatUtils.formatTemperature(info.getTemperatureCelsius())));

        items.add(new InfoItem(KEY_HEALTH, R.drawable.ic_heart, R.string.home_health,
                getString(info.getHealth().getLabelRes())));

        items.add(new InfoItem(KEY_CURRENT, R.drawable.ic_current, R.string.home_current,
                smoothedMa == BatteryInfo.UNKNOWN_INT
                        ? placeholder
                        : String.format(Locale.US, "%d mA", smoothedMa)));

        items.add(new InfoItem(KEY_VOLTAGE, R.drawable.ic_voltage, R.string.home_voltage,
                info.getVoltage() > 0
                        ? FormatUtils.formatVoltage(info.getVoltage())
                        : placeholder));

        items.add(new InfoItem(KEY_CAPACITY, R.drawable.ic_capacity, R.string.home_capacity,
                info.getDesignCapacityMah() == BatteryInfo.UNKNOWN_INT
                        ? placeholder
                        : String.format(Locale.US, "%d mAh", info.getDesignCapacityMah())));

        items.add(new InfoItem(KEY_TECHNOLOGY, R.drawable.ic_technology, R.string.home_technology,
                info.getTechnology().isEmpty() ? placeholder : info.getTechnology()));

        items.add(new InfoItem(KEY_MODEL, R.drawable.ic_phone, R.string.home_model,
                getDeviceModel()));

        return items;
    }

    /** Tên máy dạng "TECNO LH7n(TECNO)" giống bản thiết kế. */
    private String getDeviceModel() {
        return String.format(Locale.US, "%s(%s)", Build.MODEL, Build.BRAND.toUpperCase(Locale.US));
    }
}
