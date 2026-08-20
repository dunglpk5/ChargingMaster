package com.dung.chargmagagement.model.ui;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.R;

import java.util.Arrays;
import java.util.List;

/**
 * Danh mục công cụ của app, gom về một chỗ.
 *
 * <p>Cùng một danh sách được dùng ở hai nơi: lưới rút gọn trên tab Công cụ và màn
 * "Tất cả chức năng". Để mỗi màn tự khai một bản là chắc chắn có ngày hai bên lệch
 * nhau — thêm công cụ mới ở màn này mà quên màn kia.
 *
 * <p>Các biểu tượng đều là ảnh PNG đã có sẵn màu nên truyền {@link ToolItem#NO_TINT}:
 * nhuộm đè lên chúng sẽ bẹt hết thành một mảng màu đặc.
 */
public final class ToolCatalog {

    private ToolCatalog() {
    }

    /**
     * Tám mục hay dùng nhất, hiện thẳng trên tab Công cụ.
     * Mục cuối là "Xem thêm", dẫn sang màn Tất cả chức năng.
     */
    @NonNull
    public static List<ToolItem> quickTools() {
        return Arrays.asList(
                item(ToolItem.Action.NO_ADS, R.drawable.ic_tool_no_ads, R.string.tools_no_ads),
                item(ToolItem.Action.CHARGE_ALARM,
                        R.drawable.ic_tool_charge_alarm, R.string.tools_charge_alarm),
                item(ToolItem.Action.CHARGE_DETECT,
                        R.drawable.ic_tool_charge_detection, R.string.tools_charge_detect),
                item(ToolItem.Action.CHARGE_HISTORY,
                        R.drawable.ic_tool_charge_history, R.string.tools_charge_history),
                item(ToolItem.Action.X_CHARGE,
                        R.drawable.ic_tool_x_charge, R.string.tools_x_charge),
                item(ToolItem.Action.DEVICE_INFO,
                        R.drawable.ic_tool_device_info, R.string.tools_device_info),
                item(ToolItem.Action.CPU_USAGE,
                        R.drawable.ic_tool_cpu_usage, R.string.tools_cpu_usage),
                item(ToolItem.Action.MORE, R.drawable.ic_tool_more, R.string.tools_more));
    }

    @NonNull
    public static List<ToolItem> vipTools() {
        return Arrays.asList(
                item(ToolItem.Action.NO_ADS, R.drawable.ic_tool_no_ads, R.string.tools_no_ads),
                item(ToolItem.Action.CHARGE_ALARM,
                        R.drawable.ic_tool_charge_alarm, R.string.tools_charge_alarm),
                item(ToolItem.Action.PRIORITY_SUPPORT,
                        R.drawable.ic_tool_priority_support, R.string.tools_priority_support),
                item(ToolItem.Action.CHARGE_HISTORY,
                        R.drawable.ic_tool_charge_history, R.string.tools_charge_history),
                item(ToolItem.Action.X_CHARGE,
                        R.drawable.ic_tool_x_charge, R.string.tools_x_charge),
                item(ToolItem.Action.MORE, R.drawable.ic_tool_more, R.string.tools_more));
    }

    @NonNull
    public static List<ToolItem> detectTools() {
        return Arrays.asList(
                item(ToolItem.Action.DEVICE_INFO,
                        R.drawable.ic_tool_device_info, R.string.tools_device_info),
                item(ToolItem.Action.CLEAN_NOTIFICATION,
                        R.drawable.ic_tool_clean_notifications, R.string.tools_clean_notification),
                item(ToolItem.Action.CLEAN_CLIPBOARD,
                        R.drawable.ic_tool_clipboard, R.string.tools_clean_clipboard),
                item(ToolItem.Action.PHONE_TEMPERATURE,
                        R.drawable.ic_tool_phone_temperature, R.string.tools_phone_temperature),
                item(ToolItem.Action.CHARGE_DETECT,
                        R.drawable.ic_tool_charge_detection, R.string.tools_charge_detect),
                item(ToolItem.Action.CPU_USAGE,
                        R.drawable.ic_tool_cpu_usage, R.string.tools_cpu_usage));
    }

    @NonNull
    public static List<ToolItem> generalTools() {
        return Arrays.asList(
                item(ToolItem.Action.MANAGE_APPS,
                        R.drawable.ic_tool_app_manager, R.string.tools_manage_apps),
                item(ToolItem.Action.SHORTCUT,
                        R.drawable.ic_tool_shortcut, R.string.tools_shortcut),
                item(ToolItem.Action.CLEAR_CACHE,
                        R.drawable.ic_tool_clear_cache, R.string.tools_clear_cache));
    }

    @NonNull
    private static ToolItem item(@NonNull ToolItem.Action action, int iconRes, int labelRes) {
        return new ToolItem(action, iconRes, labelRes, ToolItem.NO_TINT);
    }
}
