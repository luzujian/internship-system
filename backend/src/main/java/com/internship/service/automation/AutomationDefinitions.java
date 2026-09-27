package com.internship.service.automation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.internship.config.DynamicChatClientFactory;
import com.internship.agent.business.AgentClock;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.internship.service.automation.AutomationRules.*;

@Service
public class AutomationDefinitions {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AutomationDefinitions.class);
    private final ObjectMapper json;
    private final DynamicChatClientFactory models;
    public AutomationDefinitions(ObjectMapper json,DynamicChatClientFactory models){this.json=json;this.models=models;}
    public List<Map<String,Object>> templates(){
        ObjectNode appraisal=base("鉴定表截止提醒","实习鉴定表即将截止，请尽快完成提交。");
        appraisal.put("templateId","APPRAISAL_DEADLINE");
        appraisal.set("condition",json.createObjectNode().set("all",json.valueToTree(List.of(
            leaf(json,"appraisal.required","EQ",true),leaf(json,"appraisal.canSubmit","EQ",true),leaf(json,"appraisal.deadline","DAYS_UNTIL",List.of(1,3))))));
        ObjectNode job=base("未投递学生提醒","请尽快登录系统申请实习岗位，及时推进实习安排。");
        job.put("templateId","UNAPPLIED_DAILY");job.set("condition",leaf(json,"internship.status","EQ","NOT_FOUND"));
        return List.of(template(appraisal,"在系统设置的实习鉴定表提交截止日期前第3、2、1天，每天上午09:00提醒，截止当天不发，提交后停止提醒。"),
                       template(job,"每天上午09:00提醒当前实习状态为未投递的学生；有有效投递或进入后续实习阶段后停止提醒。"));
    }
    private ObjectNode base(String name,String body){
        ObjectNode n=json.createObjectNode().put("version",2).put("name",name);
        n.set("audience",json.createObjectNode().set("studentIds",json.createArrayNode()));
        n.set("trigger",json.createObjectNode().put("mode","DAILY").put("time","09:00"));
        ObjectNode notify=json.createObjectNode().put("subject",name).put("body",body);notify.set("channels",json.valueToTree(List.of("EMAIL")));n.set("notification",notify);
        n.set("repeatPolicy",json.createObjectNode().put("mode","REPEAT").put("cooldownMinutes",1440));n.set("validity",json.createObjectNode());return n;
    }
    private Map<String,Object> template(ObjectNode n,String rule){
        String prefix="我想要创建一个自动化任务。\n任务内容是：通过邮件提醒我负责范围内的学生。\n执行条件是："+rule+"\n提醒内容是：";
        return Map.of("id",n.path("templateId").asText(),"label",n.path("name").asText(),"definition",n,"prompt",prefix+n.path("notification").path("body").asText(),"prefix",prefix);
    }
    public ObjectNode parse(String message,JsonNode previous){
        if(message==null||message.isBlank()||message.length()>8000)fail("请填写1至8000字的任务描述。");
        for(var template:templates())if(message.startsWith(template.get("prefix").toString())){
            ObjectNode n=((ObjectNode)template.get("definition")).deepCopy();((ObjectNode)n.path("notification")).put("body",message.substring(template.get("prefix").toString().length()));return n;
        }
        String prompt="""
            你是教师通知与跟进任务解析器，仅输出JSON草稿，不执行。使用管理员选择的模型能力。
            根据教师要求填写 version:2,name,audience:{studentIds:[],queries:[明确的用户名/学号/姓名],excludeStudentIds:[]},
            condition:条件树,trigger:时间,notification:{channels:[EMAIL或STATION_REMINDER],subject:邮件标题,body:正文},
            repeatPolicy:{mode:ONCE_PER_MATTER或REPEAT,cooldownMinutes:1440},validity:{startAt:null,endAt:null},questions:[]。
            condition支持 {always:true}、{all:[条件]}、{any:[条件]}、{not:条件}、{field:字段,op:操作符,value:比较值}、{exists:记录集合,where:同一条记录上的条件}。
            普通通知无需业务条件。请返岗是提醒内容，不等于可检测未返岗。没有登记的事实提出具体问题，严禁替换条件。
            未投递=NOT_FOUND，无Offer=NO_OFFER，待确认=PENDING_CONFIRMATION，已确定=CONFIRMED，实习中=IN_PROGRESS，已结束=ENDED，中断=INTERRUPTED，延期=DELAYED。
            尚未到待确认=NOT_FOUND或NO_OFFER，不能包含中断、延期、已确认和实习中。
            查询名不要编造ID。教师明确说“所有学生”“我负责范围内的学生”“本班学生”“本系学生”或“本学院学生”时，audience.studentIds和queries均为空数组，表示由服务器限定本人负责范围，不需要追问班级、专业名称或编号，也不得将这些范围描述填入queries。只有未提及任何对象且无法从上一版获知时才询问对象。
            班级、专业、系、学院及企业岗位名称条件使用对应Name字段精确匹配，不猜编号。
            岗位申请和面试status使用pending、approved、interview、pending_interview、interview_passed、interview_failed、rejected、withdrawn、cancelled。
            实习确认status数字0待审核、1通过、2退回，recallStatus数字2已撤回。
            鉴定表status使用MISSING、DRAFT、RETURNED、SUBMITTED、APPROVED；心得status使用MISSING、DRAFT、SUBMITTED、REVIEWED。
            业务申请type是selfPractice自主实习、unitChange换岗、delay延期，status是pending、approved、rejected、withdrawn。
            实习方式internship.source使用SELF_PRACTICE自主实习、NEW_APPLICATION岗位投递、INTERVIEW_INVITATION面试邀请。仅用确定事实，不把其他来源猜成自主实习。
            trigger:指定{mode:ONCE,date:yyyy-MM-dd,time:HH:mm}，每日{mode:DAILY,time:HH:mm}，
            每周{mode:WEEKLY,weekdays:[1到7],time:HH:mm}，每月{mode:MONTHLY,monthdays:[1到31],time:HH:mm}，
            间隔{mode:INTERVAL,minutes:整数}，条件满足{mode:CONDITION}。定时可使用times代替time指定多个钟点。
            截止前第3、2、1天用DAYS_UNTIL:[1,3]，截止当天不属于这个范围。不得写死系统日历日期。
            鉴定表待提交用appraisal.required EQ true；心得待提交用exists reflections where required EQ true。
            默认每事项一次，明确每天/每周重复才REPEAT。消息正文必须保留教师提供的原文，未提供则拟简短草稿。
            只修改本次明确要求的字段，保留上一版其他设置。语义不清时在questions追问，不能虚构默认条件。
            """+"\n"+AgentClock.nowLine()+"\n字段目录："+FIELDS+"\n操作符："+OPERATORS+"\n每种记录可用字段："+RECORD_SCHEMA;
        try{
            String output=models.createPreviewChatClient(null,prompt,30000).prompt()
                .options(DeepSeekChatOptions.builder().temperature(0.1).maxTokens(2200).build())
                .user("上一版草稿："+(previous==null?"无":previous.toString())+"\n教师本次要求：\n"+message).call().content();
            if(output==null)fail("模型没有返回草稿，请重试或选择模板。");
            int first=output.indexOf('{'),last=output.lastIndexOf('}');if(first<0||last<=first)fail("模型返回的任务格式无效，未创建任务。");
            JsonNode value=json.readTree(output.substring(first,last+1));if(!value.isObject())fail("任务草稿格式无效。");return (ObjectNode)value;
        }catch(com.internship.exception.BusinessException e){throw e;}catch(Exception e){
            Throwable cause=e;for(int i=0;i<8&&cause.getCause()!=null;i++)cause=cause.getCause();
            // Do not log provider bodies, credentials or the teacher's message.
            log.warn("Automation draft parsing failed: {}",cause.getClass().getSimpleName());
            boolean timeout=cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException;
            throw new com.internship.exception.BusinessException(timeout
                ?"任务解析超时，描述已保留，请重试或使用模板。"
                :"任务解析暂不可用，描述已保留，可使用模板或稍后重试。");
        }
    }
    public List<String> issues(JsonNode definition){
        List<String> errors=new ArrayList<>();
        if(definition.path("questions").isArray())definition.path("questions").forEach(q->{if(!q.asText().isBlank())errors.add(q.asText());});
        try{validate(definition.path("condition"));}catch(RuntimeException e){errors.add(e.getMessage());}
        try{AutomationTiming.validate(definition.path("trigger"));}catch(RuntimeException e){errors.add(e.getMessage());}
        if(definition.path("name").asText().isBlank()||definition.path("name").asText().length()>100)errors.add("请填写100字以内的任务名称。");
        if(definition.path("notification").path("body").asText().isBlank()||definition.path("notification").path("body").asText().length()>2000)errors.add("提醒内容需要为1至2000字。");
        if(definition.path("notification").path("subject").asText().isBlank()||definition.path("notification").path("subject").asText().length()>180)errors.add("请填写180字以内的邮件标题。");
        JsonNode channels=definition.path("notification").path("channels");if(!channels.isArray()||channels.isEmpty())errors.add("请选择通知渠道。");else channels.forEach(c->{if(!com.internship.agent.business.AgentCapabilityService.AUTO_TASK_CHANNELS.contains(c.asText()))errors.add("该通知渠道尚未接入。");});
        if(definition.path("notification").path("subject").asText().matches("(?s).*[\\r\\n].*"))errors.add("邮件标题不能换行。");
        if(!definition.path("audience").isObject())errors.add("请明确提醒对象。");
        if(!Set.of("ONCE_PER_MATTER","REPEAT").contains(definition.path("repeatPolicy").path("mode").asText()))errors.add("请明确重复提醒规则。");
        int cooldown=definition.path("repeatPolicy").path("cooldownMinutes").asInt(1440);if(cooldown<1||cooldown>525600)errors.add("提醒间隔不正确。");
        for(String field:List.of("startAt","endAt"))if(definition.path("validity").hasNonNull(field))try{java.time.LocalDateTime.parse(definition.path("validity").path(field).asText());}catch(Exception e){errors.add("开始和结束时间需要包含日期与钟点。");}
        if(definition.path("validity").hasNonNull("startAt")&&definition.path("validity").hasNonNull("endAt")&&definition.path("validity").path("startAt").asText().compareTo(definition.path("validity").path("endAt").asText())>=0)errors.add("结束时间必须晚于开始时间。");
        return errors.stream().distinct().toList();
    }
}
