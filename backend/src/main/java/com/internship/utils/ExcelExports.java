package com.internship.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.Announcement;
import com.internship.entity.Major;
import com.internship.entity.StudentInternshipStatus;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared value handling for list exports. Identifiers remain text; quantities and dates retain their types. */
public final class ExcelExports {
    private ExcelExports() {}

    public static void write(HttpServletResponse response, String name, String[] headers, List<Object[]> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(name);
            CellStyle heading = workbook.createCellStyle();
            heading.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(font);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            Row top = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = top.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(heading);
                sheet.setColumnWidth(i, 22 * 256);
            }
            for (Object[] values : rows) {
                if (values.length != headers.length) throw new IllegalArgumentException("导出列数与表头不一致");
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i); Object value = values[i];
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else if (value instanceof Date date) { cell.setCellValue(date); cell.setCellStyle(dateStyle); }
                    else cell.setCellValue(text(value));
                }
            }
            sheet.createFreezePane(0, 1);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                    URLEncoder.encode(name + ".xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }

    public static String text(Object value) { return value == null ? "" : value.toString(); }
    public static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
    public static String gender(Integer value) { return value == null ? "" : value == 1 ? "男" : value == 2 ? "女" : "未知"; }
    public static String genderText(String value) {
        return switch (text(value)) { case "1" -> "男"; case "2" -> "女"; default -> text(value); };
    }
    public static boolean contains(String value, String filter) { return filter == null || filter.isBlank() || text(value).contains(filter); }
    public static String account(Integer value) { return value == null ? "" : value == 1 ? "正常" : "禁用"; }
    public static String company(StudentInternshipStatus row) {
        return first(row.getCompany() == null ? null : row.getCompany().getCompanyName(), row.getCompanyName());
    }
    public static String position(StudentInternshipStatus row) {
        return first(row.getPosition() == null ? null : row.getPosition().getPositionName(), row.getPositionName());
    }
    public static String pathCode(StudentInternshipStatus row) {
        if (Objects.equals(row.getStatus(), 6)) return "delay";
        return row.getCompanyId() == null && !company(row).isBlank() ? "selfPractice" : "mutualChoice";
    }
    public static String path(StudentInternshipStatus row) {
        return switch (pathCode(row)) { case "delay" -> "延迟实习"; case "selfPractice" -> "自主实习"; default -> "双向选择"; };
    }
    public static String status(StudentInternshipStatus row) {
        String primary = first(row.getJobSearchStatusText(), switch (row.getStatus() == null ? -99 : row.getStatus()) {
            case 0 -> "尚未获录用"; case 1 -> "已录用待确认"; case 2 -> "已定单位待实习";
            case 3 -> "实习进行中"; case 4 -> "实习已结束"; case 5 -> "实习已中断"; case 6 -> "已延期实习";
            default -> "状态待核实";
        });
        return row.getPendingApplicationTexts() == null || row.getPendingApplicationTexts().isEmpty()
                ? primary : primary + " " + String.join(" ", row.getPendingApplicationTexts());
    }
    public static String companyConfirmation(Integer value) {
        return value == null ? "未知" : switch (value) { case 0 -> "未确认"; case 1 -> "已确认"; case 2 -> "已拒绝"; default -> "未知"; };
    }
    public static void internships(HttpServletResponse response, String name, List<StudentInternshipStatus> values) throws IOException {
        String[] headers = {"学号", "姓名", "性别", "年级", "专业", "班级", "实习状态", "实习途径", "实习单位", "岗位名称",
                "实习开始时间", "实习结束时间", "更新时间", "企业确认状态", "创建时间"};
        List<Object[]> rows = values.stream().map(r -> new Object[]{
                r.getStudent() == null ? "" : r.getStudent().getStudentUserId(),
                first(r.getStudent() == null ? null : r.getStudent().getName(), r.getStudentName()),
                r.getStudent() == null ? genderText(r.getGender()) : gender(r.getStudent().getGender()),
                r.getStudent() != null && r.getStudent().getGrade() != null ? r.getStudent().getGrade() + "级" : text(r.getGrade()),
                first(r.getMajor() == null ? null : r.getMajor().getName(), r.getMajorName()),
                first(r.getCls() == null ? null : r.getCls().getName(), r.getClassName()),
                status(r), path(r), company(r), position(r), r.getInternshipStartTime(), r.getInternshipEndTime(),
                r.getUpdateTime(), companyConfirmation(r.getCompanyConfirmStatus()), r.getCreateTime()
        }).toList();
        write(response, name, headers, rows);
    }

    public static String announcementTargets(Announcement announcement, List<Major> majors) {
        Map<String, String> types = Map.of("ALL", "全体师生", "STUDENT", "全体学生", "TEACHER", "全体教师",
                "TEACHER_TYPE", "特定教师类别", "MAJOR", "特定专业学生", "COMPANY", "企业用户");
        ObjectMapper mapper = new ObjectMapper();
        List<String> targets;
        try { targets = mapper.readValue(announcement.getTargetType(), new TypeReference<List<String>>() {}); }
        catch (Exception ignored) { targets = List.of(text(announcement.getTargetType())); }
        if (targets == null) targets = List.of();
        String result = String.join("、", targets.stream().map(t -> types.getOrDefault(t, t)).toList());
        Map<String, Object> details;
        try { details = mapper.readValue(announcement.getTargetValue(), new TypeReference<Map<String, Object>>() {}); }
        catch (Exception ignored) {
            String value = text(announcement.getTargetValue());
            details = !value.isBlank() && !value.startsWith("{") ? Map.of("teacherTypes",List.of(value)) : Map.of();
        }
        if (details == null) details = Map.of();
        if (details.get("teacherTypes") instanceof List<?> teacherTypes && !teacherTypes.isEmpty()) {
            Map<String, String> names = Map.of("COLLEGE", "学院教师", "DEPARTMENT", "系室教师", "COUNSELOR", "辅导员");
            return result + "（" + String.join("、", teacherTypes.stream().map(t -> names.getOrDefault(text(t), text(t))).toList()) + "）";
        }
        if (details.get("majorIds") instanceof List<?> ids && !ids.isEmpty()) {
            return result + "（" + String.join("、", ids.stream().map(id -> majors.stream()
                    .filter(m -> text(m.getId()).equals(text(id))).map(Major::getName).findFirst().orElse(text(id))).toList()) + "）";
        }
        return result;
    }
}
