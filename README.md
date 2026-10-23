# HỆ THỐNG QUẢN LÝ ĐỀ TÀI KHÓA LUẬN TỐT NGHIỆP (TOPIC MANAGEMENT SYSTEM)

Hệ thống quản lý quy trình đăng ký, phân công hướng dẫn, phản biện, nộp báo cáo và đánh giá đề tài khóa luận tốt nghiệp theo kiến trúc phân tầng chuẩn mực trên nền tảng **Spring Boot** và **PostgreSQL**.

---

## 🛠 1. CÔNG NGHỆ SỬ DỤNG

- **Ngôn ngữ:** Java 21 (LTS)
- **Framework:** Spring Boot 3.3.4
  - **Spring Security 6:** Xác thực (Authentication) & Phân quyền (RBAC - Role-Based Access Control) theo cấp độ URL và Method (`@PreAuthorize`).
  - **Spring MVC:** Kiến trúc Front Controller, phân tách tầng rõ ràng (Controller -> Service -> Repository).
  - **Spring Data JPA & Hibernate:** Quản lý Entity quan hệ, phân trang (`Pageable`), quản trị giao dịch (`@Transactional`).
  - **Spring Boot DevTools:** Hỗ trợ Hot-reload và phát triển nhanh.
- **Giao diện (Frontend):** Thymeleaf Template Engine, HTML5/CSS3, JavaScript thuần, thiết kế Responsive & Glassmorphism hiện đại.
- **Cơ sở dữ liệu:** PostgreSQL (kết nối qua Neon Cloud Database Pooler với HikariCP).
- **Công cụ build:** Apache Maven (đã tích hợp sẵn Maven Wrapper `mvnw`).

---

## 👥 2. CÁC VAI TRÒ TRONG HỆ THỐNG (ROLES)

1. **QUẢN TRỊ VIÊN (ADMIN):**
   - Quản lý tài khoản người dùng (tạo mới, kích hoạt/khóa tài khoản, đặt lại mật khẩu).
   - Quản lý danh mục Khoa, Bộ môn, Chuyên ngành.
   - Quản lý các đợt đăng ký toàn trường và phân quyền hệ thống.

2. **TRƯỞNG KHOA / TRƯỞNG BỘ MÔN (DEPARTMENT_HEAD):**
   - Tạo và quản lý đợt đăng ký đề tài của khoa/bộ môn.
   - Xét duyệt đề tài do Giảng viên hoặc Sinh viên đề xuất.
   - Thành lập Hội đồng chấm khóa luận và phân công đề tài vào hội đồng.
   - Đăng thông báo chung cho khoa.

3. **GIẢNG VIÊN (LECTURER):**
   - Đề xuất đề tài khóa luận mới.
   - Quản lý danh sách sinh viên/nhóm sinh viên đang hướng dẫn.
   - Phê duyệt / từ chối yêu cầu đăng ký đề tài từ nhóm sinh viên.
   - Đánh giá tiến độ, phản biện và chấm điểm đề tài.

4. **SINH VIÊN (STUDENT):**
   - Xem danh sách đợt đăng ký và đề tài thuộc đúng Chuyên ngành / Khoa của mình.
   - Thành lập nhóm làm đề tài (mời thành viên, làm nhóm trưởng).
   - Đăng ký đề tài giảng viên hoặc tự đề xuất đề tài mới.
   - Nộp báo cáo khóa luận định kỳ theo từng giai đoạn.

---

## 🚀 3. HƯỚNG DẪN CÀI ĐẶT VÀ CHẠY DỰ ÁN

### Yêu cầu môi trường:
- Đã cài đặt **JDK 21** (hoặc cao hơn).
- Không bắt buộc cài đặt Maven rời (dự án đã tích hợp sẵn `./mvnw`).

### Các bước chạy:
1. **Khởi chạy ứng dụng:**
   - Trên Windows: Chạy file script có sẵn:
     ```cmd
     .\run.bat
     ```
   - Hoặc chạy bằng lệnh Maven Wrapper:
     ```bash
     .\mvnw spring-boot:run
     ```
2. **Truy cập hệ thống:**
   - Mở trình duyệt web và truy cập: [http://localhost:8080](http://localhost:8080)
   - Trang đăng nhập sẽ tự động hiển thị.

3. **Chạy kiểm thử (Automated Tests):**
   ```bash
   .\mvnw test
   ```

---

## 🔑 4. DANH SÁCH TÀI KHOẢN DÙNG THỬ (SEED DATA)

Dữ liệu mẫu đã được tích hợp sẵn trong cơ sở dữ liệu để phục vụ việc kiểm thử nhanh:

| Vai trò | Tên đăng nhập (Username) | Mật khẩu (Password) | Chức vụ / Người dùng |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin` | `admin123` | Quản trị viên toàn hệ thống |
| **Trưởng khoa CNTT** | `head_cntt` | `123456` | TS. Nguyễn Văn Thắng (Trưởng khoa CNTT) |
| **Trưởng khoa QTKD** | `head_qtkd` | `123456` | PGS.TS. Trần Thị Mai (Trưởng khoa QTKD) |
| **Giảng viên CNTT** | `gv_cntt01` | `123456` | ThS. Lê Hoàng Long (Khoa CNTT) |
| **Giảng viên CNTT** | `gv_cntt02` | `123456` | TS. Phạm Minh Tuấn (Khoa CNTT) |
| **Sinh viên CNTT (Trưởng nhóm)** | `sv_cntt01` | `123456` | Nguyễn Văn An (Lớp CNTT) |
| **Sinh viên CNTT (Thành viên)** | `sv_cntt02` | `123456` | Trần Thị Bình (Lớp CNTT) |
| **Sinh viên QTKD** | `sv_qtkd01` | `123456` | Phạm Quốc Cường (Lớp QTKD) |

*(Xem thêm kịch bản kiểm thử chi tiết tại file `TEST_ACCOUNTS_GUIDE.md`)*

---

## 📁 5. CẤU TRÚC MÃ NGUỒN CHÍNH

```text
Java-project/
├── pom.xml                                   # Cấu hình thư viện Maven
├── run.bat / run.ps1                         # Script khởi chạy ứng dụng nhanh
├── src/
│   ├── main/
│   │   ├── java/topicmanagement/
│   │   │   ├── config/                       # Cấu hình Security, MVC, Bean
│   │   │   ├── controller/                   # Controller tiếp nhận HTTP requests
│   │   │   ├── entity/                       # Các thực thể dữ liệu JPA/Hibernate
│   │   │   ├── exception/                    # Xử lý ngoại lệ tập trung (@ControllerAdvice)
│   │   │   ├── repository/                   # Tầng DAO / Spring Data JPA Repository
│   │   │   └── service/                      # Tầng xử lý logic nghiệp vụ (@Transactional)
│   │   └── resources/
│   │       ├── application.properties        # Cấu hình dùng chung
│   │       ├── application-dev.properties    # Cấu hình môi trường phát triển (Dev profile)
│   │       ├── application-prod.properties   # Cấu hình môi trường triển khai (Prod profile)
│   │       ├── static/                       # CSS, JS, hình ảnh, tài nguyên tĩnh
│   │       └── templates/                    # Giao diện Thymeleaf UI theo từng vai trò
│   └── test/                                 # Test tự động (Security, Controller, Service)
└── README.md                                 # Tài liệu giới thiệu dự án
```