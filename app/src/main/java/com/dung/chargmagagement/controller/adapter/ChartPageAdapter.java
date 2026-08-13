package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.databinding.ItemChartPageBinding;
import com.dung.chargmagagement.model.ui.ChartPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Ba trang biểu đồ mức pin, mỗi trang một ngày, cũ nhất bên trái.
 *
 * <p>Dữ liệu từng ngày được nạp bất đồng bộ nên adapter giữ sẵn ba ô rỗng và điền
 * dần bằng {@link #setPoints}. Cách này giữ số trang cố định ngay từ đầu: nếu chờ
 * đủ dữ liệu mới tạo trang thì bộ chấm chỉ trang sẽ nhảy số trong lúc nạp.
 */
public class ChartPageAdapter extends RecyclerView.Adapter<ChartPageAdapter.PageViewHolder> {

    /** Số ngày hiển thị, khớp với nhãn "Lịch sử 3 ngày gần đây". */
    public static final int PAGE_COUNT = 3;

    private final List<List<ChartPoint>> pages = new ArrayList<>(PAGE_COUNT);
    private final int[] dayKeys = new int[PAGE_COUNT];

    public ChartPageAdapter() {
        for (int i = 0; i < PAGE_COUNT; i++) {
            pages.add(new ArrayList<>());
            // Trang cuối là hôm nay, lùi dần về trước
            dayKeys[i] = DateUtils.toDayKey(DateUtils.daysAgo(PAGE_COUNT - 1 - i));
        }
    }

    /** Khoá ngày của một trang, dùng để biết cần truy vấn ngày nào. */
    public int getDayKey(int position) {
        return dayKeys[position];
    }

    public void setPoints(int position, @NonNull List<ChartPoint> points) {
        pages.set(position, points);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PageViewHolder(ItemChartPageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        holder.bind(dayKeys[position], pages.get(position));
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {

        private final ItemChartPageBinding binding;

        PageViewHolder(@NonNull ItemChartPageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(int dayKey, @NonNull List<ChartPoint> points) {
            binding.tvChartDate.setText(formatDayKey(dayKey));
            binding.chartBattery.setPoints(points);
        }

        /** Đổi khoá yyyyMMdd thành "2026-08-13" như bản thiết kế. */
        private static String formatDayKey(int dayKey) {
            return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    dayKey / 10000, (dayKey / 100) % 100, dayKey % 100);
        }
    }
}
