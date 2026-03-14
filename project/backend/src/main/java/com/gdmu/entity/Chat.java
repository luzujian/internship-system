package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

/**
 * 聊天会话实体类
 * 支持单聊和群聊
 */
@Data
public class Chat {
    /**
     * 会话ID
     */
    private Long id;
    
    /**
     * 会话名称（群聊使用，单聊自动生成）
     */
    private String name;
    
    /**
     * 会话类型：1-单聊，2-群聊
     */
    private Integer type;
    
    /**
     * 群聊头像URL（可选）
     */
    private String avatar;
    
    /**
     * 创建者ID
     */
    private Long creatorId;
    
    /**
     * 创建者类型：1-学生，2-教师，3-管理员
     */
    private Integer creatorType;
    
    /**
     * 最后一条消息ID
     */
    private Long lastMessageId;
    
    /**
     * 最后一条消息内容
     */
    private String lastMessageContent;
    
    /**
     * 最后一条消息时间
     */
    private Date lastMessageTime;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
    
    /**
     * 状态：0-正常，1-解散
     */
    private Integer status;
}