package com.internship.mapper;

import com.internship.dto.LogQueryParams;
import com.internship.entity.LoginLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 登录日志Mapper接口
 */
@Mapper
@Repository
public interface LoginLogMapper {

    String OPERATOR_FILTER = "<if test='q.operatorName != null and q.operatorName != \"\"'>" +
            " and (login_log.user_name like concat('%', #{q.operatorName}, '%')" +
            " or login_log.user_id like concat('%', #{q.operatorName}, '%')" +
            " or exists (select 1 from student_users su where (su.name like concat('%', #{q.operatorName}, '%')" +
            " or su.username like concat('%', #{q.operatorName}, '%') or su.student_user_id like concat('%', #{q.operatorName}, '%'))" +
            " and (su.username = login_log.user_name or su.student_user_id = login_log.user_name" +
            " or cast(su.id as char) = login_log.user_id or su.student_user_id = login_log.user_id))" +
            " or exists (select 1 from teacher_users tu where (tu.name like concat('%', #{q.operatorName}, '%')" +
            " or tu.username like concat('%', #{q.operatorName}, '%') or tu.teacher_user_id like concat('%', #{q.operatorName}, '%'))" +
            " and (tu.username = login_log.user_name or tu.teacher_user_id = login_log.user_name" +
            " or cast(tu.id as char) = login_log.user_id or tu.teacher_user_id = login_log.user_id))" +
            " or exists (select 1 from admin_users au where (au.name like concat('%', #{q.operatorName}, '%')" +
            " or au.username like concat('%', #{q.operatorName}, '%'))" +
            " and (au.username = login_log.user_name or cast(au.id as char) = login_log.user_id))" +
            " or exists (select 1 from company_users cu where (cu.company_name like concat('%', #{q.operatorName}, '%')" +
            " or cu.username like concat('%', #{q.operatorName}, '%'))" +
            " and (cu.username = login_log.user_name or cast(cu.id as char) = login_log.user_id)))" +
            "</if>";

    /**
     * 插入登录日志（含 P1 审计扩展字段 + P2 哈希链字段）
     */
    @Insert("INSERT INTO login_log(user_id, user_type, user_name, login_time, ip_address, device_info, login_status, " +
            "request_id, risk_level, error_code, device_type, duration_ms, detail_json, entry_hash, prev_hash, key_version) " +
            "VALUES(#{userId}, #{userType}, #{userName}, #{loginTime}, #{ipAddress}, #{deviceInfo}, #{loginStatus}, " +
            "#{requestId}, #{riskLevel}, #{errorCode}, #{deviceType}, #{durationMs}, #{detailJson}, #{entryHash}, #{prevHash}, #{keyVersion})")
    int insert(LoginLog loginLog);

    /**
     * 查询所有登录日志
     */
    @Select("SELECT * FROM login_log ORDER BY login_time DESC")
    List<LoginLog> selectAll();

    /**
     * 根据日期范围查询登录日志
     */
    @Select("SELECT * FROM login_log WHERE login_time >= #{startDate} AND login_time <= #{endDate} ORDER BY login_time DESC")
    List<LoginLog> selectByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

    /**
     * 根据用户ID查询登录日志
     */
    @Select("SELECT * FROM login_log WHERE user_id = #{userId} ORDER BY login_time DESC")
    List<LoginLog> selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户类型查询登录日志
     */
    @Select("SELECT * FROM login_log WHERE user_type = #{userType} ORDER BY login_time DESC")
    List<LoginLog> selectByUserType(@Param("userType") String userType);

    /**
     * P1 日志中心组合筛选查询
     */
    @Select("<script>" +
            "select * from login_log " +
            "where login_time &gt;= #{q.startTime} and login_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (user_type like concat(#{q.role}, '%') or user_type like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and user_type is null))</if>" +
            "<if test='q.result != null and q.result != \"\"'> and login_status = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and risk_level = #{q.riskLevel}</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            " order by ${q.sortField} ${q.sortOrder}" +
            "</script>")
    List<LoginLog> selectByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 组合筛选统计总数（导出上限校验用）
     */
    @Select("<script>" +
            "select count(*) from login_log " +
            "where login_time &gt;= #{q.startTime} and login_time &lt;= #{q.endTime} " +
            OPERATOR_FILTER +
            "<if test='q.role != null and q.role != \"\"'> and (user_type like concat(#{q.role}, '%') or user_type like concat('ROLE_', #{q.role}, '%') or ('UNKNOWN' = #{q.role} and user_type is null))</if>" +
            "<if test='q.result != null and q.result != \"\"'> and login_status = #{q.result}</if>" +
            "<if test='q.riskLevel != null and q.riskLevel != \"\"'> and risk_level = #{q.riskLevel}</if>" +
            "<if test='q.ip != null and q.ip != \"\"'> and ip_address like concat('%', #{q.ip}, '%')</if>" +
            "<if test='q.requestId != null and q.requestId != \"\"'> and request_id = #{q.requestId}</if>" +
            "</script>")
    long countByFilter(@Param("q") LogQueryParams q);

    /**
     * P1 按主键查询登录日志详情
     */
    @Select("select * from login_log where id = #{id}")
    LoginLog selectById(@Param("id") Long id);

    /**
     * P1 统计指定时间范围内登录数量（status 为空时统计全部）
     */
    @Select("<script>" +
            "select count(*) from login_log where login_time &gt;= #{startTime} and login_time &lt;= #{endTime} " +
            "<if test='status != null and status != \"\"'> and login_status = #{status}</if>" +
            "</script>")
    long countByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("status") String status);

    /**
     * 按角色统计去重登录用户数（全量，status 为空时统计全部状态）
     */
    @Select("<script>" +
            "select count(distinct user_id) from login_log " +
            "<where>" +
            "<if test='role != null and role != \"\"'> (user_type like concat(#{role}, '%') or user_type like concat('ROLE_', #{role}, '%'))</if>" +
            "<if test='status != null and status != \"\"'> and login_status = #{status}</if>" +
            "</where>" +
            "</script>")
    long countDistinctUsers(@Param("role") String role, @Param("status") String status);

    /**
     * 按角色统计指定时间范围内去重登录用户数（status 为空时统计全部状态）
     */
    @Select("<script>" +
            "select count(distinct user_id) from login_log where login_time &gt;= #{startTime} and login_time &lt;= #{endTime} " +
            "<if test='role != null and role != \"\"'> and (user_type like concat(#{role}, '%') or user_type like concat('ROLE_', #{role}, '%'))</if>" +
            "<if test='status != null and status != \"\"'> and login_status = #{status}</if>" +
            "</script>")
    long countDistinctUsersByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("role") String role, @Param("status") String status);

    /**
     * 只统计现存学生的去重登录数（全量）：以 student_users 为准，
     * 统计有过成功登录记录的现存学生数，已删除/测试孤儿账号不再计入。
     */
    @Select("<script>" +
            "select count(distinct su.id) from student_users su where exists (" +
            "select 1 from login_log l where " +
            "(cast(su.id as char) = l.user_id or l.user_name = su.username or l.user_name = su.student_user_id " +
            "or l.user_id = su.username or l.user_id = su.student_user_id) " +
            "and (l.user_type like 'STUDENT%' or l.user_type like 'ROLE_STUDENT%')" +
            "<if test='status != null and status != \"\"'> and l.login_status = #{status}</if>" +
            ")" +
            "</script>")
    long countExistingStudentUsers(@Param("status") String status);

    /**
     * 只统计现存学生的指定时间范围内去重登录数
     */
    @Select("<script>" +
            "select count(distinct su.id) from student_users su where exists (" +
            "select 1 from login_log l where " +
            "(cast(su.id as char) = l.user_id or l.user_name = su.username or l.user_name = su.student_user_id " +
            "or l.user_id = su.username or l.user_id = su.student_user_id) " +
            "and l.login_time &gt;= #{startTime} and l.login_time &lt;= #{endTime} " +
            "and (l.user_type like 'STUDENT%' or l.user_type like 'ROLE_STUDENT%')" +
            "<if test='status != null and status != \"\"'> and l.login_status = #{status}</if>" +
            ")" +
            "</script>")
    long countExistingStudentUsersByTimeRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("status") String status);

    /**
     * 查询登录日志总数
     */
    @Select("SELECT COUNT(*) FROM login_log")
    int countAll();

    /**
     * 分页查询登录日志
     */
    @Select("SELECT * FROM login_log ORDER BY login_time DESC LIMIT #{offset}, #{limit}")
    List<LoginLog> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 根据登录状态查询登录日志
     */
    @Select("SELECT * FROM login_log WHERE login_status = #{loginStatus} ORDER BY login_time DESC")
    List<LoginLog> selectByLoginStatus(@Param("loginStatus") String loginStatus);

    /**
     * P1 时间留存：分批删除指定时间之前的数据
     */
    @Delete("delete from login_log where login_time < #{beforeTime} and id in (select t.id from (select id from login_log where login_time < #{beforeTime} order by id limit #{batchLimit}) t)")
    int deleteBeforeTimeBatch(@Param("beforeTime") Date beforeTime, @Param("batchLimit") int batchLimit);

    /**
     * P1 时间留存：统计指定时间之前的数据量（预览用）
     */
    @Select("select count(*) from login_log where login_time < #{beforeTime}")
    long countBeforeTime(@Param("beforeTime") Date beforeTime);

    /**
     * P2 哈希链：取链尾哈希
     */
    @Select("select entry_hash from login_log where entry_hash is not null order by id desc limit 1")
    String selectLastChainHash();

    /**
     * P2 哈希链：取链尾记录（id + 哈希，签署每日根用）
     */
    @Select("select id, entry_hash from login_log where entry_hash is not null order by id desc limit 1")
    LoginLog selectLastChainEntry();

    /**
     * P2 哈希链：分页取已链式记录（完整性校验用，按 id 升序）
     */
    @Select("select * from login_log where entry_hash is not null and id > #{afterId} order by id asc limit #{limit}")
    List<LoginLog> selectChainedBatch(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * P2 哈希链：统计已链式记录数
     */
    @Select("select count(*) from login_log where entry_hash is not null")
    long countChained();
}
