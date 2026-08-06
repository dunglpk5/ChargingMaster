package com.dung.chargmagagement.controller.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.LocaleManager;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityLanguageBinding;
import com.dung.chargmagagement.databinding.ItemLanguageBinding;

/**
 * Màn chọn ngôn ngữ.
 *
 * <p>Sau khi chọn, {@link LocaleManager} gọi API per-app language của AndroidX;
 * hệ thống tự tạo lại các Activity đang mở nên không cần {@code recreate()} thủ
 * công. Màn này chỉ việc đóng lại để người dùng thấy ngay màn trước đã đổi ngôn ngữ.
 */
public class LanguageActivity extends BaseActivity<ActivityLanguageBinding> {

    /** Danh sách ngôn ngữ hỗ trợ; thêm ngôn ngữ mới chỉ cần thêm một dòng ở đây. */
    private static final String[] LANGUAGE_TAGS = {
            LocaleManager.LANG_SYSTEM,
            LocaleManager.LANG_VI,
            LocaleManager.LANG_EN
    };

    private static final int[] LANGUAGE_LABELS = {
            R.string.language_system,
            R.string.language_vi,
            R.string.language_en
    };

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, LanguageActivity.class));
    }

    @NonNull
    @Override
    protected ActivityLanguageBinding onCreateBinding() {
        return ActivityLanguageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.language_title);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        buildOptions();
    }

    private void buildOptions() {
        final String current = LocaleManager.getCurrentLanguage(prefs);
        final LayoutInflater inflater = getLayoutInflater();

        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            final String tag = LANGUAGE_TAGS[i];

            ItemLanguageBinding item = ItemLanguageBinding.inflate(
                    inflater, binding.container, false);
            item.tvLanguage.setText(LANGUAGE_LABELS[i]);
            item.radioSelected.setChecked(tag.equals(current));

            // Cả dòng và nút tròn đều bấm được, người dùng không phải nhắm vào nút nhỏ
            View.OnClickListener listener = v -> applyLanguage(tag);
            item.getRoot().setOnClickListener(listener);
            item.radioSelected.setOnClickListener(listener);

            binding.container.addView(item.getRoot());
        }
    }

    private void applyLanguage(@NonNull String languageTag) {
        if (languageTag.equals(LocaleManager.getCurrentLanguage(prefs))) {
            finish();
            return;
        }

        // Bỏ chọn tất cả rồi để hệ thống tạo lại màn với ngôn ngữ mới
        uncheckAll();
        LocaleManager.setLanguage(prefs, languageTag);
        finish();
    }

    private void uncheckAll() {
        final LinearLayout container = binding.container;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            RadioButton radio = child.findViewById(R.id.radioSelected);
            if (radio != null) radio.setChecked(false);
        }
    }
}
