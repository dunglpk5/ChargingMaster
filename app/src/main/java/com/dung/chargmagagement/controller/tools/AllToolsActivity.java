package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.adapter.ToolAdapter;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityAllToolsBinding;
import com.dung.chargmagagement.model.ui.ToolCatalog;
import com.dung.chargmagagement.model.ui.ToolItem;

import java.util.List;

/**
 * Màn "Tất cả chức năng": mọi công cụ của app, chia ba nhóm VIP / Phát hiện / Công cụ.
 *
 * <p>Tab Công cụ chỉ hiện tám mục hay dùng để màn chính gọn gàng; phần còn lại nằm ở
 * đây, mở từ ô "Xem thêm".
 */
public class AllToolsActivity extends BaseActivity<ActivityAllToolsBinding>
        implements ToolAdapter.OnToolClickListener {

    private static final int GRID_SPAN_COUNT = 3;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, AllToolsActivity.class));
    }

    @NonNull
    @Override
    protected ActivityAllToolsBinding onCreateBinding() {
        return ActivityAllToolsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_all_title);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        setupGrid(binding.rvVipTools, ToolCatalog.vipTools());
        setupGrid(binding.rvDetectTools, ToolCatalog.detectTools());
        setupGrid(binding.rvGeneralTools, ToolCatalog.generalTools());
    }

    private void setupGrid(@NonNull RecyclerView recyclerView, @NonNull List<ToolItem> items) {
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        recyclerView.setHasFixedSize(true);

        ToolAdapter adapter = new ToolAdapter(items);
        adapter.setOnToolClickListener(this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onToolClick(@NonNull ToolItem.Action action) {
        // "Xem thêm" ở đây chính là màn đang mở, bấm lại chỉ chồng thêm một bản sao
        if (action == ToolItem.Action.MORE) {
            finish();
            return;
        }
        ToolLauncher.launch(this, action);
    }
}
