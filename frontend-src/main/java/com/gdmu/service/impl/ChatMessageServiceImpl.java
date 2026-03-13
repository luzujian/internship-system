package com.gdmu.service.impl;

import com.gdmu.entity.ChatMessage;
import com.gdmu.mapper.ChatMapper;
import com.gdmu.mapper.ChatMessageMapper;
import com.gdmu.mapper.ChatMemberMapper;
import com.gdmu.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息Service实现类
 */
@Service
@Transactional
public class ChatMessageServiceImpl implements ChatMessageService {
    
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private ChatMapper chatMapper;
    
    @Autowired
    private ChatMemberMapper chatMemberMapper;
    
    @Override
    public ChatMessage sendMessage(Map<String, Object> params) {
        // 创建消息
        ChatMessage message = new ChatMessage();
        
        // 安全转换chatId为Long类型
        Object chatIdObj = params.get("chatId");
        Long chatId = chatIdObj instanceof Number ? ((Number) chatIdObj).longValue() : null;
        message.setChatId(chatId);
        
        // 安全转换senderId为Long类型
        Object senderIdObj = params.get("senderId");
        Long senderId = senderIdObj instanceof Number ? ((Number) senderIdObj).longValue() : null;
        message.setSenderId(senderId);
        
        // 安全转换senderType为Integer类型
        Object senderTypeObj = params.get("senderType");
        Integer senderType = senderTypeObj instanceof Number ? ((Number) senderTypeObj).intValue() : null;
        message.setSenderType(senderType);
        
        message.setSenderName((String) params.get("senderName"));
        
        // 安全转换type为Integer类型
        Object typeObj = params.get("type");
        Integer type = typeObj instanceof Number ? ((Number) typeObj).intValue() : 1;
        message.setType(type);
        
        message.setContent((String) params.get("content"));
        message.setStatus(1); // 发送成功
        message.setSendTime(new Date());
        message.setCreateTime(new Date());
        chatMessageMapper.insert(message);
        
        // 更新会话最后一条消息
        Map<String, Object> updateParams = new java.util.HashMap<>();
        updateParams.put("chatId", message.getChatId());
        updateParams.put("lastMessageId", message.getId());
        updateParams.put("lastMessageContent", message.getContent());
        updateParams.put("lastMessageTime", message.getSendTime());
        chatMapper.updateLastMessage(updateParams);
        
        return message;
    }
    
    @Override
    public List<ChatMessage> getChatMessages(Long chatId, Long lastMessageId, Integer pageSize) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("chatId", chatId);
        params.put("lastMessageId", lastMessageId); // 可以为null
        params.put("pageSize", pageSize != null ? pageSize : 20);
        return chatMessageMapper.findByChatId(params);
    }
    
    @Override
    public boolean markMessageAsRead(Long messageId) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", messageId);
        params.put("status", 3); // 已读
        params.put("readTime", new Date());
        return chatMessageMapper.updateStatus(params) > 0;
    }
    
    @Override
    public boolean batchMarkAsRead(Long chatId, Long userId, Integer userType, Long lastReadMessageId) {
        // 更新消息状态为已读
        Map<String, Object> messageParams = new java.util.HashMap<>();
        messageParams.put("chatId", chatId);
        messageParams.put("userId", userId);
        messageParams.put("lastReadMessageId", lastReadMessageId);
        messageParams.put("readTime", new Date());
        chatMessageMapper.batchUpdateReadStatus(messageParams);
        
        // 更新成员的最后读取消息ID
        Map<String, Object> memberParams = new java.util.HashMap<>();
        memberParams.put("chatId", chatId);
        memberParams.put("memberId", userId);
        memberParams.put("memberType", userType);
        memberParams.put("lastReadMessageId", lastReadMessageId);
        chatMemberMapper.updateLastReadMessageId(memberParams);
        
        return true;
    }
    
    @Override
    public Integer getUnreadCount(Long userId, Integer userType) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("userId", userId);
        params.put("userType", userType);
        return chatMessageMapper.countUnread(params);
    }
}
