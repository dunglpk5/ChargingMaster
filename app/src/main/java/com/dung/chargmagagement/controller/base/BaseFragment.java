package com.dung.chargmagagement.controller.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.PrefManager;

/**
 * Fragment gốc cho các tab trong ViewPager2.
 *
 * @param <VB> lớp ViewBinding tương ứng với layout của tab
 */
public abstract class BaseFragment<VB extends ViewBinding> extends Fragment {

    protected VB binding;
    protected AppExecutors executors;
    protected PrefManager prefs;

    @NonNull
    protected abstract VB onCreateBinding(@NonNull LayoutInflater inflater,
                                          @Nullable ViewGroup container);

    protected abstract void onViewReady(@Nullable Bundle savedInstanceState);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        executors = AppExecutors.get();
        prefs = PrefManager.get(requireContext());
        binding = onCreateBinding(inflater, container);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        onViewReady(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // View của Fragment sống ngắn hơn Fragment nên bắt buộc giải phóng ở đây
        binding = null;
    }
}
