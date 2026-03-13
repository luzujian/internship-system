package com.gdmu.mapper;

import com.gdmu.entity.FinalScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface FinalScoreMapper {
    // 根据学生ID和作业ID查询成绩
    @Select("select id, assignment_id, score, student_user_id as studentId from final_score where student_user_id = #{studentId} and assignment_id = #{assignmentId}")
    FinalScore findByStudentIdAndAssignmentId(Long studentId, Long assignmentId);

    // 根据学生ID查询所有成绩
    @Select("select id, assignment_id, score, student_user_id as studentId from final_score where student_user_id = #{studentId}")
    List<FinalScore> findByStudentId(Long studentId);

    // 根据作业ID查询所有成绩
    @Select("select id, assignment_id, score, student_user_id as studentId from final_score where assignment_id = #{assignmentId}")
    List<FinalScore> findByAssignmentId(Long assignmentId);

    // 新增成绩
    @Insert("insert into final_score(student_user_id, assignment_id, score) values(#{studentId}, #{assignmentId}, #{score})")
    int insert(FinalScore finalScore);

    // 更新成绩
    @Update("update final_score set score = #{score} where student_user_id = #{studentId} and assignment_id = #{assignmentId}")
    int updateByStudentIdAndAssignmentId(FinalScore finalScore);

    // 根据ID删除成绩
    @Delete("delete from final_score where id = #{id}")
    int deleteById(Long id);

    // 根据作业ID批量删除成绩
    @Delete({"<script>",
            "delete from final_score where assignment_id in",
            "<foreach collection='assignmentIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"})
    int deleteByAssignmentIds(List<Long> assignmentIds);
    
    // 查询所有成绩
    @Select("select id, assignment_id, score, student_user_id as studentId from final_score")
    List<FinalScore> findAll();
    
    // 批量删除成绩记录
    int deleteBatch(List<Long> ids);
    
    // 根据学生ID删除成绩
    @Delete("delete from final_score where student_user_id = #{studentId}")
    int deleteByStudentId(Long studentId);
}