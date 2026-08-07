package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.databinding.ItemAppUsageBinding;
import com.dung.chargmagagement.model.device.AppUsageItem;

import java.util.Locale;

/**
 * Danh sách ứng dụng dùng nhiều pin nhất.
 */
public class AppUsageAdapter extends ListAdapter<AppUsageItem, AppUsageAdapter.AppViewHolder> {

    private static final DiffUtil.ItemCallback<AppUsageItem> DIFF =
            new DiffUtil.ItemCallback<AppUsageItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppUsageItem a, @NonNull AppUsageItem b) {
                    return a.getPackageName().equals(b.getPackageName());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppUsageItem a, @NonNull AppUsageItem b) {
                    return a.getForegroundMs() == b.getForegroundMs()
                            && Math.abs(a.getSharePercent() - b.getSharePercent()) < 0.01f;
                }
            };

    public AppUsageAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(ItemAppUsageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {

        private final ItemAppUsageBinding binding;

        AppViewHolder(@NonNull ItemAppUsageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull AppUsageItem item) {
            binding.tvName.setText(item.getLabel());
            binding.tvDuration.setText(FormatUtils.formatDuration(item.getForegroundMs()));
            binding.tvShare.setText(
                    String.format(Locale.getDefault(), "%.1f %%", item.getSharePercent()));
            binding.progressShare.setProgress(Math.round(item.getSharePercent()));

            if (item.getIcon() != null) {
                binding.imgIcon.setImageDrawable(item.getIcon());
            } else {
                binding.imgIcon.setImageResource(R.drawable.ic_phone);
            }
        }
    }
}
