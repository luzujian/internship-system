package com.internship.mapper;

import com.internship.entity.StudentUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentUserMapper {
    StudentUser findByStudentId(@Param("studentId") String studentId);
    int insert(StudentUser studentUser);
    StudentUser findById(Long id);
    int update(StudentUser studentUser);

    @Update("update student_users set password = #{passwordHash}, update_time = CURRENT_TIMESTAMP")
    int resetAllPasswords(@Param("passwordHash") String passwordHash);
    
    // 查询所有学生用户，配合PageHelper使用
    List<StudentUser> findAll();

    // 动态条件查询
    List<StudentUser> list(@Param("studentId") String studentId, 
                          @Param("name") String name, 
                          @Param("grade") Integer grade, 
                          @Param("majorId") Integer majorId, 
                          @Param("classId") String classId);

    List<StudentUser> listByTeacherScope(@Param("searchName") String searchName,
                                         @Param("classId") Long classId,
                                         @Param("departmentIds") List<Long> departmentIds,
                                         @Param("divisionIds") List<Long> divisionIds,
                                         @Param("classIds") List<Long> classIds);
    
    // 动态条件查询（包含状态参数）
    List<StudentUser> listWithStatus(@Param("studentId") String studentId,
                          @Param("name") String name,
                          @Param("grade") Integer grade,
                          @Param("majorId") Integer majorId,
                          @Param("classId") String classId,
                          @Param("status") Integer status,
                          @Param("gender") Integer gender,
                          @Param("department") String department);
    
    // 根据ID列表查询学生用户
    // 该方法在XML文件中实现
    List<StudentUser> selectByIds(List<Long> ids);
    
    // 查询总记录数
    @Select("select count(1) from student_users")
    Long count();
    
    // 分页查询学生用户
    @Select("select * from student_users limit #{offset}, #{pageSize}")
    List<StudentUser> findPage(Integer offset, Integer pageSize);
    
    // 删除学生用户
    @Delete("delete from student_users where id = #{id}")
    int deleteById(Long id);
    
    /**
     * 批量查询学生用户，根据学号列表
     * @param studentIds 学号列表
     * @return 学生用户列表
     */
    List<StudentUser> findByStudentIds(@Param("studentIds") List<String> studentIds);
    
    /**
     * 批量插入学生用户
     * @param studentUsers 学生用户列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("studentUsers") List<StudentUser> studentUsers);
    
    /**
     * 批量删除学生用户，根据ID列表
     * @param ids 学生用户ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 根据班级ID查询学生数量
     * @param classId 班级ID
     * @return 学生数量
     */
    Integer countByClassId(@Param("classId") Long classId);
    
    /**
     * 根据班级ID查询所有学生
     * @param classId 班级ID
     * @return 学生列表
     */
    List<StudentUser> findByClassId(@Param("classId") Long classId);
    
    /**
     * 更新 group_table 表中的 leader_student_id 为 NULL
     * @param ids 学生ID列表
     * @return 更新的记录数
     */
    int updateGroupLeaderToNull(@Param("ids") List<Long> ids);
    
    /**
     * 根据课程ID列表查询学生信息
     * @param courseIds 课程ID列表
     * @param searchName 学生姓名搜索关键字
     * @param classId 班级ID
     * @return 学生信息列表
     */
    List<Map<String, Object>> findStudentsByCourseIds(@Param("courseIds") List<Long> courseIds, 
                                                      @Param("searchName") String searchName, 
                                                      @Param("classId") Long classId);

    List<StudentUser> list(@Param("studentId") String studentId, 
                          @Param("name") String name, 
                          @Param("grade") Integer grade, 
                          @Param("majorId") Integer majorIdInt, 
                          @Param("classId") Long classId, 
                          @Param("status") Integer status);
    
    /**
     * 删除contribution表中与特定学生相关的记录
     * @param studentId 学生ID
     * @return 删除的记录数
     */
    @Delete("delete from contribution where student_user_id = #{studentId}")
    int deleteContributionByStudentId(Long studentId);

    // ===== 级联清理学生关联业务数据（删除学生时调用，防止幽灵数据） =====
    @Delete("delete from internship_application where student_id = #{studentId}")
    int deleteApplicationByStudentId(Long studentId);

    @Delete("delete from student_internship_status where student_id = #{studentId}")
    int deleteInternshipStatusByStudentId(Long studentId);

    @Delete("delete from student_job_application where student_id = #{studentId}")
    int deleteJobApplicationByStudentId(Long studentId);

    @Delete("delete from internship_confirmation_record where student_id = #{studentId}")
    int deleteConfirmationRecordByStudentId(Long studentId);

    @Delete("delete from internship_stage_assignment where student_id = #{studentId}")
    int deleteStageAssignmentByStudentId(Long studentId);

    @Delete("delete from internship_progress_record where student_id = #{studentId}")
    int deleteProgressRecordByStudentId(Long studentId);

    @Delete("delete from interview_invitation where student_id = #{studentId}")
    int deleteInterviewInvitationByStudentId(Long studentId);

    @Delete("delete from position_favorite where student_id = #{studentId}")
    int deletePositionFavoriteByStudentId(Long studentId);

    @Delete("delete from position_view_record where student_id = #{studentId}")
    int deletePositionViewRecordByStudentId(Long studentId);

    @Delete("delete from student_archive where student_id = #{studentId}")
    int deleteArchiveByStudentId(Long studentId);

    @Delete("delete from student_reminder where student_id = #{studentId}")
    int deleteReminderByStudentId(Long studentId);

    @Delete("delete from student_reflection_ai_analysis where student_id = #{studentId}")
    int deleteReflectionAiAnalysisByStudentId(Long studentId);

    @Delete("delete from student_reflection_evaluation where student_id = #{studentId}")
    int deleteReflectionEvaluationByStudentId(Long studentId);

    @Delete("delete from internship_evaluation where student_id = #{studentId}")
    int deleteEvaluationByStudentId(Long studentId);

    @Delete("delete from internship_evaluation_detail where student_id = #{studentId}")
    int deleteEvaluationDetailByStudentId(Long studentId);

    @Delete("delete from internship_appraisal_submission where student_id = #{studentId}")
    int deleteAppraisalSubmissionByStudentId(Long studentId);

    // ===== 补充：之前漏掉的关联表级联清理（删除学生时必须先清理，否则外键约束报错） =====
    @Delete("delete from student_application where student_id = #{studentId}")
    int deleteStudentApplicationByStudentId(Long studentId);

    @Delete("delete from internship_reflection where student_id = #{studentId}")
    int deleteInternshipReflectionByStudentId(Long studentId);

    @Delete("delete from internship_ai_analysis where student_id = #{studentId}")
    int deleteInternshipAIAnalysisByStudentId(Long studentId);

    // ===== 实习鉴定相关：先删文件记录，再删提交记录 =====
    @Delete("DELETE iaf FROM internship_appraisal_file iaf LEFT JOIN internship_appraisal_submission ias ON iaf.submission_id = ias.id WHERE ias.student_id = #{studentId}")
    int deleteAppraisalFileByStudentId(Long studentId);

    // ===== 小组成员表：真实存在，必须清理 student_user_id 外键 =====
    @Delete("delete from group_member where student_user_id = #{studentId}")
    int deleteGroupMemberByStudentUserId(Long studentId);

    // ===== 班级删除兜底：删学生级联失败时，直接把该班级下所有学生的 class_id 置为 NULL，绕过外键直接删班级 =====
    @Update("UPDATE student_users SET class_id = NULL WHERE class_id = #{classId}")
    int clearClassIdOnStudents(@Param("classId") Long classId);
    
    /**
     * 直接更新特定小组的所有组长相关字段为null
     * 这是解决小组组长外键约束的最终手段
     * @param groupId 小组ID
     * @return 更新的记录数
     */
    int updateSpecificGroupLeadersToNull(@Param("groupId") Long groupId);
    
    /**
     * 根据专业ID查询学生数量
     * @param majorId 专业ID
     * @return 学生数量
     */
    int countByMajorId(@Param("majorId") Long majorId);
    
    /**
     * 查询所有学生
     * @return 学生列表
     */
    List<StudentUser> selectAll();
    
    /**
     * 查询所有不重复的年级
     * @return 年级列表
     */
    List<Integer> findDistinctGrades();
    
    /**
     * 根据班级ID查询学生列表（返回Map格式）
     * @param classId 班级ID
     * @return 学生列表
     */
    List<Map<String, Object>> findByClassIdAsMap(@Param("classId") Long classId);
    
    /**
     * 根据多个班级ID和搜索条件查询学生
     * @param classIds 班级ID列表
     * @param searchName 搜索姓名
     * @param major 专业名称
     * @param grade 年级
     * @param status 实习状态
     * @param companyName 公司名称
     * @return 学生列表
     */
    List<Map<String, Object>> findByClassIdsAndSearch(
            @Param("classIds") List<Long> classIds,
            @Param("searchName") String searchName,
            @Param("major") String major,
            @Param("grade") String grade,
            @Param("status") Integer status,
            @Param("companyName") String companyName);
}
