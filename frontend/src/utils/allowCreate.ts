// P2 allow-create 提交语义：Element Plus 的 filterable + allow-create 只有在
// 回车选中 / 点击“创建”建议项时才把输入框文字落入 v-model；直接失焦、点击
// 其他字段、点击发布都不会提交。本模块把“显示值 → Vue model”的提交规则收敛
// 为纯函数（便于单元测试），组件侧在 blur 与提交前调用；绝不只改 DOM 文字。
export function commitAllowCreateText(inputText: unknown, modelValue: unknown): string {
  if (typeof modelValue === 'string' && modelValue.trim() !== '') return modelValue
  return typeof inputText === 'string' ? inputText.trim() : ''
}
// 选项兜底：model 有值但不在选项中时追加，保证下拉框回显
// （AI 直接写 model、选项尚未加载完的场景；label 与 value 同为名称时可安全提交）。
export function withModelOption<T extends { label: string; value: string | number }>(options: T[], modelValue: unknown): T[] {
  if (typeof modelValue !== 'string' || modelValue.trim() === '') return options
  if (options.some(item => String(item.value) === modelValue || item.label === modelValue)) return options
  return [...options, { label: modelValue, value: modelValue } as T]
}
// 从 el-select 组件实例读出当前输入框显示文字（提交前兜底用）。
export function readSelectInputText(selectRef: unknown): string {
  try {
    const el = (selectRef as { $el?: unknown }).$el
    if (!(el instanceof HTMLElement)) return ''
    const input = el.querySelector('input.el-input__inner')
    return input instanceof HTMLInputElement ? input.value : ''
  } catch {
    return ''
  }
}
