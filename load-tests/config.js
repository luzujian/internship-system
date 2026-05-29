// ========================================
// 压测配置文件
// ========================================

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 测试账号（需要在数据库中预先创建）
export const TEST_ACCOUNTS = {
  // 学生：class_id=132（21智工10班），绑定到辅导员 t003
  student: {
    username: __ENV.STUDENT_USER || '21209100300',
    password: __ENV.STUDENT_PASS || '123456',
    role: 'STUDENT',
  },
  // 教师：COUNSELOR 类型，绑定班级 132/133/134/153
  teacher: {
    username: __ENV.TEACHER_USER || 't003',
    password: __ENV.TEACHER_PASS || '123456',
    role: 'TEACHER',
  },
  company: {
    username: __ENV.COMPANY_USER || 'e2e_company',
    password: __ENV.COMPANY_PASS || '123456',
    role: 'COMPANY',
  },
  admin: {
    username: __ENV.ADMIN_USER || 'admin',
    password: __ENV.ADMIN_PASS || '123456',
    role: 'ADMIN',
  },
};

// 数据库中的真实关联 ID（setup 阶段会用 API 查询填充）
export const REAL_DATA = {
  teacherId: 49,          // t003 辅导员
  teacherDbId: 49,        // teacher_users.id
  studentDbId: 159,       // 学生 21209100300 的 student_users.id
  companyId: 140,         // e2e_company 的 company_users.id
  classIds: [132, 133, 134, 153],
  studentIds: [159, 160, 161, 166, 201],  // 绑定班级下的学生
  studentUserIds: ['21209100300', '21209100299', '21209100305', '21209110325', '21209110313'],
  positionCategoryId: 1,
};

// 阶梯加压模型
export const STAGES_RAMP = [
  { duration: '30s', target: 10 },    // 预热
  { duration: '1m', target: 50 },     // 快速爬升
  { duration: '2m', target: 200 },    // 中等压力
  { duration: '3m', target: 500 },    // 高压
  { duration: '2m', target: 1000 },   // 极限压力
  { duration: '3m', target: 1000 },   // 持续极限
  { duration: '1m', target: 0 },      // 降压
];

// 100 VU 摸底模型（5 场景 × 100 = 500 总 VU，寻找单机健康拐点）
export const STAGES_500 = [
  { duration: '20s', target: 20 },
  { duration: '30s', target: 50 },
  { duration: '1m', target: 80 },
  { duration: '2m', target: 100 },
  { duration: '1m', target: 100 },
  { duration: '20s', target: 0 },
];

// 轻量试跑模型（用于调试）
export const STAGES_SMOKE = [
  { duration: '3s', target: 2 },
  { duration: '5s', target: 2 },
  { duration: '2s', target: 0 },
];

// 性能阈值
export const THRESHOLDS = {
  http_req_duration: ['p(95)<3000', 'p(99)<5000'],
  http_req_failed: ['rate<0.3'],      // 允许一定比例失败（压测中正常）
  http_reqs: ['rate>10'],             // 至少 10 RPS
};

// 异常流量注入概率 (10%)
export const MALFORMED_PROBABILITY = 0.10;
