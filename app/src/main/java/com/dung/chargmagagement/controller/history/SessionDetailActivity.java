package com.dung.chargmagagement.controller.history;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.adapter.InfoAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivitySessionDetailBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.db.AppDatabase;
import com.dung.chargmagagement.model.entity.BatterySampleEntity;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.ui.ChartPoint;
import com.dung.chargmagagement.model.ui.InfoItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Chi tiết một phiên sạc: biểu đồ mức pin trong phiên và bảng số liệu.
 */
public class SessionDetailActivity extends BaseActivity<ActivitySessionDetailBinding> {

    private static final String EXTRA_SESSION_ID = "session_id";

    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private InfoAdapter adapter;

    public static void start(@NonNull Context context, long sessionId) {
        Intent intent = new Intent(context, SessionDetailActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        context.startActivity(intent);
    }

    @NonNull
    @Override
    protected ActivitySessionDetailBinding onCreateBinding() {
        return ActivitySessionDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.history_detail_title);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        adapter = new InfoAdapter();
        binding.rvStats.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStats.setAdapter(adapter);
        // Danh sách nằm trong NestedScrollView nên tự cuộn của nó phải tắt
        binding.rvStats.setNestedScrollingEnabled(false);

        loadSession(getIntent().getLongExtra(EXTRA_SESSION_ID, 0L));
    }

    /**
     * Đọc phiên và các điểm đo của nó trong <b>một lần</b> chạy nền, thay vì hai
     * lời gọi riêng – tránh tình trạng biểu đồ và bảng số liệu hiện lệch nhau.
     */
    private void loadSession(long sessionId) {
        if (sessionId <= 0L) {
            finish();
            return;
        }

        final AppDatabase database = AppDatabase.get(this);

        executors.execute(() -> {
            ChargingSessionEntity session = database.chargingSessionDao().findById(sessionId);
            if (session == null) return null;

            List<BatterySampleEntity> samples =
                    database.batterySampleDao().getSamplesOfSession(sessionId);
            return new SessionData(session, samples);
        }, data -> {
            if (binding == null || data == null) {
                finish();
                return;
            }
            bindChart(data.samples);
            adapter.submitList(buildStats(data.session));
        });
    }

    private void bindChart(@NonNull List<BatterySampleEntity> samples) {
        List<ChartPoint> points = new ArrayList<>(samples.size());
        for (BatterySampleEntity sample : samples) {
            points.add(new ChartPoint(
                    DateUtils.minuteOfDay(sample.timestamp), sample.percent, sample.charging));
        }
        binding.chartSession.setPoints(points);
    }

    /** Bảng số liệu tổng kết của phiên. */
    private List<InfoItem> buildStats(@NonNull ChargingSessionEntity session) {
        final String placeholder = getString(R.string.value_placeholder);
        List<InfoItem> items = new ArrayList<>();

        items.add(new InfoItem("start", 0, R.string.history_start_time,
                timeFormat.format(new Date(session.startTime))));

        items.add(new InfoItem("end", 0, R.string.history_end_time,
                session.isFinished()
                        ? timeFormat.format(new Date(session.endTime))
                        : getString(R.string.history_ongoing)));

        items.add(new InfoItem("duration", 0, R.string.history_duration,
                FormatUtils.formatDuration(session.getDurationMs())));

        items.add(new InfoItem("gain", 0, R.string.history_gain,
                getString(R.string.history_gain_format,
                        session.getGainedPercent(), session.startPercent, session.endPercent)));

        items.add(new InfoItem("plug", 0, R.string.history_plug_type, session.plugType));

        items.add(new InfoItem("avg", 0, R.string.check_average,
                session.avgCurrentMa == BatteryInfo.UNKNOWN_INT
                        ? placeholder
                        : String.format(Locale.US, "%d mA", session.avgCurrentMa)));

        items.add(new InfoItem("max", 0, R.string.check_max,
                session.maxCurrentMa == BatteryInfo.UNKNOWN_INT
                        ? placeholder
                        : String.format(Locale.US, "%d mA", session.maxCurrentMa)));

        items.add(new InfoItem("temp", 0, R.string.history_max_temp,
                FormatUtils.formatTemperature(session.maxTemperature)));

        items.add(new InfoItem("charged", 0, R.string.history_charged_mah,
                session.chargedMah > 0
                        ? String.format(Locale.US, "%.0f mAh", session.chargedMah)
                        : placeholder));

        items.add(new InfoItem("capacity", 0, R.string.usage_estimated_capacity,
                session.getEstimatedCapacityMah() == BatteryInfo.UNKNOWN_INT
                        ? placeholder
                        : String.format(Locale.US, "%d mAh", session.getEstimatedCapacityMah())));

        return items;
    }

    /** Gói dữ liệu trả về từ thread nền. */
    private static final class SessionData {
        final ChargingSessionEntity session;
        final List<BatterySampleEntity> samples;

        SessionData(ChargingSessionEntity session, List<BatterySampleEntity> samples) {
            this.session = session;
            this.samples = samples;
        }
    }
}
