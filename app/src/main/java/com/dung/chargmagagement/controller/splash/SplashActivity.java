package com.dung.chargmagagement.controller.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.home.HomeActivity;
import com.dung.chargmagagement.databinding.ActivitySplashBinding;

/**
 * Màn hình khởi động: hiển thị logo trong lúc chuẩn bị dữ liệu ban đầu,
 * sau đó chuyển sang {@link HomeActivity}.
 */
public class SplashActivity extends BaseActivity<ActivitySplashBinding> {

    /** Thời gian tối thiểu hiển thị splash để tránh nhấp nháy. */
    private static final long MIN_SPLASH_MS = 1200L;

    private final Runnable navigateTask = this::openHome;

    @NonNull
    @Override
    protected ActivitySplashBinding onCreateBinding() {
        return ActivitySplashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        // Phần khởi tạo nặng (mở DB, đọc thông tin thiết bị) sẽ được thêm ở Phase 2-3.
        // Hiện tại chỉ giữ splash đủ lâu rồi điều hướng.
        executors.main().postDelayed(navigateTask, MIN_SPLASH_MS);
    }

    private void openHome() {
        if (isFinishing() || isDestroyed()) return;
        startActivity(new Intent(this, HomeActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        // Gỡ callback để không giữ Activity sau khi bị huỷ
        executors.main().removeCallbacks(navigateTask);
        super.onDestroy();
    }
}
