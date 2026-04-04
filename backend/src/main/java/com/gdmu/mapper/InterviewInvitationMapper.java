package com.gdmu.mapper;

import com.gdmu.entity.InterviewInvitation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InterviewInvitationMapper {

    @Insert("INSERT INTO interview_invitation (student_id, company_id, position_id, position_name, company_name, " +
            "interview_time, interview_location, interview_type, status, reject_reason, remark, contact_person, contact_phone, create_time, update_time) " +
            "VALUES (#{studentId}, #{companyId}, #{positionId}, #{positionName}, #{companyName}, " +
            "#{interviewTime}, #{interviewLocation}, #{interviewMethod}, #{status}, #{rejectReason}, #{remark}, #{contactPerson}, #{contactPhone}, " +
            "#{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(InterviewInvitation invitation);

    @Update("UPDATE interview_invitation SET status = #{status}, reject_reason = #{rejectReason}, update_time = NOW() " +
            "WHERE id = #{id}")
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    @Results({
        @Result(property = "interviewMethod", column = "interview_type"),
        @Result(property = "contactPerson", column = "contact_person"),
        @Result(property = "contactPhone", column = "contact_phone")
    })
    @Select("SELECT ii.*, cu.website FROM interview_invitation ii " +
            "LEFT JOIN company_users cu ON ii.company_id = cu.id " +
            "WHERE ii.student_id = #{studentId} ORDER BY ii.interview_time DESC")
    List<InterviewInvitation> findByStudentId(@Param("studentId") Long studentId);

    @Results({
        @Result(property = "interviewMethod", column = "interview_type"),
        @Result(property = "contactPerson", column = "contact_person"),
        @Result(property = "contactPhone", column = "contact_phone")
    })
    @Select("SELECT ii.*, cu.website FROM interview_invitation ii " +
            "LEFT JOIN company_users cu ON ii.company_id = cu.id " +
            "WHERE ii.student_id = #{studentId} AND ii.position_id = #{positionId}")
    InterviewInvitation findByStudentAndPosition(@Param("studentId") Long studentId, @Param("positionId") Long positionId);

    @Results({
        @Result(property = "interviewMethod", column = "interview_type"),
        @Result(property = "contactPerson", column = "contact_person"),
        @Result(property = "contactPhone", column = "contact_phone")
    })
    @Select("SELECT * FROM interview_invitation WHERE id = #{id}")
    InterviewInvitation findById(@Param("id") Long id);

    @Results({
        @Result(property = "interviewMethod", column = "interview_type"),
        @Result(property = "contactPerson", column = "contact_person"),
        @Result(property = "contactPhone", column = "contact_phone")
    })
    @Select("SELECT ii.*, cu.website FROM interview_invitation ii " +
            "LEFT JOIN company_users cu ON ii.company_id = cu.id " +
            "WHERE ii.student_id = #{studentId} AND ii.status = #{status} ORDER BY ii.interview_time DESC")
    List<InterviewInvitation> findByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") String status);
}