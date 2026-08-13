package com.dung.chargmagagement.controller.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ItemCpuCoreBinding;
import com.dung.chargmagagement.model.device.ClockFormat;
import com.dung.chargmagagement.model.device.CpuCore;

import java.util.List;
import java.util.Locale;

/**
 * Lưới thẻ tải từng nhân CPU.
 *
 * <p>Adapter đọc thẳng danh sách {@link CpuCore} do màn hình sở hữu và chỉ báo
 * "dữ liệu đã đổi" sau mỗi lần lấy mẫu. Cách này thay cho bản cũ vốn phải giữ
 * tham chiếu tới từng ViewHolder – thứ chỉ cần thiết khi ô có biểu đồ trượt cần
 * nhớ lịch sử, mà thiết kế mới thì không còn.
 */
public class CpuCoreAdapter extends RecyclerView.Adapter<CpuCoreAdapter.CoreViewHolder> {

    private final List<CpuCore> cores;

    public CpuCoreAdapter(@NonNull List<CpuCore> cores) {
        this.cores = cores;
    }

    @NonNull
    @Override
    public CoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CoreViewHolder(ItemCpuCoreBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CoreViewHolder holder, int position) {
        holder.bind(cores.get(position));
    }

    @Override
    public int getItemCount() {
        return cores.size();
    }

    static class CoreViewHolder extends RecyclerView.ViewHolder {

        private final ItemCpuCoreBinding binding;

        CoreViewHolder(@NonNull ItemCpuCoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CpuCore core) {
            final Context context = binding.getRoot().getContext();

            binding.tvCoreLabel.setText(
                    context.getString(R.string.cpu_core_label, core.getDisplayNumber()));
            binding.tvCoreName.setText(core.name == null ? "" : core.name);

            binding.tvCoreClock.setText(ClockFormat.format(core.currentKhz));
            binding.tvCoreMin.setText(ClockFormat.format(core.minKhz));
            binding.tvCoreMax.setText(ClockFormat.format(core.maxKhz));

            // Chưa đo được thì để trống và thanh về 0, tuyệt đối không hiện "0%"
            // vì 0% trông y hệt một nhân đang hoàn toàn rảnh
            final boolean measured = core.loadPercent != CpuCore.LOAD_UNKNOWN;
            binding.tvCoreLoad.setText(measured
                    ? String.format(Locale.getDefault(), "%d%%", core.loadPercent)
                    : context.getString(R.string.value_placeholder));
            binding.progressCore.setProgress(measured ? core.loadPercent : 0);
        }
    }
}
