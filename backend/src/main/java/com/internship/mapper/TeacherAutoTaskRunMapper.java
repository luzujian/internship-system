package com.internship.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface TeacherAutoTaskRunMapper {
    record Row(String id, Long taskId, String occurrenceKey, long taskVersion, String status,
               long startedAt, long updatedAt, String receipt, String errorMessage) {}

    @Select("SELECT id FROM teacher_auto_task WHERE id=#{id} FOR UPDATE")
    Long lockTask(Long id);

    @Select("SELECT id,task_id,occurrence_key,task_version,status,started_at,updated_at,receipt,error_message FROM teacher_auto_task_run WHERE task_id=#{taskId} AND occurrence_key=#{key}")
    Row find(@Param("taskId") Long taskId, @Param("key") String key);

    @Select("SELECT id,task_id,occurrence_key,task_version,status,started_at,updated_at,receipt,error_message FROM teacher_auto_task_run WHERE task_id=#{taskId} AND status IN ('RUNNING','UNKNOWN') ORDER BY started_at DESC LIMIT 1")
    Row unsettled(Long taskId);

    @Select("SELECT id,task_id,occurrence_key,task_version,status,started_at,updated_at,receipt,error_message FROM teacher_auto_task_run WHERE id=#{id}")
    Row findById(String id);

    @Update("UPDATE teacher_auto_task SET status=CASE WHEN status='ENDED' THEN 'ENDED' ELSE 'PAUSED' END,enabled=0,blocked_reason=#{reason},version=version+1,update_time=NOW() WHERE id=#{id} AND (enabled<>0 OR status='ACTIVE' OR NOT(blocked_reason <=> #{reason}))")
    int pauseUncertain(@Param("id") Long id, @Param("reason") String reason);

    @Update("UPDATE teacher_auto_task_run SET updated_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int heartbeat(@Param("id") String id, @Param("now") long now);

    @Insert("INSERT INTO teacher_auto_task_run(id,task_id,occurrence_key,task_version,status,started_at,updated_at) VALUES(#{id},#{taskId},#{occurrenceKey},#{taskVersion},'RUNNING',#{startedAt},#{updatedAt})")
    int insert(Row row);

    @Update("UPDATE teacher_auto_task_run SET status=#{status},receipt=#{receipt},error_message=#{error},updated_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int finish(@Param("id") String id, @Param("status") String status, @Param("receipt") String receipt,
               @Param("error") String error, @Param("now") long now);
}
