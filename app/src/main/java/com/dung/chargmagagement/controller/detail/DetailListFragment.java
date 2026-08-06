package com.dung.chargmagagement.controller.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dung.chargmagagement.controller.adapter.DetailRowAdapter;
import com.dung.chargmagagement.controller.base.BaseFragment;
import com.dung.chargmagagement.databinding.FragmentDetailListBinding;
import com.dung.chargmagagement.model.device.DetailSection;
import com.dung.chargmagagement.model.device.DeviceDetailProvider;

/**
 * Fragment dùng chung cho cả 6 tab của màn Thông tin thiết bị.
 *
 * <p>Sáu tab chỉ khác nhau ở nguồn dữ liệu, còn giao diện đều là danh sách
 * "nhãn – giá trị", nên một lớp nhận tham số là đủ; viết 6 lớp riêng chỉ tạo ra
 * năm bản sao chép của cùng một đoạn mã.
 *
 * <p>Dữ liệu chỉ nạp <b>một lần</b> khi tab được tạo. Thông tin phần cứng không
 * đổi trong lúc chạy nên không cần làm mới; chỉ tab CPU có xung nhịp biến động
 * nhưng đó là số liệu tham khảo, không đáng để lấy mẫu liên tục và hao pin.
 */
public class DetailListFragment extends BaseFragment<FragmentDetailListBinding> {

    private static final String ARG_SECTION = "section";

    private DetailRowAdapter adapter;

    /** Tạo tab cho một section cụ thể. */
    @NonNull
    public static DetailListFragment newInstance(@NonNull DetailSection section) {
        DetailListFragment fragment = new DetailListFragment();
        Bundle args = new Bundle();
        // Lưu tên enum thay vì ordinal: thêm bớt tab sau này không làm sai dữ liệu cũ
        args.putString(ARG_SECTION, section.name());
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    private DetailSection getSection() {
        Bundle args = getArguments();
        if (args == null) return DetailSection.DEVICE;
        try {
            return DetailSection.valueOf(args.getString(ARG_SECTION, DetailSection.DEVICE.name()));
        } catch (IllegalArgumentException e) {
            return DetailSection.DEVICE;
        }
    }

    @NonNull
    @Override
    protected FragmentDetailListBinding onCreateBinding(@NonNull LayoutInflater inflater,
                                                        @Nullable ViewGroup container) {
        return FragmentDetailListBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        adapter = new DetailRowAdapter();
        binding.rvDetails.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDetails.setAdapter(adapter);

        loadData();
    }

    /** Đọc thông tin ở thread nền: phần CPU và mạng đều có thao tác chậm. */
    private void loadData() {
        final DeviceDetailProvider provider = new DeviceDetailProvider(requireContext());
        final DetailSection section = getSection();

        executors.execute(() -> provider.build(section), items -> {
            if (binding == null) return;

            adapter.submitList(items);
            final boolean empty = items == null || items.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
    }
}
