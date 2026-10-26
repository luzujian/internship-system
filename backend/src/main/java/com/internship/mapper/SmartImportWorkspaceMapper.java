package com.internship.mapper;

import com.internship.importing.SmartImportWorkspaceRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SmartImportWorkspaceMapper {
    String COLUMNS = "id,owner_key AS ownerKey,session_id AS sessionId,source_ref AS sourceRef,source_sha256 AS sourceSha256,source_json AS sourceJson,files_json AS filesJson,state_json AS stateJson,version,created_at AS createdAt,updated_at AS updatedAt,lease_token AS leaseToken,lease_turn_id AS leaseTurnId,lease_until AS leaseUntil,cancelled_turn_id AS cancelledTurnId";
    @Insert("INSERT INTO agent_import_workspace(id,owner_key,session_id,source_ref,source_sha256,source_json,files_json,state_json,version,created_at,updated_at) VALUES(#{id},#{ownerKey},#{sessionId},#{sourceRef},#{sourceSha256},#{sourceJson},#{filesJson},#{stateJson},#{version},#{createdAt},#{updatedAt})")
    int insert(SmartImportWorkspaceRecord row);
    @Select("SELECT " + COLUMNS + " FROM agent_import_workspace WHERE id=#{id}")
    SmartImportWorkspaceRecord find(String id);
    @Update("UPDATE agent_import_workspace SET state_json=#{stateJson},version=version+1,updated_at=#{now} WHERE id=#{id} AND owner_key=#{owner} AND session_id=#{session} AND version=#{version} AND ((#{token} IS NULL AND (lease_token IS NULL OR lease_until <= #{now})) OR (lease_token=#{token} AND lease_until > #{now} AND (cancelled_turn_id IS NULL OR cancelled_turn_id <> lease_turn_id)))")
    int save(@Param("id") String id, @Param("owner") String owner, @Param("session") String session,
             @Param("version") long version, @Param("stateJson") String stateJson, @Param("token") String token, @Param("now") long now);
    @Update("UPDATE agent_import_workspace SET lease_token=#{token},lease_turn_id=#{turn},lease_until=#{until} WHERE id=#{id} AND (lease_token IS NULL OR lease_until <= #{now}) AND (cancelled_turn_id IS NULL OR cancelled_turn_id <> #{turn})")
    int claim(@Param("id") String id,@Param("token") String token,@Param("turn") String turn,@Param("now") long now,@Param("until") long until);
    @Update("UPDATE agent_import_workspace SET lease_until=#{until} WHERE id=#{id} AND lease_token=#{token} AND lease_until > #{now} AND (cancelled_turn_id IS NULL OR cancelled_turn_id <> lease_turn_id)")
    int renew(@Param("id") String id,@Param("token") String token,@Param("now") long now,@Param("until") long until);
    @Update("UPDATE agent_import_workspace SET lease_token=NULL,lease_turn_id=NULL,lease_until=0 WHERE id=#{id} AND lease_token=#{token}")
    int release(@Param("id") String id,@Param("token") String token);
    @Update("UPDATE agent_import_workspace SET cancelled_turn_id=#{turn},lease_token=NULL,lease_turn_id=NULL,lease_until=0,version=version+1,updated_at=#{now},state_json=JSON_SET(state_json,'$.activeTurnId',NULL,'$.activeMessage',NULL,'$.phase',CASE WHEN JSON_EXTRACT(state_json,'$.receipt') IS NULL OR JSON_TYPE(JSON_EXTRACT(state_json,'$.receipt'))='NULL' THEN 'WAITING_INPUT' ELSE 'COMPLETED' END) WHERE id=#{id} AND (lease_turn_id=#{turn} OR lease_token IS NULL OR lease_until <= #{now})")
    int cancel(@Param("id") String id,@Param("turn") String turn,@Param("now") long now);
}
