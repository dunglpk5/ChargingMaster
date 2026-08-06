package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ItemCalendarDayBinding;
import com.dung.chargmagagement.model.ui.CalendarDay;

/**
 * Adapter cho lưới lịch tháng 7 cột.
 */
public class CalendarAdapter extends ListAdapter<CalendarDay, CalendarAdapter.DayViewHolder> {

    private static final DiffUtil.ItemCallback<CalendarDay> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CalendarDay>() {
                @Override
                public boolean areItemsTheSame(@NonNull CalendarDay o, @NonNull CalendarDay n) {
                    return o.getDayKey() == n.getDayKey();
                }

                @Override
                public boolean areContentsTheSame(@NonNull CalendarDay o, @NonNull CalendarDay n) {
                    return o.isSelected() == n.isSelected()
                            && o.hasData() == n.hasData()
                            && o.isToday() == n.isToday()
                            && o.isInCurrentMonth() == n.isInCurrentMonth();
                }
            };

    @Nullable
    private OnDayClickListener listener;

    public CalendarAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnDayClickListener(@Nullable OnDayClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCalendarDayBinding binding = ItemCalendarDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DayViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {

        private final ItemCalendarDayBinding binding;

        DayViewHolder(@NonNull ItemCalendarDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CalendarDay day, @Nullable OnDayClickListener listener) {
            binding.tvDay.setText(String.valueOf(day.getDayOfMonth()));

            // Ngày đang chọn có nền tròn trắng nên chữ phải đổi sang teal mới đọc được;
            // ngày của tháng khác thì làm mờ để người dùng phân biệt
            final int colorRes;
            if (day.isSelected()) {
                colorRes = R.color.teal_primary;
            } else if (day.isInCurrentMonth()) {
                colorRes = R.color.text_on_primary;
            } else {
                colorRes = R.color.text_on_primary_60;
            }
            binding.tvDay.setTextColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), colorRes));

            // Vòng tròn viền cho hôm nay, nền đầy cho ngày đang chọn
            if (day.isSelected()) {
                binding.tvDay.setBackgroundResource(R.drawable.bg_calendar_selected);
            } else if (day.isToday()) {
                binding.tvDay.setBackgroundResource(R.drawable.bg_calendar_today);
            } else {
                binding.tvDay.setBackground(null);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onDayClick(day);
            });
        }
    }

    /** Callback khi người dùng chọn một ngày trên lịch. */
    public interface OnDayClickListener {
        void onDayClick(@NonNull CalendarDay day);
    }
}
