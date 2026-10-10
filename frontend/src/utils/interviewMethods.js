// 表单使用数组，接口沿用文本字段，同时兼容历史单选值和浏览器面试模板。
export const normalizeInterviewMethods = (value) => {
  const values = Array.isArray(value) ? value : [value]
  return [...new Set(values
    .filter(item => typeof item === 'string')
    .flatMap(item => item.split(/[、,，;；/|]/))
    .map(item => item.trim())
    .filter(Boolean))]
}

export const serializeInterviewMethods = (value) => normalizeInterviewMethods(value).join('、')
