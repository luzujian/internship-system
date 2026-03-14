package com.gdmu.controller;

import com.gdmu.entity.Chat;
import com.gdmu.entity.ChatMessage;
import com.gdmu.entity.ChatMember;
import com.gdmu.entity.Result;
import com.gdmu.service.ChatService;
import com.gdmu.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天控制器
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private ChatMessageService chatMessageService;
    
    /**
     * 创建群聊
     */
    @PostMapping("/group")
    public Result createGroupChat(@RequestBody Map<String, Object> params) {
        Chat chat = chatService.createGroupChat(params);
        return Result.success(chat);
    }
    
    /**
     * 获取或创建单聊会话
     */
    @PostMapping("/single")
    public Result getOrCreateSingleChat(@RequestBody Map<String, Object> params) {
        try {
            System.out.println("收到创建单聊请求，参数: " + params);
            Chat chat = chatService.getOrCreateSingleChat(params);
            if (chat == null) {
                System.out.println("创建会话失败，返回null");
                return Result.error("创建会话失败");
            }
            System.out.println("创建会话成功，chatId: " + chat.getId());
            return Result.success(chat);
        } catch (IllegalArgumentException e) {
            System.out.println("参数错误: " + e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            System.out.println("创建会话异常: " + e.getMessage());
            e.printStackTrace();
            return Result.error("创建会话失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户会话列表
     */
    @GetMapping("/user")
    public Result getUserChats(@RequestParam Long userId, @RequestParam Integer userType) {
        List<Chat> chats = chatService.getUserChats(userId, userType);
        return Result.success(chats);
    }
    
    /**
     * 获取会话详情
     */
    @GetMapping("/{chatId}")
    public Result getChatById(@PathVariable Long chatId) {
        Chat chat = chatService.getChatById(chatId);
        return Result.success(chat);
    }
    
    /**
     * 获取会话成员列表
     */
    @GetMapping("/{chatId}/members")
    public Result getChatMembers(@PathVariable Long chatId) {
        List<ChatMember> members = chatService.getChatMembers(chatId);
        return Result.success(members);
    }
    
    /**
     * 发送消息
     */
    @PostMapping("/message")
    public Result sendMessage(@RequestBody Map<String, Object> params) {
        try {
            System.out.println("收到发送消息请求，参数: " + params);
            ChatMessage message = chatMessageService.sendMessage(params);
            if (message == null) {
                System.out.println("发送消息失败，返回null");
                return Result.error("发送消息失败");
            }
            System.out.println("发送消息成功，messageId: " + message.getId());
            return Result.success(message);
        } catch (Exception e) {
            System.out.println("发送消息异常: " + e.getMessage());
            e.printStackTrace();
            return Result.error("发送消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取会话消息列表
     */
    @GetMapping("/{chatId}/messages")
    public Result getChatMessages(
            @PathVariable Long chatId,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        List<ChatMessage> messages = chatMessageService.getChatMessages(chatId, lastMessageId, pageSize);
        return Result.success(messages);
    }
    
    /**
     * 标记消息为已读
     */
    @PutMapping("/message/{messageId}/read")
    public Result markMessageAsRead(@PathVariable Long messageId) {
        boolean result = chatMessageService.markMessageAsRead(messageId);
        return Result.success(result);
    }
    
    /**
     * 批量标记消息为已读
     */
    @PutMapping("/{chatId}/messages/read")
    public Result batchMarkAsRead(@PathVariable Long chatId, @RequestBody Map<String, Object> params) {
        // 安全转换userId为Long类型
        Object userIdObj = params.get("userId");
        Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
        
        // 安全转换userType为Integer类型
        Object userTypeObj = params.get("userType");
        Integer userType = userTypeObj instanceof Number ? ((Number) userTypeObj).intValue() : null;
        
        // 安全转换lastReadMessageId为Long类型
        Object lastReadMessageIdObj = params.get("lastReadMessageId");
        Long lastReadMessageId = lastReadMessageIdObj instanceof Number ? ((Number) lastReadMessageIdObj).longValue() : null;
        
        boolean result = chatMessageService.batchMarkAsRead(chatId, userId, userType, lastReadMessageId);
        return Result.success(result);
    }
    
    /**
     * 获取未读消息数
     */
    @GetMapping("/unread-count")
    public Result getUnreadCount(@RequestParam Long userId, @RequestParam Integer userType) {
        Integer count = chatMessageService.getUnreadCount(userId, userType);
        return Result.success(count);
    }
    
    /**
     * 添加成员到群聊
     */
    @PostMapping("/{chatId}/members")
    public Result addMemberToChat(@PathVariable Long chatId, @RequestBody Map<String, Object> params) {
        params.put("chatId", chatId);
        boolean result = chatService.addMemberToChat(params);
        return Result.success(result);
    }
    
    /**
     * 从群聊移除成员
     */
    @DeleteMapping("/{chatId}/members")
    public Result removeMemberFromChat(
            @PathVariable Long chatId,
            @RequestParam Long memberId,
            @RequestParam Integer memberType) {
        boolean result = chatService.removeMemberFromChat(chatId, memberId, memberType);
        return Result.success(result);
    }
    
    /**
     * 退出群聊
     */
    @PostMapping("/{chatId}/exit")
    public Result exitChat(@PathVariable Long chatId, @RequestBody Map<String, Object> params) {
        // 安全转换userId为Long类型
        Object userIdObj = params.get("userId");
        Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
        
        // 安全转换userType为Integer类型
        Object userTypeObj = params.get("userType");
        Integer userType = userTypeObj instanceof Number ? ((Number) userTypeObj).intValue() : null;
        
        boolean result = chatService.exitChat(chatId, userId, userType);
        return Result.success(result);
    }
}
