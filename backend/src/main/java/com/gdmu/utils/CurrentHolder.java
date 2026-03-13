package com.gdmu.utils;

import com.gdmu.entity.User;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户持有类，用于在请求线程中存储当前登录用户信息
 */
@Component
public class CurrentHolder {
    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前登录用户
     */
    public static void setUser(User user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前登录用户
     */
    public static User getUser() {
        return USER_HOLDER.get();
    }

    /**
     * 清除当前线程的用户信息
     */
    public static void remove() {
        USER_HOLDER.remove();
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getUserId() {
        User user = getUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前登录用户角色
     */
    public static String getUserRole() {
        User user = getUser();
        return user != null ? user.getRole() : null;
    }
}