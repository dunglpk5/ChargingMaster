package com.dung.chargmagagement;

import static org.junit.Assert.assertEquals;

import com.dung.chargmagagement.model.power.ChargeStage;

import org.junit.Test;

/**
 * Kiểm thử việc xếp giai đoạn sạc theo mức pin.
 */
public class ChargeStageTest {

    @Test
    public void duoiTamMuoi_laGiaiDoanNhanh() {
        assertEquals(ChargeStage.FAST, ChargeStage.fromPercent(0));
        assertEquals(ChargeStage.FAST, ChargeStage.fromPercent(42));
        assertEquals(ChargeStage.FAST, ChargeStage.fromPercent(79));
    }

    @Test
    public void tuTamMuoiToiDuoiMotTram_laGiaiDoanChuKy() {
        assertEquals(ChargeStage.CYCLE, ChargeStage.fromPercent(80));
        assertEquals(ChargeStage.CYCLE, ChargeStage.fromPercent(99));
    }

    @Test
    public void daDay_laGiaiDoanNhoGiot() {
        assertEquals(ChargeStage.TRICKLE, ChargeStage.fromPercent(100));
    }
}
