// Build script cấp root - chỉ khai báo plugin dùng chung cho các module con
plugins {
    alias(libs.plugins.android.application) apply false
}
