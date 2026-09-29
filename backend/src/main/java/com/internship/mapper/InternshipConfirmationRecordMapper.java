package com.internship.mapper;

import com.internship.entity.InternshipConfirmationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InternshipConfirmationRecordMapper {

    int insert(InternshipConfirmationRecord record);

    int update(InternshipConfirmationRecord record);

    int submitPending(@Param("id") Long id, @Param("remark") String remark);

    int resolvePending(@Param("id") Long id,
                       @Param("status") Integer status,
                       @Param("remark") String remark);

    int recallPending(@Param("id") Long id, @Param("recallReason") String recallReason);

    InternshipConfirmationRecord findById(@Param("id") Long id);

    InternshipConfirmationRecord findByIdForUpdate(@Param("id") Long id);

    Long lockStudent(@Param("studentId") Long studentId);

    InternshipConfirmationRecord findActiveByStudentForUpdate(@Param("studentId") Long studentId);

    InternshipConfirmationRecord findSubmittedPendingByStudentForUpdate(@Param("studentId") Long studentId);

    List<InternshipConfirmationRecord> findByStudentId(@Param("studentId") Long studentId);

    List<InternshipConfirmationRecord> findByStudentIdOrderByCreateTimeDesc(@Param("studentId") Long studentId);

    List<InternshipConfirmationRecord> findByCompanyId(@Param("companyId") Long companyId);

    List<InternshipConfirmationRecord> findByCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") Integer status);

    List<InternshipConfirmationRecord> findByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") Integer status);

    List<InternshipConfirmationRecord> findPendingRecallList(@Param("studentId") Long studentId, @Param("name") String name, @Param("companyName") String companyName);
}
