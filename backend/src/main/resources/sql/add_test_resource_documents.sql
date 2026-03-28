-- 添加测试资源文档数据
-- 用于验证资源文档管理功能

INSERT INTO resource_documents (title, description, file_url, file_name, file_type, file_size, publisher_id, publisher, publisher_role, publish_time, target_type, status, create_time, update_time) VALUES
('Java 入门教程', 'Java 编程语言基础入门教程，适合零基础学习者', 'https://oss-cn-beijing.aliyuncs.com/lzj-java-ai/resources/doc/2026/03/java-tutorial.pdf', 'java-tutorial.pdf', 'application/pdf', 1024000, 1, '管理员', 'ADMIN', NOW(), 'ALL', 'PUBLISHED', NOW(), NOW()),
('Python 数据分析实战', '使用 Python 进行数据分析的实战教程，包含 Pandas、NumPy 等库的使用', 'https://oss-cn-beijing.aliyuncs.com/lzj-java-ai/resources/doc/2026/03/python-data-analysis.pdf', 'python-data-analysis.pdf', 'application/pdf', 2048000, 1, '管理员', 'ADMIN', NOW(), 'ALL', 'PUBLISHED', NOW(), NOW()),
('前端开发指南', '现代前端开发技术指南，涵盖 HTML、CSS、JavaScript 等内容', 'https://oss-cn-beijing.aliyuncs.com/lzj-java-ai/resources/doc/2026/03/frontend-guide.pdf', 'frontend-guide.pdf', 'application/pdf', 1536000, 1, '管理员', 'ADMIN', NOW(), 'STUDENT', 'PUBLISHED', NOW(), NOW()),
('数据库设计原则', '数据库设计的最佳实践和原则，包括范式理论、索引优化等', 'https://oss-cn-beijing.aliyuncs.com/lzj-java-ai/resources/doc/2026/03/database-design.docx', 'database-design.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 512000, 1, '管理员', 'ADMIN', NOW(), 'ALL', 'PUBLISHED', NOW(), NOW()),
('Git 版本控制教程', 'Git 版本控制工具使用教程，包括分支管理、代码合并等', 'https://oss-cn-beijing.aliyuncs.com/lzj-java-ai/resources/doc/2026/03/git-tutorial.pdf', 'git-tutorial.pdf', 'application/pdf', 768000, 1, '管理员', 'ADMIN', NOW(), 'ALL', 'PUBLISHED', NOW(), NOW());

-- 验证数据
SELECT id, title, status, publisher_role FROM resource_documents ORDER BY id DESC;
