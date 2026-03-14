-- 检查 internship_application 表中所有不同的 company_id
SELECT '========== Part 1: 检查 internship_application 表中的 company_id 分布 ==========' as status;

SELECT 
    company_id,
    COUNT(*) AS count
FROM internship_application
GROUP BY company_id
ORDER BY company_id;

-- 检查 student_internship_status 表中所有不同的 company_id
SELECT '========== Part 2: 检查 student_internship_status 表中的 company_id 分布 ==========' as status;

SELECT 
    company_id,
    COUNT(*) AS count
FROM student_internship_status
GROUP BY company_id
ORDER BY company_id;

-- 检查 position 表中所有不同的 company_id
SELECT '========== Part 3: 检查 position 表中的 company_id 分布 ==========' as status;

SELECT 
    company_id,
    COUNT(*) AS count
FROM position
GROUP BY company_id
ORDER BY company_id;

-- 检查 company 表中的所有企业
SELECT '========== Part 4: 检查 company 表中的所有企业 ==========' as status;

SELECT 
    id,
    company_name,
    contact_person,
    phone
FROM company
ORDER BY id;
