package com.dung.chargmagagement.controller.home.tab;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.CalendarAdapter;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.databinding.FragmentBatteryUsageBinding;
import com.dung.chargmagagement.databinding.ViewStatCardBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.repository.BatteryRepository;
import com.dung.chargmagagement.model.stats.BatteryUsageStats;
import com.dung.chargmagagement.model.stats.UsageRate;
import com.dung.chargmagagement.model.ui.CalendarDay;
import com.dung.chargmagagement.model.ui.ChartPoint;
import com.dung.chargmagagement.model.ui.MonthGridBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Tab "Sử dụng pin": lịch tháng, biểu đồ mức pin trong ngày và các khối thống kê.
 *
 * <p>Dữ liệu được nạp lại mỗi lần tab hiện lên và mỗi khi người dùng chọn ngày
 * khác. Không đăng ký {@code BatteryMonitor} ở đây vì màn này nhìn về quá khứ,
 * không cần số liệu thời gian thực – nhờ vậy chuyển sang tab này là việc lấy mẫu
 * dừng hẳn nếu hai tab kia cũng đang ẩn.
 */
public class BatteryUsageFragment extends BaseFragment<FragmentBatteryUsageBinding>
        implements CalendarAdapter.OnDayClickListener {

    private static final int MIN_CAPACITY_MAH = 500;
    private static final int MAX_CAPACITY_MAH = 30_000;

    private BatteryRepository repository;
    private CalendarAdapter calendarAdapter;

    /** Tháng đang hiển thị trên lịch. */
    private long displayedMonth = System.currentTimeMillis();

    /** Ngày đang được chọn (mặc định là hôm nay). */
    private int selectedDayKey = DateUtils.todayKey();

    @NonNull
    @Override
    protected FragmentBatteryUsageBinding onCreateBinding(@NonNull LayoutInflater inflater,
                                                          @Nullable ViewGroup container) {
        return FragmentBatteryUsageBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        repository = BatteryRepository.get(requireContext());

        setupWeekHeader();
        setupCalendar();
        setupStaticCardTitles();

        binding.btnSetDesignCapacity.setOnClickListener(v -> showDesignCapacityDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        renderCalendar();
        loadChart();
        loadStats();
    }

    // ==================== Lịch tháng ====================

    /** Dựng hàng tiêu đề Th2…CN bằng code để chia đều 7 cột. */
    private void setupWeekHeader() {
        final String[] labels = getResources().getStringArray(R.array.weekday_short);
        binding.weekHeader.removeAllViews();

        for (String label : labels) {
            TextView textView = new TextView(requireContext());
            textView.setText(label);
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(13f);
            textView.setTextColor(getResources().getColor(R.color.text_on_primary_60, null));
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            binding.weekHeader.addView(textView);
        }
    }

    private void setupCalendar() {
        calendarAdapter = new CalendarAdapter();
        calendarAdapter.setOnDayClickListener(this);

        binding.rvCalendar.setLayoutManager(
                new GridLayoutManager(requireContext(), MonthGridBuilder.COLUMN_COUNT));
        binding.rvCalendar.setHasFixedSize(true);
        binding.rvCalendar.setAdapter(calendarAdapter);
    }

    /** Dựng lưới ngày rồi hỏi database xem ngày nào có dữ liệu để chấm dấu. */
    private void renderCalendar() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(displayedMonth);

        binding.tvYear.setText(String.valueOf(calendar.get(Calendar.YEAR)));
        binding.tvMonth.setText(
                getResources().getStringArray(R.array.month_names)[calendar.get(Calendar.MONTH)]);

        final List<CalendarDay> days = MonthGridBuilder.build(
                displayedMonth, selectedDayKey, DateUtils.todayKey());
        calendarAdapter.submitList(days);

        markDaysHavingData(days);
    }

    private void markDaysHavingData(@NonNull List<CalendarDay> days) {
        if (days.isEmpty()) return;

        final int fromKey = days.get(0).getDayKey();
        final int toKey = days.get(days.size() - 1).getDayKey();

        repository.loadDaysHavingData(fromKey, toKey, result -> {
            if (binding == null || result == null) return;

            List<CalendarDay> updated = new ArrayList<>(days.size());
            for (CalendarDay day : days) {
                updated.add(day.withHasData(result.contains(day.getDayKey())));
            }
            calendarAdapter.submitList(updated);
        });
    }

    @Override
    public void onDayClick(@NonNull CalendarDay day) {
        if (day.getDayKey() == selectedDayKey) return;

        selectedDayKey = day.getDayKey();
        // Bấm vào ngày của tháng khác thì chuyển luôn sang tháng đó
        if (!day.isInCurrentMonth()) {
            displayedMonth = DateUtils.dayKeyToMillis(day.getDayKey());
        }

        renderCalendar();
        loadChart();
    }

    // ==================== Biểu đồ ====================

    private void loadChart() {
        repository.loadSamplesOfDay(selectedDayKey, samples -> {
            if (binding == null) return;

            List<ChartPoint> points = new ArrayList<>();
            if (samples != null) {
                for (com.dung.chargmagagement.model.entity.BatterySampleEntity sample : samples) {
                    points.add(new ChartPoint(
                            DateUtils.minuteOfDay(sample.timestamp),
                            sample.percent,
                            sample.charging));
                }
            }

            binding.chartBattery.setPoints(points);
            binding.tvChartEmpty.setVisibility(points.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    // ==================== Thống kê ====================

    /** Tiêu đề các thẻ là cố định nên gán một lần lúc dựng màn. */
    private void setupStaticCardTitles() {
        binding.cardCombined.tvCardTitle.setText(R.string.usage_combined);
        binding.cardSessions.tvCardTitle.setText(R.string.usage_based_on);
        binding.cardScreenOn.tvCardTitle.setText(R.string.usage_screen_on);
        binding.cardScreenOff.tvCardTitle.setText(R.string.usage_screen_off);

        binding.cardChargeCount.tvCardTitle.setText(R.string.usage_charge_count);
        binding.cardChargeAverage.tvCardTitle.setText(R.string.usage_charge_average);
    }

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

            // Số mAh đã mất so với dung lượng thiết kế
            final int lost = stats.getDesignCapacityMah() - stats.getEstimatedCapacityMah();
            binding.tvHealthNote.setText(lost > 0
                    ? getString(R.string.usage_health_note, lost)
                    : getString(R.string.value_placeholder));
        } else {
            // Chưa đủ phiên sạc dài để ước tính - hiển thị "đang đo…" như thiết kế
            binding.tvHealthState.setText(R.string.usage_measuring);
            binding.progressHealth.setProgress(0);
            binding.tvEstimatedCapacity.setText(R.string.value_placeholder);
            binding.tvHealthNote.setText(R.string.value_placeholder);
        }

        binding.tvDesignCapacity.setText(
                stats.getDesignCapacityMah() == BatteryInfo.UNKNOWN_INT
                        ? getString(R.string.value_placeholder)
                        : String.format(Locale.getDefault(), "%d mAh",
                            stats.getDesignCapacityMah()));
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

    /**
     * Thẻ tốc độ tiêu hao: dòng lớn "%/h", dòng nhỏ "x % in y h".
     *
     * <p>Dùng {@link Locale#getDefault()} chứ không phải {@code Locale.US}: người
     * Việt viết số thập phân bằng dấu phẩy ("0,0 %/h"), đúng như bản thiết kế.
     */
    private void bindRateCard(@NonNull ViewStatCardBinding card, @NonNull UsageRate rate) {
        card.tvCardValue.setText(
                String.format(Locale.getDefault(), "%.1f %%/h", rate.getPercentPerHour()));
        card.tvCardDetail.setText(String.format(Locale.getDefault(), "%.1f %% in %.1f h",
                (float) rate.getTotalPercentDrop(), rate.getTotalHours()));
    }

    /** Thẻ 3 cột ước tính thời gian dùng được khi pin đầy. */
    private void bindFullBatteryEstimate(@NonNull BatteryUsageStats stats) {
        binding.cardFullEstimate.tvCombined.setText(formatFullBatteryTime(stats.getCombined()));
        binding.cardFullEstimate.tvScreenOn.setText(formatFullBatteryTime(stats.getScreenOn()));
        binding.cardFullEstimate.tvScreenOff.setText(formatFullBatteryTime(stats.getScreenOff()));
    }

    private String formatFullBatteryTime(@NonNull UsageRate rate) {
        if (!rate.hasData()) return getString(R.string.value_placeholder);

        final float hours = rate.getEstimatedFullBatteryHours();
        return FormatUtils.formatDuration(Math.round(hours * 3_600_000f));
    }

    private void bindChargeSummary(@NonNull BatteryUsageStats stats) {
        binding.cardChargeCount.tvCardValue.setText(
                String.valueOf(stats.getChargeSessionCount()));
        binding.cardChargeCount.tvCardDetail.setText(R.string.usage_last_7_days);

        binding.cardChargeAverage.tvCardValue.setText(String.format(Locale.getDefault(),
                "%.1f %%", stats.getAverageChargedPercentPerSession()));
        binding.cardChargeAverage.tvCardDetail.setText(
                String.format(Locale.getDefault(), "%d %%", stats.getTotalChargedPercent()));
    }

    // ==================== Đặt dung lượng thiết kế ====================

    /**
     * Cho phép người dùng nhập tay dung lượng thiết kế.
     * Cần thiết vì trên Android 14+ việc đọc PowerProfile qua reflection thường bị chặn.
     */
    private void showDesignCapacityDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.usage_capacity_dialog_title);

        final int current = repository.getDesignCapacityMah();
        if (current != BatteryInfo.UNKNOWN_INT && current > 0) {
            input.setText(String.valueOf(current));
        }

        final int padding = getResources().getDimensionPixelSize(R.dimen.space_md);
        final LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(padding, padding, padding, 0);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
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
            Toast.makeText(requireContext(), R.string.usage_capacity_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        repository.setDesignCapacity(value);
        loadStats();
    }
}
