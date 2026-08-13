package com.dung.chargmagagement.model.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Kiểm tra việc nhận dạng hãng chip theo mã. */
public class CpuVendorTest {

    @Test
    public void fromChipName_recognisesCommonVendors() {
        assertEquals("Mediatek", CpuVendor.fromChipName("MT6789"));
        assertEquals("Qualcomm", CpuVendor.fromChipName("SM8550"));
        assertEquals("Qualcomm", CpuVendor.fromChipName("msm8998"));
        assertEquals("Samsung", CpuVendor.fromChipName("Exynos 2100"));
        assertEquals("HiSilicon", CpuVendor.fromChipName("Kirin 990"));
        assertEquals("Google", CpuVendor.fromChipName("Tensor G3"));
        assertEquals("Unisoc", CpuVendor.fromChipName("SC9863A"));
    }

    @Test
    public void fromChipName_returnsNullWhenUnrecognised() {
        assertNull(CpuVendor.fromChipName(null));
        assertNull(CpuVendor.fromChipName("   "));
        assertNull(CpuVendor.fromChipName("Zebra9000"));
    }
}
