package com.dung.chargmagagement.model.stats;

import androidx.annotation.NonNull;

/**
 * Tích luỹ số liệu của một chặng dùng máy, phục vụ thông báo thường trú.
 *
 * <p>Một "chặng" bắt đầu khi cắm hoặc rút sạc và kéo dài tới lần đổi trạng thái
 * kế tiếp. Gộp cả hai chiều vào một chặng thì mọi số trung bình đều vô nghĩa: nạp
 * vào và xả ra triệt tiêu lẫn nhau.
 *
 * <p><b>Thời gian màn hình bật/tắt không suy ra từ mẫu đo pin.</b> Mẫu pin tới theo
 * nhịp của hệ thống, có lúc cách nhau hàng chục phút vì máy ngủ; nếu lấy trạng thái
 * màn hình tại thời điểm nhận mẫu rồi gán cho cả khoảng vừa trôi qua thì một lần
 * bật màn hình sau nửa tiếng máy ngủ sẽ ghi luôn nửa tiếng đó vào "màn hình bật".
 * Vì vậy lớp này nhận thêm {@link #setScreenOn} tại đúng thời điểm màn hình đổi
 * trạng thái, và cắt khoảng giữa hai mẫu ra theo các mốc đó.
 *
 * <p>Lớp thuần logic, không đụng Android, nên kiểm thử được bằng JUnit thường.
 * Mọi mốc thời gian do phía gọi truyền vào thay vì tự đọc đồng hồ.
 */
public class SessionMeter {

    /** Chưa đủ dữ liệu để tính. */
    public static final float UNKNOWN = Float.NaN;

    /** Máy không đọc được dòng điện ở mẫu này. */
    public static final int UNKNOWN_CURRENT = Integer.MIN_VALUE;

    /**
     * Chặng phải dài và tụt/nạp đủ nhiều thì mới tin được số % do hệ thống báo.
     *
     * <p>Mức pin chỉ có độ phân giải 1 %: một bước nhảy duy nhất trong bốn phút quy
     * ra đã là 15 %/h, con số đúng về số học nhưng chỉ phản ánh thời điểm hệ thống
     * làm tròn. Dưới ngưỡng này thì suy ra từ dòng điện đo được, mượt và khớp với
     * chính con số mA hiện ngay bên cạnh.
     */
    private static final long MIN_TRUSTED_MS = 15 * 60_000L;
    private static final int MIN_TRUSTED_PERCENT = 2;

    private long startTime;
    private int startPercent;

    private long lastTime;
    private int lastPercent;

    /** Tổng điện tích đã đi qua pin (mAh), luôn dương. */
    private float totalMah;

    /** Tổng mA × ms, để tính dòng trung bình có trọng số theo thời gian. */
    private double weightedMaSum;
    private long weightedMs;

    private long screenOnMs;
    private long screenOffMs;
    private float screenOnMah;
    private float screenOffMah;

    /** Trạng thái màn hình hiện tại và mốc bắt đầu của đoạn đang tính. */
    private boolean screenIsOn;
    private boolean screenKnown;
    private long segmentStart;

    /** Thời gian bật/tắt tích được trong khoảng giữa hai mẫu pin. */
    private long pendingOnMs;
    private long pendingOffMs;

    private boolean started;

    /** Bắt đầu một chặng mới, xoá sạch số liệu cũ. */
    public void reset(long timestamp, int percent) {
        startTime = timestamp;
        startPercent = percent;
        lastTime = timestamp;
        lastPercent = percent;

        totalMah = 0f;
        weightedMaSum = 0d;
        weightedMs = 0L;
        screenOnMs = 0L;
        screenOffMs = 0L;
        screenOnMah = 0f;
        screenOffMah = 0f;

        screenIsOn = false;
        screenKnown = false;
        segmentStart = timestamp;
        pendingOnMs = 0L;
        pendingOffMs = 0L;

        started = true;
    }

    /**
     * Ghi nhận màn hình vừa bật hoặc tắt.
     *
     * <p>Chốt phần thời gian tính tới đúng thời điểm này rồi mới đổi trạng thái, nhờ
     * vậy ranh giới bật/tắt chính xác tới từng giây thay vì bị làm tròn về nhịp lấy
     * mẫu pin.
     */
    public void setScreenOn(long timestamp, boolean on) {
        if (!started) return;

        // Chưa từng biết trạng thái thì suy ngược: màn hình vừa đổi *sang* trạng
        // thái này, nên khoảng vừa qua nó đang ở trạng thái ngược lại
        if (!screenKnown) {
            screenIsOn = !on;
            screenKnown = true;
        }

        closeSegment(timestamp);
        screenIsOn = on;
    }

    /**
     * Nạp một mẫu đo mới.
     *
     * @param timestamp mốc thời gian của mẫu
     * @param percent   mức pin
     * @param currentMa dòng điện (âm là đang xả); {@link #UNKNOWN_CURRENT} nếu máy
     *                  không đọc được
     * @param screenOn  màn hình có đang bật không, dùng để đồng bộ lại trạng thái
     */
    public void addSample(long timestamp, int percent, int currentMa, boolean screenOn) {
        if (!started) {
            reset(timestamp, percent);
            return;
        }

        final long deltaMs = timestamp - lastTime;
        // Mẫu lùi về quá khứ (đổi giờ hệ thống) sẽ làm hỏng mọi phép chia
        if (deltaMs <= 0L) {
            lastPercent = percent;
            return;
        }

        // Mẫu đầu tiên của chặng định nghĩa luôn trạng thái của khoảng vừa trôi qua
        if (!screenKnown) {
            screenIsOn = screenOn;
            screenKnown = true;
        }
        closeSegment(timestamp);

        // Mẫu không đọc được dòng điện chỉ tính vào thời gian, không tính vào điện
        // tích: coi nó là 0 mA sẽ kéo tụt dòng trung bình xuống một cách giả tạo
        final boolean hasCurrent = currentMa != UNKNOWN_CURRENT;
        final float mah = hasCurrent
                ? Math.abs(currentMa) * (deltaMs / 3_600_000f)
                : 0f;

        if (hasCurrent) {
            totalMah += mah;
            weightedMaSum += (double) currentMa * deltaMs;
            weightedMs += deltaMs;
        }

        // Điện tích của khoảng này chia về hai bên theo đúng tỉ lệ thời gian
        final long span = pendingOnMs + pendingOffMs;
        if (span > 0L) {
            screenOnMs += pendingOnMs;
            screenOffMs += pendingOffMs;
            screenOnMah += mah * pendingOnMs / span;
            screenOffMah += mah * pendingOffMs / span;
        }
        pendingOnMs = 0L;
        pendingOffMs = 0L;

        // Trạng thái tại thời điểm lấy mẫu là nguồn đáng tin nhất, dùng để đồng bộ
        // lại phòng khi lỡ mất một broadcast bật/tắt màn hình
        screenIsOn = screenOn;

        lastTime = timestamp;
        lastPercent = percent;
    }

    /** Dồn thời gian từ mốc đoạn hiện tại tới {@code timestamp} vào đúng bên. */
    private void closeSegment(long timestamp) {
        final long delta = timestamp - segmentStart;
        if (delta <= 0L) return;

        if (screenIsOn) {
            pendingOnMs += delta;
        } else {
            pendingOffMs += delta;
        }
        segmentStart = timestamp;
    }

    // ==================== Kết quả ====================

    public boolean hasData() {
        return started && weightedMs > 0L;
    }

    public long getElapsedMs() {
        return Math.max(0L, lastTime - startTime);
    }

    /** Dòng trung bình (mA), có trọng số theo thời gian của từng mẫu. */
    public int getAverageMa() {
        if (weightedMs <= 0L) return 0;
        return (int) Math.round(weightedMaSum / weightedMs);
    }

    /** Tổng điện tích đã đi qua pin (mAh). */
    public float getTotalMah() {
        return totalMah;
    }

    /** Số phần trăm đã đổi, dương là nạp vào, âm là tiêu hao. */
    public int getPercentDelta() {
        return lastPercent - startPercent;
    }

    /**
     * Tốc độ đổi mức pin trung bình (%/giờ).
     *
     * <p>{@link #UNKNOWN} khi chặng còn quá ngắn: chia cho vài giây đầu tiên sẽ ra
     * những con số hàng trăm %/h, đúng về số học nhưng vô dụng với người đọc.
     */
    public float getAveragePercentPerHour() {
        final long elapsed = getElapsedMs();
        if (elapsed < 60_000L) return UNKNOWN;
        return getPercentDelta() / (elapsed / 3_600_000f);
    }

    /**
     * Tốc độ đổi mức pin trung bình (%/giờ), ưu tiên nguồn số liệu đáng tin hơn.
     *
     * <p>Chặng đã đủ dài và đủ chênh lệch thì lấy chính số % hệ thống báo – đó là
     * sự thật, đã bao gồm cả hao phí mà cảm biến dòng không thấy. Chặng còn ngắn thì
     * suy ra từ dòng trung bình và dung lượng pin, vì lúc đó phép chia theo % chỉ
     * đang khuếch đại một bước nhảy làm tròn.
     *
     * @param capacityMah dung lượng dùng được của pin; 0 nếu chưa biết
     */
    public float getAveragePercentPerHour(int capacityMah) {
        if (getElapsedMs() >= MIN_TRUSTED_MS
                && Math.abs(getPercentDelta()) >= MIN_TRUSTED_PERCENT) {
            return getAveragePercentPerHour();
        }
        if (capacityMah > 0 && weightedMs > 0L) {
            return getAverageMa() * 100f / capacityMah;
        }
        return getAveragePercentPerHour();
    }

    /**
     * Thời gian màn hình bật, gồm cả đoạn dở dang từ mẫu pin gần nhất tới bây giờ.
     *
     * <p>Có tham số {@code now} vì thông báo được vẽ lại giữa hai mẫu pin: không
     * cộng phần đuôi thì đồng hồ đứng yên hàng chục phút mỗi khi máy ngủ.
     */
    public long getScreenOnMs(long now) {
        return screenOnMs + pendingOnMs + tailMs(now, true);
    }

    public long getScreenOffMs(long now) {
        return screenOffMs + pendingOffMs + tailMs(now, false);
    }

    /** Phần đuôi chưa chốt: từ mốc đoạn hiện tại tới bây giờ. */
    private long tailMs(long now, boolean forScreenOn) {
        if (!started || !screenKnown || screenIsOn != forScreenOn) return 0L;
        return Math.max(0L, now - segmentStart);
    }

    public long getScreenOnMs() {
        return screenOnMs;
    }

    public long getScreenOffMs() {
        return screenOffMs;
    }

    public float getScreenOnMah() {
        return screenOnMah;
    }

    public float getScreenOffMah() {
        return screenOffMah;
    }

    /** Tỉ lệ điện tích tiêu ở màn bật so với tổng (0..100). */
    public float getScreenOnShare() {
        return share(screenOnMah);
    }

    public float getScreenOffShare() {
        return share(screenOffMah);
    }

    private float share(float part) {
        if (totalMah <= 0f) return 0f;
        return part * 100f / totalMah;
    }

    /**
     * Tỉ lệ phần trăm pin ứng với một phần điện tích.
     *
     * <p>Dùng để quy mAh của màn bật/tắt về đơn vị % mà người dùng quen đọc.
     */
    public float toPercent(float mah, int capacityMah) {
        if (capacityMah <= 0) return 0f;
        return mah * 100f / capacityMah;
    }

    @NonNull
    @Override
    public String toString() {
        return "SessionMeter{" + getElapsedMs() + "ms, " + getAverageMa() + "mA}";
    }
}
