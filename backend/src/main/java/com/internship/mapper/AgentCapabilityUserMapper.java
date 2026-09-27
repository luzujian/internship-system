package com.internship.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** Only public account labels are returned. */
@Mapper
public interface AgentCapabilityUserMapper {
    @Select("""
        <script>
        SELECT id,name,account FROM (
        <choose>
          <when test="role == 'student'">SELECT id,name,student_user_id AS account FROM student_users</when>
          <when test="role == 'teacher'">SELECT id,name,teacher_user_id AS account FROM teacher_users</when>
          <when test="role == 'company'">SELECT id,company_name AS name,username AS account FROM company_users</when>
          <when test="role == 'admin'">SELECT id,name,username AS account FROM admin_users</when>
          <otherwise>SELECT id,name,username AS account FROM admin_users WHERE 1=0</otherwise>
        </choose>
        ) accounts
        WHERE LOCATE(#{query},name)>0 OR LOCATE(#{query},account)>0
        ORDER BY CASE WHEN account=#{query} THEN 0 WHEN name=#{query} THEN 1 ELSE 2 END,id
        LIMIT 50
        </script>
        """)
    List<Map<String,Object>> search(@Param("role") String role,@Param("query") String query);

    @Select("""
        <script>
        <choose>
          <when test="role == 'student'">SELECT id,name,student_user_id AS account FROM student_users WHERE name=#{query} OR student_user_id=#{query}</when>
          <when test="role == 'teacher'">SELECT id,name,teacher_user_id AS account FROM teacher_users WHERE name=#{query} OR teacher_user_id=#{query}</when>
          <when test="role == 'company'">SELECT id,company_name AS name,username AS account FROM company_users WHERE company_name=#{query} OR username=#{query}</when>
          <when test="role == 'admin'">SELECT id,name,username AS account FROM admin_users WHERE name=#{query} OR username=#{query}</when>
          <otherwise>SELECT id,name,username AS account FROM admin_users WHERE 1=0</otherwise>
        </choose>
        ORDER BY id LIMIT 20
        </script>
        """)
    List<Map<String,Object>> findExact(@Param("role") String role,@Param("query") String query);
}
