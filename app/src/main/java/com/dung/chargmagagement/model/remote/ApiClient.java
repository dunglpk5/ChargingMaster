package com.dung.chargmagagement.model.remote;

import com.dung.chargmagagement.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Hạ tầng networking (Retrofit + OkHttp + Gson).
 *
 * <p>Hiện chưa có endpoint nào — lớp này dựng sẵn để khi có backend chỉ cần
 * khai báo thêm interface service và gọi {@link #create(Class)}.
 * Retrofit được khởi tạo lazy nên nếu app không gọi mạng thì không tốn tài nguyên.
 */
public final class ApiClient {

    /** Thay bằng domain thật khi có backend. */
    private static final String BASE_URL = "https://example.com/";

    private static final long TIMEOUT_SECONDS = 20L;

    private static volatile Retrofit retrofit;

    private ApiClient() {
    }

    /** Tạo implementation cho một interface service của Retrofit. */
    public static <T> T create(Class<T> service) {
        return retrofit().create(service);
    }

    private static Retrofit retrofit() {
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(buildOkHttp())
                            .addConverterFactory(GsonConverterFactory.create(buildGson()))
                            .build();
                }
            }
        }
        return retrofit;
    }

    private static OkHttpClient buildOkHttp() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        if (BuildConfig.LOG_ENABLED) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
        return builder.build();
    }

    private static Gson buildGson() {
        return new GsonBuilder()
                .setLenient()
                .create();
    }
}
