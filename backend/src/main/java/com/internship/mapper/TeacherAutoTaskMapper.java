package com.internship.mapper;

import com.internship.entity.TeacherAutoTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface TeacherAutoTaskMapper {
    @Select("SELECT * FROM teacher_auto_task WHERE id = #{id} AND engine_version=1 AND deleted_at IS NULL")
    TeacherAutoTask findById(@Param("id") Long id);

    @Select("SELECT * FROM teacher_auto_task WHERE id = #{id} AND engine_version=1 AND deleted_at IS NULL FOR UPDATE")
    TeacherAutoTask lockById(@Param("id") Long id);

    @Select("SELECT * FROM teacher_auto_task WHERE teacher_id = #{teacherId} AND deleted_at IS NULL ORDER BY id DESC")
    List<TeacherAutoTask> findByTeacherId(@Param("teacherId") Long teacherId);

    @Select("SELECT * FROM teacher_auto_task WHERE engine_version=1 AND status = 'ACTIVE' AND deleted_at IS NULL AND (end_at IS NULL OR end_at > NOW()) ORDER BY id")
    List<TeacherAutoTask> findActive();

    @Select("SELECT * FROM teacher_auto_task WHERE engine_version=1 AND status = 'ACTIVE' AND deleted_at IS NULL ORDER BY id")
    List<TeacherAutoTask> findActiveIncludingExpired();

    @Insert("INSERT INTO teacher_auto_task (teacher_id, teacher_type, name, trigger_condition, "
            + "trigger_days, frequency_minutes, end_at, schedule_config, status, blocked_reason, target_scope, action, "
            + "message_template, enabled, create_time, update_time) "
            + "VALUES (#{teacherId}, #{teacherType}, #{name}, #{triggerCondition}, "
            + "#{triggerDays}, #{frequencyMinutes}, #{endAt}, #{scheduleConfig}, #{status}, #{blockedReason}, #{targetScope}, "
            + "#{action}, #{messageTemplate}, #{enabled}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeacherAutoTask task);

    @Update("UPDATE teacher_auto_task SET name = #{name}, trigger_condition = #{triggerCondition}, "
            + "trigger_days = #{triggerDays}, frequency_minutes = #{frequencyMinutes}, end_at = #{endAt}, schedule_config = #{scheduleConfig}, "
            + "action = #{action}, message_template = #{messageTemplate}, target_scope = #{targetScope}, update_time = #{updateTime}, version=version+1 "
            + "WHERE id = #{id} AND teacher_id = #{teacherId} AND version=#{version}")
    int updateByIdAndTeacher(TeacherAutoTask task);

    @Update("UPDATE teacher_auto_task SET status = #{status}, enabled = #{enabled}, "
            + "blocked_reason = #{blockedReason}, update_time = #{updateTime}, version=version+1 "
            + "WHERE id = #{id} AND teacher_id = #{teacherId}")
    int updateStatus(@Param("id") Long id, @Param("teacherId") Long teacherId,
                     @Param("status") String status, @Param("enabled") Integer enabled,
                     @Param("blockedReason") String blockedReason, @Param("updateTime") Date updateTime);

    @Update("UPDATE teacher_auto_task SET blocked_reason = #{blockedReason}, update_time = #{updateTime} WHERE id = #{id}")
    int updateBlockedReason(@Param("id") Long id, @Param("blockedReason") String blockedReason,
                            @Param("updateTime") Date updateTime);

    @Update("UPDATE teacher_auto_task SET last_run_at = #{lastRunAt} WHERE id = #{id}")
    int updateLastRun(@Param("id") Long id, @Param("lastRunAt") Date lastRunAt);

    @Update("UPDATE teacher_auto_task SET status='ENDED',enabled=0,deleted_at=NOW(3),version=version+1 WHERE id = #{id} AND teacher_id = #{teacherId}")
    int deleteByIdAndTeacher(@Param("id") Long id, @Param("teacherId") Long teacherId);
}
