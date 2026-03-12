package com.gdmu.mapper;

import com.gdmu.entity.ChatMember;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 聊天成员Mapper接口
 */
@Mapper
public interface ChatMemberMapper {
    /**
     * 添加成员
     */
    int insert(ChatMember chatMember);
    
    /**
     * 更新成员信息
     */
    int update(ChatMember chatMember);
    
    /**
     * 根据ID查询成员
     */
    ChatMember findById(Long id);
    
    /**
     * 查询会话成员列表
     */
    List<ChatMember> findByChatId(Long chatId);
    
    /**
     * 查询用户所在的会话
     */
    List<ChatMember> findByMemberId(Map<String, Object> params);
    
    /**
     * 查询指定会话中的指定成员
     */
    ChatMember findByChatIdAndMemberId(Map<String, Object> params);
    
    /**
     * 退出会话
     */
    int exitChat(Map<String, Object> params);
    
    /**
     * 更新成员未读消息数
     */
    int updateUnreadCount(Map<String, Object> params);
    
    /**
     * 更新成员最后读取消息ID
     */
    int updateLastReadMessageId(Map<String, Object> params);
}
