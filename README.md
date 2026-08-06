# ChargManagement (Charging Master)

Ứng dụng Android theo dõi và tối ưu quá trình sạc pin. Viết **100% Java**, giao diện
XML, kiến trúc MVC.

---

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.9 (qua wrapper) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |

```bash
gradlew assembleDebug
```

```bash
gradlew testDebugUnitTest lintDebug
```

---

## 2. Cấu trúc mã nguồn

```
com.dung.chargmagagement
├── common/            Tiện ích dùng chung
│   ├── AppExecutors       3 pool thread (disk / network / sampler) + trả kết quả về UI
│   ├── PrefManager        Nơi duy nhất đọc ghi SharedPreferences
│   ├── LocaleManager      Đa ngôn ngữ (per-app language của AndroidX)
│   ├── DateUtils          Khoá ngày yyyyMMdd theo múi giờ địa phương
│   ├── FormatUtils        Định dạng nhiệt độ, dung lượng, thời lượng
│   ├── FileUtils          Đọc sysfs / procfs an toàn
│   └── Logger             Bọc android.util.Log, tự tắt ở bản release
│
├── model/             Tầng Model của MVC (không biết gì về giao diện)
│   ├── battery/           Đo pin: BatteryMonitor, CurrentReader, CurrentCalibration…
│   ├── entity/ dao/ db/   Room: phiên sạc, điểm đo, khoảng màn hình
│   ├── repository/        BatteryRepository (cửa duy nhất cho UI), SessionRecorder
│   ├── stats/             UsageCalculator - toàn bộ công thức thống kê
│   ├── power/             Phát hiện hạng mục tốn điện, xếp loại tốc độ sạc
│   ├── device/            Thông tin thiết bị, CPU, bộ nhớ
│   ├── alarm/             Logic báo động sạc
│   ├── ads/ vip/          AdMob và Google Play Billing
│   └── ui/                Model hiển thị (InfoItem, DetailRow, CalendarDay…)
│
├── controller/        Tầng Controller + View
│   ├── base/              BaseActivity<VB>, BaseFragment<VB>
│   ├── home/              HomeActivity + 3 tab
│   ├── power/ detail/ history/ alarm/ tools/ settings/ vip/
│   ├── adapter/           Các RecyclerView adapter
│   └── widget/            4 custom view tự vẽ bằng Canvas
│
└── service/           Foreground service theo dõi sạc, receiver, dọn thông báo
```

**Nguyên tắc**: Controller không chứa công thức. Mọi tính toán nằm ở `model/stats`,
`model/power`, `model/alarm` — đó cũng là lý do các phần này kiểm thử được bằng
unit test thường (78 test, không cần thiết bị).

---

## 3. Những chỗ phải sửa trước khi phát hành

| # | Việc | Vị trí |
|---|---|---|
| 1 | Thay **App ID** AdMob thật | `AndroidManifest.xml` → `com.google.android.gms.ads.APPLICATION_ID` |
| 2 | Thay **3 mã đơn vị quảng cáo** | `model/ads/AdConfig.java` → các hằng `*_RELEASE` |
| 3 | Tạo sản phẩm **`charg_vip_lifetime`** | Google Play Console |
| 4 | Tạo kho khoá ký APK | Xem `keystore.properties.example` |
| 5 | Đổi `versionCode` / `versionName` | `app/build.gradle.kts` |

Mã quảng cáo hiện tại là **mã thử nghiệm của Google**. Bản debug luôn dùng mã thử
nghiệm dù có thay mã thật hay không — Google coi việc tự bấm quảng cáo của mình là
gian lận và có thể khoá vĩnh viễn tài khoản AdMob.

### Ký APK

```bash
keytool -genkey -v -keystore charg-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias charg
```

Sao chép `keystore.properties.example` thành `keystore.properties`, điền thông tin,
rồi chạy `gradlew assembleRelease`. File `.jks` và `keystore.properties` đã nằm trong
`.gitignore`.

> **Giữ kỹ file `.jks` và mật khẩu.** Mất nó là không cập nhật được app đã phát hành,
> buộc phải đăng một ứng dụng mới hoàn toàn.

---

## 4. Giới hạn kỹ thuật đã biết

Những điều sau là **giới hạn của Android**, không phải lỗi:

1. **Dòng điện mỗi hãng báo một kiểu.** Đơn vị (µA hoặc mA) và dấu (+/−) không được
   chuẩn hoá. `CurrentCalibration` tự học cả hai từ trạng thái sạc đã biết chắc rồi
   lưu lại. Máy không hỗ trợ đọc dòng sẽ hiển thị dấu gạch.

2. **App không được tự tắt Wi-Fi / Bluetooth / GPS** từ Android 10. Màn Phát hiện sạc
   chỉ phát hiện và mở trang Cài đặt tương ứng.

3. **Wifi SSID và địa chỉ MAC luôn trả về giá trị giả** (`<unknown ssid>`,
   `02:00:00:00:00:00`) nếu không có quyền vị trí. Hai dòng này giữ theo bản thiết kế.

4. **Nhiệt độ CPU thường đọc ra 0.** Nhiều ROM chặn hẳn node cảm biến nhiệt. Android
   chỉ bảo đảm đọc được nhiệt độ *pin*.

5. **`/proc/stat` có thể bị chặn** trên một số ROM → màn Sử dụng CPU báo rõ "không đọc
   được" thay vì hiện 0%.

6. **Dung lượng thiết kế pin** đọc qua reflection `PowerProfile`; Android 14+ thường
   chặn. Người dùng nhập tay được ở tab Sử dụng pin.

7. **Khoảng màn hình bật/tắt chỉ ghi được khi tiến trình còn sống** (app đang mở hoặc
   đang sạc). Số liệu %/h là tỉ lệ trung bình nên khoảng trống không làm sai kết quả.

---

## 5. Cách app tiết kiệm pin

Đây là app theo dõi pin nên bản thân nó không được hao pin:

- `BatteryMonitor` chỉ chạy khi có màn hình đang hiển thị hoặc service đang chạy;
  hết listener là gỡ receiver và huỷ tác vụ lấy mẫu ngay.
- Chu kỳ lấy mẫu tự giãn: **2 giây khi sạc / 5 giây khi dùng pin**.
- Foreground service **chỉ sống lúc cắm sạc**, rút là tự dừng.
- Điểm đo chỉ ghi vào database khi đổi 1% pin hoặc sau 60 giây (nếu ghi hết thì một
  ngày sinh hơn 40.000 dòng).
- Dữ liệu cũ hơn 60 ngày tự dọn, tối đa một lần mỗi ngày.
- Các màn có lấy mẫu định kỳ (CPU, kiểm tra nguồn) dừng hẳn ở `onPause()`.

---

## 6. Kiểm thử

```bash
gradlew testDebugUnitTest
```

78 unit test, tập trung vào phần dễ sai nhất:

| Lớp test | Nội dung |
|---|---|
| `CurrentCalibrationTest` | Nhận diện đơn vị và dấu của dòng điện theo hãng |
| `CurrentStatsTest` | Lọc nhiễu bằng trung vị, cửa sổ trượt |
| `UsageCalculatorTest` | Tốc độ tiêu hao, ước tính dung lượng, thời gian sạc đầy |
| `MonthGridBuilderTest` | Lưới lịch tháng, tuần bắt đầu Thứ 2 |
| `ChargeAlarmCheckerTest` | Chống lặp cảnh báo, vùng đệm nhiệt độ |
| `PowerOptimizationTest` | Xếp loại tốc độ sạc, thời gian rút ngắn |
| `DetailSectionTest`, `FormatUtilsTest` | Điều phối tab, định dạng hiển thị |

---

## 7. Đa ngôn ngữ

Mặc định **tiếng Anh** (`values/`), bản dịch tiếng Việt ở `values-vi/`. Người dùng
đổi ngôn ngữ ở **Cài đặt → Ngôn ngữ**. Thêm ngôn ngữ mới:

1. Tạo thư mục `values-xx/` với `strings.xml`, `arrays.xml`, `plurals.xml`
2. Thêm mã ngôn ngữ vào `res/xml/locales_config.xml`
3. Thêm một dòng vào mảng `LANGUAGE_TAGS` trong `LanguageActivity`
