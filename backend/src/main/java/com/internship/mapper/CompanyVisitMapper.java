package com.internship.mapper;

import com.internship.entity.CompanyVisitParticipant;
import com.internship.entity.CompanyVisitRecord;
import com.internship.entity.CompanyVisitSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyVisitMapper {
    CompanyVisitRecord findById(@Param("id") Long id);

    List<CompanyVisitRecord> findList(@Param("companyId") Long companyId,
                                      @Param("companyName") String companyName,
                                      @Param("year") Integer year,
                                      @Param("participantTeacherId") Long participantTeacherId);

    List<CompanyVisitParticipant> findParticipantsByRecordId(@Param("visitRecordId") Long visitRecordId);

    List<CompanyVisitParticipant> findParticipantsByRecordIds(@Param("visitRecordIds") List<Long> visitRecordIds);

    List<CompanyVisitSummary> summarizeByCompanyIds(@Param("companyIds") List<Long> companyIds,
                                                     @Param("selectedYear") Integer selectedYear,
                                                     @Param("currentYear") Integer currentYear);

    int insertRecord(CompanyVisitRecord record);

    int updateRecord(CompanyVisitRecord record);

    int deleteRecord(@Param("id") Long id);

    int batchInsertParticipants(@Param("participants") List<CompanyVisitParticipant> participants);

    int deleteParticipantsByRecordId(@Param("visitRecordId") Long visitRecordId);
}
