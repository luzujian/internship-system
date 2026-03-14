package com.gdmu.mapper;

import com.gdmu.entity.Contribution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface ContributionMapper {
    // 根据提交ID查询所有贡献度
    @Select("select * from contribution where submission_id = #{submissionId}")
    List<Contribution> findBySubmissionId(Long submissionId);

    // 根据提交ID和学生ID查询贡献度
    @Select("select * from contribution where submission_id = #{submissionId} and student_id = #{studentId}")
    Contribution findBySubmissionIdAndStudentId(Long submissionId, Long studentId);

    // 新增贡献度
    @Insert("insert into contribution(submission_id, student_id, score) values(#{submissionId}, #{studentId}, #{score})")
    int insert(Contribution contribution);

    // 更新贡献度
    @Update("update contribution set score = #{score} where submission_id = #{submissionId} and student_id = #{studentId}")
    int update(Contribution contribution);

    // 根据提交ID删除所有贡献度
    @Delete("delete from contribution where submission_id = #{submissionId}")
    int deleteBySubmissionId(Long submissionId);
}