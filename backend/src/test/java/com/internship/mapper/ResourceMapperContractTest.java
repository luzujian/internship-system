package com.internship.mapper;

import com.internship.entity.LearningResource;
import com.internship.entity.ResourceCategory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceMapperContractTest {
    @Test
    void learningResourceWriteAndListStatementsAreBoundToTheExistingPluralTable() throws Exception {
        Configuration configuration = load("LearningResourceMapper");
        String mapper = LearningResourceMapper.class.getName() + ".";
        LearningResource resource = new LearningResource();
        resource.setId(7L);
        resource.setUploaderId(42L);
        resource.setStatus("PENDING");

        BoundSql insert = configuration.getMappedStatement(mapper + "insert").getBoundSql(resource);
        BoundSql update = configuration.getMappedStatement(mapper + "updateById").getBoundSql(resource);
        BoundSql delete = configuration.getMappedStatement(mapper + "deleteById").getBoundSql(7L);
        BoundSql list = configuration.getMappedStatement(mapper + "selectList").getBoundSql(null);

        assertTrue(sql(insert).startsWith("insert into learning_resources "));
        assertEquals("uploaderId", insert.getParameterMappings().stream()
                .filter(mapping -> mapping.getProperty().equals("uploaderId"))
                .findFirst().orElseThrow().getProperty());
        assertTrue(sql(update).startsWith("update learning_resources "));
        assertTrue(sql(update).contains("title = ?"));
        assertTrue(sql(update).contains("description = ?"));
        assertTrue(sql(update).contains("file_type = ?"));
        assertTrue(sql(update).contains("where id = ?"));
        assertTrue(sql(delete).startsWith("delete from learning_resources where id = ?"));
        assertTrue(sql(list).startsWith("select * from learning_resources "));
    }

    @Test
    void categoryCrudStatementsAreBoundToTheCategoryTable() throws Exception {
        Configuration configuration = load("ResourceCategoryMapper");
        String mapper = ResourceCategoryMapper.class.getName() + ".";
        ResourceCategory category = new ResourceCategory();
        category.setId(9L);
        category.setName("synthetic category");

        assertTrue(sql(configuration.getMappedStatement(mapper + "insert")
                .getBoundSql(category)).startsWith("insert into resource_categories "));
        assertTrue(sql(configuration.getMappedStatement(mapper + "updateById")
                .getBoundSql(category)).startsWith("update resource_categories "));
        assertTrue(sql(configuration.getMappedStatement(mapper + "deleteById")
                .getBoundSql(9L)).startsWith("delete from resource_categories where id = ?"));
        assertTrue(sql(configuration.getMappedStatement(mapper + "selectById")
                .getBoundSql(9L)).startsWith("select * from resource_categories where id = ?"));
        assertTrue(sql(configuration.getMappedStatement(mapper + "selectList")
                .getBoundSql(null)).startsWith("select * from resource_categories "));
    }

    @Test
    void resourceSearchOnlyReturnsApprovedRows() throws Exception {
        Configuration configuration = load("LearningResourceMapper");
        String mapper = LearningResourceMapper.class.getName() + ".";
        Map<String, Object> parameters = Map.of(
                "query", "synthetic", "fileType", "pdf", "offset", 0, "limit", 10);

        for (String statement : new String[] {
                "findSimilarResourcesByType", "countSimilarResourcesByType",
                "findSimilarResources", "countSimilarResources"}) {
            String bound = sql(configuration.getMappedStatement(mapper + statement)
                    .getBoundSql(parameters));
            assertTrue(bound.contains("where status = 'approved'"), statement);
        }
    }

    private Configuration load(String name) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/" + name + ".xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String sql(BoundSql boundSql) {
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
