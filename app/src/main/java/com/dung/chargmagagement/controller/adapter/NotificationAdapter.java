package com.dung.chargmagagement.controller.adapter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.databinding.ItemNotificationBinding;
import com.dung.chargmagagement.model.ui.NotificationItem;

import java.util.ArrayList;
import java.util.List;

/** Danh sách thông báo đang hiển thị ở màn Dọn dẹp thông báo. */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ItemViewHolder> {

    public interface OnItemClick {
        void onClick(@NonNull NotificationItem item);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final OnItemClick onItemClick;

    public NotificationAdapter(@NonNull OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void submit(@NonNull List<NotificationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ItemViewHolder(ItemNotificationBinding.inflate(
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

        private final ItemNotificationBinding binding;
        private final OnItemClick onItemClick;

        ItemViewHolder(@NonNull ItemNotificationBinding binding, @NonNull OnItemClick onItemClick) {
            super(binding.getRoot());
            this.binding = binding;
            this.onItemClick = onItemClick;
        }

        void bind(@NonNull NotificationItem item) {
            final Context context = binding.getRoot().getContext();

            binding.tvAppName.setText(item.appName);
            binding.tvTime.setText(formatTime(context, item.postTime));

            // Thông báo không có tiêu đề thì đẩy nội dung lên dòng trên, tránh để
            // một dòng trống rồi mới tới chữ
            final boolean hasTitle = !item.title.isEmpty();
            binding.tvTitle.setText(hasTitle ? item.title : item.text);
            binding.tvTitle.setVisibility(
                    hasTitle || !item.text.isEmpty() ? View.VISIBLE : View.GONE);

            binding.tvText.setText(item.text);
            binding.tvText.setVisibility(
                    hasTitle && !item.text.isEmpty() ? View.VISIBLE : View.GONE);

            bindIcons(context, item);
            binding.getRoot().setOnClickListener(v -> onItemClick.onClick(item));
        }

        private void bindIcons(@NonNull Context context, @NonNull NotificationItem item) {
            binding.ivAppIcon.setImageDrawable(loadAppIcon(context, item.packageName));

            if (item.largeIcon == null) {
                binding.ivLargeIcon.setVisibility(View.GONE);
                return;
            }
            binding.ivLargeIcon.setImageDrawable(item.largeIcon.loadDrawable(context));
            binding.ivLargeIcon.setVisibility(View.VISIBLE);
        }

        /** Icon ứng dụng gửi thông báo; lùi về icon của chính app nếu không đọc được. */
        private android.graphics.drawable.Drawable loadAppIcon(@NonNull Context context,
                                                               @NonNull String packageName) {
            try {
                return context.getPackageManager().getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                return androidx.core.content.ContextCompat.getDrawable(
                        context, R.mipmap.ic_launcher);
            }
        }

        /** "14:16 Hôm nay" với thông báo trong ngày, còn lại thì kèm ngày tháng. */
        private static String formatTime(@NonNull Context context, long postTime) {
            final String clock = DateUtils.formatTime(postTime);
            return DateUtils.isToday(postTime)
                    ? context.getString(R.string.clean_time_today, clock)
                    : context.getString(R.string.clean_time_other,
                        clock, DateUtils.formatDate(postTime));
        }
    }
}
