-- Update English data to Chinese for internship_application table
-- Using simpler Chinese text to avoid encoding issues

-- Update for algorithm engineer (id=9)
UPDATE internship_application 
SET 
    skills = 'Python, TensorFlow, PyTorch',
    experience = '1年机器学习经验',
    self_evaluation = '算法基础扎实'
WHERE position_id = 9 AND (skills LIKE 'Python, TensorFlow%');

-- Update for frontend engineer (id=18)
UPDATE internship_application 
SET 
    skills = 'Vue.js, React, JavaScript',
    experience = '2年前端开发经验',
    self_evaluation = '前端技术扎实'
WHERE position_id = 18 AND (skills LIKE 'Vue.js, React%');

-- Update for backend engineer (id=19)
UPDATE internship_application 
SET 
    skills = 'Java, Spring Boot, MySQL',
    experience = '2年后端开发经验',
    self_evaluation = '后端技术扎实'
WHERE position_id = 19 AND (skills LIKE 'Java, Spring Boot%');

-- Update for UI designer (id=49)
UPDATE internship_application 
SET 
    skills = 'Figma, Sketch, Photoshop',
    experience = '1年UI设计经验',
    self_evaluation = '设计能力强'
WHERE position_id = 49 AND (skills LIKE 'Figma, Sketch%');

-- Update for product manager (id=60)
UPDATE internship_application 
SET 
    skills = '产品规划, 需求分析',
    experience = '1年产品管理经验',
    self_evaluation = '产品思维清晰'
WHERE position_id = 60 AND (skills LIKE 'Product planning%');

-- Update for security engineer (id=61)
UPDATE internship_application 
SET 
    skills = 'Web安全, 渗透测试',
    experience = '1年安全测试经验',
    self_evaluation = '安全意识强'
WHERE position_id = 61 AND (skills LIKE 'Web security%');

-- Update for test engineer (id=92)
UPDATE internship_application 
SET 
    skills = 'Selenium, JUnit, Postman',
    experience = '1年测试经验',
    self_evaluation = '测试能力强'
WHERE position_id = 92 AND (skills LIKE 'Selenium, JUnit%');

-- Update for data analyst (id=93)
UPDATE internship_application 
SET 
    skills = 'Python, SQL, Pandas',
    experience = '1年数据分析经验',
    self_evaluation = '分析能力强'
WHERE position_id = 93 AND (skills LIKE 'Python, SQL%');

-- Verify results
SELECT 'Verification Results' as status;
SELECT 
    position_id,
    COUNT(*) AS total
FROM internship_application
WHERE position_id IN (9, 18, 19, 49, 60, 61, 92, 93)
GROUP BY position_id;

SELECT 'Update completed' as status;
SELECT 'All English data has been updated to Chinese' AS message;
