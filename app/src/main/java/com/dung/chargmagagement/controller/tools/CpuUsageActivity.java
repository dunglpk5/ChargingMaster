package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.adapter.CpuCoreAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityCpuUsageBinding;
import com.dung.chargmagagement.databinding.ViewCpuClockCellBinding;
import com.dung.chargmagagement.databinding.ViewCpuSpecCellBinding;
import com.dung.chargmagagement.model.device.ClockFormat;
import com.dung.chargmagagement.model.device.CpuClusters;
import com.dung.chargmagagement.model.device.CpuCore;
import com.dung.chargmagagement.model.device.CpuInfoReader;
import com.dung.chargmagagement.model.device.CpuLoadEstimator;
import com.dung.chargmagagement.model.device.CpuPartName;
import com.dung.chargmagagement.model.device.CpuUsageReader;
import com.dung.chargmagagement.model.device.CpuUsageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Màn Sử dụng CPU: thông số chip, xung nhịp hiện tại và mức tải của từng nhân.
 *
 * <p>Mức tải tính bằng chênh lệch giữa hai lần đọc {@code /proc/stat}, nên lần đo
 * đầu tiên luôn là 0 – phải có mốc trước mới tính được tỉ lệ.
 *
 * <p>Việc lấy mẫu chỉ chạy khi màn hình đang hiển thị và dừng hẳn ở
 * {@code onPause()}: đọc file mỗi giây trong lúc người dùng đã rời màn là kiểu
 * hao pin vô nghĩa nhất.
 */
public class CpuUsageActivity extends BaseActivity<ActivityCpuUsageBinding> {

    /** Chu kỳ cập nhật; ngắn hơn nữa thì số nhảy quá nhanh, khó đọc. */
    private static final long SAMPLE_INTERVAL_MS = 1_500L;

    private static final int GRID_SPAN_COUNT = 2;

    /** Số ô xung nhịp trên một hàng ở thẻ "Tốc độ hiện tại". */
    private static final int CLOCK_COLUMNS = 2;

    private final List<CpuCore> cores = new ArrayList<>();
    private final List<ViewCpuClockCellBinding> clockCells = new ArrayList<>();

    private CpuCoreAdapter adapter;
    private ScheduledFuture<?> samplingTask;

    /** Lần đọc trước, dùng làm mốc để tính chênh lệch. */
    private List<CpuUsageSnapshot> previousSnapshots;

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
        binding.btnBack.setOnClickListener(v -> finish());

        final int coreCount = CpuInfoReader.getCoreCount();
        for (int i = 0; i < coreCount; i++) {
            cores.add(new CpuCore(i));
        }

        setupSpecLabels(coreCount);
        buildClockRows(coreCount);

        adapter = new CpuCoreAdapter(cores);
        binding.rvCores.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        binding.rvCores.setItemAnimator(null);
        binding.rvCores.setAdapter(adapter);

        loadStaticSpecs();
    }

    // ==================== 1. Thông số chip ====================

    private void setupSpecLabels(int coreCount) {
        setSpecLabel(binding.specName, R.string.cpu_spec_name);
        setSpecLabel(binding.specVendor, R.string.cpu_spec_vendor);
        setSpecLabel(binding.specSpeed, R.string.cpu_spec_speed);
        setSpecLabel(binding.specCores, R.string.cpu_spec_cores);
        setSpecLabel(binding.specArch, R.string.cpu_spec_arch);

        // Cột phải dùng màu thương hiệu, cột trái để đen như bản thiết kế
        setSpecValueColor(binding.specVendor, R.color.text_value);
        setSpecValueColor(binding.specCores, R.color.text_value);
        setSpecValueColor(binding.specAbi, R.color.text_value);

        // Ô tập lệnh chỉ có giá trị, không có nhãn
        binding.specAbi.tvSpecLabel.setVisibility(View.GONE);

        binding.specCores.tvSpecValue.setText(String.valueOf(coreCount));
        binding.specAbi.tvSpecValue.setText(orPlaceholder(CpuInfoReader.getPrimaryAbi()));
    }

    private void setSpecLabel(@NonNull ViewCpuSpecCellBinding cell, @StringRes int labelRes) {
        cell.tvSpecLabel.setText(labelRes);
        cell.tvSpecValue.setText(R.string.value_placeholder);
    }

    private void setSpecValueColor(@NonNull ViewCpuSpecCellBinding cell, @ColorRes int colorRes) {
        cell.tvSpecValue.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    /**
     * Đọc những thông số không đổi khi máy chạy: tên chip, hãng, dải xung và
     * dải xung riêng của từng nhân. Tất cả đều đọc file nên phải chạy ở thread nền.
     */
    private void loadStaticSpecs() {
        executors.execute(this::readStaticSpecs, specs -> {
            if (binding == null || specs == null) return;

            binding.specName.tvSpecValue.setText(orPlaceholder(specs.chipName));
            binding.specVendor.tvSpecValue.setText(orPlaceholder(specs.vendor));
            binding.specSpeed.tvSpecValue.setText(orPlaceholder(specs.speedRange));
            binding.specArch.tvSpecValue.setText(orPlaceholder(specs.architecture));

            adapter.notifyDataSetChanged();
        });
    }

    @NonNull
    private StaticSpecs readStaticSpecs() {
        final int[] parts = CpuInfoReader.readCoreParts(cores.size());
        for (CpuCore core : cores) {
            core.minKhz = CpuInfoReader.getCoreMinFrequencyKhz(core.index);
            core.maxKhz = CpuInfoReader.getCoreMaxFrequencyKhz(core.index);
            core.name = core.index < parts.length
                    ? CpuPartName.fromPart(parts[core.index])
                    : null;
        }

        StaticSpecs specs = new StaticSpecs();
        specs.chipName = CpuInfoReader.getSocModel();
        specs.vendor = CpuInfoReader.getSocManufacturer();
        specs.architecture = CpuClusters.describe(cores);
        specs.speedRange = formatSpeedRange();
        return specs;
    }

    /** Dải xung toàn máy: nhỏ nhất của nhân tiết kiệm tới lớn nhất của nhân mạnh. */
    @Nullable
    private String formatSpeedRange() {
        long min = Long.MAX_VALUE;
        long max = 0L;
        for (CpuCore core : cores) {
            if (core.minKhz > 0) min = Math.min(min, core.minKhz);
            max = Math.max(max, core.maxKhz);
        }
        if (min == Long.MAX_VALUE || max <= 0) return null;

        return getString(R.string.cpu_freq_range,
                ClockFormat.format(min), ClockFormat.format(max));
    }

    // ==================== 2. Xung nhịp hiện tại ====================

    private void buildClockRows(int coreCount) {
        final LayoutInflater inflater = getLayoutInflater();
        LinearLayout row = null;

        for (int i = 0; i < coreCount; i++) {
            if (i % CLOCK_COLUMNS == 0) {
                row = newClockRow();
                binding.clockRows.addView(row);
            }

            ViewCpuClockCellBinding cell = ViewCpuClockCellBinding.inflate(inflater, row, false);
            cell.tvClockLabel.setText(getString(R.string.cpu_core_label, i + 1));
            cell.tvClockValue.setText(R.string.value_placeholder);

            // Chia đều hai cột; ô lẻ cuối cùng vẫn chỉ chiếm nửa hàng
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % CLOCK_COLUMNS != 0) {
                params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_md));
            }
            cell.getRoot().setLayoutParams(params);

            row.addView(cell.getRoot());
            clockCells.add(cell);
        }
    }

    @NonNull
    private LinearLayout newClockRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void bindClocks() {
        for (int i = 0; i < clockCells.size() && i < cores.size(); i++) {
            final TextView value = clockCells.get(i).tvClockValue;
            value.setText(orPlaceholder(ClockFormat.format(cores.get(i).currentKhz)));
        }
    }

    // ==================== 3. Lấy mẫu ====================

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
        final long[] clocks = new long[cores.size()];
        for (int i = 0; i < clocks.length; i++) {
            clocks[i] = CpuInfoReader.getCurrentFrequencyKhz(i);
        }

        final List<CpuUsageSnapshot> current = CpuUsageReader.readSnapshots();
        final boolean estimated = current.isEmpty();

        executors.runOnMain(() -> {
            if (binding == null) return;

            for (int i = 0; i < cores.size(); i++) {
                cores.get(i).currentKhz = clocks[i];
            }

            if (estimated) {
                applyEstimatedLoad();
            } else {
                applyMeasuredLoad(current);
                previousSnapshots = current;
                binding.tvUnavailable.setVisibility(View.GONE);
            }

            bindClocks();
            adapter.notifyDataSetChanged();
        });
    }

    /** Phần tử 0 của snapshot là toàn bộ CPU, các phần tử sau mới là từng nhân. */
    private void applyMeasuredLoad(@NonNull List<CpuUsageSnapshot> current) {
        for (int index = 1; index < current.size() && index - 1 < cores.size(); index++) {
            final CpuUsageSnapshot previous =
                    previousSnapshots != null && index < previousSnapshots.size()
                            ? previousSnapshots.get(index)
                            : null;
            cores.get(index - 1).loadPercent = current.get(index).usagePercentSince(previous);
        }
    }

    private void applyEstimatedLoad() {
        boolean anyUsable = false;
        for (CpuCore core : cores) {
            final int load = CpuLoadEstimator.estimatePercent(
                    core.currentKhz, core.minKhz, core.maxKhz);
            if (load == CpuLoadEstimator.UNKNOWN) continue;

            core.loadPercent = load;
            anyUsable = true;
        }

        // Cả hai cách đều thất bại: nói thẳng là không đọc được, tuyệt đối không
        // vẽ 0% vì 0% trông y hệt như CPU đang hoàn toàn rảnh
        binding.tvUnavailable.setText(anyUsable
                ? R.string.cpu_estimated_note
                : R.string.cpu_unavailable);
        binding.tvUnavailable.setVisibility(View.VISIBLE);
    }

    @NonNull
    private String orPlaceholder(@Nullable String value) {
        return value == null || value.trim().isEmpty()
                ? getString(R.string.value_placeholder)
                : value;
    }

    /** Gói thông số cố định, để một lần truy cập nền trả về đủ mọi thứ. */
    private static class StaticSpecs {
        @Nullable String chipName;
        @Nullable String vendor;
        @Nullable String architecture;
        @Nullable String speedRange;
    }
}
