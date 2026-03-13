package com.gdmu.mapper;

import com.gdmu.entity.Chat;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 聊天会话Mapper接口
 */
@Mapper
public interface ChatMapper {
    /**
     * 创建会话
     */
    int insert(Chat chat);
    
    /**
     * 更新会话
     */
    int update(Chat chat);
    
    /**
     * 根据ID查询会话
     */
    Chat findById(Long id);
    
    /**
     * 查询用户的所有会话
     */
    List<Chat> findByUserId(Map<String, Object> params);
    
    /**
     * 查询单聊会话（根据两个用户ID）
     */
    Chat findSingleChat(Map<String, Object> params);
    
    /**
     * 更新会话最后一条消息
     */
    int updateLastMessage(Map<String, Object> params);
}
