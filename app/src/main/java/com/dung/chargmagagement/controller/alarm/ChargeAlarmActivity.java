package com.dung.chargmagagement.controller.alarm;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityChargeAlarmBinding;
import com.dung.chargmagagement.model.alarm.AlarmSettings;
import com.dung.chargmagagement.service.ChargeAlarmScheduler;

import java.util.Locale;

/**
 * Màn Báo động sạc: bật/tắt và điều chỉnh ba loại cảnh báo.
 *
 * <p>Thiết lập được lưu ngay khi người dùng thay đổi, không cần nút "Lưu" –
 * người dùng gạt công tắc rồi thoát ra là chuyện bình thường, bắt bấm lưu chỉ
 * làm mất thiết lập một cách khó hiểu.
 */
public class ChargeAlarmActivity extends BaseActivity<ActivityChargeAlarmBinding> {

    private AlarmSettings settings;

    /** Xin quyền thông báo trên Android 13+, nếu không cảnh báo sẽ bị chặn im lặng. */
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    binding.tvPermissionWarning.setVisibility(View.VISIBLE);
                    return;
                }
                // Vừa được cấp quyền: kiểm tra lại ngay. Lần kiểm tra lúc bật cảnh báo
                // chạy trong khi hộp thoại xin quyền còn trên màn hình, nên nếu pin đã
                // vượt ngưỡng từ trước thì lần đó không hiện được gì.
                ChargeAlarmScheduler.check(this);
            });

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, ChargeAlarmActivity.class));
    }

    @NonNull
    @Override
    protected ActivityChargeAlarmBinding onCreateBinding() {
        return ActivityChargeAlarmBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_charge_alarm);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());
        binding.tvExactAlarmWarning.setOnClickListener(v -> openExactAlarmSettings());

        settings = AlarmSettings.load(prefs);
        bindSettings();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng có thể vừa cấp quyền ở Cài đặt rồi quay lại
        updatePermissionWarning();
        // Đặt lại hẹn giờ: nếu quyền hẹn chính xác vừa được bật thì từ giờ mới nổ đúng
        // mốc, còn cái hẹn cũ vẫn là loại bị hệ thống dồn
        ChargeAlarmScheduler.check(this);
    }

    /** Đổ thiết lập đang lưu ra giao diện. */
    private void bindSettings() {
        binding.switchThreshold.setChecked(settings.isThresholdEnabled());
        binding.seekThreshold.setProgress(
                settings.getThresholdPercent() - AlarmSettings.MIN_THRESHOLD_PERCENT);
        updateThresholdLabel(settings.getThresholdPercent());

        binding.switchFull.setChecked(settings.isFullEnabled());

        binding.switchOverheat.setChecked(settings.isOverheatEnabled());
        binding.seekOverheat.setProgress(
                settings.getOverheatTemp() - AlarmSettings.MIN_OVERHEAT_TEMP);
        updateOverheatLabel(settings.getOverheatTemp());

        updateSeekBarStates();
        updatePermissionWarning();
    }

    private void setupListeners() {
        binding.switchThreshold.setOnCheckedChangeListener((view, checked) -> {
            settings = settings.withThreshold(checked, currentThresholdPercent());
            persist(checked);
        });

        binding.switchFull.setOnCheckedChangeListener((view, checked) -> {
            settings = settings.withFull(checked);
            persist(checked);
        });

        binding.switchOverheat.setOnCheckedChangeListener((view, checked) -> {
            settings = settings.withOverheat(checked, currentOverheatTemp());
            persist(checked);
        });

        binding.seekThreshold.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateThresholdLabel(AlarmSettings.MIN_THRESHOLD_PERCENT + progress);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Chỉ ghi khi người dùng nhả tay, tránh ghi liên tục lúc đang kéo
                settings = settings.withThreshold(
                        binding.switchThreshold.isChecked(), currentThresholdPercent());
                persist(false);
            }
        });

        binding.seekOverheat.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOverheatLabel(AlarmSettings.MIN_OVERHEAT_TEMP + progress);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                settings = settings.withOverheat(
                        binding.switchOverheat.isChecked(), currentOverheatTemp());
                persist(false);
            }
        });
    }

    private int currentThresholdPercent() {
        return AlarmSettings.MIN_THRESHOLD_PERCENT + binding.seekThreshold.getProgress();
    }

    private int currentOverheatTemp() {
        return AlarmSettings.MIN_OVERHEAT_TEMP + binding.seekOverheat.getProgress();
    }

    /**
     * Lưu thiết lập.
     *
     * @param justEnabled vừa bật một cảnh báo lên hay không – nếu có thì kiểm tra
     *                    quyền thông báo, vì bật cảnh báo mà không có quyền thì
     *                    người dùng sẽ không bao giờ nghe thấy gì
     */
    private void persist(boolean justEnabled) {
        settings.save(prefs);
        updateSeekBarStates();

        if (justEnabled) {
            requestNotificationPermissionIfNeeded();
        }
        updatePermissionWarning();

        // Kiểm tra ngay một lần rồi hẹn lần kế tiếp: bật cảnh báo trong lúc pin đã
        // vượt ngưỡng thì phải báo luôn, không chờ tới cái hẹn đầu tiên. Tắt hết
        // cảnh báo thì bên trong cũng lo việc huỷ hẹn.
        ChargeAlarmScheduler.onSettingsChanged(this);
    }

    /** Thanh trượt chỉ dùng được khi cảnh báo tương ứng đang bật. */
    private void updateSeekBarStates() {
        binding.seekThreshold.setEnabled(binding.switchThreshold.isChecked());
        binding.seekOverheat.setEnabled(binding.switchOverheat.isChecked());
    }

    private void updateThresholdLabel(int percent) {
        binding.tvThresholdValue.setText(String.format(Locale.US, "%d%%", percent));
    }

    private void updateOverheatLabel(int celsius) {
        binding.tvOverheatValue.setText(String.format(Locale.US, "%d℃", celsius));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (hasNotificationPermission()) return;

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updatePermissionWarning() {
        final boolean needWarning = settings.hasAnyEnabled() && !hasNotificationPermission();
        binding.tvPermissionWarning.setVisibility(needWarning ? View.VISIBLE : View.GONE);

        // Thiếu quyền hẹn giờ chính xác thì cảnh báo vẫn chạy, nhưng bị hệ thống dồn
        // lại và thường chỉ nổ khi người dùng mở app - phải nói rõ cho người dùng biết
        final boolean needExact = settings.hasAnyEnabled()
                && !ChargeAlarmScheduler.canScheduleExact(this);
        binding.tvExactAlarmWarning.setVisibility(needExact ? View.VISIBLE : View.GONE);
    }

    /** Mở trang "Chuông báo và lời nhắc" để người dùng bật hẹn giờ chính xác. */
    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("ChargeAlarm", "Không mở được trang hẹn giờ chính xác", e);
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** Rút gọn SeekBar.OnSeekBarChangeListener để khỏi phải viết hàm rỗng mọi nơi. */
    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }
    }
}
