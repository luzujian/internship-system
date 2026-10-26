package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.service.automation.AutomationService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.internship.agent.business.AgentTypes.*;

/** The agent proposes UI operations; real submissions remain behind the existing confirmation APIs. */
@Service
public class AgentUiTools {
    private static final Set<String> NAMES=Set.of("ui_navigate","ui_fill_form","teacher_automation_preview","admin_capabilities_preview");
    private final ObjectMapper json;
    private final AgentCapabilityService capabilities;
    private final FillIntentService fill;
    private final AutomationService automation;
    private final AgentCapabilityPreviewService adminCapabilities;
    public AgentUiTools(ObjectMapper json,AgentCapabilityService capabilities,@Lazy FillIntentService fill,
                        @Lazy AutomationService automation,@Lazy AgentCapabilityPreviewService adminCapabilities){
        this.json=json;this.capabilities=capabilities;this.fill=fill;this.automation=automation;this.adminCapabilities=adminCapabilities;
    }
    public boolean handles(String name){return NAMES.contains(name);}
    public void validateContext(Map<String,Object> filters){
        JsonNode targets=json.valueToTree(filters.get("uiTargets"));
        if(!targets.isNull()){
            if(!targets.isArray()||targets.size()>150)throw invalid();Set<String> ids=new HashSet<>();
            for(JsonNode target:targets){String id=target.path("id").asText();
                if(!id.matches("[a-zA-Z0-9_-]{1,80}")||!ids.add(id)||target.path("label").asText().length()>200||!Set.of("page","open","form").contains(target.path("kind").asText()))throw invalid();
            }
        }
        JsonNode form=json.valueToTree(filters.get("uiForm"));
        if(!form.isNull()&&(!form.isObject()||form.path("formId").asText().isBlank()||form.path("formId").asText().length()>100||!form.path("fields").isArray()||form.path("fields").size()>40||!form.path("instanceId").canConvertToLong()))throw invalid();
    }
    public List<Spec> specs(Actor actor,PageContext page){
        if(actor.readOnly()||!capabilities.has(actor,"agent_chat"))return List.of();
        List<Spec> result=new ArrayList<>();Map<String,Object> filters=page.filters();
        if(filters.get("uiTargets") instanceof List<?> list&&!list.isEmpty())result.add(new Spec("ui_navigate","仅在用户明确要求打开页面或表单时，从本轮页面候选选择targetId。用户还要求在打开后继续填表时，continue=true会在页面就绪后自动续接原要求；仅打开页面时为false。只返回导航提案，禁止提交、发布、删除、代投递或操作密码。","{\"type\":\"object\",\"properties\":{\"targetId\":{\"type\":\"string\"},\"continue\":{\"type\":\"boolean\"}},\"required\":[\"targetId\"],\"additionalProperties\":false}"));
        if(filters.get("uiForm")!=null&&capabilities.has(actor,"form_fill"))result.add(new Spec("ui_fill_form","仅将用户明确指定的字段赋值生成当前表单提案。prop必须来自本轮fields；保留/不变字段禁止修改，密码/文件不能代填；选择值遵循候选。只填写不提交。","""
            {"type":"object","properties":{"formId":{"type":"string"},"instanceId":{"type":"integer"},"assignments":{"type":"array","maxItems":40,"items":{"type":"object","properties":{"prop":{"type":"string"},"value":{"type":"string"},"operation":{"type":"string","enum":["SET","CLEAR"]},"confidence":{"type":"number","minimum":0,"maximum":1}},"required":["prop","value","confidence"],"additionalProperties":false}},"questions":{"type":"array","items":{"type":"string"}},"skippedFields":{"type":"array","items":{"type":"object","properties":{"prop":{"type":"string"},"reason":{"type":"string"}},"required":["prop","reason"]}}},"required":["formId","instanceId","assignments"],"additionalProperties":false}
            """));
        if(actor.teacherType()!=null && capabilities.has(actor,"auto_task"))result.add(messageSpec("teacher_automation_preview","教师明确要求创建自动化任务时生成真实草稿与影响预览，后续必须由教师核对确认并启用；本工具不创建、发送或启用任务。"));
        if("ROLE_ADMIN".equals(actor.role()))result.add(messageSpec("admin_capabilities_preview","管理员明确要求调整AI能力权限时生成真实变更预览；只预览，不保存，管理员在确认卡核对后操作。"));
        return result;
    }
    private Spec messageSpec(String name,String description){return new Spec(name,description,"{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\",\"maxLength\":4000}},\"required\":[\"message\"],\"additionalProperties\":false}");}
    public Map<String,Object> execute(String authorization,Actor actor,Task task,String name,JsonNode args){
        if(specs(actor,task.getPageContext()).stream().noneMatch(spec->spec.name().equals(name)))throw AgentIdentityService.forbidden();
        Map<String,Object> result=new LinkedHashMap<>();result.put("id",UUID.randomUUID().toString());
        if("ui_navigate".equals(name)){
            String id=args.path("targetId").asText();JsonNode targets=json.valueToTree(task.getPageContext().filters().get("uiTargets"));
            boolean found=false;for(JsonNode target:targets)if(id.equals(target.path("id").asText()))found=true;
            if(!found)throw invalid();result.put("type","NAVIGATE");result.put("targetId",id);result.put("continue",args.path("continue").asBoolean(false));
        }else if("ui_fill_form".equals(name)){
            JsonNode form=json.valueToTree(task.getPageContext().filters().get("uiForm"));
            if(!form.path("formId").asText().equals(args.path("formId").asText())||form.path("instanceId").asLong()!=args.path("instanceId").asLong())throw invalid();
            List<FillIntentService.FieldDescriptor> fields=new ArrayList<>();
            for(JsonNode field:form.path("fields")){
                List<Map<String,Object>> options=field.path("options").isArray()?json.convertValue(field.path("options"),new com.fasterxml.jackson.core.type.TypeReference<>(){}):null;
                fields.add(new FillIntentService.FieldDescriptor(field.path("prop").asText(),field.path("label").asText(),field.path("type").asText("input"),options,field.path("required").asBoolean(),field.path("patternHint").asText(null),field.path("valueType").asText(null),field.path("allowCustom").asBoolean()));
            }
            var proposal=((com.fasterxml.jackson.databind.node.ObjectNode)args.deepCopy()).put("intent","form_fill");
            result.putAll(fill.validateProposal(actor,new FillIntentService.FillRequest(form.path("formId").asText(),form.path("formTitle").asText("当前表单"),fields,task.getMessage()),proposal));
            result.put("type","FORM_FILL");result.put("instanceId",form.path("instanceId").asLong());
        }else if("teacher_automation_preview".equals(name)){
            result.put("type","AUTO_TASK_PREVIEW");result.put("preview",automation.previewDraft(actor,Map.of("message",message(args,4000))));
        }else if("admin_capabilities_preview".equals(name)){
            try{result.put("type","CAPABILITIES_PREVIEW");result.put("preview",adminCapabilities.parse(actor,message(args,500)));}
            catch(AgentException e){throw e;}catch(Exception e){throw new AgentException("PREVIEW_UNAVAILABLE","暂时无法生成权限变更预览，请稍后重试。",503);}
        }else throw AgentIdentityService.forbidden();
        return result;
    }
    public String answer(List<Map<String,Object>> actions){
        List<String> text=new ArrayList<>();for(Map<String,Object> action:actions){
            switch(String.valueOf(action.get("type"))){
                case "NAVIGATE" -> text.add("已生成页面打开提案。");
                case "FORM_FILL" -> text.add("已生成当前表单的填写提案，请核对填写结果后手动提交。");
                case "AUTO_TASK_PREVIEW" -> text.add("已生成自动化任务预览，请核对并确认。");
                case "CAPABILITIES_PREVIEW" -> text.add("已生成权限变更预览，请核对后确认保存。");
                default -> throw invalid();
            }
        }return String.join("\n",text);
    }
    private String message(JsonNode args,int maximum){String text=args.path("message").asText().trim();if(text.isEmpty()||text.length()>maximum)throw invalid();return text;}
    private static AgentException invalid(){return new AgentException("INVALID_CONTEXT","页面或表单信息已变化，请刷新后重新提出要求。",400);}
}
