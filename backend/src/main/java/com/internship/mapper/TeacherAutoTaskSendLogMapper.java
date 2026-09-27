package com.internship.mapper;

import com.internship.entity.TeacherAutoTaskSendLog;
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
public interface TeacherAutoTaskSendLogMapper {
    @Select("SELECT * FROM teacher_auto_task_send_log WHERE task_id = #{taskId} ORDER BY id DESC LIMIT #{limit}")
    List<TeacherAutoTaskSendLog> findByTaskId(@Param("taskId") Long taskId, @Param("limit") int limit);

    // Legacy sends have no stable todo identity: conservatively share their cooldown across teachers.
    @Select("SELECT MAX(sent_at) FROM teacher_auto_task_send_log "
            + "WHERE student_id = #{studentId} AND channel = #{channel} AND status = 'SENT' AND todo_id IS NULL")
    Date findLastSuccess(@Param("taskId") Long taskId, @Param("studentId") Long studentId,
                         @Param("channel") String channel);

    @Select("SELECT * FROM teacher_auto_task_send_log "
            + "WHERE student_id = #{studentId} AND channel = #{channel} "
            + "AND todo_id IS NULL AND status IN ('PENDING','UNKNOWN') ORDER BY id DESC LIMIT 1")
    TeacherAutoTaskSendLog findPending(@Param("taskId") Long taskId, @Param("studentId") Long studentId,
                                       @Param("channel") String channel);

    @Update("UPDATE teacher_auto_task_send_log SET status = #{status}, detail = #{detail}, sent_at = #{sentAt} "
            + "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("detail") String detail, @Param("sentAt") Date sentAt);

    @Insert("INSERT INTO teacher_auto_task_send_log (task_id, student_id, student_name, channel, "
            + "sent_at, status, detail, create_time, todo_id, run_id) "
            + "VALUES (#{taskId}, #{studentId}, #{studentName}, #{channel}, #{sentAt}, #{status}, #{detail}, #{createTime}, #{todoId}, #{runId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeacherAutoTaskSendLog record);

    @Delete("DELETE FROM teacher_auto_task_send_log WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
