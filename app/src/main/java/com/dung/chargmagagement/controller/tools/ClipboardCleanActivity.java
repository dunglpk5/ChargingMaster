package com.dung.chargmagagement.controller.tools;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.adapter.ClipAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.databinding.ActivityClipboardCleanBinding;
import com.dung.chargmagagement.model.ads.AdManager;
import com.dung.chargmagagement.model.clean.ClipboardCleaner;
import com.dung.chargmagagement.model.ui.ClipItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn Dọn dẹp clipboard, dựng theo đúng kiểu màn Dọn dẹp thông báo.
 *
 * <p>Không cần xin quyền gì: từ Android 10 chỉ ứng dụng đang hiển thị mới đọc được
 * bộ nhớ tạm, và màn này luôn đang hiển thị khi nó đọc.
 */
public class ClipboardCleanActivity extends BaseActivity<ActivityClipboardCleanBinding>
        implements ClipboardManager.OnPrimaryClipChangedListener {

    private ClipAdapter adapter;

    /** Bản chụp danh sách hiện tại, cần khi dựng lại bộ nhớ tạm để bỏ một mục. */
    private List<ClipItem> items = new ArrayList<>();

    /** Đã nạp quảng cáo chưa; chỉ nạp một lần cho cả vòng đời màn hình. */
    private boolean adLoaded;

    @Nullable
    private ClipboardManager clipboardManager;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, ClipboardCleanActivity.class));
    }

    @NonNull
    @Override
    protected ActivityClipboardCleanBinding onCreateBinding() {
        return ActivityClipboardCleanBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCleanAll.setOnClickListener(v -> cleanAll());
        binding.btnRefresh.setOnClickListener(v -> refresh());
        binding.btnDetect.setOnClickListener(v -> CheckPowerActivity.start(this));

        adapter = new ClipAdapter(this::removeOne);
        binding.rvClips.setLayoutManager(new LinearLayoutManager(this));
        binding.rvClips.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (clipboardManager != null) clipboardManager.addPrimaryClipChangedListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (clipboardManager != null) clipboardManager.removePrimaryClipChangedListener(this);
    }

    /** Người dùng sao chép thứ gì đó ở nơi khác: dựng lại danh sách ngay. */
    @Override
    public void onPrimaryClipChanged() {
        if (binding == null) return;
        refresh();
    }

    private void refresh() {
        items = ClipboardCleaner.list(this);
        adapter.submit(items);
        showList(!items.isEmpty(), items.size());
    }

    /**
     * Chuyển giữa hai trạng thái của màn hình.
     *
     * <p>Bộ nhớ tạm rỗng thì đây không còn là danh sách nữa mà là một màn thông báo
     * "đã sạch": nền teal, chổi ở giữa, gợi ý việc tiếp theo. Nút dọn và quảng cáo
     * biến mất hẳn thay vì chỉ bị làm mờ.
     */
    private void showList(boolean hasItems, int count) {
        binding.rvClips.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.emptyContainer.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        binding.btnCleanAll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.adContainer.setVisibility(hasItems ? View.VISIBLE : View.GONE);

        binding.tvToolbarTitle.setText(hasItems
                ? getString(R.string.clipboard_title_count, count)
                : getString(R.string.app_name));

        // Nạp một lần thôi: gọi lại mỗi lần danh sách đổi sẽ dựng banner mới liên tục
        if (hasItems && !adLoaded) {
            adLoaded = true;
            AdManager.loadBanner(this, binding.adContainer);
        }
    }

    // ==================== Thao tác xoá ====================

    private void cleanAll() {
        if (ClipboardCleaner.clearAll(this)) {
            Toast.makeText(this, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    /** Bấm vào một mục là bỏ riêng nó khỏi bộ nhớ tạm. */
    private void removeOne(@NonNull ClipItem item) {
        ClipboardCleaner.remove(this, items, item.index);
        refresh();
    }
}
