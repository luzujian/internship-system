-- 修复公告表的 target_type 字段类型
-- 问题：原字段为 ENUM 类型，无法存储 JSON 数组
-- 解决：将字段类型修改为 VARCHAR(500)，支持存储 JSON 数组

-- 修改字段类型
ALTER TABLE announcements
MODIFY COLUMN target_type VARCHAR(500)
COMMENT '目标类型：JSON 数组，如 ["ALL","STUDENT"]。支持：STUDENT-全体学生，TEACHER-全体教师，TEACHER_TYPE-特定教师类别，MAJOR-特定专业学生，ALL-全体师生，COMPANY-企业用户';

-- 验证修改
DESC announcements;
