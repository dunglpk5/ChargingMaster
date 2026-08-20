package com.dung.chargmagagement.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.model.alarm.AlarmScheduleCalculator;
import com.dung.chargmagagement.model.alarm.AlarmSettings;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;

/**
 * Báo động sạc chạy bằng {@link AlarmManager}, <b>không cần service nền nào</b>.
 *
 * <p><b>Vì sao bỏ được tiến trình thường trú:</b> máy đang cắm sạc thì Doze không áp
 * dụng, nên một cái hẹn giờ nổ đúng hạn. Thay vì thức suốt để nhìn từng thay đổi của
 * pin, app ngủ tới lúc <i>ước tính</i> sắp chạm ngưỡng, dậy đọc mức pin từ sticky
 * intent, rồi hoặc báo hoặc hẹn tiếp. Nhờ vậy chức năng này không cần
 * {@code FOREGROUND_SERVICE_SPECIAL_USE}, cũng không có thông báo thường trú nào.
 *
 * <p><b>Trạng thái nằm ở SharedPreferences</b> chứ không trong bộ nhớ: giữa hai lần
 * hẹn, tiến trình của app thường đã bị thu hồi. Cờ "đã báo rồi" mà mất là mỗi lần
 * dậy lại kêu thêm một lần nữa.
 */
public final class ChargeAlarmScheduler {

    private static final String TAG = "ChargeAlarmScheduler";

    /** Action riêng của app, chỉ dùng cho PendingIntent của AlarmManager. */
    static final String ACTION_CHECK = "com.dung.chargmagagement.action.CHECK_CHARGE_ALARM";

    private static final int REQUEST_CODE = 3001;

    // ==== Trạng thái phiên, lưu qua SharedPreferences ====
    private static final String KEY_THRESHOLD_FIRED = "alarm_state_threshold_fired";
    private static final String KEY_FULL_FIRED = "alarm_state_full_fired";
    private static final String KEY_OVERHEAT_FIRED = "alarm_state_overheat_fired";
    private static final String KEY_LAST_PERCENT = "alarm_state_last_percent";
    private static final String KEY_LAST_TIME = "alarm_state_last_time";
    private static final String KEY_LAST_PLUGGED = "alarm_state_last_plugged";

    private ChargeAlarmScheduler() {
    }

    /**
     * Đọc mức pin hiện tại, phát cảnh báo nếu cần, rồi hẹn lần kiểm tra kế tiếp.
     *
     * <p>Gọi được từ mọi nơi: lúc người dùng đổi thiết lập, lúc cắm/rút sạc, lúc máy
     * vừa khởi động, và từ chính cái hẹn giờ trước đó.
     */
    public static void check(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        final PrefManager prefs = PrefManager.get(appContext);
        final AlarmSettings settings = AlarmSettings.load(prefs);

        if (!settings.hasAnyEnabled()) {
            cancel(appContext);
            return;
        }

        final Intent status = appContext.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) {
            Logger.e(TAG, "Không đọc được trạng thái pin", null);
            return;
        }

        final boolean plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        final int percent = extractPercent(status);
        final float celsius = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;

        // Đọc trước khi saveState ghi đè: schedule() cần mốc của lần kiểm tra *trước*
        // để suy ra tốc độ sạc
        final int previousPercent = prefs.getInt(KEY_LAST_PERCENT, -1);
        final long previousTime = prefs.getLong(KEY_LAST_TIME, 0L);

        final ChargeAlarmChecker checker = restoreChecker(prefs);

        // Cắm hay rút đều mở ra một phiên mới: mọi cảnh báo được phép kêu lại
        if (prefs.getBoolean(KEY_LAST_PLUGGED, false) != plugged) {
            checker.resetSession();
            if (!plugged) new ChargeAlarmNotifier(appContext).cancel();
        }

        // Chụp lại trạng thái trước khi kiểm tra: nếu cảnh báo không hiện được (chưa
        // có quyền thông báo, hoặc người dùng đang còn nhìn hộp thoại xin quyền) thì
        // phải trả cờ về như cũ. Không làm vậy thì checker đã đánh dấu "đã báo rồi",
        // và cảnh báo đó mất hẳn cho tới lần cắm sạc kế tiếp.
        final boolean hadThreshold = checker.isThresholdFired();
        final boolean hadFull = checker.isFullFired();
        final boolean hadOverheat = checker.isOverheatFired();

        final ChargeAlarmChecker.AlarmType type =
                checker.check(percent, plugged, celsius, settings);
        final boolean shown = new ChargeAlarmNotifier(appContext).notifyAlarm(type, percent);

        if (type != ChargeAlarmChecker.AlarmType.NONE && !shown) {
            Logger.d(TAG, "Không hiện được cảnh báo " + type + ", sẽ thử lại");
            checker.restore(hadThreshold, hadFull, hadOverheat);
        }

        saveState(prefs, checker, percent, plugged);
        schedule(appContext, settings, percent, plugged, previousPercent, previousTime);
    }

    /**
     * Hệ thống có cho phép hẹn giờ chính xác hay không.
     *
     * <p>Từ Android 12 đây là quyền riêng, và từ Android 13 mặc định <b>chưa</b> được
     * cấp. Thiếu nó thì hẹn giờ bị App Standby dồn lại: app ít mở có thể bị hoãn hàng
     * giờ, đúng triệu chứng "chỉ khi vào app mới thấy cảnh báo".
     */
    public static boolean canScheduleExact(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    /**
     * Người dùng vừa đổi thiết lập báo động: cho phép mọi cảnh báo kêu lại rồi kiểm
     * tra ngay.
     *
     * <p>Đổi thiết lập là một ý định mới, nên không được để cờ "đã báo rồi" của phiên
     * sạc đang chạy chặn lại. Nếu không có bước xoá cờ này thì người dùng đặt ngưỡng
     * 67 % lúc pin đã 67 %, cảnh báo bắn một lần rồi vì lý do nào đó không hiện được,
     * và họ phải rút sạc ra cắm lại mới thấy gì.
     */
    public static void onSettingsChanged(@NonNull Context context) {
        final PrefManager prefs = PrefManager.get(context.getApplicationContext());
        prefs.putBoolean(KEY_THRESHOLD_FIRED, false);
        prefs.putBoolean(KEY_FULL_FIRED, false);
        prefs.putBoolean(KEY_OVERHEAT_FIRED, false);

        check(context);
    }

    /** Huỷ cái hẹn đang treo và xoá trạng thái phiên. */
    public static void cancel(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        AlarmManager manager =
                (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

        PendingIntent pending = buildPendingIntent(appContext, false);
        if (manager != null && pending != null) manager.cancel(pending);

        PrefManager prefs = PrefManager.get(appContext);
        prefs.putInt(KEY_LAST_PERCENT, -1);
        prefs.putLong(KEY_LAST_TIME, 0L);
    }

    // ==================== Hẹn giờ ====================

    private static void schedule(@NonNull Context context, @NonNull AlarmSettings settings,
                                 int percent, boolean plugged,
                                 int previousPercent, long previousTime) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = buildPendingIntent(context, true);
        if (manager == null || pending == null) return;

        final long now = System.currentTimeMillis();

        if (!plugged) {
            // Rút sạc rồi thì ngưỡng % và pin đầy không thể tới; chỉ còn nhiệt độ
            if (!settings.isOverheatEnabled()) {
                manager.cancel(pending);
                return;
            }
            fire(manager, pending, AlarmScheduleCalculator.IDLE_TEMP_DELAY_MS,
                    canScheduleExact(context));
            return;
        }

        final long delay = AlarmScheduleCalculator.nextDelayMs(
                percent,
                targetPercent(settings),
                previousPercent,
                previousTime > 0L ? now - previousTime : 0L);

        fire(manager, pending, delay, canScheduleExact(context));
    }

    /**
     * Đặt hẹn giờ, chính xác nếu được phép.
     *
     * <p>Cả hai hàm đều là loại "allow while idle" nên xuyên qua Doze. Khác biệt nằm ở
     * App Standby: bản chính xác nổ đúng mốc, còn bản thường bị hệ thống dồn theo mức
     * độ người dùng hay mở app – với app mới cài và ít mở thì hoãn hàng giờ là bình
     * thường, nên báo động gần như chỉ chạy lúc người dùng vào app.
     *
     * <p>Mốc tính theo {@link SystemClock#elapsedRealtime()} chứ không theo giờ thật:
     * đồng hồ hệ thống có thể bị đổi (người dùng sửa tay, đổi múi giờ, giờ mùa hè) và
     * khi đó cái hẹn theo giờ thật bị lệch đúng bằng lượng đã đổi. Đồng hồ realtime
     * chạy đơn điệu nên không có rủi ro đó.
     *
     * @param delayMs còn bao lâu nữa thì nổ
     */
    private static void fire(@NonNull AlarmManager manager, @NonNull PendingIntent pending,
                             long delayMs, boolean exact) {
        final long triggerAt = SystemClock.elapsedRealtime() + delayMs;

        if (exact) {
            manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
        } else {
            manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
        }
    }

    /** Ngưỡng % gần nhất cần bắt được; 100 nếu chỉ bật cảnh báo pin đầy. */
    private static int targetPercent(@NonNull AlarmSettings settings) {
        if (settings.isThresholdEnabled()) return settings.getThresholdPercent();
        return 100;
    }

    @Nullable
    private static PendingIntent buildPendingIntent(@NonNull Context context, boolean create) {
        Intent intent = new Intent(context, ChargeAlarmReceiver.class).setAction(ACTION_CHECK);
        final int flags = PendingIntent.FLAG_IMMUTABLE
                | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    // ==================== Trạng thái phiên ====================

    @NonNull
    private static ChargeAlarmChecker restoreChecker(@NonNull PrefManager prefs) {
        ChargeAlarmChecker checker = new ChargeAlarmChecker();
        checker.restore(
                prefs.getBoolean(KEY_THRESHOLD_FIRED, false),
                prefs.getBoolean(KEY_FULL_FIRED, false),
                prefs.getBoolean(KEY_OVERHEAT_FIRED, false));
        return checker;
    }

    private static void saveState(@NonNull PrefManager prefs,
                                  @NonNull ChargeAlarmChecker checker,
                                  int percent, boolean plugged) {
        prefs.putBoolean(KEY_THRESHOLD_FIRED, checker.isThresholdFired());
        prefs.putBoolean(KEY_FULL_FIRED, checker.isFullFired());
        prefs.putBoolean(KEY_OVERHEAT_FIRED, checker.isOverheatFired());
        prefs.putInt(KEY_LAST_PERCENT, percent);
        prefs.putLong(KEY_LAST_TIME, System.currentTimeMillis());
        prefs.putBoolean(KEY_LAST_PLUGGED, plugged);
    }

    private static int extractPercent(@NonNull Intent intent) {
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 0;
    }
}
