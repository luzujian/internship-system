package com.internship.agent.business;

import com.internship.entity.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.PositionService;
import com.internship.service.StudentJobApplicationService;
import com.internship.utils.StudentProfileCompleteness;
import com.internship.websocket.AnnouncementWebSocketHandler;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import static com.internship.agent.business.AgentIdentityService.requireStudent;
import static com.internship.agent.business.AgentTypes.Actor;

/** Explicit student confirmation; business mutation and immutable receipt commit together. */
@Service @Slf4j @RequiredArgsConstructor
public class JobApplyExecuteService {
    public record PreviewRequest(String actionType,String objectId) {}
    public record ApplyRequest(String positionId,String clientRequestId,boolean confirmed,String actionId,Long previewedAt){
        public ApplyRequest(String positionId,String clientRequestId,boolean confirmed){this(positionId,clientRequestId,confirmed,null,null);}
    }
    public record WithdrawRequest(String applicationId,String clientRequestId,boolean confirmed,String actionId,Long previewedAt){
        public WithdrawRequest(String applicationId,String clientRequestId,boolean confirmed){this(applicationId,clientRequestId,confirmed,null,null);}
    }
    private final AgentTaskService tasks;
    private final AgentCapabilityService capabilities;
    private final AgentActionService actions;
    private final StudentPlanService plans;
    private final PositionService positions;
    private final CompanyUserMapper companies;
    private final StudentUserMapper students;
    private final StudentJobApplicationService applications;
    private final AnnouncementWebSocketHandler webSocket;

    public AgentActionService.ActionView preview(Actor actor,PreviewRequest request){
        authorize(actor);
        if(request==null)throw new AgentException("INVALID_INPUT","请选择需要办理的岗位或申请。",400);
        if("JOB_APPLY".equals(request.actionType())){
            Position position=visiblePosition(AgentReadTools.id(request.objectId()));company(position);requireCompleteProfile(students.findById(actor.id()));
            return actions.preview(actor,"JOB_APPLY","position:"+position.getId(),Map.of("positionId",String.valueOf(position.getId())),positionVersion(position));
        }
        if("JOB_WITHDRAW".equals(request.actionType())){
            StudentJobApplication application=ownedApplication(actor,AgentReadTools.id(request.objectId()));
            return actions.preview(actor,"JOB_WITHDRAW","application:"+application.getId(),Map.of("applicationId",String.valueOf(application.getId())),applicationVersion(application));
        }
        throw new AgentException("INVALID_INPUT","不支持此操作。",400);
    }

    public Map<String,Object> apply(Actor actor,ApplyRequest request){
        gate(actor,request==null?null:request.clientRequestId(),request!=null&&request.confirmed());
        if(request.actionId()==null)throw new AgentException("PREVIEW_REQUIRED","请先打开确认卡核对最新信息。",400);
        Map<String,Object> parameters=Map.of("positionId",String.valueOf(AgentReadTools.id(request.positionId())));
        AtomicReference<StudentJobApplication> created=new AtomicReference<>();
        Map<String,Object> result=actions.execute(actor,request.actionId(),"JOB_APPLY",parameters,()->{
            authorize(actor);
            Position position=visiblePosition(AgentReadTools.id(request.positionId()));CompanyUser company=company(position);
            if(!Objects.equals(actions.get(actor,request.actionId()).expectedVersion(),positionVersion(position)))throw changed();
            StudentUser student=students.findById(actor.id());
            if(student==null||!Integer.valueOf(1).equals(student.getStatus()))throw new AgentException("AUTH_REQUIRED","账号不可用，请重新登录。",401);
            requireCompleteProfile(student);
            StudentJobApplication application=new StudentJobApplication();
            application.setStudentId(actor.id());application.setPositionId(position.getId());application.setCompanyId(company.getId());
            application.setCompanyName(company.getCompanyName());application.setPositionName(position.getPositionName());
            application.setStudentName(student.getName());application.setStudentNo(student.getStudentId());application.setPhone(student.getPhone());
            application.setEmail(student.getEmail());application.setMajor(student.getMajor());application.setGrade(student.getGrade()==null?null:String.valueOf(student.getGrade()));
            try{if(applications.create(application)!=1||application.getId()==null)throw new AgentException("EXECUTE_FAILED","投递未完成，请核对后重试。",502);}
            catch(BusinessException e){throw new AgentException("EXECUTE_FAILED",e.getMessage(),400);}
            created.set(application);
            return receipt(application,"pending");
        });
        StudentJobApplication application=created.get();
        if(application!=null){
            try{webSocket.sendNewApplicationToCompany(application.getCompanyId(),application.getStudentName(),application.getPositionName());webSocket.sendCompanyTodoUpdateForApplication(application.getCompanyId());}
            catch(Exception e){log.warn("投递已保存，企业实时推送未完成 action={}",request.actionId());}
        }
        return withPlan(actor,result);
    }

    public Map<String,Object> withdraw(Actor actor,WithdrawRequest request){
        gate(actor,request==null?null:request.clientRequestId(),request!=null&&request.confirmed());
        if(request.actionId()==null)throw new AgentException("PREVIEW_REQUIRED","请先打开确认卡核对最新信息。",400);
        Map<String,Object> parameters=Map.of("applicationId",String.valueOf(AgentReadTools.id(request.applicationId())));
        AtomicReference<StudentJobApplication> removed=new AtomicReference<>();
        Map<String,Object> result=actions.execute(actor,request.actionId(),"JOB_WITHDRAW",parameters,()->{
            authorize(actor);
            StudentJobApplication application=ownedApplication(actor,AgentReadTools.id(request.applicationId()));
            if(!Objects.equals(actions.get(actor,request.actionId()).expectedVersion(),applicationVersion(application)))throw changed();
            try{if(applications.deleteById(application.getId(),actor.id())!=1)throw new AgentException("EXECUTE_FAILED","申请状态已变化，请重新核对。",409);}
            catch(BusinessException e){throw new AgentException("EXECUTE_FAILED",e.getMessage(),400);}
            removed.set(application);return receipt(application,"withdrawn");
        });
        if(removed.get()!=null&&removed.get().getCompanyId()!=null){
            try{webSocket.sendCompanyTodoUpdateForApplication(removed.get().getCompanyId());}catch(Exception e){log.warn("撤回已保存，企业实时推送未完成 action={}",request.actionId());}
        }
        return withPlan(actor,result);
    }
    private Map<String,Object> receipt(StudentJobApplication application,String status){
        Map<String,Object> result=new LinkedHashMap<>();result.put("applicationId",String.valueOf(application.getId()));result.put("status",status);
        result.put("company",Objects.toString(application.getCompanyName(),""));result.put("position",Objects.toString(application.getPositionName(),""));
        result.put("links",List.of(Map.of("label","查看我的申请","action","OPEN_APPLICATION")));return result;
    }
    private Map<String,Object> withPlan(Actor actor,Map<String,Object> receipt){
        Map<String,Object> result=new LinkedHashMap<>(receipt);
        try{result.put("plan",plans.factsOf(plans.refresh(actor)));}catch(Exception e){result.put("planRefreshPending",true);}return result;
    }
    private void requireCompleteProfile(StudentUser student){
        List<String> missing=StudentProfileCompleteness.missingRequiredFields(student);
        if(!missing.isEmpty())throw new AgentException("PROFILE_INCOMPLETE",StudentProfileCompleteness.incompleteMessage(missing),400);
    }
    private void authorize(Actor actor){
        if(!tasks.enabled())throw new AgentException("AI_DISABLED","系统当前未启用AI助手。",503);
        if(actor.readOnly())throw new AgentException("READ_ONLY_FORBIDDEN","只读身份不可办理业务。",403);
        requireStudent(actor);if(!capabilities.has(actor,"job_apply"))throw new AgentException("CAPABILITY_DISABLED","当前身份未开通投递执行能力。",403);
    }
    private void gate(Actor actor,String requestId,boolean confirmed){
        authorize(actor);if(!confirmed)throw new AgentException("NEED_CONFIRM","请先核对确认卡中的操作内容。",400);
        try{UUID.fromString(requestId);}catch(Exception e){throw new AgentException("INVALID_INPUT","请求标识不正确。",400);}
    }
    private Position visiblePosition(Long id){Position p=positions.findById(id);if(p==null||!"active".equalsIgnoreCase(p.getStatus()))throw new AgentException("OBJECT_UNAVAILABLE","岗位已下架或不可投递，请重新核对。",404);return p;}
    private CompanyUser company(Position p){CompanyUser c=companies.findById(p.getCompanyId());if(c==null||!Integer.valueOf(1).equals(c.getStatus())||!Integer.valueOf(1).equals(c.getAuditStatus()))throw new AgentException("OBJECT_UNAVAILABLE","岗位所属企业当前不可用。",404);return c;}
    private StudentJobApplication ownedApplication(Actor actor,Long id){StudentJobApplication a=applications.findById(id);if(a==null||!actor.id().equals(a.getStudentId()))throw new AgentException("OBJECT_UNAVAILABLE","申请不存在或不属于当前用户。",404);return a;}
    private static String positionVersion(Position p){return Objects.toString(p.getUpdateTime(),"")+":"+p.getStatus();}
    private static String applicationVersion(StudentJobApplication a){return Objects.toString(a.getUpdateTime(),"")+":"+a.getStatus();}
    private static AgentException changed(){return new AgentException("PREVIEW_CHANGED","业务信息已变化，请重新打开确认卡核对。",409);}
}
