package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.common.FormatUtils;

import org.junit.Test;

/** Kiểm tra các hàm định dạng dùng chung. */
public class FormatUtilsTest {

    @Test
    public void celsiusToFahrenheit_dungCongThuc() {
        assertEquals(98.6f, FormatUtils.celsiusToFahrenheit(37f), 0.01f);
        assertEquals(32f, FormatUtils.celsiusToFahrenheit(0f), 0.01f);
    }

    @Test
    public void formatTemperature_dungDinhDangThietKe() {
        assertEquals("37.0℃/ 99℉", FormatUtils.formatTemperature(37f));
    }

    @Test
    public void formatDuration_traVeGachNgangKhiKhongXacDinh() {
        assertEquals("-", FormatUtils.formatDuration(0));
        assertEquals("45m", FormatUtils.formatDuration(45 * 60_000L));
        assertEquals("2h 15m", FormatUtils.formatDuration((2 * 60 + 15) * 60_000L));
    }

    @Test
    public void clampPercent_gioiHanTrongKhoang0_100() {
        assertEquals(0, FormatUtils.clampPercent(-12f));
        assertEquals(100, FormatUtils.clampPercent(180f));
        assertEquals(32, FormatUtils.clampPercent(31.6f));
    }
}
