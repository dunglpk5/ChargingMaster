package com.dung.chargmagagement.controller.history;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.adapter.ChargeHistoryAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityChargeHistoryBinding;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.repository.BatteryRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn Lịch sử sạc: danh sách các phiên đã kết thúc, mới nhất trước.
 *
 * <p>Nạp theo trang khi cuộn tới cuối thay vì lấy hết một lượt: người dùng lâu năm
 * có thể tích hàng nghìn phiên, đọc hết vào bộ nhớ chỉ để hiển thị mươi dòng đầu
 * là lãng phí.
 */
public class ChargeHistoryActivity extends BaseActivity<ActivityChargeHistoryBinding>
        implements ChargeHistoryAdapter.OnSessionClickListener {

    /** Số phiên nạp mỗi lần. */
    private static final int PAGE_SIZE = 30;

    /** Còn cách cuối danh sách bao nhiêu dòng thì bắt đầu nạp trang tiếp theo. */
    private static final int PREFETCH_DISTANCE = 5;

    private BatteryRepository repository;
    private ChargeHistoryAdapter adapter;

    private final List<ChargingSessionEntity> loadedSessions = new ArrayList<>();
    private boolean loading;
    private boolean reachedEnd;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, ChargeHistoryActivity.class));
    }

    @NonNull
    @Override
    protected ActivityChargeHistoryBinding onCreateBinding() {
        return ActivityChargeHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        repository = BatteryRepository.get(this);
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_charge_history);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        setupList();
        loadNextPage();
    }

    private void setupList() {
        adapter = new ChargeHistoryAdapter();
        adapter.setOnSessionClickListener(this);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvHistory.setLayoutManager(layoutManager);
        binding.rvHistory.setAdapter(adapter);

        binding.rvHistory.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return; // chỉ quan tâm khi cuộn xuống

                final int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - PREFETCH_DISTANCE) {
                    loadNextPage();
                }
            }
        });
    }

    /** Nạp trang tiếp theo; tự bỏ qua nếu đang nạp hoặc đã hết dữ liệu. */
    private void loadNextPage() {
        if (loading || reachedEnd) return;
        loading = true;

        final int offset = loadedSessions.size();
        repository.loadHistory(PAGE_SIZE, offset, result -> {
            loading = false;
            if (binding == null) return;

            if (result == null || result.isEmpty()) {
                reachedEnd = true;
                updateEmptyState();
                return;
            }

            if (result.size() < PAGE_SIZE) reachedEnd = true;

            loadedSessions.addAll(result);
            // Truyền bản sao vì ListAdapter so sánh tham chiếu danh sách để biết có đổi
            adapter.submitList(new ArrayList<>(loadedSessions));
            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        binding.tvEmpty.setVisibility(loadedSessions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSessionClick(@NonNull ChargingSessionEntity session) {
        SessionDetailActivity.start(this, session.id);
    }
}
