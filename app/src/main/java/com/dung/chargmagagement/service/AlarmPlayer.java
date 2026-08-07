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

import com.dung.chargmagagement.common.Logger;

/**
 * Phát chuông và rung khi báo động sạc.
 *
 * <p>Dùng {@link Ringtone} với {@link AudioAttributes#USAGE_ALARM}: âm báo sẽ đi
 * theo âm lượng báo thức chứ không phải âm lượng nhạc, nên vẫn kêu được khi máy
 * đang để nhỏ tiếng media — đúng ý nghĩa của một cảnh báo.
 *
 * <p>Chỉ có một phiên chuông tại một thời điểm (đối tượng tĩnh): nếu hai cảnh báo
 * xảy ra sát nhau mà mỗi cái phát một luồng riêng thì người dùng sẽ nghe chồng
 * tiếng và không tắt hết được.
 */
public final class AlarmPlayer {

    private static final String TAG = "AlarmPlayer";

    /** Nhịp rung: chờ 0ms, rung 600ms, nghỉ 800ms, lặp lại. */
    private static final long[] VIBRATE_PATTERN = {0L, 600L, 800L};

    @Nullable
    private static Ringtone ringtone;

    @Nullable
    private static Vibrator vibrator;

    private AlarmPlayer() {
    }

    /** Bắt đầu kêu chuông và rung. Gọi lại khi đang kêu sẽ khởi động lại từ đầu. */
    public static synchronized void start(@NonNull Context context) {
        stop();

        playRingtone(context);
        startVibration(context);
    }

    private static void playRingtone(@NonNull Context context) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (uri == null) return;

            Ringtone current = RingtoneManager.getRingtone(context.getApplicationContext(), uri);
            if (current == null) return;

            current.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                current.setLooping(true);
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

            // Chỉ số 0 nghĩa là lặp lại nhịp rung từ đầu mảng
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, 0));
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
