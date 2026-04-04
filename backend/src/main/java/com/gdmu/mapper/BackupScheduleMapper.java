package com.gdmu.mapper;

import com.gdmu.entity.BackupSchedule;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BackupScheduleMapper {
    
    @Select("SELECT * FROM backup_schedule WHERE id = 1")
    BackupSchedule getSchedule();
    
    @Update("UPDATE backup_schedule SET enabled = #{enabled}, frequency = #{frequency}, " +
            "backup_time = #{backupTime}, retention_days = #{retentionDays}, remark = #{remark} WHERE id = 1")
    void updateSchedule(BackupSchedule schedule);
}