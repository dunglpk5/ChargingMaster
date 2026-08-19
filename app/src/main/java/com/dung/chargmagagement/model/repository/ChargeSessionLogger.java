package com.dung.chargmagagement.model.repository;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.battery.ChargeCounter;
import com.dung.chargmagagement.model.battery.PlugType;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.stats.UsageCalculator;

/**
 * Ghi phiên sạc chỉ bằng hai mốc cắm và rút, <b>không cần dịch vụ nền nào</b>.
 *
 * <p>{@code ACTION_POWER_CONNECTED} và {@code ACTION_POWER_DISCONNECTED} nằm trong số
 * ít broadcast vẫn được gửi tới receiver khai trong Manifest, nên hai mốc này bắt được
 * kể cả khi app đã đóng hoàn toàn. Mà một phiên sạc thật ra chỉ cần đúng hai mốc đó:
 * thời điểm, mức pin, loại nguồn ở đầu và cuối. Lượng điện đã nạp lấy từ hiệu số bộ
 * đếm cu-lông của phần cứng ({@link ChargeCounter}) chứ không cần lấy mẫu liên tục.
 *
 * <p>Nhờ vậy các số liệu "số phiên sạc", "% nạp trung bình", "lần sạc cuối" và "độ
 * chai pin" vẫn hoạt động khi người dùng tắt ghi lịch sử pin — thứ duy nhất mất đi là
 * biểu đồ mức pin dày theo ngày.
 *
 * <p><b>Chỉ một nơi được ghi.</b> Khi {@code BatteryLogService} đang bật thì
 * {@link SessionRecorder} đã lo việc này rồi, nên lớp này đứng ngoài — hai bên cùng
 * ghi sẽ sinh ra hai bản ghi cho cùng một lần sạc và mọi con số bị đếm hai lần. Việc
 * chọn bên nào do {@code ChargingStateReceiver} quyết định.
 */
public final class ChargeSessionLogger {

    private static final String TAG = "ChargeSessionLogger";

    private ChargeSessionLogger() {
    }

    /** Mở một phiên sạc mới. Gọi khi nhận {@code ACTION_POWER_CONNECTED}. */
    public static void onPlugged(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        final long counter = ChargeCounter.readUah(appContext);
        final Intent status = readBatteryStatus(appContext);
        if (status == null) return;

        final int percent = extractPercent(status);
        final PlugType plugType = PlugType.fromSystemFlag(
                status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
        final float celsius = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        final long now = System.currentTimeMillis();

        AppExecutors.get().disk().execute(() -> {
            try {
                final BatteryRepository repository = BatteryRepository.get(appContext);

                // Đã có phiên đang mở thì không mở thêm: có thể service vừa mở trước,
                // hoặc lần rút sạc trước đó chưa kịp đóng
                if (repository.findOngoingSessionSync() != null) return;

                ChargingSessionEntity session = new ChargingSessionEntity();
                session.startTime = now;
                session.startPercent = percent;
                session.endPercent = percent;
                session.plugType = plugType.name();
                session.maxTemperature = celsius;
                session.chargeCounterStart = counter;
                session.id = repository.insertSessionSync(session);

                Logger.d(TAG, "Mở phiên sạc #" + session.id + " tại " + percent + "%");
            } catch (Exception e) {
                Logger.e(TAG, "Không mở được phiên sạc", e);
            }
        });
    }

    /** Đóng phiên sạc đang mở. Gọi khi nhận {@code ACTION_POWER_DISCONNECTED}. */
    public static void onUnplugged(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        final long counter = ChargeCounter.readUah(appContext);
        final Intent status = readBatteryStatus(appContext);
        final int percent = status == null ? -1 : extractPercent(status);
        final long now = System.currentTimeMillis();

        AppExecutors.get().disk().execute(() -> {
            try {
                final BatteryRepository repository = BatteryRepository.get(appContext);
                final ChargingSessionEntity session = repository.findOngoingSessionSync();
                if (session == null) return;

                session.endTime = now;
                if (percent >= 0) session.endPercent = percent;
                session.chargedMah = UsageCalculator.chargedMah(
                        session.chargeCounterStart, counter,
                        session.avgCurrentMa, session.getDurationMs());
                repository.updateSessionSync(session);

                Logger.d(TAG, "Đóng phiên sạc #" + session.id
                        + ": +" + session.getGainedPercent() + "%, "
                        + Math.round(session.chargedMah) + " mAh");
            } catch (Exception e) {
                Logger.e(TAG, "Không đóng được phiên sạc", e);
            }
        });
    }

    /** Trạng thái pin hiện tại, đọc từ sticky intent nên không phải chờ broadcast mới. */
    @Nullable
    private static Intent readBatteryStatus(@NonNull Context context) {
        Intent status = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) Logger.e(TAG, "Không đọc được trạng thái pin", null);
        return status;
    }

    private static int extractPercent(@NonNull Intent intent) {
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 0;
    }
}
