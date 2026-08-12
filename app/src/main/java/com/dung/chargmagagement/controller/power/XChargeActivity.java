package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityXChargeBinding;
import com.dung.chargmagagement.databinding.ViewChargeStageBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.power.ChargeAdvisor;
import com.dung.chargmagagement.model.power.ChargeSpeed;
import com.dung.chargmagagement.model.power.ChargeStage;
import com.dung.chargmagagement.model.power.ChargeWarning;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.power.PowerDrainFeature;
import com.dung.chargmagagement.model.power.PowerOptimizer;
import com.dung.chargmagagement.model.power.PowerSaver;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.UsageCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Màn sạc tối ưu (X-Sạc).
 *
 * <p>Màn toàn cảnh để nhìn từ xa khi máy đang đặt cắm sạc: mức pin nằm trong một
 * vòng tròn chất lỏng cỡ lớn, kèm dòng nạp, nhiệt độ và giai đoạn sạc hiện tại.
 *
 * <p>Ngoài việc hiển thị, màn này còn <b>tắt bớt thứ tốn điện</b> trong lúc mở và
 * trả lại nguyên trạng khi thoát – xem {@link PowerSaver}.
 *
 * <p>Màn hình được giữ sáng bằng {@code FLAG_KEEP_SCREEN_ON} thay vì xin quyền
 * WAKE_LOCK: cờ này chỉ có tác dụng khi màn hình đang hiển thị và hệ thống tự thu
 * hồi lúc người dùng rời màn, nên không có nguy cơ giữ máy thức vô hạn.
 */
public class XChargeActivity extends BaseActivity<ActivityXChargeBinding>
        implements BatteryMonitor.Listener {

    /** Độ sáng cửa sổ khi bật chế độ tối ưu (0..1). */
    private static final float WINDOW_DIM_BRIGHTNESS = 0.05f;

    /**
     * Giữ màn hình sáng bấy nhiêu lâu kể từ lần chạm cuối, rồi thả cho máy tự ngủ.
     *
     * <p>Màn hình là thứ ngốn điện nhất khi máy đang bật, nên giữ nó sáng suốt đêm
     * đi ngược lại mục đích của chính màn này. Nhưng tắt ngay lập tức thì người dùng
     * không kịp đọc. Một phút là đủ để xem xong số liệu, sau đó máy ngủ và sạc ở
     * tốc độ cao nhất; chạm vào màn hình là tính lại từ đầu.
     */
    private static final long SCREEN_ON_TIMEOUT_MS = 60_000L;

    private final Handler screenHandler = new Handler(Looper.getMainLooper());

    private final Runnable releaseScreenTask = () ->
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    private BatteryMonitor monitor;
    private BatteryRepository repository;
    private PowerOptimizer optimizer;

    /** Trạng thái máy trước khi tối ưu; null nghĩa là chưa áp dụng. */
    @Nullable
    private PowerSaver.SavedState savedState;

    private int usableCapacityMah = BatteryInfo.UNKNOWN_INT;

    /** Tập cảnh báo đang hiển thị, để biết khi nào cần vẽ lại. */
    private List<ChargeWarning> shownWarnings = Collections.emptyList();

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, XChargeActivity.class));
    }

    @NonNull
    @Override
    protected ActivityXChargeBinding onCreateBinding() {
        return ActivityXChargeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(this);
        repository = BatteryRepository.get(this);
        optimizer = new PowerOptimizer(this);

        setupStages();
        binding.btnExit.setOnClickListener(v -> finish());
        binding.btnGrantBrightness.setOnClickListener(v -> openWriteSettings());
        binding.chipDrain.setOnClickListener(v -> CheckPowerActivity.start(this));

        loadUsableCapacity();
    }

    /** Ba khối giai đoạn dùng chung một layout nên phải gán nội dung bằng code. */
    private void setupStages() {
        bindStageLabel(binding.stageFast, ChargeStage.FAST);
        bindStageLabel(binding.stageCycle, ChargeStage.CYCLE);
        bindStageLabel(binding.stageTrickle, ChargeStage.TRICKLE);
    }

    private void bindStageLabel(@NonNull ViewChargeStageBinding stage,
                                @NonNull ChargeStage value) {
        stage.imgStage.setImageResource(value.getIconRes());
        stage.tvStage.setText(value.getLabelRes());
    }

    private void loadUsableCapacity() {
        executors.execute(repository::getUsableCapacityMah, result -> {
            if (result != null) usableCapacityMah = result;
        });
    }

    // ==================== Tối ưu khi sạc ====================

    @Override
    protected void onStart() {
        super.onStart();
        applyOptimizations();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Rời màn bằng bất kỳ cách nào cũng phải trả lại nguyên trạng cho người dùng
        restoreOptimizations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitor.addListener(this);

        // Người dùng có thể vừa cấp quyền đổi độ sáng rồi quay lại
        if (savedState != null && !savedState.isBrightnessChanged()
                && PowerSaver.canWriteSystemSettings(this)) {
            restoreOptimizations();
            applyOptimizations();
        }
        updateBrightnessButton();
        loadDrainCount();
        keepScreenOnTemporarily();
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);

        // Không để lại tác vụ hẹn giờ trỏ vào cửa sổ của một màn đã rời tiền cảnh
        screenHandler.removeCallbacks(releaseScreenTask);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnTemporarily();
    }

    /** Bật lại đồng hồ đếm ngược giữ màn hình sáng. */
    private void keepScreenOnTemporarily() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenHandler.removeCallbacks(releaseScreenTask);
        screenHandler.postDelayed(releaseScreenTask, SCREEN_ON_TIMEOUT_MS);
    }

    /**
     * Tắt các thứ tốn điện và hạ độ sáng.
     *
     * <p>Độ sáng cửa sổ được hạ trước tiên vì việc này <b>không cần quyền gì</b> và
     * có tác dụng ngay: màn hình là thứ ngốn điện nhất khi máy đang bật sáng.
     */
    private void applyOptimizations() {
        if (savedState != null) return; // đã áp dụng rồi

        dimAppWindow();
        savedState = PowerSaver.apply(this);
    }

    private void restoreOptimizations() {
        if (savedState == null) return;

        PowerSaver.restore(this, savedState);
        savedState = null;
        restoreAppWindowBrightness();
    }

    /** Hạ độ sáng riêng cho cửa sổ của app, không đụng tới cài đặt hệ thống. */
    private void dimAppWindow() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WINDOW_DIM_BRIGHTNESS;
        getWindow().setAttributes(params);
    }

    private void restoreAppWindowBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);
    }

    private void updateBrightnessButton() {
        binding.btnGrantBrightness.setVisibility(
                PowerSaver.canWriteSystemSettings(this) ? View.GONE : View.VISIBLE);
    }

    /**
     * Đếm số mục còn tiêu điện mà app không tự tắt được.
     *
     * <p>Android chặn ứng dụng tắt Wi-Fi, Bluetooth và định vị từ bản 10, nên chỗ
     * này chỉ đếm rồi dẫn người dùng sang màn Phát hiện sạc để tự tắt từng mục.
     */
    private void loadDrainCount() {
        executors.execute(() -> optimizer.scan(monitor.getLastInfo()), result -> {
            if (binding == null || result == null) return;

            int count = 0;
            for (DrainStatus status : result) {
                if (!status.isActive() || !status.getFeature().hasSettingsPage()) continue;
                // Đồng bộ đã được app tắt hộ nên không tính vào phần cần làm tay
                if (status.getFeature() == PowerDrainFeature.AUTO_SYNC
                        && savedState != null && savedState.isAutoSyncChanged()) {
                    continue;
                }
                count++;
            }

            binding.tvDrainCount.setText(String.valueOf(count));
            binding.chipDrain.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    /** Mở trang cấp quyền đổi cài đặt hệ thống (để hạ được độ sáng toàn máy). */
    private void openWriteSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));

        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("XCharge", "Không mở được trang cấp quyền", e);
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Cập nhật số liệu ====================

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        bindHeader(info, smoothedMa);
        bindStages(info);
        bindRemaining(info, smoothedMa);
        bindWarnings(info, smoothedMa);
    }

    // ==================== Nhắc nhở bất thường ====================

    /**
     * Dựng lại danh sách cảnh báo.
     *
     * <p>Chỉ vẽ lại khi tập cảnh báo <b>thật sự đổi</b>. Không kiểm tra chỗ này thì
     * cứ hai giây các dòng chữ lại bị gỡ ra gắn vào, gây nháy và ăn CPU vô ích
     * trong chính màn hình đang quảng cáo là tối ưu điện năng.
     */
    private void bindWarnings(@NonNull BatteryInfo info, int smoothedMa) {
        final List<ChargeWarning> warnings = ChargeAdvisor.analyze(info, smoothedMa);
        if (warnings.equals(shownWarnings)) return;

        shownWarnings = warnings;
        binding.warningContainer.removeAllViews();

        if (warnings.isEmpty()) {
            addWarningLine(getString(R.string.xcharge_all_good), R.color.green_accent, true);
            return;
        }

        for (ChargeWarning warning : warnings) {
            addWarningLine(getString(warning.getMessageRes()), warning.getColorRes(), false);
        }
    }

    private void addWarningLine(@NonNull String text, @ColorRes int colorRes, boolean good) {
        TextView line = new TextView(this);
        line.setText(getString(good ? R.string.xcharge_line_done : R.string.xcharge_line_warn,
                text));
        line.setTextSize(14f);
        line.setTextColor(ContextCompat.getColor(this, colorRes));
        line.setLineSpacing(0f, 1.1f);

        final int padding = getResources().getDimensionPixelSize(R.dimen.space_xs);
        line.setPadding(0, padding, 0, padding);
        binding.warningContainer.addView(line);
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        loadUsableCapacity();
        loadDrainCount();
    }

    private void bindHeader(@NonNull BatteryInfo info, int smoothedMa) {
        binding.tvPercent.setText(String.format(Locale.getDefault(), "%d%%", info.getPercent()));
        binding.liquidBattery.setPercent(info.getPercent());

        final boolean charging = info.getPlugType().isPlugged() && smoothedMa > 0;
        final ChargeSpeed speed = charging
                ? ChargeSpeed.fromCurrent(smoothedMa)
                : ChargeSpeed.UNKNOWN;

        binding.tvSpeed.setText(info.getPlugType().isPlugged()
                ? speed.getLabelRes()
                : R.string.speed_not_charging);
        binding.tvSpeed.setTextColor(ContextCompat.getColor(this, speed.getColorRes()));

        binding.tvCurrent.setText(charging
                ? String.format(Locale.US, "%d mA", smoothedMa)
                : getString(R.string.value_placeholder));

        // Hai thang đo cùng lúc: người dùng quen ℉ vẫn đọc được mà không phải quy đổi
        final float celsius = info.getTemperatureCelsius();
        binding.tvTemperature.setText(String.format(Locale.getDefault(), "%.0f℃/ %.0f℉",
                celsius, FormatUtils.celsiusToFahrenheit(celsius)));
    }

    /** Làm nổi giai đoạn đang diễn ra, hai giai đoạn còn lại để mờ. */
    private void bindStages(@NonNull BatteryInfo info) {
        final ChargeStage current = ChargeStage.fromPercent(info.getPercent());

        applyStageState(binding.stageFast, current == ChargeStage.FAST);
        applyStageState(binding.stageCycle, current == ChargeStage.CYCLE);
        applyStageState(binding.stageTrickle, current == ChargeStage.TRICKLE);
    }

    private void applyStageState(@NonNull ViewChargeStageBinding stage, boolean active) {
        final int color = ContextCompat.getColor(this,
                active ? R.color.text_on_primary : R.color.stage_inactive);

        stage.tvStage.setTextColor(color);
        ImageViewCompat.setImageTintList(stage.imgStage, ColorStateList.valueOf(color));

        // Viền vòng tròn nằm ở background nên phải nhuộm riêng. Bắt buộc gọi
        // mutate(): ba vòng tròn được nạp từ cùng một tệp drawable nên dùng chung
        // ConstantState – nhuộm một cái là cả ba đổi màu theo, chỉ còn lại màu của
        // lần gọi cuối cùng.
        stage.imgStage.getBackground().mutate().setTint(color);
    }

    private void bindRemaining(@NonNull BatteryInfo info, int smoothedMa) {
        if (!info.getPlugType().isPlugged()) {
            binding.tvRemaining.setText(R.string.xcharge_plug_in);
            return;
        }

        final long remainingMs = UsageCalculator.estimateTimeToFull(
                info.getPercent(), smoothedMa, usableCapacityMah);
        binding.tvRemaining.setText(remainingMs > 0
                ? getString(R.string.xcharge_remaining, FormatUtils.formatDuration(remainingMs))
                : getString(R.string.xcharge_calculating));
    }
}
