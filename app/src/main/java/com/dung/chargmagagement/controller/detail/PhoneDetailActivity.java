package com.dung.chargmagagement.controller.detail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityPhoneDetailBinding;
import com.dung.chargmagagement.model.device.DetailSection;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Màn Thông tin thiết bị: ViewPager2 với 6 tab
 * DEVICE / SYSTEM / CPU / DISPLAY / NETWORK / SENSOR.
 */
public class PhoneDetailActivity extends BaseActivity<ActivityPhoneDetailBinding> {

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, PhoneDetailActivity.class));
    }

    @NonNull
    @Override
    protected ActivityPhoneDetailBinding onCreateBinding() {
        return ActivityPhoneDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.viewPager.setAdapter(new DetailPagerAdapter(this));
        // Mặc định giữ 1 tab hai bên; các tab này chỉ nạp dữ liệu một lần rồi thôi
        // nên giữ sẵn giúp vuốt qua lại mượt mà không phải đọc lại sysfs.
        binding.viewPager.setOffscreenPageLimit(1);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(DetailSection.fromPosition(position).getTitleRes())
        ).attach();
    }

    /** Adapter sinh tab từ danh sách {@link DetailSection}. */
    private static class DetailPagerAdapter extends FragmentStateAdapter {

        DetailPagerAdapter(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return DetailListFragment.newInstance(DetailSection.fromPosition(position));
        }

        @Override
        public int getItemCount() {
            return DetailSection.values().length;
        }
    }
}
