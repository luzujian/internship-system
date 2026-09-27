/** Route only a proposed AI authorization change; saving always requires its confirmation card. */
export function capabilityRequest(message, pending) {
  const text = message.trim()
  if (!text) return null
  const explanation = /^(?:请|帮我|麻烦)?(?:解释|介绍|说明)|为什么|有什么用|什么意思|有何区别|怎么(?:用|设置|配置|开启|关闭)|如何|是否已经|现在.*(?:权限|开启|关闭)|[吗么？?]$/.test(text)
  const politeAction = /(?:能不能|可以|能否).*帮我|请.*(?:开启|关闭|允许|禁止|取消|禁用|启用)/.test(text)
  if (explanation && !politeAction) return null
  const action = /赋予|授予|赋权|开通|给.{0,20}(?:加上|加|开)|开启|关闭|启用|禁用|允许|禁止|取消|撤销|撤回|开放|授权|限制|只准|只能|只允许|改为|改成|调整|恢复|不让|不允许|关掉|打开/.test(text)
  const capability = /AI|助手|语音|听写|填表|表单填写|对话|聊天|实习计划|岗位投递|自动(?:化|跟进|提醒)|邮件|站内(?:信|通知|提醒)|通知渠道|提醒间隔|冷却|单次.*(?:人数|上限)/i.test(text)
  const subject = /学生|教师|老师|辅导员|企业|管理员|用户|账号|账户|权限|授权/.test(text)
  if (action && capability && subject) return text
  if (pending && /^(?:账号|账户|用户名|学号|工号|是指|改成|改为|只针对|仅针对|补充|更正|不是|只允许|保留)/.test(text)) {
    return `${pending}\n管理员补充或修正（冲突处以本次为准）：${text}`
  }
  return null
}
