package com.internship.service.impl;

import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherAppraisalDetail;
import com.internship.entity.dto.TeacherAppraisalExportFile;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.entity.dto.TeacherAppraisalFileInfo;
import com.internship.entity.dto.TeacherAppraisalListResponse;
import com.internship.entity.dto.TeacherAppraisalStudent;
import com.internship.entity.dto.TeacherAppraisalScope;
import com.internship.mapper.TeacherInternshipAppraisalMapper;
import com.internship.mapper.InternshipAppraisalMapper;
import com.internship.service.TeacherInternshipAppraisalService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class TeacherInternshipAppraisalServiceImpl implements TeacherInternshipAppraisalService {
    private static final Set<String> ALLOWED_STATUSES = Set.of("ALL", "SUBMITTED", "NOT_SUBMITTED");
    private static final int MAX_PAGE_SIZE = 200;

    private final TeacherInternshipAppraisalMapper mapper;
    private final InternshipAppraisalMapper appraisalMapper;
    private final AliyunOSSOperator ossOperator;
    private final LegacyWordPreviewConverter wordPreviewConverter;

    public TeacherInternshipAppraisalServiceImpl(TeacherInternshipAppraisalMapper mapper,
                                                  InternshipAppraisalMapper appraisalMapper,
                                                  AliyunOSSOperator ossOperator,
                                                  LegacyWordPreviewConverter wordPreviewConverter) {
        this.mapper = mapper;
        this.appraisalMapper = appraisalMapper;
        this.ossOperator = ossOperator;
        this.wordPreviewConverter = wordPreviewConverter;
    }

    @Override
    public TeacherAppraisalListResponse list(TeacherUser teacher, int page, int pageSize,
                                               String keyword, Long classId, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        String safeKeyword = normalizeKeyword(keyword);
        String safeStatus = normalizeStatus(status);
        TeacherAppraisalScope scope = resolveScope(teacher);

        TeacherAppraisalListResponse response = new TeacherAppraisalListResponse();
        response.setCurrent(safePage);
        response.setPageSize(safePageSize);
        if (scope == null) {
            response.setTotal(0L);
            response.setRows(Collections.emptyList());
            response.setPages(0);
            response.setResponsibleStudentCount(0L);
            response.setSubmittedCount(0L);
            response.setNotSubmittedCount(0L);
            response.setClasses(Collections.emptyList());
            return response;
        }

        long responsibleCount = mapper.countResponsibleStudents(scope, safeKeyword, classId);
        long submittedCount = mapper.countSubmittedStudents(scope, safeKeyword, classId);
        long total = mapper.countStudents(scope, safeKeyword, classId, safeStatus);
        int offset = (safePage - 1) * safePageSize;

        response.setTotal(total);
        response.setRows(total == 0
                ? Collections.emptyList()
                : mapper.findStudents(scope, safeKeyword, classId, safeStatus, offset, safePageSize));
        response.setPages(total == 0 ? 0 : (int) ((total + safePageSize - 1) / safePageSize));
        response.setResponsibleStudentCount(responsibleCount);
        response.setSubmittedCount(submittedCount);
        response.setNotSubmittedCount(Math.max(0L, responsibleCount - submittedCount));
        response.setClasses(mapper.findClasses(scope));
        return response;
    }

    @Override
    public TeacherAppraisalDetail findSubmittedDetail(TeacherUser teacher, Long studentId) {
        if (studentId == null) return null;
        TeacherAppraisalScope scope = resolveScope(teacher);
        if (scope == null) return null;
        TeacherAppraisalStudent student = mapper.findSubmittedStudent(scope, studentId);
        if (student == null || student.getSubmissionId() == null) return null;

        TeacherAppraisalDetail detail = new TeacherAppraisalDetail();
        detail.setStudentId(student.getStudentId());
        detail.setStudentNumber(student.getStudentNumber());
        detail.setStudentName(student.getStudentName());
        detail.setClassId(student.getClassId());
        detail.setClassName(student.getClassName());
        detail.setMajorName(student.getMajorName());
        detail.setSubmissionId(student.getSubmissionId());
        detail.setStatus("SUBMITTED");
        detail.setSubmittedAt(student.getSubmittedAt());

        List<InternshipAppraisalFile> files = appraisalMapper.findFilesBySubmissionId(student.getSubmissionId());
        List<TeacherAppraisalFileInfo> safeFiles = new ArrayList<>();
        if (files != null) {
            for (InternshipAppraisalFile file : files) safeFiles.add(toSafeFileInfo(file));
        }
        detail.setFiles(safeFiles);
        return detail;
    }

    @Override
    public TeacherAppraisalFileContent loadFile(TeacherUser teacher, Long fileId, boolean preview) throws Exception {
        if (fileId == null) return null;
        TeacherAppraisalScope scope = resolveScope(teacher);
        if (scope == null) return null;
        InternshipAppraisalFile file = mapper.findAuthorizedSubmittedFile(scope, fileId);
        if (file == null) return null;

        byte[] content = ossOperator.downloadFile(file.getFilePath());
        String fileName = safeOriginalFileName(file.getFileName());
        if (preview && isLegacyDoc(fileName, file.getFileType())) {
            return new TeacherAppraisalFileContent(
                    replaceExtension(fileName, ".pdf"), "application/pdf",
                    wordPreviewConverter.convertToPdf(content));
        }
        return new TeacherAppraisalFileContent(fileName, contentType(fileName, file.getFileType()), content);
    }

    @Override
    public List<TeacherAppraisalExportFile> prepareExport(TeacherUser teacher, String keyword,
                                                            Long classId, String status) {
        String safeStatus = normalizeStatus(status);
        TeacherAppraisalScope scope = resolveScope(teacher);
        if (scope == null) return Collections.emptyList();
        List<TeacherAppraisalExportFile> files = mapper.findExportFiles(
                scope, normalizeKeyword(keyword), classId, safeStatus);
        return files == null ? Collections.emptyList() : files;
    }

    @Override
    public void writeZip(List<TeacherAppraisalExportFile> files, OutputStream outputStream) throws IOException {
        List<String> failures = new ArrayList<>();
        Set<String> usedEntryNames = new HashSet<>();
        Map<String, Integer> studentFileSequences = new HashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (TeacherAppraisalExportFile file : files) {
                String studentDirectory = sanitizeSegment(file.getClassName(), "未分班") + "/"
                        + sanitizeSegment(file.getStudentNumber(), "无学号") + "_"
                        + sanitizeSegment(file.getStudentName(), "未命名学生") + "/";
                int sequence = studentFileSequences.merge(studentDirectory, 1, Integer::sum);
                String baseEntryName = studentDirectory + sequence + "_"
                        + sanitizeSegment(file.getFileName(), "鉴定表");
                String entryName = uniqueEntryName(baseEntryName, usedEntryNames);
                byte[] bytes;
                try {
                    bytes = ossOperator.downloadFile(file.getFilePath());
                    if (bytes == null) throw new IOException("OSS 返回空文件内容");
                } catch (Exception e) {
                    log.warn("导出鉴定表文件失败，fileId={}: {}", file.getFileId(), e.getMessage());
                    failures.add(entryName + "：文件读取失败");
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(bytes);
                zip.closeEntry();
            }

            if (!failures.isEmpty()) {
                zip.putNextEntry(new ZipEntry("_导出失败清单.txt"));
                zip.write(String.join(System.lineSeparator(), failures).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private TeacherAppraisalScope resolveScope(TeacherUser teacher) {
        if (teacher == null || teacher.getId() == null || teacher.getTeacherType() == null) return null;
        String type = teacher.getTeacherType().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "COLLEGE" -> {
                Long departmentId = parsePositiveId(teacher.getDepartmentId());
                yield departmentId == null ? null
                        : new TeacherAppraisalScope(teacher.getId(), type, departmentId, null);
            }
            case "DEPARTMENT" -> {
                Long divisionId = parsePositiveId(teacher.getDivisionId());
                yield divisionId == null ? null
                        : new TeacherAppraisalScope(teacher.getId(), type, null, divisionId);
            }
            case "COUNSELOR" -> new TeacherAppraisalScope(teacher.getId(), type, null, null);
            default -> null;
        };
    }

    private Long parsePositiveId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("status 仅支持 ALL、SUBMITTED、NOT_SUBMITTED");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TeacherAppraisalFileInfo toSafeFileInfo(InternshipAppraisalFile file) {
        TeacherAppraisalFileInfo result = new TeacherAppraisalFileInfo();
        result.setId(file.getId());
        result.setCompanyId(file.getCompanyId());
        result.setCompanyName(file.getCompanyName());
        result.setFileName(file.getFileName());
        result.setFileSize(file.getFileSize());
        result.setFileType(file.getFileType());
        result.setUploadTime(file.getUploadTime());
        return result;
    }

    private boolean isLegacyDoc(String fileName, String fileType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".doc") && !lowerName.endsWith(".docx")
                || "doc".equalsIgnoreCase(fileType)
                || "application/msword".equalsIgnoreCase(fileType);
    }

    private String contentType(String fileName, String fileType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf") || "pdf".equalsIgnoreCase(fileType)) return "application/pdf";
        if (lowerName.endsWith(".docx") || "docx".equalsIgnoreCase(fileType)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerName.endsWith(".doc") || "doc".equalsIgnoreCase(fileType)) return "application/msword";
        return fileType != null && fileType.contains("/") ? fileType : "application/octet-stream";
    }

    private String safeOriginalFileName(String name) {
        if (name == null || name.isBlank()) return "鉴定表";
        String normalized = name.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String fileName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        fileName = fileName.replaceAll("[\\r\\n\\x00-\\x1F]", "_").trim();
        return fileName.isEmpty() ? "鉴定表" : fileName;
    }

    private String replaceExtension(String fileName, String extension) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName) + extension;
    }

    private String sanitizeSegment(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        safe = safe.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_")
                .replaceAll("[. ]+$", "");
        if (safe.isBlank()) safe = fallback;
        if (safe.matches("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$")) safe = "_" + safe;
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private String uniqueEntryName(String requested, Set<String> used) {
        if (used.add(requested)) return requested;
        int slash = requested.lastIndexOf('/');
        String directory = slash >= 0 ? requested.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? requested.substring(slash + 1) : requested;
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int index = 2; ; index++) {
            String candidate = directory + stem + "(" + index + ")" + extension;
            if (used.add(candidate)) return candidate;
        }
    }

}
