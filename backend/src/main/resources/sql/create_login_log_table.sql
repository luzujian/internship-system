-- 创建登录日志表
CREATE TABLE IF NOT EXISTS `login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID，自增主键',
  `user_id` varchar(255) DEFAULT NULL COMMENT '用户ID',
  `user_type` varchar(50) DEFAULT NULL COMMENT '用户类型',
  `user_name` varchar(255) DEFAULT NULL COMMENT '用户名',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  `ip_address` varchar(255) DEFAULT NULL COMMENT 'IP地址',
  `device_info` text COMMENT '设备信息',
  `login_status` varchar(50) DEFAULT 'SUCCESS' COMMENT '登录状态',
  PRIMARY KEY (`id`),
  KEY `idx_login_time` (`login_time`),
  KEY `idx_user_type` (`user_type`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- 插入测试数据（最近7天的登录日志）
INSERT INTO `login_log` (`user_id`, `user_type`, `user_name`, `login_time`, `ip_address`, `device_info`, `login_status`) VALUES
-- 学生登录数据
('student001', 'STUDENT', '张三', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student002', 'STUDENT', '李四', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student003', 'STUDENT', '王五', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.102', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student001', 'STUDENT', '张三', DATE_SUB(NOW(), INTERVAL 5 DAY), '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student004', 'STUDENT', '赵六', DATE_SUB(NOW(), INTERVAL 5 DAY), '192.168.1.103', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student002', 'STUDENT', '李四', DATE_SUB(NOW(), INTERVAL 4 DAY), '192.168.1.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student005', 'STUDENT', '钱七', DATE_SUB(NOW(), INTERVAL 4 DAY), '192.168.1.104', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student003', 'STUDENT', '王五', DATE_SUB(NOW(), INTERVAL 3 DAY), '192.168.1.102', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student006', 'STUDENT', '孙八', DATE_SUB(NOW(), INTERVAL 3 DAY), '192.168.1.105', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student001', 'STUDENT', '张三', DATE_SUB(NOW(), INTERVAL 2 DAY), '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student004', 'STUDENT', '赵六', DATE_SUB(NOW(), INTERVAL 2 DAY), '192.168.1.103', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student007', 'STUDENT', '周九', DATE_SUB(NOW(), INTERVAL 2 DAY), '192.168.1.106', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student002', 'STUDENT', '李四', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.1.101', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student005', 'STUDENT', '钱七', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.1.104', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student008', 'STUDENT', '吴十', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.1.107', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student001', 'STUDENT', '张三', NOW(), '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student003', 'STUDENT', '王五', NOW(), '192.168.1.102', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student006', 'STUDENT', '孙八', NOW(), '192.168.1.105', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student009', 'STUDENT', '郑十一', NOW(), '192.168.1.108', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('student010', 'STUDENT', '王十二', NOW(), '192.168.1.109', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),

-- 教师登录数据
('teacher001', 'TEACHER', '刘老师', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.200', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher002', 'TEACHER', '陈老师', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.201', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher001', 'TEACHER', '刘老师', DATE_SUB(NOW(), INTERVAL 5 DAY), '192.168.1.200', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher003', 'TEACHER', '杨老师', DATE_SUB(NOW(), INTERVAL 4 DAY), '192.168.1.202', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher002', 'TEACHER', '陈老师', DATE_SUB(NOW(), INTERVAL 3 DAY), '192.168.1.201', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher001', 'TEACHER', '刘老师', DATE_SUB(NOW(), INTERVAL 2 DAY), '192.168.1.200', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher004', 'TEACHER', '黄老师', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.1.203', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher002', 'TEACHER', '陈老师', NOW(), '192.168.1.201', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher003', 'TEACHER', '杨老师', NOW(), '192.168.1.202', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('teacher005', 'TEACHER', '赵老师', NOW(), '192.168.1.204', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),

-- 管理员登录数据
('admin001', 'ADMIN', '管理员A', DATE_SUB(NOW(), INTERVAL 6 DAY), '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin002', 'ADMIN', '管理员B', DATE_SUB(NOW(), INTERVAL 5 DAY), '192.168.1.11', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin001', 'ADMIN', '管理员A', DATE_SUB(NOW(), INTERVAL 4 DAY), '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin001', 'ADMIN', '管理员A', DATE_SUB(NOW(), INTERVAL 3 DAY), '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin002', 'ADMIN', '管理员B', DATE_SUB(NOW(), INTERVAL 2 DAY), '192.168.1.11', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin001', 'ADMIN', '管理员A', DATE_SUB(NOW(), INTERVAL 1 DAY), '192.168.1.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin002', 'ADMIN', '管理员B', NOW(), '192.168.1.11', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS'),
('admin003', 'ADMIN', '管理员C', NOW(), '192.168.1.12', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'SUCCESS');
