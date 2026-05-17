package com.gdmu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.entity.Position;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.service.StudentJobApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AnnouncementWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Autowired
    private StudentJobApplicationService applicationService;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        String role = (String) session.getAttributes().get("role");
        
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        
        log.info("WebSocket连接建立：sessionId={}, userId={}, username={}, role={}", 
                 sessionId, userId, username, role);
        
        Map<String, Object> connectMessage = Map.of(
            "type", "connected",
            "message", "连接成功",
            "userId", userId,
            "role", role != null ? role : "",
            "timestamp", System.currentTimeMillis()
        );
        sendMessage(session, connectMessage);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息：{}", payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            if ("ping".equals(type)) {
                Map<String, Object> pong = Map.of(
                    "type", "pong",
                    "timestamp", System.currentTimeMillis()
                );
                sendMessage(session, pong);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败：{}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get("userId");
        
        sessions.remove(sessionId);
        
        if (userId != null) {
            Set<String> userSessionSet = userSessions.get(userId);
            if (userSessionSet != null) {
                userSessionSet.remove(sessionId);
                if (userSessionSet.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        
        log.info("WebSocket连接关闭：sessionId={}, userId={}, status={}", sessionId, userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误：sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    public void sendAnnouncementToRole(String role, Map<String, Object> announcement) {
        log.info("向角色 {} 推送公告：{}", role, announcement.get("title"));
        
        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            if (sessionRole != null && sessionRole.contains(role)) {
                try {
                    Map<String, Object> message = Map.of(
                        "type", "new_announcement",
                        "data", announcement,
                        "timestamp", System.currentTimeMillis()
                    );
                    sendMessage(session, message);
                    log.debug("已向用户 {} 推送公告", session.getAttributes().get("userId"));
                } catch (Exception e) {
                    log.error("推送公告失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public void sendAnnouncementToUser(Long userId, Map<String, Object> announcement) {
        log.info("向用户 {} 推送公告：{}", userId, announcement.get("title"));
        
        Set<String> userSessionSet = userSessions.get(userId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        Map<String, Object> message = Map.of(
                            "type", "new_announcement",
                            "data", announcement,
                            "timestamp", System.currentTimeMillis()
                        );
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("推送公告到用户失败：userId={}, error={}", userId, e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastAnnouncement(Map<String, Object> announcement) {
        log.info("广播公告：{}", announcement.get("title"));
        
        Map<String, Object> message = Map.of(
            "type", "new_announcement",
            "data", announcement,
            "timestamp", System.currentTimeMillis()
        );
        
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("广播公告失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public void sendFeedbackToAdmin(Map<String, Object> feedback) {
        log.info("向管理员推送问题反馈：{}", feedback.get("title"));

        Map<String, Object> message = Map.of(
            "type", "new_feedback",
            "data", feedback,
            "timestamp", System.currentTimeMillis()
        );

        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            if (sessionRole != null && sessionRole.contains("ADMIN")) {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                        log.debug("已向管理员 {} 推送问题反馈", session.getAttributes().get("userId"));
                    } catch (Exception e) {
                        log.error("推送问题反馈失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    public void sendApplicationStatusToUser(Long userId, Map<String, Object> applicationStatus) {
        log.info("向用户 {} 推送申请状态更新：{}", userId, applicationStatus);

        Set<String> userSessionSet = userSessions.get(userId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        Map<String, Object> message = Map.of(
                            "type", "application_status_update",
                            "data", applicationStatus,
                            "timestamp", System.currentTimeMillis()
                        );
                        sendMessage(session, message);
                        log.debug("已向用户 {} 推送申请状态更新", userId);
                    } catch (Exception e) {
                        log.error("推送申请状态到用户失败：userId={}, error={}", userId, e.getMessage());
                    }
                }
            }
        } else {
            log.debug("用户 {} 当前没有活跃的WebSocket连接", userId);
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    public int getOnlineCount() {
        return sessions.size();
    }

    public int getOnlineUserCount() {
        return userSessions.size();
    }

    /**
     * 向所有学生用户推送岗位数据更新
     * @param position 更新后的岗位数据
     */
    public void sendPositionUpdateToAll(Position position) {
        log.info("向所有学生推送岗位数据更新：positionId={}, remainingQuota={}",
                  position.getId(), position.getRemainingQuota());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "position_update");
        message.put("timestamp", System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("positionId", position.getId());
        data.put("positionName", position.getPositionName() != null ? position.getPositionName() : "");
        data.put("remainingQuota", position.getRemainingQuota() != null ? position.getRemainingQuota() : 0);
        data.put("plannedRecruit", position.getPlannedRecruit() != null ? position.getPlannedRecruit() : 0);
        data.put("recruitedCount", position.getRecruitedCount() != null ? position.getRecruitedCount() : 0);
        data.put("status", position.getStatus() != null ? position.getStatus() : "active");
        data.put("companyId", position.getCompanyId());

        message.put("data", data);

        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            // 只推送给学生用户
            if (sessionRole != null && sessionRole.contains("STUDENT")) {
                try {
                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("推送岗位更新失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向所有学生用户推送岗位删除消息
     * @param positionId 岗位ID
     */
    public void sendPositionDeleteToAll(Long positionId) {
        log.info("向所有学生推送岗位删除消息：positionId={}", positionId);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "position_delete");
        message.put("timestamp", System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("positionId", positionId);
        message.put("data", data);

        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            if (sessionRole != null && sessionRole.contains("STUDENT")) {
                try {
                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("推送岗位删除失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定用户发送面试卡片创建通知
     * @param userId 用户ID
     * @param interviewData 面试数据
     */
    public void sendInterviewCreateToUser(Long userId, Map<String, Object> interviewData) {
        log.info("向用户 {} 推送面试卡片创建通知：{}", userId, interviewData);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "interview_create");
        message.put("data", interviewData);
        message.put("timestamp", System.currentTimeMillis());

        sendToUser(userId, message);
    }

    /**
     * 向指定用户发送面试状态更新通知
     * @param userId 用户ID
     * @param interviewId 面试ID
     * @param status 面试状态
     */
    public void sendInterviewStatusUpdateToUser(Long userId, Long interviewId, String status) {
        log.info("向用户 {} 推送面试状态更新：interviewId={}, status={}", userId, interviewId, status);

        Map<String, Object> data = new HashMap<>();
        data.put("interviewId", interviewId);
        data.put("status", status);

        String statusText = switch (status) {
            case "pending_interview" -> "待面试";
            case "interview_passed" -> "面试通过";
            case "interview_failed" -> "面试没通过";
            default -> status;
        };
        data.put("statusText", statusText);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "interview_status_update");
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        sendToUser(userId, message);
    }

    /**
     * 向指定用户发送消息的私有辅助方法
     * @param userId 用户ID
     * @param message 要发送的消息
     */
    private void sendToUser(Long userId, Map<String, Object> message) {
        Set<String> userSessionSet = userSessions.get(userId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        sendMessage(session, message);
                        log.debug("已向用户 {} 发送消息", userId);
                    } catch (Exception e) {
                        log.error("向用户发送消息失败：userId={}, error={}", userId, e.getMessage());
                    }
                }
            }
        } else {
            log.debug("用户 {} 当前没有活跃的WebSocket连接", userId);
        }
    }

    /**
     * 向指定用户发送AI分析结果通知
     * @param userId 用户ID
     * @param reflectionId 实习心得ID
     * @param success 是否成功
     * @param isNotReflection 是否不是实习心得
     * @param message 消息内容
     */
    public void sendAIAnalysisResultToUser(Long userId, Long reflectionId, boolean success, boolean isNotReflection, String message) {
        log.info("向用户 {} 推送AI分析结果：reflectionId={}, success={}, isNotReflection={}",
                 userId, reflectionId, success, isNotReflection);

        Map<String, Object> data = new HashMap<>();
        data.put("reflectionId", reflectionId);
        data.put("success", success);
        data.put("isNotReflection", isNotReflection);
        data.put("message", message);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "ai_analysis_result");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        sendToUser(userId, wsMessage);
    }

    /**
     * 向指定企业用户推送待办数据更新
     * @param companyId 企业ID
     * @param pendingApplications 待处理申请数
     * @param pendingConfirmations 待确认实习表数
     */
    public void sendCompanyTodoUpdate(Long companyId, Long pendingApplications, Long pendingConfirmations) {
        log.info("向企业 {} 推送待办数据更新：待处理申请={}, 待确认实习表={}",
                 companyId, pendingApplications, pendingConfirmations);

        Map<String, Object> data = new HashMap<>();
        data.put("pendingApplications", pendingApplications);
        data.put("pendingConfirmations", pendingConfirmations);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "company_todo_update");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        // 推送给对应的企业用户
        log.info("当前在线session数量: {}, 查找companyId={}", sessions.size(), companyId);
        for (WebSocketSession session : sessions.values()) {
            Long sessionCompanyId = (Long) session.getAttributes().get("companyId");
            log.info("检查session: sessionId={}, companyId={}, role={}", session.getId(), sessionCompanyId, session.getAttributes().get("role"));
            if (sessionCompanyId != null && sessionCompanyId.equals(companyId)) {
                try {
                    sendMessage(session, wsMessage);
                    log.info("已向企业 {} 推送待办数据更新", companyId);
                } catch (Exception e) {
                    log.error("推送待办数据失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定企业用户推送新简历申请提醒（用于静默更新最新动态）
     * @param companyId 企业ID
     * @param studentName 学生姓名
     * @param positionName 职位名称
     */
    public void sendNewApplicationToCompany(Long companyId, String studentName, String positionName) {
        log.info("向企业 {} 推送新简历提醒: {} -> {}", companyId, studentName, positionName);

        Map<String, Object> data = new HashMap<>();
        data.put("studentName", studentName);
        data.put("positionName", positionName);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "new_application");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        for (WebSocketSession session : sessions.values()) {
            Long sessionCompanyId = (Long) session.getAttributes().get("companyId");
            if (sessionCompanyId != null && sessionCompanyId.equals(companyId)) {
                try {
                    sendMessage(session, wsMessage);
                } catch (Exception e) {
                    log.error("推送新简历提醒失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定企业用户推送待办数据更新（申请撤回时调用）
     * @param companyId 企业ID
     */
    public void sendCompanyTodoUpdateForApplication(Long companyId) {
        log.info("向企业 {} 推送待办数据更新（申请撤回）", companyId);

        // 查询待处理申请数
        List<StudentJobApplication> applications = applicationService.findByCompanyId(companyId);
        long pendingApplications = applications.stream()
                .filter(a -> "pending".equals(a.getStatus()))
                .count();

        // 查询待确认实习表数
        List<StudentInternshipStatus> allStatuses = internshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
        long pendingConfirmations = allStatuses.stream()
                .filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 0)
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("pendingApplications", pendingApplications);
        data.put("pendingConfirmations", pendingConfirmations);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "company_todo_update");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        for (WebSocketSession session : sessions.values()) {
            Long sessionCompanyId = (Long) session.getAttributes().get("companyId");
            if (sessionCompanyId != null && sessionCompanyId.equals(companyId)) {
                try {
                    sendMessage(session, wsMessage);
                    log.info("已向企业 {} 推送待办数据更新（申请撤回）", companyId);
                } catch (Exception e) {
                    log.error("推送待办数据失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定学生用户推送实习确认结果更新
     * @param studentId 学生ID
     * @param confirmationStatus 确认状态：1=已确认, 2=已拒绝
     * @param companyName 企业名称
     * @param positionName 职位名称
     */
    public void sendConfirmationResultToStudent(Long studentId, Integer confirmationStatus, String companyName, String positionName) {
        log.info("向学生 {} 推送实习确认结果：status={}, company={}, position={}",
                 studentId, confirmationStatus, companyName, positionName);

        Map<String, Object> data = new HashMap<>();
        data.put("confirmationStatus", confirmationStatus);
        data.put("companyName", companyName);
        data.put("positionName", positionName);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "confirmation_result_update");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        // 推送给对应的学生用户
        Set<String> userSessionSet = userSessions.get(studentId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        sendMessage(session, wsMessage);
                        log.info("已向学生 {} 推送实习确认结果", studentId);
                    } catch (Exception e) {
                        log.error("推送实习确认结果失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        } else {
            log.info("学生 {} 当前无在线session，跳过推送", studentId);
        }
    }

    /**
     * 向指定辅导员用户推送待评分数据更新
     * @param counselorId 辅导员ID
     * @param pendingEvaluations 待评分学生数
     */
    public void sendTeacherTodoUpdate(Long counselorId, Long pendingEvaluations) {
        log.info("向辅导员 {} 推送待评分数据更新：待评分={}", counselorId, pendingEvaluations);

        Map<String, Object> data = new HashMap<>();
        data.put("pendingEvaluations", pendingEvaluations);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "teacher_todo_update");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        // 推送给对应的辅导员用户
        Set<String> userSessionSet = userSessions.get(counselorId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        sendMessage(session, wsMessage);
                        log.info("已向辅导员 {} 推送待评分数据更新", counselorId);
                    } catch (Exception e) {
                        log.error("推送待评分数据失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        } else {
            log.info("辅导员 {} 当前无在线session，跳过推送", counselorId);
        }
    }

    /**
     * 向指定学生用户推送成绩发布通知（评分已发布）
     * @param studentId 学生ID
     */
    public void sendGradePublishedToStudent(Long studentId) {
        log.info("向学生 {} 推送成绩发布通知", studentId);

        Map<String, Object> data = new HashMap<>();
        data.put("message", "教师已发布评分成绩");

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "grade_published");
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());

        // 推送给对应的学生用户
        Set<String> userSessionSet = userSessions.get(studentId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        sendMessage(session, wsMessage);
                        log.info("已向学生 {} 推送成绩发布通知", studentId);
                    } catch (Exception e) {
                        log.error("推送成绩发布通知失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        } else {
            log.info("学生 {} 当前无在线session，跳过推送", studentId);
        }
    }

    /**
     * 向指定学生用户推送提醒消息
     * @param studentId 学生ID
     * @param reminderData 提醒数据（包含 id 和 content）
     */
    public void sendReminderToStudent(Long studentId, Map<String, Object> reminderData) {
        log.info("向学生 {} 推送提醒消息：{}", studentId, reminderData.get("content"));

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "student_reminder");
        wsMessage.put("data", reminderData);
        wsMessage.put("timestamp", System.currentTimeMillis());

        // 推送给对应的学生用户
        Set<String> userSessionSet = userSessions.get(studentId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        sendMessage(session, wsMessage);
                        log.info("已向学生 {} 推送提醒消息", studentId);
                    } catch (Exception e) {
                        log.error("推送提醒消息失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        } else {
            log.info("学生 {} 当前无在线session，提醒将存入数据库待下次连接时显示", studentId);
        }
    }
}
