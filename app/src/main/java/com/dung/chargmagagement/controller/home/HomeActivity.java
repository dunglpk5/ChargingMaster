package com.dung.chargmagagement.controller.home;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityHomeBinding;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.repository.SessionRecorder;
import com.dung.chargmagagement.service.ChargingMonitorService;

/**
 * Màn hình chính: ViewPager2 gồm 3 tab (Trang chủ / Công cụ / Sử dụng pin)
 * đồng bộ hai chiều với BottomNavigationView.
 */
public class HomeActivity extends BaseActivity<ActivityHomeBinding> {

    public static final int TAB_HOME = 0;
    public static final int TAB_TOOLS = 1;
    public static final int TAB_USAGE = 2;

    private HomePagerAdapter pagerAdapter;
    private BatteryMonitor monitor;
    private SessionRecorder recorder;

    @NonNull
    @Override
    protected ActivityHomeBinding onCreateBinding() {
        return ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        monitor = BatteryMonitor.get(this);
        recorder = SessionRecorder.get(this);

        setupPager();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Trong lúc app mở, vẫn ghi lịch sử kể cả khi không sạc (service chỉ chạy
        // lúc cắm sạc) để có dữ liệu màn hình bật/tắt cho tab "Sử dụng pin"
        monitor.addListener(recorder);

        // Bù trường hợp máy đã cắm sạc từ trước khi app được mở: lúc đó broadcast
        // ACTION_POWER_CONNECTED đã trôi qua nên service chưa hề chạy.
        ChargingMonitorService.startIfPlugged(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(recorder);
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
