package com.internship.service;

import com.internship.entity.FileUpload;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.InternshipAgreementModels.*;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.entity.dto.TeacherAppraisalScope;
import com.internship.exception.BusinessException;
import com.internship.mapper.FileUploadMapper;
import com.internship.mapper.InternshipAgreementMapper;
import com.internship.mapper.TeacherInternshipAppraisalMapper;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class InternshipAgreementService {
    public static final String BUSINESS_TYPE = "internship_agreement";
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> SOURCE_TYPES = Set.of("CONFIRMATION", "SELF_PRACTICE");
    private static final Set<String> BUSINESS_STATUSES = Set.of("ALL", "ACTIVE", "HISTORY", "PENDING",
            "CONFIRMED", "IN_PROGRESS", "REPLACED", "INTERRUPTED", "ENDED", "REJECTED", "WITHDRAWN");

    private final InternshipAgreementMapper mapper;
    private final FileUploadMapper uploads;
    private final TeacherInternshipAppraisalMapper appraisalMapper;
    private final AliyunOSSOperator oss;
    private final LegacyWordPreviewConverter wordPreview;

    public InternshipAgreementService(InternshipAgreementMapper mapper, FileUploadMapper uploads,
                                      TeacherInternshipAppraisalMapper appraisalMapper,
                                      AliyunOSSOperator oss, LegacyWordPreviewConverter wordPreview) {
        this.mapper = mapper;
        this.uploads = uploads;
        this.appraisalMapper = appraisalMapper;
        this.oss = oss;
        this.wordPreview = wordPreview;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgreementFile upload(Long studentId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException("文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("单个文件不能超过20MB");
        String name = safeFileName(file.getOriginalFilename());
        String extension = extension(name);
        byte[] bytes = file.getBytes();
        validateDocument(bytes, extension);
        String objectName = "internship-agreement/" + studentId + "/" + LocalDate.now().getYear() + "/"
                + String.format("%02d", LocalDate.now().getMonthValue()) + "/" + UUID.randomUUID() + "." + extension;
        String fileUrl = oss.uploadWithObjectName(bytes, objectName);
        boolean cleanupOnRollback = TransactionSynchronizationManager.isSynchronizationActive();
        if (cleanupOnRollback) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) cleanupUpload(fileUrl);
                }
            });
        }
        try {
            FileUpload upload = new FileUpload();
            upload.setFileName(name);
            upload.setFilePath(fileUrl);
            upload.setFileSize(file.getSize());
            upload.setFileType(extension);
            upload.setUploaderId(studentId);
            upload.setUploaderType("student");
            upload.setBusinessType(BUSINESS_TYPE);
            upload.setUploadTime(new Date());
            if (uploads.insert(upload) != 1 || upload.getId() == null) throw new BusinessException("协议文件保存失败");
            AgreementFile result = new AgreementFile();
            result.setUploadId(upload.getId());
            result.setFileName(name);
            result.setFileType(extension);
            result.setFileSize(file.getSize());
            result.setUploadTime(new Date());
            return result;
        } catch (Exception e) {
            if (!cleanupOnRollback) cleanupUpload(fileUrl);
            throw e;
        }
    }

    private void cleanupUpload(String fileUrl) {
        try { oss.deleteFile(fileUrl); } catch (Exception cleanup) { log.warn("清理未保存协议文件失败"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnsubmittedUpload(Long studentId, Long uploadId) throws Exception {
        mapper.lockStudent(studentId);
        FileUpload upload = mapper.findUploadForUpdate(uploadId);
        validateOwnedUpload(studentId, upload);
        if (!mapper.findUploadBindings(uploadId).isEmpty()) throw new BusinessException("已提交的协议文件须保留历史，不能删除");
        oss.deleteFile(upload.getFilePath());
        uploads.deleteById(uploadId);
    }

    /** 与实习确认/自主申请处于同一事务，任一文件不合法时回滚业务提交。 */
    @Transactional(rollbackFor = Exception.class)
    public AgreementVersion bindSubmission(Long studentId, String sourceType, Long sourceId, List<Long> fileIds) {
        mapper.lockStudent(studentId);
        AgreementRecord record = ownRecord(studentId, sourceType, sourceId);
        return saveVersion(record, fileIds, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgreementVersion correct(Long studentId, String sourceType, Long sourceId, SubmitAgreementRequest request) {
        mapper.lockStudent(studentId);
        AgreementRecord record = ownRecord(studentId, sourceType, sourceId);
        if (!editable(record)) throw new BusinessException("已撤回或已拒绝的记录请在重新提交原申请时修改协议");
        if (request == null || request.getExpectedVersion() == null) throw new BusinessException("请刷新协议版本后重新提交");
        return saveVersion(record, request.getFileIds(), request.getExpectedVersion());
    }

    private AgreementVersion saveVersion(AgreementRecord record, List<Long> fileIds, Integer expectedVersion) {
        List<Long> safeIds = requireFileIds(fileIds);
        // 当前读避免业务提交事务先前建立的快照漏掉并发已提交的版本。
        List<AgreementVersion> versions = mapper.findVersionsForUpdate(record.getSourceType(), record.getSourceId());
        AgreementVersion latest = versions.isEmpty() ? null : versions.get(0);
        int currentVersion = latest == null ? 0 : latest.getVersion();
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw new BusinessException("协议已更新，请刷新后重新核对文件");
        }
        // 按 ID 加锁，与上传删除、其他提交共享文件锁，避免反序请求死锁。
        for (Long uploadId : safeIds.stream().sorted().toList()) {
            FileUpload upload = mapper.findUploadForUpdate(uploadId);
            validateOwnedUpload(record.getStudentId(), upload);
            for (AgreementVersion binding : mapper.findUploadBindings(uploadId)) {
                if (!Objects.equals(binding.getSourceType(), record.getSourceType())
                        || !Objects.equals(binding.getSourceId(), record.getSourceId())
                        || !sameCompany(binding.getCompanyName(), record.getCompanyName())) {
                    throw new BusinessException("协议文件已关联其他申请或单位，请重新上传");
                }
            }
        }
        if (latest != null && sameCompany(latest.getCompanyName(), record.getCompanyName())
                && Objects.equals(latest.getPositionName(), record.getPositionName())) {
            List<AgreementFile> currentFiles = mapper.findVersionFiles(latest.getId());
            if (safeIds.equals(currentFiles.stream().map(AgreementFile::getUploadId).toList())) {
                latest.setFiles(currentFiles);
                return latest;
            }
        }
        AgreementVersion version = new AgreementVersion();
        version.setStudentId(record.getStudentId());
        version.setSourceType(record.getSourceType());
        version.setSourceId(record.getSourceId());
        version.setVersion(currentVersion + 1);
        version.setCompanyName(record.getCompanyName());
        version.setPositionName(record.getPositionName());
        if (mapper.insertVersion(version) != 1 || version.getId() == null) throw new BusinessException("协议提交失败");
        for (int index = 0; index < safeIds.size(); index++) {
            if (mapper.insertFile(version.getId(), safeIds.get(index), index) != 1) throw new BusinessException("协议文件关联失败");
        }
        version.setSubmittedAt(new Date());
        version.setFiles(mapper.findVersionFiles(version.getId()));
        return version;
    }

    public AgreementDetail studentDetail(Long studentId, String sourceType, Long sourceId) {
        return detail(ownRecord(studentId, sourceType, sourceId));
    }

    public AgreementDetail teacherDetail(TeacherUser teacher, String sourceType, Long sourceId) {
        AgreementQuery query = teacherQuery(teacher, null, null, sourceType, "ALL", "ALL");
        query.setSourceId(requireSourceId(sourceId));
        return detail(requireRecord(query));
    }

    private AgreementDetail detail(AgreementRecord record) {
        AgreementDetail result = new AgreementDetail();
        result.setRecord(record);
        result.setEditable(editable(record));
        List<AgreementVersion> versions = mapper.findVersions(record.getSourceType(), record.getSourceId());
        versions.forEach(version -> version.setFiles(mapper.findVersionFiles(version.getId())));
        result.setVersions(versions);
        return result;
    }

    public CollectionPage list(TeacherUser teacher, int page, int pageSize, String keyword, Long classId,
                               String sourceType, String status, String businessStatus) {
        AgreementQuery query = teacherQuery(teacher, keyword, classId, sourceType, status, businessStatus);
        CollectionPage result = new CollectionPage();
        int safeSize = Math.min(200, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        result.setCurrent(safePage);
        result.setPageSize(safeSize);
        query.setStatus("ALL");
        result.setResponsibleRecordCount(mapper.countRecords(query));
        query.setStatus("SUBMITTED");
        result.setSubmittedCount(mapper.countRecords(query));
        result.setNotSubmittedCount(Math.max(0, result.getResponsibleRecordCount() - result.getSubmittedCount()));
        query.setStatus(normalizeStatus(status));
        result.setTotal(mapper.countRecords(query));
        query.setOffset((int) Math.min(Integer.MAX_VALUE, (long) (safePage - 1) * safeSize));
        query.setPageSize(safeSize);
        result.setRows(mapper.findRecords(query));
        result.setClasses(appraisalMapper.findClasses(query.getScope()));
        return result;
    }

    public List<ExportFile> exportFiles(TeacherUser teacher, String keyword, Long classId,
                                      String sourceType, String status, String businessStatus) {
        return mapper.findExportFiles(teacherQuery(teacher, keyword, classId, sourceType, status, businessStatus));
    }

    public TeacherAppraisalFileContent studentFile(Long studentId, Long fileId, boolean preview) throws Exception {
        AgreementFile file = requireFile(fileId);
        ownRecord(studentId, file.getSourceType(), file.getSourceId());
        return loadFile(file, preview);
    }

    public TeacherAppraisalFileContent teacherFile(TeacherUser teacher, Long fileId, boolean preview) throws Exception {
        AgreementFile file = requireFile(fileId);
        AgreementQuery query = teacherQuery(teacher, null, null, file.getSourceType(), "ALL", "ALL");
        query.setSourceId(file.getSourceId());
        requireRecord(query);
        return loadFile(file, preview);
    }

    private TeacherAppraisalFileContent loadFile(AgreementFile file, boolean preview) throws Exception {
        byte[] content = oss.downloadFile(file.getFilePath());
        String name = safeFileName(file.getFileName());
        if (preview && "doc".equals(extension(name))) {
            return new TeacherAppraisalFileContent(name.substring(0, name.length() - 4) + ".pdf",
                    "application/pdf", wordPreview.convertToPdf(content));
        }
        String type = switch (extension(name)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            default -> "application/octet-stream";
        };
        return new TeacherAppraisalFileContent(name, type, content);
    }

    public void writeZip(List<ExportFile> files, OutputStream output) throws IOException {
        List<String> failures = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (ExportFile file : files) {
                String entry = segment(file.getClassName(), "未分班") + "/"
                        + segment(file.getStudentNumber(), "无学号") + "_" + segment(file.getStudentName(), "未命名学生") + "/"
                        + ("SELF_PRACTICE".equals(file.getSourceType()) ? "自主实习" : "平台实习") + "_"
                        + segment(file.getCompanyName(), "未命名单位") + "_" + file.getSourceId() + "/v" + file.getVersion()
                        + "/" + file.getId() + "_" + segment(file.getFileName(), "实习协议");
                byte[] bytes;
                try {
                    bytes = oss.downloadFile(file.getFilePath());
                    if (bytes == null || bytes.length == 0) throw new IOException("空文件");
                } catch (Exception e) {
                    log.warn("协议导出读取失败，fileId={}", file.getId());
                    failures.add(entry + "：文件读取失败");
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(bytes);
                zip.closeEntry();
            }
            if (!failures.isEmpty()) {
                zip.putNextEntry(new ZipEntry("_导出失败清单.txt"));
                zip.write(String.join(System.lineSeparator(), failures).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private AgreementRecord ownRecord(Long studentId, String sourceType, Long sourceId) {
        if (studentId == null) throw new AccessDeniedException("学生账号不存在");
        AgreementQuery query = new AgreementQuery();
        query.setStudentId(studentId);
        query.setSourceType(requireSourceType(sourceType));
        query.setSourceId(requireSourceId(sourceId));
        return requireRecord(query);
    }

    private AgreementRecord requireRecord(AgreementQuery query) {
        List<AgreementRecord> records = mapper.findRecords(query);
        if (records.isEmpty()) throw new AccessDeniedException("实习记录不存在或不在您的管理范围内");
        return records.get(0);
    }

    private AgreementFile requireFile(Long fileId) {
        AgreementFile file = fileId == null ? null : mapper.findFile(fileId);
        if (file == null) throw new AccessDeniedException("协议文件不存在或无权查看");
        return file;
    }

    private AgreementQuery teacherQuery(TeacherUser teacher, String keyword, Long classId,
                                        String sourceType, String status, String businessStatus) {
        TeacherAppraisalScope scope = scope(teacher);
        AgreementQuery query = new AgreementQuery();
        query.setScope(scope);
        query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
        query.setClassId(classId);
        query.setSourceType(sourceType == null || sourceType.isBlank() || "ALL".equals(sourceType)
                ? null : requireSourceType(sourceType));
        query.setStatus(normalizeStatus(status));
        String business = businessStatus == null ? "ACTIVE" : businessStatus.toUpperCase(Locale.ROOT);
        if (!BUSINESS_STATUSES.contains(business)) throw new BusinessException("业务状态无效");
        query.setBusinessStatus(business);
        return query;
    }

    private TeacherAppraisalScope scope(TeacherUser teacher) {
        if (teacher != null && teacher.getId() != null && teacher.getTeacherType() != null) {
            String type = teacher.getTeacherType().toUpperCase(Locale.ROOT);
            try {
                if ("COUNSELOR".equals(type)) return new TeacherAppraisalScope(teacher.getId(), type, null, null);
                if ("COLLEGE".equals(type) && Long.parseLong(teacher.getDepartmentId()) > 0)
                    return new TeacherAppraisalScope(teacher.getId(), type, Long.parseLong(teacher.getDepartmentId()), null);
                if ("DEPARTMENT".equals(type) && Long.parseLong(teacher.getDivisionId()) > 0)
                    return new TeacherAppraisalScope(teacher.getId(), type, null, Long.parseLong(teacher.getDivisionId()));
            } catch (NumberFormatException ignored) { }
        }
        throw new AccessDeniedException("教师管理范围未配置");
    }

    public static List<Long> requireFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) throw new BusinessException("请至少上传一份已盖章的实习协议");
        if (fileIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(fileIds).size() != fileIds.size())
            throw new BusinessException("协议文件编号无效或重复");
        return List.copyOf(fileIds);
    }

    private void validateOwnedUpload(Long studentId, FileUpload upload) {
        if (upload == null || !Objects.equals(studentId, upload.getUploaderId())
                || !"student".equals(upload.getUploaderType()) || !BUSINESS_TYPE.equals(upload.getBusinessType()))
            throw new BusinessException("协议文件不存在或不属于当前学生");
        if (upload.getFileSize() == null || upload.getFileSize() <= 0 || upload.getFileSize() > MAX_FILE_SIZE
                || !Set.of("pdf", "doc", "docx").contains(extension(upload.getFileName())))
            throw new BusinessException("协议文件格式或大小不符合要求");
    }

    private static boolean editable(AgreementRecord record) {
        return !Set.of("WITHDRAWN", "REJECTED").contains(record.getBusinessStatus());
    }

    private static boolean sameCompany(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static String requireSourceType(String type) {
        if (!SOURCE_TYPES.contains(type == null ? "" : type)) throw new BusinessException("协议来源无效");
        return type;
    }

    private static Long requireSourceId(Long id) {
        if (id == null || id <= 0) throw new BusinessException("实习记录编号无效");
        return id;
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "ALL" : status.toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "SUBMITTED", "NOT_SUBMITTED").contains(value)) throw new BusinessException("提交状态无效");
        return value;
    }

    private static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) throw new BusinessException("文件名不能为空");
        String normalized = name.replace('\\', '/');
        String result = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1F]", "_").trim();
        if (result.isEmpty() || result.length() > 255) throw new BusinessException("文件名长度必须在1-255个字符之间");
        return result;
    }

    private static String segment(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.trim();
        result = result.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").replaceAll("[. ]+$", "");
        if (result.isBlank() || result.equals(".")) result = fallback;
        if (result.matches("(?i)^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$")) result = "_" + result;
        return result.length() > 120 ? result.substring(0, 120) : result;
    }

    private static void validateDocument(byte[] bytes, String extension) throws IOException {
        boolean valid = false;
        if ("pdf".equals(extension)) valid = bytes.length >= 5
                && "%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        if ("doc".equals(extension) && bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1})) {
            try (POIFSFileSystem document = new POIFSFileSystem(new java.io.ByteArrayInputStream(bytes))) {
                valid = document.getRoot().hasEntry("WordDocument");
            } catch (IOException | IllegalArgumentException invalid) {
                valid = false;
            }
        }
        if ("docx".equals(extension) && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            boolean types = false;
            boolean document = false;
            try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                int count = 0;
                long expanded = 0;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 2000) throw new BusinessException("Word文档内容过大");
                    if ("[Content_Types].xml".equals(entry.getName())) types = true;
                    if ("word/document.xml".equals(entry.getName())) document = true;
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        expanded += read;
                        if (expanded > MAX_FILE_SIZE * 5) throw new BusinessException("Word文档解压内容过大");
                    }
                }
            }
            valid = types && document;
        }
        if (!valid) throw new BusinessException("请上传有效的PDF、DOC或DOCX文档");
    }
}
