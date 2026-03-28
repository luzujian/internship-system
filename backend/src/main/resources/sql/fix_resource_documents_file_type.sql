-- 修复资源文档表file_type字段长度问题
ALTER TABLE `resource_documents` MODIFY COLUMN `file_type` varchar(150) DEFAULT NULL COMMENT '文件类型（MIME类型）';

-- 修复资源文档表publisher_role字段，支持更多发布人身份
ALTER TABLE `resource_documents` MODIFY COLUMN `publisher_role` varchar(50) DEFAULT NULL COMMENT '发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员，TEACHER-教师';
