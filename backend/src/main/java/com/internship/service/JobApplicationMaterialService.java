package com.internship.service;

import com.internship.entity.StudentArchive;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.dto.JobApplicationMaterialModels.*;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipApplicationMapper;
import com.internship.mapper.JobApplicationMaterialMapper;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.utils.AliyunOSSOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** Immutable, private copies of the files selected for one specific application attempt. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationMaterialService {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final long MAX_TOTAL_SIZE = 80L * 1024 * 1024;
    public static final int MAX_CERTIFICATES = 20;
    private final JobApplicationMaterialMapper mapper;
    private final StudentJobApplicationMapper applications;
    private final InternshipApplicationMapper companyApplications;
    private final StudentArchiveService archives;
    private final AliyunOSSOperator oss;

    public record PreparedFile(String fileType, String fileName, byte[] bytes) { }

    /** Null IDs are reserved for older JSON clients: snapshot their current library once. */
    public List<PreparedFile> prepare(Long studentId, List<Long> archiveIds, MultipartFile resume,
                                      List<MultipartFile> certificates) {
        List<StudentArchive> selected = new ArrayList<>();
        if (archiveIds == null) {
            List<StudentArchive> library = archives.findByStudentId(studentId);
            boolean hasResume = false;
            for (StudentArchive file : library == null ? List.<StudentArchive>of() : library) {
                if (!Set.of("resume", "certificate").contains(file.getFileType())) continue;
                if ("resume".equals(file.getFileType()) && hasResume) continue;
                selected.add(file);
                if ("resume".equals(file.getFileType())) hasResume = true;
            }
        } else {
            if (archiveIds.size() > MAX_CERTIFICATES + 1 || new HashSet<>(archiveIds).size() != archiveIds.size()) {
                throw new BusinessException("申请材料数量无效或包含重复文件");
            }
            for (Long id : archiveIds) {
                if (id == null || id <= 0) throw new BusinessException("材料编号无效，请重新选择");
                StudentArchive file = archives.findById(id);
                if (file == null) throw new BusinessException("所选材料已被删除，请重新选择后提交");
                selected.add(file);
            }
        }
        for (StudentArchive file : selected) {
            if (!Objects.equals(studentId, file.getStudentId())
                    || !("resume".equals(file.getFileType()) || "certificate".equals(file.getFileType()))) {
                throw new AccessDeniedException("只能使用自己的简历和证书");
            }
        }
        List<MultipartFile> uploadedCertificates = certificates == null ? List.of() : certificates;
        long selectedResumes = selected.stream().filter(f -> "resume".equals(f.getFileType())).count();
        long resumeCount = selectedResumes + (resume == null ? 0 : 1);
        long certificateCount = selected.size() - selectedResumes + uploadedCertificates.size();
        if (resumeCount > 1) throw new BusinessException("每次申请只能提交一份简历");
        if (certificateCount > MAX_CERTIFICATES) throw new BusinessException("每次申请最多提交20份证书");
        List<PreparedFile> prepared = new ArrayList<>();
        try {
            for (StudentArchive file : selected) {
                prepared.add(prepareFile(file.getFileType(), file.getFileName(), oss.downloadFile(file.getFileUrl())));
                checkTotal(prepared);
            }
            if (resume != null) prepared.add(prepareUpload("resume", resume));
            for (MultipartFile file : uploadedCertificates) {
                prepared.add(prepareUpload("certificate", file));
                checkTotal(prepared);
            }
            checkTotal(prepared);
            return prepared;
        } catch (BusinessException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取本次申请材料失败: studentId={}", studentId);
            throw new BusinessException("申请材料读取失败，请检查文件后重新提交");
        }
    }

    private PreparedFile prepareUpload(String type, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException("上传文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("单个材料不能超过10MB");
        return prepareFile(type, file.getOriginalFilename(), file.getBytes());
    }

    private PreparedFile prepareFile(String type, String name, byte[] bytes) {
        if (name == null || name.isBlank()) throw new BusinessException("文件名不能为空");
        String normalized = name.replace('\\', '/');
        name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1F]", "_").trim();
        if (name.isEmpty() || name.length() > 255) throw new BusinessException("文件名长度必须在1-255个字符之间");
        String ext = extension(name);
        Set<String> allowed = "resume".equals(type) ? Set.of("pdf", "doc", "docx") : Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png");
        if (!allowed.contains(ext)) throw new BusinessException("简历支持PDF、Word；证书还支持JPG、PNG图片");
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_SIZE) throw new BusinessException("材料不能为空，且单个材料不能超过10MB");
        boolean valid = switch (ext) {
            case "pdf" -> startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'});
            case "doc" -> startsWith(bytes, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "docx" -> startsWith(bytes, new byte[]{'P', 'K', 3, 4});
            case "png" -> startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            default -> startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        };
        if (!valid) throw new BusinessException("文件内容与格式不符，请上传有效的简历或证书");
        return new PreparedFile(type, name, bytes);
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
    }

    private static void checkTotal(List<PreparedFile> files) {
        if (files.stream().mapToLong(f -> f.bytes().length).sum() > MAX_TOTAL_SIZE) throw new BusinessException("本次申请材料总大小不能超过80MB");
    }

    /** Called inside the application transaction. Rollback also removes newly copied OSS objects. */
    public void save(StudentJobApplication application, Long companyApplicationId, List<PreparedFile> files) {
        Submission submission = new Submission();
        submission.setJobApplicationId(application.getId());
        submission.setCompanyApplicationId(companyApplicationId);
        submission.setStudentId(application.getStudentId());
        submission.setCompanyId(application.getCompanyId());
        submission.setSubmittedAt(application.getCreateTime());
        List<String> copiedPaths = new ArrayList<>();
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(copiedPaths);
                }
            });
        }
        try {
            if (application.getId() == null || companyApplicationId == null || mapper.insertSubmission(submission) != 1
                    || submission.getId() == null) throw new BusinessException("申请材料清单保存失败");
            for (int i = 0; i < files.size(); i++) {
                PreparedFile file = files.get(i);
                String objectName = "job-application/" + application.getStudentId() + "/" + application.getId() + "/"
                        + UUID.randomUUID() + "." + extension(file.fileName());
                String path = oss.uploadWithObjectName(file.bytes(), objectName);
                copiedPaths.add(path);
                MaterialFile saved = new MaterialFile();
                saved.setSubmissionId(submission.getId());
                saved.setFileType(file.fileType());
                saved.setFileName(file.fileName());
                saved.setFilePath(path);
                saved.setFileSize((long) file.bytes().length);
                saved.setSortOrder(i);
                if (mapper.insertFile(saved) != 1) throw new BusinessException("申请材料副本保存失败");
            }
        } catch (Exception e) {
            if (!synchronizedTransaction) cleanup(copiedPaths);
            log.warn("保存申请材料副本失败: applicationId={}", application.getId());
            throw new BusinessException("申请材料保存失败，本次申请未提交，请稍后重试");
        }
    }

    private void cleanup(List<String> paths) {
        for (String path : paths) {
            try { oss.deleteFile(path); } catch (Exception e) { log.warn("清理未提交的申请材料副本失败"); }
        }
    }

    public View studentView(Long studentId, Long applicationId) {
        StudentJobApplication application = applications.findById(applicationId);
        if (application == null || !Objects.equals(studentId, application.getStudentId())) throw new AccessDeniedException("无权查看此申请材料");
        return view(mapper.findByJobApplicationId(applicationId), false);
    }

    public View companyView(Long companyId, Long applicationId, boolean activity) {
        boolean restricted;
        if (activity) {
            var application = companyApplications.findById(applicationId);
            if (application == null || !Objects.equals(companyId, application.getCompanyId())) throw new AccessDeniedException("无权查看此申请材料");
            restricted = !StudentJobApplicationStatuses.isActive(application.getStatus());
        } else {
            var application = applications.findById(applicationId);
            if (application == null || !Objects.equals(companyId, application.getCompanyId())) throw new AccessDeniedException("无权查看此申请材料");
            restricted = !StudentJobApplicationStatuses.isActive(application.getStatus());
        }
        return view(activity ? mapper.findByCompanyApplicationId(applicationId) : mapper.findByJobApplicationId(applicationId), restricted);
    }

    private View view(Submission submission, boolean restricted) {
        return submission == null ? new View(false, restricted, null, List.of())
                : new View(true, restricted, submission.getSubmittedAt(), restricted ? List.of() : mapper.findFiles(submission.getId()));
    }

    public TeacherAppraisalFileContent studentFile(Long studentId, Long applicationId, Long fileId) throws Exception {
        View view = studentView(studentId, applicationId);
        return content(authorizedFile(view, fileId));
    }

    public TeacherAppraisalFileContent companyFile(Long companyId, Long applicationId, Long fileId, boolean activity) throws Exception {
        View view = companyView(companyId, applicationId, activity);
        return content(authorizedFile(view, fileId));
    }

    private MaterialFile authorizedFile(View view, Long fileId) {
        if (view.accessRestricted() || view.files().stream().noneMatch(file -> Objects.equals(fileId, file.getId()))) {
            throw new AccessDeniedException("无权访问此申请的材料");
        }
        return mapper.findFile(fileId);
    }

    private TeacherAppraisalFileContent content(MaterialFile file) throws Exception {
        if (file == null) throw new BusinessException("材料不存在");
        return new TeacherAppraisalFileContent(file.getFileName(),
                MediaTypeFactory.getMediaType(file.getFileName()).orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).toString(),
                oss.downloadFile(file.getFilePath()));
    }

    private static String extension(String name) {
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
