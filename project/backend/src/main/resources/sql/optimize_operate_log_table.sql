-- 数据库优化脚本
-- 用于优化operate_log表的性能和数据完整性

-- 1. 检查并添加缺失的索引
-- 已有索引：PRIMARY, idx_ip_address, idx_operation_result, idx_operate_time, idx_operator_role

-- 2. 添加复合索引以提高查询性能
-- 用于按角色和时间范围查询
CREATE INDEX IF NOT EXISTS idx_role_time ON operate_log(operator_role, operate_time DESC);

-- 用于按操作类型和时间范围查询
CREATE INDEX IF NOT EXISTS idx_type_time ON operate_log(operation_type, operate_time DESC);

-- 用于按模块和时间范围查询
CREATE INDEX IF NOT EXISTS idx_module_time ON operate_log(module, operate_time DESC);

-- 用于按操作结果和时间范围查询
CREATE INDEX IF NOT EXISTS idx_result_time ON operate_log(operation_result, operate_time DESC);

-- 3. 数据统计查询
-- 查看各角色日志分布
SELECT 
    operator_role,
    COUNT(*) as count,
    MIN(operate_time) as first_log_time,
    MAX(operate_time) as last_log_time
FROM operate_log
WHERE operator_role IS NOT NULL
GROUP BY operator_role
ORDER BY count DESC;

-- 查看操作类型分布
SELECT 
    operation_type,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM operate_log), 2) as percentage
FROM operate_log
GROUP BY operation_type
ORDER BY count DESC;

-- 查看模块使用频率
SELECT 
    module,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM operate_log), 2) as percentage
FROM operate_log
WHERE module IS NOT NULL
GROUP BY module
ORDER BY count DESC
LIMIT 10;

-- 4. 清理建议
-- 根据当前数据量，建议的清理策略
-- 学生日志：500条（保留100条）
-- 教师日志：300条（保留80条）
-- 管理员日志：200条（保留50条）
-- 企业日志：300条（保留80条）

-- 5. 数据完整性检查
-- 检查是否有NULL值的operator_role
SELECT 
    COUNT(*) as null_role_count,
    COUNT(DISTINCT operator_username) as unique_users
FROM operate_log
WHERE operator_role IS NULL;

-- 6. 性能优化建议
-- 定期清理旧日志
-- 定期分析慢查询
-- 定期优化表
OPTIMIZE TABLE operate_log;