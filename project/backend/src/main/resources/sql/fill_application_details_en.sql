-- Fill skills, experience, self_evaluation data for internship_application table

-- Update for algorithm engineer (id=9)
UPDATE internship_application 
SET 
    skills = 'Python, TensorFlow, PyTorch',
    experience = '1 year ML experience',
    self_evaluation = 'Strong algorithm skills'
WHERE position_id = 9 AND (skills IS NULL OR skills = '');

-- Update for frontend engineer (id=18)
UPDATE internship_application 
SET 
    skills = 'Vue.js, React, JavaScript',
    experience = '2 years frontend development',
    self_evaluation = 'Strong frontend skills'
WHERE position_id = 18 AND (skills IS NULL OR skills = '');

-- Update for backend engineer (id=19)
UPDATE internship_application 
SET 
    skills = 'Java, Spring Boot, MySQL',
    experience = '2 years backend development',
    self_evaluation = 'Strong backend skills'
WHERE position_id = 19 AND (skills IS NULL OR skills = '');

-- Update for UI designer (id=49)
UPDATE internship_application 
SET 
    skills = 'Figma, Sketch, Photoshop',
    experience = '1 year UI design',
    self_evaluation = 'Strong design skills'
WHERE position_id = 49 AND (skills IS NULL OR skills = '');

-- Update for product manager (id=60)
UPDATE internship_application 
SET 
    skills = 'Product planning, Requirements analysis',
    experience = '1 year product management',
    self_evaluation = 'Strong product thinking'
WHERE position_id = 60 AND (skills IS NULL OR skills = '');

-- Update for security engineer (id=61)
UPDATE internship_application 
SET 
    skills = 'Web security, Penetration testing',
    experience = '1 year security testing',
    self_evaluation = 'Strong security awareness'
WHERE position_id = 61 AND (skills IS NULL OR skills = '');

-- Update for test engineer (id=92)
UPDATE internship_application 
SET 
    skills = 'Selenium, JUnit, Postman',
    experience = '1 year testing',
    self_evaluation = 'Strong testing skills'
WHERE position_id = 92 AND (skills IS NULL OR skills = '');

-- Update for data analyst (id=93)
UPDATE internship_application 
SET 
    skills = 'Python, SQL, Pandas',
    experience = '1 year data analysis',
    self_evaluation = 'Strong data analysis skills'
WHERE position_id = 93 AND (skills IS NULL OR skills = '');

-- Verify results
SELECT 'Verification Results' as status;
SELECT 
    position_id,
    COUNT(*) AS total,
    SUM(CASE WHEN skills IS NOT NULL AND skills != '' THEN 1 ELSE 0 END) AS has_skills
FROM internship_application
WHERE position_id IN (9, 18, 19, 49, 60, 61, 92, 93)
GROUP BY position_id;

SELECT 'Fill completed' as status;
SELECT 'All application records have been filled with skills, experience and self-evaluation data' AS message;
