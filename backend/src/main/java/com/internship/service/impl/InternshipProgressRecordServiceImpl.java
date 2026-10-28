package com.internship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.dto.RecentActivityActionDTO;
import com.internship.entity.dto.RecentActivityItemDTO;
import com.internship.entity.dto.RecentActivityPageDTO;
import com.internship.entity.dto.RecentActivityRecordRow;
import com.internship.mapper.InternshipProgressRecordMapper;
import com.internship.service.InternshipProgressRecordService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.ByteBuffer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class InternshipProgressRecordServiceImpl implements InternshipProgressRecordService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int CURSOR_BYTES = Long.BYTES * 2;

    private static final Set<String> CANONICAL_EVENTS = Set.of(
            "job_applied", "job_withdrawn", "job_rejected",
            "interview_invited", "interview_passed", "interview_failed", "direct_offer",
            "confirmation_submitted", "confirmation_confirmed", "confirmation_rejected",
            "confirmation_withdrawn",
            "self_practice_submitted", "self_practice_approved", "self_practice_rejected",
            "delay_submitted", "delay_approved", "delay_rejected",
            "unit_change_submitted", "unit_change_resubmitted", "unit_change_approved",
            "unit_change_rejected",
            "reflection_submitted", "reflection_resubmitted", "reflection_withdrawn",
            "reflection_scored",
            "stage_continued", "stage_auto_continued", "stage_change_selected",
            "internship_started", "internship_ended", "internship_interrupted");

    @Autowired
    private InternshipProgressRecordMapper mapper;

    @Autowired
    private com.internship.service.ReflectionFeatureService reflectionFeature;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired(required = false)
    private com.internship.agent.business.StudentPlanRefreshService planRefresh;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecord(InternshipProgressRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("进展记录不能为空");
        }

        Date now = new Date();
        if (record.getEventTime() == null) {
            record.setEventTime(now);
        }
        if (record.getCreateTime() == null) {
            record.setCreateTime(now);
        }

        log.info("开始保存实习进展记录: studentId={}, eventType={}, eventTitle={}",
                record.getStudentId(), record.getEventType(), record.getEventTitle());
        int affectedRows = mapper.insert(record);
        if (affectedRows != 1) {
            throw new IllegalStateException("保存实习进展记录失败");
        }
        log.info("保存实习进展记录成功: id={}, studentId={}, eventType={}",
                record.getId(), record.getStudentId(), record.getEventType());

        if (planRefresh != null) planRefresh.afterCommit(record.getStudentId());
        notifyStudentAfterCommit(record.getStudentId(), record.getId());
    }

    @Override
    public List<InternshipProgressRecord> getByStudentId(Long studentId) {
        LambdaQueryWrapper<InternshipProgressRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InternshipProgressRecord::getStudentId, studentId)
               .orderByDesc(InternshipProgressRecord::getEventTime)
               .orderByDesc(InternshipProgressRecord::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public RecentActivityPageDTO getRecentActivities(Long studentId, int limit, String cursor) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("学生ID无效");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit必须在1到20之间");
        }

        Cursor decodedCursor = decodeCursor(cursor);
        List<RecentActivityRecordRow> rows = mapper.selectRecentPage(
                studentId,
                decodedCursor == null ? null : decodedCursor.occurredAt(),
                decodedCursor == null ? null : decodedCursor.id(),
                limit + 1, reflectionFeature.enabled());
        if (rows == null) {
            rows = List.of();
        }

        boolean hasMore = rows.size() > limit;
        int itemCount = Math.min(rows.size(), limit);
        List<RecentActivityItemDTO> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(toItem(rows.get(i)));
        }

        String nextCursor = null;
        if (hasMore && itemCount > 0) {
            RecentActivityRecordRow last = rows.get(itemCount - 1);
            nextCursor = encodeCursor(last.getOccurredAt(), last.getId());
        }
        return new RecentActivityPageDTO(List.copyOf(items), nextCursor, hasMore);
    }

    private void notifyStudentAfterCommit(Long studentId, Long activityId) {
        Runnable notification = () -> {
            try {
                webSocketHandler.sendStudentRecentActivityChanged(studentId, activityId);
            } catch (Exception e) {
                log.warn("推送学生最近动态刷新消息失败: studentId={}, activityId={}, error={}",
                        studentId, activityId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notification.run();
                }
            });
        } else {
            notification.run();
        }
    }

    private RecentActivityItemDTO toItem(RecentActivityRecordRow row) {
        String sourceEventType = safeLowerPreservingCamelCase(row.getEventType());
        String eventType = normalizeEventType(row);
        String status = normalizeStatus(row.getStatus(), eventType, sourceEventType);
        return new RecentActivityItemDTO(
                row.getId(),
                eventType,
                categoryOf(eventType),
                titleOf(eventType, row.getEventTitle()),
                row.getDescription(),
                status,
                statusTextOf(eventType, status),
                row.getOccurredAt().toInstant().atZone(BUSINESS_ZONE).toOffsetDateTime(),
                null,
                row.getRelatedId(),
                actionOf(eventType, row.getRelatedId(),
                        Boolean.TRUE.equals(row.getTargetExists())));
    }

    private String normalizeEventType(RecentActivityRecordRow row) {
        String eventType = safeLowerPreservingCamelCase(row.getEventType());
        if (eventType == null) {
            return "unknown";
        }
        if (CANONICAL_EVENTS.contains(eventType)) {
            return eventType;
        }

        return switch (eventType) {
            case "job_application" -> "job_applied";
            case "interview" -> "interview_invited";
            case "direct_hire" -> "direct_offer";
            case "internship_confirmation" -> "confirmation_submitted";
            case "selfPractice" -> "self_practice_submitted";
            case "delay" -> "delay_submitted";
            case "unit_change_application" -> {
                String description = row.getDescription();
                yield description != null && description.contains("再次")
                        ? "unit_change_resubmitted" : "unit_change_submitted";
            }
            case "reflection_submit" -> {
                String title = row.getEventTitle();
                String description = row.getDescription();
                yield (title != null && (title.contains("重新") || title.contains("再次")))
                        || (description != null && (description.contains("重新") || description.contains("再次")))
                        ? "reflection_resubmitted" : "reflection_submitted";
            }
            default -> eventType;
        };
    }

    private String safeLowerPreservingCamelCase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return "selfPractice".equals(trimmed) ? trimmed : trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String rawStatus, String eventType, String sourceEventType) {
        // 终态事件按事件类型直接确定状态
        if (eventType.endsWith("_rejected") || "interview_failed".equals(eventType)) {
            return "failed";
        }
        if (eventType.endsWith("_withdrawn")) {
            return "cancelled";
        }
        if (eventType.endsWith("_approved") || "interview_passed".equals(eventType)
                || "direct_offer".equals(eventType) || "confirmation_confirmed".equals(eventType)
                || "reflection_scored".equals(eventType)) {
            return "success";
        }

        // reflection_submitted / reflection_resubmitted 本身是历史快照（已提交就是已提交），
        // 后续评分会写入新的 reflection_scored 事件，而不是更新原记录的 status，因此保留 info 语义
        if ("reflection_submitted".equals(eventType) || "reflection_resubmitted".equals(eventType)) {
            return "info";
        }

        // 其余事件信任数据库 rawStatus，仅在数据库无值时按事件类型兜底
        String status = lower(rawStatus);
        if (status == null || status.isBlank()) {
            return defaultStatusOf(eventType);
        }
        if (isSuccessful(status) || "scored".equals(status)) return "success";
        if (isFailed(status)) return "failed";
        if (isCancelled(status)) return "cancelled";
        if ("pending".equals(status) || "waiting".equals(status)) return "pending";
        if ("info".equals(status) || "submitted".equals(status) || "warning".equals(status)) {
            return "info";
        }
        return defaultStatusOf(eventType);
    }

    private String defaultStatusOf(String eventType) {
        if (eventType.endsWith("_rejected") || "interview_failed".equals(eventType)) return "failed";
        if (eventType.endsWith("_withdrawn")) return "cancelled";
        if (eventType.endsWith("_approved") || "interview_passed".equals(eventType)
                || "direct_offer".equals(eventType) || "confirmation_confirmed".equals(eventType)
                || "reflection_scored".equals(eventType)) return "success";
        if (eventType.endsWith("_submitted") || "interview_invited".equals(eventType)
                || "job_applied".equals(eventType)) return "pending";
        return "info";
    }

    private String categoryOf(String eventType) {
        if (eventType.startsWith("job_") || eventType.startsWith("interview_")
                || "direct_offer".equals(eventType)) return "recruitment";
        if (eventType.startsWith("confirmation_")) return "confirmation";
        if (eventType.startsWith("self_practice_") || eventType.startsWith("delay_")
                || eventType.startsWith("unit_change_")) return "application";
        if (eventType.startsWith("reflection_")) return "reflection";
        if (eventType.startsWith("stage_") || eventType.startsWith("internship_")) return "internship";
        return "info";
    }

    private String titleOf(String eventType, String storedTitle) {
        return switch (eventType) {
            case "job_applied" -> "已投递岗位";
            case "job_withdrawn" -> "已撤回岗位申请";
            case "job_rejected" -> "岗位申请未通过";
            case "interview_invited" -> "收到面试邀请";
            case "interview_passed" -> "面试已通过";
            case "interview_failed" -> "面试未通过";
            case "direct_offer" -> "收到企业直接录取 Offer";
            case "confirmation_submitted" -> "已提交实习确认表";
            case "confirmation_confirmed" -> "企业已确认实习";
            case "confirmation_rejected" -> "实习确认被拒绝";
            case "confirmation_withdrawn" -> "已撤回实习确认";
            case "self_practice_submitted" -> "已提交自主实习申请";
            case "self_practice_approved" -> "自主实习申请已通过";
            case "self_practice_rejected" -> "自主实习申请被驳回";
            case "delay_submitted" -> "已提交延期申请";
            case "delay_approved" -> "延期申请已通过";
            case "delay_rejected" -> "延期申请被驳回";
            case "unit_change_submitted" -> "已提交单位变更申请";
            case "unit_change_resubmitted" -> "已重新提交单位变更申请";
            case "unit_change_approved" -> "单位变更申请已通过";
            case "unit_change_rejected" -> "单位变更申请被驳回";
            case "reflection_submitted" -> "实习心得已提交";
            case "reflection_resubmitted" -> "实习心得已重新提交";
            case "reflection_withdrawn" -> "实习心得已撤回";
            case "reflection_scored" -> "实习心得已评分";
            case "stage_continued" -> "实习经历已延续";
            case "stage_auto_continued" -> "实习经历已自动延续";
            case "stage_change_selected" -> "已选择更换实习单位";
            case "internship_started" -> "实习已开始";
            case "internship_ended" -> "实习已结束";
            case "internship_interrupted" -> "实习已中断";
            default -> storedTitle == null || storedTitle.isBlank() ? "实习动态" : storedTitle;
        };
    }

    private String statusTextOf(String eventType, String status) {
        return switch (eventType) {
            case "job_applied" -> switch (status) {
                case "success"   -> "已处理";
                case "failed"    -> "未通过";
                case "cancelled" -> "已撤回";
                default          -> "待企业处理";
            };
            case "job_withdrawn", "confirmation_withdrawn", "reflection_withdrawn" -> "已撤回";
            case "job_rejected", "interview_failed" -> "未通过";
            case "interview_invited" -> switch (status) {
                case "success"   -> "已处理";
                case "failed"    -> "已结束";
                case "cancelled" -> "已取消";
                default          -> "待面试";
            };
            case "interview_passed", "direct_offer" -> "获得 Offer";
            case "confirmation_submitted" -> switch (status) {
                case "success"   -> "已确认";
                case "failed"    -> "已拒绝";
                case "cancelled" -> "已撤回";
                default          -> "待企业确认";
            };
            case "confirmation_confirmed" -> "已确认";
            case "confirmation_rejected" -> "已拒绝";
            case "self_practice_submitted" -> switch (status) {
                case "success" -> "已通过";
                case "failed" -> "已驳回";
                case "cancelled" -> "已撤回";
                default -> "待处理";
            };
            case "delay_submitted", "unit_change_submitted",
                 "unit_change_resubmitted" -> switch (status) {
                case "success"   -> "已通过";
                case "failed"    -> "已驳回";
                case "cancelled" -> "已撤回";
                default          -> "待教师审核";
            };
            case "self_practice_approved", "delay_approved", "unit_change_approved" -> "已通过";
            case "self_practice_rejected", "delay_rejected", "unit_change_rejected" -> "已驳回";
            case "reflection_submitted", "reflection_resubmitted" -> "已提交";
            case "reflection_scored" -> "已评分";
            case "stage_continued", "stage_auto_continued", "stage_change_selected" -> "已选择";
            case "internship_started" -> "实习进行中";
            case "internship_ended" -> "实习已结束";
            case "internship_interrupted" -> "实习已中断";
            default -> switch (status) {
                case "pending" -> "待处理";
                case "success" -> "已完成";
                case "failed" -> "未通过";
                case "cancelled" -> "已撤回";
                default -> "动态";
            };
        };
    }

    private RecentActivityActionDTO actionOf(String eventType,
                                             Long relatedId,
                                             boolean targetExists) {
        if (relatedId == null || !targetExists) {
            return null;
        }
        return switch (eventType) {
            case "job_applied", "job_rejected" ->
                    new RecentActivityActionDTO("VIEW_APPLICATION", relatedId, Map.of());
            case "interview_invited", "interview_failed" ->
                    new RecentActivityActionDTO("VIEW_INTERVIEW", relatedId, Map.of());
            case "interview_passed", "direct_offer" ->
                    new RecentActivityActionDTO("START_CONFIRMATION", relatedId, Map.of());
            case "confirmation_submitted", "confirmation_confirmed", "confirmation_rejected",
                 "confirmation_withdrawn" ->
                    new RecentActivityActionDTO("VIEW_CONFIRMATION", relatedId, Map.of());
            case "self_practice_submitted", "self_practice_approved", "self_practice_rejected" ->
                    new RecentActivityActionDTO("VIEW_APPLICATION", relatedId,
                            Map.of("applicationType", "selfPractice"));
            case "delay_submitted", "delay_approved", "delay_rejected" ->
                    new RecentActivityActionDTO("VIEW_APPLICATION", relatedId,
                            Map.of("applicationType", "delay"));
            case "unit_change_submitted", "unit_change_resubmitted", "unit_change_approved",
                 "unit_change_rejected" ->
                    new RecentActivityActionDTO("MANAGE_UNIT_CHANGE", relatedId, Map.of());
            case "reflection_submitted", "reflection_resubmitted", "reflection_scored" ->
                    new RecentActivityActionDTO("VIEW_REFLECTION", relatedId, Map.of());
            case "stage_continued", "stage_auto_continued", "stage_change_selected",
                 "internship_started", "internship_ended", "internship_interrupted" ->
                    new RecentActivityActionDTO("VIEW_INTERNSHIP", relatedId, Map.of());
            default -> null;
        };
    }

    private String encodeCursor(Date occurredAt, Long id) {
        if (occurredAt == null || id == null) {
            throw new IllegalStateException("分页记录缺少排序字段");
        }
        ByteBuffer buffer = ByteBuffer.allocate(CURSOR_BYTES);
        buffer.putLong(occurredAt.getTime());
        buffer.putLong(id);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor.trim());
            if (bytes.length != CURSOR_BYTES) {
                throw new IllegalArgumentException("无效的分页游标");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long timeMillis = buffer.getLong();
            long id = buffer.getLong();
            if (timeMillis < 0 || id <= 0) {
                throw new IllegalArgumentException("无效的分页游标");
            }
            return new Cursor(new Date(timeMillis), id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的分页游标");
        }
    }

    private boolean isSuccessful(String status) {
        return "success".equals(status) || "approved".equals(status)
                || "passed".equals(status) || "interview_passed".equals(status);
    }

    private boolean isFailed(String status) {
        return "failed".equals(status) || "rejected".equals(status)
                || "interview_failed".equals(status);
    }

    private boolean isCancelled(String status) {
        return "cancelled".equals(status) || "canceled".equals(status)
                || "withdrawn".equals(status);
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Cursor(Date occurredAt, Long id) {
    }
}
