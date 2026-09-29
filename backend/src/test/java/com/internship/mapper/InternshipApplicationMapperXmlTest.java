package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InternshipApplicationMapperXmlTest {

    @Test
    void activeApplicationCanTransitionToInterviewResult() throws Exception {
        String resource = "com/internship/mapper/InternshipApplicationMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String sql = configuration
                .getMappedStatement("com.internship.mapper.InternshipApplicationMapper.updateStatus")
                .getBoundSql(Map.of("id", 10L, "status", "interview_passed"))
                .getSql()
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains(
                "status IN ('pending', 'approved', 'interview', 'pending_interview', 'interview_passed')"));
    }
}
