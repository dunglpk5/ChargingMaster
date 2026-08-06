package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.device.CpuInfoReader;
import com.dung.chargmagagement.model.device.DetailSection;

import org.junit.Test;

/** Kiểm thử phần điều phối tab và định dạng xung nhịp. */
public class DetailSectionTest {

    @Test
    public void fromPosition_dungThuTuTheoYeuCau() {
        // Thứ tự tab phải khớp yêu cầu: DEVICE, SYSTEM, CPU, DISPLAY, NETWORK, SENSOR
        assertEquals(DetailSection.DEVICE, DetailSection.fromPosition(0));
        assertEquals(DetailSection.SYSTEM, DetailSection.fromPosition(1));
        assertEquals(DetailSection.CPU, DetailSection.fromPosition(2));
        assertEquals(DetailSection.DISPLAY, DetailSection.fromPosition(3));
        assertEquals(DetailSection.NETWORK, DetailSection.fromPosition(4));
        assertEquals(DetailSection.SENSOR, DetailSection.fromPosition(5));
    }

    @Test
    public void fromPosition_chiSoSai_traVeTabDau() {
        // ViewPager có thể hỏi vị trí ngoài phạm vi lúc khôi phục trạng thái
        assertEquals(DetailSection.DEVICE, DetailSection.fromPosition(-1));
        assertEquals(DetailSection.DEVICE, DetailSection.fromPosition(99));
    }

    @Test
    public void formatFrequency_doiKhzSangGhz() {
        assertEquals("2.40 GHz", CpuInfoReader.formatFrequency(2_400_000L));
        assertEquals("1.80 GHz", CpuInfoReader.formatFrequency(1_800_000L));
    }

    @Test
    public void formatFrequency_khongDocDuoc_traVeGachNgang() {
        assertEquals("-", CpuInfoReader.formatFrequency(0L));
        assertEquals("-", CpuInfoReader.formatFrequency(-1L));
    }

    @Test
    public void getCoreCount_luonDuong() {
        // Máy chạy test cũng phải trả về ít nhất 1 nhân
        assertEquals(true, CpuInfoReader.getCoreCount() >= 1);
    }
}
