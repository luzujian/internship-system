package com.internship.anno;

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

    /**
     * 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
     * 关键操作（权限调整、密码重置、批量删除、备份恢复、日志留存）应至少标注 MEDIUM
     */
    String riskLevel() default "LOW";

    /**
     * 目标对象类型（如 USER, STUDENT, POSITION, ANNOUNCEMENT 等）
     * 用于审计详情展示目标对象
     */
    String targetType() default "";

    /**
     * 是否关键操作（权限、备份、日志留存等）
     * 关键操作审计写入失败时会阻止业务执行
     */
    boolean critical() default false;
}
