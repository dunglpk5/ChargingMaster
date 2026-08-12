package com.dung.chargmagagement.controller.home.tab;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.BuildConfig;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.common.Logger;
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
import com.dung.chargmagagement.model.settings.AppLinks;
import com.dung.chargmagagement.model.vip.VipManager;
import com.dung.chargmagagement.databinding.FragmentToolsBinding;
import com.dung.chargmagagement.databinding.ItemMenuRowBinding;
import com.dung.chargmagagement.databinding.ViewStatColumnBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.device.StorageInfo;
import com.dung.chargmagagement.model.device.SystemInfoProvider;
import com.dung.chargmagagement.model.ui.ToolItem;

import java.io.File;
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
        setupToolGrid(binding.rvGeneralTools, buildGeneralTools());
        setupMenuRows();

        binding.tvAppVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
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

    private List<ToolItem> buildGeneralTools() {
        return Arrays.asList(
                new ToolItem(ToolItem.Action.MANAGE_APPS,
                        R.drawable.ic_logo_android, R.string.tools_manage_apps, R.color.icon_accent),
                new ToolItem(ToolItem.Action.SHORTCUT,
                        R.drawable.ic_xcharge, R.string.tools_shortcut, R.color.icon_accent),
                new ToolItem(ToolItem.Action.CLEAR_CACHE,
                        R.drawable.ic_clear_cache, R.string.tools_clear_cache, R.color.icon_accent));
    }

    /** Bốn dòng của nhóm "Khác" dùng chung một layout nên gán nội dung bằng code. */
    private void setupMenuRows() {
        bindMenuRow(binding.rowSettings, R.drawable.ic_gear, R.string.settings_title,
                () -> SettingsActivity.start(requireContext()));
        bindMenuRow(binding.rowFeedback, R.drawable.ic_feedback, R.string.tools_feedback,
                this::openFeedback);
        bindMenuRow(binding.rowPrivacyPolicy, R.drawable.ic_help_circle, R.string.tools_privacy_policy,
                this::openPrivacyPolicy);
        bindMenuRow(binding.rowAboutUs, R.drawable.ic_help_circle, R.string.tools_about_us,
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

            case MANAGE_APPS:
                openAppManagement();
                break;

            case SHORTCUT:
                pinXChargeShortcut();
                break;

            case CLEAR_CACHE:
                confirmClearCache();
                break;

            default:
                showComingSoon();
                break;
        }
    }

    private void showComingSoon() {
        Toast.makeText(requireContext(), R.string.msg_coming_soon, Toast.LENGTH_SHORT).show();
    }

    // ==================== Nhóm Công cụ ====================

    /** Mở màn Quản lý ứng dụng của hệ thống, nơi liệt kê mọi app đã cài. */
    private void openAppManagement() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        startSettingsIntent(intent);
    }

    /**
     * Ghim một biểu tượng X-Sạc ra màn hình chính.
     *
     * <p>Dùng {@link ShortcutManagerCompat} thay vì gọi thẳng API: lớp compat tự lo
     * phần khác biệt giữa các phiên bản Android, kể cả launcher không hỗ trợ ghim
     * (khi đó {@code isRequestPinShortcutSupported} trả về false và ta báo cho
     * người dùng biết thay vì im lặng không làm gì).
     */
    private void pinXChargeShortcut() {
        final Resources res = requireContext().getResources();
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
            Toast.makeText(requireContext(), R.string.check_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent launchIntent = new Intent(requireContext(), XChargeActivity.class);
        launchIntent.setAction(Intent.ACTION_VIEW);

        ShortcutInfoCompat shortcut =
                new ShortcutInfoCompat.Builder(requireContext(), "x_charge")
                        .setShortLabel(res.getString(R.string.tools_x_charge))
                        .setIcon(IconCompat.createWithResource(requireContext(), R.drawable.ic_xcharge))
                        .setIntent(launchIntent)
                        .build();

        ShortcutManagerCompat.requestPinShortcut(requireContext(), shortcut, null);
    }

    /** Hỏi trước khi xoá: dữ liệu cache không quan trọng nhưng vẫn nên xin phép. */
    private void confirmClearCache() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tools_clear_cache)
                .setMessage(R.string.cache_clear_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> clearCache())
                .show();
    }

    /**
     * Xoá thư mục cache của riêng app.
     *
     * <p>Chỉ xoá {@code cacheDir} của chính mình – đây là toàn bộ những gì một app
     * thường được phép đụng vào; không có API công khai nào cho phép app dọn cache
     * của ứng dụng khác.
     */
    private void clearCache() {
        executors.execute(() -> deleteRecursively(requireContext().getCacheDir()), success -> {
            if (binding == null) return;
            Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private static boolean deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        return file.delete();
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

    private void startSettingsIntent(@NonNull Intent intent) {
        if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
            Toast.makeText(requireContext(), R.string.check_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("ToolsFragment", "Không mở được trang cài đặt", e);
            Toast.makeText(requireContext(), R.string.check_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
