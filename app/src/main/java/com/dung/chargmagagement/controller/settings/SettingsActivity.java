package com.dung.chargmagagement.controller.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.dung.chargmagagement.BuildConfig;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.LocaleManager;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.alarm.ChargeAlarmActivity;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.databinding.ActivitySettingsBinding;
import com.dung.chargmagagement.databinding.ViewSettingsSwitchRowBinding;
import com.dung.chargmagagement.service.BatteryLogService;

/**
 * Màn Cài đặt, mở từ nút "..." ở Trang chủ và ô "Thêm" ở tab Công cụ.
 */
public class SettingsActivity extends BaseActivity<ActivitySettingsBinding> {

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }

    @NonNull
    @Override
    protected ActivitySettingsBinding onCreateBinding() {
        return ActivitySettingsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.settings_title);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        binding.rowLanguage.setOnClickListener(v -> LanguageActivity.start(this));
        binding.rowAlarm.setOnClickListener(v -> ChargeAlarmActivity.start(this));
        binding.rowNotificationAccess.setOnClickListener(v -> openNotificationAccessSettings());
        binding.rowClearClipboard.setOnClickListener(v -> confirmClearClipboard());
        binding.rowAppInfo.setOnClickListener(v -> openAppSettings());

        binding.tvVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        updateLanguageSummary();
        setupBatteryLogging();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLanguageSummary();
    }

    // ==================== Ghi lịch sử pin nền ====================

    /**
     * Ba công tắc của việc ghi pin dưới nền, bật/tắt độc lập.
     *
     * <p>Dịch vụ tự khởi động khi có ít nhất một phần ghi dữ liệu được bật, và tự dừng
     * khi tắt hết - người dùng không phải nhớ thêm một công tắc tổng nào.
     */
    private void setupBatteryLogging() {
        bindSwitch(binding.rowLogChart,
                R.string.settings_log_chart, R.string.settings_log_chart_desc,
                PrefManager.KEY_LOG_CHART, BatteryLogService.isChartEnabled(this));

        bindSwitch(binding.rowLogScreen,
                R.string.settings_log_screen, R.string.settings_log_screen_desc,
                PrefManager.KEY_LOG_SCREEN, BatteryLogService.isScreenStatsEnabled(this));

        bindSwitch(binding.rowLogDetails,
                R.string.settings_log_details, R.string.settings_log_details_desc,
                PrefManager.KEY_LOG_DETAILS, BatteryLogService.isDetailedNotification(this));
    }

    /**
     * Gán nhãn và trạng thái cho một hàng công tắc.
     *
     * <p>Gán setChecked <b>trước</b> khi gắn listener: nó cũng kích hoạt callback, làm
     * ngược thứ tự là vừa mở màn Cài đặt đã tự khởi động service.
     */
    private void bindSwitch(@NonNull ViewSettingsSwitchRowBinding row,
                            int titleRes, int descRes,
                            @NonNull String prefKey, boolean checked) {
        row.tvSwitchTitle.setText(titleRes);
        row.tvSwitchDesc.setText(descRes);
        row.switchToggle.setChecked(checked);
        row.switchToggle.setOnCheckedChangeListener(
                (button, isChecked) -> BatteryLogService.setFlag(this, prefKey, isChecked));
    }

    private void updateLanguageSummary() {
        final String current = LocaleManager.getCurrentLanguage(prefs);
        final int labelRes;
        if (LocaleManager.LANG_VI.equals(current)) {
            labelRes = R.string.language_vi;
        } else if (LocaleManager.LANG_EN.equals(current)) {
            labelRes = R.string.language_en;
        } else {
            labelRes = R.string.language_system;
        }
        binding.tvLanguageValue.setText(labelRes);
    }

    // ==================== Dọn dẹp clipboard ====================

    /**
     * Hỏi trước khi xoá: nội dung clipboard có thể là thứ người dùng vừa sao chép
     * và đang định dán, xoá nhầm là mất luôn.
     */
    private void confirmClearClipboard() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tools_clean_clipboard)
                .setMessage(R.string.settings_clipboard_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> clearClipboard())
                .show();
    }

    private void clearClipboard() {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            toast(R.string.settings_clipboard_failed);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip();
            } else {
                // Android 8 chưa có clearPrimaryClip: ghi đè bằng nội dung rỗng
                manager.setPrimaryClip(ClipData.newPlainText("", ""));
            }
            toast(R.string.settings_clipboard_cleared);
        } catch (Exception e) {
            Logger.e("Settings", "Không xoá được clipboard", e);
            toast(R.string.settings_clipboard_failed);
        }
    }

    // ==================== Điều hướng hệ thống ====================

    /** Mở trang cấp quyền đọc thông báo, cần cho tính năng dọn dẹp thông báo. */
    private void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startSafely(intent);
    }

    /** Mở trang thông tin ứng dụng trong Cài đặt hệ thống. */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startSafely(intent);
    }

    private void startSafely(@NonNull Intent intent) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            toast(R.string.check_settings_unavailable);
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("Settings", "Không mở được trang cài đặt", e);
            toast(R.string.check_settings_unavailable);
        }
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}
