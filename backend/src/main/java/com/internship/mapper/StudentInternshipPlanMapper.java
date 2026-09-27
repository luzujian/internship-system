package com.internship.mapper;

import com.internship.entity.StudentInternshipPlan;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StudentInternshipPlanMapper {
    @Select("SELECT * FROM student_internship_plan WHERE student_id = #{studentId}")
    StudentInternshipPlan findByStudentId(@Param("studentId") Long studentId);

    @Insert("INSERT INTO student_internship_plan (student_id, current_stage, items, version, create_time, update_time) "
            + "VALUES (#{studentId}, #{currentStage}, #{items}, #{version}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StudentInternshipPlan plan);

    @Update("UPDATE student_internship_plan SET current_stage = #{currentStage}, items = #{items}, "
            + "version = version + 1, update_time = #{updateTime} "
            + "WHERE student_id = #{studentId} AND version = #{version}")
    int updateByVersion(StudentInternshipPlan plan);
}
