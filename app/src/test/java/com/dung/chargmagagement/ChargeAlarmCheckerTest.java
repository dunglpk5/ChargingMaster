package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.alarm.AlarmSettings;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker.AlarmType;

import org.junit.Before;
import org.junit.Test;

/**
 * Kiểm thử logic báo động sạc.
 *
 * <p>Trọng tâm là cơ chế chống lặp: pin đứng yên ở ngưỡng suốt hàng giờ mà mỗi
 * lần lấy mẫu lại kêu một tiếng thì app sẽ bị gỡ ngay.
 */
public class ChargeAlarmCheckerTest {

    private ChargeAlarmChecker checker;

    private static AlarmSettings allEnabled() {
        return new AlarmSettings(true, 80, true, true, 43);
    }

    private static AlarmSettings allDisabled() {
        return new AlarmSettings(false, 80, false, false, 43);
    }

    @Before
    public void setUp() {
        checker = new ChargeAlarmChecker();
    }

    // ==================== Ngưỡng phần trăm ====================

    @Test
    public void check_datNguong_phatCanhBao() {
        assertEquals(AlarmType.THRESHOLD, checker.check(80, true, 30f, allEnabled()));
    }

    @Test
    public void check_chuaDatNguong_khongBao() {
        assertEquals(AlarmType.NONE, checker.check(79, true, 30f, allEnabled()));
    }

    @Test
    public void check_chiBaoMotLanTrongMotPhien() {
        assertEquals(AlarmType.THRESHOLD, checker.check(80, true, 30f, allEnabled()));
        // Các lần lấy mẫu tiếp theo phải im lặng
        assertEquals(AlarmType.NONE, checker.check(81, true, 30f, allEnabled()));
        assertEquals(AlarmType.NONE, checker.check(85, true, 30f, allEnabled()));
    }

    @Test
    public void check_phienMoi_baoLai() {
        checker.check(80, true, 30f, allEnabled());
        checker.resetSession();
        assertEquals(AlarmType.THRESHOLD, checker.check(80, true, 30f, allEnabled()));
    }

    @Test
    public void check_khongSac_khongBaoNguong() {
        assertEquals(AlarmType.NONE, checker.check(85, false, 30f, allEnabled()));
    }

    // ==================== Sạc đầy ====================

    @Test
    public void check_pinDay_uuTienBaoDay() {
        // Ở 100% cả hai điều kiện đều thoả; cảnh báo sạc đầy phải được ưu tiên
        assertEquals(AlarmType.FULL, checker.check(100, true, 30f, allEnabled()));
    }

    @Test
    public void check_baoDayRoiThiBaoNguong() {
        assertEquals(AlarmType.FULL, checker.check(100, true, 30f, allEnabled()));
        // Lần sau ngưỡng 80% vẫn còn hiệu lực và được báo một lần
        assertEquals(AlarmType.THRESHOLD, checker.check(100, true, 30f, allEnabled()));
        assertEquals(AlarmType.NONE, checker.check(100, true, 30f, allEnabled()));
    }

    // ==================== Quá nhiệt ====================

    @Test
    public void check_quaNhiet_baoCaKhiKhongSac() {
        // Quá nhiệt là cảnh báo an toàn nên không phụ thuộc trạng thái sạc
        assertEquals(AlarmType.OVERHEAT, checker.check(50, false, 44f, allEnabled()));
    }

    @Test
    public void check_quaNhiet_chiBaoMotLan() {
        assertEquals(AlarmType.OVERHEAT, checker.check(50, true, 44f, allEnabled()));
        assertEquals(AlarmType.NONE, checker.check(50, true, 45f, allEnabled()));
    }

    @Test
    public void check_quaNhiet_daoQuanhNguong_khongBaoLienTuc() {
        assertEquals(AlarmType.OVERHEAT, checker.check(50, true, 43f, allEnabled()));
        // Tụt nhẹ xuống 42 (vẫn trong vùng đệm 2 độ) rồi lên lại: không được báo nữa
        assertEquals(AlarmType.NONE, checker.check(50, true, 42f, allEnabled()));
        assertEquals(AlarmType.NONE, checker.check(50, true, 43f, allEnabled()));
    }

    @Test
    public void check_quaNhiet_daNguoiHan_choPhepBaoLai() {
        assertEquals(AlarmType.OVERHEAT, checker.check(50, true, 44f, allEnabled()));
        // Nguội hẳn xuống dưới 41 (43 - 2) thì trạng thái được đặt lại
        assertEquals(AlarmType.NONE, checker.check(50, true, 38f, allEnabled()));
        assertEquals(AlarmType.OVERHEAT, checker.check(50, true, 44f, allEnabled()));
    }

    // ==================== Tắt hết ====================

    @Test
    public void check_tatHet_khongBaoGiGi() {
        assertEquals(AlarmType.NONE, checker.check(100, true, 50f, allDisabled()));
    }

    @Test
    public void hasAnyEnabled_phanBietDungTrangThai() {
        assertEquals(true, allEnabled().hasAnyEnabled());
        assertEquals(false, allDisabled().hasAnyEnabled());
    }

    // ==================== Giới hạn giá trị ====================

    @Test
    public void withThreshold_chanGiaTriNgoaiPhamVi() {
        AlarmSettings settings = allDisabled().withThreshold(true, 200);
        assertEquals(AlarmSettings.MAX_THRESHOLD_PERCENT, settings.getThresholdPercent());

        settings = allDisabled().withThreshold(true, 10);
        assertEquals(AlarmSettings.MIN_THRESHOLD_PERCENT, settings.getThresholdPercent());
    }

    @Test
    public void withOverheat_chanGiaTriNgoaiPhamVi() {
        AlarmSettings settings = allDisabled().withOverheat(true, 90);
        assertEquals(AlarmSettings.MAX_OVERHEAT_TEMP, settings.getOverheatTemp());

        settings = allDisabled().withOverheat(true, 0);
        assertEquals(AlarmSettings.MIN_OVERHEAT_TEMP, settings.getOverheatTemp());
    }
}
