package com.gdmu.mapper;

import com.gdmu.entity.ResourceViewRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ResourceViewRecordMapper {

    @Insert("INSERT INTO resource_view_record (resource_id, user_id, user_type, action_type, create_time) " +
            "VALUES (#{resourceId}, #{userId}, #{userType}, #{actionType}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ResourceViewRecord record);

    @Select("SELECT COUNT(*) FROM resource_view_record WHERE resource_id = #{resourceId} " +
            "AND user_id = #{userId} AND user_type = #{userType} AND action_type = #{actionType}")
    int countByResourceAndUser(@Param("resourceId") Long resourceId,
                               @Param("userId") String userId,
                               @Param("userType") String userType,
                               @Param("actionType") String actionType);
}
