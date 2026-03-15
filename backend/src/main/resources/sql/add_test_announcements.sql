-- 添加测试公告数据
-- 用于验证公告管理和首页公告列表功能

-- 更新现有公告状态为已发布
UPDATE announcements SET status = 'PUBLISHED', valid_to = '2027-12-31 23:59:59' WHERE id IN (90, 91, 92);

-- 更新第 151 条为已发布
UPDATE announcements SET status = 'PUBLISHED', valid_to = '2027-12-31 23:59:59' WHERE id = 151;

-- 插入新的测试公告
INSERT INTO announcements (title, content, publisher, publisher_role, publish_time, valid_from, valid_to, target_type, priority, status, create_time, update_time) VALUES
('关于 2026 年寒假放假安排的通知', '根据学校校历安排，现将 2026 年寒假放假有关事项通知如下：一、放假时间：2026 年 1 月 20 日至 2 月 28 日；二、各年级学生按照教务处统一安排，按时返校；三、留校学生需向辅导员提出申请，经批准后方可留校。', '教务处', 'ADMIN', NOW(), NOW(), '2027-12-31 23:59:59', 'ALL', 'important', 'PUBLISHED', NOW(), NOW()),
('2026 届毕业生就业双选会通知', '学校定于 2026 年 3 月 20 日举办 2026 届毕业生就业双选会，届时将有超过 500 家企业参会。请各学院组织学生积极参加。', '就业指导中心', 'ADMIN', NOW(), NOW(), '2027-12-31 23:59:59', 'STUDENT', 'important', 'PUBLISHED', NOW(), NOW()),
('关于开展 2025-2026 学年第一学期期末考试工作的通知', '根据教学工作安排，现将期末考试有关事项通知如下：一、考试时间：第 18-19 周；二、各课程考核方式按照教学计划执行；三、严格考风考纪，杜绝作弊行为。', '教务处', 'ADMIN', NOW(), NOW(), '2027-12-31 23:59:59', 'ALL', 'normal', 'PUBLISHED', NOW(), NOW()),
('计算机学院关于举办程序设计大赛的通知', '为激发学生编程兴趣，提高编程能力，计算机学院决定举办第十届程序设计大赛。参赛对象：全体在校学生。报名时间：即日起至 2026 年 4 月 10 日。', '计算机学院', 'ADMIN', NOW(), NOW(), '2027-12-31 23:59:59', 'STUDENT', 'normal', 'PUBLISHED', NOW(), NOW()),
('2026 年清明节放假安排', '根据国家法定节假日安排，结合学校实际情况，现将 2026 年清明节放假安排通知如下：4 月 4 日（星期六）至 6 日（星期一）放假，共 3 天。', '学校办公室', 'ADMIN', NOW(), NOW(), '2027-12-31 23:59:59', 'ALL', 'normal', 'PUBLISHED', NOW(), NOW());

-- 验证数据
SELECT id, title, status, priority, target_type FROM announcements ORDER BY id DESC LIMIT 10;
