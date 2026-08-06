package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.databinding.ActivityNotificationCleanBinding;
import com.dung.chargmagagement.model.ads.AdManager;
import com.dung.chargmagagement.service.NotificationCleanerService;

/**
 * Màn Dọn dẹp thông báo.
 *
 * <p>Đếm số thông báo xoá được và cho phép xoá hết. Việc này bắt buộc phải có
 * quyền đọc thông báo – quyền đặc biệt mà người dùng chỉ cấp được trong Cài đặt
 * hệ thống, không có hộp thoại xin quyền thông thường. Nếu chưa cấp, màn hình
 * hiển thị hướng dẫn và mở thẳng trang Cài đặt tương ứng.
 */
public class NotificationCleanActivity extends BaseActivity<ActivityNotificationCleanBinding> {

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, NotificationCleanActivity.class));
    }

    @NonNull
    @Override
    protected ActivityNotificationCleanBinding onCreateBinding() {
        return ActivityNotificationCleanBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.app_name);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        binding.btnRefresh.setOnClickListener(v -> refresh());
        binding.btnDetect.setOnClickListener(v -> CheckPowerActivity.start(this));
        binding.tvStatus.setOnClickListener(v -> onStatusClicked());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng có thể vừa cấp quyền ở Cài đặt rồi quay lại
        refresh();
        AdManager.loadBanner(this, binding.adContainer);
    }

    /** Cập nhật dòng trạng thái theo số thông báo đang có. */
    private void refresh() {
        if (!NotificationCleanerService.isEnabled(this)) {
            binding.tvStatus.setText(R.string.clean_need_permission);
            return;
        }

        final int count = NotificationCleanerService.countClearable();
        if (count <= 0) {
            // count == -1 nghĩa là hệ thống chưa kết nối xong dịch vụ; với người
            // dùng thì kết quả nhìn thấy vẫn là "không có gì để dọn"
            binding.tvStatus.setText(R.string.clean_nothing);
        } else {
            binding.tvStatus.setText(
                    getResources().getQuantityString(R.plurals.clean_found, count, count));
        }
    }

    /**
     * Bấm vào dòng trạng thái: chưa cấp quyền thì mở Cài đặt, đã cấp thì dọn.
     */
    private void onStatusClicked() {
        if (!NotificationCleanerService.isEnabled(this)) {
            openNotificationAccessSettings();
            return;
        }

        if (NotificationCleanerService.clearAll()) {
            Toast.makeText(this, R.string.clean_done, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void openNotificationAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            Logger.e("NotificationClean", "Không mở được trang cấp quyền", e);
            Toast.makeText(this, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
