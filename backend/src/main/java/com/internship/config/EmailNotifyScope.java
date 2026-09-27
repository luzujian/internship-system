package com.internship.config;

/**
 * 系统邮件提醒的业务范围。
 * 管理员可在「系统参数配置 - 基础配置」中总开关邮件提醒，并按业务勾选启用范围。
 */
public enum EmailNotifyScope {

    /** 企业注册申请审核结果（通过/驳回）通知企业邮箱 */
    COMPANY_AUDIT("企业注册审核结果"),

    /** 学生实习申请审核结果（自主实习/考研延迟，通过/驳回）通知学生邮箱 */
    STUDENT_APPLICATION("学生申请审核结果"),

    /** 岗位申请结果（企业处理学生投递）通知学生邮箱 */
    JOB_APPLICATION("岗位申请结果"),

    /** 实习确认结果通知学生邮箱 */
    INTERNSHIP_CONFIRMATION("实习确认结果");

    private final String label;

    EmailNotifyScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static EmailNotifyScope fromCode(String code) {
        if (code == null) return null;
        for (EmailNotifyScope scope : values()) {
            if (scope.name().equalsIgnoreCase(code.trim())) {
                return scope;
            }
        }
        return null;
    }
}
