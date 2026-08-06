package com.dung.chargmagagement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.power.ChargeSpeed;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.power.PowerDrainFeature;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Kiểm thử phần xếp loại tốc độ sạc và tính toán tối ưu. */
public class PowerOptimizationTest {

    // ==================== Xếp loại tốc độ ====================

    @Test
    public void fromCurrent_phanLoaiDungTheoNguong() {
        assertEquals(ChargeSpeed.SLOW, ChargeSpeed.fromCurrent(300));
        assertEquals(ChargeSpeed.NORMAL, ChargeSpeed.fromCurrent(1000));
        assertEquals(ChargeSpeed.FAST, ChargeSpeed.fromCurrent(2000));
        assertEquals(ChargeSpeed.VERY_FAST, ChargeSpeed.fromCurrent(4500));
    }

    @Test
    public void fromCurrent_ngayTaiNguong_thuocNhomCaoHon() {
        assertEquals(ChargeSpeed.NORMAL, ChargeSpeed.fromCurrent(500));
        assertEquals(ChargeSpeed.FAST, ChargeSpeed.fromCurrent(1500));
        assertEquals(ChargeSpeed.VERY_FAST, ChargeSpeed.fromCurrent(3000));
    }

    @Test
    public void fromCurrent_dangXaHoacKhongDoDuoc_traVeKhongXacDinh() {
        assertEquals(ChargeSpeed.UNKNOWN, ChargeSpeed.fromCurrent(-500));
        assertEquals(ChargeSpeed.UNKNOWN, ChargeSpeed.fromCurrent(0));
        assertEquals(ChargeSpeed.UNKNOWN, ChargeSpeed.fromCurrent(BatteryInfo.UNKNOWN_INT));
    }

    // ==================== Tổng hợp tính năng ====================

    @Test
    public void totalSavingMa_chiCongCacMucDangBat() {
        List<DrainStatus> statuses = Arrays.asList(
                new DrainStatus(PowerDrainFeature.HIGH_BRIGHTNESS, true), // 150
                new DrainStatus(PowerDrainFeature.BLUETOOTH, true),       // 25
                new DrainStatus(PowerDrainFeature.LOCATION, false));      // không tính

        assertEquals(175, DrainStatus.totalSavingMa(statuses));
        assertEquals(2, DrainStatus.countActive(statuses));
    }

    @Test
    public void totalSavingMa_khongCoMucNaoBat_traVeKhong() {
        List<DrainStatus> statuses = Collections.singletonList(
                new DrainStatus(PowerDrainFeature.AUTO_SYNC, false));
        assertEquals(0, DrainStatus.totalSavingMa(statuses));
        assertEquals(0, DrainStatus.countActive(statuses));
    }

    @Test
    public void hangMucChiMangTinhKhuyenNghi_khongCoTrangCaiDat() {
        // Nguồn điện và nhiệt độ được xử lý trong app, không mở Cài đặt hệ thống
        assertFalse(PowerDrainFeature.POWER_SOURCE.hasSettingsPage());
        assertFalse(PowerDrainFeature.TEMPERATURE.hasSettingsPage());
        assertTrue(PowerDrainFeature.BLUETOOTH.hasSettingsPage());
    }

    @Test
    public void danhSachHangMuc_dungThuTuTrongBanThietKe() {
        PowerDrainFeature[] expected = {
                PowerDrainFeature.POWER_SOURCE,
                PowerDrainFeature.LOCATION,
                PowerDrainFeature.HIGH_BRIGHTNESS,
                PowerDrainFeature.BLUETOOTH,
                PowerDrainFeature.AUTO_SYNC,
                PowerDrainFeature.TEMPERATURE};
        assertArrayEquals(expected, PowerDrainFeature.values());
    }

    // ==================== Thời gian tiết kiệm ====================

    @Test
    public void estimateTimeSavedMs_tinhDungChenhLech() {
        // Cần nạp 3000 mAh, đang nạp 1000 mA -> 3 giờ.
        // Tắt bớt 500 mA -> nạp 1500 mA -> 2 giờ. Tiết kiệm 1 giờ.
        long saved = DrainStatus.estimateTimeSavedMs(3000f, 1000, 500);
        assertEquals(3_600_000L, saved);
    }

    @Test
    public void estimateTimeSavedMs_tietKiemCangLonThoiGianCangNgan() {
        long small = DrainStatus.estimateTimeSavedMs(3000f, 1000, 100);
        long large = DrainStatus.estimateTimeSavedMs(3000f, 1000, 500);
        assertTrue("tắt được nhiều hơn phải tiết kiệm nhiều hơn", large > small);
    }

    @Test
    public void estimateTimeSavedMs_thamSoKhongHopLe_traVeKhong() {
        assertEquals(0L, DrainStatus.estimateTimeSavedMs(0f, 1000, 500));
        assertEquals(0L, DrainStatus.estimateTimeSavedMs(3000f, 0, 500));
        assertEquals(0L, DrainStatus.estimateTimeSavedMs(3000f, 1000, 0));
    }

    @Test
    public void estimateTimeSavedMs_pinGanDay_thoiGianTietKiemNho() {
        // Chỉ còn nạp 60 mAh (pin 99%) thì tắt bớt gần như không giúp gì
        long saved = DrainStatus.estimateTimeSavedMs(60f, 1000, 200);
        assertTrue("dưới 1 phút", saved < 60_000L);
    }
}
