package com.gdmu.service;

import com.gdmu.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 聊天消息Service接口
 */
public interface ChatMessageService {
    /**
     * 发送消息
     */
    ChatMessage sendMessage(Map<String, Object> params);
    
    /**
     * 获取会话消息列表
     */
    List<ChatMessage> getChatMessages(Long chatId, Long lastMessageId, Integer pageSize);
    
    /**
     * 标记消息为已读
     */
    boolean markMessageAsRead(Long messageId);
    
    /**
     * 批量标记消息为已读
     */
    boolean batchMarkAsRead(Long chatId, Long userId, Integer userType, Long lastReadMessageId);
    
    /**
     * 获取用户未读消息总数
     */
    Integer getUnreadCount(Long userId, Integer userType);
}
