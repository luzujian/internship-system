-- 仅在管理员「辣子鸡」确认后执行；业务账号 user 不得执行 DDL。
-- 本次增加可空邮箱字段，不修改既有管理员资料。
ALTER TABLE admin_users
    ADD COLUMN email VARCHAR(254) NULL COMMENT '管理员接收问题反馈提醒的邮箱' AFTER phone;
