import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendDir = resolve(scriptDir, "..");
const rootDir = resolve(scriptDir, "..", "..");
const outDir = join(rootDir, "output", "agent-optimization-20260918-build", "tests");
const coverageSource = readFileSync(join(frontendDir, "src", "agent", "formCoverage.ts"), "utf8");

const FILLABLE = new Set(["input", "textarea", "select", "date", "radio", "number"]);
const NON_WIDGET = new Set(["el-option", "el-form-item", "el-dialog", "el-form", "el-button", "el-icon"]);

function parseSubmitForms() {
  const block = coverageSource.slice(coverageSource.indexOf("export const SUBMIT_FORMS"), coverageSource.indexOf("export const EXEMPT_FILES"));
  const ids = [...block.matchAll(/formId:\s*'([^']+)'/g)].map(m => m[1]);
  const files = [...block.matchAll(/file:\s*'([^']+)'/g)].map(m => m[1]);
  return ids.map((id, i) => ({ formId: id, file: files[i] }));
}

function quotedValue(text, key) {
  let m = text.match(new RegExp(key + ":\\s*'([^']+)'"));
  if (m) return m[1];
  m = text.match(new RegExp(key + ':\\s*"([^"]+)"'));
  return m ? m[1] : null;
}

function depthBlock(src, openIdx, openCh, closeCh) {
  let depth = 0;
  for (let i = openIdx; i < src.length; i++) {
    if (src[i] === openCh) depth++;
    if (src[i] === closeCh) { depth--; if (depth === 0) return src.slice(openIdx, i + 1); }
  }
  return null;
}

function resolveFieldsText(script, block) {
  const fi = block.indexOf("fields:");
  if (fi < 0) return { text: null, how: "none" };
  let j = fi + 7;
  while (j < block.length && /\s/.test(block[j])) j++;
  if (block[j] === "[") return { text: depthBlock(block, j, "[", "]"), how: "inline" };
  const nm = block.slice(j).match(/^([\w$]+)(\(\))?/);
  if (!nm) return { text: null, how: "unknown" };
  const plainArr = new RegExp("const\\s+" + nm[1] + "\\s*=\\s*\\[").exec(script);
  const varArr = (!nm[2]) ? new RegExp("const\\s+" + nm[1] + "\\s*(?::\\s*[^=]+?)?=\\s*\\[").exec(script) : null;
  const bodyArrow = new RegExp("const\\s+" + nm[1] + "\\s*=\\s*\\([^)]*\\)\\s*(?::[^=;{]+)?=>\\s*\\{").exec(script);
  const anyArrow = new RegExp("const\\s+" + nm[1] + "\\s*=[\\s\\S]*?=>\\s*\\[").exec(script);
  const hit = plainArr || varArr || bodyArrow || anyArrow;
  if (!hit && nm[2]) return { text: null, how: "unresolved:" + nm[1] };
  if (!hit) return { text: block, how: "register-block-fallback" };
  const isBody = hit === bodyArrow;
  const tail = hit[0];
  return { text: depthBlock(script, hit.index + (isBody ? tail.length - 1 : tail.lastIndexOf("[")), isBody ? "{" : "[", isBody ? "}" : "]"), how: "fn:" + nm[1] };
}

function parseRegisters(script) {
  const out = [];
  for (const m of script.matchAll(/\.register\(\{/g)) {
    const open = script.indexOf("{", m.index);
    const block = depthBlock(script, open, "{", "}");
    if (!block) continue;
    const id = quotedValue(block, "formId");
    const gm = block.match(/getModel:\s*\(\)\s*=>\s*([\w$]+)/);
    const gmVal = gm ? /getModel:\s*\(\)\s*=>\s*[\w$]+\.value/.test(block) : false;
    const gt = !gm ? block.match(/getModel:\s*\(\)\s*=>\s*\([^?]*\?\s*([\w$]+)\.value\s*:\s*([\w$]+)\.value/) : null;
    const gi = (!gm && !gt) ? block.match(/getModel:\s*([\w$]+)/) : null;
    const fr = resolveFieldsText(script, block);
    let proxyBody = "";
    let proxyPairs = [];
    const proxyName = gi?.[1] || (gm && !gmVal ? gm[1] : null);
    if (proxyName) {
      const decl = new RegExp("const\\s+" + proxyName + "\\s*=").exec(script);
      if (decl) {
        const bo = script.indexOf("{", decl.index);
        const pb = depthBlock(script, bo, "{", "}");
        if (pb && /get\s+[\w$]+\(\)/.test(pb)) {
          proxyBody = pb;
          for (const pm of pb.matchAll(/get\s+([\w$]+)\(\)\s*\{\s*return\s+(?:String\(|Number\()?([\w$.]+)/g)) proxyPairs.push([pm[1], pm[2].split(".")[0]]);
        }
      }
    }
    if (id) {
      const rootsHere = gm ? [gm[1]] : gt ? [gt[1], gt[2]] : gi ? [gi[1]] : [];
      for (const r of rootsHere.length ? rootsHere : [""]) out.push({ formId: id, root: r, bare: !(gm && gmVal), proxyBody, proxyPairs, fieldsText: fr.text, fieldsHow: fr.how });
    }
  }
  return out;
}

function parseDescriptors(text) {
  const out = [];
  if (!text) return out;
  for (const m of text.matchAll(/\{\s*prop:\s*'([^']+)'/g)) {
    const open = text.lastIndexOf("{", m.index);
    const block = depthBlock(text, open, "{", "}");
    if (!block) continue;
    const type = (block.match(/type:\s*'([^']+)'/) || [])[1] || "input";
    const valueType = (block.match(/valueType:\s*'([^']+)'/) || [])[1] || "";
    const required = /required:\s*true/.test(block);
    const hasOptions = /options:/.test(block);
    out.push({ prop: m[1], type, valueType, required, hasOptions });
  }
  const seen = new Map();
  for (const d of out) if (!seen.has(d.prop)) seen.set(d.prop, d);
  return [...seen.values()];
}

function widgetAt(template, idx) {
  const from = Math.max(0, idx - 800);
  const back = template.slice(from, idx);
  const tags = [...back.matchAll(/<(el-[\w-]+|input|textarea|select)\b/g)].filter(t => !NON_WIDGET.has(t[1]));
  const widget = tags.length ? tags[tags.length - 1][1] : "unknown";
  let tagText = "";
  if (tags.length) {
    const absStart = from + tags[tags.length - 1].index;
    let quote = "";
    let end = absStart;
    while (end < template.length && end - absStart < 800) {
      const ch = template[end];
      if (quote) { if (ch === quote) quote = ""; }
      else if (ch === String.fromCharCode(34) || ch === String.fromCharCode(39)) quote = ch;
      else if (ch === ">") break;
      end++;
    }
    tagText = template.slice(absStart, end + 1);
  }
  return {
    widget,
    readonly: /readonly|disabled/.test(tagText),
    password: /password|show-password/.test(tagText),
    multiple: /\bmultiple\b/.test(tagText),
    allowCreate: /allow-create/.test(tagText)
  };
}

function parseBindings(template) {
  const out = [];
  for (const m of template.matchAll(/v-model="([A-Za-z_$][\w$]*)\.([\w$.]+)"/g)) {
    const w = widgetAt(template, m.index);
    out.push({ root: m[1], prop: m[2], ...w, password: w.password || /password|pwd|passwd/i.test(m[2]) });
  }
  for (const m of template.matchAll(/v-model="([A-Za-z_$][\w$]*)"/g)) {
    if (m[0].includes(".")) continue;
    const w = widgetAt(template, m.index);
    out.push({ root: "", prop: m[1], ...w, password: w.password || /password|pwd|passwd/i.test(m[1]) });
  }
  return out;
}

const REAL_FILL = {
  "company:position": "E2E T1/T4 manual+payload, T2 AI display+model, efficiency live, fill specs radio",
  "company:interview-template": "fill specs return-cancel-valueformat",
  "teacher:calendar": "fill specs date-string"
};


const forms = parseSubmitForms();
const rows = [];
const flagSet = new Set();
const flags = [];
function addFlag(f) {
  const key = [f.formId, f.file, f.prop || "-", f.flag, f.widget || ""].join("|");
  if (flagSet.has(key)) return;
  flagSet.add(key);
  flags.push(f);
}

for (const { formId, file } of forms) {
  const full = join(frontendDir, "src", file);
  let source;
  try { source = readFileSync(full, "utf8"); } catch { addFlag({ formId, file, flag: "FILE_MISSING" }); continue; }
  const setupMatch = source.match(/<script setup[^>]*>([\s\S]*?)<\/script\s*>/);
  const scriptBlocks = [...source.matchAll(/<script[^>]*>([\s\S]*?)<\/script\s*>/g)].map(m => m[1]);
  const script = setupMatch ? setupMatch[1] : scriptBlocks.join("\n");
  const tmMatch = source.match(/<template>([\s\S]*)<\/template\s*>/);
  const template = tmMatch ? tmMatch[1] : "";
  const regs = parseRegisters(script).filter(r => r.formId === formId);
  const roots = [...new Set(regs.map(r => r.root).filter(Boolean))];
  const bareRoots = [...new Set(regs.filter(r => r.bare).map(r => r.root))];
  const proxyBodies = new Map(regs.filter(r => r.proxyBody).map(r => [r.root, r.proxyBody]));
  const pairSet = new Set(regs.flatMap(r => r.proxyPairs || []).map(p => p[0] + "=>" + p[1]));
  const proxyMentioned = [...new Set([...proxyBodies.values()].flatMap(bd => [...bd.matchAll(/(\w+)\.value/g)].map(m => m[1])))];
  const fieldsTexts = [...new Set(regs.map(r => r.fieldsText).filter(Boolean))];
  if (regs.length && !fieldsTexts.length) addFlag({ formId, file, flag: "FIELDS_UNRESOLVED:" + regs.map(r => r.fieldsHow).join(",") });
  const descriptors = fieldsTexts.flatMap(t => parseDescriptors(t));
  const descByProp = new Map(descriptors.map(d => [d.prop, d]));
  const acceptedRoots = [...new Set([...roots, ...proxyMentioned])];
  const bindings = parseBindings(template).filter(b => {
    if (acceptedRoots.includes(b.root)) return true;
    if (b.root !== "") return false;
    if (descByProp.has(b.prop)) return bareRoots.length > 0;
    return [...descByProp.keys()].some(p => pairSet.has(p + "=>" + b.prop));
  });
  const unregRe = new RegExp("formContext\\.unregister\\([\"\\x27]" + formId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "[\"\\x27]");
  const unregs = (script.match(unregRe) || []).length;
  if (!roots.length) addFlag({ formId, file, flag: "NO_MODEL_ROOT_PARSED" });
  const aliasHit = (bprop, dprop) => bprop === dprop || pairSet.has(dprop + "=>" + bprop);
  for (const d of descriptors) {
    const bound = bindings.filter(b => aliasHit(b.prop, d.prop));
    const editable = bound.some(b => !b.readonly);
    const aiWritable = FILLABLE.has(d.type) && editable && !bound.some(b => b.password);
    rows.push({
      formId, file, prop: d.prop, declaredType: d.type, valueType: d.valueType || "-",
      required: d.required, hasOptions: d.hasOptions,
      widgets: [...new Set(bound.map(b => b.widget))].join(",") || "NOT_BOUND_IN_TEMPLATE",
      editable, aiWritable,
      readonly: bound.length > 0 && bound.every(b => b.readonly),
      password: bound.some(b => b.password),
      multiple: bound.some(b => b.multiple),
      allowCreate: bound.some(b => b.allowCreate),
      nested: d.prop.includes("."),
      realFill: REAL_FILL[formId] || ""
    });
    if (!bound.length) addFlag({ formId, file, prop: d.prop, flag: "DECLARED_NOT_BOUND" });
    if (d.type === "select" && !d.hasOptions && !bound.some(b => b.allowCreate)) addFlag({ formId, file, prop: d.prop, flag: "SELECT_NO_OPTIONS_NO_ALLOWCREATE" });
    if (formId === "admin:announcement" && d.prop === "title") if (bound.some(b => b.multiple) && d.valueType !== "array") addFlag({ formId, file, prop: d.prop, flag: "MULTIPLE_WITHOUT_ARRAY_VALUETYPE" });
    if (d.type === "upload" || bound.some(b => b.widget === "el-upload")) addFlag({ formId, file, prop: d.prop, flag: "UPLOAD_FIELD" });
  }
  for (const b of bindings) {
    if (b.readonly || b.password) continue;
    const declaredHit = descByProp.has(b.prop) || [...descByProp.keys()].some(p => aliasHit(b.prop, p));
    if (!declaredHit && !b.prop.includes(".")) {
      addFlag({ formId, file, prop: b.prop, flag: "BOUND_NOT_DECLARED", widget: b.widget, root: b.root });
    }
    if (/el-cascader|el-rate|el-time-picker|el-time-select|el-color-picker|el-transfer|el-tree-select/.test(b.widget)) {
      addFlag({ formId, file, prop: b.prop, flag: "RISKY_WIDGET:" + b.widget });
    }
    if (b.prop.includes(".")) addFlag({ formId, file, prop: b.prop, flag: "NESTED_MODEL_PATH" });
  }
  rows.push({
    formId, file, prop: "(instance)", declaredType: "lifecycle",
    valueType: "-", required: false, hasOptions: false,
    widgets: roots.length ? "roots:" + roots.join(",") : "NO_ROOT",
    editable: false, aiWritable: false, readonly: false, password: false,
    multiple: false, allowCreate: false, nested: false,
    realFill: "", unregister: unregs > 0 ? "yes" : "NO_UNREGISTER"
  });
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, "field-matrix.json"), JSON.stringify({ rows, flags }, null, 2));
let md = "# FORM P7 field matrix\n\nGenerated by frontend/tests/field_matrix.mjs from formCoverage.ts registrations.\nBindings scoped to each registration getModel root; descriptors resolved per registration fields.\n\n";
md += "## Flags (" + flags.length + ")\n\n| formId | file | prop | flag |\n|---|---|---|---|\n";
for (const f of flags) md += "| " + f.formId + " | " + f.file + " | " + (f.prop || "-") + " | " + f.flag + (f.widget ? " " + f.widget + " " + (f.root || "") : "") + " |\n";
md += "\n## Rows (" + rows.length + ")\n\n| formId | prop | type | valueType | req | widgets | editable | aiWritable | realFill |\n|---|---|---|---|---|---|---|---|---|\n";
for (const r of rows) md += "| " + r.formId + " | " + r.prop + " | " + r.declaredType + " | " + r.valueType + " | " + (r.required ? "Y" : "") + " | " + r.widgets + " | " + (r.editable ? "Y" : "") + " | " + (r.aiWritable ? "Y" : "") + " | " + (r.realFill ? "YES" : "") + " |\n";
writeFileSync(join(outDir, "field-matrix.md"), md);
console.log("forms=" + forms.length + " rows=" + rows.length + " flags=" + flags.length);
