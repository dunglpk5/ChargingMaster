package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.CurrentCalibration;

import org.junit.Test;

/**
 * Kiểm thử logic hiệu chỉnh dòng điện – phần dễ sai nhất vì mỗi hãng báo một kiểu.
 */
public class CurrentCalibrationTest {

    // ==================== Nhận diện đơn vị ====================

    @Test
    public void detectDivider_giaTriLon_laMicroAmpe() {
        // Pixel/Samsung: 1.5A báo về 1_500_000 µA
        assertEquals(CurrentCalibration.DIVIDER_MICRO,
                CurrentCalibration.detectDivider(1_500_000));
        assertEquals(CurrentCalibration.DIVIDER_MICRO,
                CurrentCalibration.detectDivider(-1_500_000));
    }

    @Test
    public void detectDivider_giaTriVua_laMiliAmpe() {
        // Xiaomi: 1.5A báo về thẳng 1500 mA
        assertEquals(CurrentCalibration.DIVIDER_MILLI,
                CurrentCalibration.detectDivider(1_500));
    }

    @Test
    public void detectDivider_giaTriQuanhKhong_khongKetLuan() {
        assertEquals(CurrentCalibration.DIVIDER_UNKNOWN,
                CurrentCalibration.detectDivider(5));
        assertEquals(CurrentCalibration.DIVIDER_UNKNOWN,
                CurrentCalibration.detectDivider(0));
    }

    // ==================== Nhận diện dấu ====================

    @Test
    public void detectSign_dangSacBaoDuong_laChuanAosp() {
        assertEquals(CurrentCalibration.SIGN_NORMAL,
                CurrentCalibration.detectSign(1_500_000, true));
    }

    @Test
    public void detectSign_dangSacBaoAm_laDaoDau() {
        // Nhiều máy Oppo/Realme báo âm khi đang nạp
        assertEquals(CurrentCalibration.SIGN_INVERTED,
                CurrentCalibration.detectSign(-1_500_000, true));
    }

    @Test
    public void detectSign_dangXaBaoAm_laChuanAosp() {
        assertEquals(CurrentCalibration.SIGN_NORMAL,
                CurrentCalibration.detectSign(-350_000, false));
    }

    @Test
    public void detectSign_dangXaBaoDuong_laDaoDau() {
        assertEquals(CurrentCalibration.SIGN_INVERTED,
                CurrentCalibration.detectSign(350_000, false));
    }

    @Test
    public void detectSign_giaTriQuanhKhong_khongKetLuan() {
        assertEquals(CurrentCalibration.SIGN_UNKNOWN,
                CurrentCalibration.detectSign(3, true));
    }

    // ==================== Quy đổi ====================

    @Test
    public void normalize_microAmpeChuan_raMiliAmpeDuong() {
        assertEquals(1500, CurrentCalibration.normalize(
                1_500_000, CurrentCalibration.DIVIDER_MICRO, CurrentCalibration.SIGN_NORMAL));
    }

    @Test
    public void normalize_mayDaoDau_traVeDuongKhiDangSac() {
        assertEquals(1500, CurrentCalibration.normalize(
                -1_500_000, CurrentCalibration.DIVIDER_MICRO, CurrentCalibration.SIGN_INVERTED));
    }

    @Test
    public void normalize_dangXa_traVeAm() {
        assertEquals(-350, CurrentCalibration.normalize(
                -350_000, CurrentCalibration.DIVIDER_MICRO, CurrentCalibration.SIGN_NORMAL));
    }

    @Test
    public void normalize_chuaHieuChinh_traVeKhongXacDinh() {
        assertEquals(BatteryInfo.UNKNOWN_INT, CurrentCalibration.normalize(
                1_500_000, CurrentCalibration.DIVIDER_UNKNOWN, CurrentCalibration.SIGN_NORMAL));
        assertEquals(BatteryInfo.UNKNOWN_INT, CurrentCalibration.normalize(
                1_500_000, CurrentCalibration.DIVIDER_MICRO, CurrentCalibration.SIGN_UNKNOWN));
    }

    @Test
    public void normalize_giaTriVoLy_biLoaiBo() {
        // 99A là không thể với điện thoại -> coi như đọc nhầm node
        assertEquals(BatteryInfo.UNKNOWN_INT, CurrentCalibration.normalize(
                99_000, CurrentCalibration.DIVIDER_MILLI, CurrentCalibration.SIGN_NORMAL));
    }
}
