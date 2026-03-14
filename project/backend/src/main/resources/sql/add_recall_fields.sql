-- 为company_users表添加撤回申请相关字段

-- 添加撤回申请状态字段（0-未申请，1-待确认撤回，2-撤回已批准，3-撤回已拒绝）
ALTER TABLE company_users ADD COLUMN recall_status INT DEFAULT 0 COMMENT '撤回申请状态：0-未申请，1-待确认撤回，2-撤回已批准，3-撤回已拒绝' AFTER audit_remark;

-- 添加撤回申请原因字段
ALTER TABLE company_users ADD COLUMN recall_reason VARCHAR(500) COMMENT '撤回申请原因' AFTER recall_status;

-- 添加撤回申请时间字段
ALTER TABLE company_users ADD COLUMN recall_apply_time DATETIME COMMENT '撤回申请时间' AFTER recall_reason;

-- 添加撤回审核时间字段
ALTER TABLE company_users ADD COLUMN recall_audit_time DATETIME COMMENT '撤回审核时间' AFTER recall_apply_time;

-- 添加撤回审核人ID字段
ALTER TABLE company_users ADD COLUMN recall_reviewer_id BIGINT COMMENT '撤回审核人ID' AFTER recall_audit_time;

-- 添加撤回审核备注字段
ALTER TABLE company_users ADD COLUMN recall_audit_remark VARCHAR(500) COMMENT '撤回审核备注' AFTER recall_reviewer_id;
