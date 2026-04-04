package com.gdmu.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Integer code; // 状态码 200成功 400失败
    private String message; // 提示信息
    private Object data; // 数据

    // 成功响应
    public static Result success(Object data) {
        return new Result(200, "success", data);
    }

    // 成功响应（无数据）
    public static Result success() {
        return new Result(200, "success", null);
    }
    
    // 成功响应（自定义消息和数据）
    public static Result success(String message, Object data) {
        return new Result(200, message, data);
    }

    // 失败响应
    public static Result error(String message) {
        return new Result(400, message, null);
    }
}