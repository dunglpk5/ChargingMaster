package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.entity.ScreenSessionEntity;
import com.dung.chargmagagement.model.stats.UsageCalculator;
import com.dung.chargmagagement.model.stats.UsageRate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Kiểm thử phần tính toán thống kê của tab "Sử dụng pin". */
public class UsageCalculatorTest {

    private static final long HOUR_MS = 3_600_000L;

    private static ScreenSessionEntity screen(int startPercent, int endPercent,
                                              long durationMs, boolean screenOn) {
        ScreenSessionEntity session = new ScreenSessionEntity();
        session.startTime = 1_000_000L;
        session.endTime = 1_000_000L + durationMs;
        session.startPercent = startPercent;
        session.endPercent = endPercent;
        session.screenOn = screenOn;
        return session;
    }

    private static ChargingSessionEntity charge(int startPercent, int endPercent, float chargedMah) {
        ChargingSessionEntity session = new ChargingSessionEntity();
        session.startTime = 1_000_000L;
        session.endTime = 1_000_000L + HOUR_MS;
        session.startPercent = startPercent;
        session.endPercent = endPercent;
        session.chargedMah = chargedMah;
        return session;
    }

    // ==================== Tốc độ tiêu hao ====================

    @Test
    public void calculateRate_khongCoDuLieu_traVeRong() {
        assertFalse(UsageCalculator.calculateRate(null).hasData());
        assertFalse(UsageCalculator.calculateRate(new ArrayList<>()).hasData());
    }

    @Test
    public void calculateRate_motKhoang_tinhDungPhanTramMoiGio() {
        // Tụt 20% trong 2 giờ -> 10 %/h
        UsageRate rate = UsageCalculator.calculateRate(
                Collections.singletonList(screen(80, 60, 2 * HOUR_MS, true)));
        assertEquals(10f, rate.getPercentPerHour(), 0.01f);
        assertEquals(20, rate.getTotalPercentDrop());
        assertEquals(2f, rate.getTotalHours(), 0.01f);
    }

    @Test
    public void calculateRate_nhieuKhoang_congDonTruocKhiChia() {
        // Khoảng ngắn tụt nhanh (30 %/h) không được kéo lệch kết quả như nhau
        // với khoảng dài tụt chậm: 3% trong 6 phút + 10% trong 10 giờ
        List<ScreenSessionEntity> sessions = Arrays.asList(
                screen(100, 97, HOUR_MS / 10, true),
                screen(97, 87, 10 * HOUR_MS, true));
        UsageRate rate = UsageCalculator.calculateRate(sessions);

        // Tổng 13% trong 10,1 giờ ~ 1,29 %/h (nếu lấy trung bình 2 tỉ lệ sẽ ra ~15,5)
        assertEquals(13, rate.getTotalPercentDrop());
        assertEquals(1.287f, rate.getPercentPerHour(), 0.01f);
    }

    @Test
    public void calculateRate_khoangKhongTieuHao_biBoQua() {
        UsageRate rate = UsageCalculator.calculateRate(Arrays.asList(
                screen(50, 50, HOUR_MS, false),   // không đổi
                screen(50, 45, HOUR_MS, false))); // tụt 5%
        assertEquals(5, rate.getTotalPercentDrop());
        assertEquals(1f, rate.getTotalHours(), 0.01f);
    }

    @Test
    public void combine_gopHaiNhom() {
        UsageRate on = new UsageRate(20, 2f);   // 10 %/h
        UsageRate off = new UsageRate(10, 8f);  // 1,25 %/h
        UsageRate combined = UsageCalculator.combine(on, off);

        assertEquals(30, combined.getTotalPercentDrop());
        assertEquals(10f, combined.getTotalHours(), 0.01f);
        assertEquals(3f, combined.getPercentPerHour(), 0.01f);
    }

    @Test
    public void estimatedFullBatteryHours_suyRaTuTiLe() {
        // 5 %/h -> pin đầy dùng được 20 giờ
        assertEquals(20f, new UsageRate(10, 2f).getEstimatedFullBatteryHours(), 0.01f);
        assertEquals(0f, UsageRate.EMPTY.getEstimatedFullBatteryHours(), 0.01f);
    }

    // ==================== Ước tính dung lượng ====================

    @Test
    public void estimateCapacity_mayPhienChuan_raGanDungLuongThat() {
        // Nạp 3000 mAh cho 50% -> pin 6000 mAh
        List<ChargingSessionEntity> sessions = Arrays.asList(
                charge(20, 70, 3000f),
                charge(30, 80, 2940f),
                charge(10, 60, 3060f));
        assertEquals(6000, UsageCalculator.estimateCapacity(sessions));
    }

    @Test
    public void estimateCapacity_trungViChongDuocPhienNhieu() {
        List<ChargingSessionEntity> sessions = Arrays.asList(
                charge(20, 70, 3000f),   // 6000
                charge(20, 70, 3000f),   // 6000
                charge(20, 70, 9000f));  // 18000 - nhiễu nặng
        // Trung bình sẽ ra 10000; trung vị vẫn bám 6000
        assertEquals(6000, UsageCalculator.estimateCapacity(sessions));
    }

    @Test
    public void estimateCapacity_phienQuaNgan_biLoai() {
        // Chỉ nạp 10% -> dưới ngưỡng 20%, không đủ tin cậy
        List<ChargingSessionEntity> sessions =
                Collections.singletonList(charge(60, 70, 600f));
        assertEquals(BatteryInfo.UNKNOWN_INT, UsageCalculator.estimateCapacity(sessions));
    }

    @Test
    public void estimateCapacity_khongDoDuocDong_traVeKhongXacDinh() {
        List<ChargingSessionEntity> sessions =
                Collections.singletonList(charge(20, 70, 0f));
        assertEquals(BatteryInfo.UNKNOWN_INT, UsageCalculator.estimateCapacity(sessions));
    }

    // ==================== Điện tích nạp & thời gian sạc đầy ====================

    @Test
    public void calculateChargedMah_dongNhanThoiGian() {
        // 2000 mA trong 1,5 giờ = 3000 mAh
        assertEquals(3000f,
                UsageCalculator.calculateChargedMah(2000, (long) (1.5 * HOUR_MS)), 0.5f);
    }

    @Test
    public void calculateChargedMah_dongKhongDoDuoc_traVeKhong() {
        assertEquals(0f,
                UsageCalculator.calculateChargedMah(BatteryInfo.UNKNOWN_INT, HOUR_MS), 0.01f);
        assertEquals(0f, UsageCalculator.calculateChargedMah(-500, HOUR_MS), 0.01f);
    }

    @Test
    public void estimateTimeToFull_tinhDungThoiGianConLai() {
        // Pin 6000 mAh ở 50%, nạp 3000 mA -> còn 3000 mAh -> 1 giờ
        assertEquals(HOUR_MS, UsageCalculator.estimateTimeToFull(50, 3000, 6000));
    }

    @Test
    public void estimateTimeToFull_pinDayHoacKhongDoDuoc_traVeKhong() {
        assertEquals(0L, UsageCalculator.estimateTimeToFull(100, 3000, 6000));
        assertEquals(0L, UsageCalculator.estimateTimeToFull(50, BatteryInfo.UNKNOWN_INT, 6000));
        assertEquals(0L, UsageCalculator.estimateTimeToFull(50, 3000, BatteryInfo.UNKNOWN_INT));
    }

    // ==================== Độ chai pin ====================

    @Test
    public void healthPercent_chanTranO100() {
        com.dung.chargmagagement.model.stats.BatteryUsageStats stats =
                com.dung.chargmagagement.model.stats.BatteryUsageStats.builder()
                        .estimatedCapacityMah(6300)
                        .designCapacityMah(6000)
                        .build();
        assertTrue(stats.hasCapacityEstimate());
        assertEquals(100, stats.getHealthPercent());
    }

    @Test
    public void healthPercent_pinChai() {
        com.dung.chargmagagement.model.stats.BatteryUsageStats stats =
                com.dung.chargmagagement.model.stats.BatteryUsageStats.builder()
                        .estimatedCapacityMah(4800)
                        .designCapacityMah(6000)
                        .build();
        assertEquals(80, stats.getHealthPercent());
    }
}
