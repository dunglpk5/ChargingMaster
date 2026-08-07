package com.dung.chargmagagement.controller.home.tab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.ToolAdapter;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.controller.alarm.ChargeAlarmActivity;
import com.dung.chargmagagement.controller.detail.PhoneDetailActivity;
import com.dung.chargmagagement.controller.history.ChargeHistoryActivity;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.controller.power.XChargeActivity;
import com.dung.chargmagagement.controller.settings.SettingsActivity;
import com.dung.chargmagagement.controller.tools.CpuUsageActivity;
import com.dung.chargmagagement.controller.tools.NotificationCleanActivity;
import com.dung.chargmagagement.controller.tools.PhoneTemperatureActivity;
import com.dung.chargmagagement.controller.vip.VipActivity;
import com.dung.chargmagagement.model.ads.AdManager;
import com.dung.chargmagagement.model.vip.VipManager;
import com.dung.chargmagagement.databinding.FragmentToolsBinding;
import com.dung.chargmagagement.databinding.ViewStatColumnBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.device.StorageInfo;
import com.dung.chargmagagement.model.device.SystemInfoProvider;
import com.dung.chargmagagement.model.ui.ToolItem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tab "Công cụ": 3 chỉ số hệ thống ở header và hai lưới công cụ.
 *
 * <p>Nhiệt độ cập nhật liên tục theo {@link BatteryMonitor}; bộ nhớ và RAM chỉ đọc
 * lại mỗi lần tab hiện lên. Hai con số đó thay đổi rất chậm, đọc liên tục chỉ tốn
 * pin mà người dùng không nhận ra khác biệt.
 */
public class ToolsFragment extends BaseFragment<FragmentToolsBinding>
        implements BatteryMonitor.Listener, ToolAdapter.OnToolClickListener {

    private static final int GRID_SPAN_COUNT = 3;

    private BatteryMonitor monitor;
    private SystemInfoProvider systemInfoProvider;

    @NonNull
    @Override
    protected FragmentToolsBinding onCreateBinding(@NonNull LayoutInflater inflater,
                                                   @Nullable ViewGroup container) {
        return FragmentToolsBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(requireContext());
        systemInfoProvider = new SystemInfoProvider(requireContext());

        setupStatIcons();
        setupToolGrid(binding.rvVipTools, buildVipTools());
        setupToolGrid(binding.rvDetectTools, buildDetectTools());
    }

    /** Ba cột chỉ số dùng chung một layout nên phải gán icon bằng code. */
    private void setupStatIcons() {
        binding.statStorage.imgStatIcon.setImageResource(R.drawable.ic_stat_storage);
        binding.statRam.imgStatIcon.setImageResource(R.drawable.ic_stat_ram);
        binding.statTemperature.imgStatIcon.setImageResource(R.drawable.ic_stat_temp);
    }

    private void setupToolGrid(@NonNull RecyclerView recyclerView, @NonNull List<ToolItem> items) {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), GRID_SPAN_COUNT));
        recyclerView.setHasFixedSize(true);

        ToolAdapter adapter = new ToolAdapter(items);
        adapter.setOnToolClickListener(this);
        recyclerView.setAdapter(adapter);
    }

    // ==================== Danh sách công cụ ====================

    private List<ToolItem> buildVipTools() {
        return Arrays.asList(
                new ToolItem(ToolItem.Action.NO_ADS,
                        R.drawable.ic_tool_no_ads, R.string.tools_no_ads, R.color.icon_dark),
                new ToolItem(ToolItem.Action.CHARGE_ALARM,
                        R.drawable.ic_tool_alarm, R.string.tools_charge_alarm, R.color.icon_dark),
                new ToolItem(ToolItem.Action.PRIORITY_SUPPORT,
                        R.drawable.ic_tool_support, R.string.tools_priority_support, R.color.icon_dark),
                new ToolItem(ToolItem.Action.CHARGE_HISTORY,
                        R.drawable.ic_tool_history, R.string.tools_charge_history, R.color.icon_dark),
                new ToolItem(ToolItem.Action.X_CHARGE,
                        R.drawable.ic_tool_check, R.string.tools_x_charge, R.color.icon_dark),
                new ToolItem(ToolItem.Action.MORE,
                        R.drawable.ic_tool_more, R.string.tools_more, R.color.icon_dark));
    }

    private List<ToolItem> buildDetectTools() {
        return Arrays.asList(
                new ToolItem(ToolItem.Action.DEVICE_INFO,
                        R.drawable.ic_phone, R.string.tools_device_info, R.color.icon_accent),
                new ToolItem(ToolItem.Action.CLEAN_NOTIFICATION,
                        R.drawable.ic_tool_notification, R.string.tools_clean_notification, R.color.icon_accent),
                new ToolItem(ToolItem.Action.CLEAN_CLIPBOARD,
                        R.drawable.ic_tool_clipboard, R.string.tools_clean_clipboard, R.color.icon_accent),
                new ToolItem(ToolItem.Action.PHONE_TEMPERATURE,
                        R.drawable.ic_tool_snowflake, R.string.tools_phone_temperature, R.color.icon_accent),
                new ToolItem(ToolItem.Action.CHARGE_DETECT,
                        R.drawable.ic_tool_charge, R.string.tools_charge_detect, R.color.icon_accent),
                new ToolItem(ToolItem.Action.CPU_USAGE,
                        R.drawable.ic_tool_cpu, R.string.tools_cpu_usage, R.color.icon_accent));
    }

    // ==================== Vòng đời ====================

    @Override
    public void onResume() {
        super.onResume();
        monitor.addListener(this);
        loadSystemInfo();
        loadAd();
    }

    /**
     * Nạp quảng cáo cuối trang.
     *
     * <p>Nạp ở {@code onResume} chứ không phải lúc dựng màn: người dùng có thể vừa
     * mua VIP xong quay lại, khi đó khung quảng cáo phải biến mất ngay.
     */
    private void loadAd() {
        if (VipManager.get(requireContext()).isVip()) {
            AdManager.clearAds(binding.adContainer);
            return;
        }
        AdManager.loadBanner(requireActivity(), binding.adContainer);
    }

    @Override
    public void onPause() {
        super.onPause();
        monitor.removeListener(this);
    }

    // ==================== Cập nhật chỉ số ====================

    /** Đọc bộ nhớ và RAM ở thread nền rồi đổ ra header. */
    private void loadSystemInfo() {
        executors.execute(systemInfoProvider::getStorageInfo,
                result -> bindStat(binding == null ? null : binding.statStorage, result));

        executors.execute(systemInfoProvider::getRamInfo,
                result -> bindStat(binding == null ? null : binding.statRam, result));
    }

    private void bindStat(@Nullable ViewStatColumnBinding column, @Nullable StorageInfo info) {
        if (column == null || info == null || !info.hasData()) return;

        column.tvStatValue.setText(String.format(Locale.US, "%d%%", info.getUsedPercent()));
        column.tvStatDetail.setText(String.format(Locale.US, "%s/%s",
                FormatUtils.formatBytes(info.getUsedBytes()),
                FormatUtils.formatBytes(info.getTotalBytes())));
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        final float celsius = info.getTemperatureCelsius();
        binding.statTemperature.tvStatValue.setText(
                String.format(Locale.US, "%.0f ℃", celsius));
        binding.statTemperature.tvStatDetail.setText(
                String.format(Locale.US, "%.0f ℉", FormatUtils.celsiusToFahrenheit(celsius)));
    }

    // ==================== Điều hướng ====================

    @Override
    public void onToolClick(@NonNull ToolItem.Action action) {
        switch (action) {
            case DEVICE_INFO:
                PhoneDetailActivity.start(requireContext());
                break;

            case CHARGE_DETECT:
                CheckPowerActivity.start(requireContext());
                break;

            case CHARGE_HISTORY:
                ChargeHistoryActivity.start(requireContext());
                break;

            case CHARGE_ALARM:
                ChargeAlarmActivity.start(requireContext());
                break;

            case PHONE_TEMPERATURE:
                PhoneTemperatureActivity.start(requireContext());
                break;

            case CPU_USAGE:
                CpuUsageActivity.start(requireContext());
                break;

            case CLEAN_NOTIFICATION:
                NotificationCleanActivity.start(requireContext());
                break;

            case CLEAN_CLIPBOARD:
            case MORE:
                SettingsActivity.start(requireContext());
                break;

            case NO_ADS:
            case PRIORITY_SUPPORT:
                VipActivity.start(requireContext());
                break;

            case X_CHARGE:
                XChargeActivity.start(requireContext());
                break;

            default:
                showComingSoon();
                break;
        }
    }

    private void showComingSoon() {
        Toast.makeText(requireContext(), R.string.msg_coming_soon, Toast.LENGTH_SHORT).show();
    }
}
