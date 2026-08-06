package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.adapter.DrainFeatureAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.tools.PhoneTemperatureActivity;
import com.dung.chargmagagement.databinding.ActivityCheckPowerBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.power.ChargeSpeed;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.ads.AdManager;
import com.dung.chargmagagement.model.power.PowerOptimizer;
import com.dung.chargmagagement.model.vip.VipManager;
import com.google.android.gms.ads.rewarded.RewardedAd;

import java.util.List;

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

        binding.btnCheckSource.setOnClickListener(v -> scanFeatures());
        binding.btnXCharge.setOnClickListener(v -> showXChargeDialog());

        featureAdapter = new DrainFeatureAdapter();
        featureAdapter.setOnFeatureClickListener(this);
        binding.rvFeatures.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeatures.setAdapter(featureAdapter);
        binding.rvFeatures.setHasFixedSize(true);

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

    private void bindGauge(@NonNull BatteryInfo info, int smoothedMa) {
        final boolean plugged = info.getPlugType().isPlugged();
        final ChargeSpeed speed = plugged
                ? ChargeSpeed.fromCurrent(smoothedMa)
                : ChargeSpeed.UNKNOWN;

        binding.tvSpeed.setText(speed.getLabelRes());

        final int dotColor = ContextCompat.getColor(this, speed.getColorRes());
        binding.arcGauge.setDotColor(dotColor);
        binding.arcGauge.setProgress(plugged && smoothedMa > 0
                ? smoothedMa / (float) GAUGE_MAX_MA
                : 0f);
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
    private void showXChargeDialog() {
        final boolean vip = VipManager.get(this).isVip();

        new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_xcharge)
                .setTitle(R.string.xcharge_title)
                .setMessage(R.string.xcharge_message)
                .setNegativeButton(R.string.action_cancel, null)
                // Người VIP dùng thẳng, người thường xem quảng cáo để dùng thử
                .setPositiveButton(vip ? R.string.xcharge_enable : R.string.xcharge_watch_ad,
                        (dialog, which) -> requestXCharge())
                .show();
    }

    /** VIP thì bật ngay; còn lại phải xem hết một quảng cáo có thưởng. */
    private void requestXCharge() {
        if (VipManager.get(this).isVip()) {
            enableXCharge();
            return;
        }

        Toast.makeText(this, R.string.xcharge_loading_ad, Toast.LENGTH_SHORT).show();

        AdManager.loadRewarded(this, new AdManager.RewardedCallback() {
            @Override
            public void onAdReady(@NonNull RewardedAd ad) {
                if (isFinishing() || isDestroyed()) return;
                ad.show(CheckPowerActivity.this, reward -> enableXCharge());
            }

            @Override
            public void onAdFailed() {
                // Không tải được quảng cáo thì vẫn cho dùng thử: lỗi mạng là
                // chuyện của app, không nên bắt người dùng chịu
                enableXCharge();
            }

            @Override
            public void onRewardEarned() {
                enableXCharge();
            }
        });
    }

    private void enableXCharge() {
        if (isFinishing() || isDestroyed()) return;

        monitor.resetSession();
        scanFeatures();
        Toast.makeText(this, R.string.xcharge_enabled, Toast.LENGTH_SHORT).show();
    }
}
