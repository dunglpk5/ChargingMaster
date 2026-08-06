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
            ImageViewCompat.setImageTintList(binding.imgShield,
                    ContextCompat.getColorStateList(context(), feature.getShieldColorRes()));

            binding.imgIcon.setImageResource(feature.getIconRes());
            binding.tvLabel.setText(feature.getLabelRes());
            binding.tvDetail.setText(feature.getDescriptionRes());

            // Chấm than chỉ hiện ở mục đang cần xử lý; mục đã đạt thì làm mờ khiên
            binding.imgBadge.setVisibility(active ? View.VISIBLE : View.GONE);
            binding.imgShield.setAlpha(active ? 1f : 0.45f);

            // Mục đã đạt vẫn giữ nút để người dùng chủ động vào xem, chỉ đổi nhãn
            binding.btnTurnOff.setText(active ? R.string.check_detect : R.string.check_ok);
            binding.btnTurnOff.setEnabled(active || !feature.hasSettingsPage());

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
