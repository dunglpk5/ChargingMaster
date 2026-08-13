package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.ChartPageAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityBatteryMonitorBinding;
import com.dung.chargmagagement.databinding.ViewDeviceInfoRowBinding;
import com.dung.chargmagagement.databinding.ViewStatCardBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.entity.BatterySampleEntity;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.BatteryUsageStats;
import com.dung.chargmagagement.model.stats.LastChargeInfo;
import com.dung.chargmagagement.model.stats.UsageRate;
import com.dung.chargmagagement.model.ui.ChartPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Màn Giám sát pin, mở từ nút "Giám sát" ở thẻ Pin trong tab Công cụ.
 *
 * <p>Gom mọi số liệu lịch sử về một chỗ: biểu đồ mức pin ba ngày gần đây, tóm tắt
 * lần sạc cuối, độ chai pin và các tỉ lệ tiêu hao.
 */
public class BatteryMonitorActivity extends BaseActivity<ActivityBatteryMonitorBinding> {

    private static final int MIN_CAPACITY_MAH = 500;
    private static final int MAX_CAPACITY_MAH = 30_000;

    /** Kích thước và khoảng cách của chấm chỉ trang (dp). */
    private static final int DOT_SIZE_DP = 7;
    private static final int DOT_SPACING_DP = 4;

    private BatteryRepository repository;
    private ChartPageAdapter chartAdapter;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, BatteryMonitorActivity.class));
    }

    @NonNull
    @Override
    protected ActivityBatteryMonitorBinding onCreateBinding() {
        return ActivityBatteryMonitorBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        repository = BatteryRepository.get(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSetDesignCapacity.setOnClickListener(v -> showDesignCapacityDialog());

        setupChartPager();
        setupStaticLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCharts();
        loadLastCharge();
        loadStats();
    }

    // ==================== Biểu đồ ba ngày ====================

    private void setupChartPager() {
        chartAdapter = new ChartPageAdapter();
        binding.chartPager.setAdapter(chartAdapter);

        // Mở ra ở hôm nay, tức trang cuối cùng
        binding.chartPager.setCurrentItem(ChartPageAdapter.PAGE_COUNT - 1, false);

        buildPageDots();
        binding.chartPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageDots(position);
            }
        });
    }

    private void buildPageDots() {
        final float density = getResources().getDisplayMetrics().density;
        final int size = Math.round(DOT_SIZE_DP * density);
        final int spacing = Math.round(DOT_SPACING_DP * density);

        binding.pageDots.removeAllViews();
        for (int i = 0; i < ChartPageAdapter.PAGE_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginStart(spacing);
            params.setMarginEnd(spacing);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_page_dot);
            binding.pageDots.addView(dot);
        }
        updatePageDots(ChartPageAdapter.PAGE_COUNT - 1);
    }

    private void updatePageDots(int selected) {
        for (int i = 0; i < binding.pageDots.getChildCount(); i++) {
            final int color = ContextCompat.getColor(this,
                    i == selected ? R.color.teal_primary : R.color.divider);
            // Nhuộm qua backgroundTintList: mọi chấm dùng chung một drawable nên
            // nhuộm thẳng vào drawable sẽ đổi màu cả ba
            binding.pageDots.getChildAt(i).setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void loadCharts() {
        for (int page = 0; page < ChartPageAdapter.PAGE_COUNT; page++) {
            final int position = page;
            repository.loadSamplesOfDay(chartAdapter.getDayKey(page), samples -> {
                if (binding == null) return;
                chartAdapter.setPoints(position, toChartPoints(samples));
            });
        }
    }

    @NonNull
    private static List<ChartPoint> toChartPoints(@Nullable List<BatterySampleEntity> samples) {
        List<ChartPoint> points = new ArrayList<>();
        if (samples == null) return points;

        for (BatterySampleEntity sample : samples) {
            points.add(new ChartPoint(
                    DateUtils.minuteOfDay(sample.timestamp), sample.percent, sample.charging));
        }
        return points;
    }

    // ==================== Lần sạc cuối ====================

    private void setupStaticLabels() {
        binding.rowLastSpeed.tvInfoLabel.setText(R.string.monitor_last_speed);
        binding.rowLastSource.tvInfoLabel.setText(R.string.monitor_last_source);
        binding.rowLastDuration.tvInfoLabel.setText(R.string.monitor_last_duration);
        binding.rowLastQuality.tvInfoLabel.setText(R.string.monitor_last_quality);
        binding.rowSinceFull.tvInfoLabel.setText(R.string.monitor_since_full);

        binding.cardCombined.tvCardTitle.setText(R.string.usage_combined);
        binding.cardSessions.tvCardTitle.setText(R.string.usage_based_on);
        binding.cardScreenOn.tvCardTitle.setText(R.string.usage_screen_on);
        binding.cardScreenOff.tvCardTitle.setText(R.string.usage_screen_off);
        binding.cardChargeCount.tvCardTitle.setText(R.string.usage_charge_count);
        binding.cardChargeAverage.tvCardTitle.setText(R.string.usage_charge_daily);
    }

    private void loadLastCharge() {
        repository.loadLastChargeInfo(info -> {
            if (binding == null || info == null) return;
            bindLastCharge(info);
        });
    }

    private void bindLastCharge(@NonNull LastChargeInfo info) {
        final String noResult = getString(R.string.monitor_no_result);

        if (!info.hasSession()) {
            // Chưa từng ghi được lần sạc nào: để trống cả bốn dòng thay vì hiện số 0,
            // vì "sạc 0 phút" khác hẳn "chưa có dữ liệu"
            setInfo(binding.rowLastSpeed, noResult);
            setInfo(binding.rowLastSource, noResult);
            setInfo(binding.rowLastDuration, noResult);
            setInfo(binding.rowLastQuality, noResult);
        } else {
            setInfo(binding.rowLastSpeed, getString(info.getSpeed().getLabelRes()));
            setInfo(binding.rowLastSource, info.getPlugType());
            setInfo(binding.rowLastDuration, FormatUtils.formatDuration(info.getDurationMs()));
            setInfo(binding.rowLastQuality, getString(R.string.monitor_range,
                    info.getStartPercent(), info.getEndPercent()));
        }

        setInfo(binding.rowSinceFull, info.hasFullCharge()
                ? FormatUtils.formatDuration(info.getTimeSinceFullChargeMs())
                : noResult);
    }

    private void setInfo(@NonNull ViewDeviceInfoRowBinding row, @NonNull String value) {
        row.tvInfoValue.setText(value.isEmpty() ? getString(R.string.value_placeholder) : value);
    }

    // ==================== Thống kê ====================

    private void loadStats() {
        repository.loadUsageStats(stats -> {
            if (binding == null || stats == null) return;

            bindHealth(stats);
            bindAverageUsage(stats);
            bindFullBatteryEstimate(stats);
            bindChargeSummary(stats);
        });
    }

    private void bindHealth(@NonNull BatteryUsageStats stats) {
        if (stats.hasCapacityEstimate()) {
            final int health = stats.getHealthPercent();
            binding.tvHealthState.setText(String.format(Locale.getDefault(), "%d %%", health));
            binding.progressHealth.setProgress(health);
            binding.tvEstimatedCapacity.setText(
                    String.format(Locale.getDefault(), "%d mAh", stats.getEstimatedCapacityMah()));
        } else {
            binding.tvHealthState.setText(R.string.usage_measuring);
            binding.progressHealth.setProgress(0);
            binding.tvEstimatedCapacity.setText(R.string.value_placeholder);
        }

        binding.tvDesignCapacity.setText(
                stats.getDesignCapacityMah() == BatteryInfo.UNKNOWN_INT
                        ? getString(R.string.value_placeholder)
                        : String.format(Locale.getDefault(), "%d mAh",
                            stats.getDesignCapacityMah()));

        binding.tvHealthBasis.setText(getString(R.string.usage_health_basis,
                stats.getChargeSessionCount(),
                stats.getTotalChargedPercent(),
                Math.round(stats.getTotalChargedMah())));
    }

    private void bindAverageUsage(@NonNull BatteryUsageStats stats) {
        bindRateCard(binding.cardCombined, stats.getCombined());
        bindRateCard(binding.cardScreenOn, stats.getScreenOn());
        bindRateCard(binding.cardScreenOff, stats.getScreenOff());

        final int sessionCount = stats.getDischargeSessionCount();
        binding.cardSessions.tvCardValue.setText(getResources().getQuantityString(
                R.plurals.usage_sessions, sessionCount, sessionCount));
        binding.cardSessions.tvCardDetail.setText(R.string.usage_last_7_days);
    }

    private void bindRateCard(@NonNull ViewStatCardBinding card, @NonNull UsageRate rate) {
        card.tvCardValue.setText(
                String.format(Locale.getDefault(), "%.1f %%/h", rate.getPercentPerHour()));
        card.tvCardDetail.setText(getString(R.string.usage_rate_detail,
                (float) rate.getTotalPercentDrop(), rate.getTotalHours()));
    }

    private void bindFullBatteryEstimate(@NonNull BatteryUsageStats stats) {
        binding.cardFullEstimate.tvCombined.setText(formatFullBatteryTime(stats.getCombined()));
        binding.cardFullEstimate.tvScreenOn.setText(formatFullBatteryTime(stats.getScreenOn()));
        binding.cardFullEstimate.tvScreenOff.setText(formatFullBatteryTime(stats.getScreenOff()));
    }

    private String formatFullBatteryTime(@NonNull UsageRate rate) {
        if (!rate.hasData()) return getString(R.string.value_placeholder);
        return FormatUtils.formatDuration(
                Math.round(rate.getEstimatedFullBatteryHours() * 3_600_000f));
    }

    private void bindChargeSummary(@NonNull BatteryUsageStats stats) {
        binding.cardChargeCount.tvCardValue.setText(
                String.valueOf(stats.getChargeSessionCount()));
        binding.cardChargeCount.tvCardDetail.setText(stats.getFirstSessionTime() > 0
                ? getString(R.string.usage_charge_since,
                    DateUtils.formatDate(stats.getFirstSessionTime()))
                : getString(R.string.usage_last_7_days));

        binding.cardChargeAverage.tvCardValue.setText(String.format(Locale.getDefault(),
                "%.0f %%", stats.getAverageChargedPercentPerDay()));
        binding.cardChargeAverage.tvCardDetail.setText(R.string.usage_last_7_days);
    }

    // ==================== Đặt dung lượng thiết kế ====================

    /**
     * Cho phép nhập tay dung lượng thiết kế.
     * Cần thiết vì trên Android 14+ việc đọc PowerProfile qua reflection thường bị chặn.
     */
    private void showDesignCapacityDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.usage_capacity_dialog_title);

        final int current = repository.getDesignCapacityMah();
        if (current != BatteryInfo.UNKNOWN_INT && current > 0) {
            input.setText(String.valueOf(current));
        }

        final int padding = getResources().getDimensionPixelSize(R.dimen.space_md);
        final LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, padding, padding, 0);
        container.addView(input, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.usage_set_design_capacity)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> applyDesignCapacity(input.getText().toString()))
                .show();
    }

    private void applyDesignCapacity(@NonNull String rawValue) {
        int value;
        try {
            value = Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            value = 0;
        }

        if (value < MIN_CAPACITY_MAH || value > MAX_CAPACITY_MAH) {
            Toast.makeText(this, R.string.usage_capacity_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        repository.setDesignCapacity(value);
        loadStats();
    }
}
