package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.adapter.DrainFeatureAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.tools.PhoneTemperatureActivity;
import com.dung.chargmagagement.databinding.ActivityCheckPowerBinding;
import com.dung.chargmagagement.databinding.DialogPowerSourceBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.power.ChargeSpeed;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.power.PowerOptimizer;

import java.util.List;
import java.util.Locale;

/**
 * Màn Phát hiện sạc.
 *
 * <p>Phần trên là đồng hồ cung thể hiện tốc độ sạc, kèm <b>hai con số cùng lúc</b>:
 * dòng vào (khi cắm sạc) và dòng chờ (mức tiêu thụ của máy). Hai con số này được
 * ghi nhớ riêng: dòng vào giữ giá trị đo được lần gần nhất lúc đang sạc, dòng chờ
 * giữ giá trị lúc đang xả, nhờ vậy người dùng so sánh được cả hai mà không phải
 * rút sạc ra rồi cắm lại.
 *
 * <p>Danh sách hạng mục được quét lại mỗi khi màn hình quay lại tiền cảnh, vì
 * người dùng vừa sang Cài đặt tắt thứ gì đó rồi bấm quay lại.
 */
public class CheckPowerActivity extends BaseActivity<ActivityCheckPowerBinding>
        implements BatteryMonitor.Listener, DrainFeatureAdapter.OnFeatureClickListener {

    /** Dòng nạp coi là mức tối đa của thang đo (mA) – tương ứng sạc siêu nhanh. */
    private static final int GAUGE_MAX_MA = 5_000;

    private BatteryMonitor monitor;
    private PowerOptimizer optimizer;
    private DrainFeatureAdapter featureAdapter;

    /** Giá trị hiển thị gần nhất của từng chiều dòng điện. */
    private int lastChargingMa = BatteryInfo.UNKNOWN_INT;
    private int lastIdleMa = BatteryInfo.UNKNOWN_INT;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, CheckPowerActivity.class));
    }

    @NonNull
    @Override
    protected ActivityCheckPowerBinding onCreateBinding() {
        return ActivityCheckPowerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(this);
        optimizer = new PowerOptimizer(this);

        binding.toolbarInclude.tvToolbarTitle.setText(R.string.check_title);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        binding.btnCheckSource.setOnClickListener(v -> showPowerSourceDialog());
        binding.btnXCharge.setOnClickListener(v -> openXCharge());

        featureAdapter = new DrainFeatureAdapter();
        featureAdapter.setOnFeatureClickListener(this);
        binding.rvFeatures.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeatures.setAdapter(featureAdapter);
        // Tuyệt đối không gọi setHasFixedSize(true) ở đây: RecyclerView này cao
        // wrap_content trong NestedScrollView và dữ liệu về sau khi đã đo xong bố
        // cục. Cờ đó khiến RecyclerView bỏ qua requestLayout lúc có dữ liệu mới,
        // nên danh sách sẽ giữ nguyên chiều cao 0 và không hiện gì cả.

        // Vào màn là bắt đầu phiên đo mới cho số liệu khớp khoảng đang quan sát
        monitor.resetSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitor.addListener(this);
        scanFeatures();
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);
    }

    // ==================== Đồng hồ cung ====================

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        rememberCurrent(info, smoothedMa);
        bindGauge(info, smoothedMa);
        bindCurrentTexts();
    }

    /** Ghi nhớ dòng vào và dòng chờ vào hai ô riêng. */
    private void rememberCurrent(@NonNull BatteryInfo info, int smoothedMa) {
        if (smoothedMa == BatteryInfo.UNKNOWN_INT) return;

        if (info.getPlugType().isPlugged()) {
            lastChargingMa = Math.abs(smoothedMa);
        } else {
            // Lúc xả, dòng mang dấu âm chính là mức tiêu thụ của máy
            lastIdleMa = -Math.abs(smoothedMa);
        }
    }

    /**
     * Đồng hồ cung bám theo <b>dòng vào</b> đã ghi nhận, không bám theo giá trị
     * thô đang trôi qua: giá trị thô lúc chưa cắm sạc là số âm, đưa thẳng vào
     * bảng xếp loại thì lúc nào cũng ra "đang đo" và kim luôn nằm ở mốc 0.
     *
     * <p>Chưa cắm sạc thì nói thẳng là chưa sạc. Nhãn "đang đo" chỉ dành cho
     * quãng vài giây vừa cắm dây mà chưa có số liệu – đúng nghĩa của nó.
     */
    private void bindGauge(@NonNull BatteryInfo info, int smoothedMa) {
        final boolean plugged = info.getPlugType().isPlugged();
        final int inMa = plugged ? lastChargingMa : BatteryInfo.UNKNOWN_INT;
        final ChargeSpeed speed = ChargeSpeed.fromCurrent(inMa);

        binding.tvSpeed.setText(plugged
                ? speed.getLabelRes()
                : R.string.speed_not_charging);

        final int dotColor = ContextCompat.getColor(this, speed.getColorRes());
        binding.arcGauge.setDotColor(dotColor);
        binding.arcGauge.setProgress(inMa > 0 ? inMa / (float) GAUGE_MAX_MA : 0f);
    }

    private void bindCurrentTexts() {
        binding.tvCurrentIn.setText(lastChargingMa == BatteryInfo.UNKNOWN_INT
                ? getString(R.string.check_current_in, 0)
                : getString(R.string.check_current_in, lastChargingMa));

        binding.tvCurrentIdle.setText(lastIdleMa == BatteryInfo.UNKNOWN_INT
                ? getString(R.string.check_current_idle, 0)
                : getString(R.string.check_current_idle, lastIdleMa));
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        // Đổi nguồn thì danh sách hạng mục có thể đổi theo (mục nguồn điện)
        scanFeatures();
    }

    // ==================== Danh sách hạng mục ====================

    private void scanFeatures() {
        final BatteryInfo info = monitor.getLastInfo();

        executors.execute(() -> optimizer.scan(info), result -> {
            if (binding == null || result == null) return;
            featureAdapter.submitList(result);
        });
    }

    @Override
    public void onFeatureClick(@NonNull DrainStatus status) {
        switch (status.getFeature()) {
            case TEMPERATURE:
                PhoneTemperatureActivity.start(this);
                return;

            case POWER_SOURCE:
                // Chỉ là khuyến nghị, không có trang cài đặt nào để mở
                Toast.makeText(this, R.string.feature_power_source_desc, Toast.LENGTH_SHORT).show();
                return;

            default:
                openSettings(status);
        }
    }

    private void openSettings(@NonNull DrainStatus status) {
        Intent intent = optimizer.buildSettingsIntent(status.getFeature());

        if (!optimizer.canOpen(intent)) {
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            // Một vài ROM tuỳ biến chặn intent dù resolveActivity trả về khác null
            Logger.e("CheckPower", "Không mở được trang cài đặt", e);
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== X-Sạc ====================

    /**
     * Chế độ sạc chuyên sâu.
     *
     * <p>Hộp thoại mô tả những gì sẽ được bật. Hiện chỉ bật phần giám sát; việc
     * tự giảm độ sáng hệ thống cần quyền {@code WRITE_SETTINGS} nên tạm để lại,
     * xem ghi chú ở phần bàn giao.
     */
    // ==================== Hộp thoại kiểm tra nguồn điện ====================

    /**
     * Hiện kết quả kiểm tra nguồn điện.
     *
     * <p>So sánh <b>công suất định mức ước tính</b> (suy từ dòng nạp cao nhất đo
     * được trong phiên – tức khả năng tối đa của bộ sạc) với <b>công suất thực tế</b>
     * đang vào pin ngay lúc này. Hai con số chênh nhau nhiều nghĩa là máy đang tiêu
     * thụ mất một phần điện thay vì nạp hết vào pin.
     */
    private void showPowerSourceDialog() {
        final BatteryInfo info = monitor.getLastInfo();
        if (info == null) return;

        DialogPowerSourceBinding dialogBinding =
                DialogPowerSourceBinding.inflate(getLayoutInflater());

        final float voltage = info.getVoltage();
        final int actualMa = Math.abs(monitor.getStats().getSmoothedMa() == BatteryInfo.UNKNOWN_INT
                ? 0 : monitor.getStats().getSmoothedMa());
        final int maxMa = monitor.getStats().getMaxMa() == BatteryInfo.UNKNOWN_INT
                ? actualMa : Math.abs(monitor.getStats().getMaxMa());

        final float actualWatt = voltage * actualMa / 1000f;
        // Định mức không bao giờ nhỏ hơn thực tế
        final float ratedWatt = voltage * Math.max(maxMa, actualMa) / 1000f;

        dialogBinding.tvVerdict.setText(getVerdictRes(actualMa, info));
        dialogBinding.tvRatedPower.setText(
                String.format(Locale.getDefault(), "%.1f W", ratedWatt));
        dialogBinding.tvActualPower.setText(
                String.format(Locale.getDefault(), "%.1f W", actualWatt));

        // Chênh lệch càng lớn thì gai sóng càng sâu
        dialogBinding.waveform.setAmplitude(ratedWatt > 0
                ? Math.min(1f, 0.35f + (ratedWatt - actualWatt) / ratedWatt)
                : 0.5f);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();

        // Bỏ nền mặc định để bo góc của layout hiện đúng
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogBinding.btnOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Câu đánh giá tốc độ sạc hiện tại. */
    @StringRes
    private int getVerdictRes(int currentMa, @NonNull BatteryInfo info) {
        if (!info.getPlugType().isPlugged()) return R.string.source_verdict_unplugged;

        switch (ChargeSpeed.fromCurrent(currentMa)) {
            case FAST:
            case VERY_FAST:
                return R.string.source_verdict_high;
            case NORMAL:
                return R.string.source_verdict_normal;
            default:
                return R.string.source_verdict_low;
        }
    }

    private void openXCharge() {
        // Bắt đầu phiên đo mới để màn sạc tối ưu hiển thị số liệu từ lúc mở
        monitor.resetSession();
        XChargeActivity.start(this);
    }
}
