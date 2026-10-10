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
    void insertPersistsTheSharedTimestampInsteadOfGeneratingADifferentAttemptTime() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "com/internship/mapper/StudentJobApplicationMapper.xml");
        var bound = configuration.getMappedStatement("com.internship.mapper.StudentJobApplicationMapper.insert")
                .getBoundSql(new HashMap<String, Object>());
        var names = bound.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("applyDate", "createTime", "updateTime"),
                names.subList(names.size() - 3, names.size()));
        assertTrue(bound.getSql().contains("COALESCE(?, NOW()), COALESCE(?, NOW())"));
    }

    @Test
    void withdrawalAndReviewUpdatesCheckStateAtomically() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "com/internship/mapper/StudentJobApplicationMapper.xml");
        parse(configuration, "com/internship/mapper/InternshipApplicationMapper.xml");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", 1L);
        parameters.put("status", "interview_passed");
        parameters.put("rejectReason", null);
        String deleteSql = configuration.getMappedStatement(
                        "com.internship.mapper.StudentJobApplicationMapper.deleteById")
                .getBoundSql(parameters).getSql();
        assertTrue(deleteSql.contains("AND status = 'pending'"));
        for (String statement : new String[]{
                "com.internship.mapper.StudentJobApplicationMapper.updateStatusIfActive",
                "com.internship.mapper.InternshipApplicationMapper.updateStatus"}) {
            String sql = configuration.getMappedStatement(statement).getBoundSql(parameters).getSql()
                    .replaceAll("\\s+", " ");
            assertTrue(sql.contains("? IN ('approved', 'rejected') AND status = 'pending'"));
            assertTrue(sql.contains("? IN ('interview_passed', 'interview_failed') AND status IN ('approved', 'interview', 'pending_interview')"));
        }
    }

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
        if (resource.endsWith("StudentJobApplicationMapper.xml")) {
            parse(configuration, "com/internship/mapper/PositionMapper.xml");
        }
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()).parse();
        }
    }
}
