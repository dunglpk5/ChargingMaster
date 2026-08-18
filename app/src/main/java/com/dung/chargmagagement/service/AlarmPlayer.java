package com.dung.chargmagagement.service;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Phát một tiếng chuông ngắn kèm rung khi báo động sạc.
 *
 * <p>Dùng <b>âm báo thông báo</b> chứ không phải nhạc chuông báo thức: cảnh báo
 * sạc là lời nhắc "ra rút sạc đi", không phải thứ để đánh thức người đang ngủ.
 * Nhạc báo thức thường dài, to và dồn dập — nghe hoảng chứ không hữu ích.
 *
 * <p>Kéo theo đó, {@link AudioAttributes#USAGE_NOTIFICATION} khiến âm báo đi theo
 * âm lượng thông báo. Máy để im lặng thì không kêu, đúng như người dùng mong đợi
 * ở một lời nhắc; nếu dùng USAGE_ALARM thì nó kêu bất chấp chế độ im lặng.
 *
 * <p><b>Kêu một lần rồi tự tắt</b> sau {@link #MAX_DURATION_MS}. Kêu dai dẳng tới
 * khi người dùng bấm tắt là cách chắc chắn nhất để họ gỡ app. Thông báo vẫn nằm
 * lại trên màn hình nên không sợ bỏ lỡ dù đang để máy ở phòng khác.
 *
 * <p>Chỉ có một phiên chuông tại một thời điểm (đối tượng tĩnh): nếu hai cảnh báo
 * xảy ra sát nhau mà mỗi cái phát một luồng riêng thì người dùng sẽ nghe chồng
 * tiếng và không tắt hết được.
 */
public final class AlarmPlayer {

    private static final String TAG = "AlarmPlayer";

    /**
     * Chuông kêu tối đa bấy nhiêu rồi tự tắt (ms).
     *
     * <p>Âm báo thông báo thường chỉ dài một, hai giây và tự hết. Mốc này chỉ để
     * chặn trường hợp người dùng chọn một bản nhạc dài làm âm báo hệ thống.
     */
    private static final long MAX_DURATION_MS = 2_000L;

    /** Hai nhịp rung ngắn kiểu thông báo: rung 200ms, nghỉ 150ms, rung 200ms. */
    private static final long[] VIBRATE_PATTERN = {0L, 200L, 150L, 200L};

    @Nullable
    private static Ringtone ringtone;

    @Nullable
    private static Vibrator vibrator;

    /**
     * Đánh dấu phiên chuông hiện tại. Tác vụ hẹn giờ tắt chỉ được phép tắt đúng
     * phiên nó thuộc về – nếu không, một cảnh báo mới vừa bắt đầu sẽ bị tác vụ
     * hẹn giờ của cảnh báo cũ tắt ngang.
     */
    private static long sessionToken;

    private AlarmPlayer() {
    }

    /**
     * Kêu một tiếng chuông ngắn kèm rung, tự tắt sau {@link #MAX_DURATION_MS}.
     * Gọi lại khi đang kêu sẽ khởi động lại từ đầu.
     */
    public static synchronized void start(@NonNull Context context) {
        stop();

        final long token = ++sessionToken;

        playRingtone(context);
        startVibration(context);

        AppExecutors.get().sampler().schedule(
                () -> stopIfSameSession(token), MAX_DURATION_MS, TimeUnit.MILLISECONDS);
    }

    private static synchronized void stopIfSameSession(long token) {
        if (token == sessionToken) stop();
    }

    private static void playRingtone(@NonNull Context context) {
        try {
            // Âm báo thông báo trước; chỉ khi máy không có mới lùi về nhạc chuông
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (uri == null) return;

            Ringtone current = RingtoneManager.getRingtone(context.getApplicationContext(), uri);
            if (current == null) return;

            current.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            // Không lặp: chỉ kêu đúng một lượt rồi thôi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                current.setLooping(false);
            }

            current.play();
            ringtone = current;
        } catch (Exception e) {
            Logger.e(TAG, "Không phát được chuông báo", e);
        }
    }

    private static void startVibration(@NonNull Context context) {
        try {
            vibrator = getVibrator(context);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            // Chỉ số -1 nghĩa là chạy hết nhịp rồi dừng, không lặp
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, -1));
        } catch (Exception e) {
            Logger.e(TAG, "Không rung được", e);
        }
    }

    @Nullable
    private static Vibrator getVibrator(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager =
                    (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Dừng chuông và rung. Gọi khi chưa kêu cũng không sao. */
    public static synchronized void stop() {
        if (ringtone != null) {
            try {
                if (ringtone.isPlaying()) ringtone.stop();
            } catch (Exception e) {
                Logger.e(TAG, "Không dừng được chuông", e);
            }
            ringtone = null;
        }

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                Logger.e(TAG, "Không dừng được rung", e);
            }
            vibrator = null;
        }
    }

    /** Có đang kêu hay không. */
    public static synchronized boolean isPlaying() {
        return ringtone != null;
    }
}
