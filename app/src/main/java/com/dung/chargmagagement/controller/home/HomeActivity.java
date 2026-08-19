package com.dung.chargmagagement.controller.home;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityHomeBinding;
import com.dung.chargmagagement.service.BatteryLogService;
import com.dung.chargmagagement.service.ChargeAlarmScheduler;

/**
 * Màn hình chính: ViewPager2 gồm 3 tab (Trang chủ / Công cụ / Sử dụng pin)
 * đồng bộ hai chiều với BottomNavigationView.
 */
public class HomeActivity extends BaseActivity<ActivityHomeBinding> {

    public static final int TAB_HOME = 0;
    public static final int TAB_TOOLS = 1;
    public static final int TAB_USAGE = 2;

    private HomePagerAdapter pagerAdapter;

    @NonNull
    @Override
    protected ActivityHomeBinding onCreateBinding() {
        return ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        setupPager();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Đảm bảo service ghi pin đang chạy. Gọi từ đây vì Activity chắc chắn đang
        // ở tiền cảnh – Android 12+ cấm khởi động foreground service từ nền.
        //
        // Cố tình KHÔNG đăng ký recorder làm listener của BatteryMonitor: service
        // là nơi ghi dữ liệu duy nhất. Có hai nguồn cùng ghi thì mỗi lần mở app sẽ
        // sinh ra bản ghi trùng và mọi con số thống kê đều bị đếm hai lần.
        BatteryLogService.start(this);

        // Báo động sạc chạy bằng hẹn giờ, độc lập với việc ghi lịch sử pin. Rà lại ở
        // đây để cái hẹn được dựng lại nếu hệ thống đã xoá nó (ví dụ sau khi người
        // dùng buộc dừng ứng dụng).
        ChargeAlarmScheduler.check(this);
    }

    private void setupPager() {
        pagerAdapter = new HomePagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        // Giữ cả 3 tab trong bộ nhớ: mỗi tab đọc dữ liệu hệ thống khá tốn,
        // tạo lại liên tục sẽ hao pin hơn là giữ sẵn.
        binding.viewPager.setOffscreenPageLimit(2);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.bottomNav.getMenu().getItem(position).setChecked(true);
            }
        });
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                binding.viewPager.setCurrentItem(TAB_HOME, false);
            } else if (itemId == R.id.nav_tools) {
                binding.viewPager.setCurrentItem(TAB_TOOLS, false);
            } else if (itemId == R.id.nav_usage) {
                binding.viewPager.setCurrentItem(TAB_USAGE, false);
            } else {
                return false;
            }
            return true;
        });
        // Chặn hành vi mặc định tạo lại fragment khi bấm lại tab đang chọn
        binding.bottomNav.setOnItemReselectedListener(item -> { });
    }
}
