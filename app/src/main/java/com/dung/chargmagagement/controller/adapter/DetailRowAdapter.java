package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ItemDetailHeaderBinding;
import com.dung.chargmagagement.databinding.ItemDetailMultiBinding;
import com.dung.chargmagagement.databinding.ItemDetailRowBinding;
import com.dung.chargmagagement.model.ui.DetailRow;

/**
 * Adapter cho danh sách thông tin thiết bị, hỗ trợ ba kiểu dòng của bản thiết kế.
 *
 * <p>Dùng {@link ListAdapter} để DiffUtil chạy ở thread nền; các tab chỉ nạp dữ
 * liệu một lần nhưng riêng tab CPU có xung nhịp thay đổi nên vẫn có lợi.
 */
public class DetailRowAdapter extends ListAdapter<DetailRow, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_VALUE = 1;
    private static final int TYPE_MULTI = 2;

    private static final DiffUtil.ItemCallback<DetailRow> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<DetailRow>() {
                @Override
                public boolean areItemsTheSame(@NonNull DetailRow o, @NonNull DetailRow n) {
                    return o.getKey().equals(n.getKey());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DetailRow o, @NonNull DetailRow n) {
                    return o.hasSameContent(n);
                }
            };

    public DetailRowAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public int getItemViewType(int position) {
        switch (getItem(position).getType()) {
            case HEADER:
                return TYPE_HEADER;
            case MULTI:
                return TYPE_MULTI;
            case VALUE:
            default:
                return TYPE_VALUE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderViewHolder(
                        ItemDetailHeaderBinding.inflate(inflater, parent, false));
            case TYPE_MULTI:
                return new MultiViewHolder(
                        ItemDetailMultiBinding.inflate(inflater, parent, false));
            case TYPE_VALUE:
            default:
                return new ValueViewHolder(
                        ItemDetailRowBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final DetailRow row = getItem(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(row);
        } else if (holder instanceof MultiViewHolder) {
            ((MultiViewHolder) holder).bind(row);
        } else {
            ((ValueViewHolder) holder).bind(row);
        }
    }

    /** Nhãn lấy từ string resource, hoặc chuỗi động với danh sách cảm biến. */
    private static void bindLabel(@NonNull TextView view, @NonNull DetailRow row) {
        if (row.hasLabelRes()) {
            view.setText(row.getLabelRes());
        } else {
            view.setText(row.getLabel());
        }
    }

    static class ValueViewHolder extends RecyclerView.ViewHolder {

        private final ItemDetailRowBinding binding;

        ValueViewHolder(@NonNull ItemDetailRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DetailRow row) {
            bindLabel(binding.tvLabel, row);
            binding.tvValue.setText(row.getValue());
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {

        private final ItemDetailHeaderBinding binding;

        HeaderViewHolder(@NonNull ItemDetailHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DetailRow row) {
            bindLabel(binding.tvLabel, row);
            binding.tvValue.setText(row.getValue());
            binding.imgLogo.setImageResource(row.getIconRes());
        }
    }

    static class MultiViewHolder extends RecyclerView.ViewHolder {

        private final ItemDetailMultiBinding binding;

        MultiViewHolder(@NonNull ItemDetailMultiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DetailRow row) {
            bindLabel(binding.tvLabel, row);

            // Số dòng con thay đổi theo số nhân CPU nên phải dựng lại mỗi lần bind
            binding.containerLines.removeAllViews();
            for (String line : row.getLines()) {
                binding.containerLines.addView(createLineView(line));
            }
        }

        private TextView createLineView(@NonNull String text) {
            TextView textView = new TextView(binding.getRoot().getContext());
            textView.setText(text);
            textView.setTextSize(18f);
            textView.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(), R.color.text_value));

            final int paddingVertical = binding.getRoot().getResources()
                    .getDimensionPixelSize(R.dimen.space_xs);
            textView.setPadding(0, paddingVertical, 0, paddingVertical);
            return textView;
        }
    }
}
