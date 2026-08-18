package com.dung.chargmagagement.model.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Kiểm tra bộ đếm số liệu của thông báo thường trú. */
public class SessionMeterTest {

    private static final long START = 1_700_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private SessionMeter meter;

    @Before
    public void setUp() {
        meter = new SessionMeter();
        meter.reset(START, 80);
    }

    @Test
    public void chuaCoMau_khongCoDuLieu() {
        assertFalse(new SessionMeter().hasData());
        assertFalse(meter.hasData());
    }

    /** Xả 500 mA trong 1 giờ = 500 mAh. */
    @Test
    public void mahTinhTheoDongVaThoiGian() {
        meter.addSample(START + HOUR, 70, -500, false);

        assertTrue(meter.hasData());
        assertEquals(500f, meter.getTotalMah(), 0.5f);
        assertEquals(-500, meter.getAverageMa());
    }

    /** Dòng trung bình phải có trọng số theo thời gian, không phải trung bình cộng. */
    @Test
    public void dongTrungBinh_coTrongSoTheoThoiGian() {
        // 1000 mA trong 1 phút rồi 100 mA trong 59 phút
        meter.addSample(START + MINUTE, 79, -1000, true);
        meter.addSample(START + HOUR, 78, -100, true);

        // Trung bình cộng sẽ ra -550; đúng phải gần -115
        assertEquals(-115, meter.getAverageMa(), 2);
    }

    @Test
    public void tocDoPhanTram_tinhTheoGio() {
        meter.addSample(START + 2 * HOUR, 60, -500, false);

        assertEquals(-20, meter.getPercentDelta());
        assertEquals(-10f, meter.getAveragePercentPerHour(), 0.1f);
    }

    /** Chặng chưa đủ một phút thì tỉ lệ %/h chưa có nghĩa. */
    @Test
    public void changQuaNgan_khongTinhTocDo() {
        meter.addSample(START + 5_000L, 79, -500, false);

        assertTrue(Float.isNaN(meter.getAveragePercentPerHour()));
    }

    @Test
    public void tachRiengManHinhBatVaTat() {
        meter.addSample(START + HOUR, 75, -600, true);
        // Mốc tắt màn hình phải được báo riêng: trạng thái kèm theo mẫu pin chỉ mô
        // tả thời điểm lấy mẫu, không nói gì về cả khoảng vừa trôi qua
        meter.setScreenOn(START + HOUR, false);
        meter.addSample(START + 2 * HOUR, 73, -200, false);

        assertEquals(HOUR, meter.getScreenOnMs());
        assertEquals(HOUR, meter.getScreenOffMs());
        assertEquals(600f, meter.getScreenOnMah(), 1f);
        assertEquals(200f, meter.getScreenOffMah(), 1f);
        assertEquals(75f, meter.getScreenOnShare(), 1f);
        assertEquals(25f, meter.getScreenOffShare(), 1f);
    }

    /** Đổi giờ hệ thống làm mốc lùi về quá khứ; không được chia cho số âm. */
    @Test
    public void mauLuiVeQuaKhu_biBoQua() {
        meter.addSample(START + HOUR, 70, -500, false);
        meter.addSample(START, 69, -500, false);

        assertEquals(500f, meter.getTotalMah(), 1f);
        assertEquals(HOUR, meter.getElapsedMs());
    }

    @Test
    public void reset_xoaSachSoLieuCu() {
        meter.addSample(START + HOUR, 70, -500, false);
        meter.reset(START + 2 * HOUR, 70);

        assertFalse(meter.hasData());
        assertEquals(0f, meter.getTotalMah(), 0.01f);
        assertEquals(0, meter.getPercentDelta());
    }

    @Test
    public void quyMahSangPhanTram() {
        assertEquals(10f, meter.toPercent(400f, 4000), 0.01f);
        assertEquals(0f, meter.toPercent(400f, 0), 0.01f);
    }
    /** Mẫu không đọc được dòng điện chỉ tính vào thời gian, không kéo tụt trung bình. */
    @Test
    public void dongKhongDocDuoc_khongTinhVaoTrungBinh() {
        meter.addSample(START + HOUR, 75, -500, true);
        meter.addSample(START + 2 * HOUR, 70, SessionMeter.UNKNOWN_CURRENT, true);

        assertEquals(-500, meter.getAverageMa());
        assertEquals(500f, meter.getTotalMah(), 0.5f);
        // Thời gian màn hình vẫn cộng đủ hai tiếng
        assertEquals(2 * HOUR, meter.getScreenOnMs());
    }

    /**
     * Chặng ngắn: %/h suy từ dòng điện, không từ bước nhảy 1 % của hệ thống.
     * 4 phút tụt 1 % quy ra -15 %/h, trong khi -1200 mA trên pin 5000 mAh là -24 %/h.
     */
    @Test
    public void changNgan_tocDoPhanTramSuyTuDongDien() {
        meter.addSample(START + 4 * MINUTE, 79, -1200, true);

        assertEquals(-24f, meter.getAveragePercentPerHour(5000), 0.1f);
    }

    /** Chặng đủ dài và đủ chênh lệch thì tin số % thật của hệ thống. */
    @Test
    public void changDai_tocDoPhanTramTheoSoDoThat() {
        meter.addSample(START + HOUR, 70, -1200, true);

        assertEquals(-10f, meter.getAveragePercentPerHour(5000), 0.1f);
    }

    /** Chưa biết dung lượng pin thì đành quay lại cách tính theo %. */
    @Test
    public void chuaBietDungLuong_quayVeTinhTheoPhanTram() {
        meter.addSample(START + 4 * MINUTE, 79, -1200, true);

        assertEquals(meter.getAveragePercentPerHour(),
                meter.getAveragePercentPerHour(0), 0.1f);
    }
    /**
     * Mốc tắt/bật màn hình cắt đúng khoảng giữa hai mẫu pin.
     *
     * <p>Máy ngủ 30 phút rồi người dùng bật màn hình lên: nếu chỉ nhìn trạng thái
     * tại thời điểm nhận mẫu thì cả 30 phút đó bị ghi vào "màn hình bật".
     */
    @Test
    public void motMauSauKhiNguDai_khongDonHetVaoManHinhBat() {
        meter.setScreenOn(START + 5 * MINUTE, false);
        meter.setScreenOn(START + 35 * MINUTE, true);
        meter.addSample(START + 36 * MINUTE, 78, -500, true);

        assertEquals(6 * MINUTE, meter.getScreenOnMs());
        assertEquals(30 * MINUTE, meter.getScreenOffMs());
    }

    /** Điện tích của khoảng bắc qua mốc chia về hai bên theo tỉ lệ thời gian. */
    @Test
    public void dienTichChiaTheoTiLeThoiGian() {
        meter.setScreenOn(START + 45 * MINUTE, false);
        meter.addSample(START + HOUR, 75, -1000, false);

        assertEquals(1000f, meter.getTotalMah(), 1f);
        assertEquals(750f, meter.getScreenOnMah(), 1f);
        assertEquals(250f, meter.getScreenOffMah(), 1f);
    }

    /** Đồng hồ màn hình vẫn chạy giữa hai mẫu pin, không đứng yên chờ mẫu mới. */
    @Test
    public void thoiGianManHinh_conhemPhanDuoiChuaChot() {
        meter.addSample(START + MINUTE, 79, -500, true);

        assertEquals(MINUTE, meter.getScreenOnMs());
        assertEquals(3 * MINUTE, meter.getScreenOnMs(START + 3 * MINUTE));
    }
}
