package com.internship.mapper;

import com.internship.entity.TeacherAutoTaskLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherAutoTaskLogMapper {
    @Select("SELECT * FROM teacher_auto_task_log WHERE task_id = #{taskId} ORDER BY id DESC LIMIT #{limit}")
    List<TeacherAutoTaskLog> findByTaskId(@Param("taskId") Long taskId, @Param("limit") int limit);

    @Insert("INSERT INTO teacher_auto_task_log (task_id, executed_at, channel, target_count, "
            + "success_count, detail, create_time) "
            + "VALUES (#{taskId}, #{executedAt}, #{channel}, #{targetCount}, #{successCount}, #{detail}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeacherAutoTaskLog log);

    @Delete("DELETE FROM teacher_auto_task_log WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
