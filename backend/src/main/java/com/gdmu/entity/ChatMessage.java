package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

/**
 * 聊天消息实体类
 */
@Data
public class ChatMessage {
    /**
     * 消息ID
     */
    private Long id;
    
    /**
     * 所属会话ID
     */
    private Long chatId;
    
    /**
     * 发送者ID
     */
    private Long senderId;
    
    /**
     * 发送者类型：1-学生，2-教师，3-管理员
     */
    private Integer senderType;
    
    /**
     * 发送者名称
     */
    private String senderName;
    
    /**
     * 消息类型：1-文本，2-图片，3-文件
     */
    private Integer type;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 消息状态：1-发送成功，2-发送失败，3-已读
     */
    private Integer status;
    
    /**
     * 发送时间
     */
    private Date sendTime;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 读取时间
     */
    private Date readTime;
    
    /**
     * 是否是自己发送的消息
     */
    private transient Boolean isSelf;
    
    /**
     * 格式化后的时间显示
     */
    private transient String formattedTime;
}