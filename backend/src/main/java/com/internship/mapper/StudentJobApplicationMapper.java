package com.internship.mapper;

import com.internship.entity.StudentJobApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentJobApplicationMapper {
    List<StudentJobApplication> findByStudentId(Long studentId);
    StudentJobApplication findLatestByStudentAndPositionForUpdate(@Param("studentId") Long studentId,
                                                                  @Param("positionId") Long positionId);
    StudentJobApplication findById(Long id);
    int insert(StudentJobApplication application);
    int update(StudentJobApplication application);
    int updateStatusIfActive(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("rejectReason") String rejectReason);
    int updateLatestStatusIfActive(@Param("studentId") Long studentId,
                                   @Param("positionId") Long positionId,
                                   @Param("status") String status,
                                   @Param("rejectReason") String rejectReason);
    int claimPendingForDirectHire(@Param("id") Long id, @Param("companyId") Long companyId);
    int deleteById(Long id);
    List<StudentJobApplication> findByCompanyId(Long companyId);

    /**
     * 获取学生首页统计数据（聚合查询，单次DB访问）
     * @param studentId 学生ID
     * @return 包含 applied, interviewInvites, confirmedRecords, submittedReflections 的Map
     */
    Map<String, Object> getStudentHomeStats(@Param("studentId") Long studentId);

    /**
     * 批量统计各职位的申请人数
     * @param positionIds 职位ID列表
     * @return 每行包含 position_id 和 cnt
     */
    List<Map<String, Object>> countByPositionIds(@Param("positionIds") List<Long> positionIds);

}
