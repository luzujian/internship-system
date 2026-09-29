package com.internship.mapper;

import com.internship.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysDictMapper {
    @Select("SELECT id, dict_type, dict_code, dict_label, dict_value, sort_order, remark, deleted, create_time, update_time FROM sys_dict WHERE deleted = 0 ORDER BY sort_order")
    List<SysDict> findAll();

    @Select("SELECT id, dict_type, dict_code, dict_label, dict_value, sort_order, remark, deleted, create_time, update_time FROM sys_dict WHERE dict_type = #{type} AND deleted = 0 ORDER BY sort_order")
    List<SysDict> findByType(@Param("type") String type);

    @Select("SELECT dict_label FROM sys_dict WHERE dict_type = #{type} AND dict_code = #{code} AND deleted = 0 LIMIT 1")
    String findLabel(@Param("type") String type, @Param("code") String code);
}
