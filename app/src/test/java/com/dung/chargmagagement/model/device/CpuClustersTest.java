package com.dung.chargmagagement.model.device;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Kiểm tra chuỗi mô tả kiến trúc CPU. */
public class CpuClustersTest {

    private static CpuCore core(int index, String name, long maxKhz) {
        CpuCore core = new CpuCore(index);
        core.name = name;
        core.maxKhz = maxKhz;
        return core;
    }

    /** Trường hợp thật của MT6789 trong bản thiết kế. */
    @Test
    public void describe_groupsAdjacentCoresOfSameCluster() {
        List<CpuCore> cores = new ArrayList<>();
        for (int i = 0; i < 6; i++) cores.add(core(i, "Cortex-A55", 2_000_000L));
        for (int i = 6; i < 8; i++) cores.add(core(i, "Cortex-A76", 2_200_000L));

        assertEquals("6x Cortex-A55 2 GHz 2x Cortex-A76 2.2 GHz",
                CpuClusters.describe(cores, Locale.US));
    }

    /** Hai cụm cùng tên nhưng khác xung phải tách riêng, không gộp làm một. */
    @Test
    public void describe_splitsSameNameDifferentClock() {
        List<CpuCore> cores = new ArrayList<>();
        cores.add(core(0, "Cortex-A53", 1_400_000L));
        cores.add(core(1, "Cortex-A53", 1_400_000L));
        cores.add(core(2, "Cortex-A53", 1_800_000L));

        assertEquals("2x Cortex-A53 1.4 GHz 1x Cortex-A53 1.8 GHz",
                CpuClusters.describe(cores, Locale.US));
    }

    /** Không tra được tên nhân thì vẫn nêu số lượng và xung. */
    @Test
    public void describe_worksWithoutCoreName() {
        List<CpuCore> cores = new ArrayList<>();
        cores.add(core(0, null, 2_000_000L));
        cores.add(core(1, null, 2_000_000L));

        assertEquals("2x 2 GHz", CpuClusters.describe(cores, Locale.US));
    }

    /** Thiếu cả tên lẫn xung thì cụm đó không nói lên gì, phải bỏ hẳn. */
    @Test
    public void describe_skipsEmptyCluster() {
        List<CpuCore> cores = new ArrayList<>();
        cores.add(core(0, null, 0L));
        cores.add(core(1, "Cortex-A55", 1_800_000L));

        assertEquals("1x Cortex-A55 1.8 GHz", CpuClusters.describe(cores, Locale.US));
    }

    @Test
    public void describe_emptyListGivesEmptyString() {
        assertEquals("", CpuClusters.describe(new ArrayList<>(), Locale.US));
    }
}
