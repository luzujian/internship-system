package com.internship.service;

import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.StudentJobApplication;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Company-owned application snapshots. Reading never changes their business timestamp. */
@Service
@RequiredArgsConstructor
public class CompanyApplicationActivityService {
    private final InternshipApplicationMapper mapper;
    private final StudentJobApplicationService applications;

    public record Activity(Long id, String studentName, String positionName, String status,
                           boolean viewed, Date applyTime, Date activityTime, Long version) {}
    public record Feed(List<Activity> items, long total, long unreadCount, boolean hasMore) {}
    public record Detail(Activity activity, InternshipApplicationEntity snapshot, StudentJobApplication application) {}

    @Transactional(readOnly = true)
    public Feed list(Long companyId, int offset, int limit, boolean unreadOnly) {
        if (offset < 0 || limit < 1 || limit > 50) throw new BusinessException("动态分页参数无效");
        List<Activity> items = mapper.findActivityPage(companyId, unreadOnly, offset, limit)
                .stream().map(CompanyApplicationActivityService::activity).toList();
        long unread = mapper.countActivities(companyId, true);
        long total = unreadOnly ? unread : mapper.countActivities(companyId, false);
        return new Feed(items, total, unread, (long) offset + items.size() < total);
    }

    public Detail detail(Long companyId, Long id) {
        InternshipApplicationEntity snapshot = owned(companyId, id);
        List<StudentJobApplication> jobs = attempts(companyId, snapshot.getStudentId(), snapshot.getPositionId());
        List<InternshipApplicationEntity> snapshots = mapper.findActivityAttempts(
                companyId, snapshot.getStudentId(), snapshot.getPositionId());
        List<StudentJobApplication> matches = jobs.stream()
                .filter(job -> Objects.equals(id, matchingActivity(job, snapshots, jobs)))
                .toList();
        StudentJobApplication linked = matches.size() == 1 ? matches.getFirst() : null;
        return new Detail(activity(snapshot), snapshot, linked);
    }

    public Activity forApplication(Long companyId, Long applicationId) {
        StudentJobApplication job = applications.findById(applicationId);
        if (job == null) throw new BusinessException("申请不存在或已撤回，请刷新列表");
        if (!companyId.equals(job.getCompanyId())) throw new AccessDeniedException("无权查看其他企业的申请");
        List<InternshipApplicationEntity> snapshots = mapper.findActivityAttempts(
                companyId, job.getStudentId(), job.getPositionId());
        Long activityId = matchingActivity(job, snapshots, attempts(companyId, job.getStudentId(), job.getPositionId()));
        return snapshots.stream().filter(row -> Objects.equals(row.getId(), activityId))
                .findFirst().map(CompanyApplicationActivityService::activity).orElse(null);
    }

    public void read(Long companyId, Long id, String status, Long version) {
        InternshipApplicationEntity row = owned(companyId, id);
        if (!Objects.equals(status, row.getStatus()) || !Objects.equals(version, millis(row.getUpdateTime()))) {
            throw new BusinessException("申请状态已更新，请重新打开详情");
        }
        if (Boolean.TRUE.equals(row.getViewed())) return;
        if (mapper.readActivityVersion(id, companyId, status, row.getUpdateTime()) == 0) {
            throw new BusinessException("申请状态已更新，请重新打开详情");
        }
    }

    private InternshipApplicationEntity owned(Long companyId, Long id) {
        InternshipApplicationEntity row = mapper.findById(id);
        if (row == null) throw new BusinessException("申请记录不存在");
        if (!companyId.equals(row.getCompanyId())) throw new AccessDeniedException("无权访问其他企业的申请动态");
        return row;
    }

    private List<StudentJobApplication> attempts(Long companyId, Long studentId, Long positionId) {
        return applications.findByCompanyId(companyId).stream()
                .filter(job -> companyId.equals(job.getCompanyId())
                        && Objects.equals(studentId, job.getStudentId())
                        && Objects.equals(positionId, job.getPositionId())).toList();
    }

    // Both tables have independent IDs. Match the shared submission time, never IDs or just the latest attempt.
    // Older single-attempt pairs can be matched unambiguously; ambiguous historical retries remain snapshots.
    private Long matchingActivity(StudentJobApplication job, List<InternshipApplicationEntity> rows,
                                  List<StudentJobApplication> jobs) {
        List<InternshipApplicationEntity> candidates = rows.stream()
                .filter(row -> !Set.of("withdrawn", "cancelled", "canceled").contains(row.getStatus() == null ? "" : row.getStatus()))
                .filter(row -> sameSecond(submitted(row), submitted(job))).toList();
        long sameTimeJobs = jobs.stream().filter(other -> sameSecond(submitted(other), submitted(job))).count();
        if (candidates.size() == 1 && sameTimeJobs == 1) return candidates.getFirst().getId();
        if (rows.size() == 1 && jobs.size() == 1 && Objects.equals(rows.getFirst().getStatus(), job.getStatus())) {
            return rows.getFirst().getId();
        }
        return null;
    }

    private static boolean sameSecond(Date a, Date b) {
        return a != null && b != null && a.getTime() / 1000 == b.getTime() / 1000;
    }
    private static Date submitted(InternshipApplicationEntity row) {
        return row.getApplyTime() != null ? row.getApplyTime() : row.getCreateTime();
    }
    private static Date submitted(StudentJobApplication row) {
        // apply_date is a DATE in production; create_time retains the attempt's time of day.
        return row.getCreateTime() != null ? row.getCreateTime() : row.getApplyDate();
    }
    private static Long millis(Date date) { return date == null ? null : date.getTime(); }
    private static Activity activity(InternshipApplicationEntity row) {
        return new Activity(row.getId(), row.getStudentName(), row.getPositionName(), row.getStatus(),
                Boolean.TRUE.equals(row.getViewed()), submitted(row),
                row.getUpdateTime() != null ? row.getUpdateTime() : submitted(row), millis(row.getUpdateTime()));
    }
}
