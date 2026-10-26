package com.internship.mapper;

import com.internship.dto.TeacherCompanyDTO;
import com.internship.entity.CompanyUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CompanyUserMapper {
    
    int insert(CompanyUser companyUser);
    
    int batchInsert(@Param("companyUsers") List<CompanyUser> companyUsers);
    
    CompanyUser findById(Long id);
    
    CompanyUser findByUsername(@Param("username") String username);
    
    int update(CompanyUser companyUser);

    @Update("UPDATE company_users SET phone=#{phone}, email=#{email}, update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateAccountContact(CompanyUser companyUser);

    int updateImportedProfile(CompanyUser companyUser);

    @Update("update company_users set password = #{passwordHash}, update_time = CURRENT_TIMESTAMP")
    int resetAllPasswords(@Param("passwordHash") String passwordHash);
    
    int deleteById(Long id);
    
    List<CompanyUser> findAll();
    
    List<CompanyUser> list(@Param("companyName") String companyName, 
                           @Param("status") Integer status);
    
    List<CompanyUser> listWithRecallStatus(@Param("companyName") String companyName, 
                                             @Param("status") Integer status,
                                             @Param("recallStatus") Integer recallStatus);
    
    List<CompanyUser> listWithRecallStatusAndTag(@Param("companyName") String companyName,
                                                     @Param("status") Integer status,
                                                     @Param("recallStatus") Integer recallStatus,
                                                     @Param("companyTags") List<String> companyTags,
                                                     @Param("industry") String industry);

    List<TeacherCompanyDTO> findTeacherCompanies(@Param("companyName") String companyName,
                                                 @Param("status") Integer status,
                                                 @Param("auditStatus") Integer auditStatus,
                                                 @Param("recallStatus") Integer recallStatus,
                                                 @Param("companyTags") List<String> companyTags,
                                                 @Param("industry") String industry,
                                                 @Param("keyword") String keyword,
                                                 @Param("visitedYear") Integer visitedYear,
                                                 @Param("visitState") Integer visitState,
                                                 @Param("visitTeacherId") Long visitTeacherId,
                                                 @Param("liaisonTeacherId") Long liaisonTeacherId);

    TeacherCompanyDTO findTeacherCompanyById(@Param("id") Long id);
    
    List<CompanyUser> selectByIds(List<Long> ids);
    
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    Long count();
    
    List<CompanyUser> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    int countByCreateTimeAfter(@Param("createTime") java.util.Date createTime);
    
    int countByStatus(@Param("status") Integer status);
    
    List<CompanyUser> selectAll();
    
    List<CompanyUser> findByAuditStatus(@Param("auditStatus") Integer auditStatus);
    
    List<CompanyUser> findPendingAudit(@Param("companyName") String companyName,
                                        @Param("contactPerson") String contactPerson,
                                        @Param("contactPhone") String contactPhone);

    List<CompanyUser> findPendingAuditWithStatus(@Param("companyName") String companyName,
                                                 @Param("contactPerson") String contactPerson,
                                                 @Param("contactPhone") String contactPhone,
                                                 @Param("status") Integer status);
    
    Long countByAuditStatus(@Param("auditStatus") Integer auditStatus);

    /**
     * 统计已审核通过的入驻企业数量（status=1 且 audit_status=1）
     * @return 已审核通过的企业数量
     */
    Integer countApproved();

    int updateAuditStatus(@Param("id") Long id, 
                          @Param("auditStatus") Integer auditStatus, 
                          @Param("auditRemark") String auditRemark, 
                          @Param("reviewerId") Long reviewerId);
    
    List<CompanyUser> findByRecallStatus(@Param("recallStatus") Integer recallStatus);
    
    List<CompanyUser> findPendingRecallAudit(@Param("companyName") String companyName,
                                             @Param("contactPerson") String contactPerson,
                                             @Param("contactPhone") String contactPhone,
                                             @Param("offset") Integer offset,
                                             @Param("pageSize") Integer pageSize);
    
    List<CompanyUser> findPendingRecallAuditById(@Param("companyId") Long companyId,
                                                @Param("offset") Integer offset,
                                                @Param("pageSize") Integer pageSize);
    
    Long countByRecallStatus(@Param("recallStatus") Integer recallStatus);
    
    int updateRecallStatus(@Param("id") Long id,
                           @Param("recallStatus") Integer recallStatus,
                           @Param("recallAuditRemark") String recallAuditRemark,
                           @Param("recallReviewerId") Long recallReviewerId);

    int clearRecallData();

    List<CompanyUser> findRecallRecords(@Param("companyName") String companyName,
                                        @Param("contactPerson") String contactPerson);

    List<CompanyUser> findRecallRecords(@Param("companyName") String companyName,
                                        @Param("contactPerson") String contactPerson,
                                        @Param("startTime") String startTime,
                                        @Param("endTime") String endTime);

    int clearRecallDataById(@Param("id") Long id);

    /**
     * 根据公司名称查找公司
     */
    CompanyUser findByCompanyName(@Param("companyName") String companyName);

    @org.apache.ibatis.annotations.Select("SELECT * FROM company_users WHERE LOWER(TRIM(company_name)) = LOWER(TRIM(#{companyName}))")
    List<CompanyUser> findImportMatches(@Param("companyName") String companyName);

    CompanyUser findApprovedByCompanyName(@Param("companyName") String companyName);

    List<CompanyUser> findApprovedCompanyMatches(@Param("keyword") String keyword);

    /**
     * 查询全部有效的校企合作企业（启用、审核通过、未召回）
     */
    List<CompanyUser> findAllApprovedCompanies();

    /**
     * 获取所有不重复的企业标签
     */
    List<String> getDistinctTags();

    /**
     * 校企合作标签及兼容旧记录的兜底标签
     */
    List<String> getDistinctCooperationModeTags();

    /**
     * 状态类标签（实习基地/已签协议/校友单位）
     */
    List<String> getDistinctFlagTags();

    // ========== 删除企业级联清理：按 company_id 删除所有引用该企业的业务表记录（每条都在 Service 层独立 try-catch，表不存在时跳过） ==========
    // 1. 先清理依赖岗位/鉴定提交的子记录（需在删 position / appraisal_submission 之前）
    @Delete("DELETE iaf FROM internship_appraisal_file iaf LEFT JOIN internship_appraisal_submission ias ON iaf.submission_id = ias.id WHERE ias.company_id = #{companyId}")
    int deleteAppraisalFileByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from position where company_id = #{companyId}")
    int deletePositionsByCompanyId(@Param("companyId") Long companyId);

    // 2. 实习申请 / 岗位申请
    @Delete("delete from internship_application where company_id = #{companyId}")
    int deleteInternshipApplicationByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from student_job_application where company_id = #{companyId}")
    int deleteJobApplicationByCompanyId(@Param("companyId") Long companyId);

    // 3. 实习确认 / 阶段分配 / 实习状态
    @Delete("delete from internship_confirmation_record where company_id = #{companyId}")
    int deleteConfirmationRecordByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from internship_stage_assignment where company_id = #{companyId}")
    int deleteStageAssignmentByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from student_internship_status where company_id = #{companyId}")
    int deleteInternshipStatusByCompanyId(@Param("companyId") Long companyId);

    // 4. 面试邀请 / 通知
    @Delete("delete from interview_invitation where company_id = #{companyId}")
    int deleteInterviewInvitationByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from company_notification where company_id = #{companyId}")
    int deleteCompanyNotificationByCompanyId(@Param("companyId") Long companyId);

    // 5. 企业扩展信息：资质 / 校企联络员 / 校友成员 / 访问记录 / 推荐评级
    @Delete("delete from company_qualification where company_id = #{companyId}")
    int deleteCompanyQualificationByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from company_school_liaison where company_id = #{companyId}")
    int deleteCompanySchoolLiaisonByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from company_alumni_member where company_id = #{companyId}")
    int deleteCompanyAlumniMemberByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from company_visit_record where company_id = #{companyId}")
    int deleteCompanyVisitRecordByCompanyId(@Param("companyId") Long companyId);

    @Delete("delete from company_recommendation_rating where company_id = #{companyId}")
    int deleteCompanyRecommendationRatingByCompanyId(@Param("companyId") Long companyId);

    // 6. 企业关联的实习鉴定提交记录（含 company_id 列），鉴定文件记录已在第 1 步删除
    @Delete("delete from internship_appraisal_submission where company_id = #{companyId}")
    int deleteAppraisalSubmissionByCompanyId(@Param("companyId") Long companyId);
}
