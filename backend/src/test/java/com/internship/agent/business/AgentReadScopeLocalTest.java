package com.internship.agent.business;

import com.internship.mapper.AgentReadMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;

/** SELECT-only integration checks. Explicitly rejects remote database URLs. */
@EnabledIfEnvironmentVariable(named="DEEPINTERN_LOCAL_READ_QA",matches="true")
class AgentReadScopeLocalTest {
    @Test void projectionsAndEveryTeacherScopeMatchLocalBusinessRecords() throws Exception {
        String url=System.getenv("DEEPINTERN_DB_URL");
        assertTrue(url!=null && url.startsWith("jdbc:mysql://127.0.0.1:3306/internship?"));
        Configuration config=new Configuration();
        config.setEnvironment(new Environment("local",new JdbcTransactionFactory(),new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"user",System.getenv("DEEPINTERN_DB_PASSWORD"))));
        String resource="com/internship/mapper/AgentReadMapper.xml";
        try(var input=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession()) {
            Connection db=session.getConnection();db.setReadOnly(true);
            AgentReadMapper mapper=session.getMapper(AgentReadMapper.class);
            long student;
            try(var ps=db.prepareStatement("SELECT id FROM student_users WHERE student_user_id = ?")) {
                ps.setString(1,"s001");try(var rs=ps.executeQuery()){assertTrue(rs.next());student=rs.getLong(1);}
            }
            var profile=mapper.studentAdvisorProfile(student);
            assertEquals("s001",profile.get("studentNumber"));
            assertTrue(Set.of("title","studentNumber","grade","className","major","college","division","phoneConfigured","emailConfigured").containsAll(profile.keySet()));
            assertNull(mapper.studentAdvisorProfile(Long.MAX_VALUE));
            var reflections=mapper.studentReflections(student,0,20);
            assertEquals(count(db,"SELECT COUNT(*) FROM internship_reflection WHERE student_id=? AND deleted=0",student),mapper.countStudentReflections(student));
            for(var row:reflections) assertTrue(Set.of("id","periodNumber","deadline","submittedAt","status","submissionStatus").containsAll(row.keySet()));
            var appraisal=mapper.studentAppraisal(student);
            if(appraisal!=null) assertTrue(Set.of("id","status","submittedAt","reviewedAt","reviewComment","fileCount").containsAll(appraisal.keySet()));
            assertNull(mapper.studentAppraisal(Long.MAX_VALUE));
            Set<String> checked=new HashSet<>();
            try(var ps=db.prepareStatement("SELECT id,teacher_type,department_id,division_id FROM teacher_users WHERE status=1 ORDER BY id");var rs=ps.executeQuery()) {
                while(rs.next()) {
                    String type=rs.getString(2);if(type==null||checked.contains(type))continue;
                    Long department=positive(rs.getString(3)),division=positive(rs.getString(4));
                    if((type.equals("COLLEGE")&&department==null)||(type.equals("DEPARTMENT")&&division==null))continue;
                    if(!Set.of("COLLEGE","DEPARTMENT","COUNSELOR").contains(type))continue;
                    Actor actor=new Actor("qa",rs.getLong(1),"ROLE_TEACHER_"+type,"QA",type,department,division,false,Set.of("view:student_tracking"));
                    List<Long> ids=mapper.teacherScopeIds(actor,1);
                    assertEquals((long)ids.size(),((Number)mapper.teacherTodoCounts(actor).get("responsible")).longValue());
                    String scope=type.equals("COLLEGE")?"COLLEGE":type.equals("DEPARTMENT")?"DIVISION":"COUNSELOR";
                    Long scopeId=type.equals("COLLEGE")?department:type.equals("DEPARTMENT")?division:null;
                    var rows=mapper.teacherSummaryStudentRows(actor,scope,scopeId);
                    assertEquals(new HashSet<>(ids),rows.stream().map(row->Long.valueOf(row.get("id").toString())).collect(java.util.stream.Collectors.toSet()));
                    if(type.equals("DEPARTMENT")) {
                        assertTrue(mapper.teacherSummaryScopes(actor,"COLLEGE",null,null).isEmpty());
                        assertTrue(mapper.teacherSummaryScopes(actor,"DIVISION",null,null).stream().allMatch(row->division.toString().equals(row.get("id").toString())));
                        assertTrue(mapper.teacherSummaryStudentRows(actor,"DIVISION",Long.MAX_VALUE).isEmpty());
                    }
                    checked.add(type);
                }
            }
            assertEquals(Set.of("COLLEGE","DEPARTMENT","COUNSELOR"),checked);
        }
    }
    private static Long positive(String value){try{long id=Long.parseLong(value);return id>0?id:null;}catch(Exception ignored){return null;}}
    private static long count(Connection db,String sql,long id)throws Exception{try(var ps=db.prepareStatement(sql)){ps.setLong(1,id);try(var rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}}
}
