package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

/**
 * 聊天成员实体类
 */
@Data
public class ChatMember {
    /**
     * 关联ID
     */
    private Long id;
    
    /**
     * 所属会话ID
     */
    private Long chatId;
    
    /**
     * 成员ID
     */
    private Long memberId;
    
    /**
     * 成员类型：1-学生，2-教师，3-管理员
     */
    private Integer memberType;
    
    /**
     * 成员名称
     */
    private String memberName;
    
    /**
     * 成员头像URL
     */
    private String avatar;
    
    /**
     * 加入时间
     */
    private Date joinTime;
    
    /**
     * 角色：1-普通成员，2-管理员，3-创建者
     */
    private Integer role;
    
    /**
     * 最后读取消息ID
     */
    private Long lastReadMessageId;
    
    /**
     * 未读消息数
     */
    private Integer unreadCount;
    
    /**
     * 状态：0-正常，1-已退出
     */
    private Integer status;
}