package com.gdmu.service.impl;

import com.gdmu.entity.Chat;
import com.gdmu.entity.ChatMember;
import com.gdmu.mapper.ChatMapper;
import com.gdmu.mapper.ChatMemberMapper;
import com.gdmu.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话Service实现类
 */
@Service
@Transactional
public class ChatServiceImpl implements ChatService {
    
    @Autowired
    private ChatMapper chatMapper;
    
    @Autowired
    private ChatMemberMapper chatMemberMapper;
    
    @Override
    public Chat createGroupChat(Map<String, Object> params) {
        // 创建会话
        Chat chat = new Chat();
        chat.setName((String) params.get("name"));
        chat.setType(2); // 群聊
        chat.setAvatar((String) params.get("avatar"));
        // 安全转换creatorId为Long类型
        Object creatorIdObj = params.get("creatorId");
        Long creatorId = creatorIdObj instanceof Number ? ((Number) creatorIdObj).longValue() : null;
        chat.setCreatorId(creatorId);
        chat.setCreatorType((Integer) params.get("creatorType"));
        chat.setCreateTime(new Date());
        chat.setUpdateTime(new Date());
        chat.setStatus(0);
        chatMapper.insert(chat);
        
        // 添加创建者为成员
        List<Map<String, Object>> members = (List<Map<String, Object>>) params.get("members");
        if (members != null) {
            for (Map<String, Object> member : members) {
                ChatMember chatMember = new ChatMember();
                chatMember.setChatId(chat.getId());
                // 安全转换memberId为Long类型
                Object memberIdObj = member.get("memberId");
                Long memberId = memberIdObj instanceof Number ? ((Number) memberIdObj).longValue() : null;
                chatMember.setMemberId(memberId);
                chatMember.setMemberType((Integer) member.get("memberType"));
                chatMember.setMemberName((String) member.get("memberName"));
                chatMember.setAvatar((String) member.get("avatar"));
                chatMember.setJoinTime(new Date());
                chatMember.setRole((Integer) member.get("role"));
                chatMember.setLastReadMessageId(0L);
                chatMember.setUnreadCount(0);
                chatMember.setStatus(0);
                chatMemberMapper.insert(chatMember);
            }
        }
        
        return chat;
    }
    
    @Override
    public Chat getOrCreateSingleChat(Map<String, Object> params) {
        // 参数非空检查
        if (params == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        
        System.out.println("getOrCreateSingleChat 收到参数: " + params);
        
        // 安全转换所有参数
        Object userId1Obj = params.get("userId1");
        Long userId1 = userId1Obj instanceof Number ? ((Number) userId1Obj).longValue() : null;
        Object userType1Obj = params.get("userType1");
        Integer userType1 = userType1Obj instanceof Number ? ((Number) userType1Obj).intValue() : null;
        String userName1 = (String) params.get("userName1");
        String userAvatar1 = (String) params.get("userAvatar1");
        
        Object userId2Obj = params.get("userId2");
        Long userId2 = userId2Obj instanceof Number ? ((Number) userId2Obj).longValue() : null;
        Object userType2Obj = params.get("userType2");
        Integer userType2 = userType2Obj instanceof Number ? ((Number) userType2Obj).intValue() : null;
        String userName2 = (String) params.get("userName2");
        String userAvatar2 = (String) params.get("userAvatar2");
        
        System.out.println("解析后参数: userId1=" + userId1 + ", userType1=" + userType1 + 
            ", userName1=" + userName1 + ", userId2=" + userId2 + ", userType2=" + userType2 + ", userName2=" + userName2);
        
        // 检查必填参数
        if (userId1 == null || userType1 == null || userName1 == null || userId2 == null || userType2 == null || userName2 == null) {
            throw new IllegalArgumentException("缺少必要的参数: userId1=" + userId1 + ", userType1=" + userType1 + 
                ", userName1=" + userName1 + ", userId2=" + userId2 + ", userType2=" + userType2 + ", userName2=" + userName2);
        }
        
        // 构建查询参数，确保类型正确
        Map<String, Object> queryParams = new java.util.HashMap<>();
        queryParams.put("userId1", userId1);
        queryParams.put("userType1", userType1);
        queryParams.put("userId2", userId2);
        queryParams.put("userType2", userType2);
        
        // 查询是否存在单聊会话
        Chat chat = chatMapper.findSingleChat(queryParams);
        if (chat != null) {
            System.out.println("找到已存在的会话: chatId=" + chat.getId());
            return chat;
        }
        
        System.out.println("创建新会话...");
        
        // 不存在则创建
        chat = new Chat();
        chat.setType(1); // 单聊
        chat.setCreatorId(userId1); // 设置创建者ID为发起者
        chat.setCreatorType(userType1); // 设置创建者类型
        chat.setCreateTime(new Date());
        chat.setUpdateTime(new Date());
        chat.setStatus(0);
        chatMapper.insert(chat);
        
        System.out.println("会话创建成功: chatId=" + chat.getId());
        
        // 添加用户1
        ChatMember member1 = new ChatMember();
        member1.setChatId(chat.getId());
        member1.setMemberId(userId1);
        member1.setMemberType(userType1);
        member1.setMemberName(userName1);
        member1.setAvatar(userAvatar1); // 允许头像为null
        member1.setJoinTime(new Date());
        member1.setRole(1); // 普通成员
        member1.setLastReadMessageId(0L);
        member1.setUnreadCount(0);
        member1.setStatus(0);
        chatMemberMapper.insert(member1);
        
        // 添加用户2
        ChatMember member2 = new ChatMember();
        member2.setChatId(chat.getId());
        member2.setMemberId(userId2);
        member2.setMemberType(userType2);
        member2.setMemberName(userName2);
        member2.setAvatar(userAvatar2); // 允许头像为null
        member2.setJoinTime(new Date());
        member2.setRole(1); // 普通成员
        member2.setLastReadMessageId(0L);
        member2.setUnreadCount(0);
        member2.setStatus(0);
        chatMemberMapper.insert(member2);
        
        System.out.println("会话成员添加完成");
        
        return chat;
    }
    
    @Override
    public List<Chat> getUserChats(Long userId, Integer userType) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("userId", userId);
        params.put("userType", userType);
        return chatMapper.findByUserId(params);
    }
    
    @Override
    public Chat getChatById(Long chatId) {
        return chatMapper.findById(chatId);
    }
    
    @Override
    public boolean addMemberToChat(Map<String, Object> params) {
        ChatMember chatMember = new ChatMember();
        
        // 安全转换chatId为Long类型
        Object chatIdObj = params.get("chatId");
        Long chatId = chatIdObj instanceof Number ? ((Number) chatIdObj).longValue() : null;
        chatMember.setChatId(chatId);
        
        // 安全转换memberId为Long类型
        Object memberIdObj = params.get("memberId");
        Long memberId = memberIdObj instanceof Number ? ((Number) memberIdObj).longValue() : null;
        chatMember.setMemberId(memberId);
        
        chatMember.setMemberType((Integer) params.get("memberType"));
        chatMember.setMemberName((String) params.get("memberName"));
        chatMember.setAvatar((String) params.get("avatar"));
        chatMember.setJoinTime(new Date());
        chatMember.setRole((Integer) params.get("role"));
        chatMember.setLastReadMessageId(0L);
        chatMember.setUnreadCount(0);
        chatMember.setStatus(0);
        
        return chatMemberMapper.insert(chatMember) > 0;
    }
    
    @Override
    public boolean removeMemberFromChat(Long chatId, Long memberId, Integer memberType) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("chatId", chatId);
        params.put("memberId", memberId);
        params.put("memberType", memberType);
        return chatMemberMapper.exitChat(params) > 0;
    }
    
    @Override
    public boolean exitChat(Long chatId, Long memberId, Integer memberType) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("chatId", chatId);
        params.put("memberId", memberId);
        params.put("memberType", memberType);
        return chatMemberMapper.exitChat(params) > 0;
    }
    
    @Override
    public boolean updateChat(Chat chat) {
        chat.setUpdateTime(new Date());
        return chatMapper.update(chat) > 0;
    }
    
    @Override
    public List<ChatMember> getChatMembers(Long chatId) {
        return chatMemberMapper.findByChatId(chatId);
    }
}
