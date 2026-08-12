package com.dung.chargmagagement.controller.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.databinding.ItemDrainFeatureBinding;
import com.dung.chargmagagement.model.power.DrainStatus;
import com.dung.chargmagagement.model.power.PowerDrainFeature;

/**
 * Adapter cho danh sách tính năng tiêu điện.
 *
 * <p>Mục đang bật được làm nổi bật kèm nút mở Cài đặt; mục đã tắt vẫn hiển thị
 * nhưng mờ đi, để người dùng thấy được mình đã tắt những gì.
 */
public class DrainFeatureAdapter
        extends ListAdapter<DrainStatus, DrainFeatureAdapter.FeatureViewHolder> {

    private static final DiffUtil.ItemCallback<DrainStatus> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<DrainStatus>() {
                @Override
                public boolean areItemsTheSame(@NonNull DrainStatus o, @NonNull DrainStatus n) {
                    return o.getFeature() == n.getFeature();
                }

                @Override
                public boolean areContentsTheSame(@NonNull DrainStatus o, @NonNull DrainStatus n) {
                    return o.isActive() == n.isActive();
                }
            };

    @Nullable
    private OnFeatureClickListener listener;

    public DrainFeatureAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnFeatureClickListener(@Nullable OnFeatureClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public FeatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDrainFeatureBinding binding = ItemDrainFeatureBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new FeatureViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FeatureViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class FeatureViewHolder extends RecyclerView.ViewHolder {

        private final ItemDrainFeatureBinding binding;

        FeatureViewHolder(@NonNull ItemDrainFeatureBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DrainStatus status, @Nullable OnFeatureClickListener listener) {
            final boolean active = status.isActive();
            final PowerDrainFeature feature = status.getFeature();

            binding.imgShield.setImageResource(R.drawable.ic_shield);
            // Mục đã đạt dùng khiên xanh nước biển; mục cần xử lý giữ màu cảnh báo
            // riêng của nó để mắt phân biệt được ngay hai nhóm
            ImageViewCompat.setImageTintList(binding.imgShield,
                    ContextCompat.getColorStateList(context(),
                            active ? feature.getShieldColorRes() : R.color.shield_blue));

            binding.imgIcon.setImageResource(feature.getIconRes());
            binding.tvLabel.setText(feature.getLabelRes());
            binding.tvDetail.setText(feature.getDescriptionRes());

            binding.imgBadge.setVisibility(active ? View.VISIBLE : View.GONE);
            // Không làm mờ nữa: màu xanh đã đủ để phân biệt, mà làm mờ thì mục đã
            // đạt trông như đang bị vô hiệu hoá
            binding.imgShield.setAlpha(1f);

            // Mục đã đạt không còn việc gì để bấm, thay nút bằng dấu tích.
            // INVISIBLE chứ không GONE – xem ghi chú trong item_drain_feature.xml
            binding.btnTurnOff.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            binding.imgOk.setVisibility(active ? View.GONE : View.VISIBLE);
            binding.btnTurnOff.setText(R.string.check_detect);

            binding.btnTurnOff.setOnClickListener(v -> {
                if (listener != null) listener.onFeatureClick(status);
            });
        }

        private Context context() {
            return binding.getRoot().getContext();
        }
    }

    /** Người dùng bấm nút mở trang Cài đặt của một tính năng. */
    public interface OnFeatureClickListener {
        void onFeatureClick(@NonNull DrainStatus status);
    }
}
