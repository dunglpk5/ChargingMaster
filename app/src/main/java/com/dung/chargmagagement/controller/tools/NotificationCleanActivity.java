package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.adapter.NotificationAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.databinding.ActivityNotificationCleanBinding;
import com.dung.chargmagagement.model.ads.AdManager;
import com.dung.chargmagagement.model.device.InstallSource;
import com.dung.chargmagagement.model.ui.NotificationItem;
import com.dung.chargmagagement.service.NotificationCleanerService;

import java.util.List;

/**
 * Màn Dọn dẹp thông báo: liệt kê thông báo đang hiển thị và cho xoá.
 *
 * <p>Bắt buộc phải có quyền đọc thông báo – quyền đặc biệt mà người dùng chỉ cấp
 * được trong Cài đặt hệ thống, không có hộp thoại xin quyền thông thường. Thiếu
 * quyền thì màn hình khoá lại và chỉ còn một lối duy nhất là nút Cấp quyền.
 */
public class NotificationCleanActivity extends BaseActivity<ActivityNotificationCleanBinding>
        implements NotificationCleanerService.Listener {

    private NotificationAdapter adapter;

    /** Đã nạp quảng cáo chưa; chỉ nạp một lần cho cả vòng đời màn hình. */
    private boolean adLoaded;

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
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSettings.setOnClickListener(v -> openNotificationAccessSettings());
        binding.btnGrantPermission.setOnClickListener(v -> openNotificationAccessSettings());
        binding.btnCleanAll.setOnClickListener(v -> cleanAll());
        binding.btnRefresh.setOnClickListener(v -> refresh());
        binding.btnDetect.setOnClickListener(v -> CheckPowerActivity.start(this));

        adapter = new NotificationAdapter(this::dismissOne);
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng có thể vừa cấp quyền ở Cài đặt rồi quay lại
        NotificationCleanerService.setListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Dịch vụ sống lâu hơn Activity rất nhiều, quên gỡ là rò rỉ cả màn hình
        NotificationCleanerService.setListener(null);
    }

    /** Có thông báo mới đến hoặc vừa bị gỡ: dựng lại danh sách ngay. */
    @Override
    public void onNotificationsChanged() {
        if (binding == null) return;
        refresh();
    }

    /**
     * Nạp lại danh sách thông báo.
     *
     * <p>Chưa có quyền thì dừng ở đây: không đếm, không hiện con số nào. Đếm mà
     * không có quyền luôn ra 0, và một màn hình báo "không có gì để dọn" trong khi
     * thật ra nó không được phép nhìn thấy gì là nói dối người dùng.
     */
    private void refresh() {
        final boolean granted = NotificationCleanerService.isEnabled(this);
        applyAccessState(granted);
        if (!granted) return;

        final List<NotificationItem> items = NotificationCleanerService.listClearable();
        adapter.submit(items);
        showList(!items.isEmpty(), items.size());
    }

    /**
     * Chuyển giữa hai trạng thái của màn hình.
     *
     * <p>Không có thông báo nào thì đây không còn là danh sách nữa mà là một màn
     * thông báo "đã sạch": nền teal, chổi ở giữa, gợi ý việc tiếp theo. Nút dọn và
     * quảng cáo biến mất hẳn – chẳng có gì để dọn thì một nút dọn bị làm mờ chỉ
     * gây bối rối, còn quảng cáo giữa màn hình trống thì lộ liễu quá.
     */
    private void showList(boolean hasItems, int count) {
        binding.rvNotifications.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.emptyContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        binding.btnCleanAll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.adContainer.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.btnSettings.setVisibility(hasItems ? View.VISIBLE : View.GONE);

        binding.tvToolbarTitle.setText(hasItems
                ? getString(R.string.clean_title_count, count)
                : getString(R.string.app_name));

        // Nạp một lần thôi: gọi lại mỗi lần danh sách đổi sẽ dựng banner mới liên tục
        if (hasItems && !adLoaded) {
            adLoaded = true;
            AdManager.loadBanner(this, binding.adContainer);
        }
    }

    /**
     * Khoá hay mở màn hình theo tình trạng quyền.
     *
     * <p>Khoá ở tầng logic chứ không chỉ làm mờ: {@link #cleanAll()} và
     * {@link #dismissOne} đều tự kiểm tra lại trước khi làm gì.
     */
    private void applyAccessState(boolean granted) {
        binding.cardPermission.setVisibility(granted ? View.GONE : View.VISIBLE);

        if (granted) return;

        binding.tvRestrictedHint.setVisibility(
                InstallSource.isRestrictedSettingLikely(this) ? View.VISIBLE : View.GONE);

        adapter.submit(java.util.Collections.emptyList());
        binding.tvToolbarTitle.setText(R.string.tools_clean_notification);
        binding.rvNotifications.setVisibility(View.GONE);
        binding.emptyContainer.setVisibility(View.GONE);
        binding.btnCleanAll.setVisibility(View.GONE);
        binding.adContainer.setVisibility(View.GONE);
        binding.btnSettings.setVisibility(View.VISIBLE);
    }

    // ==================== Thao tác xoá ====================

    private void cleanAll() {
        if (!NotificationCleanerService.isEnabled(this)) return;

        if (NotificationCleanerService.clearAll()) {
            Toast.makeText(this, R.string.clean_done, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    /** Bấm vào một thông báo là gạt riêng nó đi, giống vuốt trên thanh trạng thái. */
    private void dismissOne(@NonNull NotificationItem item) {
        if (!NotificationCleanerService.isEnabled(this)) return;

        NotificationCleanerService.cancel(item.key);
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
