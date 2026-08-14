package com.dung.chargmagagement.controller.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.databinding.ItemClipBinding;
import com.dung.chargmagagement.model.ui.ClipItem;

import java.util.ArrayList;
import java.util.List;

/** Danh sách các mục đang nằm trong bộ nhớ tạm. */
public class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.ItemViewHolder> {

    public interface OnItemClick {
        void onClick(@NonNull ClipItem item);
    }

    private final List<ClipItem> items = new ArrayList<>();
    private final OnItemClick onItemClick;

    public ClipAdapter(@NonNull OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void submit(@NonNull List<ClipItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ItemViewHolder(ItemClipBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false), onItemClick);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final ItemClipBinding binding;
        private final OnItemClick onItemClick;

        ItemViewHolder(@NonNull ItemClipBinding binding, @NonNull OnItemClick onItemClick) {
            super(binding.getRoot());
            this.binding = binding;
            this.onItemClick = onItemClick;
        }

        void bind(@NonNull ClipItem item) {
            final Context context = binding.getRoot().getContext();

            // Ứng dụng nào cũng đặt nhãn được, nhưng phần lớn để trống
            binding.tvClipLabel.setText(item.label.isEmpty()
                    ? context.getString(R.string.clipboard_item_label)
                    : item.label);

            binding.tvClipText.setText(item.text);

            // Không phải ROM nào cũng trả về mốc thời gian, thiếu thì giấu hẳn đi
            binding.tvClipTime.setVisibility(item.timestamp > 0 ? View.VISIBLE : View.GONE);
            if (item.timestamp > 0) {
                binding.tvClipTime.setText(formatTime(context, item.timestamp));
            }

            binding.getRoot().setOnClickListener(v -> onItemClick.onClick(item));
        }

        private static String formatTime(@NonNull Context context, long timestamp) {
            final String clock = DateUtils.formatTime(timestamp);
            return DateUtils.isToday(timestamp)
                    ? context.getString(R.string.clean_time_today, clock)
                    : context.getString(R.string.clean_time_other,
                        clock, DateUtils.formatDate(timestamp));
        }
    }
}
