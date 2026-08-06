package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.CurrentStats;

import org.junit.Before;
import org.junit.Test;

/** Kiểm thử bộ lọc nhiễu và thống kê dòng điện. */
public class CurrentStatsTest {

    private CurrentStats stats;

    @Before
    public void setUp() {
        stats = new CurrentStats();
    }

    @Test
    public void chuaCoMau_traVeKhongXacDinh() {
        assertEquals(BatteryInfo.UNKNOWN_INT, stats.getSmoothedMa());
        assertEquals(BatteryInfo.UNKNOWN_INT, stats.getAverageMa());
    }

    @Test
    public void trungVi_locDuocMauNhieuDotBien() {
        stats.addSample(1500);
        stats.addSample(1520);
        stats.addSample(9000); // nhiễu đột biến
        stats.addSample(1490);
        stats.addSample(1510);
        // Trung bình sẽ bị kéo lên ~3000, trung vị vẫn bám giá trị thật
        assertEquals(1510, stats.getSmoothedMa());
    }

    @Test
    public void cuaSoTruot_chiGiuNamMauGanNhat() {
        for (int i = 0; i < 5; i++) {
            stats.addSample(100);
        }
        for (int i = 0; i < 5; i++) {
            stats.addSample(2000);
        }
        assertEquals(2000, stats.getSmoothedMa());
    }

    @Test
    public void minMaxAverage_tinhTrenToanPhien() {
        stats.addSample(1000);
        stats.addSample(2000);
        stats.addSample(3000);

        assertEquals(1000, stats.getMinMa());
        assertEquals(3000, stats.getMaxMa());
        assertEquals(2000, stats.getAverageMa());
        assertEquals(3, stats.getTotalSamples());
    }

    @Test
    public void mauKhongHopLe_biBoQua() {
        stats.addSample(BatteryInfo.UNKNOWN_INT);
        assertEquals(0, stats.getTotalSamples());
    }

    @Test
    public void reset_xoaSachPhienCu() {
        stats.addSample(1000);
        stats.reset();
        assertEquals(0, stats.getTotalSamples());
        assertEquals(BatteryInfo.UNKNOWN_INT, stats.getSmoothedMa());
    }
}
