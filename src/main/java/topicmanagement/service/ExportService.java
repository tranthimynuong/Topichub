package topicmanagement.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import topicmanagement.entity.Department;
import topicmanagement.entity.RegistrationPeriod;
import topicmanagement.entity.StudentGroup;
import topicmanagement.entity.Topic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    /**
     * Xuất danh sách bảng điểm tổng kết ra định dạng Excel (.xlsx) chuẩn với Apache POI
     */
    public byte[] exportResultsToExcel(List<Topic> topics, Map<Long, StudentGroup> groupsMap, Department dept, RegistrationPeriod period) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bảng Điểm Tổng Kết");

            // Setup Print / Page
            sheet.setDisplayGridlines(true);
            sheet.setPrintGridlines(true);

            // ==================== STYLES ====================
            // 1. Title Style
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setFontName("Segoe UI");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 2. Subtitle Style
            org.apache.poi.ss.usermodel.Font subFont = workbook.createFont();
            subFont.setFontName("Segoe UI");
            subFont.setFontHeightInPoints((short) 10);
            subFont.setItalic(true);
            subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            CellStyle subStyle = workbook.createCellStyle();
            subStyle.setFont(subFont);
            subStyle.setAlignment(HorizontalAlignment.CENTER);

            // 3. Header Table Style
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setFontName("Segoe UI");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setBorder(headerStyle);

            // 4. Data Cell Normal
            org.apache.poi.ss.usermodel.Font dataFont = workbook.createFont();
            dataFont.setFontName("Segoe UI");
            dataFont.setFontHeightInPoints((short) 10);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setFont(dataFont);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(dataStyle);

            // 5. Data Cell Center
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setFont(dataFont);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(centerStyle);

            // 6. Score Style (Right Aligned, Number Format)
            CellStyle scoreStyle = workbook.createCellStyle();
            scoreStyle.setFont(dataFont);
            scoreStyle.setAlignment(HorizontalAlignment.RIGHT);
            scoreStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            DataFormat format = workbook.createDataFormat();
            scoreStyle.setDataFormat(format.getFormat("0.00"));
            setBorder(scoreStyle);

            // 7. Final Score Highlight
            org.apache.poi.ss.usermodel.Font boldScoreFont = workbook.createFont();
            boldScoreFont.setFontName("Segoe UI");
            boldScoreFont.setFontHeightInPoints((short) 10);
            boldScoreFont.setBold(true);
            boldScoreFont.setColor(IndexedColors.BLUE.getIndex());

            CellStyle finalScoreStyle = workbook.createCellStyle();
            finalScoreStyle.setFont(boldScoreFont);
            finalScoreStyle.setAlignment(HorizontalAlignment.RIGHT);
            finalScoreStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            finalScoreStyle.setDataFormat(format.getFormat("0.00"));
            setBorder(finalScoreStyle);

            // ==================== BUILD HEADER ====================
            int rowIdx = 0;

            // Row 0: Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeightInPoints(30);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG TỔNG HỢP ĐIỂM ĐÁNH GIÁ ĐỀ TÀI & KHÓA LUẬN");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 15));

            // Row 1: Subtitle
            String deptName = (dept != null) ? dept.getName() : "Khoa Công Nghệ Thông Tin";
            String periodName = (period != null) ? period.getName() : "Toàn bộ đợt đăng ký";
            String exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            org.apache.poi.ss.usermodel.Row subRow = sheet.createRow(rowIdx++);
            subRow.setHeightInPoints(20);
            org.apache.poi.ss.usermodel.Cell subCell = subRow.createCell(0);
            subCell.setCellValue("Đơn vị: " + deptName + " | Đợt: " + periodName + " | Thời gian xuất: " + exportTime);
            subCell.setCellStyle(subStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 15));

            rowIdx++; // blank row

            // Row 3: Column Headers
            String[] headers = {
                "STT", "Mã ĐT", "Tên Đề Tài", "Khoa Quản Lý", "Đợt Đăng Ký",
                "GV Hướng Dẫn", "GV Phản Biện", "Hội Đồng", "Tên Nhóm",
                "MSSV Nhóm Trưởng", "Họ Tên Nhóm Trưởng", "Điểm GVHD",
                "Điểm GVPB", "Điểm HĐ", "Điểm Tổng Kết", "Trạng Thái"
            };

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ==================== BUILD DATA ROWS ====================
            int stt = 1;
            for (Topic t : topics) {
                StudentGroup g = groupsMap != null ? groupsMap.get(t.getId()) : null;
                String supName = (t.getSupervisor() != null && t.getSupervisor().getUser() != null) ? t.getSupervisor().getUser().getFullName() : "N/A";
                String revName = (t.getReviewer() != null && t.getReviewer().getUser() != null) ? t.getReviewer().getUser().getFullName() : "N/A";
                String councilName = (t.getCouncil() != null) ? t.getCouncil().getName() : "N/A";
                String grpName = (g != null) ? g.getGroupName() : "Chưa gán nhóm";
                String leaderCode = (g != null && g.getLeader() != null) ? g.getLeader().getStudentCode() : "";
                String leaderName = (g != null && g.getLeader() != null && g.getLeader().getUser() != null) ? g.getLeader().getUser().getFullName() : "";

                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);

                createCell(row, 0, String.valueOf(stt++), centerStyle);
                createCell(row, 1, t.getCode() != null ? t.getCode() : "", centerStyle);
                createCell(row, 2, t.getTitle() != null ? t.getTitle() : "", dataStyle);
                createCell(row, 3, t.getDepartment() != null ? t.getDepartment().getName() : "", dataStyle);
                createCell(row, 4, t.getPeriod() != null ? t.getPeriod().getName() : "", dataStyle);
                createCell(row, 5, supName, dataStyle);
                createCell(row, 6, revName, dataStyle);
                createCell(row, 7, councilName, dataStyle);
                createCell(row, 8, grpName, dataStyle);
                createCell(row, 9, leaderCode, centerStyle);
                createCell(row, 10, leaderName, dataStyle);

                // Scores
                createScoreCell(row, 11, t.getAdvisorScore(), scoreStyle, centerStyle);
                createScoreCell(row, 12, t.getReviewerScore(), scoreStyle, centerStyle);
                createScoreCell(row, 13, t.getCouncilScore(), scoreStyle, centerStyle);
                createScoreCell(row, 14, t.getFinalScore(), finalScoreStyle, centerStyle);

                // Status
                String statusStr = Boolean.TRUE.equals(t.getIsFinalized()) ? "ĐÃ CÔNG BỐ" : "DỰ THẢO";
                createCell(row, 15, statusStr, centerStyle);
            }

            // Auto-size columns with a reasonable bound
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(currentWidth + 1000, 3000), 12000));
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Xuất danh sách bảng điểm tổng kết ra định dạng PDF chuẩn với OpenPDF
     */
    public byte[] exportResultsToPdf(List<Topic> topics, Map<Long, StudentGroup> groupsMap, Department dept, RegistrationPeriod period) throws DocumentException {
        // Landscape A4 for wide table presentation
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, out);
        document.open();

        // Fonts
        com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new java.awt.Color(30, 41, 59));
        com.lowagie.text.Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new java.awt.Color(100, 116, 139));
        com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, java.awt.Color.WHITE);
        com.lowagie.text.Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new java.awt.Color(51, 65, 85));
        com.lowagie.text.Font boldCellFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new java.awt.Color(37, 99, 235));

        // Header Table
        PdfPTable headerMeta = new PdfPTable(2);
        headerMeta.setWidthPercentage(100);
        headerMeta.setWidths(new float[]{1, 1});

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("TRUONG DAI HOC SU PHAM KY THUAT TP.HCM", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
        String deptName = (dept != null) ? dept.getName() : "KHOA CONG NGHE THONG TIN";
        leftCell.addElement(new Paragraph(deptName.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new java.awt.Color(37, 99, 235))));
        headerMeta.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pCountry = new Paragraph("CONG HOA XA HOI CHU NGHIA VIET NAM", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8));
        pCountry.setAlignment(Element.ALIGN_RIGHT);
        Paragraph pMotto = new Paragraph("Doc lap - Tu do - Hanh phuc", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8));
        pMotto.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(pCountry);
        rightCell.addElement(pMotto);
        headerMeta.addCell(rightCell);

        document.add(headerMeta);
        document.add(new Paragraph(" "));

        // Title
        Paragraph title = new Paragraph("BANG TONG HOP DIEM DANH GIA DE TAI & KHOA LUAN TOT NGHIEP", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        String periodName = (period != null) ? period.getName() : "Tat ca cac dot dang ky";
        String exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        Paragraph subTitle = new Paragraph("Dot: " + periodName + "  |  Ngay xuat: " + exportTime, subTitleFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        subTitle.setSpacingAfter(10);
        document.add(subTitle);

        // Data Table: 11 columns
        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 1.2f, 3.2f, 1.8f, 1.8f, 1.8f, 0.9f, 0.9f, 0.9f, 1.0f, 1.1f});

        String[] pdfHeaders = {
            "STT", "Ma DT", "Ten De Tai", "Nhom / Truong", "GV Huong Dan",
            "Hoi Dong", "Diem GVHD", "Diem GVPB", "Diem HD", "Diem Tong", "Trang Thai"
        };

        for (String h : pdfHeaders) {
            PdfPCell c = new PdfPCell(new Phrase(h, headerFont));
            c.setBackgroundColor(new java.awt.Color(30, 41, 59));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPadding(4);
            table.addCell(c);
        }

        int stt = 1;
        for (Topic t : topics) {
            StudentGroup g = groupsMap != null ? groupsMap.get(t.getId()) : null;
            String supName = (t.getSupervisor() != null && t.getSupervisor().getUser() != null) ? t.getSupervisor().getUser().getFullName() : "N/A";
            String councilName = (t.getCouncil() != null) ? t.getCouncil().getName() : "N/A";
            String groupInfo = (g != null) ? (g.getGroupName() + "\n(" + (g.getLeader() != null ? g.getLeader().getStudentCode() : "") + ")") : "Chua co";

            addPdfCell(table, String.valueOf(stt++), cellFont, Element.ALIGN_CENTER);
            addPdfCell(table, t.getCode() != null ? t.getCode() : "", cellFont, Element.ALIGN_CENTER);
            addPdfCell(table, t.getTitle() != null ? t.getTitle() : "", cellFont, Element.ALIGN_LEFT);
            addPdfCell(table, groupInfo, cellFont, Element.ALIGN_LEFT);
            addPdfCell(table, supName, cellFont, Element.ALIGN_LEFT);
            addPdfCell(table, councilName, cellFont, Element.ALIGN_LEFT);

            addPdfCell(table, t.getAdvisorScore() != null ? String.format("%.2f", t.getAdvisorScore()) : "--", cellFont, Element.ALIGN_RIGHT);
            addPdfCell(table, t.getReviewerScore() != null ? String.format("%.2f", t.getReviewerScore()) : "--", cellFont, Element.ALIGN_RIGHT);
            addPdfCell(table, t.getCouncilScore() != null ? String.format("%.2f", t.getCouncilScore()) : "--", cellFont, Element.ALIGN_RIGHT);
            addPdfCell(table, t.getFinalScore() != null ? String.format("%.2f", t.getFinalScore()) : "--", boldCellFont, Element.ALIGN_RIGHT);

            String statusStr = Boolean.TRUE.equals(t.getIsFinalized()) ? "DA CONG BO" : "DU THAO";
            addPdfCell(table, statusStr, cellFont, Element.ALIGN_CENTER);
        }

        document.add(table);

        // Signatures Block
        document.add(new Paragraph(" "));
        PdfPTable signTable = new PdfPTable(3);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{1, 1, 1});

        addSignatureCell(signTable, "TRUONG KHOA", "(Ky va ghi ro ho ten)");
        addSignatureCell(signTable, "CHU TICH HOI DONG", "(Ky va ghi ro ho ten)");
        addSignatureCell(signTable, "THU KY HOI DONG", "(Ky va ghi ro ho ten)");

        document.add(signTable);

        document.close();
        return out.toByteArray();
    }

    private void addSignatureCell(PdfPTable table, String role, String subText) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph pRole = new Paragraph(role, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8));
        pRole.setAlignment(Element.ALIGN_CENTER);
        Paragraph pSub = new Paragraph(subText, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, new java.awt.Color(100, 116, 139)));
        pSub.setAlignment(Element.ALIGN_CENTER);
        Paragraph pSpace = new Paragraph("\n\n\n", FontFactory.getFont(FontFactory.HELVETICA, 7));
        cell.addElement(pRole);
        cell.addElement(pSub);
        cell.addElement(pSpace);
        table.addCell(cell);
    }

    private void addPdfCell(PdfPTable table, String text, com.lowagie.text.Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private void createCell(org.apache.poi.ss.usermodel.Row row, int colIdx, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(colIdx);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void createScoreCell(org.apache.poi.ss.usermodel.Row row, int colIdx, Double score, CellStyle scoreStyle, CellStyle nullStyle) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(colIdx);
        if (score != null) {
            cell.setCellValue(score);
            cell.setCellStyle(scoreStyle);
        } else {
            cell.setCellValue("--");
            cell.setCellStyle(nullStyle);
        }
    }
}
