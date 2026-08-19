package com.dung.chargmagagement.model.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Kiểm tra cách hẹn giờ kiểm tra báo động sạc. */
public class AlarmScheduleCalculatorTest {

    private static final long MINUTE = 60_000L;

    /** Mẫu đầu tiên của phiên: chưa đo được tốc độ nên dùng mức mặc định. */
    @Test
    public void chuaBietTocDo_dungMacDinh() {
        assertEquals(AlarmScheduleCalculator.DEFAULT_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(50, 80, -1, 0L));
    }

    /** Pin đứng yên hoặc tụt: không suy ra được tốc độ nạp. */
    @Test
    public void pinKhongTang_dungMacDinh() {
        assertEquals(AlarmScheduleCalculator.DEFAULT_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(50, 80, 50, 3 * MINUTE));
        assertEquals(AlarmScheduleCalculator.DEFAULT_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(50, 80, 52, 3 * MINUTE));
    }

    /**
     * Sạc 2 % trong 2 phút, còn 4 % nữa là tới ngưỡng.
     * Ước tính 4 phút, hẹn sớm 10 % thành khoảng 3 phút 36 giây.
     */
    @Test
    public void henTheoTocDoDoDuoc() {
        final long delay = AlarmScheduleCalculator.nextDelayMs(76, 80, 74, 2 * MINUTE);

        assertEquals(216_000L, delay);
        assertTrue(delay < 4 * MINUTE); // luôn sớm hơn ước tính
    }

    /** Sạc rất chậm cũng không được hẹn quá thưa, để sai số không vượt một chu kỳ. */
    @Test
    public void sacCham_chanTranNamPhut() {
        assertEquals(AlarmScheduleCalculator.MAX_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(20, 80, 19, 10 * MINUTE));
    }

    /** Sát ngưỡng và sạc nhanh cũng không được hẹn dày hơn một phút. */
    @Test
    public void sacNhanh_chanSanMotPhut() {
        assertEquals(AlarmScheduleCalculator.MIN_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(79, 80, 74, MINUTE));
    }

    /** Đã vượt ngưỡng: kiểm tra lại sớm nhất có thể. */
    @Test
    public void daVuotNguong_kiemTraLaiNgay() {
        assertEquals(AlarmScheduleCalculator.MIN_DELAY_MS,
                AlarmScheduleCalculator.nextDelayMs(85, 80, 80, MINUTE));
    }
}
