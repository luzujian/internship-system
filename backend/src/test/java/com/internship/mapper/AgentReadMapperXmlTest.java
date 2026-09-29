package com.internship.mapper;

import com.internship.agent.business.AgentTypes;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReadMapperXmlTest {
    @Test void teacherSummaryScopesApplyTeacherAuthorizationInSql()throws Exception{
        Configuration configuration=parseMapper();
        AgentTypes.Actor college=new AgentTypes.Actor("c",1L,"ROLE_TEACHER_COLLEGE","学院","COLLEGE",24L,null,false,Set.of());
        AgentTypes.Actor department=new AgentTypes.Actor("d",2L,"ROLE_TEACHER_DEPARTMENT","系室","DEPARTMENT",24L,15L,false,Set.of());
        AgentTypes.Actor counselor=new AgentTypes.Actor("n",3L,"ROLE_TEACHER_COUNSELOR","辅导员","COUNSELOR",null,null,false,Set.of());

        String collegeSql=sql(configuration,"teacherSummaryStudentRows",params(college,"COLLEGE",24L));
        String departmentSql=sql(configuration,"teacherSummaryStudentRows",params(department,"DIVISION",16L));
        String counselorSql=sql(configuration,"teacherSummaryStudentRows",params(counselor,"COUNSELOR",null));
        String classSql=sql(configuration,"teacherSummaryScopes",scopeParams(counselor,"CLASS","23信管1班",null));
        String divisionSql=sql(configuration,"teacherSummaryScopes",scopeParams(department,"DIVISION","信工系",null));

        assertTrue(collegeSql.contains("coalesce(m.department_id, dv.department_id) = ?"));
        assertTrue(departmentSql.contains("m.division_id = ?"));
        assertTrue(departmentSql.contains("dv.id = ?"));
        assertTrue(counselorSql.contains("summary_ccr.counselor_id = ?"));
        assertTrue(classSql.contains("summary_ccr.counselor_id = ?"));
        assertTrue(classSql.contains("summary_ccr.class_id = c.id"));
        assertFalse(classSql.contains("su."));
        assertTrue(classSql.contains("c.name like concat('%', ?, '%')"));
        assertTrue(divisionSql.contains("dv.department_id = ?"));
        assertTrue(divisionSql.contains("dv.name like concat('%', ?, '%')"));
    }

    private Configuration parseMapper()throws Exception{
        Configuration configuration=new Configuration();
        String resource="com/internship/mapper/AgentReadMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(resource)){
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    private Map<String,Object> params(AgentTypes.Actor actor,String scopeType,Long scopeId){
        Map<String,Object> params=new HashMap<>();
        params.put("actor",actor);params.put("scopeType",scopeType);params.put("scopeId",scopeId);
        return params;
    }
    private Map<String,Object> scopeParams(AgentTypes.Actor actor,String scopeType,String scopeName,Long scopeId){
        Map<String,Object> params=params(actor,scopeType,scopeId);params.put("scopeName",scopeName);return params;
    }
    private String sql(Configuration configuration,String method,Map<String,Object> params){
        return configuration.getMappedStatement("com.internship.mapper.AgentReadMapper."+method)
                .getBoundSql(params).getSql().toLowerCase(Locale.ROOT).replaceAll("\s+"," ").trim();
    }
}
