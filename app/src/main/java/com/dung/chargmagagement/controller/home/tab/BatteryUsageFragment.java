package com.dung.chargmagagement.controller.home.tab;

import android.os.Bundle;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.AppUsageAdapter;
import com.dung.chargmagagement.controller.adapter.CalendarAdapter;
import com.dung.chargmagagement.model.device.AppUsageProvider;
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

    /** Cửa sổ thống kê cho danh sách ứng dụng, khớp với các mục còn lại. */
    private static final int APP_USAGE_WINDOW_DAYS = 7;

    /**
     * Tốc độ vuốt tối thiểu để tính là lật tháng (pixel/giây).
     * Thấp hơn nữa thì lịch nhảy tháng mỗi khi người dùng cuộn trang hơi chéo.
     */
    private static final float MIN_SWIPE_VELOCITY = 600f;

    /** Thời gian trượt ra và trượt vào khi đổi tháng (ms). */
    private static final long SLIDE_OUT_MS = 140L;
    private static final long SLIDE_IN_MS = 200L;

    /** Trạng thái phân xử cử chỉ ngang với ViewPager2, xem claimHorizontalGesture. */
    private int touchSlop;
    private float touchStartX;
    private float touchStartY;
    private boolean gestureDirectionKnown;

    /** Đang chạy hiệu ứng đổi tháng; chặn cú vuốt mới chồng lên. */
    private boolean monthAnimating;

    private BatteryRepository repository;
    private CalendarAdapter calendarAdapter;
    private AppUsageProvider appUsageProvider;
    private AppUsageAdapter appAdapter;

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
        setupApps();

        binding.btnSetDesignCapacity.setOnClickListener(v -> showDesignCapacityDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        renderCalendar();
        loadChart();
        loadStats();
        loadApps();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Rời tab giữa chừng thì hiệu ứng bị bỏ dở, để nguyên là lần sau quay lại
        // lưới lịch vẫn nằm lệch ra ngoài màn hình và mờ tịt
        binding.rvCalendar.animate().cancel();
        binding.rvCalendar.setTranslationX(0f);
        binding.rvCalendar.setAlpha(1f);
        monthAnimating = false;
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
        binding.rvCalendar.setAdapter(calendarAdapter);
        // Tắt hiệu ứng mặc định của RecyclerView: nó làm từng ô ngày mờ dần rồi
        // trồi lên theo phương dọc mỗi lần submitList, trông như lịch rơi từ trên
        // xuống. Hiệu ứng trượt ngang do animateMonthChange() lo.
        binding.rvCalendar.setItemAnimator(null);
        // Tuyệt đối không gọi setHasFixedSize(true) ở đây. Lưới này cao wrap_content
        // trong NestedScrollView, còn dữ liệu thì tới sau khi bố cục đã đo xong:
        // markDaysHavingData() truy vấn database rồi mới submitList lần hai. Cờ đó
        // khiến RecyclerView bỏ qua requestLayout khi có dữ liệu mới, nên lưới giữ
        // nguyên chiều cao 0 và cả tháng biến mất, chỉ còn trơ hàng tiêu đề thứ.

        setupMonthSwipe();
    }

    /**
     * Vuốt ngang trên khối lịch để đổi tháng.
     *
     * <p>Vuốt sang phải lùi về tháng trước. Vuốt sang trái chỉ có tác dụng khi đang
     * đứng ở một tháng trong quá khứ – ở tháng hiện tại thì không đi tiếp được, vì
     * tháng sau chưa tới và sẽ chỉ là một lưới trống.
     *
     * <p>Gắn {@code OnItemTouchListener} lên RecyclerView thay vì
     * {@code setOnTouchListener}: lưới lịch tự xử lý chạm để bắt sự kiện bấm vào ô
     * ngày, nên listener thường sẽ không bao giờ nhận được chuỗi cử chỉ đầy đủ.
     * Cách này cho ta xem trước sự kiện trước khi RecyclerView tiêu thụ nó.
     */
    private void setupMonthSwipe() {
        touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();

        final GestureDetector detector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(@Nullable MotionEvent down, @NonNull MotionEvent move,
                                           float velocityX, float velocityY) {
                        // Bỏ qua cử chỉ nghiêng về phương dọc: đó là người dùng đang
                        // cuộn trang chứ không phải lật lịch
                        if (Math.abs(velocityX) < Math.abs(velocityY)) return false;
                        if (Math.abs(velocityX) < MIN_SWIPE_VELOCITY) return false;

                        if (velocityX > 0) {
                            shiftMonth(-1);
                        } else if (!isShowingCurrentMonth()) {
                            shiftMonth(1);
                        }
                        return true;
                    }
                });

        binding.rvCalendar.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                claimHorizontalGesture(rv, e);
                // Trả về false luôn: ta chỉ quan sát chuỗi sự kiện để nhận ra cú
                // vuốt, còn cú chạm chọn ngày vẫn phải tới được ô ngày như thường
                detector.onTouchEvent(e);
                return false;
            }
        });

        // Bắt cả cú vuốt ở vùng tên tháng và hàng tiêu đề thứ, không chỉ trên lưới
        // ngày – người dùng vuốt ở đâu trong khối lịch cũng phải lật được tháng
        binding.calendarSection.setOnTouchListener((v, event) -> {
            claimHorizontalGesture(v, event);
            detector.onTouchEvent(event);
            // Không nuốt sự kiện: khối lịch không có gì để bấm, trả false để hệ
            // thống xử lý tiếp như bình thường
            return false;
        });
    }

    /**
     * Giành quyền xử lý cử chỉ ngang khỏi ViewPager2 của thanh tab.
     *
     * <p><b>Vấn đề:</b> tab này nằm trong ViewPager2, vốn cũng lật trang bằng cử chỉ
     * ngang. Thành phần cha luôn được hỏi {@code onInterceptTouchEvent} trước con,
     * nên ngay khi ngón tay đi ngang quá ngưỡng, ViewPager2 cướp luôn chuỗi sự kiện
     * và lịch không bao giờ thấy cú vuốt – người dùng chỉ thấy màn hình nhảy sang
     * tab khác.
     *
     * <p><b>Cách xử lý:</b> chặn cha ngay từ lúc ngón tay chạm xuống, rồi thả ra nếu
     * hoá ra người dùng đang vuốt dọc. Không thể chờ tới lúc biết chắc là vuốt ngang
     * mới chặn, vì lúc đó ViewPager2 đã cướp mất rồi. Phải thả lại đúng lúc, nếu
     * không thì cả trang mất khả năng cuộn dọc.
     */
    private void claimHorizontalGesture(@NonNull View view, @NonNull MotionEvent event) {
        final ViewParent parent = view.getParent();
        if (parent == null) return;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                gestureDirectionKnown = false;
                parent.requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (gestureDirectionKnown) break;

                final float dx = Math.abs(event.getX() - touchStartX);
                final float dy = Math.abs(event.getY() - touchStartY);
                if (Math.max(dx, dy) < touchSlop) break;

                gestureDirectionKnown = true;
                if (dy > dx) {
                    // Vuốt dọc: trả quyền lại để NestedScrollView cuộn trang
                    parent.requestDisallowInterceptTouchEvent(false);
                }
                break;

            default:
                // Nhả tay hoặc cử chỉ bị huỷ: luôn trả quyền lại cho thành phần cha
                parent.requestDisallowInterceptTouchEvent(false);
                break;
        }
    }

    /**
     * Lùi/tiến một tháng trên lịch.
     *
     * <p>Chỉ đổi tháng đang xem, <b>không đổi ngày đang chọn</b>: người dùng lật lịch
     * để tìm ngày, biểu đồ chỉ nên đổi khi họ thật sự bấm vào một ngày cụ thể.
     */
    private void shiftMonth(int delta) {
        if (monthAnimating) return; // vuốt dồn dập không được chồng lên nhau

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(displayedMonth);
        // Về ngày 1 trước khi cộng tháng: đang ở ngày 31 mà cộng sang tháng 30 ngày
        // thì Calendar tự nhảy sang tháng kế tiếp, lật một cái mất luôn hai tháng
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, delta);

        displayedMonth = calendar.getTimeInMillis();
        animateMonthChange(delta);
    }

    /**
     * Trượt ngang khi đổi tháng, cùng chiều với ngón tay.
     *
     * <p>Lùi tháng thì lưới cũ trôi sang phải và tháng mới vào từ bên trái – giống
     * cảm giác kéo trang lịch giấy sang phải để lộ trang trước đó. Tiến tháng thì
     * ngược lại.
     *
     * <p>Hiệu ứng mặc định của RecyclerView đã bị tắt ở {@code setupCalendar}: nó
     * làm từng ô ngày mờ dần rồi trồi lên theo phương dọc, trông như lịch rơi từ
     * trên xuống chứ không liên quan gì tới hướng vuốt.
     */
    private void animateMonthChange(int delta) {
        final View grid = binding.rvCalendar;
        final float width = grid.getWidth();

        // Chưa đo xong bố cục thì không có gì để trượt, đổi thẳng cho xong
        if (width <= 0f) {
            renderCalendar();
            return;
        }

        final float exitX = delta < 0 ? width : -width;
        monthAnimating = true;

        grid.animate()
                .translationX(exitX)
                .alpha(0f)
                .setDuration(SLIDE_OUT_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (binding == null) {
                        monthAnimating = false;
                        return;
                    }
                    renderCalendar();

                    // Vào từ phía đối diện với phía vừa trôi ra
                    grid.setTranslationX(-exitX);
                    grid.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(SLIDE_IN_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> monthAnimating = false)
                            .start();
                })
                .start();
    }

    /** Lịch đang hiển thị đúng tháng hiện tại hay không. */
    private boolean isShowingCurrentMonth() {
        Calendar shown = Calendar.getInstance();
        shown.setTimeInMillis(displayedMonth);

        Calendar now = Calendar.getInstance();
        return shown.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && shown.get(Calendar.MONTH) == now.get(Calendar.MONTH);
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

            // Ngày chưa có ghi nhận nào thì ẩn hẳn biểu đồ thay vì vẽ một khung
            // trống: khung trống trông y hệt như biểu đồ bị lỗi
            final boolean empty = points.isEmpty();
            binding.chartContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.tvChartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

            if (!empty) binding.chartBattery.setPoints(points);
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
        binding.cardChargeAverage.tvCardTitle.setText(R.string.usage_charge_daily);
        binding.cardChargingNow.tvCardTitle.setText(R.string.usage_charging_now);
        binding.cardChargingSince.tvCardTitle.setText(R.string.usage_charging_since);
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
            binding.tvHealthBasis.setText(getString(R.string.usage_health_basis,
                    stats.getChargeSessionCount(),
                    stats.getTotalChargedPercent(),
                    Math.round(stats.getTotalChargedMah())));

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
            binding.tvHealthBasis.setText(getString(R.string.usage_health_basis,
                    stats.getChargeSessionCount(),
                    stats.getTotalChargedPercent(),
                    Math.round(stats.getTotalChargedMah())));
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
        card.tvCardDetail.setText(getString(R.string.usage_rate_detail,
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
        binding.cardChargeCount.tvCardValue.setText(getResources().getQuantityString(
                R.plurals.usage_sessions, stats.getChargeSessionCount(),
                stats.getChargeSessionCount()));
        binding.cardChargeCount.tvCardDetail.setText(stats.getFirstSessionTime() > 0
                ? getString(R.string.usage_charge_since,
                    DateUtils.formatDate(stats.getFirstSessionTime()))
                : getString(R.string.usage_last_7_days));

        binding.cardChargeAverage.tvCardValue.setText(String.format(Locale.getDefault(),
                "%.0f %%", stats.getAverageChargedPercentPerDay()));
        binding.cardChargeAverage.tvCardDetail.setText(R.string.usage_last_7_days);

        bindActiveCharge(stats);
    }

    /** Hai thẻ về phiên sạc đang chạy; ẩn hẳn khi máy không cắm sạc. */
    private void bindActiveCharge(@NonNull BatteryUsageStats stats) {
        if (!stats.isChargingNow()) {
            binding.rowActiveCharge.setVisibility(View.GONE);
            return;
        }

        binding.rowActiveCharge.setVisibility(View.VISIBLE);
        binding.cardChargingNow.tvCardValue.setText(String.format(Locale.getDefault(),
                "%d %%", stats.getActiveSessionGainedPercent()));
        binding.cardChargingNow.tvCardDetail.setText(R.string.usage_charging_now);

        binding.cardChargingSince.tvCardValue.setText(
                DateUtils.formatTime(stats.getActiveSessionStartTime()));
        binding.cardChargingSince.tvCardDetail.setText(
                DateUtils.formatDate(stats.getActiveSessionStartTime()));
    }

    // ==================== Sử dụng pin ứng dụng ====================

    private void setupApps() {
        appUsageProvider = new AppUsageProvider(requireContext());
        appAdapter = new AppUsageAdapter();

        binding.rvApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvApps.setAdapter(appAdapter);

        binding.btnGrantUsageAccess.setOnClickListener(v -> openUsageAccessSettings());
    }

    /**
     * Nạp danh sách ứng dụng dùng nhiều nhất.
     *
     * <p>Gọi lại ở {@code onResume} vì người dùng có thể vừa sang Cài đặt cấp quyền
     * rồi bấm quay lại – lúc đó khối xin quyền phải biến mất ngay.
     */
    private void loadApps() {
        final boolean granted = appUsageProvider.hasPermission();
        binding.permissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);

        if (!granted) {
            binding.rvApps.setVisibility(View.GONE);
            binding.tvAppsEmpty.setVisibility(View.GONE);
            return;
        }

        final long from = DateUtils.daysAgo(APP_USAGE_WINDOW_DAYS);
        executors.execute(() -> appUsageProvider.loadTopApps(from), result -> {
            if (binding == null) return;

            final boolean empty = result == null || result.isEmpty();
            binding.tvAppsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvApps.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) appAdapter.submitList(result);
        });
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(AppUsageProvider.buildPermissionIntent());
        } catch (Exception e) {
            // Một số ROM rút gọn không có trang này
            Toast.makeText(requireContext(), R.string.check_settings_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
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
