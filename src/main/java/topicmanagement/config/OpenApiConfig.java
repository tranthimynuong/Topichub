package topicmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hệ Thống Quản Lý Đề Tài Sinh Viên - Khoa CNTT (API Documentation)")
                        .description("Tài liệu đặc tả toàn bộ REST API của Hệ thống quản lý đề tài sinh viên (Môn học / NCKH / TLCN / KLTN) - Spring Boot 3.4.3 & PostgreSQL Neon.\n\n"
                                + "### 5 Nhóm Vai Trò:\n"
                                + "- **ADMIN / DEAN**: Quản trị hệ thống, cấu hình đợt đăng ký, công bố kết quả.\n"
                                + "- **HEAD_OF_DEPARTMENT**: Trưởng bộ môn duyệt đề tài, phân công GVPB, thành lập hội đồng, gộp nhóm thiếu người.\n"
                                + "- **LECTURER**: Giảng viên đề xuất đề tài, hướng dẫn, nộp điểm quá trình, chấm phản biện & hội đồng.\n"
                                + "- **STUDENT (LEADER / MEMBER)**: Sinh viên tạo nhóm, gửi lời mời bắt tay 2 chiều, đăng ký đề tài, nộp báo cáo tiến độ, rút/hủy nhóm.")
                        .version("v2.0.0")
                        .contact(new Contact()
                                .name("Khoa Công nghệ Thông tin")
                                .email("cntt@university.edu.vn"))
                        .license(new License()
                                .name("Academic License 2.0")
                                .url("https://fit.university.edu.vn")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}
