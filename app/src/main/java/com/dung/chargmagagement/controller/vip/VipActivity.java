package com.dung.chargmagagement.controller.vip;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.databinding.ActivityVipBinding;
import com.dung.chargmagagement.model.vip.VipManager;

/**
 * Màn nâng cấp VIP.
 *
 * <p>Liệt kê quyền lợi và mở luồng thanh toán của Google Play. Giá hiển thị lấy
 * trực tiếp từ Play theo tiền tệ của người dùng, <b>không viết cứng trong app</b>:
 * giá do bạn đặt trong Play Console và khác nhau theo từng quốc gia.
 */
public class VipActivity extends BaseActivity<ActivityVipBinding>
        implements VipManager.VipStateListener {

    /** Các quyền lợi liệt kê trên màn hình, dùng lại nhãn của lưới công cụ. */
    @StringRes
    private static final int[] BENEFITS = {
            R.string.tools_no_ads,
            R.string.tools_charge_alarm,
            R.string.tools_charge_history,
            R.string.tools_x_charge,
            R.string.tools_priority_support
    };

    private VipManager vipManager;

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, VipActivity.class));
    }

    @NonNull
    @Override
    protected ActivityVipBinding onCreateBinding() {
        return ActivityVipBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        binding.toolbarInclude.tvToolbarTitle.setText(R.string.tools_group_vip);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        vipManager = VipManager.get(this);
        vipManager.setStateListener(this);

        buildBenefitList();
        binding.btnPurchase.setOnClickListener(v -> purchase());

        // Kết nối Google Play rồi đồng bộ lại trạng thái đã mua
        vipManager.connect(this::renderState);
        renderState();
    }

    @Override
    protected void onDestroy() {
        // Không giữ tham chiếu Activity trong singleton sau khi màn bị huỷ
        vipManager.setStateListener(null);
        super.onDestroy();
    }

    private void buildBenefitList() {
        binding.benefitContainer.removeAllViews();

        for (int benefitRes : BENEFITS) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.vip_benefit_format, getString(benefitRes)));
            row.setTextSize(16f);
            row.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            row.setGravity(Gravity.CENTER_VERTICAL);

            final int padding = getResources().getDimensionPixelSize(R.dimen.space_md);
            row.setPadding(padding, padding / 2, padding, padding / 2);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            binding.benefitContainer.addView(row);
        }
    }

    /** Cập nhật giao diện theo trạng thái VIP hiện tại. */
    private void renderState() {
        if (binding == null) return;

        final boolean vip = vipManager.isVip();
        binding.tvVipState.setText(vip ? R.string.vip_active : R.string.vip_subtitle);
        binding.btnPurchase.setEnabled(!vip);

        if (vip) {
            binding.btnPurchase.setText(R.string.vip_active);
            return;
        }

        final String price = vipManager.getFormattedPrice();
        binding.btnPurchase.setText(price.isEmpty()
                ? getString(R.string.vip_purchase)
                : getString(R.string.vip_purchase_price, price));
    }

    private void purchase() {
        if (vipManager.isVip()) return;

        if (!vipManager.launchPurchase(this)) {
            // Chưa kết nối được Play, hoặc sản phẩm chưa được tạo trong Play Console
            Toast.makeText(this, R.string.vip_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onVipStateChanged(boolean vip) {
        renderState();
        if (vip) {
            Toast.makeText(this, R.string.vip_thanks, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onProductReady(@NonNull String formattedPrice) {
        renderState();
    }
}
