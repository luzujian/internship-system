-- 优化权限表结构，按照系统菜单栏的模块--子模块--权限来组织

-- 更新用户管理模块的权限
UPDATE permissions SET module = '用户管理-学生管理' WHERE permission_code LIKE 'user:student:%';
UPDATE permissions SET module = '用户管理-教师管理' WHERE permission_code LIKE 'user:teacher:%';
UPDATE permissions SET module = '用户管理-管理员管理' WHERE permission_code LIKE 'user:admin:%';
UPDATE permissions SET module = '用户管理-企业管理' WHERE permission_code LIKE 'user:company:%';

-- 更新撤回申请审核模块的权限
UPDATE permissions SET module = '撤回申请审核-企业撤回申请审核' WHERE permission_code LIKE 'company:recall:%';
UPDATE permissions SET module = '撤回申请审核-实习确认表撤回审核' WHERE permission_code LIKE 'internship:recall:%';
UPDATE permissions SET module = '撤回申请审核-实习确认表' WHERE permission_code = 'internship:confirm:view';

-- 更新基础数据管理模块的权限
UPDATE permissions SET module = '基础数据管理-岗位类别管理' WHERE permission_code LIKE 'position-category:%';
UPDATE permissions SET module = '基础数据管理-班级管理' WHERE permission_code LIKE 'class:%';
UPDATE permissions SET module = '基础数据管理-院系专业管理' WHERE permission_code LIKE 'department:%' OR permission_code LIKE 'major:%';
UPDATE permissions SET module = '基础数据管理-招聘管理' WHERE permission_code = 'recruitment:view';

-- 更新AI配置模块的权限
UPDATE permissions SET module = 'AI配置-关键词库管理' WHERE permission_code = 'keyword:view';
UPDATE permissions SET module = 'AI配置-评分规则管理' WHERE permission_code = 'scoring:view';
UPDATE permissions SET module = 'AI配置-AI大模型选择' WHERE permission_code = 'ai:model:view';
UPDATE permissions SET module = 'AI配置-AI测试' WHERE permission_code = 'ai:test:view';

-- 更新系统管理模块的权限
UPDATE permissions SET module = '系统管理-日志管理' WHERE permission_code LIKE 'log:%';
UPDATE permissions SET module = '系统管理-权限管理' WHERE permission_code LIKE 'permission:%';
UPDATE permissions SET module = '系统管理-数据备份与恢复' WHERE permission_code LIKE 'backup:%';
UPDATE permissions SET module = '系统管理-系统参数配置' WHERE permission_code = 'system:settings:view';

-- 删除旧的权限（AI关键词配置、AI评分规则配置、岗位信息相关权限）
DELETE FROM permissions WHERE permission_code IN ('AI_KEYWORD_CONFIG', 'AI_SCORING_CONFIG');
DELETE FROM permissions WHERE permission_code LIKE 'position:%' AND permission_code != 'position-category:%';

-- 删除role_permissions中对应的权限
DELETE FROM role_permissions WHERE permission_code IN ('AI_KEYWORD_CONFIG', 'AI_SCORING_CONFIG');
DELETE FROM role_permissions WHERE permission_code LIKE 'position:%' AND permission_code NOT LIKE 'position-category:%';

-- 查看更新后的权限结构
SELECT module, GROUP_CONCAT(permission_code ORDER BY permission_code) as permissions 
FROM permissions 
GROUP BY module 
ORDER BY 
  CASE 
    WHEN module LIKE '用户管理%' THEN 1
    WHEN module LIKE '撤回申请审核%' THEN 2
    WHEN module LIKE '基础数据管理%' THEN 3
    WHEN module = '公告管理' THEN 4
    WHEN module = '资源文档管理' THEN 5
    WHEN module = '问题反馈管理' THEN 6
    WHEN module = '数据统计' THEN 7
    WHEN module LIKE 'AI配置%' THEN 8
    WHEN module LIKE '系统管理%' THEN 9
    ELSE 10
  END,
  module;
