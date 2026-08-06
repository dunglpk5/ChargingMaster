package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.databinding.ItemToolBinding;
import com.dung.chargmagagement.model.ui.ToolItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho lưới công cụ 3 cột.
 *
 * <p>Danh sách cố định (6 ô mỗi nhóm) và không đổi trong lúc chạy nên dùng adapter
 * thường, không cần DiffUtil như {@link InfoAdapter}.
 */
public class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.ToolViewHolder> {

    private final List<ToolItem> items = new ArrayList<>();

    @Nullable
    private OnToolClickListener listener;

    public ToolAdapter(@NonNull List<ToolItem> items) {
        this.items.addAll(items);
    }

    public void setOnToolClickListener(@Nullable OnToolClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ToolViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemToolBinding binding = ItemToolBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ToolViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ToolViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ToolViewHolder extends RecyclerView.ViewHolder {

        private final ItemToolBinding binding;

        ToolViewHolder(@NonNull ItemToolBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ToolItem item, @Nullable OnToolClickListener listener) {
            binding.imgIcon.setImageResource(item.getIconRes());
            ImageViewCompat.setImageTintList(binding.imgIcon,
                    ContextCompat.getColorStateList(
                            binding.getRoot().getContext(), item.getIconTintRes()));
            binding.tvLabel.setText(item.getLabelRes());

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onToolClick(item.getAction());
            });
        }
    }

    /** Callback khi người dùng bấm một ô công cụ. */
    public interface OnToolClickListener {
        void onToolClick(@NonNull ToolItem.Action action);
    }
}
