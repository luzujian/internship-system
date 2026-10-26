package com.internship.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentIdentityService;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.mapper.SmartImportWorkspaceMapper;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** MySQL owns source and import state. Redis is read only when migrating a legacy workspace. */
@Component
public class SmartImportConversationStore {
    private static final long LEASE_MILLIS = 120_000;
    private final RedisTemplate<String,String> redis;
    private final ObjectMapper json;
    private final SmartImportWorkspaceMapper mapper;
    private final PrivateImportAttachmentStorage files;
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("import-workspace-lease-",0).factory());

    public SmartImportConversationStore(@Qualifier("stringRedisTemplate") RedisTemplate<String,String> redis,ObjectMapper json,
                                       SmartImportWorkspaceMapper mapper,PrivateImportAttachmentStorage files) {
        this.redis=redis; this.json=json; this.mapper=mapper; this.files=files;
    }
    public record ToolStep(String tool,String title,String status) {}
    public record Turn(String id,String message,String answer,long createdAt,List<ToolStep> steps,String actionId,String phase) {}
    @Data public static class State {
        private String id,ownerKey,sessionId,phase="ATTACHED",activeTurnId,activeMessage,confirmingActionId,handoffTurnId;
        private String reviewedActionId,lastAnswer,sourceRef,sourceSha256;
        private long updatedAt,startedAt,version;
        private boolean legacySourceOnly;
        private List<PrivateImportAttachmentStorage.Attachment> attachments=new ArrayList<>();
        private List<SmartWorkbookReader.SourceSheet> source=new ArrayList<>();
        private SmartImportService.Options options=new SmartImportService.Options(null,null,null,null,null,null,null);
        private Map<String,Object> preview,receipt;
        private Map<String,List<SmartImportCatalog.Option>> choices=new LinkedHashMap<>();
        private List<Turn> turns=new ArrayList<>();
        private List<ToolStep> steps=new ArrayList<>();
        @JsonIgnore private String leaseToken;
    }

    @Transactional
    public State create(Actor actor,String sessionId,List<SmartWorkbookReader.SourceSheet> source,List<MultipartFile> uploads) {
        State state=new State(); state.setId(UUID.randomUUID().toString()); state.setOwnerKey(actor.ownerKey());
        state.setSessionId(sessionId); state.setSource(source); state.setSourceRef(state.getId());
        boolean retained=uploads!=null && !uploads.isEmpty();
        state.setLegacySourceOnly(!retained);
        if(retained)state.setAttachments(files.persist(state.getId(),uploads));
        try {
            if(retained && TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                    @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)files.rollbackNew(state.getId());}
                });
            insert(state); return state;
        } catch(RuntimeException e){if(retained)files.rollbackNew(state.getId());throw e;}
    }
    public State create(Actor actor,String sessionId,List<SmartWorkbookReader.SourceSheet> source) {
        return create(actor,sessionId,source,null);
    }

    public State owned(Actor actor,String id,String sessionId) {
        validateId(id);
        try {
            SmartImportWorkspaceRecord row=mapper.find(id);
            if(row==null)row=migrateLegacy(actor,id,sessionId);
            requireOwner(actor,row,sessionId); return decode(row);
        } catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    private SmartImportWorkspaceRecord migrateLegacy(Actor actor,String id,String sessionId) throws Exception {
        String value=redis.opsForValue().get(key(id));
        if(value==null)throw new AgentException("IMPORT_CONTEXT_EXPIRED","旧附件已超过原保留时间，请重新上传；聊天记录仍保留。",410);
        State state=json.readValue(value,State.class);
        if(!actor.ownerKey().equals(state.getOwnerKey()) || !Objects.equals(sessionId,state.getSessionId()))throw AgentIdentityService.forbidden();
        if(Boolean.TRUE.equals(redis.hasKey(key(id)+":lock")))throw busy();
        state.setId(id);state.setSourceRef(id);state.setLegacySourceOnly(true);state.setVersion(0);state.setLeaseToken(null);
        // Legacy uploads retained parsed rows only. Do not invent an original workbook or original-file hash.
        state.setAttachments(new ArrayList<>());
        try{insert(state);}catch(DuplicateKeyException raced){ /* The first authorized migration wins. */ }
        SmartImportWorkspaceRecord row=mapper.find(id);if(row==null)throw unavailable();return row;
    }
    private void insert(State state) {
        try {
            String source=sourceJson(state.getSource());state.setSourceSha256(sha256(source));state.setUpdatedAt(System.currentTimeMillis());
            SmartImportWorkspaceRecord row=new SmartImportWorkspaceRecord();row.setId(state.getId());row.setOwnerKey(state.getOwnerKey());row.setSessionId(state.getSessionId());
            row.setSourceRef(state.getSourceRef());row.setSourceSha256(state.getSourceSha256());row.setSourceJson(source);
            row.setFilesJson(json.writeValueAsString(state.getAttachments()));row.setStateJson(stateJson(state));row.setVersion(state.getVersion());
            row.setCreatedAt(state.getUpdatedAt());row.setUpdatedAt(state.getUpdatedAt());
            if(mapper.insert(row)!=1)throw unavailable();
        } catch(AgentException | DuplicateKeyException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public void save(State state) {
        try {
            if(!sha256(sourceJson(state.getSource())).equals(state.getSourceSha256()))throw conflict();
            long now=System.currentTimeMillis();String snapshot=stateJson(state);
            if(mapper.save(state.getId(),state.getOwnerKey(),state.getSessionId(),state.getVersion(),snapshot,state.getLeaseToken(),now)!=1){
                SmartImportWorkspaceRecord current=mapper.find(state.getId());
                if(current!=null && state.getActiveTurnId()!=null && state.getActiveTurnId().equals(current.getCancelledTurnId()))throw cancelled();
                throw conflict();
            }
            state.setVersion(state.getVersion()+1);state.setUpdatedAt(now);
        } catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    /** The fencing token is never persisted or sent to the model. */
    public void bindLease(State state,Lease lease){lease.assertActive();state.setLeaseToken(lease.token);}
    public Lease lock(String id,String turnId) {
        validateId(id);validateTurn(turnId);long now=System.currentTimeMillis();String token=UUID.randomUUID().toString();
        try {
            if(mapper.claim(id,token,turnId,now,now+LEASE_MILLIS)!=1)throw busy();
            return new Lease(id,turnId,token);
        } catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    public boolean locked(String id) {
        try{SmartImportWorkspaceRecord row=mapper.find(id);return row!=null && row.getLeaseToken()!=null && row.getLeaseUntil()>System.currentTimeMillis();}
        catch(Exception e){throw unavailable();}
    }
    public void cancel(String id,String turnId) {
        validateId(id);validateTurn(turnId);
        try{mapper.cancel(id,turnId,System.currentTimeMillis());}catch(Exception e){throw unavailable();}
    }
    public boolean cancelled(String id,String turnId) {
        if(turnId==null)return false;
        try{SmartImportWorkspaceRecord row=mapper.find(id);return row!=null && turnId.equals(row.getCancelledTurnId());}
        catch(Exception e){throw unavailable();}
    }
    public final class Lease implements AutoCloseable {
        private final String id,turnId,token;private final ScheduledFuture<?> renewal;
        private volatile boolean active=true;
        private Lease(String id,String turnId,String token){
            this.id=id;this.turnId=turnId;this.token=token;
            renewal=renewals.scheduleWithFixedDelay(this::renew,30,30,TimeUnit.SECONDS);
        }
        private void renew(){try{if(active && mapper.renew(id,token,System.currentTimeMillis(),System.currentTimeMillis()+LEASE_MILLIS)!=1)active=false;}catch(Exception e){active=false;}}
        public void assertActive(){
            if(!active)throw conflict();
            try{long now=System.currentTimeMillis();if(mapper.renew(id,token,now,now+LEASE_MILLIS)!=1){active=false;if(cancelled(id,turnId))throw SmartImportConversationStore.cancelled();throw conflict();}}
            catch(AgentException e){throw e;}catch(Exception e){active=false;throw unavailable();}
        }
        @Override public void close(){active=false;renewal.cancel(false);try{mapper.release(id,token);}catch(Exception ignored){ /* A stale token never unlocks a newer turn. */ }}
    }
    private State decode(SmartImportWorkspaceRecord row) throws Exception {
        if(!sha256(row.getSourceJson()).equals(row.getSourceSha256()))throw new AgentException("IMPORT_SOURCE_CORRUPTED","附件原始行数据校验失败，请核对存储后继续。",503);
        State state=json.readValue(row.getStateJson(),State.class);
        state.setId(row.getId());state.setOwnerKey(row.getOwnerKey());state.setSessionId(row.getSessionId());state.setVersion(row.getVersion());state.setUpdatedAt(row.getUpdatedAt());
        state.setSourceRef(row.getSourceRef());state.setSourceSha256(row.getSourceSha256());state.setSource(json.readValue(row.getSourceJson(),new TypeReference<>(){}));
        state.setAttachments(json.readValue(row.getFilesJson(),new TypeReference<>(){}));return state;
    }
    private String stateJson(State state) throws Exception {
        // Do not serialize the complete source workbook on every mutable-state update.
        String value=json.writeValueAsString(new Snapshot(state.getPhase(),state.getActiveTurnId(),state.getActiveMessage(),
                state.getConfirmingActionId(),state.getHandoffTurnId(),state.getReviewedActionId(),state.getLastAnswer(),state.getStartedAt(),
                state.isLegacySourceOnly(),state.getOptions(),state.getPreview(),state.getReceipt(),state.getChoices(),state.getTurns(),state.getSteps()));
        if(value.getBytes(StandardCharsets.UTF_8).length>12*1024*1024)throw new AgentException("IMPORT_TOO_LARGE","导入工作区过大，请拆分名单后重试。",400);return value;
    }
    private record Snapshot(String phase,String activeTurnId,String activeMessage,String confirmingActionId,String handoffTurnId,
                            String reviewedActionId,String lastAnswer,long startedAt,boolean legacySourceOnly,SmartImportService.Options options,
                            Map<String,Object> preview,Map<String,Object> receipt,Map<String,List<SmartImportCatalog.Option>> choices,List<Turn> turns,List<ToolStep> steps) {}
    private String sourceJson(List<SmartWorkbookReader.SourceSheet> source) throws Exception {
        List<SmartWorkbookReader.SourceSheet> canonical=source.stream().map(sheet->new SmartWorkbookReader.SourceSheet(sheet.id(),sheet.fileName(),sheet.name(),
                sheet.rows().stream().map(row->new SmartWorkbookReader.SourceRow(row.number(),row.cells(),new TreeSet<>(row.uncertainColumns()==null?Set.of():row.uncertainColumns()))).toList())).toList();
        String value=json.writeValueAsString(canonical);if(value.getBytes(StandardCharsets.UTF_8).length>12*1024*1024)throw new AgentException("IMPORT_TOO_LARGE","解析后的附件过大，请拆分名单再上传。",400);return value;
    }
    private static String sha256(String value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    private static void requireOwner(Actor actor,SmartImportWorkspaceRecord row,String session){if(!actor.ownerKey().equals(row.getOwnerKey()) || !Objects.equals(session,row.getSessionId()))throw AgentIdentityService.forbidden();}
    private static String key(String id){return "agent:smart-import:"+id;}
    private static void validateId(String id){try{UUID.fromString(id);}catch(Exception e){throw new AgentException("INVALID_INPUT","导入会话标识无效。",400);}}
    private static void validateTurn(String id){if(id==null || id.isBlank() || id.length()>100)throw new AgentException("INVALID_INPUT","导入轮次标识无效。",400);}
    private static AgentException busy(){return new AgentException("IMPORT_BUSY","正在处理上一条消息，请等待结果后继续。",409);}
    private static AgentException conflict(){return new AgentException("IMPORT_STATE_CONFLICT","导入状态已变化或处理租约已失效，请刷新后继续；不要重复确认。",409);}
    private static AgentException cancelled(){return new AgentException("IMPORT_CANCELLED","已停止这轮处理。文件和补充仍保留，可以继续对话。",409);}
    private static AgentException unavailable(){return new AgentException("IMPORT_CONTEXT_UNAVAILABLE","附件持久存储尚未就绪或暂时不可用，请稍后继续；不要重复确认导入。",503);}
    @PreDestroy void close(){renewals.shutdownNow();}
}
