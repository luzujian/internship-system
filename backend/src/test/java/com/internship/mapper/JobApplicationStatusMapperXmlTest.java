package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JobApplicationStatusMapperXmlTest {

    @Test
    void mainAndMirrorUpdatesShareCompleteActiveStatusSet() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "com/internship/mapper/StudentJobApplicationMapper.xml");
        parse(configuration, "com/internship/mapper/InternshipApplicationMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", 1L);
        parameters.put("studentId", 2L);
        parameters.put("positionId", 3L);
        parameters.put("status", "interview_passed");
        parameters.put("rejectReason", null);

        String mainSql = configuration.getMappedStatement(
                        "com.internship.mapper.StudentJobApplicationMapper.updateLatestStatusIfActive")
                .getBoundSql(parameters).getSql();
        String mirrorSql = configuration.getMappedStatement(
                        "com.internship.mapper.InternshipApplicationMapper.updateLatestStatusByStudentAndPosition")
                .getBoundSql(parameters).getSql();
        String studentListSql = configuration.getMappedStatement(
                        "com.internship.mapper.StudentJobApplicationMapper.findByStudentId")
                .getBoundSql(parameters).getSql();

        for (String active : new String[]{"pending", "approved", "interview",
                "pending_interview", "interview_passed"}) {
            assertTrue(mainSql.contains("'" + active + "'"));
            assertTrue(mirrorSql.contains("'" + active + "'"));
        }
        assertTrue(mainSql.contains("ORDER BY COALESCE(apply_date, create_time) DESC, id DESC"));
        assertTrue(mirrorSql.contains("ORDER BY COALESCE(apply_time, create_time) DESC, id DESC"));
        assertTrue(studentListSql.contains(
                "ORDER BY COALESCE(sja.apply_date, sja.create_time) DESC, sja.id DESC"));
    }

    private void parse(Configuration configuration, String resource) throws Exception {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()).parse();
        }
    }
}
