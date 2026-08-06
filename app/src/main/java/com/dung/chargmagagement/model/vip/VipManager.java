package com.dung.chargmagagement.model.vip;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;

import java.util.Collections;
import java.util.List;

/**
 * Quản lý gói VIP (gỡ quảng cáo và mở khoá các chức năng nâng cao).
 *
 * <p>Trạng thái VIP được lưu trong SharedPreferences để đọc được <b>ngay lập tức</b>
 * và cả khi không có mạng: tầng quảng cáo cần biết có được hiển thị hay không tại
 * thời điểm dựng giao diện, không thể chờ Google Play trả lời. Bản lưu này chỉ là
 * bộ nhớ đệm – nguồn sự thật vẫn là Google Play, và được đồng bộ lại mỗi lần app
 * kết nối được tới dịch vụ thanh toán.
 *
 * <p><b>Trước khi phát hành</b> phải tạo sản phẩm có mã {@link #PRODUCT_ID_VIP}
 * trong Google Play Console, nếu không việc truy vấn sẽ luôn trả về danh sách rỗng.
 */
public final class VipManager implements PurchasesUpdatedListener {

    private static final String TAG = "VipManager";

    /** Mã sản phẩm trong Play Console; phải trùng khớp tuyệt đối. */
    public static final String PRODUCT_ID_VIP = "charg_vip_lifetime";

    private static final String KEY_IS_VIP = "is_vip";

    private static volatile VipManager instance;

    private final Context appContext;
    private final PrefManager prefs;
    private final BillingClient billingClient;

    @Nullable
    private ProductDetails vipProduct;

    @Nullable
    private VipStateListener stateListener;

    private VipManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = PrefManager.get(appContext);

        this.billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases()
                .build();
    }

    public static VipManager get(@NonNull Context context) {
        if (instance == null) {
            synchronized (VipManager.class) {
                if (instance == null) {
                    instance = new VipManager(context);
                }
            }
        }
        return instance;
    }

    // ==================== Trạng thái ====================

    /** Người dùng có đang là VIP không. Đọc từ bộ nhớ đệm nên trả về tức thì. */
    public boolean isVip() {
        return prefs.getBoolean(KEY_IS_VIP, false);
    }

    private void setVip(boolean vip) {
        if (isVip() == vip) return;

        prefs.putBoolean(KEY_IS_VIP, vip);
        AppExecutors.get().runOnMain(() -> {
            if (stateListener != null) stateListener.onVipStateChanged(vip);
        });
    }

    public void setStateListener(@Nullable VipStateListener listener) {
        this.stateListener = listener;
    }

    // ==================== Kết nối Google Play ====================

    /**
     * Kết nối tới dịch vụ thanh toán rồi đồng bộ lại trạng thái đã mua.
     * Gọi khi mở màn VIP; kết nối lại nhiều lần là an toàn.
     */
    public void connect(@Nullable Runnable onReady) {
        if (billingClient.isReady()) {
            queryPurchases();
            if (onReady != null) onReady.run();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    Logger.d(TAG, "Không kết nối được dịch vụ thanh toán: "
                            + result.getDebugMessage());
                    return;
                }
                queryProduct();
                queryPurchases();
                if (onReady != null) AppExecutors.get().runOnMain(onReady);
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Google Play có thể ngắt kết nối tạm thời; lần dùng sau sẽ tự nối lại
                Logger.d(TAG, "Dịch vụ thanh toán bị ngắt kết nối");
            }
        });
    }

    /** Lấy thông tin sản phẩm (tên, giá) để hiển thị trên màn VIP. */
    private void queryProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_ID_VIP)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();

        billingClient.queryProductDetailsAsync(params, (result, products) -> {
            if (products == null || products.isEmpty()) {
                Logger.d(TAG, "Chưa có sản phẩm VIP trong Play Console");
                return;
            }
            vipProduct = products.get(0);
            AppExecutors.get().runOnMain(() -> {
                if (stateListener != null) stateListener.onProductReady(getFormattedPrice());
            });
        });
    }

    /** Đồng bộ lại các giao dịch đã mua (ví dụ người dùng cài lại app). */
    private void queryPurchases() {
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                (result, purchases) -> {
                    boolean vip = false;
                    for (Purchase purchase : purchases) {
                        if (isValidVipPurchase(purchase)) {
                            vip = true;
                            acknowledgeIfNeeded(purchase);
                        }
                    }
                    setVip(vip);
                });
    }

    /** Giá đã định dạng theo tiền tệ của người dùng; rỗng nếu chưa lấy được. */
    @NonNull
    public String getFormattedPrice() {
        if (vipProduct == null) return "";

        ProductDetails.OneTimePurchaseOfferDetails offer =
                vipProduct.getOneTimePurchaseOfferDetails();
        return offer == null ? "" : offer.getFormattedPrice();
    }

    // ==================== Mua hàng ====================

    /**
     * Mở luồng thanh toán của Google Play.
     *
     * @return false nếu chưa sẵn sàng (chưa kết nối hoặc chưa có sản phẩm)
     */
    @MainThread
    public boolean launchPurchase(@NonNull Activity activity) {
        if (!billingClient.isReady() || vipProduct == null) return false;

        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(vipProduct)
                                .build()))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, params);
        return result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result,
                                   @Nullable List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                || purchases == null) {
            // Người dùng bấm huỷ hoặc thanh toán thất bại: không đổi trạng thái
            return;
        }

        for (Purchase purchase : purchases) {
            if (isValidVipPurchase(purchase)) {
                acknowledgeIfNeeded(purchase);
                setVip(true);
            }
        }
    }

    private boolean isValidVipPurchase(@NonNull Purchase purchase) {
        return purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                && purchase.getProducts().contains(PRODUCT_ID_VIP);
    }

    /**
     * Xác nhận giao dịch.
     *
     * <p>Bắt buộc: Google Play <b>tự hoàn tiền</b> nếu giao dịch không được xác
     * nhận trong vòng 3 ngày, và người dùng sẽ mất quyền VIP dù đã trả tiền.
     */
    private void acknowledgeIfNeeded(@NonNull Purchase purchase) {
        if (purchase.isAcknowledged()) return;

        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, result -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Logger.e(TAG, "Xác nhận giao dịch thất bại: " + result.getDebugMessage(), null);
            }
        });
    }

    /** Nghe thay đổi trạng thái VIP và thông tin sản phẩm. */
    public interface VipStateListener {

        @MainThread
        void onVipStateChanged(boolean vip);

        /** Đã lấy được giá từ Google Play. */
        @MainThread
        default void onProductReady(@NonNull String formattedPrice) {
        }
    }
}
