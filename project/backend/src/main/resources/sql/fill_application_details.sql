-- 为 internship_application 表填充 skills、experience、self_evaluation 数据
-- 为每个岗位创建不同的技能和经验数据

-- 为算法工程师（id=9）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Python, TensorFlow, PyTorch, 机器学习, 深度学习',
    experience = '参与过推荐算法项目，熟悉常用机器学习框架',
    self_evaluation = '算法基础扎实，学习能力强，对新技术有浓厚兴趣'
WHERE position_id = 9 AND (skills IS NULL OR skills = '');

-- 为前端开发工程师（id=18）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Vue.js, React, JavaScript, CSS, HTML5, Webpack',
    experience = '参与过多个前端项目开发，熟悉主流前端框架',
    self_evaluation = '前端技术扎实，注重用户体验，代码质量高'
WHERE position_id = 18 AND (skills IS NULL OR skills = '');

-- 为后端开发工程师（id=19）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Java, Spring Boot, MySQL, Redis, 微服务',
    experience = '参与过后端系统开发，熟悉分布式架构',
    self_evaluation = '后端技术扎实，系统设计能力强，责任心强'
WHERE position_id = 19 AND (skills IS NULL OR skills = '');

-- 为UI设计师（id=49）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Figma, Sketch, Photoshop, Adobe XD, 用户体验设计',
    experience = '参与过多个UI/UX设计项目，熟悉设计工具',
    self_evaluation = '设计能力强，注重用户体验，审美水平高'
WHERE position_id = 49 AND (skills IS NULL OR skills = '');

-- 为产品经理（id=60）的申请填充数据
UPDATE internship_application 
SET 
    skills = '产品规划, 需求分析, 原型设计, 数据分析, 项目管理',
    experience = '参与过产品全生命周期管理，熟悉敏捷开发流程',
    self_evaluation = '产品思维清晰，沟通能力强，执行力好'
WHERE position_id = 60 AND (skills IS NULL OR skills = '');

-- 为安全工程师（id=61）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Web安全, APP安全, 渗透测试, 漏洞挖掘, 安全审计',
    experience = '参与过安全测试项目，熟悉常见安全漏洞',
    self_evaluation = '安全意识强，技术全面，问题发现能力突出'
WHERE position_id = 61 AND (skills IS NULL OR skills = '');

-- 为测试工程师（id=92）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Selenium, JUnit, Postman, 接口测试, 性能测试',
    experience = '参与过自动化测试项目，熟悉测试流程',
    self_evaluation = '测试用例设计能力强，质量意识强，细心负责'
WHERE position_id = 92 AND (skills IS NULL OR skills = '');

-- 为数据分析师（id=93）的申请填充数据
UPDATE internship_application 
SET 
    skills = 'Python, SQL, Pandas, NumPy, Matplotlib, 数据可视化',
    experience = '参与过数据分析项目，熟悉数据处理流程',
    self_evaluation = '数据处理能力强，逻辑清晰，分析能力突出'
WHERE position_id = 93 AND (skills IS NULL OR skills = '');

-- 验证填充结果
SELECT '========== 验证填充结果 ==========' as status;

SELECT 
    position_id,
    COUNT(*) AS total_count,
    SUM(CASE WHEN skills IS NOT NULL AND skills != '' THEN 1 ELSE 0 END) AS has_skills,
    SUM(CASE WHEN experience IS NOT NULL AND experience != '' THEN 1 ELSE 0 END) AS has_experience,
    SUM(CASE WHEN self_evaluation IS NOT NULL AND self_evaluation != '' THEN 1 ELSE 0 END) AS has_self_evaluation
FROM internship_application
WHERE position_id IN (9, 18, 19, 49, 60, 61, 92, 93)
GROUP BY position_id
ORDER BY position_id;

SELECT '========== 填充完成 ==========' as status;
SELECT '已为所有申请记录填充技能、经验和自我评价数据' AS message;
