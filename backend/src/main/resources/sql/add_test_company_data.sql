-- 添加测试企业数据
-- 用于修复实习单位显示问题

-- 插入测试企业数据（对应student_internship_status中的company_id）
INSERT INTO company_user (company_name, contact_person, contact_phone, contact_email, address, introduction, status, create_time, update_time) VALUES
('某某科技有限公司', '张经理', '13800138001', 'zhang@company1.com', '北京市海淀区中关村大街1号', '一家专注于人工智能和大数据技术的科技公司', 1, NOW(), NOW()),
('某某网络科技有限公司', '李总监', '13900139002', 'li@company2.com', '上海市浦东新区张江高科技园区2号', '专业从事网络技术和软件开发', 1, NOW(), NOW()),
('某某信息技术有限公司', '王总', '13700137003', 'wang@company3.com', '广州市天河区珠江新城3号', '提供全方位的信息技术服务', 1, NOW(), NOW()),
('某某数据服务有限公司', '赵经理', '13600136004', 'zhao@company4.com', '深圳市南山区科技园4号', '专注于大数据分析和云计算服务', 1, NOW(), NOW());

-- 验证数据
SELECT '企业数据:' as info;
SELECT id, company_name, contact_person, contact_phone FROM company_user ORDER BY id;

-- 验证关联
SELECT '实习状态与企业关联:' as info;
SELECT 
    sis.id as status_id,
    sis.company_id,
    su.name as student_name,
    su.student_user_id,
    c.company_name as company_name
FROM student_internship_status sis
LEFT JOIN student_users su ON sis.student_id = su.id
LEFT JOIN company_user c ON sis.company_id = c.id
ORDER BY sis.id;
