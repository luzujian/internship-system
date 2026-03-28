package com.gdmu.anno;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTeacherPermission {
    
    String[] value() default {};
    
    String message() default "您没有权限访问此功能";
}
