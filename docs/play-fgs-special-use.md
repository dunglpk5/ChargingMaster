# Giải trình Foreground Service `specialUse` — Play Console

Dùng cho mục **App content → Foreground service permissions** (hoặc form
"Foreground service type declaration") khi nộp bản có
`android.permission.FOREGROUND_SERVICE_SPECIAL_USE`.

Nội dung dưới đây bám đúng code hiện tại: service khai báo ở
`AndroidManifest.xml` là `.service.BatteryLogService`, `foregroundServiceType="specialUse"`,
kèm `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.

Trước khi nộp, thay các chỗ trong `[...]` và kiểm tra lại phần "Số liệu" cho khớp
bản phát hành.

---

## 1. Bản tiếng Anh (điền vào form)

### Which foreground service type does your app use?

`specialUse`, on exactly one service:
`com.dung.chargmagagement.service.BatteryLogService`.

The app has no other foreground service. The charge alarm — the other feature that
needs to observe the battery while the app is closed — was deliberately rebuilt on
`AlarmManager` so that it needs no foreground service at all (see "What we removed"
below).

### What does the foreground service do?

Charging Master is a battery monitoring app. Its single foreground service records
the device battery level continuously so the app can draw an accurate day-by-day
battery history chart (00:00–24:00) and compute discharge and charging statistics.

It performs three tasks, all of which require an uninterrupted process:

1. **Continuous battery sampling.** It registers a runtime receiver for
   `ACTION_BATTERY_CHANGED` and stores each level change to a local Room database.
   This is the data source for the battery history chart.
2. **Session recording.** It records charging sessions and screen-on/screen-off
   sessions, which produce the app's discharge rate (%/h), battery health estimate
   and time-to-empty figures.
3. **Live status notification.** The ongoing notification shows current draw,
   voltage, average current, screen-on/screen-off time and awake/deep-sleep time,
   refreshed as new samples arrive.

### What we removed

Two features used to depend on a foreground service and no longer do.

**The charge alarm** (notify me at 80%, when full, or when the battery overheats) had
its own foreground service. That service no longer exists.

The alarm now runs on `AlarmManager`. Because Doze does not apply while a device is
charging, the app can sleep instead of watching: on `ACTION_POWER_CONNECTED` it
estimates when the threshold will be reached from the observed charging rate, sets a
single alarm, and on wake-up reads the level from the sticky `ACTION_BATTERY_CHANGED`
intent and either notifies or re-estimates. The check interval is capped at 5 minutes
so a sudden change in charging speed cannot overshoot by more than one cycle. No
process stays alive, no persistent notification is shown, and no foreground service
permission is involved. Temperature monitoring while unplugged uses
`setAndAllowWhileIdle` at a 10-minute interval, which needs no special permission
either.

**Charge session history and battery health** used to be derived from the continuous
sample stream. They are now recorded from `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED` — two of the few broadcasts still delivered to
manifest-declared receivers — with the charge amount taken from the hardware coulomb
counter (`BATTERY_PROPERTY_CHARGE_COUNTER`) read once at each end of the session. That
is both more accurate than integrating current over time and completely free of any
background process, so the charge count, average charge per session, last-charge
summary and battery health estimate all work with the foreground service switched off.

What remains genuinely dependent on the foreground service is the dense day-by-day
chart, the screen-on versus screen-off discharge split, and the live status
notification.

### Why is a foreground service required?

Most battery activity happens while the app is closed — the phone is charging on a
desk, or in a pocket with the screen off. A background process is killed within
minutes, and `ACTION_BATTERY_CHANGED` is excluded from manifest-declared receivers
since Android 8, so there is no way to observe level changes without a live process.

Without a foreground service the chart would only contain isolated points around the
moments the user happened to open the app, and the statistics would be computed from
a biased sample.

`AlarmManager`, which we do use for the charge alarm, cannot replace it here: the
chart needs every level change over a full day, not a check at a predicted moment.
A periodic `WorkManager` job cannot either — its 15-minute floor plus Doze batching
leaves gaps of one to two hours overnight, exactly where users look most.

### Why do the existing foreground service types not fit?

| Type | Why it does not apply |
| --- | --- |
| `dataSync` | Nothing is transferred to or from a server; all data stays on device. Android 15 also caps this type at 6 hours per 24 hours, which would leave daily gaps in a chart whose whole purpose is continuity. |
| `systemExempted` | Reserved for narrow exemptions (device management, VPN, and similar) that this app does not qualify for. |
| `mediaPlayback`, `mediaProjection`, `camera`, `microphone`, `location`, `connectedDevice`, `phoneCall`, `remoteMessaging` | The app uses none of these capabilities. It holds no location, camera, microphone or media permissions. |
| `health` | Refers to user health and fitness data, not device hardware telemetry. |
| `shortService` | Capped at roughly 3 minutes and intended for short completion work; battery history requires all-day observation. |

`specialUse` is the only remaining type that describes device battery telemetry
recorded for the user's own device.

### Battery and privacy impact

- The service does **not** poll. It has no timer, no repeating alarm and holds no
  wake lock. It sleeps until the system itself broadcasts a battery change, and
  further throttles processing to at most one sample per 15 seconds.
- Data collected is limited to battery level, voltage, current, temperature,
  charging state and screen state. It is written only to a local Room database and
  is never uploaded; the app has no server component that receives it.
- The ongoing notification is always visible while the service runs, so the user
  can see the service at any time and open the app from it.

### User control

The service is **off by default**. It never starts on install or on first launch.

Settings exposes the three things it does as three independent switches — the battery
level chart, the screen-on/off statistics, and the live figures in the notification —
each with a one-line explanation of what it records. The service starts only when at
least one of the two recording switches is on, and stops the moment both are off, so a
user who wants the chart but not the screen statistics runs strictly less background
work. The choices survive reboot, so a device that restarts does not silently bring the
service back.

No other feature can start it. In particular the charge alarm does not, since it no
longer uses a service.

### Demo

[Link video màn hình 30–60 giây, quyền xem công khai: mở app → tab Sử dụng pin →
biểu đồ theo ngày → thông báo thường trú → màn Báo động sạc.]

---

## 2. Bản tiếng Việt (để đối chiếu nội bộ)

**Loại đã khai:** `specialUse`, trên đúng một service — `BatteryLogService`.

**Service làm gì:** ghi mức pin liên tục vào cơ sở dữ liệu cục bộ để dựng biểu đồ pin
theo ngày, tính tốc độ tiêu hao và độ chai pin, và hiển thị thông báo thường trú với
số liệu trực tiếp.

**Đã cắt được gì:** hai chức năng trước đây phải dựa vào foreground service, nay không
cần nữa.
- *Báo động sạc* chuyển sang `AlarmManager` — máy đang cắm sạc thì Doze không áp dụng
  nên chỉ cần hẹn giờ dậy đúng lúc sắp chạm ngưỡng.
- *Lịch sử phiên sạc và độ chai pin* chuyển sang hai mốc `POWER_CONNECTED` /
  `POWER_DISCONNECTED` (manifest receiver vẫn nhận được), lượng nạp lấy từ hiệu số bộ
  đếm cu-lông của phần cứng — chính xác hơn tích phân dòng điện và không cần tiến
  trình nào sống.

Phần thật sự còn phụ thuộc service chỉ còn: biểu đồ dày theo ngày, tách tiêu hao màn
bật / màn tắt, và thông báo số liệu trực tiếp.

**Vì sao phải chạy nền:** phần lớn thời gian pin tụt là lúc app đã đóng, máy nằm
trong túi và màn hình tắt. Tiến trình nền thường bị hệ thống thu hồi sau ít phút,
còn `ACTION_BATTERY_CHANGED` thì từ Android 8 không gửi tới receiver khai trong
manifest — không có tiến trình sống thì không ai ghi được gì.

**Vì sao không dùng loại khác:**
- `dataSync` — không có dữ liệu nào được đồng bộ với máy chủ, và Android 15 chặn
  loại này ở 6 tiếng mỗi 24 tiếng, tức là biểu đồ đứt quãng mỗi ngày.
- `systemExempted` — chỉ dành cho vài diện miễn trừ hẹp mà app không thuộc.
- `shortService` — trần khoảng 3 phút.
- Các loại còn lại gắn với camera, micro, vị trí, media, cuộc gọi… app không dùng
  và cũng không xin những quyền đó.

**Ảnh hưởng pin:** service không hẹn giờ, không giữ wakelock, chỉ chờ broadcast của
hệ thống và tiết chế xử lý tối đa 15 giây một lần.

**Người dùng kiểm soát:** mặc định tắt. Service chỉ chạy khi người dùng tự bật công
tắc "Ghi lịch sử pin" trong Cài đặt; tắt là dừng ngay, và lựa chọn đó sống qua cả
lần khởi động máy sau.

**Quyền riêng tư:** chỉ thu thập mức pin, điện áp, dòng điện, nhiệt độ, trạng thái
sạc và trạng thái màn hình; ghi vào Room cục bộ, không tải lên bất kỳ máy chủ nào.

---

## 3. Việc cần làm kèm theo

- [ ] Quay video demo và để chế độ ai có link cũng xem được (Google thường yêu cầu).
- [ ] Điền mục **Data safety** khớp với phần "Battery and privacy impact" ở trên:
      không chia sẻ, không thu thập lên máy chủ.
- [ ] Kiểm tra `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` trong manifest mô tả đúng một câu
      như trong form — Google có đối chiếu hai chỗ này.
- [ ] Nếu bị từ chối: đường lùi là bỏ foreground service, chuyển sang WorkManager
      chu kỳ 15 phút; chấp nhận biểu đồ thưa hơn và báo động sạc trễ.
