package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

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

    /**
     * Nhiệt độ phải hạ xuống dưới mức này thì mới cho phép tự hiện lời khuyên lần
     * nữa. Không có vùng đệm thì nhiệt độ dao động sát ngưỡng sẽ làm hộp thoại bật
     * lên liên tục.
     */
    private static final float ADVICE_RESET_MARGIN = 2f;

    private BatteryMonitor monitor;
    private float lastCelsius;

    /** Đã tự hiện lời khuyên cho đợt nóng hiện tại hay chưa. */
    private boolean adviceShown;

    @Nullable
    private AlertDialog adviceDialog;

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

        // Rời màn mà hộp thoại còn treo sẽ rò rỉ cửa sổ của Activity đã huỷ
        if (adviceDialog != null) {
            adviceDialog.dismiss();
            adviceDialog = null;
        }
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        lastCelsius = info.getTemperatureCelsius();
        binding.thermometer.setCelsius(lastCelsius);

        updateAdviceState(lastCelsius);
    }

    // ==================== Lời khuyên hạ nhiệt ====================

    /**
     * Tự hiện lời khuyên ngay khi máy nóng lên, không bắt người dùng phải bấm
     * PHÁT HIỆN mới biết – lúc máy đang nóng thì đó là thông tin cần đưa ngay.
     *
     * <p>Mỗi đợt nóng chỉ nhắc một lần, và chỉ nhắc lại sau khi máy đã nguội hẳn
     * xuống dưới ngưỡng kèm vùng đệm.
     */
    private void updateAdviceState(float celsius) {
        if (celsius >= TEMP_WARM) {
            if (!adviceShown) {
                adviceShown = true;
                showAdviceDialog();
            }
            return;
        }

        if (celsius < TEMP_WARM - ADVICE_RESET_MARGIN) {
            adviceShown = false;
        }
    }

    /** Bấm PHÁT HIỆN: nói rõ nhiệt độ hiện tại đang ở mức nào và nên làm gì. */
    private void showVerdict() {
        showAdviceDialog();
    }

    private void showAdviceDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (adviceDialog != null && adviceDialog.isShowing()) return;

        adviceDialog = new AlertDialog.Builder(this)
                .setMessage(getAdviceRes(lastCelsius))
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.dismiss())
                .setOnDismissListener(dialog -> adviceDialog = null)
                .show();
    }

    /** Nội dung lời khuyên tương ứng với mức nhiệt hiện tại. */
    @StringRes
    private int getAdviceRes(float celsius) {
        if (celsius >= TEMP_HOT) return R.string.temp_advice_hot;
        if (celsius >= TEMP_WARM) return R.string.temp_advice_warm;
        return R.string.temp_advice_normal;
    }
}
