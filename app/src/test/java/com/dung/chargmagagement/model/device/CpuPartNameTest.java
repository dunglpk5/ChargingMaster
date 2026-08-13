package com.dung.chargmagagement.model.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Kiểm tra việc tra tên nhân từ mã "CPU part". */
public class CpuPartNameTest {

    @Test
    public void parsePart_acceptsHexWithAndWithoutPrefix() {
        assertEquals(0xd05, CpuPartName.parsePart("0xd05"));
        assertEquals(0xd05, CpuPartName.parsePart(" 0xD05 "));
        assertEquals(0xd0b, CpuPartName.parsePart("d0b"));
    }

    @Test
    public void parsePart_returnsMinusOneWhenInvalid() {
        assertEquals(-1, CpuPartName.parsePart(null));
        assertEquals(-1, CpuPartName.parsePart(""));
        assertEquals(-1, CpuPartName.parsePart("không phải số"));
    }

    @Test
    public void fromPart_mapsKnownArmCores() {
        assertEquals("Cortex-A55", CpuPartName.fromPart(0xd05));
        assertEquals("Cortex-A76", CpuPartName.fromPart(0xd0b));
        assertEquals("Cortex-A520", CpuPartName.fromPart(0xd80));
    }

    /** Nhân do hãng tự thiết kế không có trong danh mục, phải trả null. */
    @Test
    public void fromPart_returnsNullForUnknown() {
        assertNull(CpuPartName.fromPart(-1));
        assertNull(CpuPartName.fromPart(0x802));
    }
}
