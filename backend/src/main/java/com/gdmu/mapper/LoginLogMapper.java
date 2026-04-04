package com.gdmu.mapper;

import com.gdmu.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 登录日志Mapper接口
 */
@Mapper
public interface LoginLogMapper {
    
    /**
     * 插入登录日志
     * @param loginLog 登录日志对象
     * @return 影响行数
     */
    @Insert("INSERT INTO login_log(user_id, user_type, user_name, login_time, ip_address, device_info, login_status) " +
            "VALUES(#{userId}, #{userType}, #{userName}, #{loginTime}, #{ipAddress}, #{deviceInfo}, #{loginStatus})")
    int insert(LoginLog loginLog);
    
    /**
     * 查询所有登录日志
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log ORDER BY login_time DESC")
    List<LoginLog> selectAll();
    
    /**
     * 根据日期范围查询登录日志
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log WHERE login_time >= #{startDate} AND login_time <= #{endDate} ORDER BY login_time DESC")
    List<LoginLog> selectByDateRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    
    /**
     * 根据用户ID查询登录日志
     * @param userId 用户ID
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log WHERE user_id = #{userId} ORDER BY login_time DESC")
    List<LoginLog> selectByUserId(@Param("userId") String userId);
    
    /**
     * 根据用户类型查询登录日志
     * @param userType 用户类型
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log WHERE user_type = #{userType} ORDER BY login_time DESC")
    List<LoginLog> selectByUserType(@Param("userType") String userType);
    
    /**
     * 根据用户类型和日期范围查询登录日志
     * @param userType 用户类型
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log WHERE user_type = #{userType} AND login_time >= #{startDate} AND login_time <= #{endDate} ORDER BY login_time DESC")
    List<LoginLog> selectByUserTypeAndDateRange(@Param("userType") String userType, @Param("startDate") Date startDate, @Param("endDate") Date endDate);
    
    /**
     * 查询登录日志总数
     * @return 登录日志总数
     */
    @Select("SELECT COUNT(*) FROM login_log")
    int countAll();
    
    /**
     * 分页查询登录日志
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log ORDER BY login_time DESC LIMIT #{offset}, #{limit}")
    List<LoginLog> selectPage(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 根据登录状态查询登录日志
     * @param loginStatus 登录状态
     * @return 登录日志列表
     */
    @Select("SELECT * FROM login_log WHERE login_status = #{loginStatus} ORDER BY login_time DESC")
    List<LoginLog> selectByLoginStatus(@Param("loginStatus") String loginStatus);
}