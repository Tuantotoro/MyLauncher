# My Launcher — bản đầu tiên

Màn hình chính tối giản kiểu Niagara: đồng hồ lớn, ngày + % pin, ô tìm ứng dụng
(gõ không dấu vẫn tìm được), danh sách ứng dụng có thanh chữ cái A–Z bên phải,
và mục "Yêu thích" ở đầu danh sách.

## Cách chạy thử
1. Cài Android Studio (bản mới nhất) từ developer.android.com/studio.
2. Giải nén file zip này. Mở Android Studio → Open → chọn thư mục MyLauncher.
3. Chờ Android Studio tải thư viện (lần đầu có thể mất 5–15 phút, cần mạng).
4. Cắm điện thoại qua cáp USB, bật "Tùy chọn nhà phát triển" → "Gỡ lỗi USB".
5. Bấm nút ▶ Run (màu xanh) để cài app lên điện thoại.
6. Bấm nút Home → điện thoại hỏi chọn ứng dụng màn hình chính → chọn "My Launcher".
   (Hoặc: Cài đặt → Ứng dụng → Ứng dụng mặc định → Ứng dụng màn hình chính.)

Muốn quay lại launcher cũ: vào lại mục trên và chọn launcher gốc (One UI Home...).

## Cách dùng
- Chạm tên app để mở. Nhấn giữ để: thêm/bỏ yêu thích, xem thông tin, gỡ cài đặt.
- Chạm hoặc vuốt thanh chữ cái bên phải để nhảy nhanh.
- Gõ tên vào ô tìm kiếm, bấm "Đi" trên bàn phím để mở kết quả đầu tiên.
- Chạm đồng hồ → mở Báo thức. Chạm dòng ngày → mở Lịch.
- Bấm Home khi đang ở launcher → xoá tìm kiếm và cuộn lên đầu.

## Các file chính
- app/src/main/AndroidManifest.xml — khai báo app là màn hình chính (HOME).
- AppRepository.kt — đọc danh sách app, lưu yêu thích, mở/gỡ app.
- LauncherScreen.kt — toàn bộ giao diện (đồng hồ, tìm kiếm, danh sách, thanh A–Z).
- MainActivity.kt — điểm khởi động của app.
