# TÀI LIỆU HƯỚNG DẪN TÀI KHOẢN VÀ KỊCH BẢN KIỂM THỬ HỆ THỐNG

Tập dữ liệu mẫu (Seed Data) đã được thiết lập sẵn trong Database và tự động đồng bộ khi ứng dụng khởi chạy.

---

## 1. BẢNG TÀI KHOẢN TEST THEO PHÂN QUYỀN

| Vai trò (Role) | Username | Password | Họ và Tên | Khoa / Chuyên ngành | Mã số / Mã GV | Mục đích kiểm thử |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **QUẢN TRỊ VIÊN** | `admin` | `admin123` | Quản Trị Viên Hệ Thống | Toàn trường | `ADMIN_01` | Quản trị tài khoản, khoa, đợt, phân quyền toàn hệ thống |
| **TRƯỞNG KHOA CNTT** | `head_cntt` | `123456` | TS. Nguyễn Văn Thắng | Khoa Công nghệ Thông tin | `GV_HEAD_CNTT` | Duyệt đề tài CNTT, tạo đợt CNTT, phân công hội đồng CNTT |
| **TRƯỞNG KHOA QTKD** | `head_qtkd` | `123456` | PGS.TS. Trần Thị Mai | Khoa Quản trị Kinh doanh | `GV_HEAD_QTKD` | Duyệt đề tài QTKD, quản lý đợt QTKD (kiểm tra phân tách dữ liệu khoa) |
| **GIẢNG VIÊN CNTT (1)** | `gv_cntt01` | `123456` | ThS. Lê Hoàng Long | Khoa Công nghệ Thông tin | `GV_IT_01` | GVHD chính: Đã có nhóm hướng dẫn & đề tài mẫu |
| **GIẢNG VIÊN CNTT (2)** | `gv_cntt02` | `123456` | TS. Phạm Minh Tuấn | Khoa Công nghệ Thông tin | `GV_IT_02` | GVPB: Chấm điểm phản biện đề tài |
| **ỦY VIÊN HỘI ĐỒNG CNTT** | `gv_cntt04` | `123456` | ThS. Trần Anh Dũng | Khoa Công nghệ Thông tin | `GV_IT_04` | Hội đồng bảo vệ: Chấm điểm thành viên hội đồng cho Đợt 4 |
| **GIẢNG VIÊN MỚI (TRỐNG)** | `gv_cntt06` | `123456` | TS. Phạm Quốc Bảo | Khoa Công nghệ Thông tin | `GV_IT_06` | **Trống 100%:** Test đề xuất đề tài mới từ đầu (Đợt 1) |
| **GIẢNG VIÊN QTKD** | `gv_qtkd01` | `123456` | ThS. Hoàng Thu Thảo | Khoa Quản trị Kinh doanh | `GV_BA_01` | Đề xuất đề tài QTKD, kiểm tra giảng viên chỉ quản lý đề tài khoa mình |
| **SINH VIÊN MỚI (1)** | `sv_cntt01` | `123456` | Nguyễn Văn An | CNTT (Khoa CNTT) | `20110001` | **Trống 100% (Chưa nhóm):** Test chọn đề tài trống ở Đợt 2, tạo nhóm, mời bạn |
| **SINH VIÊN MỚI (2)** | `sv_cntt02` | `123456` | Trần Thị Bình | CNTT (Khoa CNTT) | `20110002` | **Trống 100% (Chưa nhóm):** Test nhận thông báo & chấp nhận lời mời nhóm của `sv_cntt01` |
| **SINH VIÊN ĐÃ CÓ NHÓM** | `23110001` | `123456` | Nguyễn Minh Khang | CNTT (Khoa CNTT) | `23110001` | **Đã có nhóm:** Xem giao diện nhóm đang thực hiện đề tài & nộp báo cáo |
| **SINH VIÊN QTKD** | `sv_qtkd01` | `123456` | Phạm Quốc Cường | QTKD (Khoa QTKD) | `20120001` | Sinh viên khác khoa: test tính năng **CHẶN ĐĂNG KÝ** đợt/đề tài của Khoa CNTT |

---

## 2. DANH SÁCH ĐỀ TÀI MẪU CÓ SẴN TRONG DATABASE

### A. Đề tài thuộc Khoa CNTT:
1. **`CNTT-SEED-01`**: *Xây dựng hệ thống quản lý kho thông minh sử dụng IoT và Machine Learning* (GVHD: ThS. Lê Hoàng Long - `gv_cntt01`)
2. **`CNTT-SEED-02`**: *Phát triển ứng dụng di động hỗ trợ học tiếng Anh theo phương pháp Spaced Repetition* (GVHD: ThS. Lê Hoàng Long - `gv_cntt01`)
3. **`CNTT-SEED-03`**: *Nghiên cứu và triển khai hệ thống phát hiện xâm nhập mạng dùng Deep Learning* (GVHD: TS. Phạm Minh Tuấn - `gv_cntt02`)
4. **`CNTT-SEED-04`**: *Thiết kế nền tảng E-Learning hỗ trợ học tập cộng tác theo kiến trúc Microservices* (GVHD: TS. Phạm Minh Tuấn - `gv_cntt02`)

### B. Đề tài thuộc Khoa QTKD:
1. **`QTKD-SEED-01`**: *Phân tích tác động của Digital Marketing đến hành vi mua sắm trực tuyến của Gen Z* (GVHD: ThS. Hoàng Thu Thảo - `gv_qtkd01`)
2. **`QTKD-SEED-02`**: *Xây dựng mô hình đánh giá rủi ro tín dụng cho doanh nghiệp SME tại Ngân hàng Thương mại* (GVHD: ThS. Hoàng Thu Thảo - `gv_qtkd01`)

---

## 3. CÁC KỊCH BẢN KIỂM THỬ QUY TRÌNH (TEST FLOWS)

### Kịch bản 1: Sinh viên đúng khoa đăng ký đề tài & tạo nhóm
1. Mở trình duyệt ẩn danh 1: Đăng nhập `sv_cntt01` / `123456`.
2. Vào mục **Đăng ký đề tài** -> Chọn đề tài `CNTT-SEED-01` (thuộc đợt Khóa luận CNTT).
3. Đăng ký thành công -> Vào mục **Quản lý nhóm** -> Mời thành viên `sv_cntt02` (Mã SV `20110002`).
4. Mở trình duyệt ẩn danh 2: Đăng nhập `sv_cntt02` / `123456` -> Chấp nhận lời mời nhóm.
5. Kiểm tra nhóm đủ 2 thành viên và hiển thị đề tài đã đăng ký.

### Kịch bản 2: Test tính năng CHẶN sinh viên khác khoa (Security & Validation)
1. Mở trình duyệt ẩn danh: Đăng nhập `sv_qtkd01` / `123456` (Sinh viên Khoa QTKD).
2. Khi vào danh sách đề tài/đợt đăng ký:
   - Hệ thống chỉ cho phép xem/đăng ký đề tài thuộc Khoa QTKD.
   - Nếu cố tình truy cập hoặc gửi request đăng ký đề tài CNTT hoặc đợt CNTT, hệ thống chặn ngay lập tức và báo lỗi: *"Sinh viên không thuộc khoa quản lý đợt đăng ký này!"*.

### Kịch bản 3: Giảng viên đề xuất & quản lý đề tài
1. Đăng nhập `gv_cntt01` / `123456`.
2. Vào mục **Quản lý đề tài** -> Xem các đề tài mình đang hướng dẫn.
3. Tạo mới một đề tài CNTT mới -> Đề tài chuyển sang trạng thái `PENDING`.
4. Đăng nhập `head_cntt` / `123456` -> Vào mục **Duyệt đề tài** -> Duyệt đề tài vừa tạo sang `APPROVED`.

### Kịch bản 4: Trưởng khoa phân quyền & cách ly dữ liệu giữa các khoa
1. Đăng nhập `head_cntt` -> Xem dashboard và danh sách đề tài/hội đồng chỉ thuộc Khoa CNTT.
2. Đăng nhập `head_qtkd` -> Xem dashboard và danh sách đề tài/hội đồng chỉ thuộc Khoa QTKD (không bị lẫn dữ liệu của CNTT).
