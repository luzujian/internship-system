package com.gdmu.mapper;

import com.gdmu.entity.ProblemFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProblemFeedbackMapper {
    int insert(ProblemFeedback feedback);

    ProblemFeedback findById(Long id);

    int update(ProblemFeedback feedback);

    int deleteById(Long id);

    List<ProblemFeedback> findAll();

    List<ProblemFeedback> list(@Param("userType") String userType,
                                @Param("status") String status,
                                @Param("priority") String priority,
                                @Param("feedbackType") String feedbackType,
                                @Param("userName") String userName,
                                @Param("title") String title);

    List<ProblemFeedback> selectByIds(List<Long> ids);

    int batchDeleteByIds(@Param("ids") List<Long> ids);

    Long count();

    List<ProblemFeedback> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

    List<ProblemFeedback> findPageWithConditions(@Param("offset") Integer offset,
                                                   @Param("pageSize") Integer pageSize,
                                                   @Param("userType") String userType,
                                                   @Param("status") String status,
                                                   @Param("priority") String priority,
                                                   @Param("feedbackType") String feedbackType,
                                                   @Param("userName") String userName,
                                                   @Param("title") String title);

    Long countWithConditions(@Param("userType") String userType,
                             @Param("status") String status,
                             @Param("priority") String priority,
                             @Param("feedbackType") String feedbackType,
                             @Param("userName") String userName,
                             @Param("title") String title);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int reply(@Param("id") Long id,
              @Param("adminReply") String adminReply,
              @Param("adminId") Long adminId,
              @Param("adminName") String adminName,
              @Param("status") String status);

    Map<String, Object> getStatistics();

    Long countByStatus(@Param("status") String status);
}
