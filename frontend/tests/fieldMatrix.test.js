import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// §八收尾签字 (2026-09-18 夜): field_matrix.mjs 当前 26 个 flag 已逐项定性,
// 明细见 output/agent-optimization-20260918-closing/field-flags-triage.md。
// verdict 口径: MANUAL_* 手动(安全/治理) | UPLOAD_MANUAL 上传手动 |
// CASCADE_BY_DESIGN 级联双绑 watcher 同步 | DYNAMIC_RUNTIME_KEYS 运行时动态键 |
// PARSER_LIMITATION 解析器局限 (产品无问题)。
// 任何新增或消失的 flag 都会让本门禁变红; 改动时必须同步更新此表 (签字)。
// [formId, prop, flag前缀]
const REVIEWED = [
  ['admin:ai-model', 'clearApiConfig', 'BOUND_NOT_DECLARED'],
  // isDefault 现有条件 disabled，扫描器按 readonly 排除；下方独立断言继续保护手动用途与敏感字段。
  ['admin:ai-model', 'useForVoice', 'BOUND_NOT_DECLARED'],
  ['admin:backup', 'enabled', 'BOUND_NOT_DECLARED'],
  ['admin:backup', 'time', 'BOUND_NOT_DECLARED'],
  ['admin:backup', 'time', 'RISKY_WIDGET:el-time-picker'],
  ['admin:dashboard-announcement', 'attachments', 'DECLARED_NOT_BOUND'],
  ['admin:dashboard-announcement', 'attachments', 'UPLOAD_FIELD'],
  ['admin:dashboard-resource', 'file', 'DECLARED_NOT_BOUND'],
  ['admin:dashboard-resource', 'file', 'UPLOAD_FIELD'],
  ['admin:score-weights', '-', 'FIELDS_UNRESOLVED'],
  ['company:info', 'province', 'DECLARED_NOT_BOUND'],
  ['company:info', 'city', 'DECLARED_NOT_BOUND'],
  ['company:info', 'district', 'DECLARED_NOT_BOUND'],
  ['company:info', 'addressCode', 'BOUND_NOT_DECLARED'],
  ['company:position', 'province', 'DECLARED_NOT_BOUND'],
  ['company:position', 'city', 'DECLARED_NOT_BOUND'],
  ['company:position', 'district', 'DECLARED_NOT_BOUND'],
  ['company:position', 'addressCode', 'BOUND_NOT_DECLARED'],
  // 手工上传回调绑定上传控件，声明仅用于缺项/手工上传提示；AI bridge 拒绝 upload 赋值。
  ['company:promotion', 'logo', 'DECLARED_NOT_BOUND'],
  ['company:promotion', 'logo', 'UPLOAD_FIELD'],
  ['company:promotion', 'photos', 'DECLARED_NOT_BOUND'],
  ['company:promotion', 'photos', 'UPLOAD_FIELD'],
  ['company:promotion', 'videos', 'DECLARED_NOT_BOUND'],
  ['company:promotion', 'videos', 'UPLOAD_FIELD'],
  ['home:delay', 'materialUrl', 'DECLARED_NOT_BOUND'],
  ['home:delay', 'materialUrl', 'UPLOAD_FIELD'],
  ['home:self-practice', 'otherMaterials', 'DECLARED_NOT_BOUND'],
  ['home:self-practice', 'otherMaterials', 'UPLOAD_FIELD'],
  // 岗位 ID 由学生选择企业已有岗位生成，AI 仅填写岗位文字。
  ['home:self-practice', 'positionId', 'BOUND_NOT_DECLARED'],
  // 实习协议由多文件上传组件生成 ID 清单；只声明必填提示，不允许 AI 写入文件 ID。
  ['home:self-practice', 'agreementFileIds', 'DECLARED_NOT_BOUND'],
  ['home:self-practice', 'agreementFileIds', 'UPLOAD_FIELD'],
  ['confirmation:remark', 'agreementFileIds', 'UPLOAD_FIELD'],
  ['teacher:score-weights', '-', 'FIELDS_UNRESOLVED'],
];

const dir = dirname(fileURLToPath(import.meta.url));

test('AI模型用途与密钥始终保持手动配置，条件禁用不削弱AI字段门禁', () => {
  const source = readFileSync(join(dir, '..', 'src', 'views', 'admin', 'AIModelManagement.vue'), 'utf8');
  const start = source.indexOf('const aiModelFillFields');
  const end = source.indexOf('const registerAiModelForm');
  assert.ok(start >= 0 && end > start, 'must locate the actual registered descriptor factory');
  const fields = [...source.slice(start, end).matchAll(/prop:\s*'([^']+)'/g)].map(match => match[1]).sort();
  assert.deepEqual(fields, ['modelName', 'modelCode', 'provider', 'apiEndpoint', 'maxTokens', 'temperature', 'status', 'description'].sort());
  for (const prop of ['isDefault', 'useForVoice', 'clearApiConfig']) {
    assert.match(source, new RegExp('v-model="form\\.' + prop + '"'), `${prop} must retain its manual control`);
    assert.ok(!fields.includes(prop), `${prop} must stay outside AI fill descriptors`);
  }
  assert.ok(!fields.includes('apiKey'), 'AI must never receive a fillable credential field');
  assert.match(source, /<el-checkbox[^>]*v-model="form\.isDefault"[^>]*:disabled="editingCurrentChat"/);
});

test('字段矩阵零未评审 flag 门禁', () => {
  execFileSync(process.execPath, [join(dir, 'field_matrix.mjs')], { stdio: 'pipe' });
  const outDir = resolve(dir, '..', '..', 'output', 'agent-optimization-20260918-build', 'tests');
  const data = JSON.parse(readFileSync(join(outDir, 'field-matrix.json'), 'utf8'));
  const unreviewed = data.flags.filter((f) => !REVIEWED.some(
    (e) => e[0] === f.formId && e[1] === (f.prop || '-') && String(f.flag).startsWith(e[2])));
  assert.deepEqual(unreviewed, [], '存在未评审 flag: ' + JSON.stringify(unreviewed));
  assert.equal(data.flags.length, REVIEWED.length, 'flag 数量变化, 请同步更新签字表, 实际: ' + JSON.stringify(data.flags.map((f) => f.formId + '|' + (f.prop || '-') + '|' + f.flag)));
});
