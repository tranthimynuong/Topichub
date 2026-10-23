package topicmanagement.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import topicmanagement.entity.AppUser;
import topicmanagement.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Cung cấp dữ liệu tài khoản kiểm thử thực tế từ cơ sở dữ liệu Neon DB cho toàn bộ giao diện Thymeleaf.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final topicmanagement.repository.RegistrationPeriodRepository registrationPeriodRepository;

    public GlobalModelAttributes(topicmanagement.repository.RegistrationPeriodRepository registrationPeriodRepository) {
        this.registrationPeriodRepository = registrationPeriodRepository;
    }

    /**
     * Học kỳ hiện tại (lấy từ session nếu đã chọn hoặc mặc định từ đợt mới nhất)
     */
    @ModelAttribute("currentSemester")
    public Integer getCurrentSemester(jakarta.servlet.http.HttpSession session) {
        if (session != null && session.getAttribute("selectedSemester") != null) {
            return (Integer) session.getAttribute("selectedSemester");
        }
        var activePeriods = registrationPeriodRepository.findByIsActiveTrueOrderByIdDesc();
        if (!activePeriods.isEmpty() && activePeriods.get(0).getSemester() != null) {
            return activePeriods.get(0).getSemester();
        }
        return 1;
    }

    /**
     * Năm học hiện tại (lấy từ session nếu đã chọn hoặc mặc định từ đợt mới nhất)
     */
    @ModelAttribute("currentAcademicYear")
    public String getCurrentAcademicYear(jakarta.servlet.http.HttpSession session) {
        if (session != null && session.getAttribute("selectedAcademicYear") != null) {
            return (String) session.getAttribute("selectedAcademicYear");
        }
        var activePeriods = registrationPeriodRepository.findByIsActiveTrueOrderByIdDesc();
        if (!activePeriods.isEmpty() && activePeriods.get(0).getAcademicYear() != null && !activePeriods.get(0).getAcademicYear().isBlank()) {
            return activePeriods.get(0).getAcademicYear();
        }
        return "2026-2027";
    }
}
