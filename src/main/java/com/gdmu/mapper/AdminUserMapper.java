package com.gdmu.mapper;

import com.gdmu.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface AdminUserMapper {
    AdminUser findByUsername(@Param("username") String username);
    int insert(AdminUser adminUser);
    AdminUser findById(Long id);
    int update(AdminUser adminUser);
    
    // 查询所有管理员用户，配合PageHelper使用
    List<AdminUser> findAll();
    
    // 根据ID列表查询管理员用户
    // 注意：该方法在XML文件中实现，因为需要使用动态SQL
    List<AdminUser> selectByIds(List<Long> ids);
    
    // 查询总记录数
    @Select("select count(1) from admin_users")
    Long count();
    
    // 分页查询管理员用户
    @Select("select * from admin_users limit #{offset}, #{pageSize}")
    List<AdminUser> findPage(Integer offset, Integer pageSize);
    
    // 删除管理员用户
    @Delete("delete from admin_users where id = #{id}")
    int deleteById(Long id);
}