# 实习管理系统 - 并发极限压测脚本

## 文件结构

```
load-tests/
├── config.js                  # 配置（账号、加压模型、阈值）
├── helpers.js                 # 工具函数（认证、动态数据、异常注入）
├── full-load-test.js          # 主压测脚本（全场景）
├── race-condition-test.js     # 竞态条件专项测试
├── idempotency-test.js        # 幂等性专项测试
└── flows/
    ├── student-flow.js        # 学生链路（50 个接口）
    ├── company-flow.js        # 企业链路（27 个接口）
    ├── teacher-flow.js        # 教师链路（44 个接口）
    ├── admin-flow.js          # 管理员链路（60 个接口）
    └── common-flow.js         # 公共/AI/聊天/资源链路
```

## 运行命令

### 1. 冒烟测试（快速验证）
```bash
k6 run --env SMOKE=true --duration 10s --vus 4 load-tests/full-load-test.js
```

### 2. 全场景极限压测（正式）
```bash
k6 run load-tests/full-load-test.js
```

### 3. 竞态条件测试
```bash
k6 run load-tests/race-condition-test.js
```

### 4. 幂等性测试
```bash
k6 run load-tests/idempotency-test.js
```

### 5. 自定义参数
```bash
# 指定目标服务器
k6 run --env BASE_URL=http://10.244.49.236:8080 load-tests/full-load-test.js

# 指定测试账号
k6 run --env STUDENT_USER=xxx --env STUDENT_PASS=yyy load-tests/full-load-test.js
```

## 加压模型

| 阶段 | 时长 | VU 数 | 说明 |
|------|------|-------|------|
| 预热 | 30s | 10 | 逐步启动 |
| 爬升 | 1m | 50 | 快速加压 |
| 中压 | 2m | 200 | 中等压力 |
| 高压 | 3m | 500 | 高并发 |
| 极限 | 2m | 1000 | 极限压力 |
| 持续 | 3m | 1000 | 持续极限 |
| 降压 | 1m | 0 | 逐步停止 |

## 覆盖接口统计

- 学生链路：50 个接口
- 企业链路：27 个接口
- 教师链路：44 个接口
- 管理员链路：60 个接口
- 公共/AI/聊天/资源：30+ 个接口
- **总计覆盖：200+ 个核心接口**

## 测试特性

- **动态 Payload**：所有写操作使用 UUID/时间戳随机生成，防止缓存命中
- **异常流量注入**：10% 概率注入超大文本、空值、非法类型、SQL 注入尝试
- **竞态条件**：100 并发对同一岗位同时申请
- **幂等性**：10 并发使用完全相同 Payload 重复提交
- **4 种角色**：学生、教师、企业、管理员全覆盖
