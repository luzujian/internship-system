package com.gdmu.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    /**
     * 操作类型
     * @return 操作类型（如LOGIN, LOGOUT, ADD, UPDATE, DELETE, QUERY等）
     */
    String operationType() default "UPDATE";
    
    /**
     * 操作模块
     * @return 操作模块（如USER_MANAGEMENT, COURSE_MANAGEMENT等）
     */
    String module() default "SYSTEM_SETTINGS";
    
    /**
     * 操作描述
     * @return 操作描述
     */
    String description() default "执行了操作";
}