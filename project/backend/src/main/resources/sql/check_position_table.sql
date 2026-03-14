-- 检查 position 表中的所有数据
SELECT '========== Part 1: 检查 position 表中的所有数据 ==========' as status;

SELECT 
    id,
    position_name,
    company_id,
    salary_min,
    salary_max,
    recruit_count,
    status
FROM position
ORDER BY id;

-- 检查是否有 id=4 的岗位
SELECT '========== Part 2: 检查 id=4 的岗位是否存在 ==========' as status;

SELECT 
    id,
    position_name,
    company_id
FROM position
WHERE id = 4;

-- 检查 position 表中缺失的 id
SELECT '========== Part 3: 检查 position 表中缺失的 id ==========' as status;

SELECT 
    n.n AS missing_id
FROM (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION
    SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION
    SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
) n
WHERE n.n NOT IN (SELECT id FROM position)
ORDER BY n.n;
