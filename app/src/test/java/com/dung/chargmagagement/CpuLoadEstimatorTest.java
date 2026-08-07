package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.device.CpuLoadEstimator;

import org.junit.Test;

/**
 * Kiểm thử cách ước tính tải CPU theo xung nhịp – phần dự phòng khi ROM chặn
 * đọc {@code /proc/stat}.
 */
public class CpuLoadEstimatorTest {

    // Dải xung điển hình của một nhân: 500 MHz .. 2200 MHz
    private static final long MIN = 500_000L;
    private static final long MAX = 2_200_000L;

    @Test
    public void xungToiDa_choRaMotTramPhanTram() {
        assertEquals(100, CpuLoadEstimator.estimatePercent(MAX, MIN, MAX));
    }

    @Test
    public void xungToiThieu_choRaKhong() {
        assertEquals(0, CpuLoadEstimator.estimatePercent(MIN, MIN, MAX));
    }

    @Test
    public void xungGiuaDai_choRaKhoangGiua() {
        // 1350 MHz nằm đúng giữa 500 và 2200
        assertEquals(50, CpuLoadEstimator.estimatePercent(1_350_000L, MIN, MAX));
    }

    @Test
    public void nhanDangNgu_laRanhChuKhongPhaiLoi() {
        assertEquals(0, CpuLoadEstimator.estimatePercent(0L, MIN, MAX));
    }

    @Test
    public void vuotNgoaiDai_biKepLai() {
        assertEquals(100, CpuLoadEstimator.estimatePercent(MAX * 2, MIN, MAX));
    }

    @Test
    public void khongDocDuocDaiXung_traVeKhongXacDinh() {
        assertEquals(CpuLoadEstimator.UNKNOWN,
                CpuLoadEstimator.estimatePercent(1_000_000L, 0L, 0L));
        // min bằng max thì không chia được
        assertEquals(CpuLoadEstimator.UNKNOWN,
                CpuLoadEstimator.estimatePercent(1_000_000L, MAX, MAX));
    }
}
