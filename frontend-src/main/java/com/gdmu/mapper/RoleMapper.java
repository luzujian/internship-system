package com.gdmu.mapper;

import com.gdmu.entity.Role;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoleMapper {
    
    @Select("SELECT * FROM roles ORDER BY create_time DESC")
    List<Role> findAll();
    
    @Insert("INSERT INTO roles (role_code, role_name, role_desc, status, create_time, update_time) VALUES (#{roleCode}, #{roleName}, #{roleDesc}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Role role);
    
    @Update("UPDATE roles SET role_code = #{roleCode}, role_name = #{roleName}, role_desc = #{roleDesc}, status = #{status}, update_time = NOW() WHERE id = #{id}")
    void update(Role role);
    
    @Delete("DELETE FROM roles WHERE id = #{id}")
    void deleteById(Long id);
    
    @Select("SELECT * FROM roles WHERE id = #{id}")
    Role findById(Long id);
}
