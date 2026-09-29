package com.internship.service;

import com.internship.entity.*;
import com.internship.mapper.AnnouncementMapper;
import com.internship.service.impl.AnnouncementServiceImpl;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, loopback only, business account CRUD, all fixture writes rolled back. */
@EnabledIfEnvironmentVariable(named="SECURITY_LOCAL_MYSQL_TESTS", matches="true")
class AnnouncementOwnerLocalMapperTest {
    @Test void ownerRoundTripsAndCannotBeReassignedIncludingImportedRows() throws Exception {
        var dataSource=new org.apache.ibatis.datasource.unpooled.UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:3306/internship?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&characterEncoding=utf8",
                "user", Objects.requireNonNull(System.getenv("SECURITY_LOCAL_MYSQL_PASSWORD")));
        var environment=new org.apache.ibatis.mapping.Environment("local-only",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),dataSource);
        var config=new org.apache.ibatis.session.Configuration(environment);
        try(var input=getClass().getClassLoader().getResourceAsStream("com/internship/mapper/AnnouncementMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,config,"AnnouncementMapper.xml",config.getSqlFragments()).parse();
        }
        var factory=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config);
        try(var session=factory.openSession(false)) {
            try {
                var actor=new User();actor.setId(900000001L);actor.setRole("ROLE_ADMIN");CurrentHolder.setUser(actor);
                var mapper=session.getMapper(AnnouncementMapper.class);var service=new AnnouncementServiceImpl(mapper);
                String title="security-owner-fixture-"+UUID.randomUUID();
                var a=new Announcement();a.setTitle(title);a.setContent("Disposable transaction fixture");a.setPublisher("显示名称");a.setPublisherRole("COLLEGE");a.setStatus("DRAFT");a.setTargetType("STUDENT");
                a.setPublisherUserId(999L);a.setPublisherUserType("TEACHER");assertEquals(1,service.insert(a));
                long id;
                try(var query=session.getConnection().prepareStatement("SELECT id FROM announcements WHERE title=?")) {
                    query.setString(1,title);try(var rows=query.executeQuery()){assertTrue(rows.next());id=rows.getLong(1);assertFalse(rows.next());}
                }
                var stored=mapper.findById(id);assertEquals(actor.getId(),stored.getPublisherUserId());assertEquals("ADMIN",stored.getPublisherUserType());
                stored.setPublisherUserId(666L);stored.setPublisherUserType("TEACHER");assertEquals(1,mapper.update(stored));session.clearCache();
                assertEquals(actor.getId(),mapper.findById(id).getPublisherUserId());assertEquals("ADMIN",mapper.findById(id).getPublisherUserType());
                var imported=service.importFromExcel(List.of(Map.of("标题",title+"-import","内容","fixture","发布人","另一显示名","发布人身份","学院教师","状态","草稿","目标群体","全体学生")));
                assertEquals(1,imported.get("successCount"));
                try(var query=session.getConnection().prepareStatement("SELECT publisher_user_id,publisher_user_type FROM announcements WHERE title=?")) {
                    query.setString(1,title+"-import");try(var rows=query.executeQuery()){assertTrue(rows.next());assertEquals(actor.getId().longValue(),rows.getLong(1));assertEquals("ADMIN",rows.getString(2));}
                }
            } finally {session.rollback(true);CurrentHolder.remove();}
        }
    }
}
