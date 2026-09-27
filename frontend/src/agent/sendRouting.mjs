const BUSINESS_ACTION = /(?:发邮件|发 mail|邮件通知|发送邮件|发提醒|发送提醒|发消息|发送消息|通知学生|提醒学生|给学生|给老师|给企业|投递|申请.{0,20}岗位)/i

/** 当前有表单时统一交给字段分配模型判断，不再用固定句式猜测用户怎么说。 */
export function shouldResolveFormFill(message, { hasForm = false } = {}) {
  const text = String(message || '').trim()
  return Boolean(hasForm && text && !BUSINESS_ACTION.test(text))
}

/** 页面/表单导航同样交给 UI 意图模型判断，普通问答会由它返回 chat 后继续走顾问。 */
export function shouldResolveUiIntent(message) {
  const text = String(message || '').trim()
  return Boolean(text && !BUSINESS_ACTION.test(text))
}
