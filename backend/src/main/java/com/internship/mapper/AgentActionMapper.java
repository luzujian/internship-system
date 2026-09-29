package com.internship.mapper;

import com.internship.agent.business.AgentActionService.ActionRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentActionMapper {
    String COLUMNS="id,owner_key AS ownerKey,action_type AS actionType,target_key AS targetKey,parameters,parameters_hash AS parametersHash,expected_version AS expectedVersion,status,previewed_at AS previewedAt,expires_at AS expiresAt,updated_at AS updatedAt,receipt,error_code AS errorCode,error_message AS errorMessage";
    @Insert("INSERT INTO agent_action(id,owner_key,action_type,target_key,parameters,parameters_hash,expected_version,status,previewed_at,expires_at,updated_at) VALUES(#{id},#{ownerKey},#{actionType},#{targetKey},#{parameters},#{parametersHash},#{expectedVersion},#{status},#{previewedAt},#{expiresAt},#{updatedAt})")
    void insert(ActionRecord row);
    @Select("SELECT "+COLUMNS+" FROM agent_action WHERE id=#{id}") ActionRecord find(String id);
    @Select("SELECT "+COLUMNS+" FROM agent_action WHERE id=#{id} FOR UPDATE") ActionRecord lock(String id);
    @Update("UPDATE agent_action SET status='RUNNING',updated_at=#{now} WHERE id=#{id} AND status='READY'")
    int claim(@Param("id") String id,@Param("now") long now);
    @Update("UPDATE agent_action SET status=#{status},receipt=#{receipt},error_code=#{code},error_message=#{message},updated_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int finish(@Param("id") String id,@Param("status") String status,@Param("receipt") String receipt,@Param("code") String code,@Param("message") String message,@Param("now") long now);
}
