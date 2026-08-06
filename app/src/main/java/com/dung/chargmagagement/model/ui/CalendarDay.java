package com.dung.chargmagagement.model.ui;

/**
 * Một ô ngày trên lịch tháng.
 */
public final class CalendarDay {

    /** Khoá ngày dạng yyyyMMdd. */
    private final int dayKey;

    /** Số hiển thị trong ô (1..31). */
    private final int dayOfMonth;

    /** Ngày này có thuộc tháng đang xem không (ô đầu/cuối lưới thì không). */
    private final boolean inCurrentMonth;

    private final boolean today;

    /** Có dữ liệu đo trong ngày này không. */
    private final boolean hasData;

    private final boolean selected;

    public CalendarDay(int dayKey, int dayOfMonth, boolean inCurrentMonth,
                       boolean today, boolean hasData, boolean selected) {
        this.dayKey = dayKey;
        this.dayOfMonth = dayOfMonth;
        this.inCurrentMonth = inCurrentMonth;
        this.today = today;
        this.hasData = hasData;
        this.selected = selected;
    }

    /** Tạo bản sao với trạng thái chọn khác, dùng khi người dùng đổi ngày. */
    public CalendarDay withSelected(boolean newSelected) {
        return new CalendarDay(dayKey, dayOfMonth, inCurrentMonth, today, hasData, newSelected);
    }

    /** Tạo bản sao có đánh dấu dữ liệu, dùng khi truy vấn database trả về. */
    public CalendarDay withHasData(boolean newHasData) {
        return new CalendarDay(dayKey, dayOfMonth, inCurrentMonth, today, newHasData, selected);
    }

    public int getDayKey() {
        return dayKey;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public boolean isInCurrentMonth() {
        return inCurrentMonth;
    }

    public boolean isToday() {
        return today;
    }

    public boolean hasData() {
        return hasData;
    }

    public boolean isSelected() {
        return selected;
    }
}
