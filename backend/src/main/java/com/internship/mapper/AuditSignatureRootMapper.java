package com.internship.mapper;

import com.internship.entity.AuditSignatureRoot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审计日志每日签名根 Mapper（P2 完整性增强）
 */
@Mapper
@Repository
public interface AuditSignatureRootMapper {

    /** 幂等写入：同一 (root_date, table_name) 重复签署时更新 */
    @Insert("INSERT INTO audit_signature_root (root_date, table_name, last_entry_id, last_entry_hash, entry_count, signature, key_version, created_at) " +
            "VALUES (#{rootDate}, #{tableName}, #{lastEntryId}, #{lastEntryHash}, #{entryCount}, #{signature}, #{keyVersion}, NOW()) " +
            "ON DUPLICATE KEY UPDATE last_entry_id = VALUES(last_entry_id), last_entry_hash = VALUES(last_entry_hash), " +
            "entry_count = VALUES(entry_count), signature = VALUES(signature), key_version = VALUES(key_version), created_at = NOW()")
    int upsert(AuditSignatureRoot root);

    /** 查询指定表全部签名根（按日期升序，校验锚点用） */
    @Select("select * from audit_signature_root where table_name = #{tableName} order by root_date asc")
    List<AuditSignatureRoot> selectByTable(@Param("tableName") String tableName);

    /** 查询指定表最新一个签名根 */
    @Select("select * from audit_signature_root where table_name = #{tableName} order by root_date desc limit 1")
    AuditSignatureRoot selectLatest(@Param("tableName") String tableName);

    /** 统计签名根数量 */
    @Select("select count(*) from audit_signature_root where table_name = #{tableName}")
    long countByTable(@Param("tableName") String tableName);
}
