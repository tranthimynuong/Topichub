package topicmanagement.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import topicmanagement.entity.*;
import topicmanagement.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DepartmentRepository departmentRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== USER MANAGEMENT ====================

    public List<AppUser> getAllUsers() {
        List<AppUser> list = userRepository.findAll();
        System.out.println("[DB-ADMIN] getAllUsers: Lấy " + list.size() + " người dùng từ Database");
        return list;
    }

    public AppUser getUserById(Long id) {
        if (id == null) return null;
        return userRepository.findById(id).orElse(null);
    }

    public AppUser getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .or(() -> userRepository.findByUsername(username.trim()))
                .orElse(null);
    }

    public List<AppUser> getUsersByRole(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty() || "ALL".equalsIgnoreCase(roleCode)) {
            return getAllUsers();
        }
        return userRepository.findByRoleCode(roleCode.trim().toUpperCase());
    }

    public List<AppUser> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllUsers();
        }
        return userRepository.searchUsers(keyword.trim());
    }

    public List<AppUser> filterUsers(String roleCode, String keyword) {
        List<AppUser> list = getUsersByRole(roleCode);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            return list.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw)) ||
                            (u.getFullName() != null && u.getFullName().toLowerCase().contains(kw)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw)) ||
                            (u.getPhone() != null && u.getPhone().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        return list;
    }

    @Transactional
    public String createUser(AppUser user, Long roleId) {
        String pass = user != null ? user.getPasswordHash() : null;
        return createUser(user, roleId, null, pass);
    }

    @Transactional
    public String createUser(AppUser user, Long roleId, Long departmentId) {
        String pass = user != null ? user.getPasswordHash() : null;
        return createUser(user, roleId, departmentId, pass);
    }

    @Transactional
    public String createUser(AppUser user, Long roleId, String rawPassword) {
        return createUser(user, roleId, null, rawPassword);
    }

    @Transactional
    public String createUser(AppUser user, Long roleId, Long departmentId, String rawPassword) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return "Tên đăng nhập không được để trống!";
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            return "Mật khẩu không được để trống!";
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return "Email không được để trống!";
        }

        String cleanUsername = user.getUsername().trim().toLowerCase();
        String cleanEmail = user.getEmail().trim().toLowerCase();

        if (userRepository.findByUsernameIgnoreCase(cleanUsername).isPresent()) {
            return "Tên đăng nhập '" + cleanUsername + "' đã tồn tại trên hệ thống!";
        }
        if (userRepository.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            return "Email '" + cleanEmail + "' đã được sử dụng bởi tài khoản khác!";
        }

        Role role = null;
        if (roleId != null) {
            role = roleRepository.findById(roleId).orElse(null);
        }
        if (role == null) {
            role = roleRepository.findByCode("STUDENT").orElse(null);
        }

        // RÀNG BUỘC: nếu role là DEPARTMENT_HEAD và departmentId != null, kiểm tra khoa đó đã có trưởng khoa chưa
        if (role != null && "DEPARTMENT_HEAD".equalsIgnoreCase(role.getCode()) && departmentId != null) {
            List<Lecturer> deptLecturers = lecturerRepository.findByDepartmentId(departmentId);
            for (Lecturer l : deptLecturers) {
                if (l.getUser() != null && l.getUser().getRole() != null
                        && "DEPARTMENT_HEAD".equalsIgnoreCase(l.getUser().getRole().getCode())) {
                    return "Khoa này đã có trưởng khoa là " + l.getUser().getUsername();
                }
            }
        }

        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setFullName(user.getFullName() != null ? user.getFullName().trim() : cleanUsername);
        user.setPhone(user.getPhone() != null && !user.getPhone().trim().isEmpty() ? user.getPhone().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(rawPassword.trim()));
        user.setRole(role);
        if (user.getIsActive() == null) {
            user.setIsActive(true);
        }
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        AppUser saved = userRepository.save(user);

        // Khởi tạo hồ sơ chuyên biệt theo Role
        if (role != null) {
            Department department = (departmentId != null) ? departmentRepository.findById(departmentId).orElse(null) : null;

            if ("STUDENT".equalsIgnoreCase(role.getCode())) {
                if (studentRepository.findByUserId(saved.getId()).isEmpty()) {
                    Student student = new Student();
                    student.setUser(saved);
                    student.setStudentCode(saved.getUsername());
                    if (department != null) {
                        student.setDepartment(department);
                    }
                    studentRepository.save(student);
                }
            } else if ("LECTURER".equalsIgnoreCase(role.getCode()) || "DEPARTMENT_HEAD".equalsIgnoreCase(role.getCode())) {
                if (lecturerRepository.findByUserId(saved.getId()).isEmpty()) {
                    Lecturer lecturer = new Lecturer();
                    lecturer.setUser(saved);
                    lecturer.setLecturerCode(saved.getUsername());
                    lecturer.setMaxTopicsQuota(5);
                    if (department != null) {
                        lecturer.setDepartment(department);
                    }
                    lecturerRepository.save(lecturer);
                }
            }
        }

        System.out.println("[DB-ADMIN] createUser: ĐÃ LƯU TÀI KHOẢN MỚI VÀO DB! (ID=" + saved.getId() + ", User=" + saved.getUsername() + ", Role=" + (role != null ? role.getCode() : "null") + ")");
        return null;
    }

    @Transactional
    public String updateUser(Long id, String fullName, String email, String phone, Long roleId, Boolean isActive) {
        if (id == null) return "Mã định danh người dùng không hợp lệ!";
        Optional<AppUser> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return "Không tìm thấy người dùng với ID: " + id;
        }

        AppUser user = opt.get();

        if (email != null && !email.trim().isEmpty()) {
            String cleanEmail = email.trim().toLowerCase();
            Optional<AppUser> existingEmailUser = userRepository.findByEmailIgnoreCase(cleanEmail);
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(id)) {
                return "Email '" + cleanEmail + "' đã được sử dụng bởi người dùng khác (" + existingEmailUser.get().getUsername() + ")!";
            }
            user.setEmail(cleanEmail);
        }

        if (fullName != null && !fullName.trim().isEmpty()) {
            user.setFullName(fullName.trim());
        }
        user.setPhone(phone != null && !phone.trim().isEmpty() ? phone.trim() : null);

        if (isActive != null) {
            user.setIsActive(isActive);
        }

        Role oldRole = user.getRole();
        Role newRole = null;
        if (roleId != null) {
            newRole = roleRepository.findById(roleId).orElse(null);
            if (newRole != null) {
                user.setRole(newRole);
            }
        }
        user.setUpdatedAt(OffsetDateTime.now());

        AppUser saved = userRepository.save(user);

        // Đồng bộ hồ sơ khi thay đổi vai trò
        if (newRole != null && (oldRole == null || !oldRole.getId().equals(newRole.getId()))) {
            if ("STUDENT".equalsIgnoreCase(newRole.getCode())) {
                if (studentRepository.findByUserId(saved.getId()).isEmpty()) {
                    Student student = new Student();
                    student.setUser(saved);
                    student.setStudentCode(saved.getUsername());
                    studentRepository.save(student);
                }
            } else if ("LECTURER".equalsIgnoreCase(newRole.getCode()) || "DEPARTMENT_HEAD".equalsIgnoreCase(newRole.getCode())) {
                if (lecturerRepository.findByUserId(saved.getId()).isEmpty()) {
                    Lecturer lecturer = new Lecturer();
                    lecturer.setUser(saved);
                    lecturer.setLecturerCode(saved.getUsername());
                    lecturer.setMaxTopicsQuota(5);
                    lecturerRepository.save(lecturer);
                }
            }
        }

        System.out.println("[DB-ADMIN] updateUser(" + id + "): Đã cập nhật thông tin user '" + user.getUsername() + "' vào DB thành công!");
        return null;
    }

    @Transactional
    public String updateStudentProfile(Long userId, String studentCode, String className, String academicYear, Double gpa, Long departmentId) {
        if (userId == null) return "User ID không hợp lệ!";
        Optional<Student> opt = studentRepository.findByUserId(userId);
        Student student = opt.orElseGet(() -> {
            AppUser u = userRepository.findById(userId).orElse(null);
            if (u == null) return null;
            Student s = new Student();
            s.setUser(u);
            return s;
        });
        if (student == null) return "Không tìm thấy người dùng!";

        if (studentCode != null && !studentCode.trim().isEmpty()) {
            student.setStudentCode(studentCode.trim());
        }
        student.setClassName(className != null && !className.trim().isEmpty() ? className.trim() : null);
        student.setAcademicYear(academicYear != null && !academicYear.trim().isEmpty() ? academicYear.trim() : null);
        if (gpa != null) {
            if (gpa < 0.0 || gpa > 4.0) return "GPA phải nằm trong thang điểm từ 0.0 đến 4.0!";
            student.setGpa(gpa);
        } else {
            student.setGpa(null);
        }
        if (departmentId != null) {
            Department d = departmentRepository.findById(departmentId).orElse(null);
            student.setDepartment(d);
        }
        studentRepository.save(student);
        return null;
    }

    @Transactional
    public String updateLecturerProfile(Long userId, String lecturerCode, String academicTitle, String degree, Integer maxTopicsQuota, Long departmentId) {
        if (userId == null) return "User ID không hợp lệ!";
        Optional<Lecturer> opt = lecturerRepository.findByUserId(userId);
        Lecturer lecturer = opt.orElseGet(() -> {
            AppUser u = userRepository.findById(userId).orElse(null);
            if (u == null) return null;
            Lecturer l = new Lecturer();
            l.setUser(u);
            return l;
        });
        if (lecturer == null) return "Không tìm thấy người dùng!";

        if (lecturerCode != null && !lecturerCode.trim().isEmpty()) {
            lecturer.setLecturerCode(lecturerCode.trim());
        }
        lecturer.setAcademicTitle(academicTitle != null && !academicTitle.trim().isEmpty() ? academicTitle.trim() : null);
        lecturer.setDegree(degree != null && !degree.trim().isEmpty() ? degree.trim() : null);
        if (maxTopicsQuota != null && maxTopicsQuota > 0) {
            lecturer.setMaxTopicsQuota(maxTopicsQuota);
        }
        if (departmentId != null) {
            Department d = departmentRepository.findById(departmentId).orElse(null);
            lecturer.setDepartment(d);
        }
        lecturerRepository.save(lecturer);
        return null;
    }

    public java.util.Map<Long, Student> getStudentMap() {
        return studentRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getId() != null)
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s, (a, b) -> a));
    }

    public java.util.Map<Long, Lecturer> getLecturerMap() {
        return lecturerRepository.findAll().stream()
                .filter(l -> l.getUser() != null && l.getUser().getId() != null)
                .collect(Collectors.toMap(l -> l.getUser().getId(), l -> l, (a, b) -> a));
    }

    @Transactional
    public boolean toggleUserActive(Long id) {
        if (id == null) return false;
        Optional<AppUser> opt = userRepository.findById(id);
        if (opt.isPresent()) {
            AppUser user = opt.get();
            user.setIsActive(!Boolean.TRUE.equals(user.getIsActive()));
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
            System.out.println("[DB-ADMIN] toggleUserActive(" + id + "): Trạng thái mới isActive=" + user.getIsActive());
            return true;
        }
        return false;
    }

    @Transactional
    public boolean resetUserPassword(Long id, String newPassword) {
        if (id == null) return false;
        Optional<AppUser> opt = userRepository.findById(id);
        if (opt.isPresent()) {
            AppUser user = opt.get();
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
            System.out.println("[DB-ADMIN] resetUserPassword(" + id + "): Đã đổi mật khẩu (BCrypt) cho user '" + user.getUsername() + "' vào DB");
            return true;
        }
        return false;
    }

    // ==================== ROLE MANAGEMENT ====================

    public List<Role> getAllRoles() {
        List<Role> list = roleRepository.findAll().stream()
                .filter(r -> r.getCode() != null && !"DIVISION_HEAD".equalsIgnoreCase(r.getCode()))
                .toList();
        System.out.println("[DB-ADMIN] getAllRoles: Lấy " + list.size() + " vai trò từ DB (đã loại bỏ DIVISION_HEAD)");
        return list;
    }

    public Role getRoleById(Long roleId) {
        if (roleId == null) return null;
        return roleRepository.findById(roleId).orElse(null);
    }

    // ==================== DEPARTMENT MANAGEMENT ====================

    public List<Department> getAllDepartments() {
        List<Department> list = departmentRepository.findAll();
        System.out.println("[DB-ADMIN] getAllDepartments: Lấy " + list.size() + " khoa từ DB");
        return list;
    }

    public Department getDepartmentById(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id).orElse(null);
    }

    @Transactional
    public String createDepartment(Department department) {
        if (department == null || department.getCode() == null || department.getCode().trim().isEmpty()) {
            return "Mã khoa không được để trống!";
        }
        if (department.getName() == null || department.getName().trim().isEmpty()) {
            return "Tên khoa không được để trống!";
        }
        String cleanCode = department.getCode().trim().toUpperCase();
        if (departmentRepository.findByCodeIgnoreCase(cleanCode).isPresent()) {
            return "Mã khoa '" + cleanCode + "' đã tồn tại trên hệ thống!";
        }

        department.setCode(cleanCode);
        department.setName(department.getName().trim());
        if (department.getIsActive() == null) {
            department.setIsActive(true);
        }
        Department saved = departmentRepository.save(department);
        System.out.println("[DB-ADMIN] createDepartment: ĐÃ TẠO KHOA MỚI VÀO DB! (Mã: " + saved.getCode() + ", Tên: " + saved.getName() + ")");
        return null;
    }

    @Transactional
    public String updateDepartment(Long id, String name, String code, String description, Boolean isActive) {
        if (id == null) return "Mã định danh khoa không hợp lệ!";
        Optional<Department> opt = departmentRepository.findById(id);
        if (opt.isEmpty()) {
            return "Không tìm thấy Khoa với ID: " + id;
        }
        if (code == null || code.trim().isEmpty()) {
            return "Mã khoa không được để trống!";
        }
        if (name == null || name.trim().isEmpty()) {
            return "Tên khoa không được để trống!";
        }

        String cleanCode = code.trim().toUpperCase();
        Optional<Department> existingCodeDept = departmentRepository.findByCodeIgnoreCase(cleanCode);
        if (existingCodeDept.isPresent() && !existingCodeDept.get().getId().equals(id)) {
            return "Mã khoa '" + cleanCode + "' đã được sử dụng bởi khoa khác (" + existingCodeDept.get().getName() + ")!";
        }

        Department d = opt.get();
        d.setName(name.trim());
        d.setCode(cleanCode);
        d.setDescription(description);
        if (isActive != null) d.setIsActive(isActive);
        departmentRepository.save(d);
        System.out.println("[DB-ADMIN] updateDepartment(" + id + "): Đã cập nhật khoa '" + cleanCode + "' vào DB");
        return null;
    }

    @Transactional
    public String updateDepartment(Long id, String name, String code, Boolean isActive) {
        return updateDepartment(id, name, code, null, isActive);
    }

    @Transactional
    public boolean toggleDepartmentActive(Long id) {
        if (id == null) return false;
        Optional<Department> opt = departmentRepository.findById(id);
        if (opt.isPresent()) {
            Department d = opt.get();
            d.setIsActive(!Boolean.TRUE.equals(d.getIsActive()));
            departmentRepository.save(d);
            System.out.println("[DB-ADMIN] toggleDepartmentActive(" + id + "): Trạng thái mới isActive=" + d.getIsActive());
            return true;
        }
        return false;
    }

    // ==================== SYSTEM STATS ====================

    public Map<String, Object> getSystemStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countActiveUsers();
        long adminCount = userRepository.countByRoleCode("ADMIN");
        long deptHeadCount = userRepository.countByRoleCode("DEPARTMENT_HEAD");
        long lecturerCount = userRepository.countByRoleCode("LECTURER");
        long studentCount = userRepository.countByRoleCode("STUDENT");
        
        List<Department> depts = getAllDepartments();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("totalDepartments", depts.size());
        stats.put("adminCount", adminCount);
        stats.put("deptHeadCount", deptHeadCount);
        stats.put("lecturerCount", lecturerCount);
        stats.put("studentCount", studentCount);

        System.out.println("[DB-ADMIN] getSystemStats: Thống kê DB -> Tổng user: " + totalUsers + ", Active: " + activeUsers + ", Khoa: " + depts.size());
        return stats;
    }
}
