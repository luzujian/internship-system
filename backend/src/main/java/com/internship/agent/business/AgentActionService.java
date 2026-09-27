package com.internship.agent.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.mapper.AgentActionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.internship.agent.business.AgentTypes.Actor;

/** Confirmation identity and immutable receipts for LOCAL DATABASE actions only.
 * Suppliers must use the same database transaction and must not send external notifications. */
@Service
public class AgentActionService {
    private final AgentActionMapper mapper;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public AgentActionService(AgentActionMapper mapper,ObjectMapper json,PlatformTransactionManager manager){
        this.mapper=mapper;this.json=json;this.transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Data public static class ActionRecord {
        private String id,ownerKey,actionType,targetKey,parameters,parametersHash,expectedVersion,status,receipt,errorCode,errorMessage;
        private long previewedAt,expiresAt,updatedAt;
    }
    public record ActionView(String id,String actionType,String targetKey,Map<String,Object> parameters,
                             String expectedVersion,String status,long previewedAt,long expiresAt,
                             Map<String,Object> receipt,String errorCode,String errorMessage) {}

    public ActionView preview(Actor actor,String actionType,String targetKey,Map<String,Object> parameters,String expectedVersion){
        if(actor.readOnly())throw AgentIdentityService.forbidden();
        if(actionType==null||!actionType.matches("[A-Z][A-Z0-9_]{1,63}")||targetKey==null||targetKey.length()>180)
            throw new AgentException("INVALID_INPUT","操作预览不正确。",400);
        ActionRecord row=new ActionRecord();long now=System.currentTimeMillis();
        row.setId(UUID.randomUUID().toString());row.setOwnerKey(actor.ownerKey());row.setActionType(actionType);row.setTargetKey(targetKey);
        row.setParameters(encode(parameters==null?Map.of():parameters));row.setParametersHash(hash(row.getParameters()));
        row.setExpectedVersion(expectedVersion);row.setStatus("READY");row.setPreviewedAt(now);row.setExpiresAt(now+600_000);row.setUpdatedAt(now);
        try{mapper.insert(row);}catch(Exception e){throw unavailable();}
        return view(row);
    }
    public ActionView get(Actor actor,String actionId){return view(owned(actor,find(actionId)));}

    /** Caller must recheck current capability and object rules INSIDE executor before mutation. */
    public Map<String,Object> execute(Actor actor,String actionId,String actionType,Map<String,Object> parameters,
                                      Supplier<Map<String,Object>> executor){
        if(actor.readOnly())throw AgentIdentityService.forbidden();
        ActionRecord row=owned(actor,find(actionId));
        if(!Objects.equals(actionType,row.getActionType())||!hash(encode(parameters)).equals(row.getParametersHash()))
            throw new AgentException("ACTION_MISMATCH","确认内容已经变化，请重新预览。",409);
        if("SUCCEEDED".equals(row.getStatus()))return receipt(row);
        if(!"READY".equals(row.getStatus()))throw stateError(row);
        if(System.currentTimeMillis()>row.getExpiresAt())throw new AgentException("PREVIEW_EXPIRED","确认内容已过期，请重新核对后确认。",409);
        try{
            Integer claimed=transaction.execute(status->mapper.claim(actionId,System.currentTimeMillis()));
            if(claimed==null||claimed!=1){ActionRecord latest=owned(actor,find(actionId));if("SUCCEEDED".equals(latest.getStatus()))return receipt(latest);throw stateError(latest);}
        }catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
        try{
            return transaction.execute(status->{
                ActionRecord current=owned(actor,mapper.lock(actionId));
                if(!"RUNNING".equals(current.getStatus()))throw stateError(current);
                Map<String,Object> result=new LinkedHashMap<>(Objects.requireNonNull(executor.get(),"Action receipt required"));
                result.put("actionId",actionId);result.put("actionStatus","SUCCEEDED");
                if(mapper.finish(actionId,"SUCCEEDED",encode(result),null,null,System.currentTimeMillis())!=1)
                    throw new IllegalStateException("Action receipt was not recorded");
                return result;
            });
        }catch(RuntimeException e){
            // The database transaction rolled back: do not mark a potentially committed transaction as failed.
            ActionRecord observed;
            try{observed=owned(actor,find(actionId));}catch(Exception unavailable){throw new AgentException("ACTION_UNKNOWN","结果暂时无法确认，请查看操作记录；不要重新办理。",503);}
            if("SUCCEEDED".equals(observed.getStatus()))return receipt(observed);
            String code=e instanceof AgentException a?a.getCode():"EXECUTE_FAILED";
            String message=e instanceof AgentException?e.getMessage():"操作未完成，请核对原业务页面后重试。";
            // Transaction infrastructure/connection failures cannot prove that the commit failed.
            boolean uncertain=e instanceof org.springframework.transaction.TransactionException
                    ||e instanceof org.springframework.dao.DataAccessResourceFailureException;
            try{transaction.execute(status->{mapper.finish(actionId,uncertain?"UNKNOWN":"FAILED",null,code,clip(message,500),System.currentTimeMillis());return null;});}catch(Exception ignored){}
            if(uncertain)throw new AgentException("ACTION_UNKNOWN","结果暂时无法确认，请查看操作记录；不要重新办理。",503);
            throw e instanceof AgentException a?a:new AgentException(code,message,400);
        }
    }
    /**
     * 外部发送（邮件 / WebSocket）专用：确认账本在自己的事务里占用，供应商代码在无事务状态下执行，
     * 回执随后单独写入。绝不在数据库事务内发送外部通知；回执写入失败时保留 RUNNING，
     * 读取时表现为 UNKNOWN，提示不要重复发送。
     */
    public Map<String,Object> executeExternal(Actor actor,String actionId,String actionType,Map<String,Object> parameters,
                                              Supplier<Map<String,Object>> executor){
        if(actor.readOnly())throw AgentIdentityService.forbidden();
        ActionRecord row=owned(actor,find(actionId));
        if(!Objects.equals(actionType,row.getActionType())||!hash(encode(parameters)).equals(row.getParametersHash()))
            throw new AgentException("ACTION_MISMATCH","确认内容已经变化，请重新预览。",409);
        if("SUCCEEDED".equals(row.getStatus()))return receipt(row);
        if(!"READY".equals(row.getStatus()))throw stateError(row);
        if(System.currentTimeMillis()>row.getExpiresAt())throw new AgentException("PREVIEW_EXPIRED","确认内容已过期，请重新核对后确认。",409);
        try{
            Integer claimed=transaction.execute(status->mapper.claim(actionId,System.currentTimeMillis()));
            if(claimed==null||claimed!=1){ActionRecord latest=owned(actor,find(actionId));if("SUCCEEDED".equals(latest.getStatus()))return receipt(latest);throw stateError(latest);}
        }catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
        Map<String,Object> result;
        try{
            result=new LinkedHashMap<>(Objects.requireNonNull(executor.get(),"Action receipt required"));
        }catch(RuntimeException e){
            String code=e instanceof AgentException a?a.getCode():"EXECUTE_FAILED";
            String message=e instanceof AgentException?e.getMessage():"操作未完成，请核对后重试。";
            boolean uncertain=e instanceof org.springframework.transaction.TransactionException
                    ||e instanceof org.springframework.dao.DataAccessResourceFailureException;
            try{transaction.execute(status->{mapper.finish(actionId,uncertain?"UNKNOWN":"FAILED",null,code,clip(message,500),System.currentTimeMillis());return null;});}catch(Exception ignored){}
            if(uncertain)throw new AgentException("ACTION_UNKNOWN","结果暂时无法确认，请查看操作记录；不要重新办理。",503);
            throw e instanceof AgentException a?a:new AgentException(code,message,400);
        }
        result.put("actionId",actionId);result.put("actionStatus","SUCCEEDED");
        try{
            if(transaction.execute(status->mapper.finish(actionId,"SUCCEEDED",encode(result),null,null,System.currentTimeMillis()))!=1)
                throw new IllegalStateException("Action receipt was not recorded");
        }catch(RuntimeException e){
            // 发送已经发生；这一分支只表示回执没能落库，绝不自动重发。
            throw new AgentException("ACTION_UNKNOWN","发送结果已提交但回执未能保存，结果待核实；请勿重复发送。",503);
        }
        return result;
    }
    private ActionRecord find(String id){
        try{UUID.fromString(id);}catch(Exception e){throw new AgentException("PREVIEW_REQUIRED","请先打开确认卡核对操作内容。",400);}
        try{ActionRecord row=mapper.find(id);if(row==null)throw new AgentException("ACTION_NOT_FOUND","操作记录不存在，请重新预览。",404);return row;}
        catch(AgentException e){throw e;}catch(Exception e){throw unavailable();}
    }
    private ActionRecord owned(Actor actor,ActionRecord row){if(row==null||!actor.ownerKey().equals(row.getOwnerKey()))throw AgentIdentityService.forbidden();return row;}
    private ActionView view(ActionRecord row){
        boolean interrupted="RUNNING".equals(row.getStatus())&&System.currentTimeMillis()-row.getUpdatedAt()>180_000;
        return new ActionView(row.getId(),row.getActionType(),row.getTargetKey(),decode(row.getParameters()),row.getExpectedVersion(),interrupted?"UNKNOWN":row.getStatus(),row.getPreviewedAt(),row.getExpiresAt(),row.getReceipt()==null?null:decode(row.getReceipt()),interrupted?"ACTION_UNKNOWN":row.getErrorCode(),interrupted?"处理未返回确认回执，请核对业务记录；不会自动重复执行。":row.getErrorMessage());
    }
    private Map<String,Object> receipt(ActionRecord row){return decode(row.getReceipt());}
    private AgentException stateError(ActionRecord row){return switch(row.getStatus()){
        case "RUNNING"->new AgentException("ACTION_PENDING","操作正在处理，正在核验回执；请勿重复办理。",409);
        case "UNKNOWN"->new AgentException("ACTION_UNKNOWN","操作结果尚未确认，请核对业务记录；不会自动重复办理。",409);
        default->new AgentException("ACTION_FAILED",row.getErrorMessage()==null?"此操作未完成，请重新核对后创建新的确认卡。":row.getErrorMessage(),409);
    };}
    private String encode(Object value){try{return json.writeValueAsString(sorted(json.valueToTree(value)));}catch(Exception e){throw new AgentException("INVALID_INPUT","操作参数无法保存。",400);}}
    private JsonNode sorted(JsonNode value){
        if(value.isObject()){ObjectNode out=json.createObjectNode();List<String> names=new ArrayList<>();value.fieldNames().forEachRemaining(names::add);Collections.sort(names);names.forEach(name->out.set(name,sorted(value.get(name))));return out;}
        if(value.isArray()){var out=json.createArrayNode();value.forEach(item->out.add(sorted(item)));return out;}return value;
    }
    private Map<String,Object> decode(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw unavailable();}}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
    private static AgentException unavailable(){return new AgentException("ACTION_STORE_UNAVAILABLE","操作记录存储暂时不可用，未发起新的业务操作。请使用原业务页面或稍后重试。",503);}
}
