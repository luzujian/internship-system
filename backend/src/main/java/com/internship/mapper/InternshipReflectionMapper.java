package com.internship.mapper;

import com.internship.entity.InternshipReflection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipReflectionMapper {

    int insert(InternshipReflection internshipReflection);

    int updateById(InternshipReflection internshipReflection);

    int withdrawSubmitted(@Param("id") Long id,
                          @Param("studentId") Long studentId,
                          @Param("updateTime") java.util.Date updateTime);

    int deleteById(Long id);

    InternshipReflection findById(Long id);

    List<InternshipReflection> findByIds(@Param("ids") List<Long> ids);

    InternshipReflection lockById(@Param("id") Long id);

    boolean hasManualEvaluation(@Param("reflectionId") Long reflectionId);

    Long latestSubmissionEventId(@Param("reflectionId") Long reflectionId, @Param("studentId") Long studentId);

    int completeAiAnalysis(@Param("id") Long id, @Param("accepted") boolean accepted,
                           @Param("updateTime") java.util.Date updateTime);

    List<Long> findPendingAiAnalysis(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts,
                                    @Param("retryDelaySeconds") int retryDelaySeconds);

    int countAiFailures(@Param("reflectionId") Long reflectionId, @Param("studentId") Long studentId,
                        @Param("version") Long version);

    int recordAiFailure(@Param("snapshot") InternshipReflection snapshot, @Param("version") Long version,
                        @Param("failedAt") java.util.Date failedAt);

    InternshipReflection findByStudentId(Long studentId);

    List<InternshipReflection> findAll();

    List<InternshipReflection> findByStatus(@Param("status") Integer status);

    List<InternshipReflection> list(@Param("studentId") Long studentId,
                                     @Param("studentName") String studentName,
                                     @Param("studentUserId") String studentUserId,
                                     @Param("status") Integer status);

    int countByStatus(@Param("status") Integer status);

    /**
     * 检查学生是否在指定阶段已提交实习心得
     * @param studentId 学生ID
     * @param periodNumber 阶段编号
     * @return 已提交返回true，否则返回false
     */
    boolean existsByStudentIdAndPeriodNumber(@Param("studentId") Long studentId, @Param("periodNumber") Integer periodNumber);

    /**
     * 统计指定班级中待批改的实习心得数量（status=0表示已提交未批改）
     * @param classIds 班级ID列表
     * @return 待批改心得数量
     */
    int countPendingByClassIds(@Param("classIds") List<Long> classIds);
}
