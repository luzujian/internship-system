-- 为company_users表添加审核相关字段

-- 添加手机号字段
ALTER TABLE company_users ADD COLUMN phone VARCHAR(20) COMMENT '手机号' AFTER contact_email;

-- 添加营业执照图片路径字段
ALTER TABLE company_users ADD COLUMN business_license VARCHAR(500) COMMENT '营业执照图片路径' AFTER introduction;

-- 添加法人身份证图片路径字段
ALTER TABLE company_users ADD COLUMN legal_id_card VARCHAR(500) COMMENT '法人身份证图片路径' AFTER business_license;

-- 添加申请时间字段
ALTER TABLE company_users ADD COLUMN apply_time DATETIME COMMENT '申请时间' AFTER legal_id_card;

-- 添加审核时间字段
ALTER TABLE company_users ADD COLUMN audit_time DATETIME COMMENT '审核时间' AFTER apply_time;

-- 添加审核状态字段（0-待审核，1-已通过，2-已拒绝）
ALTER TABLE company_users ADD COLUMN audit_status INT DEFAULT 0 COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝' AFTER audit_time;

-- 添加审核备注字段（拒绝原因）
ALTER TABLE company_users ADD COLUMN audit_remark VARCHAR(500) COMMENT '审核备注（拒绝原因）' AFTER audit_status;

-- 添加审核人ID字段
ALTER TABLE company_users ADD COLUMN reviewer_id BIGINT COMMENT '审核人ID' AFTER audit_remark;

-- 修改status字段，添加更多状态值（0-待审核，1-正常，2-已禁用，3-已拒绝）
-- 注意：如果status字段已有数据，需要先更新数据
ALTER TABLE company_users MODIFY COLUMN status INT DEFAULT 0 COMMENT '状态：0-待审核，1-正常，2-已禁用，3-已拒绝';
