package com.dung.chargmagagement.model.ui;

import com.dung.chargmagagement.common.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Dựng lưới 7 cột cho lịch tháng.
 *
 * <p>Tuần bắt đầu từ <b>Thứ 2</b> đúng như bản thiết kế (Th2…CN), không theo mặc
 * định của {@link Calendar} vốn phụ thuộc vào Locale máy – ở Mỹ tuần bắt đầu từ
 * Chủ nhật, nếu để mặc định thì lịch sẽ lệch một cột trên máy đặt tiếng Anh.
 *
 * <p>Logic thuần Java, không phụ thuộc Android nên kiểm thử được.
 */
public final class MonthGridBuilder {

    /** Lịch luôn hiển thị đủ 6 hàng để chiều cao không nhảy khi đổi tháng. */
    public static final int ROW_COUNT = 6;
    public static final int COLUMN_COUNT = 7;
    public static final int CELL_COUNT = ROW_COUNT * COLUMN_COUNT;

    private MonthGridBuilder() {
    }

    /**
     * Dựng danh sách 42 ô cho tháng chứa {@code monthTimestamp}.
     *
     * @param monthTimestamp một mốc thời gian bất kỳ trong tháng cần hiển thị
     * @param selectedDayKey ngày đang được chọn (0 nếu không có)
     * @param todayKey       khoá ngày hôm nay
     */
    public static List<CalendarDay> build(long monthTimestamp, int selectedDayKey, int todayKey) {
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(DateUtils.startOfMonth(monthTimestamp));

        final int currentMonth = cursor.get(Calendar.MONTH);

        // Lùi về thứ 2 của tuần chứa ngày mùng 1
        cursor.add(Calendar.DAY_OF_MONTH, -daysFromMonday(cursor));

        List<CalendarDay> days = new ArrayList<>(CELL_COUNT);
        for (int i = 0; i < CELL_COUNT; i++) {
            final int dayKey = DateUtils.toDayKey(cursor);
            days.add(new CalendarDay(
                    dayKey,
                    cursor.get(Calendar.DAY_OF_MONTH),
                    cursor.get(Calendar.MONTH) == currentMonth,
                    dayKey == todayKey,
                    false,
                    dayKey == selectedDayKey));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return days;
    }

    /**
     * Số ngày cần lùi lại để về thứ 2.
     * Thứ 2 → 0, Thứ 3 → 1, …, Chủ nhật → 6.
     */
    static int daysFromMonday(Calendar calendar) {
        final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        // Calendar.SUNDAY = 1 … Calendar.SATURDAY = 7
        return (dayOfWeek + 5) % 7;
    }
}
