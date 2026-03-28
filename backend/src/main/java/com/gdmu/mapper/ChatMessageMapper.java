package com.gdmu.mapper;

import com.gdmu.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息Mapper接口
 */
@Mapper
public interface ChatMessageMapper {
    /**
     * 发送消息
     */
    int insert(ChatMessage message);
    
    /**
     * 更新消息状态
     */
    int updateStatus(Map<String, Object> params);
    
    /**
     * 根据ID查询消息
     */
    ChatMessage findById(Long id);
    
    /**
     * 查询会话消息列表
     */
    List<ChatMessage> findByChatId(Map<String, Object> params);
    
    /**
     * 查询用户未读消息数
     */
    Integer countUnread(Map<String, Object> params);
    
    /**
     * 批量更新消息状态为已读
     */
    int batchUpdateReadStatus(Map<String, Object> params);
}
