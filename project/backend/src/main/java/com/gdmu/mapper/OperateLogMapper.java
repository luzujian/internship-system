package com.gdmu.mapper;

import com.gdmu.entity.OperateLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface OperateLogMapper {

    //插入日志数据
    @Insert("insert into operate_log (operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time) " +
            "values (#{operateAdminId}, #{operatorName}, #{operatorUsername}, #{operatorRole}, #{ipAddress}, #{operationType}, #{module}, #{description}, #{operationResult}, #{operateTime});")
    void insert(OperateLog log);

    @Select("select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time from operate_log " +
            "order by ${sortField} ${sortOrder}")
    List<OperateLog> selectAll(@Param("sortField") String sortField, @Param("sortOrder") String sortOrder);

    @Select("select id, operate_admin_id, operator_name, operator_username, operator_role, ip_address, operation_type, module, description, operation_result, operate_time from operate_log " +
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

    //获取日志总数
    @Select("select count(*) from operate_log")
    int countTotal();

    @Select("select count(*) from operate_log where operator_role = #{operatorRole}")
    int countByRole(@Param("operatorRole") String operatorRole);

    @Delete("delete from operate_log where id not in (select id from operate_log order by operate_time desc limit #{keepCount})")
    int cleanOldLogs(@Param("keepCount") int keepCount);

    @Select("select id from operate_log where operator_role = #{operatorRole} order by operate_time desc limit #{keepCount}")
    List<Long> getKeepLogIdsByRole(@Param("operatorRole") String operatorRole, @Param("keepCount") int keepCount);

    int cleanOldLogsByRole(@Param("operatorRole") String operatorRole, @Param("keepIds") List<Long> keepIds);

    @Delete("delete from operate_log where operator_role = #{operatorRole}")
    int deleteAllLogsByRole(@Param("operatorRole") String operatorRole);

    @Delete("delete from operate_log where id in (select id from operate_log order by operate_time asc limit #{limit})")
    void deleteOldLogs(@Param("limit") Integer limit);
    
    @Delete("delete from operate_log")
    void deleteAllLogs();

}