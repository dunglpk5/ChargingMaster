package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.adapter.CpuCoreAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityCpuUsageBinding;
import com.dung.chargmagagement.model.device.CpuInfoReader;
import com.dung.chargmagagement.model.device.CpuLoadEstimator;
import com.dung.chargmagagement.model.device.CpuUsageReader;
import com.dung.chargmagagement.model.device.CpuUsageSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;

/**
 * Màn Sử dụng CPU: dải tần số, số nhân và biểu đồ tải của từng nhân.
 *
 * <p>Mức tải tính bằng chênh lệch giữa hai lần đọc {@code /proc/stat}, nên lần đo
 * đầu tiên luôn là 0 – phải có mốc trước mới tính được tỉ lệ.
 *
 * <p>Việc lấy mẫu chỉ chạy khi màn hình đang hiển thị và dừng hẳn ở
 * {@code onPause()}: đọc file mỗi giây trong lúc người dùng đã rời màn là kiểu
 * hao pin vô nghĩa nhất.
 */
public class CpuUsageActivity extends BaseActivity<ActivityCpuUsageBinding> {

    /** Chu kỳ cập nhật; ngắn hơn nữa thì đồ thị nhảy quá nhanh, khó theo dõi. */
    private static final long SAMPLE_INTERVAL_MS = 1_500L;

    private static final int GRID_SPAN_COUNT = 2;

    private CpuCoreAdapter adapter;
    private ScheduledFuture<?> samplingTask;

    /** Lần đọc trước, dùng làm mốc để tính chênh lệch. */
    private List<CpuUsageSnapshot> previousSnapshots;

    /** Dải xung nhịp [min, max] của từng nhân, cho cách ước tính dự phòng. */
    @Nullable
    private long[][] coreFreqRange;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, CpuUsageActivity.class));
    }

    @NonNull
    @Override
    protected ActivityCpuUsageBinding onCreateBinding() {
        return ActivityCpuUsageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_cpu_usage);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        final int coreCount = CpuInfoReader.getCoreCount();
        binding.tvCoreCount.setText(String.valueOf(coreCount));

        adapter = new CpuCoreAdapter(coreCount);
        binding.rvCores.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        binding.rvCores.setAdapter(adapter);

        loadFrequencyRange();
        loadCoreFreqRanges(coreCount);
    }

    /** Dải xung của từng nhân chỉ cần đọc một lần, giá trị không đổi khi máy chạy. */
    private void loadCoreFreqRanges(int coreCount) {
        executors.execute(() -> {
            long[][] ranges = new long[coreCount][2];
            for (int i = 0; i < coreCount; i++) {
                ranges[i][0] = CpuInfoReader.getCoreMinFrequencyKhz(i);
                ranges[i][1] = CpuInfoReader.getCoreMaxFrequencyKhz(i);
            }
            return ranges;
        }, ranges -> coreFreqRange = ranges);
    }

    /** Dải tần số đọc từ sysfs nên phải chạy nền. */
    private void loadFrequencyRange() {
        executors.execute(
                () -> new long[]{
                        CpuInfoReader.getMinFrequencyKhz(),
                        CpuInfoReader.getMaxFrequencyKhz()},
                range -> {
                    if (binding == null || range == null) return;
                    binding.tvFrequency.setText(getString(R.string.cpu_freq_range,
                            formatMhz(range[0]), formatMhz(range[1])));
                });
    }

    /** Hiển thị theo MHz như bản thiết kế ("500Mhz ~ 2200Mhz"). */
    private String formatMhz(long khz) {
        if (khz <= 0) return getString(R.string.value_placeholder);
        return String.format(Locale.US, "%dMhz", khz / 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        previousSnapshots = null;
        samplingTask = executors.schedulePeriodic(this::sample, 0L, SAMPLE_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }
    }

    /**
     * Một lần lấy mẫu, chạy trên thread sampler.
     *
     * <p>Ưu tiên {@code /proc/stat} vì đó là số đo thật. ROM nào chặn file đó thì
     * chuyển sang ước tính theo xung nhịp – vẫn hơn là bỏ trống cả màn hình.
     */
    private void sample() {
        final List<CpuUsageSnapshot> current = CpuUsageReader.readSnapshots();
        final int[] estimated = current.isEmpty() ? readEstimatedLoad() : null;

        executors.runOnMain(() -> {
            if (binding == null) return;

            if (estimated != null) {
                bindEstimated(estimated);
                return;
            }

            binding.tvUnavailable.setVisibility(View.GONE);
            pushSamples(current);
            previousSnapshots = current;
        });
    }

    /** Ước tính tải từng nhân theo xung nhịp; null nếu chưa có dải xung. */
    @Nullable
    private int[] readEstimatedLoad() {
        final long[][] ranges = coreFreqRange;
        if (ranges == null) return null;

        int[] result = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            result[i] = CpuLoadEstimator.estimatePercent(
                    CpuInfoReader.getCurrentFrequencyKhz(i), ranges[i][0], ranges[i][1]);
        }
        return result;
    }

    private void bindEstimated(@NonNull int[] loads) {
        boolean anyUsable = false;
        for (int core = 0; core < loads.length; core++) {
            if (loads[core] == CpuLoadEstimator.UNKNOWN) continue;
            adapter.addSample(core, loads[core]);
            anyUsable = true;
        }

        // Cả hai cách đều thất bại: nói thẳng là không đọc được, tuyệt đối không
        // vẽ 0% vì 0% trông y hệt như CPU đang hoàn toàn rảnh
        binding.tvUnavailable.setText(anyUsable
                ? R.string.cpu_estimated_note
                : R.string.cpu_unavailable);
        binding.tvUnavailable.setVisibility(View.VISIBLE);
    }

    /** Phần tử 0 là toàn bộ CPU, các phần tử sau mới là từng nhân. */
    private void pushSamples(@NonNull List<CpuUsageSnapshot> current) {
        for (int index = 1; index < current.size(); index++) {
            final CpuUsageSnapshot previous =
                    previousSnapshots != null && index < previousSnapshots.size()
                            ? previousSnapshots.get(index)
                            : null;
            adapter.addSample(index - 1, current.get(index).usagePercentSince(previous));
        }
    }
}
