-- 检查 position 表的结构
DESCRIBE position;

-- 检查 position 表中的所有数据
SELECT 
    id,
    position_name,
    company_id,
    salary_min,
    salary_max,
    status
FROM position
ORDER BY id;

-- 检查是否有 id=4 的岗位
SELECT 
    id,
    position_name,
    company_id
FROM position
WHERE id = 4;
