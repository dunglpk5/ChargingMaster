package com.dung.chargmagagement.model.device;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

/** Kiểm tra cách hiển thị xung nhịp CPU. */
public class ClockFormatTest {

    @Test
    public void format_belowOneGhzUsesMhz() {
        assertEquals("500 MHz", ClockFormat.format(500_000L, Locale.US));
        assertEquals("725 MHz", ClockFormat.format(725_000L, Locale.US));
    }

    /** 2.0 GHz phải hiện "2 GHz", không kèm số 0 thừa. */
    @Test
    public void format_dropsTrailingZeroDecimal() {
        assertEquals("2 GHz", ClockFormat.format(2_000_000L, Locale.US));
    }

    @Test
    public void format_keepsOneDecimalWhenMeaningful() {
        assertEquals("2.2 GHz", ClockFormat.format(2_200_000L, Locale.US));
    }

    /** Dấu thập phân đổi theo ngôn ngữ; bản tiếng Việt dùng dấu phẩy. */
    @Test
    public void format_usesLocaleDecimalSeparator() {
        assertEquals("2,2 GHz", ClockFormat.format(2_200_000L, new Locale("vi", "VN")));
    }

    /** Không đọc được thì trả chuỗi rỗng để phía gọi tự quyết định hiển thị gì. */
    @Test
    public void format_returnsEmptyWhenUnknown() {
        assertEquals("", ClockFormat.format(0L, Locale.US));
        assertEquals("", ClockFormat.format(-1L, Locale.US));
    }
}
