package com.dung.chargmagagement.model.ads;

import com.dung.chargmagagement.BuildConfig;

/**
 * Mã đơn vị quảng cáo AdMob.
 *
 * <p><b>Hiện đang dùng mã thử nghiệm chính thức của Google.</b> Mã thật phải lấy
 * từ tài khoản AdMob của bạn rồi thay vào các hằng số {@code *_RELEASE} bên dưới,
 * đồng thời đổi cả {@code APPLICATION_ID} khai báo trong AndroidManifest.
 *
 * <p>Tuyệt đối không dùng mã thật khi đang phát triển: Google coi việc tự bấm vào
 * quảng cáo của chính mình là gian lận và có thể khoá vĩnh viễn tài khoản AdMob.
 * Vì vậy lớp này tự chọn mã theo loại bản dựng – bản debug luôn dùng mã thử nghiệm.
 */
public final class AdConfig {

    // ==== Mã thử nghiệm của Google, dùng được ngay, không tính doanh thu ====
    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110";
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    // ==== Mã thật: thay bằng mã trong tài khoản AdMob trước khi phát hành ====
    private static final String BANNER_RELEASE = TEST_BANNER;
    private static final String NATIVE_RELEASE = TEST_NATIVE;
    private static final String REWARDED_RELEASE = TEST_REWARDED;

    private AdConfig() {
    }

    public static String bannerUnitId() {
        return BuildConfig.DEBUG ? TEST_BANNER : BANNER_RELEASE;
    }

    public static String nativeUnitId() {
        return BuildConfig.DEBUG ? TEST_NATIVE : NATIVE_RELEASE;
    }

    public static String rewardedUnitId() {
        return BuildConfig.DEBUG ? TEST_REWARDED : REWARDED_RELEASE;
    }

    /** Cảnh báo khi build release mà chưa thay mã thật. */
    public static boolean isUsingTestIdsInRelease() {
        return !BuildConfig.DEBUG && BANNER_RELEASE.equals(TEST_BANNER);
    }
}
