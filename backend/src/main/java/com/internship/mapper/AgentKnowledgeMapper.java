package com.internship.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface AgentKnowledgeMapper {
    @Select("SELECT card_id AS id, category, title, roles, keywords, states, rules, steps, source, verified_at AS verifiedAt, verify_level AS verifyLevel FROM agent_knowledge WHERE deleted = 0 ORDER BY sort_order")
    List<Map<String, Object>> findAllCards();

    @Select("SELECT COUNT(*) FROM agent_knowledge WHERE deleted = 0")
    long count();
}
