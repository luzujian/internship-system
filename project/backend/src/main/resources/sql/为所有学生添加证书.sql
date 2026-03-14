-- 为所有申请岗位的学生添加获奖证书
-- 这个脚本会为所有已经申请岗位的学生添加2个证书

-- 为所有申请岗位的学生添加第一个证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time)
SELECT 
    ia.student_id,
    CONCAT(su.name, '_英语六级证书.jpg') AS file_name,
    '证书' AS file_type,
    'https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800' AS file_url,
    NOW() AS upload_time,
    NOW() AS create_time,
    NOW() AS update_time
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE NOT EXISTS (
    SELECT 1 FROM student_archives sa 
    WHERE sa.student_id = ia.student_id AND sa.file_name LIKE '%英语六级证书%'
)
GROUP BY ia.student_id;

-- 为所有申请岗位的学生添加第二个证书
INSERT INTO student_archives (student_id, file_name, file_type, file_url, upload_time, create_time, update_time)
SELECT 
    ia.student_id,
    CONCAT(su.name, '_计算机二级证书.jpg') AS file_name,
    '证书' AS file_type,
    'https://images.unsplash.com/photo-1557683316-973673baf926?w=800' AS file_url,
    NOW() AS upload_time,
    NOW() AS create_time,
    NOW() AS update_time
FROM internship_application ia
LEFT JOIN student_users su ON ia.student_id = su.id
WHERE NOT EXISTS (
    SELECT 1 FROM student_archives sa 
    WHERE sa.student_id = ia.student_id AND sa.file_name LIKE '%计算机二级证书%'
)
GROUP BY ia.student_id;

-- 验证添加的数据
SELECT 
    sa.student_id,
    su.name AS student_name,
    COUNT(*) AS file_count,
    SUM(CASE WHEN sa.file_type = '简历' THEN 1 ELSE 0 END) AS resume_count,
    SUM(CASE WHEN sa.file_type = '证书' THEN 1 ELSE 0 END) AS certificate_count
FROM student_archives sa
LEFT JOIN student_users su ON sa.student_id = su.id
WHERE sa.student_id IN (
    SELECT DISTINCT student_id FROM internship_application
)
GROUP BY sa.student_id, su.name
ORDER BY sa.student_id;

-- 显示添加成功信息
SELECT '已为所有申请岗位的学生添加了简历和证书！' AS message;
