const BUSINESS_ACTION = /(?:发邮件|发 mail|邮件通知|发送邮件|发提醒|发送提醒|发消息|发送消息|通知学生|提醒学生|给学生|给老师|给企业|投递|申请.{0,20}岗位)/i
// 普通问题直接交给问答，避免在发送前串行等待一次页面/填表意图模型。
// 明确要求操作或修改字段的混合请求仍由原有语义识别处理。
const UI_ACTION = /(?:帮我|替我|请你)?(?:打开|进入|跳转|切换|填写|填入|填上|改成|改为|设为|设置为|换成|选中|选择|勾选|取消勾选)|(?:标题|内容|状态|名称|目标群体).{0,10}(?:用|弄成)|(?:顺便|然后|再帮我)/
export function isDirectQuestion(message) {
  const text = String(message || '').trim()
  // “可以选择多少人吗”是在问字段能力；“帮我选择张三”才需要操作路由。
  const asksAboutAction = !/帮我|替我|请你|顺便|然后|再帮|改成|改为|设为|设置为|换成|填入|填上|选中|勾选/.test(text)
    && /怎么|如何|在哪|哪里|能否|是否|能不能|可不可以|(?:可以|能).+吗[？?。!！]*$/.test(text)
  if (asksAboutAction) return true
  return Boolean(text && !UI_ACTION.test(text) && /什么|为何|为什么|怎么|如何|多少|几个|几项|是谁|哪里|哪儿|哪些|是否|吗[？?。!！]*$|[？?]$/.test(text))
}

/** 当前有表单时统一交给字段分配模型判断，不再用固定句式猜测用户怎么说。 */
export function shouldResolveFormFill(message, { hasForm = false } = {}) {
  const text = String(message || '').trim()
  return Boolean(hasForm && text && !BUSINESS_ACTION.test(text) && !isDirectQuestion(text))
}

/** 页面/表单导航同样交给 UI 意图模型判断，普通问答会由它返回 chat 后继续走顾问。 */
export function shouldResolveUiIntent(message) {
  const text = String(message || '').trim()
  return Boolean(text && !BUSINESS_ACTION.test(text) && !isDirectQuestion(text))
}
