package com.gdmu.mapper;

import com.gdmu.entity.InternshipReflection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipReflectionMapper {

    int insert(InternshipReflection internshipReflection);

    int updateById(InternshipReflection internshipReflection);

    int deleteById(Long id);

    InternshipReflection findById(Long id);

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
}
