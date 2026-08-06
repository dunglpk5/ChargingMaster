package com.dung.chargmagagement.model.ads;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.vip.VipManager;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Điểm truy cập duy nhất tới AdMob.
 *
 * <p>Mọi lời gọi đều <b>tự kiểm tra trạng thái VIP trước</b>: người đã mua gói
 * không quảng cáo thì không tải quảng cáo nữa, vừa đúng cam kết vừa đỡ tốn dữ
 * liệu di động của họ.
 *
 * <p>SDK được khởi tạo lười (lazy) ở lần dùng đầu tiên chứ không ở
 * {@code Application.onCreate()}: khởi tạo AdMob tốn khoảng 100–300 ms, đặt ở
 * luồng khởi động sẽ làm app mở chậm thấy rõ.
 */
public final class AdManager {

    private static final String TAG = "AdManager";

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private AdManager() {
    }

    /** Khởi tạo SDK một lần duy nhất; gọi lại nhiều lần cũng không sao. */
    public static void ensureInitialized(@NonNull Context context) {
        if (VipManager.get(context).isVip()) return;
        if (!initialized.compareAndSet(false, true)) return;

        // initialize() tự chạy phần nặng ở thread nền của SDK
        MobileAds.initialize(context.getApplicationContext(),
                status -> Logger.d(TAG, "AdMob đã khởi tạo"));
    }

    /**
     * Gắn banner thích ứng vào một khung chứa.
     *
     * <p>Dùng banner co giãn theo bề ngang màn hình thay vì kích thước cố định:
     * Google ưu tiên loại này và nó hiển thị đẹp trên mọi kích cỡ máy.
     *
     * @param container khung chứa; sẽ bị xoá sạch trước khi gắn quảng cáo mới
     */
    @MainThread
    public static void loadBanner(@NonNull Activity activity, @NonNull ViewGroup container) {
        if (VipManager.get(activity).isVip()) {
            container.removeAllViews();
            container.setVisibility(ViewGroup.GONE);
            return;
        }

        ensureInitialized(activity);

        AdView adView = new AdView(activity);
        adView.setAdUnitId(AdConfig.bannerUnitId());
        adView.setAdSize(adaptiveSize(activity, container));

        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                container.setVisibility(ViewGroup.VISIBLE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                // Không có mạng hoặc chưa có quảng cáo phù hợp: giấu hẳn khung
                // chứa để không chừa một khoảng trống khó hiểu giữa giao diện
                Logger.d(TAG, "Banner không tải được: " + error.getMessage());
                container.setVisibility(ViewGroup.GONE);
            }
        });

        container.removeAllViews();
        container.addView(adView);
        adView.loadAd(new AdRequest.Builder().build());
    }

    /** Kích thước banner co theo bề ngang khung chứa. */
    private static AdSize adaptiveSize(@NonNull Activity activity, @NonNull ViewGroup container) {
        final float density = activity.getResources().getDisplayMetrics().density;
        int widthPixels = container.getWidth();
        if (widthPixels <= 0) {
            widthPixels = activity.getResources().getDisplayMetrics().widthPixels;
        }
        final int widthDp = (int) (widthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp);
    }

    /**
     * Tải quảng cáo có thưởng, dùng cho chức năng dùng thử X-Sạc.
     *
     * @param callback nhận kết quả trên UI thread
     */
    @MainThread
    public static void loadRewarded(@NonNull Context context, @NonNull RewardedCallback callback) {
        if (VipManager.get(context).isVip()) {
            // Người dùng VIP được dùng thẳng, không phải xem quảng cáo
            callback.onRewardEarned();
            return;
        }

        ensureInitialized(context);

        RewardedAd.load(context, AdConfig.rewardedUnitId(),
                new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        callback.onAdReady(ad);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Logger.d(TAG, "Quảng cáo có thưởng không tải được: " + error.getMessage());
                        callback.onAdFailed();
                    }
                });
    }

    /** Kết quả tải quảng cáo có thưởng. */
    public interface RewardedCallback {

        /** Quảng cáo đã sẵn sàng, phía gọi tự quyết định lúc nào hiển thị. */
        void onAdReady(@NonNull RewardedAd ad);

        /** Không tải được (mất mạng, hết quảng cáo…). */
        void onAdFailed();

        /**
         * Người dùng đã nhận thưởng. Mặc định không làm gì; chỉ dùng cho trường
         * hợp VIP được bỏ qua bước xem quảng cáo.
         */
        default void onRewardEarned() {
        }
    }

    /** Người dùng vừa mua VIP: gỡ hết quảng cáo đang hiển thị trong khung chứa. */
    @MainThread
    public static void clearAds(@Nullable ViewGroup container) {
        if (container == null) return;
        container.removeAllViews();
        container.setVisibility(ViewGroup.GONE);
    }
}
