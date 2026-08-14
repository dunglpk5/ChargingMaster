package com.dung.chargmagagement.controller.power;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.controller.base.BaseActivity;
import com.dung.chargmagagement.controller.home.HomeActivity;
import com.dung.chargmagagement.controller.settings.SettingsActivity;
import com.dung.chargmagagement.databinding.ActivityChargingScreenBinding;
import com.dung.chargmagagement.databinding.ViewChargeStageBinding;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.power.ChargeAdvisor;
import com.dung.chargmagagement.model.power.ChargeStage;
import com.dung.chargmagagement.model.power.ChargeWarning;
import com.dung.chargmagagement.model.power.PowerSaver;
import com.dung.chargmagagement.model.stats.UsageCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Màn sạc phủ toàn màn hình, hiện đè lên màn khoá ngay khi cắm sạc.
 *
 * <p>Không có nền riêng: hệ thống vẽ thẳng hình nền của máy phía sau cửa sổ nhờ
 * {@code windowShowWallpaper} trong theme. Cách này không cần bất kỳ quyền nào,
 * khác hẳn việc tự đọc hình nền qua {@code WallpaperManager} – từ Android 13 việc
 * đó đòi quyền và thường chỉ trả về ảnh mặc định.
 *
 * <p>Đây là một Activity chứ không phải cửa sổ nổi kiểu {@code TYPE_APPLICATION_OVERLAY}:
 * lớp phủ hệ thống bị cấm vẽ đè lên màn khoá, nên chỉ Activity mới làm được điều
 * người dùng muốn.
 */
public class ChargingScreenActivity extends BaseActivity<ActivityChargingScreenBinding>
        implements BatteryMonitor.Listener {

    /** Quãng vuốt lên tối thiểu để coi là muốn đóng màn (dp). */
    private static final int SWIPE_DISMISS_DP = 80;

    /** Độ sáng cửa sổ sau khi người dùng ngừng thao tác (0..1). */
    private static final float WINDOW_DIM_BRIGHTNESS = 0.05f;

    /**
     * Giữ màn hình sáng bấy nhiêu lâu kể từ lần chạm cuối rồi mới hạ sáng.
     *
     * <p>Khác màn X-Sạc vốn hạ sáng ngay: màn này lấy hình nền của máy làm nền, hạ
     * sáng tức thì thì người dùng chẳng kịp nhìn thấy gì. Chạm vào màn hình là sáng
     * lại và đếm lại từ đầu.
     */
    private static final long SCREEN_ON_TIMEOUT_MS = 5_000L;

    private final Handler screenHandler = new Handler(Looper.getMainLooper());

    private final Runnable dimTask = this::dimWindow;

    private BatteryMonitor monitor;
    private int swipeThresholdPx;
    private float touchStartY;

    /** Trạng thái máy trước khi tối ưu; null nghĩa là chưa áp dụng. */
    @Nullable
    private PowerSaver.SavedState savedState;

    /** Tập cảnh báo đang hiển thị, để biết khi nào cần vẽ lại. */
    private List<ChargeWarning> shownWarnings = Collections.emptyList();

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, ChargingScreenActivity.class);
        // Gọi từ receiver nên không có Activity nào đứng sau, phải tự mở task mới
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    @NonNull
    @Override
    protected ActivityChargingScreenBinding onCreateBinding() {
        return ActivityChargingScreenBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onViewReady(@Nullable Bundle savedInstanceState) {
        showOverLockScreen();
        applySystemBarInsets();

        swipeThresholdPx = Math.round(SWIPE_DISMISS_DP
                * getResources().getDisplayMetrics().density);

        monitor = BatteryMonitor.get(this);

        binding.btnOpenApp.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        binding.btnSettings.setOnClickListener(v -> SettingsActivity.start(this));
        binding.btnDetectSpeed.setOnClickListener(v -> {
            CheckPowerActivity.start(this);
            finish();
        });

        setupSwipeToDismiss();
        setupStageLabels();
    }

    /**
     * Hiện đè lên màn khoá và bật màn hình.
     *
     * <p>Từ Android 8.1 phải gọi API mới; cờ cửa sổ cũ vẫn còn nhưng đã ngừng hoạt
     * động trên nhiều ROM nên không dùng riêng nó được.
     */
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Cửa sổ vẽ tràn ra sau thanh trạng thái nên phải tự chừa chỗ cho nó. */
    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            final androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
    }

    // ==================== Vuốt để đóng ====================

    /**
     * Vuốt lên để đóng, giống thao tác mở khoá quen thuộc.
     *
     * <p>Cố tình không chặn nút Quay lại: đây là màn hiện đè lên màn khoá, chặn
     * mọi lối thoát là cách nhanh nhất khiến người dùng thấy máy bị treo.
     */
    private void setupSwipeToDismiss() {
        binding.getRoot().setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = event.getY();
                    // Chạm vào là sáng lại và tính lại một phút chờ
                    scheduleDim();
                    return true;

                case MotionEvent.ACTION_UP:
                    if (touchStartY - event.getY() >= swipeThresholdPx) finish();
                    return true;

                default:
                    return true;
            }
        });
    }

    // ==================== Nội dung ====================

    private void setupStageLabels() {
        bindStageLabel(binding.stageFast, ChargeStage.FAST);
        bindStageLabel(binding.stageCycle, ChargeStage.CYCLE);
        bindStageLabel(binding.stageTrickle, ChargeStage.TRICKLE);
    }

    private void bindStageLabel(@NonNull ViewChargeStageBinding stage,
                                @NonNull ChargeStage value) {
        stage.imgStage.setImageResource(value.getIconRes());
        stage.tvStage.setText(getString(value.getLabelRes()).toUpperCase(Locale.getDefault()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.tvClock.setText(DateUtils.formatTime(System.currentTimeMillis()));
        monitor.addListener(this);

        applyOptimizations();
        scheduleDim();
    }

    @Override
    protected void onPause() {
        super.onPause();
        monitor.removeListener(this);

        screenHandler.removeCallbacks(dimTask);
        restoreOptimizations();
    }

    // ==================== Tối ưu như màn X-Sạc ====================

    /**
     * Tắt các thứ tốn điện trong lúc sạc, y như màn X-Sạc.
     *
     * <p>Trạng thái cũ được cất lại và trả về nguyên vẹn ở {@code onPause()}: màn
     * này có thể tự bật lên khi cắm sạc, nên tuyệt đối không được để lại thay đổi
     * nào sau khi người dùng đóng nó.
     */
    private void applyOptimizations() {
        if (savedState != null) return;
        savedState = PowerSaver.apply(this);
    }

    private void restoreOptimizations() {
        if (savedState == null) return;

        PowerSaver.restore(this, savedState);
        savedState = null;
        restoreWindowBrightness();
    }

    /** Hạ độ sáng riêng cho cửa sổ này, không đụng tới cài đặt hệ thống. */
    private void dimWindow() {
        setWindowBrightness(WINDOW_DIM_BRIGHTNESS);
    }

    private void restoreWindowBrightness() {
        setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
    }

    private void setWindowBrightness(float value) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = value;
        getWindow().setAttributes(params);
    }

    private void scheduleDim() {
        restoreWindowBrightness();
        screenHandler.removeCallbacks(dimTask);
        screenHandler.postDelayed(dimTask, SCREEN_ON_TIMEOUT_MS);
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        if (binding == null) return;

        binding.tvPercent.setText(String.format(Locale.US, "%d%%", info.getPercent()));
        binding.tvStatus.setText(buildStatusText(info, smoothedMa));
        binding.tvSource.setText(getString(R.string.charging_screen_source,
                getString(info.getPlugType().getLabelRes()),
                FormatUtils.formatTemperature(info.getTemperatureCelsius())));

        bindStages(info.getPercent());
        bindWarnings(info, smoothedMa);
    }

    /**
     * Cảnh báo sạc bất thường, cùng nguồn dữ liệu với màn X-Sạc.
     *
     * <p>Chỉ vẽ lại khi tập cảnh báo thật sự đổi. Không có bước so sánh này thì cứ
     * mỗi lần cập nhật pin các dòng chữ lại bị gỡ ra gắn vào, gây nháy và ăn CPU vô
     * ích trong chính màn hình đang lo tiết kiệm điện.
     */
    private void bindWarnings(@NonNull BatteryInfo info, int smoothedMa) {
        final List<ChargeWarning> warnings = ChargeAdvisor.analyze(info, smoothedMa);
        if (warnings.equals(shownWarnings)) return;

        shownWarnings = warnings;
        binding.warningContainer.removeAllViews();

        for (ChargeWarning warning : warnings) {
            addWarningLine(getString(warning.getMessageRes()));
        }
    }

    /**
     * Một dòng cảnh báo.
     *
     * <p>Dùng màu trắng chứ không dùng màu riêng của từng cảnh báo như màn X-Sạc:
     * nền ở đây là hình nền của người dùng, màu cam hay đỏ đặt lên đó có thể không
     * đọc nổi. Dấu chấm than đứng trước đã đủ nói đây là cảnh báo.
     */
    private void addWarningLine(@NonNull String text) {
        TextView line = new TextView(this);
        line.setText(getString(R.string.xcharge_line_warn, text));
        line.setTextSize(13f);
        line.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary));
        line.setGravity(android.view.Gravity.CENTER);
        line.setLineSpacing(0f, 1.1f);

        final int padding = getResources().getDimensionPixelSize(R.dimen.space_xs);
        line.setPadding(0, padding, 0, padding);
        binding.warningContainer.addView(line);
    }

    /** "Đang sạc" kèm thời gian còn lại nếu ước tính được. */
    @NonNull
    private String buildStatusText(@NonNull BatteryInfo info, int smoothedMa) {
        if (!info.getPlugType().isPlugged()) return getString(R.string.charging_screen_unplugged);
        if (info.getPercent() >= 100) return getString(R.string.charging_screen_full);

        final long remainingMs = UsageCalculator.estimateTimeToFull(
                info.getPercent(), smoothedMa, UsageCalculator.FALLBACK_CAPACITY_MAH);
        if (remainingMs <= 0) return getString(R.string.charging_screen_charging);

        return getString(R.string.charging_screen_charging_left,
                FormatUtils.formatDuration(remainingMs));
    }

    /** Giai đoạn đang chạy sáng rõ, hai giai đoạn còn lại mờ đi. */
    private void bindStages(int percent) {
        final ChargeStage active = ChargeStage.fromPercent(percent);

        setStageActive(binding.stageFast, active == ChargeStage.FAST);
        setStageActive(binding.stageCycle, active == ChargeStage.CYCLE);
        setStageActive(binding.stageTrickle, active == ChargeStage.TRICKLE);
    }

    private void setStageActive(@NonNull ViewChargeStageBinding stage, boolean active) {
        final int color = ContextCompat.getColor(this,
                active ? R.color.text_on_primary : R.color.stage_inactive);

        stage.tvStage.setTextColor(color);
        stage.imgStage.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        stage.getRoot().setAlpha(active ? 1f : 0.6f);
    }

    @Override
    public void onPowerStateChanged(boolean connected) {
        // Rút sạc là màn này hết lý do tồn tại
        if (!connected) finish();
    }

    /** Ẩn thanh điều hướng để màn hình liền một khối như bản thiết kế. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || binding == null) return;

        binding.getRoot().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}
