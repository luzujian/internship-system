ALTER TABLE student_users ADD COLUMN phone VARCHAR(20) COMMENT '联系电话' AFTER class_id;
ALTER TABLE student_users ADD COLUMN email VARCHAR(100) COMMENT '联系邮箱' AFTER phone;