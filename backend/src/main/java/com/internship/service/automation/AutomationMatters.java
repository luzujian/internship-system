package com.internship.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.internship.service.AgentNotificationGuardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared matter-key computation for enumerate, run-preview skip analysis and guard checks. */
public final class AutomationMatters {
    private AutomationMatters(){}
    @SuppressWarnings("unchecked")
    public static String matter(JsonNode def,Map<String,Object> facts){
        String rule=def.path("condition").toString();List<String> keys=new ArrayList<>();
        if(rule.contains("appraisal"))keys.add("appraisal:calendar:"+Objects.toString(facts.get("calendar.endDate"),"current"));
        for(String key:(List<String>)facts.getOrDefault("matterKeys",List.of()))if((rule.contains("reflections")&&key.startsWith("reflection:"))||(rule.contains("PENDING_CONFIRMATION")&&key.startsWith("offer:")))keys.add(key);
        recordKeys(def.path("condition"),facts,keys);
        if(keys.isEmpty())keys.add(rule.contains("internship.status")?"internship:application": "custom:"+AgentNotificationGuardService.key(0L,def.path("notification").path("body").asText().replaceAll("\\s+"," ").trim(),"TEXT"));
        String result=String.join("|",keys.stream().sorted().distinct().toList());if(result.length()>215)throw new IllegalStateException("一次提醒涉及的事项过多，请拆分任务。");
        return result;
    }
    @SuppressWarnings("unchecked")
    static void recordKeys(JsonNode rule,Map<String,Object> facts,List<String> keys){
        if(AutomationRules.evaluate(rule,facts,AutomationService.now().toLocalDate())!=AutomationRules.Truth.TRUE)return;
        if(rule.has("all")||rule.has("any")){rule.path(rule.has("all")?"all":"any").forEach(child->recordKeys(child,facts,keys));return;}
        if(!rule.has("exists"))return;String source=rule.path("exists").asText();
        for(var row:(List<Map<String,Object>>)facts.getOrDefault(source,List.of()))if(AutomationRules.evaluate(rule.path("where"),row,AutomationService.now().toLocalDate())==AutomationRules.Truth.TRUE){
            String key=source.equals("reflections")?"reflection:"+row.get("periodNumber"):source+":"+row.get("id");
            keys.add(key);
        }
    }
}
