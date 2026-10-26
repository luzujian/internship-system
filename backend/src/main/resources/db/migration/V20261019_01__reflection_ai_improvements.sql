-- 管理员「辣子鸡」已确认本次单列迁移；业务账号和应用启动均不执行 DDL。
-- 发布工具先查询 information_schema.columns：不存在才执行；已存在且为可空 TEXT 则跳过，其他定义停止。
-- 保留历史记录 NULL；应用回退时保留此兼容列，不执行 DROP COLUMN。
ALTER TABLE internship_reflection
    ADD COLUMN ai_improvements TEXT NULL COMMENT 'AI改进建议';
