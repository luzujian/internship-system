package com.internship.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.Position;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PositionCompanyNameMapperTest {
    @Test
    void allPositionsReturnTheCompanyNameFromTheExistingLeftJoin() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/PositionMapper.xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.internship.mapper.PositionMapper.findAll");
        String sql = statement.getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        assertTrue(sql.contains("cu.company_name AS company_name"));
        assertTrue(sql.contains("LEFT JOIN company_users cu ON p.company_id = cu.id"));
        assertTrue(statement.getResultMaps().getFirst().getResultMappings().stream().anyMatch(mapping ->
                "companyName".equals(mapping.getProperty()) && "company_name".equals(mapping.getColumn())));
        assertTrue(sql.contains("ORDER BY p.create_time DESC"));
    }

    @Test
    void companyNameIsReadableButCannotBeSuppliedByAMutationRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Position position = mapper.readValue("{\"companyId\":7,\"companyName\":\"伪造的企业名称\"}", Position.class);
        assertEquals(7L, position.getCompanyId());
        assertNull(position.getCompanyName());
        position.setCompanyName("实际发布企业");
        assertEquals("实际发布企业", mapper.readTree(mapper.writeValueAsString(position)).get("companyName").asText());
    }
}
