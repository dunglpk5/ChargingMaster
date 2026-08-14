package com.dung.chargmagagement.model.clean;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.ui.ClipItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc và xoá nội dung bộ nhớ tạm.
 *
 * <p>Android chỉ giữ <b>một</b> bộ nhớ tạm tại một thời điểm, không có lịch sử.
 * Nhưng một lần sao chép có thể chứa nhiều mục ({@code ClipData} nhiều item), nên
 * màn hình vẫn là một danh sách chứ không phải một dòng.
 *
 * <p>Từ Android 10, chỉ ứng dụng đang hiển thị mới đọc được bộ nhớ tạm. Mọi hàm ở
 * đây vì thế phải gọi từ Activity đang mở, gọi từ nền sẽ luôn trả về rỗng.
 */
public final class ClipboardCleaner {

    private static final String TAG = "ClipboardCleaner";

    private ClipboardCleaner() {
    }

    /** Các mục đang nằm trong bộ nhớ tạm; rỗng nếu không có gì hoặc không đọc được. */
    @NonNull
    public static List<ClipItem> list(@NonNull Context context) {
        List<ClipItem> items = new ArrayList<>();

        final ClipboardManager manager = getManager(context);
        if (manager == null || !manager.hasPrimaryClip()) return items;

        final ClipData clip = manager.getPrimaryClip();
        if (clip == null) return items;

        final ClipDescription description = clip.getDescription();
        final String label = description == null || description.getLabel() == null
                ? ""
                : description.getLabel().toString();
        final long timestamp = readTimestamp(description);

        for (int i = 0; i < clip.getItemCount(); i++) {
            final CharSequence text = clip.getItemAt(i).coerceToText(context);
            if (text == null || text.length() == 0) continue;

            items.add(new ClipItem(i, label, text.toString(), timestamp));
        }
        return items;
    }

    /** Xoá sạch bộ nhớ tạm. */
    public static boolean clearAll(@NonNull Context context) {
        final ClipboardManager manager = getManager(context);
        if (manager == null) return false;

        try {
            // clearPrimaryClip có từ API 28; máy cũ hơn phải ghi đè bằng chuỗi rỗng
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip();
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""));
            }
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Không xoá được bộ nhớ tạm", e);
            return false;
        }
    }

    /**
     * Bỏ một mục khỏi bộ nhớ tạm.
     *
     * <p>Android không có API xoá lẻ, nên phải dựng lại bộ nhớ tạm từ những mục
     * còn lại. Bỏ mục cuối cùng thì xoá sạch luôn.
     */
    public static boolean remove(@NonNull Context context, @NonNull List<ClipItem> items,
                                 int index) {
        if (items.size() <= 1) return clearAll(context);

        final ClipboardManager manager = getManager(context);
        if (manager == null) return false;

        try {
            ClipData rebuilt = null;
            for (ClipItem item : items) {
                if (item.index == index) continue;

                if (rebuilt == null) {
                    rebuilt = ClipData.newPlainText(item.label, item.text);
                } else {
                    rebuilt.addItem(new ClipData.Item(item.text));
                }
            }
            if (rebuilt == null) return clearAll(context);

            manager.setPrimaryClip(rebuilt);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Không bỏ được mục khỏi bộ nhớ tạm", e);
            return false;
        }
    }

    /** Thời điểm sao chép; API 26 mới có, và một số ROM vẫn trả 0. */
    private static long readTimestamp(@Nullable ClipDescription description) {
        if (description == null) return 0L;

        try {
            return description.getTimestamp();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Nullable
    private static ClipboardManager getManager(@NonNull Context context) {
        return (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }
}
