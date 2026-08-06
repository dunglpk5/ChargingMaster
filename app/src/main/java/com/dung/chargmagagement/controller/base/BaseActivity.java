package com.dung.chargmagagement.controller.base;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.PrefManager;

/**
 * Activity gốc theo mô hình MVC: lớp này đóng vai trò View + Controller mỏng,
 * chỉ lo vòng đời và gắn view; toàn bộ logic nghiệp vụ nằm ở tầng model.
 *
 * @param <VB> lớp ViewBinding tương ứng với layout của màn hình
 */
public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    protected VB binding;
    protected AppExecutors executors;
    protected PrefManager prefs;

    /** Trả về ViewBinding đã inflate của màn hình. */
    @NonNull
    protected abstract VB onCreateBinding();

    /** Nơi ánh xạ dữ liệu và gắn sự kiện, gọi sau khi setContentView. */
    protected abstract void onViewReady(@Nullable Bundle savedInstanceState);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executors = AppExecutors.get();
        prefs = PrefManager.get(this);

        binding = onCreateBinding();
        setContentView(binding.getRoot());

        onViewReady(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tránh giữ tham chiếu view sau khi Activity bị huỷ (rò rỉ bộ nhớ)
        binding = null;
    }
}
