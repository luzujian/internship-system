-- 查询student_id为7的学生信息
SELECT 
    id,
    name,
    student_user_id
FROM student_users
WHERE id = 7;

-- 查询student_id为7的档案记录
SELECT 
    id,
    student_id,
    file_name,
    file_type,
    file_url
FROM student_archives
WHERE student_id = 7;

-- 查询赵强的实际student_id
SELECT 
    id,
    name,
    student_user_id
FROM student_users
WHERE name = '赵强' OR student_user_id = '20200007';

-- 查询赵强的档案记录
SELECT 
    id,
    student_id,
    file_name,
    file_type,
    file_url
FROM student_archives
WHERE student_id IN (
    SELECT id FROM student_users 
    WHERE name = '赵强' OR student_user_id = '20200007'
);
