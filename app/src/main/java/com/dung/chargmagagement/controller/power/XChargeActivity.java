package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityXChargeBinding;
import com.dung.chargmagagement.databinding.ViewStatColumnBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.power.ChargeSpeed;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.power.PowerDrainFeature;
import com.dung.chargmagagement.model.power.PowerOptimizer;
import com.dung.chargmagagement.model.power.PowerSaver;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.UsageCalculator;

import java.util.Locale;

/**
 * Màn sạc tối ưu (X-Sạc).
 *
 * <p>Theo dõi phiên sạc theo thời gian thực với số liệu cỡ lớn để nhìn được từ xa
 * khi máy đang đặt cắm sạc, đồng thời cảnh báo khi phát hiện bất thường về nhiệt
 * độ hoặc dòng điện.
 *
 * <p>Màn hình được giữ sáng bằng {@code FLAG_KEEP_SCREEN_ON} thay vì xin quyền
 * WAKE_LOCK: cờ này chỉ có tác dụng khi màn hình đang hiển thị và hệ thống tự thu
 * hồi lúc người dùng rời màn, nên không có nguy cơ giữ máy thức vô hạn.
 */
public class XChargeActivity extends BaseActivity<ActivityXChargeBinding>
        implements BatteryMonitor.Listener {

    /** Ngưỡng cảnh báo nhiệt độ khi đang sạc (℃). */
    private static final float WARN_TEMPERATURE = 41f;

    /** Dòng nạp thấp bất thường (mA) – thường do củ sạc hoặc cáp kém. */
    private static final int WARN_LOW_CURRENT = 400;

    /** Độ sáng cửa sổ khi bật chế độ tối ưu (0..1). */
    private static final float WINDOW_DIM_BRIGHTNESS = 0.05f;

    private BatteryMonitor monitor;
    private BatteryRepository repository;
    private PowerOptimizer optimizer;

    /** Trạng thái máy trước khi tối ưu; null nghĩa là chưa áp dụng. */
    @Nullable
    private PowerSaver.SavedState savedState;

    /** Mốc thời gian mở màn, dùng để tính thời lượng phiên đang xem. */
    private long sessionStartMs;

    private int usableCapacityMah = BatteryInfo.UNKNOWN_INT;

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
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_x_charge);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        // Giữ màn hình sáng để người dùng theo dõi được suốt quá trình sạc
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        monitor = BatteryMonitor.get(this);
        repository = BatteryRepository.get(this);
        optimizer = new PowerOptimizer(this);
        sessionStartMs = SystemClock.elapsedRealtime();

        setupCards();
        loadUsableCapacity();
    }

    /** Bốn ô chỉ số dùng chung một layout nên phải gán nhãn bằng code. */
    private void setupCards() {
        binding.cardCurrent.tvStatDetail.setText(R.string.home_current);
        binding.cardVoltage.tvStatDetail.setText(R.string.home_voltage);
        binding.cardTemperature.tvStatDetail.setText(R.string.home_temperature);
        binding.cardElapsed.tvStatDetail.setText(R.string.xcharge_elapsed);

        // Layout gốc có icon ở trên, màn này không cần nên ẩn đi
        binding.cardCurrent.imgStatIcon.setVisibility(View.GONE);
        binding.cardVoltage.imgStatIcon.setVisibility(View.GONE);
        binding.cardTemperature.imgStatIcon.setVisibility(View.GONE);
        binding.cardElapsed.imgStatIcon.setVisibility(View.GONE);
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
        renderOptimizeStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);
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
        renderOptimizeStatus();
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

    /** Liệt kê những gì đã tối ưu và những gì người dùng phải tự tắt. */
    private void renderOptimizeStatus() {
        if (binding == null) return;

        binding.optimizeContainer.removeAllViews();
        addStatusLine(true, getString(R.string.xcharge_done_screen));

        if (savedState != null && savedState.isAutoSyncChanged()) {
            addStatusLine(true, getString(R.string.xcharge_done_sync));
        }

        final boolean canWrite = PowerSaver.canWriteSystemSettings(this);
        if (canWrite) {
            addStatusLine(true, getString(R.string.xcharge_done_brightness));
        }
        binding.btnGrantBrightness.setVisibility(canWrite ? View.GONE : View.VISIBLE);
        binding.btnGrantBrightness.setOnClickListener(v -> openWriteSettings());

        // Ba mục dưới đây Android không cho ứng dụng tự tắt
        loadManualItems();
    }

    private void addStatusLine(boolean done, @NonNull String text) {
        TextView line = new TextView(this);
        line.setText(getString(done ? R.string.xcharge_line_done : R.string.xcharge_line_manual,
                text));
        line.setTextSize(13f);
        line.setTextColor(ContextCompat.getColor(this,
                done ? R.color.state_good : R.color.state_warning));

        final int padding = getResources().getDimensionPixelSize(R.dimen.space_xs);
        line.setPadding(0, padding, 0, padding);
        binding.optimizeContainer.addView(line);
    }

    /** Quét xem còn mục nào đang bật mà app không tắt hộ được. */
    private void loadManualItems() {
        executors.execute(() -> optimizer.scan(monitor.getLastInfo()), result -> {
            if (binding == null || result == null) return;

            int manualCount = 0;
            for (DrainStatus status : result) {
                if (!status.isActive() || !status.getFeature().hasSettingsPage()) continue;
                // Đồng bộ đã được app tắt hộ nên không liệt kê lại
                if (status.getFeature() == PowerDrainFeature.AUTO_SYNC
                        && savedState != null && savedState.isAutoSyncChanged()) {
                    continue;
                }
                addStatusLine(false, getString(status.getFeature().getLabelRes()));
                manualCount++;
            }

            binding.btnManualOff.setVisibility(manualCount > 0 ? View.VISIBLE : View.GONE);
            binding.btnManualOff.setOnClickListener(v -> CheckPowerActivity.start(this));
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
        bindCards(info, smoothedMa);
        bindWarning(info, smoothedMa);
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        // Cắm hoặc rút sạc đều là bắt đầu một lần theo dõi mới
        sessionStartMs = SystemClock.elapsedRealtime();
        loadUsableCapacity();
    }

    private void bindHeader(@NonNull BatteryInfo info, int smoothedMa) {
        binding.tvPercent.setText(String.format(Locale.getDefault(), "%d%%", info.getPercent()));
        binding.progressBattery.setProgress(info.getPercent());

        final boolean plugged = info.getPlugType().isPlugged();
        if (!plugged) {
            binding.tvStatus.setText(R.string.xcharge_not_charging);
            binding.tvRemaining.setText(R.string.xcharge_plug_in);
            return;
        }

        binding.tvStatus.setText(ChargeSpeed.fromCurrent(smoothedMa).getLabelRes());

        final long remainingMs = UsageCalculator.estimateTimeToFull(
                info.getPercent(), smoothedMa, usableCapacityMah);
        binding.tvRemaining.setText(remainingMs > 0
                ? getString(R.string.xcharge_remaining, FormatUtils.formatDuration(remainingMs))
                : getString(R.string.xcharge_calculating));
    }

    private void bindCards(@NonNull BatteryInfo info, int smoothedMa) {
        setCardValue(binding.cardCurrent, smoothedMa == BatteryInfo.UNKNOWN_INT
                ? getString(R.string.value_placeholder)
                : String.format(Locale.US, "%d mA", Math.abs(smoothedMa)));

        setCardValue(binding.cardVoltage, info.getVoltage() > 0
                ? FormatUtils.formatVoltage(info.getVoltage())
                : getString(R.string.value_placeholder));

        setCardValue(binding.cardTemperature,
                String.format(Locale.getDefault(), "%.1f℃", info.getTemperatureCelsius()));

        setCardValue(binding.cardElapsed,
                FormatUtils.formatDuration(SystemClock.elapsedRealtime() - sessionStartMs));
    }

    private void setCardValue(@NonNull ViewStatColumnBinding card, @NonNull String value) {
        card.tvStatValue.setText(value);
        // Cỡ chữ mặc định của layout gốc quá lớn cho lưới 2 cột ở màn này
        card.tvStatValue.setTextSize(22f);
    }

    /**
     * Cảnh báo bất thường: nhiệt độ cao hoặc dòng nạp quá thấp.
     * Chỉ hiện khi đang cắm sạc, vì lúc dùng pin thì hai chỉ số này không nói lên gì.
     */
    private void bindWarning(@NonNull BatteryInfo info, int smoothedMa) {
        if (!info.getPlugType().isPlugged()) {
            binding.tvWarning.setVisibility(View.GONE);
            return;
        }

        if (info.getTemperatureCelsius() >= WARN_TEMPERATURE) {
            binding.tvWarning.setText(R.string.xcharge_warn_hot);
            binding.tvWarning.setVisibility(View.VISIBLE);
            return;
        }

        if (smoothedMa != BatteryInfo.UNKNOWN_INT
                && smoothedMa > 0 && smoothedMa < WARN_LOW_CURRENT) {
            binding.tvWarning.setText(R.string.xcharge_warn_slow);
            binding.tvWarning.setVisibility(View.VISIBLE);
            return;
        }

        binding.tvWarning.setVisibility(View.GONE);
    }
}
