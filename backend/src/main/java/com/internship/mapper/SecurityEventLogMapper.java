package com.internship.mapper;

import com.internship.dto.LogQueryParams;
import com.internship.entity.SecurityEventLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 安全事件日志Mapper接口（P1 新增）
 */
@Mapper
@Repository
public interface SecurityEventLogMapper {

    String OPERATOR_FILTER = "<if test='q.operatorName != null and q.operatorName != \"\"'>" +
            " and (security_event_log.username like concat('%', #{q.operatorName}, '%')" +
            " or security_event_log.user_id like concat('%', #{q.operatorName}, '%')" +
            " or security_event_log.real_operator_name like concat('%', #{q.operatorName}, '%')" +
            " or exists (select 1 from student_users su where (su.name like concat('%', #{q.operatorName}, '%')" +
            " or su.username like concat('%', #{q.operatorName}, '%') or su.student_user_id like concat('%', #{q.operatorName}, '%'))" +
            " and (su.username = security_event_log.username or su.student_user_id = security_event_log.username" +
            " or cast(su.id as char) = security_event_log.user_id or su.student_user_id = security_event_log.user_id))" +
            " or exists (select 1 from teacher_users tu where (tu.name like concat('%', #{q.operatorName}, '%')" +
            " or tu.username like concat('%', #{q.operatorName}, '%') or tu.teacher_user_id like concat('%', #{q.operatorName}, '%'))" +
            " and (tu.username = security_event_log.username or tu.teacher_user_id = security_event_log.username" +
            " or cast(tu.id as char) = security_event_log.user_id or tu.teacher_user_id = security_event_log.user_id))" +
            " or exists (select 1 from admin_users au where (au.name like concat('%', #{q.operatorName}, '%')" +
            " or au.username like concat('%', #{q.operatorName}, '%'))" +
            " and (au.username = security_event_log.username or cast(au.id as char) = security_event_log.user_id))" +
            " or exists (select 1 from company_users cu where (cu.company_name like concat('%', #{q.operatorName}, '%')" +
            " or cu.username like concat('%', #{q.operatorName}, '%'))" +
            " and (cu.username = security_event_log.username or cast(cu.id as char) = security_event_log.user_id)))" +
            "</if>";

    @Insert("insert into security_event_log (event_time, request_id, event_type, user_id, username, user_role, real_operator_name, " +
            "risk_level, result, error_code, target_type, target_id, affected_count, http_method, request_path, ip_address, device_type, duration_ms, description, detail_json, " +
            "entry_hash, prev_hash, key_version) " +
            "values (#{eventTime}, #{requestId}, #{eventType}, #{userId}, #{username}, #{userRole}, #{realOperatorName}, " +
            "#{riskLevel}, #{result}, #{errorCode}, #{targetType}, #{targetId}, #{affectedCount}, #{httpMethod}, #{requestPath}, #{ipAddress}, #{deviceType}, #{durationMs}, #{description}, #{detailJson}, " +
            "#{entryHash}, #{prevHash}, #{keyVersion})")
    void insert(SecurityEventLog eventLog);

    /**
     * P1 日志中心组合筛选查询
     */
    @Select("<script>" +
            "select * from security_event_log " +
            "where event_time &gt;= #{q.startTime} and event_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (user_role like concat(#{q.role}, '%') or user_role like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and user_role is null))</if>" +
            "<if test='q.actionList != null and q.actionList.size() > 0'>" +
            " and event_type in <foreach collection='q.actionList' item='action' open='(' separator=',' close=')'>#{action}</foreach>" +
            "</if>" +
            "<if test='q.result != null and q.result != \"\"'> and result = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and (risk_level = #{q.riskLevel} or (#{q.riskLevel} = 'HIGH,CRITICAL' and risk_level in ('HIGH','CRITICAL')))</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.targetId != null and q.targetId != \"\"'> and target_id = #{q.targetId}</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            " order by ${q.sortField} ${q.sortOrder}, id ${q.sortOrder}" +
            "<if test='q.limit != null'> limit #{q.limit}</if>" +
            "</script>")
    List<SecurityEventLog> selectByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 组合筛选统计总数（导出上限校验用）
     */
    @Select("<script>" +
            "select count(*) from security_event_log " +
            "where event_time &gt;= #{q.startTime} and event_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (user_role like concat(#{q.role}, '%') or user_role like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and user_role is null))</if>" +
            "<if test='q.actionList != null and q.actionList.size() > 0'>" +
            " and event_type in <foreach collection='q.actionList' item='action' open='(' separator=',' close=')'>#{action}</foreach>" +
            "</if>" +
            "<if test='q.result != null and q.result != \"\"'> and result = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and (risk_level = #{q.riskLevel} or (#{q.riskLevel} = 'HIGH,CRITICAL' and risk_level in ('HIGH','CRITICAL')))</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.targetId != null and q.targetId != \"\"'> and target_id = #{q.targetId}</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            "</script>")
    long countByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 按主键查询安全事件详情
     */
    @Select("select * from security_event_log where id = #{id}")
    SecurityEventLog selectById(@Param("id") Long id);

    /**
     * P1 统计指定时间范围内高风险事件数量（HIGH/CRITICAL）
     */
    @Select("select count(*) from security_event_log where event_time >= #{startTime} and event_time <= #{endTime} and risk_level in ('HIGH','CRITICAL')")
    long countHighRiskByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /**
     * P1 获取事件类型选项（去重，供前端筛选用）
     */
    @Select("select distinct event_type from security_event_log where event_type is not null order by event_type")
    List<String> selectDistinctEventTypes();

    /**
     * P1 时间留存：分批删除指定时间之前的数据
     */
    @Delete("delete from security_event_log where event_time < #{beforeTime} and id in (select t.id from (select id from security_event_log where event_time < #{beforeTime} order by id limit #{batchLimit}) t)")
    int deleteBeforeTimeBatch(@Param("beforeTime") Date beforeTime, @Param("batchLimit") int batchLimit);

    /**
     * P1 时间留存：分批删除指定时间之前的数据（排除 HIGH/CRITICAL 高风险记录，单独按更长留存）
     */
    @Delete("<script>" +
            "delete from security_event_log where event_time &lt; #{beforeTime} " +
            "and (risk_level is null or risk_level not in ('HIGH','CRITICAL')) " +
            "and id in (select t.id from (select id from security_event_log where event_time &lt; #{beforeTime} " +
            "and (risk_level is null or risk_level not in ('HIGH','CRITICAL')) order by id limit #{batchLimit}) t)" +
            "</script>")
    int deleteBeforeTimeBatchExcludeHighRisk(@Param("beforeTime") Date beforeTime, @Param("batchLimit") int batchLimit);

    /**
     * P1 时间留存：统计指定时间之前的数据量（预览用）
     */
    @Select("select count(*) from security_event_log where event_time < #{beforeTime}")
    long countBeforeTime(@Param("beforeTime") Date beforeTime);

    @Select("select count(*) from security_event_log where event_time < #{beforeTime} and risk_level in ('HIGH','CRITICAL')")
    long countBeforeTimeHighRisk(@Param("beforeTime") Date beforeTime);

    /**
     * P1 时间留存：统计指定时间之前的非高风险数据量（预览用）
     */
    @Select("select count(*) from security_event_log where event_time < #{beforeTime} and (risk_level is null or risk_level not in ('HIGH','CRITICAL'))")
    long countBeforeTimeExcludeHighRisk(@Param("beforeTime") Date beforeTime);

    /**
     * P2 哈希链：取链尾哈希
     */
    @Select("select entry_hash from security_event_log where entry_hash is not null order by id desc limit 1")
    String selectLastChainHash();

    /**
     * P2 哈希链：取链尾记录（id + 哈希，签署每日根用）
     */
    @Select("select id, entry_hash from security_event_log where entry_hash is not null order by id desc limit 1")
    SecurityEventLog selectLastChainEntry();

    /**
     * P2 哈希链：分页取已链式记录（完整性校验用，按 id 升序）
     */
    @Select("select * from security_event_log where entry_hash is not null and id > #{afterId} order by id asc limit #{limit}")
    List<SecurityEventLog> selectChainedBatch(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * P2 哈希链：统计已链式记录数
     */
    @Select("select count(*) from security_event_log where entry_hash is not null")
    long countChained();

    @Select("select count(*) from security_event_log where entry_hash is null")
    long countUnchained();

    /**
     * P2 归档：分页取到期高风险记录（删除前归档用）
     */
    @Select("select * from security_event_log where event_time < #{beforeTime} and risk_level in ('HIGH','CRITICAL') and id > #{afterId} order by id asc limit #{limit}")
    List<SecurityEventLog> selectExpiredHighRiskBatch(@Param("beforeTime") Date beforeTime, @Param("afterId") long afterId, @Param("limit") int limit);

    @Delete("<script>delete from security_event_log where risk_level in ('HIGH','CRITICAL') and id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteArchivedHighRisk(@Param("ids") List<Long> ids);
}
