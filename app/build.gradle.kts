import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Thông tin kho khoá ký APK, đọc từ file keystore.properties ở thư mục gốc.
 *
 * File này KHÔNG được commit lên git (đã có trong .gitignore) vì chứa mật khẩu.
 * Nếu chưa có file, bản release vẫn build được nhưng không được ký - dùng để
 * kiểm thử R8, không cài lên máy hay tải lên Play được.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasKeystore = keystoreProperties.containsKey("storeFile")

android {
    namespace = "com.dung.chargmagagement"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dung.chargmagagement"
        minSdk = 26
        targetSdk = 35

        // --- CHỈNH SỬA VERSION Ở ĐÂY KHI CẬP NHẬT APP ---
        versionCode = 1
        versionName = "1.0.0"
        // -----------------------------------------------

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Lọc tài nguyên ngôn ngữ: chỉ những mã liệt kê ở đây mới được đóng gói vào
        // APK, phần còn lại bị loại bỏ dù thư mục values-<mã> vẫn nằm trong mã nguồn.
        // Danh sách này phải khớp với res/xml/locales_config.xml, thiếu một mã là
        // người dùng chọn ngôn ngữ đó xong lại thấy giao diện quay về mặc định.
        //
        // Lưu ý cách viết mã ở đây là kiểu thư mục tài nguyên: "in" cho tiếng
        // Indonesia và "zh-rCN" cho tiếng Trung giản thể.
        resourceConfigurations += listOf(
            "en", "vi", "es", "pt-rBR", "fr", "de", "ru",
            "in", "hi", "zh-rCN", "ja", "ko", "tr", "ar"
        )

        javaCompileOptions {
            annotationProcessorOptions {
                // Cho phép Room export schema phục vụ migration
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "LOG_ENABLED", "true")
        }
        release {
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "LOG_ENABLED", "false")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Bắt buộc: mã nguồn có comment tiếng Việt, mặc định javac trên Windows
        // dùng windows-1252 sẽ báo lỗi "unmappable character"
        encoding = "UTF-8"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }

    // --- ĐOẠN CODE THÊM VÀO ĐỂ TỰ ĐỘNG ĐỔI TÊN FILE APK ---
    applicationVariants.all {
        val variantName = name
        val appVersion = versionName
        val appName = "ChargeManagement" // Bạn có thể đổi tên app ở đây

        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            // Kết quả xuất ra sẽ có dạng: ChargeManagement-release-v1.0.0.apk
            outputImpl.outputFileName = "${appName}-${variantName}-v${appVersion}.apk"
        }
    }
    // --------------------------------------------------------
}

// Đảm bảo mọi tác vụ biên dịch Java (kể cả unit test) đều đọc file theo UTF-8
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // UI
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)

    // Room - lưu lịch sử sạc / mẫu pin
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Networking (chưa dùng endpoint nào - hạ tầng dựng sẵn)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Quảng cáo AdMob và thanh toán trong ứng dụng
    implementation(libs.play.services.ads)
    implementation(libs.billing)

    // Ảnh
    implementation(libs.glide.core)
    annotationProcessor(libs.glide.compiler)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}