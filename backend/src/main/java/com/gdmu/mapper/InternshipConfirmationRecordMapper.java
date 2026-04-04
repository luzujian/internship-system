package com.gdmu.mapper;

import com.gdmu.entity.InternshipConfirmationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InternshipConfirmationRecordMapper {

    int insert(InternshipConfirmationRecord record);

    int update(InternshipConfirmationRecord record);

    InternshipConfirmationRecord findById(@Param("id") Long id);

    List<InternshipConfirmationRecord> findByStudentId(@Param("studentId") Long studentId);

    List<InternshipConfirmationRecord> findByStudentIdOrderByCreateTimeDesc(@Param("studentId") Long studentId);

    List<InternshipConfirmationRecord> findByCompanyId(@Param("companyId") Long companyId);

    List<InternshipConfirmationRecord> findByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") Integer status);

    List<InternshipConfirmationRecord> findByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") Integer status);
}
