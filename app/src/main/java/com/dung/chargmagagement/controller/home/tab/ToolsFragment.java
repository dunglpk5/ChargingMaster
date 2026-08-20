package com.dung.chargmagagement.controller.home.tab;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.BuildConfig;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.controller.adapter.ToolAdapter;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.controller.detail.PhoneDetailActivity;
import com.dung.chargmagagement.controller.settings.SettingsActivity;
import com.dung.chargmagagement.controller.tools.BatteryMonitorActivity;
import com.dung.chargmagagement.controller.tools.StorageCleanActivity;
import com.dung.chargmagagement.controller.tools.ToolLauncher;
import com.dung.chargmagagement.controller.vip.VipActivity;
import com.dung.chargmagagement.model.settings.AppLinks;
import com.dung.chargmagagement.databinding.FragmentToolsBinding;
import com.dung.chargmagagement.databinding.ItemMenuRowBinding;
import com.dung.chargmagagement.databinding.ViewSettingsSwitchRowBinding;
import com.dung.chargmagagement.databinding.ViewStatRingCardBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.device.StorageInfo;
import com.dung.chargmagagement.model.device.SystemInfoProvider;
import com.dung.chargmagagement.service.BatteryLogService;
import com.dung.chargmagagement.model.ui.ToolCatalog;
import com.dung.chargmagagement.model.ui.ToolItem;

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

    /** Lưới công cụ chính rộng hơn: tám mục chia đều bốn cột thành hai hàng. */
    private static final int QUICK_SPAN_COUNT = 4;

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

        setupStatCards();
        setupToolGrid(binding.rvQuickTools, QUICK_SPAN_COUNT, ToolCatalog.quickTools());
        setupToolGrid(binding.rvGeneralTools, GRID_SPAN_COUNT, ToolCatalog.generalTools());
        setupLogSwitches();
        setupMenuRows();

        binding.btnRemoveAds.setOnClickListener(v -> VipActivity.start(requireContext()));
        binding.btnMore.setOnClickListener(v -> SettingsActivity.start(requireContext()));
    }

    /**
     * Ba thẻ chỉ số dùng chung một layout nên phải gán màu, nhãn và hành động
     * bằng code. Mỗi thẻ một màu riêng để phân biệt được ngay từ xa.
     */
    private void setupStatCards() {
        bindStatCard(binding.cardStorage, R.string.tools_stat_storage,
                R.string.tools_action_scan, R.color.stat_storage,
                () -> StorageCleanActivity.start(requireContext()));

        bindStatCard(binding.cardRam, R.string.tools_stat_ram,
                R.string.tools_action_details, R.color.teal_primary,
                () -> PhoneDetailActivity.start(requireContext()));

        bindStatCard(binding.cardBattery, R.string.tools_stat_battery,
                R.string.tools_action_monitor, R.color.stat_battery,
                () -> BatteryMonitorActivity.start(requireContext()));
    }

    private void bindStatCard(@NonNull ViewStatRingCardBinding card, int titleRes,
                              int actionRes, @ColorRes int colorRes, @NonNull Runnable onAction) {
        final int color = ContextCompat.getColor(requireContext(), colorRes);

        card.tvRingTitle.setText(titleRes);
        card.tvRingPercent.setTextColor(color);
        card.ringProgress.setProgressColor(color);

        card.btnRingAction.setText(actionRes);
        card.btnRingAction.setBackgroundResource(R.drawable.bg_pill_primary);
        // Nhuộm nền nút thay vì tạo ba drawable gần giống nhau. Dùng backgroundTintList
        // của View chứ không setTint lên chính drawable: ba nút nạp từ cùng một tệp
        // nên dùng chung ConstantState, nhuộm trực tiếp là cả ba đổi màu theo.
        card.btnRingAction.setBackgroundTintList(ColorStateList.valueOf(color));
        card.btnRingAction.setOnClickListener(v -> onAction.run());
    }

    private void setupToolGrid(@NonNull RecyclerView recyclerView, int spanCount,
                               @NonNull List<ToolItem> items) {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        recyclerView.setHasFixedSize(true);

        ToolAdapter adapter = new ToolAdapter(items);
        adapter.setOnToolClickListener(this);
        recyclerView.setAdapter(adapter);
    }

    // ==================== Ba công tắc ghi nền ====================

    /**
     * Ba phần của việc ghi lịch sử pin, bật/tắt độc lập.
     *
     * <p>Gán trạng thái trước rồi mới gắn listener: {@code setChecked} cũng kích hoạt
     * callback, gắn ngược thứ tự là vừa mở tab đã tự khởi động service.
     */
    private void setupLogSwitches() {
        bindLogSwitch(binding.rowLogChart, R.string.settings_log_chart_desc,
                BatteryLogService.isChartEnabled(requireContext()),
                PrefManager.KEY_LOG_CHART);

        bindLogSwitch(binding.rowLogScreen, R.string.settings_log_screen_desc,
                BatteryLogService.isScreenStatsEnabled(requireContext()),
                PrefManager.KEY_LOG_SCREEN);

        bindLogSwitch(binding.rowLogDetails, R.string.settings_log_details_desc,
                BatteryLogService.isDetailedNotification(requireContext()),
                PrefManager.KEY_LOG_DETAILS);
    }

    private void bindLogSwitch(@NonNull ViewSettingsSwitchRowBinding row, int descRes,
                               boolean checked, @NonNull String prefKey) {
        row.tvSwitchDesc.setText(descRes);
        row.switchToggle.setChecked(checked);
        row.switchToggle.setOnCheckedChangeListener((button, isChecked) ->
                BatteryLogService.setFlag(requireContext(), prefKey, isChecked));
    }

    /** Bốn dòng của nhóm "Khác" dùng chung một layout nên gán nội dung bằng code. */
    private void setupMenuRows() {
        bindMenuRow(binding.rowSettings, R.drawable.ic_setting, R.string.settings_title,
                () -> SettingsActivity.start(requireContext()));
        bindMenuRow(binding.rowFeedback, R.drawable.ic_feedback, R.string.tools_feedback,
                this::openFeedback);
        bindMenuRow(binding.rowPrivacyPolicy, R.drawable.ic_privacy_policy, R.string.tools_privacy_policy,
                this::openPrivacyPolicy);
        bindMenuRow(binding.rowAboutUs, R.drawable.ic_about_us, R.string.tools_about_us,
                this::showAboutUs);
    }

    private void bindMenuRow(@NonNull ItemMenuRowBinding row, int iconRes, int labelRes,
                             @NonNull Runnable onClick) {
        row.imgIcon.setImageResource(iconRes);
        row.tvLabel.setText(labelRes);
        row.getRoot().setOnClickListener(v -> onClick.run());
    }

    // ==================== Vòng đời ====================

    @Override
    public void onResume() {
        super.onResume();
        monitor.addListener(this);
        loadSystemInfo();
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
                result -> bindStat(binding == null ? null : binding.cardStorage, result));

        executors.execute(systemInfoProvider::getRamInfo,
                result -> bindStat(binding == null ? null : binding.cardRam, result));
    }

    private void bindStat(@Nullable ViewStatRingCardBinding card, @Nullable StorageInfo info) {
        if (card == null || info == null || !info.hasData()) return;

        card.ringProgress.setPercent(info.getUsedPercent());
        card.tvRingPercent.setText(String.format(Locale.US, "%d%%", info.getUsedPercent()));
        card.tvRingDetail.setText(String.format(Locale.US, "%s/%s",
                FormatUtils.formatBytes(info.getUsedBytes()),
                FormatUtils.formatBytes(info.getTotalBytes())));
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        binding.cardBattery.ringProgress.setPercent(info.getPercent());
        binding.cardBattery.tvRingPercent.setText(
                String.format(Locale.US, "%d%%", info.getPercent()));
        binding.cardBattery.tvRingDetail.setText(
                String.format(Locale.US, "%.0f℃", info.getTemperatureCelsius()));
    }

    // ==================== Điều hướng ====================

    @Override
    public void onToolClick(@NonNull ToolItem.Action action) {
        ToolLauncher.launch(requireContext(), action);
    }

    // ==================== Nhóm Khác ====================

    /** Mở app Gửi thư với địa chỉ và tiêu đề điền sẵn. */
    private void openFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + AppLinks.FEEDBACK_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

        if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
            Toast.makeText(requireContext(), R.string.feedback_no_email_app,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("ToolsFragment", "Không mở được app gửi thư", e);
        }
    }

    private void openPrivacyPolicy() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.PRIVACY_POLICY_URL));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("ToolsFragment", "Không mở được trình duyệt", e);
            Toast.makeText(requireContext(), R.string.check_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutUs() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tools_about_us)
                .setMessage(getString(R.string.about_us_message,
                        getString(R.string.app_name), BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

}
