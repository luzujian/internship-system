package com.internship.mapper;

import com.internship.agent.business.AgentTypes.Actor;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Fixed, parameterized SELECTs only. Never exposed as an arbitrary SQL tool. */
@Mapper
public interface AgentReadMapper {
    List<Long> teacherScopeIds(@Param("actor") Actor actor, @Param("period") int period);
    List<Map<String, Object>> searchJobs(@Param("keyword") String keyword, @Param("city") String city,
            @Param("offset") int offset, @Param("limit") int limit);
    long countJobs(@Param("keyword") String keyword, @Param("city") String city);
    List<Map<String, Object>> teacherReflections(@Param("actor") Actor actor, @Param("period") int period,
            @Param("classId") Long classId, @Param("studentId") Long studentId, @Param("status") String status,
            @Param("offset") int offset, @Param("limit") int limit);
    Map<String, Object> reflectionCounts(@Param("actor") Actor actor, @Param("period") int period,
            @Param("classId") Long classId, @Param("studentId") Long studentId);
    List<Map<String, Object>> teacherSummaryScopes(@Param("actor") Actor actor,
            @Param("scopeType") String scopeType, @Param("scopeName") String scopeName,
            @Param("scopeId") Long scopeId);
    List<Map<String, Object>> teacherSummaryStudentRows(@Param("actor") Actor actor,
            @Param("scopeType") String scopeType, @Param("scopeId") Long scopeId);

    Map<String, Object> studentAdvisorProfile(@Param("studentId") Long studentId);
    List<Map<String, Object>> studentReflections(@Param("studentId") Long studentId, @Param("offset") int offset, @Param("limit") int limit);
    long countStudentReflections(@Param("studentId") Long studentId);
    Map<String, Object> studentAppraisal(@Param("studentId") Long studentId);
    Map<String, Object> teacherTodoCounts(@Param("actor") Actor actor);

    // 企业端：companyId 必须来自后端核验的企业身份
    List<Map<String, Object>> companyJobs(@Param("companyId") Long companyId, @Param("status") String status,
            @Param("offset") int offset, @Param("limit") int limit);
    long countCompanyJobs(@Param("companyId") Long companyId, @Param("status") String status);
    List<Map<String, Object>> companyApplications(@Param("companyId") Long companyId, @Param("positionId") Long positionId,
            @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    long countCompanyApplications(@Param("companyId") Long companyId, @Param("positionId") Long positionId,
            @Param("status") String status);
    List<Map<String, Object>> companyPendingConfirmations(@Param("companyId") Long companyId,
            @Param("offset") int offset, @Param("limit") int limit);
    long countCompanyPendingConfirmations(@Param("companyId") Long companyId);

    // 管理端：只读总量与待审核队列
    List<Map<String, Object>> adminPendingCompanies(@Param("offset") int offset, @Param("limit") int limit);
    long countAdminPendingCompanies();
    Map<String, Object> adminOverview();

    // 归属校验用最小投影
    Long reflectionOwner(@Param("id") Long id);
    Long appraisalOwner(@Param("id") Long id);
    Long positionOwnerCompany(@Param("id") Long id);
    Long applicationOwnerCompany(@Param("id") Long id);
}
