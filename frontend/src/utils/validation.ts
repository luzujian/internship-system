// 通用表单验证规则工具

/**
 * 年级验证器
 * @param message 自定义错误消息
 */
export const createGradeValidator = (message = '请输入 4 位年份，如 2024') => ({
  validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
    if (!value) {
      callback(new Error('请输入年级'))
    } else if (!/^\d{4}$/.test(value)) {
      callback(new Error(message))
    } else {
      const year = parseInt(value, 10)
      const currentYear = new Date().getFullYear()
      if (year < currentYear - 10 || year > currentYear + 2) {
        callback(new Error(`请输入有效的年份（${currentYear - 10}-${currentYear + 2}）`))
      } else {
        callback()
      }
    }
  },
  trigger: 'blur'
})

/**
 * 手机号验证器
 * @param message 自定义错误消息
 */
export const createPhoneValidator = (message = '请输入 11 位中国大陆手机号') => ({
  validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
    if (!value) {
      callback(new Error('请输入手机号'))
    } else if (!/^1[3-9]\d{9}$/.test(value)) {
      callback(new Error(message))
    } else {
      callback()
    }
  },
  trigger: 'blur'
})

/**
 * 邮箱验证器
 * @param message 自定义错误消息
 */
export const createEmailValidator = (message = '请输入有效的邮箱地址，如 example@email.com') => ({
  validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
    if (!value) {
      callback(new Error('请输入邮箱'))
    } else if (!/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value)) {
      callback(new Error(message))
    } else {
      callback()
    }
  },
  trigger: 'blur'
})

/**
 * 密码验证器
 * @param minLength 最小长度
 * @param complexity 复杂度要求：['lowercase', 'uppercase', 'number', 'special']
 */
export const createPasswordValidator = (
  minLength = 6,
  complexity: string[] = []
) => ({
  validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
    if (!value) {
      callback(new Error('请输入密码'))
      return
    }
    if (value.length < minLength) {
      callback(new Error(`密码长度不能少于${minLength}位`))
      return
    }
    if (complexity.includes('lowercase') && !/[a-z]/.test(value)) {
      callback(new Error('密码必须包含小写字母'))
      return
    }
    if (complexity.includes('uppercase') && !/[A-Z]/.test(value)) {
      callback(new Error('密码必须包含大写字母'))
      return
    }
    if (complexity.includes('number') && !/\d/.test(value)) {
      callback(new Error('密码必须包含数字'))
      return
    }
    if (complexity.includes('special') && !/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]/.test(value)) {
      callback(new Error('密码必须包含特殊字符'))
      return
    }
    callback()
  },
  trigger: 'blur'
})

/**
 * 必填验证器
 * @param message 自定义错误消息
 */
export const createRequiredValidator = (message = '此项为必填项') => ({
  required: true,
  message,
  trigger: 'blur'
})

/**
 * 数字范围验证器
 * @param min 最小值
 * @param max 最大值
 * @param message 自定义错误消息
 */
export const createNumberRangeValidator = (
  min: number,
  max: number,
  message?: string
) => ({
  validator: (_rule: any, value: number | string, callback: (error?: Error) => void) => {
    const numValue = typeof value === 'string' ? parseFloat(value) : value
    if (isNaN(numValue)) {
      callback(new Error('请输入有效的数字'))
      return
    }
    if (numValue < min || numValue > max) {
      callback(new Error(message || `请输入${min}到${max}之间的数字`))
      return
    }
    callback()
  },
  trigger: 'blur'
})

/**
 * 字符串长度验证器
 * @param min 最小长度
 * @param max 最大长度
 * @param message 自定义错误消息
 */
export const createStringLengthValidator = (
  min: number,
  max: number,
  message?: string
) => ({
  validator: (_rule: any, value: string, callback: (error?: Error) => void) => {
    if (!value) {
      callback(new Error('请输入内容'))
      return
    }
    if (value.length < min || value.length > max) {
      callback(new Error(message || `长度应在${min}到${max}个字符之间`))
      return
    }
    callback()
  },
  trigger: 'blur'
})
