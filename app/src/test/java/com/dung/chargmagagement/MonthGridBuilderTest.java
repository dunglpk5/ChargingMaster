package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dung.chargmagagement.model.ui.CalendarDay;
import com.dung.chargmagagement.model.ui.MonthGridBuilder;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;

/**
 * Kiểm thử phần dựng lưới lịch tháng.
 *
 * <p>Ca kiểm chứng chính lấy thẳng từ ảnh thiết kế: tháng 8/2026 phải bắt đầu
 * bằng hàng "27 28 29 30 31 1 2".
 */
public class MonthGridBuilderTest {

    private static long millisOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTimeInMillis();
    }

    private static List<CalendarDay> august2026() {
        return MonthGridBuilder.build(millisOf(2026, 8, 6), 20260806, 20260806);
    }

    @Test
    public void build_luonTraVeDu42O() {
        assertEquals(MonthGridBuilder.CELL_COUNT, august2026().size());
        // Tháng 2 ngắn nhất cũng phải đủ 6 hàng để chiều cao lịch không nhảy
        assertEquals(MonthGridBuilder.CELL_COUNT,
                MonthGridBuilder.build(millisOf(2026, 2, 10), 0, 0).size());
    }

    @Test
    public void build_hangDauKhopVoiBanThietKe() {
        List<CalendarDay> days = august2026();
        // Đúng như ảnh: 27 28 29 30 31 | 1 2
        int[] expected = {27, 28, 29, 30, 31, 1, 2};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("ô thứ " + i, expected[i], days.get(i).getDayOfMonth());
        }
    }

    @Test
    public void build_tuanBatDauTuThuHai() {
        // Ô đầu tiên của lưới luôn phải là Thứ 2, không phụ thuộc Locale máy
        List<CalendarDay> days = august2026();
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        final int dayKey = days.get(0).getDayKey();
        calendar.set(dayKey / 10000, (dayKey / 100) % 100 - 1, dayKey % 100);
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void build_danhDauDungNgayThuocThangDangXem() {
        List<CalendarDay> days = august2026();
        assertFalse("ngày 27/7 không thuộc tháng 8", days.get(0).isInCurrentMonth());
        assertTrue("ngày 1/8 thuộc tháng 8", days.get(5).isInCurrentMonth());
    }

    @Test
    public void build_danhDauDungHomNayVaNgayDangChon() {
        List<CalendarDay> days = august2026();

        int todayCount = 0;
        int selectedCount = 0;
        for (CalendarDay day : days) {
            if (day.isToday()) todayCount++;
            if (day.isSelected()) selectedCount++;
            if (day.getDayKey() == 20260806) {
                assertTrue(day.isToday());
                assertTrue(day.isSelected());
            }
        }
        // Chỉ đúng một ô được đánh dấu, tránh lỗi trùng ngày giữa hai tháng
        assertEquals(1, todayCount);
        assertEquals(1, selectedCount);
    }

    @Test
    public void build_khoaNgayTangDanLienTuc() {
        List<CalendarDay> days = august2026();
        for (int i = 1; i < days.size(); i++) {
            assertTrue("khoá ngày phải tăng dần",
                    days.get(i).getDayKey() > days.get(i - 1).getDayKey());
        }
    }

    @Test
    public void build_chuyenGiaoNam_hoatDongDung() {
        // Tháng 1/2027: lưới phải chứa cả ngày của tháng 12/2026
        List<CalendarDay> days = MonthGridBuilder.build(millisOf(2027, 1, 15), 0, 0);
        assertEquals(20261228, days.get(0).getDayKey());
        assertFalse(days.get(0).isInCurrentMonth());
    }

    @Test
    public void withSelected_khongDoiCacThuocTinhKhac() {
        CalendarDay day = august2026().get(5);
        CalendarDay updated = day.withSelected(true);

        assertTrue(updated.isSelected());
        assertEquals(day.getDayKey(), updated.getDayKey());
        assertEquals(day.getDayOfMonth(), updated.getDayOfMonth());
        assertEquals(day.isInCurrentMonth(), updated.isInCurrentMonth());
        assertEquals(day.isToday(), updated.isToday());
    }
}
