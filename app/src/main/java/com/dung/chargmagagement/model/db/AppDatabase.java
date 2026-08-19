package com.dung.chargmagagement.model.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.dung.chargmagagement.model.dao.BatterySampleDao;
import com.dung.chargmagagement.model.dao.ChargingSessionDao;
import com.dung.chargmagagement.model.dao.ScreenSessionDao;
import com.dung.chargmagagement.model.entity.BatterySampleEntity;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.entity.ScreenSessionEntity;

/**
 * Database của app.
 *
 * <p>Khởi tạo lazy: chỉ mở file DB khi thật sự có truy vấn đầu tiên, giúp thời
 * gian mở app không bị chậm vì Room.
 *
 * <p><b>Về migration:</b> schema được export ra thư mục {@code app/schemas} (cấu
 * hình trong build.gradle.kts). Khi đổi cấu trúc bảng phải tăng {@link #VERSION}
 * và viết Migration tương ứng – tuyệt đối không dùng
 * {@code fallbackToDestructiveMigration()} ở bản phát hành vì sẽ xoá sạch lịch
 * sử sạc của người dùng.
 */
@Database(
        entities = {
                ChargingSessionEntity.class,
                BatterySampleEntity.class,
                ScreenSessionEntity.class
        },
        version = AppDatabase.VERSION,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 2;
    private static final String DB_NAME = "charg.db";

    /**
     * v2: thêm {@code charge_counter_start} vào bảng phiên sạc.
     *
     * <p>Phiên cũ để nguyên giá trị 0 – nghĩa là "không có bộ đếm", và tầng thống kê
     * tự quay về cách tính theo dòng trung bình như trước.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE charging_session "
                    + "ADD COLUMN charge_counter_start INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase instance;

    public abstract ChargingSessionDao chargingSessionDao();

    public abstract BatterySampleDao batterySampleDao();

    public abstract ScreenSessionDao screenSessionDao();

    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            // Ghi mẫu pin diễn ra liên tục ở nền; WAL cho phép đọc
                            // (vẽ biểu đồ) song song với ghi mà không chặn nhau
                            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
