package com.dung.chargmagagement.controller.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.databinding.ItemChargeSessionBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter cho danh sách lịch sử phiên sạc.
 */
public class ChargeHistoryAdapter
        extends ListAdapter<ChargingSessionEntity, ChargeHistoryAdapter.SessionViewHolder> {

    private static final DiffUtil.ItemCallback<ChargingSessionEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChargingSessionEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChargingSessionEntity o,
                                               @NonNull ChargingSessionEntity n) {
                    return o.id == n.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChargingSessionEntity o,
                                                  @NonNull ChargingSessionEntity n) {
                    return o.endTime == n.endTime
                            && o.endPercent == n.endPercent
                            && o.avgCurrentMa == n.avgCurrentMa;
                }
            };

    @Nullable
    private OnSessionClickListener listener;

    public ChargeHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnSessionClickListener(@Nullable OnSessionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChargeSessionBinding binding = ItemChargeSessionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SessionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {

        /** Định dạng ngày giờ theo Locale máy để hợp với thói quen người dùng. */
        private final SimpleDateFormat dateFormat =
                new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

        private final ItemChargeSessionBinding binding;

        SessionViewHolder(@NonNull ItemChargeSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ChargingSessionEntity session,
                  @Nullable OnSessionClickListener listener) {

            binding.tvTime.setText(dateFormat.format(new Date(session.startTime)));
            binding.tvDuration.setText(FormatUtils.formatDuration(session.getDurationMs()));

            binding.tvGain.setText(binding.getRoot().getContext().getString(
                    R.string.history_gain_format,
                    session.getGainedPercent(), session.startPercent, session.endPercent));

            binding.tvCurrent.setText(session.avgCurrentMa == BatteryInfo.UNKNOWN_INT
                    ? binding.getRoot().getContext().getString(R.string.value_placeholder)
                    : String.format(Locale.US, "%d mA", session.avgCurrentMa));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onSessionClick(session);
            });
        }
    }

    /** Người dùng bấm vào một phiên để xem chi tiết. */
    public interface OnSessionClickListener {
        void onSessionClick(@NonNull ChargingSessionEntity session);
    }
}
