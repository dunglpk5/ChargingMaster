package com.dung.chargmagagement.controller.tools;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityStorageCleanBinding;
import com.dung.chargmagagement.databinding.ItemJunkGroupBinding;
import com.dung.chargmagagement.model.clean.JunkCategory;
import com.dung.chargmagagement.model.clean.JunkCleaner;
import com.dung.chargmagagement.model.clean.JunkFile;
import com.dung.chargmagagement.model.clean.JunkGroup;
import com.dung.chargmagagement.model.clean.JunkScanner;
import com.dung.chargmagagement.model.clean.StoragePermission;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Màn Dọn dẹp bộ nhớ, mở từ nút "Quét" ở thẻ Bộ nhớ trong tab Công cụ.
 *
 * <p>Quyền quản lý toàn bộ tệp là điều kiện bắt buộc và màn hình <b>không quét</b>
 * khi chưa có. Quét nửa vời sẽ báo vài trăm KB trong khi máy còn hàng GB rác, rồi
 * nút Dọn dẹp bấm vào lại không xoá nổi gì – con số sai còn tệ hơn không có số.
 */
public class StorageCleanActivity extends BaseActivity<ActivityStorageCleanBinding> {

    /** Tiến trình vòng tròn khi mới bắt đầu, để người dùng thấy máy đang chạy. */
    private static final int PROGRESS_STARTED = 12;

    /** Ước lượng số tệp của một lượt quét đầy đủ, dùng để quy ra phần trăm. */
    private static final int ESTIMATED_TOTAL_FILES = 12_000;

    private final Map<JunkCategory, JunkGroup> groups = new EnumMap<>(JunkCategory.class);
    private final List<ItemJunkGroupBinding> rows = new ArrayList<>();
    private final JunkScanner scanner = new JunkScanner();

    private boolean scanning;
    private boolean cleaning;

    private ActivityResultLauncher<String[]> legacyPermissionLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, StorageCleanActivity.class));
    }

    @NonNull
    @Override
    protected ActivityStorageCleanBinding onCreateBinding() {
        return ActivityStorageCleanBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        registerPermissionLaunchers();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnGrantPermission.setOnClickListener(v -> requestStorageAccess());
        binding.btnClean.setOnClickListener(v -> confirmClean());

        buildGroupRows();
        applyAccessState();

        // Người dùng bấm "Quét" là đã tỏ rõ ý định, nên xin quyền ngay thay vì
        // bắt họ bấm thêm một nút nữa mới thấy hộp thoại
        if (!StoragePermission.hasAccess(this)) {
            requestStorageAccess();
        } else {
            startScan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quyền có thể được bật ở màn Cài đặt rồi quay lại bằng nút Home, khi đó
        // không có kết quả trả về nào để bắt
        applyAccessState();
        if (StoragePermission.hasAccess(this) && !scanning && !cleaning && !hasResult()) {
            startScan();
        }
    }

    @Override
    protected void onDestroy() {
        // Luồng quét đọc hàng nghìn tệp; phải dừng hẳn kẻo nó chạy tiếp sau khi
        // người dùng đã rời màn hình
        scanner.cancel();
        super.onDestroy();
    }

    // ==================== Quyền truy cập bộ nhớ ====================

    private void registerPermissionLaunchers() {
        legacyPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    applyAccessState();
                    if (StoragePermission.hasAccess(this)) startScan();
                });

        // Màn Cài đặt không trả kết quả gì, phải tự kiểm tra lại khi quay về
        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    applyAccessState();
                    if (StoragePermission.hasAccess(this)) startScan();
                });
    }

    private void requestStorageAccess() {
        if (!StoragePermission.needsSettingsScreen()) {
            legacyPermissionLauncher.launch(StoragePermission.legacyPermissions());
            return;
        }

        try {
            settingsLauncher.launch(StoragePermission.buildSettingsIntent(this));
        } catch (ActivityNotFoundException e) {
            try {
                settingsLauncher.launch(StoragePermission.buildFallbackSettingsIntent());
            } catch (ActivityNotFoundException fallbackFailure) {
                Toast.makeText(this, R.string.clean_permission_unavailable,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Khoá hay mở toàn bộ màn hình theo tình trạng quyền.
     *
     * <p>Thiếu quyền thì danh sách nhóm bị làm mờ và không bấm được, vòng tiến
     * trình về 0, nút Dọn dẹp tắt. Người dùng nhìn thấy màn hình sẽ làm được gì
     * cho họ, nhưng không có cách nào chạy nó với dữ liệu sai.
     */
    private void applyAccessState() {
        final boolean granted = StoragePermission.hasAccess(this);

        binding.cardPermission.setVisibility(granted ? View.GONE : View.VISIBLE);
        binding.groupRows.setAlpha(granted ? 1f : 0.4f);

        if (granted) return;

        binding.ringScan.setPercent(0);
        binding.tvFoundSize.setText(R.string.value_placeholder);
        binding.tvScanState.setText(R.string.clean_permission_required);
        binding.btnClean.setEnabled(false);
        binding.btnClean.setAlpha(0.5f);
    }

    /** Đã có kết quả quét trong tay hay chưa. */
    private boolean hasResult() {
        for (JunkGroup group : groups.values()) {
            if (!group.isEmpty()) return true;
        }
        return false;
    }

    // ==================== Danh sách nhóm ====================

    private void buildGroupRows() {
        final LayoutInflater inflater = getLayoutInflater();

        for (JunkCategory category : JunkCategory.values()) {
            final JunkGroup group = new JunkGroup(category);
            groups.put(category, group);

            ItemJunkGroupBinding row =
                    ItemJunkGroupBinding.inflate(inflater, binding.groupRows, false);
            row.ivGroupIcon.setImageResource(category.getIconRes());
            row.tvGroupLabel.setText(category.getLabelRes());
            row.tvGroupSize.setText(R.string.value_placeholder);
            row.cbGroup.setChecked(group.isSelected());

            // Nhóm chọn theo tệp không dùng ô tick mà mở danh sách; mũi tên báo
            // cho người dùng biết bấm vào sẽ mở ra một màn khác
            row.cbGroup.setVisibility(
                    category.isPerFileSelection() ? View.GONE : View.VISIBLE);
            row.ivGroupChevron.setVisibility(
                    category.isPerFileSelection() ? View.VISIBLE : View.GONE);

            row.getRoot().setOnClickListener(v -> onGroupClicked(group, row));

            binding.groupRows.addView(row.getRoot());
            rows.add(row);

            if (category.ordinal() < JunkCategory.values().length - 1) {
                binding.groupRows.addView(newDivider());
            }
        }
    }

    @NonNull
    private View newDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.divider_height)));
        divider.setBackgroundResource(R.color.divider);
        return divider;
    }

    private void onGroupClicked(@NonNull JunkGroup group, @NonNull ItemJunkGroupBinding row) {
        if (scanning || cleaning || group.isEmpty()) return;
        if (!StoragePermission.hasAccess(this)) return;

        if (group.category.isPerFileSelection()) {
            showFilePicker(group, row);
            return;
        }

        group.setSelected(!group.isSelected());
        row.cbGroup.setChecked(group.isSelected());
        updateSelectionSummary();
    }

    /**
     * Cho chọn từng tệp trong nhóm APK cũ và Tệp tin lớn.
     *
     * <p>Chỉ ghi lựa chọn vào model khi người dùng bấm Xong: bấm ra ngoài hoặc
     * bấm Huỷ phải bỏ hết những gì vừa tick, không được lưu nửa vời.
     */
    private void showFilePicker(@NonNull JunkGroup group, @NonNull ItemJunkGroupBinding row) {
        final List<JunkFile> files = group.getFiles();

        final CharSequence[] labels = new CharSequence[files.size()];
        final boolean[] checked = new boolean[files.size()];
        for (int i = 0; i < files.size(); i++) {
            final JunkFile file = files.get(i);
            labels[i] = getString(R.string.clean_file_entry,
                    file.getName(), FormatUtils.formatBytes(file.sizeBytes));
            checked[i] = file.isSelected();
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clean_pick_files,
                        getString(group.category.getLabelRes()), files.size()))
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    for (int i = 0; i < files.size(); i++) {
                        files.get(i).setSelected(checked[i]);
                    }
                    bindGroupRow(group, row);
                    updateSelectionSummary();
                })
                .show();
    }

    private void bindGroupSizes() {
        for (int i = 0; i < rows.size(); i++) {
            final JunkGroup group = groups.get(JunkCategory.values()[i]);
            if (group != null) bindGroupRow(group, rows.get(i));
        }
    }

    private void bindGroupRow(@NonNull JunkGroup group, @NonNull ItemJunkGroupBinding row) {
        // Nhóm chọn theo tệp hiện dung lượng đang chọn kèm số tệp; con số này thay
        // đổi theo lựa chọn nên phải nói rõ "3/12 tệp" chứ không chỉ một dung lượng
        if (group.category.isPerFileSelection() && group.hasSelection()) {
            row.tvGroupSize.setText(FormatUtils.formatBytes(group.getSelectedBytes()));
            row.tvGroupCount.setText(getString(R.string.clean_file_count,
                    group.getSelectedCount(), group.getFiles().size()));
            row.tvGroupCount.setVisibility(View.VISIBLE);
        } else {
            row.tvGroupSize.setText(FormatUtils.formatBytes(group.getTotalBytes()));
            row.tvGroupCount.setVisibility(View.GONE);
        }

        // Nhóm rỗng thì bỏ tick và mờ đi: không có gì để dọn
        row.cbGroup.setEnabled(!group.isEmpty());
        row.cbGroup.setChecked(!group.isEmpty() && group.isSelected());
        row.ivGroupChevron.setAlpha(group.isEmpty() ? 0.3f : 1f);
        row.getRoot().setAlpha(group.isEmpty() ? 0.5f : 1f);
    }

    private void updateSelectionSummary() {
        long selected = 0L;
        for (JunkGroup group : groups.values()) {
            selected += group.getSelectedBytes();
        }

        binding.tvSelectedSize.setText(getString(R.string.clean_selected,
                FormatUtils.formatBytes(selected)));

        final boolean canClean = selected > 0 && !scanning && !cleaning;
        binding.btnClean.setEnabled(canClean);
        binding.btnClean.setAlpha(canClean ? 1f : 0.5f);
    }

    // ==================== Quét ====================

    private void startScan() {
        if (scanning) return;

        // Cửa chặn duy nhất: không có quyền thì không quét, không có con số nào
        if (!StoragePermission.hasAccess(this)) {
            applyAccessState();
            return;
        }

        scanning = true;
        scanner.reset();

        binding.ringScan.setPercent(PROGRESS_STARTED);
        binding.tvScanState.setText(R.string.clean_scanning);
        binding.tvFoundSize.setText(R.string.value_placeholder);
        updateSelectionSummary();

        executors.execute(this::runScan, result -> {
            if (binding == null || result == null) return;

            groups.clear();
            groups.putAll(result);

            scanning = false;
            binding.ringScan.setPercent(100);
            binding.tvScanState.setText(R.string.clean_scan_done);
            binding.tvFoundSize.setText(FormatUtils.formatBytes(totalFound()));

            bindGroupSizes();
            updateSelectionSummary();
        });
    }

    @NonNull
    private Map<JunkCategory, JunkGroup> runScan() {
        return scanner.scan(this, (scannedFiles, foundBytes) ->
                executors.runOnMain(() -> publishProgress(scannedFiles, foundBytes)));
    }

    /**
     * Cập nhật vòng tiến trình trong lúc quét.
     *
     * <p>Không biết trước tổng số tệp nên phần trăm là ước lượng và bị chặn ở 95:
     * vòng tròn chạy đủ 100 rồi mà máy vẫn còn quét thì người dùng tưởng bị treo.
     */
    private void publishProgress(int scannedFiles, long foundBytes) {
        if (binding == null || !scanning) return;

        final int percent = Math.min(95, PROGRESS_STARTED
                + scannedFiles * (95 - PROGRESS_STARTED) / ESTIMATED_TOTAL_FILES);
        binding.ringScan.setPercent(percent);
        binding.tvFoundSize.setText(FormatUtils.formatBytes(foundBytes));
    }

    private long totalFound() {
        long total = 0L;
        for (JunkGroup group : groups.values()) {
            total += group.getTotalBytes();
        }
        return total;
    }

    // ==================== Dọn dẹp ====================

    /**
     * Xoá tệp là việc không hoàn tác được, nên luôn hỏi lại và nêu rõ sẽ xoá
     * những nhóm nào, tổng bao nhiêu.
     */
    private void confirmClean() {
        if (scanning || cleaning || !StoragePermission.hasAccess(this)) return;

        final List<String> names = new ArrayList<>();
        long selected = 0L;
        for (JunkGroup group : groups.values()) {
            if (!group.hasSelection()) continue;

            names.add(getString(group.category.getLabelRes()));
            selected += group.getSelectedBytes();
        }
        if (names.isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.clean_confirm_title)
                .setMessage(getString(R.string.clean_confirm_message,
                        FormatUtils.formatBytes(selected),
                        android.text.TextUtils.join(", ", names)))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.clean_action, (dialog, which) -> runClean())
                .show();
    }

    private void runClean() {
        cleaning = true;
        binding.tvScanState.setText(R.string.clean_cleaning);
        updateSelectionSummary();

        executors.execute(() -> JunkCleaner.clean(new ArrayList<>(groups.values())), freed -> {
            if (binding == null) return;

            cleaning = false;
            Toast.makeText(this, getString(R.string.clean_freed,
                    FormatUtils.formatBytes(freed == null ? 0L : freed)),
                    Toast.LENGTH_LONG).show();

            // Quét lại để con số phản ánh đúng những gì thực sự xoá được: một số
            // tệp có thể bị hệ thống hoặc ứng dụng khác giữ và không xoá nổi
            startScan();
        });
    }
}
