package com.gdmu.mapper;

import com.gdmu.entity.StudentJobApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentJobApplicationMapper {
    List<StudentJobApplication> findByStudentId(Long studentId);
    StudentJobApplication findById(Long id);
    int insert(StudentJobApplication application);
    int update(StudentJobApplication application);
    int deleteById(Long id);
    List<StudentJobApplication> findByCompanyId(Long companyId);

    /**
     * 获取学生首页统计数据（聚合查询，单次DB访问）
     * @param studentId 学生ID
     * @return 包含 applied, interviewInvites, confirmedRecords, submittedReflections 的Map
     */
    Map<String, Object> getStudentHomeStats(@Param("studentId") Long studentId);
}
