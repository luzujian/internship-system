package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExportLatestRecordSqlTest {
    @Test void exportedDetailsAndStatisticsUseOneLatestActiveRecordPerStudent() throws Exception {
        Configuration configuration=new Configuration();
        for(String resource:List.of("com/internship/mapper/JobSearchStatusMapper.xml","com/internship/mapper/StudentInternshipStatusMapper.xml")) {
            try(InputStream input=Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        for(String statement:List.of("findAllWithDetails","getMajorProgressForExport","getClassStatisticsForExport")) {
            String sql=configuration.getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper."+statement)
                    .getBoundSql(Map.of()).getSql().replaceAll("\\s+"," ").toLowerCase();
            assertTrue(sql.contains("sis.id = ( select latest_status.id"),statement);
            assertTrue(sql.contains("order by latest_status.create_time desc, latest_status.id desc limit 1"),statement);
            assertTrue(sql.contains("su.status = 1"),statement);
            assertFalse(sql.contains("on su.id = sis.student_id"),statement);
            if(!statement.equals("findAllWithDetails")) {
                assertTrue(sql.contains("su.id is not null and"),statement);
                assertTrue(sql.contains("nullif(count(distinct su.id), 0)"),statement);
            }
        }
    }
}
