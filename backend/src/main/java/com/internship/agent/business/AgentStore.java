package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.AgentHistoryMapper;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.internship.agent.business.AgentTypes.*;

/** MySQL owns history and request identity. Redis only owns short lived coordination. */
@Service
@RequiredArgsConstructor
public class AgentStore {
    private static final String PREFIX="agent:readonly:v1:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final AgentHistoryMapper history;

    public boolean available(){try{history.available();return true;}catch(Exception e){return false;}}
    public void save(Task task){
        try{
            task.setUpdatedAt(System.currentTimeMillis());
            history.saveTask(task.getId(),task.getSessionId(),task.getOwnerKey(),task.getCreatedAt(),task.getUpdatedAt(),task.getStatus(),encode(task));
            Map<String,Object> row=history.task(task.getId());
            if(row==null)throw missing();
            task.setVersion(number(row,"version"));
        }catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public void save(Session session){
        session.setUpdatedAt(System.currentTimeMillis());
        try{history.saveSession(session.getId(),session.getOwnerKey(),session.getTitle(),session.isArchived(),session.getUpdatedAt(),encode(session));}
        catch(Exception e){throw unavailable();}
    }
    public void metadata(Session session){
        try{history.metadata(session.getId(),session.getOwnerKey(),session.getTitle(),session.isArchived(),System.currentTimeMillis());}
        catch(Exception e){throw unavailable();}
    }
    public Session find(String id){try{Map<String,Object> row=history.session(id);return row==null?null:sessionRow(row);}catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}}
    public Session session(String id){Session result=find(id);if(result==null)throw missing();return result;}
    public List<Session> sessions(String owner){return sessions(owner,0,50);}
    public List<Session> sessions(String owner,int offset,int limit){
        try{return history.sessions(owner,offset,limit).stream().map(this::sessionRow).toList();}catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    @Transactional
    public boolean removeSession(String owner,String id){
        Map<String,Object> row=history.lockSession(id);if(row==null)return false;
        Session s=sessionRow(row);if(!owner.equals(s.getOwnerKey()))return false;
        if(history.running(id)>0)throw new AgentException("TASK_RUNNING","该会话仍有任务在执行，请先停止或等待结束后再删除。",409);
        history.deleteEvents(id);history.deleteRequests(id);history.deleteTasks(id);history.deleteDraft(owner,id);
        return history.deleteSession(id,owner)==1;
    }
    public Task task(String id){try{Map<String,Object> row=history.task(id);if(row==null)throw missing();return taskRow(row);}catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}}
    public List<Task> history(String session,Task before,int limit){
        try{return history.tasks(session,before==null?null:before.getCreatedAt(),before==null?null:before.getId(),limit).stream().map(this::taskRow).toList();}
        catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public boolean running(String session){try{return history.running(session)>0;}catch(Exception e){throw unavailable();}}
    public int taskCount(String session){try{return history.taskCount(session);}catch(Exception e){throw unavailable();}}
    public String request(String owner,String requestId){try{return history.request(owner,requestId);}catch(Exception e){throw unavailable();}}
    public boolean claimRequest(String owner,String requestId,String taskId){
        try{return history.claim(owner,requestId,taskId,System.currentTimeMillis())==1;}catch(DuplicateKeyException e){return false;}catch(Exception e){throw unavailable();}
    }
    /** A lost create response always resolves to a durable task, never a dangling request key. */
    @Transactional
    public boolean registerRequestTask(String owner,String requestId,Task task){
        Map<String,Object> row=history.lockSession(task.getSessionId());if(row==null)throw missing();
        Session session=sessionRow(row);if(!owner.equals(session.getOwnerKey()))throw AgentIdentityService.forbidden();
        if(history.request(owner,requestId)!=null)return false;
        if(history.running(session.getId())>0)throw new AgentException("TASK_RUNNING","本会话已有任务在执行，请等待或停止后再提问。",409);
        try{history.claim(owner,requestId,task.getId(),System.currentTimeMillis());}
        catch(DuplicateKeyException e){return false;}
        catch(Exception e){throw unavailable();}
        if(task.getEditOf()!=null&&task.getRootMessageId()!=null)task.setRevisionNumber(history.latestRevision(task.getSessionId(),task.getRootMessageId())+1);
        task.setUpdatedAt(System.currentTimeMillis());task.setVersion(1);
        history.insertTask(task.getId(),task.getSessionId(),owner,task.getCreatedAt(),task.getUpdatedAt(),task.getStatus(),encode(task));
        if(session.getTitle()==null||session.getTitle().isBlank())session.setTitle(task.getMessage()==null?null:task.getMessage().substring(0,Math.min(60,task.getMessage().length())));
        session.getTaskIds().add(task.getId());if(session.getTaskIds().size()>20)session.getTaskIds().removeFirst();
        save(session);return true;
    }
    /** A cancelled/deleted worker cannot overwrite a later turn, even if Redis lost its lock. */
    @Transactional
    public boolean saveSessionForTask(Session session,Task task){
        Map<String,Object> currentSession=history.lockSession(session.getId());if(currentSession==null)return false;
        Map<String,Object> currentTask=history.lockTask(task.getId());if(currentTask==null)return false;
        Task latest=taskRow(currentTask);
        if(latest.terminal()||!session.getId().equals(latest.getSessionId())||!session.getOwnerKey().equals(latest.getOwnerKey()))return false;
        save(task);save(session);return true;
    }
    public void releaseRequest(String owner,String requestId,String task){try{history.release(owner,requestId,task);}catch(Exception e){throw unavailable();}}
    public boolean lock(String session,String task){return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX+"lock:"+session,task,Duration.ofMinutes(3)));}
    public void unlock(String session,String task){try{redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class),List.of(PREFIX+"lock:"+session),task);}catch(Exception ignored){/* lock expires; durable task is retained */}}
    public boolean cancel(String id){try{return history.cancel(id)>0;}catch(Exception e){throw unavailable();}}
    public boolean interrupt(Task task,long cutoff){try{return history.interrupt(task.getId(),task.getVersion(),cutoff,System.currentTimeMillis(),encode(task))==1;}catch(Exception e){throw unavailable();}}
    public boolean cancelled(String id){try{return Boolean.TRUE.equals(history.cancelled(id));}catch(Exception e){throw unavailable();}}
    public void checkRate(String owner){String key=PREFIX+"rate:"+owner+":"+(System.currentTimeMillis()/60_000);Long n=redis.opsForValue().increment(key);if(Long.valueOf(1).equals(n))redis.expire(key,Duration.ofMinutes(2));if(n!=null&&n>10)throw new AgentException("RATE_LIMITED","请求较多，请稍后再试。",429);}
    public Map<String,Object> draft(String owner,String key){
        try{Map<String,Object> row=history.draft(owner,key);return row==null?Map.of("draftKey",key,"text","","revision",0,"updatedAt",0):row;}catch(Exception e){throw unavailable();}
    }
    @Transactional
    public Map<String,Object> saveDraft(String owner,DraftRequest request){
        try{
            if(!"new".equals(request.draftKey()))lockOwnedSession(owner,request.draftKey());
            long now=System.currentTimeMillis();history.ensureDraft(owner,request.draftKey(),now);
            if(history.draftUpdate(owner,request.draftKey(),request.text(),request.expectedRevision(),now)!=1)
                throw new AgentException("DRAFT_CONFLICT","另一页面已修改草稿，请核对最新草稿后再保存。",409);
            return draft(owner,request.draftKey());
        }catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    @Transactional
    public TimelineEvent event(String owner,String session,TimelineEvent event){
        String data=encode(event);if(data.length()>32768)throw new AgentException("INVALID_INPUT","记录内容过长。",400);
        lockOwnedSession(owner,session);
        try{history.event(event.id(),session,owner,event.kind(),event.createdAt(),data);return event;}catch(Exception e){throw unavailable();}
    }
    private void lockOwnedSession(String owner,String id){Map<String,Object> row=history.lockSession(id);if(row==null)throw missing();if(!owner.equals(sessionRow(row).getOwnerKey()))throw AgentIdentityService.forbidden();}
    public List<TimelineEvent> events(String owner,String session){try{return history.events(session,owner).stream().map(value->decode(value,TimelineEvent.class)).toList();}catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}}
    private Session sessionRow(Map<String,Object> row){Session result=decode(String.valueOf(row.get("snapshot")),Session.class);result.setTitle((String)row.get("title"));result.setArchived(truth(row.get("archived")));result.setUpdatedAt(number(row,"updated_at"));return result;}
    private Task taskRow(Map<String,Object> row){Task result=decode(String.valueOf(row.get("snapshot")),Task.class);result.setVersion(number(row,"version"));result.setStatus(String.valueOf(row.get("status")));result.setUpdatedAt(number(row,"updated_at"));if(truth(row.get("cancelled"))){result.setStatus("CANCELLED");result.setErrorCode("CANCELLED");result.setErrorMessage("生成已停止，已输出内容保留；已完成业务不会撤销。");}return result;}
    private static boolean truth(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    private static long number(Map<String,Object> row,String key){return row.get(key) instanceof Number n?n.longValue():0;}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw unavailable();}}
    private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw unavailable();}}
    private static AgentException missing(){return new AgentException("TASK_EXPIRED","聊天记录不存在或已被删除。",404);}
    private static AgentException unavailable(){return new AgentException("HISTORY_UNAVAILABLE","助手历史存储尚未就绪或暂时不可用，草稿请保留后重试；原业务页面仍可正常使用。",503);}
}
