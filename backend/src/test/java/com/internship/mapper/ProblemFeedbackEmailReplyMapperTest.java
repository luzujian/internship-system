package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProblemFeedbackEmailReplyMapperTest {
    @Test void rowLockAndRecipientLookupAreBoundToTheSavedFeedbackRoleAndUser() throws Exception {
        Configuration config = new Configuration();
        var resource = new ClassPathResource("com/internship/mapper/ProblemFeedbackMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, config, resource.getPath(), config.getSqlFragments()).parse();
        }
        String lock = config.getMappedStatement("com.internship.mapper.ProblemFeedbackMapper.findByIdForUpdate").getBoundSql(Map.of("id", 7L)).getSql();
        String recipient = config.getMappedStatement("com.internship.mapper.ProblemFeedbackMapper.findRecipientEmail").getBoundSql(Map.of("id", 7L)).getSql();
        assertTrue(lock.contains("FOR UPDATE"));
        for (String role : new String[]{"student", "teacher", "company"}) assertTrue(recipient.contains("f.user_type = '" + role + "'"));
        assertTrue(recipient.contains("s.id = f.user_id") && recipient.contains("t.id = f.user_id") && recipient.contains("c.id = f.user_id"));
        assertTrue(recipient.contains("WHERE f.id = ?"));
    }
}
