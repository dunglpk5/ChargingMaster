package com.dung.chargmagagement.controller.home;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.dung.chargmagagement.controller.home.tab.BatteryUsageFragment;
import com.dung.chargmagagement.controller.home.tab.DashboardFragment;
import com.dung.chargmagagement.controller.home.tab.ToolsFragment;

/**
 * Adapter cấp 3 tab cho {@link HomeActivity}.
 */
public class HomePagerAdapter extends FragmentStateAdapter {

    private static final int TAB_COUNT = 3;

    public HomePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case HomeActivity.TAB_TOOLS:
                return new ToolsFragment();
            case HomeActivity.TAB_USAGE:
                return new BatteryUsageFragment();
            case HomeActivity.TAB_HOME:
            default:
                return new DashboardFragment();
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
