import test from 'node:test'
import assert from 'node:assert/strict'
import { isReflectionRoute, isReflectionPermission } from './reflectionBusiness.mjs'

test('retired reflection routes include old aliases and nested details', () => {
  for (const path of ['/student/internships', '/student/internship-reflection/submit',
    '/teacher/evaluation', '/teacher/grade-finalize', '/teacher/ai-keyword-config',
    '/teacher/reflection-evaluation/18', '/admin/keyword-library', '/admin/scoring-rule?tab=rules']) {
    assert.equal(isReflectionRoute(path), true, path)
  }
})

test('appraisal, calendar, recruitment and general AI management stay available', () => {
  for (const path of ['/student/internship-appraisal', '/student/internship-confirmation-form',
    '/teacher/internship-appraisals', '/teacher/key-timeline-config', '/teacher/timeline-config',
    '/teacher/reports', '/company/internship-students', '/admin/ai-model', '/admin/permissions']) {
    assert.equal(isReflectionRoute(path), false, path)
  }
})

test('related grading permissions are retired while shared settings and company feedback remain', () => {
  for (const value of ['view:reflection', 'menu:evaluation', 'menu:teacherAiScoringConfig', 'keyword:view',
    'scoring:view', { module: '评分规则管理' }, { permission_name: '智慧评分' },
    { path: '/teacher/ai-keyword-config' }]) assert.equal(isReflectionPermission(value), true)
  for (const value of ['menu:settings', 'menu:appraisal-collection', 'ai:model:view',
    { module: 'AI配置测试' }, { permissionName: '搜索关键词' }, { permissionName: '企业推荐评分' }]) {
    assert.equal(isReflectionPermission(value), false)
  }
})
