package com.dung.chargmagagement.controller.alarm;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityAlarmRingBinding;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.service.AlarmPlayer;
import com.dung.chargmagagement.service.ChargeAlarmNotifier;

import java.util.Locale;

/**
 * Màn báo động, hiện toàn màn hình khi một cảnh báo sạc được kích hoạt.
 *
 * <p>Màn này phải hiện được <b>cả khi máy đang khoá và màn hình đang tắt</b> – đó
 * là tình huống phổ biến nhất: người dùng cắm sạc rồi để đó đi ngủ. Vì vậy dùng
 * {@code setShowWhenLocked} và {@code setTurnScreenOn} (từ Android 8.1), kết hợp
 * với thông báo có {@code fullScreenIntent} để hệ thống cho phép bật lên.
 *
 * <p>Chuông tự tắt khi người dùng rời màn hình, kể cả bằng nút Back – không để
 * tình huống thoát ra mà tiếng vẫn kêu.
 */
public class AlarmRingActivity extends BaseActivity<ActivityAlarmRingBinding>
        implements BatteryMonitor.Listener {

    private static final String EXTRA_TYPE = "alarm_type";
    private static final String EXTRA_PERCENT = "percent";

    private BatteryMonitor monitor;

    /** Tạo intent mở màn báo động cho một loại cảnh báo. */
    @NonNull
    public static Intent createIntent(@NonNull Context context,
                                      @NonNull ChargeAlarmChecker.AlarmType type,
                                      int percent) {
        Intent intent = new Intent(context, AlarmRingActivity.class);
        intent.putExtra(EXTRA_TYPE, type.name());
        intent.putExtra(EXTRA_PERCENT, percent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return intent;
    }

    @NonNull
    @Override
    protected ActivityAlarmRingBinding onCreateBinding() {
        return ActivityAlarmRingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setupLockScreenFlags();
        super.onCreate(savedInstanceState);
    }

    /** Cho phép hiện đè lên màn khoá và tự bật màn hình. */
    private void setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(this);

        bindAlarm(getIntent());
        binding.btnDismiss.setOnClickListener(v -> dismiss());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // Cảnh báo khác ập tới lúc màn này đang mở: cập nhật nội dung tại chỗ
        setIntent(intent);
        bindAlarm(intent);
    }

    private void bindAlarm(@Nullable Intent intent) {
        final ChargeAlarmChecker.AlarmType type = parseType(intent);
        final int percent = intent == null ? 0 : intent.getIntExtra(EXTRA_PERCENT, 0);

        binding.tvTitle.setText(type.getTitleRes());
        binding.tvMessage.setText(getString(type.getMessageRes(), percent));

        // Quá nhiệt là vấn đề an toàn nên dùng nền đỏ cho khác hẳn
        final int backgroundRes = type == ChargeAlarmChecker.AlarmType.OVERHEAT
                ? R.color.state_danger
                : R.color.state_warning;
        binding.rootAlarm.setBackgroundColor(ContextCompat.getColor(this, backgroundRes));
    }

    @NonNull
    private ChargeAlarmChecker.AlarmType parseType(@Nullable Intent intent) {
        if (intent == null) return ChargeAlarmChecker.AlarmType.THRESHOLD;
        try {
            return ChargeAlarmChecker.AlarmType.valueOf(intent.getStringExtra(EXTRA_TYPE));
        } catch (Exception e) {
            return ChargeAlarmChecker.AlarmType.THRESHOLD;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitor.addListener(this);

        // Chuông có thể đã được service bật; nếu chưa thì bật ở đây để màn hình
        // mở trực tiếp (ví dụ từ thông báo) vẫn kêu
        if (!AlarmPlayer.isPlaying()) {
            AlarmPlayer.start(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Rời màn bằng bất kỳ cách nào cũng phải tắt chuông
        AlarmPlayer.stop();
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        final String current = smoothedMa == BatteryInfo.UNKNOWN_INT
                ? getString(R.string.value_placeholder)
                : String.format(Locale.US, "%d mA", Math.abs(smoothedMa));

        binding.tvBatteryInfo.setText(String.format(Locale.getDefault(), "%d%%  ·  %s  ·  %s",
                info.getPercent(),
                FormatUtils.formatTemperature(info.getTemperatureCelsius()),
                current));
    }

    private void dismiss() {
        AlarmPlayer.stop();
        new ChargeAlarmNotifier(this).cancel();
        finish();
    }
}
