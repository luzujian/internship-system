package com.internship.mapper;

import com.internship.entity.ProblemFeedbackEmailReply;
import org.apache.ibatis.annotations.*;
import java.util.Date;
import java.util.List;

@Mapper
public interface ProblemFeedbackEmailReplyMapper {
    @Select("SELECT * FROM problem_feedback_email_reply WHERE request_id = #{requestId}")
    ProblemFeedbackEmailReply findByRequestId(String requestId);

    @Select("SELECT * FROM problem_feedback_email_reply WHERE feedback_id = #{feedbackId} ORDER BY id DESC LIMIT 20")
    List<ProblemFeedbackEmailReply> findHistory(Long feedbackId);

    @Insert("""
        INSERT INTO problem_feedback_email_reply
        (feedback_id,request_id,recipient_email,subject,reply_content,mail_body,admin_id,admin_name,
         feedback_status,delivery_status,delivery_detail,created_at)
        VALUES (#{feedbackId},#{requestId},#{recipientEmail},#{subject},#{replyContent},#{mailBody},
                #{adminId},#{adminName},#{feedbackStatus},#{deliveryStatus},#{deliveryDetail},#{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProblemFeedbackEmailReply reply);

    @Update("""
        UPDATE problem_feedback_email_reply
        SET delivery_status = #{status}, delivery_detail = #{detail}, finished_at = #{finishedAt}
        WHERE id = #{id} AND delivery_status = 'SENDING'
        """)
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("detail") String detail, @Param("finishedAt") Date finishedAt);
}
