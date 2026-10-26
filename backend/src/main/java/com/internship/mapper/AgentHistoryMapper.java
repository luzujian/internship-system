package com.internship.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** Durable assistant records; deliberately no foreign key cascade to business records. */
@Mapper
public interface AgentHistoryMapper {
    @Select("SELECT COUNT(*) FROM agent_session s JOIN agent_task t ON 1=0 JOIN agent_request r ON 1=0 JOIN agent_draft d ON 1=0 JOIN agent_session_event e ON 1=0 JOIN agent_action a ON 1=0") int available();
    @Insert("INSERT INTO agent_session(id,owner_key,title,archived,updated_at,snapshot) VALUES(#{id},#{owner},#{title},#{archived},#{now},#{snapshot}) ON DUPLICATE KEY UPDATE snapshot=VALUES(snapshot),updated_at=VALUES(updated_at),title=COALESCE(title,VALUES(title))")
    void saveSession(@Param("id") String id,@Param("owner") String owner,@Param("title") String title,@Param("archived") boolean archived,@Param("now") long now,@Param("snapshot") String snapshot);
    @Select("SELECT snapshot,title,archived,updated_at FROM agent_session WHERE id=#{id}") Map<String,Object> session(String id);
    @Select("SELECT snapshot,title,archived,updated_at FROM agent_session WHERE id=#{id} FOR UPDATE") Map<String,Object> lockSession(String id);
    @Select("SELECT snapshot,title,archived,updated_at FROM agent_session WHERE owner_key=#{owner} ORDER BY updated_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> sessions(@Param("owner") String owner,@Param("offset") int offset,@Param("limit") int limit);
    @Update("UPDATE agent_session SET title=#{title},archived=#{archived},updated_at=#{now} WHERE id=#{id} AND owner_key=#{owner}")
    int metadata(@Param("id") String id,@Param("owner") String owner,@Param("title") String title,@Param("archived") boolean archived,@Param("now") long now);
    @Delete("DELETE FROM agent_session WHERE id=#{id} AND owner_key=#{owner}") int deleteSession(@Param("id") String id,@Param("owner") String owner);
    @Insert("INSERT INTO agent_task(id,session_id,owner_key,created_at,updated_at,version,status,snapshot) VALUES(#{id},#{session},#{owner},#{created},#{now},1,#{status},#{snapshot})")
    void insertTask(@Param("id") String id,@Param("session") String session,@Param("owner") String owner,@Param("created") long created,@Param("now") long now,@Param("status") String status,@Param("snapshot") String snapshot);
    @Update("UPDATE agent_task SET updated_at=#{now},version=version+1,status=#{status},snapshot=#{snapshot} WHERE id=#{id} AND session_id=#{session} AND owner_key=#{owner} AND cancelled=FALSE AND status IN ('QUEUED','RUNNING','CANCELLING')")
    void saveTask(@Param("id") String id,@Param("session") String session,@Param("owner") String owner,@Param("created") long created,@Param("now") long now,@Param("status") String status,@Param("snapshot") String snapshot);
    @Update("UPDATE agent_task SET updated_at=#{now},version=version+1,status=#{status},snapshot=#{snapshot} WHERE id=#{id} AND session_id=#{session} AND owner_key=#{owner} AND version=#{version} AND cancelled=FALSE AND status IN ('QUEUED','RUNNING','CANCELLING')")
    int saveTaskVersioned(@Param("id") String id,@Param("session") String session,@Param("owner") String owner,@Param("version") long version,@Param("now") long now,@Param("status") String status,@Param("snapshot") String snapshot);
    @Update("UPDATE agent_task SET cancelled=TRUE,status='CANCELLED',updated_at=#{now},version=version+1 WHERE id=#{id} AND owner_key=#{owner} AND version=#{version} AND status='WAITING_INPUT' AND cancelled=FALSE")
    int cancelWaiting(@Param("id") String id,@Param("owner") String owner,@Param("version") long version,@Param("now") long now);
    @Select("SELECT snapshot,version,status,updated_at,cancelled FROM agent_task WHERE id=#{id}") Map<String,Object> task(String id);
    @Select("SELECT snapshot,version,status,updated_at,cancelled FROM agent_task WHERE id=#{id} FOR UPDATE") Map<String,Object> lockTask(String id);
    @Select("<script>SELECT snapshot,version,status,updated_at,cancelled FROM agent_task WHERE session_id=#{session}<if test='beforeTime != null'> AND (created_at &lt; #{beforeTime} OR (created_at=#{beforeTime} AND id &lt; #{beforeId}))</if> ORDER BY created_at DESC,id DESC LIMIT #{limit}</script>")
    List<Map<String,Object>> tasks(@Param("session") String session,@Param("beforeTime") Long beforeTime,@Param("beforeId") String beforeId,@Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM agent_task WHERE session_id=#{session} AND status IN ('QUEUED','RUNNING','CANCELLING')") int running(String session);
    @Select("SELECT COUNT(*) FROM agent_task WHERE session_id=#{session}") int taskCount(String session);
    @Select("SELECT COALESCE(MAX(CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.revisionNumber')) AS UNSIGNED)),0) FROM agent_task WHERE session_id=#{session} AND (id=#{root} OR JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.rootMessageId'))=#{root})")
    int latestRevision(@Param("session") String session,@Param("root") String root);
    @Update("UPDATE agent_task SET cancelled=TRUE,status='CANCELLED',version=version+1 WHERE id=#{id} AND status IN ('QUEUED','RUNNING')") int cancel(String id);
    @Update("UPDATE agent_task SET status='FAILED',snapshot=#{snapshot},updated_at=#{now},version=version+1 WHERE id=#{id} AND version=#{version} AND updated_at <= #{cutoff} AND cancelled=FALSE AND status IN ('QUEUED','RUNNING','CANCELLING')")
    int interrupt(@Param("id") String id,@Param("version") long version,@Param("cutoff") long cutoff,@Param("now") long now,@Param("snapshot") String snapshot);
    @Select("SELECT cancelled FROM agent_task WHERE id=#{id}") Boolean cancelled(String id);
    @Delete("DELETE FROM agent_task WHERE session_id=#{session}") void deleteTasks(String session);
    @Select("SELECT task_id FROM agent_request WHERE owner_key=#{owner} AND request_id=#{request}") String request(@Param("owner") String owner,@Param("request") String request);
    @Insert("INSERT INTO agent_request(owner_key,request_id,task_id,created_at) VALUES(#{owner},#{request},#{task},#{now})")
    int claim(@Param("owner") String owner,@Param("request") String request,@Param("task") String task,@Param("now") long now);
    @Delete("DELETE FROM agent_request WHERE owner_key=#{owner} AND request_id=#{request} AND task_id=#{task}")
    void release(@Param("owner") String owner,@Param("request") String request,@Param("task") String task);
    @Delete("DELETE r FROM agent_request r JOIN agent_task t ON t.id=r.task_id WHERE t.session_id=#{session}") void deleteRequests(String session);
    @Select("SELECT draft_key AS draftKey,text,revision,updated_at AS updatedAt FROM agent_draft WHERE owner_key=#{owner} AND draft_key=#{key}")
    Map<String,Object> draft(@Param("owner") String owner,@Param("key") String key);
    @Insert("INSERT INTO agent_draft(owner_key,draft_key,text,revision,updated_at) VALUES(#{owner},#{key},'',0,#{now}) ON DUPLICATE KEY UPDATE draft_key=draft_key")
    void ensureDraft(@Param("owner") String owner,@Param("key") String key,@Param("now") long now);
    @Update("UPDATE agent_draft SET text=#{text},revision=revision+1,updated_at=#{now} WHERE owner_key=#{owner} AND draft_key=#{key} AND revision=#{revision}")
    int draftUpdate(@Param("owner") String owner,@Param("key") String key,@Param("text") String text,@Param("revision") long revision,@Param("now") long now);
    @Delete("DELETE FROM agent_draft WHERE owner_key=#{owner} AND draft_key=#{key}") void deleteDraft(@Param("owner") String owner,@Param("key") String key);
    @Insert("INSERT INTO agent_session_event(id,session_id,owner_key,kind,created_at,snapshot) VALUES(#{id},#{session},#{owner},#{kind},#{created},#{snapshot}) ON DUPLICATE KEY UPDATE snapshot=IF(owner_key=VALUES(owner_key) AND session_id=VALUES(session_id),VALUES(snapshot),snapshot)")
    void event(@Param("id") String id,@Param("session") String session,@Param("owner") String owner,@Param("kind") String kind,@Param("created") long created,@Param("snapshot") String snapshot);
    @Select("SELECT snapshot FROM agent_session_event WHERE session_id=#{session} AND owner_key=#{owner} ORDER BY created_at,id")
    List<String> events(@Param("session") String session,@Param("owner") String owner);
    @Delete("DELETE FROM agent_session_event WHERE session_id=#{session}") void deleteEvents(String session);
}
