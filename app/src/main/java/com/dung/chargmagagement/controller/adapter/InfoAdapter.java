package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.databinding.ItemInfoRowBinding;
import com.dung.chargmagagement.model.ui.InfoItem;

/**
 * Adapter cho danh sách dòng "nhãn – giá trị".
 *
 * <p>Dùng {@link ListAdapter} để tận dụng DiffUtil chạy trên thread nền: khi dòng
 * điện đổi từ 1500 lên 1520 mA thì chỉ mỗi dòng "Dòng sạc" được vẽ lại.
 */
public class InfoAdapter extends ListAdapter<InfoItem, InfoAdapter.InfoViewHolder> {

    private static final DiffUtil.ItemCallback<InfoItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<InfoItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull InfoItem oldItem, @NonNull InfoItem newItem) {
                    return oldItem.isSameItem(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InfoItem oldItem, @NonNull InfoItem newItem) {
                    return oldItem.hasSameContent(newItem);
                }
            };

    public InfoAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public InfoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInfoRowBinding binding = ItemInfoRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new InfoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull InfoViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class InfoViewHolder extends RecyclerView.ViewHolder {

        private final ItemInfoRowBinding binding;

        InfoViewHolder(@NonNull ItemInfoRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull InfoItem item) {
            // Icon là tuỳ chọn: các tab chi tiết thiết bị không dùng icon cho từng hàng
            if (item.getIconRes() == 0) {
                binding.imgIcon.setVisibility(View.GONE);
            } else {
                binding.imgIcon.setVisibility(View.VISIBLE);
                binding.imgIcon.setImageResource(item.getIconRes());
            }

            if (item.hasLabelRes()) {
                binding.tvLabel.setText(item.getLabelRes());
            } else {
                binding.tvLabel.setText(item.getLabel());
            }
            binding.tvValue.setText(item.getValue());
        }
    }
}
