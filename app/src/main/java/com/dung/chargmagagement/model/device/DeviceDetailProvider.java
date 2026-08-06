package com.dung.chargmagagement.model.device;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FileUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.ui.DetailRow;

import java.io.File;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Dựng dữ liệu cho 6 tab của màn Thông tin thiết bị.
 *
 * <p>Luôn gọi ở thread nền: phần CPU đọc nhiều file sysfs, phần mạng duyệt danh
 * sách network interface.
 *
 * <p>Giá trị không đọc được hiển thị là "unknown" thay vì bị ẩn đi, để người dùng
 * biết máy mình không cung cấp thông tin đó chứ không phải app quên hiển thị.
 */
public final class DeviceDetailProvider {

    private static final String TAG = "DeviceDetailProvider";

    /** Các đường dẫn thường thấy của tệp nhị phân su, dùng để đoán máy đã root. */
    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/su", "/vendor/bin/su", "/su/bin/su"
    };

    /** Vùng cảm biến nhiệt của CPU; tên node khác nhau tuỳ hãng chip. */
    private static final String[] CPU_TEMP_PATHS = {
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/class/hwmon/hwmon0/temp1_input"
    };

    private final Context appContext;

    public DeviceDetailProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Danh sách dòng thông tin của một tab. */
    @WorkerThread
    @NonNull
    public List<DetailRow> build(@NonNull DetailSection section) {
        try {
            switch (section) {
                case SYSTEM:
                    return buildSystem();
                case CPU:
                    return buildCpu();
                case DISPLAY:
                    return buildDisplay();
                case NETWORK:
                    return buildNetwork();
                case SENSOR:
                    return buildSensor();
                case DEVICE:
                default:
                    return buildDevice();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Không dựng được dữ liệu tab " + section.name(), e);
            return Collections.emptyList();
        }
    }

    // ==================== DEVICE ====================

    private List<DetailRow> buildDevice() {
        List<DetailRow> rows = new ArrayList<>();

        rows.add(DetailRow.header("device_name", R.string.detail_device_name,
                R.drawable.ic_logo_phone,
                Build.BRAND.toUpperCase(Locale.US) + "\n" + Build.MODEL));

        rows.add(DetailRow.value("model", R.string.detail_model, Build.MODEL));
        rows.add(DetailRow.value("manufacturer", R.string.detail_manufacturer, Build.MANUFACTURER));
        rows.add(DetailRow.value("brand", R.string.detail_brand, Build.BRAND));
        rows.add(DetailRow.value("device", R.string.detail_device, Build.DEVICE));
        rows.add(DetailRow.value("board", R.string.detail_board, Build.BOARD));
        rows.add(DetailRow.value("hardware", R.string.detail_hardware, Build.HARDWARE));
        rows.add(DetailRow.value("ble", R.string.detail_bluetooth_le, yesNo(hasBluetoothLe())));
        rows.add(DetailRow.value("nfc_present", R.string.detail_nfc_present, yesNo(hasNfc())));
        rows.add(DetailRow.value("nfc_enabled", R.string.detail_nfc_enabled, yesNo(isNfcEnabled())));
        rows.add(DetailRow.value("ring_mode", R.string.detail_ring_mode, getRingerMode()));
        rows.add(DetailRow.value("system_time", R.string.detail_system_time, getSystemTime()));
        rows.add(DetailRow.value("brightness_level", R.string.detail_brightness_level,
                getBrightnessPercent()));
        rows.add(DetailRow.value("brightness_mode", R.string.detail_brightness_mode,
                appContext.getString(isAutoBrightness()
                        ? R.string.detail_brightness_auto
                        : R.string.detail_brightness_manual)));
        rows.add(DetailRow.value("orientation", R.string.detail_orientation, getOrientation()));
        rows.add(DetailRow.value("sdcard", R.string.detail_sdcard, yesNo(hasExternalStorage())));
        rows.add(DetailRow.value("emulator", R.string.detail_emulator, yesNo(isEmulator())));

        return rows;
    }

    private boolean hasBluetoothLe() {
        return appContext.getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private boolean hasNfc() {
        return appContext.getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC);
    }

    private boolean isNfcEnabled() {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(appContext);
            return adapter != null && adapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private String getRingerMode() {
        AudioManager manager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return null;

        switch (manager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                return appContext.getString(R.string.detail_ring_silent);
            case AudioManager.RINGER_MODE_VIBRATE:
                return appContext.getString(R.string.detail_ring_vibrate);
            case AudioManager.RINGER_MODE_NORMAL:
                return appContext.getString(R.string.detail_ring_normal);
            default:
                return null;
        }
    }

    private String getSystemTime() {
        return new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }

    /** Độ sáng theo phần trăm của thang 0–255 mà hệ thống dùng. */
    private String getBrightnessPercent() {
        final int raw = Settings.System.getInt(appContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
        if (raw < 0) return null;
        return String.format(Locale.US, "%d %%", Math.round(raw * 100f / 255f));
    }

    private String getOrientation() {
        final int orientation = appContext.getResources().getConfiguration().orientation;
        return appContext.getString(orientation == Configuration.ORIENTATION_LANDSCAPE
                ? R.string.detail_landscape
                : R.string.detail_portrait);
    }

    private boolean hasExternalStorage() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Đoán máy ảo dựa vào các dấu hiệu trong thông tin bản dựng.
     * Không có API chính thức nên đây chỉ là phỏng đoán, tuy nhiên đủ chính xác
     * với các máy ảo phổ biến (Android Studio, Genymotion, BlueStacks).
     */
    private boolean isEmulator() {
        final String fingerprint = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT;
        final String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE;
        final String model = Build.MODEL == null ? "" : Build.MODEL;

        return fingerprint.startsWith("generic")
                || fingerprint.contains("vbox")
                || fingerprint.contains("emulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || model.contains("Emulator")
                || model.contains("Android SDK built for");
    }

    // ==================== SYSTEM ====================

    private List<DetailRow> buildSystem() {
        List<DetailRow> rows = new ArrayList<>();

        final String androidName = "Android " + Build.VERSION.RELEASE;

        rows.add(DetailRow.header("android", R.string.detail_tab_system,
                R.drawable.ic_logo_android,
                androidName + "\n" + formatBuildTime()));

        rows.add(DetailRow.value("version", R.string.detail_android_version, Build.VERSION.RELEASE));
        rows.add(DetailRow.value("api", R.string.detail_api_level,
                String.valueOf(Build.VERSION.SDK_INT)));
        rows.add(DetailRow.value("bootloader", R.string.detail_bootloader, Build.BOOTLOADER));
        rows.add(DetailRow.value("product", R.string.detail_product, Build.PRODUCT));
        rows.add(DetailRow.value("host", R.string.detail_host, Build.HOST));
        rows.add(DetailRow.value("user", R.string.detail_user, Build.USER));
        rows.add(DetailRow.value("tags", R.string.detail_build_tags, Build.TAGS));
        rows.add(DetailRow.value("fingerprint", R.string.detail_fingerprint, Build.FINGERPRINT));
        rows.add(DetailRow.value("language", R.string.detail_language,
                Locale.getDefault().getLanguage()));
        rows.add(DetailRow.value("code_name", R.string.detail_code_name, androidName));
        rows.add(DetailRow.value("released", R.string.detail_released_time, formatBuildTime()));
        rows.add(DetailRow.value("device_type", R.string.detail_device_type, getPhoneType()));
        rows.add(DetailRow.value("root", R.string.detail_root_access, yesNo(isRooted())));
        rows.add(DetailRow.value("security_patch", R.string.detail_security_patch,
                Build.VERSION.SECURITY_PATCH));
        rows.add(DetailRow.value("kernel", R.string.detail_kernel,
                System.getProperty("os.version")));

        return rows;
    }

    private String formatBuildTime() {
        return new SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(Build.TIME));
    }

    /**
     * Kiểm tra dấu hiệu máy đã root bằng cách tìm tệp {@code su}.
     * Đây là cách phổ biến nhưng không tuyệt đối: máy root có ẩn su thì không phát hiện được.
     */
    private boolean isRooted() {
        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) return true;

        for (String path : SU_PATHS) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    // ==================== CPU ====================

    private List<DetailRow> buildCpu() {
        List<DetailRow> rows = new ArrayList<>();
        final int cores = CpuInfoReader.getCoreCount();
        final String chipName = CpuInfoReader.getChipName();

        rows.add(DetailRow.header("processor_name", R.string.detail_processor_name,
                R.drawable.ic_logo_chip,
                chipName == null ? "unknown" : chipName));

        rows.add(DetailRow.value("cpu_hardware", R.string.detail_cpu_hardware, Build.HARDWARE));
        rows.add(DetailRow.value("processor", R.string.detail_processor,
                CpuInfoReader.getArchitecture()));
        rows.add(DetailRow.value("cores", R.string.detail_cores, String.valueOf(cores)));

        // "Running CPUs": mỗi nhân một dòng, nhân đang ngủ đọc ra 0
        List<String> coreLines = new ArrayList<>(cores);
        for (int core = 0; core < cores; core++) {
            final long khz = CpuInfoReader.getCurrentFrequencyKhz(core);
            coreLines.add(String.format(Locale.US, "Core %d   %s", core,
                    khz > 0 ? (khz / 1000) + " MHz"
                            : appContext.getString(R.string.detail_core_idle)));
        }
        rows.add(DetailRow.multi("running_cpus", R.string.detail_running_cpus, coreLines));

        rows.add(DetailRow.value("abi", R.string.detail_abi, joinAbis()));
        rows.add(DetailRow.value("cpu_temp", R.string.detail_cpu_temperature, readCpuTemperature()));

        return rows;
    }

    private String joinAbis() {
        final String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) return null;
        return String.join(" , ", abis);
    }

    /**
     * Nhiệt độ CPU đọc từ vùng cảm biến nhiệt.
     *
     * <p>Không có API công khai và nhiều máy chặn hẳn các node này, nên giá trị
     * trả về thường là 0 – giống đúng như trong bản thiết kế. Đơn vị cũng không
     * thống nhất: có máy trả về milli-độ (37000), có máy trả về độ (37).
     */
    private String readCpuTemperature() {
        for (String path : CPU_TEMP_PATHS) {
            Long raw = FileUtils.readLong(path);
            if (raw == null) continue;

            final float celsius = raw > 1000 ? raw / 1000f : raw;
            if (celsius > 0 && celsius < 150) {
                return String.format(Locale.US, "%.0f", celsius);
            }
        }
        return "0";
    }

    // ==================== DISPLAY ====================

    private List<DetailRow> buildDisplay() {
        List<DetailRow> rows = new ArrayList<>();

        WindowManager windowManager =
                (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return rows;

        final Display display = windowManager.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        rows.add(DetailRow.value("density", R.string.detail_density,
                String.format(Locale.US, "%d dpi (%s)",
                        metrics.densityDpi, densityBucket(metrics.densityDpi))));

        rows.add(DetailRow.value("resolution", R.string.detail_resolution,
                String.format(Locale.US, "%d x %d Pixels",
                        Math.max(metrics.widthPixels, metrics.heightPixels),
                        Math.min(metrics.widthPixels, metrics.heightPixels))));

        rows.add(DetailRow.value("physical_size", R.string.detail_physical_size,
                screenSizeInches(metrics)));

        rows.add(DetailRow.value("refresh", R.string.detail_refresh_rate,
                String.format(Locale.US, "%.1f Hz", display.getRefreshRate())));

        return rows;
    }

    /** Đường chéo màn hình theo inch, tính từ số điểm ảnh và mật độ thật. */
    private String screenSizeInches(@NonNull DisplayMetrics metrics) {
        if (metrics.xdpi <= 0 || metrics.ydpi <= 0) return null;
        final double width = metrics.widthPixels / metrics.xdpi;
        final double height = metrics.heightPixels / metrics.ydpi;
        return String.format(Locale.US, "%.2f", Math.hypot(width, height));
    }

    private String densityBucket(int densityDpi) {
        if (densityDpi <= DisplayMetrics.DENSITY_MEDIUM) return "MDPI";
        if (densityDpi <= DisplayMetrics.DENSITY_HIGH) return "HDPI";
        if (densityDpi <= DisplayMetrics.DENSITY_XHIGH) return "XHDPI";
        if (densityDpi <= DisplayMetrics.DENSITY_XXHIGH) return "XXHDPI";
        return "XXXHDPI";
    }

    private boolean isAutoBrightness() {
        ContentResolver resolver = appContext.getContentResolver();
        return Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    // ==================== NETWORK ====================

    /**
     * Thông tin mạng.
     *
     * <p><b>Lưu ý về hai trường Wi-Fi SSID và MAC:</b> từ Android 10, hệ thống trả
     * về giá trị giả ({@code <unknown ssid>} và {@code 02:00:00:00:00:00}) cho ứng
     * dụng không có quyền vị trí, vì hai thông tin này có thể dùng để định danh
     * người dùng. App vẫn hiển thị chúng đúng như bản thiết kế, nhưng giá trị sẽ
     * luôn là giá trị giả nếu không xin quyền vị trí.
     */
    private List<DetailRow> buildNetwork() {
        List<DetailRow> rows = new ArrayList<>();

        rows.add(DetailRow.value("available", R.string.detail_network_available,
                yesNo(isNetworkAvailable())));
        rows.add(DetailRow.value("net_type", R.string.detail_network_type, getNetworkTypeName()));

        WifiManager wifi = (WifiManager) appContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            rows.add(DetailRow.value("wifi_enabled", R.string.detail_wifi_enabled,
                    yesNo(wifi.isWifiEnabled())));

            final WifiInfo info = wifi.getConnectionInfo();
            if (info != null) {
                rows.add(DetailRow.value("wifi_bssid", R.string.detail_wifi_bssid, info.getBSSID()));
                rows.add(DetailRow.value("wifi_speed", R.string.detail_wifi_link_speed,
                        info.getLinkSpeed() > 0
                                ? info.getLinkSpeed() + " Mbps"
                                : null));
                rows.add(DetailRow.value("wifi_ssid", R.string.detail_wifi_ssid, info.getSSID()));
                rows.add(DetailRow.value("wifi_mac", R.string.detail_wifi_mac,
                        info.getMacAddress()));
            }
        }

        rows.add(DetailRow.value("ipv4", R.string.detail_ip_address, getIpAddress(true)));
        rows.add(DetailRow.value("ipv6", R.string.detail_ipv6_address, getIpAddress(false)));

        TelephonyManager telephony =
                (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony != null) {
            final String simCountry = telephony.getSimCountryIso();
            rows.add(DetailRow.value("sim_country", R.string.detail_sim_country,
                    simCountry == null ? null : simCountry.toUpperCase(Locale.US)));
            rows.add(DetailRow.value("carrier", R.string.detail_sim_carrier,
                    telephony.getNetworkOperatorName()));
        }
        return rows;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;

        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private String getNetworkTypeName() {
        ConnectivityManager manager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return null;

        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        if (capabilities == null) return appContext.getString(R.string.detail_network_none);

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wifi";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return null;
    }

    /** Địa chỉ IP của giao diện mạng đang hoạt động. */
    private String getIpAddress(boolean ipv4) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress()) continue;

                    final boolean matches = ipv4
                            ? address instanceof Inet4Address
                            : address instanceof Inet6Address;
                    if (!matches) continue;

                    final String host = address.getHostAddress();
                    if (host == null) continue;
                    // Địa chỉ IPv6 kèm hậu tố tên giao diện (%wlan0), cắt bỏ cho gọn
                    final int scopeIndex = host.indexOf('%');
                    return scopeIndex > 0 ? host.substring(0, scopeIndex) : host;
                }
            }
        } catch (Exception e) {
            Logger.d(TAG, "Không lấy được địa chỉ IP: " + e.getClass().getSimpleName());
        }
        return null;
    }

    private String getPhoneType() {
        TelephonyManager telephony =
                (TelephonyManager) appContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony == null) return null;

        switch (telephony.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_GSM:
                return "GSM";
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.PHONE_TYPE_SIP:
                return "SIP";
            default:
                return appContext.getString(R.string.detail_network_none);
        }
    }

    // ==================== SENSOR ====================

    /** Danh sách cảm biến; nhãn là tên do nhà sản xuất đặt nên không dịch được. */
    private List<DetailRow> buildSensor() {
        SensorManager manager =
                (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) return Collections.emptyList();

        List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
        List<DetailRow> rows = new ArrayList<>(sensors.size());

        for (int i = 0; i < sensors.size(); i++) {
            final Sensor sensor = sensors.get(i);
            rows.add(DetailRow.sensor("sensor_" + i, sensor.getName(), sensor.getVendor()));
        }
        return rows;
    }

    // ==================== Tiện ích ====================

    private String yesNo(boolean value) {
        return appContext.getString(value ? R.string.detail_yes : R.string.detail_no);
    }
}
