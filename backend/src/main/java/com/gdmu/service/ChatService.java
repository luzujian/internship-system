package com.gdmu.service;

import com.gdmu.entity.Chat;
import com.gdmu.entity.ChatMember;

import java.util.List;
import java.util.Map;

/**
 * 聊天会话Service接口
 */
public interface ChatService {
    /**
     * 创建群聊
     */
    Chat createGroupChat(Map<String, Object> params);
    
    /**
     * 获取单聊会话（不存在则创建）
     */
    Chat getOrCreateSingleChat(Map<String, Object> params);
    
    /**
     * 查询用户的所有会话
     */
    List<Chat> getUserChats(Long userId, Integer userType);
    
    /**
     * 根据ID查询会话
     */
    Chat getChatById(Long chatId);
    
    /**
     * 添加成员到群聊
     */
    boolean addMemberToChat(Map<String, Object> params);
    
    /**
     * 从群聊移除成员
     */
    boolean removeMemberFromChat(Long chatId, Long memberId, Integer memberType);
    
    /**
     * 退出群聊
     */
    boolean exitChat(Long chatId, Long memberId, Integer memberType);
    
    /**
     * 更新会话信息
     */
    boolean updateChat(Chat chat);
    
    /**
     * 获取会话成员列表
     */
    List<ChatMember> getChatMembers(Long chatId);
}
