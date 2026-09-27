package com.internship.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.TeacherUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Personal, non-executable templates share the persistent draft store. Tombstones prevent re-seeding. */
@Service
public class AutomationTemplates {
    private static final String SESSION = "__personal_templates__";
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public AutomationTemplates(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }

    public void initialize(TeacherUser teacher) {
        for(var template:new AutomationDefinitions(json,null).templates()) {
            String id=UUID.nameUUIDFromBytes(("automation-template:"+teacher.getId()+":"+template.get("id")).getBytes(StandardCharsets.UTF_8)).toString();
            var definition=json.createObjectNode().put("kind","TEMPLATE").put("name",template.get("label").toString())
                    .put("templateKey",template.get("id").toString()).put("deleted",false);
            db.update("INSERT IGNORE INTO teacher_auto_task_draft(id,teacher_id,teacher_type,session_id,revision,source_text,definition,issues,updated_at) VALUES(?,?,?,?,1,?,?, '[]',NOW(3))",
                    id,teacher.getId(),teacher.getTeacherType(),SESSION,template.get("prompt"),definition.toString());
        }
    }
    public List<Map<String,Object>> list(TeacherUser teacher) {
        initialize(teacher);
        return db.queryForList("SELECT id,revision,source_text,JSON_UNQUOTE(JSON_EXTRACT(definition,'$.name')) AS label,JSON_UNQUOTE(JSON_EXTRACT(definition,'$.templateKey')) AS template_key FROM teacher_auto_task_draft WHERE teacher_id=? AND session_id=? AND JSON_EXTRACT(definition,'$.deleted')=false ORDER BY JSON_UNQUOTE(JSON_EXTRACT(definition,'$.templateKey'))",teacher.getId(),SESSION)
                .stream().map(r->Map.<String,Object>of("id",r.get("id"),"revision",r.get("revision"),"label",r.get("label"),"prompt",r.get("source_text"),"templateKey",r.get("template_key"))).toList();
    }
    public void update(TeacherUser teacher,String id,long revision,String name,String prompt,boolean delete) {
        if(!delete&&(name==null||name.isBlank()||name.length()>100||prompt==null||prompt.isBlank()||prompt.length()>8000))AutomationRules.fail("模板名称为1至100字，内容为1至8000字。");
        int updated=delete
                ?db.update("UPDATE teacher_auto_task_draft SET definition=JSON_SET(definition,'$.deleted',true),revision=revision+1,updated_at=NOW(3) WHERE id=? AND teacher_id=? AND session_id=? AND revision=? AND JSON_EXTRACT(definition,'$.deleted')=false",id,teacher.getId(),SESSION,revision)
                :db.update("UPDATE teacher_auto_task_draft SET definition=JSON_SET(definition,'$.name',?),source_text=?,revision=revision+1,updated_at=NOW(3) WHERE id=? AND teacher_id=? AND session_id=? AND revision=? AND JSON_EXTRACT(definition,'$.deleted')=false",name.trim(),prompt,id,teacher.getId(),SESSION,revision);
        if(updated!=1)throw AutomationService.conflict();
    }
}
