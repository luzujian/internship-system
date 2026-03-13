package com.gdmu.mapper;

import com.gdmu.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface SubmissionMapper {
    // 根据ID查询提交信息
    @Select("select * from submission where id = #{id}")
    Submission selectById(Long id);

    // 根据作业ID查询提交信息
    @Select("select * from submission where assignment_id = #{assignmentId}")
    List<Submission> selectByAssignmentId(Long assignmentId);
    
    // 根据作业ID查询提交信息，按批改状态排序（未批改的在前）
    @Select("select * from submission where assignment_id = #{assignmentId} order by (teacher_score is null) desc, submit_time asc")
    List<Submission> selectByAssignmentIdOrderByGradedStatus(Long assignmentId);

    // 根据小组ID查询提交信息
    @Select("select * from submission where group_id = #{groupId}")
    List<Submission> selectByGroupId(Long groupId);

    // 根据作业ID和小组ID查询提交信息
    @Select("select * from submission where assignment_id = #{assignmentId} and group_id = #{groupId}")
    Submission selectByAssignmentIdAndGroupId(Long assignmentId, Long groupId);
    
    // 根据学生ID查询提交信息
    @Select("SELECT s.* FROM submission s JOIN group_member gm ON s.group_id = gm.group_id WHERE gm.student_id = #{studentId}")
    List<Submission> selectByStudentId(Long studentId);
    
    // 高效获取学生已提交的作业ID列表
    @Select("SELECT DISTINCT s.assignment_id FROM submission s JOIN group_member gm ON s.group_id = gm.group_id WHERE gm.student_id = #{studentId}")
    List<Long> selectSubmittedAssignmentIdsByStudentId(Long studentId);

    // 新增提交
    @Insert("insert into submission(assignment_id, group_id, content, file_path, submit_time, teacher_score, status, submitter_id, submit_note, online_content, file_count, has_contribution, contribution_scores, group_member_count, is_final_score_calculated, teacher_comment, graded_time, graded_by, is_score_published, score_publish_time) values(#{assignmentId}, #{groupId}, #{content}, #{filePath}, #{submitTime}, #{teacherScore}, #{status}, #{submitterId}, #{submitNote}, #{onlineContent}, #{fileCount}, #{hasContribution}, #{contributionScores}, #{groupMemberCount}, #{isFinalScoreCalculated}, #{teacherComment}, #{gradedTime}, #{gradedBy}, #{isScorePublished}, #{scorePublishTime})"
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Submission submission);

    // 更新提交
    @Update("update submission set content = #{content}, file_path = #{filePath}, teacher_score = #{teacherScore}, status = #{status}, submitter_id = #{submitterId}, submit_note = #{submitNote}, online_content = #{onlineContent}, file_count = #{fileCount}, has_contribution = #{hasContribution}, contribution_scores = #{contributionScores}, group_member_count = #{groupMemberCount}, is_final_score_calculated = #{isFinalScoreCalculated}, teacher_comment = #{teacherComment}, graded_time = #{gradedTime}, graded_by = #{gradedBy}, is_score_published = #{isScorePublished}, score_publish_time = #{scorePublishTime} where id = #{id}")
    int update(Submission submission);

    // 删除提交
    @Delete("delete from submission where id = #{id}")
    int deleteById(Long id);
}