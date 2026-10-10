package com.internship.agent.business;

import com.fasterxml.jackson.databind.*;
import com.internship.service.CompanyUserService;
import com.internship.service.CompanySchoolLiaisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Page and assistant consume the same schema; values are read afresh for the authenticated company. */
@Service
@RequiredArgsConstructor
public class CompanyInfoKnowledgeService {
    private final ObjectMapper json;
    private final CompanyUserService companies;
    private final CompanySchoolLiaisonService liaisons;

    public Map<String,Object> read(Long companyId) {
        var company=companies.findById(companyId);
        if(company==null)throw new AgentException("NOT_FOUND","企业资料不存在。",404);
        try(var stream=new ClassPathResource("agent/company-info-schema.json").getInputStream()) {
            JsonNode schema=json.readTree(stream);
            JsonNode values=json.valueToTree(company);
            Map<String,JsonNode> displayValues=new HashMap<>();
            List<Map<String,Object>> fields=new ArrayList<>(),tags=new ArrayList<>();
            for(JsonNode field:schema.path("fields")) {
                Map<String,Object> row=json.convertValue(field,Map.class);
                String prop=field.path("prop").asText();
                JsonNode stored=values.path(prop);
                JsonNode display=stored.isMissingNode() || stored.isNull() ? field.path("defaultValue") : stored;
                displayValues.put(prop,display);
                row.put("value", "schoolLiaisons".equals(prop) ? liaisons.findByCompanyId(companyId)
                        : json.convertValue(values.path(prop),Object.class));
                if(field.has("defaultValue")) {
                    row.put("displayValue",json.convertValue(display,Object.class));
                    row.put("usesPageDefault",stored.isMissingNode() || stored.isNull());
                }
                if("alumniMembers".equals(prop) && (company.getAlumniMembers()==null || company.getAlumniMembers().isEmpty())
                        && Integer.valueOf(1).equals(company.getIsAlumniUnit()) && company.getAlumniRepresentativeName()!=null)
                    row.put("value",List.of(Map.of("name",company.getAlumniRepresentativeName(),"phone",Objects.toString(company.getAlumniRepresentativePhone(),""),"position",Objects.toString(company.getAlumniRepresentativePosition(),""))));
                fields.add(row);
            }
            Set<String> modes=new HashSet<>(Arrays.asList(displayValues.get("cooperationMode").asText("").split("\\s*,\\s*")));
            for(JsonNode tag:schema.path("tags")) {
                String prop=tag.path("prop").asText();boolean active=false;
                if("cooperationMode".equals(prop) && tag.path("requiresApproval").asBoolean(false)) active=true;
                for(JsonNode value:tag.path("values")) active|="cooperationMode".equals(prop)?modes.contains(value.asText()):value.equals(displayValues.get(prop));
                if(tag.path("requiresApproval").asBoolean(false) && !Integer.valueOf(1).equals(company.getAuditStatus())) active=false;
                Map<String,Object> row=json.convertValue(tag,Map.class);row.put("active",active);tags.add(row);
            }
            long activeTagCount=tags.stream().filter(tag -> Boolean.TRUE.equals(tag.get("active"))).count();
            return Map.of("id",companyId,"fields",fields,"tags",tags,"activeTagCount",activeTagCount,"inactiveTagCount",tags.size()-activeTagCount,
                    "registrationApproved",Integer.valueOf(1).equals(company.getAuditStatus()),
                    "source","企业信息基础信息的共享字段定义与本企业最新已保存资料",
                    "note","未保存的页面修改不属于已保存资料；usesPageDefault=true表示页面使用默认显示，不能当成已经核实的企业事实；未知编码没有解释依据，不能猜测。标签数量使用activeTagCount/inactiveTagCount；用户要求解释所有字段或标签时，逐项使用description说明含义，不要只复述名称或值。");
        }catch(java.io.IOException e){throw new AgentException("KNOWLEDGE_UNAVAILABLE","企业字段定义暂不可用。",503);}
    }
}
