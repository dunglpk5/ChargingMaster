package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.PlugType;
import com.dung.chargmagagement.model.power.ChargeAdvisor;
import com.dung.chargmagagement.model.power.ChargeWarning;

import org.junit.Test;

import java.util.List;

/**
 * Kiểm thử phần nhắc nhở bất thường của màn X-Sạc.
 *
 * <p>Đây là loại lỗi khó phát hiện bằng tay: một điều kiện viết nhầm chỉ biểu hiện
 * thành "app chẳng bao giờ cảnh báo gì", mà im lặng thì trông y hệt như mọi thứ ổn.
 */
public class ChargeAdvisorTest {

    private static BatteryInfo info(PlugType plug, int percent, float celsius) {
        return BatteryInfo.builder()
                .plugType(plug)
                .percent(percent)
                .temperatureCelsius(celsius)
                .build();
    }

    @Test
    public void sacBinhThuong_khongCanhBaoGi() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.AC, 50, 30f), 2_000);
        assertTrue(result.isEmpty());
    }

    @Test
    public void pinNong_canhBaoCaKhiKhongSac() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.NONE, 50, 42f), -300);
        assertEquals(1, result.size());
        assertEquals(ChargeWarning.OVERHEAT, result.get(0));
    }

    @Test
    public void camSacMaVanTutPin_baoNguonKhongTheoKip() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.AC, 50, 30f), -200);
        assertTrue(result.contains(ChargeWarning.DRAINING));
        // Đang tụt pin thì không báo kèm "dòng nạp thấp", hai điều đó loại trừ nhau
        assertFalse(result.contains(ChargeWarning.LOW_CURRENT));
    }

    @Test
    public void dongNapThap_baoKiemTraCapVaCuSac() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.AC, 50, 30f), 250);
        assertTrue(result.contains(ChargeWarning.LOW_CURRENT));
    }

    @Test
    public void sacQuaCongUsb_baoRieng() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.USB, 50, 30f), 450);
        assertTrue(result.contains(ChargeWarning.USB_SOURCE));
    }

    @Test
    public void pinGanDay_nhacRutSac() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.AC, 92, 30f), 800);
        assertTrue(result.contains(ChargeWarning.NEARLY_FULL));
    }

    @Test
    public void khongDoDuocDong_khongBaoBuaVeDongDien() {
        List<ChargeWarning> result = ChargeAdvisor.analyze(
                info(PlugType.AC, 50, 30f), BatteryInfo.UNKNOWN_INT);
        assertFalse(result.contains(ChargeWarning.LOW_CURRENT));
        assertFalse(result.contains(ChargeWarning.DRAINING));
    }

    @Test
    public void nhieuBatThuongCungLuc_xepTheoMucDoUuTien() {
        List<ChargeWarning> result =
                ChargeAdvisor.analyze(info(PlugType.USB, 95, 43f), 200);
        // Nhiệt độ là vấn đề an toàn nên phải đứng trước mọi cảnh báo về tốc độ
        assertEquals(ChargeWarning.OVERHEAT, result.get(0));
        assertEquals(4, result.size());
    }
}
