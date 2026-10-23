package topicmanagement.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "zip", "rar", "7z", "pptx", "txt"
    );
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25MB

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads", "submissions").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Không thể khởi tạo thư mục lưu trữ file nộp bài!", ex);
        }
    }

    public StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống!");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Kích thước file vượt quá giới hạn cho phép (tối đa 25MB)!");
        }

        String rawOriginalName = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "report.pdf"));
        if (rawOriginalName.contains("..")) {
            throw new IllegalArgumentException("Tên file chứa ký tự không hợp lệ: " + rawOriginalName);
        }

        String extension = "";
        int dotIndex = rawOriginalName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < rawOriginalName.length() - 1) {
            extension = rawOriginalName.substring(dotIndex + 1).toLowerCase();
        }

        if (!extension.isEmpty() && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Định dạng file ." + extension + " không được hỗ trợ! Vui lòng nộp file PDF, DOC, DOCX, PPTX, ZIP, RAR.");
        }

        String savedFileName = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);
        Path targetLocation = this.fileStorageLocation.resolve(savedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Không thể lưu file " + rawOriginalName + ". Vui lòng thử lại!", ex);
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return new StoredFile(savedFileName, rawOriginalName, "/uploads/submissions/" + savedFileName, file.getSize(), contentType);
    }

    public Resource loadFileAsResource(String storedFileName) {
        try {
            // Lấy tên file thuần từ đường dẫn
            String cleanName = storedFileName;
            if (cleanName.contains("/") || cleanName.contains("\\")) {
                cleanName = Paths.get(cleanName).getFileName().toString();
            }
            Path filePath = this.fileStorageLocation.resolve(cleanName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Không tìm thấy file hoặc không thể đọc file: " + storedFileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Đường dẫn file không hợp lệ: " + storedFileName, ex);
        }
    }

    public static class StoredFile {
        private final String savedFileName;
        private final String originalFileName;
        private final String filePath;
        private final long fileSize;
        private final String contentType;

        public StoredFile(String savedFileName, String originalFileName, String filePath, long fileSize, String contentType) {
            this.savedFileName = savedFileName;
            this.originalFileName = originalFileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.contentType = contentType;
        }

        public String getSavedFileName() {
            return savedFileName;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
