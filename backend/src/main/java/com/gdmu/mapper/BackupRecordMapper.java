package com.gdmu.mapper;

import com.gdmu.entity.BackupRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Date;

@Mapper
public interface BackupRecordMapper {
    
    @Insert("INSERT INTO backup_record (backup_name, backup_path, backup_size, backup_type, backup_time, status, remark, backup_format, is_compressed, checksum, parent_backup_id, table_list, table_count) " +
            "VALUES (#{backupName}, #{backupPath}, #{backupSize}, #{backupType}, #{backupTime}, #{status}, #{remark}, #{backupFormat}, #{isCompressed}, #{checksum}, #{parentBackupId}, #{tableList}, #{tableCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(BackupRecord record);
    
    @Update("UPDATE backup_record SET backup_name = #{backupName}, backup_path = #{backupPath}, backup_size = #{backupSize}, " +
            "backup_type = #{backupType}, backup_time = #{backupTime}, status = #{status}, remark = #{remark}, " +
            "backup_format = #{backupFormat}, is_compressed = #{isCompressed}, checksum = #{checksum}, parent_backup_id = #{parentBackupId}, " +
            "table_list = #{tableList}, table_count = #{tableCount} WHERE id = #{id}")
    void update(BackupRecord record);
    
    @Select("SELECT * FROM backup_record ORDER BY backup_time DESC")
    List<BackupRecord> findAll();
    
    @Select("SELECT * FROM backup_record WHERE id = #{id}")
    BackupRecord findById(Long id);
    
    @Select("SELECT * FROM backup_record WHERE backup_path = #{backupPath}")
    BackupRecord findByBackupPath(String backupPath);
    
    @Delete("DELETE FROM backup_record WHERE id = #{id}")
    void deleteById(Long id);
    
    @Select("SELECT * FROM backup_record WHERE backup_time < #{expireDate}")
    List<BackupRecord> findExpiredRecords(Date expireDate);
    
    @Delete("DELETE FROM backup_record WHERE id IN (${ids})")
    void deleteByIds(@Param("ids") String ids);
    
    @Select("SELECT * FROM backup_record WHERE parent_backup_id = #{parentBackupId}")
    List<BackupRecord> findByParentBackupId(Long parentBackupId);
}
