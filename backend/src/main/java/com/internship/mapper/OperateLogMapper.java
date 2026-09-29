package com.internship.mapper;

import com.internship.dto.LogQueryParams;
import com.internship.entity.OperateLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Mapper
@Repository
public interface OperateLogMapper {

    String OPERATOR_FILTER = "<if test='q.operatorName != null and q.operatorName != \"\"'>" +
            " and (operator_name like concat('%', #{q.operatorName}, '%')" +
            " or operator_username like concat('%', #{q.operatorName}, '%')" +
            " or real_operator_name like concat('%', #{q.operatorName}, '%'))" +
            "</if>";

    //插入日志数据（含 P1 审计扩展字段 + P2 哈希链字段）
    @Insert("insert into operate_log (operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time, " +
            "request_id, risk_level, error_code, target_type, target_id, affected_count, real_operator_name, http_method, request_path, device_type, duration_ms, detail_json, " +
            "entry_hash, prev_hash, key_version) " +
            "values (#{operateAdminId}, #{operatorName}, #{operatorUsername}, #{operatorRole}, #{ipAddress}, #{operationType}, #{module}, #{description}, #{operationResult}, #{operateTime}, " +
            "#{requestId}, #{riskLevel}, #{errorCode}, #{targetType}, #{targetId}, #{affectedCount}, #{realOperatorName}, #{httpMethod}, #{requestPath}, #{deviceType}, #{durationMs}, #{detailJson}, " +
            "#{entryHash}, #{prevHash}, #{keyVersion});")
    void insert(OperateLog log);

    @Select("select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time, " +
            "request_id, risk_level, error_code, target_type, target_id, affected_count, real_operator_name, http_method, request_path, device_type, duration_ms, detail_json " +
            "from operate_log order by ${sortField} ${sortOrder}")
    List<OperateLog> selectAll(@Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    @Select("select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time, " +
            "request_id, risk_level, error_code, target_type, target_id, affected_count, real_operator_name, http_method, request_path, device_type, duration_ms, detail_json " +
            "from operate_log " +
            "where (#{operatorName} is null or operator_name like concat('%', #{operatorName}, '%')) " +
            "and (#{operatorRole} is null or operator_role = #{operatorRole}) " +
            "and (#{operationType} is null or operation_type = #{operationType}) " +
            "and (#{module} is null or module = #{module}) " +
            "order by ${sortField} ${sortOrder}")
    List<OperateLog> selectByConditions(@Param("operatorName") String operatorName,
                                        @Param("operatorRole") String operatorRole,
                                        @Param("operationType") String operationType,
                                        @Param("module") String module,
                                        @Param("sortField") String sortField,
                                        @Param("sortOrder") String sortOrder);

    /**
     * P1 日志中心组合筛选查询（时间范围/操作人/角色/模块/动作/结果/风险/IP/目标ID/requestId）
     * 排序字段由 Controller 白名单校验后传入
     */
    @Select("<script>" +
            "select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time, " +
            "request_id, risk_level, error_code, target_type, target_id, affected_count, real_operator_name, http_method, request_path, device_type, duration_ms, detail_json " +
            "from operate_log " +
            "where operate_time &gt;= #{q.startTime} and operate_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (operator_role like concat(#{q.role}, '%') or operator_role like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and operator_role is null))</if>" +
            "<if test='q.module != null and q.module != \"\"'> and module = #{q.module}</if>" +
            "<if test='q.actionList != null and q.actionList.size() > 0'>" +
            " and operation_type in <foreach collection='q.actionList' item='action' open='(' separator=',' close=')'>#{action}</foreach>" +
            "</if>" +
            "<if test='q.result != null and q.result != \"\"'> and operation_result = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and risk_level = #{q.riskLevel}</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.targetId != null and q.targetId != \"\"'> and target_id = #{q.targetId}</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            " order by ${q.sortField} ${q.sortOrder}" +
            "</script>")
    List<OperateLog> selectByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 统计指定时间范围内的操作数量（result 为空时统计全部）
     */
    @Select("<script>" +
            "select count(*) from operate_log where operate_time &gt;= #{startTime} and operate_time &lt;= #{endTime} " +
            "<if test='result != null and result != \"\"'> and operation_result = #{result}</if>" +
            "</script>")
    long countByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("result") String result);

    /**
     * P1 组合筛选统计总数（导出上限校验用）
     */
    @Select("<script>" +
            "select count(*) from operate_log " +
            "where operate_time &gt;= #{q.startTime} and operate_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (operator_role like concat(#{q.role}, '%') or operator_role like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and operator_role is null))</if>" +
            "<if test='q.module != null and q.module != \"\"'> and module = #{q.module}</if>" +
            "<if test='q.actionList != null and q.actionList.size() > 0'>" +
            " and operation_type in <foreach collection='q.actionList' item='action' open='(' separator=',' close=')'>#{action}</foreach>" +
            "</if>" +
            "<if test='q.result != null and q.result != \"\"'> and operation_result = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and risk_level = #{q.riskLevel}</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.targetId != null and q.targetId != \"\"'> and target_id = #{q.targetId}</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            "</script>")
    long countByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 按主键查询日志详情
     */
    @Select("select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time, " +
            "request_id, risk_level, error_code, target_type, target_id, affected_count, real_operator_name, http_method, request_path, device_type, duration_ms, detail_json, " +
            "entry_hash, prev_hash, key_version " +
            "from operate_log where id = #{id}")
    OperateLog selectById(@Param("id") Long id);

    /**
     * P2 哈希链：取链尾哈希（最后一条已链式记录）
     */
    @Select("select entry_hash from operate_log where entry_hash is not null order by id desc limit 1")
    String selectLastChainHash();

    /**
     * P2 哈希链：取链尾记录（id + 哈希，签署每日根用）
     */
    @Select("select id, entry_hash from operate_log where entry_hash is not null order by id desc limit 1")
    OperateLog selectLastChainEntry();

    /**
     * P2 哈希链：分页取已链式记录（完整性校验用，按 id 升序）
     */
    @Select("select * from operate_log where entry_hash is not null and id > #{afterId} order by id asc limit #{limit}")
    List<OperateLog> selectChainedBatch(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * P2 哈希链：统计已链式记录数
     */
    @Select("select count(*) from operate_log where entry_hash is not null")
    long countChained();

    /**
     * P2 归档：分页取到期高风险记录（删除前归档用）
     */
    @Select("select * from operate_log where operate_time < #{beforeTime} and risk_level in ('HIGH','CRITICAL') and id > #{afterId} order by id asc limit #{limit}")
    List<OperateLog> selectExpiredHighRiskBatch(@Param("beforeTime") Date beforeTime, @Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * P1 获取操作类型选项（去重，供前端筛选用）
     */
    @Select("select distinct operation_type from operate_log where operation_type is not null order by operation_type")
    List<String> selectDistinctOperationTypes();

    /**
     * P1 获取模块选项（去重，供前端筛选用）
     */
    @Select("select distinct module from operate_log where module is not null order by module")
    List<String> selectDistinctModules();

    //获取日志总数
    @Select("select count(*) from operate_log")
    int countTotal();

    @Select("select count(*) from operate_log where operator_role = #{operatorRole}")
    int countByRole(@Param("operatorRole") String operatorRole);

    @Delete("delete from operate_log where id not in (select t.id from (select id from operate_log order by operate_time desc limit #{keepCount}) t)")
    int cleanOldLogs(@Param("keepCount") int keepCount);

    @Select("select id from operate_log where operator_role = #{operatorRole} order by operate_time desc limit #{keepCount}")
    List<Long> getKeepLogIdsByRole(@Param("operatorRole") String operatorRole, @Param("keepCount") int keepCount);

    int cleanOldLogsByRole(@Param("operatorRole") String operatorRole, @Param("keepIds") List<Long> keepIds);

    @Delete("delete from operate_log where operator_role = #{operatorRole}")
    int deleteAllLogsByRole(@Param("operatorRole") String operatorRole);

    @Delete("delete from operate_log where id in (select t.id from (select id from operate_log order by operate_time asc limit #{limit}) t)")
    void deleteOldLogs(@Param("limit") Integer limit);

    @Delete("delete from operate_log")
    void deleteAllLogs();

    /**
     * P1 时间留存：分批删除指定时间之前的数据（返回本批删除行数；batchLimit 控制 单批大小）
     */
    @Delete("delete from operate_log where operate_time < #{beforeTime} and id in (select t.id from (select id from operate_log where operate_time < #{beforeTime} order by id limit #{batchLimit}) t)")
    int deleteBeforeTimeBatch(@Param("beforeTime") Date beforeTime, @Param("batchLimit") int batchLimit);

    /**
     * P1 时间留存：分批删除指定时间之前的数据（排除 HIGH/CRITICAL 高风险记录，单独按更长留存）
     */
    @Delete("<script>" +
            "delete from operate_log where operate_time &lt; #{beforeTime} " +
            "and (risk_level is null or risk_level not in ('HIGH','CRITICAL')) " +
            "and id in (select t.id from (select id from operate_log where operate_time &lt; #{beforeTime} " +
            "and (risk_level is null or risk_level not in ('HIGH','CRITICAL')) order by id limit #{batchLimit}) t)" +
            "</script>")
    int deleteBeforeTimeBatchExcludeHighRisk(@Param("beforeTime") Date beforeTime, @Param("batchLimit") int batchLimit);

    /**
     * P1 时间留存：统计指定时间之前的数据量（预览用）
     */
    @Select("select count(*) from operate_log where operate_time < #{beforeTime}")
    long countBeforeTime(@Param("beforeTime") Date beforeTime);

    /**
     * P1 时间留存：统计指定时间之前的非高风险数据量（预览用）
     */
    @Select("select count(*) from operate_log where operate_time < #{beforeTime} and (risk_level is null or risk_level not in ('HIGH','CRITICAL'))")
    long countBeforeTimeExcludeHighRisk(@Param("beforeTime") Date beforeTime);
}
