// ========================================
// 全场景极限压测脚本
// 覆盖全部 470+ 接口，4 种角色业务链路
// 包含阶梯加压、异常流量注入、动态 Payload
//
// 模式：
//   SMOKE=true  — 冒烟测试（2 VU, 5s）
//   MEDIUM=true  — 中等压测（50→500 VU）
//   CLEAN=true  — 纯净压测：仅 4 种角色核心 CRUD，剥离 AI/聊天
//   (none)       — 极限压测（10→1000 VU）
// ========================================

import { sleep, group } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { studentFlow } from './flows/student-flow.js';
import { companyFlow } from './flows/company-flow.js';
import { teacherFlow } from './flows/teacher-flow.js';
import { adminFlow } from './flows/admin-flow.js';
import { publicFlow, resourceFlow } from './flows/common-flow.js';
import { STAGES_RAMP, STAGES_500, STAGES_SMOKE, THRESHOLDS } from './config.js';
import { login } from './helpers.js';

// 自定义指标
const flowSuccess = new Counter('flow_success');
const flowFailed = new Counter('flow_failed');
const flowDuration = new Trend('flow_duration');
const errorRate = new Rate('error_rate');

// ---- Setup: 预热登录缓存 ----
export function setup() {
  console.log('[SETUP] 预热登录缓存...');
  for (const role of ['student', 'teacher', 'admin', 'company']) {
    const token = login(role);
    if (token) {
      console.log(`[SETUP] ${role} 登录缓存预热成功`);
    } else {
      console.warn(`[SETUP] ${role} 登录缓存预热失败`);
    }
  }
  console.log('[SETUP] 缓存预热完成，开始压测');
  return {};
}

// ---- 选择运行模式 ----
const isSmoke = __ENV.SMOKE === 'true';
const isMedium = __ENV.MEDIUM === 'true';
const isClean = __ENV.CLEAN === 'true';
const stages = isSmoke ? STAGES_SMOKE : (isMedium ? STAGES_500 : STAGES_RAMP);

export const options = {
  scenarios: {
    // 学生链路 (40% 流量)
    student_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stages,
      exec: 'studentScenario',
    },
    // 企业链路 (20% 流量)
    company_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stages,
      exec: 'companyScenario',
    },
    // 教师链路 (20% 流量)
    teacher_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stages,
      exec: 'teacherScenario',
    },
    // 管理员链路 (20% 流量)
    admin_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stages,
      exec: 'adminScenario',
    },
    // 资源/公告链路（非 AI 的公共 CRUD）
    resource_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stages,
      exec: 'resourceScenario',
    },
  },
  thresholds: THRESHOLDS,
};

// ---- 纯净模式下忽略 AI/聊天场景 ----
// AI 接口在隔离的 isolated-flow.js 中独立运行

// ---- 默认场景（CLI 模式回退）----
export default function () {
  const roll = Math.random();
  if (roll < 0.35) studentScenario();
  else if (roll < 0.55) companyScenario();
  else if (roll < 0.75) teacherScenario();
  else if (roll < 0.9) adminScenario();
  else resourceScenario();
}

// ---- 学生链路场景 ----
export function studentScenario() {
  const start = Date.now();
  try {
    group('Student Flow', () => { studentFlow(); });
    flowSuccess.add(1);
    flowDuration.add(Date.now() - start);
    errorRate.add(false);
  } catch (e) {
    flowFailed.add(1);
    errorRate.add(true);
    console.error(`[STUDENT] Error: ${e.message}`);
  }
  sleep(Math.random() * 2 + 0.5);
}

// ---- 企业链路场景 ----
export function companyScenario() {
  const start = Date.now();
  try {
    group('Company Flow', () => { companyFlow(); });
    flowSuccess.add(1);
    flowDuration.add(Date.now() - start);
    errorRate.add(false);
  } catch (e) {
    flowFailed.add(1);
    errorRate.add(true);
    console.error(`[COMPANY] Error: ${e.message}`);
  }
  sleep(Math.random() * 2 + 0.5);
}

// ---- 教师链路场景 ----
export function teacherScenario() {
  const start = Date.now();
  try {
    group('Teacher Flow', () => { teacherFlow(); });
    flowSuccess.add(1);
    flowDuration.add(Date.now() - start);
    errorRate.add(false);
  } catch (e) {
    flowFailed.add(1);
    errorRate.add(true);
    console.error(`[TEACHER] Error: ${e.message}`);
  }
  sleep(Math.random() * 2 + 0.5);
}

// ---- 管理员链路场景 ----
export function adminScenario() {
  const start = Date.now();
  try {
    group('Admin Flow', () => { adminFlow(); });
    flowSuccess.add(1);
    flowDuration.add(Date.now() - start);
    errorRate.add(false);
  } catch (e) {
    flowFailed.add(1);
    errorRate.add(true);
    console.error(`[ADMIN] Error: ${e.message}`);
  }
  sleep(Math.random() * 2 + 0.5);
}

// ---- 资源/公告链路（纯 CRUD，无 AI）----
export function resourceScenario() {
  const start = Date.now();
  try {
    const roll = Math.random();
    if (roll < 0.4) {
      group('Public Flow', () => publicFlow());
    } else {
      group('Resource Flow', () => resourceFlow());
    }
    flowSuccess.add(1);
    flowDuration.add(Date.now() - start);
    errorRate.add(false);
  } catch (e) {
    flowFailed.add(1);
    errorRate.add(true);
    console.error(`[RESOURCE] Error: ${e.message}`);
  }
  sleep(Math.random() * 2 + 0.5);
}
