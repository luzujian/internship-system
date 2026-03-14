-- Update English data to Chinese for internship_application table

-- Update for algorithm engineer (id=9)
UPDATE internship_application 
SET 
    skills = 'Python, TensorFlow, PyTorch, 机器学习, 深度学习',
    experience = '参与过推荐算法项目，熟悉常用机器学习框架',
    self_evaluation = '算法基础扎实，学习能力强，对新技术有浓厚兴趣'
WHERE position_id = 9 AND (skills LIKE 'Python, TensorFlow%');

-- Update for frontend engineer (id=18)
UPDATE internship_application 
SET 
    skills = 'Vue.js, React, JavaScript, CSS, HTML5, Webpack',
    experience = '参与过多个前端项目开发，熟悉主流前端框架',
    self_evaluation = '前端技术扎实，注重用户体验，代码质量高'
WHERE position_id = 18 AND (skills LIKE 'Vue.js, React%');

-- Update for backend engineer (id=19)
UPDATE internship_application 
SET 
    skills = 'Java, Spring Boot, MySQL, Redis, 微服务',
    experience = '参与过后端系统开发，熟悉分布式架构',
    self_evaluation = '后端技术扎实，系统设计能力强，责任心强'
WHERE position_id = 19 AND (skills LIKE 'Java, Spring Boot%');

-- Update for UI designer (id=49)
UPDATE internship_application 
SET 
    skills = 'Figma, Sketch, Photoshop, Adobe XD, 用户体验设计',
    experience = '参与过多个UI/UX设计项目，熟悉设计工具',
    self_evaluation = '设计能力强，注重用户体验，审美水平高'
WHERE position_id = 49 AND (skills LIKE 'Figma, Sketch%');

-- Update for product manager (id=60)
UPDATE internship_application 
SET 
    skills = '产品规划, 需求分析, 原型设计, 数据分析, 项目管理',
    experience = '参与过产品全生命周期管理，熟悉敏捷开发流程',
    self_evaluation = '产品思维清晰，沟通能力强，执行力好'
WHERE position_id = 60 AND (skills LIKE 'Product planning%');

-- Update for security engineer (id=61)
UPDATE internship_application 
SET 
    skills = 'Web安全, APP安全, 渗透测试, 漏洞挖掘, 安全审计',
    experience = '参与过安全测试项目，熟悉常见安全漏洞',
    self_evaluation = '安全意识强，技术全面，问题发现能力突出'
WHERE position_id = 61 AND (skills LIKE 'Web security%');

-- Update for test engineer (id=92)
UPDATE internship_application 
SET 
    skills = 'Selenium, JUnit, Postman, 接口测试, 性能测试',
    experience = '参与过自动化测试项目，熟悉测试流程',
    self_evaluation = '测试用例设计能力强，质量意识强，细心负责'
WHERE position_id = 92 AND (skills LIKE 'Selenium, JUnit%');

-- Update for data analyst (id=93)
UPDATE internship_application 
SET 
    skills = 'Python, SQL, Pandas, NumPy, Matplotlib, 数据可视化',
    experience = '参与过数据分析项目，熟悉数据处理流程',
    self_evaluation = '数据处理能力强，逻辑清晰，分析能力突出'
WHERE position_id = 93 AND (skills LIKE 'Python, SQL%');

-- Verify results
SELECT 'Verification Results' as status;
SELECT 
    position_id,
    COUNT(*) AS total,
    SUM(CASE WHEN skills LIKE '%机器学习%' OR skills LIKE '%前端%' OR skills LIKE '%后端%' OR skills LIKE '%设计%' OR skills LIKE '%产品%' OR skills LIKE '%安全%' OR skills LIKE '%测试%' OR skills LIKE '%数据%' THEN 1 ELSE 0 END) AS has_chinese
FROM internship_application
WHERE position_id IN (9, 18, 19, 49, 60, 61, 92, 93)
GROUP BY position_id;

SELECT 'Update completed' as status;
SELECT 'All English data has been updated to Chinese' AS message;
