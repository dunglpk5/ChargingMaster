package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityPhoneTemperatureBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;

/**
 * Màn Nhiệt độ điện thoại: nhiệt kế hai thang đo, cập nhật theo thời gian thực.
 *
 * <p>Android chỉ cho phép đọc <b>nhiệt độ pin</b>; nhiệt độ CPU hay bề mặt máy
 * không có API công khai nên không thể hiển thị chính xác.
 */
public class PhoneTemperatureActivity extends BaseActivity<ActivityPhoneTemperatureBinding>
        implements BatteryMonitor.Listener {

    /** Ngưỡng đánh giá nhiệt độ pin (℃). */
    private static final float TEMP_WARM = 38f;
    private static final float TEMP_HOT = 43f;

    private BatteryMonitor monitor;
    private float lastCelsius;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, PhoneTemperatureActivity.class));
    }

    @NonNull
    @Override
    protected ActivityPhoneTemperatureBinding onCreateBinding() {
        return ActivityPhoneTemperatureBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_phone_temperature);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        monitor = BatteryMonitor.get(this);
        binding.btnDetect.setOnClickListener(v -> showVerdict());
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitor.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        lastCelsius = info.getTemperatureCelsius();
        binding.thermometer.setCelsius(lastCelsius);
    }

    /** Bấm PHÁT HIỆN: nói rõ nhiệt độ hiện tại đang ở mức nào. */
    private void showVerdict() {
        Toast.makeText(this, getStateLabel(lastCelsius), Toast.LENGTH_SHORT).show();
    }

    @StringRes
    private int getStateLabel(float celsius) {
        if (celsius >= TEMP_HOT) return R.string.temp_state_hot;
        if (celsius >= TEMP_WARM) return R.string.temp_state_warm;
        return R.string.temp_state_normal;
    }
}
