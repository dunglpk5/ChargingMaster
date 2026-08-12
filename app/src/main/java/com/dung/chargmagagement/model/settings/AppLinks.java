package com.dung.chargmagagement.model.settings;

/**
 * Các đường dẫn/địa chỉ liên hệ ra ngoài app: phản hồi, chính sách bảo mật.
 *
 * <p><b>Giá trị hiện tại là placeholder.</b> Phải thay bằng địa chỉ thật của bạn
 * trước khi phát hành — xem mục "Những chỗ phải sửa trước khi phát hành" trong
 * README. Tách thành hằng số ở một chỗ để không phải lục code khi cần đổi.
 */
public final class AppLinks {

    /** Email nhận phản hồi, dùng để mở app Gửi thư có sẵn nội dung điền trước. */
    public static final String FEEDBACK_EMAIL = "support@example.com";

    /** Trang chính sách bảo mật, bắt buộc phải có khi đăng lên Google Play. */
    public static final String PRIVACY_POLICY_URL = "https://example.com/privacy-policy";

    private AppLinks() {
    }
}
