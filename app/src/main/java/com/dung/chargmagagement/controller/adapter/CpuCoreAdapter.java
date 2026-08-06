package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ItemCpuCoreBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho lưới biểu đồ tải từng nhân CPU.
 *
 * <p>Khác với các adapter còn lại, adapter này <b>giữ tham chiếu tới view của mỗi
 * ô</b>: biểu đồ là cửa sổ trượt, mỗi chu kỳ ta chỉ thêm một mẫu mới chứ không
 * dựng lại dữ liệu. Nếu dùng {@code notifyItemChanged} thì lịch sử vẽ trong view
 * sẽ bị mất mỗi lần bind lại.
 */
public class CpuCoreAdapter extends RecyclerView.Adapter<CpuCoreAdapter.CoreViewHolder> {

    private final int coreCount;

    /** ViewHolder theo vị trí nhân, để đẩy mẫu mới vào đúng biểu đồ. */
    private final List<CoreViewHolder> holders = new ArrayList<>();

    public CpuCoreAdapter(int coreCount) {
        this.coreCount = Math.max(0, coreCount);
    }

    @NonNull
    @Override
    public CoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCpuCoreBinding binding = ItemCpuCoreBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CoreViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CoreViewHolder holder, int position) {
        holder.bind(position);
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
    }

    @Override
    public void onViewRecycled(@NonNull CoreViewHolder holder) {
        super.onViewRecycled(holder);
        holders.remove(holder);
    }

    @Override
    public int getItemCount() {
        return coreCount;
    }

    /**
     * Đẩy một mẫu mới vào biểu đồ của một nhân.
     *
     * @param core    chỉ số nhân
     * @param percent mức tải 0..100
     */
    public void addSample(int core, int percent) {
        for (CoreViewHolder holder : holders) {
            if (holder.coreIndex == core) {
                holder.binding.chartCore.addSample(percent);
                return;
            }
        }
    }

    static class CoreViewHolder extends RecyclerView.ViewHolder {

        private final ItemCpuCoreBinding binding;
        private int coreIndex;

        CoreViewHolder(@NonNull ItemCpuCoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(int core) {
            coreIndex = core;
            binding.tvCoreLabel.setText(
                    binding.getRoot().getContext().getString(R.string.cpu_core_label, core));
            binding.chartCore.clear();
        }
    }
}
