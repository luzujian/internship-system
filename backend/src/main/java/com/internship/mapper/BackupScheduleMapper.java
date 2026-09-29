package com.internship.mapper;

import com.internship.entity.BackupSchedule;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BackupScheduleMapper {
    
    @Select("SELECT * FROM backup_schedule WHERE id = 1")
    BackupSchedule getSchedule();
    
    @Insert("INSERT INTO backup_schedule (enabled, frequency, backup_time, retention_days, remark, last_execution_date) " +
            "VALUES (#{enabled}, #{frequency}, #{backupTime}, #{retentionDays}, #{remark}, #{lastExecutionDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertSchedule(BackupSchedule schedule);

    @Update("UPDATE backup_schedule SET enabled = #{enabled}, frequency = #{frequency}, " +
            "backup_time = #{backupTime}, retention_days = #{retentionDays}, remark = #{remark}, " +
            "last_execution_date = #{lastExecutionDate} WHERE id = 1")
    void updateSchedule(BackupSchedule schedule);

    /**
     * 原子占位：只有今天还没执行过时才标记为今天，返回影响行数。
     * 用于防止 5 分钟轮询窗口内多次 tick 重复备份，多实例下也有效。
     */
    @Update("UPDATE backup_schedule SET last_execution_date = CURDATE() " +
            "WHERE id = 1 AND (last_execution_date IS NULL OR last_execution_date <> CURDATE())")
    int claimTodayExecution();

    /**
     * 备份失败时回滚占位，允许窗口内下一次 tick 重试。
     */
    @Update("UPDATE backup_schedule SET last_execution_date = NULL WHERE id = 1")
    void clearTodayExecution();
}